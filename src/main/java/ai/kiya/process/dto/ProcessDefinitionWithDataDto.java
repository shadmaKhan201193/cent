package ai.kiya.process.dto;

import java.io.Serializable;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class ProcessDefinitionWithDataDto implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private Integer tenantId;
	private Integer processId;
	private String processName;
	private String frequency;
	private String processStage;
	private String scheduled;
	private String executionTime;
	private String notificationType;
	private String triggerQueue;
	private String triggerTable;
	private String triggerApi;
	private String processApi;
	private String branchWise;
	private Boolean paused;
	private Map<String, Object> request;
	private Integer spExecutionYN;
	private String spName;
	private String inputParam1;
	private String inputParam2;
	private String inputParam3;
	private String inputParam4;
	private String inputParam5;
}
