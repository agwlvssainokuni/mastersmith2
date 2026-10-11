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
package cherry.mastersmith.role.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.domain.PermissionValues;
import cherry.mastersmith.role.domain.RoleName;
import cherry.mastersmith.role.domain.RoleNameValidation;
import cherry.mastersmith.role.transfer.TransferPlanner.CurrentRole;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 指紋（BR9.9）と本文の SHA-256（BR11.7）の単体テスト。 */
class TransferFingerprintTest {

    private static final PermissionValues READ = new PermissionValues(MainPermission.READ, null, null);

    private static RoleTransferDocument.Role role(String value, Map<PermissionTarget, PermissionValues> settings) {
        return new RoleTransferDocument.Role(((RoleNameValidation.Valid) RoleName.parse(value)).name(), settings);
    }

    private static String fingerprint(
            List<RoleTransferDocument.Role> roles, List<CurrentRole> current, String dslHash) {
        return TransferPlanner.plan(new RoleTransferDocument(roles), current, target -> true, dslHash)
                .fingerprint();
    }

    @Test
    @DisplayName("the same contents give the same fingerprint regardless of the order of the maps")
    void orderIndependent() {
        Map<PermissionTarget, PermissionValues> forward = new LinkedHashMap<>();
        forward.put(PermissionTarget.schema("A"), READ);
        forward.put(PermissionTarget.schema("B"), READ);
        Map<PermissionTarget, PermissionValues> backward = new LinkedHashMap<>();
        backward.put(PermissionTarget.schema("B"), READ);
        backward.put(PermissionTarget.schema("A"), READ);

        String first = fingerprint(List.of(role("x", forward), role("y", Map.of())), List.of(), "h");
        String second = fingerprint(List.of(role("y", Map.of()), role("x", backward)), List.of(), "h");

        assertThat(first).isEqualTo(second);
    }

    @Test
    @DisplayName("the DSL identity, the replaced role id and the values change the fingerprint")
    void sensitive() {
        List<RoleTransferDocument.Role> roles = List.of(role("x", Map.of(PermissionTarget.schema("A"), READ)));
        String base = fingerprint(roles, List.of(new CurrentRole(1, "x", "x", Map.of())), "h");

        assertThat(fingerprint(roles, List.of(new CurrentRole(1, "x", "x", Map.of())), "other"))
                .isNotEqualTo(base);
        assertThat(fingerprint(roles, List.of(new CurrentRole(1, "x", "x", Map.of())), null))
                .isNotEqualTo(base);
        assertThat(fingerprint(roles, List.of(new CurrentRole(2, "x", "x", Map.of())), "h"))
                .isNotEqualTo(base);
        assertThat(fingerprint(roles, List.of(), "h")).as("作るロールに変わる").isNotEqualTo(base);
        assertThat(fingerprint(
                        List.of(role(
                                "x",
                                Map.of(
                                        PermissionTarget.schema("A"),
                                        new PermissionValues(MainPermission.FULL, null, null)))),
                        List.of(new CurrentRole(1, "x", "x", Map.of())),
                        "h"))
                .isNotEqualTo(base);
    }

    @Test
    @DisplayName("names with separators do not collide with other lists")
    void noCollisions() {
        String one = fingerprint(List.of(role("a;b", Map.of())), List.of(), null);
        String two = fingerprint(List.of(role("a", Map.of()), role("b", Map.of())), List.of(), null);

        assertThat(one).isNotEqualTo(two);
    }

    @Test
    @DisplayName("a fingerprint and a file hash are 64 lowercase hex characters")
    void format() {
        String fingerprint = fingerprint(List.of(), List.of(), null);

        assertThat(fingerprint).matches("[0-9a-f]{64}");
        assertThat(TransferFingerprint.isWellFormed(fingerprint)).isTrue();
        assertThat(TransferFingerprint.isWellFormed(fingerprint.toUpperCase(java.util.Locale.ROOT)))
                .isFalse();
        assertThat(TransferFingerprint.isWellFormed("a".repeat(63))).isFalse();
        assertThat(TransferFingerprint.isWellFormed(null)).isFalse();
        assertThat(TransferFingerprint.sha256Hex("abc".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(new RoleTransferPayload("abc".getBytes(StandardCharsets.UTF_8)).sha256())
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    @DisplayName("the payload keeps a copy of the bytes and shows only its size")
    void payload() {
        byte[] bytes = "roles: SECRET".getBytes(StandardCharsets.UTF_8);
        RoleTransferPayload payload = new RoleTransferPayload(bytes);
        bytes[0] = 'X';

        assertThat(payload.bytes()[0]).isEqualTo((byte) 'r');
        assertThat(payload.size()).isEqualTo(13);
        assertThat(payload.toString()).isEqualTo("RoleTransferPayload[bytes=13]");
    }
}
