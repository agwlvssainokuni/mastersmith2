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
package cherry.mastersmith.invitation.service;

/** 取り消しの結果（契約 C5 の POST cancel）。 */
public sealed interface CancelResult {

    /** 取り消した。 */
    record Cancelled() implements CancelResult {}

    /** 対象が無い・招待中でない（BR6.3）。 */
    record NotFound() implements CancelResult {}
}
