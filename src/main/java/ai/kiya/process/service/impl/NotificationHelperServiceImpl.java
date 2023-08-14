package ai.kiya.process.service.impl;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.mail.MessagingException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import ai.kiya.process.constants.ProcessConstants;
import ai.kiya.process.entities.CommunicationsMst;
import ai.kiya.process.entities.CommunicationsRecipientsMst;
import ai.kiya.process.repositories.CommunicationsMstRepo;
import ai.kiya.process.repositories.CommunicationsRecipientsMstRepo;
import ai.kiya.process.repositories.ProcessGroupingRepo;
import ai.kiya.process.service.NotificationHelperService;
import ai.kiya.process.service.ProcessDefinitionService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class NotificationHelperServiceImpl implements NotificationHelperService {

	@Autowired
	private CommunicationsMstRepo communicationsMstRepo;

	@Autowired
	private CommunicationsRecipientsMstRepo communicationsRecipientsMstRepo;

	@Autowired
	private ProcessGroupingRepo groupingRepo;

	@Autowired
	private EmailService emailService;

	@Autowired
	private ProcessDefinitionService definitionService;

	@Override
	public void sendProcessNotification(Integer processId, Integer tenantId, String eventType, String errorMsg) {
		CommunicationsMst mst = communicationsMstRepo.getRecordForProcess(ProcessConstants.NOTIFY_TYPE_PROCESS,
				tenantId, processId);
		if (mst != null) {
			List<CommunicationsRecipientsMst> recipients = communicationsRecipientsMstRepo
					.getAllRecordsForType(mst.getTenantId(), mst.getNotifyType(), mst.getTypeId());
			for (CommunicationsRecipientsMst recipient : recipients) {
				if (recipient.getUserRole() != null && recipient.getUserIds() == null) {
					List<Map<String, String>> users = groupingRepo.getUsersForRole(tenantId, recipient.getUserRole());
					log.debug("Notification generation for a role {} without specific users.", recipient.getUserRole());
					for (Map<String, String> user : users) {
						triggerIndividualEmail(tenantId, mst, user.get("emailId"), user.get("loginId"), eventType,
								errorMsg);
					}
				} else if (recipient.getUserRole() != null && recipient.getUserIds() != null) {
					String emailId = groupingRepo.getEmailForUser(tenantId, recipient.getUserIds());
					triggerIndividualEmail(tenantId, mst, emailId, recipient.getUserIds(), eventType, errorMsg);
				} else if (recipient.getAdditionalEmailId() != null) {
					Arrays.asList(recipient.getAdditionalEmailId().split(";")).forEach(recipientEmail -> {
						triggerIndividualEmail(tenantId, mst, recipientEmail, ProcessConstants.USER_ALL, eventType,
								errorMsg);
					});
				}
			}
		}
	}

	private void triggerIndividualEmail(Integer tenantId, CommunicationsMst processData, String emailId, String userId,
			String eventType, String errorMsg) {
		try {
			emailService.sendNotificationMessage(emailId, processData.getCc() != null ? processData.getCc().split(";") : null,
					processData.getBcc() != null ? processData.getBcc().split(";") : null,
					replaceDynamicText(getEventBasedExtraSubject(eventType, processData.getTypeId()) + " : " + processData.getSubjectLine(), userId, tenantId, processData.getTypeId()),
					getEmailBody(replaceDynamicText(processData.getMailBody(), userId, tenantId, processData.getTypeId()),
							processData.getTypeId(), eventType, errorMsg));
		} catch (MessagingException e) {
			log.error("Failed to send email for process Id ");
			e.printStackTrace();
		}
	}

	private String getEmailBody(String replaceDynamicText, String processId, String eventType, String errorMsg) {
		String extraMsg = getEventBasedExtraMessage(eventType, processId, errorMsg);
		if (replaceDynamicText != null && extraMsg != null) {
			return replaceDynamicText + "<br><br>" + extraMsg;
		} else if (extraMsg != null) {
			return extraMsg;
		} else if (replaceDynamicText != null) {
			return replaceDynamicText;
		}
		return "";
	}

	private String getEventBasedExtraMessage(String eventType, String processId, String errorMsg) {
		switch (eventType) {
		case ProcessConstants.PROCESS_BEGIN_EVENT:
			return "Process " + processId + " has begun.";
		case ProcessConstants.PROCESS_TRIGGER_FAILED_EVENT:
			return "Process trigger has failed for process " + processId + errorMsg != null && !errorMsg.isEmpty()
					? " with reason" + errorMsg
					: "";
		case ProcessConstants.PROCESS_PROCESSING_FAILED_EVENT:
			return "Process " + processId + " has failed during processing" + errorMsg != null && !errorMsg.isEmpty()
					? " with reason" + errorMsg
					: "";
		case ProcessConstants.PROCESS_COMPLETED_EVENT:
			return "Process " + processId + " has been completed.";

		default:
			break;
		}
		return null;
	}
	
	private String getEventBasedExtraSubject(String eventType, String processId) {
		switch (eventType) {
		case ProcessConstants.PROCESS_BEGIN_EVENT:
			return "Process " + processId + " Start";
		case ProcessConstants.PROCESS_TRIGGER_FAILED_EVENT:
			return "Process " + processId + " Trigger Failed";
		case ProcessConstants.PROCESS_PROCESSING_FAILED_EVENT:
			return "Process " + processId + " Failed";
		case ProcessConstants.PROCESS_COMPLETED_EVENT:
			return "Process " + processId + " Completed";

		default:
			break;
		}
		return null;
	}

	private String replaceDynamicText(String text, String userId, Integer tenantId, String processId) {
		if (text == null) {
			return null;
		}
		SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy");
		if (text.contains("${userId}")) {
			if (userId != null && !userId.equals(ProcessConstants.USER_ALL)) {
				text = text.replace("${userId}", groupingRepo.getNameForUser(tenantId, userId));
			} else {
				text = text.replace("${userId}", "User");
			}
		}
		if (text.contains("${processId}")) {
			text = text.replace("${processId}", processId);
		}
		if (text.contains("${processName}")) {
			text = text.replace("${processName}",
					definitionService.getProcessDefinition(tenantId, Integer.parseInt(processId)).getProcessName());
		}
		if (text.contains("${operationDate}")) {
			if (userId != null && !userId.equals("ALL")) {
				text = text.replace("${operationDate}",
						sdf.format(groupingRepo.getOperationalDateForUser(tenantId, userId)));
			} else {
				text = text.replace("${operationDate}", "");
			}
		}
		if (text.contains("${systemDate}")) {
			text = text.replace("${systemDate}", sdf.format(new Date()));
		}
		return text;
	}

}
