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
/**
 * 内部DB の行の排他の失敗（待ちの上限切れ・行き詰まり）の扱いの共通部品（Intent 260930-user-admin の U3、
 * {@code security-design.md} 7.1・7.2）。
 *
 * <p>排他の失敗の例外の連なりの最後（H2 の {@code MVStoreException}）の文には、排他されていた行の全部の列の値が入りうる。この
 * パッケージの部品は、例外を値を含まない形に置き換えるために使う。判定（{@link cherry.mastersmith.common.persistence.RowLockFailures}）、
 * 値を含まない例外（{@link cherry.mastersmith.common.persistence.RowLockUnavailableException}）、排他の口の結果
 * （{@link cherry.mastersmith.common.persistence.RowLockAttempt}）を持つ。どれも Bean ではなく、メソッドの呼び出しの追跡の対象の層の外に置く。
 */
package cherry.mastersmith.common.persistence;
