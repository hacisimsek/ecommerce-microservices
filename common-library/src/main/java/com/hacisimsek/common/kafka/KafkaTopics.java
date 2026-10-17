package com.hacisimsek.common.kafka;

public final class KafkaTopics {

    public static final String ORDER_EVENTS = "order-events";
    public static final String INVENTORY_EVENTS = "inventory-events";
    public static final String PAYMENT_EVENTS = "payment-events";
    public static final String SHIPPING_EVENTS = "shipping-events";

    static final String[] ALL = {ORDER_EVENTS, INVENTORY_EVENTS, PAYMENT_EVENTS, SHIPPING_EVENTS};

    /** Dead letter topic for a saga topic: {@code <topic>.DLT}. */
    public static final String DLT_SUFFIX = ".DLT";

    private KafkaTopics() {
    }
}
