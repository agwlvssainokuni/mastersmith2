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
// 表示の書式（BR5.10・BR6.3）。識別は先頭 12 文字、ファイルの大きさ。日時は管理の画面すべてで表示のタイムゾーンの時刻の欄の部品
// （src/app/display-settings/DisplayTime.tsx。Intent 261102-timezone-ui-polish の U2）で描くため、ここには持たない。

/** 画面に示す識別の長さ */
export const SHORT_HASH_LENGTH = 12

/** 識別を先頭 12 文字に縮める。 */
export function shortHash(hash: string): string {
  return hash.slice(0, SHORT_HASH_LENGTH)
}

/** ファイルの大きさを人が読める形にする（1024 を単位とする）。 */
export function formatBytes(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes}B`
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)}KB`
  }
  return `${(bytes / (1024 * 1024)).toFixed(1)}MB`
}
