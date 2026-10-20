# ビルドの手順 — Intent 261016-business-data-views

この文書は、今の `README.md`・`build.gradle.kts`・`frontend/package.json`・`docker/` を正として、この Intent の成果物（業務データの一覧・詳細・作成・更新・削除、axe-core 4.14.0、持ち越しのテストの直し）をビルドして確かめる手順を書く。新しい手順は作らず、既にある1コマンドの検査（`./gradlew verify`）と E2E（`./gradlew e2eTest`）の使い方を、この Intent の差に絞って記録する。

依頼者の決定（`build-and-test-questions.md` の Q1: B）により、この段では `develop` の今の形での verify と E2E の全件は流さない。各 Bolt の作業ブランチの上の最後の結果と CI の結果を正とする（`test-results.md`）。この文書は、後で流し直すとき・リリースの前に流すときの手順である。

## 1. 前提の道具

| 道具 | 版 | 確かめ方 | 使う場面 |
|---|---|---|---|
| JDK | 25（Temurin） | `java -version` | ビルド・テスト（`./gradlew checkToolchain` が確かめる） |
| Node.js・npm | 24 | `node -v` | 画面のビルド・テスト（版が 24 でなければ verify が止まる） |
| Gitleaks | 8.30.1 | `gitleaks version` | 秘密情報の検出（verify の 8 の段） |
| OSV-Scanner | 2.6.0 | `osv-scanner --version` | 依存関係の脆弱性の検査（verify の 8 の段） |
| colima（Docker） | Compose v2、VM は CPU 4・メモリ 6GiB | `colima status` | 対象DB の結合テスト（Testcontainers）と E2E の見本の PG・Mailpit |
| Playwright の Chromium | `@playwright/test` と同じ版 | `cd frontend && npx playwright install chromium` | E2E |
| pre-commit | 4.x | `pre-commit --version` | コミットの前の Gitleaks とフォーマット |

- Gradle は Wrapper（`./gradlew`）を使う。依存の取得元は Maven Central だけ（`settings.gradle.kts`）。
- サブモジュールは固定先のコミットで取る: `git submodule update --init`。今の固定先は `vendor/make-you-chic-ui` が `b0a1e79`（B3 で `a9309c4` から上げた）、`vendor/java-mustache-processor` が `8d44c36`（0.1.0）。
  - **注意（記録の食い違い）**: `README.md` の 38 行は make-you-chic-ui の固定先を `a9309c4` と書いたままで、B3 の更新（`a9309c4` → `b0a1e79`）が README に反映されていない。`git submodule status` の値が正。直すかは承認の場で諮る（`build-and-test-summary.md` の 8節）。

## 2. 環境の用意

### 2.1 colima とシェルの環境変数

```bash
colima start --cpu 4 --memory 6
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

- この2つを渡さないと、対象DB の結合テストが SKIPPED になり、パッケージごとのカバレッジの下限で失敗する（`project.md` の学び）。
- 開発中に Docker に届かないときは、対象DB のテストだけを飛ばし「この状態では統合しない」の警告を出す。**その状態では統合しない**（team.md の Way of Working）。CI（`CI=true`）では飛ばさずに失敗する。

### 2.2 設定ファイル

| ファイル | 中身 | この Intent の差 |
|---|---|---|
| `.env`（コミットしない） | 配備したアプリの設定 | 書き込みの4項目（`MASTERSMITH_TARGET_DB_WRITE_*`）と時間の上限（`MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_LIST`・`_DETAIL`・`_WRITE`）が増えた。見本は `.env.example` |
| `.env.targetdb`（コミットしない） | 見本の対象DB の管理者・読み取り・書き込みのパスワード | 書き込みのアカウント `mastersmith_writer` のパスワードが増えた。見本は `.env.targetdb.example` |
| `docker/targetdb/{postgres,mysql,mariadb}/03-writer-account.sh` | 見本の DB の書き込みのアカウント（行の操作だけ、DDL なし） | 新しい。既にあるボリュームへの足し方は README の「既にある見本の DB に書き込みのアカウントを足す（U2）」 |
| `docker/e2e/compose.targetdb-port.yaml` | E2E の間だけ見本の PG を `127.0.0.1:15432` に開ける上乗せ | 新しい（U3 の B6） |

- `.env` と `.env.targetdb` を開いて値を表示しない。変えるときは、変更の前にリポジトリの外（ホームの下）へ中身を表示せずに複写する（`project.md` の学び）。

### 2.3 E2E の前に起動しておくもの

```bash
docker compose --profile mail up -d mailpit
docker compose -f compose.yaml -f docker/e2e/compose.targetdb-port.yaml \
  --profile targetdb-postgres up -d targetdb-postgres
```

- 15432 を開けた直後に、誤ったパスワードが拒まれることを確かめる（`./gradlew e2eTest` の前提の確かめ (d) が毎回行う。拒まれなければ、すぐに上乗せ無しで作り直して閉じる）。
- 流し終えたら上乗せ無しで作り直してポートを閉じる: `docker compose --profile targetdb-postgres up -d targetdb-postgres`。

## 3. 依存の入れ方

| 対象 | コマンド | 守り |
|---|---|---|
| make-you-chic-ui | `./gradlew vendorInstall vendorBuild vendorUnchanged` | lockfile どおり（`npm ci`）。vendor の `npm ci` は `ignore-scripts` の外（`project.md` の学び）。`vendorUnchanged` で中身が変わっていないことを確かめる |
| 画面 | `./gradlew frontendInstall`（`npm ci`） | `frontend/.npmrc` の `ignore-scripts=true`。`npm ls axe-core` が 4.14.0 の1つであること（B7） |
| バックエンド | Gradle が `backend/gradle.lockfile` どおりに取る | 取得元は Maven Central だけ |

## 4. ビルドのコマンド

| 目的 | コマンド | 成果物・確かめ |
|---|---|---|
| 1コマンドの検査（統合の前の関門） | `caffeinate -dims ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` | 0〜9 の段（準備・フォーマット・リンタ・ライセンスヘッダー・ビルド・単体・結合・カバレッジ・安全の検査・成果物）。件数とカバレッジは `cleanTest`・`cleanIntegrationTest` を付けた実測だけを報告する（`project.md`） |
| WAR だけ | `./gradlew :backend:bootWar` | `backend/build/libs/mastersmith.war`（画面の `dist` を同梱） |
| 段ごと | `./gradlew verifyFormat`・`verifyLint`・`verifyBuild`・`verifyUnitTest`・`verifyIntegrationTest`・`verifyCoverage`・`verifySecurity`・`verifyArtifact` | 失敗した段だけを流し直すとき |
| 画面の量 | `cd frontend && npm run build && npm run bundle:size` | 入口の gzip が 500KB の目安を超えたら警告だけ（U3 の NFR1.10） |
| `dist` に axe-core が入らないこと | `grep -c -e axe-core -e axe.run -e 'Deque' -r frontend/dist` | 0 件（U4 の NFR4.8） |

### 4.1 長いテストの眠りの守り

- verify・E2E・k6 は `caffeinate -dims` で包む。流す前に `pmset -g assertions` で `PreventUserIdleSystemSleep` が 1 であること、流した後に `pmset -g log` に Sleep・DarkWake が無いことを確かめ、入っていたら結果を使わずに流し直す（`project.md`）。

## 5. ビルドの確かめ

| 確かめ | 合格の線 | この Intent の最後の値（作業ブランチの上、B7 のレビューの直しの後） |
|---|---|---|
| `verify` の結果 | BUILD SUCCESSFUL | 通過（23分24秒） |
| `verify` の時間 | CI の上限 60 分の7割の約 42 分以内（team.md） | 23分24秒 |
| バックエンドのカバレッジ（全体） | 行 80%・分岐 70% 以上 | 行 97.64%・分岐 91.23% |
| パッケージごとの下限（新しいパッケージ） | 行 80%・分岐 70% 以上 | `jacocoTestCoverageVerification` が通過（`businessdata.*`・`targetdb.*` を含む） |
| フロントエンドのカバレッジ（全体） | 行 80%・分岐 70% 以上 | 行 95.06%・分岐 89.82% |
| SpotBugs の関門 | priority 1 と `SQL_`・`PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION` が 0 件 | 0 件（除外は `RowSqlExecutor` の読み取り2・書き込み5のメソッドとパターン名で絞ったものだけ） |
| OSV-Scanner | 失敗の条件 0 件 | 0 件（警告 18 件。開発用の依存と vendor の lockfile） |
| Gitleaks | 漏れ 0 件 | 0 件 |
| WAR | `backend/build/libs/mastersmith.war` がある・DSL の JSON Schema が入る（`verifyDslSchemaInWar`） | 通過 |

## 6. よくある失敗と直し方

| 起きたこと | 原因 | 直し方 |
|---|---|---|
| 対象DB のテストが SKIPPED になり、パッケージごとのカバレッジで落ちる | `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡していない、colima が止まっている | 2.1 のとおり渡して流し直す。飛ばした状態では統合しない |
| テストの件数・カバレッジが前回と同じに見える | テストのタスクが UP-TO-DATE で飛んだ | `:backend:cleanTest :backend:cleanIntegrationTest` を付けて流し直す |
| OSV-Scanner が前回の結果のまま | lockfile が変わらず `osvScan` が UP-TO-DATE | `./gradlew osvScan --rerun` |
| `frontendLintCss` で落ちる | 新しい CSS のライセンスヘッダーの直後に空行が無い（stylelint の `comment-empty-line-before`。B6 の1回目） | 空行を足す |
| `./gradlew e2eTest` が前提の確かめで止まる | Mailpit が動いていない、15432 が開いていない、DB `business` の PUBLIC に `TEMPORARY` がある | 2.3 のとおり起動する。`TEMPORARY` は README の取り上げの1文を流す |
| Gradle の作業プロセスの手元の通信が止まり、Mailpit の確かめで止まる | 実行環境の sandbox の制限（B7 の1回目） | sandbox の外で流し直す |
| `npx playwright test` を直接流すと設定の読み込みで止まる | 対象DB の環境変数は `./gradlew e2eTest` が作って渡す | `./gradlew e2eTest -Pe2e.files=e2e/<ファイル>` で流す |
| 不安定なテストが1回だけ落ちる | 3件は「不安定と確かめられていない」扱い（`test-results.md` の 4節） | team.md の決まりのとおり、再現の試みの上限を先に決めて試し、記録する。同じテストが二度目に落ちたら直すまで進まない |

## Sources

- `README.md`（前提の道具・取得と準備・1コマンドの検査・E2E・対象DB の結合テストとコンテナの実行環境・既にある見本の DB に書き込みのアカウントを足す）
- `build.gradle.kts`（`verify` の段と `e2eTest`・`e2eTargetDbCleanup`）、`frontend/package.json` の scripts、`.github/workflows/ci.yml`（`timeout-minutes: 60`）
- 各単位の `code-generation/code-summary.md`・`generation-notes.md`（特に U3 の 10節・11節）
- `aidlc/spaces/default/memory/team.md`・`project.md`

## Assumptions & Open Questions

- [assumption] この段では `develop` の今の形で verify を流さない（Q1: B）。5節の値は B7 の作業ブランチの上の最後の回で、`develop` の `d220447` は B7 を squash で統合したもののため中身は同じと見る。合わせた形の確かめは CI が受け持つ（`test-results.md` の 6節）。
- README の make-you-chic-ui の固定先の記述（`a9309c4`）の直しは、承認の場で諮る。
