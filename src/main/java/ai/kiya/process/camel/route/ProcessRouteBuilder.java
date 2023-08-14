package ai.kiya.process.camel.route;

import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.RoutePolicySupport;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
public class ProcessRouteBuilder extends RouteBuilder {

	private String queueName;
	private Integer threadCount;
	private Processor processor;
	private RoutePolicySupport camelPolicy;

	@Override
	public void configure() throws Exception {
		// TODO - setup all the routes dynamically with all params
		log.info("Configuring route for {}", queueName);
		String queueConnName = queueName + "?connectionFactory=#connectionFactory";
		if(threadCount != null && threadCount > 0) {
			queueConnName = queueConnName + "&concurrentConsumers=" + threadCount;
		}
		//from(queueName + "?connectionFactory=#connectionFactory&concurrentConsumers=" + threadCount)
		from(queueConnName)
				.routePolicy(camelPolicy).transacted()
				// .threads(threadCount).maxQueueSize(20)
				.log("Received a message - ${body}").process(processor).end();
	}
}