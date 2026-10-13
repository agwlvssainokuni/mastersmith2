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
// ログアウトの画面（Intent 261004-role-menu の U7 の D27・W9.1、L11、security-design.md 5.2、NFR1.6・NFR1.8）。
// - 描画の中では印を読むだけ（状態の初期値の関数で peekLogoutIntent）。StrictMode の二度の描画でも同じ値を読む。
// - 描画の確定の後の効果の中で、ref で1回に守って次を行う（StrictMode の作り直しでも2回目の効果は何もしない）。
//   印があった: 印を消し、開閉の保存を消し、同じ機能の authSession.logout を1回呼ぶ。終わった後の移動は振り分けに
//   任せる（未ログインの LOGGED_IN の画面はログインの画面へ）。印が無かった: ログアウトせずにホームへ replace で移る。
// - logout の失敗は外へ出さない（画面の側のトークンの破棄は authSession.logout が必ず行う）。
import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { useMessages } from '../../app/i18n/I18nProvider'
import { clearExpanded } from '../../app/navigation/navExpansion'
import { logout } from './authSession'
import { consumeLogoutIntent, peekLogoutIntent } from './logoutIntent'

/** ログアウトの画面 */
export function LogoutPage() {
  const t = useMessages()
  const navigate = useNavigate()
  const [intended] = useState(peekLogoutIntent)
  const handled = useRef(false)

  useEffect(() => {
    if (handled.current) {
      return
    }
    handled.current = true
    if (!intended) {
      void navigate('/', { replace: true })
      return
    }
    consumeLogoutIntent()
    clearExpanded()
    void logout()
  }, [intended, navigate])

  return (
    <p role="status" data-testid="logout-page">
      {t('auth.logout.inProgress')}
    </p>
  )
}
