package daiso.order.controller;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import daiso.order.db.OrderDAO;

@WebListener
public class PointOrderExpiryListener implements ServletContextListener {

    private ScheduledExecutorService scheduler;

    // Tomcat이 앱을 시작하면 만료 검사를 예약하고, 앱을 종료하면 예약도 해제한다.
    @Override
    public void contextInitialized(ServletContextEvent event) {
        ServletContext context = event.getServletContext();

        scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "daiso-point-order-expiry");
            thread.setDaemon(true);
            return thread;
        });

        scheduler.scheduleWithFixedDelay(
                () -> expireOrders(context), 1, 1, TimeUnit.MINUTES);
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    // 한 주문의 처리 실패가 다른 주문의 포인트 반환까지 막지 않도록 건별로 처리한다.
    private void expireOrders(ServletContext context) {
        try {
            OrderDAO dao = new OrderDAO();
            long afterOrderId = 0;

            while (!Thread.currentThread().isInterrupted()) {
                List<Long> orderIds = dao.findExpiredPointOrderIds(afterOrderId);
                if (orderIds.isEmpty()) {
                    return;
                }

                for (long orderId : orderIds) {
                    if (Thread.currentThread().isInterrupted()) {
                        return;
                    }

                    try {
                        dao.expirePendingPointOrder(orderId);
                    } catch (SQLException | RuntimeException e) {
                        context.log("포인트 사용 주문 만료 처리 실패: " + orderId, e);
                    }
                    afterOrderId = orderId;
                }
            }
        } catch (SQLException | RuntimeException e) {
            context.log("포인트 만료 대상 주문 조회 실패", e);
        }
    }
}