# Code Generation Plan — U5 DSL の管理画面（u5-dsl-admin-ui）

U5 のコード生成の計画を示す。作るものは、管理者が DSL を扱う画面（`/admin/dsl`、`frontend/src/features/dsl/`）と、その画面が使う ApiClient の拡張（`frontend/src/shared/api-client/`）、およびサーバー側のアクセス制御の一括の確かめ（`backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAccessControlIT.java`）である。Bolt は B6（`inception/delivery-planning/bolt-plan.md`）。

## 1. 入力にした設計

| 文書 | 使うところ |
|---|---|
| `construction/u5-dsl-admin-ui/functional-design/functional-spec.md`・`frontend-components.md` | 置き場と登録、操作の手順、読み直し、誤り・警告・知らせの表示、文言、アクセシビリティ、アクセス制御の一括の確かめ、部品の構成、dslApi、ApiClient の拡張、画面の状態（BR1.1〜BR8.2） |
| `construction/u5-dsl-admin-ui/nfr-requirements/*.md`・`nfr-design/*.md` | NFR1.18〜NFR1.21、NFR3.9〜NFR3.11、NFR5.6、NFR9.1、NFR10.1、承認済みの機能設計との差（10MB・`DSL_BUSY`・JSON Schema のリンク） |
| `construction/u5-dsl-admin-ui/infrastructure-design/*.md` | 検査の流れ、`DslAccessControlIT`、E2E での時間の測定（Build and Test） |
| `inception/refined-mockups/mockups.md`・`interaction-spec.md`・`design-system-mapping.md`・`accessibility-checklist.md` | 画面の配置、部品ごとの状態・入力・アクセシビリティ、使う make-you-chic-ui の部品、点検の項目 |
| `inception/contract-design/contract-summary.md` の C6 | API の形と code の一覧 |
| `inception/user-stories/stories.md` | US6.2（主）と、US1.1・US2.1・US2.2・US3.x・US4.x・US5.x の画面の部分（機能設計の traceability.json で OK のもの） |
| U4 のコード生成の記録（`construction/u4-dsl-management/code-generation/code-summary.md`） | 実装した API・code（`DSL_BUSY` を含む）、U5 への申し送り（戻しと適用中のダウンロードを一括の確かめに含める、戻しの置き換えの確かめ AC5.1.4） |

## 2. 前提と、この計画での読み方

- **承認済みの機能設計との差（NFR 要件・NFR 設計で決定済み）**: 大きさの上限は 10MB（10 × 1024 × 1024 バイト、文言は「10MB まで」）。`DSL_BUSY`（503）を受けたら画面の中の Alert で「ほかの処理中です。少し待ってからやり直してください」を示し、状態は読み直さず入力は残す。投入のタブに JSON Schema のリンク（同じオリジンの固定のパス `/dsl/dsl-schema-v1.json`、`download` 属性つき、トークンを付けない）を置く。承認済みの文書は書き換えない。
- **承認の場で決まったこと**: NFR Design の承認の場「U5 の Minor は直さない」。この段で新しく扱うものは無い。
- **API の本数**: 機能設計と基盤の設計は「C6 の 11 本の API」と書くが、契約 C6 と U4 の実装の API は 10 本（今の状態・プレビューの取得・投入・破棄・スキーマの読み込み・プレビュー中のダウンロード・適用・適用中のダウンロード・履歴・履歴からの戻し）である。一括の確かめ（`DslAccessControlIT`）と dslApi は、この 10 本をすべて扱う。差を `code-summary.md` に記録する。
- **一括の確かめはバックエンドのテストだけ**（機能設計の BR8.1a）。本番のバックエンドのコードは足さない・変えない。
- **画面の骨組みは書き換えない**（機能設計の BR1.1）。機能の登録は `frontend/src/features/dsl/registration.ts` の名前付きの `registration` だけで行う。既存の登録の仕組み（`frontend/src/features/admin/` の例）に合わせる。
- **make-you-chic-ui に無い部品**（ファイルの選択・違いの表の行の開閉・メニューの木）は frontend の側で作る。`vendor/make-you-chic-ui` は変えない。
- **画面の時間（NFR1.18〜NFR1.20）** は Build and Test の E2E で測る。この段では、描き方（開いた行だけ・先頭 100 件だけ・`File.size` で読む前に判定）を画面部品のテストで確かめる。
- **既存の決まり**: フロントエンドの Prettier・oxlint・ESLint・Stylelint・`tsc` の `strict`、名前付きのエクスポートだけ（`export default` を使わない）、`enum` を使わず文字列リテラルの union、CSS は部品と同じ場所に素の CSS、`react/no-danger`、テストは対象と同じ場所の `*.test.ts(x)`（Vitest・Testing Library・user-event・vitest-axe、部品ごとにアクセシビリティ検査1件）、ソースの先頭に `/* ... */` の Apache License 2.0 のヘッダー、フロントエンドのカバレッジの下限（行 80%・分岐 70%、`@vitest/coverage-v8` の `thresholds`）。

## 3. 作るもの

| 置き場 | 部品 | 役割 |
|---|---|---|
| `frontend/src/shared/api-client/` | ApiClient の拡張 | エラーの応答に Problem Details の本文全体（`problem`）を足す。既存の `apiFetch`・`apiRequest` の呼び方と `ApiResponseError` の `kind`・`status`・`code` は変えない（BR2.6・BR2.7）。ダウンロードのためにバイト列と `Content-Disposition` を受け取る口が無ければ足す |
| `frontend/src/features/dsl/` | `registration.ts`（`featureId: 'dsl'`、`/admin/dsl`、`layout: 'SHELL'`・`access: 'ADMIN'`、サイドバーの項目 `order: 210`、`messages` の ja・en） | 機能の登録（BR1.1・BR6.1〜BR6.3、NFR9.1） |
| 同上 | `api/dslApi.ts`・`api/types.ts` | C6 の 10 本を呼ぶ関数と応答の型（BR2.1〜BR2.5・BR2.8） |
| 同上 | `DslAdminPage`・`DslStatusPanel`・`DslTabs`・`DslPreviewPanel`・`DslDiffTable`・`DslMenuTree`・`DslWarningList`・`DslSubmitForm`・`DslErrorList`・`DslHistoryTable`・`DslConfirmDialog` と、それぞれの CSS | 画面の部品（`frontend-components.md` の 1.1、interaction-spec.md） |
| 同上 | 画面の状態と読み直しの仕組み（DslAdminPage の中、古い応答で上書きしない） | BR3.1〜BR3.3・BR4.1・BR4.2 |
| `backend/src/test/java/cherry/mastersmith/dslmanage/web/` | `DslAccessControlIT` | 10 本の API の未認証 401・管理者でない 403（アクセス拒否の監査の出来事つき）・管理者の成功と、401・403 でプレビューと履歴が変わらないこと（BR8.1・BR8.1a、AC6.2.1〜AC6.2.3） |

## 4. 手順

各層で実装を書き、同じ Bolt の中でその層のテストを書いて実行し、通ってから次の層へ進む（test-after、Testing Contract の `ordering`）。

### Step 1: 骨組み

- [x] `frontend/src/features/dsl/` の置き場を作り、既存の機能（`frontend/src/features/admin/`）の登録・画面・テストの形と、`frontend/src/features/README.md` の決まりを確かめる
- [x] 対応するストーリー: US6.2

### Step 2: ApiClient の拡張 — 実装とテスト

- [x] 2節・3節の拡張。既存のテストは変えずに通す。足した口のテスト（Problem Details の本文を渡す、JSON でない・Problem Details でない本文では `problem` を持たない、ダウンロードの受け取り）
- [x] `npm --prefix frontend run test -- src/shared/api-client` を実行し、通す
- [x] 対応するストーリー: US2.2（誤りの一覧の本文）、NFR3.10

### Step 3: dslApi と型 — 実装とテスト

- [x] `dslApi`（10 本）と応答の型。投入は `Content-Type: application/yaml` で本文を送り、`source` を付ける。適用は表示中の `previewId` を付ける。誤りの一覧の本文は `total`（1以上）と `errors` があるときだけ誤りの一覧として扱う（BR2.8）
- [x] 単体テスト（各関数の要求の形、成功と失敗の値、形の合わない誤りの本文、ダウンロードのファイル名を `Content-Disposition` から取り、無ければ `dsl.yaml`）
- [x] `npm --prefix frontend run test -- src/features/dsl` を実行し、通す
- [x] 対応するストーリー: 各ストーリーの API の呼び方、BR2.1〜BR2.5・BR2.8

### Step 4: 表示の部品 — 実装

- [x] `DslStatusPanel`（識別は先頭 12 文字、全体をツールチップと読み上げで、BR5.10。日時は UTC を端末の時差で時差の略号つき、BR6.3）
- [x] `DslPreviewPanel`・`DslDiffTable`（既定は違いのあるテーブルだけ、「すべて表示」、区分はバッジの文字、開いた行のカラムだけを描く、`aria-expanded`、BR5.7、NFR1.19）・`DslMenuTree`（表示言語の表示名、物理名は訳さない、既定で1段目だけを開く、BR5.9）・`DslWarningList`（種類ごとの見出し、照合できなかったことを先頭に、色に加えて記号と文言、BR5.8・BR7.3）、表示名の未設定の先頭 100 件と総数（BR5.6）
- [x] `DslErrorList`（件数を先頭に、先頭 100 件だけを表で、残りの件数、危険な形・版の非対応で1件だけのときは理由の1件だけ、行・列・場所が無ければ「—」、件数の警告へフォーカスと読み上げ、BR5.1〜BR5.4、NFR1.21）
- [x] `DslHistoryTable`（新しい順、適用中の印、戻し、適用中のダウンロード）
- [x] サーバーから来る文字列はすべて文字として描く（NFR3.9）
- [x] 対応するストーリー: US3.1（AC3.1.1〜AC3.1.7）、US3.2（AC3.2.1〜AC3.2.4・AC3.2.6）、US2.2（AC2.2.9）、US5.1・US5.2 の画面の部分、US6.2（AC6.2.5・AC6.2.6）

### Step 5: 表示の部品 — テスト

- [x] 部品ごとの振る舞いのテストと vitest-axe の検査1件（違反0件）。`<script>` を含む表示名・コメント・誤りの文言が文字のまま出る。英語の表示で物理名と識別が訳されない。違いの表で開いていない行のカラムが描かれない。誤りが 101 件以上のとき先頭 100 件だけを描き総数と残りを示す
- [x] 単位のフロントエンドのテストのコマンドを実行し、通す

### Step 6: 入力と確かめる表示の部品 — 実装

- [x] `DslSubmitForm`（ファイル・貼り付けの切り替え、入力が無ければ投入を使えなくし案内、ファイルは `File.size` で 10MB を読む前に判定し超えたら読まずに案内、貼り付けは UTF-8 のバイト数で判定、上限の内のファイルはテキスト（UTF-8）として読んで送る、JSON Schema のリンク、BR2.2・BR2.3、NFR1.20・NFR3.11）
- [x] `DslConfirmDialog`（置き換え・適用・破棄。置き換えでは今のプレビューの出どころ・置いた人・日時、適用では違いの件数と照合の警告の件数。はじめのフォーカスは「やめる」、閉じると開く前のボタンへ戻す、背景のクリックでは閉じない、BR7.1）
- [x] `DslTabs`
- [x] 対応するストーリー: US2.1（AC2.1.4・AC2.1.5）、US1.1（AC1.1.6）、US3.3（AC3.3.1）、US4.1（AC4.1.2）、US5.1（AC5.1.4）

### Step 7: 入力と確かめる表示の部品 — テスト

- [x] 部品ごとの振る舞いのテストと vitest-axe の検査1件。10MB ちょうどは送れ、1 バイト超えは読まずに案内（読み込みの関数が呼ばれない）。貼り付けの UTF-8 のバイト数での判定（日本語を含む）。JSON Schema のリンクの `href` が固定の同じオリジンのパス。確かめる表示のフォーカスの決まりとやめたときに何も送らないこと
- [x] 単位のフロントエンドのテストのコマンドを実行し、通す

### Step 8: 画面と登録 — 実装

- [x] `DslAdminPage`（画面を開いたら今の状態とプレビューを同時に要求、4節の手順、読み直しの決まり、古い応答で上書きしない、処理中のボタンの無効、読み上げの領域、409 の置き換え・破棄の見分け、503 の設定が無い・接続できない・`DSL_BUSY` の見分け、404 の知らせと読み直し、知らない code は状態コードの種類の一般の文言で `detail` を出さない、BR3.1〜BR3.3・BR4.1・BR4.2・BR5.5・BR7.2、NFR1.18・NFR5.6）
- [x] `registration.ts` と `messages`（すべての鍵に ja・en、`dsl.error.<code>`（`DSL_BUSY` を含む 9 つ）・`dsl.errorKind.<kind>`・`dsl.warningKind.<kind>`・`dsl.source.<source>`・`dsl.change.<change>`、BR6.1・BR6.2、NFR9.1）
- [x] 対応するストーリー: US6.2（AC6.2.4・AC6.2.5）、画面の部分のすべての操作

### Step 9: 画面と登録 — テスト

- [x] `DslAdminPage` の流れのテスト（dslApi を差し替える）: 開いたときの表示と空の状態の案内、読み込み・投入・戻しの成功（Toast・タブの切り替え・読み直し・フォーカス・入力を空に）、422（誤りの一覧・入力を残す・件数へフォーカス、戻しは履歴のタブ）、503 の2種類と `DSL_BUSY`（読み直さない・入力を残す）、適用の成功と 409 の置き換え・破棄の見分け（自動で適用しない）、破棄の成功と 404、ダウンロードの 404、古い応答で上書きしない、英語の表示、vitest-axe の検査1件
- [x] 登録の検査（既存の登録の検査で ja・en の欠けが無い、`access: 'ADMIN'`、サイドバーの `order: 210`）
- [x] 単位のフロントエンドのテストのコマンドを実行し、通す

### Step 10: アクセス制御の一括の確かめ（バックエンドの結合テスト）

- [x] `DslAccessControlIT`（3節）。10 本の API を1つの一覧にし、未認証 401・管理者でない 403（アクセス拒否の監査の出来事が記録される）・管理者の成功（各 API の成功の状態コード。事前のデータが要る API はテストの中で用意する）を確かめ、401・403 で内部DB のプレビューと履歴が変わらないことを確かめる。既存の補助（`access/testsupport/`・`auth/testsupport/`・`audit/testsupport/`・`dslmanage` の `testsupport`）を使う
- [x] `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.dslmanage.web.DslAccessControlIT'` を実行し、通す
- [x] 対応するストーリー: US6.2（AC6.2.1〜AC6.2.3）、BR8.1・BR8.1a・BR8.2

### Step 11: 1コマンドの検査と文書

- [x] `README.md` の DSL の節に、管理画面（`/admin/dsl`）の使い方の要約と JSON Schema のリンクの場所を足す
- [x] `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` をコンテナの実行環境が動いている状態で実行し、すべての段（フロントエンドのフォーマット・リンタ・型・テスト・カバレッジ・ビルドを含む）が通ることを確かめる

### Step 12: 記録

- [x] `code-summary.md`、`source-manifest.json`、`traceability.json` を作る（コード生成の段の手順 5）。`code-summary.md` には、承認済みの文書との差（10MB・`DSL_BUSY`・JSON Schema のリンク・API の本数）と、Build and Test への申し送り（NFR1.18〜NFR1.20 の E2E での測定、E2E の代表の流れに DSL の管理画面を足すか）を載せる

## 5. ストーリーと手順の対応

| ストーリー | 受け入れ基準（U5 の部分） | 手順 |
|---|---|---|
| US6.2 | AC6.2.1〜AC6.2.6 | Step 4〜10 |
| US1.1・US2.1・US2.2 | AC1.1.6〜AC1.1.8 の画面、AC2.1.1〜AC2.1.6 の画面、AC2.2.9 | Step 2〜9 |
| US3.1〜US3.4 | AC3.1.x・AC3.2.x・AC3.3.x・AC3.4.x の画面 | Step 3〜9 |
| US4.1・US4.2 | AC4.1.2、AC4.2.1・AC4.2.2 の画面 | Step 6〜9 |
| US5.1・US5.2 | AC5.1.x・AC5.2.x の画面（AC5.1.4 の置き換えの確かめを含む） | Step 4〜9 |

時間の目標（NFR1.18〜NFR1.20、AC3.2.5 の画面の分）は Build and Test の E2E で測る。

## 6. テストの量（Standard）

| 部品 | 画面部品・単体のテスト | 結合 |
|---|---|---|
| ApiClient の拡張 | 3〜5 件 | — |
| dslApi と型 | 6〜8 件 | — |
| 表示の部品（6つ） | 部品ごとに 3〜6 件と vitest-axe 1 件 | — |
| 入力と確かめる表示の部品（3つ） | 部品ごとに 4〜6 件と vitest-axe 1 件 | — |
| 画面と登録 | 10〜14 件と vitest-axe 1 件 | — |
| アクセス制御の一括の確かめ | — | `DslAccessControlIT`（10 本 × 3 通りを一覧で） |

## Testing Contract

```json
{
  "version": 1,
  "methodology": "test-after",
  "source": "team",
  "ordering": "テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。",
  "scope": "classic",
  "test_strategy": "standard",
  "project_type": "brownfield",
  "applicable_notes": [
    {
      "layer": "org",
      "text": "We treat tests as a first-class deliverable in every Bolt. The specific\nmethodology (TDD, BDD, ATDD, or classic test-after) is affirmed at\npractices-discovery and recorded in `team.md` under this heading with explicit\n`Methodology` and `Ordering` fields; Code Generation resolves those fields\nindependently from coverage, tooling, and scope notes.\n\nWhen no posture has been affirmed, our default per scope is:\n- **Methodology**: test-after\n- **Ordering**: implement each applicable testable layer, then write and run\n  that layer's tests.\n- `mvp`, `enterprise`, `feature`, `infra`, `classic` add an 80% line-coverage\n  floor and CI execution before merge.\n- `bugfix`, `security-patch` add a targeted regression for the specific\n  bug/vulnerability and require the existing suite to remain green.\n- `express` uses the Minimal strategy: requirement-driven unit tests (one per\n  requirement, with a happy-path floor per component); existing tests remain\n  green.\n- `poc`, `refactor`, `workshop` add no extra new-test floor and require the\n  existing suite to remain green.\n\nThe active `Test Strategy` still applies in every scope and determines test\nvolume/types. Scope floors are additive; they never reduce or replace the\nselected strategy.\n\nBuild and Test verifies defined coverage floors and affirmed quality targets;\nthey may not be weakened to make a step pass.\n\nAffirm a stricter posture in `team.md` if the team commits to one."
    },
    {
      "layer": "team",
      "text": "- **Methodology**: test-after\n- **Ordering**: テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。\n- テストは各 Bolt の成果物の一部であり、テストのない機能は完成とみなさない。テスト量はワークフローの Test Strategy に従い、以下の下限はそれに追加される。\n- カバレッジの下限は、すべての Intent に共通で **行カバレッジ 80% 以上、分岐カバレッジ 70% 以上** とする。バックエンド・フロントエンドの両方に適用し、下回ったらビルドを失敗させる。道具はバックエンドが JaCoCo、フロントエンドが `@vitest/coverage-v8`（`thresholds` 設定）。\n- バックエンドでは、全体の合計に加えて、すべてのパッケージごとにも同じ下限（行 80%・分岐 70%）を当てる。ただし、既存のパッケージに単独で下限を下回るものがあれば、パッケージごとの下限は新しく作るパッケージだけに当てる（既存のパッケージは全体の合計で判定する）。どちらになるかは、パッケージごとの下限を入れる Bolt で既存のパッケージのカバレッジを実測して決め、結果を記録する。\n- カバレッジの計測から外すのは、アプリの起動クラス、設定値だけのクラス、自動生成コード、`vendor/` 配下に限る。除外を後から増やして実質的に下限を下げることはしない。パッケージごとの下限を満たすために除外を増やすこともしない。\n- DB を使うテストは、本番と同じ種類の DB をコンテナで起動して使う（Testcontainers）。そのため、ローカルと CI にコンテナの実行環境があることを前提条件として文書化する。テストごとにデータを用意して巻き戻し、実行順に依存させない。表を作る・消す操作（DDL）がその場で確定して巻き戻せない DB（MySQL・MariaDB など）で表を作るテストは、巻き戻す代わりに、テスト（またはテストのクラス）ごとに名前の重ならないスキーマ（MySQL・MariaDB ではデータベース）を作り、終わったら消す。\n- 対象DB（MySQL・MariaDB・PostgreSQL）の結合テストをどこで実行するかは、Build and Test で `./gradlew verify` の時間と colima の VM のメモリを実測してから決める。決まるまでは、3種類すべてを `./gradlew verify` の中で毎回実行する（CI も同じ）。コンテナの実行環境が無いときの扱いは Way of Working のとおり。\n- 画面からの一連の操作を確かめるテスト（E2E）は、代表的な流れ1〜2本に絞って入れる（まずは「ログイン → 管理画面に入れるか → ログアウト」）。道具は設計のステージで決める。\n- 入力と出力の性質をランダムな入力で確かめるテスト（性質ベースのテスト）を、純粋な関数（ロック判定の回数計算、有効期限の判定、入力の検証など）に一部適用する。道具は Java が jqwik、フロントエンドが fast-check。失敗時の乱数の種を記録して再現できるようにする。\n- テストの説明文（テスト名、`describe` / `it`、`@DisplayName`）は英語で書く。テストデータは日本語でよい。\n- フロントエンドのテストは対象と同じ場所に `*.test.ts` / `*.test.tsx` として置き、Vitest ＋ Testing Library（jsdom）＋ user-event ＋ vitest-axe を使う。画面部品ごとにアクセシビリティ検査を1件入れる。\n- Java のテストは `src/test/java` に、対象と同じパッケージ構成で置く。単体テストは `XxxTest`、Spring や DB を起動する結合テストは `XxxIT` とし、分けて実行できるようにする。\n- 時刻に依存する処理（有効期限、ロックの解除など）は注入可能な時計（`Clock` 等）から現在時刻を取得し、テストで `sleep` や実時刻に依存しない。不安定なテストは放置せず、原因を直すまで統合しない。\n- 認証・認可・監査に関わる機能では、次のテストを必ず書く。★印の項目は要件（しきい値や動作）が未確定のため、要件定義で決めてからテストを書く。\n  - アカウントロック: 失敗回数のしきい値の境界（しきい値−1回ではロックされない／しきい値ちょうどでロックされる）、ロック中は正しいパスワードでも拒否、ログイン成功時の失敗回数の扱い。★しきい値、回数を数える期間、ロックの解除方法\n  - ログイン: 存在しないユーザーとパスワード誤りで、応答からユーザーIDの存在を推測できないこと\n  - トークン: 有効期限の境界（直前は有効／直後は無効）、署名の改ざん、署名方式の指定を悪用した改ざん（`alg: none` 等）、ログアウト後のリフレッシュトークンの拒否、ログアウト後もアクセストークンが有効期限まで使えること（決定済みの仕様として明示する）。★各トークンの有効期限の値、リフレッシュトークンを使うたびに作り直すか\n  - 初期管理者の自動作成: 2回目以降の起動で重複作成しない、設定が無い／不正なときの動作、パスワードがログに出ない。★設定が無いときに起動を止めるか\n  - 認可: 未認証（401）、管理者フラグなし（403）、管理者（200）をサーバー側のテストで確かめる（画面で管理メニューを隠すことはサーバー側の検査の代わりにしない）\n  - 監査ログ: 対象イベントごとに必須項目が記録されること、存在しないユーザーIDでのログイン失敗も記録されること。★監査ログの書き込みに失敗したときに操作を失敗させるか\n  - 秘密情報の漏えい: ログ・監査ログの出力にパスワード・トークンの値が含まれないこと\n  - 構造化ログ・分散トレース: 決めた形式で出る、トレースIDがログに含まれる、外部エクスポートが既定で無効であること\n  - 画面: ログイン画面のアクセシビリティ検査、ロック時のメッセージ表示、ログアウトでトークンが破棄されること\n- 利用者が投入する DSL（YAML）を読み込む機能では、Code Style の「信頼できない入力」の決まりを確かめる次のテストを必ず書く（認証・認可・監査と同じ扱い）。上限の具体的な数値は設計の段で決め、決めた値の境界で確かめる。\n  - 大きさ: 上限ちょうどは受け付け、上限を超えると拒否される\n  - 入れ子の深さ: 上限ちょうどは受け付け、上限を超えると拒否される\n  - 別名（アンカー）: 展開の数が上限を超えると拒否され、別名の展開の爆発で処理が止まらない\n  - タグ: 任意の型を作るタグ（`!!` など）を含む DSL は拒否され、型が作られない\n  - 重複キー: 同じキーが重なる DSL はエラーになり、後の値で黙って上書きされない\n  - JSON Schema の `$ref`: 外部の URL を取りに行かない\n  - 拒否の応答: Problem Details の形で返り、YAML・JSON Schema の部品の例外のメッセージを含まない\n\n- 内部DB（組み込みの H2）を使うテストは、コンテナではなく本番と同じ組み込みの H2 で行う。Testcontainers は、コンテナで動かす対象DB（後続 Intent D・E で扱う業務DB）のテストに使う。Walking Skeleton の「DB を使うテスト1件以上（本番と同じ種類の DB をコンテナで起動する）」も、内部DBについてはこの読み方とする。 (learned 2026-09-22)"
    },
    {
      "layer": "project",
      "text": "- テストの件数やカバレッジを報告するときは、`./gradlew verify` がテストのタスクを UP-TO-DATE で飛ばすことがあるため、`:backend:cleanTest :backend:cleanIntegrationTest` を付けて実行し直し、実測の数字だけを報告する。 (learned 2026-09-23) \n- 負荷の環境や配備先が決まらないと測れない目標（応答時間のパーセンタイル、運用の指標、ファイルの権限など）は、Build and Test で `Unverified` とし、持ち主の段（performance-validation・observability-setup・deployment-execution）を明記して引き継ぐ。目標を緩めて「満たした」ことにはしない。 (learned 2026-09-23) \n- 負荷の試験は、配備した環境とは別の使い捨ての環境（仮の署名鍵・仮の利用者、終わったら消す）で行い、本物のデータと監査ログを汚さない。手順は perf/README.md。 (learned 2026-09-23) \n- 負荷の試験で、アプリが止まる・極端に遅いなどの結果が出たときは、環境を起動し直して再現させ、原因をログと状態（OOMKilled など）で確かめてから記録する。 (learned 2026-09-23) \n- 同時の重なりを確実に作るため、本番のコードを変えずに、監査の書き込みの時間を測る LongSupplier（AuditEventListener で2本目を借りる直前に呼ばれる）をテストで差し替えて待ち合わせる方式にした。既存の LoginConcurrencyIT は 8 スレッドでプールの 10 に届かず、前回の失敗のログインで尽きなかった理由の1つと見られる。 (learned 2026-09-23) \n- Intent の流れに Performance Validation の段が無く、負荷の環境（使い捨ての環境）を手元で用意できるときは、k6 の試験と NMT の測定の持ち主を Build and Test とし、Unverified で引き継がずにその段で実行する。 (learned 2026-09-23) \n- 修正の前の設定（例: 上限 1g）も修正の後の環境（例: CPU 4 の VM）で流し（pre1g）、要件の前提（VM を上げても F3 が起きる）を実測で裏付ける。 (learned 2026-09-23)"
    }
  ],
  "obligations": {
    "strategy": "standard",
    "strategy_volume": [
      "Five to eight tests per component.",
      "Unit tests plus integration tests for key boundaries.",
      "Add E2E, performance, or security tests when requirements demand them."
    ],
    "scope_floor": [
      "Keep the existing test suite green.",
      "This scope adds no extra new-test floor beyond the selected test strategy."
    ],
    "combination_rule": "Apply every selected-strategy obligation and every scope-floor obligation; neither replaces the other, and a targeted scope regression may add the narrowest necessary test type beyond the strategy default."
  },
  "plan_profile": {
    "methodology": "test-after",
    "runner_step": "Verify the existing test runner/configuration and record the exact unit-scoped command.",
    "runner_ready_before_first_test": true,
    "testable_layers": [
      "Data model / database behavior",
      "Repository / data access",
      "Business logic",
      "API / endpoint",
      "Frontend behavior"
    ],
    "steps": [
      "Project structure and production configuration skeleton.",
      "Verify the existing test runner/configuration and record the exact unit-scoped command.",
      "Data model / database behavior - implement.",
      "Data model / database behavior - write and run its tests after implementation.",
      "Repository / data access - implement.",
      "Repository / data access - write and run its tests after implementation.",
      "Business logic - implement.",
      "Business logic - write and run its tests after implementation.",
      "API / endpoint - implement.",
      "API / endpoint - write and run its tests after implementation.",
      "Frontend behavior - implement.",
      "Frontend behavior - write and run its tests after implementation.",
      "Environment/build configuration.",
      "Documentation and traceability."
    ]
  },
  "input_sha256": "sha256:2adf2e19e08e8d9f09279453393e762919beea4d6d9d5d13b89e72c1fffeed06",
  "contract_sha256": "sha256:e0a9abee7ec1245e67d5b55b468219a36caaa126e29794fd62a14795c277380c"
}
```


Testing Contract の `plan_profile.steps` との対応: 骨組みは Step 1、API（ApiClient の拡張と dslApi）は Step 2・3、画面の振る舞い（表示・入力・確かめる表示・画面と登録）は Step 4〜9、サーバー側のアクセス制御の一括の確かめは Step 10、環境とビルドの設定（検査）は Step 11、記録は Step 12。U5 は ui の単位で、DB と業務処理の層を持たない。テストの実行の準備（`plan_profile` の2つ目）は既存の Vitest と Gradle の設定を使い、`unit-test-instructions.md` にコマンドを記す。
