# CI/CD Pipeline — U2 ページ送りの共通化（u2-shared-paging）

U2 の検査の流れ（CI と1コマンドの検査）、E2E、戻し方、B2 で確かめることを示します。U2 は library の単位で、招待の一覧のページ送りの計算（サーバーの `InvitationPaging`、画面の `features/invitation/paging.ts`）を、口と振る舞いを変えずにサーバーの `cherry.mastersmith.common.paging.Paging`（契約 C2）と画面の `frontend/src/shared/paging/paging.ts`（UiPaging、契約 C5）へ移すだけです。新しい依存・API・スキーマ・設定・イメージを持たないため、CI と1コマンドの検査と配備の流れは **変えず**、この文書は既にある仕組みの記録として、どの段で何を確かめるかを書きます。配備先は開発者の PC 上のコンテナだけで、クラウドの基盤・IaC・警報の通知先は作りません（`aidlc/spaces/default/memory/project.md` の Deployment）。

- 答え: `infrastructure-design-questions.md`（質問なし、要点 7 件、まとめの確認は Looks correct）
- 上流: `construction/u2-shared-paging/nfr-design/security-design.md`（1〜11節、承認の場の決定 A-01〜A-04）・`logical-components.md`、`construction/u2-shared-paging/nfr-requirements/tech-stack-decisions.md`（NFR9.6〜NFR9.11）・`security-requirements.md`（残る危険 R1・R2、承認の場の決定 R-01〜R-05）、`construction/u2-shared-paging/functional-design/functional-spec.md`・`rules.md`、`inception/domain-design/components.md`、`inception/contract-design/contract-summary.md`（C2・C5）、`inception/delivery-planning/bolt-plan.md`（B2）（どれも `aidlc/spaces/default/intents/260930-user-admin/` の下）
- 既にある仕組み（正とする、読むだけ）: `.github/workflows/ci.yml`・`build.gradle.kts`・`backend/build.gradle.kts`・`frontend/vitest.config.ts`・`frontend/playwright.config.ts`・`frontend/e2e/`・`docker/monitoring/`

## 1. CI（GitHub Actions）

`.github/workflows/ci.yml` は変えません。

| 項目 | 今の形 | U2 での扱い |
|---|---|---|
| きっかけ | `develop` へのプッシュ・`v*` のタグ・手動 | 変えない。CI は統合の後の再確認（`team.md` の Way of Working） |
| 実行 | `./gradlew verify`（`ubuntu-latest`、制限時間 60 分） | 変えない。U2 の足すテストは数回の整数の計算の性質ベースのテスト（jqwik 500 回・fast-check 100 回）で、時間にほとんど効かない |
| サブモジュール | `submodules: true` で固定先のコミットを取得 | 変えない。U2 はサブモジュールの固定先を更新しない |
| 依存の入れ方 | lockfile どおり（`npm ci`、Gradle の lockfile） | 変えない。U2 は lockfile を変えない（NFR9.11） |
| 秘密 | CI は秘密を使わない | 変えない。U2 は秘密情報を扱わない |
| 成果物 | WAR をコミットのハッシュの名前で保存 | 変えない。WAR の中のクラスと `dist` の中のモジュールの置き場が移るだけ |

CI が失敗したときは、次の Bolt に進む前に `team.md` の Testing Posture「不安定なテストと CI の失敗」の決まりで扱います。

## 2. 1コマンドの検査（`./gradlew verify`）の段と関門

段・関門・設定は増やさず変えません。U2 の変更は既存の段で次のとおり確かめられます。どれか1つでも失敗したら全体を失敗とします（既存のとおり）。

| 段 | U2 で確かめること | 関門（失敗の条件） | 当たる要件 |
|---|---|---|---|
| 0 準備 | 変わらない（サブモジュールを変えていないことの確認を含む） | 既存のとおり | NFR9.11 |
| 1 フォーマット | 移した `Paging.java`・`PagingTest.java`（Spotless、palantir-java-format）と `paging.ts`・`paging.test.ts`（Prettier）が新しい置き場でも対象になる | 書式の違い | NFR9.4 |
| 2 リンタ | `frontend/src/shared/paging/` が oxlint・ESLint（`export default`・`enum` の禁止、セキュリティ系のルール）の対象になる。除外を足さない | 1件でも error | NFR9.4・NFR11.1 |
| 3 ライセンスヘッダー | 移したファイルの先頭に `/* ... */` の Apache License 2.0 のヘッダーがある（Java は段 1 の Spotless、画面は `check-license-header.mjs`） | ヘッダーが無い・形が違う | NFR9.4 |
| 4 ビルド | `InvitationPaging` と `features/invitation/paging.ts` への古い参照が残らない（Java のコンパイル、`tsc --noEmit`、Vite のビルド） | コンパイル・型の誤り | NFR9.5・NFR11.1 |
| 5 単体テスト | `PagingTest`（移す事例と jqwik の性質3つ、`tries = 500`）、`paging.test.ts`（移す事例と fast-check の性質4つ、既定 100 回）、招待の既存の単体テスト、既存の `ArchitectureTest`・`InvitationBoundaryArchitectureTest`（書き換えない） | 1件でも失敗 | NFR9.1・NFR9.2・NFR9.5・NFR9.6・NFR9.7・NFR11.1 |
| 6 結合テスト | 招待の一覧の既存の結合テスト（`InvitationRepositoryIT` など。不正な page の 400 `VALIDATION_FAILED`、最後のページとその次の空の 200）を、参照先と import の変更だけで通す | 1件でも失敗 | NFR9.1・NFR9.2・NFR9.3・NFR9.5 |
| 7 カバレッジ | 3節のとおり。`common.paging` は新しいパッケージとして自動でパッケージごとの下限の対象。画面は全体の合計 | 全体またはパッケージごとの行 80%・分岐 70% を下回る。除外は足さない | NFR9.8・NFR9.9・NFR9.10 |
| 8 安全の検査 | SpotBugs ＋ FindSecBugs（priority 1 と `SQL_`・`PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION`）、OSV-Scanner、Gitleaks を除外を足さずに通す。Paging は SQL・乱数・メールに触れない | 既存の基準 | NFR9.4・NFR9.11 |
| 9 成果物 | 既存の `bootWar`・画面の初回の読み込みの量の確かめ | WAR が作れない・量の上限を超える | （U2 の変更は量に効かない） |

補足:

- 性質ベースのテストの失敗のときの乱数の種は、既存の仕組みのまま CI の記録に残ります（Gradle のテストの出力は失敗の詳細をすべて出す設定、fast-check は seed と path を出す）。再現の仕方は移すテストの先頭の説明文に書きます（NFR9.7）。
- 新しい性能の目標・k6 の場面・指標・警報・ログは足しません。招待の一覧の応答時間（同時 10 件で p95 1 秒）は、既存の k6 の場面 `invitationList` で Performance Validation が確かめます。警報の決まり（`docker/monitoring/provisioning/alerting/mastersmith.yaml`）は変えません（NFR5.1・NFR5.2）。

## 3. カバレッジ（段 7）

| 対象 | 今の扱い | U2 での扱い |
|---|---|---|
| `packagesJudgedByTotal`（`backend/build.gradle.kts`、12 パッケージ） | 全体の合計で判定する既存のパッケージの一覧 | 変えない。`invitation.*` は元から一覧に無く、外す作業も戻す作業も無い。一覧を増やさない |
| `invitation.domain` | パッケージごとの下限（行 80%・分岐 70%）の対象 | `InvitationPaging` が抜けた後も下限を満たす。コード生成の計画で今の値と `InvitationPaging` を除いた見込みの値を実測して並べ、足すテストの量を見積もる（A-04、残る危険 R1）。下回ったら、計測の除外を増やさず、同じパッケージのほかのクラスのテストを足す |
| `invitation.service` | パッケージごとの下限の対象 | 参照先だけの変更。値を実測して記録する |
| `common.paging` | 無い（新しいパッケージ） | 自動でパッケージごとの下限の対象になる。移す単体テストで parsePage の分かれ道と2つの例外を通る。`package-info.java` は置かない |
| 画面（`frontend/vitest.config.ts` の `thresholds`） | 全体の合計で行 80%・分岐 70%、計測は `src/**/*.{ts,tsx}` | 全体の合計のまま判定する。`src/shared/paging/` を計測から外さない |
| 計測の除外 | 起動クラス・設定値だけのクラス・自動生成コード・`vendor/` | 増やさない |

実測は `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で行い、`invitation.domain`・`invitation.service`・`common.paging` と画面の全体の値を記録します（`team.md` の Testing Posture、`project.md` の学び）。計画の前の実測は、`project.md` の学びのとおり `:backend:cleanTest :backend:cleanIntegrationTest :backend:test :backend:integrationTest :backend:jacocoTestReport` で行い、`jacocoTestReport.xml` から読んでもかまいません。

## 4. E2E（`./gradlew e2eTest`、verify と CI の外）

- B2 は画面に関わる Bolt のため、統合の前に手元で `./gradlew e2eTest` を流します（`bolt-plan.md`、`team.md` の Testing Posture）。U4 の変更とあわせて、同じ Bolt で1回流します。
- 招待に関わる既存の `frontend/e2e/060-invitation-accessibility.e2e.ts`・`090-invitation-registration-flow.e2e.ts` が、変更なしで通ることを確かめます。ほかの既存の E2E も全体の実行で一緒に通ります。
- U2 のために E2E のファイルは足しません（Intent ごとに代表の流れを1本までの決まりは、U5 の側で使います）。
- 前提は既存のとおり（Playwright の chromium、`docker compose --profile mail up -d mailpit` で起動したメールの受け手。`e2eTest` は届かなければ失敗させる）。

## 5. 統合と配備の流れ

| 項目 | 扱い | 出典 |
|---|---|---|
| 作業ブランチ | `develop` から作る短命のブランチ（例 `feature/260930-user-admin-b2`）。worktree は使わない | `team.md` の Way of Working |
| 統合の前の関門 | `./gradlew verify`（コンテナの実行環境あり、対象DB のテストを飛ばさない）と E2E（4節） | `team.md` の Way of Working・Testing Posture |
| 統合の形 | `develop` への squash。B2 は U2・U4 の単位ごとの squash（2コミット）にしてよく、どちらにするかはコード生成の計画で決める。サブモジュールの更新は無いため fast-forward の例外には当たらない | `bolt-plan.md`、`team.md` の Way of Working |
| プッシュ | 依頼者自身が行う。AI はプッシュしない | `team.md` の Way of Working |
| 配備 | 既存の手順（Dockerfile・`compose.yaml`・README）のまま。U2 はイメージの作り方・`compose.yaml`・`.env`・ボリューム・JVM の設定を変えない | `security-design.md` の8節 |
| 配備の後の確かめ | 既存のヘルスチェックとスモークテスト。招待の一覧のページ送りの確かめを入れるかは deployment-pipeline の段で決める | `team.md` の Deployment |

## 6. 戻し方と秘密

| 項目 | 扱い |
|---|---|
| DB スキーマ | 変えない。内部DB に触れない |
| 前の版への戻し | 直前の版のイメージで起動し直すだけで済む。設定・スキーマの変更が無いため、内部DB のバックアップの要否に影響しない |
| 設定（`.env`・`application.yaml`） | 変えない。戻すものは無い |
| 秘密 | 扱わない。CI に秘密を渡さない。Gitleaks の除外を足さない |
| 依存 | 新しい依存を足さず、`gradle/libs.versions.toml`・`backend/gradle.lockfile`・`frontend/package.json`・`frontend/package-lock.json` を変えない。ライセンスの確かめと lockfile の更新は要らない |

## 7. DevSecOps と Compliance の視点

| 観点 | 確かめた結果 |
|---|---|
| 入力の検証 | page の検証はサーバーの `Paging.parsePage` の1か所だけを正とし、画面の UiPaging はサーバーの検証の代わりにしない。拒否は既存の 400 `VALIDATION_FAILED`（Problem Details）で、新しい code を足さない。段 5・6 のテストで確かめる |
| 個人に関する値・秘密 | Paging・UiPaging の口は整数と列挙だけ（parsePage の page の文字列を除く）。伏せ字の型と `*SecretLeakIT` は足さない。TRACE で page の文字列が出うるのは呼び出し元の引数だけで、受け入れた残る危険（R3）。要求の行の上限（A-01）とログの行の偽造（A-02）はコード生成の計画で扱う |
| 静的解析・依存の脆弱性 | 既存の段 8 の関門を除外なしで通す。新しい依存が無いため、OSV-Scanner の対象も変わらない |
| 監査 | 読み取りの一覧の操作で、権限・状態を変えないため監査に残さない（BR3.1）。既存の監査の決まりを変えない |
| 公開のリポジトリ | 移すテストのデータは整数だけで、個人に関する値・実在しそうな宛先を置かない |

## 8. B2 で確かめること

| 確かめ | 方法 | 成り立たないとき |
|---|---|---|
| (i) 参照の切り替えの後に `InvitationPaging` と `features/invitation/paging.ts` への参照が残っていない | 段 4 のビルドとコード生成のレビュー（名前の検索） | 参照を直す |
| (ii) 招待の既存のテストの中身が変わっていない（import と参照先だけの差） | コード生成のレビュー（差分の確かめ）、段 5・6 | 中身を元に戻し、振る舞いの違いを直す |
| (iii) カバレッジの実測の値（`invitation.domain`・`invitation.service`・`common.paging` と画面の全体） | 3節の実測 | 除外を増やさずテストを足す |
| (iv) E2E の結果（060・090 を含む全体） | 4節 | 統合しない。原因を直す |
| (v) 依存・lockfile・警報の決まり・`packagesJudgedByTotal`・計測の除外が変わっていない | コード生成のレビュー | 元に戻す |
| A-01〜A-04（要求の行の上限、ログの行の偽造、`PAGE_SIZE` の一致の確かめ、`invitation.domain` の見込み） | コード生成の計画で扱う（NFR 設計の承認の場の決定） | 計画の中で決める |

## 9. 上流との差

上流（要件・機能設計・契約 C2・C5・NFR 要件・NFR 設計）と違う作りはありません。この文書は、承認済みの設計と既にある仕組みを、CI と verify の段ごとに並べ直した記録です。

## 承認の場の決定（Request Changes、2026-10-02）

依頼者は、この段の承認の場で Request Changes を選んだ。U2 の文書の中身は直さず、次の扱いを記録する。

| 指摘 | 扱い | 中身 |
|---|---|---|
| R-01（E2E の 060・090 がページ送りを操作するかが書かれていない） | B2 のコード生成の計画へ申し送る | 操作しないなら、画面のページ送りの確かめは Vitest（`paging.test.ts` など）だけが担うと計画で書き分ける |
| R-02（`common.paging` は新しいパッケージとして、すぐにパッケージごとの下限の対象になる） | B2 のコード生成の計画へ申し送る | 計画の実測の表に、`parsePage` の分岐を網羅できる見込みを並べる |
| R-03（単位ごとの squash にするときの条件） | B2 のコード生成の計画へ申し送る | 単位ごとの squash にするなら、U2 のコミットが単独で `./gradlew verify` を通ることを確かめる。通らないなら Bolt 全体の1コミットにする |
| 配備の後のスモークテストに、招待の一覧のページ送りの確かめを入れるか（5節） | deployment-pipeline の段で決める | この段では決めない |
| E2E の報告の扱い（U5 の基盤の設計の Q1 A・レビュー R-02 の決定） | 決定に従う | B2 の統合の前の `./gradlew e2eTest` の後は、json の報告から結果を記録してから `frontend/playwright-report/` と `frontend/test-results/` を消し、消したことと共有していないことを B2 のコード生成の記録に書く |
