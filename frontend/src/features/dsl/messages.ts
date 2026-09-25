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
// DSL の管理画面の文言（BR6.1・BR6.2、NFR9.1、NFR5.6）。鍵は `dsl.` で始め、ja・en を対で置く。
// サーバーの code ごとは `dsl.error.<code>`、誤りの種類は `dsl.errorKind.<kind>`、警告の種類は `dsl.warningKind.<kind>`、
// 出どころは `dsl.source.<source>`、違いの区分は `dsl.change.<change>`。物理名・識別・場所は文言に入れず、値として埋める。
// 大きさの上限は 10MB（U5 の NFR 要件 tech-stack-decisions.md 2節。承認済みの機能設計の 5MB との差）。
import type { FeatureMessages } from '../../app/registry/types'

const ja: Record<string, string> = {
  'dsl.nav.label': 'DSL',
  'dsl.page.heading': 'DSL',
  'dsl.page.tabsLabel': 'DSL の操作',
  'dsl.tab.preview': 'プレビュー',
  'dsl.tab.submit': '投入',
  'dsl.tab.history': '履歴',

  'dsl.status.heading': '今の状態',
  'dsl.status.applied': '適用中',
  'dsl.status.preview': 'プレビュー',
  'dsl.status.noApplied': 'まだ適用していません',
  'dsl.status.noPreview': 'プレビューはありません',
  'dsl.status.hash': '識別',
  'dsl.status.source': '出どころ',
  'dsl.status.appliedBy': '適用した人',
  'dsl.status.placedBy': '置いた人',
  'dsl.status.at': '日時',
  'dsl.status.loading': '今の状態を読み込んでいます',
  'dsl.status.loadFailed': '今の状態を読み込めませんでした',
  'dsl.user.unknown': '不明',

  'dsl.source.GENERATED': '生成',
  'dsl.source.UPLOAD': 'アップロード',
  'dsl.source.PASTE': '貼り付け',
  'dsl.source.RESTORE': '履歴からの戻し',

  'dsl.action.retry': 'もう一度読み込む',
  'dsl.action.readSchema': 'スキーマを読み込む',
  'dsl.action.download': 'ダウンロード',
  'dsl.action.discard': '破棄する',
  'dsl.action.apply': '適用する',
  'dsl.action.cancel': 'やめる',
  'dsl.action.close': '閉じる',
  'dsl.action.replace': '置き換える',
  'dsl.action.submit': '投入する',
  'dsl.action.restore': 'プレビューに戻す',
  'dsl.action.showLatest': '最新のプレビューを表示する',
  'dsl.action.goToSubmit': '投入のタブへ',

  'dsl.preview.heading': 'プレビュー',
  'dsl.preview.loading': 'プレビューを読み込んでいます',
  'dsl.preview.loadFailed': 'プレビューを読み込めませんでした',
  'dsl.preview.emptyHeading': 'プレビューはありません',
  'dsl.preview.emptyDescription': 'スキーマを読み込むか、DSL を投入すると、ここで確かめられます。',
  'dsl.preview.discardedByOther': 'プレビューは別の管理者によって破棄されました',
  'dsl.preview.replacedByOther':
    '適用できませんでした。あなたが確かめた後に、プレビューが別の管理者によって置き換えられました。適用中の DSL は変わっていません。',
  'dsl.preview.valid': '検証を通りました',
  'dsl.preview.mismatchCount': '対象DB との食い違いが {{count}}件あります（適用はできます）',
  'dsl.preview.notCompared':
    '対象DB と照合できませんでした。適用はできますが、食い違いは確かめられていません。運用者に接続の状態を確かめてもらってください。',
  'dsl.preview.summaryHeading': '要約',
  'dsl.preview.tableCount': 'テーブル {{tables}}（うちビュー {{views}}）',
  'dsl.preview.columnCount': 'カラム {{count}}',
  'dsl.preview.missingCount': '表示名の未設定 {{count}}件',
  'dsl.preview.missingShow': '場所を見る',
  'dsl.preview.missingHide': '場所を閉じる',
  'dsl.preview.missingTableLabel': '表示名が埋まっていない場所',
  'dsl.preview.missingPath': '場所',
  'dsl.preview.missingLanguage': '言語',
  'dsl.preview.missingTruncated': '先頭の {{shown}}件を表示しています（全 {{total}}件）',
  'dsl.preview.diffHeading': '適用中との違い',
  'dsl.preview.warningHeading': '対象DB との食い違い（警告）',
  'dsl.preview.menuHeading': 'メニュー',
  'dsl.language.ja': '日本語',
  'dsl.language.en': '英語',

  'dsl.diff.counts': '増えた {{added}}・減った {{removed}}・変わった {{changed}}',
  'dsl.diff.noApplied':
    '適用中の DSL がまだ無いため、すべてのテーブルとカラムを「増えた」として示します。',
  'dsl.diff.showAll': 'すべて表示',
  'dsl.diff.none': '適用中の DSL と違いはありません',
  'dsl.diff.tableLabel': 'テーブルごとの違い',
  'dsl.diff.table': 'テーブル',
  'dsl.diff.change': '区分',
  'dsl.diff.columnChanges': 'カラムの違い',
  'dsl.diff.column': 'カラム',
  'dsl.diff.changedItems': '変わった項目',
  'dsl.diff.expand': '{{table}} のカラムの違いを開く',
  'dsl.diff.collapse': '{{table}} のカラムの違いを閉じる',
  'dsl.diff.columnTableLabel': '{{table}} のカラムの違い',
  'dsl.change.ADDED': '増えた',
  'dsl.change.REMOVED': '減った',
  'dsl.change.CHANGED': '変わった',
  'dsl.change.UNCHANGED': '変わらない',

  'dsl.menu.expand': '{{label}} を開く',
  'dsl.menu.collapse': '{{label}} を閉じる',
  'dsl.menu.empty': 'メニューはありません',

  'dsl.warning.none': '対象DB との食い違いはありません',
  'dsl.warningKind.TABLE_MISSING': '対象DB に無いテーブル',
  'dsl.warningKind.COLUMN_MISSING': '対象DB に無いカラム',
  'dsl.warningKind.TYPE_MISMATCH': '型の違い',
  'dsl.warningKind.TARGET_UNCONFIGURED':
    '照合できませんでした（対象DB の接続先が設定されていません）',
  'dsl.warningKind.TARGET_UNAVAILABLE':
    '照合できませんでした（対象DB に接続できないか、応答がありません）',

  'dsl.errors.count': 'DSL に誤りが {{count}}件あります。受け付けていません。',
  'dsl.errors.truncated': '先頭の {{shown}}件を表示しています（残り {{rest}}件）。',
  'dsl.errors.single': 'DSL を受け付けていません。',
  'dsl.errors.unknown': 'DSL を受け付けられませんでした',
  'dsl.errors.tableLabel': 'DSL の誤りの一覧',
  'dsl.errors.line': '行',
  'dsl.errors.column': '列',
  'dsl.errors.path': '場所',
  'dsl.errors.kind': '種類',
  'dsl.errors.message': '内容',
  'dsl.errorKind.SIZE_LIMIT': '大きさ',
  'dsl.errorKind.DEPTH_LIMIT': '入れ子の深さ',
  'dsl.errorKind.ALIAS_LIMIT': '別名の数',
  'dsl.errorKind.FORBIDDEN_TAG': 'タグ',
  'dsl.errorKind.DUPLICATE_KEY': '重複キー',
  'dsl.errorKind.UNSUPPORTED_VERSION': '書式の版',
  'dsl.errorKind.SYNTAX': '構文',
  'dsl.errorKind.SEMANTIC': '意味',

  'dsl.history.heading': '適用の履歴',
  'dsl.history.caption': '適用の履歴（新しい順、最大 20件）',
  'dsl.history.loading': '履歴を読み込んでいます',
  'dsl.history.loadFailed': '履歴を読み込めませんでした',
  'dsl.history.empty': 'まだ一度も適用していません。適用すると、ここに履歴が残ります。',
  'dsl.history.at': '適用した日時',
  'dsl.history.by': '適用した人',
  'dsl.history.hash': '識別',
  'dsl.history.state': '状態',
  'dsl.history.actions': '操作',
  'dsl.history.current': '適用中',
  'dsl.history.restoreLabel': '{{at}} の版をプレビューに戻す',
  'dsl.history.downloadLabel': '適用中の DSL をダウンロード',

  'dsl.submit.heading': 'DSL の投入',
  'dsl.submit.mode': '投入のしかた',
  'dsl.submit.modeFile': 'ファイルを選ぶ',
  'dsl.submit.modePaste': '貼り付ける',
  'dsl.submit.fileLabel': 'DSL のファイル（YAML、10MB まで）',
  'dsl.submit.choose': 'ファイルを選ぶ',
  'dsl.submit.noFile': 'ファイルが選ばれていません',
  'dsl.submit.pasteLabel': 'DSL（YAML、10MB まで）',
  'dsl.submit.empty': 'ファイルを選ぶか、貼り付けてください',
  'dsl.submit.fileTooLarge':
    'ファイルが 10MB を超えています。10MB 以下のファイルを選んでください。',
  'dsl.submit.pasteTooLarge': '貼り付けた DSL が 10MB を超えています。10MB 以下にしてください。',
  'dsl.submit.readFailed': 'ファイルを読み込めませんでした。もう一度選んでください。',
  'dsl.submit.schemaLink': 'DSL の書式（JSON Schema）',

  'dsl.confirm.replaceTitle': '今のプレビューを置き換えますか',
  'dsl.confirm.replaceGenerate':
    'スキーマを読み込むと、今のプレビューは新しく作る DSL に置き換わります。適用中の DSL は変わりません。',
  'dsl.confirm.replaceSubmit':
    'DSL を投入すると、今のプレビューは投入した DSL に置き換わります。適用中の DSL は変わりません。',
  'dsl.confirm.replaceRestore':
    '履歴の版を戻すと、今のプレビューはその版に置き換わります。適用中の DSL は変わりません。',
  'dsl.confirm.currentPreview': '今のプレビュー',
  'dsl.confirm.applyTitle': 'DSL を適用しますか',
  'dsl.confirm.applyBody': '適用すると、アプリが使う DSL がこのプレビューの内容に切り替わります。',
  'dsl.confirm.diffLabel': '違い',
  'dsl.confirm.tableCounts': 'テーブル 増えた {{added}}・減った {{removed}}・変わった {{changed}}',
  'dsl.confirm.columnCounts': 'カラム 増えた {{added}}・減った {{removed}}・変わった {{changed}}',
  'dsl.confirm.warningLabel': '警告',
  'dsl.confirm.warningCount': '対象DB との食い違い {{count}}件',
  'dsl.confirm.noWarning': 'ありません',
  'dsl.confirm.discardTitle': 'プレビューを破棄しますか',
  'dsl.confirm.discardBody': '破棄したプレビューは元に戻せません。適用中の DSL は変わりません。',

  'dsl.toast.generated': 'スキーマを読み込み、DSL をプレビューに置きました',
  'dsl.toast.submitted': 'DSL をプレビューに置きました',
  'dsl.toast.restored': '履歴の版をプレビューに置きました',
  'dsl.toast.applied': 'DSL を適用しました',
  'dsl.toast.discarded': 'プレビューを破棄しました',
  'dsl.live.generating': 'スキーマを読み込んでいます',
  'dsl.live.submitting': 'DSL を確かめています',
  'dsl.live.restoring': '履歴の版を確かめています',
  'dsl.live.applying': 'DSL を適用しています',
  'dsl.live.discarding': 'プレビューを破棄しています',

  'dsl.error.DSL_INVALID': 'DSL に誤りがあります。一覧を見て直してください',
  'dsl.error.DSL_TOO_LARGE': 'DSL が大きすぎます（10MB まで）',
  'dsl.error.DSL_PREVIEW_NOT_FOUND':
    'プレビューはありません（別の管理者が適用・破棄した可能性があります）',
  'dsl.error.DSL_PREVIEW_CHANGED': '確かめた後にプレビューが変わりました',
  'dsl.error.DSL_APPLIED_NOT_FOUND': '適用中の DSL はありません',
  'dsl.error.DSL_REVISION_NOT_FOUND':
    'その版は履歴にありません（件数の上限で消えた可能性があります）',
  'dsl.error.TARGET_DB_UNCONFIGURED':
    'スキーマを読み込めませんでした。対象DB の接続先が設定されていません。運用者に、対象DB の接続の設定を確かめてもらってください',
  'dsl.error.TARGET_DB_UNAVAILABLE':
    'スキーマを読み込めませんでした。対象DB に接続できないか、応答がありませんでした。運用者に、対象DB の状態を確かめてもらってください',
  'dsl.error.DSL_BUSY': 'ほかの処理中です。少し待ってからやり直してください',
  'dsl.errorGeneral.client':
    '操作を受け付けられませんでした。画面を読み込み直してからやり直してください',
  'dsl.errorGeneral.server': 'サーバーで問題が起きました。しばらくしてからやり直してください',
  'dsl.errorGeneral.network':
    'サーバーにつながりませんでした。通信の状態を確かめてからやり直してください',
}

const en: Record<string, string> = {
  'dsl.nav.label': 'DSL',
  'dsl.page.heading': 'DSL',
  'dsl.page.tabsLabel': 'DSL operations',
  'dsl.tab.preview': 'Preview',
  'dsl.tab.submit': 'Submit',
  'dsl.tab.history': 'History',

  'dsl.status.heading': 'Current state',
  'dsl.status.applied': 'Applied',
  'dsl.status.preview': 'Preview',
  'dsl.status.noApplied': 'Nothing has been applied yet',
  'dsl.status.noPreview': 'There is no preview',
  'dsl.status.hash': 'ID',
  'dsl.status.source': 'Source',
  'dsl.status.appliedBy': 'Applied by',
  'dsl.status.placedBy': 'Placed by',
  'dsl.status.at': 'Date',
  'dsl.status.loading': 'Loading the current state',
  'dsl.status.loadFailed': 'The current state could not be loaded',
  'dsl.user.unknown': 'Unknown',

  'dsl.source.GENERATED': 'Generated',
  'dsl.source.UPLOAD': 'Upload',
  'dsl.source.PASTE': 'Paste',
  'dsl.source.RESTORE': 'Restored from history',

  'dsl.action.retry': 'Load again',
  'dsl.action.readSchema': 'Load schema',
  'dsl.action.download': 'Download',
  'dsl.action.discard': 'Discard',
  'dsl.action.apply': 'Apply',
  'dsl.action.cancel': 'Cancel',
  'dsl.action.close': 'Close',
  'dsl.action.replace': 'Replace',
  'dsl.action.submit': 'Submit',
  'dsl.action.restore': 'Restore to preview',
  'dsl.action.showLatest': 'Show the latest preview',
  'dsl.action.goToSubmit': 'Go to the Submit tab',

  'dsl.preview.heading': 'Preview',
  'dsl.preview.loading': 'Loading the preview',
  'dsl.preview.loadFailed': 'The preview could not be loaded',
  'dsl.preview.emptyHeading': 'There is no preview',
  'dsl.preview.emptyDescription': 'Load the schema or submit a DSL to check it here.',
  'dsl.preview.discardedByOther': 'The preview was discarded by another administrator',
  'dsl.preview.replacedByOther':
    'The DSL was not applied. Another administrator replaced the preview after you checked it. The applied DSL has not changed.',
  'dsl.preview.valid': 'The DSL passed validation',
  'dsl.preview.mismatchCount':
    'There are {{count}} mismatches with the target database (you can still apply it)',
  'dsl.preview.notCompared':
    'The DSL could not be compared with the target database. You can still apply it, but mismatches have not been checked. Ask the operator to check the connection.',
  'dsl.preview.summaryHeading': 'Summary',
  'dsl.preview.tableCount': 'Tables {{tables}} (views {{views}})',
  'dsl.preview.columnCount': 'Columns {{count}}',
  'dsl.preview.missingCount': 'Missing display names: {{count}}',
  'dsl.preview.missingShow': 'Show the places',
  'dsl.preview.missingHide': 'Hide the places',
  'dsl.preview.missingTableLabel': 'Places without a display name',
  'dsl.preview.missingPath': 'Place',
  'dsl.preview.missingLanguage': 'Language',
  'dsl.preview.missingTruncated': 'Showing the first {{shown}} of {{total}}',
  'dsl.preview.diffHeading': 'Differences from the applied DSL',
  'dsl.preview.warningHeading': 'Mismatches with the target database (warnings)',
  'dsl.preview.menuHeading': 'Menu',
  'dsl.language.ja': 'Japanese',
  'dsl.language.en': 'English',

  'dsl.diff.counts': 'Added {{added}} · Removed {{removed}} · Changed {{changed}}',
  'dsl.diff.noApplied': 'No DSL has been applied yet, so every table and column is shown as added.',
  'dsl.diff.showAll': 'Show all',
  'dsl.diff.none': 'There are no differences from the applied DSL',
  'dsl.diff.tableLabel': 'Differences by table',
  'dsl.diff.table': 'Table',
  'dsl.diff.change': 'Change',
  'dsl.diff.columnChanges': 'Column changes',
  'dsl.diff.column': 'Column',
  'dsl.diff.changedItems': 'Changed items',
  'dsl.diff.expand': 'Show the column differences of {{table}}',
  'dsl.diff.collapse': 'Hide the column differences of {{table}}',
  'dsl.diff.columnTableLabel': 'Column differences of {{table}}',
  'dsl.change.ADDED': 'Added',
  'dsl.change.REMOVED': 'Removed',
  'dsl.change.CHANGED': 'Changed',
  'dsl.change.UNCHANGED': 'Unchanged',

  'dsl.menu.expand': 'Expand {{label}}',
  'dsl.menu.collapse': 'Collapse {{label}}',
  'dsl.menu.empty': 'There is no menu',

  'dsl.warning.none': 'There are no mismatches with the target database',
  'dsl.warningKind.TABLE_MISSING': 'Tables missing from the target database',
  'dsl.warningKind.COLUMN_MISSING': 'Columns missing from the target database',
  'dsl.warningKind.TYPE_MISMATCH': 'Type differences',
  'dsl.warningKind.TARGET_UNCONFIGURED':
    'Not compared (the target database connection is not configured)',
  'dsl.warningKind.TARGET_UNAVAILABLE':
    'Not compared (the target database could not be reached or did not respond)',

  'dsl.errors.count': 'The DSL has {{count}} errors. It was not accepted.',
  'dsl.errors.truncated': 'Showing the first {{shown}} ({{rest}} more).',
  'dsl.errors.single': 'The DSL was not accepted.',
  'dsl.errors.unknown': 'The DSL could not be accepted',
  'dsl.errors.tableLabel': 'Errors in the DSL',
  'dsl.errors.line': 'Line',
  'dsl.errors.column': 'Column',
  'dsl.errors.path': 'Place',
  'dsl.errors.kind': 'Kind',
  'dsl.errors.message': 'Message',
  'dsl.errorKind.SIZE_LIMIT': 'Size',
  'dsl.errorKind.DEPTH_LIMIT': 'Nesting depth',
  'dsl.errorKind.ALIAS_LIMIT': 'Number of aliases',
  'dsl.errorKind.FORBIDDEN_TAG': 'Tag',
  'dsl.errorKind.DUPLICATE_KEY': 'Duplicate key',
  'dsl.errorKind.UNSUPPORTED_VERSION': 'Format version',
  'dsl.errorKind.SYNTAX': 'Syntax',
  'dsl.errorKind.SEMANTIC': 'Meaning',

  'dsl.history.heading': 'Application history',
  'dsl.history.caption': 'Application history (newest first, up to 20)',
  'dsl.history.loading': 'Loading the history',
  'dsl.history.loadFailed': 'The history could not be loaded',
  'dsl.history.empty': 'Nothing has been applied yet. The history appears here after you apply.',
  'dsl.history.at': 'Applied at',
  'dsl.history.by': 'Applied by',
  'dsl.history.hash': 'ID',
  'dsl.history.state': 'State',
  'dsl.history.actions': 'Actions',
  'dsl.history.current': 'Applied',
  'dsl.history.restoreLabel': 'Restore to preview the version of {{at}}',
  'dsl.history.downloadLabel': 'Download the applied DSL',

  'dsl.submit.heading': 'Submit a DSL',
  'dsl.submit.mode': 'How to submit',
  'dsl.submit.modeFile': 'Choose a file',
  'dsl.submit.modePaste': 'Paste',
  'dsl.submit.fileLabel': 'DSL file (YAML, up to 10MB)',
  'dsl.submit.choose': 'Choose a file',
  'dsl.submit.noFile': 'No file is chosen',
  'dsl.submit.pasteLabel': 'DSL (YAML, up to 10MB)',
  'dsl.submit.empty': 'Choose a file or paste a DSL',
  'dsl.submit.fileTooLarge': 'The file is larger than 10MB. Choose a file of 10MB or less.',
  'dsl.submit.pasteTooLarge': 'The pasted DSL is larger than 10MB. Make it 10MB or less.',
  'dsl.submit.readFailed': 'The file could not be read. Choose it again.',
  'dsl.submit.schemaLink': 'DSL format (JSON Schema)',

  'dsl.confirm.replaceTitle': 'Replace the current preview?',
  'dsl.confirm.replaceGenerate':
    'Loading the schema replaces the current preview with a newly generated DSL. The applied DSL does not change.',
  'dsl.confirm.replaceSubmit':
    'Submitting replaces the current preview with the submitted DSL. The applied DSL does not change.',
  'dsl.confirm.replaceRestore':
    'Restoring replaces the current preview with that version. The applied DSL does not change.',
  'dsl.confirm.currentPreview': 'Current preview',
  'dsl.confirm.applyTitle': 'Apply the DSL?',
  'dsl.confirm.applyBody':
    'Applying switches the DSL used by the application to the contents of this preview.',
  'dsl.confirm.diffLabel': 'Differences',
  'dsl.confirm.tableCounts': 'Tables added {{added}} · removed {{removed}} · changed {{changed}}',
  'dsl.confirm.columnCounts': 'Columns added {{added}} · removed {{removed}} · changed {{changed}}',
  'dsl.confirm.warningLabel': 'Warnings',
  'dsl.confirm.warningCount': '{{count}} mismatches with the target database',
  'dsl.confirm.noWarning': 'None',
  'dsl.confirm.discardTitle': 'Discard the preview?',
  'dsl.confirm.discardBody':
    'A discarded preview cannot be restored. The applied DSL does not change.',

  'dsl.toast.generated': 'The schema was loaded and the DSL was placed in the preview',
  'dsl.toast.submitted': 'The DSL was placed in the preview',
  'dsl.toast.restored': 'The version was placed in the preview',
  'dsl.toast.applied': 'The DSL was applied',
  'dsl.toast.discarded': 'The preview was discarded',
  'dsl.live.generating': 'Loading the schema',
  'dsl.live.submitting': 'Checking the DSL',
  'dsl.live.restoring': 'Checking the version',
  'dsl.live.applying': 'Applying the DSL',
  'dsl.live.discarding': 'Discarding the preview',

  'dsl.error.DSL_INVALID': 'The DSL has errors. Check the list and fix them',
  'dsl.error.DSL_TOO_LARGE': 'The DSL is too large (up to 10MB)',
  'dsl.error.DSL_PREVIEW_NOT_FOUND':
    'There is no preview (another administrator may have applied or discarded it)',
  'dsl.error.DSL_PREVIEW_CHANGED': 'The preview changed after you checked it',
  'dsl.error.DSL_APPLIED_NOT_FOUND': 'There is no applied DSL',
  'dsl.error.DSL_REVISION_NOT_FOUND':
    'That version is not in the history (it may have been removed by the history limit)',
  'dsl.error.TARGET_DB_UNCONFIGURED':
    'The schema could not be loaded. The target database connection is not configured. Ask the operator to check the connection settings of the target database',
  'dsl.error.TARGET_DB_UNAVAILABLE':
    'The schema could not be loaded. The target database could not be reached or did not respond. Ask the operator to check the state of the target database',
  'dsl.error.DSL_BUSY': 'Another operation is in progress. Wait a moment and try again',
  'dsl.errorGeneral.client': 'The operation was not accepted. Reload the screen and try again',
  'dsl.errorGeneral.server': 'A problem occurred on the server. Try again later',
  'dsl.errorGeneral.network':
    'The server could not be reached. Check your connection and try again',
}

/** DSL の管理画面の文言 */
export const dslMessages: FeatureMessages = { ja, en }
