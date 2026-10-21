package com.pedidos360.catalog.exception;

import org.springframework.http.HttpStatus;

/**
 * Parámetros de paginación fuera del rango permitido.
 *
 * <p>Se rechaza en lugar de recortar en silencio: un límite invisible hace que
 * el cliente crea que pidió algo distinto de lo que recibió.
 */
public class InvalidPageRequestException extends CatalogException {

    public InvalidPageRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, "INVALID_PAGINATION", message);
    }
}
