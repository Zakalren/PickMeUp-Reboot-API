-- Order cancellation: orders now carry a lifecycle status. Existing rows
-- backfill to PLACED — no order in the current schema can already be
-- cancelled. Cancellation is an atomic conditional UPDATE (status guard),
-- mirroring the stock-decrement pattern; the order row is never deleted so
-- history stays readable.
ALTER TABLE orders
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PLACED';
