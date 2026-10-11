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

import java.util.Objects;

/**
 * 権限の YAML の確かめの拒否（{@code ROLE_TRANSFER_INVALID}）の誤りの1件（BR9.3・BR9.5、{@code entities.md} の TransferError）。
 *
 * <p>位置（行・列）と YAML の中の場所だけを持ち、入れた値（主権限の文字列・真偽の値など）は持たない。場所はロールの名前・対象の名前を
 * 含みうるため、{@link #toString()} では理由と位置だけを出す（NFR1.9）。
 *
 * @param reason 理由
 * @param line 行（1 から。位置が得られないときは null）
 * @param column 列（1 から。位置が得られないときは null）
 * @param path YAML の中の場所（キーを点でつないだ形。例 {@code roles.営業.schemas.SALES}。文書の根や得られないときは null）
 */
public record TransferError(Reason reason, Integer line, Integer column, String path) {

    /** 1回の応答に入れる誤りの数の上限（DSL の誤りの一覧と同じ。BR9.5）。 */
    public static final int MAX_ERRORS = 100;

    /** 誤りの理由（{@code entities.md} の TransferError の reason。13 の値）。 */
    public enum Reason {
        /** 入れ子が深すぎる（読み込みの上限）。 */
        TOO_DEEP,
        /** コレクションを指す別名・展開後の節の数が上限を超えた（読み込みの上限）。 */
        TOO_MANY_ALIASES,
        /** タグ（{@code !!} など）を使った（読み込みの拒否）。 */
        TAG_NOT_ALLOWED,
        /** 1つの対応表の中で同じキーが重なった（読み込みの拒否）。 */
        DUPLICATE_KEY,
        /** YAML として読めない（読み込みの拒否）。 */
        SYNTAX,
        /** 版が 1 でない。 */
        UNSUPPORTED_VERSION,
        /** 必須の項目（{@code version}・{@code roles}）が無い。 */
        MISSING_KEY,
        /** 知らない項目がある。 */
        UNKNOWN_KEY,
        /** 値の型・語が誤り（主権限は NONE・READ・FULL の文字列、補助権限は真偽、入れ子は対応表）。 */
        INVALID_VALUE,
        /** ロールの名前が名前の規則（BR1）に合わない。 */
        INVALID_ROLE_NAME,
        /** 名前の鍵（大文字と小文字・前後の空白を除いた名前）が同じロールが重なる。 */
        DUPLICATE_ROLE,
        /** スキーマ・テーブル・カラムの名前が規則（BR4.1）に合わない。 */
        INVALID_TARGET_NAME,
        /** カラムに CREATE・DELETE を書いた（カラムは主権限の値だけ）。 */
        COLUMN_AUX_NOT_ALLOWED
    }

    /** 理由が必須で、行と列がそろっていることを確かめる。 */
    public TransferError {
        Objects.requireNonNull(reason, "reason");
        if ((line == null) != (column == null)) {
            throw new IllegalArgumentException("line と column は両方あるか両方無いかのどちらかです");
        }
        if (line != null && (line < 1 || column < 1)) {
            throw new IllegalArgumentException("line と column は 1 以上です");
        }
        if (path != null && path.isEmpty()) {
            throw new IllegalArgumentException("path は空にしません（無いときは null）");
        }
    }

    /** 理由と位置だけを出す（場所はロールと対象の名前を含みうるため出さない）。 */
    @Override
    public String toString() {
        return "TransferError[reason=" + reason + ", line=" + line + ", column=" + column + "]";
    }
}
