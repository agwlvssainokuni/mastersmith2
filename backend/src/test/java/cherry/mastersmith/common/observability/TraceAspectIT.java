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
package cherry.mastersmith.common.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cherry.mastersmith.common.error.web.ProblemTypeController;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.JsonLogRecords;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.common.testsupport.service.TraceTargetService;
import cherry.mastersmith.common.web.CacheControlFilter;
import cherry.mastersmith.config.SecurityConfig;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/** メソッドの呼び出しの追跡（TraceAspect）の結合テスト（NFR10.12〜NFR10.14、依頼者の指定した既定値）。 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.test-fixture.trace-target=true")
@ExtendWith(OutputCaptureExtension.class)
class TraceAspectIT {

    private static final String TARGET_LOGGER = TraceTargetService.class.getName();

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @Autowired
    ApplicationContext context;

    @Autowired
    TraceTargetService service;

    @LocalServerPort
    int port;

    private static List<Map<String, Object>> traceLogs(CapturedOutput output) {
        return JsonLogRecords.parse(output.getOut()).stream()
                .filter(record -> TARGET_LOGGER.equals(record.get("logger")))
                .toList();
    }

    @Test
    @DisplayName("with the default INFO level no trace lines are written")
    void silentByDefault(CapturedOutput output) {
        assertThat(service.greet("太郎")).isEqualTo("こんにちは、太郎");

        assertThat(traceLogs(output)).isEmpty();
    }

    @Test
    @DisplayName("defaults from the configuration are bound as specified")
    void defaultsBound() {
        TraceProperties properties = context.getBean(TraceProperties.class);

        assertThat(properties.useDynamicLogger()).isTrue();
        assertThat(properties.hideProxyClassNames()).isTrue();
        assertThat(properties.logExceptionStackTrace()).isTrue();
        assertThat(properties.enterMessage()).isEqualTo("ENTER $[targetClassShortName]#$[methodName]($[arguments])");
        assertThat(properties.exitMessage()).isEqualTo("EXIT  $[targetClassShortName]#$[methodName](): $[returnValue]");
        assertThat(properties.exceptionMessage())
                .isEqualTo("EXCEPTION $[targetClassShortName]#$[methodName](): $[exception]");
    }

    @Test
    @DisplayName("layer beans are proxied while filters and configuration classes are not")
    void proxyTargets() {
        assertThat(AopUtils.isAopProxy(service)).isTrue();
        assertThat(AopUtils.isAopProxy(context.getBean(ProblemTypeController.class)))
                .isTrue();
        assertThat(AopUtils.isAopProxy(context.getBean(CacheControlFilter.class)))
                .isFalse();
        assertThat(AopUtils.isAopProxy(context.getBean(SecurityConfig.class))).isFalse();
        assertThat(AopUtils.isAopProxy(context.getBean(TraceIdProvider.class))).isFalse();
    }

    @Test
    @DisplayName("requests passing through the filters still work with the aspect in place")
    void filtersWork() {
        HttpTestClient client = new HttpTestClient(port);

        assertThat(client.get("/").statusCode()).isEqualTo(200);
        assertThat(client.get("/api/problems/not-found").statusCode()).isEqualTo(200);
        assertThat(client.get("/actuator/health").statusCode()).isEqualTo(200);
    }

    @Nested
    @TestPropertySource(properties = "logging.level.cherry.mastersmith.common.testsupport.service=TRACE")
    @DisplayName("with the TRACE level enabled for the target")
    class TraceEnabled {

        @Autowired
        TraceTargetService nestedService;

        @Test
        @DisplayName("enter and exit lines use the logger of the target class and the configured formats")
        void enterAndExit(CapturedOutput output) {
            nestedService.greet("太郎");

            List<Map<String, Object>> logs = traceLogs(output);
            assertThat(logs).extracting(record -> record.get("level")).containsOnly("TRACE");
            assertThat(logs)
                    .extracting(record -> record.get("message"))
                    .containsSubsequence(
                            "ENTER TraceTargetService#greet(太郎)", "EXIT  TraceTargetService#greet(): こんにちは、太郎");
        }

        @Test
        @DisplayName("exceptions are written with the configured format and a stack trace")
        void exception(CapturedOutput output) {
            assertThatThrownBy(() -> nestedService.fail("失敗の理由")).isInstanceOf(IllegalStateException.class);

            List<Map<String, Object>> logs = traceLogs(output);
            assertThat(logs)
                    .filteredOn(record -> String.valueOf(record.get("message")).startsWith("EXCEPTION "))
                    .singleElement()
                    .satisfies(record -> {
                        assertThat(record.get("message"))
                                .asString()
                                .startsWith("EXCEPTION TraceTargetService#fail(): ")
                                .contains("IllegalStateException");
                        assertThat(record.get("exception")).asString().contains("at ");
                    });
        }
    }
}
