package ai.kiya.process.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import ai.kiya.process.entities.CommunicationsMst;
import ai.kiya.process.entities.CommunicationsMstKey;

public interface CommunicationsMstRepo extends JpaRepository<CommunicationsMst, CommunicationsMstKey> {

	@Query(value = "select * from D008005 with (nolock) where notifyType = ? and authStatus = 'A' and isActive = 1 ",  nativeQuery = true)
	List<CommunicationsMst> getAllReportRecords(String notifyType);
	
	@Query(value = "select * from D008005 with (nolock) where notifyType = ? and tenantId = ? and typeId = ? and authStatus = 'A' and isActive = 1 ",  nativeQuery = true)
	CommunicationsMst getRecordForProcess(String notifyType, Integer tenantId, Integer processId);

}
