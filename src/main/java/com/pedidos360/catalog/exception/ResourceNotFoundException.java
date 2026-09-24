package com.pedidos360.catalog.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends CatalogException {

    public ResourceNotFoundException(String errorCode, String message) {
        super(HttpStatus.NOT_FOUND, errorCode, message);
    }
}
