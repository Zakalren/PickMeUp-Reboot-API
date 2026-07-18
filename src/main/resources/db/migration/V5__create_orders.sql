-- Order (checkout) domain. Table is named "orders" because ORDER is a MySQL
-- reserved word. order_items snapshots product name/price at purchase time so
-- order history survives catalog edits; the product FK is kept for traceability
-- but nulls out on product deletion instead of blocking it.

CREATE TABLE orders
(
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    user_id     BIGINT      NOT NULL,
    total_price INT         NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_orders_user (user_id),
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB;

CREATE TABLE order_items
(
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    order_id     BIGINT       NOT NULL,
    product_id   BIGINT       NULL,
    product_name VARCHAR(100) NOT NULL,
    price        INT          NOT NULL,
    quantity     INT          NOT NULL,
    PRIMARY KEY (id),
    KEY idx_order_items_order (order_id),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE SET NULL
) ENGINE = InnoDB;
