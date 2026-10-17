CREATE TABLE payments (
    id             UUID PRIMARY KEY,
    order_id       UUID           NOT NULL,
    customer_id    UUID,
    correlation_id UUID,
    amount         NUMERIC(19, 2) NOT NULL,
    status         VARCHAR(32)    NOT NULL,
    transaction_id VARCHAR(64),
    payment_method VARCHAR(32),
    payment_date   TIMESTAMP(6),
    created_at     TIMESTAMP(6)   NOT NULL,
    created_by     VARCHAR(255),
    -- One payment per order: backs the consumer's idempotency check under concurrent redelivery
    CONSTRAINT uk_payments_order_id UNIQUE (order_id)
);

CREATE INDEX idx_payments_customer_id ON payments (customer_id);
