-- ============================================
-- CMT Trading Engine - Database Schema
-- Drop & Recreate (idempotent)
-- ============================================

CREATE DATABASE IF NOT EXISTS trading_system;
USE trading_system;

-- ============================================
-- 1. Security Master (Reference Data)
-- ============================================
DROP TABLE IF EXISTS security_master;
CREATE TABLE security_master (
    symbol          VARCHAR(20) PRIMARY KEY,
    security_type   VARCHAR(10)  NOT NULL,
    description     VARCHAR(100),
    underlying      VARCHAR(20),
    lot_size        INT          NOT NULL DEFAULT 1
);

-- Seed data
INSERT INTO security_master (symbol, security_type, description, underlying, lot_size) VALUES
    ('GOOG', 'CS', 'Alphabet Inc.', 'GOOG', 1),
    ('MSFT', 'CS', 'Microsoft Corp.', 'MSFT', 1),
    ('IBM',  'CS', 'IBM Corp.', 'IBM', 1),
    ('AAPL', 'CS', 'Apple Inc.', 'AAPL', 1),
    ('AMZN', 'CS', 'Amazon.com Inc.', 'AMZN', 1);

-- ============================================
-- 2. Customer Master (Reference Data)
-- ============================================
DROP TABLE IF EXISTS customer_master;
CREATE TABLE customer_master (
    customer_code   VARCHAR(20) PRIMARY KEY,
    customer_name   VARCHAR(100) NOT NULL,
    customer_type   VARCHAR(20)  NOT NULL,
    credit_limit    DECIMAL(15,2) DEFAULT 0.00
);

INSERT INTO customer_master (customer_code, customer_name, customer_type, credit_limit) VALUES
    ('CUST001', 'Hedge Fund Alpha', 'INSTITUTIONAL', 10000000.00),
    ('CUST002', 'Retail Trader Bob', 'RETAIL', 100000.00),
    ('CUST003', 'Market Maker LLC', 'MARKET_MAKER', 50000000.00);

-- ============================================
-- 3. Orders Table
--    order_id:    BIGINT (timestamp * 10000 + sequence)
--    cl_ord_id:   BIGINT (from MiniFIX client)
-- ============================================
DROP TABLE IF EXISTS executions;   -- drop child first (FK dependency)
DROP TABLE IF EXISTS orders;

CREATE TABLE orders (
    order_id          BIGINT       PRIMARY KEY,
    cl_ord_id         BIGINT       NOT NULL,
    symbol            VARCHAR(20)  NOT NULL,
    side              CHAR(1)      NOT NULL COMMENT '1=BUY, 2=SELL',
    price             DOUBLE       NOT NULL,
    quantity          DOUBLE       NOT NULL COMMENT 'Remaining quantity',
    original_quantity DOUBLE       NOT NULL COMMENT 'Original quantity at order creation',
    status            VARCHAR(20)  NOT NULL DEFAULT 'NEW' COMMENT 'NEW, PARTIALLY_FILLED, FILLED',
    timestamp_micros  BIGINT       NOT NULL COMMENT 'Epoch microseconds',

    INDEX idx_orders_status (status),
    INDEX idx_orders_symbol (symbol),
    INDEX idx_orders_cl_ord (cl_ord_id)
);

-- ============================================
-- 4. Executions Table
--    exec_id:       BIGINT (timestamp * 10000 + sequence)
--    buy_order_id:  FK → orders.order_id
--    sell_order_id: FK → orders.order_id
-- ============================================
CREATE TABLE executions (
    exec_id           BIGINT       PRIMARY KEY,
    buy_order_id      BIGINT       NOT NULL,
    sell_order_id     BIGINT       NOT NULL,
    symbol            VARCHAR(20)  NOT NULL,
    side              CHAR(1)      NOT NULL COMMENT 'Aggressor side: 1=BUY, 2=SELL',
    exec_qty          INT          NOT NULL,
    exec_price        DOUBLE       NOT NULL,
    match_time_micros BIGINT       NOT NULL COMMENT 'Epoch microseconds',

    CONSTRAINT fk_exec_buy  FOREIGN KEY (buy_order_id)  REFERENCES orders(order_id),
    CONSTRAINT fk_exec_sell FOREIGN KEY (sell_order_id) REFERENCES orders(order_id),
    INDEX idx_exec_symbol (symbol),
    INDEX idx_exec_time (match_time_micros)
);
