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
// ホームのカードの一覧を作る純粋な関数（Intent 261102-timezone-ui-polish の U5、functional-spec.md の D16・D18・D29）。
// - 業務のカードは、業務のメニューが読み込み済み（loaded・reloading）のときだけ、最上位の項目ごとに作る（応答の順のまま）。
//   名前はサイドバーと同じ決め方、アイコンは resolveItemIcon、移り先は firstNavigablePath。移り先が無い項目は出さない。
// - 管理のカードは、サイドバーの管理の区画と同じ項目（selectAdminSidebarItems）から作る。管理者だけに order の順。
//   画面で権限を足さない（判定はサーバーの応答のログイン状態に従う）。
// - 道はアプリの中の道だけ（`/` で始まり `//` で始まらない）。外れたカードは出さない。
// - 例外を投げず、同じ入力には同じ結果を返す。
import type { IconName } from 'make-you-chic-ui'
import type { DisplayLanguage } from '../i18n/resolveLanguage'
import {
  resolveNavIcon,
  resolveNavLabel,
  type BusinessMenuView,
} from '../navigation/buildNavSections'
import { firstNavigablePath } from '../navigation/navDestination'
import { resolveItemIcon } from '../navigation/navIcon'
import { selectAdminSidebarItems } from '../navigation/navigationItems'
import type { FeatureRegistration, LoginState } from '../registry/types'

/** ホームのカード 1 枚 */
export interface HomeCardModel {
  /** 一意の識別（業務は `biz:` + 位置の道、管理は `adm:` + 登録の id） */
  id: string
  label: string
  icon: IconName
  /** アプリの中の道 */
  path: string
}

/** カードの一覧 */
export interface HomeCards {
  business: HomeCardModel[]
  admin: HomeCardModel[]
}

/** カードを作る入力 */
export interface BuildHomeCardsInput {
  business: BusinessMenuView
  loginState: LoginState
  registrations: readonly FeatureRegistration[]
  language: DisplayLanguage
  /** 文言の鍵から文言を引く */
  t: (key: string) => string
}

/** アプリの中の道か（外部の URL・プロトコル相対・`javascript:` を通さない） */
export function isInAppPath(path: string): boolean {
  return path.startsWith('/') && !path.startsWith('//')
}

/**
 * ホームのカードを作る。
 *
 * @param input 業務のメニューの状態・ログイン状態・画面の登録・表示の言語・文言を引く関数
 */
export function buildHomeCards(input: BuildHomeCardsInput): HomeCards {
  const { business, loginState, registrations, language, t } = input
  const untitled = t('nav.untitled')

  const businessCards: HomeCardModel[] = []
  if ((business.status === 'loaded' || business.status === 'reloading') && business.response) {
    for (const node of business.response.items) {
      const path = firstNavigablePath(node)
      if (path === null || !isInAppPath(path)) {
        continue
      }
      businessCards.push({
        id: `biz:${node.id}`,
        label: resolveNavLabel(node.label, language, node.table, untitled),
        icon: resolveItemIcon({
          icon: node.icon,
          tableKind: node.tableKind,
          navigable: node.navigable,
        }),
        path,
      })
    }
  }

  const adminCards: HomeCardModel[] = []
  for (const item of selectAdminSidebarItems(registrations, loginState)) {
    if (!isInAppPath(item.path)) {
      continue
    }
    adminCards.push({
      id: `adm:${item.id}`,
      label: t(item.labelKey),
      icon: resolveNavIcon(item.icon),
      path: item.path,
    })
  }

  return { business: businessCards, admin: adminCards }
}
