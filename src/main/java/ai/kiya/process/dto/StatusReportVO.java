package ai.kiya.process.dto;

import java.util.Date;
import java.util.List;

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
public class StatusReportVO {
	private String processName;
	private Integer processId;
	private String processStatus;
	private String processingDate;
	private Date processStartTime;
	private Date processEndTime;
	private List<BranchWiseStatusReportVO> branchWiseStatusVO;
}
