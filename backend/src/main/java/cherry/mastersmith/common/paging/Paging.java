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
package cherry.mastersmith.common.paging;

import java.util.OptionalInt;
import java.util.regex.Pattern;

/**
 * 管理の一覧（招待の一覧・利用者の一覧）のページ送りの計算（BR1.1〜BR1.5）。DB・時刻・設定・ほかの機能に依存しない純粋な関数。1ページは 20 件。
 */
public final class Paging {

    /**
     * 1ページの件数（契約 C2 の PAGE_SIZE、BR1.1）。応答の size にもこの値を入れる。
     *
     * <p>画面の {@code frontend/src/shared/paging/paging.ts} の {@code PAGE_SIZE} と同じ値にする（変えるときは両方を同じ変更で直す）。
     */
    public static final int PAGE_SIZE = 20;

    private static final Pattern DIGITS = Pattern.compile("[0-9]{1,9}");

    private Paging() {}

    /**
     * 一覧の要求の page を読む（BR1.2）。
     *
     * @param raw 問い合わせの値（無ければ null）
     * @return 1 以上の整数なら page（無ければ 1）。1 未満・整数でない・空は空。数字は 1〜9 桁だけを受け、10 桁以上
     *     （{@code "9999999999"} や先頭に 0 を重ねた 10 桁以上の数字など）も空
     */
    public static OptionalInt parsePage(String raw) {
        if (raw == null) {
            return OptionalInt.of(1);
        }
        if (!DIGITS.matcher(raw).matches()) {
            return OptionalInt.empty();
        }
        int page = Integer.parseInt(raw);
        return page >= 1 ? OptionalInt.of(page) : OptionalInt.empty();
    }

    /**
     * 一覧の並びでの位置（1 から数える）から、その行が載るページを返す（位置 ÷ 20 の切り上げ、BR1.4）。
     *
     * @param position 位置（1 以上）
     * @return ページ
     * @throws IllegalArgumentException 位置が 1 未満のとき
     * @throws ArithmeticException ページが int の範囲を超えるとき（{@code Math.toIntExact} による。位置が
     *     {@code (long) Integer.MAX_VALUE * 20} を超えるとき）
     */
    public static int pageOf(long position) {
        if (position < 1) {
            throw new IllegalArgumentException("位置は 1 以上です: " + position);
        }
        return Math.toIntExact((position - 1) / PAGE_SIZE + 1);
    }

    /**
     * ページの読み始めの位置（0 から数える）を返す（BR1.3）。
     *
     * @param page ページ（1 以上）
     * @return 読み始めの位置
     * @throws IllegalArgumentException ページが 1 未満のとき
     */
    public static long offsetOf(int page) {
        if (page < 1) {
            throw new IllegalArgumentException("ページは 1 以上です: " + page);
        }
        return (long) (page - 1) * PAGE_SIZE;
    }
}
