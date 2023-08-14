package ai.kiya.process.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class DateFormatterUtil {
	
	//TODO - check the timezone for each tenantId
	public static String getDateInGivenFormat(String format) {
		SimpleDateFormat sdf = new SimpleDateFormat(format);
		sdf.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
		String date = sdf.format(new Date());
		return date;
	}
}
