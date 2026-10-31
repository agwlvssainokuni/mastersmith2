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

-- E2E の業務データの代表の流れ（180）が使う表と架空の行（Intent 261016-business-data-views の U3、基盤の設計 1.1）。
-- ./gradlew e2eTest の準備が、見本の PostgreSQL の中に実行ごとのスキーマ（e2e_biz_<印>）を作った後、1つのトランザクションの
-- 中でこのファイルを psql の標準入力に流す。スキーマの名前は psql の変数 :"schema" で入る（ここに名前を書かない）。
-- 行の値はすべて架空の固定の文字と番号で、品目の名前に E2E の報告の確かめの目印（E2E-BIZ-MARK。frontend/e2e/support/secretValues.ts の
-- BUSINESS_VALUE_MARK）を含める。報告にこの目印が出れば、行の値が報告に漏れたことになる。
-- 行は 25 件（一覧の1ページ 20 件を超え、ページ送りを確かめられる数）。

CREATE TABLE :"schema".e2e_item (
    item_code VARCHAR(20) NOT NULL PRIMARY KEY,
    item_name VARCHAR(50) NOT NULL,
    quantity INTEGER,
    note TEXT
);

INSERT INTO :"schema".e2e_item (item_code, item_name, quantity, note)
SELECT
    'ITEM-' || lpad(n::text, 3, '0'),
    'E2E-BIZ-MARK 品目 ' || lpad(n::text, 2, '0'),
    n * 10,
    CASE WHEN n % 5 = 0 THEN NULL ELSE '架空の備考 ' || lpad(n::text, 2, '0') END
FROM generate_series(1, 25) AS n;

-- 他テーブル参照の E2E（190・195。Intent 261029-reference-lookup-cleanup の U3、D39）が使う表と架空の行。
-- 参照先の顧客（3 行）と商品（25 行。1ページ 20 件を超え、ピッカーのページ送りを確かめられる数）、参照元の注文（1 行）。
-- 商品の名前は目印（E2E-BIZ-MARK）を含める。ただし axe の確かめに使う2行は目印を含めない: 100 文字以上で `<` を含む長い名前の
-- 行（PROD-024）と、2語以上の現実に近い架空の品名の行（PROD-025）。
CREATE TABLE :"schema".e2e_customer (
    customer_code VARCHAR(20) NOT NULL PRIMARY KEY,
    customer_name VARCHAR(50) NOT NULL
);

INSERT INTO :"schema".e2e_customer (customer_code, customer_name)
SELECT 'CUST-' || lpad(n::text, 3, '0'), 'E2E-BIZ-MARK 顧客 ' || lpad(n::text, 2, '0')
FROM generate_series(1, 3) AS n;

CREATE TABLE :"schema".e2e_product (
    product_code VARCHAR(20) NOT NULL PRIMARY KEY,
    product_name VARCHAR(200) NOT NULL,
    price INTEGER
);

INSERT INTO :"schema".e2e_product (product_code, product_name, price)
SELECT 'PROD-' || lpad(n::text, 3, '0'), 'E2E-BIZ-MARK 商品 ' || lpad(n::text, 2, '0'), n * 100
FROM generate_series(1, 23) AS n;

INSERT INTO :"schema".e2e_product (product_code, product_name, price) VALUES
    ('PROD-024', 'とても長い名前の架空の商品 <' || repeat('あいうえおかきくけこ', 10), 2400),
    ('PROD-025', '青い 木製の椅子', 2500);

CREATE TABLE :"schema".e2e_order (
    order_no VARCHAR(20) NOT NULL PRIMARY KEY,
    customer_code VARCHAR(20),
    product_code VARCHAR(20)
);

INSERT INTO :"schema".e2e_order (order_no, customer_code, product_code)
VALUES ('ORD-001', 'CUST-001', 'PROD-001');
