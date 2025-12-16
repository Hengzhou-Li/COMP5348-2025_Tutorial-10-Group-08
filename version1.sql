-- =========================================================
-- STORE SYSTEM DATABASE SCHEMA (SERIAL VERSION)
-- =========================================================
-- Each table uses SERIAL (auto-increment integer IDs)
-- instead of UUIDs. Suitable for local / single-node DBs.
-- =========================================================

-- ----------------------------
-- 1. CUSTOMER & AUTH
-- ----------------------------
CREATE TABLE customer (
    customer_id SERIAL PRIMARY KEY,
    full_name   VARCHAR(100) NOT NULL,
    email       VARCHAR(100) UNIQUE NOT NULL,
    phone       VARCHAR(30),
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE auth_account (
    account_id   SERIAL PRIMARY KEY,
    customer_id  INT REFERENCES customer(customer_id) ON DELETE CASCADE,
    username     VARCHAR(50) UNIQUE NOT NULL,
    password_hash TEXT NOT NULL,
    last_login_at TIMESTAMP
);

-- ----------------------------
-- 2. PRODUCT & WAREHOUSE
-- ----------------------------
CREATE TABLE product (
    product_id  SERIAL PRIMARY KEY,
    sku         VARCHAR(50) UNIQUE NOT NULL,
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    unit_price  DECIMAL(10,2) NOT NULL CHECK (unit_price >= 0),
    is_active   BOOLEAN DEFAULT TRUE
);

CREATE TABLE warehouse (
    warehouse_id SERIAL PRIMARY KEY,
    name         VARCHAR(100) NOT NULL,
    address      TEXT NOT NULL
);

-- ----------------------------
-- 3. STOCK CONTROL
-- ----------------------------
CREATE TABLE warehouse_stock (
    warehouse_id INT REFERENCES warehouse(warehouse_id) ON DELETE CASCADE,
    product_id   INT REFERENCES product(product_id) ON DELETE CASCADE,
    qty_on_hand  INT DEFAULT 0 CHECK (qty_on_hand >= 0),
    qty_reserved INT DEFAULT 0 CHECK (qty_reserved >= 0),
    PRIMARY KEY (warehouse_id, product_id)
);

CREATE TABLE stock_ledger (
    ledger_id    SERIAL PRIMARY KEY,
    warehouse_id INT REFERENCES warehouse(warehouse_id),
    product_id   INT REFERENCES product(product_id),
    reason       VARCHAR(30) NOT NULL, -- ALLOCATE, PICK, CANCEL, REFUND, ADJUST
    qty_delta    INT NOT NULL,
    order_id     INT NULL,
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ----------------------------
-- 4. ORDER & ITEMS
-- ----------------------------
CREATE TABLE orders (
    order_id    SERIAL PRIMARY KEY,
    customer_id INT REFERENCES customer(customer_id),
    order_total DECIMAL(10,2) NOT NULL,
    status      VARCHAR(20) DEFAULT 'NEW',
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE order_item (
    order_id   INT REFERENCES orders(order_id) ON DELETE CASCADE,
    product_id INT REFERENCES product(product_id),
    qty        INT CHECK (qty > 0),
    unit_price DECIMAL(10,2) CHECK (unit_price >= 0),
    PRIMARY KEY (order_id, product_id)
);

-- ----------------------------
-- 5. FULFILLMENT
-- ----------------------------
CREATE TABLE fulfillment (
    fulfillment_id SERIAL PRIMARY KEY,
    order_id       INT REFERENCES orders(order_id),
    warehouse_id   INT REFERENCES warehouse(warehouse_id),
    status         VARCHAR(30) DEFAULT 'ALLOCATED',
    allocated_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE fulfillment_item (
    fulfillment_id INT REFERENCES fulfillment(fulfillment_id) ON DELETE CASCADE,
    product_id     INT REFERENCES product(product_id),
    qty_picked     INT CHECK (qty_picked >= 0),
    PRIMARY KEY (fulfillment_id, product_id)
);

-- ----------------------------
-- 6. PAYMENT & REFUND
-- ----------------------------
CREATE TABLE payment (
    payment_id   SERIAL PRIMARY KEY,
    order_id     INT UNIQUE REFERENCES orders(order_id) ON DELETE CASCADE,
    amount       DECIMAL(10,2) CHECK (amount >= 0),
    bank_txn_ref VARCHAR(100) UNIQUE,
    status       VARCHAR(20) DEFAULT 'PENDING',
    requested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    confirmed_at TIMESTAMP
);

CREATE TABLE refund (
    refund_id       SERIAL PRIMARY KEY,
    order_id        INT REFERENCES orders(order_id) ON DELETE CASCADE,
    amount          DECIMAL(10,2) CHECK (amount >= 0),
    bank_refund_ref VARCHAR(100),
    status          VARCHAR(20) DEFAULT 'PENDING',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ----------------------------
-- 7. DELIVERY & EVENTS
-- ----------------------------
CREATE TABLE delivery (
    delivery_id   SERIAL PRIMARY KEY,
    order_id      INT UNIQUE REFERENCES orders(order_id) ON DELETE CASCADE,
    carrier       VARCHAR(50),
    tracking_code VARCHAR(100),
    status        VARCHAR(30) DEFAULT 'RECEIVED',
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE delivery_event (
    event_id    SERIAL PRIMARY KEY,
    delivery_id INT REFERENCES delivery(delivery_id) ON DELETE CASCADE,
    status      VARCHAR(30),
    details     TEXT,
    event_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ----------------------------
-- 8. EMAIL NOTIFICATION
-- ----------------------------
CREATE TABLE email_notification (
    email_id   SERIAL PRIMARY KEY,
    order_id   INT NULL REFERENCES orders(order_id) ON DELETE SET NULL,
    event_id   INT NULL REFERENCES delivery_event(event_id) ON DELETE SET NULL,
    to_addr    VARCHAR(100) NOT NULL,
    subject    VARCHAR(200),
    body       TEXT,
    send_status VARCHAR(20) DEFAULT 'PENDING',
    sent_at     TIMESTAMP
);
-- ----------------------------
-- 9. OUTBOX
-- ----------------------------
CREATE TABLE outbox (
    event_id       SERIAL PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id   INT NOT NULL,
    event_type     VARCHAR(50) NOT NULL,
    payload        TEXT,
    correlation_id VARCHAR(100) NOT NULL,
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    publish_at     TIMESTAMP
);
