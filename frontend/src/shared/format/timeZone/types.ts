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
// 日時変換（U1）の結果の型（LC6）。関数は例外を投げず、kind で分岐できる結果だけを返す。
// 呼ぶ側は kind を switch で尽くす。text・raw・instant は HTML ではない、ただの文字として扱う（SD2.1）。

/** 夏時間で2回ある時刻のうち、どちらの時点を選ぶか（earlier は先、later は後） */
export type OverlapChoice = 'earlier' | 'later'

/** 時点 → 表示の文字の結果 */
export type FormatResult =
  | { readonly kind: 'ok'; readonly text: string }
  | { readonly kind: 'unreadable'; readonly raw: string }
  | { readonly kind: 'invalid-time-zone' }

/** ローカル時刻 → 時点の結果（ok・overlap の時点は UTC の +00:00 の文字） */
export type ParseResult =
  | { readonly kind: 'ok'; readonly instant: string }
  | { readonly kind: 'gap' }
  | { readonly kind: 'overlap'; readonly earlier: string; readonly later: string }
  | { readonly kind: 'invalid'; readonly reason: 'format' | 'out-of-range' }
  | { readonly kind: 'invalid-time-zone' }

/** 2つの時点の比べの結果（order は a が前なら負・同じなら 0・後なら正） */
export type CompareResult =
  | { readonly kind: 'ok'; readonly order: number }
  | { readonly kind: 'unreadable'; readonly side: 'a' | 'b' | 'both' }
