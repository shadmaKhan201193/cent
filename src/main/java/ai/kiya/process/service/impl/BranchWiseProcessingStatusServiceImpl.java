package ai.kiya.process.service.impl;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.JmsException;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import ai.kiya.process.constants.ProcessConstants;
import ai.kiya.process.dto.BaseProcessRequestObj;
import ai.kiya.process.dto.ProcessResponse;
import ai.kiya.process.entities.BranchWiseProcessingStatus;
import ai.kiya.process.entities.DailyProcessStatus;
import ai.kiya.process.entities.ProcessDefinition;
import ai.kiya.process.entities.ProcessGrouping;
import ai.kiya.process.repositories.BranchWiseProcessingStatusRepo;
import ai.kiya.process.repositories.DailyProcessingStatusRepo;
import ai.kiya.process.repositories.ProcessGroupingRepo;
import ai.kiya.process.service.BranchWiseProcessingStatusService;
import ai.kiya.process.service.ProcessDefinitionService;
import ai.kiya.process.util.DateFormatterUtil;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class BranchWiseProcessingStatusServiceImpl implements BranchWiseProcessingStatusService {

	@Autowired
	private BranchWiseProcessingStatusRepo branchWiseProcessingStatusRepo;

	@Autowired
	private ProcessGroupingRepo groupingRepo;

	@Autowired
	private DailyProcessingStatusRepo dailyProcessingStatusRepo;

	@Autowired
	private ProcessDefinitionService processDefinitionService;

	@PersistenceContext
	private EntityManager entityManager;

	@Autowired
	private JmsTemplate jmsTemplate;

	@Value("${generic.trigger.queue}")
	private String genericTriggerQueue;

	@Override
	public BranchWiseProcessingStatus getById(UUID id) {
		Optional<BranchWiseProcessingStatus> optionalBranchWiseProcessingStatus = branchWiseProcessingStatusRepo
				.findById(id);
		if (optionalBranchWiseProcessingStatus.isPresent()) {
			return optionalBranchWiseProcessingStatus.get();
		} else {
			return null;
		}
	}

	@Override
	public Boolean isProcessCompletedForAllBranches(Integer tenantId, Integer processId, String processingDate) {
		ProcessDefinition process = processDefinitionService.getProcessDefinition(tenantId, processId);
		if (process != null) {
			Integer inProgressOrErrorRecords = branchWiseProcessingStatusRepo
					.getCountOfUnprocessedOrInProgressOrInErrorRecords(tenantId, processId, processingDate);
			if (inProgressOrErrorRecords != null && inProgressOrErrorRecords == 0) {
				return Boolean.TRUE;
			} else {
				return Boolean.FALSE;
			}
		} else {
			return Boolean.FALSE;
		}
	}

	@Override
	public Boolean isProcessPrerequisiteCompleted(String groupName, Integer tenantId, Integer processId,
			String processingDate) {
		List<ProcessGrouping> groupItems = groupingRepo.findByGroupNameAndTenantId(groupName, tenantId);
		List<DailyProcessStatus> dailyProcessByGroupName = dailyProcessingStatusRepo
				.findByTenantIdAndGroupNameAndProcessingDate(tenantId, groupName, processingDate);
		// for processes with sequence before the process being checked
		for (ProcessGrouping processGrouping : groupItems) {
			if (processGrouping == null || processGrouping.getProcessId() == null) {
				log.error("Found missing group process or group process without processId.");
				return Boolean.FALSE;
			}
			if (!(processGrouping.getProcessId() == processId)) {
				for (DailyProcessStatus dailyProcessStatus : dailyProcessByGroupName) {
					// if the earlier process is not completed
					if (dailyProcessStatus != null
							&& dailyProcessStatus.getProcessId() == processGrouping.getProcessId()
							&& !dailyProcessStatus.getProcessingStatus()
									.equals(ProcessConstants.PROCESS_STATUS_SUCCESS)) {
						log.debug("Process {} still in progress, hence not scheduling process {}",
								dailyProcessStatus.getProcessId(), processId);
						return Boolean.FALSE;
					}
				}
			} else {
				return Boolean.TRUE;
			}
		}
		return Boolean.TRUE;
	}

	@Override
	public void save(BranchWiseProcessingStatus obj) {
		obj.setModifiedDateTime(new Date());
		branchWiseProcessingStatusRepo.save(obj);
	}

	@Override
	@Transactional
	public void saveAndFlush(BranchWiseProcessingStatus obj) {
		obj.setModifiedDateTime(new Date());
		// branchWiseProcessingStatusRepo.saveAndFlush(obj);
		entityManager.persist(obj);
		entityManager.flush();
		entityManager.detach(obj);
	}

	@Override
	public void updateStatus(String processingStatus, UUID recordId) {
		// Sending current datetime for modifiedDateTime. This will help with retry
		// mechanism.
		branchWiseProcessingStatusRepo.updateStatus(processingStatus, new Date(), recordId.toString());
	}

	@Override
	public List<BranchWiseProcessingStatus> findByStatusAndProcessingDate(String status, String processingDate) {
		return branchWiseProcessingStatusRepo.findByStatusAndProcessingDate(status, processingDate);
	}

	@Override
	public Boolean addNewManualProcessExecution(BaseProcessRequestObj processReq) {
		String date = DateFormatterUtil.getDateInGivenFormat(ProcessConstants.PROCESSING_DATE_FORMAT);

		if (processReq == null
				|| processReq.getBranchCodes() == null && ObjectUtils.isEmpty(processReq.getBranchCodes())) {
			log.error("Process add request missing or branch missing");
			return Boolean.FALSE;
		}

		for (String branch : processReq.getBranchCodes().split(",")) {
			BranchWiseProcessingStatus addProcess = new BranchWiseProcessingStatus();
			addProcess.setTenantId(processReq.getTenantId());
			addProcess.setBranchCode(branch);
			addProcess.setCreatedBy(processReq.getCreatedBy());
			addProcess.setProcessId(processReq.getProcessId());
			addProcess.setCreatedDateTime(new Date());
			addProcess.setId(UUID.randomUUID());
			addProcess.setModifiedDateTime(new Date());
			addProcess.setProcessingDate(date);
			addProcess.setStatus(ProcessConstants.PROCESS_STATUS_UNPROCESSED);
			addProcess.setScheduled(ProcessConstants.PROCESS_TYPE_MANUAL);
			branchWiseProcessingStatusRepo.save(addProcess);
		}

		return Boolean.TRUE;
	}

	@Override
	public List<BranchWiseProcessingStatus> findManuallyCreatedUnProcessedRequests(String processingDate) {
		return branchWiseProcessingStatusRepo.findByScheduledAndStatus(ProcessConstants.PROCESS_TYPE_MANUAL,
				ProcessConstants.PROCESS_STATUS_UNPROCESSED, processingDate);
	}

	@Override
	public BranchWiseProcessingStatus getLastRunForRecursiveProcess(Integer tenantId, Integer processId) {
		return branchWiseProcessingStatusRepo.findFirstByTenantIdAndProcessIdOrderByModifiedDateTimeDesc(tenantId,
				processId);
	}

	@Override
	public List<BranchWiseProcessingStatus> getErroredOrTriggerErroredProcesses(String processingDate, Date minAgo) {
		List<BranchWiseProcessingStatus> processes = branchWiseProcessingStatusRepo
				.findErroredOrTriggerErroredProcessesBefore10Mins(processingDate, minAgo);
		return processes;
	}

	@Override
	public List<String> getBranches(Integer tenantId) {
		List<String> branches = groupingRepo.getAllBranches(tenantId);
		return branches;
	}

	@Override
	public List<BranchWiseProcessingStatus> fetchBranchWiseProcessForRetry(String date, Integer tenantId,
			Integer processId) {
		List<BranchWiseProcessingStatus> processes = branchWiseProcessingStatusRepo.fetchBranchWiseProcessForRetry(date,
				processId, tenantId);
		return processes;
	}

	@Override
	public List<BranchWiseProcessingStatus> getTriggeredProcesses(String processingDate, Date minAgo) {
		List<BranchWiseProcessingStatus> processes = branchWiseProcessingStatusRepo
				.findTriggeredProcessesBefore20Mins(processingDate, minAgo);
		return processes;
	}

	@Override
	public void retryBranchWiseProcess(Integer tenantId, Integer processId, String branchCode)
			throws JmsException, JsonProcessingException {
		String processingDate = DateFormatterUtil.getDateInGivenFormat(ProcessConstants.PROCESSING_DATE_FORMAT);

		BranchWiseProcessingStatus process = branchWiseProcessingStatusRepo.findErroredRecordsByBranch(tenantId,
				processId, branchCode, processingDate);
		ProcessDefinition processDefinition = processDefinitionService.getProcessDefinition(process.getTenantId(),
				process.getProcessId());
		BaseProcessRequestObj baseProcessRequestObj = new BaseProcessRequestObj();
		baseProcessRequestObj.setBranchCode(process.getBranchCode());
		baseProcessRequestObj.setCreatedBy(process.getCreatedBy());
		baseProcessRequestObj.setProcessId(process.getProcessId());
		baseProcessRequestObj.setProcessingDate(process.getProcessingDate());
		baseProcessRequestObj.setProcessingUUID(process.getId());
		baseProcessRequestObj.setTenantId(process.getTenantId());
		baseProcessRequestObj.setProcessDefinition(processDefinition);
		jmsTemplate.convertAndSend(genericTriggerQueue.substring(4),
				new ObjectMapper().writeValueAsString(baseProcessRequestObj));
	}

	@Override
	public void updateStatus(String processingStatus, UUID processingUUID, Date initiatedDateTime) {
		// Sending current datetime for modifiedDateTime. This will help with retry
		// mechanism.
		branchWiseProcessingStatusRepo.updateStatus(processingStatus, new Date(), initiatedDateTime,
				processingUUID.toString());
	}

	@Override
	public ProcessResponse addNewManualProcessGroupExecution(BaseProcessRequestObj processReq)
			throws JmsException, JsonProcessingException {
		String date = DateFormatterUtil.getDateInGivenFormat(ProcessConstants.PROCESSING_DATE_FORMAT);

		if (processReq == null
				|| processReq.getBranchCodes() == null && ObjectUtils.isEmpty(processReq.getBranchCodes())) {
			log.error("Process add request missing or branch missing");
			return new ProcessResponse(ProcessConstants.PROCESS_STATUS_ERROR, Boolean.FALSE,
					"Process details or branch missing");
		}

		List<ProcessGrouping> groupDetails = groupingRepo.findByGroupNameAndTenantId(processReq.getGroupName(),
				processReq.getTenantId());
		for (ProcessGrouping processGrouping : groupDetails) {
			for (String branch : processReq.getBranchCodes().split(",")) {
				BranchWiseProcessingStatus processAlreadyPlanned = branchWiseProcessingStatusRepo
						.findbyTenantIdAndProcessIdAndBranchCodeAndProcessingDate(processGrouping.getTenantId(),
								processGrouping.getProcessId(), branch, date);
				if (processAlreadyPlanned != null) {
					return new ProcessResponse(ProcessConstants.PROCESS_STATUS_ERROR, Boolean.FALSE,
							"Process " + processAlreadyPlanned.getProcessId()
									+ " has already been executed/planned for the branch " + processAlreadyPlanned.getBranchCode());
				}
				BranchWiseProcessingStatus addProcess = new BranchWiseProcessingStatus();
				addProcess.setTenantId(processReq.getTenantId());
				addProcess.setBranchCode(branch);
				addProcess.setCreatedBy(processReq.getCreatedBy());
				addProcess.setProcessId(processGrouping.getProcessId());
				addProcess.setCreatedDateTime(new Date());
				addProcess.setId(UUID.randomUUID());
				addProcess.setModifiedDateTime(new Date());
				addProcess.setProcessingDate(date);
				addProcess.setStatus(ProcessConstants.PROCESS_STATUS_UNPROCESSED);
				addProcess.setScheduled(ProcessConstants.PROCESS_TYPE_MANUAL);
				branchWiseProcessingStatusRepo.save(addProcess);

				ProcessDefinition processDefinition = processDefinitionService
						.getProcessDefinition(addProcess.getTenantId(), addProcess.getProcessId());
				BaseProcessRequestObj baseProcessRequestObj = new BaseProcessRequestObj();
				baseProcessRequestObj.setBranchCode(branch);
				baseProcessRequestObj.setCreatedBy(processReq.getCreatedBy());
				baseProcessRequestObj.setProcessId(processGrouping.getProcessId());
				baseProcessRequestObj.setProcessingDate(date);
				baseProcessRequestObj.setProcessingUUID(addProcess.getId());
				baseProcessRequestObj.setTenantId(addProcess.getTenantId());
				baseProcessRequestObj.setProcessDefinition(processDefinition);
				jmsTemplate.convertAndSend(genericTriggerQueue.substring(4),
						new ObjectMapper().writeValueAsString(baseProcessRequestObj));
			}
		}

		return new ProcessResponse(ProcessConstants.PROCESS_STATUS_SUCCESS, null, null);
	}

	@Override
	public void updateStatusAndError(String processingStatus, String errorReason, UUID processingUUID,
			Date initiatedDateTime) {
		branchWiseProcessingStatusRepo.updateStatusAndError(processingStatus, errorReason, new Date(), initiatedDateTime,
				processingUUID.toString());
	}

}
