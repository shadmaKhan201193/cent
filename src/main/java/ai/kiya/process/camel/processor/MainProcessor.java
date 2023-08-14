package ai.kiya.process.camel.processor;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;

import javax.persistence.EntityManager;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.hibernate.transform.AliasToEntityMapResultTransformer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

import ai.kiya.process.constants.ProcessConstants;
import ai.kiya.process.dto.BaseProcessRequestObj;
import ai.kiya.process.dto.ProcessDefinitionWithDataDto;
import ai.kiya.process.service.BranchWiseProcessingStatusService;
import ai.kiya.process.util.ProcessDefinitionMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * @author ankit.dedhiya
 * 
 *
 */

@Component
@SuppressWarnings("rawtypes")
@Slf4j
public class MainProcessor implements Processor {

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private JmsTemplate jmsTemplate;

	@Autowired
	private BranchWiseProcessingStatusService branchWiseProcessingStatusService;

	@SuppressWarnings({ "unchecked" })
	@Override
	// don't do @Transactional because there is API call and it can not be reverted
	public void process(Exchange exchange) throws Exception {

		if (Optional.of(exchange).isPresent()) {

			// get the request body and convert into the request object to be used
			BaseProcessRequestObj processObj = (BaseProcessRequestObj) exchange.getIn().getBody();
			ProcessDefinitionWithDataDto dto = ProcessDefinitionMapper.map(processObj.getProcessDefinition());
			//String nextQueue = getNextQueue(exchange, dto);
			if(dto != null && dto.getTriggerTable() != null) {
				Session session = entityManager.unwrap(Session.class);
				ScrollableResults requests = getSelectResult(processObj, session);
				SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy");
				sdf.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
				try {
					if(!requests.next()) {
						// no records to process; mark the process as completed
						log.info("No records to process for process {}, branch {}", processObj.getProcessId(), processObj.getBranchCode());
						updateBranchWiseRecordStatus(processObj, ProcessConstants.PROCESS_STATUS_SUCCESS);
					} else {
						do {
							Map<String, Object> request = (Map<String, Object>) requests.get(0);
							Map<String, Object> extraParams = new HashMap<>();
							if(request != null) {
								for (Map.Entry<String, Object> entry : request.entrySet()) {
									String key = entry.getKey();
									Object val = entry.getValue();
									if(val != null && val instanceof Timestamp) {
										Date date = new Date(((Timestamp)val).getTime());
										extraParams.put(key+"Str", sdf.format(date));
									}
									
								}
							}
							if(!extraParams.isEmpty()) {
								request.putAll(extraParams);
							}
							dto.setRequest(request);
							jmsTemplate.convertAndSend(dto.getTriggerQueue(), dto);
						} while (requests.next());
					}
				} catch (Exception e) {
					// TODO: need to find what exceptions can come and deal with them appropriately
					e.printStackTrace();
				} finally {
					requests.close();
				}
			} else {
				jmsTemplate.convertAndSend(dto.getTriggerQueue(), dto);
			}
			updateBranchWiseRecordStatus(processObj, ProcessConstants.PROCESS_STATUS_INITIATED);
		}
	}

	@SuppressWarnings({ "deprecation" })
	private ScrollableResults getSelectResult(BaseProcessRequestObj processObj, Session session) {
		Query querySelectRecords = null;
		if(processObj.getBranchCode() != null) {
			querySelectRecords = session.createNativeQuery("SELECT * FROM "
					+ processObj.getProcessDefinition().getTriggerTable() + " WHERE tenantId = ? AND branchCode = ? AND processingStatus = 'N'");
			querySelectRecords.setParameter(1, processObj.getTenantId());
			querySelectRecords.setParameter(2, processObj.getBranchCode());
		} else {
			querySelectRecords = session.createNativeQuery("SELECT * FROM "
					+ processObj.getProcessDefinition().getTriggerTable() + " WHERE tenantId = ? AND processingStatus = 'N' ");
			querySelectRecords.setParameter(1, processObj.getTenantId());
		}
		querySelectRecords.setReadOnly(true);
		querySelectRecords.setFetchSize(1000);
		querySelectRecords.setResultTransformer(AliasToEntityMapResultTransformer.INSTANCE);
		ScrollableResults results = querySelectRecords.scroll(ScrollMode.FORWARD_ONLY);
		return results;
	}

	private void updateBranchWiseRecordStatus(BaseProcessRequestObj processObj, String processingStatus) {
		branchWiseProcessingStatusService.updateStatus(processingStatus, processObj.getProcessingUUID());
	}

}
