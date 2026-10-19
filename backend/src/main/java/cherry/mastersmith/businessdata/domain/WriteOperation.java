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

/**
 * 業務データの書き込みの操作（Intent 261016-business-data-views の U2、entities.md の WriteRequest.operation）。道から決め、本文からは
 * 受け取らない。
 */
public enum WriteOperation {
    /** 行の作成。 */
    CREATE,
    /** 行の更新。 */
    UPDATE,
    /** 行の削除。 */
    DELETE;

    /**
     * 判定に使う許す操作を返す。
     *
     * @return 許す操作
     */
    public AllowedOperation allowedOperation() {
        return switch (this) {
            case CREATE -> AllowedOperation.CREATE;
            case UPDATE -> AllowedOperation.UPDATE;
            case DELETE -> AllowedOperation.DELETE;
        };
    }
}
