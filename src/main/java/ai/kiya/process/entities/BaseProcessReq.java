package ai.kiya.process.entities;

import java.util.Date;
import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.MappedSuperclass;

import org.hibernate.annotations.Type;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@MappedSuperclass
public class BaseProcessReq {
	@Column(nullable = false)
	@Type(type = "uuid-char")
	private UUID processingUUID;
	@Column(nullable = false)
	private String processingDate;
	@Column(nullable = true)
	private String processingStatus;
	private Integer retryCount;
	@Column(nullable = false, updatable = false)
	private String createdBy;
	@Column(nullable = false, updatable = false)
	private Date createdDateTime;
}
