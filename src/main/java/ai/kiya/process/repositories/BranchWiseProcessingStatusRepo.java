package ai.kiya.process.repositories;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import ai.kiya.process.entities.BranchWiseProcessingStatus;

public interface BranchWiseProcessingStatusRepo extends JpaRepository<BranchWiseProcessingStatus, UUID> {

	@Query(value = "select count(*) from D008004 with (nolock) where tenantId = ? and processId = ? and processingDate = ? and status not in ('Y')", nativeQuery = true)
	public Integer getCountOfUnprocessedOrInProgressOrInErrorRecords(Integer tenantId, Integer processId,
			String processingDate);

	// TODO - change the table name to entity name
	@Modifying(clearAutomatically = true)
	@Query(value = "UPDATE D008004 SET status = ? , modifiedDateTime = ? WHERE id = ?", nativeQuery = true)
	public Integer updateStatus(String processingStatus, Date modifiedDate, String recordId);

	@Query(value = "select * from D008004 with (nolock) where status = ? and processingDate = ? ", nativeQuery = true)
	public List<BranchWiseProcessingStatus> findByStatusAndProcessingDate(String status, String processingDate);

	@Query(value = "select * from D008004 with (nolock) where scheduled = ? and status = ? and processingDate = ? ", nativeQuery = true)
	public List<BranchWiseProcessingStatus> findByScheduledAndStatus(String scheduled, String status,
			String processingDate);

	@Lock(LockModeType.NONE)
	public BranchWiseProcessingStatus findFirstByTenantIdAndProcessIdOrderByModifiedDateTimeDesc(Integer tenantId,
			Integer processId);

	@Query(value = "select * from D008004 with (nolock) where processingDate = ? and modifiedDateTime < ? and status in ('E','D') ", nativeQuery = true)
	public List<BranchWiseProcessingStatus> findErroredOrTriggerErroredProcessesBefore10Mins(String processingDate,
			Date modifiedDate);

	@Query(value = "select * from D008004 with (nolock) where processingDate = ? and processId = ? and tenantId = ? and status in ('E', 'F') ", nativeQuery = true)
	public List<BranchWiseProcessingStatus> fetchBranchWiseProcessForRetry(String date, Integer processId,
			Integer tenantId);

	@Query(value = "select * from D008004 with (nolock) where processingDate = ? and modifiedDateTime < ? and status = 'T' ", nativeQuery = true)
	public List<BranchWiseProcessingStatus> findTriggeredProcessesBefore20Mins(String processingDate, Date minAgo);

	@Query(value = "select * from D008004 with (nolock) where status in ('F', 'E') and tenantId = ? and processId = ? and branchCode = ? and processingDate = ? ", nativeQuery = true)
	public BranchWiseProcessingStatus findErroredRecordsByBranch(Integer tenantId, Integer processId, String branchCode,
			String processingDate);

	@Modifying(clearAutomatically = true)
	@Query(value = "UPDATE D008004 SET status = ? , modifiedDateTime = ?, processBeginDateTime = ? WHERE id = ?", nativeQuery = true)
	public void updateStatus(String processingStatus, Date modifiedDate, Date initiatedDateTime, String uuid);

	@Query(value = "select * from D008004 with (nolock) where tenantId = ? and processId = ? and branchCode = ? and processingDate = ? ", nativeQuery = true)
	public BranchWiseProcessingStatus findbyTenantIdAndProcessIdAndBranchCodeAndProcessingDate(Integer tenantId,
			Integer processId, String branch, String date);

	@Modifying(clearAutomatically = true)
	@Query(value = "UPDATE D008004 SET status = ? , errorCd = ?, modifiedDateTime = ?, processBeginDateTime = ? WHERE id = ?", nativeQuery = true)
	public void updateStatusAndError(String processingStatus, String errorReason, Date modifiedDateTime, Date initiatedDateTime,
			String string);
}
