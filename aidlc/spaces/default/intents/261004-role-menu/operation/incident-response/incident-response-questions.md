# Incident Response — 質問

障害の対応の記録（runbooks・incident-plan・escalation-matrix）は、次の3つの Intent で作りました。

- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/incident-response/`（以下「元の手順書」）: 0節、RB-01〜RB-12、警報ごとの初動の表、定期の確認。
- `aidlc/spaces/default/intents/260925-user-management/operation/incident-response/`（以下「2つ目の手順書」）: RB-13〜RB-20 と、元の手順書の直し。
- `aidlc/spaces/default/intents/260930-user-admin/operation/incident-response/`（以下「前の手順書」）: RB-21〜RB-27、索引（RB-01〜RB-27）と、前の2つの直し。

その後の Intent 261003-user-admin-followup・261004-safety-carryover には、この段がありませんでした。この Intent で増えた障害と、手順書の古くなった箇所を、読み取りだけで洗い出しました（2026-10-15）。この Intent で増えたのは次の機能です。
- U1 API の分類・U2 DSL の版 2
- U3 グループ・U4 ロールと権限・割り当て・作業ロール・権限の受け渡し
- U5 業務のメニューと置き場・U6 管理の画面・U7 骨組みの画面

この段では、コンテナを起動・停止しておらず、配備したアプリに要求を送っていません。

## 1. この Intent で増えた、起こりうる主な障害と、今の手順書で扱えるか

| # | 障害 | 今の手順書・README で扱えるか |
|---|---|---|
| F1 | **ロール・グループの取り合い**: 409 `ROLE_BUSY`・`GROUP_BUSY`（行の排他 3 秒・一意の鍵 約 2 秒）。状態は変わらず、監査に残らない | 一部。README は「少し待ってやり直す」だけを書く。見方は `operation/observability-setup/log-queries.md` の L1・L3・L5。前の手順書の RB-24（`USER_ADMIN_BUSY`）と同じ形の RB が無い |
| F2 | **権限の受け渡しの入口の取り合い**: 確かめ・適用は同時に1つずつで、2つ目は 409 `ROLE_BUSY` と WARN「権限の受け渡しの入口を取れませんでした」（`lockKind=ROLE_TRANSFER_SLOT`） | 一部。README の「同時に1つずつ」と `log-queries.md` の L4。RB は無い |
| F3 | **権限の YAML の確かめ・適用の大きさ**: 5 MiB を超えると 413。書き出しは上限を超えても止まらず、`X-Role-Transfer-Exceeds-Import-Limit: true` が付く。確かめの後に設定が変わると 409 `ROLE_TRANSFER_STALE`。変える点が無いと 409 `ROLE_NO_CHANGE`（`FAILURE`/`NO_CHANGE` が監査に残る。Observability Setup で実機で見た） | 一部。README の「権限の受け渡し（B6）」に分け方と code がある。RB は無い |
| F4 | **ヒープが尽きて JVM が止まり、起き直さない**（`ExitOnOutOfMemoryError`、`restart: "no"`）。警報は `ms-app-absent` だけ（止めてから鳴るまで 10 分 20 秒。配備したアプリは外部エクスポートが無効のため、今は鳴らない） | 扱える（`operation/deployment-pipeline/rollback-runbook.md` 7節、`log-queries.md` 4節）。ただし、手順の中の「終了コード 3・OOMKilled false」は Build and Test の値で、**配備と同じ compose の形で実際に尽きさせて、`docker compose start` で起き直す流れは確かめていない**。Loki に止まる直前の行が届かないことも `Unverified` |
| F5 | **書き出し・DSL の 10 MiB の投入と適用が重なってヒープを使う**（入口の外。受け入れた制約） | 一部。README の注意だけ。尽きれば F4 |
| F6 | **DSL の版**: 版 1 は 422（読めない）。この版から前の版へ戻すと、DSL が無い状態で起動する（ERROR 1件）。上げ直すと版 1 がまた読めない | 扱える（`rollback-runbook.md` 2.1・2.2・4節。戻しの練習 D1〜D6 で確かめ済み） |
| F7 | **ロールの削除・割り当ての外しで見えなくなる**: ロールを消すと、それを指す作業ロールの保存も消える。外れたロールは「最初のロール」に読み替わる。ロールが無いとメニューが空（`NOTHING_VISIBLE`） | 一部。README の「作業ロールの決め方」。利用者から「急に見えなくなった」と知らされたときの調べ方の RB が無い |
| F8 | **最後の管理者の扱い**: 管理の可否は今も `users.admin_flag` だけで決まる（ロールでは管理の API を使えない。`RoleGrantsNoAdminAccessIT`）。最後の有効な管理者の保護は変わらない | 前の手順書の RB-21〜RB-23 のまま当たる。**ただし前の手順書の RB-22（`.env` を新しいアドレスに替えて管理者を作る）は古い**。Intent 261004-safety-carryover の「初期管理者の救済」（起動のたびに、停止・印なし・パスワード不一致の初期管理者を救う。`INITIAL_ADMIN_RESCUED`）が README にあり、手順が変わった |
| F9 | **置き場の 403 の多さ**: `ms-forbidden` の数に入り、Observability Setup の確かめでは置き場の 403 だけで 13 件（しきい値 20 の 65%）。監査に残らない | 扱える（`log-queries.md` 3節の見分け方、`alarms.md` 2.2 の申し送り）。前の手順書の警報ごとの初動の表には無い |
| F10 | **画面の固まり**（S8: 自分の権限の木でテーブルの開閉を押すと固まった。a16cd68 で直して配備済み）。同じ種類の画面だけの不具合は、サーバーのログ・指標・監査に出ない | 無い。S8 の調べ方（依頼者の操作を先に聞く・使い捨ての環境で同じ操作を試す・ブラウザの開発者ツール）は `project.md` の学びと `operation/deployment-execution/deployment-log.md` 10節にだけある |
| F11 | **入れ替えの後の古いタブ**: 画面の部品の読み込みに失敗しうる（README「入れ替えの後」） | 一部。README だけ |
| F12 | **この Intent の版を戻す**: 戻し先は `mastersmith:pre-role-menu`。前の版にはロール・グループ・業務のメニューが無い（表は残る）。前の版は `ExitOnOutOfMemoryError` を持たない | 扱える（`rollback-runbook.md`）。前の手順書の RB-26 の戻し先は古い |

古くなった箇所（前の3つの手順書）:
- **RB-22**: 救済に置き換わった（F8）。
- **RB-27**（行の「操作」のメニューのはみ出し）: Intent 261003-user-admin-followup の FR2 で直った。
- **RB-26・RB-20・RB-10 の戻し先**: 今は `mastersmith:pre-role-menu`。
- **警報ごとの初動の表**: Observability Setup の結果を反映していない。`ms-app-absent` が鳴るまでの時間、`ms-forbidden` の置き場の 403、区画のパネルが要る。
- **最新の複写の場所**: 今は `~/.mastersmith-backup/mastersmith-data-202610142218-after-role-menu-final.tgz`（配備の後）と `…-202610140820-pre-role-menu.tgz`（配備の前）。

## 2. 決まっていること・読み取りで確かめたこと（質問にしません）

| 決まっていること | 出典 |
|---|---|
| 受け手と判断者は依頼者1名。AI は頼まれたときに調べと手順の実行を補助する。止める・戻す・データを戻す操作は、依頼者の承認を得てから行う。知らせは Grafana の画面で見るだけ（通知の先は置かない） | 元の手順書の段の Q1: A、`aidlc/spaces/default/memory/team.md`・`project.md` の Deployment |
| 重さは2段（高・低） | 元の手順書の段の Q2: A |
| クラウドの仕組み（SSM・Incident Manager・AWS Backup）は、配備先が決まるまで作らない。手作業の手順で代える | `project.md` の Deployment |
| JVM が止まったときは `restart: "no"` のまま、`rollback-runbook.md` 7節の手順で原因を見てから手で起動し直す（`docker compose start app`）。自動で起き直す設定にはしない | Deployment Pipeline の Q6: A（Build and Test の Q2: A の前提） |
| 戻しはイメージだけ（`mastersmith:pre-role-menu`）が第一の手。スキーマは前進のみ（V12 のまま）。配備の前の複写を戻すのは、移行の失敗・データの破損のときだけ。戻した後は前の版で DSL（版 1）を生成し直す | `rollback-runbook.md` 2〜4節（Deployment Pipeline の Q1: A） |
| 警報は足さない・変えない。`ROLE_BUSY`・`GROUP_BUSY`・業務の 409・404 は警報にせず、`log-queries.md` の問い合わせで見る。`ms-forbidden` の式も変えない | Observability Setup の `alarms.md` 1節・2.2（Q4: A） |
| 警報の確かめの区別を手順書にそのまま書く。<br>・鳴ることを確かめた: `ms-forbidden`・`ms-app-absent`<br>・performance-validation で確かめる: `ms-pool-pending`・`ms-error-logs`・`ms-audit-fail`<br>・Unverified: `ms-5xx-ratio`・`ms-audit-slow` | `alarms.md` 2節 |
| 管理の可否は今も `users.admin_flag` だけで決まる。最後の有効な管理者の保護・利用者の管理の手順（前の手順書の RB-21・RB-23）はそのまま当たる | README の「ロールと権限の API」（管理の可否）、`rollback-runbook.md` 4節 |
| 使える管理者がいなくなったときは、前の手順書の RB-22 ではなく、README の「使える管理者がいなくなったとき（初期管理者の救済）」で戻す。手順書の RB-22 は古いとして直し、救済を参照させる | README、Intent 261004-safety-carryover |
| 古くなった箇所を直す。<br>・RB-27: 直ったとして閉じる<br>・RB-26 などの戻し先: `mastersmith:pre-role-menu` と `rollback-runbook.md` を参照させる<br>・警報ごとの初動の表: Observability Setup の結果を足す | 1節の表の下の一覧 |
| ロール・グループ・割り当て・作業ロールの変更は、依頼者が画面で行う。AI は管理者の資格情報で API を呼ばない。成功と業務の拒否は監査に残り、消せない。確かめのために送る要求が監査に残るときは、送る前に伝える | 前の手順書の `escalation-matrix.md` 3節・4節、`project.md` の Corrections |
| `BUSY` は待ってやり直す。続くときは `log-queries.md` の L3〜L5（同じ `trace_id` の排他の口の WARN）で見る。アプリを起動し直すと排他は外れる。重さは低 | group・role の `reliability-design.md`、README |
| 個人に関する値（メールアドレス・氏名・利用者 ID・送り元の IP）と、ロール・グループ・スキーマ・テーブルの名前は、手順・記録・報告に出さず、件数とキーの名前だけで見る | `project.md` の Corrections・Forbidden |
| 画面の不具合を調べるときは、依頼者がした操作（どのボタン・どの開閉、文言と場所）を先に聞き、同じ操作を試す。木の画面はすべての段の開閉を押して確かめる | `project.md` の学び（2026-10-14、S8） |

## Q1. 成果物の書き方

手順書は3つの Intent に分かれ、この Intent で4つ目になります。古くなった RB（RB-22・RB-27・戻し先）も増えています。

A. 前の3つの手順書を正とし、この Intent の差（RB-28 から）と古くなった箇所の直しだけを書く。`runbooks.md` の冒頭に、RB-01〜この Intent の最後までの索引の表を置く。表の列は、症状・重さ・置き場と「今は使わない（置き換え先）」の印（推奨）
B. 前の3つの手順書を写して直し、今の全体を1つの手順書にまとめ直す。以後の Intent は、このまとめを正として差を書く（読む場所は1つになるが、量が大きく、前の手順書と中身が重なる）
C. 前の Intent と同じく、差と索引だけを書く（古い RB の印は付けず、直しの表だけで示す）
X. Other (please specify)

推奨の理由: これまでと同じ書き方で量を抑えながら、障害のときに最初に開く索引だけで、どの RB が今は使えないかが分かるためです。まとめ直し（B）は、配備先が決まって手順を作り直すときにまとめて行うほうが、二度手間になりません。

[Answer]: A

## Q2. 復旧の目標（RTO・RPO）と、ロールの設定の守り方

RTO 1日・RPO は「最後の手での複写まで」です（元の手順書の段の Q3: A）。この Intent で、内部DB にロール・権限の設定・割り当て・作業ロール・グループが加わりました。今ある最新の複写は、配備の後の `…-202610142218-after-role-menu-final.tgz` です。この後に依頼者が画面で作るロールと権限は、次に複写するまで RPO の外にあります。一方、ロールと権限の設定（割り当て・グループ・作業ロールは含まない）は、画面の「書き出す」で YAML として取り出せます。確かめと適用（監査に残る）で戻すこともできます。

A. RTO・RPO の値は変えない。手順書の定期の確認に「ロール・権限の設定を大きく変えた後は、依頼者が画面で書き出した YAML をリポジトリの外（`~/.mastersmith-backup/`、権限 700）に保存する」を足し、RB に「設定だけを戻す」手順（確かめ → 適用）を書く。割り当て・グループ・作業ロールは、今までどおり内部DB の複写で守る（推奨）
B. RTO・RPO の値は変えず、手順も足さない（今までどおり内部DB の複写だけで守る）
C. A に加えて、ロール・グループ・割り当てを変えた日の終わりに内部DB を複写する決まりにする（アプリを止めるため、毎回承認が要る）
X. Other (please specify)

推奨の理由: アプリを止めずに、いちばん手間のかかる設定（1万カラムの権限など）を守る手が既にあり、戻す操作が監査に残るためです。ただし、取り出せるのはロールと権限の設定だけなので、割り当てなどは複写に頼ることを明記します。

[Answer]: A

## Q3. 手順の確かめ（実機で一度やってみる範囲）

この Intent の手順のうち、次の2つがまだ実機で確かめられていません。
- F4: JVM が止まったときの手順（`rollback-runbook.md` 7節）
- F8: 救済の手順。Intent 261004-safety-carryover の結合テストと配備で働かないことは確かめたが、救済が働くところは配備では見ていない

確かめるときも、配備したアプリと `.env` には触れず、配備したアプリに要求を送りません。

A. この段で、使い捨ての環境（`docker/perf/compose.yaml`、仮の署名鍵・仮の利用者、メモリの上限 1g、別の project 名と番号）で F4 だけを確かめる。
   - 尽きさせ方: `MASTERSMITH_JAVA_OPTIONS` で最大ヒープを小さくし、大きな権限の YAML の確かめを送ってヒープを尽かせる。
   - 見るもの: 終了コード・OOMKilled・`OutOfMemoryError` の行の件数（`docker compose logs`）、`restart: "no"` で起き直さないこと、`docker compose start` で healthy に戻ること。
   - 終わったら環境を消す。F8 は Intent 261004-safety-carryover の確かめを正とし、`Unverified` と書かない（推奨）
B. A に加えて、F8 も同じ使い捨ての環境で確かめる。仮の初期管理者を画面の外（SQL ではなく API）で止めた後、起動し直して救済の WARN・`INITIAL_ADMIN_RESCUED`・管理者の数を件数だけで見る。使い捨ての環境の監査にだけ残る
C. この段では確かめない。F4 は performance-validation の `roleTransferLarge` の間に止まったら手順を当てて確かめ、止まらなければ `Unverified` と書く
X. Other (please specify)

推奨の理由: 手順の中の値（終了コード 3 など）と「起き直さない」は、配備と同じ compose の形で一度も見ていません。そのうえ、配備したアプリが止まっても今は警報が鳴らないため、手順だけが頼りです。数分で確かめられ、Observability Setup で残した `Unverified`（止まる直前の行）も同じ場で見られます。救済は、前の Intent の結合テストと配備の記録で、働く条件と働かない条件を確かめ済みです。

[Answer]: A

## Q4. 画面だけの不具合（S8 のような固まり）の手順

S8 の固まりは直して配備しました。同じ種類の画面だけの不具合は、サーバーのログ・指標・監査に出ないため、警報でも定期の確認でも見つかりません。

A. RB を1つ足す（「画面が固まる・表示が崩れる・押しても反応しない」、重さは低、業務が止まるなら高）。中身は次の4つ。
   - 依頼者がした操作を先に聞く（`project.md` の学び）。
   - 依頼者がブラウザの開発者ツールで Network の状態コード・大きさ・時間と Console の誤りの種類を見る（値は記録しない）。
   - サーバー側の同じ時刻の WARN・ERROR とトレースを件数で見る。
   - 使い捨ての環境で同じ操作を試し、直すかどうかを依頼者に諮る。古いタブ（F11）は読み込み直しを先に試す（推奨）
B. RB は足さず、S8 を「直った既知の不具合」として索引に載せるだけにする
C. A に加えて、画面の誤りを集める仕組み（ブラウザの誤りをサーバーへ送る）を後の Intent の候補として `incident-plan.md` の「配備先が決まったときに見直すこと」に書く
X. Other (please specify)

推奨の理由: S8 では、操作を聞かずに試して「環境の差」と見立てを誤りました。その学びを手順にしておくと、次に同じ種類の不具合が起きたときに早く見分けられます。画面の誤りを集める仕組み（C）は、role-admin-ui・app-frame-ui の `monitoring-design.md` 1節で「配備先が決まり利用者が増えたときに改めて決める」と決まっているため、ここでは広げません。

[Answer]: A

## Consolidated Summary Confirmation

答えのまとめ: Q1 A（今回の差を RB-28 から書き、索引に置き換え先の印を付ける。全体のまとめ直しは配備先が決まったとき）、Q2 A（RTO・RPO の値は変えず、ロール・権限の設定を書き出した YAML の保存と、それで設定だけを戻す RB を足す。割り当て・グループ・作業ロールは内部DB の複写で守る）、Q3 A（使い捨ての環境でヒープを小さくして実際に JVM を止め、終了コード・起き直さないこと・docker compose start で戻ること・止まる直前の行を確かめる。配備したアプリには触れない）、Q4 A（画面だけの不具合の RB を1つ足す。依頼者の操作を先に聞き、開発者ツール・サーバーのログの件数・使い捨ての環境での同じ操作で調べる）。この内容で進めてよいか。

- Looks correct
- Request changes

[Answer]: Looks correct
