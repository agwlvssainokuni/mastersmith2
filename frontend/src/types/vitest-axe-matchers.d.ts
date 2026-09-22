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
//
// vitest-axe の照合（toHaveNoViolations）の型を、Vitest の型に足す。
// vitest-axe@0.1.0 は古い書き方で型を足すため、現在の Vitest が読む `declare module 'vitest'` の形で宣言し直す。
import 'vitest'
import type { AxeMatchers } from 'vitest-axe'

// 型の宣言の結合のため、空のインターフェースと未使用の型の引数を残す。
/* oxlint-disable typescript/no-empty-object-type, no-unused-vars */
declare module 'vitest' {
  interface Assertion<T = unknown> extends AxeMatchers {}
  interface AsymmetricMatchersContaining extends AxeMatchers {}
}
/* oxlint-enable typescript/no-empty-object-type, no-unused-vars */
