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
package cherry.mastersmith.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * ログインの試みの状態（表 {@code login_attempt_states}）。利用者ごとに1行と、存在しないメールアドレスの試みに使うダミーの行
 * （負の ID）がある（BR2.7、BR3.9）。
 *
 * <p>書き込みは JPA の変更の検出に頼らず、repository の明示の更新の問い合わせで行う（読み書きの回数をそろえるため）。
 */
@Entity
@Table(name = "login_attempt_states")
public class LoginAttemptState {

    @Id
    @Column(name = "subject_id")
    private Long subjectId;

    @Column(name = "consecutive_failures", nullable = false)
    private int consecutiveFailures;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    /** JPA が使う。 */
    protected LoginAttemptState() {}

    /**
     * 状態を作る。
     *
     * @param subjectId 利用者ID、またはダミーの行の ID（負の値）
     * @param consecutiveFailures 連続失敗回数（0 以上）
     * @param lockedUntil ロックの解除時刻（ロックしていなければ null）
     */
    public LoginAttemptState(long subjectId, int consecutiveFailures, Instant lockedUntil) {
        this.subjectId = subjectId;
        this.consecutiveFailures = consecutiveFailures;
        this.lockedUntil = lockedUntil;
    }

    /**
     * 利用者ID、またはダミーの行の ID を返す。
     *
     * @return ID
     */
    public Long getSubjectId() {
        return subjectId;
    }

    /**
     * 連続失敗回数を返す。
     *
     * @return 連続失敗回数
     */
    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    /**
     * ロックの解除時刻を返す。
     *
     * @return ロックの解除時刻（ロックしていなければ null）
     */
    public Instant getLockedUntil() {
        return lockedUntil;
    }
}
