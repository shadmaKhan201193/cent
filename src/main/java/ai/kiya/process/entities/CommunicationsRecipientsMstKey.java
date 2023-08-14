package ai.kiya.process.entities;

import java.io.Serializable;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode
public class CommunicationsRecipientsMstKey implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private Integer tenantId;
	private String notifyType;
	private String typeId;
	private Long srNo;
}
