# Code Generation Questions — U3 既定の DSL の生成（u3-default-dsl-generation）

## Q1. MySQL・MariaDB の `tinyint(1)` を真偽値と見分ける方法

U3 の決まり（`construction/u3-default-dsl-generation/functional-design/rules.md` の BR2.2）は「MySQL・MariaDB の `tinyint(1)`・`bit(1)` は BOOLEAN（真偽値。フォーム部品は checkbox）」としています。ところが、U1（対象DB）が返すスキーマの写しは、型の名前を情報スキーマの `DATA_TYPE`（例 `tinyint`）、数値の精度を `NUMERIC_PRECISION` で持つため、`tinyint(1)` は「`tinyint`・精度 3」になり、ふつうの `tinyint` と見分けられません（U1 のコード生成の申し送り）。`bit(1)` は精度 1 になるため、今の写しで見分けられます。

A. U1 の写しの型に、MySQL・MariaDB の `COLUMN_TYPE`（例 `tinyint(1)`・`int unsigned`）を任意の項目として足し、U3 はそれで `tinyint(1)` を BOOLEAN にする（U1 のコードと契約 C1 に項目が1つ増える。PostgreSQL は無し。DSL の `dbType.name` は今までどおり `DATA_TYPE` のまま）
B. U1 は変えず、`bit(1)` だけを精度で BOOLEAN にし、`tinyint` はすべて NUMBER にする（BR2.2 の「`tinyint(1)` は BOOLEAN」は満たさない。差を記録する）
X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

- Q1: A — U1 の写しの型に MySQL・MariaDB の `COLUMN_TYPE` を任意の項目として足し、U3 はそれで `tinyint(1)` を BOOLEAN にする。`bit(1)` は精度 1 で BOOLEAN。PostgreSQL では項目は無し。DSL の `dbType.name` は `DATA_TYPE` のまま。U1 のコード・テストと、契約 C1 との差（項目の追加）を U3 の計画に含めて記録する。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct

## Plan Approval

`code-generation-plan.md`（中の Testing Contract を含む）と `unit-test-instructions.md` のとおりにコードを生成してよいかを確かめる。

[Approval Fingerprint]: sha256:v3:e8d6a05629233ab46f4cd880c02d3ce5caed56329e5cb5f4fd9c3dd96976baa3
[Planned Source]: 0d508d3925d1677cd98767d92808d75eb52593a8c2ffbc7e0346d7f0cbe6a003

- Approve Plan
- Request Changes

[Answer]: Approve Plan
