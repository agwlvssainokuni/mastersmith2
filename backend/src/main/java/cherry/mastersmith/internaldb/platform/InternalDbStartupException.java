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
package cherry.mastersmith.internaldb.platform;

/**
 * 内部DB の起動の失敗で、起動を止める例外（BR5.1）。文は失敗の区分と固定の説明だけで、原因（cause）を連ねない（原因の連なりには
 * 接続先・利用者名・パスワードを含む文が入りうるため）。
 */
public class InternalDbStartupException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final StartupFailureKind failureKind;

    /**
     * 失敗の区分から作る。
     *
     * @param failureKind 失敗の区分
     */
    public InternalDbStartupException(StartupFailureKind failureKind) {
        super("Internal DB startup failed: " + failureKind);
        this.failureKind = failureKind;
    }

    /**
     * 失敗の区分。
     *
     * @return 失敗の区分
     */
    public StartupFailureKind failureKind() {
        return failureKind;
    }
}
