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
package cherry.mastersmith.businessdata.web;

import static cherry.mastersmith.businessdata.testsupport.BusinessDataSampleSchema.ITEM;
import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.businessdata.testsupport.BusinessDataSampleSchema;
import cherry.mastersmith.businessdata.testsupport.BusinessDataTestApp;
import cherry.mastersmith.businessdata.testsupport.BusinessDataTestApp.Writer;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.JsonLogRecords;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.testsupport.RoleActors.Actor;
import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import cherry.mastersmith.targetdb.testsupport.ContainerRuntimeCheck;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * 書き込みの資格情報が無い・片方だけのとき（Intent 261016-business-data-views の U2、計画の Step 21、BR1.7・BR9.1、AC3.3.1〜AC3.3.4）。
 * PostgreSQL のコンテナ。書き込みは 409 {@code BUSINESS_WRITE_NOT_CONFIGURED}（権限の無い人には 403 のまま）、読み取りは動き続け、
 * 定義は {@code writeConfigured: false}、片方だけのときの WARN は項目の名前だけ（値を出さない）。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
// 拡張の登録の順は、ログの取り込みを先、コンテナの実行環境の確かめを後にする（ExtensionOrderArchitectureTest）。
@ExtendWith({OutputCaptureExtension.class, ContainerRuntimeCheck.class})
class BusinessDataWriteNotConfiguredIT {

    private final BusinessDataSampleSchema sample = new BusinessDataSampleSchema(DatabaseProduct.POSTGRESQL);

    private Path dir;

    @BeforeAll
    void start(@TempDir Path tempDir) {
        dir = tempDir;
        sample.start();
    }

    @AfterAll
    void stop() {
        sample.stop();
    }

    static Stream<Arguments> cases() {
        return Stream.of(
                Arguments.of("none", null),
                Arguments.of("usernameOnly", "mastersmith.target-db.write.password"),
                Arguments.of("passwordOnly", "mastersmith.target-db.write.username"));
    }

    private Writer writer(String name) {
        Writer real = sample.writer();
        return switch (name) {
            case "none" -> null;
            case "usernameOnly" -> new Writer(real.user(), "");
            case "passwordOnly" -> new Writer("", real.password());
            default -> throw new IllegalArgumentException(name);
        };
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    @DisplayName(
            "without both write credentials writes are 409 or 403, reads go on and the warning names only the item")
    @SuppressWarnings("unchecked")
    void notConfigured(String name, String missingItem, CapturedOutput output) {
        try (BusinessDataTestApp app =
                BusinessDataTestApp.start(dir.resolve(name), sample.target(), writer(name), new Class<?>[0])) {
            app.installDsl(sample.model());
            Actor writer = app.reader(sample.writeSettings());
            Actor reader = app.reader(sample.settings(MainPermission.READ));
            Map<String, Object> definitionId = app.definitionId(writer.token(), sample.schema(), ITEM);
            Map<String, Object> create = sample.body(ITEM);
            create.put("definitionId", definitionId);
            create.put("values", Map.of("id", "601", "name", "設定なし"));
            Map<String, Object> readerCreate = sample.body(ITEM);
            readerCreate.put("definitionId", app.definitionId(reader.token(), sample.schema(), ITEM));
            readerCreate.put("values", Map.of("id", "602", "name", "設定なし"));
            Map<String, Object> delete = sample.body(ITEM);
            delete.put("definitionId", definitionId);
            delete.put("key", Map.of("id", "1"));

            HttpResponse<String> created = app.post(BusinessDataPaths.ROWS, writer.token(), create);
            HttpResponse<String> deleted = app.post(BusinessDataPaths.DELETE, writer.token(), delete);
            HttpResponse<String> forbidden = app.post(BusinessDataPaths.ROWS, reader.token(), readerCreate);
            HttpResponse<String> search =
                    app.post(BusinessDataPaths.SEARCH, reader.token(), sample.body(ITEM, "page", 1, "size", 20));
            Map<String, Object> definition =
                    HttpTestClient.json(app.post(BusinessDataPaths.DEFINITION, writer.token(), sample.body(ITEM)));

            assertThat(created.statusCode()).as(created.body()).isEqualTo(409);
            assertThat(HttpTestClient.json(created)).containsEntry("code", "BUSINESS_WRITE_NOT_CONFIGURED");
            assertThat(HttpTestClient.json(deleted)).containsEntry("code", "BUSINESS_WRITE_NOT_CONFIGURED");
            assertThat(forbidden.statusCode()).as("権限の無い人には 403 のまま").isEqualTo(403);
            assertThat(search.statusCode()).as("読み取りは動く").isEqualTo(200);
            assertThat(definition).containsEntry("writeConfigured", false);
            assertThat((List<Object>) definition.get("operations")).doesNotContain("CREATE", "UPDATE", "DELETE");
            assertThat(sample.count(ITEM, "id IN (601, 602)")).isZero();
            assertThat(sample.count(ITEM, "id = 1")).isEqualTo(1);
            List<Map<String, Object>> warns = JsonLogRecords.parse(output.getAll()).stream()
                    .filter(r -> "WARN".equals(r.get("level")))
                    .filter(r -> String.valueOf(r.get("message")).contains("書き込みの設定に欠け"))
                    .toList();
            if (missingItem == null) {
                assertThat(warns).as("両方が無いときは WARN を出さない").isEmpty();
            } else {
                assertThat(warns)
                        .hasSize(1)
                        .first()
                        .satisfies(r -> assertThat(r).containsEntry("items", missingItem));
            }
            JsonLogRecords.assertContainsNoSecret(
                    output.getAll(), sample.writer().user(), sample.writer().password());
        }
    }
}
