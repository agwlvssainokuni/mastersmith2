-- Copyright 2026 agwlvssainokuni
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.

-- 手元で試す対象DB（PostgreSQL、compose の profile targetdb-postgres）の見本のスキーマ。
-- 初めて起動したときに、DB business の中にスキーマ sales を作る。
-- テーブル・ビュー・外部キー・コメント・大文字小文字の混じった名前・記号を含む名前を入れる。

CREATE SCHEMA sales;

CREATE TABLE sales.customers (
    customer_id INTEGER NOT NULL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255),
    note TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE sales.customers IS '顧客';
COMMENT ON COLUMN sales.customers.customer_id IS '顧客の番号';
COMMENT ON COLUMN sales.customers.name IS '顧客の名前';
COMMENT ON COLUMN sales.customers.email IS 'メールアドレス';

CREATE TABLE sales.orders (
    order_id INTEGER NOT NULL PRIMARY KEY,
    customer_id INTEGER NOT NULL,
    ordered_on DATE NOT NULL,
    total NUMERIC(12, 2) NOT NULL DEFAULT 0,
    is_paid BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id) REFERENCES sales.customers (customer_id)
);
COMMENT ON TABLE sales.orders IS '注文';

CREATE TABLE sales.order_items (
    order_id INTEGER NOT NULL,
    line_no INTEGER NOT NULL,
    product_name VARCHAR(200) NOT NULL,
    quantity INTEGER NOT NULL DEFAULT 1,
    PRIMARY KEY (order_id, line_no),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES sales.orders (order_id)
);
COMMENT ON TABLE sales.order_items IS '注文の明細';

-- 大文字・小文字の混じった名前と、引用符・空白・セミコロンを含む名前。
CREATE TABLE sales."Product Master; ""Quoted"" 'Name'" (
    "Product_Id" INTEGER NOT NULL PRIMARY KEY,
    "Label ""x""; 'y'" VARCHAR(40)
);
COMMENT ON TABLE sales."Product Master; ""Quoted"" 'Name'" IS '記号を含む名前の表';

CREATE VIEW sales.customer_orders AS
SELECT c.customer_id, c.name, o.order_id, o.ordered_on, o.total
FROM sales.customers c
JOIN sales.orders o ON o.customer_id = c.customer_id;
COMMENT ON VIEW sales.customer_orders IS '顧客ごとの注文';

INSERT INTO sales.customers (customer_id, name, email) VALUES
    (1, '見本 太郎', 'taro@example.com'),
    (2, '見本 花子', NULL);
INSERT INTO sales.orders (order_id, customer_id, ordered_on, total) VALUES
    (1, 1, DATE '2026-09-01', 1200.00);
INSERT INTO sales.order_items (order_id, line_no, product_name, quantity) VALUES
    (1, 1, '見本の品', 2);
