package daiso.order.notification;

import java.nio.charset.StandardCharsets;
import java.util.Properties;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

public class OrderEmailSender {
	//배송 픽업안내 메일을 프로젝트 gmail 계정으로 발송
	public void send(String recipient, String subject, String body)
		throws MessagingException{
		String username=System.getenv("ORDER_SMTP_USERNAME");
		String password=System.getenv("ORDER_SMTP_PASSWORD");
		
		if(username == null || username.isBlank()
				|| password == null || password.isBlank()) {
			throw new MessagingException("Tomcat의 SMTP환경변수를 확인해 주세요.");
			
		}
		Properties properties = new Properties();
		properties.setProperty("mail.smtp.host", "smtp.gmail.com");
		properties.setProperty("mail.smtp.port", "587");
		properties.setProperty("mail.smtp.auth", "true");
		properties.setProperty("mail.smtp.starttls.enable", "true");
		properties.setProperty("mail.smtp.starttls.required", "true");
		properties.setProperty("mail.smtp.ssl.checkserveridentity", "true");
		properties.setProperty("mail.smtp.connectiontimeout", "5000");
		properties.setProperty("mail.smtp.timeout", "10000");
		properties.setProperty("mail.smtp.writetimeout", "10000");
		
		Session session = Session.getInstance(properties);
		MimeMessage message = new MimeMessage(session);
		message.setFrom(new InternetAddress(username, true));
		message.setRecipient(Message.RecipientType.TO, new InternetAddress(recipient,true));
		message.setSubject(subject,StandardCharsets.UTF_8.name());
		message.setText(body,StandardCharsets.UTF_8.name());
		
		Transport.send(message, username,password);
	}

}
