package ai.kiya.process.schedulers;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;

import javax.transaction.Transactional;

import org.apache.camel.CamelExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.JmsException;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import ai.kiya.process.constants.ProcessConstants;
import ai.kiya.process.dto.BaseProcessRequestObj;
import ai.kiya.process.entities.BranchWiseProcessingStatus;
import ai.kiya.process.entities.DailyProcessStatus;
import ai.kiya.process.entities.ProcessDefinition;
import ai.kiya.process.service.BranchWiseProcessingStatusService;
import ai.kiya.process.service.DailyProcessStatusService;
import ai.kiya.process.service.NotificationHelperService;
import ai.kiya.process.service.ProcessDefinitionService;
import ai.kiya.process.util.DateFormatterUtil;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ProcessInitiatorScheduler {

	@Autowired
	private DailyProcessStatusService dailyProcessStatusService;

	@Autowired
	private ProcessDefinitionService processDefinitionService;

	@Autowired
	private BranchWiseProcessingStatusService branchWiseProcessingStatusService;

	@Autowired
	private JmsTemplate jmsTemplate;

	@Value("${generic.trigger.queue}")
	private String genericTriggerQueue;

	@Value("${tenantId}")
	private Integer tenantId;

	@Value("${report.email.process.id}")
	private Integer reportEmailProcessId;
	
	@Autowired
	private NotificationHelperService notificationHelperService;

	@Scheduled(cron = "40 * * * * ?", zone = "Asia/Kolkata") // TODO - make this run every minute instead of every 5
																// seconds
	@Transactional
	public void checkAndInitiateProcess() throws JmsException, JsonProcessingException {
		String date = DateFormatterUtil.getDateInGivenFormat(ProcessConstants.PROCESSING_DATE_FORMAT);

		// logic to initiate scheduled processes
		triggerUnprocessedRecords(date);

		// logic to initiate report processes
		triggerReportEmailRecords(date);

		// logic to initiate manually processes
		triggerManualRecords(date);

		// logic to initiate recursive processes
		triggerRecursiveRecords(date);

		// logic to initiate retry records
		triggerRetryRecords(date);
	}

	// TODO - if you are switching over to new day but the process from previous day
	// are still to be planned, do not check for the today's date as processing date
	private void triggerUnprocessedRecords(String date) {
		SimpleDateFormat sdf;
		List<DailyProcessStatus> pendingProcesses = dailyProcessStatusService
				.findByProcessingDateAndProcessingStatus(date, ProcessConstants.PROCESS_STATUS_UNPROCESSED);
		if (pendingProcesses != null && !pendingProcesses.isEmpty()) {
			sdf = new SimpleDateFormat("HHmm");
			sdf.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
			String time = sdf.format(new Date());
			for (DailyProcessStatus pendingProcess : pendingProcesses) {
				try {
					if (pendingProcess != null && pendingProcess.getExecutionTime() != null
					// don't pick recursive processes, they are app level and have different logic
					// to
					// check for trigger
							&& !ProcessConstants.FREQUENCY_RECURSIVE.equals(pendingProcess.getFrequency())
							// processing time has been reached
							&& Integer.parseInt(time) > Integer.parseInt(pendingProcess.getExecutionTime())
							// avoid processing report email processes, they will be executed separately
							&& !reportEmailProcessId.equals(pendingProcess.getProcessId())
							// no pending process in the sequence of the group
							&& branchWiseProcessingStatusService.isProcessPrerequisiteCompleted(
									pendingProcess.getGroupName(), pendingProcess.getTenantId(),
									pendingProcess.getProcessId(), pendingProcess.getProcessingDate())) {
						ProcessDefinition processDefinition = processDefinitionService
								.getProcessDefinition(pendingProcess.getTenantId(), pendingProcess.getProcessId());
						if (processDefinition != null) {
							pendingProcess.setProcessingStatus(ProcessConstants.PROCESS_STATUS_INPROGRESS);
							pendingProcess.setProcessBeginDateTime(new Date());
							dailyProcessStatusService.updateRecord(pendingProcess);
							BaseProcessRequestObj baseProcessRequestObj = new BaseProcessRequestObj();
							baseProcessRequestObj.setProcessId(pendingProcess.getProcessId());
							baseProcessRequestObj.setTenantId(pendingProcess.getTenantId());
							List<String> branchList = getBranches();
							branchWiseProcessTrigger(date, processDefinition, baseProcessRequestObj, branchList);
							log.info("Scheduled process {} for tenant {} for each branch.",
									pendingProcess.getProcessId(), pendingProcess.getTenantId());
							notificationHelperService.sendProcessNotification(pendingProcess.getProcessId(),
									pendingProcess.getTenantId(), ProcessConstants.PROCESS_BEGIN_EVENT, null);
						} else {
							log.error("Process not found in process definition master.");
						}
					}
				} catch (NumberFormatException e) {
					e.printStackTrace();
					log.error("Scheduled process times not provided in the correct format.");
				}
			}
		}
	}

	private void triggerReportEmailRecords(String date) {
		SimpleDateFormat sdf;
		List<DailyProcessStatus> pendingProcesses = dailyProcessStatusService
				.findByProcessingDateAndProcessingStatus(date, ProcessConstants.PROCESS_STATUS_UNPROCESSED);
		if (pendingProcesses != null && !pendingProcesses.isEmpty()) {
			sdf = new SimpleDateFormat("HHmm");
			sdf.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
			String time = sdf.format(new Date());
			for (DailyProcessStatus pendingProcess : pendingProcesses) {
				try {
					if (pendingProcess != null && pendingProcess.getExecutionTime() != null
					// don't pick recursive processes, they are app level and have different logic
					// to
					// check for trigger
							&& !ProcessConstants.FREQUENCY_RECURSIVE.equals(pendingProcess.getFrequency())
							// processing time has been reached
							&& Integer.parseInt(time) > Integer.parseInt(pendingProcess.getExecutionTime())
							// avoid processing report email processes, they will be executed separately
							&& reportEmailProcessId.equals(pendingProcess.getProcessId())) {

						ProcessDefinition processDefinition = processDefinitionService
								.getProcessDefinition(pendingProcess.getTenantId(), pendingProcess.getProcessId());
						pendingProcess.setProcessBeginDateTime(new Date());
						pendingProcess.setProcessingStatus(ProcessConstants.PROCESS_STATUS_INPROGRESS);
						dailyProcessStatusService.updateRecord(pendingProcess);
						BaseProcessRequestObj baseProcessRequestObj = new BaseProcessRequestObj();
						baseProcessRequestObj.setProcessId(pendingProcess.getProcessId());
						baseProcessRequestObj.setTenantId(pendingProcess.getTenantId());
						baseProcessRequestObj.setProcessingUUID(pendingProcess.getRecordId());
						baseProcessRequestObj.setGroupName(pendingProcess.getGroupName());
						baseProcessRequestObj.setProcessingDate(pendingProcess.getProcessingDate());
						triggerAndSendMsg(date, processDefinition, baseProcessRequestObj, null);
						log.info("Scheduled process {} for tenant {} for each branch.", pendingProcess.getProcessId(),
								pendingProcess.getTenantId());
					}
				} catch (NumberFormatException e) {
					e.printStackTrace();
					log.error("Scheduled process times not provided in the correct format.");
				}
			}
		}
	}

	private void triggerManualRecords(String processingDate) throws JsonProcessingException {
		List<BranchWiseProcessingStatus> branchWiseProcessing = branchWiseProcessingStatusService
				.findManuallyCreatedUnProcessedRequests(processingDate);
		if (branchWiseProcessing != null && !branchWiseProcessing.isEmpty()) {
			for (BranchWiseProcessingStatus branchWiseProcessingStatus : branchWiseProcessing) {
				ProcessDefinition processDefinition = processDefinitionService.getProcessDefinition(
						branchWiseProcessingStatus.getTenantId(), branchWiseProcessingStatus.getProcessId());
				BaseProcessRequestObj baseProcessRequestObj = new BaseProcessRequestObj();
				baseProcessRequestObj.setBranchCode(branchWiseProcessingStatus.getBranchCode());
				baseProcessRequestObj.setCreatedBy(branchWiseProcessingStatus.getCreatedBy());
				baseProcessRequestObj.setProcessId(branchWiseProcessingStatus.getProcessId());
				baseProcessRequestObj.setProcessingDate(branchWiseProcessingStatus.getProcessingDate());
				baseProcessRequestObj.setProcessingUUID(branchWiseProcessingStatus.getId());
				baseProcessRequestObj.setTenantId(branchWiseProcessingStatus.getTenantId());
				mapProcessDefinition(processDefinition, baseProcessRequestObj);
				jmsTemplate.convertAndSend(genericTriggerQueue.substring(4),
						new ObjectMapper().writeValueAsString(baseProcessRequestObj));
			}
		}
	}

	private void triggerRecursiveRecords(String date) throws JsonProcessingException {
		List<DailyProcessStatus> recursiveProcesses = dailyProcessStatusService.findRecursiveProcesses(date);
		if (recursiveProcesses != null && !recursiveProcesses.isEmpty()) {
			for (DailyProcessStatus recursiveProcess : recursiveProcesses) {
				// this would be the place where we can check if we ever decide to run the
				// process on specific days
				if (checkIfEligibleForNextRun(recursiveProcess)) {
					ProcessDefinition processDefinition = processDefinitionService
							.getProcessDefinition(recursiveProcess.getTenantId(), recursiveProcess.getProcessId());

					BranchWiseProcessingStatus branchWiseProcessingTrigger = new BranchWiseProcessingStatus();
					branchWiseProcessingTrigger.setId(UUID.randomUUID());
					branchWiseProcessingTrigger.setCreatedBy(ProcessConstants.USER_SYSTEM);
					branchWiseProcessingTrigger.setCreatedDateTime(new Date());
					branchWiseProcessingTrigger.setProcessId(processDefinition.getProcessId());
					branchWiseProcessingTrigger.setStatus(ProcessConstants.PROCESS_STATUS_UNPROCESSED);
					branchWiseProcessingTrigger.setTenantId(processDefinition.getTenantId());
					branchWiseProcessingTrigger.setProcessingDate(date);
					branchWiseProcessingStatusService.saveAndFlush(branchWiseProcessingTrigger);

					BaseProcessRequestObj baseProcessRequestObj = new BaseProcessRequestObj();
					baseProcessRequestObj.setProcessingUUID(branchWiseProcessingTrigger.getId());
					baseProcessRequestObj.setCreatedBy(ProcessConstants.USER_SYSTEM);
					baseProcessRequestObj.setProcessId(processDefinition.getProcessId());
					baseProcessRequestObj.setProcessingDate(recursiveProcess.getProcessingDate());
					baseProcessRequestObj.setTenantId(processDefinition.getTenantId());
					mapProcessDefinition(processDefinition, baseProcessRequestObj);
					jmsTemplate.convertAndSend(genericTriggerQueue.substring(4),
							new ObjectMapper().writeValueAsString(baseProcessRequestObj));
				}
			}
		}
	}

	private void triggerRetryRecords(String date) {
		List<DailyProcessStatus> retryProcesses = dailyProcessStatusService
				.findByProcessingDateAndProcessingStatus(date, ProcessConstants.PROCESS_STATUS_RETRY);
		if (retryProcesses != null && !retryProcesses.isEmpty()) {
			for (DailyProcessStatus retryProcess : retryProcesses) {
				try {
					ProcessDefinition processDefinition = processDefinitionService
							.getProcessDefinition(retryProcess.getTenantId(), retryProcess.getProcessId());
					if (processDefinition != null) {
						retryProcess.setProcessingStatus(ProcessConstants.PROCESS_STATUS_INPROGRESS);
						dailyProcessStatusService.updateRecord(retryProcess);
						BaseProcessRequestObj baseProcessRequestObj = new BaseProcessRequestObj();
						baseProcessRequestObj.setProcessId(retryProcess.getProcessId());
						baseProcessRequestObj.setTenantId(retryProcess.getTenantId());
						branchWiseProcessReTrigger(date, processDefinition, baseProcessRequestObj);
						log.info("Scheduled process {} for tenant {} for each branch.", retryProcess.getProcessId(),
								retryProcess.getTenantId());
					}
				} catch (NumberFormatException e) {
					e.printStackTrace();
					log.error("Scheduled process times not provided in the correct format.");
				}
			}
		}
	}

	private boolean isProcessToBeSkipped(String processFrequency, String processingDate, Integer tenantId, String branchCd) {
		if ((ProcessConstants.FREQUENCY_MONTHLY.equals(processFrequency)
				&& !processDefinitionService.isProcessingDateMonthEnd(processingDate, tenantId, branchCd))
				|| (ProcessConstants.FREQUENCY_QUARTERLY.equals(processFrequency)
						&& !processDefinitionService.isProcessingDateQuarterEnd(processingDate, tenantId, branchCd))
				|| (ProcessConstants.FREQUENCY_YEARLY.equals(processFrequency)
						&& !processDefinitionService.isProcessingDateYearEnd(processingDate, tenantId, branchCd))) {
			return true;
		}
		return false;
	}

	// TODO - write scheduler which will pick up X or N records which have been
	// lying for last 10 mins

	private boolean checkIfEligibleForNextRun(DailyProcessStatus recursiveProcess) {
		BranchWiseProcessingStatus previousRun = branchWiseProcessingStatusService
				.getLastRunForRecursiveProcess(recursiveProcess.getTenantId(), recursiveProcess.getProcessId());
		if (previousRun == null) {
			// no previous run then its time to plan this since its already been an internal
			// from 'never'
			return true;
		} else if (previousRun != null && (ProcessConstants.PROCESS_STATUS_SUCCESS.equals(previousRun.getStatus())
				|| ProcessConstants.PROCESS_STATUS_TRIGGER_ERROR.equals(previousRun.getStatus())
				|| ProcessConstants.PROCESS_STATUS_ERROR.equals(previousRun.getStatus())
				|| ProcessConstants.PROCESS_STATUS_FAILED.equals(previousRun.getStatus()))) {
			Calendar calendar = Calendar.getInstance();
			calendar.setTime(previousRun.getModifiedDateTime());
			calendar.add(Calendar.MINUTE, Integer.parseInt(recursiveProcess.getExecutionTime()));
			if (calendar.getTime().before(new Date())) {
				calendar.setTime(previousRun.getCreatedDateTime());
				calendar.add(Calendar.MINUTE, Integer.parseInt(recursiveProcess.getExecutionTime()));
				if (calendar.getTime().before(new Date())) {
					// required interval elapsed since the last run, time to plan the process again
					return true;
				} else {
					return false;
				}
			} else {
				return false;
			}
		} else {
			// last run is either still in progress or it is in error/failed state
			return false;
		}
	}

	private List<String> getBranches() {
		List<String> branchList = branchWiseProcessingStatusService.getBranches(tenantId);
		return branchList;
	}

	/*
	 * private List<String> getDummyBranches() { List<String> branchList =
	 * IntStream.rangeClosed(1,
	 * 2).mapToObj(Integer::toString).collect(Collectors.toList()); return
	 * branchList; }
	 */

	private void branchWiseProcessTrigger(String date, ProcessDefinition processDefinition,
			BaseProcessRequestObj baseProcessRequestObj, List<String> branchList) {
		if (processDefinition.getBranchWise() != null && processDefinition.getBranchWise().equals("1")) {
			for (String branchCd : branchList) {
				if(isProcessToBeSkipped(processDefinition.getFrequency(), date,
						processDefinition.getTenantId(), branchCd)) {
					continue;
				}
				baseProcessRequestObj.setBranchCode(branchCd);
				triggerAndSendMsg(date, processDefinition, baseProcessRequestObj, branchCd);
			}
		} else {
			triggerAndSendMsg(date, processDefinition, baseProcessRequestObj, null);
		}
	}

	private void branchWiseProcessReTrigger(String date, ProcessDefinition processDefinition,
			BaseProcessRequestObj baseProcessRequestObj) {
		List<BranchWiseProcessingStatus> branchWiseProcessRecords = branchWiseProcessingStatusService
				.fetchBranchWiseProcessForRetry(date, baseProcessRequestObj.getTenantId(),
						baseProcessRequestObj.getProcessId());
		for (BranchWiseProcessingStatus branchRecord : branchWiseProcessRecords) {
			triggerAndSendMsgForRetry(date, processDefinition, baseProcessRequestObj, branchRecord);
		}
	}

	private void triggerAndSendMsg(String date, ProcessDefinition processDefinition,
			BaseProcessRequestObj baseProcessRequestObj, String branchCd) {
		try {
			BranchWiseProcessingStatus branchWiseProcessing = new BranchWiseProcessingStatus();
			branchWiseProcessing.setId(UUID.randomUUID());
			branchWiseProcessing.setBranchCode(branchCd);
			branchWiseProcessing.setCreatedBy(ProcessConstants.USER_SYSTEM);
			branchWiseProcessing.setCreatedDateTime(new Date());
			branchWiseProcessing.setProcessId(baseProcessRequestObj.getProcessId());
			branchWiseProcessing.setStatus(ProcessConstants.PROCESS_STATUS_UNPROCESSED);
			branchWiseProcessing.setTenantId(baseProcessRequestObj.getTenantId());
			branchWiseProcessing.setProcessingDate(date);

			baseProcessRequestObj.setProcessingUUID(branchWiseProcessing.getId());
			mapProcessDefinition(processDefinition, baseProcessRequestObj);
			baseProcessRequestObj.setCreatedBy(ProcessConstants.USER_SYSTEM);
			baseProcessRequestObj.setProcessingDate(date);

			branchWiseProcessingStatusService.saveAndFlush(branchWiseProcessing);

			// jmsTemplate.convertAndSend(processDefinition.getTriggerQueue(),
			jmsTemplate.convertAndSend(genericTriggerQueue.substring(4),
					new ObjectMapper().writeValueAsString(baseProcessRequestObj));

			log.debug("Triggered process {} for branch : {} and groupName : {} ", branchWiseProcessing.getProcessId(),
					branchCd, baseProcessRequestObj.getGroupName());
		} catch (CamelExecutionException | JsonProcessingException e) {
			e.printStackTrace(); // TODO - remove printStackTrace from everywhere
			log.error("Unable to send trigger message to the queue for process : {} & branch : {}",
					baseProcessRequestObj.getProcessId(), baseProcessRequestObj.getBranchCode());
		}
	}

	private void triggerAndSendMsgForRetry(String date, ProcessDefinition processDefinition,
			BaseProcessRequestObj baseProcessRequestObj, BranchWiseProcessingStatus branchRecord) {
		try {
			branchRecord.setStatus(ProcessConstants.PROCESS_STATUS_UNPROCESSED);
			branchRecord.setRetryCount(branchRecord.getRetryCount() == null ? 1 : branchRecord.getRetryCount() + 1);
			branchWiseProcessingStatusService.save(branchRecord);

			baseProcessRequestObj.setProcessingUUID(branchRecord.getId());
			mapProcessDefinition(processDefinition, baseProcessRequestObj);
			baseProcessRequestObj.setCreatedBy(ProcessConstants.USER_SYSTEM);
			baseProcessRequestObj.setProcessingDate(date);

			jmsTemplate.convertAndSend(genericTriggerQueue.substring(4),
					new ObjectMapper().writeValueAsString(baseProcessRequestObj));

			log.debug("Retriggered process {} or branch : {}", processDefinition.getProcessId(), branchRecord);
		} catch (CamelExecutionException | JsonProcessingException e) {
			e.printStackTrace(); // TODO - remove printStackTrace from everywhere
			log.error("Unable to send trigger message to the queue for process : {} & branch : {}",
					baseProcessRequestObj.getProcessId(), baseProcessRequestObj.getBranchCode());
		}
	}

	private void mapProcessDefinition(ProcessDefinition processDefinition,
			BaseProcessRequestObj baseProcessRequestObj) {
		baseProcessRequestObj.setProcessDefinition(processDefinition);
		baseProcessRequestObj.setSpName(processDefinition.getSpName());
		baseProcessRequestObj.setInputParam1(processDefinition.getInputParam1());
		baseProcessRequestObj.setInputParam2(processDefinition.getInputParam2());
		baseProcessRequestObj.setInputParam3(processDefinition.getInputParam3());
		baseProcessRequestObj.setInputParam4(processDefinition.getInputParam4());
		baseProcessRequestObj.setInputParam5(processDefinition.getInputParam5());
	}

}
