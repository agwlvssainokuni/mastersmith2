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
package cherry.mastersmith.common.persistence;

/**
 * 「あれば更新・無ければ挿入」の手順（{@link KeyedUpsertExecutor}）が扱う表ごとの定義。
 *
 * <p>文は定数で、利用者の入力を連結しない。値は JDBC の位置の引数（{@code ?}）だけで渡す。重なりの印は、鍵の重なり（SQLState
 * {@code 23505}）が「この表の鍵の重なり」かを見分けるための大文字の文字列で、H2 の文（表の名前入り）と PostgreSQL の制約の名前
 * （小文字）の両方に当たるよう、判定は文を大文字にして行う。
 */
public enum KeyedUpsert {

    /** 作業ロールの保存（鍵は利用者 ID。表には主キーだけがある）。 */
    WORK_ROLE_SELECTIONS(
            "UPDATE work_role_selections SET role_id = ?, updated_at = ? WHERE user_id = ?",
            "INSERT INTO work_role_selections (user_id, role_id, updated_at) VALUES (?, ?, ?)",
            "WORK_ROLE_SELECTIONS",
            null),

    /**
     * プレビュー（鍵は固定のスロット 1）。{@code preview_id} の一意の制約 {@code uk_dsl_previews_preview_id} の名前にも表の名前が
     * 入るため、それは鍵の重なりでないとみなす。
     */
    DSL_PREVIEWS(
            "UPDATE dsl_previews SET preview_id = ?, yaml_bytes = ?, dsl_hash = ?, source = ?,"
                    + " placed_by_user_id = ?, placed_at = ? WHERE preview_slot = 1",
            "INSERT INTO dsl_previews (preview_slot, preview_id, yaml_bytes, dsl_hash, source, placed_by_user_id,"
                    + " placed_at) VALUES (1, ?, ?, ?, ?, ?, ?)",
            "DSL_PREVIEWS",
            "UK_DSL_PREVIEWS_PREVIEW_ID");

    private final String updateSql;

    private final String insertSql;

    private final String conflictMarker;

    private final String notConflictMarker;

    KeyedUpsert(String updateSql, String insertSql, String conflictMarker, String notConflictMarker) {
        this.updateSql = updateSql;
        this.insertSql = insertSql;
        this.conflictMarker = conflictMarker;
        this.notConflictMarker = notConflictMarker;
    }

    /**
     * 更新の文を返す。
     *
     * @return 更新の文（位置の引数）
     */
    String updateSql() {
        return updateSql;
    }

    /**
     * 挿入の文を返す。
     *
     * @return 挿入の文（位置の引数）
     */
    String insertSql() {
        return insertSql;
    }

    /**
     * 鍵の重なりとみなす印（大文字の表の名前）を返す。
     *
     * @return 印
     */
    String conflictMarker() {
        return conflictMarker;
    }

    /**
     * 鍵の重なりでないとみなす印（大文字）を返す。
     *
     * @return 印（無ければ null）
     */
    String notConflictMarker() {
        return notConflictMarker;
    }
}
