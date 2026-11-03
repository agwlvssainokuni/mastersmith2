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
// アプリのアイコン（鍛冶の金床とハンマーの 1 色のシルエット）。飾りの部品で、読み上げには出さない（U6 の SD5）。
// - 図柄はこのソースに書いた固定の値だけで、props から d・色・クラスを受けない（props は大きさだけ）。
//   利用者の入力・サーバーの応答は図柄に入らない。
// - 色は周りの文字の色（currentColor）から継ぐ。独自の色を足さない。
// - 属性は SVG の許可リスト（scripts/check-svg-safety.mjs）の内側だけ。className・style・title・aria-label・
//   dangerouslySetInnerHTML は使わない。描いた結果を許可リストの検査に通すことを AppIcon.test.tsx が確かめる。
// - 図柄の d は frontend/public/app-icon.svg の d と同じ値（一致を AppIcon.test.tsx が確かめる）。
//   ログイン画面と、上のバーのロゴ（別の単位）が使う。

/** 図柄の path の d（public/app-icon.svg と同じ。金床・ハンマーの順） */
export const APP_ICON_PATHS = [
  'M6 34L16 30H58V38H46V46H54V54H14V46H22V38H16Z',
  'M17 5L34 19L27 29L10 15ZM24 10L28 14L37 4L32 1Z',
] as const

export interface AppIconProps {
  /** 幅と高さ（px）。既定は 24 */
  size?: number
}

/** アプリのアイコン。飾りのため読み上げに出さない。 */
export function AppIcon({ size = 24 }: AppIconProps) {
  return (
    <svg
      viewBox="0 0 64 64"
      width={size}
      height={size}
      fill="currentColor"
      aria-hidden="true"
      focusable="false"
    >
      {APP_ICON_PATHS.map((d) => (
        <path key={d} d={d} />
      ))}
    </svg>
  )
}
