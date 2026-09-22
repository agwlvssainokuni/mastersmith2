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
package cherry.mastersmith.common.i18n.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 要求の Accept-Language から表示言語を決める（BR6.3）。
 *
 * <p>各言語を q 値の大きい順に並べ（q=0 は除く、q 値が同じなら書かれた順）、先頭の言語部分（{@code ja-JP} なら {@code ja}）が
 * 最初に ja または en に当たったものを使う。ヘッダーが無い、形式が正しくない、どれにも当たらない場合は日本語にする。
 */
public final class AcceptLanguageResolver {

    /** 見る言語の数の上限（極端に長いヘッダーで処理を重くしない）。 */
    private static final int MAX_ENTRIES = 50;

    private AcceptLanguageResolver() {}

    /**
     * Accept-Language から表示言語を決める。
     *
     * @param header Accept-Language の値（無ければ null）
     * @return 表示言語（既定は日本語）
     */
    public static DisplayLanguage resolve(String header) {
        if (header == null || header.isBlank()) {
            return DisplayLanguage.JA;
        }
        List<Entry> entries = new ArrayList<>();
        String[] parts = header.split(",", MAX_ENTRIES + 1);
        for (int i = 0; i < Math.min(parts.length, MAX_ENTRIES); i++) {
            Entry entry = parse(parts[i], i);
            if (entry != null && entry.quality() > 0.0) {
                entries.add(entry);
            }
        }
        entries.sort(Comparator.comparingDouble(Entry::quality).reversed().thenComparingInt(Entry::order));
        for (Entry entry : entries) {
            String primary = entry.tag().split("-", 2)[0].toLowerCase(Locale.ROOT);
            if (primary.equals("ja")) {
                return DisplayLanguage.JA;
            }
            if (primary.equals("en")) {
                return DisplayLanguage.EN;
            }
        }
        return DisplayLanguage.JA;
    }

    /** 1つの言語の指定を読む。形式が正しくなければ null。 */
    private static Entry parse(String part, int order) {
        String[] pieces = part.split(";");
        String tag = pieces[0].strip();
        if (tag.isEmpty() || !tag.matches("[A-Za-z]{1,8}(-[A-Za-z0-9]{1,8})*|\\*")) {
            return null;
        }
        double quality = 1.0;
        for (int i = 1; i < pieces.length; i++) {
            String param = pieces[i].strip();
            if (param.startsWith("q=") || param.startsWith("Q=")) {
                String value = param.substring(2).strip();
                if (!value.matches("0(\\.\\d{0,3})?|1(\\.0{0,3})?")) {
                    return null;
                }
                quality = Double.parseDouble(value);
            }
        }
        return new Entry(tag, quality, order);
    }

    private record Entry(String tag, double quality, int order) {}
}
