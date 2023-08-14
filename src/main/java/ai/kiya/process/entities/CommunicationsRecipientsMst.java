package ai.kiya.process.entities;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@IdClass(CommunicationsRecipientsMstKey.class)
public class CommunicationsRecipientsMst {
	@Id
	private Integer tenantId;
	@Id
	private String notifyType;
	@Id
	private String typeId;
	@Id
	private Long srNo;
	private String additionalEmailId;
	private String authStatus;
	private Integer isActive;
	private String time;
	private String userIds;
    private Integer userRole;
}
