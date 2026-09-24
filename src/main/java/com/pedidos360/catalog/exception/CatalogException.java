package com.pedidos360.catalog.exception;

import org.springframework.http.HttpStatus;

public class CatalogException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public CatalogException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
