# 配備の記録（deployment-log）

配備先は開発者の PC 上のコンテナ（`compose.yaml`、プロジェクト名 `mastersmith`）。依頼者の決定（Q1: A）により、2026-09-25（UTC では 2026-09-24T16 時台）に `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/deployment-pipeline/deployment-strategy.md` 2節の手順で配備した。

## 1. 配備した版

| 項目 | 値 |
|---|---|
| 配備した版（コミット） | `7bc1b68`（`develop` の先頭。アプリの中身は `8961cb2` と同じ。CI は `6f212e8` で成功） |
| 新しいイメージ | `mastersmith:local`（`sha256:1585bd4ef3dd…`） |
| WAR | `backend/build/libs/mastersmith.war`。コンテナの中の WAR と SHA-256 が一致（`63ecd8a7edf2a6a2…`）。WAR の中の `application.yaml` の既定の接続先に `;DEFRAG_ALWAYS=TRUE` がある |
| 直前の版（戻し先） | `10742a3`、イメージ `mastersmith:pre-dsl`（`sha256:8441534a745f…`。配備の前の `mastersmith:local` と同じ ID） |
| 見本の対象DB | `mastersmith-targetdb-postgres-1`（`postgres:18.6@sha256:86c951e0…`）。Environment Provisioning で起動済み |

## 2. 手順と結果

| 順 | 手順 | 結果 |
|---|---|---|
| 1 | アプリのソースに未コミットの変更が無い | 変更はワークフローの記録（監査ログと本段の記録のディレクトリ）だけ（project.md の決まりどおり、これらは除いて判断） |
| 2 | 版のハッシュを控える | `7bc1b68` |
| 3 | `./gradlew verify` | 成功（4分43秒）。コンテナの実行環境が無いときの警告は 0 件 |
| 4・5 | `.env` の9項目 | 9項目すべて 1。`MASTERSMITH_DB_URL` は 0（既定が効く）。値は表示していない |
| 6 | 起動するサービス | `app`・`targetdb-postgres` |
| 7 | 戻し用のタグ | `mastersmith:pre-dsl` が `sha256:8441534a745f…` |
| 8 | アプリを止めて内部DB を複写（16:20:12Z） | `~/.mastersmith-backup/mastersmith-data-202609250120-before-dsl.tgz`（8,016 バイト）、置き場の権限 700 |
| 9 | `docker compose --profile targetdb-postgres up -d --build` | イメージを作り直し、アプリを作り直して起動。見本の対象DB は動いたまま |
| 10 | healthy まで待つ | 16:20:32Z に `healthy`（起動から約 9 秒）。アプリが止まっていたのは約 20 秒 |
| 11 | 見本の対象DB | Environment Provisioning で初期化を確かめ済み。ログの ERROR・FATAL の 11 件は、すべて Environment Provisioning の確かめ（書き込みの拒否・誤ったパスワード・記号入りの名前で崩れた確かめの問い合わせ）によるもので、見本の DB の不具合ではない |
| 12 | 内部DB の移行 | Flyway: `Current version of schema "PUBLIC": 4` → `Migrating ... "5 - u4 dsl management"` → `"6 - u4 dsl audit columns"` → `Successfully applied 2 migrations ..., now at version v6 (execution time 00:00.023s)` |
| 13 | 上限 | アプリ `2147483648 4000000000`（メモリ 2GiB・CPU 4）、見本の DB `536870912`（512MiB） |
| 14 | スモークテスト | すべて合格（`smoke-test-results.md`） |
| 15 | 配備の完了 | `7bc1b68` が、いま動いている版 |

## 3. 未確認のまま残る前提（依頼者の判断 Q2: C）

- **古い版 `10742a3` が、V5・V6 の当たった内部DB で起動できること（U4-MIGRATION）は確かめていない。** 戻し方（`rollback-runbook.md` 2節）はこの前提に立つ。根拠は見込みだけ（V5 は表を足すだけ・V6 は NULL を許す列を足すだけ、Flyway は既定で知らない先の移行を無視する、H2 の版は同じ）。
- 次に確かめる機会: 実際に戻すとき（起動しなければ `rollback-runbook.md` 4節でデータも戻す。配備の前の複写 `mastersmith-data-202609250120-before-dsl.tgz` がある）、または次の配備の前に戻しを練習するとき。
- Build and Test の U4-MIGRATION は Unverified のまま（持ち主の段だった deployment-execution で、依頼者の判断により確かめなかった）。

## 4. 既知の制約（配備したものに残るもの）

- **U4-STORAGE-RUN**（Build and Test で依頼者が受け入れた）: アプリが動いている間は、DSL の投入と適用のたびに内部DB のファイルが本文の大きさの分ずつ増え、止めると詰め直される。大きな DSL の投入と適用を重ねたら、ディスクの空きを確かめてアプリを起動し直す（README の「DSL の管理」の節）。
- アプリのメモリ（プロセスの分）は、10MB の DSL を重ねたときに上限 2g の約 92% まで使う（Build and Test の実測）。

## 5. 後始末

- 内部DB の複写: 配備の前（`…-before-dsl.tgz`）と、スモークテストの後の監査イベントの確かめ用（`mastersmith-data-202609250130-after-smoke.tgz`）の2つを `~/.mastersmith-backup/` に残した（リポジトリの外、権限 700）。確かめのために展開した作業の複写は消した。
- `mastersmith:pre-dsl` のタグは次の配備まで残す。

## Sources

- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/deployment-execution/deployment-execution-questions.md`（Q1: A・Q2: C、確認済みの要約）
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/deployment-pipeline/cd-config.md`・`deployment-strategy.md`・`rollback-runbook.md`
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/environment-provisioning/environment-inventory.md`・`validation-report.md`
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/construction/build-and-test/test-results.md`・`build-and-test-summary.md`（U4-STORAGE-RUN・U4-MIGRATION）
- 実行したコマンド: `git status --porcelain`・`git rev-parse --short HEAD`・`./gradlew verify`・`grep -c '^<項目>=.' .env`・`docker compose --profile targetdb-postgres config --services`・`docker tag`・`docker compose stop app`・`docker run ... tar czf`・`docker compose --profile targetdb-postgres up -d --build`・`docker compose logs`・`docker inspect`・`docker cp`（WAR の比べ）

## Assumptions & Open Questions

- [assumption] 古い版 `10742a3` が V5・V6 の当たった内部DB で起動できる（U4-MIGRATION。依頼者の判断で確かめていない。3節）。
