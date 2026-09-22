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

/**
 * アクセストークンの認証の失敗の区分（BR4.4。U3 との約束）。
 *
 * <p>トークンが無い要求はこの区分を持たない（U3 が {@code TOKEN_MISSING} とみなす）。
 */
public enum TokenFailureReason {
    /** 形式の誤り。 */
    TOKEN_MALFORMED,
    /** 署名・方式の不一致（{@code alg: none} を含む）。 */
    TOKEN_INVALID,
    /** 有効期限切れ。 */
    TOKEN_EXPIRED,
    /** 利用者が DB にいない。 */
    USER_NOT_FOUND
}
