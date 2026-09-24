# Code Summary — U5 DSL の管理画面（u5-dsl-admin-ui）— B6

承認済みの `code-generation-plan.md` の Step 1〜Step 12 を、test-after（層ごとに実装 → その層のテストを書いて実行 → 通ってから次の層）で行った結果を記録する。層の順は、ApiClient の拡張（Step 2）→ dslApi と型（Step 3）→ 表示の部品（Step 4・5）→ 入力と確かめる表示の部品（Step 6・7）→ 画面と登録（Step 8・9）→ サーバー側のアクセス制御の一括の確かめ（Step 10）→ 1コマンドの検査と文書（Step 11）。

## 1. 作った・変えたファイル

一覧の正本は `source-manifest.json`（58 件）。

### 1.1 画面（`frontend/src/features/dsl/`、新規）

| ファイル | 内容 |
|---|---|
| `registration.ts` | 機能の登録（`featureId: 'dsl'`、`/admin/dsl`、`layout: 'SHELL'`・`access: 'ADMIN'`、サイドバー `order: 210`・`visibleWhen: 'ADMIN'`、画面は `lazy`） |
| `messages.ts` | 文言の ja・en（鍵は `dsl.` で始まる。`dsl.error.<code>` 9 つ、`dsl.errorKind.<kind>`・`dsl.warningKind.<kind>`・`dsl.source.<source>`・`dsl.change.<change>`） |
| `api/types.ts`・`api/dslApi.ts` | 契約 C6 の応答の型と、10 本の API を呼ぶ関数、`Content-Disposition` からのファイル名、422 の本文からの誤りの一覧の取り出し（BR2.8）、差し替え用の集まり `dslApi` |
| `api/saveFile.ts` | 受け取ったバイト列から一時的な URL を作って保存させ、すぐに捨てる（NFR3.10） |
| `DslAdminPage.tsx`・`useDslAdmin.ts` | 画面と、画面の状態・操作（同時の要求、古い応答で上書きしない、読み直し、確かめる表示、失敗の見分け、読み上げの領域） |
| `DslStatusPanel.tsx`・`DslHash.tsx` | 今の状態、識別の先頭 12 文字（全体はツールチップと読み上げ） |
| `DslTabs.tsx` | make-you-chic-ui の Tabs を包む |
| `DslPreviewPanel.tsx`・`DslDiffTable.tsx`・`DslMenuTree.tsx`・`DslWarningList.tsx` | プレビュー（要約・表示名の未設定・違い・照合の警告・メニューの木・操作） |
| `DslSubmitForm.tsx`・`submitInput.ts` | 投入（ファイル・貼り付け、`File.size` と UTF-8 のバイト数での 10MB の判定、JSON Schema のリンク） |
| `DslErrorList.tsx` | 誤りの件数と先頭 100 件の表、理由の1件だけの表示、件数の警告へのフォーカス |
| `DslHistoryTable.tsx` | 適用の履歴、適用中の印、戻し、適用中のダウンロード |
| `DslConfirmDialog.tsx` | 置き換え・適用・破棄の確認（はじめのフォーカスは「やめる」、背景のクリックでは閉じない） |
| `failureMessage.ts`・`format.ts`・`diffCounts.ts`・`loadState.ts`・`useDslText.ts` | 失敗から文言の鍵、日時・大きさの書式、違いの数え方、読み込みの状態、値を埋める文言 |
| `*.css`（10 個） | 部品と同じ場所の素の CSS（make-you-chic-ui の値だけを使う） |
| `testing/renderDsl.tsx`・`testing/fixtures.ts` | テストだけが使う描画の補助と C6 の形のテストの値 |
| `*.test.ts(x)`（16 個） | 3節 |

### 1.2 既存の部分の変更

| ファイル | 変更 |
|---|---|
| `frontend/src/shared/api-client/apiError.ts` | `ProblemDetails` の型、`readProblem`、`ApiResponseError` に `problem`（本文が Problem Details のときだけ）を足した。`problem` は列挙されない項目として付けるため、`kind`・`status`・`code` の形と既存の `toEqual` の確かめは変わらない |
| `frontend/src/shared/api-client/apiClient.ts` | `apiRequest` のエラーを `toApiError` で作る（`problem` が付く）。ダウンロードの口 `apiDownload`（本文のバイト列と `Content-Disposition`）を足した。`apiFetch`・`apiRequest` の呼び方は変えていない |
| `frontend/src/shared/api-client/apiError.problem.test.ts`・`apiClient.download.test.ts`（新規） | 足した口のテスト。既存のテストのファイルは変えていない |
| `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAccessControlIT.java`（新規） | 10 本の API の 401・403（監査つき）・管理者の結果と、401・403 で状態が変わらないこと |
| `README.md` | 節「DSL の管理画面（U5）」（使い方の要約と JSON Schema のリンクの場所） |

本番のバックエンドのコード、U1〜U4 のコード、画面の骨組み（`frontend/src/app/`）、`vendor/make-you-chic-ui` は変えていない。新しい依存も足していない（`package-lock.json`・`gradle.lockfile` は変わらない）。

## 2. 主な判断

| 判断 | 理由 |
|---|---|
| `problem` を列挙されない項目で付けた | BR2.6「項目を足すだけ。既存のテストはそのまま通る」を守るため。既存のテストはエラーの値を `toEqual` で比べており、列挙される項目を足すと形が変わる |
| 違いの表・誤りの一覧・履歴・表示名の未設定の表を、make-you-chic-ui の Table ではなく、同じ見た目の素の `table`（`mycui-table` の見た目）で作った | Table は行の開閉を持たない（設計どおり）ことに加え、ページ送りの文言（「前へ」「全N件」など）が日本語に固定されていて英語の表示（NFR9.1）を満たせない。部品の見た目の値は make-you-chic-ui のものを使っている |
| 誤りの一覧の件数の警告は、make-you-chic-ui の Alert の見た目の `div`（`role="alert"`・`tabindex="-1"`）にした | Alert は `tabindex` を受け付けず、BR5.4 のフォーカスの移し先にできないため |
| 誤りの表に「種類」の列を足した（行・列・場所・種類・内容） | BR5.2「誤りの種類ごとに見出しの文言を画面で持つ」を、一覧の中でも示すため（mockups.md の表は4列） |
| 確かめる表示の背景のクリックを、Modal の前で押下の場所を覚えて無視する形にした | Modal は背景の押下・Escape・閉じるボタンの3つを同じ `onClose` で知らせるため。背景の押下だけを見分けて閉じない（BR7.1）。`vendor` は変えていない |
| ファイルの読み込みは投入のボタンを押したとき（確かめる表示の前）に行う | `File.size` で 10MB を先に判定し、上限の内のときだけテキストとして読む（NFR1.20、BR2.2）。読めなかったときは画面の中で知らせる |
| 画面の状態と操作を `useDslAdmin.ts` に分けた | 画面の部品を短く保つため。読み込みは `.then` の形にした（React Compiler の lint `react-hooks/set-state-in-effect` が effect の中の同期の setState を止めるため） |
| 置き換えの確認に示す「今のプレビュー」は、今の状態の `preview` を優先し、無ければ表示中のプレビューを使う | 出どころ・置いた人・日時の正は今の状態（AC1.1.6） |
| 403 を受けたら画面全体を「ページが見つかりません」にした | 既存の管理者向け領域（`AdminAreaPage`）と同じ扱い（functional-spec.md 9節） |
| `GET /preview` の `DSL_BUSY` は、プレビューの領域を「読み込めませんでした」と「もう一度読み込む」にし、画面の中の Alert に `DSL_BUSY` の文言を出す | NFR5.6（プレビューの表示で受けたら Alert で示す）と BR3.1（読めなかった領域に Alert と再読み込み）の両方を満たすため |
| 表示名の未設定・違い・メニューの木・投入の入力の値（DSL・対象DB から来る文字列）を i18next の埋め込みに使う場合も、文字として描く | i18next の既定（`skipOnVariables: true`）で埋めた値の中の入れ子の指定は展開されず、React が文字として描く（NFR3.9）。`dangerouslySetInnerHTML` は使っていない |

## 3. テストの件数とカバレッジ（実測）

README の「対象DB」の節の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を設定し、colima が動いている状態で `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を実行した（2026-09-24、10 分 33 秒、BUILD SUCCESSFUL、全段（0〜9）が通過、失敗・飛ばし 0）。その後に画面の流れのテストを1件（AC2.1.3）足し、`npm --prefix frontend run test:coverage`・フォーマット・リンタ・ライセンスヘッダーを流し直して通ることを確かめた。

| 対象 | 単体テスト（`*Test`・Vitest） | 結合テスト（`*IT`） |
|---|---|---|
| バックエンド全体 | 709 件（失敗 0・飛ばし 0） | 375 件（失敗 0・飛ばし 0） |
| うち U5（`DslAccessControlIT`） | — | 31 件 |
| 画面（Vitest）全体 | 313 件（失敗 0） | — |
| うち U5 | 146 件（`features/dsl` 136、ApiClient の拡張 10） | — |

U4 の B5 の記録（単体 709・結合 344・画面 167）からの差は、結合 +31、画面 +146。

画面の U5 のテストの内訳: `DslAdminPage` 24、`dslApi` 13、`DslPreviewPanel` 13、`DslSubmitForm` 11、`DslConfirmDialog` 10、`DslDiffTable` 9、`DslErrorList` 9、`DslStatusPanel` 7、`registration` 7、`DslHistoryTable` 6、`DslMenuTree` 6、`DslWarningList` 5、`format` 5、`submitInput` 5（UTF-8 のバイト数を TextEncoder と比べる性質ベースのテスト1件を含む）、`DslTabs` 4、`saveFile` 2、ApiClient の拡張 10。vitest-axe の検査は部品ごとに1件（画面・状態・タブ・プレビュー・違い・メニュー・警告・投入・誤り・履歴・確かめる表示の 11 部品）で、違反 0 件。

`DslAccessControlIT` の内訳: 10 本の API × 3 通り（未認証 401 と状態・DSL の監査が変わらない、管理者でない 403 と `ACCESS_DENIED`・`NOT_ADMIN`・要求のパスの監査の行が1件増え状態が変わらない、管理者の結果）＝ 30 件と、一覧が 10 本をそろえていることの確かめ 1 件。

| カバレッジ | 行 | 分岐 |
|---|---|---|
| 画面全体（`@vitest/coverage-v8`） | 97.86%（961/982） | 93.6%（585/625） |
| `src/features/dsl`（ファイルごとの最低は `useDslAdmin.ts` 94.0%・76.1%） | 96.98% | 92.28% |
| `src/features/dsl/api` | 98.36% | 97.82% |
| `src/shared/api-client` | 98.59% | 97.56% |
| バックエンド全体（JaCoCo、単体と結合の合算） | 98.1%（3884/3960） | 94.1%（1348/1433） |

下限（行 80%・分岐 70%）はすべて満たした。バックエンドは本番のコードを足していないため、B5 の記録と同じ値。計測の除外は足していない。

安全の検査（`verifySecurity`）: SpotBugs の統合を止める指摘 0 件、Gitleaks は漏えい無し、OSV-Scanner は依存が変わらないため統合を止める指摘なし。

## 4. 計画からのずれ

| ずれ | 扱い |
|---|---|
| 計画の3節の部品の表に無いファイルを足した: `DslHash`・`useDslAdmin`・`useDslText`・`messages`・`failureMessage`・`format`・`diffCounts`・`loadState`・`submitInput`・`api/saveFile`・`testing/*`・`DslCommon.css` | 3節の部品の中身を分けたもの（状態と操作、文言、書式、判定、テストの補助） |
| ApiClient の拡張のテストを、既存のテストのファイルに足さず、新しいファイル（`apiError.problem.test.ts`・`apiClient.download.test.ts`）に置いた | 「既存のテストは変えずに通す」を守るため |
| 登録の検査を `registration.test.tsx`（拡張子 tsx）にした | 画面の遅延読み込み（`lazy`）の確かめに JSX を使うため |
| `verify` の時間が 10 分 33 秒だった（B5 の記録は 4 分 6 秒） | 原因は切り分けていない。U5 の結合テスト（31 件、毎回データを用意する）と画面のテストの増加に加え、同じ VM で配備済みのアプリが動いていた。Build and Test への申し送り（6節） |
| Step 11 の `verify` の後に、画面の流れのテストを1件（投入の置き換えを取りやめたとき入力が残る、AC2.1.3）足した | 取りやめの確かめが確かめる表示の部品のテストだけで、入力が残ることを画面で直接確かめていなかったため。足した後に画面のテスト・カバレッジ・フォーマット・リンタ・ライセンスヘッダーを流し直して通ることを確かめた（`verify` 全体は流し直していない） |

## 5. 承認済みの文書との差

承認済みの文書は書き換えていない。

| 項目 | 文書 | 承認済みの記述 | 実装 |
|---|---|---|---|
| 大きさの上限 | functional-spec.md 6節・9節、frontend-components.md BR2.3、Refined Mockups の入力の欄 | 5MB（5 × 1024 × 1024 バイト） | 10MB（10 × 1024 × 1024 バイト）、文言は「10MB まで」（U5 の NFR 要件 tech-stack-decisions.md 2節） |
| `DSL_BUSY`（503） | functional-spec.md 6節 | 無い | 文言「ほかの処理中です。少し待ってからやり直してください」（ja・en）。画面の中の Alert、状態を読み直さない、入力を残す（NFR5.6） |
| JSON Schema のリンク | 画面の部品 | 無い | 投入のタブに `/dsl/dsl-schema-v1.json` への `download` 属性つきの普通のリンク（NFR3.11） |
| API の本数 | functional-spec.md BR8.1、frontend-components.md 1.1・1.2、基盤の設計 | C6 の 11 本 | 契約 C6 と U4 の実装の 10 本（今の状態・プレビューの取得・投入・破棄・スキーマの読み込み・プレビュー中のダウンロード・適用・適用中のダウンロード・履歴・履歴からの戻し）。dslApi と `DslAccessControlIT` は 10 本をすべて扱う |
| 表の部品 | design-system-mapping.md 1節（違いの表・誤りの一覧・履歴は Table） | make-you-chic-ui の Table | 同じ見た目の素の `table`（2節の理由） |
| 誤りの一覧の列 | mockups.md 5.2 | 行・列・場所・内容 | 行・列・場所・種類・内容（2節の理由） |
| 確かめる表示の本文の結び付け | interaction-spec.md の DslConfirmDialog（本文を `aria-describedby` で結ぶ） | `aria-describedby` | make-you-chic-ui の Modal は見出しの `aria-labelledby` だけを付け、`aria-describedby` を渡す口が無い。`vendor` を変えられないため付けていない（本文は見出しの後に読まれる） |
| 閉じるボタンの名前 | 英語の表示（NFR9.1） | — | make-you-chic-ui の Modal・Alert の閉じるボタンの名前は「閉じる」に固定されており、英語の表示でも日本語のまま（`vendor` を変えられないため） |

## 6. Build and Test への申し送り

- **画面の時間の測定（NFR1.18〜NFR1.20）**: E2E（実際のブラウザー）で、画面を開いてから今の状態とプレビューが表示されるまで（照合を除く 3 秒・照合を含む 11 秒）、違いの表の行を開いてから表示まで（0.5 秒、100 テーブル × 100 カラム）、10MB のファイルを選んでから送り始めるまで（2 秒）を測って記録する。この段では描き方（開いた行だけ・先頭 100 件だけ・`File.size` で読む前に判定・状態とプレビューの同時の要求）を画面部品のテストで確かめた。
- **E2E の代表の流れ**: 「DSL の管理画面を開く」を足すかを決める（要件の OQ7）。
- **`verify` の時間**: 今回 10 分 33 秒（B5 の記録 4 分 6 秒）。対象DB の結合テストの置き場を決める実測（team.md の Testing Posture）と合わせて、配備済みのアプリを止めた状態でも測り、内訳を確かめる。
- **手での確認**: accessibility-checklist.md の手で確かめる項目（Tab の順、フォーカスの見え方、200% の拡大、767px 以下の幅、VoiceOver での読み上げ）は未確認。
- **サーバーの応答の言語**: 画面は `Accept-Language` を付けていない（既存の ApiClient のまま）。誤り・警告の `message` はブラウザーが送る `Accept-Language` の言語で返る。画面の表示言語の決め方（ブラウザーの希望言語）と同じ元なので通常は一致するが、E2E の英語の表示で確かめる。
