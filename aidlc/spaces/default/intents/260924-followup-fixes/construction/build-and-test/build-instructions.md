# ビルドの手順（build-instructions）

Intent 260924-followup-fixes（前の Intent で後に回した小さな修正7件）の変更を、作業ブランチ `fix/260924-followup-fixes` でビルドして確かめる手順。統合の前の関門は `./gradlew verify` の1コマンド（team.md の Way of Working、project.md の学び）。

## 1. 前提

- colima の VM が動いていること（この PC は CPU 4・メモリ 6GiB。`colima list` で確かめる）。コンテナの実行環境が無いと対象DB（MySQL・MariaDB・PostgreSQL）の結合テストが飛ばされ、飛ばした状態では統合しない（team.md）。
- シェルに次の2つを渡す（README「手元で試す対象DB」。渡さないと Testcontainers が colima の Docker を見つけられず、対象DB のテストが SKIPPED になり、パッケージごとのカバレッジの下限で失敗する。Code Generation の段で確かめた）。

```bash
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

- サブモジュールは固定先 `edb1f943c0e66293494fa974605f34fcd7e258d7` で取り出す（`git submodule update --init`）。`vendor/make-you-chic-ui` の中は変えない（`./gradlew vendorUnchanged` が確かめる）。
- 依存は lockfile どおりに入れる（`frontend/` と `vendor/make-you-chic-ui` は `npm ci`。`verify` の準備の段が行う）。

## 2. ビルドと検査

```bash
# 統合の前の関門（フォーマット → リンタ → ライセンスヘッダー → ビルド → 単体 → 結合（組み込みの H2 と対象DB のコンテナ）→ カバレッジの下限 → 安全の検査 → 成果物）
# テストの件数とカバレッジは UP-TO-DATE を避けて実測する（project.md の Testing Posture）
caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify

# verify の外の E2E（ビルドした WAR を起動し Playwright で確かめる）
caffeinate -i ./gradlew e2eTest
```

## 3. 試験のためのイメージ

依頼者の判断（Build and Test の問い）で、配備したアプリが使うタグ `mastersmith:local` は上書きしない。試験には別のタグのイメージを作る。

```bash
./gradlew :backend:bootWar && docker build -t mastersmith:followup-fixes .
```

配備（`mastersmith:local` の作り直し）は Deployment Execution の段で行う。

## 4. ビルドの確かめ

- `verify` が `BUILD SUCCESSFUL` で終わり、対象DB のテストが `SKIPPED` でない（Testcontainers の警告が無い）こと。
- `backend/build/libs/` に実行可能 WAR ができ、フロントエンドの `dist` を同梱していること（`verify` の成果物の段）。
- `git status` で `.env`・`.env.targetdb` がコミットの対象に無く、`vendor/make-you-chic-ui` に中身の変更が無いこと。

## 5. よくある失敗

| 症状 | 原因と対処 |
|---|---|
| 対象DB のテストが SKIPPED、パッケージごとのカバレッジで失敗 | `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡していない。1節の2行を渡して流し直す |
| `vendorUnchanged` で失敗 | サブモジュールの中を変えた、または固定先と違う版を取り出している。`git submodule update --init` で戻す |
| テストの件数が前回と同じまま | テストのタスクが UP-TO-DATE で飛ばされた。`:backend:cleanTest :backend:cleanIntegrationTest` を付けて流し直す |
| 長い実行の途中で止まる | PC のスリープ。`caffeinate -i` を付ける（project.md の学び） |
