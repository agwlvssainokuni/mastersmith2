# CI/CD Pipeline — U7 app-frame-ui

この文書は U7 の次の項目を示します。

- 検査の流れ（CI と1コマンドの検査）
- make-you-chic-ui の固定先の更新の手順
- E2E（160・170 の案と、150 への追加）と本数の読み方
- 統合の前の手元の確かめ
- 入口の量の判定（Q1: A）
- 統合と戻し方
- B9 で確かめること

U7 は画面だけの単位で、B9 で作ります。新しい依存・設定・秘密は足さず、CI・Gradle・Vitest・Playwright の設定と、1コマンドの検査の段・関門は **変えません**。この文書は既にある仕組みの記録として、どの段で何を確かめるかを書きます。配備先は開発者の PC 上のコンテナだけです（`aidlc/spaces/default/memory/project.md` の Deployment）。

## 出典

- 答え: `infrastructure-design-questions.md`（Q1: A、まとめの確認は Looks correct）
- 上流（どれも `aidlc/spaces/default/intents/261004-role-menu/` の下）:
  - `construction/app-frame-ui/nfr-design/performance-design.md`（2〜5節）・`security-design.md`（2〜7節）・`logical-components.md`（3〜9節）
  - `construction/app-frame-ui/nfr-requirements/tech-stack-decisions.md`（NFR4.4・NFR4.5・NFR6.3・NFR6.5〜NFR6.8）
  - `construction/app-frame-ui/functional-design/functional-spec.md`・`frontend-components.md`
  - `inception/domain-design/components.md`
  - `inception/contract-design/contract-summary.md`（C2・C8・C9）
  - `inception/delivery-planning/bolt-plan.md`（B9）
  - role-admin-ui の基盤の設計の `cicd-pipeline.md`（140・150 と差し替えの口）と、その読み直しの指摘 R-01・R-03〜R-06
- 既にある仕組み（正とする。読むだけ）:
  - `.github/workflows/ci.yml`・`.github/dependabot.yml`
  - `build.gradle.kts`（`verify` の段、`vendorInstall`・`vendorBuild`・`vendorUnchanged`・`osvScan`・`e2eTest`）
  - `frontend/package.json`・`frontend/vitest.config.ts`・`frontend/playwright.config.ts`
  - `frontend/e2e/`（010〜130 と `support/`、`support/mailpit.ts`）
  - `frontend/scripts/check-bundle-size.mjs`

## 1. CI（GitHub Actions）

`.github/workflows/ci.yml` は変えません。

| 項目 | 今の形 | U7 での扱い |
|---|---|---|
| きっかけ | `develop` へのプッシュ・`v*` のタグ・手動 | 変えない |
| 実行 | `./gradlew verify`（制限時間 60 分） | 変えない |
| サブモジュール | `submodules: true` で固定先のコミットを取得 | 変えない。固定先が 5bf1ffe に上がり、CI はその版で同梱のビルドを確かめる（`team.md` の Deployment） |
| 依存の入れ方 | lockfile どおり（`frontend` は `npm ci`・`ignore-scripts=true`、`vendor/make-you-chic-ui` は `npm ci`） | 変えない |
| 秘密 | 使わない | 変えない |
| 成果物 | WAR（`dist` を同梱）をコミットのハッシュの名前で保存 | 変えない。`dist` に遅延読み込みの塊が増える |

CI が失敗したときは、`team.md` の Testing Posture「不安定なテストと CI の失敗」の決まりで扱います。

## 2. make-you-chic-ui の固定先の更新（B9 の最初）

| 順 | 手順 | 確かめ・記録 |
|---|---|---|
| 1 | 上流のリポジトリで、固定先にする版（5bf1ffe、または追加の依頼が入った版）を決める | 機能設計 D28、`logical-components.md` 7節 |
| 2 | 2つの版の差を読み取りで確かめる | `git diff --stat`。`package.json`・`package-lock.json`・`LICENSE` に変更が無い |
| 3 | `vendor/make-you-chic-ui` の固定先だけを上げる専用のコミットを、依頼者の承認を得て作る | 前後のハッシュを記録する（`project.md` の Mandated） |
| 4 | `./gradlew vendorInstall vendorBuild vendorUnchanged` と `./gradlew verify` を流す | `npm ci` が lockfile どおり、サブモジュールの中身が変わっていない、OSV-Scanner（vendor の lockfile を含む）と全検査が通る |
| 5 | アイコンの一覧を確かめる | 18 個が変わらず、`allowed-icons.txt` と画面の一覧が一致する（段 5 の `allowedNavIcons.test.ts`） |

- `.github/dependabot.yml` は変えません（`infrastructure-specification.md` 2節）。
- 統合の形（squash か fast-forward か）は、B9 のコード生成の計画で決めます（7節）。

## 3. 1コマンドの検査（`./gradlew verify`）の段と関門

| 段 | U7 で確かめること | 関門（失敗の条件） | 当たる要件 |
|---|---|---|---|
| 0 準備 | make-you-chic-ui のビルド（新しい固定先）とサブモジュールを変えていないこと | 既存のとおり | NFR6.6 |
| 1 フォーマット | 足す・書き換える画面のファイルと E2E のファイル（Prettier） | 書式の違い | NFR1.10 |
| 2 リンタ | oxlint のセキュリティ系の決まり、ESLint（機能どうしの import の禁止、骨組みは `features/` を import しない、`export default`・`enum` の禁止、react-hooks）、Stylelint を、決まりを緩めずに通す | 1件でも error | NFR1.7・NFR1.10 |
| 3 ライセンスヘッダー | 足すファイルの先頭のヘッダー | ヘッダーが無い・形が違う | NFR1.10 |
| 4 ビルド | `tsc --noEmit`（`beforeNavigate`・`clearLogoutIntent` の型、5bf1ffe の `SidebarNavSection` の型、見本の型）と Vite のビルド（遅延読み込みの塊） | 型の誤り・ビルドの失敗 | NFR1.10・NFR6.6 |
| 5 単体テスト | 画面のテスト（部品ごとに vitest-axe を1件、`waitFor`、説明文は英語）。fast-check（`buildNavSections.property.test.ts` の性質 1〜7、`tablePlaceholderPath.test.ts` の往復、失敗時の種を記録）。`validateRegistrations.test.ts`・`ShellLayout.test.tsx`・`LoginStateGate.test.tsx`・`AppRouter.test.tsx`・`logoutIntent.test.ts`・`LogoutPage.test.tsx`（StrictMode）、既存のテストの書き換え | 1件でも失敗 | NFR1.1・NFR1.4・NFR1.6〜NFR1.8・NFR2.9・NFR2.11・NFR4.1〜NFR4.3・NFR6.1・NFR6.2・NFR6.7 |
| 7 カバレッジ | 全体の合計で行 80%・分岐 70%。除外を足さない | 下回る | NFR6.4 |
| 8 安全の検査 | OSV-Scanner の npm の関門（3つの lockfile。vendor を含む）を今のまま通す | 既存の基準 | NFR6.5・NFR6.6 |
| 9 成果物 | `bootWar`（`dist` を同梱）、`frontendBundleSize`（入口の量、500KB で警告だけ） | WAR が作れない | NFR2.10（判定は 6節） |

補足:

- Vitest は `src/**/*.test.{ts,tsx}` のすべてを流すため、名前の振り分けに当たらないテストはありません（group の読み直しの R-01 の確かめ）。
- U7 は `frontend/` の外のファイルを読む画面のテストを持ちません（navigation の読み直しの R-04 は U5 の `allowedNavIcons.test.ts` に当たる）。
- 段 6（結合テスト）は、U7 では足しません。

## 4. E2E（`./gradlew e2eTest`、verify と CI の外）

E2E は意図して `verify` と CI の外に置いています。代わりに、統合の前とリリースの前に手元で流します（`team.md` の Testing Posture）。前提は既存のとおりです。

- `npx playwright install chromium`
- `docker compose --profile mail up -d mailpit`

番号は B9 の計画の最初に、U6 の 140・150 と突き合わせて確定します。

| ファイル | 中身 | 本数 | 出典 |
|---|---|---|---|
| 160（案）U7 の画面の検査 | 実際のブラウザの axe（表示の設定の 20 組、組ごとに1つのテスト）。5 段のサイドバー・開いた作業ロールの Dropdown・S2・S8・ホーム・畳んだサイドバー。幅 360・768・1280 px と悪い側の1組（lg・dark・360px）。画面の時間の測り（既定の1組）。API は差し替えの口（`frameApiRoute.ts`）で返す。1組あたりの時間の見積もりで全体が 10 分を超えるなら、160・161 に分ける | 数えない | `logical-components.md` 5節 |
| 170（案）I の流れ | 自分で `E2EI_` の版 2 の DSL を投入・適用し、ロール・招待から登録まで済ませた利用者を作る（初期管理者を変えない、後始末はしない）。5 段を開いて末端を選び、置き場の見出し・`aria-current`・再読み込み・権限なしの表示・本物の応答と見本の型の一致・ログアウト（`/logout` の直接の表示・戻る・再読み込み、`sessionStorage` の鍵が無い）を確かめる | I の流れの1本 | `logical-components.md` 6.1節、`security-design.md` 5節 |
| 150 に足す部分（U6 のファイル） | 2つ目のロールを割り当て、トップバーで切り替え、Toast の文とサイドバーの項目の出入りを確かめる。`GET /api/me/work-role` の本物の応答と見本の型の一致 | F の流れの同じ1本の中の手順（本数を増やさない） | `logical-components.md` 6.2節 |

- **本数の読み方**（role-admin-ui の読み直しの R-03 の手当て。承認済みの U7 の NFR6.3「Intent で2本のまま」）:
  - `team.md` の「束ねた元の Intent ごとに数える（Intent 261004-role-menu は最大2本）」のとおり、F（ロールベースの権限）の流れは 150 の1本、I（N 階層のメニュー）の流れは 170 の1本で、合わせて2本です。
  - B9 が 150 に足す作業ロールの切り替えは、同じ1本の中の手順です。
  - 140・160（161）の検査と、既存の E2E の書き換え（`registeredUser.ts`・`080` の開き口）は数えません。
- **共有の状態**: E2E は1つの WAR と内部DB を番号の順に1本ずつ流します（`workers: 1`）。170 は自分で DSL を適用し直すため、150 の後の `E2EF_` の DSL に頼りません。
- **Mailpit に残るメール**（role-admin-ui の読み直しの R-04 の手当て）:
  - 150・170 の招待のメールは Mailpit に残ります。既存の決まり（`e2e/support/mailpit.ts`: GET だけを使い、消す API は使わない。メールは開発者が片付ける）のままにします。
  - 宛先は走らせるごとに一意の予約のドメインのアドレスです。宛先の完全一致（`to:`）で探すため、前の実行のメールに当たりません。
- **秘密**:
  - 170 は作った利用者のメールアドレス・パスワード・氏名を `secretValues.ts` の口で書き、読み戻して揃わなければ流れの始めで失敗にします。報告の部品（`playwright-secret-check-reporter.ts`）が、json の報告と `test-results/` を探します。
  - 仮の資格情報はプロセスの環境変数で渡し、`webServer.env` に置きません（`project.md` の学び）。

## 5. 統合の前の手元の確かめ

B9 は骨組み・ログアウト・ルーターに手が入るため、次をすべて通してから統合します（role-admin-ui の読み直しの R-05 の手当て）。

| 確かめ | 方法 |
|---|---|
| E2E の全体（010〜170、160 を分けたら 161 も） | `./gradlew e2eTest` |
| ログアウトとログイン（ユーザーメニューの「ログアウト」→ `/logout` → ログイン画面 → 再ログイン） | 手元のブラウザで、ビルドした WAR を起動して確かめる |
| 深い道の再読み込み（`/tables?schema=…&table=…`・`/me/permissions`・`/admin/roles`） | 同上 |
| ブラウザの戻る・進む（業務の項目・管理の項目・置き場の間） | 同上 |
| `/logout` の直接の表示（ログイン中・未ログイン） | 同上 |
| B8 の data router が入っていれば、未保存の確かめ（D27）がサイドバーの移動で出る | 同上 |

- 手元の確かめで操作する利用者・ロールは、使い捨ての内部DB の中で作ります。配備した環境の内部DB では行いません。

## 6. 入口の量の判定（Q1: A）

| 順 | 手順 | 記録・判定 |
|---|---|---|
| 1 | B9 の変更の前（develop の B8 の統合の後、作業ブランチで変更を始める前）に、`npm run build` と `npm run bundle:size` を流す | 入口の値（gzip）を「前の値」として記録する。B8 の後の値と同じ版で取る |
| 2 | B9 の後に同じ2つを流す | 入口の値（gzip）を「後の値」として記録し、前との差を並べる。500KB の目安の警告は今のまま |
| 3 | `dist/.vite/manifest.json` の入口から静的な import（`imports`）をたどった集まりを作る | 集まりに `features/tables`・`features/mypermissions`・`LogoutPage` の塊が無い。あれば原因の静的な import を直す |
| 4 | 3つの塊の gzip の大きさを manifest から読む | 記録だけ |

- 判定と記録は Build and Test で行い、台本（`check-bundle-size.mjs`）は変えません。
- 登録のテストで `lazy` であることも、今までどおり確かめます（`performance-design.md` 5節）。
- role-admin-ui の入口の量の判定（その読み直しの R-01）も、同じ形（新しい画面の塊が入口の静的な import に無いこと、前の値を先に取る）にそろえます。

## 7. 統合・配備・戻し方・秘密

| 項目 | 扱い | 出典 |
|---|---|---|
| 作業ブランチ | `develop` から作る短命のブランチ（例 `feature/261004-role-menu-b9`） | `team.md` の Way of Working |
| 統合の前の関門 | `./gradlew verify`（コンテナの実行環境あり）、E2E の全体、5節の手元の確かめ | `team.md` |
| 統合の形 | 固定先の更新を含むため、`team.md` の fast-forward の例外を使ってよい（固定先の更新の専用のコミットが `develop` に残る）。squash にするか fast-forward にするかは B9 のコード生成の計画で決める | 機能設計 D28 |
| プッシュ | 依頼者自身が行う | `team.md` |
| 配備 | 既存の手順のまま | `infrastructure-specification.md` 1節 |
| 配備の段への引き継ぎ | スモークテストの最初に「開いているタブを読み込み直す」。メニューの確かめの前提（DSL・ロール・作業ロール）と監査の件数の合わせ。開いた Dropdown・5 段のサイドバーのはみ出し | `infrastructure-specification.md` 4節 |
| 戻し | 直前の版のイメージ（古い固定先の make-you-chic-ui を同梱）。前の版には U7 の骨組みが無い | `infrastructure-specification.md` 1節 |
| 秘密 | 足さない。画面は値をコンソール・道・ブラウザの保存（開閉の `id` を除く）に出さない | `security-design.md` 3節 |
| 依存 | 足さない（`frontend/package.json`・`package-lock.json` の差分が無いことを、コード生成の終わりに確かめる） | NFR6.5 |

## 8. B9 で確かめること

| 確かめ | 方法 | 成り立たないとき |
|---|---|---|
| (i) 固定先の更新の確かめ（2節の 2〜5） | 専用のコミットの後の検査 | 更新を止め、依頼者に諮る |
| (ii) B8 の data router が入ったか | 計画の最初に `main.tsx` を読む | 入っていなければ `BrowserRouter` のまま進め、D27 が出ないことを記録する |
| (iii) cross-cutting の B2 で `useLogout` が入った形 | 計画の最初 | `useClearLogoutIntent()` を同じ形にそろえる（`logical-components.md` 9節） |
| (iv) E2E の番号（160・170）を U6 の案と突き合わせる | 計画の最初 | 計画で決め直す |
| (v) 160 の1組あたりの時間の見積もり | 計画の最初 | 10 分を超える見込みなら 160・161 に分ける |
| (vi) 自分の権限の応答の型 | U4 の B5 の計画・実装 | 確かめられなければ S8 を外すかを計画の承認で諮る（NFR6.8） |
| (vii) 入口の量（6節）・カバレッジ・画面の時間・E2E の全体・手元の確かめ（5節） | 6節・段 7・4節・5節 | 静的な import を直す。除外を増やさずテストを足す。目標を緩めない |
| (viii) `package.json`・`package-lock.json`・Vitest と Playwright の設定・`vite.config.ts`・`main.tsx`・`docker/monitoring/`・`.github/dependabot.yml` が変わっていない | コード生成のレビュー | 元に戻す |

## 9. 上流との差（承認済みの文書は書き換えない）

| 上流 | 上流の書き方 | この設計 | 理由 |
|---|---|---|---|
| `performance-design.md` 5節（NFR2.10） | 入口の値を B9 の前と後で測って記録する。`lazy` は登録のテストで確かめる | 前の値を B9 の変更の前に取ることを手順にし、B9 の後に manifest の入口の静的な import に3つの画面の塊が無いことで判定する（6節） | Q1: A。role-admin-ui の R-01 と同じ形にそろえる |
| 承認済みの NFR6.3（「Intent で2本のまま」） | 150 への追加と 170 の関係の書き方が、role-admin-ui の記述と食い違って読めた | F は 150 の1本、I は 170 の1本で、150 への追加は同じ1本の中の手順と明記した（4節） | role-admin-ui の読み直しの R-03 の手当て。中身は変えていない |
