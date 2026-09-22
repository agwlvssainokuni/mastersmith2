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
package cherry.mastersmith.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import cherry.mastersmith.common.observability.SanitizingSpanExporter;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 外部エクスポート（トレース・ログ・指標の OTLP の送信）の組み立て（FR10.4、BR4.3〜BR4.5、observability-design 6章）。
 *
 * <p>送信の有効・無効は {@code mastersmith.observability.export.enabled}（既定は無効）1つで切り替え、application.yaml で
 * Spring Boot の OTLP の設定に対応づける。無効のときは送信の仕組みを作らない。トレースとログの送信は、Spring Boot が作る上限付きの
 * 待ち行列（あふれたら捨てる）から要求と切り離して行い、送信の失敗は警告のログになるだけで要求の処理を止めない。
 */
@Configuration(proxyBeanMethods = false)
public class ObservabilityConfig {

    /**
     * 外部へ送るトレースから例外のメッセージとスタックトレースを取り除くよう、送信の仕組みを包む。
     *
     * @return 送信の仕組みを包む後処理
     */
    @Bean
    public static BeanPostProcessor sanitizingSpanExporterPostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof SpanExporter exporter && !(bean instanceof SanitizingSpanExporter)) {
                    return new SanitizingSpanExporter(exporter);
                }
                return bean;
            }
        };
    }

    /**
     * 外部エクスポートを有効にしたときだけ、ログを OTLP で送る出力（OpenTelemetry の logback 用の出力）を加える。
     * 標準出力への出力はそのまま残す。
     *
     * @param openTelemetry OpenTelemetry の仕組み
     * @return 出力の取り付け
     */
    @Bean
    @ConditionalOnBooleanProperty("mastersmith.observability.export.enabled")
    public OtlpLogAppenderInstaller otlpLogAppenderInstaller(OpenTelemetry openTelemetry) {
        return new OtlpLogAppenderInstaller(openTelemetry);
    }

    /** ログを OTLP で送る出力を、ルートのロガーに取り付ける（停止のときに外す）。 */
    public static class OtlpLogAppenderInstaller implements InitializingBean, DisposableBean {

        /** 取り付ける出力の名前。 */
        public static final String APPENDER_NAME = "OTLP_EXPORT";

        private final OpenTelemetry openTelemetry;

        private OpenTelemetryAppender appender;

        /**
         * 取り付けの仕組みを作る。
         *
         * @param openTelemetry OpenTelemetry の仕組み
         */
        public OtlpLogAppenderInstaller(OpenTelemetry openTelemetry) {
            this.openTelemetry = openTelemetry;
        }

        @Override
        public void afterPropertiesSet() {
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
            appender = new OpenTelemetryAppender();
            appender.setName(APPENDER_NAME);
            appender.setContext(context);
            appender.start();
            context.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(appender);
            OpenTelemetryAppender.install(openTelemetry);
        }

        @Override
        public void destroy() {
            if (appender != null) {
                LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
                context.getLogger(Logger.ROOT_LOGGER_NAME).detachAppender(appender);
                appender.stop();
            }
        }
    }
}
