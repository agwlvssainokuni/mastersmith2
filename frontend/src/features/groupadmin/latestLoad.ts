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
// 最新の読み込みの見分け（Intent 261004-role-menu の U6 の L4、NFR2.11）。読み込みを始めるたびに世代を進め、答えを受けた
// ときに世代が違えば捨てる。画面を離れたら世代を進めて、後から届いた答えをすべて捨てる。要求は取り消さない（ApiClient に
// 取り消しの口が無く、送った書き換えはサーバーで確定しうるため）。決まった間隔の自動の読み直しはしない。
// roleadmin・roletransfer にも同じ形を持つ（機能どうしの import の禁止）。

/** 最新の読み込みの見分け */
export interface LatestLoad {
  /** 読み込みを始め、その世代を返す */
  begin: () => number
  /** その世代が今も最新か */
  isLatest: (generation: number) => boolean
  /** 世代を進めて、始めた読み込みの答えをすべて古くする（画面を離れたとき） */
  invalidate: () => void
}

/** 最新の読み込みの見分けを作る。 */
export function createLatestLoad(): LatestLoad {
  let generation = 0
  return {
    begin: () => {
      generation += 1
      return generation
    },
    isLatest: (value) => value === generation,
    invalidate: () => {
      generation += 1
    },
  }
}
