package ai.kiya.process.schedulers;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;

import org.apache.camel.CamelContext;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.hibernate.transform.AliasToEntityMapResultTransformer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.JmsException;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import ai.kiya.process.camel.processor.IndividualTaskProcessor;
import ai.kiya.process.camel.route.AllPauseResumeRoutePolicy;
import ai.kiya.process.camel.route.ProcessRouteBuilder;
import ai.kiya.process.constants.ProcessConstants;
import ai.kiya.process.dto.BaseProcessRequestObj;
import ai.kiya.process.dto.ProcessDefinitionWithDataDto;
import ai.kiya.process.entities.BranchWiseProcessingStatus;
import ai.kiya.process.entities.ProcessDefinition;
import ai.kiya.process.service.BranchWiseProcessingStatusService;
import ai.kiya.process.service.ProcessDefinitionService;
import ai.kiya.process.util.DateFormatterUtil;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@SuppressWarnings({ "deprecation", "rawtypes" })
public class MiscSchedulers {

	@Autowired
	private ProcessDefinitionService processDefinitionService;

	@Autowired
	private CamelContext camelContext;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private JmsTemplate jmsTemplate;

	@Autowired
	private IndividualTaskProcessor individualTaskProcessor;

	@Autowired
	private AllPauseResumeRoutePolicy pauseResumePolicy;

	@Autowired
	private BranchWiseProcessingStatusService branchWiseProcessingStatusService;

	@Value("${retry.after.min}")
	private Integer retryAfterMin;

	@Value("${max.retry.count}")
	private Integer maxRetryCount;
	
	@Value("${generic.trigger.queue}")
	private String genericTriggerQueue;

	@Scheduled(cron = "0 */3 * * * ?", zone = "Asia/Kolkata")
	public void queueConsumersRefresh() throws Exception {
		List<ProcessDefinition> processes = processDefinitionService.findProcessesModifiedInLast10Min();
		if (processes != null) {
			for (ProcessDefinition processDefinition : processes) {
				if (processDefinition == null) {
					continue;
				}
				if (!ObjectUtils.isEmpty(processDefinition.getTriggerQueue())) {
					log.info("Adding listener for process {}", processDefinition.getProcessId());
					// TODO - first check if a consumer already exists on this queue
					camelContext.addRoutes(new ProcessRouteBuilder("jms:" + processDefinition.getTriggerQueue(), 10,
							individualTaskProcessor, pauseResumePolicy));
				}
			}
		}
	}

	@Scheduled(cron = "0 */5 * * * ?", zone = "Asia/Kolkata")
	@Transactional
	public void reAttachTriggeredRecords() throws JmsException, JsonProcessingException {

		String date = DateFormatterUtil.getDateInGivenFormat(ProcessConstants.PROCESSING_DATE_FORMAT);
		Date now = new Date();
		Date minAgo = null;
		Calendar cal = Calendar.getInstance();
		// remove next line if you're always using the current time.
		cal.setTime(now);
		cal.add(Calendar.MINUTE, -retryAfterMin * 2);
		minAgo = cal.getTime();

		List<BranchWiseProcessingStatus> triggeredRecords = branchWiseProcessingStatusService
				.getTriggeredProcesses(date, minAgo);
		initiateRetry(date, minAgo, triggeredRecords);
	}

	@Scheduled(cron = "0 */2 * * * ?", zone = "Asia/Kolkata")
	@Transactional
	public void retryErrorRecords() throws JmsException, JsonProcessingException {

		String date = DateFormatterUtil.getDateInGivenFormat(ProcessConstants.PROCESSING_DATE_FORMAT);
		Date now = new Date();
		Date minAgo = null;
		Calendar cal = Calendar.getInstance();
		// remove next line if you're always using the current time.
		cal.setTime(now);
		cal.add(Calendar.MINUTE, -retryAfterMin);
		minAgo = cal.getTime();

		List<BranchWiseProcessingStatus> initiatedRecords = branchWiseProcessingStatusService
				.getErroredOrTriggerErroredProcesses(date, minAgo);
		initiateRetry(date, minAgo, initiatedRecords);
	}

	@SuppressWarnings("unchecked")
	private void initiateRetry(String date, Date minAgo, List<BranchWiseProcessingStatus> records) throws JmsException, JsonProcessingException {
		if (records != null && !records.isEmpty()) {
			Session session = entityManager.unwrap(Session.class);
			SimpleDateFormat sdfForStr = new SimpleDateFormat("dd-MM-yyyy");
			sdfForStr.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
			for (BranchWiseProcessingStatus branchWiseProcessingStatus : records) {
				ProcessDefinition process = processDefinitionService.getProcessDefinition(
						branchWiseProcessingStatus.getTenantId(), branchWiseProcessingStatus.getProcessId());
				if(branchWiseProcessingStatus.getStatus().equals(ProcessConstants.PROCESS_STATUS_TRIGGER_ERROR)) {
					BaseProcessRequestObj baseProcessRequestObj = new BaseProcessRequestObj();
					baseProcessRequestObj.setProcessingUUID(branchWiseProcessingStatus.getId());
					baseProcessRequestObj.setCreatedBy(ProcessConstants.USER_SYSTEM);
					baseProcessRequestObj.setProcessId(process.getProcessId());
					baseProcessRequestObj.setProcessingDate(branchWiseProcessingStatus.getProcessingDate());
					baseProcessRequestObj.setTenantId(process.getTenantId());
					mapProcessDefinition(process, baseProcessRequestObj);
					jmsTemplate.convertAndSend(genericTriggerQueue.substring(4),
							new ObjectMapper().writeValueAsString(baseProcessRequestObj));
				} else {
					ScrollableResults requests = getSelectResult(branchWiseProcessingStatus.getBranchCode(),
							branchWiseProcessingStatus.getTenantId(), process.getTriggerTable(), date, minAgo, session);
					
					if (!requests.next()) {
						// no records to process; mark the process as completed
						log.info("No records to reprocess for process {}, branch {}",
								branchWiseProcessingStatus.getProcessId(), branchWiseProcessingStatus.getBranchCode());
						continue;
					}
					do {
						Map<String, Object> request = (Map<String, Object>) requests.get(0);
						if ((Integer) request.get("retryCount") != null
								&& (Integer) request.get("retryCount") >= maxRetryCount) {
							log.debug("Marking records as failed : processId {} , processingUUID {}",
									branchWiseProcessingStatus.getProcessId(), branchWiseProcessingStatus.getId());
							updateRecordStatus(request, process.getTriggerTable(), session);
							continue;
						}
						updateRetryCount(request, process.getTriggerTable(), session);
						Map<String, Object> extraParams = new HashMap<>();
						if (request != null) {
							for (Map.Entry<String, Object> entry : request.entrySet()) {
								String key = entry.getKey();
								Object val = entry.getValue();
								if (val != null && val instanceof Timestamp) {
									Date dateDb = new Date(((Timestamp) val).getTime());
									extraParams.put(key + "Str", sdfForStr.format(dateDb));
								}
							}
						}
						if (!extraParams.isEmpty()) {
							request.putAll(extraParams);
						}
						ProcessDefinitionWithDataDto dto = new ProcessDefinitionWithDataDto();
						dto.setBranchWise(process.getBranchWise());
						dto.setExecutionTime(process.getExecutionTime());
						dto.setFrequency(process.getFrequency());
						dto.setNotificationType(process.getNotificationType());
						dto.setPaused(process.getPaused());
						dto.setProcessApi(process.getProcessApi());
						dto.setProcessId(process.getProcessId());
						dto.setProcessName(process.getProcessName());
						dto.setProcessStage(process.getProcessStage());
						dto.setScheduled(process.getScheduled());
						dto.setTenantId(process.getTenantId());
						dto.setTriggerApi(process.getTriggerApi());
						dto.setTriggerQueue(process.getTriggerQueue());
						dto.setTriggerTable(process.getTriggerTable());
						dto.setRequest(request);
						jmsTemplate.convertAndSend(dto.getTriggerQueue(), dto);
					} while (requests.next());
				}
				if (branchWiseProcessingStatus.getRetryCount() == null) {
					branchWiseProcessingStatus.setRetryCount(1);
				} else {
					branchWiseProcessingStatus.setRetryCount(branchWiseProcessingStatus.getRetryCount() + 1);
				}
				branchWiseProcessingStatusService.save(branchWiseProcessingStatus);
			}
		}
	}

	private void updateRetryCount(Map<String, Object> request, String triggerTable, Session session) {
		Query updateRecord = null;
		Integer retryCount = 0;
		if (request.get("retryCount") != null) {
			retryCount = (Integer) request.get("retryCount") + 1;
		}
		updateRecord = session
				.createNativeQuery("UPDATE " + triggerTable + " SET retryCount = ? WHERE processingUUID = ? ");
		updateRecord.setParameter(1, retryCount);
		updateRecord.setParameter(2, request.get("processingUUID"));
		updateRecord.executeUpdate();
	}

	private void updateRecordStatus(Map<String, Object> request, String triggerTable, Session session) {
		Query updateRecord = null;
		updateRecord = session.createNativeQuery("UPDATE " + triggerTable + " SET processingStatus = '"
				+ ProcessConstants.PROCESS_STATUS_FAILED + "' WHERE processingUUID = ? ");
		updateRecord.setParameter(1, request.get("processingUUID"));
		updateRecord.executeUpdate();
	}

	private ScrollableResults getSelectResult(String branchCode, Integer tenantId, String triggerTable,
			String processingDate, Date minAgo, Session session) {
		Query querySelectRecords = null;
		if (branchCode != null) {
			querySelectRecords = session.createNativeQuery("SELECT * FROM " + triggerTable
					+ " with (nolock) WHERE tenantId = ? AND branchCode = ? AND processingDate = ? AND lastModifiedDate < ? AND processingStatus = 'E'");
			querySelectRecords.setParameter(1, tenantId);
			querySelectRecords.setParameter(2, branchCode);
			querySelectRecords.setParameter(3, processingDate);
			querySelectRecords.setParameter(4, minAgo);
		} else {
			querySelectRecords = session.createNativeQuery("SELECT * FROM " + triggerTable
					+ " with (nolock) WHERE tenantId = ? AND processingDate = ? AND lastModifiedDate < ? AND processingStatus = 'E' ");
			querySelectRecords.setParameter(1, tenantId);
			querySelectRecords.setParameter(2, processingDate);
			querySelectRecords.setParameter(3, minAgo);
		}
		querySelectRecords.setReadOnly(true);
		querySelectRecords.setFetchSize(1000);
		querySelectRecords.setResultTransformer(AliasToEntityMapResultTransformer.INSTANCE);
		ScrollableResults results = querySelectRecords.scroll(ScrollMode.FORWARD_ONLY);
		return results;
	}
	
	private void mapProcessDefinition(ProcessDefinition processDefinition,
			BaseProcessRequestObj baseProcessRequestObj) {
		baseProcessRequestObj.setProcessDefinition(processDefinition);
		baseProcessRequestObj.setSpName(processDefinition.getSpName());
		baseProcessRequestObj.setInputParam1(processDefinition.getInputParam1());
		baseProcessRequestObj.setInputParam2(processDefinition.getInputParam2());
		baseProcessRequestObj.setInputParam3(processDefinition.getInputParam3());
		baseProcessRequestObj.setInputParam4(processDefinition.getInputParam4());
		baseProcessRequestObj.setInputParam5(processDefinition.getInputParam5());
	}
}
