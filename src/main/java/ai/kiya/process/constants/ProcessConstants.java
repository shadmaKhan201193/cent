package ai.kiya.process.constants;

public class ProcessConstants {

	public static final String PROCESS_STATUS_UNPROCESSED = "N";
	public static final String PROCESS_STATUS_INPROGRESS = "X";
	public static final String PROCESS_STATUS_ERROR = "E";
	public static final String PROCESS_STATUS_TRIGGER_ERROR = "D";
	public static final String PROCESS_STATUS_FAILED = "F";

	/**
	 * This status has been put for the state where Branch/Branch-Account wise
	 * trigger is stored in DB but we have not yet sent process message to queue. We
	 * can pick records with these statuses and send appropriate trigger messages.
	 * This is not applicable for individual processes.
	 */
	public static final String PROCESS_STATUS_SAVED = "S";

	/**
	 * This status has been put for the state where Branch/Branch-Account wise
	 * trigger has happened but processing is still pending. Once the processing is
	 * also completed, record can be marked as success (Y). This is not applicable
	 * for individual processes.
	 */
	public static final String PROCESS_STATUS_TRIGGERED = "T";
	public static final String PROCESS_STATUS_RETRY = "R";
	public static final String PROCESS_STATUS_SUCCESS = "Y";
	public static final String PROCESS_STATUS_INITIATED = "I";

	public static final String FREQUENCY_DAILY = "1";
	public static final String FREQUENCY_RECURSIVE = "5";
	public static final String FREQUENCY_MONTHLY = "2";
	public static final String FREQUENCY_QUARTERLY = "4";
	public static final String FREQUENCY_YEARLY = "3";

	public static final String USER_SYSTEM = "SYSTEM";
	
	public static final String PROCESS_TYPE_MANUAL = "1";
	public static final String PROCESS_TYPE_SCHEDULED = "2";
	
	public static final String PROCESS_BRANCH_WISE = "1";
	public static final String PROCESS_NOT_BRANCH_WISE = "0";

	public static final String QUEUE_OR_DB_Q = "Q";
	public static final String QUEUE_OR_DB_DB = "D";
	
	public static final String PROCESS_STAGE_EXECUTE = "1";
	public static final String PROCESS_STAGE_CALCULATE = "2";
	public static final String PROCESS_STAGE_VOUCHER = "3";
	
	public static final String PROCESSING_UUID_STR = "processingUUID";
	public static final String PROCESSING_DATE_FORMAT = "yyyy-MM-dd";
	
	public static final String NOTIFY_TYPE_REPORT = "R";
	public static final String NOTIFY_TYPE_PROCESS = "P";
	
	public static final String STRING_NA = "NA";
	
	public static final String USER_ALL = "ALL";
	
	//Process email event categories
	public static final String PROCESS_BEGIN_EVENT = "PROCESS_BEGIN";
	public static final String PROCESS_TRIGGER_FAILED_EVENT = "PROCESS_TRIGGER_FAILED";
	public static final String PROCESS_PROCESSING_FAILED_EVENT = "PROCESS_PROCESSING_FAILED";
	public static final String PROCESS_COMPLETED_EVENT = "PROCESS_COMPLETED";
	
}
