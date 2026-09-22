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
package cherry.mastersmith.common.error.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cherry.mastersmith.MastersmithApplication;
import cherry.mastersmith.common.error.domain.LocalizedText;
import cherry.mastersmith.common.error.domain.ProblemType;
import cherry.mastersmith.common.error.domain.ProblemTypeCatalog;
import cherry.mastersmith.common.testsupport.TestDatabase;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;

/** 問題の種類の code・slug の重複で、アプリの起動が失敗すること（BR5.16）。 */
class ProblemTypeDuplicateStartupIT {

    private static final LocalizedText TEXT = new LocalizedText("重複の確認", "duplicate check");

    @TempDir
    Path tempDir;

    /**
     * 後の単位が U1 と同じ code を定義した場合の役（部品の走査の対象にしないよう、注釈を付けずに起動の元として渡す）。
     */
    static class DuplicateCodeCatalog {

        @Bean
        ProblemTypeCatalog duplicateCodeCatalog() {
            return () -> List.of(new ProblemType("NOT_FOUND", 410, TEXT, TEXT, null));
        }
    }

    /** 2つの機能が同じ code（したがって同じ slug）を定義した場合の役。 */
    static class DuplicateSlugCatalogs {

        @Bean
        ProblemTypeCatalog firstFeatureCatalog() {
            return () -> List.of(new ProblemType("ORDER_CONFLICT", 409, TEXT, TEXT, null));
        }

        @Bean
        ProblemTypeCatalog secondFeatureCatalog() {
            return () -> List.of(new ProblemType("ORDER_CONFLICT", 409, TEXT, TEXT, null));
        }
    }

    private void start(Class<?> extra) {
        new SpringApplicationBuilder(MastersmithApplication.class, extra)
                .run("--spring.datasource.url=" + TestDatabase.url(tempDir), "--server.port=0")
                .close();
    }

    @Test
    @DisplayName("startup fails when a feature defines a code that already exists")
    void duplicateCodeStopsStartup() {
        assertThatThrownBy(() -> start(DuplicateCodeCatalog.class))
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NOT_FOUND");
    }

    @Test
    @DisplayName("startup fails and names both the duplicated code and slug")
    void duplicateSlugStopsStartup() {
        assertThatThrownBy(() -> start(DuplicateSlugCatalogs.class))
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("code=[ORDER_CONFLICT]")
                .hasMessageContaining("slug=[order-conflict]");
    }

    @Test
    @DisplayName("startup succeeds with only the U1 problem types")
    void startsWithoutDuplicates() {
        new SpringApplicationBuilder(MastersmithApplication.class)
                .run("--spring.datasource.url=" + TestDatabase.url(tempDir), "--server.port=0")
                .close();
    }
}
