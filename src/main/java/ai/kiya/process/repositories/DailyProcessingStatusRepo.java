package ai.kiya.process.repositories;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import ai.kiya.process.entities.DailyProcessStatus;

public interface DailyProcessingStatusRepo extends JpaRepository<DailyProcessStatus, UUID> {
	public List<DailyProcessStatus> findByTenantIdAndProcessIdAndProcessingDate(Integer tenantId, Integer processId,
			String processingDate);

	@Lock(LockModeType.NONE)
	public List<DailyProcessStatus> findByProcessingDateAndProcessingStatus(String date,
			String processStatusUnprocessed);

	@Lock(LockModeType.NONE)
	public List<DailyProcessStatus> findByTenantIdAndGroupNameAndProcessingDate(Integer tenantId, String groupName,
			String processingDate);

	// TODO - change the table name to entity name
	@Query(value = "UPDATE D008003 SET processingStatus = 'R' WHERE tenantId = ? AND processId = ? AND processingDate = ?", nativeQuery = true)
	public void reprocessFailedRecords(Integer tenantId, Integer processId, String processingDate);

	@Lock(LockModeType.NONE)
	public List<DailyProcessStatus> findByFrequencyAndProcessingDate(String frequencyRecursive, String date);

	@Query(value = "select p.processName as processName,p.processId as processId,b.branchCode as branchCode, b.errorCd, d.processingStatus as processStatus,b.status as branchWiseStatus, d.processBeginDateTime as processStartTime, d.processCompleteDateTime as processEndTime, b.processBeginDateTime as branchWiseStartTime, b.processCompleteDateTime as branchWiseEndTime  from D008003 d WITH (NOLOCK) inner join D008004 b WITH (NOLOCK) on b.tenantId = d.tenantId and b.processId = d.processId and b.processingDate = d.processingDate inner join D008001 p WITH (NOLOCK) on b.processId = p.processId and b.tenantId = p.tenantId where b.tenantId = ? and d.processingDate = ? order by p.processId,CAST(b.branchCode AS INT)", nativeQuery = true)
	public List<Map<String, Object>> getProcessStatus(Integer tenantId, String processingDate);
}
