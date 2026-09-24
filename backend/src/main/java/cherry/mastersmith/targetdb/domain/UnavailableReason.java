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
package cherry.mastersmith.targetdb.domain;

/** 対象DB を読めなかった理由（契約 C1 の {@code Unavailable.reason}。BR1.8・BR1.9）。 */
public enum UnavailableReason {
    /** 接続・問い合わせの待ちの上限で打ち切った。 */
    TIMEOUT,
    /** 接続・認証・問い合わせに失敗した。 */
    CONNECTION_FAILED
}
