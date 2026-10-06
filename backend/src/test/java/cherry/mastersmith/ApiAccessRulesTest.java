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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cherry.mastersmith.common.testsupport.ApiAccessRules;
import cherry.mastersmith.common.testsupport.apiaccess.AdminOutsideSample;
import cherry.mastersmith.common.testsupport.apiaccess.AdminPathNotAdminSample;
import cherry.mastersmith.common.testsupport.apiaccess.AuthenticatedOutsideApiSample;
import cherry.mastersmith.common.testsupport.apiaccess.DoubleMarkSample;
import cherry.mastersmith.common.testsupport.apiaccess.NoMarkSample;
import cherry.mastersmith.common.testsupport.apiaccess.ValidSample;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 検査の検査: {@link ApiAccessRules} の規則が違反を本当に落とし、正しい口を落とさないことの確かめ（security-design 4.2.1、
 * NFR1.1・NFR1.2・NFR1.5）。
 *
 * <p>違反の見本（{@code common/testsupport/apiaccess/}）を1つずつ読み込み、本番のクラスに当てるのと同じ規則を当てる。
 */
class ApiAccessRulesTest {

    private static JavaClasses importOnly(Class<?> sample) {
        return new ClassFileImporter().importClasses(sample);
    }

    private static void assertFails(ArchRule rule, Class<?> sample, String... methodNames) {
        JavaClasses classes = importOnly(sample);
        assertThatThrownBy(() -> rule.check(classes))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(sample.getName())
                .satisfies(error -> {
                    for (String methodName : methodNames) {
                        assertThat(error.getMessage()).contains(sample.getName() + "." + methodName);
                    }
                });
    }

    @Test
    @DisplayName("an endpoint without any mark is rejected")
    void rejectsNoMark() {
        assertFails(ApiAccessRules.markedExactlyOnce(), NoMarkSample.class, "noMark");
    }

    @Test
    @DisplayName("an endpoint marked on both the class and the method is rejected")
    void rejectsDoubleMark() {
        assertFails(ApiAccessRules.markedExactlyOnce(), DoubleMarkSample.class, "doubleMark");
    }

    @Test
    @DisplayName("an ADMIN endpoint outside the admin paths is rejected")
    void rejectsAdminOutsideAdminPaths() {
        assertFails(ApiAccessRules.adminMatchesAdminPaths(), AdminOutsideSample.class, "adminOutside");
    }

    @Test
    @DisplayName("a non-ADMIN endpoint on an admin path is rejected for both AUTHENTICATED and PUBLIC")
    void rejectsNonAdminOnAdminPath() {
        assertFails(
                ApiAccessRules.adminMatchesAdminPaths(),
                AdminPathNotAdminSample.class,
                "authenticatedOnAdminPath",
                "publicOnAdminPath");
    }

    @Test
    @DisplayName("an AUTHENTICATED endpoint outside /api/ is rejected")
    void rejectsAuthenticatedOutsideApi() {
        assertFails(
                ApiAccessRules.authenticatedPathsInsideApi(),
                AuthenticatedOutsideApiSample.class,
                "authenticatedOutsideApi");
    }

    @Test
    @DisplayName("a valid sample passes the three rules about marks and paths")
    void validSamplePasses() {
        JavaClasses classes = importOnly(ValidSample.class);

        assertThat(ApiAccessRules.endpoints(classes)).hasSize(4);
        for (ArchRule rule : List.of(
                ApiAccessRules.markedExactlyOnce(),
                ApiAccessRules.adminMatchesAdminPaths(),
                ApiAccessRules.authenticatedPathsInsideApi())) {
            rule.check(classes);
        }
    }

    @Test
    @DisplayName("every rule fails on an empty set of classes so that it never passes vacuously")
    void emptySetFails() {
        JavaClasses empty = new ClassFileImporter().importClasses(List.of());

        for (ArchRule rule : List.of(
                ApiAccessRules.markedExactlyOnce(),
                ApiAccessRules.adminMatchesAdminPaths(),
                ApiAccessRules.authenticatedPathsInsideApi(),
                ApiAccessRules.commonSecurityIndependentOfFeatures())) {
            assertThatThrownBy(() -> rule.check(empty)).isInstanceOf(AssertionError.class);
        }
    }
}
