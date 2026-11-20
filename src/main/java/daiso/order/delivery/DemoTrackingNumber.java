package daiso.order.delivery;


import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class DemoTrackingNumber {

    public static String create() {

        // 가상 운송장은 UTC 기준으로 생성
        ZonedDateTime now =
            ZonedDateTime.now(ZoneOffset.UTC);

        // 다음 3시간 단위 시각을 배송완료 예정 시각으로 설정
        // 예: 현재 10시라면 12시, 현재 12시라면 15시
        int hoursToAdd = 3 - (now.getHour() % 3);

        ZonedDateTime deliveryTime = now
            .withMinute(0)
            .withSecond(0)
            .withNano(0)
            .plusHours(hoursToAdd);

        return deliveryTime.format(
            DateTimeFormatter.ofPattern(
                "yyyy-MM-dd'T'HH:mm:ss'Z'"
            )
        );
    }
}