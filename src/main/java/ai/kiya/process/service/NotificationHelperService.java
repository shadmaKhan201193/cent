package ai.kiya.process.service;

public interface NotificationHelperService {
	
	public void sendProcessNotification(Integer processId, Integer tenantId, String eventType, String errorMsg);

}
