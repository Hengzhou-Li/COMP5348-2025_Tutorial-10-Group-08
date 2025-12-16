package com.example.bank.dto;

public class PaymentResponseDto {

    private Integer customerId;
    private Integer orderId;
    private Integer paymentId;
    private String status;
    private String bankTransactionReference;

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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getBankTransactionReference() {
        return bankTransactionReference;
    }

    public void setBankTransactionReference(String bankTransactionReference) {
        this.bankTransactionReference = bankTransactionReference;
    }
}
