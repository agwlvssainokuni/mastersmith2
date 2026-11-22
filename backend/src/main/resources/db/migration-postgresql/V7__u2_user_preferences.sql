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

-- Intent 260925-user-management の U2（利用者のプリファレンスとパスワードの変更）。前進のみ・後方互換（BR9.1・BR9.2、NFR10.1〜NFR10.4）。
-- 適用済みの V1〜V6 は書き換えない。
--
-- users に氏名と表示の設定の4列を足す（ADR-003）。
--   language・theme・font_size: 既定の値（ja・system・md）つきの必須の列。既存の行にも既定の値が入り、1つ前の版のアプリの
--     追記（4列を渡さない）でも入る。値は小文字の文字列（ja・en／light・dark・system／sm・md・lg）で、決まりはアプリの純粋な関数で
--     守る（CHECK の制約は置かない）。
--   display_name: 既存の行に保存済みのメールアドレスを入れてから必須にする（既定の値は置かない）。長さは 254 コードポイントの
--     2倍（VARCHAR(n) は Java の文字数で数えるため、サロゲートペアを含む値は上限の2倍の長さになりうる。監査の表と同じ考え方）。
-- audit_events に対象の2列を足す（契約 C8。列の一覧の正は U2）。空を許し、参照の制約と索引は置かない（追記だけの記録のため）。
--   既存の行と、1つ前の版のアプリの追記では空のまま。
--
-- H2 は DDL を巻き戻せないため、途中で失敗すると一部だけ当たった状態が残りうる。失敗したときは Flyway が起動を止め、配備の前の
-- バックアップから戻す（README の「戻し方」）。

-- (1) 表示の設定の3列（既定の値つき・必須）
ALTER TABLE users ADD COLUMN language VARCHAR(2) DEFAULT 'ja' NOT NULL;
ALTER TABLE users ADD COLUMN theme VARCHAR(6) DEFAULT 'system' NOT NULL;
ALTER TABLE users ADD COLUMN font_size VARCHAR(2) DEFAULT 'md' NOT NULL;

-- (2) 氏名（まず空を許す形で足す）
ALTER TABLE users ADD COLUMN display_name VARCHAR(508);

-- (3) 既存の行の氏名に、保存済みのメールアドレスを入れる（BR2.2）
UPDATE users SET display_name = email;

-- (4) 氏名を必須にする（既定の値は置かない）
ALTER TABLE users ALTER COLUMN display_name SET NOT NULL;

-- (5) 監査の対象の2列（空を許す）
ALTER TABLE audit_events ADD COLUMN target_user_id BIGINT;
ALTER TABLE audit_events ADD COLUMN target_invitation_id BIGINT;
