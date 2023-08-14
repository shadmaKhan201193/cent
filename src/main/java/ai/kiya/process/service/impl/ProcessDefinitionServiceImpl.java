package ai.kiya.process.service.impl;

import java.net.URISyntaxException;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.apache.http.client.utils.URIBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import ai.kiya.process.entities.ProcessDefinition;
import ai.kiya.process.entities.ProcessDefinitionId;
import ai.kiya.process.repositories.ProcessDefinitionRepo;
import ai.kiya.process.service.ProcessDefinitionService;

@Service
public class ProcessDefinitionServiceImpl implements ProcessDefinitionService {

	@Autowired
	private ProcessDefinitionRepo processDefinitionRepo;
	
	@Value("${month.end.check.api}")
	private String monthEndCheckApi;
	
	@Autowired
	private RestTemplate restTemplate;
	
	@Override
	//@Cacheable(value = "processDefinition", key = "#tenantId + '_' + #processId", unless="#result == null")
	public ProcessDefinition getProcessDefinition(Integer tenantId, Integer processId) {
		Optional<ProcessDefinition> optionalProcessDefinition = processDefinitionRepo.findById(new ProcessDefinitionId(tenantId, processId));
		if(optionalProcessDefinition.isPresent()) {
			return optionalProcessDefinition.get();
		}
		else {
			return null;
		}
	}
	
	@Override
	//@Cacheable(value = "processDefinitionPaused", key = "#queueName", unless="#result == null")
	public boolean isProcessToBePaused(String queueName) {
		ProcessDefinition processDefinition = processDefinitionRepo.findByTriggerQueue(queueName);
		if(processDefinition != null && processDefinition.getPaused() != null) {
			return processDefinition.getPaused();
		}
		else {
			return Boolean.FALSE;
		}
	}

	@Override
	public List<ProcessDefinition> getAllActiveProcesses() {
		return processDefinitionRepo.findAllActiveProcesses();
	}
	
	@Override
	public ProcessDefinition findProcessForSchedule(Integer tenantId, Integer processId,
			String processTypeScheduled) {
		return processDefinitionRepo.findByScheduledProcesses(tenantId, processId, processTypeScheduled);
	}

	@Override
	public ProcessDefinition findProcessForDailySchedule(Integer tenantId, Integer processId,
			String processTypeScheduled, String frequency) {
		return processDefinitionRepo.findByTenantIdAndProcessIdAndScheduledAndFrequency(tenantId, processId, processTypeScheduled, frequency);
	}

	@Override
	public List<ProcessDefinition> findProcessesModifiedInLast10Min() {
		Date now = new Date();
		Date min10Ago = null;
		Calendar cal = Calendar.getInstance();
		// remove next line if you're always using the current time.
		cal.setTime(now);
		cal.add(Calendar.MINUTE, -10);
		min10Ago =  cal.getTime();
		return processDefinitionRepo.findByLastModifiedDateBetween(now, min10Ago);
	}
	
	@Override
	public Boolean isProcessingDateMonthEnd(String processingDate, Integer tenantId, String branchCd) {
		URIBuilder uriBuilder;
		try {
			uriBuilder = new URIBuilder(monthEndCheckApi);
			uriBuilder.addParameter("tenantId", ""+tenantId);
			uriBuilder.addParameter("branchCode", branchCd);
	
			ResponseEntity<Boolean> responseBool;
			responseBool = restTemplate.exchange(uriBuilder.build(), HttpMethod.GET, null,
					Boolean.class);
			return responseBool.getBody();
		} catch (URISyntaxException e1) {
			return Boolean.FALSE;
		} catch (RestClientException e) {
			return Boolean.FALSE;
		}
	}
	
	@Override
	public Boolean isProcessingDateQuarterEnd(String processingDate, Integer tenantId, String branchCd) {
		return false;
	}
	
	@Override
	public Boolean isProcessingDateYearEnd(String processingDate, Integer tenantId, String branchCd) {
		return false;
	}

}
