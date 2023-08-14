package ai.kiya.process.entities;

import java.io.Serializable;
import java.util.Date;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@ToString
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity(name = "D008001")
@IdClass(ProcessDefinitionId.class)
public class ProcessDefinition implements Serializable {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	@Id
	private Integer tenantId;
	@Id
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
	private Date lastModifiedDate;
	private Date lastModifiedTime;
	private Integer noOfThreads;
	private Integer spExecutionYN;
	private String spName;
	private String inputParam1;
	private String inputParam2;
	private String inputParam3;
	private String inputParam4;
	private String inputParam5;

}
