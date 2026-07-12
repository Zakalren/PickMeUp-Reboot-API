-- Existing rows are backfilled with 0: no inventory is promised until an
-- admin sets a real value. The application always writes stock explicitly.
ALTER TABLE products
    ADD COLUMN stock INT NOT NULL DEFAULT 0;
