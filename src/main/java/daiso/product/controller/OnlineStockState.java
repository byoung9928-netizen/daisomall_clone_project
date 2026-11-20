package daiso.product.controller;

public enum OnlineStockState {
    ALL("전체", ""),
    NORMAL("정상", "p.status IN ('AVAILABLE','SOLD_OUT') AND p.stock_quantity > p.low_stock_threshold"),
    LOW("부족", "p.status IN ('AVAILABLE','SOLD_OUT') AND p.stock_quantity BETWEEN 1 AND p.low_stock_threshold"),
    SOLD_OUT("품절 (0개)", "p.status IN ('AVAILABLE','SOLD_OUT') AND p.stock_quantity = 0"),
    INACTIVE("발주 집계 제외", "p.status NOT IN ('AVAILABLE','SOLD_OUT')");

    public final String label;
    final String condition;

    OnlineStockState(String label, String condition) {
        this.label = label;
        this.condition = condition;
    }

    public static OnlineStockState of(ProductDTO product) {
        if (!"AVAILABLE".equals(product.getStatus()) && !"SOLD_OUT".equals(product.getStatus())) return INACTIVE;
        if (product.getStockQuantity() == 0) return SOLD_OUT;
        return product.getStockQuantity() <= product.getLowStockThreshold() ? LOW : NORMAL;
    }
}
