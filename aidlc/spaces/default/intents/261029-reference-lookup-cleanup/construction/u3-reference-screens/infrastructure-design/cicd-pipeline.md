# 届ける流れ — U3 u3-reference-screens

U3 を統合して配備するまでの流れ。CI の仕組みと配備の手順は既にあるため、新しい設計ではなく、既にあるものに U3 がどう乗るかの記録として書く（`project.md` の学び）。CI のワークフロー（`.github/workflows/ci.yml`）は変えない。

文章で ID を指すときは、単位の接頭辞を付ける（例 `U3-NFR5.43`）。

## 1. 段と関門

統合の前の関門は `./gradlew verify` の1コマンド（`team.md` の Way of Working）。U3 が乗る段は次のとおり（`build.gradle.kts` の `verifyStages`）。

| 段 | 中身（既存） | U3 が乗るもの | 関門 |
|---|---|---|---|
| 1 フォーマット | Prettier・Spotless | 足す `.ts`・`.tsx`・`.css` | 違反で失敗 |
| 2 リンタ | oxlint・ESLint（`react/no-danger`・機能どうしの import の禁止など）・Stylelint | 参照の部品・E2E 190・195 | 違反で失敗。`dangerouslySetInnerHTML` を使わない（U3-NFR1.41） |
| 3 ライセンスヘッダー | `check-license-header.mjs` | 足すファイル（参照の部品・E2E 190・195・`support/` の新しいファイル） | 無ければ失敗 |
| 4 ビルド | `tsc --noEmit`・Vite のビルド（`frontendBuild`） | 参照の型と部品 | 型の誤りで失敗 |
| 5 単体テスト | Vitest（Testing Library・user-event・**vitest-axe**） | `frontend-components.md` 7節と `security-design.md` 4節のテスト（`useReferenceLabels`・`ReferenceSelectField` の `radio` 200 件・`ReadonlyReferenceField`・`LookupField`・`LookupPickerDialog`・写し・純粋な関数の fast-check）。画面部品ごとに vitest-axe を1件 | 失敗で止める。時間の上限を延ばさない（`team.md`） |
| 6 結合テスト | JUnit の IT（U3 は足さない） | なし | — |
| 7 カバレッジ | `@vitest/coverage-v8` の `thresholds`（行 80%・分岐 70%、全体の合計） | `features/tables` の値（記録は Build and Test） | 下回れば失敗。除外を足さない |
| 8 安全の検査 | OSV-Scanner（lockfile）・Gitleaks | 新しい依存は無い（`package-lock.json` に新しいパッケージが無いこと） | High 以上で失敗 |
| 9 成果物と量 | `bootWar`（`dist` を同梱）・`check-bundle-size.mjs`（入口、500 KB で警告） | 業務データの画面の chunk は一時の手順で別に記録（`infrastructure-specification.md` 4節） | 入口は警告だけ（既存） |

## 2. E2E（verify と CI の外）

| 項目 | 決めたこと |
|---|---|
| 足すファイル | `frontend/e2e/190-reference-picker-flow.e2e.ts`（代表の流れ 1本）、`frontend/e2e/195-reference-picker-accessibility.e2e.ts`（axe。本数に数えない。U3-NFR5.43） |
| 流し方 | `./gradlew e2eTest`（`:backend:bootWar` に依存して WAR を作り、Playwright の `webServer` で起動。`workers: 1`・`fullyParallel: false` で全ファイルを順に流す）。ファイルを選ぶときは `-Pe2e.files=e2e/190-reference-picker-flow.e2e.ts,e2e/195-reference-picker-accessibility.e2e.ts` |
| 前提 | colima（`DOCKER_HOST`）、`npx playwright install chromium`、Mailpit（`docker compose --profile mail up -d mailpit`）、見本の PostgreSQL を上乗せの compose で `127.0.0.1:15432` に開く（`infrastructure-specification.md` 2節）。`e2eTest` は届かなければ起動の手順を示して失敗し、起動・停止はしない |
| 流す時点 | U3 は frontend の本体（`frontend/src` のテスト以外）に触れるため、**統合の前**に E2E の全件を流して通す（`team.md` の Way of Working）。あわせて Build and Test（Intent の最後）とリリース（`main` への取り込み）の前にも流す |
| 180 の確かめ | 共有の DSL を `support/` へ移して参照の表を足すため、180 の全件が通ることを同じ実行で確かめる |
| 資格情報 | 実行のスキーマの一時のロールの資格情報は `e2eTest` のプロセスの環境変数だけで渡し、`webServer.env` に置かない。報告の部品（`playwright-secret-check-reporter.ts`）が json の報告と `test-results/` を検索する。190 の参照先のラベルを `businessValue` として値のファイルに書く（U3-NFR1.47） |
| 保存を起こさない確かめ | 195 の差し替えで作る状態（4〜9・12）が作成・更新の POST を差し替えの外へ出していないことを、Playwright の要求の記録で数える（`security-design.md` 3.4、R-04） |
| axe | 規則と要素を外さない（`project.md` の Forbidden）。状態 7 の axe を先に流し、`color-contrast` が出た組があれば make-you-chic-ui への依頼の要否を依頼者に諮る（R-03） |
| 長いテストの守り | `caffeinate -dims` で包み、流した後に `pmset -g log` で Sleep・DarkWake が無いことを確かめる。入っていたら結果を使わずに流し直す（`project.md` の学び） |
| 片付け | `e2eTargetDbCleanup`（`finalizedBy`）が実行のスキーマと一時のロールを名前で消す。コード生成の段を始める前と承認の場の前に、`frontend/playwright-report`・`frontend/test-results` を消しておく（`project.md` の学び） |

## 3. 時間の記録（U3-NFR2.45）

| 何を | いつ | 線 |
|---|---|---|
| E2E の全体・190・195 の時間 | Code Generation の最初の実行（変更の前）、U3 の Bolt の後、Build and Test | 全体が変更の前の 1.5 倍を超えたら、ファイルごとの時間を記録して 195 の組と状態の分け方を依頼者に諮る（上限で落とさない） |
| `./gradlew verify` の時間 | U3 の Bolt の後、Build and Test | CI の上限 60 分の7割（約 42 分）を超えたら、テストの分け方を依頼者に諮る（`team.md`） |
| CI の時間 | 統合の後の push、Build and Test | 検査の job の始めから終わりで測る（run の待ちを含めない。`project.md` の学び） |
| 業務データの画面の chunk の大きさ | U3 の Bolt の前と後 | 線なし（記録だけ。`infrastructure-specification.md` 4節） |

## 4. CI（変えない）

| 項目 | 記録 |
|---|---|
| きっかけ | `develop` への push（統合の後の再確認）。`v*` のタグは配備先が決まるまで付けない（`team.md`） |
| 中身 | `./gradlew verify` の1つ。段 5 の Vitest（vitest-axe を含む）と段 7 のカバレッジで U3 の画面のテストが流れる |
| E2E | CI の外（意図して外に置く。代わりの実行の場は 2節の手元の `e2eTest`）。`project.md` の学び「CI の外に置く検査は代わりの実行の場を明記する」 |
| 失敗したとき | `team.md` の「不安定なテストと CI の失敗」の決まり。CI の結果は試行ごと（`gh run view --json attempt,jobs`）に確かめ、再実行で通ったものも1回目の失敗とテストを記録する |
| 変更 | 無し。ワークフローのファイル・ジョブ・上限（`timeout-minutes: 60`）を変えない |

## 5. 配備と戻し

| 項目 | 決めたこと |
|---|---|
| 配備の形 | 既存の手順のまま。WAR を作り（`./gradlew :backend:bootWar`）、`mastersmith:local` のイメージを作り直して `app` のコンテナを入れ替える。入れ替えの後に `docker inspect` でコンテナのイメージ ID と `mastersmith:local` の ID の一致を確かめ、食い違えば `--force-recreate --no-deps`（`project.md` の学び） |
| U3 の分の差 | 画面の静的なファイルだけ。移行・`.env`・compose の変更は無い。ヘルスチェックとスモークテストの中身は U1・U2 と合わせて Deployment Pipeline で決める |
| 戻し | **イメージだけの戻し**（直前の版のイメージで `app` を作り直す）。U3 の分は DB の形と設定を変えないため、内部DB の複写を戻す必要が無い |
| 戻しの注意 | 直前の版の画面は参照の部品を持たないため、戻すと作成・更新の画面の参照のカラムは前の形（文字の入力）に戻る。U1・U2 のサーバー側を同時に戻すかは、Intent の全体の戻しの手順（Deployment Pipeline）で決める |

## 6. 環境の昇格

検証・本番の環境は無く、昇格の流れも無い（`team.md` の Deployment）。`develop` から `main` への fast-forward をリリースとし、どのコミットをリリースしたかは `main` の先頭と Deployment Execution の記録で追う。取り込みの前に、前の取り込みの後のすべてのコミットの CI を試行ごとに確かめる（`project.md` の学び）。

## 7. 秘密の扱い

| 秘密 | 扱い |
|---|---|
| E2E の実行のスキーマの一時のロールのパスワード | `e2eTest` のビルドの中で乱数で作り、プロセスの環境変数だけで渡す（既存。2 時間で失効）。U3 は変えない |
| E2E の自作の利用者のパスワード・メールアドレス・氏名・参照先のラベル | `recordSecretValues` で値のファイルに書き、報告の部品が報告を検索する（U3-NFR1.47） |
| 画面 | 秘密を持たない。CI に新しい秘密を足さない |

## 上流との差

| 上流 | 差 | 扱い |
|---|---|---|
| 無し | U3 の NFR 設計の決め（E2E の本数・状態・時間の記録・axe の扱い）を、既存の `verify` と `e2eTest` の段に割り当てただけ | 新しい決めは無い |

## 後の段に回すこと

- **Code Generation**: 2節の前提で 190・195 を書き、統合の前に E2E の全件を流して通す。変更の前の E2E の全体の時間を最初の実行で測る。chunk の大きさを前と後で記録する。状態 7 の axe を先に流す（R-03）。
- **Build and Test**: E2E の全件の通過と 195 の違反 0 件の件数、axe-core の版が lockfile で1つにそろっていること、`verify`・CI・E2E の時間、`features/tables` のカバレッジの値。
- **Deployment Pipeline**: U3 の分の差（画面だけ・イメージだけの戻し・戻したときの画面の形）を U1・U2 の差と合わせて手順にする。

## Sources

- この段の質問（質問なし・決まっていること）: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u3-reference-screens/infrastructure-design/infrastructure-design-questions.md`
- NFR 設計: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u3-reference-screens/nfr-design/performance-design.md`（4節・5節）・`security-design.md`（1.7・3.4・4節・7節、承認の場の決定）・`logical-components.md`（L9）
- 機能設計: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u3-reference-screens/functional-design/functional-spec.md`（D37〜D39・F8）・`frontend-components.md`（7節・8節）
- 部品: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/domain-design/components.md`（BusinessDataScreens）
- 契約: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/contract-design/contract-summary.md`（C2・C3）
- U1 の届ける流れ: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/infrastructure-design/cicd-pipeline.md`
- 読み取りだけで確かめた既存の設定: `build.gradle.kts`（`verifyStages`・`e2eTest`・`e2e.files`・`e2eTargetDbCleanup`）、`frontend/package.json`（`test`・`test:coverage`・`bundle:size`）、`frontend/playwright.config.ts`（`workers: 1`・`SECRET_ENV_NAMES`・報告の部品）、`.github/workflows/ci.yml`（`./gradlew verify`・`timeout-minutes: 60`）
- 決まり: `aidlc/spaces/default/memory/team.md`（Way of Working の E2E の時点・Testing Posture・Deployment）、`aidlc/spaces/default/memory/project.md`（Forbidden の axe、学び: `caffeinate`・`pmset`・CI の試行ごとの確かめ・`docker inspect`）

## Assumptions & Open Questions

- [assumption] 190・195 を足しても E2E の全体は変更の前の 1.5 倍に収まる（195 は1件のテストで1つの組の状態を順に流し、件数を組の数に抑える形。`performance-design.md` 5節）。超えたら依頼者に諮る。
