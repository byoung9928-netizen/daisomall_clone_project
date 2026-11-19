package daiso.product.dto;

import java.util.List;
import java.util.Map;

public final class ReviewExtras {
    public final boolean available;
    public final List<ReviewQuestion> questions;
    private final Map<Long, List<Answer>> answers;
    private final Map<Long, List<Long>> photos;

    public ReviewExtras(boolean available, List<ReviewQuestion> questions,
            Map<Long, List<Answer>> answers, Map<Long, List<Long>> photos) {
        this.available = available;
        this.questions = List.copyOf(questions);
        this.answers = Map.copyOf(answers);
        this.photos = Map.copyOf(photos);
    }

    public List<Answer> answers(long reviewId) {
        return answers.getOrDefault(reviewId, List.of());
    }

    public List<Long> photos(long reviewId) {
        return photos.getOrDefault(reviewId, List.of());
    }

    public static final class Submission {
        public static final int MAX_PHOTOS = 10;
        public final Map<String, Integer> answers;
        public final List<byte[]> photos;
        public final List<Long> removedPhotoIds;

        public Submission(Map<String, Integer> answers, List<byte[]> photos, List<Long> removedPhotoIds) {
            this.answers = Map.copyOf(answers);
            this.photos = List.copyOf(photos);
            this.removedPhotoIds = List.copyOf(removedPhotoIds);
        }
    }

    public static final class Answer {
        public final String key;
        public final String question;
        public final int value;
        public final String label;

        public Answer(String key, String question, int value, String label) {
            this.key = key;
            this.question = question;
            this.value = value;
            this.label = label;
        }
    }
}
