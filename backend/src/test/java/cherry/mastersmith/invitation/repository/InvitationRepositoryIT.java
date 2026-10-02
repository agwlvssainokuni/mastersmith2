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
package cherry.mastersmith.invitation.repository;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.common.paging.Paging;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.invitation.domain.Invitation;
import cherry.mastersmith.invitation.domain.InvitationEmail;
import cherry.mastersmith.invitation.domain.InvitationState;
import cherry.mastersmith.invitation.domain.SendResult;
import cherry.mastersmith.user.domain.Language;
import cherry.mastersmith.user.service.UserAccountService;
import cherry.mastersmith.user.testsupport.TestUserAccounts;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/** 招待の DB アクセスの結合テスト（組み込みの H2。BR2.3・BR4.3・BR5.1・BR5.2・BR11.1）。 */
@SpringBootTest(properties = "mastersmith.auth.password.bcrypt-cost=4")
class InvitationRepositoryIT {

    private static final Instant T0 = Instant.parse("2026-09-22T00:00:00Z");

    private static final AtomicInteger SEQ = new AtomicInteger(1000);

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @Autowired
    InvitationRepository repository;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    UserAccountService userAccountService;

    private long adminId;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM invitations");
        adminId = TestUserAccounts.create(
                userAccountService, "admin-" + UUID.randomUUID() + "@example.com", "テスト用パスワード-0000", true);
    }

    private static byte[] hash(int seq) {
        return HexFormat.of().parseHex("%064x".formatted(seq));
    }

    private Invitation save(String email, Instant invitedAt, Duration validity) {
        return save(email, invitedAt, validity, SEQ.incrementAndGet());
    }

    private Invitation save(String email, Instant invitedAt, Duration validity, int seq) {
        return tx.execute(status -> repository.saveAndFlush(new Invitation(
                new InvitationEmail(email), Language.JA, hash(seq), adminId, invitedAt, invitedAt.plus(validity))));
    }

    private int count(java.util.function.IntSupplier action) {
        Integer result = tx.execute(status -> action.getAsInt());
        return result == null ? 0 : result;
    }

    private void end(long id, String state, Instant endedAt) {
        jdbc.update("UPDATE invitations SET state = ?, ended_at = ? WHERE invitation_id = ?", state, endedAt, id);
    }

    @Test
    @DisplayName("an invitation is found by the pending email, the id and the token hash, with and without a lock")
    void reads() {
        Invitation saved = save("hanako@example.com", T0, Duration.ofHours(24), 1);
        InvitationEmail email = new InvitationEmail("hanako@example.com");

        tx.executeWithoutResult(status -> {
            assertThat(repository.findPendingByEmailForUpdate(email)).isPresent();
            assertThat(repository.findByIdForUpdate(saved.getInvitationId())).isPresent();
            assertThat(repository.findByTokenHashForUpdate(hash(1))).isPresent();
        });
        assertThat(repository.findByTokenHash(hash(1)))
                .map(Invitation::getInvitationId)
                .contains(saved.getInvitationId());
        assertThat(repository.findByTokenHash(hash(2))).isEmpty();
        assertThat(repository.findByEmailAndState(email, InvitationState.PENDING))
                .isPresent();
        end(saved.getInvitationId(), "CANCELLED", T0);
        assertThat(repository.findByEmailAndState(email, InvitationState.PENDING))
                .isEmpty();
        tx.executeWithoutResult(
                status -> assertThat(repository.findByIdForUpdate(-1)).isEmpty());
    }

    @Test
    @DisplayName("the list orders by invited time descending then id descending, pages by 20 and counts pending only")
    void pagingAndOrder() {
        for (int i = 0; i < 21; i++) {
            save("p" + i + "@example.com", T0.plusSeconds(i / 2), Duration.ofHours(1));
        }
        Invitation ended = save("ended@example.com", T0.plusSeconds(100), Duration.ofHours(1));
        end(ended.getInvitationId(), "COMPLETED", T0);

        List<Invitation> first = repository.findPage(InvitationState.PENDING, PageRequest.of(0, Paging.PAGE_SIZE));
        List<Invitation> second = repository.findPage(InvitationState.PENDING, PageRequest.of(1, Paging.PAGE_SIZE));

        assertThat(repository.countByState(InvitationState.PENDING)).isEqualTo(21);
        assertThat(first).hasSize(20);
        assertThat(second).hasSize(1);
        assertThat(repository.findPage(InvitationState.PENDING, PageRequest.of(2, 20)))
                .isEmpty();
        for (int i = 1; i < first.size(); i++) {
            Invitation before = first.get(i - 1);
            Invitation after = first.get(i);
            assertThat(before.getInvitedAt().isAfter(after.getInvitedAt())
                            || (before.getInvitedAt().equals(after.getInvitedAt())
                                    && before.getInvitationId() > after.getInvitationId()))
                    .isTrue();
        }
        Invitation last = second.getFirst();
        assertThat(repository.countBefore(InvitationState.PENDING, last.getInvitedAt(), last.getInvitationId()))
                .isEqualTo(20);
        Invitation top = first.getFirst();
        assertThat(repository.countBefore(InvitationState.PENDING, top.getInvitedAt(), top.getInvitationId()))
                .isZero();
    }

    @Test
    @DisplayName("the send result is written only to the pending row that still has the sent token hash")
    void conditionalSendResult() {
        Invitation saved = save("hanako@example.com", T0, Duration.ofHours(24), 5);
        long id = saved.getInvitationId();

        assertThat(count(() -> repository.recordSendResult(id, hash(6), SendResult.SENT)))
                .isZero();
        assertThat(count(() -> repository.recordSendResult(id, hash(5), SendResult.FAILED)))
                .isEqualTo(1);
        assertThat(count(() -> repository.recordSendResult(id, hash(5), SendResult.SENT)))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT send_result FROM invitations WHERE invitation_id = ?", String.class, id))
                .isEqualTo("FAILED");
        assertThat(count(() -> repository.recordSendResult(-1, hash(5), SendResult.SENT)))
                .isZero();
    }

    @Test
    @DisplayName("ended rows at or before the cutoff are deleted and later ones are kept, within the limit")
    void deleteEnded() {
        Instant cutoff = T0.plus(Duration.ofDays(90));
        Invitation atCutoff = save("a@example.com", T0, Duration.ofHours(1));
        Invitation before = save("b@example.com", T0, Duration.ofHours(1));
        Invitation after = save("c@example.com", T0, Duration.ofHours(1));
        Invitation pending = save("d@example.com", T0, Duration.ofHours(1));
        end(atCutoff.getInvitationId(), "COMPLETED", cutoff);
        end(before.getInvitationId(), "REPLACED", cutoff.minusSeconds(1));
        end(after.getInvitationId(), "CANCELLED", cutoff.plusNanos(1000));

        assertThat(count(() -> repository.deleteEndedBefore(cutoff, 1))).isEqualTo(1);
        assertThat(count(() -> repository.deleteEndedBefore(cutoff, 10))).isEqualTo(1);
        assertThat(count(() -> repository.deleteEndedBefore(cutoff, 10))).isZero();
        assertThat(repository.findById(after.getInvitationId())).isPresent();
        assertThat(repository.findById(pending.getInvitationId())).isPresent();
    }

    @Test
    @DisplayName("expired pending rows at or before the cutoff are deleted but resent rows with a new expiry are kept")
    void deleteExpiredPending() {
        Instant cutoff = T0.plus(Duration.ofDays(90));
        Invitation atCutoff = save("a@example.com", T0, Duration.ofDays(90));
        Invitation justAfter = save("b@example.com", T0.plusNanos(1000), Duration.ofDays(90));
        Invitation resent = save("c@example.com", T0, Duration.ofHours(24));
        Invitation ended = save("d@example.com", T0, Duration.ofHours(24));
        end(ended.getInvitationId(), "CANCELLED", cutoff.plusSeconds(1));
        tx.executeWithoutResult(status -> repository
                .findByIdForUpdate(resent.getInvitationId())
                .orElseThrow()
                .resend(hash(9999), cutoff, cutoff.plus(Duration.ofHours(24))));

        assertThat(count(() -> repository.deleteExpiredPendingBefore(cutoff, 10)))
                .isEqualTo(1);
        assertThat(repository.findById(atCutoff.getInvitationId())).isEmpty();
        assertThat(repository.findById(justAfter.getInvitationId())).isPresent();
        assertThat(repository.findById(resent.getInvitationId())).isPresent();
        assertThat(repository.findById(ended.getInvitationId())).isPresent();
    }
}
