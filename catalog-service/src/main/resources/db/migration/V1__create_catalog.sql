CREATE TABLE categories (
    id          UUID PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    slug        VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(1000)
);

CREATE TABLE products (
    id          UUID PRIMARY KEY,
    sku         VARCHAR(64)    NOT NULL UNIQUE,
    name        VARCHAR(255)   NOT NULL,
    description VARCHAR(2000),
    price       NUMERIC(19, 2) NOT NULL CHECK (price > 0),
    image_url   VARCHAR(1024),
    category_id UUID           NOT NULL REFERENCES categories (id),
    active      BOOLEAN        NOT NULL DEFAULT TRUE,
    version     BIGINT         NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX idx_products_category_id ON products (category_id) WHERE active;
CREATE INDEX idx_products_name ON products (lower(name));

-- Transactional outbox (see common-library EventPublisher / OutboxRelay)
CREATE TABLE outbox (
    id           BIGSERIAL PRIMARY KEY,
    topic        VARCHAR(255) NOT NULL,
    message_key  VARCHAR(255),
    payload_type VARCHAR(255) NOT NULL,
    payload      JSONB        NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE processed_events (
    event_id     UUID         NOT NULL,
    consumer     VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (event_id, consumer)
);
