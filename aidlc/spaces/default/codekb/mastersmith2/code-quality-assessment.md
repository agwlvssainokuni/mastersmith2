# コードの質の評価（mastersmith2）

件数はファイルを数えた値・検索で数えた値で、テストを実行した値ではない（Gradle・npm・Docker は実行していない）。この文書に本文を書いた所見は K-30 で、ほかは持ち主の文書を参照する（一覧は `business-overview.md`）。パスは `backend/src/main/java/cherry/mastersmith/` の下のものはそれを省いて書く。

## テスト

| 対象 | 置き場と数（2026-10-04 に開発担当がファイルを数えた値） | 道具 |
|---|---|---|
| バックエンドの単体 | `backend/src/test/java/`、`*Test` 173 件（タスク `test`） | JUnit 5・jqwik・ArchUnit |
| バックエンドの結合 | 同上、`*IT` 133 件（タスク `integrationTest`）。内部DB は組み込みの H2。漏えいの確かめ `*SecretLeakIT` 12 件 | Spring Boot Test・Testcontainers（対象DB だけ）・SubEthaSMTP |
| 画面 | `frontend/src/**/*.test.ts(x)` 110 件 | Vitest・Testing Library・vitest-axe・fast-check |
| E2E | `frontend/e2e/*.e2e.ts` 13 本。`verify` と CI の外 | Playwright |

### 今回の Intent に関わる既存のテスト

| 論点 | テスト（今回読んだ範囲） | 足りないところ |
|---|---|---|
| K-25 初期管理者 | `backend/src/test/java/cherry/mastersmith/user/service/InitialAdminIT.java`（テストの名前の範囲） | 監査の記録と救済の口は機能が無いため、テストも無い |
| K-27 BUSY | `backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminBusyApiIT.java`（別のトランザクションで行を持ち続けて5つの操作の 409 を作る） | L3・L4 のログの `traceId` の結び付きを確かめていない（`traceId` の検索で 0 件） |
| K-28 二重の ERROR | 該当のテストは見つけていない（開発担当の検索の範囲） | フィルターの中で接続を借りられない場合のログの件数を確かめるテストが無い |
| K-29 言語の欄 | `frontend/src/features/useradmin/EditProfileDialog.test.tsx` があることだけ（中身は今回読んでいない） | Enter で送信した後のフォーカスの行き先を確かめるテストがあるかは確かめていない（前回の記録では、jsdom は `inert` でフォーカスを止めないなど、ブラウザとフォーカスの扱いが違う） |
| K-31 イメージ | `TargetDbImages` を使う対象DB の結合テスト（名前だけ） | 3か所の版とダイジェストが一致することを確かめる仕組みが無い |

## カバレッジ

- バックエンド: JaCoCo。単体と結合を合わせた全体で行 80%・分岐 70%、加えて `packagesJudgedByTotal` 以外のすべてのパッケージごとに同じ下限（`backend/build.gradle.kts` の `jacocoTestCoverageVerification`）。
- 画面: `frontend/vitest.config.ts` の `thresholds`（行 80%・分岐 70%。前回までの記録で、今回は読んでいない）。
- 計測から外すのは起動クラス・設定値だけのクラス・自動生成・`vendor/` に限る（`team.md` の Testing Posture）。

### K-30 `team.md` の「12 パッケージ」は古く、一覧は今 7 個

確かめた事実:

- `backend/build.gradle.kts` の `packagesJudgedByTotal`（225〜233 行）は次の **7 個** である（いずれも `cherry.mastersmith.` の下）: `access.service`・`audit.repository`・`common.error.domain`・`common.error.service`・`common.health`・`common.i18n.domain`・`common.web`。
- 同じファイルの注記によると、Intent `260930-user-admin` の B1 で `auth.domain`・`auth.repository`・`access.domain` を、B4 で `common.error.web`・`common.observability` を外した。
- `team.md` の Testing Posture は「2026-09-29 の時点で 12 パッケージ」と書いており、今のビルドと食い違う。`team.md` は依頼者が直す決まりの文書で、今回の Intent の片付けの対象である。

今回の Intent への関わり（事実と見立て）:

- K-25（`user.service`・`audit.domain`・`audit.service`）、K-27（`useradmin.*`・`common.persistence`、テストだけの見込み）、K-28（`common.error.web`・`auth.web`）で手を入れそうなパッケージは一覧に無く、すでにパッケージごとの下限の対象である（事実）。
- K-25 で監査の行の扱いを変えて `audit.repository` に手を入れる場合、または K-28 で `common.error.service`・`common.error.domain`・`common.web` に手を入れる場合は、`team.md` の決まりによりテストを足して下限を満たし、一覧から外す作業が付く（見立て。どこに手が入るかは設計で決まる）。

## 検査と CI

- 形と静的検査: Spotless（palantir-java-format・ライセンスヘッダー）、SpotBugs ＋ FindSecBugs と関門 `spotbugsGate`（除外は `backend/config/spotbugs-exclude.xml`）、ArchUnit の層と境界の検査。
- 画面: Prettier・oxlint・ESLint（`frontend/eslint.config.js`）・Stylelint・型検査・ライセンスヘッダーの検査。
- 秘密情報: Gitleaks（`.gitleaks.toml`、pre-commit と `verify`）。依存: OSV-Scanner（`verify`）と Dependabot（`.github/dependabot.yml`: gradle・npm・github-actions・docker・docker-compose。対象のディレクトリの抜けは K-31、`dependencies.md`）。
- CI: `.github/workflows/ci.yml`（GitHub Actions。`uses:` はすべてコミットのハッシュで固定し、版をコメントに書く。開発担当の検索）。E2E は CI の外（`team.md`）。
- 上の検査の中身は前回までの記録で、今回は `backend/build.gradle.kts` のカバレッジの範囲と `.github/dependabot.yml` だけを深く読んだ。

## 文書

- `README.md`（1,188 行）: 起動・配備・設定の一覧・既知の制約。初期管理者の記述は、初めての起動で `.env` に設定を入れ、起動のログの INFO（キー `maskedEmail`）で作成を確かめ、2回目以降の起動では作られない、という手順を書く（266 行。今回はその範囲だけ読んだ）。救済の手順（前の Intent の RB-22）は README ではなく前の Intent の記録にあり、アプリに救済の口が無いことは K-25。
- `perf/README.md`（495 行）: 負荷の試験の手順（今回はイメージの行の検索だけ）。
- コードのコメント: Javadoc・JSDoc は日本語で、要件・設計の ID（BR・NFR・FR）へのつながりが細かく書かれている（今回読んだファイルで確かめた）。

## 技術的負債の一覧

今回（2026-10-04、コミット `47ec27b`）の走査で確かめたものだけを並べる。前回までの一覧（監査の3ファイルの肥大・要求の文脈の読み取りの複製・大きめの hook など）は今回確かめ直していないため載せていない。

| 項目 | 内容（場所） | 所見 | 重さ |
|---|---|---|---|
| 起動時の運用の操作が監査の外 | 初期管理者の作成が監査に残らず、救済の口も無い。`audit_events.source_ip` が `NOT NULL` | K-25（`architecture.md` の Interaction Diagrams 1） | 高（今回の Intent で決めて実装する） |
| 設定の型の `toString` がメールアドレスを出す | `user/service/InitialAdminProperties.java` 33 行 | K-25 の一部 | 中（ログに出る経路は見当たらないが、`project.md` の Forbidden に関わる） |
| ログインの p95 の余裕の縮みの原因が切り分けられていない | 停止の判定は問い合わせを増やしていない。ぶれとの区別には同じ条件の繰り返しの比較が要る | K-26（`architecture.md` の Interaction Diagrams 2） | 中（目標は緩めない） |
| BUSY のログの結び付きが確かめられていない | `UserAdminBusyApiIT` は 409 だけを確かめる | K-27（`architecture.md` の Interaction Diagrams 3） | 低〜中（テストを足す見込み） |
| 例外の ERROR が二重に出うる | フィルターの中の例外が Tomcat と `/error` の両方で ERROR になる見立て。`GlobalExceptionHandler` と `ErrorPathController` の ERROR は同じ文 | K-28（`architecture.md` の Interaction Diagrams 4） | 中（`team.md` の「1回だけ出す」に反する見込み、警報の件数を膨らませる） |
| 送信中に押せなくした選択肢からフォーカスが外れる | `frontend/src/features/useradmin/EditProfileDialog.tsx`、make-you-chic-ui の `RadioGroup` に読み取り専用の口が無い | K-29（`component-inventory.md` の `frontend-feature-useradmin`） | 低〜中（画面の操作性とアクセシビリティ） |
| 決まりの文書の数が古い | `team.md` の「12 パッケージ」と `packagesJudgedByTotal` の 7 個 | K-30（この文書） | 低 |
| イメージの固定先の手での揃え | 3か所の版とダイジェスト、Dependabot の対象の抜け、ダイジェストの無いイメージ | K-31（`dependencies.md`） | 中（揃え漏れで結合テストと手元の環境の版がずれうる） |
| `audit_events` の列が追記ごとに増える | NULL を許す列が V6・V7 で増え、出来事ごとの列の使い方はコメントにしか無い（開発担当の記録） | —（今回の Intent の外） | 低 |
