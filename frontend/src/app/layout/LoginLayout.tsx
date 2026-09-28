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
// ログイン画面のレイアウト（BR7.7）。アプリ名と表示言語に応じた見出しを持ち、入力欄とボタンは U2 が子として置く。
// 右上の置き場（topRight）には、AuthUi がログインの画面の言語の切り替えを置く（U4 の W7）。
import type { ReactNode } from 'react'
import { useMessages } from '../i18n/I18nProvider'
import './LoginLayout.css'

export interface LoginLayoutProps {
  /** U2 が置く入力欄とボタン（無ければ空） */
  children?: ReactNode
  /** 右上に置くもの（任意。登録が無い場合の枠だけの表示では渡さない） */
  topRight?: ReactNode
}

/** ログイン画面の枠。role=LOGIN の画面が使う。 */
export function LoginLayout({ children, topRight }: LoginLayoutProps) {
  const t = useMessages()
  return (
    <section
      className="login-layout"
      data-testid="login-layout"
      aria-labelledby="login-layout-heading"
    >
      {topRight !== undefined && (
        <div className="login-layout-top-right" data-testid="login-layout-top-right">
          {topRight}
        </div>
      )}
      <p className="login-layout-app-name" data-testid="login-layout-app-name">
        {t('app.name')}
      </p>
      <h1 id="login-layout-heading" className="login-layout-heading">
        {t('login.heading')}
      </h1>
      <div className="login-layout-body" data-testid="login-layout-body">
        {children}
      </div>
    </section>
  )
}
