-- Peak Finds schema (SQLite). Merged from the SDS class attributes.
-- Safe to run repeatedly: every statement uses IF NOT EXISTS.
-- Status/role text must match peakfinds.Constants exactly (a test checks this).
-- Dates are stored as ISO text: 'YYYY-MM-DD HH:MM:SS' (SQLite has no date type).

-- ---------- Login accounts (one row per person who can sign in) ----------
CREATE TABLE IF NOT EXISTS users (
    user_id       INTEGER PRIMARY KEY AUTOINCREMENT,
    email         TEXT NOT NULL UNIQUE COLLATE NOCASE,
    password_hash TEXT NOT NULL,
    role          TEXT NOT NULL CHECK (role IN ('owner', 'customer', 'bearer')),
    is_active     INTEGER NOT NULL DEFAULT 1 CHECK (is_active IN (0, 1)),
    created_at    TEXT NOT NULL DEFAULT (datetime('now', 'localtime'))
);

-- Assumption A-1: exactly one owner account.
CREATE UNIQUE INDEX IF NOT EXISTS ux_single_owner ON users(role) WHERE role = 'owner';

-- ---------- Customer Management ----------
CREATE TABLE IF NOT EXISTS customers (
    customer_id      INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id          INTEGER NOT NULL UNIQUE REFERENCES users(user_id),
    name             TEXT NOT NULL,
    phone_number     TEXT NOT NULL,
    delivery_address TEXT,
    is_active        INTEGER NOT NULL DEFAULT 1 CHECK (is_active IN (0, 1)),
    created_at       TEXT NOT NULL DEFAULT (datetime('now', 'localtime'))
);

-- ---------- Delivery Management: bearers ----------
CREATE TABLE IF NOT EXISTS bearers (
    bearer_id    INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id      INTEGER NOT NULL UNIQUE REFERENCES users(user_id),
    name         TEXT NOT NULL,
    phone_number TEXT NOT NULL,
    is_active    INTEGER NOT NULL DEFAULT 1 CHECK (is_active IN (0, 1))
);

-- ---------- Supplier Management ----------
CREATE TABLE IF NOT EXISTS suppliers (
    supplier_id  INTEGER PRIMARY KEY AUTOINCREMENT,
    name         TEXT NOT NULL,
    phone_number TEXT NOT NULL,
    email        TEXT NOT NULL,
    location     TEXT NOT NULL,
    is_active    INTEGER NOT NULL DEFAULT 1 CHECK (is_active IN (0, 1))   -- 0 = removed, history kept
);

-- ---------- Product Management ----------
CREATE TABLE IF NOT EXISTS products (
    product_id     INTEGER PRIMARY KEY AUTOINCREMENT,
    name           TEXT NOT NULL,
    description    TEXT,
    colour         TEXT,
    category       TEXT NOT NULL,
    price          REAL NOT NULL CHECK (price >= 0),
    stock_quantity INTEGER NOT NULL DEFAULT 0 CHECK (stock_quantity >= 0),
    reorder_level  INTEGER NOT NULL DEFAULT 0 CHECK (reorder_level >= 0),
    is_active      INTEGER NOT NULL DEFAULT 1 CHECK (is_active IN (0, 1)), -- 0 = removed, past orders keep it
    created_at     TEXT NOT NULL DEFAULT (datetime('now', 'localtime'))
);

CREATE TABLE IF NOT EXISTS product_images (
    image_id   INTEGER PRIMARY KEY AUTOINCREMENT,
    product_id INTEGER NOT NULL REFERENCES products(product_id),
    image_path TEXT NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS product_suppliers (
    product_id  INTEGER NOT NULL REFERENCES products(product_id),
    supplier_id INTEGER NOT NULL REFERENCES suppliers(supplier_id),
    PRIMARY KEY (product_id, supplier_id)
);

CREATE TABLE IF NOT EXISTS restock_orders (
    restock_id    INTEGER PRIMARY KEY AUTOINCREMENT,
    supplier_id   INTEGER NOT NULL REFERENCES suppliers(supplier_id),
    order_date    TEXT NOT NULL DEFAULT (datetime('now', 'localtime')),
    status        TEXT NOT NULL DEFAULT 'Ordered' CHECK (status IN ('Ordered', 'Received')),
    received_date TEXT
);

CREATE TABLE IF NOT EXISTS restock_order_items (
    restock_id INTEGER NOT NULL REFERENCES restock_orders(restock_id),
    product_id INTEGER NOT NULL REFERENCES products(product_id),
    quantity   INTEGER NOT NULL CHECK (quantity > 0),
    PRIMARY KEY (restock_id, product_id)
);

-- ---------- Sales Processing: cart ----------
CREATE TABLE IF NOT EXISTS carts (
    cart_id        INTEGER PRIMARY KEY AUTOINCREMENT,
    customer_id    INTEGER NOT NULL UNIQUE REFERENCES customers(customer_id),
    payment_method TEXT CHECK (payment_method IN ('Cash on Delivery', 'Bank Transfer')),
    updated_at     TEXT NOT NULL DEFAULT (datetime('now', 'localtime'))
);

CREATE TABLE IF NOT EXISTS cart_items (
    cart_id    INTEGER NOT NULL REFERENCES carts(cart_id),
    product_id INTEGER NOT NULL REFERENCES products(product_id),
    quantity   INTEGER NOT NULL CHECK (quantity > 0),
    PRIMARY KEY (cart_id, product_id)
);

-- ---------- Order Management ----------
CREATE TABLE IF NOT EXISTS orders (
    order_id         INTEGER PRIMARY KEY AUTOINCREMENT,
    customer_id      INTEGER NOT NULL REFERENCES customers(customer_id),
    customer_name    TEXT NOT NULL,   -- snapshot, so the order still reads correctly if the customer is deleted
    order_date       TEXT NOT NULL DEFAULT (datetime('now', 'localtime')),
    delivery_address TEXT NOT NULL,
    contact_number   TEXT NOT NULL,
    total_amount     REAL NOT NULL CHECK (total_amount >= 0),
    payment_method   TEXT NOT NULL CHECK (payment_method IN ('Cash on Delivery', 'Bank Transfer')),
    payment_status   TEXT NOT NULL DEFAULT 'Pending' CHECK (payment_status IN ('Pending', 'Paid')),
    order_status     TEXT NOT NULL CHECK (order_status IN
                       ('Awaiting Payment', 'Order Confirmed', 'Order Packaging', 'Cancelled'))
);

CREATE TABLE IF NOT EXISTS order_items (
    order_id     INTEGER NOT NULL REFERENCES orders(order_id),
    product_id   INTEGER NOT NULL REFERENCES products(product_id),
    product_name TEXT NOT NULL,   -- snapshots so removed products still show on past orders
    unit_price   REAL NOT NULL CHECK (unit_price >= 0),
    quantity     INTEGER NOT NULL CHECK (quantity > 0),
    PRIMARY KEY (order_id, product_id)
);

CREATE TABLE IF NOT EXISTS order_status_history (
    history_id INTEGER PRIMARY KEY AUTOINCREMENT,
    order_id   INTEGER NOT NULL REFERENCES orders(order_id),
    status     TEXT NOT NULL,
    changed_at TEXT NOT NULL DEFAULT (datetime('now', 'localtime')),
    changed_by INTEGER REFERENCES users(user_id),
    note       TEXT
);

-- ---------- Delivery Management ----------
CREATE TABLE IF NOT EXISTS deliveries (
    delivery_id       INTEGER PRIMARY KEY AUTOINCREMENT,
    order_id          INTEGER NOT NULL UNIQUE REFERENCES orders(order_id),
    bearer_id         INTEGER REFERENCES bearers(bearer_id),   -- NULL when Knutsford Express delivers
    delivery_method   TEXT NOT NULL CHECK (delivery_method IN ('Bearer', 'Knutsford Express')),
    delivery_address  TEXT NOT NULL,
    delivery_fee      REAL NOT NULL DEFAULT 0 CHECK (delivery_fee >= 0),
    delivery_status   TEXT NOT NULL DEFAULT 'Assigned' CHECK (delivery_status IN
                        ('Assigned', 'Out for Delivery', 'Delivered', 'Failed', 'Returned')),
    failure_reason    TEXT,
    bill_number       TEXT,   -- Knutsford waybill number
    expected_date     TEXT,
    payment_collected REAL NOT NULL DEFAULT 0 CHECK (payment_collected >= 0),
    CHECK ((delivery_method = 'Bearer' AND bearer_id IS NOT NULL)
        OR (delivery_method = 'Knutsford Express' AND bearer_id IS NULL))
);

CREATE TABLE IF NOT EXISTS delivery_status_history (
    history_id  INTEGER PRIMARY KEY AUTOINCREMENT,
    delivery_id INTEGER NOT NULL REFERENCES deliveries(delivery_id),
    status      TEXT NOT NULL,
    reason      TEXT,
    changed_at  TEXT NOT NULL DEFAULT (datetime('now', 'localtime')),
    changed_by  INTEGER REFERENCES users(user_id)
);

-- Every reassignment is recorded here (SRS Feature 06 UR1 SR-2).
CREATE TABLE IF NOT EXISTS delivery_assignment_log (
    log_id          INTEGER PRIMARY KEY AUTOINCREMENT,
    delivery_id     INTEGER NOT NULL REFERENCES deliveries(delivery_id),
    previous_bearer INTEGER REFERENCES bearers(bearer_id),
    previous_method TEXT,
    changed_at      TEXT NOT NULL DEFAULT (datetime('now', 'localtime')),
    changed_by      INTEGER REFERENCES users(user_id)
);

-- ---------- Notifications ----------
CREATE TABLE IF NOT EXISTS email_log (
    log_id     INTEGER PRIMARY KEY AUTOINCREMENT,
    recipient  TEXT NOT NULL,
    subject    TEXT NOT NULL,
    order_id   INTEGER REFERENCES orders(order_id),
    status     TEXT NOT NULL CHECK (status IN ('Sent', 'Failed')),
    error      TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now', 'localtime'))
);

CREATE TABLE IF NOT EXISTS notifications (
    notification_id INTEGER PRIMARY KEY AUTOINCREMENT,
    kind            TEXT NOT NULL,       -- e.g. 'low_stock', 'email_failed'
    message         TEXT NOT NULL,
    product_id      INTEGER REFERENCES products(product_id),
    is_read         INTEGER NOT NULL DEFAULT 0 CHECK (is_read IN (0, 1)),
    created_at      TEXT NOT NULL DEFAULT (datetime('now', 'localtime'))
);

-- ---------- Indexes ----------
CREATE INDEX IF NOT EXISTS ix_orders_customer ON orders(customer_id);
CREATE INDEX IF NOT EXISTS ix_orders_status ON orders(order_status);
CREATE INDEX IF NOT EXISTS ix_deliveries_bearer ON deliveries(bearer_id);
CREATE INDEX IF NOT EXISTS ix_deliveries_status ON deliveries(delivery_status);
CREATE INDEX IF NOT EXISTS ix_products_active ON products(is_active);
CREATE INDEX IF NOT EXISTS ix_restock_supplier ON restock_orders(supplier_id);

-- ---------- Business-rule guards (so the limits hold even if a page forgets) ----------
CREATE TRIGGER IF NOT EXISTS trg_max_bearers_insert
BEFORE INSERT ON bearers
WHEN NEW.is_active = 1 AND (SELECT COUNT(*) FROM bearers WHERE is_active = 1) >= 5
BEGIN
    SELECT RAISE(ABORT, 'A maximum of 5 bearers can be active at once.');
END;

CREATE TRIGGER IF NOT EXISTS trg_max_bearers_update
BEFORE UPDATE OF is_active ON bearers
WHEN NEW.is_active = 1 AND OLD.is_active = 0 AND (SELECT COUNT(*) FROM bearers WHERE is_active = 1) >= 5
BEGIN
    SELECT RAISE(ABORT, 'A maximum of 5 bearers can be active at once.');
END;

CREATE TRIGGER IF NOT EXISTS trg_max_images
BEFORE INSERT ON product_images
WHEN (SELECT COUNT(*) FROM product_images WHERE product_id = NEW.product_id) >= 5
BEGIN
    SELECT RAISE(ABORT, 'A product can have at most 5 images.');
END;

CREATE TRIGGER IF NOT EXISTS trg_max_products
BEFORE INSERT ON products
WHEN NEW.is_active = 1 AND (SELECT COUNT(*) FROM products WHERE is_active = 1) >= 100
BEGIN
    SELECT RAISE(ABORT, 'The catalogue is limited to 100 active products.');
END;
