# ビルドの手順（Intent 261015-deps-ui-carryover）

## 前提

- JDK 25（Temurin）・Node.js（`frontend/.nvmrc` の版）・colima（Docker の実行環境）。手順は `README.md` の「開発の準備」と同じ。
- サブモジュールを固定先で取得する: `git submodule update --init`（make-you-chic-ui は `a9309c4`、java-mustache-processor は `8d44c36`）。
- 対象DB のテストのため、colima を起動し、シェルに次の2つを渡す（`README.md` 575〜577 行）:
  - `export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"`
  - `export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`
- 依存は lockfile どおりに入る（`frontend/package-lock.json` は `npm ci`、`frontend/.npmrc` の `ignore-scripts=true`。Gradle は `backend/gradle.lockfile`）。

## ビルドと検査（1コマンド）

```bash
caffeinate -dims ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify
```

- 中身: フォーマット・リンタ・ライセンスヘッダー・ビルド（型の検査を含む）・backend の単体と結合（3種類の対象DB）・frontend の単体とカバレッジ・カバレッジの下限・Gitleaks・SpotBugs（FindSecBugs）・OSV-Scanner。
- 件数とカバレッジを報告するときは、`:backend:cleanTest :backend:cleanIntegrationTest` を付けて流し直した実測の値だけを使う（`project.md` の学び）。
- 流した後に `pmset -g log | grep -E ' (Sleep|DarkWake) '` で、流した時間に眠りが無いことを確かめる。

## 成果物

- 実行可能 WAR: `./gradlew :backend:bootWar`（`backend/build/libs/mastersmith.war`、画面のビルドの結果を同梱）。イメージは `Dockerfile` が手元の WAR を写すだけのため、配備の前に WAR を作り直して SHA-256 を確かめる（`project.md` の学び）。
- 土台のイメージは `eclipse-temurin:25.0.4.1_1-jre-noble`（ダイジェストで固定）。

## よくある問題

- 対象DB のテストが SKIPPED になる: 上の2つの変数を渡していない。渡して流し直す（飛ばした状態では統合しない）。
- 画面のテストの時間切れ: 描画の後の効果で反映される値を同期で確かめていないかを先に見る（`team.md`）。時間の上限は延ばさない。
