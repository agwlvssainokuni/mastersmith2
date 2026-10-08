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
package cherry.mastersmith.group.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 監査の detail の中身（BR8.5・BR8.7、契約 C10）の単体テスト。 */
class GroupAuditDetailTest {

    @Test
    @DisplayName("there are exactly the four shapes Name, Rename, Membership and InUse")
    void fourShapes() {
        assertThat(Arrays.stream(GroupAuditDetail.class.getPermittedSubclasses())
                        .map(Class::getSimpleName))
                .containsExactlyInAnyOrder("Name", "Rename", "Membership", "InUse");
    }

    @Test
    @DisplayName("the shapes only have the decided keys: group names and counts")
    void decidedKeysOnly() {
        Set<String> components = Arrays.stream(GroupAuditDetail.class.getPermittedSubclasses())
                .flatMap(type -> Arrays.stream(type.getRecordComponents()))
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());

        assertThat(components)
                .containsExactlyInAnyOrder("name", "before", "after", "groupName", "members", "assignedRoles");
    }

    @Test
    @DisplayName("each shape keeps its values")
    void values() {
        assertThat(new GroupAuditDetail.Name("営業部").name()).isEqualTo("営業部");
        GroupAuditDetail.Rename rename = new GroupAuditDetail.Rename("Sales", "SALES");
        assertThat(rename.before()).isEqualTo("Sales");
        assertThat(rename.after()).isEqualTo("SALES");
        assertThat(new GroupAuditDetail.Membership("営業部").groupName()).isEqualTo("営業部");
        GroupAuditDetail.InUse inUse = new GroupAuditDetail.InUse("営業部", 3, 0);
        assertThat(inUse.members()).isEqualTo(3);
        assertThat(inUse.assignedRoles()).isZero();
    }

    @Test
    @DisplayName("missing names and negative counts are refused")
    void invalidValues() {
        assertThatThrownBy(() -> new GroupAuditDetail.Name(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new GroupAuditDetail.Rename("営業部", null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new GroupAuditDetail.Rename(null, "営業部")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new GroupAuditDetail.Membership(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new GroupAuditDetail.InUse(null, 0, 0)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new GroupAuditDetail.InUse("営業部", -1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GroupAuditDetail.InUse("営業部", 0, -1)).isInstanceOf(IllegalArgumentException.class);
    }
}
