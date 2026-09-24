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
package cherry.mastersmith.dslmanage.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.dslmanage.domain.DslAppliedRef;
import cherry.mastersmith.dslmanage.domain.DslContent;
import cherry.mastersmith.dslmanage.domain.DslPreviewRef;
import cherry.mastersmith.dslmanage.domain.DslSource;
import cherry.mastersmith.dslmanage.testsupport.RecordingStatementInspector;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/** U4 の保存（V5・V6 の移行、プレビューと適用の履歴の DB アクセス）の結合テスト（組み込みの H2）。 */
@SpringBootTest(
        properties = "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "cherry.mastersmith.dslmanage.testsupport.RecordingStatementInspector")
class DslManageRepositoryIT {

    private static final String HASH_A = "a".repeat(64);

    private static final String HASH_B = "b".repeat(64);

    private static final Instant T0 = Instant.parse("2026-09-24T01:00:00Z");

    /** 10MB（10,485,760 バイト）。 */
    private static final int TEN_MB = 10 * 1024 * 1024;

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    DslPreviewRepository previews;

    @Autowired
    DslAppliedRevisionRepository revisions;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM dsl_previews");
        jdbc.update("DELETE FROM dsl_applied_revisions");
    }

    private <T> T read(Supplier<T> action) {
        return tx.execute(status -> action.get());
    }

    private static byte[] yaml(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private UUID place(byte[] body, String hash, DslSource source, long userId, Instant at) {
        UUID previewId = UUID.randomUUID();
        tx.executeWithoutResult(status -> previews.place(previewId, body, hash, source, userId, at));
        return previewId;
    }

    private UUID apply(UUID previewId, long userId, Instant at) {
        UUID revisionId = UUID.randomUUID();
        int copied = read(() -> revisions.copyFromPreview(revisionId, previewId, userId, at));
        assertThat(copied).isEqualTo(1);
        return revisionId;
    }

    @Test
    @DisplayName("the U4 migrations V5 and V6 are applied at startup and add nullable audit columns")
    void migrationsApplied() {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT \"version\", \"success\" FROM"
                + " \"flyway_schema_history\" WHERE \"version\" IN ('5', '6') ORDER BY \"installed_rank\"");
        assertThat(rows).hasSize(2).allSatisfy(row -> assertThat(row).containsEntry("success", true));

        List<Map<String, Object>> auditColumns = jdbc.queryForList("SELECT COLUMN_NAME, IS_NULLABLE, DATA_TYPE"
                + " FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'AUDIT_EVENTS' AND COLUMN_NAME IN"
                + " ('ACTOR_USER_ID', 'DSL_HASH', 'DSL_SOURCE', 'REJECTION_KIND') ORDER BY COLUMN_NAME");
        assertThat(auditColumns).hasSize(4).allSatisfy(row -> assertThat(row).containsEntry("IS_NULLABLE", "YES"));

        List<String> bodyTypes = jdbc.queryForList(
                "SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS WHERE COLUMN_NAME = 'YAML_BYTES'"
                        + " AND TABLE_NAME IN ('DSL_PREVIEWS', 'DSL_APPLIED_REVISIONS')",
                String.class);
        assertThat(bodyTypes).containsExactly("BINARY LARGE OBJECT", "BINARY LARGE OBJECT");
    }

    @Test
    @DisplayName("placing a preview twice keeps a single row holding the later preview")
    void placeReplacesTheSingleRow() {
        place(yaml("version: 1\n"), HASH_A, DslSource.UPLOAD, 1L, T0);
        UUID second = place(yaml("version: 1 # 2\n"), HASH_B, DslSource.PASTE, 2L, T0.plusSeconds(1));

        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM dsl_previews", Integer.class);
        DslPreviewRef ref = read(() -> previews.findRef()).orElseThrow();

        assertThat(count).isEqualTo(1);
        assertThat(ref).isEqualTo(new DslPreviewRef(second, HASH_B, DslSource.PASTE, 2L, T0.plusSeconds(1)));
    }

    @Test
    @DisplayName("the preview table accepts only the fixed row key")
    void onlyTheFixedSlot() {
        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO dsl_previews (preview_slot, preview_id, yaml_bytes, dsl_hash, source,"
                                + " placed_by_user_id, placed_at) VALUES (2, RANDOM_UUID(), X'00', ?, 'UPLOAD', 1,"
                                + " CURRENT_TIMESTAMP)",
                        HASH_A))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("deleting by previewId removes the row only when the id matches")
    void deleteByPreviewIdCountsRows() {
        UUID previewId = place(yaml("version: 1\n"), HASH_A, DslSource.GENERATED, 1L, T0);

        int mismatch = read(() -> previews.deleteByPreviewId(UUID.randomUUID()));
        int match = read(() -> previews.deleteByPreviewId(previewId));

        assertThat(mismatch).isZero();
        assertThat(match).isEqualTo(1);
        assertThat(read(() -> previews.findRef())).isEmpty();
    }

    @Test
    @DisplayName("reading the status of the preview and the history never selects the body column")
    void projectionsDoNotReadTheBody() {
        UUID previewId = place(yaml("version: 1\n"), HASH_A, DslSource.UPLOAD, 1L, T0);
        apply(previewId, 1L, T0);

        RecordingStatementInspector.start();
        read(() -> previews.findRef());
        read(() -> revisions.findCurrentRef());
        read(() -> revisions.findRefsNewestFirst());
        List<String> selects = RecordingStatementInspector.stop();

        assertThat(selects).hasSize(3).noneMatch(sql -> sql.toLowerCase().contains("yaml_bytes"));
    }

    @Test
    @DisplayName("a 10MB body is stored and read back byte for byte, in the preview and in the history")
    void tenMegabytesRoundTrip() {
        byte[] body = new byte[TEN_MB];
        new Random(20260924L).nextBytes(body);

        UUID previewId = place(body, HASH_A, DslSource.UPLOAD, 1L, T0);
        DslContent<DslPreviewRef> preview = read(() -> previews.findContent()).orElseThrow();
        UUID revisionId = apply(previewId, 1L, T0);
        DslContent<DslAppliedRef> applied =
                read(() -> revisions.findContent(revisionId)).orElseThrow();

        assertThat(preview.yamlBytes()).hasSize(TEN_MB);
        assertThat(Arrays.equals(preview.yamlBytes(), body)).isTrue();
        assertThat(Arrays.equals(applied.yamlBytes(), body)).isTrue();
        assertThat(applied.ref().dslHash()).isEqualTo(HASH_A);
    }

    @Test
    @DisplayName(
            "copying a preview into the history keeps its body, hash and source, and copies nothing for another id")
    void copyFromPreview() {
        UUID previewId = place(yaml("version: 1\n"), HASH_A, DslSource.PASTE, 7L, T0);

        int none = read(() -> revisions.copyFromPreview(UUID.randomUUID(), UUID.randomUUID(), 8L, T0));
        UUID revisionId = apply(previewId, 8L, T0.plusSeconds(5));
        DslContent<DslAppliedRef> current =
                read(() -> revisions.findCurrentContent()).orElseThrow();

        assertThat(none).isZero();
        assertThat(current.ref())
                .isEqualTo(new DslAppliedRef(revisionId, HASH_A, DslSource.PASTE, 8L, T0.plusSeconds(5)));
        assertThat(new String(current.yamlBytes(), StandardCharsets.UTF_8)).isEqualTo("version: 1\n");
        assertThat(read(() -> revisions.findContent(UUID.randomUUID()))).isEmpty();
    }

    @Test
    @DisplayName("the current revision is the latest applied time, and the list is in the order of addition")
    void currentAndListOrder() {
        UUID previewId = place(yaml("version: 1\n"), HASH_A, DslSource.UPLOAD, 1L, T0);
        UUID first = apply(previewId, 1L, T0.plusSeconds(10));
        UUID second = apply(previewId, 1L, T0.plusSeconds(10));
        UUID third = apply(previewId, 1L, T0);

        DslAppliedRef current = read(() -> revisions.findCurrentRef()).orElseThrow();
        List<UUID> newestFirst = read(() -> revisions.findRefsNewestFirst()).stream()
                .map(DslAppliedRef::revisionId)
                .toList();

        assertThat(current.revisionId()).as("同じ日時なら追加の順の新しいもの").isEqualTo(second);
        assertThat(newestFirst).containsExactly(third, second, first);
    }

    @Test
    @DisplayName("history beyond the limit loses only its oldest rows in the order of addition")
    void deleteOlderThanNewest() {
        UUID previewId = place(yaml("version: 1\n"), HASH_A, DslSource.UPLOAD, 1L, T0);
        for (int i = 0; i < 20; i++) {
            apply(previewId, 1L, T0.plusSeconds(i));
        }
        List<DslAppliedRef> before = read(() -> revisions.findRefsNewestFirst());

        int keptAll = read(() -> revisions.deleteOlderThanNewest(20));
        apply(previewId, 1L, T0.plusSeconds(100));
        int removed = read(() -> revisions.deleteOlderThanNewest(20));
        List<DslAppliedRef> after = read(() -> revisions.findRefsNewestFirst());

        assertThat(keptAll).isZero();
        assertThat(removed).isEqualTo(1);
        assertThat(read(() -> revisions.count())).isEqualTo(20L);
        assertThat(after).doesNotContain(before.getLast()).contains(before.getFirst());
        assertThatThrownBy(() -> revisions.deleteOlderThanNewest(0))
                .isInstanceOf(InvalidDataAccessApiUsageException.class);
    }

    @Test
    @DisplayName("an empty table yields no preview and no current revision")
    void emptyTables() {
        assertThat(read(() -> previews.findRef())).isEmpty();
        assertThat(read(() -> previews.findContent())).isEmpty();
        Optional<DslAppliedRef> current = read(() -> revisions.findCurrentRef());
        assertThat(current).isEmpty();
        assertThat(read(() -> revisions.findCurrentContent())).isEmpty();
    }
}
