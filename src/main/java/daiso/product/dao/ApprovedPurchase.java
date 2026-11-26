package daiso.product.dao;

final class ApprovedPurchase {
    // Queries sharing this policy use o for orders and p for payment_attempts.
    static final String PAYMENT = "o.canceled_at IS NULL "
            + "AND p.method = 'KAKAOPAY' AND p.status = 'APPROVED' "
            + "AND p.tid IS NOT NULL AND p.tid <> '' AND p.approved_at IS NOT NULL AND p.amount = o.total_amount";

    static final String CONDITION = "o.status = 'PAID' AND " + PAYMENT;

    private ApprovedPurchase() { }
}
