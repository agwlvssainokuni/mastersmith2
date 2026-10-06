# CI/CD Pipeline — U6 role-admin-ui

U6 の検査の流れ（CI と1コマンドの検査）、E2E（140・150 の案）、入口の差し替えの確かめ、量と時間の記録、統合、戻し方、B8 で確かめることを示します。

U6 は画面だけの単位で、B8 で作ります。新しい依存・設定・秘密は足さず、CI・Gradle・Vitest・Playwright の設定と、1コマンドの検査の段・関門は **変えません**。この文書は既にある仕組みの記録として、どの段で何を確かめるかを書きます。配備先は開発者の PC 上のコンテナだけです（`aidlc/spaces/default/memory/project.md` の Deployment）。

## 出典

- 答え: `infrastructure-design-questions.md`（質問なし、設計の要点 6 件、まとめの確認は Looks correct）
- 上流（どれも `aidlc/spaces/default/intents/261004-role-menu/` の下）:
  - `construction/role-admin-ui/nfr-design/performance-design.md`（2〜6節・9節）・`security-design.md`（1〜6節）・`logical-components.md`（1節・3節・6節）
  - `construction/role-admin-ui/nfr-requirements/tech-stack-decisions.md`（NFR4.4・NFR4.5・NFR6.3〜NFR6.10）
  - `construction/role-admin-ui/functional-design/functional-spec.md`・`frontend-components.md`
  - `inception/domain-design/components.md`
  - `inception/contract-design/contract-summary.md`（C2・C6・C7）
  - `inception/delivery-planning/bolt-plan.md`（B8）
- 既にある仕組み（正とする。読むだけ）:
  - `.github/workflows/ci.yml`
  - `build.gradle.kts`（`verify` の段、`e2eTest`）
  - `frontend/package.json`
  - `frontend/vitest.config.ts`（`include: ['src/**/*.test.{ts,tsx}']`、`thresholds` 行 80%・分岐 70%）
  - `frontend/playwright.config.ts`（`testMatch: '**/*.e2e.ts'`、`workers: 1`、`retries: 0`、報告の秘密の確かめ）
  - `frontend/e2e/`（010〜130 と `support/`）
  - `frontend/scripts/check-bundle-size.mjs`

## 1. CI（GitHub Actions）

`.github/workflows/ci.yml` は変えません。

| 項目 | 今の形 | U6 での扱い |
|---|---|---|
| きっかけ | `develop` へのプッシュ・`v*` のタグ・手動 | 変えない |
| 実行 | `./gradlew verify`（制限時間 60 分） | 変えない。U6 の画面のテストは段 5・7 に入る |
| 依存の入れ方 | lockfile どおり（`npm ci`、`ignore-scripts=true`） | 変えない。U6 は `package.json`・`package-lock.json` を変えない（NFR6.5） |
| 秘密 | 使わない | 変えない |
| 成果物 | WAR（`dist` を同梱）をコミットのハッシュの名前で保存 | 変えない。`dist` に遅延読み込みの塊が増える |

CI が失敗したときは、`team.md` の Testing Posture「不安定なテストと CI の失敗」の決まりで扱います。

## 2. 1コマンドの検査（`./gradlew verify`）の段と関門

| 段 | U6 で確かめること | 関門（失敗の条件） | 当たる要件 |
|---|---|---|---|
| 0 準備 | make-you-chic-ui のビルド（固定先 e82b651 のまま。U6 は固定先を更新しない） | 既存のとおり | — |
| 1 フォーマット | 足す画面のファイルと E2E のファイル（Prettier） | 書式の違い | NFR1.12 |
| 2 リンタ | oxlint のセキュリティ系の決まり（`react/no-danger` など）、ESLint（機能どうしの import の禁止・`export default`・`enum` の禁止・react-hooks）、Stylelint を、決まりを緩めずに通す | 1件でも error | NFR1.7・NFR1.12 |
| 3 ライセンスヘッダー | 足すファイルの先頭のヘッダー（`check-license-header.mjs`） | ヘッダーが無い・形が違う | NFR1.12 |
| 4 ビルド | `tsc --noEmit`（`any` の禁止、各機能の `api/types.ts`、見本の型）と Vite のビルド（遅延読み込みの塊） | 型の誤り・ビルドの失敗 | NFR1.9・NFR6.10 |
| 5 単体テスト | 画面のテスト（`*.test.ts(x)`、部品ごとに vitest-axe を1件）。fast-check（`changePaging.test.ts`・`permissionDraft`・`reasonMessage.test.ts`）、偽の時計の待ちの案内、古い答えを捨てる、`createMemoryRouter` で描く `useBlocker`、`TransferFilePicker`（10,485,760 は送る・10,485,761 は送らない）、`apiClient.download.test.ts`（`headers` を足す） | 1件でも失敗 | NFR1.1・NFR1.4〜NFR1.10・NFR2.10〜NFR2.12・NFR4.1〜NFR4.3・NFR4.6・NFR4.7・NFR6.1・NFR6.2・NFR6.6・NFR6.7・NFR6.9 |
| 7 カバレッジ | 全体の合計で行 80%・分岐 70%。U6 の機能を計測から外さない | 下回る。除外は足さない | NFR6.4 |
| 8 安全の検査 | OSV-Scanner の npm の関門（実行時の依存の High 以上・`MAL-`・成果物を作る道具の High 以上）を今のまま通す | 既存の基準 | NFR6.8 |
| 9 成果物 | `bootWar`（`dist` を同梱）、`frontendBundleSize`（入口の量、500KB で警告だけ） | WAR が作れない | NFR2.13（記録は 5節） |

補足:

- Vitest は `src/**/*.test.{ts,tsx}` のすべてを流すため、名前の振り分けに当たらないテストはありません（group の読み直しの R-01 の確かめ）。
- テストの時間の上限は、原因を確かめずに延ばしません。描画の後の値は `waitFor`・`findBy*` で待ちます（`team.md` の Testing Posture）。
- 段 6（結合テスト）は、U6 では足しません。サーバー側の必須のテストは U3・U4 が持ちます。

## 3. 入口の差し替えの確かめ（B8 の最初の手順）

| 確かめ | 方法 | 成り立たないとき |
|---|---|---|
| `main.tsx` を data router（`createBrowserRouter`＋`RouterProvider`、`path: '*'` で今の `App` を包む）に替えた後、既存の画面のテストが変わらず通る | 段 5 | 戻して機能設計 7.3 の形にし、差を記録する（`logical-components.md` 3節 L11） |
| 既存の E2E（010〜130）が変わらず通る | 手元の `./gradlew e2eTest` | 同上 |
| `useBlocker` がサイドバーの移動で止まる | 画面のテストと E2E の 140 | 同上 |

## 4. E2E（`./gradlew e2eTest`、verify と CI の外）

E2E は意図して `verify` と CI の外に置いています。代わりに、統合の前とリリースの前に手元で流します（`team.md` の Testing Posture）。前提は既存のとおりです。

- `npx playwright install chromium`
- `docker compose --profile mail up -d mailpit`（`e2eTest` は届かなければ失敗する）

足すファイルと口は次のとおりです。番号はコード生成の計画で、app-frame-ui（160・170 の案）と合わせて確定します（読み直しの R-08）。

| ファイル・口 | 中身 | 本数 | 出典 |
|---|---|---|---|
| 140（案）U6 の画面の検査 | 実際のブラウザの axe（表示の設定の 20 組、S3〜S7 と `UserRolesDialog` の状態）。幅 360・768・1280 px（既定の1組、画面全体の横のスクロールが無い、開いた Dropdown と Modal が画面の中に収まる）。画面の時間の測り（既定の1組、5 回の中央値。`performance-design.md` 4節）。API は差し替えの口で受け、書き換えをサーバーへ届けない（受けた件数 1 以上・打ち切り 0 件を確かめる） | 数えない（`project.md` の読み方） | `tech-stack-decisions.md` NFR4.4・NFR4.5、`security-design.md` 5節 |
| 150（案）F の流れ | 始めに自分で版 2 の DSL（接頭辞 `E2EF_`）を管理の API で投入・適用する（対象DB は要らない。照合の警告のまま適用できる）。管理の画面でロールを作り、テーブルの主権限を READ にして保存し、招待から登録まで済ませた自分の利用者に割り当て、その利用者で `GET /api/me/work-role` と `GET /api/me/navigation` を確かめる。本物の応答と見本の項目の名前・型の一致を確かめる。後始末はしない。B9 が同じファイルに後半を足す | この Intent の2本までの1本（F） | NFR6.3・NFR6.10 |
| `frontend/e2e/support/adminApiRoute.ts` | `MockReply` に、直列化済みの本文（`body: string`）を受けて `route.fulfill` の `body` にそのまま渡す形を足す。今の `json` の形は残し、既存の 120・130 は変えない | — | `logical-components.md` 3節 L13 |

- **共有の状態**: E2E は1つの WAR と内部DB を全ファイルで共有し、番号の順に1本ずつ流します（`workers: 1`）。
  - 150 の後は、適用中の DSL が `E2EF_` のものになります。後のファイル（160・170）は自分で DSL を適用し直します。
  - 040 は 140・150 より前に流れるため、影響を受けません。
- **秘密**:
  - 150 は作った利用者のメールアドレス・パスワード・氏名・`runTag` を `recordSecretValues` で値のファイルに書きます。報告の部品が json の報告と `test-results/` を探し、見つかれば失敗にします。
  - 初期管理者の資格情報はプロセスの環境変数で渡し、`webServer.env` に置きません（`project.md` の学び）。
  - 試験のデータは予約のドメイン（`example.com`）だけにします。
- **統合の前**: B8 は画面と認可に関わるため、統合の前に手元で `./gradlew e2eTest` の全体（010〜150）を流し、すべて通ってから統合します。

## 5. 量と時間の記録（Build and Test）

| 記録 | 方法 | 判定 |
|---|---|---|
| 入口の JavaScript の量 | `frontendBundleSize` の出力を、B8 の前（develop の B7 の統合の後）と B8 の後で比べる | 増えていない（新しい画面が入口に入っていない）。増えていたら原因を直す |
| 遅延読み込みの3つの塊の gzip の大きさ | `dist/.vite/manifest.json` から読む | 記録だけ |
| 画面の時間（4項目と記録だけの1項目） | E2E の 140 の測りのテスト | 5 回の中央値で判定。目標を超えたら `Not Met` と値で記録し、承認の場で扱いを決める（統合は止めない） |
| E2E の全体の時間 | `e2eTest` の実行の時間 | 目標の数値は置かない。記録する（20 組の axe と測りで延びる） |
| `verify` の時間 | `./gradlew verify` | 記録する |

## 6. 統合・配備・戻し方・秘密

| 項目 | 扱い | 出典 |
|---|---|---|
| 作業ブランチ | `develop` から作る短命のブランチ（例 `feature/261004-role-menu-b8`） | `team.md` の Way of Working |
| 統合の前の関門 | `./gradlew verify`（コンテナの実行環境あり）と E2E の全体（4節） | `team.md` |
| 統合の形 | `develop` への squash（1コミット）。サブモジュールの更新は無い | `team.md` |
| プッシュ | 依頼者自身が行う | `team.md` |
| 配備 | 既存の手順のまま（WAR に同梱した画面） | `infrastructure-specification.md` 1節 |
| 戻し | 直前の版のイメージ。前の版には U6 の画面が無い。DSL とロールの扱いは dsl-v2 と U3・U4 の引き継ぎ | `infrastructure-specification.md` 4節 |
| 秘密 | 足さない。画面は値をコンソール・ブラウザの保存・URL に出さない | `security-design.md` 4節 |
| 依存 | 足さない（`package.json`・`package-lock.json` の差分が無いことを、コード生成の終わりに確かめる） | NFR6.5 |

## 7. B8 で確かめること

| 確かめ | 方法 | 成り立たないとき |
|---|---|---|
| (i) 入口の差し替え（3節） | 段 5・E2E | 戻して機能設計 7.3 の形にし、差を記録する |
| (ii) E2E の番号（140・150）を app-frame-ui と合わせて確定する | コード生成の計画 | 計画で決め直す |
| (iii) 差し替えの口の `body: string` を足した後も、既存の 120・130 が変わらず通る | E2E | 口を直す |
| (iv) 画面の時間の測りで、差し替えた応答でも `responseEnd` の項目が得られる | 140 の最初の実行 | 操作から測る形にして、その旨を記録する（`performance-design.md` 4.2） |
| (v) カバレッジ・入口の量・画面の時間・E2E の全体 | 段 7・5節・4節 | 除外を増やさずテストを足す。目標を緩めない |
| (vi) `package.json`・`package-lock.json`・Vitest と Playwright の設定・`vite.config.ts`・`docker/monitoring/` が変わっていない | コード生成のレビュー | 元に戻す |

## 8. 上流との差

承認済みの NFR 設計と違う作りはありません。この文書は、承認済みの設計と既にある仕組みを、CI・verify の段・E2E ごとに並べ直した記録です。量と時間の記録の置き場（5節）は、承認済みの `performance-design.md` 4節・5節のとおりです。
