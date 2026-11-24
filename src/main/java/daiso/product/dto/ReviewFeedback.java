package daiso.product.dto;

import java.util.Map;

public final class ReviewFeedback {
    public enum Reaction {
        HELPFUL("👍", "완전꿀팁"),
        PURCHASE_INTEREST("💗", "구매욕상승"),
        QUESTION_RESOLVED("👀", "궁금증해결");

        public final String icon;
        public final String label;

        Reaction(String icon, String label) {
            this.icon = icon;
            this.label = label;
        }
    }

    public enum Reason {
        OFF_TOPIC("목적에 맞지 않는 내용"),
        ABUSE("욕설/비방"),
        ADVERTISING("지나친 광고"),
        PERSONAL_INFORMATION("개인정보 유출"),
        SPAM("도배"),
        SEXUAL_CONTENT("음란/선정성"),
        MALWARE("악성코드 유포"),
        COPYRIGHT("저작권 침해"),
        OTHER("기타");

        public final String label;
        Reason(String label) { this.label = label; }
    }

    public enum ReportStatus {
        PENDING("미처리"), RESOLVED("처리 완료"), DISMISSED("기각");

        public final String label;
        ReportStatus(String label) { this.label = label; }
    }

    public static final class Tally {
        public static final Tally EMPTY = new Tally(0, false);
        public final long count;
        public final boolean selected;

        public Tally(long count, boolean selected) {
            this.count = count;
            this.selected = selected;
        }
    }

    public static final ReviewFeedback UNAVAILABLE = new ReviewFeedback(false, false, Map.of());
    public final boolean reactionsAvailable;
    public final boolean reportsAvailable;
    private final Map<Long, Map<Reaction, Tally>> reactions;

    public ReviewFeedback(boolean reactionsAvailable, boolean reportsAvailable,
            Map<Long, Map<Reaction, Tally>> reactions) {
        this.reactionsAvailable = reactionsAvailable;
        this.reportsAvailable = reportsAvailable;
        this.reactions = reactions;
    }

    public Tally tally(long review, Reaction reaction) {
        return reactions.getOrDefault(review, Map.of()).getOrDefault(reaction, Tally.EMPTY);
    }
}
