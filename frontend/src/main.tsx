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
// 画面の入口。アプリの部品（App）は Step 17 で組み込む。
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import 'make-you-chic-ui/style.css'

const rootElement = document.getElementById('root')
if (!rootElement) {
  throw new Error('#root の要素が見つかりません')
}

createRoot(rootElement).render(<StrictMode />)
