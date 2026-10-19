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

import java.io.Serial;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 締め切りつきで DSL の正規表現を値の全体に当てる部品（Intent 261016-business-data-views の U2、BR4.1・BR4.2、NFR4.7、
 * security-design.md の 5節・performance-design.md の 3節）。
 *
 * <p>照合は、締め切りを見る {@link CharSequence} の包みで行い、別のスレッドを残さない。包みは {@value #CHECK_INTERVAL} 文字を読む
 * ごとに注入した時計を読み、締め切りを過ぎていたら、メッセージもスタックトレースも持たない例外で照合を打ち切る（部品の中で
 * {@link MatchResult#TIMEOUT} に変え、外へ出さない）。締め切りは、値ごとに {@value #PER_VALUE_MILLIS} ms と、1要求の合計
 * {@value #TOTAL_MILLIS} ms の早い方。合計を使い切ったら、残りの値は照合せずに {@link MatchResult#TIMEOUT} にする。
 *
 * <p>組み立てた式は、DSL の識別（{@code dslHash}）と式の組ごとに、数を限って取っておく。
 */
public final class DeadlinePatternMatcher {

    /** 値ごとの照合の時間の上限（ms）。 */
    public static final long PER_VALUE_MILLIS = 100;

    /** 1要求の照合の時間の合計の上限（ms）。 */
    public static final long TOTAL_MILLIS = 300;

    /** 締め切りを見る間隔（読んだ文字の数）。 */
    static final int CHECK_INTERVAL = 256;

    /** 取っておく式の数の上限。 */
    static final int CACHE_LIMIT = 1_024;

    private final Clock clock;

    private final Map<CacheKey, Pattern> cache = new LinkedHashMap<>(64, 0.75f, true) {
        @Serial
        private static final long serialVersionUID = 1L;

        @Override
        protected boolean removeEldestEntry(Map.Entry<CacheKey, Pattern> eldest) {
            return size() > CACHE_LIMIT;
        }
    };

    /**
     * 作る。
     *
     * @param clock 締め切りを決める時計（テストでは進め方を決めた時計を渡す）
     */
    public DeadlinePatternMatcher(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock は必須です");
    }

    /** 照合の結果。 */
    public enum MatchResult {
        /** 全体が合う。 */
        MATCH,
        /** 合わない（式を組み立てられないときを含む）。 */
        MISMATCH,
        /** 締め切りで打ち切った。 */
        TIMEOUT
    }

    /**
     * 1要求の照合の時間の合計の残り（要求ごとに {@link #budget()} で作り、使い回さない）。
     */
    public static final class Budget {

        private long remainingMillis;

        private Budget(long remainingMillis) {
            this.remainingMillis = remainingMillis;
        }

        /**
         * 残りの ms を返す。
         *
         * @return 残り（0 以下なら使い切った）
         */
        public long remainingMillis() {
            return remainingMillis;
        }
    }

    /**
     * 1要求の照合の時間の合計を作る。
     *
     * @return 合計の残り
     */
    public Budget budget() {
        return new Budget(TOTAL_MILLIS);
    }

    /**
     * 値の全体が式に合うかを、締め切りつきで確かめる。
     *
     * @param dslHash 式を取っておく DSL の識別
     * @param regex DSL の式
     * @param value 値（null でない）
     * @param budget 1要求の合計の残り
     * @return 結果
     */
    public MatchResult match(String dslHash, String regex, String value, Budget budget) {
        Objects.requireNonNull(value, "value は必須です");
        Objects.requireNonNull(budget, "budget は必須です");
        if (budget.remainingMillis <= 0) {
            return MatchResult.TIMEOUT;
        }
        Pattern pattern;
        try {
            pattern = compile(dslHash, regex);
        } catch (PatternSyntaxException e) {
            // DSL の読み込みで式は確かめてある。組み立てられない式は、合わないものとして扱う（閉じる向き）。
            return MatchResult.MISMATCH;
        }
        long start = clock.millis();
        long deadline = start + Math.min(PER_VALUE_MILLIS, budget.remainingMillis);
        try {
            return pattern.matcher(new DeadlineSequence(value, clock, deadline, new int[1]))
                            .matches()
                    ? MatchResult.MATCH
                    : MatchResult.MISMATCH;
        } catch (PatternDeadline e) {
            return MatchResult.TIMEOUT;
        } catch (StackOverflowError e) {
            // 入れ子の深い式の後戻りでスタックが尽きたときも、照合を打ち切ったものとして扱う。
            return MatchResult.TIMEOUT;
        } finally {
            budget.remainingMillis -= Math.max(0, clock.millis() - start);
        }
    }

    private Pattern compile(String dslHash, String regex) {
        CacheKey key = new CacheKey(dslHash, regex);
        synchronized (cache) {
            Pattern cached = cache.get(key);
            if (cached != null) {
                return cached;
            }
        }
        Pattern compiled = Pattern.compile(regex);
        synchronized (cache) {
            cache.put(key, compiled);
        }
        return compiled;
    }

    /**
     * 取っておいた式の数を返す（テスト用）。
     *
     * @return 数
     */
    int cachedPatterns() {
        synchronized (cache) {
            return cache.size();
        }
    }

    private record CacheKey(String dslHash, String regex) {}

    /**
     * 締め切りを見る文字の並び。文字列にするときは値を伏せる（{@code ***}）。
     *
     * @param value 値
     * @param clock 時計
     * @param deadline 締め切り（ms）
     * @param count 読んだ文字の数（部分の並びと共有する）
     */
    private record DeadlineSequence(CharSequence value, Clock clock, long deadline, int[] count)
            implements CharSequence {

        @Override
        public char charAt(int index) {
            if (++count[0] % CHECK_INTERVAL == 0 && clock.millis() - deadline >= 0) {
                throw new PatternDeadline();
            }
            return value.charAt(index);
        }

        @Override
        public int length() {
            return value.length();
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return new DeadlineSequence(value.subSequence(start, end), clock, deadline, count);
        }

        @Override
        public String toString() {
            return "***";
        }
    }

    /** 締め切りを過ぎたことを表す（メッセージもスタックトレースも持たない。部品の外へ出さない）。 */
    private static final class PatternDeadline extends RuntimeException {

        @Serial
        private static final long serialVersionUID = 1L;

        PatternDeadline() {
            super(null, null, false, false);
        }
    }
}
