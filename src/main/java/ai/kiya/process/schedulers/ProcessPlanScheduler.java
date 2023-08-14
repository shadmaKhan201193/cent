package ai.kiya.process.schedulers;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import ai.kiya.process.constants.ProcessConstants;
import ai.kiya.process.entities.CommunicationsMst;
import ai.kiya.process.entities.CommunicationsRecipientsMst;
import ai.kiya.process.entities.DailyProcessStatus;
import ai.kiya.process.entities.ProcessDefinition;
import ai.kiya.process.entities.ProcessGrouping;
import ai.kiya.process.repositories.CommunicationsMstRepo;
import ai.kiya.process.repositories.CommunicationsRecipientsMstRepo;
import ai.kiya.process.service.DailyProcessStatusService;
import ai.kiya.process.service.ProcessDefinitionService;
import ai.kiya.process.service.ProcessGroupingService;
import ai.kiya.process.util.DateFormatterUtil;
import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class ProcessPlanScheduler {

	@Autowired
	private ProcessDefinitionService processDefinitionService;

	@Autowired
	private DailyProcessStatusService dailyProcessStatusService;

	@Autowired
	private ProcessGroupingService processGroupingService;
	
	@Autowired
	private CommunicationsMstRepo communicationsMstRepo;
	
	@Autowired
	private CommunicationsRecipientsMstRepo communicationsRecipientsMstRepo;
	
	@Value("${report.email.process.id}")
	private Integer reportEmailProcessId;
	
	// TODO - implement ShedLock here for multi-node environment
	/**
	 * This scheduler is meant to create initial entries for what needs to be
	 * processed today. There are few glaring issues here: 1. How to ensure that we
	 * match the entries are not delayed due to timezone ? - probably we can make
	 * sure that we do the entry for next 48 hours OR run the scheduler every 12
	 * hours
	 */
	@Scheduled(cron = "20 * * * * ?", zone = "Asia/Kolkata") // for production -- (cron = "0 0 */12 * * ?")
	public void scheduleProcessesDailyAndRecursive() {
		List<ProcessGrouping> groups = processGroupingService.getAllProcessGroups();
		for (ProcessGrouping processGrouping : groups) {
			ProcessDefinition process = processDefinitionService.findProcessForSchedule(processGrouping.getTenantId(),
					processGrouping.getProcessId(), ProcessConstants.PROCESS_TYPE_SCHEDULED);
			String date = DateFormatterUtil.getDateInGivenFormat(ProcessConstants.PROCESSING_DATE_FORMAT);
			if(process == null) {
				process = processDefinitionService.findProcessForDailySchedule(processGrouping.getTenantId(),
						processGrouping.getProcessId(), ProcessConstants.PROCESS_TYPE_SCHEDULED, ProcessConstants.FREQUENCY_RECURSIVE);
			}
			if(process != null) {
				List<DailyProcessStatus> dailyProcesses = dailyProcessStatusService
						.findProcessForDate(process.getTenantId(), process.getProcessId(), date);
				if (dailyProcesses == null || dailyProcesses.isEmpty()) {
					DailyProcessStatus dailyProcessStatus = new DailyProcessStatus(UUID.randomUUID(), process.getTenantId(),
							process.getProcessId(), process.getFrequency(), date, ProcessConstants.PROCESS_STATUS_UNPROCESSED,
							process.getExecutionTime(), processGrouping.getGroupName());
					dailyProcessStatusService.saveRecord(dailyProcessStatus);
					log.info("Scheduled daily process {} for tenant {} for the processing date {}", dailyProcessStatus.getProcessId(),
							dailyProcessStatus.getTenantId(), dailyProcessStatus.getProcessingDate());
				}
			}
		}
	}
	
	/**
	 * This is like a 12 hours scheduler which will be triggered once and will
	 * insert all records for reports to be emailed with available details in
	 * CommunicationsMst & CommunicationsRecipientsMst tables. Actual execution will
	 * be done once the report times are reached. This will be as per the normal
	 * Group to Branch wise transition except that there are no branches for email reports.
	 */
	@Scheduled(cron = "30 * * * * ?", zone = "Asia/Kolkata") // for production -- (cron = "0 0 */12 * * ?")
	public void scheduleForReports() {
		List<CommunicationsMst> msts = communicationsMstRepo.getAllReportRecords(ProcessConstants.NOTIFY_TYPE_REPORT);
		for (CommunicationsMst communicationsMst : msts) {
			List<CommunicationsRecipientsMst> recipients = communicationsRecipientsMstRepo.getAllRecordsForType(
					communicationsMst.getTenantId(), communicationsMst.getNotifyType(), communicationsMst.getTypeId());
			String processingDate = DateFormatterUtil.getDateInGivenFormat(ProcessConstants.PROCESSING_DATE_FORMAT);
			for (CommunicationsRecipientsMst recipient : recipients) {
				String groupName = communicationsMst.getTypeId() + "_" + recipient.getSrNo();
				List<DailyProcessStatus> existingRecords = dailyProcessStatusService
						.findExistingRecordsByGroupName(recipient.getTenantId(), groupName, processingDate);
				if(existingRecords == null || existingRecords.isEmpty()) {
					DailyProcessStatus dailyProcessStatus = new DailyProcessStatus();
					dailyProcessStatus.setTenantId(communicationsMst.getTenantId());
					dailyProcessStatus.setProcessId(reportEmailProcessId);
					dailyProcessStatus.setProcessingDate(processingDate);
					dailyProcessStatus.setRecordId(UUID.randomUUID());
					dailyProcessStatus.setFrequency(ProcessConstants.FREQUENCY_DAILY);
					dailyProcessStatus.setGroupName(groupName);
					dailyProcessStatus.setExecutionTime(recipient.getTime());
					dailyProcessStatus.setProcessingStatus(ProcessConstants.PROCESS_STATUS_UNPROCESSED);
					dailyProcessStatusService.saveRecord(dailyProcessStatus);
				}
			}
		}
	}
}
