package daiso.product.service;
import java.util.Map;
import java.util.Set;
import java.util.List;

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
    
    public static final class Entry {
        public final String category;
        public final String question;
        public final String answer;

        private Entry(String category, String question, String answer) {
            this.category = category;
            this.question = question;
            this.answer = answer;
        }
    }

    public static List<Entry> entries() {
        return List.of(
                new Entry("배송·픽업", "택배 배송비는 얼마인가요?",
                        ANSWERS.get("shipping_fee")),
                new Entry("배송·픽업", "매장픽업은 어떻게 이용하나요?",
                        ANSWERS.get("pickup_guide")),
                new Entry("취소·반품", "반품은 어떻게 신청하나요?",
                        ANSWERS.get("return_policy")),
                new Entry("취소·반품", "주문을 취소하려면 어디로 가나요?",
                        "마이페이지의 주문내역에서 해당 주문을 열어 주세요. "
                        + "취소할 수 있는 주문에는 [주문취소] 버튼이 표시돼요."),
                new Entry("회원정보", "비밀번호는 어디서 변경하나요?",
                        "마이페이지 → 회원정보에서 [비밀번호 변경]을 눌러 주세요."),
                new Entry("회원정보", "배송지를 추가하거나 수정하려면 어떻게 하나요?",
                        "마이페이지 → 배송지/픽업매장에서 배송지를 추가하거나 수정할 수 있어요."),
                new Entry("고객센터", "1:1 문의 답변은 어디서 확인하나요?",
                        "로그인한 뒤 마이페이지 → 1:1 문의내역에서 문의를 펼치면 답변을 볼 수 있어요.")
        );
    }

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