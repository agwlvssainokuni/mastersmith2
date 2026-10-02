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
// 400 VALIDATION_FAILED の Problem Details から項目ごとの誤りを読む純粋な関数（functional-spec.md の W12、
// security-design.md の 3.2、NFR9.2・NFR9.6）。U2 が決めた形 `fieldErrors: [{ field, reason }]` だけを知る1か所。
// 壊れた入力でも例外を出さず、読めなければ空の一覧を返す。reason は文字列のまま返し、ここでは解釈しない（3.3 で寄せる）。
// detail・traceId などのほかの値は写さない。React・window・API・ブラウザの保存に触れない。
// プリファレンスと利用者の管理の2つの機能が使うため、features/preferences から共通の置き場へ移した（U5 の FS 10節の (f)）。
import type { ProblemDetails } from './apiError'

/** 読み取った項目ごとの誤り */
export interface ReadFieldError<F extends string> {
  field: F
  reason: string
}

/**
 * Problem Details の `fieldErrors` から、画面の項目（fields）の誤りだけを読む。
 * 形の違う要素・知らない項目・同じ項目の2つ目以降は捨てる。problem が無い・形が違うときは空の一覧。
 */
export function readFieldErrors<F extends string>(
  problem: ProblemDetails | unknown,
  fields: readonly F[],
): ReadFieldError<F>[] {
  if (typeof problem !== 'object' || problem === null || Array.isArray(problem)) {
    return []
  }
  const list = (problem as Record<string, unknown>).fieldErrors
  if (!Array.isArray(list)) {
    return []
  }
  const known: readonly string[] = fields
  const seen = new Set<string>()
  const result: ReadFieldError<F>[] = []
  for (const item of list as unknown[]) {
    if (typeof item !== 'object' || item === null || Array.isArray(item)) {
      continue
    }
    const { field, reason } = item as Record<string, unknown>
    if (typeof field !== 'string' || typeof reason !== 'string') {
      continue
    }
    if (!known.includes(field) || seen.has(field)) {
      continue
    }
    seen.add(field)
    result.push({ field: field as F, reason })
  }
  return result
}
