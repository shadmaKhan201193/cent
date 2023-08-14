package ai.kiya.process.entities;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ProcessGroupingId implements Serializable {
	private static final long serialVersionUID = 1L;
	private Integer tenantId;
	private String groupName;
	private Integer processId;
}
