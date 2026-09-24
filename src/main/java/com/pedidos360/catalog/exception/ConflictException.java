package com.pedidos360.catalog.exception;

import org.springframework.http.HttpStatus;

public class ConflictException extends CatalogException {

    public ConflictException(String errorCode, String message) {
        super(HttpStatus.CONFLICT, errorCode, message);
    }
}
