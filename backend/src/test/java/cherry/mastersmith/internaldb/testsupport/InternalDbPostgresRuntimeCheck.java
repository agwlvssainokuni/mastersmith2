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

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.opentest4j.TestAbortedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;

/**
 * 内部DB の PostgreSQL の結合テストのクラスの前に、コンテナの実行環境（Docker）に届くかを確かめる JUnit 5 の拡張（BR6.4）。
 *
 * <ul>
 *   <li>届く: そのまま進む。
 *   <li>届かず、CI（環境変数 {@code CI=true}）またはシステムプロパティ {@value #REQUIRE_PROPERTY}{@code =true}: 失敗させる。
 *   <li>届かず、開発中: WARN を出し、そのクラスのテストを中断（飛ばした扱い）にする。黙って飛ばさない。この状態では統合しない。
 * </ul>
 */
public class InternalDbPostgresRuntimeCheck implements BeforeAllCallback {

    /** 開発中にコンテナの実行環境が無いときの警告の文。 */
    public static final String SKIP_MESSAGE = "コンテナの実行環境が無いため内部DB の PostgreSQL のテストを飛ばした。この状態では統合しない（colima を起動してやり直す）";

    /** 飛ばさずに失敗させることを求めるシステムプロパティの名前。 */
    public static final String REQUIRE_PROPERTY = "mastersmith.requireContainerRuntime";

    private static final Logger LOGGER = LoggerFactory.getLogger(InternalDbPostgresRuntimeCheck.class);

    @Override
    public void beforeAll(ExtensionContext context) {
        decide(
                DockerClientFactory.instance().isDockerAvailable(),
                requiresContainerRuntime(System.getenv("CI"), System.getProperty(REQUIRE_PROPERTY)));
    }

    /**
     * 環境変数 {@code CI} とシステムプロパティの値から、コンテナの実行環境を必ず求めるかを決める。
     *
     * @param ciEnv 環境変数 {@code CI} の値（無ければ {@code null}）
     * @param requireProperty システムプロパティ {@value #REQUIRE_PROPERTY} の値（無ければ {@code null}）
     * @return どちらかが {@code true}（大文字と小文字を問わない）なら {@code true}
     */
    static boolean requiresContainerRuntime(String ciEnv, String requireProperty) {
        return "true".equalsIgnoreCase(ciEnv) || "true".equalsIgnoreCase(requireProperty);
    }

    /**
     * 届くかどうかと、必ず求めるかから、進む・失敗・中断を決める。
     *
     * @param dockerAvailable コンテナの実行環境に届くか
     * @param required コンテナの実行環境を必ず求めるか
     */
    static void decide(boolean dockerAvailable, boolean required) {
        if (dockerAvailable) {
            return;
        }
        if (required) {
            throw new IllegalStateException("CI ではコンテナの実行環境が必要です。内部DB の PostgreSQL のテストを飛ばさずに失敗させます");
        }
        LOGGER.warn(SKIP_MESSAGE);
        System.err.println("警告: " + SKIP_MESSAGE);
        throw new TestAbortedException(SKIP_MESSAGE);
    }
}
