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
// 管理者向け領域を U1 の画面の骨組みに登録する（BR5.1、BR5.4）。U1 のファイルは書き換えない。
// サイドバーの「管理」の表示・非表示は表示の切り替えにすぎず、判定はサーバー側で行う（FR8.2）。
import { lazy } from 'react'
import type { FeatureRegistration } from '../../app/registry/types'

const AdminAreaPage = lazy(() =>
  import('./AdminAreaPage').then((module) => ({ default: module.AdminAreaPage })),
)

/** 管理者向け領域の URL */
export const ADMIN_AREA_PATH = '/admin'

/** 管理者向け領域の機能の登録 */
export const registration: FeatureRegistration = {
  featureId: 'admin',
  routes: [
    {
      path: ADMIN_AREA_PATH,
      screen: AdminAreaPage,
      layout: 'SHELL',
      access: 'ADMIN',
    },
  ],
  sidebarItems: [
    {
      id: 'admin-area',
      labelKey: 'admin.nav.label',
      path: ADMIN_AREA_PATH,
      order: 200,
      visibleWhen: 'ADMIN',
    },
  ],
  messages: {
    ja: {
      'admin.nav.label': '管理',
      'admin.area.heading': '管理',
      'admin.area.description':
        '今後の管理機能はこの画面に加わります。現在は表示できる機能がありません。',
      'admin.area.checking': '表示できるか確認しています',
      'admin.area.error':
        '管理の画面を表示できませんでした。しばらくしてから、もう一度お試しください',
    },
    en: {
      'admin.nav.label': 'Administration',
      'admin.area.heading': 'Administration',
      'admin.area.description':
        'Administration features will be added to this screen. There is nothing to show yet.',
      'admin.area.checking': 'Checking whether this screen can be shown',
      'admin.area.error': 'The administration screen could not be shown. Please try again later',
    },
  },
}
