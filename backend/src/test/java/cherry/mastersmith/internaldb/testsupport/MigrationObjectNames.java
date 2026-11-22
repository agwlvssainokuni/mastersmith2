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
package cherry.mastersmith.internaldb.testsupport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * 内部DB の移行の SQL から、表・名前付きの制約・索引の名前を取り出す、テストの補助（BR2.8）。H2 用と PostgreSQL 用の移行の
 * 突き合わせに使う。名前の無い主キーは取り出さない（比べない）。
 */
public final class MigrationObjectNames {

    private static final Pattern FILE_NAME = Pattern.compile("V(\\d+)__(.+)\\.sql");
    private static final Pattern CREATE_TABLE =
            Pattern.compile("(?i)\\bCREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern CONSTRAINT = Pattern.compile("(?i)\\bCONSTRAINT\\s+([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern CREATE_INDEX =
            Pattern.compile("(?i)\\bCREATE\\s+(?:UNIQUE\\s+)?INDEX\\s+([A-Za-z_][A-Za-z0-9_]*)");

    private MigrationObjectNames() {}

    /**
     * SQL の {@code --} 行のコメント（行頭の空白を許す）と行末のコメントを取り除く。
     *
     * @param sql SQL の文字列
     * @return コメントを除いた SQL
     */
    public static String stripComments(String sql) {
        StringBuilder out = new StringBuilder();
        for (String line : sql.split("\n", -1)) {
            int at = line.indexOf("--");
            out.append(at < 0 ? line : line.substring(0, at)).append('\n');
        }
        return out.toString();
    }

    /**
     * SQL から、表・名前付きの制約・索引の名前の集まり（小文字）を取り出す。コメントは先に取り除く。
     *
     * @param sql SQL の文字列
     * @return 名前の集まり（整列済み）
     */
    public static Set<String> objectNames(String sql) {
        String body = stripComments(sql);
        Set<String> names = new TreeSet<>();
        for (Pattern pattern : List.of(CREATE_TABLE, CONSTRAINT, CREATE_INDEX)) {
            Matcher matcher = pattern.matcher(body);
            while (matcher.find()) {
                names.add(matcher.group(1).toLowerCase(Locale.ROOT));
            }
        }
        return names;
    }

    /**
     * 置き場（クラスパスの場所。例: {@code db/migration}）の移行のファイルを、版の番号の順に読む。置き場の下を再帰して探す。
     *
     * @param location クラスパスの場所（先頭・末尾にスラッシュを付けない）
     * @return 版の番号 → ファイル
     */
    public static SortedMap<Integer, MigrationFile> load(String location) {
        SortedMap<Integer, MigrationFile> files = new TreeMap<>();
        try {
            Resource[] resources =
                    new PathMatchingResourcePatternResolver().getResources("classpath*:" + location + "/**/*.sql");
            for (Resource resource : resources) {
                String name = resource.getFilename();
                Matcher matcher = name == null ? null : FILE_NAME.matcher(name);
                if (matcher == null || !matcher.matches()) {
                    continue;
                }
                String sql = new String(resource.getContentAsByteArray(), StandardCharsets.UTF_8);
                int version = Integer.parseInt(matcher.group(1));
                files.put(version, new MigrationFile(version, matcher.group(2), name, sql));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return files;
    }

    /**
     * 置き場にあるすべての {@code .sql} の名前（再帰して探す。名前の形を問わない）。
     *
     * @param location クラスパスの場所
     * @return ファイル名の一覧
     */
    public static List<String> allSqlFileNames(String location) {
        List<String> names = new ArrayList<>();
        try {
            for (Resource resource :
                    new PathMatchingResourcePatternResolver().getResources("classpath*:" + location + "/**/*.sql")) {
                names.add(String.valueOf(resource.getFilename()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        names.sort(String::compareTo);
        return names;
    }

    /**
     * 移行のファイル 1 本。
     *
     * @param version 版の番号
     * @param description 説明（ファイル名の {@code __} の後ろ）
     * @param fileName ファイル名
     * @param sql 中身
     */
    public record MigrationFile(int version, String description, String fileName, String sql) {}
}
