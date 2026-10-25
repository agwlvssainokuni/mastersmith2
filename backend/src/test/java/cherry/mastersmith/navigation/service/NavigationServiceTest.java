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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cherry.mastersmith.dsl.domain.DslModel;
import cherry.mastersmith.navigation.domain.EmptyReason;
import cherry.mastersmith.navigation.domain.NavNode;
import cherry.mastersmith.navigation.domain.NavTableRef;
import cherry.mastersmith.navigation.domain.NavigationMenu;
import cherry.mastersmith.navigation.testsupport.CountingDslProvider;
import cherry.mastersmith.navigation.testsupport.CountingResolver;
import cherry.mastersmith.navigation.testsupport.NavigationModels;
import cherry.mastersmith.role.domain.MainPermission;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 業務処理の振る舞いのテスト（BR1.2・BR1.3・BR1.5・BR2.5・BR3.3・BR5.2・BR5.3、NFR1.11）。 */
class NavigationServiceTest {

    private static final DslModel MODEL = NavigationModels.model(
            List.of(NavigationModels.schema("SALES", "ORDER", "LINE")),
            List.of(
                    NavigationModels.group("販売", NavigationModels.tableItem("受注", "SALES", "ORDER")),
                    NavigationModels.tableItem("明細", "SALES", "LINE")));

    /** 点を記録し、点で差し替えの操作を行う待ち合わせの口。 */
    private static final class RecordingBarrier implements NavigationBarrier {

        private final List<String> points = new ArrayList<>();

        private Runnable onDslRead = () -> {};

        @Override
        public void afterDslRead() {
            points.add("afterDslRead");
            onDslRead.run();
        }
    }

    private final CountingDslProvider dsl = new CountingDslProvider(MODEL);

    private final RecordingBarrier barrier = new RecordingBarrier();

    private DslModel snapshotModel = MODEL;

    private final CountingResolver resolver = new CountingResolver(() -> snapshotModel);

    private NavigationService service() {
        return new NavigationService(dsl, resolver, new AllowedNavIconList(), barrier);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("no DSL and empty menus give NOT_CONFIGURED without stopping at the barrier")
    void notConfigured() {
        dsl.replace(null);
        assertThat(service().menu(1L)).isEqualTo(NavigationMenu.notConfigured());
        dsl.replace(NavigationModels.model(List.of(NavigationModels.schema("SALES", "ORDER")), List.of()));
        assertThat(service().menu(1L).emptyReason()).isEqualTo(EmptyReason.NOT_CONFIGURED);

        assertThat(barrier.points).isEmpty();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("no work role or only NONE tables give NOTHING_VISIBLE")
    void nothingVisible() {
        assertThat(service().menu(1L).emptyReason()).isEqualTo(EmptyReason.NOTHING_VISIBLE);
        resolver.withoutWorkRole();
        NavigationMenu menu = service().menu(1L);

        assertThat(menu.items()).isEmpty();
        assertThat(menu.emptyReason()).isEqualTo(EmptyReason.NOTHING_VISIBLE);
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "visible tables give the filtered tree, no empty reason and the stop point after the DSL read")
    void filteredTree() {
        resolver.table("SALES", "ORDER", MainPermission.READ);

        NavigationMenu menu = service().menu(1L);

        assertThat(menu.emptyReason()).isNull();
        assertThat(menu.items()).singleElement().satisfies(group -> {
            assertThat(group.id()).isEqualTo("0");
            assertThat(group.navigable()).isFalse();
            assertThat(group.items()).extracting(NavNode::id).containsExactly("0.0");
        });
        assertThat(barrier.points).containsExactly("afterDslRead");
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "a DSL replaced after the read filters the old menus with the new snapshot without reading again")
    void dslReplacedDuringMenu() {
        resolver.table("SALES", "ORDER", MainPermission.READ).table("SALES", "LINE", MainPermission.READ);
        DslModel withoutLine =
                NavigationModels.model(List.of(NavigationModels.schema("SALES", "ORDER")), MODEL.menus());
        barrier.onDslRead = () -> snapshotModel = withoutLine;

        NavigationMenu menu = service().menu(1L);

        assertThat(menu.items()).extracting(NavNode::id).containsExactly("0");
        assertThat(dsl.calls()).isEqualTo(1);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("a failure of the resolver is thrown as it is")
    void resolverFailure() {
        IllegalStateException failure = new IllegalStateException("DB の誤り");
        resolver.failWith(failure);

        assertThatThrownBy(() -> service().menu(1L)).isSameAs(failure);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("the production barrier does nothing and lets the service run to the end")
    void productionBarrier() {
        resolver.table("SALES", "ORDER", MainPermission.READ);
        NavigationService service =
                new NavigationService(dsl, resolver, new AllowedNavIconList(), new NoOpNavigationBarrier());

        assertThat(service.menu(1L).items()).hasSize(1);
        assertThat(service.menu(1L).items().getFirst().items().getFirst().table())
                .isEqualTo(new NavTableRef("SALES", "ORDER"));
    }
}
