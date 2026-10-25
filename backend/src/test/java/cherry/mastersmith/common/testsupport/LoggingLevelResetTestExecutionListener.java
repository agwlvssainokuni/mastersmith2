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
package cherry.mastersmith.common.testsupport;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import java.util.Map;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

/**
 * Spring の文脈を使うテストの前に、ロガーの水準をそのテストの文脈の設定（{@code logging.level.*}）に合わせ直す
 * （Intent 261025-deps-perf-carryover の計画 B1・Spring Boot 4.1.2）。
 *
 * <p>なぜ: 別のテストが {@code logging.level.<名前>=TRACE} を指定した文脈を起動すると、ロガーの水準は JVM の中に残る。
 * Spring Boot 4.1.1 まではキャッシュ中の文脈を一時停止するときにロギングの後片付けが走り、水準が戻っていた。
 * 4.1.2 でロギングの後片付けは一時停止の対象から外れた（{@code LoggingApplicationListener.Lifecycle.isPauseable()} が偽）ため、
 * TRACE が次のテストへ漏れ、ログの出来事の件数を確かめるテスト（{@code AuditWriteFailureIT} など）が TRACE の出力を余分に数えて落ちる。
 * そのため、テストの前に、TRACE・DEBUG を明示したロガーのうち文脈の設定に無いものを継承へ戻し、設定にあるものを設定どおりにする。
 * Spring Boot が既定で入れる WARN・OFF などの水準（例: Hibernate や Tomcat の一部）は触らない。
 *
 * <p>テストのソースの中の {@code META-INF/spring.factories} で登録する。Logback を使っていないときは何もしない。
 */
public class LoggingLevelResetTestExecutionListener extends AbstractTestExecutionListener {

    @Override
    public int getOrder() {
        // 文脈の読み込みと注入（DependencyInjectionTestExecutionListener）より後ろで、他の補助より前に動く。
        return Ordered.LOWEST_PRECEDENCE - 100;
    }

    @Override
    public void prepareTestInstance(TestContext testContext) {
        ILoggerFactory factory = LoggerFactory.getILoggerFactory();
        if (!(factory instanceof LoggerContext loggerContext)) {
            return;
        }
        Environment environment = testContext.getApplicationContext().getEnvironment();
        Map<String, String> configured = Binder.get(environment)
                .bind("logging.level", Bindable.mapOf(String.class, String.class))
                .orElse(Map.of());
        for (Logger logger : loggerContext.getLoggerList()) {
            String name = logger.getName();
            Level level = logger.getLevel();
            if (Logger.ROOT_LOGGER_NAME.equals(name) || level == null) {
                continue;
            }
            if (level.isGreaterOrEqual(Level.INFO)) {
                continue;
            }
            if (!configured.containsKey(name)) {
                logger.setLevel(null);
            }
        }
        configured.forEach((name, level) -> {
            String loggerName = "root".equalsIgnoreCase(name) ? Logger.ROOT_LOGGER_NAME : name;
            loggerContext.getLogger(loggerName).setLevel(toLevel(level));
        });
    }

    /** 設定の値を Logback の水準にする（YAML の {@code OFF} は {@code false} として届くため、Spring Boot と同じく OFF に読む）。 */
    private static Level toLevel(String value) {
        if ("false".equalsIgnoreCase(value)) {
            return Level.OFF;
        }
        return Level.toLevel(value, null);
    }
}
