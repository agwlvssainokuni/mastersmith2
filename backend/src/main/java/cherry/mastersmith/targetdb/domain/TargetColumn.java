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
package cherry.mastersmith.targetdb.domain;

import java.util.Objects;

/**
 * カラム1つ（契約 C1 の {@code TargetColumn}）。
 *
 * @param name 物理名（DB が返した大文字・小文字のまま。BR2.3）
 * @param dbType DB 上の型
 * @param nullable NULL を許すか
 * @param defaultValue DB が返した既定値の式をそのまま（無ければ null）
 * @param comment コメント（無ければ null。空の文字列・空白だけは null に揃える。BR2.5）
 */
public record TargetColumn(String name, TargetDbType dbType, boolean nullable, String defaultValue, String comment) {

    /** 名前と型が必須であることを確かめ、コメントを揃える。 */
    public TargetColumn {
        DomainValues.requireName(name, "column.name");
        Objects.requireNonNull(dbType, "column.dbType は必須です");
        comment = DomainValues.normalizeComment(comment);
    }
}
