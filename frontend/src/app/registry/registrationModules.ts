/*
 * Copyright 2026 agwlvssainokuni
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// 各機能の登録用ファイル（決まった場所・名前: src/features/<featureId>/registration.ts）をすべて読み込む（BR7.1）。
// 登録用ファイルは画面の起動に必要なため最初に読み込む（performance-design 4章）。登録が指す画面の部品は、
// 各機能が遅延読み込み（React.lazy）にしてよい。
export const registrationModules: Readonly<Record<string, unknown>> = import.meta.glob(
  '../../features/*/registration.ts',
  { eager: true },
)
