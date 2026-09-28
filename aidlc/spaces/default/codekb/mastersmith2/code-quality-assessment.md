# コードの質の評価（mastersmith2）

件数はファイルを数えた値・`grep` で数えた値で、テストを実行した値ではない（Gradle・npm は実行していない）。この文書に本文を書いた所見は K-3・K-5・K-9・K-10 で、ほかは持ち主の文書を参照する。

## テスト

| 対象 | 置き場と数 | 道具 |
|---|---|---|
| バックエンドの単体 | `backend/src/test/java/`、`*Test` 151 ファイル（タスク `test`） | JUnit Jupiter・AssertJ・jqwik・ArchUnit |
| バックエンドの結合 | 同上、`*IT` 108 ファイル（タスク `integrationTest`）。補助を含め 317 ファイル、テストの注釈は 1,332 個 | Spring Boot Test・Awaitility・Testcontainers・SubEthaSMTP |
| 画面 | `frontend/src/**/*.test.ts(x)` 91 ファイル、`it(`・`test(` 702 個 | Vitest・Testing Library・user-event・vitest-axe・fast-check |
| E2E | `frontend/e2e/*.e2e.ts` 9 ファイル、`test(` 32 個。`verify` と CI の外 | Playwright・axe-core |

前の Intent の記録では、CI の実行で結合テスト 562 件・画面のテスト 732 件だった（実行の値。ここでは確かめていない）。

### CI の2つの時間切れ

- `H2CompactionByPoolSuspensionIT` の 10 秒の待ち（K-4）: 本文と仮説は `architecture.md` の Interaction Diagrams 3。
- `InvitationAdminPage.test.tsx` の1件（K-5）: 本文は下。
- どちらも前の Intent（`260925-user-management`）で「次の Intent で直す」と受け入れた失敗で、`team.md` の「CI が失敗したら次の Bolt に進む前に原因を直す」「不安定なテストは原因を直すまで統合しない」との差が記録されている。

### K-5 `InvitationAdminPage.test.tsx` の1件が Vitest の既定の 5 秒で動く

確かめた事実:

- 「keeps addresses and names out of storage, the URL and the console in every flow」（749 行）は `it` に上限の指定が無い。`frontend/vitest.config.ts`・`vitest.setup.ts` にも `testTimeout` が無く、`frontend/src` に個別の上限の指定も無い。Vitest の既定の 5 秒で動く。
- 1つのテストで、招待 3 回（36 文字のメールアドレスを `user.type`）・送り直し 2 回・取り消し 2 回とページ送りを順に行う。
- 前の Intent の記録では、1回目の CI で 4,583 ms で通り、2回目で 5 秒を超えた。手元の `verify` では通っていた。

見立て（未検証）: 1つのテストの操作が多く（`user.type` は1文字ごとに描画する）、CI の runner で 5 秒すれすれになる。直し方の候補は、そのテストだけ上限を延ばす・入力を `user.paste` などに変える・流れを複数のテストに分ける。ほかの画面のテストにも 5 秒に近いものがあるかは、実行の時間を測らないと分からない。

## カバレッジ

- バックエンド: JaCoCo で全体の合計とパッケージごとに行 80%・分岐 70%（`backend/build.gradle.kts` 229〜262 行）。既存の一部のパッケージは `packagesJudgedByTotal` の一覧で全体の合計だけで判定する（K-10）。
- 画面: `vitest.config.ts` の `thresholds`（行 80・分岐 70）。
- 計測から外すのは起動クラス・設定値だけのクラス・自動生成・`vendor/` に限る（`team.md` の Testing Posture）。

### K-10 `team.md` の `packagesJudgedByTotal` の記述が今のビルドより古い

確かめた事実: `team.md` の Testing Posture は一覧を「`user.domain`・`user.repository`・`user.service` を含む 22 パッケージ」と書くが、`backend/build.gradle.kts` の今の一覧は **12 個**（`access.domain`・`access.service`・`audit.repository`・`auth.domain`・`auth.repository`・`common.error.domain`・`common.error.service`・`common.error.web`・`common.health`・`common.i18n.domain`・`common.observability`・`common.web`）。コードの注記どおり、user-management の B2・U8 で `user.*` を含む 10 個が外れた。

帰結: 今回の変更がこの一覧のパッケージ（K-6 の直しをコードで行うなら `common.observability` など）に触れると、`team.md` によりテストを足してそのパッケージの下限を満たし、一覧から外す作業が付く。`team.md` の記述の直しは、ワークフローの学びの手順（依頼者の承認）で扱う。

## 検査と CI

- 形と静的検査: Spotless＋palantir-java-format（Java・Kotlin DSL・メールのテンプレートのライセンスヘッダー）、Prettier・oxlint・ESLint・Stylelint（`frontend/`）、SpotBugs＋FindSecBugs（`backend/config/spotbugs-exclude.xml`）、Gitleaks（pre-commit と CI）、OSV-Scanner。
- CI: `.github/workflows/ci.yml` は `./gradlew verify` を1回、`timeout-minutes: 60`。サブモジュールは固定先を取得し、WAR を成果物として保存する。E2E（ブラウザの axe のコントラストの検査を含む）は CI の外（K-2、`component-inventory.md` の `frontend-e2e`）。
- 観測: p95 の警報3件がバケットの無い指標を問い合わせており、値を持たない（K-6、`architecture.md` の Interaction Diagrams 1）。

## 画面の質

### K-3 make-you-chic-ui の直しが及ばない、アプリ自身の CSS の文字の色

確かめた事実:

- `frontend/src/features/preferences/PreferencesForm.css` 47 行の `.preferences-choice-error` は `color: var(--color-danger)`。README 948 行も「同じ色」と書く。make-you-chic-ui の直し（K-1）は FormField の誤りの文字を `--color-danger-text`（dark は `--red-400`）に切り替えるが、この独自のクラスには及ばない。
- `frontend/src/features/dsl/DslSubmitForm.css` 27 行の `.dsl-link` と `frontend/src/app/pages/Page.css` 33 行の `.page-link` は `color: var(--color-primary)`（文字の色に塗りの色を使う）。`.page-link` は `frontend/src` のどの `.tsx` にも使われていない（`grep`）。

見立て（未検証）: dark のテーマで `.preferences-choice-error` は FormField の直す前と同じコントラスト不足になりうる。サーバーが誤りを返したときだけ出るため、E2E の 080 で検査されていない可能性がある（K-2）。`.dsl-link` は E2E の axe の対象の画面に入っていない。直すなら K-1 で足された文字用のトークン（`--color-danger-text` など）に切り替える形が考えられる。`.page-link` は使われていないため、消すか残すかを決める。

## 文書

- `README.md`（約 950 行）は起動・環境変数・監視・監査・招待・既知の制約・戻し方まで持つ。コードのコメントは日本語で、決定の出どころ（Intent・単位・決定の番号）を丁寧に書いている。
- README の「既知の制約（ブランドカラーのコントラスト）」と E2E の一覧の説明は、K-1 の更新で古くなる（K-2）。
- 承認済みの運用の記録に、コードと合わない記述がある（K-8、`api-documentation.md`）。

### K-9 運用の手順書・警報の説明・ログの問い合わせがリポジトリの中（`aidlc/` の外）に無い

確かめた事実: `runbooks.md`・`alarms.md`・`log-queries.md` は `aidlc/spaces/default/intents/*/operation/` の Intent の記録の中にだけあり、リポジトリのアプリの側（`aidlc/` の外）には無い。README は運用の一部（監視・監査・戻し方）を持つが、警報ごとの説明と手順書は持たない。

帰結（決める点）: K-8 の「README と手順書で正す」の手順書の置き場（README の節・この Intent の運用の記録・新しい文書のどれか）を要件で決める必要がある。承認済みの前の Intent の記録は書き換えず、差を明記する（`project.md` の Way of Working・Change Control）。

## 技術的負債の一覧

| 項目 | 所見 | 重さ |
|---|---|---|
| コントラストの Not Met と E2E の既知の違反の一覧 | K-1・K-2・K-3 | 高（依頼の対象） |
| CI の2つの時間切れ | K-4・K-5 | 高（CI が赤いまま） |
| p95 の警報3件が働かない | K-6 | 高（監視の穴） |
| Dependabot の作業ブランチ 15 本と固定の決まりとの衝突 | K-7 | 中 |
| 運用の記録の誤りと、手順書の置き場が無いこと | K-8・K-9 | 中 |
| `team.md` の記述の古さ | K-10 | 低（記録の食い違い） |
