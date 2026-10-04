# 要件（261004-safety-carryover）

Initial description: 安全の機能の判断と持ち越し（第2の束と第1の束の持ち越し、team.md・イメージの固定先）。依頼の文の全文は `aidlc/spaces/default/intents/261004-safety-carryover/project-description.json` [desc]
Workflow-selected scope: bugfix（深さ Minimal） [scope]

## Intent の分析

Intent `260930-user-admin` の振り返りの第2の束（S2・P1）と、Intent `261003-user-admin-followup` の持ち越し（FR4.2-c・Tomcat の ERROR・言語の欄のフォーカス）、片付け（`team.md` の記述・イメージの固定先とダイジェスト）を扱う [desc]。

- **ねらい**:
  - 使える管理者がいなくなっても、`.env` の初期管理者の設定で再起動すれば、その利用者が有効な管理者として戻り、ログインできる。救った・作ったことが監査に残る。
  - ログインの p95 の余裕の縮みが、ぶれの範囲かどうかを数字で示す。
  - 前の Intent で確かめられなかった BUSY のログの結び付きと、Tomcat の ERROR の原因を確かめ、二重の ERROR なら直す。
  - 決まりの文書とイメージの固定を今の実態に合わせる。
- **種類**: 機能の追加（起動時の救済と監査、1点）、不具合の修正（例外のログの二重の出力、見立てが確かめられた場合）、確かめ（2点）、片付け（2点）。配備（開発者の PC 上のコンテナ）まで含める [desc]。
- **完了の目安**:
  - 救済と作成の監査が、結合テストで確かめられる（前の Intent の `feedback-loop.md` の S2 の目安）。
  - ログインの p95 の判定は FR2 の目安による（前の Intent の目安から変更。FR2.3）。
  - L3・L4 の traceId と、Tomcat の ERROR の原因が、結合テストで確かめられる。

## 機能の要件

### FR1 初期管理者の救済と、作成・救済の監査（S2。部品 `user`・`auth`・`audit`、K-25）

- **FR1.1** 起動のたびに、初期管理者の設定（`MASTERSMITH_AUTH_INITIAL_ADMIN_EMAIL`・`MASTERSMITH_AUTH_INITIAL_ADMIN_PASSWORD`）が有効で、そのメールアドレスの利用者が**既にいる**とき、次のどれか1つでも当たれば、その利用者を救う。救済専用の設定は置かない [Q1] [F2] [F4]。
  - (a) 利用が止められている（停止中）
  - (b) 管理者の印が無い
  - (c) 今のパスワードが、設定のパスワードと一致しない
- **FR1.2** 救うときは、次をすべて行う [F1]。
  - (a) 停止を解く
  - (b) 管理者の印を付ける
  - (c) ログインの失敗回数を 0 に戻し、ロックを解く
  - (d) パスワードを設定の値に置き換える（今のパスワードの規則を満たす値だけを受け付ける。満たさなければ今どおり作成も救済もせず WARN を出す）
  - (e) その利用者のリフレッシュトークンをすべて無効にする
- **FR1.2a** (a)〜(e) は1つのトランザクションで行い、全部が成功するか、全部が取り消されるかのどちらかにする（一部だけ反映された状態を残さない）。途中で失敗したときは、すべてを取り消し、アプリのログに ERROR を1行（伏せ字のメールアドレスと失敗の種類だけ）出して、起動は続ける。監査には救済の行を残さない（救済は起きていないため）[assumption]（レビューの R-03）。
- **FR1.3** (a)〜(c) のどれにも当たらない（有効な管理者で、パスワードが設定と一致する）ときは何もしない。今の INFO「初期管理者は既にいるため、作成しませんでした」はそのまま出す。
- **FR1.4** 利用者がいないときは、今どおり管理者の印つきで新しく作る。
- **FR1.5** 初期管理者を新しく作ったとき、および救ったときに、それぞれ監査に1行を残す。救ったときの行には、当たった条件（停止中・印なし・パスワードの不一致）が分かる値と、対象の利用者 ID を載せる。メールアドレス・パスワード（平文・ハッシュ値とも）は載せない（`project.md` の Forbidden）[Q1] [memory:M1]。
- **FR1.6** 起動時の出来事は要求が無いため、監査の行の接続元 IP には、IP ではないと分かる決まった値（例: `system`）を入れる。表の形（`audit_events.source_ip` の `NOT NULL`）は変えない [Q2]。
- **FR1.6a** 監査の行の形を次のとおり固定する。表の列は増やさず、移行も足さない（レビューの R-02）[Q2]。
  - 出来事の種類: 作成は `INITIAL_ADMIN_CREATED`、救済は `INITIAL_ADMIN_RESCUED`（どちらも `event_type` の 32 文字以内）。
  - 結果（`result`）: どちらも `SUCCESS`。
  - 対象の利用者（`target_user_id`）: 作った・救った利用者の ID。
  - 操作した人（`actor_user_id`）: 空（NULL）。起動時の操作で、操作した利用者がいないため。
  - 当たった条件（救済だけ）: 既存の 32 文字以内の列（`failure_reason` か `rejection_kind`。どちらにするかは Code Generation の計画で既存の列の使い方に合わせて決める）に、決まった順の短い値を `+` でつないで入れる。値は `SUSPENDED`（停止中）・`NO_ADMIN`（印なし）・`PASSWORD`（パスワードの不一致）とし、3つすべてで `SUSPENDED+NO_ADMIN+PASSWORD`（27 文字）になる。作成の行では空にする。
  - 入れた値の文字（メールアドレス `entered_email`・`user_agent`・`request_path`・`trace_id`）: 空（NULL）。`entered_email` にメールアドレスを入れない。
- **FR1.6b** 判定は、監査の行を種類・結果・対象の利用者・条件の列の値で数えて行う（例: `INITIAL_ADMIN_RESCUED` で条件が `SUSPENDED+NO_ADMIN+PASSWORD` の行がちょうど1行）。
- **FR1.7** 救済が働いたときは、アプリのログに WARN を1行出す（キーは伏せ字のメールアドレス `maskedEmail` と当たった条件だけ。メールアドレスそのもの・パスワードは出さない）[F5]。
- **FR1.8** 監査の書き込みに失敗しても、起動は止めない（今の監査の仕組みの決まりどおり、受け止めて ERROR を1回出す）[assumption]。
- **FR1.9** 手順書（RB-22 ほか、前の Intent の `runbooks.md` に当たるもの）を、救済の口に合わせて直す。あわせて「初期管理者を止めたい・印を外したいときは、先に `.env` の初期管理者の設定を替える（外す）。そうしないと次の再起動で戻る」を書く [F5]。
- **判定**:
  - Given 初期管理者の設定のメールアドレスの利用者が停止中で印が無く、パスワードが設定と違う
  - When アプリを起動する
  - Then その利用者は停止が解け、印が付き、失敗回数が 0、パスワードが設定の値になり、起動の前のリフレッシュトークンはすべて使えない。設定のパスワードでログインできる。監査に `INITIAL_ADMIN_RESCUED`・条件 `SUSPENDED+NO_ADMIN+PASSWORD` の行がちょうど1行、ログに WARN がちょうど1行ある。
  - Given (a)〜(c) のどれか1つだけに当たる（3通り）
  - When アプリを起動する
  - Then それぞれで救済が働き、監査に `INITIAL_ADMIN_RESCUED` の行が1行（条件の列が `SUSPENDED`・`NO_ADMIN`・`PASSWORD` のそれぞれ1つ）残る。
  - Given 救済の途中で書き込みが失敗する（テストで失敗を起こす）
  - When アプリを起動する
  - Then 利用者の停止・印・失敗回数・パスワード・リフレッシュトークンは起動の前のままで、監査に救済の行は無く、ログに ERROR が1行ある。起動は続く（FR1.2a）。
  - Given 有効な管理者で、パスワードが設定と一致する
  - When アプリを起動する
  - Then 何も変わらず、監査に行は増えない。
  - Given そのメールアドレスの利用者がいない
  - When アプリを起動する
  - Then 管理者が作られ、監査に `INITIAL_ADMIN_CREATED` の行がちょうど1行残る（対象の利用者は作った利用者、操作した人は空、接続元 IP は決まった値）。
  - Given 救済・作成のどちらの場合も
  - Then 監査の行・アプリのログ・トレースの属性に、メールアドレスそのもの・パスワード・トークンの値が含まれない（既存の `*SecretLeakIT` と同じ形で確かめる）。

### FR2 ログインの p95 の切り分け（P1。部品 `auth`・`perf-and-monitoring`、K-26）

- **FR2.1** 今の版のイメージを、使い捨ての環境・前の Intent と同じ条件（同じ場面 `loginSuccess`・同じ VU の数と時間・同じ上限）で **3回以上** 流し、ログインの p95 を回ごとに記録する [Q3] [F3]。
- **FR2.2** 合否は (b) **すべての回で p95 が 1 秒を下回ること** とする（主の判定。目標の 1 秒は緩めない）[desc]。あわせて参考として (a) 3回以上の p95 の幅（最小〜最大）と、前の Intent の値 939.6 ms がその幅に入るかを示す。3回の幅はぶれの大きさの目安にとどまり、(a) だけで「停止の判定の影響が無い」とは結論しない（レビューの R-04）。
- **FR2.2a** 回ごとに、比べられる条件を記録する: 流した日時、イメージのタグとダイジェスト、colima の VM の CPU・メモリ、アプリのコンテナの CPU・メモリの上限、`VUS`・`DURATION`、同時に動いていたコンテナ、電源（AC かバッテリー）と `caffeinate -i` の有無。条件がそろわない回は判定から外し、理由を記録する（レビューの R-04）。
- **FR2.3** 前の Intent の完了の目安（「停止の判定の有無を入れ替えた同じ条件の k6 で、差がぶれの幅に入るかを数字で示す」）を、この Intent では FR2.2 に変える。停止の判定の前の版は流さない。差をこの要件と Build and Test の記録に明記する [F3]。
- **FR2.4** k6 の試験の持ち主は Build and Test とする（この Intent に Performance Validation の段が無いため、`project.md` の学び）。台本全体を `caffeinate -i` で包む [memory:M2]。
- **判定**: Build and Test の記録に、回ごとの p95・条件（FR2.2a）・幅と (a) の参考・(b) の合否を実測の値で残す。(b) を満たさない回があれば、目標を緩めずに Not Met とし、依頼者に扱いを諮る。

### FR3 BUSY のときの L3・L4 のログの traceId（前の Intent の FR4.2-c の持ち越し。部品 `useradmin`・`common-persistence`・`common-error`、K-27）

- **FR3.1** 結合テストで 409 `USER_ADMIN_BUSY` を起こし（既存の `UserAdminBusyApiIT` と同じく、別のトランザクションで対象の行を持ち続ける）、アプリのログの JSON を捕まえて、L3（`GlobalExceptionHandler` の WARN、`code` が `USER_ADMIN_BUSY`）の各行に、同じ `traceId` の L4（`RowLockFailures` の WARN）がちょうど1行あることを確かめる [Q4]。
- **FR3.2** 負荷の試験（k6）では確かめない [Q4]。
- **判定**:
  - Given 対象の行を別のトランザクションが持ち続けている
  - When 利用者の管理の操作を送る
  - Then 409 `USER_ADMIN_BUSY` が返り、L3 が1行・同じ `traceId` の L4 が1行出る。`traceId` は空でない。

### FR4 フィルターの中の例外の ERROR（前の Intent の持ち越し。部品 `common-error`・`auth`、K-28）

- **FR4.1** 結合テストで、認証の要る要求の認証の途中（`AccessTokenAuthenticationProvider` の利用者の読み直し）で接続を借りられない状況を作り、Tomcat のロガーの ERROR（`Servlet.service() … threw exception`）と `ErrorPathController` の ERROR が、同じ `traceId` で2行出るかを、ログの JSON の `logger` の項目で数えて確かめる [Q5]。
- **FR4.1a** 接続を借りられない状況は、テストの中だけの設定で作る（例: テスト用の接続プールの上限を 1 にして別のスレッドが接続を持ち続け、借りる待ちの上限を短くする）。本番の設定とコードは変えない。作り方は Code Generation の計画で決め、待ち合わせは `sleep` ではなく決まった合図で行う（`team.md` の Testing Posture）（レビューの R-05）。
- **FR4.1b** 計画の作り方で、認証の途中で接続を借りられない状況を再現できなかったときは、再現を試みた範囲と結果を記録し、直し（FR4.2）は行わずに依頼者に扱いを諮る（推測で直さない）（レビューの R-05）。
- **FR4.2** 2行出る（同じ例外の ERROR が二重に出る）と確かめられたら、1つの要求の1つの例外について ERROR が1行だけ出るように直す（`team.md` の Code Style「例外のログは変換する境界で1回だけ出す」）。残す1行は、例外をエラー応答に変える境界（`ErrorPathController`）の ERROR「想定外のエラーが起きました」とし、原因の例外（スタックトレース）と `traceId` を持つこと。Tomcat のロガーの ERROR は出さない（レビューの R-05）。応答（500 の Problem Details、内部のメッセージを含めない）は変えない。直し方は Code Generation の計画で決める [Q5]。
- **FR4.3** 見立てと違う原因だったときは、確かめた原因を記録し、直すかどうかを依頼者に諮る。
- **判定**:
  - Given 接続を借りられない状況で、認証の要る要求を送る
  - When 応答が返る
  - Then 500 の Problem Details が返り、その要求の `traceId` の ERROR はちょうど1行である（FR4.2 で直した場合）。直す前の状態を再現するテストを同じコミットに含める（`project.md` の Mandated）[memory:M3]。

### FR5 言語の欄で Enter を押して送信したときのフォーカス（前の Intent の R-01（G2）の持ち越し。部品 `frontend-feature-useradmin`、K-29）

- **FR5.1** 直さない。既知の点として、利用者の管理の画面の既知の点の記録（手順書・README のどちらか、Code Generation の計画で決める）に残す [Q6]。

### FR6 `team.md` の「12 パッケージ」の記述（部品 `build-and-verify`、K-30）

- **FR6.1** `team.md` の Testing Posture の「2026-09-29 の時点で 12 パッケージ」を、今の `packagesJudgedByTotal` の 7 個（`access.service`・`audit.repository`・`common.error.domain`・`common.error.service`・`common.health`・`common.i18n.domain`・`common.web`）に合わせて直す。この Intent で一覧から外すパッケージがあれば、その後の数にする [desc]。
- **FR6.2** `team.md` は決まりの文書のため、直す文言は依頼者の確認を得てから書く（前の Intent で `team.md` を直したときと同じく Build and Test で行う）[assumption]。

### FR7 イメージの固定（部品 `container-runtime`・`perf-and-monitoring`・`backend-test-support`、K-31）

- **FR7.1** 対象DB のイメージの固定先（`compose.yaml`・`docker/perf/compose.yaml`・`TargetDbImages`）は、3か所の手での揃えのままとする。仕組みは変えない [Q7]。
- **FR7.2** 次のイメージにダイジェストを付ける（版のタグは残し、`タグ@sha256:…` の形）[Q8]。
  - `Dockerfile` の `FROM eclipse-temurin:25.0.4_7-jre-noble`
  - `compose.yaml` の `otel/opentelemetry-collector:0.162.0`・`grafana/otel-lgtm:0.34.0`
  - `perf/README.md` の `grafana/k6:2.3.0`・`eclipse-temurin:25.0.4_7-jdk-noble`
- **FR7.3** 固定の決まりの記録を今の実態に合わせて直す: `project.md` の Tech Stack の学び（3か所を一緒に上げる）を補う形で、ダイジェストを付けたイメージと、Dependabot が見ないもの（`docker/perf/compose.yaml`・`TargetDbImages`・`perf/README.md`）は手で揃えることを、`.github/dependabot.yml` のコメントか README に書く。`project.md` に足すときは学びの手順（承認を得て）で行う [Q7] [Q8]。
- **判定**: FR7.2 の5つのイメージがダイジェスト付きで書かれ、`docker compose config` が通り、`Dockerfile` からアプリのイメージを作れて起動の確かめが通る。ダイジェストは、付ける時点のタグが指すものを `docker buildx imagetools inspect` などで確かめた値にする。
- **FR7.4** 付けるダイジェストは、1つのアーキテクチャのイメージのダイジェストではなく、複数のアーキテクチャをまとめた index（manifest list）のダイジェストとする。その index に `linux/arm64`（手元の Mac の colima）と `linux/amd64`（CI の GitHub Actions）の両方が含まれることを確かめて記録する（レビューの R-06）。既存のダイジェスト付きのイメージ（対象DB・Mailpit）は今回変えない。

### FR8 配備（部品 `container-runtime`）

- **FR8.1** 統合した版を、開発者の PC 上のコンテナ（compose のプロジェクト `mastersmith`）へ手で配備する。前の Intent の配備の手順を正とし、今回の差（救済の口・新しい監査の種類・`Dockerfile` のダイジェスト）だけを Deployment Pipeline の段で書く [desc] [memory:M4]。
- **FR8.2** 配備の前に、配備したアプリの初期管理者が救済の条件（停止中・印なし・パスワードの不一致）に当たりうるかを確かめる。値（メールアドレス・パスワード）は表示しない。確かめ方は、依頼者に「初期管理者を画面でパスワードを変えた・止めた・印を外したことがあるか」を聞き、あわせて内部DB の複写で初期管理者の停止と印の有無だけを読む（パスワードの一致はアプリでしか判定できないため、依頼者の答えで見込む）。救済が働く見込みなら、配備の前に依頼者に「パスワードが `.env` の値に戻り、ログイン中の端末はログインし直しになる」ことを伝えて了承を得る（レビューの R-01）。
- **FR8.3** 配備の後の確かめで、起動のログ（初期管理者の INFO・WARN）を、値を出さず件数とキーの名前だけで見る（`project.md` の学び）。合否は次のどちらかに当たること（レビューの R-01）。
  - 救済が働かなかったとき: 救済の WARN が 0 行、監査の `INITIAL_ADMIN_RESCUED`・`INITIAL_ADMIN_CREATED` が 0 行。
  - 救済が働いたとき: 救済の WARN が1行、監査の `INITIAL_ADMIN_RESCUED` が1行で、条件の列が FR8.2 の見込みと一致する。依頼者が `.env` のパスワードでログインでき、利用者の管理の画面が開ける。
  - 見込みと違う結果（見込みが「働かない」なのに働いた、またはその逆）のときは、合否を決める前に依頼者に諮る。

## 非機能の要件

- **NFR1 漏えいの防止**: 救済と作成の経路で、メールアドレスそのもの・パスワード（平文・ハッシュ値）・トークンを、アプリのログ・監査ログ・トレースの属性・エラー応答に含めない（`project.md` の Forbidden）。`InitialAdminProperties.toString()` がメールアドレスを伏せずに文字列にする点（K-25）は、救済の口を足すのにあわせて伏せる形にそろえる（`team.md` の Code Style の「個人に関する値を持つ record は `toString` で伏せる」）。
- **NFR2 認証・認可・監査のテスト**: 救済は認証と利用者の状態に関わるため、失敗の場合を含めて確かめる（`project.md` の Mandated）。少なくとも、救済の後に起動の前のリフレッシュトークンが拒否されること、救済の後に設定のパスワードでログインできること、救済の条件に当たらないときに何も変わらないこと、設定が無い・不正なときに作成も救済もしないこと、を結合テストで確かめる。
- **NFR3 起動時間**: 起動のたびに行うパスワードの照合（bcrypt、cost 12）は1回だけとし、起動を目立って遅くしない（1回の照合はおよそ数百 ms の見込み）[assumption]。
- **NFR4 検査**: 統合の前に `./gradlew verify` を通す。コンテナの実行環境を動かし、対象DB のテストも含める（`team.md`）。画面の変更は無いため、E2E は統合の前の必須の対象にしない。ただし救済で初期管理者の状態が変わりうるため、E2E を流すときは初期管理者が救済の条件に当たらないことを確かめる [assumption]。
- **NFR5 カバレッジ**: 行 80%・分岐 70% の下限を下回らない。手を入れるパッケージが `packagesJudgedByTotal` に当たれば（例: `audit.repository`・`common.error.service`・`common.error.domain`・`common.web`）、そのパッケージの下限を満たして一覧から外す（`team.md`）。手を入れる見込みのパッケージの今の値は Code Generation の計画で実測する。
- **NFR6 回帰のテスト**: 不具合（FR4 の二重の ERROR）を直すときは、再現するテストを同じコミットに含める（`project.md` の Mandated）。
- **NFR7 性能の目標**: ログインの p95 は 1 秒を下回る（前の Intent からの目標。緩めない）。

## 制約

- 救済の口は起動時の設定だけで働き、画面・API からは操作できない [Q1]。
- `audit_events` の表の形は変えない（`source_ip` の `NOT NULL` のまま）[Q2]。
- 機能の間の依存の向き（`auth` → `user`、`audit` → `user` など）は ArchUnit の境界テストで固定されている。救済は `user`（利用者）と `auth`（失敗回数・ロック・リフレッシュトークン）の両方に手が入るため、依存の向きを崩さない置き場（出来事で知らせる・`auth` 側に置くなど）を Code Generation の計画で決める。既存の境界テストを緩めるときは計画に明記して承認を得る（`team.md` の Code Style）。
- 負荷の試験は使い捨ての環境で行い、配備したアプリのデータと監査ログを汚さない。配備したアプリを止めるときは、先に依頼者に伝える（`project.md` の Testing Posture の学び）。
- `vendor/make-you-chic-ui` の中身は直接変更しない（`project.md` の Forbidden）。
- `git push` は依頼者が行う。コミットは提案して承認を得てから行う。

## 前提

- 監査の書き込みに失敗しても起動を止めない（FR1.8）。今の監査の仕組みの決まり（記録の失敗を呼び出し元へ伝えない）に合わせた [assumption]。
- `team.md` の直しは Build and Test で依頼者の確認を得て行う（FR6.2）[assumption]。
- 救済の途中の失敗では全部を取り消し、起動は続ける（FR1.2a）[assumption]。
- 起動のたびのパスワードの照合は起動を目立って遅くしない（NFR3）[assumption]。
- E2E は画面の変更が無いため統合の前の必須にしない（NFR4）[assumption]。

## 範囲の外

- 救済専用の設定（救済を有効にする印・救済専用のメールアドレス）[F2]。
- 画面・API からの救済、利用者を消す仕組み、すべての利用者のトークンをまとめて無効にする仕組み。
- 言語の欄のフォーカスの直し（FR5）[Q6]。
- イメージの固定先を1か所にまとめる仕組み・一致の検査・Dependabot の対象の追加（FR7.1）[Q7]。
- 停止の判定の前の版での k6（FR2.3）[F3]。
- 負荷の試験での BUSY の確かめ（FR3.2）[Q4]。
- 前の Intent の第3の束（配備先の決定を待つもの）。

## 受け入れる振る舞い（依頼者の決定）

この節は承認の場で依頼者に確かめる（レビューの R-07）。

- **範囲**: FR1（救済の口）は、bugfix の範囲の中での機能の追加である。前の Intent の振り返り（第2の束の S2）で「口を設けるか」を要件定義で決めるとしていたため、この Intent で扱う [desc] [Q1]。
- **安全の面での後退**: 下の2つ目（わざと止めた初期管理者が再起動で戻る）は、今の「止めた利用者は管理者が解くまで使えない」から見ると安全の面での後退である。依頼者は F5 でこれを受け入れ、`.env` を先に替える運用で補う [F5]。

- 初期管理者が画面でパスワードを変えても、次の再起動で設定の値に戻り、リフレッシュトークンも無効になる [F4]。
- ほかの管理者がわざと止めた・印を外した初期管理者も、次の再起動で有効な管理者に戻る。`.env` の値も漏れていれば、漏れた資格情報で再び入れる。これを避けるには、先に `.env` の初期管理者の設定を替える（外す）（FR1.9）[F5]。
- 初期管理者がロックされただけ（停止も印の変更も無く、パスワードも一致）のときは救済は働かない。ロックは今どおり時間で解ける（30 分）か、ほかの管理者が失敗回数を戻す [F4]。

## 未解決の点

- FR1.6a の当たった条件を入れる列（`failure_reason` か `rejection_kind`）は、Code Generation の計画で既存の列の使い方に合わせて決める。
- FR4.1a の接続を借りられない状況の作り方は、Code Generation の計画で決める。
- FR1 の救済の置き場（依存の向きを崩さない形）は、Code Generation の計画で決める（「制約」の節）。
- FR4.2 の直し方は、FR4.1 の確かめの結果を見て Code Generation の計画（またはその承認の場）で決める。
- FR5.1 の既知の点の記録の置き場は、Code Generation の計画で決める。

## Sources

- 依頼の文: `aidlc/spaces/default/intents/261004-safety-carryover/project-description.json` [desc]
- 質問と答え: `aidlc/spaces/default/intents/261004-safety-carryover/inception/requirements-analysis/requirements-analysis-questions.md` [Q1]〜[Q8]、追加の質問 [F1]〜[F5]
- 前の Intent の振り返り: `aidlc/spaces/default/intents/260930-user-admin/operation/feedback-optimization/feedback-loop.md`（第2の束 S2・P1）、手順書 `aidlc/spaces/default/intents/260930-user-admin/operation/incident-response/runbooks.md`（RB-22）
- 前の Intent の持ち越し: `aidlc/spaces/default/intents/261003-user-admin-followup/construction/build-and-test/build-and-test-summary.md`（FR4.2-c・Tomcat の ERROR・R-01（G2））
- コード知識ベース: `aidlc/spaces/default/codekb/mastersmith2/business-overview.md`（所見 K-25〜K-31 の一覧）、`aidlc/spaces/default/codekb/mastersmith2/architecture.md`（Interaction Diagrams 1〜4）、`aidlc/spaces/default/codekb/mastersmith2/code-structure.md`、`component-inventory.md`（K-29）、`code-quality-assessment.md`（K-30）、`dependencies.md`（K-31）
- 開発担当のスキャン: `aidlc/spaces/default/intents/261004-safety-carryover/inception/reverse-engineering/developer-scan.md`
- 決まり:
  - [memory:M1] `aidlc/spaces/default/memory/project.md` Forbidden（パスワード・トークン・メールアドレスをログ・監査・エラー応答に含めない）、Mandated（利用者の権限・状態を変える操作を監査に残す、認証・認可・監査の変更に失敗の場合のテストを含める）
  - [memory:M2] `project.md` Testing Posture の学び（Performance Validation の段が無いときは k6 の持ち主を Build and Test にする、`caffeinate -i` で台本全体を包む）
  - [memory:M3] `project.md` Mandated（不具合の再現のテストを同じコミットに含める）
  - [memory:M4] `project.md` Deployment の学び（前の Intent の配備の手順を正として差だけを書く）

## Assumptions & Open Questions

- 前提は「前提」の節のとおり（`[assumption]` の5件）。FR1.2a・FR1.8・NFR3・NFR4 は Code Generation と Build and Test で、FR6.2 は Build and Test で確かめる。
- 未解決の点は「未解決の点」の節のとおり（条件を入れる列、FR4.1a の作り方、救済の置き場、FR4.2 の直し方、FR5.1 の記録の置き場）。
- レビューの指摘 R-01〜R-07 は、依頼者の Request Changes（「R-01〜R-07 をすべて反映して」）で反映した（R-01: FR8.2・FR8.3、R-02: FR1.6a・FR1.6b、R-03: FR1.2a、R-04: FR2.2・FR2.2a、R-05: FR4.1a・FR4.1b・FR4.2、R-06: FR7.4、R-07: 「受け入れる振る舞い」の節）。いずれも Code Generation の計画で決める。
