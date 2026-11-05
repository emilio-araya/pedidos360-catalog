package com.pedidos360.catalog.messaging.kafka;

/** Nombres compartidos con `orders` e infra (contrato S2). */
public final class KafkaTopics {

    public static final String ORDER_EVENTS = "pedidos360.order-events";
    public static final String ORDER_EVENTS_DLT = ORDER_EVENTS + ".DLT";
    public static final String SALES_PROJECTION_GROUP = "catalog-sales-projection";

    private KafkaTopics() {
    }
}
