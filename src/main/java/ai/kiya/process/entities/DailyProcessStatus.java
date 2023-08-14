package ai.kiya.process.entities;

import java.util.Date;
import java.util.UUID;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Version;

import org.hibernate.annotations.Type;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
@Entity(name = "D008003")
//@Table(name = "D008003")
public class DailyProcessStatus {
	public DailyProcessStatus(UUID recordId, Integer tenantId, Integer processId, String frequency,
			String processingDate, String processingStatus, String executionTime, String groupName) {
		super();
		this.recordId = recordId;
		this.tenantId = tenantId;
		this.processId = processId;
		this.frequency = frequency;
		this.processingDate = processingDate;
		this.processingStatus = processingStatus;
		this.executionTime = executionTime;
		this.groupName = groupName;
	}
	@Id
	@Type(type = "uuid-char")
	private UUID recordId;
	@Version
	protected Integer version = 0;
	private Integer tenantId;
	//private String branchCode; //only needed if the processes are not uniform across branches (it will create issues with the size of the table)
	private Integer processId;
	//private String stage; //manage multiplication of stages at the trigger level if needed
	private String frequency;
	private String processingDate;
	private String processingStatus;
	private String executionTime;
	private String groupName;
	private Date processBeginDateTime;
	private Date processCompleteDateTime;
	
}
