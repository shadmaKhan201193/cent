package ai.kiya.process.camel.processor;

import java.util.Optional;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import ai.kiya.process.constants.ProcessConstants;
import ai.kiya.process.dto.BaseProcessRequestObj;
import ai.kiya.process.entities.BranchWiseProcessingStatus;
import ai.kiya.process.service.BranchWiseProcessingStatusService;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class RecoverableExceptionHandlerProcessor implements Processor {

	@Autowired
	private BranchWiseProcessingStatusService branchWiseProcessingStatusService;

	@Value("${max.retry.count}")
	private Integer maxRetryCount;

	@Override
	public void process(Exchange exchange) throws Exception {
		if (Optional.of(exchange).isPresent()) {

			String body = (String) exchange.getIn().getBody();
			BaseProcessRequestObj processObj = getProcessObject(body);
			BranchWiseProcessingStatus processingStatus = branchWiseProcessingStatusService
					.getById(processObj.getProcessingUUID());
			processingStatus.setStatus(ProcessConstants.PROCESS_STATUS_ERROR);
			processingStatus
					.setRetryCount(processingStatus.getRetryCount() != null ? processingStatus.getRetryCount() + 1 : 1);
			if (processingStatus.getRetryCount() >= maxRetryCount) {
				processingStatus.setStatus(ProcessConstants.PROCESS_STATUS_FAILED);
				log.error("Max retry consumed for processId {} branchCode : {}", processingStatus.getProcessId(),
						processingStatus.getBranchCode());
			}
			branchWiseProcessingStatusService.save(processingStatus);
			// TODO - set BO queue on the header
			// https://stackoverflow.com/questions/20483561/camel-forward-message-to-dynamic-destinations-from-database
		}
	}

	protected BaseProcessRequestObj getProcessObject(String body) throws JsonMappingException, JsonProcessingException {
		// Here you can convert incoming JSON into appropriate Java object which
		// represents your request. For e.g. a normal branch closure may only be a base
		// object with branch code but an interest calculation request will also have
		// accountId in it placed by the trigger process.
		return new ObjectMapper().readValue(body, BaseProcessRequestObj.class);
	}

}
