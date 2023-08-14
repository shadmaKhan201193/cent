package ai.kiya.process;

import java.util.List;

import org.apache.camel.CamelContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.util.ObjectUtils;

import ai.kiya.process.camel.processor.IndividualTaskProcessor;
import ai.kiya.process.camel.route.AllPauseResumeRoutePolicy;
import ai.kiya.process.camel.route.ProcessRouteBuilder;
import ai.kiya.process.entities.ProcessDefinition;
import ai.kiya.process.service.ProcessDefinitionService;

@Configuration
public class PostStartupConfig {
	
	@Autowired
	private ProcessDefinitionService processDefinitionService;
	
	@Autowired
	private IndividualTaskProcessor individualTaskProcessor;
	
	@Autowired
	private CamelContext camelContext;
	
	@Autowired
	private AllPauseResumeRoutePolicy pauseResumePolicy;
	
	@EventListener(ApplicationReadyEvent.class)
	public void runAfterStartup() throws Exception {
		List<ProcessDefinition> processes = processDefinitionService.getAllActiveProcesses();
		if(processes != null) {
			for (ProcessDefinition processDefinition : processes) {
				if(processDefinition == null) {
					continue;
				}
				if(!ObjectUtils.isEmpty(processDefinition.getTriggerQueue())) {
					camelContext.addRoutes(new ProcessRouteBuilder("jms:" + processDefinition.getTriggerQueue(), processDefinition.getNoOfThreads(), individualTaskProcessor, pauseResumePolicy));
				}
			}
		}
	}
}
