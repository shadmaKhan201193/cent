package ai.kiya.process.service.impl;

import java.util.Properties;

import javax.mail.Authenticator;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class EmailService {

	public void sendNotificationMessage(String to, String[] cc, String[] bcc, String subject, String message)
			throws javax.mail.MessagingException {
		String host = "smtp.office365.com";
		String from = "ankit.dedhiya@kiya.ai";
		Properties properties = System.getProperties();
		properties.put("mail.smtp.host", host);
		properties.put("mail.smtp.port", "587");
		properties.put("mail.smtp.auth", "true");
		properties.put("mail.smtp.starttls.enable", "true");

		Session session = Session.getInstance(properties, new Authenticator() {
			@Override
			protected PasswordAuthentication getPasswordAuthentication() {
				return new PasswordAuthentication("ankit.dedhiya@kiya.ai", "ad$aug2023");
			}
		});
		session.setDebug(true);
		MimeMessage msg = new MimeMessage(session);
		MimeMessageHelper helper = new MimeMessageHelper(msg, true);

		helper.setFrom(from);
		if(to != null && StringUtils.hasText(to)) {
			if(!to.contains("kiya.ai")) {
				log.debug("Email ID not part of kiya.ai");
				return;
			}
			helper.setTo(new InternetAddress(to));
		}
		if(cc != null) {
			for (String ccAdd : cc) {
				if(ccAdd == null || !StringUtils.hasText(ccAdd) || !ccAdd.contains("kiya.ai")) {
					continue;
				}
				helper.addCc(ccAdd);
			}
		}
		if(bcc != null) {
			for (String ccAdd : bcc) {
				if(ccAdd == null || !StringUtils.hasText(ccAdd) || !ccAdd.contains("kiya.ai")) {
					continue;
				}
				helper.addBcc(ccAdd);
			}
		}
		helper.setSubject(subject);
		helper.setText("<html><body>" + message + "</html></body>", true);

		Transport.send(msg);
		
		log.debug("Email sent to {} for subject {}", to, subject);

	}

}
