package ai.kiya.process.entities;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;

import lombok.Getter;
import lombok.Setter;
@Getter
@Setter
@Entity
@IdClass(CommunicationsMstKey.class)
public class CommunicationsMst {
	@Id
	private Integer tenantId;
	@Id
	private String notifyType;
	@Id
	private String typeId;
	private String authStatus;
	private String cc;
	private String bcc;
	private Integer isActive;
	private String mailBody;
    private Integer status;
    private String subjectLine;
}
