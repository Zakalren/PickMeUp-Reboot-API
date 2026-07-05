-- Optimistic locking for cart items: concurrent quantity updates conflict
-- at commit instead of silently losing one side's change.
ALTER TABLE cart_items
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
