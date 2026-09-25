# 配備の記録（deployment-log）

Intent 260924-followup-fixes の配備。2026-09-25、開発者の PC 上のコンテナ（`compose.yaml`、プロジェクト名 `mastersmith`）。手順は `aidlc/spaces/default/intents/260924-followup-fixes/operation/deployment-pipeline/deployment-strategy.md` 2節、決定は `deployment-execution-questions.md`（Q1: A 今すぐ、Q2: A 戻し方の前提を使い捨てのコンテナで確かめる）。

## 1. 配備した版

| 項目 | 値 |
|---|---|
| 配備した版 | `develop` の `87cc0fc`（作業ブランチ `fix/260924-followup-fixes` を fast-forward で取り込んだもの。アプリの中身は C6 `295407f` と同じ） |
| 新しいイメージ | `mastersmith:local` = `sha256:a9cfa9dc…` |
| 戻し先 | `mastersmith:pre-followup` = `sha256:1585bd4e…`（前の Intent で配備した版） |
| 見本の対象DB | `mastersmith-targetdb-postgres-1`（`postgres:18.6`、新しい起動の入口で作り直し。ボリュームはそのまま） |

## 2. 手順と結果

| 順 | 手順 | 結果（UTC） |
|---|---|---|
| 1 | アプリのソースの未コミットの変更の確かめ | 無し（ワークフローの記録と監査ログのディレクトリだけ変更あり。project.md の決まりで除いて判断） |
| 2 | `develop` へ取り込み | `git fetch . fix/260924-followup-fixes:develop`（fast-forward だけを許す形）で `develop` を `87cc0fc` に進め、`develop` に切り替えた。merge コミットは無い。作業ツリーのワークフローの記録の未コミットの変更を保ったまま切り替えるため、`git switch` による取り込みではなくこの形にした |
| 3 | `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` | 成功（4分27秒）。単体 726・結合 382（対象DB を含む）・画面 316 件、失敗 0・飛ばし 0。Gitleaks 検出なし。WAR 87,091,735 バイト |
| 4 | `docker tag mastersmith:local mastersmith:pre-followup` | `sha256:1585bd4e…` を指す |
| 5 | `.env` の複写 | `~/.mastersmith-backup/env-202609251129-before-followup`（権限 600）。元と同じ中身（`cmp`）。中身は表示していない |
| 6 | `.env` の2行を `.env.targetdb` へ | `.env.targetdb`（権限 600、Git 管理外）に `MASTERSMITH_SAMPLE_TARGETDB_ADMIN_PASSWORD`・`MASTERSMITH_SAMPLE_TARGETDB_READER_PASSWORD` の2行。`.env` の空でない行は 22 → 20、`MASTERSMITH_SAMPLE_TARGETDB_` の行は 0 |
| 7 | `docker compose --profile targetdb-postgres up -d --build` | 02:29:42 開始。アプリと見本の対象DB のコンテナを作り直した |
| 8 | healthy | 02:29:49 にアプリが healthy（止まっていたのは約 7 秒）。見本の対象DB は Up |
| 9 | 見本の対象DB の起動 | `database system is ready to accept connections`。ERROR 0 件。起動の入口は `sh -c` で包んだ形（`deployment-strategy.md` の案 A）、`CMD` は `postgres` |
| 10 | 上限と環境変数の名前 | アプリ `2147483648 4000000000`（メモリ 2g・CPU 4）。アプリの環境変数に `MASTERSMITH_SAMPLE_TARGETDB_` の名前 0 件（FR6.2）。見本の対象DB が2項目を持つ |
| 11 | スモークテスト | すべて通過（`smoke-test-results.md`） |
| 12 | 配備の完了 | いま動いている版は `87cc0fc` |

## 3. 戻し方の前提の確かめ（Q2: A）

- 使い捨てのコンテナ `mastersmith-rbcheck`（イメージ `mastersmith:pre-followup`、`--env-file .env`（2行を除いた新しい `.env`）、メモリ 2g・CPU 4、一時のボリューム、ポートの公開なし）を起動した。
- 約 9 秒で `/actuator/health` が 200。ログは INFO 42・WARN 2（起動の既存の案内）、ERROR 0。OOMKilled false。
- 確かめた後、コンテナと一時のボリュームを消した。配備したアプリとデータには触れていない。
- 確かめたのは「新しい `.env` で前の版が起動して健全になる」こと。配備した内部DB のデータでの起動は確かめていない（スキーマは変わらないため、同じ形で起動できる見込み）。

## 4. 片付けと残したもの

- 残したもの（Git 管理外、ホームの下）: `~/.mastersmith-backup/env-202609251129-before-followup`（`.env` の複写）、`~/.mastersmith-backup/mastersmith-data-202609251135-after-followup.tgz`（監査イベントの確かめで取った内部DB の複写）。
- 残したイメージ: `mastersmith:pre-followup`（戻し先）、`mastersmith:followup-fixes`（Build and Test の試験のイメージ。配備には使っていない。不要なら依頼者の判断で消してよい）。
- `origin` へのプッシュは依頼者が行い、その後の CI（GitHub Actions）の結果を確かめる（`cd-config.md` 3節の 7）。

## Sources

- `aidlc/spaces/default/intents/260924-followup-fixes/operation/deployment-pipeline/cd-config.md`
- `aidlc/spaces/default/intents/260924-followup-fixes/operation/deployment-pipeline/deployment-strategy.md`
- `aidlc/spaces/default/intents/260924-followup-fixes/operation/deployment-pipeline/rollback-runbook.md`
- `aidlc/spaces/default/intents/260924-followup-fixes/construction/build-and-test/test-results.md`
- `aidlc/spaces/default/intents/260924-followup-fixes/operation/deployment-execution/deployment-execution-questions.md`

## Assumptions & Open Questions

- CI の結果は、依頼者のプッシュの後に確かめる（この記録の時点では未確認）。
- 戻し方の前提は、新しい `.env` と一時のボリュームで確かめた。配備した内部DB での前の版の起動は確かめていない（スキーマの変更が無いため、同じ形で起動できる見込み）。
