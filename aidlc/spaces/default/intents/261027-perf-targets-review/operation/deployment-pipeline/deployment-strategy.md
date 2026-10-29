# 配備の方式とスモークテスト（deployment-strategy）

Intent 261027-perf-targets-review。流れは `cd-config.md` の 3節。前の Intent（`261025-deps-perf-carryover`）の方式と同じで、差はスモークテストの範囲（Q2: A）と、アプリの中身が変わらない点。

## 1. 方式

- **1 台の置き換え**（recreate）。`docker compose --profile targetdb-postgres up -d --build app` でアプリだけを入れ替える。ブルー・グリーンやカナリアは、PC 上の 1 台のコンテナでは意味がないため使わない。
- 環境の段（dev → staging → prod）は持たない。本番配備の承認者は依頼者。承認は、アプリが止まる順 5〜7 の前に 1 回（`cd-config.md`）。
- 内部DB のスキーマ変更は無い（前進のみ・後方互換のまま）。機能フラグは使わない。
- 配備の完了は、healthy とスモークテストのすべてが通ったとき。通るまで完了とはみなさない。

## 2. 起動の確かめ

| # | 確かめ | 行う人 | 通る条件 |
|---|---|---|---|
| S1 | healthy | AI | `mastersmith-app-1` が healthy。`/actuator/health` が 200（compose と同じ bash の `/dev/tcp` で待つ）。メモリの上限 2 GiB・CPU 4 |
| S2 | 起動のログ | AI | ERROR 0 件。Flyway は `Migrating schema` が 0 件で現在の版が 13。WARN は前の版から出ているもの（Flyway の H2 の版・Bean の後処理の案内）のほかに無い。初期管理者の INFO にメールアドレスそのものが出ていない（キーの名前と件数だけで見る） |

## 3. スモークテスト（Q2: A、小さな範囲）

ロールは作らず、初期管理者の今の割り当てを使う。ログインは監査に残るため、行う前に依頼者に伝える。値（メールアドレス・パスワード・業務データの値・主キーの値）は表示しない。パスワードが要る操作は依頼者が行う。

| # | 確かめ | 行う人 | 通る条件 | 監査に残るもの |
|---|---|---|---|---|
| S3 | ログイン | 依頼者 | 初期管理者でログインでき、ホームが開く | `LOGIN_SUCCEEDED` 1 件（ログインした回数ぶん） |
| S4 | 業務データの一覧と詳細 | 依頼者 | 業務のメニューから見本の対象DB の既存のテーブルの一覧が開き、1 行の詳細が開ける。値が文字として出て、エラー表示が無い | 読み取りは監査に残らない |
| S5 | プールの設定 | AI | `/actuator/metrics/hikaricp.connections.max` が使えない公開範囲のときは、起動のログまたは設定の確かめで読み取りのプールの既定が 10 であること（`application.yaml`、WAR の中身）を値ではなく設定の有無で確かめる。公開されていれば `mastersmith-target-db` の `hikaricp.connections.max` が 10 | — |
| S6 | 監査の確かめ | AI | 配備の後の複写（`cd-config.md` の順 9）を読み取りで開き、T0 以降の `audit_events` を種類と結果ごとに数える。期待は `LOGIN_SUCCEEDED` が依頼者のログインの回数と一致、FAILURE・UNKNOWN が 0、予定に無い管理の操作が無い。ログイン以外に画面で行った操作があれば、その場で聞いて期待の件数に足す | 数える対象 |

- 依頼者が画面で触れるときは、監査に残る操作（管理の操作・ログインの失敗など）をしたかをその場で聞き、期待の件数を合わせてから数える（`project.md` の学び）。
- S5 は、`hikaricp` の指標が配備したアプリで公開されない設定のとき（`MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE` を渡さない既定）、読み取りの設定の確かめで代える。差を記録する。

## 4. 通らなかったとき

- 通らない点があれば、`rollback-runbook.md` の 2節で戻すかを依頼者に諮る。アプリの中身が変わらないため、通らない原因は再ビルドの差か環境の状態のどちらかである見込み。
- 戻すと決めるまでは、状態（行の数・監査）を変える操作をしない。

## Sources

- 前の手順: `aidlc/spaces/default/intents/261025-deps-perf-carryover/operation/deployment-pipeline/deployment-strategy.md`
- `cd-config.md`・`rollback-runbook.md`・`deployment-pipeline-questions.md`
- リポジトリ: `compose.yaml`、`backend/src/main/resources/application.yaml`（読み取りのプールの既定）、README
- 決まり: `aidlc/spaces/default/memory/team.md`（Deployment）、`project.md`（Deployment・Corrections の学び）

## Assumptions & Open Questions

- [assumption] 初期管理者でログインする操作は依頼者が行う（パスワードを AI が扱わない）。
- [assumption] 配備したアプリは `hikaricp` の指標を公開しない既定のままで、S5 は設定の確かめで代える。
