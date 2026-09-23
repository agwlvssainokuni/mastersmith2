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
package cherry.mastersmith.access.domain;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AdminPathsTest {

    @Property
    @Label("anything under /api/admin/ is admin only whatever follows the slash")
    void anythingUnderAdminIsAdminOnly(@ForAll @StringLength(min = 0, max = 40) String rest) {
        assertThat(AdminPaths.isAdminOnly("/api/admin/" + rest)).isTrue();
    }

    @Property
    @Label("a path that does not start with /api/admin is never admin only")
    void otherPrefixesAreNotAdminOnly(@ForAll @AlphaChars @StringLength(min = 1, max = 20) String segment) {
        assertThat(AdminPaths.isAdminOnly("/api/" + segment + "x/list")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/admin", "/api/admin/", "/api/admin/check", "/api/admin/nothing"})
    @DisplayName("the admin root and everything under it are admin only")
    void adminOnlyPaths(String path) {
        assertThat(AdminPaths.isAdminOnly(path)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "/API/admin/check",
                "/api/Admin/check",
                "/api/administrators",
                "/api/adminx",
                "/api/auth/login",
                "/actuator/health",
                "/"
            })
    @DisplayName("case differences, longer names and other areas are not admin only")
    void notAdminOnlyPaths(String path) {
        assertThat(AdminPaths.isAdminOnly(path)).isFalse();
    }

    @Test
    @DisplayName("a null path is not admin only")
    void nullIsNotAdminOnly() {
        assertThat(AdminPaths.isAdminOnly(null)).isFalse();
    }

    @Test
    @DisplayName("the context path is removed from the request URI")
    void relativePathRemovesContextPath() {
        assertThat(AdminPaths.relativePath("/app/api/admin/check", "/app")).isEqualTo("/api/admin/check");
        assertThat(AdminPaths.relativePath("/api/admin/check", "")).isEqualTo("/api/admin/check");
        assertThat(AdminPaths.relativePath("/api/admin/check", null)).isEqualTo("/api/admin/check");
        assertThat(AdminPaths.relativePath("/api/admin/check", "/other")).isEqualTo("/api/admin/check");
        assertThat(AdminPaths.relativePath(null, "/app")).isNull();
    }

    @Test
    @DisplayName("the patterns used by the access rules cover the root and everything below it")
    void patterns() {
        assertThat(AdminPaths.adminPatterns()).containsExactly("/api/admin", "/api/admin/**");
        assertThat(AdminPaths.adminPatterns()).isNotSameAs(AdminPaths.ADMIN_PATTERNS);
    }
}
