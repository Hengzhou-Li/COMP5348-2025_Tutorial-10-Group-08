package com.example.bank.dto;

public class RefundResponseDto {

    private Integer customerId;
    private Integer orderId;
    private Integer paymentId;
    private Integer refundId;
    private String status;
    private String bankRefundReference;

    public Integer getOrderId() {
        return orderId;
    }

    public void setOrderId(Integer orderId) {
        this.orderId = orderId;
    }

    public Integer getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(Integer paymentId) {
        this.paymentId = paymentId;
    }

    public Integer getRefundId() {
        return refundId;
    }

    public void setRefundId(Integer refundId) {
        this.refundId = refundId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getBankRefundReference() {
        return bankRefundReference;
    }

    public void setBankRefundReference(String bankRefundReference) {
        this.bankRefundReference = bankRefundReference;
    }
}
