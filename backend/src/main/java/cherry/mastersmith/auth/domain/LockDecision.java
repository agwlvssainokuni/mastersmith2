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
 * ロックの判定の結果と、書き込む状態。
 *
 * @param outcome 判定の結果
 * @param next 書き込む状態（ロック中の拒否では元の状態のまま）
 * @param lockedNow この試みでロックしたか
 */
public record LockDecision(LoginOutcome outcome, LockState next, boolean lockedNow) {}
