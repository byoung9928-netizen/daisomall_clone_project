package daiso.order.notification;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import javax.activation.DataHandler;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;
import com.google.zxing.WriterException;

public class OrderEmailSender {
	//배송 픽업안내 메일을 프로젝트 gmail 계정으로 발송
	public void send(String recipient, String subject, String body)
		throws MessagingException{
		send(recipient, subject,body,null);
	}
	
	//qr코드
	public void send(String recipient, String subject,String body, byte[] qrPng)
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
		message.setSubject(subject, StandardCharsets.UTF_8.name());
		if(qrPng ==null) {
			message.setText(body,StandardCharsets.UTF_8.name());
		}else {
			//매일 본문의 cid와 QR이미지의 Content-ID를 연결
			MimeBodyPart textPart = new MimeBodyPart();
			textPart.setText(body, StandardCharsets.UTF_8.name());
			
			MimeBodyPart htmlPart = new MimeBodyPart();
			htmlPart.setContent(
					"<html><body><pre>"+body.replace("&", "&amp;")
					.replace("<", "&lt;").replace(">", "&gt;")
					+"</pre><p>매장에서 아래 QR을 보여주세요.</p>"
					+"<img src=\"cid:pickup-qr\" width=\"320\" height=\"320\" "
					+"alt=\"매장 픽업 QR 코드\"></body></html>",
					"text/html; charset=UTF-8");
			MimeBodyPart imagePart=new MimeBodyPart();
			imagePart.setDataHandler(new DataHandler(
					new ByteArrayDataSource(qrPng,"image/png")));
			imagePart.setContentID("<pickup-qr>");
			imagePart.setDisposition(MimeBodyPart.INLINE);
			
			MimeMultipart related = new MimeMultipart("related");
			related.addBodyPart(htmlPart);
			related.addBodyPart(imagePart);
			
			MimeBodyPart htmlWithImage = new MimeBodyPart();
			htmlWithImage.setContent(related);
			
			MimeMultipart alternatives = new MimeMultipart("alternative");
			alternatives.addBodyPart(textPart);
			alternatives.addBodyPart(htmlWithImage);
			message.setContent(alternatives);
			
		}
		
		Transport.send(message, username,password);
	}

}
