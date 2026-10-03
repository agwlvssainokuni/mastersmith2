# 受け手と権限の表（escalation-matrix）

前の2つの表（`aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/incident-response/escalation-matrix.md`、以下「元の表」と、`aidlc/spaces/default/intents/260925-user-management/operation/incident-response/escalation-matrix.md`、以下「前の表」）を正とし、この Intent の差だけを書きます（Q1: A）。

## 1. 重さごとの受け手と期限

変えません（元の表の 1節）。

| 重さ | 受け手・判断者 | 着手 | 戻すまでの期限 | その先の受け手 |
|---|---|---|---|---|
| 高 | 依頼者 | 気づいたらすぐ | 1日の内（RTO 1日） | 無い（1名での運用） |
| 低 | 依頼者 | 当日か翌日 | 置かない | 無い |

- 連絡の先は依頼者本人です。受け手が1名のため、呼び出しの順・交代の決まりは置きません。個人の連絡の情報（メールアドレスなど）は、公開のリポジトリに書きません（元の表の 1節）。
- この Intent の高の例: 使える管理者がいなくなった（`runbooks.md` の RB-22）、この Intent の版を戻す（RB-26）、既存の経路の行の排他の時間切れの 500 が続く（RB-24）。

## 2. 承認なしで行ってよい調べの差（元の表の 2.1節・前の表の 2節に足す）

| 調べ | コマンドの例 | 気をつけること |
|---|---|---|
| 行の排他の時間切れの件数と種類 | `docker compose logs --no-log-prefix app` を `grep -c '行の排他を取れませんでした'`、`grep -o '"lockKind":"[A-Z_]*"' \| sort \| uniq -c` で数える（`runbooks.md` の RB-24） | 出るのは排他の種類と例外のクラスの名前だけ |
| BUSY・最後の管理者の保護などの拒否の件数 | `grep -c '"code":"USER_ADMIN_BUSY"'` など、code ごとの件数。手元の監視が動いていれば `operation/observability-setup/log-queries.md` 2節の L1〜L4 | 件数と code だけを報告に書く |
| 5つの操作の監査の失敗 | `grep -c '監査イベントの記録に失敗しました'`（`runbooks.md` の RB-25） | ERROR のキーに利用者 ID（`actorUserId`・`targetUserId`）が載る。報告には件数・種類・時刻だけを書く |
| 初期管理者の作成の有無（起動の後） | `grep -c '初期管理者を作成しました'`・`grep -c '初期管理者は既にいるため、作成しませんでした'`（`runbooks.md` の RB-22 の手順 4） | 件数だけを出す。行を表示しない（キー `maskedEmail` は伏せ字だが、出さない） |
| 監査の複写の読み取り（5つの操作） | 既に `~/.mastersmith-backup/` にある複写を展開し、`log-queries.md` 3節の問い合わせを流す | 新しく複写するときはアプリを止めるため承認が要る（元の表の 3節）。利用者 ID・送り元の IP は数だけを見る |

## 3. 依頼者の承認が要る操作の差（元の表の 3節・前の表の 3節に足す）

| 操作 | 例 | 承認のときに示すこと |
|---|---|---|
| 利用者の管理の5つの操作（印を付ける・外す・止める・停止を解く・失敗回数を戻す） | 利用者の管理の画面の行の「操作」 | **【依頼者】が画面で行う**。AI は API で行わない（管理者の資格情報が要るため）。どれも成功も拒否も監査に残り、消せない。止めると対象のリフレッシュトークンがすべて無効になり、解いても戻らない。失敗回数を戻す操作は取り消せない（`runbooks.md` の RB-21） |
| 新しい管理者を作るための作り直し（RB-22） | 【依頼者】`.env` の初期管理者のメールアドレスとパスワードを替えた後、【承認】`docker compose --profile targetdb-postgres up -d app` | 作り直すと今のコンテナのログが消える（先に保存する）。利用者が1人増え、消せない。作成は監査に残らない。終わったら `.env` を元に戻して、もう一度作り直す |
| 起動し直して排他を外す（RB-24） | 【承認】`docker compose --profile targetdb-postgres restart app` | 止まっている間（約 10 秒〜）の要求は失敗する。進行中の操作は巻き戻る |
| 版を戻す | `operation/deployment-pipeline/rollback-runbook.md`（戻し先 `mastersmith:pre-user-admin`） | **戻している間は利用停止が効かない**（停止中の利用者も入れる、止め直せない）。利用者の管理の画面と RB-22 の手順が使えない。配備の前のバックアップは無い。V9 は戻さない（`runbooks.md` の RB-26） |
| 使い捨ての環境で手順を確かめる | `docker/perf/compose.yaml`（`perf/README.md`） | VM のメモリ（6GiB）を使う。配備したアプリと `.env` には触れない。終わったら結果を見てから、コンテナ・ボリューム・網と一時の置き場を消す |

## 4. AI がしないことの差（元の表の 4節・前の表の 4節に足す）

- 利用者の管理の5つの操作と、氏名・言語の変更を、API で行うこと（管理者の資格情報を使わない。画面の操作は【依頼者】）。
- `.env` の初期管理者のメールアドレス・パスワードを書き換えること、中身を開く・表示する・数えること（`.env` の編集は【依頼者】）。
- 内部DB を書き込みで開き、SQL で利用者の印・停止・失敗回数を直接書き換えること（監査に残らず、`aidlc/spaces/default/memory/project.md` の Mandated「権限・状態を変える管理の操作は監査に残す」を外れる。RB-22 の「採らなかった手」）。
- 利用者 ID・メールアドレス・氏名・送り元の IP を、コマンドの出力・記録・報告に出すこと（件数とキーの名前だけで見る）。
- 承認なしに、配備したアプリを止める・作り直す・戻すこと。

## 5. 知らせ（通知）

- 変えません（元の表の 5節）。通知の先は置かず、依頼者が Grafana の警報の一覧で見ます。
- 警報は 16 件のままです（`operation/observability-setup/alarms.md` 1節）。
- 前の表の 5節の「p95 の3件は鳴りません」は古くなりました。式は値を出せるようになっています（`runbooks.md` 1節）。
- 409（`USER_ADMIN_BUSY`・`USER_ADMIN_LAST_ADMIN` など）・停止中の利用者の拒否・管理者の誤った操作・使える管理者がいなくなったことには、警報がありません。利用者からの知らせ、利用者の管理の一覧、定期の確認（`runbooks.md` の 3節）で見つけます。

## Sources

- 元の表（正とする）: `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/incident-response/escalation-matrix.md`
- 前の表（正とする）: `aidlc/spaces/default/intents/260925-user-management/operation/incident-response/escalation-matrix.md`
- `operation/incident-response/incident-response-questions.md`（決まっていること、Q1〜Q3、確認済みの要約）、`operation/incident-response/runbooks.md`・`incident-plan.md`
- `operation/observability-setup/alarms.md`・`dashboards.md`・`log-queries.md`
- `operation/deployment-pipeline/rollback-runbook.md`
- `construction/u3-user-admin-api/nfr-design/security-design.md`・`reliability-design.md`、`construction/u3-user-admin-api/infrastructure-design/infrastructure-specification.md`
- `README.md`（「利用者の管理の API」「監査ログの確かめ方」）、`perf/README.md`
- `aidlc/spaces/default/memory/project.md`（Forbidden・Mandated・Corrections・Deployment）

## Assumptions & Open Questions

None.
