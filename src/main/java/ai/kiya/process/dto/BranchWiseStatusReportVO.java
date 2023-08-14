package ai.kiya.process.dto;

import java.util.Date;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class BranchWiseStatusReportVO {
	private String branchCode;
	private String processStatus;
	private Boolean branchWise;
	private String errorReason;
	private Integer successRecordCount;
	private Integer errorRecordCount;
	private Integer failedRecordCount;
	private Integer pendingRecordCount;
	private Date processStartTime;
	private Date processEndTime;
}
