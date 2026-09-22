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
package cherry.mastersmith.auth.testsupport;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Spring を起動するすべてのテスト（U1 の結合テストを含む）で、署名鍵が設定されていなければ、実行のたびに作る 32 バイトの乱数
 * （Base64）を入れる（計画の C6）。鍵の値はリポジトリに置かない。
 *
 * <p>テストのソースの中の {@code META-INF/spring.factories} で登録する。鍵が無いときの起動の失敗を確かめるテストは、
 * {@code mastersmith.test-fixture.signing-key-injection=false} で入れないようにする。
 */
public class TestSigningKeyEnvironmentPostProcessor implements EnvironmentPostProcessor {

    /** 署名鍵の設定の名前。 */
    public static final String SIGNING_KEY = "mastersmith.auth.signing-key";

    /** 鍵を入れるかどうかの設定の名前（既定は入れる）。 */
    public static final String INJECTION = "mastersmith.test-fixture.signing-key-injection";

    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.getProperty(INJECTION, Boolean.class, true)) {
            return;
        }
        String current = environment.getProperty(SIGNING_KEY);
        if (current != null && !current.isBlank()) {
            return;
        }
        environment
                .getPropertySources()
                .addFirst(new MapPropertySource("testSigningKey", Map.of(SIGNING_KEY, randomKey(32))));
    }

    /**
     * 指定したバイト数の乱数を Base64 にした鍵を作る。
     *
     * @param bytes バイト数
     * @return Base64 の鍵
     */
    public static String randomKey(int bytes) {
        byte[] key = new byte[bytes];
        RANDOM.nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }
}
