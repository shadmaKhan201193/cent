package ai.kiya.process.service;

import java.util.List;

import ai.kiya.process.dto.StatusReportVO;
import ai.kiya.process.entities.DailyProcessStatus;

public interface DailyProcessStatusService {

	void markProcessForRetry(Integer tenantId, Integer processId);

	List<DailyProcessStatus> findByProcessingDateAndProcessingStatus(String date, String processStatusUnprocessed);

	void updateRecord(DailyProcessStatus pendingProcess);

	List<DailyProcessStatus> findRecursiveProcesses(String date);

	List<StatusReportVO> getProcessStatus(Integer tenantId, String processingDate);

	void saveRecord(DailyProcessStatus dailyProcessStatus);

	List<DailyProcessStatus> findProcessForDate(Integer tenantId, Integer processId, String date);

	List<DailyProcessStatus> findExistingRecordsByGroupName(Integer tenantId, String groupName, String processingDate);

}
