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
// 写しの関数を通した定義の見本（テストからだけ使う）。
import { parseTableDefinition } from '../businessDataParse'
import type { TableDefinition } from '../businessDataTypes'
import { SAMPLE_REF, wireDefinition, type WireObject } from './businessDataSamples'

/** 見本の定義を写して返す（形が崩れていれば例外。テストの見本の誤りを早く見つけるため） */
export function sampleDefinition(overrides: WireObject = {}): TableDefinition {
  const parsed = parseTableDefinition(wireDefinition(overrides), SAMPLE_REF)
  if (parsed === undefined) {
    throw new Error('the sample definition does not pass the parser')
  }
  return parsed
}
