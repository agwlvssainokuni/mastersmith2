# 配備の方式とスモークテスト（deployment-strategy）

Intent 261110-deps-forks-review の配備の方式とスモークテストです。流れは `cd-config.md` の 3節にあります。方式は前の配備（Intent `261108-ci-checks-release`）と同じです。

前の配備との差は3つです。

- 起動の確かめに、WAR の中の部品の版（networknt 3.0.9・Jackson 3.1.8・itu 1.17.0）を足した。
- スモークテストは、今回の差（Jackson・networknt・画面の依存の i18next 27・日時の文言の包み直し）に合わせ、ログインと、見るだけの画面を日本語と英語の両方で回る形にした（Q2: A）。保存・作成・DSL の投入など監査に残る書き込みはしない。
- アプリのコンテナは止まって残っている（前の配備では無かった）。入れ替えの操作で作り直す。

## 1. 方式

- **1 台の置き換え**（recreate）。`docker compose up -d --build --no-deps app` でアプリだけを作り、イメージ ID の一致を `docker inspect` で確かめる（食い違えば `--force-recreate --no-deps app`）。ブルー・グリーンやカナリアは、PC 上の 1 台のコンテナでは意味がないため使わない。
- 環境の段（dev → staging → prod）は持たない。本番配備の承認者は依頼者。承認は、`main` の取り込み（順 1）と、アプリを起動する順 6〜8 の前（`cd-config.md`）。
- 内部DB のスキーマ変更は無い。機能フラグは使わない（画面の振る舞いを変える変更が無い）。
- 配備の完了は、healthy とスモークテストのすべてが通ったとき。通るまで完了とはみなさない。

## 2. 起動の確かめ

| # | 確かめ | 行う人 | 通る条件 |
|---|---|---|---|
| S1 | healthy・移行なし・起動のログ・部品の版 | AI | 下の5点がすべて通る |

S1 の5点:

1. **健全性**: `mastersmith-app-1` が healthy で、`/actuator/health` が 200（compose と同じ bash の `/dev/tcp` で待つ。イメージに curl が無いため。起動の直後の running を健全と判定しない）。メモリの上限 2 GiB・CPU 4。
2. **イメージ**: コンテナのイメージ ID が `mastersmith:local` の ID と一致し、戻し先 `mastersmith:pre-deps-forks-review`（`0d4b6e8866a9`）と違う。コンテナの中の WAR の SHA-256 が `cd-config.md` の順 2 と同じ。
3. **部品の版**: コンテナの中の WAR の `WEB-INF/lib` に `json-schema-validator-3.0.9`・`jackson-databind-3.1.8`・`itu-1.17.0` があり、前の版（3.0.6・3.1.7・1.14.0）が無い（`unzip -Z1` の名前だけを見る）。
4. **移行**: 起動のログで Flyway の `Migrating schema` が 0 件で、版が 14 のまま（版と件数だけを見る）。
5. **起動のログ**: JSON として読み、ロガーと項目の名前と件数だけを出す。
   - ERROR 0 件（適用中の DSL の読み直しで JSON Schema の検証の誤りが出ていないことを含む）。
   - 救済の WARN 0 件。
   - WARN は前の版から出ているもの（Flyway の H2 の版の案内・Bean の後処理の案内）だけ。
   - 初期管理者の INFO にメールアドレスそのものが出ていない（キー `maskedEmail` の有無と、`.env` の値の件数で見る。値は表示しない）。

## 3. スモークテスト（Q2: A）

AI が確かめられる項目を先に行い、依頼者には画面の見た目と、パスワードが要るログインだけを頼む（`project.md` の Deployment）。★は依頼者が行う項目です。

| # | 確かめ | 行う人 | 通る条件 |
|---|---|---|---|
| S2 | 未認証の要求とログ | AI | (1) `/` と `/favicon.svg`・`/favicon.ico` が 200。(2) 認証の要る API（`/api/me` など）が 401 の Problem Details（`code` が `AUTHENTICATION_REQUIRED`。Jackson 3.1.8 で項目の名前と形が前と同じ）。(3) 配る画面のファイル（`/assets/` の JavaScript）の名前が WAR の中と同じ。(4) この間のアプリのログに ERROR 0 件・5xx 0 件 |
| S3 | ★ログインと、見るだけの画面（Chrome） | 依頼者 | 開いているタブを読み込み直し、初期管理者でログインする（`LOGIN_SUCCEEDED` が監査に残ることを前もって伝える）。次の画面を開いて見るだけで、保存・作成・削除・投入・生成はしない: (a) ホーム、(b) DSL の状態の画面（適用中の DSL が出る）、(c) 業務データの一覧（例: `customers`）が開け、検索の欄を開くと日時の列のヒント（表示のタイムゾーンを含む文）が出る。日時の欄に形の合わない値（例: `2026-11-01 25:00`）を入れて検索を押すと、誤りの文言が出る（検索は読むだけで監査に残らない）、(d) 役割の一覧、(e) 利用者の管理の一覧。続けて、表示の設定で言語を英語に切り替え（ブラウザに保存されるだけで監査に残らない）、(a)〜(e) を同じように見て、英語の文言が出る。終わったら日本語に戻す。どの画面も文言の鍵がそのまま出ていない（`tables.search.…` のような表示が無い）こと |
| S4 | 画面の操作の間のログ | AI | S3 の時間の範囲のアプリのログで、ERROR 0 件、API の 5xx 0 件、`WARN` が前の版から出ているものだけ。ロガーと件数だけを出す |
| S5 | 監査と状態の確かめ | AI | 配備の後の複写（`cd-config.md` の順 11）を台本 A3（`--since <T0> --set basic,audit`）で数え、T0 の後の監査の種類と結果ごとの件数を 3.1 と照らす。スキーマの版 14、初期管理者・利用者・DSL の数が配備の前の数え（`cd-config.md` の 4.1）と同じ。流した後に `build/count-db-copy/` に `work-*` が残っていない |

### 3.1 監査の期待（`AuditEventType` で確かめた種類だけ）

`backend/src/main/java/cherry/mastersmith/audit/domain/AuditEventType.java` で確かめた。画面を開いて見るだけの操作（一覧・詳細・検索・ヒントの表示）と、表示の設定の言語の切り替え（ブラウザの `localStorage` に保存。`frontend/src/app/display-settings/`）は、どの種類にも当たらず監査に残らない。

| 種類 | 結果 | 期待の件数 | どの操作 |
|---|---|---|---|
| `LOGIN_SUCCEEDED` | `SUCCESS` | 依頼者がログインした回数（1 の見込み） | S3 |
| `LOGGED_OUT` | `SUCCESS` | 依頼者がログアウトした回数（0 または 1） | S3 の後 |
| 上のほかの結果 `FAILURE`・`UNKNOWN` のすべて | — | 0 | 予定に無い |
| ほかのすべての種類（`DSL_*`・`BUSINESS_ROW_*`・`ACCESS_DENIED`・`INVITATION_*`・`INITIAL_ADMIN_RESCUED`・`ROLE_*`・`WORK_ROLE_SWITCHED`・`GROUP_*`・`USER_*`・`REGISTRATION_*`・`PASSWORD_CHANGED`・`LOGIN_FAILED`・`LOGIN_FAILURES_RESET` など） | — | 0 | 予定に無い |

- 予定に無い操作をしたとき（パスワードの打ち間違い・作業ロールの切り替えなど）は、その場で依頼者に聞いて期待を合わせ、「済んだ」の答えの後も S5 で数えてから判定する（`project.md` の Deployment）。

## 4. 通らなかったとき

- S1 が通らない（healthy にならない・ERROR が出る・移行が当たる・部品の版が違う）: 配備を止め、`rollback-runbook.md` の 2節で戻すかを依頼者に諮る。
- S2〜S4 で画面が崩れる・文言の鍵が出る・英語に切り替わらない・5xx が出る: 原因をログで確かめ、戻すかを依頼者に諮る。直すときは `develop` で直し、Build and Test に当たる検査を通してから配備し直す。
- S5 で期待に無い監査の行がある: 依頼者に操作を聞いて合わせる。合わなければ原因を調べてから判定する。

## Sources

- 前の手順: `aidlc/spaces/default/intents/261108-ci-checks-release/operation/deployment-pipeline/deployment-strategy.md`
- 流れ: `cd-config.md`（3節・4節）、質問と答え: `deployment-pipeline-questions.md`（Q1〜Q3）
- リポジトリ: `compose.yaml`（`app` の健全性の確かめ）、`backend/src/main/java/cherry/mastersmith/audit/domain/AuditEventType.java`、`frontend/src/app/display-settings/`（言語の保存先）、`git diff a9ddc077 HEAD -- frontend/src backend/gradle.lockfile`、`frontend/e2e/185-business-data-accessibility.e2e.ts`（日時の誤りの入力の例）、`scripts/count-db-copy.mjs`・`docs/internal-db.md`
- 決まり: `aidlc/spaces/default/memory/team.md`（Deployment）、`project.md`（Deployment のスモークテストの分担と監査の期待の決まり）

## Assumptions & Open Questions

- [assumption] 配備したアプリで適用中の DSL に `customers` が載り、業務データの一覧が開ける（前の配備のスモークテストで開けた）。日時の列を持つテーブルが無ければ S3 の (c) の日時の部分は省き、差を Deployment Execution の記録に書く。
- [assumption] 役割・利用者の管理の画面は、初期管理者で開ける（管理の権限を持つ）。
