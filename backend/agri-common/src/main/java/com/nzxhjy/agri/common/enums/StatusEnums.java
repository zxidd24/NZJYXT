package com.nzxhjy.agri.common.enums;

public final class StatusEnums {
    private StatusEnums() {
    }

    public enum OrderStatus { PENDING_PAYMENT(0), PENDING_AUDIT(1), PENDING_SHIPMENT(2), PENDING_RECEIPT(3), COMPLETED(4), CANCELLED(5), REFUNDING(6), REFUNDED(7);
        public final int value; OrderStatus(int value) { this.value = value; } }
    public enum PayStatus { UNPAID(0), PAID(1); public final int value; PayStatus(int value) { this.value = value; } }
    public enum PayMethod { WALLET, BANK_TRANSFER, CREDIT }
    public enum ProductStatus { PENDING_AUDIT(0), ON_SALE(1), OFF_SALE(2), REJECTED(3);
        public final int value; ProductStatus(int value) { this.value = value; } }
    public enum AuthStatus { UNAUTHENTICATED(0), REVIEWING(1), VERIFIED(2), REJECTED(3);
        public final int value; AuthStatus(int value) { this.value = value; } }
    public enum RefundStatus { PENDING_AUDIT(0), APPROVED(1), REJECTED(2), REFUNDED(3);
        public final int value; RefundStatus(int value) { this.value = value; } }
    public enum LoanStatus { APPLYING(0), RELEASED(1), REPAID(2), REJECTED(3);
        public final int value; LoanStatus(int value) { this.value = value; } }
    public enum InvoiceStatus { PENDING(0), ISSUED(1), REJECTED(2);
        public final int value; InvoiceStatus(int value) { this.value = value; } }
    public enum AuditStatus { PENDING(0), APPROVED(1), REJECTED(2);
        public final int value; AuditStatus(int value) { this.value = value; } }
    public enum AuditBizType { AUTH(1), PRODUCT_SHELF(2), PRODUCT_PRICE_STOCK(3), ORDER(4), REFUND(5), LOAN(6);
        public final int value; AuditBizType(int value) { this.value = value; } }
    public enum WalletTransactionType { DEPOSIT(1), WITHDRAW(2), PAYMENT(3), REFUND(4);
        public final int value; WalletTransactionType(int value) { this.value = value; } }
    public enum WalletDirection { IN(1), OUT(2); public final int value; WalletDirection(int value) { this.value = value; } }
    public enum OrderAttachmentType { CUSTOMER_RECEIPT(1), SIGNED_DELIVERY(2);
        public final int value; OrderAttachmentType(int value) { this.value = value; } }
    public enum WalletTransactionStatus { PROCESSING(0), SUCCESS(1), FAILED(2);
        public final int value; WalletTransactionStatus(int value) { this.value = value; } }
    public enum MessageReadStatus { UNREAD(0), READ(1); public final int value; MessageReadStatus(int value) { this.value = value; } }
    public enum UserStatus { DISABLED(0), ENABLED(1); public final int value; UserStatus(int value) { this.value = value; } }
    public enum ProductCategoryStatus { DISABLED(0), ENABLED(1); public final int value; ProductCategoryStatus(int value) { this.value = value; } }
    public enum ArticlePublishStatus { DRAFT(0), PUBLISHED(1); public final int value; ArticlePublishStatus(int value) { this.value = value; } }
}
