-- Demo catalog. Each product also gets a ProductCreatedEvent in the outbox, exactly as the API
-- would write it, so inventory creates the matching stock records when the relay runs.

INSERT INTO categories (id, name, slug, description) VALUES
    (gen_random_uuid(), 'Electronics', 'electronics', 'Phones, audio and accessories'),
    (gen_random_uuid(), 'Books', 'books', 'Fiction and non-fiction'),
    (gen_random_uuid(), 'Home & Kitchen', 'home-kitchen', 'Cookware, appliances and decor'),
    (gen_random_uuid(), 'Sports & Outdoors', 'sports-outdoors', 'Gear for training and the outdoors');

INSERT INTO products (id, sku, name, description, price, image_url, category_id)
SELECT gen_random_uuid(), p.sku, p.name, p.description, p.price,
       'https://picsum.photos/seed/' || lower(p.sku) || '/600/600', c.id
FROM (VALUES
    ('ELEC-HEADPHONES-01', 'Wireless Noise-Cancelling Headphones', 'Over-ear, 30h battery, active noise cancelling.', 249.00, 'electronics'),
    ('ELEC-KEYBOARD-01', 'Mechanical Keyboard', 'Tenkeyless, hot-swappable switches, RGB backlight.', 119.90, 'electronics'),
    ('ELEC-SPEAKER-01', 'Portable Bluetooth Speaker', 'Waterproof IP67 speaker with 12h playtime.', 79.50, 'electronics'),
    ('ELEC-CHARGER-01', '65W USB-C Charger', 'GaN charger with two USB-C ports.', 39.99, 'electronics'),
    ('BOOK-DDIA-01', 'Designing Data-Intensive Applications', 'The big ideas behind reliable, scalable systems.', 45.00, 'books'),
    ('BOOK-MICRO-01', 'Building Microservices', 'Designing fine-grained systems, 2nd edition.', 42.50, 'books'),
    ('BOOK-NOVEL-01', 'The Midnight Library', 'A novel about the choices that make a life.', 16.99, 'books'),
    ('HOME-PAN-01', 'Cast Iron Skillet 28cm', 'Pre-seasoned, oven safe.', 34.90, 'home-kitchen'),
    ('HOME-COFFEE-01', 'Pour-Over Coffee Set', 'Glass dripper, carafe and 100 filters.', 29.00, 'home-kitchen'),
    ('HOME-KNIFE-01', 'Chef Knife 20cm', 'Forged stainless steel with full tang.', 59.00, 'home-kitchen'),
    ('SPRT-YOGA-01', 'Non-Slip Yoga Mat', '6mm thick, carrying strap included.', 27.50, 'sports-outdoors'),
    ('SPRT-BOTTLE-01', 'Insulated Water Bottle 750ml', 'Keeps drinks cold for 24h.', 24.00, 'sports-outdoors')
) AS p (sku, name, description, price, category_slug)
JOIN categories c ON c.slug = p.category_slug;

INSERT INTO outbox (topic, message_key, payload_type, payload)
SELECT 'catalog-events', p.id::text, 'com.hacisimsek.common.event.catalog.ProductCreatedEvent',
       jsonb_build_object('eventId', gen_random_uuid(), 'correlationId', gen_random_uuid(),
                          'timestamp', to_char(now() AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
                          'productId', p.id, 'sku', p.sku, 'name', p.name, 'price', p.price)
FROM products p
ORDER BY p.sku;
