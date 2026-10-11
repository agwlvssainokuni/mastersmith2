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
package cherry.mastersmith.role.transfer;

import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.domain.PermissionValues;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * 権限の YAML の書き出しの文字列を作る純粋な関数（BR9.1・BR9.14、{@code security-design.md} 5節）。
 *
 * <ul>
 *   <li>{@code version: 1} と、ロールの名前をキーにした入れ子の対応表。ロールは渡した順（呼び出し元が ID の順に並べる）、スキーマ・
 *       テーブル・カラムは名前のコードポイントの順。設定のある対象と、その上の階層だけを書く（設定の無いロールは {@code {}}）
 *   <li>スキーマとテーブルは {@code main}・{@code create}・{@code delete} の順に設定のある値だけを書き、下位の対応表を続ける。カラムは
 *       主権限の値だけ
 *   <li>別名とタグを作らない。YAML で型や特別な意味を持ちうる名前（{@code true}・{@code null}・{@code ~}・数や日付に読める名前・
 *       記号を含む名前・前後に空白のある名前・空の名前など）は二重引用符で囲み、読み込みで同じ名前に戻るようにする
 * </ul>
 */
public final class RoleTransferWriter {

    private static final String INDENT = "  ";

    /** どこにあっても二重引用符で囲む文字（{@code security-design.md} 5節の一覧と、エスケープの {@code \}）。 */
    private static final String QUOTE_ANYWHERE = ":#&*!|>'\"%@`\\";

    /** 先頭にあると YAML の別の書き方になりうる文字（並び・対応表・数・合わせのキーなど）。 */
    private static final String QUOTE_AT_START = "-?[]{},<=~.+";

    /** 真偽・null として読まれうる語（大文字と小文字を区別しない）。 */
    private static final Set<String> RESERVED_WORDS =
            Set.of("true", "false", "yes", "no", "on", "off", "y", "n", "null", "~");

    private RoleTransferWriter() {}

    /**
     * 書き出しの文字列を作る。
     *
     * @param roles 書き出すロール（ID の順）
     * @return YAML の文字列（UTF-8 で送る）
     */
    public static String write(List<TransferRoleSettings> roles) {
        Objects.requireNonNull(roles, "roles");
        StringBuilder yaml = new StringBuilder();
        yaml.append("version: ").append(RoleTransferReader.SUPPORTED_VERSION).append('\n');
        if (roles.isEmpty()) {
            yaml.append("roles: {}\n");
            return yaml.toString();
        }
        yaml.append("roles:\n");
        for (TransferRoleSettings role : roles) {
            role(yaml, role);
        }
        return yaml.toString();
    }

    private static void role(StringBuilder yaml, TransferRoleSettings role) {
        Map<String, SchemaNode> schemas = new TreeMap<>(TransferOrder.NAME);
        for (Map.Entry<PermissionTarget, PermissionValues> entry :
                role.settings().entrySet()) {
            PermissionTarget target = entry.getKey();
            if (entry.getValue().isEmpty()) {
                continue;
            }
            SchemaNode schema = schemas.computeIfAbsent(target.schemaName(), name -> new SchemaNode());
            switch (target.level()) {
                case SCHEMA -> schema.values = entry.getValue();
                case TABLE -> schema.table(target.tableName()).values = entry.getValue();
                case COLUMN -> {
                    MainPermission main = entry.getValue().main();
                    if (main != null) {
                        schema.table(target.tableName()).columns.put(target.columnName(), main);
                    }
                }
            }
        }
        key(yaml, 1, role.name());
        if (schemas.isEmpty()) {
            yaml.append(" {}\n");
            return;
        }
        yaml.append('\n');
        line(yaml, 2, "schemas:");
        for (Map.Entry<String, SchemaNode> schema : schemas.entrySet()) {
            key(yaml, 3, schema.getKey());
            yaml.append('\n');
            values(yaml, 4, schema.getValue().values);
            if (!schema.getValue().tables.isEmpty()) {
                line(yaml, 4, "tables:");
                for (Map.Entry<String, TableNode> table :
                        schema.getValue().tables.entrySet()) {
                    key(yaml, 5, table.getKey());
                    TableNode node = table.getValue();
                    if (node.values == null && node.columns.isEmpty()) {
                        yaml.append(" {}\n");
                        continue;
                    }
                    yaml.append('\n');
                    values(yaml, 6, node.values);
                    if (!node.columns.isEmpty()) {
                        line(yaml, 6, "columns:");
                        for (Map.Entry<String, MainPermission> column : node.columns.entrySet()) {
                            key(yaml, 7, column.getKey());
                            yaml.append(' ').append(column.getValue().name()).append('\n');
                        }
                    }
                }
            }
        }
    }

    private static void values(StringBuilder yaml, int depth, PermissionValues values) {
        if (values == null) {
            return;
        }
        if (values.main() != null) {
            line(yaml, depth, "main: " + values.main().name());
        }
        if (values.create() != null) {
            line(yaml, depth, "create: " + values.create());
        }
        if (values.delete() != null) {
            line(yaml, depth, "delete: " + values.delete());
        }
    }

    private static void line(StringBuilder yaml, int depth, String text) {
        yaml.append(INDENT.repeat(depth)).append(text).append('\n');
    }

    /** 名前のキーと {@code :} を書く（改行は書かない）。 */
    private static void key(StringBuilder yaml, int depth, String name) {
        yaml.append(INDENT.repeat(depth)).append(scalar(name)).append(':');
    }

    /**
     * 名前を YAML のキーとして書く形にする（必要なら二重引用符で囲む）。
     *
     * @param name 名前
     * @return 書く形
     */
    static String scalar(String name) {
        return needsQuotes(name) ? quoted(name) : name;
    }

    /**
     * 名前を二重引用符で囲む必要があるかを判定する（{@code security-design.md} 5節）。
     *
     * @param name 名前
     * @return 囲むなら真
     */
    static boolean needsQuotes(String name) {
        if (name.isEmpty() || RESERVED_WORDS.contains(name.toLowerCase(Locale.ROOT))) {
            return true;
        }
        int first = name.codePointAt(0);
        int last = name.codePointBefore(name.length());
        if (isSpace(first) || isSpace(last) || Character.isDigit(first) || QUOTE_AT_START.indexOf(first) >= 0) {
            return true;
        }
        return name.codePoints()
                .anyMatch(cp -> QUOTE_ANYWHERE.indexOf(cp) >= 0 || needsEscape(cp) || isSpace(cp) && cp != ' ');
    }

    private static String quoted(String name) {
        StringBuilder text = new StringBuilder(name.length() + 2).append('"');
        name.codePoints().forEach(cp -> {
            if (cp == '"' || cp == '\\') {
                text.append('\\').appendCodePoint(cp);
            } else if (needsEscape(cp)) {
                text.append(String.format(Locale.ROOT, "\\u%04X", cp));
            } else {
                text.appendCodePoint(cp);
            }
        });
        return text.append('"').toString();
    }

    private static boolean needsEscape(int cp) {
        int type = Character.getType(cp);
        return type == Character.CONTROL
                || type == Character.LINE_SEPARATOR
                || type == Character.PARAGRAPH_SEPARATOR
                || cp == 0xFEFF;
    }

    private static boolean isSpace(int cp) {
        return Character.isWhitespace(cp) || Character.isSpaceChar(cp);
    }

    /** スキーマの節（書き出しの組み立ての中だけで使う）。 */
    private static final class SchemaNode {

        private PermissionValues values;

        private final Map<String, TableNode> tables = new TreeMap<>(TransferOrder.NAME);

        TableNode table(String name) {
            return tables.computeIfAbsent(name, key -> new TableNode());
        }
    }

    /** テーブルの節（書き出しの組み立ての中だけで使う）。 */
    private static final class TableNode {

        private PermissionValues values;

        private final Map<String, MainPermission> columns = new TreeMap<>(TransferOrder.NAME);
    }
}
