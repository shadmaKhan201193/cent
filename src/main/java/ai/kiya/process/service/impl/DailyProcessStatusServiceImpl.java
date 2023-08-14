package ai.kiya.process.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import ai.kiya.process.constants.ProcessConstants;
import ai.kiya.process.dto.BranchWiseStatusReportVO;
import ai.kiya.process.dto.StatusReportVO;
import ai.kiya.process.entities.DailyProcessStatus;
import ai.kiya.process.repositories.DailyProcessingStatusRepo;
import ai.kiya.process.service.DailyProcessStatusService;
import ai.kiya.process.util.DateFormatterUtil;

@Service
public class DailyProcessStatusServiceImpl implements DailyProcessStatusService {

	@Autowired
	private DailyProcessingStatusRepo dailyProcessingStatusRepo;

	@Override
	public void markProcessForRetry(Integer tenantId, Integer processId) {
		String date = DateFormatterUtil.getDateInGivenFormat(ProcessConstants.PROCESSING_DATE_FORMAT);
		dailyProcessingStatusRepo.reprocessFailedRecords(tenantId, processId, date);
	}

	@Override
	public List<DailyProcessStatus> findByProcessingDateAndProcessingStatus(String date,
			String processStatusUnprocessed) {
		return dailyProcessingStatusRepo.findByProcessingDateAndProcessingStatus(date, processStatusUnprocessed);
	}

	@Override
	public void updateRecord(DailyProcessStatus pendingProcess) {
		// pendingProcess.setVersion(pendingProcess.getVersion()+1);
		dailyProcessingStatusRepo.save(pendingProcess);
	}

	@Override
	public List<DailyProcessStatus> findRecursiveProcesses(String date) {
		return dailyProcessingStatusRepo.findByFrequencyAndProcessingDate(ProcessConstants.FREQUENCY_RECURSIVE, date);
	}

	@Override
	public List<StatusReportVO> getProcessStatus(Integer tenantId, String processingDate) {
		List<Map<String, Object>> records = dailyProcessingStatusRepo.getProcessStatus(tenantId, processingDate);
		List<StatusReportVO> statusReportVOList = new ArrayList<>();
		if (records != null && !records.isEmpty()) {
			List<BranchWiseStatusReportVO> branchWiseStatusReportVOs = new ArrayList<>();
			StatusReportVO statusReportVO = new StatusReportVO();
			for (Map<String, Object> map : records) {
				if (statusReportVO.getProcessId() != null
						&& statusReportVO.getProcessId() != (Integer) map.get("processId")) {
					statusReportVO.setBranchWiseStatusVO(branchWiseStatusReportVOs);
					statusReportVOList.add(statusReportVO);
					statusReportVO = new StatusReportVO();
					branchWiseStatusReportVOs = new ArrayList<>();
				}
				statusReportVO.setProcessId((Integer) map.get("processId"));
				statusReportVO.setProcessName((String) map.get("processName"));
				statusReportVO.setProcessStatus(getProcessingStatusStr((String) map.get("processStatus")));
				statusReportVO.setProcessingDate(processingDate);
				statusReportVO.setProcessStartTime((Date)map.get("processStartTime"));
				statusReportVO.setProcessEndTime((Date)map.get("processEndTime"));
				BranchWiseStatusReportVO bwStatusVO = new BranchWiseStatusReportVO();
				bwStatusVO.setBranchCode((String) map.get("branchCode") == null ? ProcessConstants.STRING_NA : (String) map.get("branchCode"));
				bwStatusVO.setProcessStatus(getProcessingStatusStr((String) map.get("branchWiseStatus")));
				bwStatusVO.setErrorReason((String) map.get("errorCd"));
				bwStatusVO.setProcessStartTime((Date)map.get("branchWiseStartTime"));
				bwStatusVO.setProcessStartTime((Date)map.get("branchWiseEndTime"));
				branchWiseStatusReportVOs.add(bwStatusVO);
			}
			statusReportVO.setBranchWiseStatusVO(branchWiseStatusReportVOs);
			statusReportVOList.add(statusReportVO);
		}
		return statusReportVOList;
	}
	
	private String getProcessingStatusStr(String processingStatus) {
		String processingStatusStr = "";
		switch(processingStatus) {
			case ProcessConstants.PROCESS_STATUS_ERROR : processingStatusStr = "Errored"; break;
			case ProcessConstants.PROCESS_STATUS_TRIGGER_ERROR : processingStatusStr = "Errored"; break;
			case ProcessConstants.PROCESS_STATUS_INITIATED : processingStatusStr = "In Progress"; break;
			case ProcessConstants.PROCESS_STATUS_FAILED : processingStatusStr = "Failed"; break;
			case ProcessConstants.PROCESS_STATUS_INPROGRESS : processingStatusStr = "In Progress"; break;
			case ProcessConstants.PROCESS_STATUS_TRIGGERED : processingStatusStr = "In Progress"; break;
			case ProcessConstants.PROCESS_STATUS_SAVED : processingStatusStr = "In Progress"; break;
			case ProcessConstants.PROCESS_STATUS_SUCCESS : processingStatusStr = "Completed"; break;
			case ProcessConstants.PROCESS_STATUS_UNPROCESSED : processingStatusStr = "Yet To Start"; break;
		}
		return processingStatusStr;
	}

	@Override
	public void saveRecord(DailyProcessStatus dailyProcessStatus) {
		dailyProcessingStatusRepo.save(dailyProcessStatus);
	}

	@Override
	public List<DailyProcessStatus> findProcessForDate(Integer tenantId, Integer processId, String date) {
		return dailyProcessingStatusRepo.findByTenantIdAndProcessIdAndProcessingDate(tenantId, processId, date);
	}

	@Override
	public List<DailyProcessStatus> findExistingRecordsByGroupName(Integer tenantId, String groupName,
			String processingDate) {
		return dailyProcessingStatusRepo.findByTenantIdAndGroupNameAndProcessingDate(tenantId, groupName,
				processingDate);
	}

}
