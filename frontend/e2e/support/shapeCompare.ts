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
// 本物の応答と見本の、項目の名前と型の違いを求める（Intent 261004-role-menu の U6 の NFR6.10、計画 9.2 の 7・11節 D-7）。
// 150 が、140 で差し替える見本と、本物の API の応答の形が一致することを確かめる。違いは項目の道（名前）だけを返し、値を
// 出さない。配列は、見本の最初の要素の形と、本物のすべての要素を比べる（見本が空の配列なら中は比べない）。
// null を許す項目は、null と型のどちらでも一致とみなす（nullable に名前を入れる）。

function typeName(value: unknown): string {
  if (value === null) {
    return 'null'
  }
  if (Array.isArray(value)) {
    return 'array'
  }
  return typeof value
}

/** 違いの道の一覧（無ければ空） */
export function shapeCompare(
  actual: unknown,
  sample: unknown,
  nullable: ReadonlySet<string> = new Set(),
  at = '',
): string[] {
  const name = at === '' ? '(本文)' : at
  const key = at.split('.').at(-1)?.replace(/\[\]$/, '') ?? ''
  const actualType = typeName(actual)
  const sampleType = typeName(sample)
  if (actualType !== sampleType) {
    const eitherNull = actualType === 'null' || sampleType === 'null'
    return eitherNull && nullable.has(key) ? [] : [name]
  }
  if (Array.isArray(actual) && Array.isArray(sample)) {
    if (sample.length === 0) {
      return []
    }
    const differences = new Set<string>()
    for (const item of actual) {
      for (const difference of shapeCompare(item, sample[0], nullable, `${at}[]`)) {
        differences.add(difference)
      }
    }
    return [...differences]
  }
  if (actualType !== 'object') {
    return []
  }
  const actualRecord = actual as Record<string, unknown>
  const sampleRecord = sample as Record<string, unknown>
  const keys = new Set([...Object.keys(actualRecord), ...Object.keys(sampleRecord)])
  const differences: string[] = []
  for (const field of [...keys].sort()) {
    const path = at === '' ? field : `${at}.${field}`
    if (!(field in actualRecord) || !(field in sampleRecord)) {
      differences.push(path)
      continue
    }
    differences.push(...shapeCompare(actualRecord[field], sampleRecord[field], nullable, path))
  }
  return differences
}
