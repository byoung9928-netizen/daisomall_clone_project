package daiso.product.dto;

import java.util.List;
import java.util.Map;

public final class ReviewExtras {
    public final boolean available;
    public final List<ReviewQuestion> questions;
    private final Map<Long, List<Answer>> answers;
    private final Map<Long, List<Long>> photos;
    public final List<Distribution> distributions;
    public final Gallery gallery;

    public ReviewExtras(boolean available, List<ReviewQuestion> questions,
            Map<Long, List<Answer>> answers, Map<Long, List<Long>> photos) {
        this(available, questions, answers, photos, List.of(), Gallery.EMPTY);
    }

    public ReviewExtras(boolean available, List<ReviewQuestion> questions, Map<Long, List<Answer>> answers,
            Map<Long, List<Long>> photos, List<Distribution> distributions, Gallery gallery) {
        this.distributions = List.copyOf(distributions);
        this.gallery = gallery;
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

    public static final class Distribution {
        public final ReviewQuestion question;
        public final long responses;
        public final List<Integer> percentages;

        public Distribution(ReviewQuestion question, List<Long> counts) {
            this.question = question;
            this.responses = counts.stream().mapToLong(Long::longValue).sum();
            this.percentages = counts.stream()
                    .map(count -> responses == 0 ? 0 : (int) Math.round(100.0 * count / responses))
                    .collect(java.util.stream.Collectors.toUnmodifiableList());
        }
    }

    public static final class Gallery {
        public static final Gallery EMPTY = new Gallery(List.of(), 0, 1, 1);

        public final List<Long> ids;
        public final long total;
        public final int page;
        public final long pages;

        public Gallery(List<Long> ids, long total, int page, long pages) {
            this.ids = List.copyOf(ids);
            this.total = total;
            this.page = page;
            this.pages = pages;
        }
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
