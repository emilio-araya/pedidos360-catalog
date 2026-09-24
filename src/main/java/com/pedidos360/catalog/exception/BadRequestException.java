package com.pedidos360.catalog.exception;

import org.springframework.http.HttpStatus;

public class BadRequestException extends CatalogException {

    public BadRequestException(String errorCode, String message) {
        super(HttpStatus.BAD_REQUEST, errorCode, message);
    }
}
