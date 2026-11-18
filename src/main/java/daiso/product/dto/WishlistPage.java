package daiso.product.dto;

import java.util.List;

/** 회원 한 명의 찜 목록과 페이지 정보를 전달합니다. */
public final class WishlistPage {
    private final List<WishlistItem> items;
    private final int total;
    private final int page;
    private final int totalPages;

    public WishlistPage(List<WishlistItem> items, int total, int page, int totalPages) {
        this.items = List.copyOf(items);
        this.total = total;
        this.page = page;
        this.totalPages = totalPages;
    }

    public List<WishlistItem> getItems() { return items; }
    public int getTotal() { return total; }
    public int getPage() { return page; }
    public int getTotalPages() { return totalPages; }
}
