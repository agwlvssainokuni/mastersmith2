# ビルドの手順（Intent 260928-quality-followup）

この Intent は既存のビルドの形を変えていない。1コマンドの検査 `./gradlew verify` が入口である（`project.md` の Way of Working）。コード生成の記録（`aidlc/spaces/default/intents/260928-quality-followup/construction/code-generation/code-summary.md`）と計画（`code-generation-plan.md` の Step 25）の最後の検査の手順を、統合の後の `develop`（`d1fda19`）で確かめ直したものを書く。

## 前提の環境

- JDK 25（`gradle/libs.versions.toml` の `java = "25"`、CI も 25）。Gradle は wrapper（この Intent で 9.8.0 に更新）。
- Node.js と npm（`frontend/`。`npm ci` で lockfile どおりに入れる。`./gradlew` から呼ばれる）。
- colima（コンテナの実行環境）。対象DB（MySQL・MariaDB・PostgreSQL）の結合テストのため、次の2つの環境変数をシェルに渡す（渡さないと対象DB のテストが SKIPPED になる。`project.md` の Testing Posture）。

```bash
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

- サブモジュールは固定先で取り込む（`git submodule update --init`）。`vendor/make-you-chic-ui` の固定先は `310e1ecf2fe8b4a388cb8ae098a59144dc93acc5`（この Intent で `735ef04` から更新）。

## ビルドと検査

```bash
caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify --console=plain
```

- `verify` はサブモジュールの準備、フォーマット・リンタ・ライセンスヘッダー・ビルド、単体テスト、結合テスト（対象DB を含む）、カバレッジの下限、SpotBugs・OSV-Scanner・Gitleaks、WAR の組み立ての順に流す。
- テストの件数とカバレッジを報告するときは、`:backend:cleanTest :backend:cleanIntegrationTest` を付けて実測する（`project.md` の Testing Posture）。
- `osvScan` は入力が変わらないと UP-TO-DATE で飛ばされる。依存の脆弱性の今の結果を見るときは、`./gradlew osvScan --rerun` で流し直す（新しい知らせは入力が同じでも出るため）。
- 成果物は `backend/build/libs/mastersmith.war`（画面のビルド結果を同梱した実行可能 WAR）。

## よくあるつまずき

- 対象DB のテストが SKIPPED になり、パッケージごとのカバレッジの下限で失敗する: 上の2つの環境変数を渡していない。
- `osvScan` が High で止まる: 新しい脆弱性の知らせ。`team.md` の受け方（手元で版と lockfile を更新して `verify`）で取り込む。この Intent では Jackson 3.1.6 がこれに当たった（コード生成の G4）。
- PC のスリープで長い検査が崩れる: `caffeinate -i` で全体を包む。
