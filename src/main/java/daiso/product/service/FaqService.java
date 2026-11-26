package daiso.product.service;
import java.util.Map;
import java.util.Set;

public final class FaqService {
    private static final String SHIPPING_FEE_ANSWER =
            "택배 배송비는 상품 합계가 30,000원 미만이면 3,000원이고, "
            + "30,000원 이상이면 무료예요. 매장픽업 수수료는 200원이에요.";

    private static final Set<String> SHIPPING_FEE_QUESTIONS = Set.of(
            "배송비", "배송비얼마야", "배송비얼마예요",
            "배송비알려줘", "무료배송기준", "얼마부터무료배송이야"
    );
    
    private static final Map<String, String> ANSWERS = Map.of(
            "shipping_fee", SHIPPING_FEE_ANSWER,
            "return_policy", "마이페이지 → 주문내역에서 배송 완료된 주문을 열고 "
                    + "[반품 신청]을 눌러 주세요. 결제가 승인된 본인 주문에서 신청할 수 있어요.",
            "pickup_guide", "상품 상세에서 [매장픽업]을 선택하고 이용할 매장을 골라 주세요. "
                    + "픽업 주문서에서 매장과 수령인 정보를 확인한 뒤 결제하고, "
                    + "준비가 끝나면 선택한 매장에서 상품을 받아 가면 돼요."
    );

    private static final Set<String> RETURN_QUESTIONS = Set.of(
            "반품방법", "반품어떻게해", "반품신청어떻게해", "반품신청방법알려줘"
    );

    private static final Set<String> PICKUP_QUESTIONS = Set.of(
            "매장픽업안내", "매장픽업방법", "매장픽업어떻게해", "픽업어떻게해"
    );

    private FaqService() {
    }

    public static String findAnswer(String faqId, String question) {
        if (faqId != null && ANSWERS.containsKey(faqId)) {
            return ANSWERS.get(faqId);
        }

        String normalized = question.replaceAll("[\\s?!.,？]+", "");
        if (SHIPPING_FEE_QUESTIONS.contains(normalized)) {
            return ANSWERS.get("shipping_fee");
        }
        if (RETURN_QUESTIONS.contains(normalized)) {
            return ANSWERS.get("return_policy");
        }
        if (PICKUP_QUESTIONS.contains(normalized)) {
            return ANSWERS.get("pickup_guide");
        }
        return null;
    }
}