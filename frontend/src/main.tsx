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
import '@fontsource/noto-sans-jp/japanese-400.css'
import '@fontsource/noto-sans-jp/japanese-500.css'
import '@fontsource/noto-sans-jp/japanese-600.css'
import '@fontsource/noto-sans-jp/japanese-700.css'
import '@fontsource/noto-sans-jp/latin-400.css'
import '@fontsource/noto-sans-jp/latin-500.css'
import '@fontsource/noto-sans-jp/latin-600.css'
import '@fontsource/noto-sans-jp/latin-700.css'
import 'make-you-chic-ui/style.css'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router'
import { App } from './app/App'

const rootElement = document.getElementById('root')
if (!rootElement) {
  throw new Error('#root の要素が見つかりません')
}

createRoot(rootElement).render(
  <StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </StrictMode>,
)
