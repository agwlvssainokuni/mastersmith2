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
package cherry.mastersmith.dslmanage.service;

import cherry.mastersmith.dsl.domain.DisplayName;
import cherry.mastersmith.dsl.domain.DslColumn;
import cherry.mastersmith.dsl.domain.DslMenuItem;
import cherry.mastersmith.dsl.domain.DslModel;
import cherry.mastersmith.dsl.domain.DslTable;
import cherry.mastersmith.dsl.domain.OptionItem;
import cherry.mastersmith.dslmanage.domain.PreviewView;
import cherry.mastersmith.dslmanage.domain.PreviewView.MenuNode;
import cherry.mastersmith.dslmanage.domain.PreviewView.MissingDisplayName;
import cherry.mastersmith.dslmanage.domain.PreviewView.Summary;
import java.util.ArrayList;
import java.util.List;

/**
 * プレビューの要約を求める（BR2.3）。DB にも時計にも触れない純粋な関数。
 *
 * <p>表示名が空（空の文字列・空白だけ）の場所は、メニューの項目・テーブル・カラム・固定の選択肢の表示名を DSL の順に数え、言語ごとに
 * 1件とする。一覧は先頭の100件まで、総数は別に返す。
 */
public final class DslSummaryCalculator {

    private DslSummaryCalculator() {}

    /**
     * 要約を求める。
     *
     * @param model プレビューのモデル
     * @return 要約
     */
    public static Summary summarize(DslModel model) {
        int tableCount = 0;
        int viewCount = 0;
        int columnCount = 0;
        for (DslTable table : model.tables().values()) {
            if (table.view()) {
                viewCount++;
            } else {
                tableCount++;
            }
            columnCount += table.columns().size();
        }
        MissingCollector missing = new MissingCollector();
        List<MenuNode> menuTree = menus(model.menus(), "menus", missing);
        for (DslTable table : model.tables().values()) {
            String tablePath = "tables." + table.name();
            missing.check(tablePath + ".label", table.label());
            for (DslColumn column : table.columns().values()) {
                String columnPath = tablePath + ".columns." + column.name();
                missing.check(columnPath + ".label", column.label());
                if (column.options() != null) {
                    List<OptionItem> items = column.options().items();
                    for (int i = 0; i < items.size(); i++) {
                        missing.check(
                                columnPath + ".options.items." + i + ".label",
                                items.get(i).label());
                    }
                }
            }
        }
        return new Summary(tableCount, viewCount, columnCount, menuTree, missing.first, missing.total);
    }

    private static List<MenuNode> menus(List<DslMenuItem> items, String path, MissingCollector missing) {
        List<MenuNode> nodes = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            DslMenuItem item = items.get(i);
            String itemPath = path + "." + i;
            missing.check(itemPath + ".label", item.label());
            nodes.add(new MenuNode(item.label(), item.table(), menus(item.items(), itemPath + ".items", missing)));
        }
        return nodes;
    }

    /** 表示名が空の場所を、先頭の100件と総数で集める。 */
    private static final class MissingCollector {

        private final List<MissingDisplayName> first = new ArrayList<>();

        private int total;

        void check(String path, DisplayName label) {
            add(path, "ja", label.ja());
            add(path, "en", label.en());
        }

        private void add(String path, String language, String value) {
            if (!value.isBlank()) {
                return;
            }
            total++;
            if (first.size() < PreviewView.MAX_MISSING_DISPLAY_NAMES) {
                first.add(new MissingDisplayName(path, language));
            }
        }
    }
}
