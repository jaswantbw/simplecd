package com.simplecd.model;

public class ProviderVerificationResult {
    private boolean success;
    private int statusCode;
    private String message;
    private String providerType;

    public ProviderVerificationResult() {
    }

    public ProviderVerificationResult(boolean success, int statusCode, String message, String providerType) {
        this.success = success;
        this.statusCode = statusCode;
        this.message = message;
        this.providerType = providerType;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getProviderType() {
        return providerType;
    }

    public void setProviderType(String providerType) {
        this.providerType = providerType;
    }
}
