# 配備の仕方（deployment-strategy）

Intent 261004-safety-carryover の配備の仕方と、配備の後の確かめです。流れの全体は `cd-config.md`、戻しは `rollback-runbook.md` にあります。前の Intent の `deployment-strategy.md`（以下「前の手順」）を正とし、差だけを書きます。CI の構成・関門・基盤の仕様（`ci-config`・`quality-gates`・`infrastructure-specification`・`cicd-pipeline`）は、この Intent の流れに無い段のため作られていません。

## 1. 方式

| 項目 | 値 |
|---|---|
| 方式 | 1台の置き換え（Recreate）。青緑・カナリア・段階の昇格は無い（配備先が開発者の PC 上のコンテナ1つのため） |
| 止まる時間 | 救済の見込みの確かめ（`cd-config.md` の 3節の順 3）と、新しいイメージでの起動の間。スキーマの変更は無い |
| 機能の切り替え（feature flag） | 使わない。救済の口は `.env` の初期管理者の設定で働く（専用の切り替えは置かない。要件 F2: C） |
| 環境の昇格 | 無い（検証環境・本番環境は配備先が決まってから） |
| 承認 | 依頼者 |

## 2. 入れ替えの手順（`cd-config.md` の 3節の順 2〜4）

```bash
# 順 2: 戻し先のタグ（今動いている版）
docker tag mastersmith:local mastersmith:pre-safety-carryover
docker image inspect mastersmith:pre-safety-carryover --format '{{.Id}}'   # sha256:f124296eb7ca… であること

# 順 3: 依頼者の承認の後、アプリを止めて内部DB を複写し（配備の前のバックアップ）、初期管理者の停止と印の有無だけを数える
docker compose stop app
#   README の「監査ログの確かめ方」と同じ形で、ボリュームの内部DB を ~/.mastersmith-backup/（権限 700）へ複写し、
#   複写を ACCESS_MODE_DATA=r で開いて、.env の MASTERSMITH_AUTH_INITIAL_ADMIN_EMAIL の値（表示しない）の利用者の
#   suspended と admin_flag の値だけを読む（行が無ければ「作成」の見込み）

# 順 4: WAR とイメージを作り直し、app だけを入れ替える
docker compose --profile targetdb-postgres up -d --build app
docker compose --profile targetdb-postgres ps app                              # app が healthy になるまで待つ
docker image inspect mastersmith:local --format '{{.Id}} {{.Created}}'          # 配備したイメージの ID を記録する
```

- `app` だけを指定する。見本の対象DB と Mailpit は入れ替えない。
- 画面は `http://localhost:8080/` で開く（`127.0.0.1` では Origin の確かめが合わず 403）。
- 順 3 の見込み: 停止中・印なし・依頼者が画面でパスワードを変えた、のどれかに当たれば「救済が働く」。救済が働くと、初期管理者のパスワードが `.env` の値に戻り、リフレッシュトークンがすべて無効になる（ログイン中の端末はログインし直し）。依頼者の了承を得てから順 4 に進む。

## 3. 配備の後の確かめ（スモークテスト、Q2: A）

健全性と、下のすべてが通るまで、配備の完了としません（`team.md` の Deployment）。

- パスワードの要る操作は依頼者が行い、AI はアプリのログと監査の記録で裏付けます。
- 起動のログは、値を表示せず、レベル・ロガー・キーの名前と件数だけを見ます。
- 監査に残る要求は、依頼者のログインの `LOGIN_SUCCEEDED` だけの見込みです（送る前に依頼者に伝えます）。依頼者が画面で監査に残る操作をしたときは、その場で伺い、S4 の期待の件数を合わせます（`project.md` の学び）。

| # | 確かめ | 行う人 | 通る条件 |
|---|---|---|---|
| S1 | 健全性と起動のログ | AI | `app` が healthy になり、`/actuator/health` が 200・UP。ERROR が 0 件。WARN はロガーと件数だけを見て、前の版から出ているもの（Spring の Bean の案内、Flyway の H2 の版の案内）と、救済の見込みどおりの救済の WARN のほかに無い |
| S2 | 初期管理者の起動のログ | AI | 救済の WARN「初期管理者を救済しました」が、順 3 の見込みどおりの件数（働かない見込みなら 0、働く見込みなら 1）。初期管理者の INFO・WARN にキー `maskedEmail` があり、キー `email` が無い（キーの有無だけを見る）。Flyway は版 9 のまま（`Migrating schema` が 0 件） |
| S3 | ★ログイン | 依頼者（AI は S4 で裏付け） | 依頼者が初期管理者でログインし、ホームが開ける（救済が働いたときは `.env` のパスワードで） |
| S4 | 監査の記録 | AI | README の「監査ログの確かめ方」のとおり、アプリを止めて内部DB を `~/.mastersmith-backup/` へ複写し、アプリを起動し直してから、複写を読み取りで開いて、配備の時刻以降の `audit_events` を種類と結果ごとに数える。`INITIAL_ADMIN_RESCUED` が順 3 の見込みどおり（0 か 1。1 のときは条件の列の値が見込みと一致）、`INITIAL_ADMIN_CREATED` が 0、`LOGIN_SUCCEEDED` が1件以上。アプリを止める間（数秒）は依頼者に伝えてから行う。複写は配備の後のバックアップを兼ねる |

S1・S2 のコマンド（値を表示しない形。前の手順の S1・S2 と同じ形）:

```bash
# S1: レベルごとの件数と、WARN・ERROR のロガーと件数だけ
docker compose logs --no-log-prefix app | jq -R -r 'fromjson? | .level' | sort | uniq -c
docker compose logs --no-log-prefix app | jq -R -r 'fromjson? | select(.level=="WARN" or .level=="ERROR") | .logger' | sort | uniq -c

# S2: 初期管理者の行の文とキーの有無（値は表示しない）と、Flyway の移行の件数
docker compose logs --no-log-prefix app | jq -R -r 'fromjson? | select(.logger | test("InitialAdmin")) | .level + " " + .message + " " + (keys_unsorted | join(","))' | sort | uniq -c
docker compose logs --no-log-prefix app | jq -R -r 'fromjson? | select(.logger | startswith("org.flywaydb")) | .message' | grep -c 'Migrating schema'   # 0 であること
```

## 4. 中止の条件

次のどれかが起きたら配備を中止し、`rollback-runbook.md` に従ってイメージだけを戻します。

| 条件 | 戻し方 |
|---|---|
| 起動が healthy にならない | 戻し先のイメージで起動し直す |
| S1〜S4 のどれかが通らず、その場で原因が設定だけと分からない | 同上 |
| ERROR のログが出続ける、ログに秘密・メールアドレスそのものが出る | 同上 |
| 救済が見込みと違って働いた（または働かなかった） | 合否を決める前に依頼者に諮る（要件 FR8.3）。救済で変わった利用者の状態は、戻しても元に戻らない（`rollback-runbook.md` の 3節） |

## 5. 監視

- 手元の監視（lgtm、profile `monitoring`）は、この段では起動しない。指標・警報・ダッシュボードは変えていない（監視の2つのイメージにダイジェストを付けただけ）。
- 配備の直後は、`docker compose logs app`（値を表示しない形）と監査の記録（S4）で確かめる。

## Sources

- 前の手順: `aidlc/spaces/default/intents/261003-user-admin-followup/operation/deployment-pipeline/deployment-strategy.md`
- `cd-config.md`・`rollback-runbook.md`・`deployment-pipeline-questions.md`（Q1〜Q3、まとめの確認）
- `inception/requirements-analysis/requirements.md`（FR1・FR8.2・FR8.3）
- `README.md`（コンテナでの起動と確認、監査ログの確かめ方、初期管理者の救済）
- `aidlc/spaces/default/memory/team.md`・`project.md`（Deployment・Corrections の学び）

## Assumptions & Open Questions

- [assumption] S2 の救済の WARN の文（「初期管理者を救済しました」）と初期管理者の INFO の文は、この Intent のコード（`InitialAdminInitializer`）と使い捨ての環境の起動のログで確かめた形を前提にしている。違うときはロガーと件数で判定し直す。
