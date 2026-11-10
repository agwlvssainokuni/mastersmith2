# 配備の方式とスモークテスト（deployment-strategy）

Intent 261108-ci-checks-release の配備の方式とスモークテストです。流れは `cd-config.md` の 3節にあります。方式は前の配備（Intent `261104-tz-options-perf`）と同じです。

前の配備との差は3つです。

- 移行が無いため、起動の確かめは「移行が当たらないこと」を見る。
- スモークテストは、今回の差（使われていない画面のコードの削除）に合わせて、ログインと、コードを消した画面を見るだけに絞った（Q1: A）。保存・作成・DSL の生成など監査に残る書き込みはしない。
- 環境と内部DB の確かめに、この Intent で作った台本 A1（`scripts/ops-readonly-check.mjs`）と A3（`scripts/count-db-copy.mjs`）を使う。

## 1. 方式

- **1 台の置き換え**（recreate）。`docker compose up -d --build --no-deps app` でアプリだけを作り、イメージ ID の一致を `docker inspect` で確かめる（食い違えば `--force-recreate --no-deps app`）。今回はアプリのコンテナが無いため、この操作で新しく作る（`cd-config.md` の 1節）。ブルー・グリーンやカナリアは、PC 上の 1 台のコンテナでは意味がないため使わない。
- 環境の段（dev → staging → prod）は持たない。本番配備の承認者は依頼者。承認は、アプリを起動する順 6〜8 の前に 1 回（`cd-config.md`）。
- 内部DB のスキーマ変更は無い。機能フラグは使わない（画面の振る舞いを変える変更が無い）。
- 配備の完了は、healthy とスモークテストのすべてが通ったとき。通るまで完了とはみなさない。

## 2. 起動の確かめ

| # | 確かめ | 行う人 | 通る条件 |
|---|---|---|---|
| S1 | healthy・移行なし・起動のログ | AI | 下の4点がすべて通る |

S1 の4点:

1. **健全性**: `mastersmith-app-1` が healthy で、`/actuator/health` が 200（compose と同じ bash の `/dev/tcp` で待つ。イメージに curl が無いため）。メモリの上限 2 GiB・CPU 4。
2. **イメージ**: コンテナのイメージ ID が `mastersmith:local` の ID と一致し、戻し先 `mastersmith:pre-ci-checks-release`（`4ee5fb45593e`）と違う。コンテナの中の WAR の SHA-256 が `cd-config.md` の順 2 と同じ。
3. **移行**: 起動のログで Flyway の `Migrating schema` が 0 件で、版が 14 のまま（版と件数だけを見る）。
4. **起動のログ**: JSON として読み、ロガーと項目の名前と件数だけを出す。
   - ERROR 0 件。
   - 救済の WARN 0 件。
   - WARN は前の版から出ているもの（Flyway の H2 の版の案内・Bean の後処理の案内）だけ。
   - 初期管理者の INFO にメールアドレスそのものが出ていない（キー `maskedEmail` の有無と、`.env` の値の件数で見る。値は表示しない）。

## 3. スモークテスト（Q1: A）

AI が確かめられる項目を先に行い、依頼者には画面の見た目と、パスワードが要るログインだけを頼む（`project.md` の Deployment）。★は依頼者が行う項目です。

| # | 確かめ | 行う人 | 通る条件 |
|---|---|---|---|
| S2 | 未認証の要求とログ | AI | (1) `/` と `/favicon.svg`・`/favicon.ico` が 200。(2) 認証の要る API（`/api/me` など）が 401 の Problem Details（`code` が `AUTHENTICATION_REQUIRED`）。(3) 配る画面のファイル（`/assets/` の JavaScript）の名前が WAR の中と同じで、`dsl.status.at` が 0 件。(4) この間のアプリのログに ERROR 0 件・5xx 0 件 |
| S3 | ★ログインと、コードを消した画面（Chrome） | 依頼者 | 開いているタブを読み込み直し、初期管理者でログインする（`LOGIN_SUCCEEDED` が監査に残ることを前もって伝える）。次の画面を開いて見るだけで、保存・作成・削除・生成はしない: (a) DSL の状態の画面（出どころ・適用した人などの欄が出る）、(b) 業務データの一覧（例: `customers`）でページの件数を 20・50・100 に切り替え、ページ送りが動く、(c) 役割の一覧と、1つの役割の権限の木と割り当て、(d) グループの一覧、(e) 役割の引き継ぎの画面、(f) 利用者の管理の一覧、(g) プリファレンスの画面の表示のタイムゾーンの選択肢が出る（選ぶだけで保存しない）。どの画面も文言の鍵がそのまま出ていない（`dsl.status.…` のような表示が無い）こと |
| S4 | 画面の操作の間のログ | AI | S3 の時間の範囲のアプリのログで、ERROR 0 件、API の 5xx 0 件、`WARN` が前の版から出ているものだけ。ロガーと件数だけを出す |
| S5 | 監査と状態の確かめ | AI | 配備の後の複写（`cd-config.md` の順 11）を台本 A3（`--since <T0> --set basic,audit`）で数え、T0 の後の監査の種類と結果ごとの件数を 3.1 と照らす。スキーマの版 14、初期管理者・利用者・DSL の数が 4.1 と同じ。流した後に `build/count-db-copy/` に `work-*` が残っていない |

### 3.1 監査の期待（`AuditEventType` で確かめた種類だけ）

`backend/src/main/java/cherry/mastersmith/audit/domain/AuditEventType.java` で確かめた。画面を開いて見るだけの操作（一覧・詳細・権限の木・ページ送り・選択肢の表示）は、どの種類にも当たらず監査に残らない。

| 種類 | 結果 | 期待の件数 | どの操作 |
|---|---|---|---|
| `LOGIN_SUCCEEDED` | `SUCCESS` | 依頼者がログインした回数（1 の見込み） | S3 |
| `LOGGED_OUT` | `SUCCESS` | 依頼者がログアウトした回数（0 または 1） | S3 の後 |
| 上のほかの結果 `FAILURE`・`UNKNOWN` のすべて | — | 0 | 予定に無い |
| ほかのすべての種類（`DSL_*`・`BUSINESS_ROW_*`・`ACCESS_DENIED`・`INVITATION_*`・`INITIAL_ADMIN_RESCUED`・`ROLE_*`・`WORK_ROLE_SWITCHED`・`GROUP_*`・`USER_*`・`REGISTRATION_*`・`PASSWORD_CHANGED`・`LOGIN_FAILED`・`LOGIN_FAILURES_RESET` など） | — | 0 | 予定に無い |

- 予定に無い操作をしたとき（パスワードの打ち間違い・作業ロールの切り替えなど）は、その場で依頼者に聞いて期待を合わせ、「済んだ」の答えの後も S5 で数えてから判定する（`project.md` の Deployment）。

## 4. 通らなかったとき

- S1 が通らない（healthy にならない・ERROR が出る・移行が当たる）: 配備を止め、`rollback-runbook.md` の 2節で戻すかを依頼者に諮る。
- S2〜S4 で画面が崩れる・文言の鍵が出る・5xx が出る: 原因をログで確かめ、戻すかを依頼者に諮る。直すときは Intent の外の直しとして `develop` で直し、Build and Test に当たる検査を通してから配備し直す。
- S5 で期待に無い監査の行がある: 依頼者に操作を聞いて合わせる。合わなければ原因を調べてから判定する。

## Sources

- 前の手順: `aidlc/spaces/default/intents/261104-tz-options-perf/operation/deployment-pipeline/deployment-strategy.md`
- 流れ: `cd-config.md`（3節・4節）、質問と答え: `deployment-pipeline-questions.md`（Q1〜Q3）
- リポジトリ: `compose.yaml`（`app` の健全性の確かめ）、`backend/src/main/java/cherry/mastersmith/audit/domain/AuditEventType.java`、`git show 04b797d8`（消したコードと文言の鍵）、`scripts/count-db-copy.mjs`・`docs/internal-db.md`
- 決まり: `aidlc/spaces/default/memory/team.md`（Deployment）、`project.md`（Deployment のスモークテストの分担と監査の期待の決まり）

## Assumptions & Open Questions

- [assumption] 配備したアプリで適用中の DSL に `customers` が載り、業務データの一覧が開ける（前の配備のスモークテストで開けた）。載っていなければ S3 の (b) は省き、差を Deployment Execution の記録に書く。
- [assumption] 役割・グループ・役割の引き継ぎの画面は、初期管理者で開ける（管理の権限を持つ）。
