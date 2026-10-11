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
package cherry.mastersmith.navigation.service;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.dsl.domain.DslModel;
import cherry.mastersmith.navigation.domain.NavigationMenu;
import cherry.mastersmith.navigation.domain.TableAccessResult;
import cherry.mastersmith.navigation.testsupport.CountingDslProvider;
import cherry.mastersmith.navigation.testsupport.CountingResolver;
import cherry.mastersmith.navigation.testsupport.NavigationModels;
import cherry.mastersmith.role.domain.MainPermission;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * navigation が呼ぶ口の回数のテスト（Spring を起動しない。NFR2.3・NFR1.9、{@code performance-design.md} の 1節の表）。数えるのは
 * navigation が呼ぶ回数だけで、口の内側の問い合わせの数は role の {@code EffectivePermissionQueryCountIT} が持つ。
 */
class NavigationCallCountTest {

    private static final DslModel SMALL = NavigationModels.model(
            List.of(NavigationModels.schema("SALES", "ORDER", "LINE")),
            List.of(NavigationModels.tableItem("受注", "SALES", "ORDER")));

    private static final DslModel LARGE = NavigationModels.model(
            List.of(NavigationModels.schema("SALES", "ORDER", "LINE")),
            NavigationModels.flatMenus(1_000, "SALES", "ORDER", "LINE"));

    private static final DslModel NO_MENUS =
            NavigationModels.model(List.of(NavigationModels.schema("SALES", "ORDER")), List.of());

    private CountingDslProvider dsl;

    private CountingResolver resolver;

    private NavigationService service(DslModel model) {
        dsl = new CountingDslProvider(model);
        resolver = new CountingResolver(() -> model).table("SALES", "ORDER", MainPermission.READ);
        return new NavigationService(dsl, resolver, new AllowedNavIconList(), new NoOpNavigationBarrier());
    }

    private void assertCalls(int current, int snapshot, int resolve) {
        assertThat(dsl.calls()).as("current()").isEqualTo(current);
        assertThat(resolver.snapshotCalls()).as("snapshotFor").isEqualTo(snapshot);
        assertThat(resolver.resolveCalls()).as("resolve").isEqualTo(resolve);
    }

    @Test
    @DisplayName("the menu reads the DSL once and the snapshot once for one item")
    void menuWithOneItem() {
        NavigationMenu menu = service(SMALL).menu(1L);

        assertThat(menu.items()).hasSize(1);
        assertCalls(1, 1, 0);
    }

    @Test
    @DisplayName("the menu reads the DSL once and the snapshot once for a thousand items")
    void menuWithThousandItems() {
        NavigationMenu menu = service(LARGE).menu(1L);

        assertThat(menu.items()).hasSize(500);
        assertCalls(1, 1, 0);
    }

    @Test
    @DisplayName("the menu does not call the resolver when there is no DSL")
    void menuWithoutDsl() {
        service(null).menu(1L);

        assertCalls(1, 0, 0);
    }

    @Test
    @DisplayName("the menu does not call the resolver when the menus are empty")
    void menuWithoutMenus() {
        service(NO_MENUS).menu(1L);

        assertCalls(1, 0, 0);
    }

    @Test
    @DisplayName("a visible table resolves once and reads the DSL once for the display name")
    void visibleTable() {
        assertThat(service(SMALL).tableAccess(1L, "SALES", "ORDER")).isInstanceOf(TableAccessResult.Visible.class);

        assertCalls(1, 0, 1);
    }

    @Test
    @DisplayName("a NONE table resolves once and does not read the DSL")
    void noneTable() {
        assertThat(service(SMALL).tableAccess(1L, "SALES", "LINE")).isInstanceOf(TableAccessResult.NotVisible.class);

        assertCalls(0, 0, 1);
    }

    @Test
    @DisplayName("a table that is not in the DSL resolves once and does not read the DSL")
    void tableNotInDsl() {
        assertThat(service(SMALL).tableAccess(1L, "SALES", "MISSING")).isInstanceOf(TableAccessResult.NotVisible.class);

        assertCalls(0, 0, 1);
    }

    @Test
    @DisplayName("without a DSL the table access resolves once and does not read the DSL")
    void tableWithoutDsl() {
        assertThat(service(null).tableAccess(1L, "SALES", "ORDER")).isInstanceOf(TableAccessResult.NotVisible.class);

        assertCalls(0, 0, 1);
    }
}
