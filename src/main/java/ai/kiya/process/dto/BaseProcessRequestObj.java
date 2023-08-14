package ai.kiya.process.dto;

import java.io.Serializable;
import java.util.UUID;

import ai.kiya.process.entities.ProcessDefinition;
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
public class BaseProcessRequestObj implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private UUID processingUUID;
	private Integer tenantId;
	private String branchCode;
	//for multi-branch request
	private String branchCodes;
	private String groupName;
	private Integer processId;
	private String createdBy;
	private String processingDate;
	private ProcessDefinition processDefinition;
	private String spName;
	private String inputParam1;
	private String inputParam2;
	private String inputParam3;
	private String inputParam4;
	private String inputParam5;
}
