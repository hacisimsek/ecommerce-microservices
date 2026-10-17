CREATE TABLE shipments (
    id                      UUID PRIMARY KEY,
    order_id                UUID         NOT NULL,
    customer_id             UUID,
    correlation_id          UUID,
    status                  VARCHAR(32)  NOT NULL,
    tracking_number         VARCHAR(64),
    carrier_name            VARCHAR(64),
    shipped_date            TIMESTAMP(6),
    estimated_delivery_date TIMESTAMP(6),
    shipping_address        VARCHAR(255),
    recipient_name          VARCHAR(255),
    recipient_phone         VARCHAR(64),
    created_at              TIMESTAMP(6) NOT NULL,
    created_by              VARCHAR(255),
    last_modified_at        TIMESTAMP(6),
    last_modified_by        VARCHAR(255),
    -- One shipment per order: backs the consumer's idempotency check under concurrent redelivery
    CONSTRAINT uk_shipments_order_id UNIQUE (order_id)
);

CREATE INDEX idx_shipments_customer_id ON shipments (customer_id);
CREATE UNIQUE INDEX uk_shipments_tracking_number ON shipments (tracking_number);
