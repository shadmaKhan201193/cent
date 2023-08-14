package ai.kiya.process.schedulers;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;

import org.hibernate.Session;
import org.hibernate.query.Query;
import org.hibernate.transform.AliasToEntityMapResultTransformer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import ai.kiya.process.constants.ProcessConstants;
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
public class StatusAggregatorScheduler {

	@Autowired
	private DailyProcessStatusService dailyProcessStatusService;

	@Autowired
	private BranchWiseProcessingStatusService branchWiseProcessingStatusService;

	@Autowired
	private ProcessDefinitionService processDefinitionService;

	@Autowired
	private EntityManager entityManager;
	
	@Autowired
	private NotificationHelperService notificationHelperService;

	@Scheduled(cron = "0 */3 * * * ?", zone = "Asia/Kolkata")
	public void aggregateStatusForBranch() {
		String date = DateFormatterUtil.getDateInGivenFormat(ProcessConstants.PROCESSING_DATE_FORMAT);
		List<DailyProcessStatus> pendingProcesses = dailyProcessStatusService
				.findByProcessingDateAndProcessingStatus(date, ProcessConstants.PROCESS_STATUS_INPROGRESS);

		if (pendingProcesses != null && !pendingProcesses.isEmpty()) {
			for (DailyProcessStatus dailyProcessStatus : pendingProcesses) {
				// TODO bifurcate Y & F to determine if process should be aggregated as Y or F
				// (used for retry)
				if (branchWiseProcessingStatusService.isProcessCompletedForAllBranches(dailyProcessStatus.getTenantId(),
						dailyProcessStatus.getProcessId(), date)) {
					dailyProcessStatus.setProcessingStatus(ProcessConstants.PROCESS_STATUS_SUCCESS);
					dailyProcessStatus.setProcessCompleteDateTime(new Date());
					dailyProcessStatusService.updateRecord(dailyProcessStatus);
					log.info("Process {} completed for tenant {} for date {}", dailyProcessStatus.getProcessId(),
							dailyProcessStatus.getTenantId(), dailyProcessStatus.getProcessingDate());
					notificationHelperService.sendProcessNotification(dailyProcessStatus.getProcessId(),
							dailyProcessStatus.getTenantId(), ProcessConstants.PROCESS_COMPLETED_EVENT, null);
				} else {
					log.info("Process {} is not yet completed across all branches.", dailyProcessStatus.getProcessId());
				}
			}
		}
	}

	@Scheduled(cron = "30 */1 * * * ?", zone = "Asia/Kolkata")
	public void aggregateStatusForAccountProcessOverBranch() {
		String date = DateFormatterUtil.getDateInGivenFormat(ProcessConstants.PROCESSING_DATE_FORMAT);
		List<BranchWiseProcessingStatus> pendingProcesses = branchWiseProcessingStatusService
				.findByStatusAndProcessingDate(ProcessConstants.PROCESS_STATUS_INITIATED, date);
		pendingProcesses.addAll(branchWiseProcessingStatusService.findByStatusAndProcessingDate(ProcessConstants.PROCESS_STATUS_ERROR, date));
		if (pendingProcesses != null && !pendingProcesses.isEmpty()) {
			Session session = entityManager.unwrap(Session.class);
			for (BranchWiseProcessingStatus branchWiseProcessStatus : pendingProcesses) {
				ProcessDefinition process = processDefinitionService.getProcessDefinition(
						branchWiseProcessStatus.getTenantId(), branchWiseProcessStatus.getProcessId());
				if (process.getTriggerTable() != null && StringUtils.hasText(process.getTriggerTable())
						&& !process.getTriggerTable().equals(ProcessConstants.STRING_NA)
						&& process.getBranchWise() != null
						&& process.getBranchWise().equals(ProcessConstants.PROCESS_BRANCH_WISE)) {
					// TODO - make integer vs biginteger database agnostic
					Map<String, Integer> countsSummary = getPendingCount(process.getTriggerTable(), process.getTenantId(),
							branchWiseProcessStatus.getBranchCode(), date, session);
					
					if (!countsSummary.containsKey(ProcessConstants.PROCESS_STATUS_UNPROCESSED)
							&& !countsSummary.containsKey(ProcessConstants.PROCESS_STATUS_INPROGRESS)) {
						if (countsSummary.containsKey(ProcessConstants.PROCESS_STATUS_ERROR)) {
							if(ProcessConstants.PROCESS_STATUS_ERROR.equals(branchWiseProcessStatus.getStatus())) {
								if(countsSummary.get(ProcessConstants.PROCESS_STATUS_UNPROCESSED) == branchWiseProcessStatus.getPendingRecordCount() &&
										countsSummary.get(ProcessConstants.PROCESS_STATUS_ERROR) == branchWiseProcessStatus.getErrorRecordCount() &&
										countsSummary.get(ProcessConstants.PROCESS_STATUS_FAILED) == branchWiseProcessStatus.getFailedRecordCount() &&
										countsSummary.get(ProcessConstants.PROCESS_STATUS_SUCCESS) == branchWiseProcessStatus.getSuccessRecordCount()) {
									// no change in status hence not firing update query
									continue;
								}
							}
							branchWiseProcessStatus.setPendingRecordCount(countsSummary.get(ProcessConstants.PROCESS_STATUS_UNPROCESSED));
							branchWiseProcessStatus.setErrorRecordCount(countsSummary.get(ProcessConstants.PROCESS_STATUS_ERROR));
							branchWiseProcessStatus.setFailedRecordCount(countsSummary.get(ProcessConstants.PROCESS_STATUS_FAILED));
							branchWiseProcessStatus.setSuccessRecordCount(countsSummary.get(ProcessConstants.PROCESS_STATUS_SUCCESS));
							rollUpDetailsFromTriggerToBranchWise(session, branchWiseProcessStatus, process, countsSummary);
							branchWiseProcessStatus.setStatus(ProcessConstants.PROCESS_STATUS_ERROR);
						} else if (countsSummary.containsKey(ProcessConstants.PROCESS_STATUS_FAILED)) {
							rollUpDetailsFromTriggerToBranchWise(session, branchWiseProcessStatus, process, countsSummary);
							if(ProcessConstants.PROCESS_STATUS_FAILED.equals(branchWiseProcessStatus.getStatus())) {
								// no change in status hence not firing update query
								continue;
							}
							branchWiseProcessStatus.setStatus(ProcessConstants.PROCESS_STATUS_FAILED);
						} else {
							branchWiseProcessStatus.setStatus(ProcessConstants.PROCESS_STATUS_SUCCESS);
							branchWiseProcessStatus.setProcessCompleteDateTime(new Date());
						}
						branchWiseProcessingStatusService.save(branchWiseProcessStatus);
					}
				}
				}
		}
	}

	private void rollUpDetailsFromTriggerToBranchWise(Session session,
			BranchWiseProcessingStatus branchWiseProcessStatus, ProcessDefinition process,
			Map<String, Integer> countsSummary) {
		if(countsSummary != null) {
			Integer totalCounts = countsSummary.entrySet().stream().map(e -> e.getValue())
					.collect(Collectors.summingInt(Integer::intValue));
			if(totalCounts == 1) {
				Map<String, Object> record = getSingularTriggerRecord(branchWiseProcessStatus, process, session);
				branchWiseProcessStatus.setErrorCd((String) record.get("message"));
			}
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked", "deprecation" })
	private Map<String, Object> getSingularTriggerRecord(BranchWiseProcessingStatus branchWiseProcessStatus,
			ProcessDefinition process, Session session) {
		Query querySelectRecords = null;
		if (branchWiseProcessStatus.getBranchCode() != null) {
			querySelectRecords = session.createNativeQuery("select * from " + process.getTriggerTable()
					+ " with (nolock) WHERE tenantId = ? AND branchCode = ? AND processingDate = ? ");
			querySelectRecords.setParameter(1, branchWiseProcessStatus.getTenantId());
			querySelectRecords.setParameter(2, branchWiseProcessStatus.getBranchCode());
			querySelectRecords.setParameter(3, branchWiseProcessStatus.getProcessingDate());
		} else {
			querySelectRecords = session.createNativeQuery("select * from " + process.getTriggerTable()
					+ " with (nolock) WHERE tenantId = ? AND processingDate = ? ");
			querySelectRecords.setParameter(1, branchWiseProcessStatus.getTenantId());
			querySelectRecords.setParameter(2, branchWiseProcessStatus.getProcessingDate());
		}
		querySelectRecords.setResultTransformer(AliasToEntityMapResultTransformer.INSTANCE);
		Map<String, Object> record = (Map<String, Object>) querySelectRecords.getSingleResult();
		return record;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	// TODO - send this method to some service class since this involves DB stuff
	private Map<String, Integer> getPendingCount(String table, Integer tenantId, String branchCode,
			String processingDate, Session session) {
		Query querySelectRecords = null;
		if (branchCode != null) {
			querySelectRecords = session.createNativeQuery("select processingStatus, count(*) as counts from " + table
					+ " with (nolock) WHERE tenantId = ? AND branchCode = ? AND processingDate = ? group by processingStatus ");
			querySelectRecords.setParameter(1, tenantId);
			querySelectRecords.setParameter(2, branchCode);
			querySelectRecords.setParameter(3, processingDate);
		} else {
			querySelectRecords = session.createNativeQuery("select processingStatus, count(*) as counts from " + table
					+ " with (nolock) WHERE tenantId = ? AND processingDate = ? group by processingStatus ");
			querySelectRecords.setParameter(1, tenantId);
			querySelectRecords.setParameter(2, processingDate);
		}
		List<Object[]> result = (List<Object[]>) querySelectRecords.getResultList();
		Map<String, Integer> counts = new HashMap<>();
		result.stream().forEach(p -> {
			counts.put((String) p[0], (Integer) p[1]);
		});
		return counts;
	}
}
