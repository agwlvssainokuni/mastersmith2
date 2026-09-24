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
package cherry.mastersmith.targetdb.testsupport;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.opentest4j.TestAbortedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;

/**
 * 対象DB のコンテナを使うテストのクラスの前に、コンテナの実行環境（Docker）に届くかを確かめる JUnit 5 の拡張（NFR12.3、
 * team.md の Way of Working）。
 *
 * <ul>
 *   <li>届く: そのまま進む。
 *   <li>届かず、CI（環境変数 {@code CI=true}。GitHub Actions が設定する）: 失敗させる（CI では必ず実行する）。
 *   <li>届かず、開発中: WARN を出し、そのクラスのテストを中断（飛ばした扱い）にする。黙って飛ばさない。この状態では統合しない。
 * </ul>
 */
public class ContainerRuntimeCheck implements BeforeAllCallback {

    /** 開発中にコンテナの実行環境が無いときの警告の文。 */
    public static final String SKIP_MESSAGE = "コンテナの実行環境が無いため対象DB のテストを飛ばした。この状態では統合しない（colima を起動してやり直す）";

    private static final Logger LOGGER = LoggerFactory.getLogger(ContainerRuntimeCheck.class);

    @Override
    public void beforeAll(ExtensionContext context) {
        decide(DockerClientFactory.instance().isDockerAvailable(), "true".equalsIgnoreCase(System.getenv("CI")));
    }

    /**
     * 届くかどうかと CI かどうかから、進む・失敗・中断を決める。
     *
     * @param dockerAvailable コンテナの実行環境に届くか
     * @param ci CI で動いているか
     */
    static void decide(boolean dockerAvailable, boolean ci) {
        if (dockerAvailable) {
            return;
        }
        if (ci) {
            throw new IllegalStateException("CI ではコンテナの実行環境が必要です。対象DB のテストを飛ばさずに失敗させます");
        }
        LOGGER.warn(SKIP_MESSAGE);
        System.err.println("警告: " + SKIP_MESSAGE);
        throw new TestAbortedException(SKIP_MESSAGE);
    }
}
