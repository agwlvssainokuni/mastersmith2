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

-- 手元で試す対象DB（MariaDB、compose の profile targetdb-mariadb）の見本のスキーマ。
-- 初めて起動したときに、データベース business（MariaDB ではスキーマと同じ）の中に作る。
-- テーブル・ビュー・外部キー・コメント・大文字小文字の混じった名前・記号を含む名前を入れる。

USE business;

CREATE TABLE customers (
    customer_id INT NOT NULL PRIMARY KEY COMMENT '顧客の番号',
    name VARCHAR(100) NOT NULL COMMENT '顧客の名前',
    email VARCHAR(255) NULL COMMENT 'メールアドレス',
    note TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '顧客';

CREATE TABLE orders (
    order_id INT NOT NULL PRIMARY KEY,
    customer_id INT NOT NULL,
    ordered_on DATE NOT NULL,
    total DECIMAL(12, 2) NOT NULL DEFAULT 0,
    is_paid TINYINT(1) NOT NULL DEFAULT 0,
    CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id) REFERENCES customers (customer_id)
) COMMENT '注文';

CREATE TABLE order_items (
    order_id INT NOT NULL,
    line_no INT NOT NULL,
    product_name VARCHAR(200) NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    PRIMARY KEY (order_id, line_no),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (order_id)
) COMMENT '注文の明細';

-- 大文字・小文字の混じった名前と、引用符・空白・セミコロンを含む名前。
CREATE TABLE `Product Master; "Quoted" 'Name'` (
    `Product_Id` INT NOT NULL PRIMARY KEY,
    `Label "x"; 'y'` VARCHAR(40) NULL
) COMMENT '記号を含む名前の表';

CREATE VIEW customer_orders AS
SELECT c.customer_id, c.name, o.order_id, o.ordered_on, o.total
FROM customers c
JOIN orders o ON o.customer_id = c.customer_id;

INSERT INTO customers (customer_id, name, email) VALUES
    (1, '見本 太郎', 'taro@example.com'),
    (2, '見本 花子', NULL);
INSERT INTO orders (order_id, customer_id, ordered_on, total) VALUES
    (1, 1, '2026-09-01', 1200.00);
INSERT INTO order_items (order_id, line_no, product_name, quantity) VALUES
    (1, 1, '見本の品', 2);
