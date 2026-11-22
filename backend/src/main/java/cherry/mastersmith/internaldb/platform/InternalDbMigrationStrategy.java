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
package cherry.mastersmith.internaldb.platform;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;

/**
 * 起動時の移行を行い、失敗を {@link StartupFailureReporter} で 1 件だけ記録して、{@link InternalDbStartupException}（原因を連ねない）で
 * 起動を止める（BR2.9・BR5.1・NFR1.2）。成功したときは、移行にかかった時間と件数を INFO で 1 行出す（値は時間と件数だけ）。
 */
public class InternalDbMigrationStrategy implements FlywayMigrationStrategy {

    private static final Logger LOGGER = LoggerFactory.getLogger(InternalDbMigrationStrategy.class);

    @Override
    public void migrate(Flyway flyway) {
        long start = System.nanoTime();
        MigrateResult result;
        try {
            result = flyway.migrate();
        } catch (RuntimeException e) {
            StartupFailureReport report = StartupFailureReporter.classify(e);
            StartupFailureReporter.log(report);
            throw new InternalDbStartupException(report.failureKind());
        }
        long millis = (System.nanoTime() - start) / 1_000_000L;
        LOGGER.atInfo()
                .addKeyValue("migrationMillis", millis)
                .addKeyValue("migrationCount", result.migrationsExecuted)
                .log("Internal DB migration finished");
    }
}
