package daiso.product.dto;

import java.util.List;

public final class ReviewQuestion {
    public final String key;
    public final String label;
    public final List<String> options;

    public ReviewQuestion(String key, String label, String low, String middle, String high) {
        this.key = key;
        this.label = label;
        this.options = List.of(low, middle, high);
    }
}
