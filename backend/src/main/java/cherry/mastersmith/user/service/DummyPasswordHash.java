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
package cherry.mastersmith.user.service;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * ダミーのハッシュ（BR2.5）。起動時に、設定の cost で1つ作る。利用者がいない・パスワードが 72 バイトを超えるときの照合に使い、
 * 照合の時間をそろえる。
 *
 * <p>作ったときに照合1回の時間を測って INFO（{@code bcryptCost}・{@code elapsedMs}）で出し、100〜500 ミリ秒の外なら WARN で
 * cost の見直しを促す（NFR2.1、observability-design 1章）。
 */
@Component
public class DummyPasswordHash {

    /** 照合の時間の目安の下限（ミリ秒）。 */
    static final long MIN_EXPECTED_MILLIS = 100;

    /** 照合の時間の目安の上限（ミリ秒）。 */
    static final long MAX_EXPECTED_MILLIS = 500;

    private static final Logger LOGGER = LoggerFactory.getLogger(DummyPasswordHash.class);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final String hash;

    private final String input;

    /**
     * ダミーのハッシュを作り、照合の時間を測る。
     *
     * @param encoder パスワードのハッシュの仕組み
     * @param properties パスワードのハッシュの設定
     */
    @Autowired
    public DummyPasswordHash(PasswordEncoder encoder, PasswordProperties properties) {
        this(encoder, properties, System::nanoTime);
    }

    /**
     * 時間の測り方を指定して作る（テストで使う）。
     *
     * @param encoder パスワードのハッシュの仕組み
     * @param properties パスワードのハッシュの設定
     * @param nanoTime 経過時間の測り方
     */
    DummyPasswordHash(PasswordEncoder encoder, PasswordProperties properties, LongSupplier nanoTime) {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        this.input = HexFormat.of().formatHex(bytes);
        this.hash = encoder.encode(input);
        long start = nanoTime.getAsLong();
        encoder.matches(input, hash);
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(nanoTime.getAsLong() - start);
        if (elapsedMillis < MIN_EXPECTED_MILLIS || elapsedMillis > MAX_EXPECTED_MILLIS) {
            LOGGER.atWarn()
                    .addKeyValue("bcryptCost", properties.bcryptCost())
                    .addKeyValue("elapsedMs", elapsedMillis)
                    .log("パスワードの照合の時間が目安（100〜500 ミリ秒）の外です。cost の見直しを検討してください");
        } else {
            LOGGER.atInfo()
                    .addKeyValue("bcryptCost", properties.bcryptCost())
                    .addKeyValue("elapsedMs", elapsedMillis)
                    .log("パスワードの照合の時間を測りました");
        }
    }

    /**
     * ダミーのハッシュを返す。
     *
     * @return ダミーのハッシュ
     */
    public String hash() {
        return hash;
    }

    /**
     * 72 バイトを超えるパスワードの代わりに照合に渡す入力を返す（照合の仕組みに長すぎる入力を渡さないため）。
     *
     * @return 代わりの入力
     */
    public String substituteInput() {
        return input;
    }

    /** 中身を伏せて文字列にする。 */
    @Override
    public String toString() {
        return "DummyPasswordHash[***]";
    }
}
