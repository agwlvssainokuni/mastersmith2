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
package cherry.mastersmith.businessdata.domain;

/** 許す操作（ADR-005、BR2.3）。{@link TableAccessPolicy} だけが求める。 */
public enum AllowedOperation {
    /** 一覧と検索。 */
    LIST,
    /** 行の詳細。 */
    DETAIL,
    /** 行の作成（U2）。 */
    CREATE,
    /** 行の更新（U2）。 */
    UPDATE,
    /** 行の削除（U2）。 */
    DELETE
}
