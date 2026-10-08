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

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 詳細のメンバー1人分の値（BR7.4・BR9.1・BR9.2）と、表のエンティティの値の単体テスト。 */
class GroupMemberTest {

    private static final Instant ADDED_AT = Instant.parse("2026-10-08T06:00:00Z");

    private static GroupName name(String raw) {
        return ((GroupNameValidation.Valid) GroupName.parse(raw)).name();
    }

    @Test
    @DisplayName("the string form of a member hides the display name and the email address")
    void stringFormIsRedacted() {
        GroupMember member = new GroupMember(9L, "営業 太郎", "taro@example.com", true, ADDED_AT);

        assertThat(member.displayName()).isEqualTo("営業 太郎");
        assertThat(member.email()).isEqualTo("taro@example.com");
        assertThat(member.toString())
                .contains("userId=9")
                .contains("suspended=true")
                .doesNotContain("営業 太郎")
                .doesNotContain("taro@example.com");
    }

    @Test
    @DisplayName("a member needs a display name, an email address and the time it was added")
    void requiredValues() {
        assertThatThrownBy(() -> new GroupMember(9L, null, "taro@example.com", false, ADDED_AT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new GroupMember(9L, "営業 太郎", null, false, ADDED_AT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new GroupMember(9L, "営業 太郎", "taro@example.com", false, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("a new group takes the name and key from GroupName and the same time for creation and update")
    void newGroup() {
        Group group = new Group(name(" Sales "), ADDED_AT);

        assertThat(group.getGroupId()).isNull();
        assertThat(group.getName()).isEqualTo("Sales");
        assertThat(group.getNameKey()).isEqualTo("sales");
        assertThat(group.getCreatedAt()).isEqualTo(ADDED_AT);
        assertThat(group.getUpdatedAt()).isEqualTo(ADDED_AT);
    }

    @Test
    @DisplayName("a rename changes the name, the key and the update time but not the creation time")
    void rename() {
        Group group = new Group(name("Sales"), ADDED_AT);
        Instant later = ADDED_AT.plusSeconds(60);

        group.rename(name("営業部"), later);

        assertThat(group.getName()).isEqualTo("営業部");
        assertThat(group.getNameKey()).isEqualTo("営業部");
        assertThat(group.getCreatedAt()).isEqualTo(ADDED_AT);
        assertThat(group.getUpdatedAt()).isEqualTo(later);
    }

    @Test
    @DisplayName("a membership and its id keep the group, the user and the time it was added")
    void membership() {
        GroupMembership membership = new GroupMembership(3L, 9L, ADDED_AT);

        assertThat(membership.getGroupId()).isEqualTo(3L);
        assertThat(membership.getUserId()).isEqualTo(9L);
        assertThat(membership.getAddedAt()).isEqualTo(ADDED_AT);
        assertThat(new GroupMembershipId(3L, 9L))
                .isEqualTo(new GroupMembershipId(3L, 9L))
                .hasSameHashCodeAs(new GroupMembershipId(3L, 9L))
                .isNotEqualTo(new GroupMembershipId(3L, 10L));
        assertThat(new GroupMembershipId(3L, 9L).getGroupId()).isEqualTo(3L);
        assertThat(new GroupMembershipId(3L, 9L).getUserId()).isEqualTo(9L);
    }
}
