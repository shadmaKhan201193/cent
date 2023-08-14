package ai.kiya.process.camel.route;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import ai.kiya.process.camel.processor.MainProcessor;
import ai.kiya.process.camel.processor.PauseResumeRouteProcessor;
import ai.kiya.process.camel.processor.RecoverableExceptionHandlerProcessor;
import ai.kiya.process.camel.processor.TriggerProcessor;
import ai.kiya.process.exception.ProcessRecoverableException;

@Component
public class CamelRoute extends RouteBuilder {

	@Autowired
	private MainProcessor mainProcessor;
	
	@Autowired
	private TriggerProcessor triggerProcessor;
	
	@Autowired
	private PauseResumeRouteProcessor pauseResumeRouteProcessor;
	
	@Autowired
	private RecoverableExceptionHandlerProcessor exceptionHandlerProcessor;
	
	@Value("${branch.closure.backout.queue}")
	private String branchClosureBackoutQueue;
	
	@Value("${no.of.threads}")
	private Integer threadCount;
	
	@Value("${generic.trigger.queue}")
	private String genericTriggerQueue;
	
	@Value("${generic.initiation.queue}")
	private String genericInitiationQueue;
	
	/**
	 * IMPORTANT!!
	 * 
	 * This is an example of how we can use Apache Camel to build event based
	 * process flow. Each line in the method represents a step and we can actually
	 * put these steps in different places or different services altogether as long
	 * as you can contain your information within message body and headers. we begin
	 * with a sample request which has all the IDs and then at each step we fetch
	 * more information and forward the request. We can also do more things like
	 * insert some DB entries on each step.
	 * 
	 */
	@Override
	public void configure() throws Exception {

		//.routePolicy(new ThrottlingExceptionRoutePolicy(5, 5000, 2000, Arrays.asList(ProcessRecoverableException.class)))
		onException(ProcessRecoverableException.class)
			.process(exceptionHandlerProcessor)
			.maximumRedeliveries(5)
			.redeliveryDelay(100)
			.logStackTrace(false)
			//.retryAttemptedLogLevel(LoggingLevel.WARN)
			.to(branchClosureBackoutQueue).useOriginalMessage();

		from(genericTriggerQueue+"?connectionFactory=#connectionFactory&concurrentConsumers="+threadCount)
			//.routePolicy(pauseResumePolicy)
			.transacted()
			//.threads(threadCount).maxQueueSize(20)
			.log("Received a message - ${body}")
			.process(triggerProcessor);
		from(genericInitiationQueue+"?connectionFactory=#connectionFactory&concurrentConsumers="+threadCount)
			//.routePolicy(pauseResumePolicy)
			.transacted()
			//.threads(threadCount).maxQueueSize(20)
			.log("Received a message - ${body}")
			.process(mainProcessor);
		
		from("timer://simpleTimer?period=5m").process(pauseResumeRouteProcessor);

	}
	
}