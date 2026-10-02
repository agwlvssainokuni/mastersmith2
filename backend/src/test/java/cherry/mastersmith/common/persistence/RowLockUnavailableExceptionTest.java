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
package cherry.mastersmith.common.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

class RowLockUnavailableExceptionTest {

    @Test
    @DisplayName("has a fixed message, the lock kind and no cause")
    void fixedMessageAndNoCause() {
        RowLockUnavailableException exception = new RowLockUnavailableException("INVITATION_ROW");

        assertThat(exception.getMessage()).isEqualTo(RowLockUnavailableException.MESSAGE);
        assertThat(exception.getLockKind()).isEqualTo("INVITATION_ROW");
        assertThat(exception.getCause()).isNull();
        assertThat(exception.toString())
                .isEqualTo(RowLockUnavailableException.class.getName() + ": " + RowLockUnavailableException.MESSAGE);
    }

    @Test
    @DisplayName("a cause or suppressed exception cannot be attached later")
    void causeCannotBeAttachedLater() {
        RowLockUnavailableException exception = new RowLockUnavailableException("USER_ROW");

        assertThatThrownBy(() -> exception.initCause(new IllegalStateException("row value")))
                .isInstanceOf(IllegalStateException.class);
        exception.addSuppressed(new IllegalStateException("row value"));
        assertThat(exception.getSuppressed()).isEmpty();
        assertThat(exception.getCause()).isNull();
    }

    @Test
    @DisplayName("is not in the DataAccessException family, so concurrency catch blocks do not swallow it")
    void notADataAccessException() {
        assertThat(new RowLockUnavailableException("LOGIN_ATTEMPT_ROW"))
                .isNotInstanceOf(DataAccessException.class)
                .isInstanceOf(RuntimeException.class);
        assertThat(RowLockFailures.isLockFailure(new RowLockUnavailableException("LOGIN_ATTEMPT_ROW")))
                .isFalse();
    }
}
