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
/**
 * 業務データの行の SQL を組み立てて実行する唯一の部品（{@code RowSqlExecutor}）と、DB の種類ごとの方言・値の型の対応・失敗の分け方
 * （BR3.5・BR7.1・BR8.5）。
 */
package cherry.mastersmith.businessdata.repository;
