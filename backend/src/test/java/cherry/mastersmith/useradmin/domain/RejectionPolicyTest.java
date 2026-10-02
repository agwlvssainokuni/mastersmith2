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
package cherry.mastersmith.useradmin.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** 5つの操作の拒否の判定（BR2.1・BR2.2、FS の 2.2）の単体テスト。性質ベースのテスト（jqwik、NFR9.8）を含む。 */
class RejectionPolicyTest {

    /** どの理由にも当たらない事実の組（対象はいて、自分自身でなく、停止しておらず、0 人にもならない）。 */
    private static OperationFacts clean(boolean admin, boolean suspended, boolean resettable) {
        return new OperationFacts(true, false, suspended, admin, resettable, false);
    }

    @ParameterizedTest
    @EnumSource(AdminOperation.class)
    @DisplayName("a missing target is USER_NOT_FOUND for every operation, before any other reason")
    void missingTargetComesFirst(AdminOperation operation) {
        OperationFacts everythingTrue = new OperationFacts(false, true, true, true, false, true);

        assertThat(RejectionPolicy.decide(operation, everythingTrue)).contains(RejectionReason.USER_NOT_FOUND);
        assertThat(RejectionPolicy.decide(operation, OperationFacts.targetMissing()))
                .contains(RejectionReason.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("granting is rejected for self, a suspended target and an existing admin, in this order")
    void grantAdmin() {
        assertThat(RejectionPolicy.decide(
                        AdminOperation.GRANT_ADMIN, new OperationFacts(true, true, true, true, false, false)))
                .contains(RejectionReason.SELF_OPERATION);
        assertThat(RejectionPolicy.decide(
                        AdminOperation.GRANT_ADMIN, new OperationFacts(true, false, true, true, false, false)))
                .contains(RejectionReason.TARGET_SUSPENDED);
        assertThat(RejectionPolicy.decide(AdminOperation.GRANT_ADMIN, clean(true, false, false)))
                .contains(RejectionReason.NO_CHANGE);
        assertThat(RejectionPolicy.decide(AdminOperation.GRANT_ADMIN, clean(false, false, false)))
                .isEmpty();
        // 印を付けるは有効な管理者を減らさないため、最後の管理者を当てない。
        assertThat(RejectionPolicy.decide(
                        AdminOperation.GRANT_ADMIN, new OperationFacts(true, false, false, false, false, true)))
                .isEmpty();
    }

    @Test
    @DisplayName(
            "revoking is rejected for self, a suspended target, a non-admin and the last active admin, in this order")
    void revokeAdmin() {
        assertThat(RejectionPolicy.decide(
                        AdminOperation.REVOKE_ADMIN, new OperationFacts(true, false, true, false, false, true)))
                .contains(RejectionReason.TARGET_SUSPENDED);
        assertThat(RejectionPolicy.decide(
                        AdminOperation.REVOKE_ADMIN, new OperationFacts(true, false, false, false, false, true)))
                .contains(RejectionReason.NO_CHANGE);
        assertThat(RejectionPolicy.decide(
                        AdminOperation.REVOKE_ADMIN, new OperationFacts(true, false, false, true, false, true)))
                .contains(RejectionReason.LAST_ACTIVE_ADMIN);
        assertThat(RejectionPolicy.decide(AdminOperation.REVOKE_ADMIN, clean(true, false, false)))
                .isEmpty();
    }

    @Test
    @DisplayName("suspending ignores TARGET_SUSPENDED and reports an already suspended target as NO_CHANGE")
    void suspend() {
        assertThat(RejectionPolicy.decide(
                        AdminOperation.SUSPEND, new OperationFacts(true, true, false, true, false, true)))
                .contains(RejectionReason.SELF_OPERATION);
        assertThat(RejectionPolicy.decide(
                        AdminOperation.SUSPEND, new OperationFacts(true, false, true, true, false, true)))
                .contains(RejectionReason.NO_CHANGE);
        assertThat(RejectionPolicy.decide(
                        AdminOperation.SUSPEND, new OperationFacts(true, false, false, true, false, true)))
                .contains(RejectionReason.LAST_ACTIVE_ADMIN);
        assertThat(RejectionPolicy.decide(AdminOperation.SUSPEND, clean(false, false, false)))
                .isEmpty();
    }

    @Test
    @DisplayName("resuming is rejected for self and for a user who is not suspended")
    void resume() {
        assertThat(RejectionPolicy.decide(
                        AdminOperation.RESUME, new OperationFacts(true, true, false, false, false, false)))
                .contains(RejectionReason.SELF_OPERATION);
        assertThat(RejectionPolicy.decide(AdminOperation.RESUME, clean(true, false, false)))
                .contains(RejectionReason.NO_CHANGE);
        assertThat(RejectionPolicy.decide(AdminOperation.RESUME, clean(true, true, false)))
                .isEmpty();
        // 停止を解くは最後の管理者を当てない。
        assertThat(RejectionPolicy.decide(
                        AdminOperation.RESUME, new OperationFacts(true, false, true, false, false, true)))
                .isEmpty();
    }

    @Test
    @DisplayName("resetting login failures allows self and suspended targets and rejects only a non-resettable one")
    void resetLoginFailures() {
        assertThat(RejectionPolicy.decide(
                        AdminOperation.RESET_LOGIN_FAILURES, new OperationFacts(true, true, true, true, true, true)))
                .isEmpty();
        assertThat(RejectionPolicy.decide(
                        AdminOperation.RESET_LOGIN_FAILURES, new OperationFacts(true, true, true, true, false, true)))
                .contains(RejectionReason.NO_CHANGE);
    }

    @Test
    @DisplayName("null inputs are refused as unexpected errors")
    void nullInputs() {
        assertThatThrownBy(() -> RejectionPolicy.decide(null, OperationFacts.targetMissing()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> RejectionPolicy.decide(AdminOperation.RESUME, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("the applicable reasons of each operation follow BR2.2")
    void applicability() {
        assertThat(applicable(AdminOperation.GRANT_ADMIN))
                .containsExactlyInAnyOrder(
                        RejectionReason.USER_NOT_FOUND,
                        RejectionReason.SELF_OPERATION,
                        RejectionReason.TARGET_SUSPENDED,
                        RejectionReason.NO_CHANGE);
        assertThat(applicable(AdminOperation.REVOKE_ADMIN)).containsExactlyInAnyOrder(RejectionReason.values());
        assertThat(applicable(AdminOperation.SUSPEND))
                .containsExactlyInAnyOrder(
                        RejectionReason.USER_NOT_FOUND,
                        RejectionReason.SELF_OPERATION,
                        RejectionReason.NO_CHANGE,
                        RejectionReason.LAST_ACTIVE_ADMIN);
        assertThat(applicable(AdminOperation.RESUME))
                .containsExactlyInAnyOrder(
                        RejectionReason.USER_NOT_FOUND, RejectionReason.SELF_OPERATION, RejectionReason.NO_CHANGE);
        assertThat(applicable(AdminOperation.RESET_LOGIN_FAILURES))
                .containsExactlyInAnyOrder(RejectionReason.USER_NOT_FOUND, RejectionReason.NO_CHANGE);
    }

    private static Set<RejectionReason> applicable(AdminOperation operation) {
        Set<RejectionReason> reasons = EnumSet.noneOf(RejectionReason.class);
        for (RejectionReason reason : RejectionReason.values()) {
            if (RejectionPolicy.applies(operation, reason)) {
                reasons.add(reason);
            }
        }
        return reasons;
    }

    /** 理由の条件が事実の組で成り立つか（テストの側で FS の 2.2 の擬似コードをそのまま書いたもの）。 */
    private static boolean holds(AdminOperation operation, RejectionReason reason, OperationFacts facts) {
        return switch (reason) {
            case USER_NOT_FOUND -> !facts.targetExists();
            case SELF_OPERATION -> facts.targetIsOperator();
            case TARGET_SUSPENDED -> facts.targetSuspended();
            case NO_CHANGE ->
                switch (operation) {
                    case GRANT_ADMIN -> facts.targetAdmin();
                    case REVOKE_ADMIN -> !facts.targetAdmin();
                    case SUSPEND -> facts.targetSuspended();
                    case RESUME -> !facts.targetSuspended();
                    case RESET_LOGIN_FAILURES -> !facts.targetResettable();
                };
            case LAST_ACTIVE_ADMIN -> facts.leavesNoActiveAdmin();
        };
    }

    @Property
    @Label("the returned reason applies to the operation and its condition holds")
    void returnedReasonAppliesAndHolds(
            @ForAll AdminOperation operation,
            @ForAll boolean exists,
            @ForAll boolean self,
            @ForAll boolean suspended,
            @ForAll boolean admin,
            @ForAll boolean resettable,
            @ForAll boolean leavesNone) {
        OperationFacts facts = new OperationFacts(exists, self, suspended, admin, resettable, leavesNone);

        Optional<RejectionReason> decided = RejectionPolicy.decide(operation, facts);

        decided.ifPresent(reason -> {
            assertThat(RejectionPolicy.applies(operation, reason)).isTrue();
            assertThat(holds(operation, reason, facts)).isTrue();
        });
    }

    @Property
    @Label("no applicable reason that comes before the returned one holds")
    void earlierReasonsDoNotHold(
            @ForAll AdminOperation operation,
            @ForAll boolean exists,
            @ForAll boolean self,
            @ForAll boolean suspended,
            @ForAll boolean admin,
            @ForAll boolean resettable,
            @ForAll boolean leavesNone) {
        OperationFacts facts = new OperationFacts(exists, self, suspended, admin, resettable, leavesNone);
        Optional<RejectionReason> decided = RejectionPolicy.decide(operation, facts);
        int limit = decided.map(Enum::ordinal).orElse(RejectionReason.values().length);

        for (RejectionReason earlier : RejectionReason.values()) {
            if (earlier.ordinal() < limit && RejectionPolicy.applies(operation, earlier)) {
                assertThat(holds(operation, earlier, facts))
                        .as("earlier reason %s", earlier)
                        .isFalse();
            }
        }
    }

    @Property
    @Label("when no applicable reason holds the operation is not rejected")
    void notRejectedWhenNothingHolds(
            @ForAll AdminOperation operation,
            @ForAll boolean exists,
            @ForAll boolean self,
            @ForAll boolean suspended,
            @ForAll boolean admin,
            @ForAll boolean resettable,
            @ForAll boolean leavesNone) {
        OperationFacts facts = new OperationFacts(exists, self, suspended, admin, resettable, leavesNone);
        boolean anyHolds = false;
        for (RejectionReason reason : RejectionReason.values()) {
            anyHolds |= RejectionPolicy.applies(operation, reason) && holds(operation, reason, facts);
        }

        assertThat(RejectionPolicy.decide(operation, facts).isPresent()).isEqualTo(anyHolds);
    }
}
