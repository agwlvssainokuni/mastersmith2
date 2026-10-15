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
// 自分の権限の画面（S8、Should）の文言（Intent 261004-role-menu の U7 の D24〜D26・D29、frontend-components.md の
// 7節・9節）。鍵は `mypermissions.` で始め、ja・en を対で置く。共有の木の文言もここに持つ。対象の表示名とロールの名前は
// 訳さず、React の文字として描く。
import type { FeatureMessages } from '../../app/registry/types'

const ja: Record<string, string> = {
  'mypermissions.menu': '自分の権限',
  'mypermissions.heading': '自分の権限',
  'mypermissions.currentWorkRole': '今の作業ロール: {{name}}',
  'mypermissions.noRole': '作業ロールがありません。すべて見られません（NONE・不可）。',
  'mypermissions.empty': '対象がありません。',
  'mypermissions.loading': '読み込んでいます',
  'mypermissions.loadError': '読み込めませんでした。',
  'mypermissions.retry': 'もう一度',
  'mypermissions.tree.label': '自分の権限の対象',
  'mypermissions.tree.expand': '{{label}}を開く',
  'mypermissions.tree.collapse': '{{label}}を閉じる',
  'mypermissions.tree.childArea': '{{label}}の子',
  'mypermissions.tree.empty': '子がありません',
  'mypermissions.main.READ': 'READ（見られる・直せない）',
  'mypermissions.main.FULL': 'FULL（見られる・直せる）',
  'mypermissions.main.NONE': 'NONE（見られない）',
  'mypermissions.allowed': '可',
  'mypermissions.denied': '不可',
  'mypermissions.inMenu.shown': '出る',
  'mypermissions.inMenu.hidden': '出ない',
  'mypermissions.column.target': '対象',
  'mypermissions.column.main': '主権限',
  'mypermissions.column.create': 'CREATE',
  'mypermissions.column.delete': 'DELETE',
  'mypermissions.column.inMenu': '業務のメニュー',
  'mypermissions.column.column': 'カラム',
  'mypermissions.detail.heading': '選んだ対象の権限',
  'mypermissions.detail.columnsHeading': 'カラムの権限',
  'mypermissions.detail.select': '左の木から対象を選んでください。',
  'mypermissions.detail.noColumns': 'カラムがありません。',
}

const en: Record<string, string> = {
  'mypermissions.menu': 'My permissions',
  'mypermissions.heading': 'My permissions',
  'mypermissions.currentWorkRole': 'Current work role: {{name}}',
  'mypermissions.noRole': 'You have no work role. Nothing can be viewed (NONE, not allowed).',
  'mypermissions.empty': 'There is nothing to show.',
  'mypermissions.loading': 'Loading',
  'mypermissions.loadError': 'Could not load.',
  'mypermissions.retry': 'Try again',
  'mypermissions.tree.label': 'Targets of my permissions',
  'mypermissions.tree.expand': 'Expand {{label}}',
  'mypermissions.tree.collapse': 'Collapse {{label}}',
  'mypermissions.tree.childArea': 'Children of {{label}}',
  'mypermissions.tree.empty': 'No children',
  'mypermissions.main.READ': 'READ (can view, cannot edit)',
  'mypermissions.main.FULL': 'FULL (can view and edit)',
  'mypermissions.main.NONE': 'NONE (cannot view)',
  'mypermissions.allowed': 'Allowed',
  'mypermissions.denied': 'Not allowed',
  'mypermissions.inMenu.shown': 'Shown',
  'mypermissions.inMenu.hidden': 'Hidden',
  'mypermissions.column.target': 'Target',
  'mypermissions.column.main': 'Main permission',
  'mypermissions.column.create': 'CREATE',
  'mypermissions.column.delete': 'DELETE',
  'mypermissions.column.inMenu': 'Business menu',
  'mypermissions.column.column': 'Column',
  'mypermissions.detail.heading': 'Permissions of the selected target',
  'mypermissions.detail.columnsHeading': 'Column permissions',
  'mypermissions.detail.select': 'Choose a target in the tree.',
  'mypermissions.detail.noColumns': 'There are no columns.',
}

/** 自分の権限の画面の文言 */
export const myPermissionsMessages: FeatureMessages = { ja, en }
