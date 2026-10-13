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
package cherry.mastersmith.role.testsupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cherry.mastersmith.role.transfer.RoleTransferDocument;
import cherry.mastersmith.role.transfer.RoleTransferReader;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 上限ちょうどの大きさの権限の YAML の生成の部品（計画の 13節 Q7: A・D-22）の単体テスト。 */
class LargeRoleTransferYamlTest {

    private static final int LIMIT = 5_242_880;

    @Test
    @DisplayName("the YAML has exactly the target size and passes the safe reader and the validation")
    void exactSize() {
        byte[] bytes = LargeRoleTransferYaml.generateBytes(LargeRoleTransferYaml.Spec.of(10, 10_000, 30, LIMIT));

        assertThat(bytes).hasSize(LIMIT);
        RoleTransferReader.Result result = RoleTransferReader.read(
                TransferYamlTrees.tree(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)));
        assertThat(result).isInstanceOf(RoleTransferReader.Valid.class);
        RoleTransferDocument document = ((RoleTransferReader.Valid) result).document();
        assertThat(document.roles()).hasSize(10);
        assertThat(document.roles().getFirst().settings()).hasSize(1 + 100 + 10_000);
    }

    @Test
    @DisplayName("a small target is filled exactly, including a one byte remainder, and a too small target is refused")
    void smallTargets() {
        String base = LargeRoleTransferYaml.generate(LargeRoleTransferYaml.Spec.of(1, 2, 5, 1_000));
        int content = base.indexOf('#');

        assertThat(base.getBytes(java.nio.charset.StandardCharsets.UTF_8)).hasSize(1_000);
        assertThat(LargeRoleTransferYaml.generateBytes(LargeRoleTransferYaml.Spec.of(1, 2, 5, content + 1)))
                .hasSize(content + 1);
        assertThat(LargeRoleTransferYaml.generateBytes(LargeRoleTransferYaml.Spec.of(1, 2, 5, content)))
                .hasSize(content);
        assertThatThrownBy(() -> LargeRoleTransferYaml.generate(LargeRoleTransferYaml.Spec.of(1, 2, 5, content - 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LargeRoleTransferYaml.Spec.of(0, 1, 5, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("the main method writes the YAML to the given path for the load test preparation")
    void mainWritesFile(@TempDir Path dir) throws Exception {
        Path out = dir.resolve("role-transfer.yaml");

        LargeRoleTransferYaml.main(
                new String[] {out.toString(), "2", "150", "10", "20000", "perf", "perf_t", "100", "r_"});

        assertThat(Files.size(out)).isEqualTo(20_000);
        assertThat(Files.readString(out))
                .contains(
                        "  r_00001:",
                        "          perf_t002:",
                        "              c_001xxxxx: READ",
                        "              c_050xxxxx: READ");
    }
}
