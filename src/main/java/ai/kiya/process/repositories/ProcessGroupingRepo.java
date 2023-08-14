package ai.kiya.process.repositories;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import ai.kiya.process.entities.ProcessGrouping;
import ai.kiya.process.entities.ProcessGroupingId;

public interface ProcessGroupingRepo extends JpaRepository<ProcessGrouping, ProcessGroupingId> {

	List<ProcessGrouping> findByGroupNameAndTenantIdAndProcessId(String groupName, Integer tenantId, Integer processId);

	List<ProcessGrouping> findByGroupNameAndTenantId(String groupName, Integer tenantId);
	
	@Query(value = "SELECT * FROM D008002 where authStatus = 'A' and isActive = 1 order by groupName,sequence ", nativeQuery = true)
	List<ProcessGrouping> findAllProcesses();
	
	@Query(value = "select branchCode from D001003 WHERE isActive = 1 and authStatus = 'A' and tenantId = ?", nativeQuery = true)
	List<String> getAllBranches(Integer tenantId);
	
	@Query(value = "select count(*) from ? where tenantId = ? and branchCode = ? and status in ('X','E','N') ", nativeQuery = true)
	public Integer getCountOfPendingRecords(String tableName, Integer tenantId, String branchCode);
	
	@Query(value = "select loginId,emailId from D002001 with (nolock) where tenantId = ? and roleCode = ? and isActive = 1 and authStatus = 'A' ", nativeQuery = true)
	List<Map<String, String>> getUsersForRole(Integer tenantId, Integer roleCode);
	
	@Query(value = "select emailId from D002001 with (nolock) where tenantId = ? and loginId = ? and isActive = 1 and authStatus = 'A' ", nativeQuery = true)
	String getEmailForUser(Integer tenantId, String userId);
	
	@Query(value = "select bod from D001003 with (nolock) where tenantId = :tenantId and authStatus = 'A' and isActive = 1 and branchCode = (select userBaseBranchCode from D002001 with (nolock) where loginId = :userId and authStatus = 'A' and isActive = 1 and tenantId = :tenantId )", nativeQuery = true)
	public Date getOperationalDateForUser(Integer tenantId, String userId);

	@Query(value = "select userFName from D002001 with (nolock) where tenantId = ? and loginId = ? and isActive = 1 and authStatus = 'A' ", nativeQuery = true)
	String getNameForUser(Integer tenantId, String userId);
	
	@Query(value = "select userBaseBranchCode from D002001 with (nolock) where tenantId = ? and loginId = ? and isActive = 1 and authStatus = 'A' ", nativeQuery = true)
	String getBranchForUser(String userId);

}
