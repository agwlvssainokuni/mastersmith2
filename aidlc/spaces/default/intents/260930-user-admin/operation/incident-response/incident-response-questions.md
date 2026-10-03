# Incident Response — 質問

障害の対応の記録（runbooks・incident-plan・escalation-matrix）は、次の2つの Intent で作りました。

- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/incident-response/`（以下「元の手順書」）: 0節、RB-01〜RB-12、警報ごとの初動の表、定期の確認。
- `aidlc/spaces/default/intents/260925-user-management/operation/incident-response/`（以下「前の手順書」）: 元の手順書を正とした差。RB-13〜RB-20 と、古くなった箇所の直し。

その後の Intent 260928-quality-followup・260929-log-deps-cleanup には、この段がありませんでした。この Intent（利用者の管理: U1 利用停止・U2 ページ送りの共通化・U3 管理の API・U4 403 の画面・U5 利用者の管理の画面）で増えた障害を、読み取りだけで洗い出しました（2026-10-03）。この段では、確かめのための要求を配備したアプリに送っていません。

## この Intent で増えた、起こりうる主な障害

- **管理者の誤った操作**
  - 誤って印を外した・利用を止めた・失敗回数を戻した。
  - ほかに有効な管理者がいれば、画面から印を付け直す・停止を解くことで戻せます（監査に残る）。
- **使える管理者がいなくなる**
  - 最後の有効な管理者の保護（`USER_ADMIN_LAST_ADMIN`）で、有効な管理者（印があり停止していない。ロック中も数える）は 0 人になりません。
  - ただし「有効な管理者」がいても、その人がログインできないこと（パスワードを忘れた・資格情報の漏えいの疑いで使えない）は防げません。
  - 例: 依頼者の初期管理者がもう1人の管理者に印を外された・止められた後に、そのもう1人の資格情報が分からない。
  - アプリには、画面と API の外から印・停止を戻す口がありません。`InitialAdminInitializer` は、`.env` のメールアドレスの利用者がいないときに作るだけで、既にいる利用者の印・停止は戻しません（ソースで確かめた）。
- **停止した利用者が使えない・停止を解きたい**
  - 停止中は、ログイン（401 `AUTHENTICATION_FAILED`）・トークンの更新（401 `REFRESH_FAILED`）・アクセストークンの認証（401 `AUTHENTICATION_REQUIRED`）のすべてで拒まれます。応答は、ほかの失敗と見分けがつきません。
  - 停止中のログインは、監査の `LOGIN_FAILED`（理由 `ACCOUNT_SUSPENDED`）だけで見分けます。トークンの更新とアクセストークンの認証の拒否は、監査に残りません。
  - 止めたときに無効にしたリフレッシュトークンは、停止を解いても戻りません（ログインし直す）。
- **行の排他の時間切れ**
  - 5つの操作: 409 `USER_ADMIN_BUSY`。状態は変わらず、監査に残りません。WARN が2行（`RowLockFailures`「行の排他を取れませんでした」と、変換の境界）出ます。
  - 既存のログイン・招待・送り直し・取り消し・登録の完了の時間切れ（U3 の `security-design.md` 7.2 の E1〜E4）は、500 のままです。この Intent でログの形が変わり、repository の WARN（クラスの名前だけ）と、値を含まない例外の ERROR になりました。
- **確定の後の監査の書き込みの失敗**
  - 2本目の接続の待ちの時間切れなどで監査が欠けます。応答と状態は変わらず、ERROR「監査イベントの記録に失敗しました」（`actorUserId`・`targetUserId`）が出ます。
- **戻している間は利用停止が効かない**: `operation/deployment-pipeline/rollback-runbook.md` 3節の既知の制約。
- **行の「操作」のメニューのはみ出し**: Deployment Execution の S4 で見つかった既知の不具合です。後の Intent で直します。

## 決まっていること・読み取りで確かめたこと（質問にしません）

| 決まっていること | 出典 |
|---|---|
| 受け手と判断者は依頼者1名。AI は頼まれたときに調べと手順の実行を補助する。止める・戻す・データを戻す操作は、依頼者の承認を得てから行う。知らせは Grafana の画面で見るだけ（通知の先は置かない） | 元の手順書の段の Q1: A、`project.md` の Deployment |
| 重さは2段（高・低）。復旧の目標は RTO 1日・RPO は最後の手での複写まで | 元の手順書の段の Q2: A・Q3: A |
| 今ある最新の複写は `~/.mastersmith-backup/mastersmith-data-202610032204-after-user-admin.tgz`（配備の後、Deployment Execution の S5）。配備の前のバックアップは取っていない | `operation/deployment-execution/deployment-log.md` 2節、`rollback-runbook.md` 2節（Q1: B） |
| クラウドの仕組み（SSM・Incident Manager・AWS Backup）は、配備先が決まるまで作らない | `project.md` の Deployment |
| 戻しはイメージだけ（`mastersmith:pre-user-admin`）。スキーマ（V9）は戻さない。戻す前に停止中の利用者は確かめない。戻している間は利用停止が効かない制約を受け入れた | `rollback-runbook.md` 2節・3節（Q2: C、F1: A） |
| 警報は足さない。409（`USER_ADMIN_BUSY`・`USER_ADMIN_LAST_ADMIN` など）は警報にしない。BUSY・最後の管理者の保護の拒否は、`log-queries.md` の L1〜L4 と監査の問い合わせで見る | `operation/observability-setup/alarms.md` 1節、`log-queries.md` 2節・3節 |
| 「拾う」とした警報のうち、鳴ることを確かめたのは `ms-forbidden` だけ。`ms-pool-pending`・`ms-error-logs`・`ms-audit-fail` は performance-validation で確かめる（この段の次）。`ms-5xx-ratio`・`ms-audit-slow` は Unverified。手順書には、この区別をそのまま書く | `alarms.md` 2節 |
| 入口の 403 は指標では `uri="UNKNOWN"` に入り、道で分けられない。道は監査の `ACCESS_DENIED` の `request_path` で見る | `dashboards.md` 2節、`log-queries.md` 2節 |
| 前の手順書の「p95 の警報3件は鳴らない」は古くなった。`ms-login-p95` は値を返す（Intent 260928-quality-followup の FR4）。手順書の該当の行を直す | `alarms.md` 3節 |
| 誤って印を外した・止めた・戻したときは、ほかの有効な管理者が画面から付け直す・解く（監査に残る）。止めたときに無効にしたリフレッシュトークンは戻らず、本人がログインし直す。失敗回数を戻した操作は取り消せない（戻した後は 0 から数え直す） | README の「利用者の管理の API」、U3 の `security-design.md` 2節 |
| 古くなった箇所を直す: 元の手順書の RB-11・RB-17（前の手順書）・RB-18 の「その利用者の利用を控える」は、今は「利用を止める」（停止）でサーバー側で止められる。止めると、その利用者のリフレッシュトークンはすべて無効になり、アクセストークンも次の要求から拒まれる。ただし、自分自身と最後の有効な管理者は止められない（`SELF_OPERATION`・`LAST_ACTIVE_ADMIN`）。この場合の手当ては Q2 | README の「利用者の管理の API」、U1 の `security-design.md` 2.4・3節 |
| 古くなった箇所を直す: 元の手順書の RB-08（監査の書き込みの失敗・接続プールの待ち）に、5つの操作も確定の後に2本目の接続を使うこと（README の「既知の制約」）と、既存の経路の上限切れのログが WARN＋ERROR の2行になったこと（SD-5・SD-6）を足す | README の「監査ログ（U4）」、U3 の `security-design.md` 7.2・12節 |
| BUSY が続くときの手当ては、待ってやり直す。続くなら L3・L4（同じ `trace_id` の排他の口の WARN）と、元の手順書の RB-07（DSL_BUSY）・RB-08 と同じ見方をする。アプリを作り直すと排他は外れる。重さは低 | U3 の `reliability-design.md` 5節・10節、`log-queries.md` 2節 |
| 確かめのために送る要求が監査に残る・データを変えるときは、送る前に依頼者に伝える。利用者の管理の5つの操作は、成功も拒否も監査に残り、消せない | `project.md` の Corrections、README の「監査ログ（U4）」 |
| 個人に関する値（メールアドレス・氏名・利用者 ID・送り元の IP）は、手順・記録・報告に出さず、件数とキーの名前だけで見る | `project.md` の Corrections・Forbidden |
| 行の「操作」のメニューのはみ出しは、既知の不具合として手順書に書く。回避の方法は、確かめていないため「推測」と書く。直すのは後の Intent | `deployment-log.md` 3節（依頼者の決定 B）、`project.md` の学び（2026-10-03） |
| 後の Intent への持ち越し（N-19 閉じた後のフォーカス・Q-H・`ms-pool-pending` の式の見直しなど）は、手順書の「確かめられなかったこと」に参照だけを書く | `construction/code-generation/gate-decisions.md` 4節 |

## Q1. 成果物の書き方

前の Intent と同じく差だけを書くと、障害のときに読む手順書が3つの Intent に分かれます（元の手順書の RB-01〜RB-12、前の手順書の RB-13〜RB-20 と直し、この Intent の RB-21〜）。

A. 前の2つの手順書を正とし、この Intent の差（RB-21 から）と、古くなった箇所の直しだけを書く。加えて、`runbooks.md` の冒頭に「どの RB がどのファイルにあるか」の索引の表（RB-01〜この Intent の最後まで、症状・重さ・置き場）を置く（推奨）
B. 3つの手順書を写して直し、今の全体を1つの手順書にまとめ直す（読む場所は1つになるが、前の手順書と中身が重なり、量が大きい）
C. 前の Intent と同じく差だけを書き、索引の表は置かない
X. Other (please specify)

推奨の理由: これまでの運用の段と同じ書き方で量を抑えたまま、障害のときに最初に開く1か所（索引）で3つの置き場をたどれるため。

[Answer]: A 

## Q2. 使える管理者がいなくなったときの戻し方

画面と API で印・停止を戻せる管理者がいない場合の手当てです。例: 依頼者の初期管理者が印を外された・止められた。または依頼者の初期管理者の資格情報が漏れた疑いがあり、それが最後の有効な管理者のため止められない（`SELF_OPERATION`・`LAST_ACTIVE_ADMIN`）。アプリには、画面と API の外から戻す口がありません。

A. 【依頼者】`.env` の `MASTERSMITH_AUTH_INITIAL_ADMIN_EMAIL`・`_PASSWORD` を、まだいない新しいメールアドレスと新しいパスワードに替え、【承認】アプリを作り直す。`InitialAdminInitializer` が新しい管理者を作る（作成は監査に残らない。起動のログの INFO「初期管理者を作成しました」と `maskedEmail` だけ）。その管理者で画面から、元の利用者の印を付け直す・停止を解く・漏えいの疑いのある利用者を止める（どれも監査に残る）。終わったら、【依頼者】`.env` を元のメールアドレスに戻す（`rollback-runbook.md` 5節の「初期管理者のメールアドレスは変えない」に合わせる）。新しく作った管理者は消せない（利用者を消す仕組みが無い）ため、要らなければ止める（推奨）
B. 【承認】アプリを止め、内部DB のファイルを H2 の道具で書き込みで開き、SQL で `users` の印・停止の列を直接書き換える。監査に残らない直接の書き換えのため、手順書には最後の手としてだけ書く
C. 【承認】最新の複写（配備の後、`mastersmith-data-202610032204-after-user-admin.tgz`）を展開して戻す（複写の後の利用者・監査・招待の記録が消える）
X. Other (please specify)

推奨の理由: アプリの既存の仕組みだけで戻せ、印と停止を戻す操作は監査に残る。直接の書き換え（B）は `project.md` の Mandated（権限・状態を変える操作は監査に残す）を外れ、C は記録を失うため。

[Answer]: A 

## Q3. Q2 の手順の確かめ

Q2 の手順（A なら `.env` のメールアドレスを替えて作り直すと管理者が増え、元に戻した後の起動で何も作られないこと）は、まだ確かめていません。配備したアプリで確かめると、利用者が増え（消せない）、監査とデータに残ります。確かめるときも、配備したアプリには要求を送りません。

A. この段で、使い捨ての環境（`docker/perf/compose.yaml`、仮の署名鍵・仮の利用者、メモリの上限 1g）で確かめる。仮の初期管理者で起動した後、メールアドレスを替えて作り直し、起動のログの INFO と、内部DB の複写の管理者の数（件数だけ）を見る。元に戻して作り直し、何も作られないことを見る。API の要求は送らない。終わったら環境を消す（推奨）
B. 次の performance-validation の段で、使い捨ての環境を起動したときに合わせて確かめる（持ち主を移し、`runbooks.md` に書く）
C. 確かめない。手順書には `Unverified` と書き、実際に要るときか、配備先が決まったときに確かめる
X. Other (please specify)

推奨の理由: 最も重い新しい障害の戻し方で、配備したアプリと監査を汚さずに数分で確かめられるため。負荷の試験と同時に動かすと VM のメモリ（6GiB）が詰まるため、B より段を分けるほうがよい。

[Answer]: A 

## Consolidated Summary Confirmation

- 冒頭の「決まっていること」のとおり進める（受け手と判断者は依頼者1名、重さは高・低の2段、RTO 1日・RPO は最後の複写まで、イメージだけの戻しと戻している間は利用停止が効かない制約、警報は足さない、誤った操作は画面から戻す、BUSY は待ってやり直し調べる、古くなった箇所を直す）。
- 手順書は、前の2つ（dsl-schema-loader・user-management）を正とし、この Intent の差（RB-21 から）と古くなった箇所の直しだけを書く。`runbooks.md` の冒頭に、症状・重さ・置き場の索引の表を置く（Q1: A）。
- 使える管理者がいなくなったときは、依頼者が `.env` の初期管理者のメールアドレスとパスワードを新しいものに替えて作り直し、作られた管理者で画面から印と停止を戻す（監査に残る）。終わったら `.env` を元に戻し、要らなくなった管理者は止める（Q2: A）。
- Q2 の手順は、この段で使い捨ての環境（`docker/perf/compose.yaml`）で確かめる。初期管理者のメールアドレスを替えて作り直し、起動のログの INFO と管理者の数を件数だけで見る。元に戻して作り直し、何も作られないことを見る。API の要求は送らず、配備したアプリと `.env` には触らない（使い捨ての環境の設定だけで替える）。終わったら環境を消す（Q3: A）。
- 結果は `runbooks.md`・`incident-plan.md`・`escalation-matrix.md` に記録する。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
