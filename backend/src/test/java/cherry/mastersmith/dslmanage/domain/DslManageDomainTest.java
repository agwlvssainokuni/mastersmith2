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
package cherry.mastersmith.dslmanage.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cherry.mastersmith.dsl.domain.DisplayName;
import cherry.mastersmith.dslmanage.domain.PreviewView.ColumnDiff;
import cherry.mastersmith.dslmanage.domain.PreviewView.DiffChange;
import cherry.mastersmith.dslmanage.domain.PreviewView.MissingDisplayName;
import cherry.mastersmith.dslmanage.domain.PreviewView.Summary;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** U4 のドメインの値（本文の持ち方・文字列化・値の確かめ・ファイル名）の単体テスト。 */
class DslManageDomainTest {

    private static final Instant AT = Instant.parse("2026-09-24T00:00:00Z");

    private static final DslPreviewRef PREVIEW =
            new DslPreviewRef(UUID.randomUUID(), "a".repeat(64), DslSource.UPLOAD, 1L, AT);

    @Test
    @org.junit.jupiter.api.DisplayName("content copies its bytes, compares by value and never prints the body")
    void content() {
        byte[] bytes = "version: 1\n".getBytes(StandardCharsets.UTF_8);
        DslContent<DslPreviewRef> content = new DslContent<>(PREVIEW, bytes);
        bytes[0] = 'X';
        byte[] returned = content.yamlBytes();
        returned[1] = 'Y';

        assertThat(new String(content.yamlBytes(), StandardCharsets.UTF_8)).isEqualTo("version: 1\n");
        assertThat(content)
                .isEqualTo(new DslContent<>(PREVIEW, "version: 1\n".getBytes(StandardCharsets.UTF_8)))
                .hasSameHashCodeAs(new DslContent<>(PREVIEW, "version: 1\n".getBytes(StandardCharsets.UTF_8)))
                .isNotEqualTo(new DslContent<>(PREVIEW, new byte[0]))
                .isNotEqualTo(new DslContent<>(
                        new DslPreviewRef(UUID.randomUUID(), "a".repeat(64), DslSource.UPLOAD, 1L, AT), bytes))
                .isNotEqualTo("version: 1");
        assertThat(content.toString()).contains("bytes=11").doesNotContain("version");
    }

    @Test
    @org.junit.jupiter.api.DisplayName("entities loaded without a body print a zero length and never the body")
    void entitiesToString() {
        assertThat(new DslAppliedRevisionRecord().toString()).contains("bytes=0");
        assertThat(new DslPreviewRecord().toString()).contains("bytes=0");
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "the download file name carries the kind and the first 12 characters of the hash")
    void downloadFileName() {
        DslDownload download = DslDownload.preview(new DslContent<>(PREVIEW, new byte[0]));

        assertThat(download.fileName()).isEqualTo("dsl-preview-aaaaaaaaaaaa.yaml");
        assertThat(DslDownload.prefix(null)).isNull();
        assertThat(DslDownload.prefix("abc")).isEqualTo("abc");
    }

    @Test
    @org.junit.jupiter.api.DisplayName("preview values reject inconsistent counts and unchanged columns")
    void previewValueRules() {
        List<MissingDisplayName> tooMany = Collections.nCopies(101, new MissingDisplayName("p", "ja"));
        assertThatThrownBy(() -> new Summary(0, 0, 0, List.of(), tooMany, 101))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Summary(0, 0, 0, List.of(), List.of(new MissingDisplayName("p", "ja")), 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ColumnDiff("c", DiffChange.UNCHANGED, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new PreviewView.MenuNode(new DisplayName("a", "a"), null, List.of()).children())
                .isEmpty();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("the operation event carries a rejection kind only for rejected submissions")
    void eventRules() {
        assertThat(new DslOperationEvent(
                                DslOperationType.DSL_SUBMISSION_REJECTED,
                                1L,
                                AT,
                                null,
                                null,
                                DslOperationEvent.SIZE_LIMIT,
                                "192.0.2.1",
                                null,
                                null)
                        .rejectionKind())
                .isEqualTo("SIZE_LIMIT");
        assertThatThrownBy(() -> new DslOperationEvent(
                        DslOperationType.DSL_GENERATED, 1L, AT, null, null, "SYNTAX", "192.0.2.1", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
