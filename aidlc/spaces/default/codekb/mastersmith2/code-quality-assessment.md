# コードの質の評価（mastersmith2）

件数はファイルを数えた値・`grep` で数えた値で、テストを実行した値ではない（Gradle・npm は実行していない）。この文書に本文を書いた所見は K-10（前回からの続き）と K-16 で、ほかは持ち主の文書を参照する（一覧は `business-overview.md`）。

## テスト

| 対象 | 置き場と数（前回 `e68f54d` に数えた値） | 道具 |
|---|---|---|
| バックエンドの単体 | `backend/src/test/java/`、`*Test` 151 ファイル（タスク `test`） | JUnit Jupiter・AssertJ・Mockito・jqwik・ArchUnit |
| バックエンドの結合 | 同上、`*IT` 108 ファイル（タスク `integrationTest`）。その後 `HistogramBucketsIT` などが足された | Spring Boot Test（`OutputCaptureExtension`）・Awaitility・Testcontainers・SubEthaSMTP |
| 画面 | `frontend/src/**/*.test.ts(x)` 91 ファイル | Vitest・Testing Library・user-event・vitest-axe・fast-check |
| E2E | `frontend/e2e/*.e2e.ts` 10 ファイル（今回数えた。100 が前回の後に足された）。`verify` と CI の外 | Playwright・axe-core |

前の Intent の Build and Test の記録では、統合の後の `develop` で `verify`・E2E 110 件・CI が通った（コミット `b445b6b` の件名による。ここでは確かめていない）。

### ログの確かめの向き（K-11 の関連）

- ログに秘密が出ないことの確かめは、`LogEvents`（ログの出来事の取り込み）と `JsonLogRecords.assertContainsNoSecret`（標準出力の JSON）の2つの形がある。`*SecretLeakIT` の7つが後者の形を使う（名前だけ確かめた）。
- `InitialAdminInitializerTest` は、決まり（アプリのログにメールアドレスを出さない）と逆に「含まれること」を確かめている。本文は `component-inventory.md` の `user`（K-11）。

### 前回の CI の2つの時間切れ（前回の K-4・K-5）

前回（`e68f54d`）の時点では、`H2CompactionByPoolSuspensionIT` の 10 秒の待ち（K-4）と `InvitationAdminPage.test.tsx` の1件が Vitest の既定の 5 秒で動くこと（K-5）が CI の失敗の原因だった。`b20bf1e`（上限を延ばし、失敗時の診断と不安定なテストの直しを足す）で扱われた（件名による。延ばした値と診断の形は今回確かめていない）。前回の見立て（`user.type` の1文字ごとの描画が 5 秒すれすれ、など）は `architecture.md` の Interaction Diagrams 3 と前回の記録に残る。

## カバレッジ

- バックエンド: JaCoCo で全体の合計とパッケージごとに行 80%・分岐 70%（`backend/build.gradle.kts` 236〜270 行）。既存の一部のパッケージは `packagesJudgedByTotal` の一覧で全体の合計だけで判定する（K-10）。
- 画面: `vitest.config.ts` の `thresholds`（行 80・分岐 70、前回の記録）。
- 計測から外すのは起動クラス・設定値だけのクラス・自動生成・`vendor/` に限る（`team.md` の Testing Posture）。

### K-10 `team.md` の `packagesJudgedByTotal` の記述が今のビルドより古い（前回からの続き）

確かめた事実（アーキテクトが `backend/build.gradle.kts` 221〜234 行で確かめ直した）: `team.md` の Testing Posture は一覧を「`user.domain`・`user.repository`・`user.service` を含む 22 パッケージ」と書くが、今の一覧は **12 個**（`access.domain`・`access.service`・`audit.repository`・`auth.domain`・`auth.repository`・`common.error.domain`・`common.error.service`・`common.error.web`・`common.health`・`common.i18n.domain`・`common.observability`・`common.web`）。210〜220 行の説明どおり、Intent `260925-user-management` の B2 で `user.*` を含む7つ、U8 で3つが外れた（`user.service` はコミット `9a9a633` で外れた）。前回（`260928-quality-followup`）の後も `team.md` は直っていない。

帰結:

- 今回の依頼の「team.md も修正する」の対象の候補である（どこを直すかは要件で決める。この記述のほかに、Dependabot の受け方などの決まりを変えるかも含む）。`team.md` は「Edit at the gate, not directly」の決まりのため、直し方（practices の関門か、学びの手順か）も要件で決める。
- K-11 の直しで手を入れる `user.service` は既にパッケージごとの下限の対象で、一覧から外す作業は付かない。

## 検査と CI

- 形と静的検査: Spotless（Gradle のプラグイン 8.10.2）＋palantir-java-format（Java・Kotlin DSL・メールのテンプレートのライセンスヘッダー）、Prettier・oxlint・ESLint（`@typescript-eslint/parser`）・Stylelint（`frontend/`）、SpotBugs＋FindSecBugs（`backend/config/spotbugs-exclude.xml`）、Gitleaks（pre-commit と CI）、OSV-Scanner。
- CI: `.github/workflows/ci.yml` は `develop` へのプッシュ・タグ `v*`・手動で動き、`permissions: contents: read`、Actions はコミットのハッシュで固定。verify の仕事は checkout（`submodules: true`）→ setup-java（temurin 25）→ setup-node（24、npm のキャッシュは `frontend/package-lock.json` と `vendor/make-you-chic-ui/package-lock.json`）→ setup-gradle → Gitleaks・OSV-Scanner を SHA-256 を確かめて入れる → `./gradlew verify` → WAR を成果物として保存。release の仕事はタグのときだけ WAR を GitHub のリリースに添付する。E2E は CI の外（K-13）。
- 更新の知らせ: Dependabot の ignore と開いた知らせは K-14（`dependencies.md`）。
- 観測: p95 の警報3件は境界のバケットで値を持つが、`ms-check-p95` のしきい値が境界に無い（K-15、`architecture.md` の Interaction Diagrams 1）。

## 画面の質

### 前回の K-3 アプリ自身の CSS の文字の色

前回（`e68f54d`）の時点では、`PreferencesForm.css` の `.preferences-choice-error`（`--color-danger`）、`DslSubmitForm.css` の `.dsl-link` と使われていない `Page.css` の `.page-link`（`--color-primary`）が、make-you-chic-ui の文字用のトークンの直しの外にあった。`79a0395`（E2E の既知の違反の一覧とアプリ独自の CSS を直す）で3つの CSS が変わった（件名と変わったファイルによる。中身は今回確かめていない）。今回の make-you-chic-ui の更新（K-12）で足される `--color-primary-emphasis-text`・`--color-primary-hover-text` をアプリの CSS が使うかは、確かめていない。

## 文書

- `README.md`（1,069 行）は起動・環境変数・E2E・監視・警報と対応の手順・監査・招待・既知の制約・戻し方まで持つ。コードのコメントは日本語で、決定の出どころ（Intent・単位・決定の番号）を丁寧に書いている。
- 前回の K-9（運用の手順書の置き場が無い）は、`7c2fea4` で README に「警報と対応の手順」の節（692 行〜）が足されて扱われた。前回の K-8（承認済みの運用の記録の誤り）も同じコミットの README の直しで扱われた（件名による、`api-documentation.md`）。

### K-16 README の古い記述と、固定先を上げた後に書き直す節

確かめた事実（開発担当が行を示した。アーキテクトは固定先だけを `git submodule status` で確かめ直した）:

- 38 行「make-you-chic-ui の固定先は `735ef04`」は、今の固定先 `310e1ec` と食い違う（前の Intent の更新漏れ）。
- 今回外す E2E の既知の違反（K-13）を説明する節: 158 行（100 の説明）・957 行（DSL の管理の画面のコントラスト）・990〜993 行（「残る既知の制約」の2件）。
- `310e1ec` の解消を説明する節（固定先を上げた後に書き直す候補）: 113・122・130・138・909・924・938・974〜988 行。976〜988 行の表は green・orange の primary の Button の文字が濃い色という前提で、`077f5b4` で hover の文字が白になると前提が変わる（K-12 の見立て）。
- ms-check-p95 の 300 ms（K-15）: 682〜683 行（境界と刻みの説明）・699〜706 行（警報と対応の手順の表）。
- 初期管理者の起動の確かめ（K-11）: 215 行が「初期管理者を作成しました」の INFO を確かめる手順に使う。664 行は標準出力のログは伏せない（外部エクスポートだけ伏せる）と説明する。774 行は監査の書き込みの失敗の ERROR がメールアドレスをキーと値で載せると書く（コードは確かめていない）。

帰結: 固定先の更新・既知の違反の削除・しきい値の直し・ログの直しのそれぞれで、README の上の行を同じ変更で直す必要がある。38 行の食い違いは今回の固定先の更新でまとめて直せる。

## 技術的負債の一覧

| 項目 | 所見 | 重さ |
|---|---|---|
| 初期管理者の作成のログがメールアドレスを出し、テストが逆向きに確かめる | K-11 | 高（`project.md` の Forbidden に反する） |
| make-you-chic-ui の固定先と E2E の 100 の既知の違反 | K-12・K-13 | 中（依頼の対象。確かめは手元の E2E だけ） |
| Dependabot の開いた知らせ4件と npm の ignore が無いこと | K-14 | 中 |
| `ms-check-p95` のしきい値が境界に無い | K-15 | 中（判定が粗い） |
| `team.md` の記述の古さ | K-10 | 低（記録の食い違い） |
| README の固定先の記述の古さと、書き直しの対象の節 | K-16 | 低 |
