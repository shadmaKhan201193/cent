package ai.kiya.process.entities;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;

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
@Entity(name = "D008002")
//@Table(name = "D008001")
@IdClass(ProcessGroupingId.class)
public class ProcessGrouping {
	
	@Id
	private String groupName;
	
	@Id
	private Integer processId;
	
	@Id
	private Integer tenantId;
	
	private Integer sequence;
}
