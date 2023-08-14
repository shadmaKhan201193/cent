package ai.kiya.process.util;

import ai.kiya.process.dto.ProcessDefinitionWithDataDto;
import ai.kiya.process.entities.ProcessDefinition;

public class ProcessDefinitionMapper {
	public static ProcessDefinitionWithDataDto map(ProcessDefinition processDefinition) {
		ProcessDefinitionWithDataDto dto = new ProcessDefinitionWithDataDto();
		dto.setBranchWise(processDefinition.getBranchWise());
		dto.setExecutionTime(processDefinition.getExecutionTime());
		dto.setFrequency(processDefinition.getFrequency());
		dto.setNotificationType(processDefinition.getNotificationType());
		dto.setPaused(processDefinition.getPaused());
		dto.setProcessApi(processDefinition.getProcessApi());
		dto.setProcessId(processDefinition.getProcessId());
		dto.setProcessName(processDefinition.getProcessName());
		dto.setProcessStage(processDefinition.getProcessStage());
		dto.setScheduled(processDefinition.getScheduled());
		dto.setTenantId(processDefinition.getTenantId());
		dto.setTriggerApi(processDefinition.getTriggerApi());
		dto.setTriggerQueue(processDefinition.getTriggerQueue());
		dto.setTriggerTable(processDefinition.getTriggerTable());
		//dto.setSpExecutionYN(processDefinition.getSpExecutionYN());
		dto.setSpName(processDefinition.getSpName());
		dto.setInputParam1(processDefinition.getInputParam1());
		dto.setInputParam2(processDefinition.getInputParam2());
		dto.setInputParam3(processDefinition.getInputParam3());
		dto.setInputParam4(processDefinition.getInputParam4());
		dto.setInputParam5(processDefinition.getInputParam5());;
		return dto;
	}
}
