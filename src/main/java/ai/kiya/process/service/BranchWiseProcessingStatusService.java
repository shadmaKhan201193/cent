package ai.kiya.process.service;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.springframework.jms.JmsException;

import com.fasterxml.jackson.core.JsonProcessingException;

import ai.kiya.process.dto.BaseProcessRequestObj;
import ai.kiya.process.dto.ProcessResponse;
import ai.kiya.process.entities.BranchWiseProcessingStatus;

public interface BranchWiseProcessingStatusService {
	public BranchWiseProcessingStatus getById(UUID id);
	public void save(BranchWiseProcessingStatus obj);
	public Boolean isProcessCompletedForAllBranches(Integer tenantId, Integer processId, String processingDate);
	public Boolean isProcessPrerequisiteCompleted(String groupName, Integer tenantId, Integer processId, String processingDate);
	void updateStatus(String processingStatus, UUID recordId);
	public List<BranchWiseProcessingStatus> findByStatusAndProcessingDate(String status, String processingDate);
	public Boolean addNewManualProcessExecution(BaseProcessRequestObj processReq);
	public List<BranchWiseProcessingStatus> findManuallyCreatedUnProcessedRequests(String processingDate);
	public BranchWiseProcessingStatus getLastRunForRecursiveProcess(Integer tenantId, Integer processId);
	void saveAndFlush(BranchWiseProcessingStatus obj);
	public List<BranchWiseProcessingStatus> getErroredOrTriggerErroredProcesses(String date, Date minAgo);
	public List<String> getBranches(Integer tenantId);
	public List<BranchWiseProcessingStatus> fetchBranchWiseProcessForRetry(String date, Integer tenantId, Integer processId);
	public List<BranchWiseProcessingStatus> getTriggeredProcesses(String date, Date minAgo);
	public void retryBranchWiseProcess(Integer tenantId, Integer processId, String branchCode) throws JmsException, JsonProcessingException;
	public void updateStatus(String processingStatus, UUID processingUUID, Date initiatedDateTime);
	public ProcessResponse addNewManualProcessGroupExecution(BaseProcessRequestObj processReq) throws JmsException, JsonProcessingException;
	public void updateStatusAndError(String processingStatus, String errorReason, UUID processingUUID,
			Date initiatedDateTime);
}
