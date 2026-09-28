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
// 画面の入口。デザインシステムの CSS とフォント（自己ホスティング）を読み込み、アプリを描画する。
// 日本語の表示のため japanese、英語の表示のため latin のサブセットを読み込む。
// Noto Serif JP は見た目の設定のフォントファミリーが serif のときの明朝体（U4 の機能設計 9節）。@font-face の宣言だけでは
// フォントのファイルは読まれず、明朝体の文字を描くときにだけ読まれる（<link rel="preload"> は足さない）。
// 描画の前に、make-you-chic-ui の写しの鍵を U4 の鍵の値で書き直し（W1 の2）、要求の言語の関数を登録し、
// 見た目の設定の読み取りを描画の外で1回だけ始める（W1 の3、NFR6.2）。埋め込みのスクリプトは使わない（NFR9.4）。
import '@fontsource/noto-sans-jp/japanese-400.css'
import '@fontsource/noto-sans-jp/japanese-500.css'
import '@fontsource/noto-sans-jp/japanese-600.css'
import '@fontsource/noto-sans-jp/japanese-700.css'
import '@fontsource/noto-sans-jp/latin-400.css'
import '@fontsource/noto-sans-jp/latin-500.css'
import '@fontsource/noto-sans-jp/latin-600.css'
import '@fontsource/noto-sans-jp/latin-700.css'
import '@fontsource/noto-serif-jp/japanese-400.css'
import '@fontsource/noto-serif-jp/japanese-500.css'
import '@fontsource/noto-serif-jp/japanese-600.css'
import '@fontsource/noto-serif-jp/japanese-700.css'
import '@fontsource/noto-serif-jp/latin-400.css'
import '@fontsource/noto-serif-jp/latin-500.css'
import '@fontsource/noto-serif-jp/latin-600.css'
import '@fontsource/noto-serif-jp/latin-700.css'
import 'make-you-chic-ui/style.css'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router'
import { App } from './app/App'
import { startAppearanceLoad } from './app/display-settings/appearanceLoad'
import { rewriteDesignSystemCopies } from './app/display-settings/browserStorage'
import { getPrefersDark } from './app/display-settings/colorScheme'
import { installLanguageResolver } from './app/display-settings/displaySettingsStore'

const rootElement = document.getElementById('root')
if (!rootElement) {
  throw new Error('#root の要素が見つかりません')
}

rewriteDesignSystemCopies(getPrefersDark())
installLanguageResolver()
const appearance = startAppearanceLoad()

createRoot(rootElement).render(
  <StrictMode>
    <BrowserRouter>
      <App appearance={appearance} />
    </BrowserRouter>
  </StrictMode>,
)
