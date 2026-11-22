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
package cherry.mastersmith.internaldb.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.MastersmithApplication;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.JsonLogRecords;
import cherry.mastersmith.internaldb.testsupport.InternalDbPostgresDatabase;
import cherry.mastersmith.internaldb.testsupport.InternalDbPostgresIT;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 内部DB を空の PostgreSQL で起動してから、ヘルスチェックが UP になるまでの時間の基準を測る（NFR1.2・DD-6）。合否には使わない。標準出力に
 * {@code INTERNAL_DB_PG_BASELINE startupToHealthMs=<値>} を 1 行出し、移行の時間（INFO の {@code migrationMillis}）も出す。
 */
@InternalDbPostgresIT
class InternalDbPostgresBaselineIT {

    @Test
    @DisplayName("the time from the start to a UP health check on an empty PostgreSQL is measured and printed")
    void measuresStartupToHealth(CapturedOutput output) {
        try (InternalDbPostgresDatabase database = InternalDbPostgresDatabase.create()) {
            long start = System.nanoTime();
            try (ConfigurableApplicationContext context =
                    new SpringApplicationBuilder(MastersmithApplication.class).run(database.applicationArgs())) {
                HttpTestClient client = new HttpTestClient(
                        Integer.parseInt(context.getEnvironment().getProperty("local.server.port")));
                int status = client.get("/actuator/health").statusCode();
                long millis = (System.nanoTime() - start) / 1_000_000L;

                assertThat(status).isEqualTo(200);
                System.out.println("INTERNAL_DB_PG_BASELINE startupToHealthMs=" + millis);
            }
            List<Map<String, Object>> finished = JsonLogRecords.parse(output.getOut()).stream()
                    .filter(record -> "Internal DB migration finished".equals(record.get("message")))
                    .toList();
            assertThat(finished).hasSize(1);
            assertThat(finished.get(0)).containsEntry("migrationCount", 14).containsKey("migrationMillis");
            System.out.println(
                    "INTERNAL_DB_PG_BASELINE migrationMillis=" + finished.get(0).get("migrationMillis"));
        }
    }
}
