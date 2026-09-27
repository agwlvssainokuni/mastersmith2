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
package cherry.mastersmith.appearance.web;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.MastersmithApplication;
import cherry.mastersmith.appearance.service.AppearanceService;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.JsonLogRecords;
import cherry.mastersmith.common.testsupport.TestDatabase;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 見た目の設定ごとの起動・応答・警告のログの結合テスト（BR1.1〜BR1.4・BR1.7・BR2.1・BR3.1、NFR4.5・NFR9.1・NFR9.4、CR2 の
 * サーバー側の確かめ方）。
 *
 * <p>内部DB は組み込みの H2 を使い、コンテナは使わない。開発者の環境変数に見た目の設定があっても影響しないよう、2つの項目を起動の
 * 引数で明示する。警告は {@link AppearanceService} のロガーの JSON のログだけを数える（起動ごとに Bean が作られるため、1回の起動の
 * 中の件数を数える）。
 */
@ExtendWith(OutputCaptureExtension.class)
class AppearanceStartupIT {

    private static final String SERVICE_LOGGER = AppearanceService.class.getName();

    /** 許されない値（ほかの文字列と重ならない目印を付けて、ログと応答に出ないことを確かめる）。 */
    private static final String DISALLOWED_COLOR = "red";

    private static final String DISALLOWED_FAMILY = "mono";

    private static final String LEAK_MARK = "appearance-leak-check";

    @TempDir
    Path tempDir;

    private static ConfigurableApplicationContext start(Path dir, String brandColor, String fontFamily) {
        return new SpringApplicationBuilder(MastersmithApplication.class)
                .run(
                        "--spring.datasource.url=" + TestDatabase.url(dir),
                        "--server.port=0",
                        "--mastersmith.auth.password.bcrypt-cost=4",
                        "--mastersmith.appearance.brand-color=" + brandColor,
                        "--mastersmith.appearance.font-family=" + fontFamily);
    }

    private static int port(ConfigurableApplicationContext context) {
        return Integer.parseInt(context.getEnvironment().getRequiredProperty("local.server.port"));
    }

    private static HttpResponse<String> getAppearance(ConfigurableApplicationContext context) {
        return new HttpTestClient(port(context)).get(AppearanceController.PATH);
    }

    private static List<Map<String, Object>> serviceRecords(CapturedOutput output) {
        return JsonLogRecords.parse(output.getOut()).stream()
                .filter(record -> SERVICE_LOGGER.equals(record.get("logger")))
                .toList();
    }

    private static List<Map<String, Object>> warnings(CapturedOutput output) {
        return serviceRecords(output).stream()
                .filter(record -> "WARN".equals(record.get("level")))
                .toList();
    }

    @Test
    @DisplayName("red and mono keep the start, answer blue and sans, and warn exactly once per item")
    void disallowedValuesWarnOncePerItem(CapturedOutput output) {
        try (ConfigurableApplicationContext context = start(tempDir, DISALLOWED_COLOR, DISALLOWED_FAMILY)) {
            HttpResponse<String> response = getAppearance(context);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(HttpTestClient.json(response))
                    .containsOnlyKeys("brandColor", "fontFamily")
                    .containsEntry("brandColor", "blue")
                    .containsEntry("fontFamily", "sans");
            assertThat(response.body()).doesNotContain(DISALLOWED_COLOR).doesNotContain(DISALLOWED_FAMILY);

            assertThat(warnings(output)).hasSize(2);
            for (int i = 0; i < 3; i++) {
                assertThat(getAppearance(context).statusCode()).isEqualTo(200);
            }
            assertThat(warnings(output)).as("要求のたびには警告しない").hasSize(2);
        }

        List<Map<String, Object>> warnings = warnings(output);
        assertThat(warnings)
                .extracting(record -> record.get("property"))
                .containsExactly("mastersmith.appearance.brand-color", "mastersmith.appearance.font-family");
        assertThat(warnings.get(0))
                .containsEntry("defaultValue", "blue")
                .containsEntry("allowedValues", "blue, green, purple, orange")
                .doesNotContainKey("exception");
        assertThat(warnings.get(1))
                .containsEntry("defaultValue", "sans")
                .containsEntry("allowedValues", "sans, serif")
                .doesNotContainKey("exception");
        assertThat(serviceRecords(output))
                .as("AppearanceService のログに設定された値が出ない")
                .allSatisfy(record -> assertThat(record.values().toString())
                        .doesNotContain(DISALLOWED_COLOR)
                        .doesNotContain(DISALLOWED_FAMILY));
    }

    @Test
    @DisplayName("the configured disallowed strings appear nowhere in the whole log output or the answer")
    void configuredStringsDoNotLeak(CapturedOutput output) {
        String color = "color-" + LEAK_MARK;
        String family = "family-" + LEAK_MARK;
        try (ConfigurableApplicationContext context = start(tempDir, color, family)) {
            HttpResponse<String> response = getAppearance(context);

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).doesNotContain(LEAK_MARK);
        }
        assertThat(warnings(output)).hasSize(2);
        JsonLogRecords.assertContainsNoSecret(output.getAll(), color, family, LEAK_MARK);
    }

    @Test
    @DisplayName("without any setting the application starts with blue and sans and warns nothing")
    void noSettingWarnsNothing(CapturedOutput output) {
        try (ConfigurableApplicationContext context = start(tempDir, "", "")) {
            HttpResponse<String> response = getAppearance(context);

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(HttpTestClient.json(response))
                    .containsEntry("brandColor", "blue")
                    .containsEntry("fontFamily", "sans");
        }
        assertThat(warnings(output)).isEmpty();
    }

    @Test
    @DisplayName("allowed values in any case are passed on as green and serif without a warning")
    void allowedValuesArePassedOn(CapturedOutput output) {
        try (ConfigurableApplicationContext context = start(tempDir, "GREEN", "Serif")) {
            HttpResponse<String> response = getAppearance(context);

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(HttpTestClient.json(response))
                    .containsOnlyKeys("brandColor", "fontFamily")
                    .containsEntry("brandColor", "green")
                    .containsEntry("fontFamily", "serif");
        }
        assertThat(warnings(output)).isEmpty();
    }
}
