package ai.kiya.process.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jms.JmsException;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;

import ai.kiya.process.constants.ProcessConstants;
import ai.kiya.process.dto.BaseProcessRequestObj;
import ai.kiya.process.dto.ProcessResponse;
import ai.kiya.process.dto.StatusReportVO;
import ai.kiya.process.service.BranchWiseProcessingStatusService;
import ai.kiya.process.service.DailyProcessStatusService;
import lombok.extern.slf4j.Slf4j;

@RestController
@CrossOrigin(origins = "*")
@Slf4j
public class CentralizedProcessController {

	@Autowired
	private DailyProcessStatusService dailyProcessStatusService;

	@Autowired
	private BranchWiseProcessingStatusService branchWiseProcessingStatusService;

	@PostMapping(value = "/reprocess/{tenantId}/{processId}/{branchCode}")
	public ResponseEntity<Boolean> retryProcess(@PathVariable Integer tenantId, @PathVariable Integer processId,
			@PathVariable(required = false) String branchCode) throws JmsException, JsonProcessingException {

		log.info("Reprocessing for process : {}, branchCode : {}", processId, branchCode);
		if(branchCode == null) {
			dailyProcessStatusService.markProcessForRetry(tenantId, processId);
		} else {
			branchWiseProcessingStatusService.retryBranchWiseProcess(tenantId, processId, branchCode);
		}
		return new ResponseEntity<Boolean>(Boolean.TRUE, HttpStatus.OK);
	}

	@PostMapping(value = "/process/{tenantId}")
	public ResponseEntity<String> addProcessExecution(@PathVariable Integer tenantId,
			@RequestBody BaseProcessRequestObj processReq) {

		log.info("Process add request received for tenant {}, branch {}, process {} by user {}",
				processReq.getTenantId(), processReq.getBranchCodes(), processReq.getProcessId(),
				processReq.getCreatedBy());
		Boolean processAdded = branchWiseProcessingStatusService.addNewManualProcessExecution(processReq);
		if (Boolean.TRUE.equals(processAdded)) {
			return new ResponseEntity<String>("Process Added Successfully.", HttpStatus.OK);
		} else {
			return new ResponseEntity<String>("Process Add Failed.", HttpStatus.OK);
		}
	}
	
	@PostMapping(value = "/process/manual")
	public ResponseEntity<Map<String, String>> addProcessGroupExecution(@RequestBody BaseProcessRequestObj processReq) throws JmsException, JsonProcessingException {

		log.info("Process group add request received for tenant {}, branch {}, group {} by user {}",
				processReq.getTenantId(), processReq.getBranchCodes(), processReq.getGroupName(),
				processReq.getCreatedBy());
		ProcessResponse processResponse = branchWiseProcessingStatusService.addNewManualProcessGroupExecution(processReq);
		Map<String, String> response = new HashMap<>();
		if (processResponse == null) {
			log.error("Process response for manual add returned as null");
			response.put("errorMessage", "Process group add failed. Please retry.");
			return new ResponseEntity<Map<String, String>>(response, HttpStatus.OK);
		} else {
			if(ProcessConstants.PROCESS_STATUS_SUCCESS.equals(processResponse.getProcessingStatus())) {
				response.put("successMessage", "Manual processing for provided processes successfully added.");
			} else {
				response.put("errorMessage", processResponse.getErrorReason());
			}
			return new ResponseEntity<Map<String, String>>(response, HttpStatus.OK);
		}
	}

	@GetMapping(value = "/centralized-processing-status/{tenantId}/{processingDate}")
	public ResponseEntity<List<StatusReportVO>> getCentralizedProcessStatus(@PathVariable Integer tenantId,
			@PathVariable String processingDate) {
		List<StatusReportVO> responseVO = dailyProcessStatusService.getProcessStatus(tenantId, processingDate);
		return new ResponseEntity<List<StatusReportVO>>(responseVO, HttpStatus.OK);
	}
}
