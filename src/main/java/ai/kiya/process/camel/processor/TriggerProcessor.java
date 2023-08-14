package ai.kiya.process.camel.processor;

import java.net.ConnectException;
import java.util.Date;
import java.util.Optional;

import javax.transaction.Transactional;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import ai.kiya.process.constants.ProcessConstants;
import ai.kiya.process.dto.BaseProcessRequestObj;
import ai.kiya.process.dto.ProcessResponse;
import ai.kiya.process.entities.ProcessDefinition;
import ai.kiya.process.service.BranchWiseProcessingStatusService;
import ai.kiya.process.service.NotificationHelperService;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class TriggerProcessor implements Processor {

	@Autowired
	private BranchWiseProcessingStatusService branchWiseProcessingStatusService;

	@Autowired
	private JmsTemplate jmsTemplate;

	@Value("${generic.initiation.queue}")
	private String genericInitiationQueue;
	
	@Autowired
	private RestTemplate restTemplate;
	
	@Autowired
	private NotificationHelperService notificationHelperService;
	
	@Override
	@Transactional
	public void process(Exchange exchange) throws Exception {
		if (Optional.of(exchange).isPresent()) {

			// get the request body and convert into the request object to be used
			String body = (String) exchange.getIn().getBody();
			BaseProcessRequestObj processObj = getProcessObject(body);
			String nextStatus = null;
			Date initiatedDateTime = new Date();
			log.info("Received message for process {} branch {}", processObj.getProcessId(), processObj.getBranchCode());
			try {
				nextStatus = ProcessConstants.PROCESS_STATUS_INPROGRESS;
				ProcessResponse processResponse = executeProcess(processObj);
				if (processResponse != null && processResponse.getProcessingStatus() != null
						&& processResponse.getProcessingStatus().equals(ProcessConstants.PROCESS_STATUS_SUCCESS)) {
					nextStatus = ProcessConstants.PROCESS_STATUS_SAVED;
					if (processObj.getProcessDefinition().getProcessApi() == null
							|| !StringUtils.hasText(processObj.getProcessDefinition().getProcessApi())
							|| processObj.getProcessDefinition().getProcessApi().equals(ProcessConstants.STRING_NA)) {
						nextStatus = ProcessConstants.PROCESS_STATUS_SUCCESS;
						updateRecordStatus(processObj, nextStatus, initiatedDateTime);
						return;
					}
				} else if (processResponse != null && processResponse.getProcessingStatus() != null
						&& processResponse.getProcessingStatus().equals(ProcessConstants.PROCESS_STATUS_ERROR)
						&& processResponse.getRecoverable()) {
					// throw recoverable exception so this record can be retried
					nextStatus = ProcessConstants.PROCESS_STATUS_TRIGGER_ERROR;
					updateRecordStatusAndError(processObj, nextStatus, processResponse.getErrorReason(), initiatedDateTime);
					notificationHelperService.sendProcessNotification(processObj.getProcessId(),
							processObj.getTenantId(), ProcessConstants.PROCESS_TRIGGER_FAILED_EVENT,
							processObj.getBranchCode() != null ? "Errored for branch : " + processObj.getBranchCode()
									: "" + processResponse.getErrorReason());
					return;
				} else if (processResponse != null && processResponse.getProcessingStatus() != null
						&& processResponse.getProcessingStatus().equals(ProcessConstants.PROCESS_STATUS_FAILED)
						&& !processResponse.getRecoverable()) {
					nextStatus = ProcessConstants.PROCESS_STATUS_FAILED;
					updateRecordStatusAndError(processObj, nextStatus, processResponse.getErrorReason(), initiatedDateTime);
					notificationHelperService.sendProcessNotification(processObj.getProcessId(),
							processObj.getTenantId(), ProcessConstants.PROCESS_TRIGGER_FAILED_EVENT,
							processObj.getBranchCode() != null ? "Errored for branch : " + processObj.getBranchCode()
									: "" + processResponse.getErrorReason());
					return;
				}
				sendProcessMessage(processObj);
				nextStatus = ProcessConstants.PROCESS_STATUS_TRIGGERED;
				updateRecordStatus(processObj, nextStatus, initiatedDateTime);
			} catch (Exception e) {
				nextStatus = ProcessConstants.PROCESS_STATUS_TRIGGER_ERROR;
				updateRecordStatus(processObj, nextStatus, initiatedDateTime);
				throw e;
			}
		}
	}

	private void sendProcessMessage(BaseProcessRequestObj processObj) {
		log.info("sent message for process {} branch {}", processObj.getProcessId(), processObj.getBranchCode());
		jmsTemplate.convertAndSend(genericInitiationQueue.substring(4), processObj);
	}

	protected void updateRecordStatus(BaseProcessRequestObj processObj, String processingStatus, Date initiatedDateTime) {
		branchWiseProcessingStatusService.updateStatus(processingStatus, processObj.getProcessingUUID(), initiatedDateTime);
	}
	
	protected void updateRecordStatusAndError(BaseProcessRequestObj processObj, String processingStatus, String errorReason, Date initiatedDateTime) {
		branchWiseProcessingStatusService.updateStatusAndError(processingStatus, errorReason, processObj.getProcessingUUID(), initiatedDateTime);
	}

	protected BaseProcessRequestObj getProcessObject(String body) throws JsonMappingException, JsonProcessingException {
		// Here you can convert incoming JSON into appropriate Java object which
		// represents your request. For e.g. a normal branch closure may only be a base
		// object with branch code but an interest calculation request will also have
		// accountId in it placed by the trigger process.
		return new ObjectMapper().readValue(body, BaseProcessRequestObj.class);
	}

	protected ProcessResponse executeProcess(BaseProcessRequestObj processObj) {
		try {
			log.info("Executing process {} for branch {}", processObj.getProcessId(), processObj.getBranchCode());

			ProcessDefinition processDefinition = processObj.getProcessDefinition();
			if (processDefinition != null && processDefinition.getTriggerApi() != null) {
				ResponseEntity<ProcessResponse> response = restTemplate
						.postForEntity(processDefinition.getTriggerApi(), processObj, ProcessResponse.class);
				return response.getBody();
			}

		} catch (RestClientException e) {
			// TODO : what would be the best way to log the error sent by called API?
			if (e != null && e instanceof ResourceAccessException && e.getCause() != null
					&& e.getCause() instanceof ConnectException) {
				// this is connection related issue, lets put this into retry mechanism
				log.error("API connection issue for process : {}", processObj.getProcessId());
			}
			e.printStackTrace();
			return new ProcessResponse(ProcessConstants.PROCESS_STATUS_ERROR, Boolean.TRUE, "API connection/response issue");
		}
		return new ProcessResponse(ProcessConstants.PROCESS_STATUS_SUCCESS, null, null);
	}

}
