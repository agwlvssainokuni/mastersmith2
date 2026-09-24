# Deployment Execution — 質問

前提（配備の前の確かめ。2026-09-25）:
- 配備の手順は `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/deployment-pipeline/deployment-strategy.md` 2節（15 の手順）と 3節（スモークテスト）。戻し方は同じフォルダの `rollback-runbook.md`。
- 配備する版は `develop` の先頭（`7bc1b68`。アプリの中身は `8961cb2` と同じ。CI は `6f212e8` で成功）。今動いている版は `10742a3`（`mastersmith:local`、healthy）。
- `.env` の9項目はそろっている（Environment Provisioning で確かめ済み）。見本の対象DB（`targetdb-postgres`）は Environment Provisioning で起動済みで、初期化・パスワードの認証・読み取りだけの権限を確かめ済み。
- 内部DB のスキーマは V5・V6 の2つ進む（前進のみ。移行の SQL はテーブルと NULL を許す列を足すだけ）。Flyway が起動のときに当てる。
- 配備の間（手順 8〜10）はアプリが止まる（数十秒〜2分）。
- スモークテストの要求（ログイン・既定の DSL の生成・ログアウト）は監査ログに残り、追記だけで消せない（project.md の決まりにより送る前に伝える）。監査イベントの確かめでは、アプリを一度止めて内部DB を複写する（止めると H2 のファイルが詰め直される。約 1 秒）。

## Q1. 配備を行う時期

A. 今すぐ行う（配備の間、アプリが数十秒〜2分止まる。スモークテストの要求と監査イベントの確かめのための停止を含む）
B. 依頼者が時期を指定する
X. Other (please specify)

[Answer]: A

## Q2. 古い版が新しいスキーマで起動できるかの確かめ（U4-MIGRATION。Build and Test から引き継いだ Unverified）

戻し方（`rollback-runbook.md` 2節）は、スキーマを戻さずに直前の版 `10742a3` で起動する前提。その前提が成り立つかを確かめる。

A. 配備の後に、配備したアプリとは別の使い捨てのコンテナで、戻し先のイメージ（`mastersmith:pre-dsl`）を移行後の内部DB の複写（スモークテストの後の監査イベントの確かめで作る複写）につないで起動し、healthy になること・ログに Flyway の誤りが無いことを確かめる（配備したアプリは止めない。使い捨てのコンテナと複写は終わったら消す）
B. 配備の後に、実際に戻しを練習する（アプリを一度 `10742a3` に戻して healthy とスモークテストを確かめ、また新しい版に戻す。その間の要求は古い版が受ける）
C. 確かめない（Unverified のまま、戻すときに初めて確かめる）
X. Other (please specify)

[Answer]: C

## Consolidated Summary Confirmation

回答をまとめると、この段では次のとおり進める。

1. 今すぐ配備する（Q1: A）。`deployment-strategy.md` 2節の手順 1〜13 を AI が行う。手順 4・5（`.env` の9項目）と手順 11（見本の対象DB の初期化）は Environment Provisioning で済んでいるため、数の確かめと状態の確かめだけを行う。手順 8 でアプリを止めて内部DB を `~/.mastersmith-backup/` に複写し、手順 7 で今のイメージに `mastersmith:pre-dsl` のタグを付けてから、手順 9 で新しい版を起動する。
2. スモークテスト（3節）は、ブラウザでの操作（ログイン・管理者向け領域・DSL の管理画面・今の状態・見本の対象DB からの既定の DSL の生成・ログアウト）を依頼者が行い、AI は健全性・ログ（ERROR が無い・生成の INFO・接続先とユーザー名がログに無い）と監査イベント（`LOGIN_SUCCEEDED`・`DSL_GENERATED`・`LOGGED_OUT`）で裏付ける。監査イベントの確かめでは、アプリを一度止めて内部DB を複写し、複写したファイルを読み取りで開く。個人に関する値は表示せず、値の有無だけを確かめる。
3. 古い版が新しいスキーマで起動できるか（U4-MIGRATION）は確かめない（Q2: C）。未確認のまま残る前提（戻すときに `10742a3` が V5・V6 の当たった内部DB で起動できること）と、次に確かめる機会（実際に戻すとき、または次の配備の前の戻しの練習）を記録する（project.md の決まり）。
4. 手順のどこかで通らなかったときは、`rollback-runbook.md` 1節の基準で戻すかどうかを依頼者に諮る（AI が戻す場合は依頼者の承認を得てから）。
5. 成果物は `deployment-log.md`・`smoke-test-results.md`・`health-check-report.md`。既知の制約（U4-STORAGE-RUN：大きな DSL の投入と適用を重ねたら起動し直す）を配備の記録に書く。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct

