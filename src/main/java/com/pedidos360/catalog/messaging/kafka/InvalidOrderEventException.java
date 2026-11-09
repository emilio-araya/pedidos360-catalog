package com.pedidos360.catalog.messaging.kafka;

/**
 * Evento de pedido que nunca podrá aplicarse, por más que se reintente: JSON
 * ilegible, `eventType` desconocido, `schemaVersion` no soportada o campos
 * obligatorios ausentes. El error handler lo manda directo a la DLT.
 */
public class InvalidOrderEventException extends RuntimeException {

    public InvalidOrderEventException(String message) {
        super(message);
    }

    public InvalidOrderEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
