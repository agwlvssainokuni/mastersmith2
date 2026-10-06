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
package cherry.mastersmith;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.common.testsupport.ApiAccessRules;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * API の分類の印 {@code ApiAccess} の静的な構造の検査（security-design 4.2、要件 NFR1.1・NFR1.2・NFR1.5、機能設計
 * BR1.1〜BR1.3・BR1.8、US1.1 AC1.1.15）。
 *
 * <p>本番のクラス（テストのクラスを読まない）に {@link ApiAccessRules} の規則を当てる。規則が違反を本当に落とすことは、同じ規則を
 * 違反の見本に当てる {@code ApiAccessRulesTest} で確かめる。
 *
 * <p>道は注釈の文字を読み、設定値の置き換えの形 {@code ${名前:既定値}} は既定値で読む。そのため、設定で道を変えた環境とのずれは
 * この検査では見えず、ずれを見るのは解決した道を読む実行時の検査 {@code ApiAccessConsistencyIT} だけである（NFR 設計の読み直し
 * R-06）。
 *
 * <p>ADR-008 の補足: 分類の正は口の隣の印で、テストの側に分類の一覧は持たない。ただし PUBLIC だけは追加の守りとして、口の一覧
 * （{@code PublicApiInventory}）を {@code ApiAccessConsistencyIT} が比べる。
 */
class ApiAccessArchitectureTest {

    /** 今ある口の数（機能設計 3節）。口が減ったら検査が空振りに近づくため、これ以上であることを確かめる。 */
    private static final int MIN_ENDPOINTS = 34;

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("cherry.mastersmith");

    @Test
    @DisplayName("every production endpoint carries exactly one ApiAccess mark")
    void everyEndpointMarkedExactlyOnce() {
        ApiAccessRules.markedExactlyOnce().check(CLASSES);
    }

    @Test
    @DisplayName("ADMIN marks and the admin paths agree in both directions")
    void adminMatchesAdminPaths() {
        ApiAccessRules.adminMatchesAdminPaths().check(CLASSES);
    }

    @Test
    @DisplayName("AUTHENTICATED endpoints are under /api/ and outside the admin paths")
    void authenticatedPathsInsideApi() {
        ApiAccessRules.authenticatedPathsInsideApi().check(CLASSES);
    }

    @Test
    @DisplayName("common.security depends on no feature package")
    void commonSecurityIndependentOfFeatures() {
        ApiAccessRules.commonSecurityIndependentOfFeatures().check(CLASSES);
    }

    @Test
    @DisplayName("the check sees at least the 34 endpoints that exist today")
    void seesAllEndpoints() {
        assertThat(ApiAccessRules.endpointKeys(CLASSES)).hasSizeGreaterThanOrEqualTo(MIN_ENDPOINTS);
    }
}
