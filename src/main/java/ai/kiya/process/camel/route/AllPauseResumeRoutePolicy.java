package ai.kiya.process.camel.route;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.component.jms.JmsConsumer;
import org.apache.camel.impl.event.ExchangeCompletedEvent;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.camel.support.RoutePolicySupport;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.ObjectHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import ai.kiya.process.service.ProcessDefinitionService;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class AllPauseResumeRoutePolicy extends RoutePolicySupport implements CamelContextAware {

	private CamelContext camelContext;
	private final Lock lock = new ReentrantLock();
	private ContextScopedEventNotifier eventNotifier;

	@Autowired
	private ProcessDefinitionService processDefinitionService;

	@Override
	public final void setCamelContext(final CamelContext camelContext) {
		this.camelContext = camelContext;
	}

	@Override
	public final CamelContext getCamelContext() {
		return this.camelContext;
	}

	@Override
	public final void onExchangeDone(final Route route, final Exchange exchange) {
		throttle(route);
	}

	/*
	 * Removed this because this could leave current message dangling
	 * 
	 * @Override public void onExchangeBegin(Route route, Exchange exchange) {
	 * throttle(route); }
	 */

	private void throttle(final Route route) {
		// this works the best when this logic is executed when the exchange is done
		Consumer consumer = route.getConsumer();
		boolean stop = consumer instanceof JmsConsumer && isRouteMarkedForSuspension(consumer.getEndpoint().getEndpointBaseUri().substring(6))
				&& route.getConsumer() instanceof JmsConsumer && ((JmsConsumer) route.getConsumer()).isStarted();
		if (stop) {
			try {
				lock.lock();
				suspendOrStopConsumer(consumer);
				// suspendRoute(route);
				log.info("Suspended the route due to throttle condition.");
			} catch (Exception e) {
				handleException(e);
			} finally {
				lock.unlock();
			}
		}

		// reload size in case a race condition with too many at once being invoked
		// so we need to ensure that we read the most current size and start the
		// consumer if we are already to low
		boolean start = !isRouteMarkedForSuspension(consumer.getEndpoint().getEndpointBaseUri().substring(6))
				&& route.getConsumer() instanceof JmsConsumer && ((JmsConsumer) route.getConsumer()).isSuspended();
		if (start) {
			try {
				lock.lock();
				resumeOrStartConsumer(consumer);
				// resumeRoute(route);
				log.info("Resumed the route due to throttle condition.");
			} catch (Exception e) {
				handleException(e);
			} finally {
				lock.unlock();
			}
		}
	}

	private boolean isRouteMarkedForSuspension(String queue) {
		// TODO - here we need to call the logic which will confirm if the route needs
		// to be paused
		// using cache on called method (another class which will answer true/false)
		// will help ensure that we are not over-doing admin part here
		return queue.equalsIgnoreCase("genericTriggerQ") ? false : processDefinitionService.isProcessToBePaused(queue);
	}

	@Override
	protected final void doStart() throws Exception {
		ObjectHelper.notNull(camelContext, "CamelContext", this);
		eventNotifier = new ContextScopedEventNotifier();
		// must start the notifier before it can be used
		ServiceHelper.startService(eventNotifier);
		// we are in context scope, so we need to use an event notifier to keep track
		// when any exchanges is done on the camel context.
		// This ensures we can trigger accordingly to context scope
		camelContext.getManagementStrategy().addEventNotifier(eventNotifier);
	}

	@Override
	protected final void doStop() throws Exception {
		ObjectHelper.notNull(camelContext, "CamelContext", this);
		camelContext.getManagementStrategy().removeEventNotifier(eventNotifier);
	}

	private class ContextScopedEventNotifier extends EventNotifierSupport {

		@Override
		public void notify(final CamelEvent event) throws Exception {
			for (Route route : camelContext.getRoutes()) {
				throttle(route);
			}
		}

		@Override
		public boolean isEnabled(final CamelEvent event) {
			return event instanceof ExchangeCompletedEvent;
		}

		@Override
		protected void doStart() throws Exception {
			// noop
		}

		@Override
		protected void doStop() throws Exception {
			// noop
		}

		@Override
		public String toString() {
			return "ContextScopedEventNotifier";
		}
	}
}