package ai.kiya.process.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import ai.kiya.process.entities.CommunicationsRecipientsMst;
import ai.kiya.process.entities.CommunicationsRecipientsMstKey;

public interface CommunicationsRecipientsMstRepo extends JpaRepository<CommunicationsRecipientsMst, CommunicationsRecipientsMstKey> {

	@Query(value = "select * from D008105 with (nolock) where tenantId = ? and notifyType = ? and typeId = ? and authStatus = 'A' and isActive = 1 ", nativeQuery = true)
	List<CommunicationsRecipientsMst> getAllRecordsForType(Integer tenantId, String notifyType, String typeId);

}
