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
package cherry.mastersmith.user.domain;

import java.util.Objects;

/**
 * 入力の誤りの1件（項目の名前と理由。{@code nfr-design/security-design.md} 3節）。入れた値は持たない（NFR2.3）。
 *
 * @param field 要求の項目の名前（契約の項目名そのまま。例: {@code displayName}）
 * @param reason 理由
 */
public record FieldError(String field, FieldErrorReason reason) {

    /** 値が null でないことを確かめる。 */
    public FieldError {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(reason, "reason");
    }
}
