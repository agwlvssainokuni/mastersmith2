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
import cherry.mastersmith.internaldb.testsupport.InternalDbPostgresContainer;
import cherry.mastersmith.internaldb.testsupport.InternalDbPostgresDatabase;
import cherry.mastersmith.internaldb.testsupport.InternalDbPostgresIT;
import java.time.Duration;
import java.util.function.IntPredicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 内部DB を PostgreSQL で動かす経路の、ヘルスチェックの結合テスト（FR3.9・BR3.6）。{@code /actuator/health} が UP、内部DB に届かない
 * （コンテナを一時停止）とき DOWN（503）、再開すると UP に戻る。
 */
@InternalDbPostgresIT
class InternalDbPostgresHealthIT {

    private static final Duration LIMIT = Duration.ofSeconds(60);

    @Test
    @DisplayName("health is UP, DOWN while the PostgreSQL container is paused, and UP again after it resumes")
    void healthFollowsTheDatabase() throws InterruptedException {
        try (InternalDbPostgresDatabase database = InternalDbPostgresDatabase.create();
                ConfigurableApplicationContext context =
                        new SpringApplicationBuilder(MastersmithApplication.class).run(database.applicationArgs())) {
            HttpTestClient client =
                    new HttpTestClient(Integer.parseInt(context.getEnvironment().getProperty("local.server.port")));

            assertThat(client.get("/actuator/health").statusCode()).isEqualTo(200);

            InternalDbPostgresContainer.pause();
            try {
                assertThat(awaitStatus(client, status -> status == 503))
                        .as("health becomes DOWN while the database is unreachable")
                        .isTrue();
            } finally {
                InternalDbPostgresContainer.unpause();
            }

            assertThat(awaitStatus(client, status -> status == 200))
                    .as("health becomes UP again after the database resumes")
                    .isTrue();
        }
    }

    private static boolean awaitStatus(HttpTestClient client, IntPredicate expected) throws InterruptedException {
        long deadline = System.nanoTime() + LIMIT.toNanos();
        while (System.nanoTime() < deadline) {
            if (expected.test(client.get("/actuator/health").statusCode())) {
                return true;
            }
            Thread.sleep(500);
        }
        return false;
    }
}
