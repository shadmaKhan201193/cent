package ai.kiya.process.camel.processor;

import java.net.ConnectException;
import java.util.Date;
import java.util.Map;

import javax.persistence.EntityManager;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.util.json.JsonObject;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import ai.kiya.process.constants.ProcessConstants;
import ai.kiya.process.dto.ProcessDefinitionWithDataDto;
import ai.kiya.process.dto.ProcessResponse;
import lombok.extern.slf4j.Slf4j;

@SuppressWarnings({ "rawtypes" })
@Slf4j
@Component
public class IndividualTaskProcessor implements Processor {

	@Autowired
	private EntityManager entityManager;
	
	@Autowired
	private RestTemplate restTemplate;

	@Override
	// don't do @Transactional because there is API call and it can not be reverted
	public void process(Exchange exchange) throws Exception {
		ProcessDefinitionWithDataDto dto = (ProcessDefinitionWithDataDto) exchange.getIn().getBody();
		Map<String, Object> request = dto.getRequest();
		if(request != null) {
			Session session = entityManager.unwrap(Session.class);
			Query updateQuery = getUpdateStatusQuery(dto, session);
			
			// putting below check if we are coming around from a retry
			// all the successfully processed records will be skipped
			// TODO - should we also skip the Failed (F) records ?
			if (ProcessConstants.PROCESS_STATUS_SUCCESS.equalsIgnoreCase((String) request.get("processingStatus"))) {
				return;
			}
			// TODO - set this in progress somewhere but not here; more than 1 update call
			// to DB causes deadlock issues
			// setIndividualRecordStatus(updateQuery,
			// ProcessConstants.PROCESS_STATUS_INPROGRESS, request.get("processingUUID"));
			ProcessResponse processResponse = executeProcess(dto, request);
			if (processResponse != null && processResponse.getProcessingStatus() != null
					&& processResponse.getProcessingStatus().equals(ProcessConstants.PROCESS_STATUS_SUCCESS)) {
				setIndividualRecordStatus(updateQuery, ProcessConstants.PROCESS_STATUS_SUCCESS,
						request.get(ProcessConstants.PROCESSING_UUID_STR), processResponse.getErrorReason());
			} else if (processResponse != null && processResponse.getProcessingStatus() != null
					&& processResponse.getProcessingStatus().equals(ProcessConstants.PROCESS_STATUS_ERROR)
					&& processResponse.getRecoverable()) {
				setIndividualRecordStatus(updateQuery, ProcessConstants.PROCESS_STATUS_ERROR,
						request.get(ProcessConstants.PROCESSING_UUID_STR), processResponse.getErrorReason());
			} else if (processResponse != null && processResponse.getProcessingStatus() != null
					&& processResponse.getProcessingStatus().equals(ProcessConstants.PROCESS_STATUS_ERROR)
					&& !processResponse.getRecoverable()) {
				setIndividualRecordStatus(updateQuery, ProcessConstants.PROCESS_STATUS_FAILED,
						request.get(ProcessConstants.PROCESSING_UUID_STR), processResponse.getErrorReason());
			}
		} else {
			//this is when there is no trigger table
			//ProcessResponse processResponse = 
			executeProcess(dto, request);
			//TODO - need to set D008004 to E if the response came in error
		}
	}

	protected ProcessResponse executeProcess(ProcessDefinitionWithDataDto dto, Map<String, Object> request) {
		try {
			if (dto != null) {
				ResponseEntity<ProcessResponse> response = null;
				if(request != null) {
					//Additional processing to map SP related params when executing SP process
					if(dto != null && dto.getSpExecutionYN() != null && dto.getSpExecutionYN() == 1) {
						mapSpFields(dto, request);
					}
					response = restTemplate.postForEntity(dto.getProcessApi(),
							new JsonObject(request), ProcessResponse.class);
				} else {
					response = restTemplate.postForEntity(dto.getProcessApi(),
							dto, ProcessResponse.class);
				}
				return response.getBody();
			} else {
				log.error("ProcessDefinition missing; cant find process API.");
			}
		} catch (RestClientException e) {
			// TODO : what would be the best way to log the error sent by called API?
			if (e != null && e instanceof ResourceAccessException && e.getCause() != null
					&& e.getCause() instanceof ConnectException) {
				// this is connection related issue, lets put this into retry mechanism
				log.error("API connection issue for process : {}", dto.getProcessId());
				e.printStackTrace();
			}
			if (e instanceof HttpServerErrorException) {
				try {
					return new ObjectMapper().readValue(((HttpServerErrorException) e).getResponseBodyAsString(),
							ProcessResponse.class);
				} catch (JsonProcessingException e1) {
					log.error("Failed to extract response from exception : " + e);
				}
				e.printStackTrace();
			}
			return new ProcessResponse(ProcessConstants.PROCESS_STATUS_ERROR, Boolean.TRUE,
					"API connection/response issue");
		}
		return new ProcessResponse(ProcessConstants.PROCESS_STATUS_SUCCESS, null, null);
	}

	private void mapSpFields(ProcessDefinitionWithDataDto dto, Map<String, Object> request) {
		request.put("spName", dto.getSpName());
		request.put("inputParam1", dto.getInputParam1());
		request.put("inputParam2", dto.getInputParam2());
		request.put("inputParam3", dto.getInputParam3());
		request.put("inputParam4", dto.getInputParam4());
		request.put("inputParam5", dto.getInputParam5());
	}

	private Query getUpdateStatusQuery(ProcessDefinitionWithDataDto dto, Session session) {
		Query queryUpdateStatus = session.createNativeQuery("UPDATE " + dto.getTriggerTable()
				+ " SET processingStatus = ?, lastModifiedDate = ?, message = ? WHERE "
				+ ProcessConstants.PROCESSING_UUID_STR + " = ? ");
		return queryUpdateStatus;
	}

	private void setIndividualRecordStatus(Query updateQuery, String status, Object uuid, String errorMessage) {
		updateQuery.setParameter(1, status);
		updateQuery.setParameter(2, new Date());
		updateQuery.setParameter(3, errorMessage);
		updateQuery.setParameter(4, uuid);
		updateQuery.executeUpdate();
	}
}
