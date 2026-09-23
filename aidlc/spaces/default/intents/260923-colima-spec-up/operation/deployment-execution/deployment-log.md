# 配備の記録（deployment-log）

Intent `260923-colima-spec-up`（F3・F4 の修正）の配備の記録。手順は `aidlc/spaces/default/intents/260923-colima-spec-up/operation/deployment-pipeline/deployment-strategy.md` 1節。配備先は開発者の PC 上のコンテナ。依頼者の承認（Q1: A）のもとで AI がコマンドを実行した。時刻は日本時間（2026-09-23）。

## 1. 配備した版

| 項目 | 値 |
|---|---|
| 配備した版（コミット） | `10742a3`（修正は `e4b10af`。その後は記録と `perf/README.md` の文面のコミットだけ） |
| 直前の版 | `3287050` |
| イメージ | `mastersmith:local`（`sha256:8441534a745f…`） |
| 上限 | メモリ 2GiB（2147483648）、CPU 4（NanoCpus 4000000000） |
| colima の VM | CPU 4、メモリ 6GiB（Build and Test の段で作り直し済み） |

## 2. 手順と結果

| 順 | 時刻 | 手順 | 結果 |
|---|---|---|---|
| 1 | 17:11:20 | 作業ツリーの確認 | アプリのソースに未コミットの変更なし（この段の記録のディレクトリだけが未追跡） |
| 2 | 17:11:20 | ハッシュを控える | `10742a3` |
| 3 | 17:11:20 | `./gradlew verify` | 成功（25 秒。前の段から変更が無いため多くのタスクが UP-TO-DATE）、Gitleaks `no leaks found`、`backend/build/libs/mastersmith.war` あり |
| 4 | 17:11:56 | アプリを止めてボリュームを複写 | `mastersmith-data-202609231711-before-deploy.tgz`（Git 管理外） |
| 5 | 17:11:56 | `.env` を複写（中身は表示しない） | `~/.mastersmith-env-backup/env-202609231711`（権限 600、置き場は 700） |
| 6 | 17:11:56 | `.env` の2行を変える | `MASTERSMITH_CONTAINER_CPUS=4`（2 から置き換え）、`MASTERSMITH_CONTAINER_MEMORY=2g`（追加）。表示したのはこの2行だけ |
| 7 | 17:11:56 | `docker compose config app` | `cpus: 4`、`mem_limit: "2147483648"` |
| 8 | 17:12:04 | `docker compose up -d --build` | コンテナを作り直して起動 |
| 9 | 17:12:14 | healthy を待つ | `healthy`（起動から約 10 秒） |
| 10 | 17:12 | 上限と起動の形 | Memory 2147483648・NanoCpus 4000000000。PID 1 は `java -XX:MaxRAMPercentage=75.0 -Duser.timezone=Asia/Tokyo -jar /app/mastersmith.war` |
| 11 | 17:12〜17:21 | スモークテスト | 7項目すべて合格（`smoke-test-results.md`） |
| 12 | 17:21 | 配備の完了 | 動いている版は `10742a3` |

## 3. 監査イベントの確認のための停止

- Q2: A により、スモークテストの後にアプリを止めてボリュームを複写し、複写を読み取りで確かめた。
- 1回目（17:21:32）は、一時の置き場を macOS の一時ディレクトリ（`mktemp -d`）にしたため、colima の VM から見えず、複写が空振りした。アプリはすぐ起動し直した。
- 2回目（17:21:49）は、置き場をホームの下（`~/.mastersmith-audit-check`、権限 700）にして複写できた。確かめた後、複写と H2 の JAR を消した。
- どちらの停止も数秒で、起動し直した後は `healthy`・`{"status":"UP"}` を確かめた。

## 4. スキーマ・データ

- スキーマの変更は無い（V1〜V4 のまま）。スキーマの変更の実行は行っていない。
- ボリュームを消す操作は行っていない。

## Sources

- `deployment-execution-questions.md`（Q1〜Q4、確認済みの要約）
- `aidlc/spaces/default/intents/260923-colima-spec-up/operation/deployment-pipeline/cd-config.md`・`deployment-strategy.md`
- `aidlc/spaces/default/intents/260923-colima-spec-up/construction/build-and-test/test-results.md`
- コマンドの出力（`git`、`./gradlew verify`、`docker compose`、`docker inspect`）

## Assumptions & Open Questions

- 戻しの練習は行っていない（Q3: A）。未確認の前提は `health-check-report.md` 3節。
