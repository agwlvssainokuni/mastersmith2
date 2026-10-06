# 基盤の設計の質問 — U6 role-admin-ui

単位 U6 role-admin-ui（kind: ui。B8 で作る）の基盤の設計の前に、決まっていない点を確かめます。作る成果物は、段の定義の `produces_kinds` により `infrastructure-specification.md`・`monitoring-design.md`・`cicd-pipeline.md`・`traceability.json` の4つです。

読んだもの:

- この単位の承認済みの NFR 設計: `nfr-design/performance-design.md`・`security-design.md`・`logical-components.md`・`traceability.json`。各ファイルの末尾の「承認の場の決定と直し」も読みました。ui の単位のため、scalability・reliability・observability の設計はありません。
- NFR 要件（`nfr-requirements/`）、機能設計（`functional-design/functional-spec.md`・`frontend-components.md`）
- `inception/domain-design/components.md`、`inception/contract-design/contract-summary.md`（C2・C6・C7）、`inception/delivery-planning/bolt-plan.md`（B8）
- group・role・navigation の基盤の設計（読み取りだけ）
- 既存のもの（読み取りだけ）:
  - CI とビルド: `.github/workflows/ci.yml`、`build.gradle.kts`（`verify`・`e2eTest`）、`frontend/package.json`
  - 画面の設定: `frontend/vite.config.ts`、`frontend/vitest.config.ts`、`frontend/playwright.config.ts`
  - E2E: `frontend/e2e/`（010〜130 と `support/`）、`frontend/scripts/check-bundle-size.mjs`、`frontend/src/main.tsx`
  - 配備と監視: `compose.yaml`、`docker/monitoring/`
- `team.md`・`project.md`

## 決まっていること（質問にしない）

### 配備と基盤の範囲

- 画面は、既存のとおりビルドの結果（`frontend/dist`）を実行可能 WAR に同梱し、同じオリジンで配ります。CORS の設定は置きません（`team.md` の Code Style）。
  - 書き出しの応答のヘッダー `X-Role-Transfer-Exceeds-Import-Limit` は同じオリジンのため、`Access-Control-Expose-Headers` は要らず、`ApiDownload` の `headers` で読めます（`logical-components.md` 3節 L10）。
- 次のものは変えません。
  - バックエンド・内部DB・接続プール・`application.yaml`・`compose.yaml`・`Dockerfile`・`.env.example`
- 新しい npm の依存は足しません（NFR6.5）。`package.json`・`package-lock.json` も変えません。data router と `useBlocker` は今の react-router（8.x、MIT）を使います。
- 新しい秘密も足しません。
- 戻しは直前の版のイメージだけで行います。
  - 前の版には U6 の画面が無く、サイドバーの項目も出ません。
  - 戻したときの DSL とロールの扱い（版 2 の DSL が読めず DSL が無い状態で起動する、管理の可否は `users.admin_flag` のまま）は、dsl-v2 と U3・U4 の配備の段への引き継ぎにまとめます（role の読み直しの R-03・R-04）。
  - U6 の成果物には「前の版には画面が無い」ことだけを書きます。

### 画面の入口の変更（設計で決まっている）

- `frontend/src/main.tsx` を `BrowserRouter` から `createBrowserRouter`＋`RouterProvider` に替えます。今の `App` を1つの道（`path: '*'`）で包み、表示の設定の受け渡しと `StrictMode` は保ちます（`logical-components.md` 3節 L11）。
  - B8 の計画の最初の手順で、差し替えの後に次の2つを確かめます。成り立たなければ戻して、機能設計 7.3 の形にします。
    - 既存の画面のテストと E2E（010〜130）が変わらないこと
    - `useBlocker` がサイドバーの移動で止まること
  - 深い道（`/admin/roles/…` など）を開き直したときに `index.html` を返す既存のサーバー側の配信は変えません。
- 新しい3つの画面と `UserRolesDialog` は遅延読み込み（`lazy`）にします。
  - 入口の JavaScript の量の確かめは、`verify` の段 9 の既存の `frontendBundleSize`（`check-bundle-size.mjs`、500KB で警告だけ）のまま、台本を変えません。
  - Build and Test で、入口の大きさが B8 の前（develop の B7 の統合の後）と比べて増えていないことと、遅延読み込みの3つの塊の gzip の大きさを、`dist/.vite/manifest.json` から読んで記録します（`performance-design.md` 5節）。

### CI と1コマンドの検査（既にあるものの記録）

- CI・Gradle のタスク・Vitest と Playwright の設定（`include`・`testMatch`・`workers: 1`・`retries: 0`）は変えません。
- 画面のテスト（`*.test.ts(x)`、対象と同じ場所）は `verify` の段 5（`frontendTest`）で、カバレッジは段 7（全体の合計で行 80%・分岐 70%、計測から外さない）で、毎回流れます。
  - fast-check の性質ベースのテスト（`changePaging.test.ts`・`permissionDraft`）も段 5 に入ります。
  - `useBlocker` を使う部品は `createMemoryRouter` で描きます（NFR6.9）。
  - 承認済みの設計に、名前の振り分けに当たらないテストはありません（group の読み直しの R-01 の手当て。Vitest は `src/**/*.test.{ts,tsx}` のすべてを流す）。
- ESLint の機能どうしの import の制限（U1）は、段 2 で当たります。共有に置くのは `shared/validation/validateAdminName.ts`（新しい）と `shared/api-client/apiClient.ts` の `ApiDownload` の `headers`（互換の変更）だけです。
- 統合は B8 の短命のブランチから `develop` へ squash で戻します。

### E2E（verify と CI の外）

- E2E は意図して `verify` と CI の外に置いています。代わりに、統合の前とリリースの前に手元で流します（`team.md` の Testing Posture）。前提は既存のとおりで、次の2つを済ませておきます。
  - `npx playwright install chromium`
  - `docker compose --profile mail up -d mailpit`
- 足すファイルは2つです。番号はコード生成の計画で確定します（`logical-components.md` 6節、読み直しの R-08）。
  - **140（案）U6 の画面の検査**:
    - 実際のブラウザの axe（表示の設定の 20 組）
    - 幅 360・768・1280 px（既定の1組）
    - 開いた Dropdown のはみ出し
    - 画面の時間の測り（既定の1組で、`performance-design.md` 4節）
    - 検査の本数には数えません（`project.md` の読み方）。
  - **150（案）F の流れ**:
    - この Intent の2本までの代表の流れの1本です（`team.md` の Testing Posture）。B9 が同じファイルに後半を足します。
    - 始めに自分で版 2 の DSL（名前の接頭辞 `E2EF_`）を管理の API で投入・適用します。対象DB が無くても、照合の警告のまま適用できます（`040-dsl-admin.e2e.ts` の前提と同じ）。
    - 作った利用者（招待から登録まで）と一意の名前のロールだけを対象にし、初期管理者は変えません。後始末はしません。
  - app-frame-ui は 160・170 の案です。I の流れの見本の名前は `E2EF_` と重ねません。
  - E2E は1つの WAR と内部DB を全ファイルで共有し、番号の順に1本ずつ流します。150 の後に適用中の DSL が `E2EF_` のものに替わることは、後のファイル（160・170）が自分で DSL を適用し直すことで扱います。
- 差し替えの口 `frontend/e2e/support/adminApiRoute.ts` に、直列化済みの本文（`body: string`）を受ける形を足します。今の `json` の形は残し、既存の 120・130 は変えません（承認の場の直し R-01）。
  - 差し替えの見本は1つにまとめ、画面の型を付けます。150 の流れで、本物の応答と項目の名前・型が一致することを毎回確かめます（NFR6.10）。
- E2E の報告の秘密の確かめ（`playwright-secret-check-reporter.ts`、`e2e/support/secretValues.ts`）は今のまま当たります。
  - 仮の資格情報はプロセスの環境変数で渡し、`webServer.env` に置きません（`project.md` の学び）。
  - 試験のデータは予約のドメインだけにします。
- B8 は画面と認可に関わるため、統合の前に手元で `./gradlew e2eTest` の全体（010〜150）を流します。
  - E2E の時間は 20 組 × 画面の状態と、測り（5 回）の分で延びます。目標の数値は置かず、Build and Test で記録します。

### 監視

- 画面の側の監視（利用者のブラウザの誤りや時間を集める仕組み）は、今までの画面と同じく置きません。
- U6 が呼ぶ API の時間・5xx は、group・role の監視（ダッシュボードの区画、group の Q1 A）で見ます。
- 画面の時間は、手元の1台・1つのブラウザで測って記録するだけです。判定は 5 回の中央値で、目標を超えたら Build and Test が `Not Met` と値で記録し、承認の場で扱いを決めます（`performance-design.md` 4.4・7節）。
- SLO は U6 で新しく置きません。

## 質問

この単位で新しく決める論点はありません（`project.md` の Way of Working「Construction の設計の段で、単位に新しく決める論点が無いときは、質問を作らず、設計の要点を要約として依頼者に確認する」）。次の設計の要点を要約として確かめ、Looks correct / Request changes で進めます。

## 設計の要点（要約の確認に使う）

1. **作る成果物**: 4つです。
   - `infrastructure-specification.md`: 配信の形と変えないもの、共有の変更 L10・L11
   - `monitoring-design.md`: 画面の監視を置かないこと、API の監視は group・role の区画で見ること、画面の時間の記録
   - `cicd-pipeline.md`: verify の段への対応づけ、E2E の 140・150、差し替えの口、入口の大きさの記録
   - `traceability.json`
2. **基盤の変更なし**: バックエンド・CI・Gradle・Vitest と Playwright の設定・依存・秘密・compose は変えません。変えるのは画面のコードと、`main.tsx` の入口、`apiClient.ts` の `ApiDownload`、E2E の差し替えの口だけです。
3. **入口の差し替えの確かめ**: B8 の最初に、data router への差し替えで、既存の画面のテストと E2E が変わらないことと、`useBlocker` が止まることを確かめます。成り立たなければ戻します。
4. **E2E**: 140（検査と測り。本数に数えない）と 150（F の流れ。自分で `E2EF_` の DSL を適用する）を足し、統合の前に全体を手元で流します。番号はコード生成の計画で確定します。
5. **量と時間**: 入口の JavaScript が増えていないことと遅延読み込みの塊の大きさ、画面の時間（5 回の中央値）、E2E の時間を Build and Test で記録します。目標は緩めません。
6. **戻し**: イメージだけです。戻したときの DSL とロールの扱いは、dsl-v2 と U3・U4 の配備の段への引き継ぎにまとめます。

## Consolidated Summary Confirmation

答えのまとめ（role-admin-ui の基盤の設計）: 質問は無く、上の「設計の要点」1〜6 のとおりとする（バックエンド・CI・Gradle・Vitest と Playwright の設定・依存・秘密は変えない、`main.tsx` を data router に替え B8 の最初に既存のテストと E2E と `useBlocker` を確かめる、新しい画面は遅延読み込みで入口が増えていないことを記録、E2E の 140・150 を足し `adminApiRoute.ts` に `body: string` の形を足し統合の前に全体を手元で流す、量と時間は Build and Test で記録し目標は緩めない、戻しはイメージだけ）。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
