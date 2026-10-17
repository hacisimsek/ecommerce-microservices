CREATE TABLE orders (
    id               UUID PRIMARY KEY,
    customer_id      UUID           NOT NULL,
    total_amount     NUMERIC(19, 2) NOT NULL,
    status           VARCHAR(32)    NOT NULL,
    created_at       TIMESTAMP(6)   NOT NULL,
    created_by       VARCHAR(255),
    last_modified_at TIMESTAMP(6),
    last_modified_by VARCHAR(255)
);

CREATE INDEX idx_orders_customer_id ON orders (customer_id);

CREATE TABLE order_items (
    id           UUID PRIMARY KEY,
    order_id     UUID           NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    product_id   UUID           NOT NULL,
    product_name VARCHAR(255),
    quantity     INTEGER        NOT NULL CHECK (quantity > 0),
    price        NUMERIC(19, 2) NOT NULL
);

CREATE INDEX idx_order_items_order_id ON order_items (order_id);
