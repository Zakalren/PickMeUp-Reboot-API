-- Initial schema (MySQL 8), matching the JPA entities as of this migration.
-- Prod runs with ddl-auto: validate; all schema changes go through Flyway.

CREATE TABLE users
(
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    service_number   VARCHAR(20)  NOT NULL,
    encoded_password VARCHAR(255) NOT NULL,
    name             VARCHAR(50)  NOT NULL,
    affiliated_unit  VARCHAR(100) NULL,
    military_rank    VARCHAR(20)  NULL,
    date_of_birth    DATE         NULL,
    tel_number       VARCHAR(20)  NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY idx_users_service_number (service_number)
) ENGINE = InnoDB;

CREATE TABLE products
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    name       VARCHAR(100) NOT NULL,
    image_url  VARCHAR(500) NULL,
    price      INT          NOT NULL,
    category   VARCHAR(50)  NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB;

CREATE TABLE cart_items
(
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    product_id BIGINT      NOT NULL,
    quantity   INT         NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY idx_cart_items_user_product (user_id, product_id),
    CONSTRAINT fk_cart_items_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_cart_items_product FOREIGN KEY (product_id) REFERENCES products (id)
) ENGINE = InnoDB;