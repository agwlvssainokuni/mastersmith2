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
// 業務データの画面（S1 一覧・S2 詳細・S6 開けないとき）の文言（Intent 261016-business-data-views の U3、
// frontend-components.md の 5節、inception/refined-mockups/interaction-spec.md の「文言」、計画 4.6、U3-NFR6.3）。
// - 鍵は `tables.` で始め、ja・en を対で置く。テーブル・カラムの表示名と値は訳さず、React の文字として描く。
// - 置き場の画面の `tables.preparing` は消し、`tables.forbidden` を業務データの文に変えた。
// - 作成・更新・削除の文言は B6 で足す。
import type { FeatureMessages } from '../../app/registry/types'

const ja: Record<string, string> = {
  'tables.loading': '読み込んでいます',
  'tables.forbidden': 'このテーブルを見る権限がありません。作業ロールを確かめてください。',
  'tables.currentWorkRole': '今の作業ロール: {{name}}',
  'tables.noWorkRole': 'なし',
  'tables.switchWorkRole': '作業ロールを切り替える',
  'tables.goHome': 'ホームへ',
  'tables.loadError': '読み込めませんでした。',
  'tables.retry': 'もう一度',
  'tables.backToList': '一覧へ戻る',
  'tables.chooseFromMenu': 'メニューからテーブルを選び直してください。',
  'tables.definitionChanged': '表示の設定が変わりました。',

  'tables.value.null': '（値なし）',
  'tables.value.nullSpoken': '値なし',
  'tables.value.empty': '（空）',
  'tables.value.binary': '（バイナリ）',
  'tables.value.maskedSpoken': '伏せた値',
  'tables.value.truncated': '長いため先頭だけを表示しています',
  'tables.value.truncatedMark': '（省略）',
  'tables.value.yes': 'はい',
  'tables.value.no': 'いいえ',
  'tables.value.on': 'オン',
  'tables.value.off': 'オフ',

  'tables.list.count': '全 {{total}} 件（{{from}}〜{{to}} 件を表示）',
  'tables.list.countEmpty': '全 {{total}} 件',
  'tables.list.empty.filtered': '条件に合う行がありません。',
  'tables.list.empty.table': 'このテーブルには行がありません。',
  'tables.list.pageOverflow': 'このページには行がありません。',
  'tables.list.prevPage': '前のページへ',
  'tables.list.regionLabel': '{{name}}の一覧の表',
  'tables.list.pageSize': '1ページの件数',
  'tables.table.prev': '前へ',
  'tables.table.next': '次へ',
  'tables.table.pageStatus': '{{page}} / {{pages}}ページ(全{{total}}件)',
  'tables.table.emptyStatus': '0件',
  'tables.table.selectAllRows': '全ての行を選択',
  'tables.table.selectRow': 'この行を選択',
  'tables.table.toggleRowDetail': '行の中身',
  'tables.table.actionsColumn': '操作',

  'tables.search.heading': '検索の条件',
  'tables.search.conditionCount': '（{{count}} 件の条件）',
  'tables.search.submit': '検索',
  'tables.search.clear': '条件をクリア',
  'tables.search.fieldLabel': '{{name}}（{{op}}）',
  'tables.search.from': '{{name}}の下限',
  'tables.search.to': '{{name}}の上限',
  'tables.search.op.EQUALS': '完全一致',
  'tables.search.op.CONTAINS': '部分一致',
  'tables.search.op.RANGE': '範囲',
  'tables.search.op.CHOICE': '選択肢',

  'tables.actions.button': '操作',
  'tables.actions.busySuffix': '（処理中）',
  'tables.actions.detail': '詳細',
  'tables.detailLink': '詳細: {{table}} {{label}}',
  'tables.detail.heading': '{{table}}: {{label}}',

  'tables.restriction.noPrimaryKey':
    'このテーブルは行を1件に決められないため、詳細・更新・削除はできません。',
  'tables.restriction.view': 'このビューは読み取りだけです。',
  'tables.writeNotConfigured':
    'いまは業務データを変えられません（書き込みの設定がありません）。管理者に確かめてください。',

  'tables.problem.tableNotFound': 'テーブルが見つかりません。',
  'tables.problem.rowNotFound': '行が見つかりません（ほかの人が削除した可能性があります）。',
  'tables.problem.dslNotApplied':
    'いまは業務データを使える状態ではありません。管理者に確かめてください。',
  'tables.problem.unavailable':
    '業務データのデータベースとつながりません。時間をおいてやり直してください。',
  'tables.problem.mismatch':
    'テーブルの定義とデータベースが食い違っています。管理者に確かめてください。',
  'tables.problem.notUnique': '行を1件に決められないため、この操作はできません。',
  'tables.problem.payloadTooLarge': '入力が大きすぎるため送れませんでした。値を短くしてください。',
  'tables.problem.badRequest':
    '条件か表示の範囲を読めませんでした。条件を見直してやり直してください。',
  'tables.problem.unknownClient': '要求を受け付けられませんでした。',
  'tables.problem.unknownServer': 'サーバーで問題が起きました。時間をおいてやり直してください。',

  'tables.error.NOT_ALLOWED': 'この項目は使えません。',
  'tables.error.INVALID_VALUE': '値を読めませんでした。形を確かめてください。',
  'tables.error.RANGE_REVERSED': '下限が上限より大きくなっています。',
  'tables.error.NOT_A_CHOICE': '選択肢に無い値です。',
  'tables.error.KEY_MISMATCH': '行を指す値が足りないか、余分な値があります。',
  'tables.error.REQUIRED': '入力してください。',
  'tables.error.TOO_SHORT': '短すぎます。',
  'tables.error.TOO_LONG': '長すぎます。',
  'tables.error.OUT_OF_RANGE': '範囲の外の値です。',
  'tables.error.TOO_MANY_DIGITS': '桁が多すぎます。',
  'tables.error.PATTERN_MISMATCH': '決められた形に合いません。',
  'tables.error.PATTERN_TIMEOUT': '形の確かめに時間がかかりすぎました。値を短くしてください。',
  'tables.error.DUPLICATE': '同じ値がすでにあります。',
  'tables.error.REFERENCE_NOT_FOUND': '参照先に無い値です。',
  'tables.error.NOT_NULL': '値を空にできません。',
  'tables.error.NOT_EDITABLE_NOT_NULL':
    'このテーブルの作成には、入力できない項目の値が要ります。管理者に確かめてください。',
  'tables.error.NO_CHANGE': '変えた項目がありません。',
}

const en: Record<string, string> = {
  'tables.loading': 'Loading',
  'tables.forbidden': 'You do not have permission to view this table. Check your work role.',
  'tables.currentWorkRole': 'Current work role: {{name}}',
  'tables.noWorkRole': 'none',
  'tables.switchWorkRole': 'Switch work role',
  'tables.goHome': 'Go to home',
  'tables.loadError': 'Could not load.',
  'tables.retry': 'Try again',
  'tables.backToList': 'Back to the list',
  'tables.chooseFromMenu': 'Choose the table again from the menu.',
  'tables.definitionChanged': 'The display settings have changed.',

  'tables.value.null': '(none)',
  'tables.value.nullSpoken': 'no value',
  'tables.value.empty': '(empty)',
  'tables.value.binary': '(binary)',
  'tables.value.maskedSpoken': 'hidden value',
  'tables.value.truncated': 'Only the beginning is shown because the value is long',
  'tables.value.truncatedMark': '(truncated)',
  'tables.value.yes': 'Yes',
  'tables.value.no': 'No',
  'tables.value.on': 'On',
  'tables.value.off': 'Off',

  'tables.list.count': '{{total}} rows ({{from}}–{{to}} shown)',
  'tables.list.countEmpty': '{{total}} rows',
  'tables.list.empty.filtered': 'No rows match your search.',
  'tables.list.empty.table': 'This table has no rows.',
  'tables.list.pageOverflow': 'There are no rows on this page.',
  'tables.list.prevPage': 'Go to the previous page',
  'tables.list.regionLabel': 'Table of {{name}}',
  'tables.list.pageSize': 'Rows per page',
  'tables.table.prev': 'Previous',
  'tables.table.next': 'Next',
  'tables.table.pageStatus': 'Page {{page}} of {{pages}} ({{total}} items)',
  'tables.table.emptyStatus': '0 items',
  'tables.table.selectAllRows': 'Select all rows',
  'tables.table.selectRow': 'Select this row',
  'tables.table.toggleRowDetail': 'Row contents',
  'tables.table.actionsColumn': 'Actions',

  'tables.search.heading': 'Search conditions',
  'tables.search.conditionCount': '({{count}} conditions)',
  'tables.search.submit': 'Search',
  'tables.search.clear': 'Clear conditions',
  'tables.search.fieldLabel': '{{name}} ({{op}})',
  'tables.search.from': '{{name}} from',
  'tables.search.to': '{{name}} to',
  'tables.search.op.EQUALS': 'exact match',
  'tables.search.op.CONTAINS': 'contains',
  'tables.search.op.RANGE': 'range',
  'tables.search.op.CHOICE': 'choices',

  'tables.actions.button': 'Actions',
  'tables.actions.busySuffix': ' (processing)',
  'tables.actions.detail': 'Details',
  'tables.detailLink': 'Details: {{table}} {{label}}',
  'tables.detail.heading': '{{table}}: {{label}}',

  'tables.restriction.noPrimaryKey':
    'Rows in this table cannot be identified one by one, so you cannot view, edit, or delete them.',
  'tables.restriction.view': 'This view is read-only.',
  'tables.writeNotConfigured':
    'Business data cannot be changed right now (no write connection is configured). Contact your administrator.',

  'tables.problem.tableNotFound': 'Table not found.',
  'tables.problem.rowNotFound': 'Row not found (it may have been deleted by someone else).',
  'tables.problem.dslNotApplied':
    'Business data is not available right now. Contact your administrator.',
  'tables.problem.unavailable': 'Cannot connect to the business database. Please try again later.',
  'tables.problem.mismatch':
    'The table definition does not match the database. Contact your administrator.',
  'tables.problem.notUnique':
    'This operation cannot be performed because the row cannot be identified uniquely.',
  'tables.problem.payloadTooLarge': 'The input is too large to send. Please shorten the values.',
  'tables.problem.badRequest':
    'The conditions or the display range could not be read. Check the conditions and try again.',
  'tables.problem.unknownClient': 'The request could not be accepted.',
  'tables.problem.unknownServer': 'A problem occurred on the server. Please try again later.',

  'tables.error.NOT_ALLOWED': 'This field cannot be used.',
  'tables.error.INVALID_VALUE': 'The value could not be read. Check its format.',
  'tables.error.RANGE_REVERSED': 'The lower bound is greater than the upper bound.',
  'tables.error.NOT_A_CHOICE': 'This value is not one of the choices.',
  'tables.error.KEY_MISMATCH': 'The values that identify the row are missing or extra.',
  'tables.error.REQUIRED': 'Enter a value.',
  'tables.error.TOO_SHORT': 'The value is too short.',
  'tables.error.TOO_LONG': 'The value is too long.',
  'tables.error.OUT_OF_RANGE': 'The value is out of range.',
  'tables.error.TOO_MANY_DIGITS': 'The value has too many digits.',
  'tables.error.PATTERN_MISMATCH': 'The value does not match the required format.',
  'tables.error.PATTERN_TIMEOUT': 'Checking the format took too long. Please shorten the value.',
  'tables.error.DUPLICATE': 'This value already exists.',
  'tables.error.REFERENCE_NOT_FOUND': 'This value does not exist in the referenced data.',
  'tables.error.NOT_NULL': 'This value cannot be empty.',
  'tables.error.NOT_EDITABLE_NOT_NULL':
    'Creating a row in this table requires values for fields you cannot enter. Contact your administrator.',
  'tables.error.NO_CHANGE': 'No fields were changed.',
}

/** 業務データの画面の文言 */
export const tablesMessages: FeatureMessages = { ja, en }
