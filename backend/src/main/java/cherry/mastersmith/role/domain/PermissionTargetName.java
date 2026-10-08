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
package cherry.mastersmith.role.domain;

import java.util.Optional;

/**
 * 権限の設定の対象の名前（スキーマ・テーブル・カラムの名前の1つ）の検証（BR4.3）。DB を使わない純粋な関数である。
 *
 * <ul>
 *   <li>前後の空白を取り除かない（DSL の名前と完全一致で照らすため）。大文字と小文字を区別する
 *   <li>空（null を含む）は {@link Reason#BLANK}
 *   <li>コードポイントの数が {@value #MAX_CODE_POINTS} を超えれば {@link Reason#TOO_LONG}（ちょうどは受け付ける）
 *   <li>制御文字（Cc）と行・段落の区切りの文字（Zl・Zp）が1つでもあれば {@link Reason#CONTROL_CHARACTER}（{@link RoleName} と同じ範囲）
 * </ul>
 *
 * <p>保存・消す・import の確かめで使う。木の読み取りの引数には長さの上限を当てない（BR4.12）。
 */
public final class PermissionTargetName {

    /** 名前の長さの上限（コードポイントの数）。 */
    public static final int MAX_CODE_POINTS = 128;

    /** 名前が正しくない理由。 */
    public enum Reason {
        /** 空。 */
        BLANK,
        /** 128 コードポイントを超える。 */
        TOO_LONG,
        /** 制御文字がある。 */
        CONTROL_CHARACTER
    }

    private PermissionTargetName() {}

    /**
     * 名前を検証する。理由は上の順で最初に当たったものだけを返す。
     *
     * @param name 名前（null でもよい）
     * @return 正しくなければ理由、正しければ空
     */
    public static Optional<Reason> problemOf(String name) {
        if (name == null || name.isEmpty()) {
            return Optional.of(Reason.BLANK);
        }
        if (name.codePointCount(0, name.length()) > MAX_CODE_POINTS) {
            return Optional.of(Reason.TOO_LONG);
        }
        if (name.codePoints().anyMatch(RoleName::isControl)) {
            return Optional.of(Reason.CONTROL_CHARACTER);
        }
        return Optional.empty();
    }

    /**
     * 対象のすべての名前を検証する。
     *
     * @param target 対象
     * @return 最初に当たった理由（正しければ空）
     */
    public static Optional<Reason> problemOf(PermissionTarget target) {
        Optional<Reason> schema = problemOf(target.schemaName());
        if (schema.isPresent() || target.tableName() == null) {
            return schema;
        }
        Optional<Reason> table = problemOf(target.tableName());
        if (table.isPresent() || target.columnName() == null) {
            return table;
        }
        return problemOf(target.columnName());
    }
}
