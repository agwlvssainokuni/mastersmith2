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
// アプリシェルの外の独立した配置（ログイン画面など）（BR7.7）。
import type { ReactNode } from 'react'
import './StandaloneLayout.css'

export interface StandaloneLayoutProps {
  children: ReactNode
}

/** サイドバー・トップバーを持たず、子の画面を画面の中央に置く。 */
export function StandaloneLayout({ children }: StandaloneLayoutProps) {
  return (
    <main className="standalone-layout" data-testid="standalone-layout">
      {children}
    </main>
  )
}
