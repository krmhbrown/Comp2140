-- Test data: 1 owner, 2 customers, 2 bearers, 12 products, 2 suppliers.
-- Every account's password is:  Password123!   (test data only, never use in production)
-- Product names and stock come from the owner's stock notes (SRS Appendix B1).
-- Only the Black Juicy price ($7500) is from the owner's Instagram post; the other prices are placeholders.

INSERT INTO users (user_id, email, password_hash, role) VALUES
 (1, 'owner@peakfinds.test',     'pbkdf2_sha256$600000$MakOgvo3GXx/TWTl8880gw==$wWQ7UiQ0kvmgclRVfIslU6WbjD/yoUTMng0htHgvbTY=', 'owner'),
 (2, 'customer1@example.test',   'pbkdf2_sha256$600000$SdQdSIWxYxlDeHzRlqKUhw==$9Udfa0J/gxv3yrcbYGDpj0Hhg4ub7fOgT7zOpQUlNHk=', 'customer'),
 (3, 'customer2@example.test',   'pbkdf2_sha256$600000$12Z7sguxpH7c1BIiAraOyg==$0/nLD9k5grxbsnA/gN14GdZZogN2NUeIstQ+n4eH410=', 'customer'),
 (4, 'bearer1@peakfinds.test',   'pbkdf2_sha256$600000$u7TtyybVaTSHm/2JM1np8A==$DIVuNIyj2jXRbwDOnnhG0Ayt1GZ5Tryq+Sl02VLRZIE=', 'bearer'),
 (5, 'bearer2@peakfinds.test',   'pbkdf2_sha256$600000$cSm70iN+RakQDJ5n4mp6Og==$d8gZkV8GUEsCF9CJe4CJvzfcBXqUC0mNm1ElukqRVjE=', 'bearer');

INSERT INTO customers (customer_id, user_id, name, phone_number, delivery_address) VALUES
 (1, 2, 'Aaliyah Test', '876-555-0101', '12 Hope Road, Kingston'),
 (2, 3, 'Brianna Test', '876-555-0102', '8 Waterford Drive, Portmore');

INSERT INTO bearers (bearer_id, user_id, name, phone_number) VALUES
 (1, 4, 'Bearer One', '876-555-0201'),
 (2, 5, 'Bearer Two', '876-555-0202');

INSERT INTO suppliers (supplier_id, name, phone_number, email, location) VALUES
 (1, 'Island Bags Wholesale', '876-555-0301', 'sales@islandbags.test', 'Kingston'),
 (2, 'Caribbean Fashion Imports', '876-555-0302', 'orders@cfi.test', 'Montego Bay');

-- Feature 5 (Product Management): the 12 bags from the owner's stock list (SRS Appendix B1).
-- Prices are the owner's; quantities are "Qty on Hand". Reorder levels are placeholders. Descriptions are placeholders until the owner supplies them.
INSERT INTO products (product_id, name, description, colour, category, price, stock_quantity, reorder_level) VALUES
 (1, 'Blush Pink Juicy Couture', 'Blush pink Juicy Couture backpack.', 'Blush Pink', 'Backpack', 7000, 0, 1),
 (2, 'Dark Pink Juicy C. Backpack', 'Dark pink Juicy backpack.', 'Dark Pink', 'Backpack', 10500, 0, 1),
 (3, 'Pink Juicy Shoulder Bag', 'Pink Juicy shoulder bag.', 'Pink', 'Shoulder Bag', 9500, 2, 1),
 (4, 'Jessica Simpson Backpack', 'Jessica Simpson backpack.', 'White', 'Backpack', 6500, 0, 1),
 (5, 'Basic Fashion Shoulder Bag', 'Basic fashion shoulder bag.', 'White', 'Shoulder Bag', 6500, 1, 1),
 (6, 'White Steve Madden Shoulder Bag + Coin Pouch & Adjustable Strap', 'White Steve Madden shoulder bag that comes with a coin pouch and an adjustable strap.', 'White', 'Shoulder Bag', 9000, 1, 1),
 (7, 'Karl Lagerfeld Paris Crossbody Bag', 'Karl Lagerfeld Paris crossbody bag.', 'White', 'Crossbody Bag', 13500, 1, 1),
 (8, 'DKNY Margot Crescent Crossbody Bag', 'DKNY Margot crescent crossbody bag.', 'White', 'Crossbody Bag', 14500, 1, 1),
 (9, 'Pink Juicy Crossbody Bag', 'Pink Juicy crossbody bag.', 'Pink', 'Crossbody Bag', 8500, 1, 1),
 (10, 'Sage Green Crossbody Bag + Card Holder', 'Sage green crossbody bag that comes with a card holder.', 'Sage Green', 'Crossbody Bag', 8500, 2, 1),
 (11, 'Black Juicy Crossbody Bag + Card Holder & Adjustable Strap', 'Black Juicy crossbody bag with a card holder and an adjustable strap.', 'Black', 'Crossbody Bag', 7500, 2, 1),
 (12, 'True Religion Denim Tote', 'True Religion denim tote.', 'Denim', 'Tote Bag', 12500, 0, 1);

INSERT INTO product_images (product_id, image_path, sort_order) VALUES
 (1, 'images/P001.jpeg', 0),
 (2, 'images/P002.jpeg', 0),
 (3, 'images/P003.jpeg', 0),
 (4, 'images/P004.jpeg', 0),
 (5, 'images/P005.jpeg', 0),
 (6, 'images/P006.jpeg', 0),
 (7, 'images/P007.jpeg', 0),
 (8, 'images/P008.jpeg', 0),
 (9, 'images/P009.jpeg', 0),
 (10, 'images/P010.jpeg', 0),
 (11, 'images/P011.jpeg', 0),
 (12, 'images/P012.jpeg', 0);

INSERT INTO product_suppliers (product_id, supplier_id) VALUES
 (1, 1),
 (2, 2),
 (3, 1),
 (4, 2),
 (5, 1),
 (6, 2),
 (7, 1),
 (8, 2),
 (9, 1),
 (10, 2),
 (11, 1),
 (12, 2);
