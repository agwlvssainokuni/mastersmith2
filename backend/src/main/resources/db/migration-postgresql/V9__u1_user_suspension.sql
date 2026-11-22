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

-- Intent 260930-user-admin の U1（利用停止の状態と3つの入口）。前進のみ（BR1.2、NFR10.1）。
-- 適用済みの V1〜V8 は書き換えない。users に停止の状態の列を足し、既存の行は既定の false（停止していない）になる。
-- 書き換えは停止の列だけの更新の問い合わせ（user.repository）で行う。

ALTER TABLE users ADD COLUMN suspended BOOLEAN DEFAULT FALSE NOT NULL;
