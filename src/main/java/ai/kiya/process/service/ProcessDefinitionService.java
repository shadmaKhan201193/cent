package ai.kiya.process.service;

import java.util.List;

import ai.kiya.process.entities.ProcessDefinition;

public interface ProcessDefinitionService {
	public ProcessDefinition getProcessDefinition(Integer tenantId, Integer processId);

	boolean isProcessToBePaused(String queueName);

	public List<ProcessDefinition> getAllActiveProcesses();

	public ProcessDefinition findProcessForDailySchedule(Integer tenantId, Integer processId,
			String processTypeScheduled, String frequency);

	public List<ProcessDefinition> findProcessesModifiedInLast10Min();

	Boolean isProcessingDateMonthEnd(String processingDate, Integer tenantId, String branchCd);

	Boolean isProcessingDateQuarterEnd(String processingDate, Integer tenantId, String branchCd);

	Boolean isProcessingDateYearEnd(String processingDate, Integer tenantId, String branchCd);

	ProcessDefinition findProcessForSchedule(Integer tenantId, Integer processId, String processTypeScheduled);
}
