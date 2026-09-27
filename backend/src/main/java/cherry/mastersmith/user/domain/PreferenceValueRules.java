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

import java.util.Optional;

/**
 * 表示の設定の値（言語・テーマ・文字の大きさ）の共通の判定（BR2.1）。DB を使わない純粋な関数。
 *
 * <p>一致は小文字の文字列の完全な一致とし、大文字や前後の空白を含む値はそろえずに拒否する。値が無い（null・空の文字列）ときは
 * {@link FieldErrorReason#REQUIRED}、決めた値のどれでもないときは {@link FieldErrorReason#INVALID_VALUE}
 * （{@code nfr-design/security-design.md} 3節の理由の一覧）。
 */
final class PreferenceValueRules {

    private PreferenceValueRules() {}

    /**
     * 値を決めた値の一覧から探す。
     *
     * @param <E> 値の列挙
     * @param values 決めた値の一覧
     * @param raw 入力された値（null でもよい）
     * @return 一致した値（無ければ空）
     */
    static <E extends Enum<E> & PreferenceValue> Optional<E> find(E[] values, String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        for (E value : values) {
            if (value.value().equals(raw)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    /**
     * 値が決まりに合うかを判定する。
     *
     * @param <E> 値の列挙
     * @param values 決めた値の一覧
     * @param raw 入力された値（null でもよい）
     * @return 合わなければ理由（合えば空）
     */
    static <E extends Enum<E> & PreferenceValue> Optional<FieldErrorReason> check(E[] values, String raw) {
        if (raw == null || raw.isEmpty()) {
            return Optional.of(FieldErrorReason.REQUIRED);
        }
        return find(values, raw).isPresent() ? Optional.empty() : Optional.of(FieldErrorReason.INVALID_VALUE);
    }

    /**
     * 内部DB に保存された値から列挙に戻す（表の列との対応。知らない値は想定外の誤り）。
     *
     * @param <E> 値の列挙
     * @param values 決めた値の一覧
     * @param column 表の列の値（null なら null）
     * @return 列挙の値
     * @throws IllegalStateException 決めた値のどれでもないとき
     */
    static <E extends Enum<E> & PreferenceValue> E fromColumn(E[] values, String column) {
        if (column == null) {
            return null;
        }
        return find(values, column).orElseThrow(() -> new IllegalStateException("表示の設定の値が内部DB に想定外の形で保存されています"));
    }
}
