package ai.kiya.process.camel.processor;

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.SuspendableService;
import org.apache.camel.component.jms.JmsConsumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import ai.kiya.process.service.ProcessDefinitionService;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class PauseResumeRouteProcessor implements Processor {

	@Autowired
	private ProcessDefinitionService processDefinitionService;

	@Override
	public void process(Exchange exchange) throws Exception {
		List<Route> routes = exchange.getContext().getRoutes();
		for (Route route : routes) {
			if (route.getConsumer() instanceof JmsConsumer) {
				if (((SuspendableService) route.getConsumer()).isSuspended()
						&& !isRouteMarkedForSuspension(route.getConsumer().getEndpoint().getEndpointBaseUri().substring(6))) {
					SuspendableService ss = (SuspendableService) route.getConsumer();
					ss.resume();
					log.info("Resuming consumer for {}", route.getConsumer().getEndpoint().getEndpointBaseUri());
				}
			}
		}
	}

	private boolean isRouteMarkedForSuspension(String queue) {
		return processDefinitionService.isProcessToBePaused(queue);
	}

}
