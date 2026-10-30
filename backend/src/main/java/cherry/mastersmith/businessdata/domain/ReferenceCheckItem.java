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

import java.util.Objects;

/**
 * 保存前に確かめる参照1つ（Intent 261029-reference-lookup-cleanup の U2、entities.md の ReferenceCheckItem、BR2.1・BR2.2・BR2.4・
 * BR4.6）。参照先は要求1件の権限の写しの DSL から引き、利用者の入力からは作らない。値は検証を通った型つきの値から作り、NULL の値は
 * 作らない。
 *
 * <p>文字列にするときはカラムの名前と由来だけを出し、参照先と値を出さない（BR7.1）。
 *
 * @param column 参照元のカラムの名前
 * @param target 参照先（DSL の options から引いたもの）
 * @param value 確かめる値
 * @param origin 由来
 */
public record ReferenceCheckItem(String column, ReferenceTarget target, ReferenceValue value, Origin origin) {

    /** 確かめる参照の由来。 */
    public enum Origin {
        /** 作成で値を入れた参照（必須の参照項目でないもの。BR2.2）。 */
        CREATE,
        /** 作成の必須の参照項目で、使えて値を入れたもの（BR3.5）。 */
        CREATE_REQUIRED,
        /** 更新で、更新前の値と違う値を入れた参照（BR2.4）。 */
        UPDATE_CHANGED
    }

    /** 必須の値を確かめる。 */
    public ReferenceCheckItem {
        Objects.requireNonNull(column, "column は必須です");
        Objects.requireNonNull(target, "target は必須です");
        Objects.requireNonNull(value, "value は必須です");
        Objects.requireNonNull(origin, "origin は必須です");
    }

    /** カラムの名前と由来だけを出す（参照先と値を出さない。BR7.1）。 */
    @Override
    public String toString() {
        return "ReferenceCheckItem[column=" + column + ", origin=" + origin + "]";
    }
}
