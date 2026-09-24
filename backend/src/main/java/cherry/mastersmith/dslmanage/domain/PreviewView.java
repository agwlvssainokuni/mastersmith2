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
package cherry.mastersmith.dslmanage.domain;

import cherry.mastersmith.dsl.domain.DisplayName;
import java.util.List;
import java.util.Objects;

/**
 * プレビューの表示の中身（{@code entities.md} の PreviewView、契約 C6 の {@code Preview}）。表示のたびに求める値で、保存しない
 * （BR2.1）。接続先・内部の例外の文言を持たない。
 *
 * @param preview プレビューの参照
 * @param placedBy 置いた管理者
 * @param summary 要約（BR2.3）
 * @param diff 適用中との違い（BR2.2）
 * @param warnings 対象DB との照合の警告（BR2.4）
 */
public record PreviewView(
        DslPreviewRef preview, DslUserRef placedBy, Summary summary, Diff diff, List<Warning> warnings) {

    /** 必須の値を確かめ、一覧を変更できないものにする。 */
    public PreviewView {
        Objects.requireNonNull(preview, "preview は必須です");
        Objects.requireNonNull(placedBy, "placedBy は必須です");
        Objects.requireNonNull(summary, "summary は必須です");
        Objects.requireNonNull(diff, "diff は必須です");
        warnings = List.copyOf(warnings);
    }

    /** 表示名の未設定の一覧に返す件数の上限（先頭の100件。BR2.3）。 */
    public static final int MAX_MISSING_DISPLAY_NAMES = 100;

    /**
     * 要約（BR2.3）。
     *
     * @param tableCount テーブルの数（ビューを除く）
     * @param viewCount ビューの数
     * @param columnCount カラムの数（テーブルとビューの合計）
     * @param menuTree メニューの木（DSL の順）
     * @param missingDisplayNames 表示名が空の場所（先頭の100件まで）
     * @param missingDisplayNameTotal 表示名が空の場所の総数
     */
    public record Summary(
            int tableCount,
            int viewCount,
            int columnCount,
            List<MenuNode> menuTree,
            List<MissingDisplayName> missingDisplayNames,
            int missingDisplayNameTotal) {

        /** 一覧を変更できないものにし、件数の上限を確かめる。 */
        public Summary {
            menuTree = List.copyOf(menuTree);
            missingDisplayNames = List.copyOf(missingDisplayNames);
            if (missingDisplayNames.size() > MAX_MISSING_DISPLAY_NAMES) {
                throw new IllegalArgumentException("missingDisplayNames は 100 件までです");
            }
            if (missingDisplayNameTotal < missingDisplayNames.size()) {
                throw new IllegalArgumentException("missingDisplayNameTotal は一覧の件数以上です");
            }
        }
    }

    /**
     * メニューの木の節。
     *
     * @param label 表示名
     * @param table 紐付くテーブル（無ければ null）
     * @param children 子（DSL の順）
     */
    public record MenuNode(DisplayName label, String table, List<MenuNode> children) {

        /** 子を変更できない一覧にする。 */
        public MenuNode {
            Objects.requireNonNull(label, "label は必須です");
            children = List.copyOf(children);
        }
    }

    /**
     * 表示名が空の場所。
     *
     * @param path DSL の中の場所（点でつないだ形。例 {@code tables.dept_mst.columns.code.label}）
     * @param language 空の言語（{@code ja} または {@code en}）
     */
    public record MissingDisplayName(String path, String language) {}

    /**
     * 適用中との違い（BR2.2）。
     *
     * @param appliedExists 適用中の DSL があるか
     * @param tables テーブルの違い（変わらないテーブルも含めてすべて）
     */
    public record Diff(boolean appliedExists, List<TableDiff> tables) {

        /** 一覧を変更できないものにする。 */
        public Diff {
            tables = List.copyOf(tables);
        }
    }

    /**
     * テーブル1つの違い。
     *
     * @param name 物理名
     * @param change 区分
     * @param columns カラムの違い（増えた・減った・変わったものだけ）
     */
    public record TableDiff(String name, DiffChange change, List<ColumnDiff> columns) {

        /** 一覧を変更できないものにする。 */
        public TableDiff {
            columns = List.copyOf(columns);
        }
    }

    /**
     * カラム1つの違い。
     *
     * @param name 物理名
     * @param change 区分（{@link DiffChange#UNCHANGED} は使わない。契約 C6）
     * @param changedItems 変わった項目の名前（例 {@code label.ja}・{@code list.order}。増えた・減ったときは空）
     */
    public record ColumnDiff(String name, DiffChange change, List<String> changedItems) {

        /** 区分と一覧を確かめる。 */
        public ColumnDiff {
            if (change == DiffChange.UNCHANGED) {
                throw new IllegalArgumentException("変わらないカラムは並べません");
            }
            changedItems = List.copyOf(changedItems);
        }
    }

    /** 違いの区分。 */
    public enum DiffChange {
        /** 増えた。 */
        ADDED,
        /** 減った。 */
        REMOVED,
        /** 変わった。 */
        CHANGED,
        /** 変わらない（テーブルだけ）。 */
        UNCHANGED
    }

    /**
     * 照合の警告1件（BR2.4）。
     *
     * @param kind 種類
     * @param path DSL の中の場所（照合できないときは null）
     * @param message 要求の表示言語の文言（接続先・内部の例外の文言を含めない）
     */
    public record Warning(WarningKind kind, String path, String message) {

        /** 必須の値を確かめる。 */
        public Warning {
            Objects.requireNonNull(kind, "kind は必須です");
            Objects.requireNonNull(message, "message は必須です");
        }
    }

    /** 照合の警告の種類（契約 C6）。 */
    public enum WarningKind {
        /** 対象DB にテーブルが無い。 */
        TABLE_MISSING,
        /** 対象DB にカラムが無い。 */
        COLUMN_MISSING,
        /** 型の名前と長さ・精度・桁が違う。 */
        TYPE_MISMATCH,
        /** 対象DB の設定が無いため照合できなかった。 */
        TARGET_UNCONFIGURED,
        /** 対象DB に接続できない・応答しないため照合できなかった。 */
        TARGET_UNAVAILABLE
    }
}
