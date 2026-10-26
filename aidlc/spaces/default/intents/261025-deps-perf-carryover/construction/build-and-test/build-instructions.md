# ビルドの手順（261025-deps-perf-carryover）

**Collaborator:** aidlc-quality-agent

範囲は bugfix（深さ Minimal、Test Strategy Minimal）、brownfield。この手順は、`develop` の先頭（B1〜B4 を統合した `5bebcab`）をビルドして検査するためのものです。パスはリポジトリのルートからの相対パスで書きます。値（資格情報・接続先）は表示しません。

## 1. 前提の道具

| 道具 | 版・場所 | 確かめ方 |
|---|---|---|
| JDK | Gradle のツールチェーンが取得（`gradle/libs.versions.toml`・`backend/build.gradle.kts`） | `./gradlew --version` |
| Node | `frontend/.nvmrc` の版。Volta の `$HOME/.volta/bin` を `PATH` の先頭に置く | `node -v` |
| colima（Docker） | 動いていること。対象DB（MySQL・MariaDB・PostgreSQL）の結合テストが Testcontainers で使う | `colima status` |
| `osv-scanner`・`gitleaks` | `README.md` の「前提の道具」 | `which osv-scanner gitleaks` |
| `jq` | 負荷の試験の報告の読み取り | `which jq` |
| Playwright の Chromium | `@playwright/test` と同じ版（1.64.1） | `cd frontend && npx playwright install chromium`（初回と Playwright の更新のとき） |
| k6 | 手元には入れない。`perf/versions.env` の `K6_IMAGE`（版とダイジェストで固定）を `docker run` で使う | `source perf/versions.env; echo "$K6_IMAGE"` |

## 2. 環境の設定（コマンドを流すシェルで）

```bash
export PATH="$HOME/.volta/bin:$PATH"
# colima の PC では次の 2 つを渡す。渡さないと対象DB のテストが SKIPPED になり、パッケージごとのカバレッジの下限で失敗する
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

- `.env`・`.env.targetdb` は開かない・書かない。ビルドとテストは `.env` を必要としない。
- 依存関係は lockfile どおりに入れる（`backend/gradle.lockfile`、`frontend/package-lock.json`、`vendor/make-you-chic-ui/package-lock.json`）。npm は `frontend/.npmrc` の `ignore-scripts=true` のまま。`vendor/` の中身は変えない。
- サブモジュール（`vendor/make-you-chic-ui`・`vendor/java-mustache-processor`）は固定先のコミットで取得されていること（`git submodule status` で先頭に `-` や `+` が付かない）。

## 3. ビルドと全体の検査（1 コマンド）

```bash
caffeinate -dims ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify
```

- `verify` はフォーマット → リンタ → ライセンスヘッダー → ビルド（型検査を含む。画面のビルドと WAR）→ 単体テスト → 結合テスト（対象DB のコンテナを使う）→ カバレッジ下限（行 80%・分岐 70%、パッケージごとを含む）→ 秘密情報の検出（Gitleaks）→ 静的解析（SpotBugs + FindSecBugs）→ 依存関係の脆弱性検査（OSV-Scanner）を、この順で 1 つのタスクとして流す。
- `:backend:cleanTest :backend:cleanIntegrationTest` を付けるのは、テストのタスクが UP-TO-DATE で飛ばされることがあり、件数とカバレッジを実測の数字で報告するため（`project.md` の決まり）。
- 流す前に `pmset -g assertions` で眠らない状態（`PreventUserIdleSystemSleep` が 1）を、流した後に `pmset -g log` で窓の中に `Sleep`・`DarkWake` が無いことを確かめる。入っていたらその回の結果は使わずに流し直す。

## 4. 成果物と確かめ

| 確かめること | コマンド | 期待 |
|---|---|---|
| ビルドが成功 | 上の `verify` の最後の行 | `BUILD SUCCESSFUL` |
| 単体・結合テストの件数 | `backend/build/test-results/test/*.xml`・`backend/build/test-results/integrationTest/*.xml` の `tests`・`failures`・`errors`・`skipped` の合計 | 失敗 0・エラー 0。SKIPPED は他の DB 種別向けの `assumeTrue` だけ |
| 画面のテストの件数 | `verify` の出力の Vitest の最後の行 | 失敗 0 |
| カバレッジ（バックエンド） | `backend/build/reports/jacoco/` の `jacocoTestReport.xml` | 全体 行 80%・分岐 70% 以上、`packagesJudgedByTotal` の 7 パッケージを除く各パッケージも同じ |
| カバレッジ（画面） | `frontend/coverage/` | 全体 行 80%・分岐 70% 以上（`vitest.config.ts` の `thresholds`） |
| WAR | `backend/build/libs/mastersmith.war` | 存在し、`shasum -a 256` を記録する |
| 依存関係の脆弱性 | `./gradlew osvScan --rerun` | 最後の行が `OSV-Scanner: 失敗の条件に当たるもの 0 件、警告 N 件` で `BUILD SUCCESSFUL`（`verify` の中の `osvScan` は lockfile が変わらないと UP-TO-DATE で飛ばされるため、流し直す） |
| axe-core が成果物に入っていない | `grep -rIl 'axe-core\|axe\.min' frontend/dist` と `unzip -l backend/build/libs/mastersmith.war \| grep -i axe` | どちらも何も出さない（終わりの値 1） |

## 5. 画面からの一連の操作（E2E）

`verify` と CI の外。手順は `README.md` の「ビルドした WAR での画面の確認（E2E）」と `performance-test-instructions.md` の 3 節。

```bash
docker compose --profile mail up -d mailpit        # Mailpit が動いていれば不要
docker compose -f compose.yaml -f docker/e2e/compose.targetdb-port.yaml --profile targetdb-postgres up -d targetdb-postgres   # 見本の PG を 127.0.0.1:15432 に開ける
caffeinate -dims ./gradlew e2eTest
docker compose --profile targetdb-postgres up -d targetdb-postgres     # 上乗せ無しで作り直してポートを閉じる
```

- 配備したアプリは止めない。E2E は WAR を別の番号で起動する。
- 報告 `frontend/test-results/e2e-results.json` に資格情報・接続先の値が入っていないことを、文字列の検索の件数（値は表示しない）で確かめる。

## 6. つまずきやすい点

| 症状 | 原因と対処 |
|---|---|
| 対象DB のテストが SKIPPED になり、パッケージごとのカバレッジで失敗 | `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡していない。2 節のとおり渡して流し直す |
| `verify` の件数が前回と同じで古い | テストが UP-TO-DATE。`:backend:cleanTest :backend:cleanIntegrationTest` を付ける |
| `e2eTest` が始める前に止まる | Mailpit か見本の PG に届かない。メッセージの手順で起動する。起動・停止は `e2eTest` が行わない |
| 画面のテストが長く止まって時間切れ | PC のスリープ（DarkWake の Thermal Emergency）。`caffeinate -dims` で包み、`pmset -g log` で確かめ、入っていたら流し直す。時間の上限は原因を確かめずに延ばさない |
| `osvScan` が新しい High で失敗 | ソースを変えなくても公表で変わる。原因の依存と版を記録し、依頼者に諮る。上限や線は緩めない |
