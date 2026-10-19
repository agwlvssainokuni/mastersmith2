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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 業務データの監査の {@code detail}（主キーの JSON）を作る純粋な関数（Intent 261016-business-data-views の U2、BR11.4、NFR 要件の
 * 承認の場の U2 R-01）。{@code {"key":{カラム:値},"keyTruncated":偽}} の形で、全体が上限（{@value #MAX_LENGTH} 字。UTF-16 の単位）を
 * 超えるなら、値を後ろのカラムから、後ろから切って上限に収め、{@code keyTruncated} を真にする（黙って切らない）。切り詰めは
 * {@code businessdata} の側で行い、{@code AuditEvent} の「超えたら作らない」確かめは変えない。
 *
 * <p>businessdata は JSON の部品（Jackson の本体）に依存しないため、決まった形の JSON を自分で組み立てる。文字は JSON の決まりで
 * エスケープする。サロゲートペアは途中で切らない。
 */
public final class AuditDetailBuilder {

    /** {@code detail} の長さの上限（UTF-16 の単位。監査の列 {@code detail} の上限）。 */
    public static final int MAX_LENGTH = 16_384;

    private AuditDetailBuilder() {}

    /**
     * 作った {@code detail} と、主キーのカラムの数・切り詰めたか。
     *
     * @param json {@code detail} の JSON（主キーが無ければ null）
     * @param keyColumnCount 主キーのカラムの数
     * @param keyTruncated 値を切り詰めたなら true
     */
    public record AuditDetail(String json, int keyColumnCount, boolean keyTruncated) {

        /** 値を伏せ、数と切り詰めたかだけを出す。 */
        @Override
        public String toString() {
            return "AuditDetail[keyColumns=" + keyColumnCount + ", keyTruncated=" + keyTruncated + "]";
        }
    }

    /**
     * 主キーの値から {@code detail} を作る。
     *
     * @param key 主キーのカラムと文字の値（主キーの順。無ければ空。値は null を許す）
     * @return {@code detail}（主キーが空なら JSON は null）
     */
    public static AuditDetail build(Map<String, String> key) {
        Objects.requireNonNull(key, "key は必須です");
        if (key.isEmpty()) {
            return new AuditDetail(null, 0, false);
        }
        Map<String, String> values = new LinkedHashMap<>(key);
        String json = json(values, false);
        if (json.length() <= MAX_LENGTH) {
            return new AuditDetail(json, key.size(), false);
        }
        List<String> columns = new ArrayList<>(values.keySet());
        for (int i = columns.size() - 1; i >= 0; i--) {
            String column = columns.get(i);
            String value = values.get(column);
            if (value == null || value.isEmpty()) {
                continue;
            }
            // この値を短くして収まる最も長い前の部分を探す（コードポイントの境で切る）。
            int low = 0;
            int high = value.codePointCount(0, value.length());
            while (low < high) {
                int middle = (low + high + 1) >>> 1;
                values.put(column, prefix(value, middle));
                if (json(values, true).length() <= MAX_LENGTH) {
                    low = middle;
                } else {
                    high = middle - 1;
                }
            }
            values.put(column, prefix(value, low));
            String truncated = json(values, true);
            if (truncated.length() <= MAX_LENGTH) {
                return new AuditDetail(truncated, key.size(), true);
            }
        }
        // 値をすべて空にしても収まらない（カラムの名前だけで上限を超える）。名前は DSL のもので、ここに来ることは無い想定。
        throw new IllegalArgumentException("主キーのカラムの名前だけで detail の上限を超えます");
    }

    private static String prefix(String value, int codePoints) {
        return value.substring(0, value.offsetByCodePoints(0, codePoints));
    }

    private static String json(Map<String, String> values, boolean truncated) {
        StringBuilder builder = new StringBuilder("{\"key\":{");
        boolean first = true;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            quote(builder, entry.getKey());
            builder.append(':');
            if (entry.getValue() == null) {
                builder.append("null");
            } else {
                quote(builder, entry.getValue());
            }
        }
        return builder.append("},\"keyTruncated\":")
                .append(truncated)
                .append('}')
                .toString();
    }

    private static void quote(StringBuilder builder, String text) {
        builder.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                default -> {
                    if (c < 0x20) {
                        builder.append(String.format("\\u%04x", (int) c));
                    } else {
                        builder.append(c);
                    }
                }
            }
        }
        builder.append('"');
    }
}
