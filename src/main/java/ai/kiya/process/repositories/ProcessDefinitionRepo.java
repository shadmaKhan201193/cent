package ai.kiya.process.repositories;

import java.util.Date;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import ai.kiya.process.entities.ProcessDefinition;
import ai.kiya.process.entities.ProcessDefinitionId;

public interface ProcessDefinitionRepo extends JpaRepository<ProcessDefinition, ProcessDefinitionId> {

	public List<ProcessDefinition> findByScheduledAndFrequency(Boolean scheduled, String frequency);

	public ProcessDefinition findByTenantIdAndProcessIdAndScheduledAndFrequency(Integer tenantId, Integer processId,
			String scheduled, String frequency);

	public ProcessDefinition findByTriggerQueue(String queueName);

	public List<ProcessDefinition> findByLastModifiedDateBetween(Date now, Date min10Ago);

	@Query(value = "SELECT * FROM D008001 with (nolock) WHERE tenantId = ? and processId = ? and scheduled = ? and authStatus = 'A' and isActive = 1", nativeQuery = true)
	public ProcessDefinition findByScheduledProcesses(Integer tenantId, Integer processId,
			String processTypeScheduled);

	@Query(value = "SELECT * FROM D008001 with (nolock) WHERE authStatus = 'A' and isActive = 1", nativeQuery = true)
	public List<ProcessDefinition> findAllActiveProcesses();
}
