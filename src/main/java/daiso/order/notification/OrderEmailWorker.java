package daiso.order.notification;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.mail.MessagingException;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import javax.sql.DataSource;

import com.google.zxing.WriterException;

@WebListener
public class OrderEmailWorker implements ServletContextListener {

    private ScheduledExecutorService executor;
    private final OrderEmailSender sender = new OrderEmailSender();

    // Tomcat이 시작되면 발송 대기열을 주기적으로 확인한다.
    @Override
    public void contextInitialized(ServletContextEvent event) {
        ServletContext context = event.getServletContext();
        if (!"true".equalsIgnoreCase(System.getenv("ORDER_EMAIL_ENABLED"))) {
            return;
        }

        try {
            DataSource dataSource =
                    InitialContext.doLookup("java:comp/env/jdbc/mysqlDB");
            String testRecipient = System.getenv("ORDER_EMAIL_TEST_RECIPIENT");

            executor = Executors.newSingleThreadScheduledExecutor();
            executor.scheduleWithFixedDelay(
                    () -> sendNext(dataSource, context, testRecipient),
                    0, 10, TimeUnit.SECONDS);
        } catch (NamingException error) {
            context.log("주문 이메일용 DB 연결을 찾지 못했습니다.", error);
        }
    }

    private void sendNext(DataSource dataSource, ServletContext context,
            String testRecipient) {
        boolean testMode = testRecipient != null && !testRecipient.isBlank();
        String selectSql =
        		"SELECT e.email_id, e.recipient_email, e.subject, e.body, "
			      + "       (SELECT o.pickup_token FROM orders o "
			      + "        WHERE o.order_id = e.order_id "
			      + "          AND e.event_type = 'PICKUP_READY' "
			      + "          AND o.status = 'PAID' "
			      + "          AND o.pickup_status = 'READY' "
			      + "          AND o.canceled_at IS NULL "
			      + "          AND o.pickup_token_expires_at > NOW()) AS pickup_token "
			      + "FROM order_email_outbox e "
			      + "WHERE e.status = 'PENDING' AND e.next_attempt_at <= NOW() "
			      + (testMode ? "AND e.recipient_email = ? " : "")
			      + "ORDER BY e.next_attempt_at, e.email_id "
			      + "LIMIT 1 FOR UPDATE SKIP LOCKED";

        String sentSql =
                "UPDATE order_email_outbox "
              + "SET status = 'SENT', sent_at = NOW(), "
              + "    attempt_count = attempt_count + 1 "
              + "WHERE email_id = ?";

        String retrySql =
                "UPDATE order_email_outbox "
              + "SET status = CASE WHEN attempt_count >= 2 "
              + "                  THEN 'FAILED' ELSE 'PENDING' END, "
              + "    attempt_count = attempt_count + 1, "
              + "    next_attempt_at = DATE_ADD(NOW(), INTERVAL 1 MINUTE) "
              + "WHERE email_id = ?";

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);

            try {
                long emailId;
                String recipient;
                String subject;
                String body;
                String pickupToken;

                // 발송 결과를 저장할 때까지 이 메일 행을 다른 작업이 가져가지 못하게 한다.
                try (PreparedStatement select = connection.prepareStatement(selectSql)) {
                    if (testMode) {
                        select.setString(1, testRecipient.trim());
                    }
                    try (ResultSet rows = select.executeQuery()) {
                        if (!rows.next()) {
                            connection.commit();
                            return;
                        }
                        emailId = rows.getLong("email_id");
                        recipient = rows.getString("recipient_email");
                        subject = rows.getString("subject");
                        body = rows.getString("body");
                        pickupToken=rows.getString("pickup_token");
                    }
                }

                String updateSql = sentSql;
                try {
                    byte[] qrPng = pickupToken==null ?null: PickupQrImage.toPng(pickupToken);
                    sender.send(recipient, subject, body,qrPng);
                } catch (MessagingException|WriterException|IOException error) {
                    updateSql = retrySql;
                    context.log("주문 이메일 발송 실패. email_id=" + emailId
                            + ", 오류 유형=" + error.getClass().getSimpleName());
                }

                try (PreparedStatement update = connection.prepareStatement(updateSql)) {
                    update.setLong(1, emailId);
                    update.executeUpdate();
                }
                connection.commit();
            } catch (SQLException | RuntimeException error) {
                connection.rollback();
                throw error;
            }
        } catch (SQLException | RuntimeException error) {
            context.log("주문 이메일 대기열 처리 실패", error);
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        if (executor != null) {
            executor.shutdownNow();
        }
    }
}