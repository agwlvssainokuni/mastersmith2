# 障害の対応の計画（incident-plan）

MasterSmith の障害の対応の決まり。この Intent で初めて作る。範囲は、今動いているアプリ全体（ログイン・アクセス制御・監査ログ・DSL の管理・対象DB の接続）と、それを動かしている手元の環境（colima の VM・コンテナ・ボリューム）である。

前提（`incident-response-questions.md` の回答と確認済みの要約）:

- 配備先は開発者の PC 上のコンテナ（アプリ `mastersmith-app-1`・見本の対象DB `mastersmith-targetdb-postgres-1`・必要なときだけ手元の監視 `lgtm`）。アプリは `127.0.0.1:8080` にだけ結び付き、PC の外からは届かない。
- 利用者・運用者・判断者は依頼者1名。AI は依頼者に頼まれたときに調べと手順の実行を補助する（Q1: A）。受け手と権限の詳細は `escalation-matrix.md`。
- 知らせの仕組み（通知）は無い。警報は Grafana の画面で見るだけ（Q1: A、`project.md` の Deployment）。
- クラウドの仕組み（SSM の手順・Incident Manager・AWS Backup など）は、配備先が決まるまで作らない。

## 1. 重さの段階（2段、Q3: A）

| 重さ | 当てはまるもの | 対応の速さ | 戻すか |
|---|---|---|---|
| **高** | アプリが止まる・healthy にならない／ログインできない（管理者を含む）／内部DB のデータが壊れた・失われた疑い（監査ログを含む）／秘密情報（パスワード・トークン・署名鍵・対象DB の接続情報）が漏れた疑い | 気づいたらすぐ対応する（ほかの作業より先） | 直す見込みが立たなければ戻す（`runbooks.md` の RB-10、`operation/deployment-pipeline/rollback-runbook.md`） |
| **低** | 一部の機能だけが使えない（例: スキーマの読み込みが「接続できない」、`DSL_BUSY` が続く）／遅い／警告のログ・警報（例: 監査の書き込みの遅れ、内部DB のファイルの伸び） | 当日か翌日に対応する | 原則として戻さず、その場で直すか、既知の制約として記録する |

- 迷ったときは「高」にする。調べて影響が小さいと分かったら「低」に下げ、下げた理由を記録に残す。
- 警報のファイル（`docker/monitoring/provisioning/alerting/mastersmith.yaml`）の `severity` のラベルは「高・中・低」の3つだが、障害の重さは上の2段で決める。当てはめの目安: 警報の「高」は障害の「高」、警報の「中」は初動で影響を見て「高」か「低」を決める（ログインできない・データや秘密情報に関わるなら「高」、それ以外は「低」）、警報の「低」は障害の「低」。警報ごとの目安は `runbooks.md` の 13節。
- 1名での運用のため、依頼者が対応できない間は、アプリが止まったままになることを受け入れる（利用者も依頼者だけのため、影響を受ける人はいない）。

## 2. 見つけてから閉じるまでの流れ

```mermaid
flowchart LR
    A[見つける] --> B[重さを決める]
    B --> C[抑える]
    C -->|直す見込みが無い| R[戻す]
    C --> D[直す]
    R --> D
    D --> E[確かめる]
    E --> F[記録と振り返り]
```

文字の代わり: 見つける → 重さを決める → 抑える（必要なら戻す）→ 直す → 確かめる → 記録と振り返り。

| 段 | すること | 誰が |
|---|---|---|
| 1. 見つける | 次のどれかで気づく。画面でログインできない・エラーが出る／`docker compose --profile targetdb-postgres ps` で `app` が `healthy` でない／手元の監視を起動しているときの Grafana の「Alerting」→「Alert rules」（フォルダー MasterSmith）の発火／`docker compose logs app` の ERROR。通知は無いため、アプリを使う前に `ps` で状態を見る習慣を置く | 依頼者（AI は頼まれたら読み取りで調べる） |
| 2. 重さを決める | 1節の表で「高」か「低」を決め、記録（3節）を始める。`runbooks.md` の見分け方で、どの手順に当たるかを決める | 依頼者が決める。AI は見分けの材料（状態・ログの件数）を出す |
| 3. 抑える | 影響が広がらないようにする。例: 大きな DSL の操作をやめる、負荷の試験をやめる、止まったアプリを起動し直す、秘密情報の疑いなら署名鍵を替える。**直す前に、消えると困る材料を残す**（コンテナを作り直すとログが消えるため、先にログをリポジトリの外へ保存する。`runbooks.md` の 0節）。直す見込みが立たない「高」は戻す（RB-10） | 依頼者が決める。止める・戻す・データを戻す操作は依頼者の承認の後（`escalation-matrix.md`） |
| 4. 直す | 原因を直す。設定の誤りは `.env` を直して作り直す（依頼者）。コードの不具合は、通常の Intent（bugfix の範囲）で、不具合を再現するテストと一緒に直して配備する（`project.md` の Mandated） | 依頼者。コードの直しは新しい Intent |
| 5. 確かめる | `healthy`・ヘルスチェック `{"status":"UP"}`・スモークテスト（README の「コンテナでの起動と確認」）・ログに ERROR が無いこと・当てはまる手順の「確かめ方」 | 依頼者（ログイン・スモークテスト）、AI（状態・ログの読み取り） |
| 6. 記録と振り返り | 記録を閉じる。「高」は事後の振り返り（4節）を必ず行う | 依頼者（AI は下書きを補助） |

- 確かめのために送る要求（ログイン・DSL の操作・存在しない API への要求など）は監査ログに残り、消せない。AI が送るときは、送る前に依頼者に伝える（`project.md` の Corrections）。
- パスワードなど秘密情報が要る操作（初期管理者でのログインなど）は依頼者が行い、AI は監査イベントとログで裏付ける（`project.md` の Corrections）。

## 3. 記録の仕方

1件の障害ごとに、時刻の順に「時刻・操作・判断」を書く。記録の置き場は依頼者と決める（下の「置き場の候補」）。

記録の形（見本）:

```text
# 障害の記録 <YYYY-MM-DD>-<短い名前>
- 重さ: 高 / 低（途中で変えたら、その時刻と理由も）
- 見つけたきっかけ: 画面 / docker compose ps / Grafana の警報名 / ログ
- 動いていた版: <コミットのハッシュ>（イメージ mastersmith:local の ID）
- 影響: 使えなかった機能・時間（利用者は依頼者だけ）

| 時刻（JST） | 操作（実行したコマンド・画面の操作） | 判断（なぜそうしたか・結果） |
|---|---|---|
| 10:02 | docker compose --profile targetdb-postgres ps | app が unhealthy。重さを高にした |
| 10:05 | ログをリポジトリの外へ保存 | 作り直すと消えるため |

- 閉じた時刻と、確かめた結果:
- 事後の振り返り（高のとき）: 4節の形で
```

- 時刻は JST で書き、ログ（`timestamp`、`+09:00` つき）と照らせるようにする。監査の `occurred_at` は UTC のため、照らすときは9時間ずらす。
- コマンドは実際に打ったものを書く。**秘密の値（パスワード・トークン・署名鍵・対象DB の接続情報）は書かない**。ログを貼るときも、値が入っていないことを確かめてから貼る（`project.md` の Forbidden）。
- メールアドレス・接続元IP など個人に関する値は、記録に書き写さない。必要なら「利用者 ID 1」「接続元は 127.0.0.1 のみ」のように書く。

置き場の候補（依頼者が決める）:

| 候補 | 良いところ | 気をつけること |
|---|---|---|
| リポジトリの外（例: `~/.mastersmith-incidents/`、権限 700） | 個人に関する値や保存したログを置いても公開されない | バックアップと同じく、PC が壊れると失われる |
| リポジトリの中（例: `docs/incidents/`） | 版の履歴と一緒に残り、後の Intent から参照できる | リポジトリは公開（`team.md`）。個人に関する値・保存したログ・秘密の値を置かない。コミットは Gitleaks を通る |

保存したログ（`runbooks.md` の 0節）と内部DB の複写は、どちらの場合もリポジトリの外（`~/.mastersmith-backup/`・`~/.mastersmith-incidents/`）に置く。

## 4. 事後の振り返り

- **「高」の障害は、閉じた後に必ず振り返る**（`aidlc/spaces/default/memory/phases/operation.md` の Incident Response）。「低」は、同じものが2回目に起きたとき、または依頼者が望むときに行う。
- 振り返りは人を責めず、仕組みと手順を直すために行う。
- 書くこと:

| 項目 | 内容 |
|---|---|
| 経過 | 3節の記録の表（見つけた時刻・重さを決めた時刻・抑えた時刻・閉じた時刻） |
| 影響 | 止まっていた時間、失われたデータ（戻したときは、最後の複写の後の記録が失われる）、秘密情報の疑いの範囲 |
| 原因 | 直接の原因と、それを許した条件（設定・手順・テストの抜け） |
| 再発を防ぐこと | 具体的な直し（コードの直しは新しい Intent。不具合を再現するテストを同じコミットに入れる）、手順書（`runbooks.md`・README）の直し、警報やダッシュボードの足し |
| 学び | うまくいったこと・困ったこと。AI-DLC の決まりにすべきものは、Intent の学びの記録（learnings）の手順で `project.md` に足す |

- 振り返りで手順書の誤りや足りない手順が見つかったら、`runbooks.md` と README を直す。

## 5. 復旧の目標（Q2: A）

| 目標 | 値 | 意味 |
|---|---|---|
| RTO（戻すまでの時間） | **1日** | 障害に気づいてから1日の内に、アプリを使える状態（`healthy` とスモークテストの合格）に戻す |
| RPO（失ってよいデータ） | **最後の手での複写まで** | 内部DB（利用者・監査ログ・DSL のプレビューと適用の履歴）は、最後に `~/.mastersmith-backup/` へ複写した時点まで戻せればよい。その後の変更は失われうる |

- 内部DB の複写は今のまま手で行う。行う時: 配備の前・大きな DSL の操作の前・監査ログを確かめるとき（README の「内部DBのバックアップと戻し方」）。定期の複写は置かない。
- 今ある複写（2026-09-25 の配備のとき）: `~/.mastersmith-backup/mastersmith-data-202609250120-before-dsl.tgz`（配備の前、V4）・`mastersmith-data-202609250130-after-smoke.tgz`（スモークテストの後、V6）。これより後の変更は、次に複写するまで RPO の外にある。
- 複写は PC の中にだけある。PC そのものが壊れたときは戻せない（受け入れている危険）。
- DSL の適用の履歴は、内部DB のバックアップの代わりにしない（U4 の `reliability-design.md` 4節）。
- 見本の対象DB（`targetdb-postgres`）は業務のデータを持たないため、復旧の目標の外にある。壊れたら作り直す（`rollback-runbook.md` 6節）。

## 6. 配備先が決まったときに見直すこと

- 通知の先（Grafana の連絡先、メールなど）と、重さごとの知らせ方。
- 2人目の受け手を置くか、1日の RTO と手での複写で足りるか（定期の複写、クラウドのバックアップ）。
- SSM の手順・Incident Manager などクラウドの仕組みに `runbooks.md` の手順を移すか。

## Sources

- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/incident-response/incident-response-questions.md`（Q1: A・Q2: A・Q3: A、確認済みの要約）
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/deployment-pipeline/rollback-runbook.md`
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/deployment-execution/deployment-log.md`・`health-check-report.md`
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/environment-provisioning/environment-inventory.md`
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/observability-setup/alarms.md`・`log-queries.md`・`dashboards.md`・`slo-config.md`
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/construction/u4-dsl-management/nfr-design/reliability-design.md`（3節・4節）
- `docker/monitoring/provisioning/alerting/mastersmith.yaml`（`severity` のラベル）、`compose.yaml`（`app` の `ports`・`restart: "no"`・`logging`）
- `README.md`（「コンテナでの起動と確認」「内部DBのバックアップと戻し方」「監査ログ（U4）」「手元の監視（Grafana）」）、`backend/src/main/resources/logback-spring.xml`（`timestamp` の形）
- `aidlc/spaces/default/memory/team.md`（Way of Working・Deployment）、`aidlc/spaces/default/memory/project.md`（Deployment・Forbidden・Corrections）、`aidlc/spaces/default/memory/phases/operation.md`（Incident Response）
- `.claude/knowledge/aidlc-operations-agent/incident-response-guide.md`
- 読み取りだけで調べた今の環境（2026-09-25）: `docker ps`・`docker inspect mastersmith-app-1`（`healthy`、`OOMKilled` false、メモリ 2147483648、再起動の決まり `no`）・`docker volume ls`

## Assumptions & Open Questions

- [assumption] 記録の置き場は、依頼者がまだ決めていない。3節の候補のどちらにするか（またはほかの場所）を承認の場で決める。決まるまでは、リポジトリの外（`~/.mastersmith-incidents/`、権限 700）に置く前提で書いた。
- [assumption] 「高」の対応の速さは「気づいたらすぐ」とし、時間の数値（例: 1時間以内に着手）は置いていない。1名での運用で、気づくきっかけが依頼者の操作に限られるため。
