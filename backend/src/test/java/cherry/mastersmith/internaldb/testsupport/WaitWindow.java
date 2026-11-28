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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.Callable;

/**
 * 待ちの時間の幅の確かめ（NFR 設計 DD-1・DD-10）。時間は待つ側の呼び出しの開始から、失敗が戻るまでを測る（観測の時点から測らない）。
 * 幅は要件のまま（書き込み: 2 秒以上 3 秒未満、行の排他の読み取り: 3 秒以上 4 秒未満）で、幅を広げる口を持たない。時間は判定の前に
 * {@code INTERNAL_DB_PG_WAIT <名前> waitMs=<値>} の 1 行で出す。
 */
public final class WaitWindow {

    /** 書き込みの待ちの下限（接続の {@code lock_timeout} 2 秒）。 */
    public static final long WRITE_MIN_MILLIS = 2_000L;

    /** 書き込みの待ちの上限（これ未満）。 */
    public static final long WRITE_MAX_MILLIS = 3_000L;

    /** 行の排他の読み取りの待ちの下限（ヒントの 3 秒）。 */
    public static final long ROW_LOCK_MIN_MILLIS = 3_000L;

    /** 行の排他の読み取りの待ちの上限（これ未満）。 */
    public static final long ROW_LOCK_MAX_MILLIS = 4_000L;

    private WaitWindow() {}

    /**
     * 呼び出しの結果と、呼び出しの開始から終わりまでの時間。
     *
     * @param value 呼び出しの結果
     * @param millis 時間（ミリ秒）
     * @param <T> 結果の型
     */
    public record Timed<T>(T value, long millis) {}

    /**
     * 呼び出しを実行し、開始から終わりまでの時間を測る。
     *
     * @param call 呼び出し（待つ側の要求）
     * @param <T> 結果の型
     * @return 結果と時間
     */
    public static <T> Timed<T> measure(Callable<T> call) {
        long start = System.nanoTime();
        T value;
        try {
            value = call.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "the measured call failed: " + e.getClass().getSimpleName());
        }
        return new Timed<>(value, (System.nanoTime() - start) / 1_000_000L);
    }

    /**
     * 時間を出して、書き込みの待ちの幅（2 秒以上 3 秒未満）に入ることを確かめる。
     *
     * @param name テストの名前
     * @param millis 呼び出しの開始からの時間
     */
    public static void expectWrite(String name, long millis) {
        expect(name, millis, WRITE_MIN_MILLIS, WRITE_MAX_MILLIS);
    }

    /**
     * 時間を出して、行の排他の読み取りの待ちの幅（3 秒以上 4 秒未満）に入ることを確かめる。
     *
     * @param name テストの名前
     * @param millis 呼び出しの開始からの時間
     */
    public static void expectRowLock(String name, long millis) {
        expect(name, millis, ROW_LOCK_MIN_MILLIS, ROW_LOCK_MAX_MILLIS);
    }

    private static void expect(String name, long millis, long minInclusive, long maxExclusive) {
        System.out.println("INTERNAL_DB_PG_WAIT " + name + " waitMs=" + millis);
        assertThat(millis)
                .as("the wait from the call start must be within [%d, %d) ms", minInclusive, maxExclusive)
                .isGreaterThanOrEqualTo(minInclusive)
                .isLessThan(maxExclusive);
    }
}
