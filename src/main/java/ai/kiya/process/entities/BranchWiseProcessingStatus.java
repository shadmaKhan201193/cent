package ai.kiya.process.entities;

import java.util.Date;
import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Version;

import org.hibernate.annotations.Type;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity(name = "D008004")
public class BranchWiseProcessingStatus {
	
	@Id
	@Type(type = "uuid-char")
	private UUID id;
	@Version
	protected Integer version = 0;
	@Column(updatable = false)
	private Date createdDateTime;
	
	@Column(updatable = false)
	private String createdBy;
	private Date modifiedDateTime;
	// One of N/E/F/Y
	private String status;
	private String processingDate;
	private Integer tenantId;
	private String branchCode;
	private Integer processId;
	private String errorCd; // future use
	private Integer retryCount;
	private String scheduled;
	@Column(updatable = false)
	private Date processBeginDateTime;
	private Date processCompleteDateTime;
	private Integer successRecordCount;
	private Integer errorRecordCount;
	private Integer failedRecordCount;
	private Integer pendingRecordCount;
}
