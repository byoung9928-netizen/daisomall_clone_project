package daiso.product.service;

import daiso.product.dto.ReviewQuestion;
import java.util.List;
import java.util.Map;

final class ReviewQuestions {
    private static final ReviewQuestion DURABILITY =
            question("durability", "상품은 튼튼한가요?", "아쉬워요", "보통이에요", "튼튼해요");
    private static final ReviewQuestion EASE =
            question("ease", "사용하기 편한가요?", "불편해요", "보통이에요", "편해요");
    private static final Map<String, List<ReviewQuestion>> BY_CATEGORY = Map.ofEntries(
            Map.entry("CTGR_00083", List.of(DURABILITY,
                    question("brightness", "실제 밝기는 어떤가요?", "어두워요", "적당해요", "밝아요"))),
            Map.entry("CTGR_00637", List.of(EASE,
                    question("brightness", "실제 밝기는 어떤가요?", "어두워요", "적당해요", "밝아요"))),
            Map.entry("CTGR_01050", List.of(
                    question("texture", "사용감은 어떤가요?", "아쉬워요", "보통이에요", "만족해요"),
                    question("comfort", "자주 사용하기 편한가요?", "불편해요", "보통이에요", "편해요"))),
            Map.entry("CTGR_00002", List.of(EASE,
                    question("cleanup", "사용 후 세척은 어떤가요?", "번거로워요", "보통이에요", "간편해요"))),
            Map.entry("CTGR_00012", List.of(EASE,
                    question("effect", "사용 효과는 어떤가요?", "아쉬워요", "보통이에요", "만족해요"))),
            Map.entry("CTGR_00016", List.of(DURABILITY,
                    question("capacity", "수납공간은 어떤가요?", "작아요", "적당해요", "넉넉해요"))),
            Map.entry("CTGR_00015", List.of(EASE,
                    question("design", "실물 디자인은 어떤가요?", "아쉬워요", "보통이에요", "마음에 들어요"))),
            Map.entry("CTGR_00019", List.of(
                    question("size", "실제 크기는 예상과 비교해 어떤가요?", "작아요", "비슷해요", "커요"),
                    question("design", "실물 디자인은 어떤가요?", "아쉬워요", "보통이에요", "마음에 들어요"))),
            Map.entry("CTGR_00017", List.of(DURABILITY, EASE)),
            Map.entry("CTGR_00022", List.of(
                    question("taste", "맛은 어떤가요?", "아쉬워요", "보통이에요", "맛있어요"),
                    question("portion", "양은 어떤가요?", "적어요", "적당해요", "많아요"))),
            Map.entry("CTGR_00013", List.of(DURABILITY, EASE)),
            Map.entry("CTGR_01136", List.of(
                    question("appearance", "실물은 상품 사진과 비슷한가요?", "달라요", "비슷해요", "실물이 더 좋아요"),
                    question("fit", "착용하거나 사용하기 편한가요?", "불편해요", "보통이에요", "편해요"))),
            Map.entry("CTGR_00018", List.of(
                    question("pet_acceptance", "반려동물이 상품을 잘 사용하나요?", "잘 안 써요", "보통이에요", "잘 써요"),
                    question("care", "상품을 관리하기 편한가요?", "번거로워요", "보통이에요", "편해요"))),
            Map.entry("CTGR_00020", List.of(DURABILITY, EASE)),
            Map.entry("CTGR_00005", List.of(
                    question("design", "실물 디자인은 어떤가요?", "아쉬워요", "보통이에요", "마음에 들어요"), EASE)));

    static List<ReviewQuestion> forPath(List<String> categoryIds) {
        for (String id : categoryIds) {
            List<ReviewQuestion> questions = BY_CATEGORY.get(id);
            if (questions != null) return questions;
        }
        return List.of(question("expectations", "상품이 기대에 맞았나요?", "아쉬워요", "보통이에요", "만족해요"), EASE);
    }

    private static ReviewQuestion question(String key, String label, String low, String middle, String high) {
        return new ReviewQuestion(key, label, low, middle, high);
    }
}
