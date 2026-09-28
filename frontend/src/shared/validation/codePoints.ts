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
// 文字の数え方と前後の空白の除き方（U6 の functional-spec.md の 6節、U2 の BR1.1）。React・window・ブラウザの保存に触れない
// 純粋な関数で、U6（登録の完了）と U7（パスワードの変更・プリファレンス）が共用する。

/**
 * 前後から除く Unicode の White_Space の性質を持つ文字（U2 の BR1.1）。サーバーの
 * `backend/src/main/java/cherry/mastersmith/user/domain/DisplayName.java` の isWhiteSpace と同じ集合。
 * JavaScript の標準の trim とは集合が違う（trim は U+FEFF を除き、U+0085 を除かない）ため使わない。
 */
const WHITE_SPACE_CODE_POINTS: ReadonlySet<number> = new Set([
  0x0009, 0x000a, 0x000b, 0x000c, 0x000d, 0x0020, 0x0085, 0x00a0, 0x1680, 0x2000, 0x2001, 0x2002,
  0x2003, 0x2004, 0x2005, 0x2006, 0x2007, 0x2008, 0x2009, 0x200a, 0x2028, 0x2029, 0x202f, 0x205f,
  0x3000,
])

/** Unicode の White_Space の性質を持つ文字かどうか。 */
export function isWhiteSpaceCodePoint(codePoint: number): boolean {
  return WHITE_SPACE_CODE_POINTS.has(codePoint)
}

/**
 * コードポイントの数を返す（UTF-16 の単位の数ではない）。サロゲートの組は1つ、組になっていないサロゲートも1つと数える。
 */
export function countCodePoints(value: string): number {
  let count = 0
  let index = 0
  while (index < value.length) {
    const unit = value.charCodeAt(index)
    const next = index + 1 < value.length ? value.charCodeAt(index + 1) : -1
    // 上位のサロゲートの後に下位のサロゲートが続くときだけ、2つの単位を1つのコードポイントと数える。
    const pair = unit >= 0xd800 && unit <= 0xdbff && next >= 0xdc00 && next <= 0xdfff
    index += pair ? 2 : 1
    count += 1
  }
  return count
}

/** UTF-8 に符号化したバイト数を返す（TextEncoder と同じ数え方。組になっていないサロゲートは置換文字の3バイト）。 */
export function utf8ByteLength(value: string): number {
  let bytes = 0
  for (const char of value) {
    const codePoint = char.codePointAt(0) ?? 0
    if (codePoint < 0x80) {
      bytes += 1
    } else if (codePoint < 0x800) {
      bytes += 2
    } else if (codePoint < 0x10000) {
      // 組になっていないサロゲート（U+D800〜U+DFFF）も、置換文字 U+FFFD の3バイトになる。
      bytes += 3
    } else {
      bytes += 4
    }
  }
  return bytes
}

/** 前後から Unicode の White_Space の性質を持つ文字だけを除く（内側は変えない。U2 の BR1.1）。 */
export function trimDisplayName(value: string): string {
  // 集合の文字はすべて基本多言語面のサロゲートでない文字のため、UTF-16 の単位で見てよい。
  let start = 0
  let end = value.length
  while (start < end && isWhiteSpaceCodePoint(value.charCodeAt(start))) {
    start += 1
  }
  while (end > start && isWhiteSpaceCodePoint(value.charCodeAt(end - 1))) {
    end -= 1
  }
  return value.slice(start, end)
}
