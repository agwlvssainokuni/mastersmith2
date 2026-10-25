# 障害ごとの手順書（runbooks）

前の4つの手順書を正とし、この Intent で増えた障害（RB-36 から）と、古くなった箇所の直しだけを書きます（Q1: A）。この Intent で増えた機能は次のとおりです。
- U1 業務データの読み取り（定義・一覧・詳細）・U2 業務データの書き込み（作成・更新・削除）・U3 業務データの画面
- U4・U5 持ち越しの直し（make-you-chic-ui の固定先・行の操作の名前・axe-core 4.14.0・不安定なテストの直し）

障害のときは、まず下の「索引」で RB の置き場と、今も使えるかを引きます。

- **元の手順書**: `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/incident-response/runbooks.md`（0節・RB-01〜RB-12・13節〜15節）
- **2つ目の手順書**: `aidlc/spaces/default/intents/260925-user-management/operation/incident-response/runbooks.md`（RB-13〜RB-20・21節〜23節）
- **3つ目の手順書**: `aidlc/spaces/default/intents/260930-user-admin/operation/incident-response/runbooks.md`（RB-21〜RB-27・2節〜4節）
- **前の手順書**: `aidlc/spaces/default/intents/261004-role-menu/operation/incident-response/runbooks.md`（索引 RB-01〜RB-35・1節の直し・RB-28〜RB-35・2節〜4節）
- どの障害でも最初に行うことは、元の手順書の 0節のままです（状態・ログを見る、作り直す前にログを保存する、記録を始める）。
- 記号: 【承認】は、AI が行うときに依頼者の承認が要る操作です。【依頼者】は依頼者が行う操作です（`.env` の中身を決める編集、秘密情報が要るログイン、画面の操作）。印の無いものは読み取りだけです。
- 配備したアプリの compose のコマンドには、今までどおり `--profile targetdb-postgres` を付けます（`operation/deployment-pipeline/rollback-runbook.md`）。
- **値を出さない。** 次のものは、コマンドの出力・記録・報告に出しません。件数と、ロガー・キー・code の名前だけで見ます（`aidlc/spaces/default/memory/project.md` の Corrections・Forbidden）。
  - 業務データの行の値（主キーの値を含む）と、スキーマ・テーブル・カラムの名前
  - メールアドレス・氏名・利用者 ID・送り元の IP・パスワード・トークン、ロール・グループの名前
  - `.env`・`.env.targetdb` の値（`cat`・`grep` で値を出さない。数えるのは項目の名前と「値が空でないか」だけ）
- **業務データの行の作成・更新・削除と、行の有無の確かめは【依頼者】が画面で行います。** AI は利用者の資格情報で業務データの API を呼ばず、見本の PG に入って業務データの行を読みません（`escalation-matrix.md` 4節）。書き込みは対象DB と監査に残り、監査は消せません。
- ログの文言・code・監査の種類は、`backend/src/main/java/cherry/mastersmith/` のソース（`businessdata/`・`audit/`・`targetdb/config/`）、README、この Intent の `operation/observability-setup/` の確かめ（2026-10-25）で確かめたものです。確かめられなかったものは「推測」と書きます。
- 警報の確かめの区別（`operation/observability-setup/alarms.md` 2節・3節。前の手順書の表を置き換える）:

  | 区別 | 警報 |
  |---|---|
  | 鳴ることを確かめた（使い捨ての環境） | `ms-biz-target-db`（対象DB を止めてから 2分48秒）・`ms-forbidden`（業務データの 403 534 件で 1分42秒）・`ms-app-absent`（前の Intent。止めてから 10 分 20 秒） |
  | 鳴った（条件つき。使い捨ての環境） | `ms-5xx-ratio`（ほかの要求が少ないときだけ。ほかの要求が 30 件/秒を超えると 1% を下回る）・`ms-pool-pending`（60 秒ごとの1点に待ちが当たったときだけ） |
  | 鳴ることを確かめていない（Unverified、持ち主 feedback-optimization） | `ms-audit-fail`・`ms-audit-slow`（業務データの出来事も数に入ることは式の読み取りで確かめた） |
  | この Intent では確かめていない（前の手順書の区別のまま） | `ms-error-logs`（業務データの 503 は WARN のため数に入らないことは確かめた）・`ms-heap` ほか |

  **配備したアプリは外部エクスポートが無効のため、今はどの警報も配備したアプリでは鳴りません**（手元の監視は使い捨ての環境の確かめにだけ使っている）。配備したアプリで対象DB に届かないことに気づくのは、画面の 503 の表示です。

## 索引（RB-01〜RB-47）

「今」の列: **使う**＝そのまま使う。**直しを合わせて読む**＝1節の直しと一緒に読む。**使わない**＝置き換え先を読む。

| RB | 症状 | 重さ | 置き場 | 今 |
|---|---|---|---|---|
| RB-01 | アプリが止まる・healthy にならない | 高 | 元の手順書 | 直しを合わせて読む（JVM が止まって起き直さないときは RB-30） |
| RB-02 | メモリの上限（2g）に近い・OOMKilled | OOMKilled なら高、近いだけなら低 | 元の手順書 | 直しを合わせて読む（終了コード 3 は RB-30。業務データの大きな本文は 1節） |
| RB-03 | 内部DB のファイルの伸び | 低（ディスクが尽きたら高） | 元の手順書 | 直しを合わせて読む（業務データの書き込みの監査の行も伸びる。1節） |
| RB-04 | 見本の対象DB が止まった・応答しない | 低 | 元の手順書 | 直しを合わせて読む（業務データの 503 は RB-36、作り直しは RB-46） |
| RB-05 | 対象DB の設定の誤り | 低 | 元の手順書 | 直しを合わせて読む（書き込みの設定は RB-38） |
| RB-06 | 起動のときに適用中の DSL を読めない | 低（依頼者の判断で高） | 元の手順書 | 直しを合わせて読む（業務データの code は RB-41） |
| RB-07 | `DSL_BUSY` が続く | 低 | 元の手順書 | 使う |
| RB-08 | 監査の書き込みの失敗・接続プールの待ち | 低（続く・多い、内部DB の不調なら高） | 元の手順書 | 直しを合わせて読む（業務データの出来事は RB-39） |
| RB-09 | ログインの失敗の多発・ロック・回り込みの試み・送り元の不一致 | 低（知らないログインの成功などは高） | 元の手順書 | 直しを合わせて読む（3つ目の手順書の 1節） |
| RB-10 | 配備の後の不具合（戻す） | 高 | 元の手順書 | 使わない → RB-47 |
| RB-11 | 秘密情報の漏えいの疑い | 高 | 元の手順書 | 直しを合わせて読む（書き込みの資格情報は RB-45。1節） |
| RB-12 | そのほかの起動のときのログ | 低 | 元の手順書 | 直しを合わせて読む（書き込みの設定の WARN は RB-38） |
| RB-13 | メールの受け手が止まっている・招待のメールが届かない | 低 | 2つ目の手順書 | 使う |
| RB-14 | 招待を使えない設定（503 `INVITATION_NOT_CONFIGURED`） | 低 | 2つ目の手順書 | 使う |
| RB-15 | 起動が止まる設定の誤り（招待の長さ・メールのテンプレート） | 高 | 2つ目の手順書 | 使う |
| RB-16 | 招待の定期の削除の失敗 | 低 | 2つ目の手順書 | 使う |
| RB-17 | 総当たりの疑い（登録の完了・今のパスワード） | 低（知らない登録があれば高） | 2つ目の手順書 | 直しを合わせて読む（3つ目の手順書の 1節） |
| RB-18 | 招待のトークン・URL が漏れた疑い | 高 | 2つ目の手順書 | 直しを合わせて読む（3つ目の手順書の 1節） |
| RB-19 | `127.0.0.1` で開いたときの拒否・見た目の設定の不正 | 低（最初の描画が止まるなら高） | 2つ目の手順書 | 使う |
| RB-20 | Intent 260925-user-management の版を戻すときの注意 | 高 | 2つ目の手順書 | 使わない → RB-47 |
| RB-21 | 管理者の誤った操作（印・停止・失敗回数） | 低 | 3つ目の手順書 | 使う |
| RB-22 | 使える管理者がいなくなった | 高 | 3つ目の手順書 | **使わない** → README の「使える管理者がいなくなったとき（初期管理者の救済）」（前の手順書の 1節） |
| RB-23 | 停止中の利用者が使えない・停止を解きたい | 低 | 3つ目の手順書 | 使う |
| RB-24 | 行の排他の時間切れ（409 `USER_ADMIN_BUSY`、既存の経路の 500） | 低（500 が続くなら高） | 3つ目の手順書 | 使う（ロール・グループは RB-28） |
| RB-25 | 5つの操作の確定の後の監査の書き込みの失敗 | 低（続く・多いなら高） | 3つ目の手順書 | 直しを合わせて読む（前の手順書の 1節。業務データは RB-39） |
| RB-26 | Intent 260930-user-admin の版を戻すときの注意 | 高 | 3つ目の手順書 | 使わない → RB-47 |
| RB-27 | 行の「操作」のメニューのはみ出し | 低 | 3つ目の手順書 | **使わない**（Intent 261003-user-admin-followup の FR2 で直った） |
| RB-28 | ロール・グループの取り合い（409 `ROLE_BUSY`・`GROUP_BUSY`） | 低 | 前の手順書 | 使う |
| RB-29 | 権限の受け渡しがうまくいかない | 低 | 前の手順書 | 使う |
| RB-30 | ヒープが尽きて JVM が止まり、起き直さない | 高 | 前の手順書 | 直しを合わせて読む（業務データの大きな本文。1節） |
| RB-31 | ロール・権限の設定を YAML で保存する・設定だけを戻す | 低 | 前の手順書 | 使う |
| RB-32 | 業務のメニュー・テーブルが急に見えなくなった | 低 | 前の手順書 | 直しを合わせて読む（業務データの画面の 403・404 は RB-40・RB-42） |
| RB-33 | 置き場の 403 が多い（`ms-forbidden`） | 低 | 前の手順書 | 直しを合わせて読む（業務データの 403 は RB-42） |
| RB-34 | 画面が固まる・表示が崩れる・押しても反応しない | 低（業務が止まるなら高） | 前の手順書 | 使う（業務データの画面も同じ） |
| RB-35 | Intent 261004-role-menu の版を戻す・DSL の版 | 高 | 前の手順書 | **使わない** → RB-47（戻し先が古い。1節） |
| RB-36 | 業務データの対象DB に届かない（503 `BUSINESS_TARGET_DB_UNAVAILABLE`、`ms-biz-target-db`） | 低（業務データが使えない間が長いなら高） | この手順書 | 使う |
| RB-37 | 保存されたか分からない（503 `BUSINESS_TARGET_DB_TIMEOUT`、監査の `UNKNOWN`） | 低（続くなら RB-36 と同じく高） | この手順書 | 使う |
| RB-38 | 書き込みの設定が無い・誤り（409 `BUSINESS_WRITE_NOT_CONFIGURED`、資格情報の誤り） | 低 | この手順書 | 使う |
| RB-39 | 対象DB の確定の後に監査の書き込みが失敗した | 低（続く・多いなら高） | この手順書 | 使う |
| RB-40 | 業務データの「保存できない」「開けない」の code の見分け（409・404・400・413） | 低 | この手順書 | 使う |
| RB-41 | DSL と対象DB の食い違い（409 `BUSINESS_TARGET_DB_MISMATCH`・404 `BUSINESS_DSL_NOT_APPLIED`） | 低 | この手順書 | 使う |
| RB-42 | 業務データの 403 が多い（`ms-forbidden`） | 低 | この手順書 | 使う |
| RB-43 | 手元の監視（lgtm）が止まった | 低 | この手順書 | 使う |
| RB-44 | 書き込みだけを止める（読み取りは動かしたまま） | 低（止めるのは依頼者の判断） | この手順書 | 使う（`rollback-runbook.md` 3.1 の複写を戻す形の置き換え） |
| RB-45 | 書き込みの資格情報の入れ替え（漏えいの疑い） | 高 | この手順書 | 使う |
| RB-46 | 見本の PG が壊れた・作り直す | 低（業務データが使えない間が長いなら高） | この手順書 | 使う |
| RB-47 | この Intent の版を戻す | 高 | この手順書（中身は `rollback-runbook.md`） | 使う |

| 節 | 中身 | 置き場 |
|---|---|---|
| 警報ごとの初動の表 | 17 件の警報ごとの最初に見るもの | 元の手順書の 13節、2つ目の手順書の 21節、3つ目の手順書の 2節、前の手順書の 2節、この手順書の 2節 |
| 定期の確認 | アプリを使う前の確かめ | 元の手順書の 14節、2つ目の手順書の 22節、3つ目の手順書の 3節、前の手順書の 3節、この手順書の 3節 |
| 確かめられなかったこと | 推測と未確認の一覧 | 元の手順書の 15節、2つ目の手順書の 23節、3つ目の手順書の 4節、前の手順書の 4節、この手順書の 4節 |

## 1. 前の手順書から直す箇所

| 箇所 | 前の記述 | 今 |
|---|---|---|
| 前の手順書の RB-35、元の手順書の RB-10、2つ目の手順書の RB-20、3つ目の手順書の RB-26（版を戻す） | 戻し先 `mastersmith:pre-role-menu`（`role-menu-before-s8fix`）、手順は Intent 261004-role-menu の `rollback-runbook.md` | 今の戻し先は `mastersmith:pre-business-data-views`（`sha256:f103aee1702a…`、前の Intent 261015-deps-ui-carryover で配備した `6dd9bb0` の版）です。手順は `aidlc/spaces/default/intents/261016-business-data-views/operation/deployment-pipeline/rollback-runbook.md`（RB-47）。戻し先の版も DSL の版 2・ロール・メニューを持つため、前の手順書の RB-35 の「版 2 のプレビューを破棄する」「版 1 の DSL を生成し直す」は当たりません |
| 前の手順書の incident-plan の「今ある複写」 | `…-202610140820-pre-role-menu.tgz`・`…-202610142218-after-role-menu-final.tgz` | 配備の前: `~/.mastersmith-backup/mastersmith-data-202610210848-pre-business-data-views.tgz`。配備の後: `~/.mastersmith-backup/mastersmith-data-202610210915-after-business-data-views.tgz`（どちらも権限 600。`operation/deployment-execution/deployment-log.md` 2節、`operation/observability-setup/measurement-notes.md` 10節） |
| 元の手順書の RB-04（見本の対象DB が止まった） | DSL の照合・既定の DSL の生成の時代の手順。症状は 503 `TARGET_DB_UNAVAILABLE` と照合の警告だけ。警報は無い | 業務データの API（`/api/tables/…`）も止まります（読み取り・書き込みとも 503 `BUSINESS_TARGET_DB_UNAVAILABLE`。定義の `POST /api/tables/definition` だけは対象DB に接続せず動く）。新しい警報 `ms-biz-target-db`（業務データの API の 503 が 5 分に1件以上）が鳴ります。業務データの見分けと手順は RB-36。手順 4 の「見本の DB を作り直す」は、Intent 260923-dsl-schema-loader の `rollback-runbook.md` 6節ではなく RB-46（書き込みのアカウントと `TEMPORARY` の取り上げも入り直すことを確かめる） |
| 元の手順書の RB-05・RB-12（対象DB の設定の誤り・起動のときのログ） | 読み取りの7項目だけ | 書き込みの2項目（`MASTERSMITH_TARGET_DB_WRITE_USERNAME`・`MASTERSMITH_TARGET_DB_WRITE_PASSWORD`）が加わりました。片方だけのときは起動のときに WARN「対象DB の書き込みの設定に欠け・不正があるため、業務データの書き込みを使いません。項目を直して再起動してください」（キー `items`）が1件出ます（`targetdb/config/TargetDataSourceConfig.java`）。RB-38 |
| 元の手順書の RB-11 の取り替えの表 | 対象DB の読み取りのパスワードだけ | 書き込みのパスワード（`mastersmith_writer`）の行を足します。手順は RB-45（見本の PG の `ALTER ROLE` で替えるため、ボリュームを作り直さなくても効く）。読み取りのパスワードの行は変えません（Q4: A で読み取りには手順を足さない） |
| 元の手順書の RB-08、3つ目の手順書の RB-25（確定の後の監査） | 内部DB の2本目の接続 | 業務データの書き込みは、対象DB の書き込みの接続を返した**後に**内部DB へ監査を書きます（README の「業務データの書き込みの API」）。監査が失敗しても対象DB の行は変わったままです。業務データの ERROR は `detail`・主キーの値・例外の原因を載せません。RB-39 |
| 元の手順書の RB-03（内部DB のファイルの伸び） | DSL の投入と監査 | 業務データの書き込みは、成功も対象DB に関わる失敗も監査の行を1件ずつ足します（1件 約 100 B。`measurement-notes.md` 10節）。補助権限を持つ人が同じ失敗をくり返すと行が増えます（README の既知の制約） |
| 元の手順書の RB-02、前の手順書の RB-30（メモリ） | 権限の YAML・DSL の投入 | 業務データの作成・更新の本文は 4 MiB まで受け付けます。上限まで詰めた本文の同時の書き込みは、アプリのヒープと対象DB（見本の PG の上限 512m）のメモリを使いえます。見本の PG が `OOMKilled` になったときはアプリは動き続け、業務データの API が 503 を返します（RB-36。`rollback-runbook.md` 7節） |
| 前の手順書の 2節の `ms-forbidden` の行・RB-33 | 置き場の 403 と管理の API の入口の 403 | 業務データの権限の 403（権限の無いテーブル・カラム・操作）も数に入り、監査に残りません（`alarms.md` 4節）。RB-42 |
| 前の手順書の冒頭の「警報の確かめの区別」の表 | `ms-5xx-ratio` は Unverified、`ms-pool-pending`・`ms-audit-fail` は performance-validation で確かめる | 冒頭の表に置き換えます。`ms-5xx-ratio`・`ms-pool-pending` は使い捨ての環境で鳴った（条件つき）、`ms-audit-fail`・`ms-audit-slow` は鳴ることを確かめていない（Unverified） |
| `rollback-runbook.md` 3.1（書き込みだけを止める） | 配備の前に取った `.env` の複写 `$HOME/.mastersmith-env-before-writer` を `.env` に戻す | **その複写はもう無い**（Q5: A で、この段の 2026-10-25 に消した。中身は表示していない）。今は `.env` の書き込みの2行を、値を表示せずに消す形です（RB-44）。`rollback-runbook.md` は承認済みのため書き換えず、差をここに書きます |
| Deployment Execution の記録（`deployment-log.md` 3節） | 複写は「書き込みだけを止める手の元」。`rollback-runbook.md` 3.1 は「消すのは Deployment Execution の終わり」 | Deployment Execution の終わりには消しておらず、Incident Response の段（この段）で消しました。承認済みの記録は書き換えず、差をここに書きます |

## RB-36 業務データの対象DB に届かない（503 `BUSINESS_TARGET_DB_UNAVAILABLE`、`ms-biz-target-db`）

**当たる場面**: 業務データの一覧・詳細・作成・更新・削除の画面が「対象DB に接続できません」になる。手元の監視を有効にしているときは `ms-biz-target-db` が鳴る（`alarms.md` 1節・2節）。

**見分け方**

| 手がかり | 内容 |
|---|---|
| 症状 | 業務データの API（`/api/tables/rows/…`）が 503。読み取りと書き込みの両方が止まる。定義（`POST /api/tables/definition`）は対象DB に接続しないため動く（README の「業務データの読み取りの API」）。ログイン・管理の画面・DSL の適用済みの表示は動く |
| code の意味（`businessdata/domain/BusinessDataProblemTypes.java`） | `BUSINESS_TARGET_DB_UNAVAILABLE`（503）: 対象DB に接続できない・読み取りの接続の設定が無い。借りる待ちは 3 秒（MySQL で接続の確かめが走るときは 6 秒）。書き込みでは「保存されていない」（借りられない・書き込みの文の段の時間切れ・断絶・競合（デッドロックなど）。`businessdata/repository/RowSqlFailureClassifier.java`）<br>`BUSINESS_TARGET_DB_TIMEOUT`（503）: 読み取りでは問い合わせ（件数を含む）の時間切れ（既定 5 秒）。書き込みでは確定の段の失敗だけで「保存されたか分からない」（RB-37） |
| アプリのログ（`log-queries.md` 1節・2節） | 読み取り: WARN「対象DB の業務データを読めませんでした」（キー `purpose`・`reason`・`sqlState`・`vendorCode`・`exceptionClass`）<br>書き込み: WARN「対象DB の業務データを書き込めませんでした」（上に `stage` を加えたもの）<br>どちらも 503 の件数と同じ数が出る（実測）。ERROR は出ない。ライブラリの WARN（`com.zaxxer.hikari.pool.PoolBase` の「Pool is empty…」など）も出る |
| 実測（使い捨ての環境） | 対象DB を `docker stop` しても `docker pause` しても、503 はすべて `UNAVAILABLE`（`sqlState` は 08001 と 08003、`exceptionClass` は `java.sql.SQLTransientConnectionException`）。`TIMEOUT` は 0 件（`measurement-notes.md` 6節） |
| 警報 | `ms-biz-target-db`（止めてから 2分48秒で firing、戻してから 5分41秒で inactive）。条件つきで `ms-pool-pending`・`ms-5xx-ratio` も鳴る。`ms-error-logs` は鳴らない（WARN のため） |
| 監査 | 書き込みの `UNAVAILABLE` は、結果 `FAILURE`・失敗の理由 `BUSINESS_TARGET_DB_UNAVAILABLE` で残る。読み取りは監査に残らない |

**重さ**: 低（ログイン・監査・管理の画面は対象DB に触れない）。業務データが使えない間が業務で困る長さになるなら高。

**手順**

1. 理由と段の件数を見る（値は出ない）。
   ```bash
   docker compose logs --no-log-prefix --since 1h app | grep '対象DB の業務データを読めませんでした' | grep -o '"reason":"[A-Z_]*"' | sort | uniq -c
   docker compose logs --no-log-prefix --since 1h app | grep '対象DB の業務データを書き込めませんでした' | grep -oE '"(stage|reason)":"[A-Z_]*"' | paste - - | sort | uniq -c
   docker compose logs --no-log-prefix --since 1h app | grep '対象DB の業務データを' | grep -o '"sqlState":"[0-9A-Z]*"' | sort | uniq -c
   ```
   - 手元の監視が動いていれば `log-queries.md` の L1〜L3。気になる行の `trace_id` から Tempo で要求を追う。
   - 書き込みに `"stage":"COMMIT"` と `"reason":"TIMEOUT"` の組があれば、RB-37 も行う。
   - `sqlState` が `28` で始まる（認証の失敗）なら、読み取りは元の手順書の RB-05、書き込みだけなら RB-38。
2. 見本の PG の状態を見る（`ms-biz-target-db` の注記のとおり）。
   ```bash
   docker compose --profile targetdb-postgres ps -a targetdb-postgres
   docker inspect mastersmith-targetdb-postgres-1 --format '{{.State.Status}} OOMKilled={{.State.OOMKilled}} exit={{.State.ExitCode}}'
   docker compose --profile targetdb-postgres exec -T targetdb-postgres pg_isready -U target_admin -d business   # 動いているときだけ
   docker compose --profile targetdb-postgres logs --tail 50 targetdb-postgres | grep -cE 'ERROR|FATAL|PANIC'
   ```
   - 見本の PG のログは、行の数とレベルだけを見る（ERROR の行に業務データの値が入りうるため、本文を写さない）。
3. 止まっていれば【承認】起動する。動いているのに答えない・`OOMKilled` なら【承認】起動し直す（見本の PG が止まっている間は業務データが 503 のまま）。
   ```bash
   docker compose --profile targetdb-postgres start targetdb-postgres     # 止まっているとき
   docker compose --profile targetdb-postgres restart targetdb-postgres   # 答えない・OOMKilled のとき
   ```
   - `OOMKilled` のときは、直前に大きな本文（上限まで詰めた作成・更新）を重ねて送っていないかを【依頼者】に確かめる（README の既知の制約）。
4. ボリュームが壊れて起動しない・初期化が止まったときは RB-46。
5. 読み取りの `TIMEOUT` が続くとき（対象DB は答えるが遅い）: 【依頼者】検索の条件を絞る（code の案内のとおり）。見本の PG の負荷（`docker stats --no-stream mastersmith-targetdb-postgres-1`）を見る。
6. 配備した環境でドライバーのログを有効にしない（接続情報がログに残る。README の「対象DB」）。

**確かめ方**: `pg_isready` が `accepting connections`。アプリは作り直さなくても、新しい接続で戻る（使い捨ての環境で、PG を戻した後に要求が成功した。`alarms.md` 2節）。【依頼者】業務データの一覧が開ける（読み取りは監査に残らない）。WARN「対象DB の業務データを読めませんでした」「…書き込めませんでした」が新しく出ない。手元の監視を動かしていれば、`ms-biz-target-db` は最後の 503 から 5 分を過ぎると inactive に戻る。

## RB-37 保存されたか分からない（503 `BUSINESS_TARGET_DB_TIMEOUT`、監査の `UNKNOWN`、Q2: A）

**当たる場面**: 作成・更新・削除のあと、画面が「保存されたか分からない」を出した。書き込みの確定の段で時間切れ・断絶が起きると、対象DB で確定したかが分かりません（NFR 設計の試しで、確定の途中で止めると応答は失敗なのに行は保存されていた。`construction/u2-businessdata-write/nfr-design/reliability-design.md` 4節）。

**見分け方**（ソースで確かめた）

| 手がかり | 内容 |
|---|---|
| 応答 | 503 `BUSINESS_TARGET_DB_TIMEOUT`。書き込みでこの code になるのは**確定の段（`COMMIT`）の失敗だけ**（`RowSqlFailureClassifier.java`）。書き込みの文の段の時間切れは、巻き戻すため `BUSINESS_TARGET_DB_UNAVAILABLE`（保存されていない）になる |
| ログ | WARN「対象DB の業務データを書き込めませんでした」の `"stage":"COMMIT"`・`"reason":"TIMEOUT"` |
| 監査 | `BUSINESS_ROW_CREATED`・`BUSINESS_ROW_UPDATED`・`BUSINESS_ROW_DELETED` の結果 `UNKNOWN`、失敗の理由 `BUSINESS_TARGET_DB_TIMEOUT`（`businessdata/domain/BusinessDataAuditResult.java`） |
| 読み取りの `TIMEOUT` | 読み取りの問い合わせの時間切れも同じ code だが、保存の話ではない（RB-36 の手順 5） |
| 実測 | 使い捨ての環境では起こせなかった（`docker stop`・`docker pause` のどちらでも `UNAVAILABLE`）。問い合わせの最中・確定の最中に止める必要がある見込み。結合テスト（ServerSocket の方式）を正とする |

**重さ**: 低。続くなら RB-36 と同じ扱い（対象DB の状態を見る）。

**手順**

1. 【依頼者】画面の一覧・詳細で、その行が保存されたかを見る。
   - 作成: 一覧に、作ろうとした行があるか。
   - 更新: 詳細の値が、変えた値になっているか。
   - 削除: 一覧から消えているか。
   - **保存されていなければ、もう一度作成・更新・削除する**（監査に残る）。保存されていれば何もしない。AI は行の値を見ません。
   - 作成をやり直す前に必ず一覧で確かめる（同じ行を二重に作らないため。一意の制約があれば 409 `BUSINESS_DUPLICATE_VALUE` で止まるが、制約の無いテーブルでは二重になる）。
2. 何件起きたかをログの件数で見る（値は出ない。アプリは止めない）。
   ```bash
   docker compose logs --no-log-prefix --since 24h app | grep '対象DB の業務データを書き込めませんでした' | grep '"stage":"COMMIT"' | grep -c '"reason":"TIMEOUT"'
   ```
3. ログが作り直しで消えた後や、期間をまたいで数えたいときは、監査の `UNKNOWN` を数える。**【承認】アプリを約 10 秒止める**ため、依頼者の承認を得てから行う。
   1. 複写の取り方は README の「監査ログの確かめ方」と Deployment Execution の `deployment-log.md` 4節の形（止める → 複写 → 起動）。名前は `mastersmith-data-<日時>-incident.tgz`、権限 600。
      ```bash
      docker compose stop app
      docker run --rm -v mastersmith_mastersmith-data:/data -v "$HOME/.mastersmith-backup":/backup eclipse-temurin:25.0.4.1_1-jre-noble@sha256:d9a39a23634650173f1e2bbc176227af9728587ecf0f4b62d53e9355cd7a19ab \
        tar czf /backup/mastersmith-data-$(date +%Y%m%d%H%M)-incident.tgz -C /data .
      docker compose --profile targetdb-postgres start app
      chmod 600 ~/.mastersmith-backup/mastersmith-data-*-incident.tgz
      docker compose --profile targetdb-postgres ps app   # healthy を待つ
      ```
   2. 複写をホームの下の権限 700 の作業用のディレクトリに展開し、H2 2.4.240 の JDBC（`ACCESS_MODE_DATA=r`）で開いて、種類ごとの件数と時刻の幅だけを出す（Deployment Execution と同じリポジトリの外の道具 `~/.mastersmith-backup/count-db2.sh` の形）。
      ```sql
      SELECT event_type, COUNT(*), MIN(occurred_at), MAX(occurred_at)
        FROM audit_events
       WHERE event_type LIKE 'BUSINESS_ROW_%' AND result = 'UNKNOWN'
       GROUP BY event_type ORDER BY event_type;
      ```
      - `detail`（主キー）・`target_schema`・`target_table`・利用者 ID の列は選ばない。
      - 流す前に列の名前を `INFORMATION_SCHEMA.COLUMNS` で確かめ、誤りの文を画面に出さない形（誤りの文を捨てて件数だけを出す）にする（`deployment-log.md` 4節の学び）。
   3. 数え終えたら作業用のディレクトリを消す。複写は `~/.mastersmith-backup/` に残す（バックアップを兼ねる）。
4. 【依頼者】数と時刻を、自分がした操作と突き合わせる（どの行かは画面で確かめる。手順 1）。
5. 時間切れが続くときは、RB-36 の手順 2 で対象DB の状態を見る。
6. **自動でやり直す仕組みは置きません**（Q2: A）。

**確かめ方**: 【依頼者】画面で行が意図どおり（保存した・やり直した）。新しい `"stage":"COMMIT"`・`"reason":"TIMEOUT"` の WARN が出ない。

## RB-38 書き込みの設定が無い・誤り（409 `BUSINESS_WRITE_NOT_CONFIGURED`、資格情報の誤り）

**見分け方**（`targetdb/config/TargetDataSourceConfig.java`、`businessdata/service/BusinessDataWriteService.java`、README で確かめた）

| 手がかり | 意味 |
|---|---|
| 作成・更新・削除が 409 `BUSINESS_WRITE_NOT_CONFIGURED`、読み取りは動く | 書き込みの2項目が無い（RB-44 で止めた状態も同じ）。権限の確かめを通った要求だけに返す。監査に残らない |
| 上に加えて、起動のときに WARN「対象DB の書き込みの設定に欠け・不正があるため、業務データの書き込みを使いません。項目を直して再起動してください」が1件（キー `items` に項目の名前だけ） | 2項目の片方だけがある。読み取りと起動は続く |
| 作成・更新・削除が 503 `BUSINESS_TARGET_DB_UNAVAILABLE`、読み取りは動く、見本の PG は動いている | 書き込みの資格情報の誤り（パスワードの食い違い・アカウントが無い）の見込み（**推測**。誤りの資格情報で実際に起こしてはいない）。WARN「対象DB の業務データを書き込めませんでした」が `"stage":"BORROW"`・`"reason":"UNAVAILABLE"` で出て、`sqlState` が `28` で始まる見込み（推測） |
| 読み取りも 503 | RB-36 か、元の手順書の RB-05（読み取りの設定） |

**重さ**: 低（読み取りは動く）。

**手順**

1. 項目の有無だけを数える（値は出さない）。
   ```bash
   grep -cE '^MASTERSMITH_TARGET_DB_WRITE_USERNAME=.+' .env              # 1 なら値あり
   grep -cE '^MASTERSMITH_TARGET_DB_WRITE_PASSWORD=.+' .env              # 1 なら値あり
   grep -cE '^MASTERSMITH_SAMPLE_TARGETDB_WRITER_PASSWORD=.+' .env.targetdb   # 1 なら値あり
   docker compose logs --no-log-prefix app | grep -c '対象DB の書き込みの設定に欠け・不正があるため'
   docker compose logs --no-log-prefix --since 1h app | grep '対象DB の業務データを書き込めませんでした' | grep -oE '"(stage|reason|sqlState)":"[0-9A-Z_]*"' | paste - - - | sort | uniq -c
   ```
2. 書き込みのアカウントが見本の PG にあるかを件数で見る。
   ```bash
   docker compose --profile targetdb-postgres exec -T targetdb-postgres psql -U target_admin -d business -tAc \
     "SELECT COUNT(*) FROM pg_roles WHERE rolname = 'mastersmith_writer'"   # 1 であること
   ```
3. 直し方:
   - **わざと止めている**（RB-44 の後）なら何もしない。
   - **項目が無い・片方だけ**: 【依頼者】`.env` の2行をそろえる（README の「手元で試す対象DB」の手順 3）。値は `.env.targetdb` の `MASTERSMITH_SAMPLE_TARGETDB_WRITER_PASSWORD` と同じ。そろえた後、【承認】`docker compose --profile targetdb-postgres up -d --force-recreate --no-build app`。
   - **パスワードの食い違い・アカウントが無い**: 【承認】README の「既にある見本の DB に書き込みのアカウントを足す（U2）」の台本 `03-writer-account.sh` を流し直す（何度流しても同じ結果。パスワードと付与を当て直す）。台本は見本の PG のコンテナの環境の値を使うため、`.env.targetdb` を替えた後なら先に `docker compose --profile targetdb-postgres up -d --force-recreate --no-deps targetdb-postgres` で渡す。値を替えたいときは RB-45。
4. 配備した環境でドライバーのログを有効にしない。

**確かめ方**: 起動のログの WARN「対象DB の書き込みの設定に欠け・不正があるため」が 0 件。【依頼者】画面で1件の書き込みが通る（監査に `BUSINESS_ROW_*` の `SUCCESS` が残る。送る前に伝える）。

## RB-39 対象DB の確定の後に監査の書き込みが失敗した

**当たる場面**: 業務データの書き込みは成功（または対象DB に関わる失敗）として応答したのに、監査の行が欠ける。対象DB の確定の後に内部DB へ監査を書くため、監査が失敗しても対象DB の行は変わったままです（承認済みの既知の制約。README の「業務データの書き込みの API」）。

**見分け方**（`audit/service/AuditEventListener.java` で確かめた）

| 手がかり | 内容 |
|---|---|
| ログ | ERROR「監査イベントの記録に失敗しました」が1行。キーは `auditEventType`（`BUSINESS_ROW_CREATED`・`BUSINESS_ROW_UPDATED`・`BUSINESS_ROW_DELETED`）・`result`・`failureReason`・`occurredAt`・`actorUserId`・`workRoleId`・`keyColumnCount`・`keyTruncated`・`exceptionType`（クラスの名前）ほか |
| 載らないもの | `detail`（主キーの値）・例外の原因（スタックトレース）。ほかの種類の失敗の ERROR とは違い、`setCause` を付けない。そのため、3つ目の手順書の RB-25 より手がかりが少なく、どの行かはログから分からない |
| 載るが出さないもの | `targetSchema`・`targetTable`（名前）・`sourceIp`・`userAgent`・`requestPath`・`actorUserId`。件数を数えるときは `grep -o` で種類のキーだけを取り出し、行の全体を表示しない |
| 警報 | `ms-audit-fail`（1 時間に1件でも。ログの文で数え、出来事の種類で絞らない）・`ms-error-logs`。**どちらも業務データの失敗で鳴ることは確かめていない（Unverified）**。`ms-audit-slow` は遅れの行で、業務データの書き込みの間に1件数えた実測がある（しきい値に届かない） |

**重さ**: 低。続く・多いなら高（元の手順書の RB-08 と同じ）。

**手順**: 元の手順書の RB-08 の手順 1〜5 のとおり。加えて:

1. 種類と件数を見る。
   ```bash
   docker compose logs --no-log-prefix --since 24h app | grep '監査イベントの記録に失敗しました' | grep -oE '"auditEventType":"BUSINESS_ROW_[A-Z]*"' | sort | uniq -c
   docker compose logs --no-log-prefix --since 24h app | grep '監査イベントの記録に失敗しました' | grep -o '"exceptionType":"[A-Za-z0-9_.$]*"' | sort | uniq -c
   ```
2. 【依頼者】その時刻に行った業務データの操作を思い出し、画面で行の今の状態を確かめる（対象DB の行は変わっている）。
3. 欠けた記録を補う手順は、今も決まっていません（元の手順書の 15節）。時刻・種類・件数を障害の記録（リポジトリの外、`~/.mastersmith-incidents/`）に書きます。報告には件数と種類と時刻だけを書きます。

**確かめ方**: 新しい ERROR「監査イベントの記録に失敗しました」が出ない。

## RB-40 業務データの「保存できない」「開けない」の code の見分け（409・404・400・413）

**当たる場面**: 【依頼者】から「保存できない」「開けない」と知らされた。画面の文言と、ブラウザの開発者ツールの Network の状態コード・`code` だけを聞く（値は聞かない・写さない。前の手順書の RB-34 の手順 3）。

**表**（code と状態コードは `businessdata/domain/BusinessDataProblemTypes.java`、監査の有無は `businessdata/domain/BusinessDataAuditReason.java`・`BusinessDataWriteService.java` で確かめた。**読み取りは、どの結果も監査に残らない**）

| 状態 | code | 意味 | 書き込みの監査 | 手当て |
|---|---|---|---|---|
| 404 | `BUSINESS_DSL_NOT_APPLIED` | DSL が適用されていない | 残らない | RB-41 |
| 404 | `BUSINESS_TABLE_NOT_FOUND` | 適用中の DSL に無いテーブル（古いブックマーク・DSL の適用し直し） | 残らない | 【依頼者】メニューから選び直す。RB-41 |
| 404 | `BUSINESS_ROW_NOT_FOUND` | 主キーで指した行が無い（ほかの人が削除した） | 残る（更新・削除。`FAILURE`） | 【依頼者】一覧を読み直す |
| 409 | `BUSINESS_ROW_OPERATION_NOT_ALLOWED` | 主キーが無い・主キーを見られない・ビューで、行を1件に決める操作を行えない | その操作の補助権限を持つ人の書き込みだけ残る | 障害ではない（決まりどおり。`project.md` の Mandated） |
| 409 | `BUSINESS_ROW_NOT_UNIQUE` | 主キーで2行以上が当たる | 残る（書き込み） | DSL の主キーの設定を見直す（【依頼者】DSL の画面） |
| 409 | `BUSINESS_TARGET_DB_MISMATCH` | DSL と対象DB が合わない | 残る（書き込み） | RB-41 |
| 409 | `BUSINESS_DEFINITION_CHANGED` | 画面を開いた後に定義（DSL か作業ロール）が変わった | 残らない | 【依頼者】画面を読み直してから保存し直す |
| 409 | `BUSINESS_WRITE_NOT_CONFIGURED` | 書き込みの接続の設定が無い | 残らない | RB-38（RB-44 で止めているなら決まりどおり） |
| 409 | `BUSINESS_DUPLICATE_VALUE` | 一意の項目にほかの行と同じ値（`fieldErrors` でカラムを示す） | 残る（`FAILURE`） | 【依頼者】値を変える |
| 409 | `BUSINESS_REFERENCE_NOT_FOUND` | 参照先に無い値（`fieldErrors`） | 残る（`FAILURE`） | 【依頼者】参照先にある値を入れる |
| 409 | `BUSINESS_SAVE_REJECTED` | 対象DB の決まり（参照されている行の削除・必須・長さ・桁・CHECK・日時の範囲・文字集合・更新できないビューなど）に合わない | 残る（`FAILURE`） | 【依頼者】入力を見直す |
| 403 | `ACCESS_DENIED` | テーブル・カラム・操作の権限が無い | 残らない | RB-42、前の手順書の RB-32 |
| 400 | `VALIDATION_FAILED` | 入力の誤り（`fieldErrors`。入力できないカラムを足した `NOT_ALLOWED`、長さ・桁・必須・選択肢など） | 残らない | 【依頼者】入力を直す |
| 413 | `PAYLOAD_TOO_LARGE` | 作成・更新の本文が 4 MiB（4,194,304 バイト）、定義・一覧・詳細・削除の本文が 64 KB を超えた（`businessdata/web/BusinessDataWebConfig.java`）。400 より先に出る | 残らない | 【依頼者】1回の保存の量を減らす（値1つは 10,000 字まで）。画面の送り方は字を `\u` でエスケープしないが、字を `\uXXXX` でエスケープする送り手では、4 バイトの字が 12 バイトになり同じ値でも超えうる（`perf/README.md`） |
| 503 | `BUSINESS_TARGET_DB_UNAVAILABLE` | 対象DB に届かない（書き込みは保存されていない） | 残る（`FAILURE`） | RB-36 |
| 503 | `BUSINESS_TARGET_DB_TIMEOUT` | 読み取り: 問い合わせの時間切れ。書き込み: 保存されたか分からない | 書き込みは残る（`UNKNOWN`） | RB-36・RB-37 |
| 500 | （共通の 500） | 想定外の失敗。書き込みでは WARN「対象DB の業務データを書き込めませんでした」が `"reason":"UNEXPECTED"` で出てから 500 になる | 残らない（ソースの読み取り。`finish` の前に投げる） | 元の手順書の RB-01・RB-12。時刻と件数を記録し、再現するかを見る（前の手順書の RB-34 の手順 5） |

- 書き込みの監査に「残る」ものは、操作した人・作業ロール・スキーマ・テーブル・主キー（`detail`）とともに残ります（README）。数えるときは RB-37 の手順 3 の形で `event_type`・`result`・`failure_reason` だけを選びます（`log-queries.md` 4節）。
- 件数を見るとき（値は出ない）:
  ```bash
  docker compose logs --no-log-prefix --since 1h app | grep '要求をエラー応答に変換しました' | grep -oE '"code":"(BUSINESS_[A-Z_]*|ACCESS_DENIED|VALIDATION_FAILED|PAYLOAD_TOO_LARGE)"' | sort | uniq -c
  ```
  `ACCESS_DENIED`・`VALIDATION_FAILED`・`PAYLOAD_TOO_LARGE` はほかの機能の分も数に入ります。業務データの道の内訳は、手元の監視のパネル 40・43（`dashboards.md` 1節）で見ます。

**重さ**: 低。

## RB-41 DSL と対象DB の食い違い（409 `BUSINESS_TARGET_DB_MISMATCH`・404 `BUSINESS_DSL_NOT_APPLIED`）

**当たる場面と見分け**（`BusinessDataProblemTypes.java`、`RowSqlFailureClassifier.java`）

| 応答 | 意味 | 見込みの原因 |
|---|---|---|
| 409 `BUSINESS_TARGET_DB_MISMATCH` | 適用中の DSL にあるテーブル・カラムが対象DB に無い（PG の SQLState 42P01・42703 など）、または保存済みの値を DSL の型で読めない | DSL を適用した後に、対象DB の表・カラムを変えた・消した。見本の PG を作り直したときに台本と DSL の形が違った（RB-46） |
| 404 `BUSINESS_DSL_NOT_APPLIED` | DSL が適用されていない | 起動のときに適用中の DSL を読めなかった（元の手順書の RB-06）。戻した版で DSL を触った（RB-47） |
| 404 `BUSINESS_TABLE_NOT_FOUND` | テーブルが DSL に無い | DSL を適用し直した（前の手順書の RB-32） |

- WARN のキー `reason` は、読み取りでは `MISMATCH`、書き込みでも `MISMATCH`（`RowQueryFailureReason`・`RowWriteFailureReason`）。
- `BUSINESS_TARGET_DB_MISMATCH` の書き込みは監査に残ります（`FAILURE`）。

**重さ**: 低。

**手順**

1. 件数を見る。
   ```bash
   docker compose logs --no-log-prefix --since 1h app | grep '対象DB の業務データを' | grep -c '"reason":"MISMATCH"'
   docker compose logs --no-log-prefix --since 1h app | grep '要求をエラー応答に変換しました' | grep -oE '"code":"BUSINESS_(TARGET_DB_MISMATCH|DSL_NOT_APPLIED|TABLE_NOT_FOUND)"' | sort | uniq -c
   ```
2. 【依頼者】DSL の画面の照合（元の手順書の RB-05・RB-06 の照合の警告）で、どのテーブル・カラムが食い違うかを画面で見る（名前は AI の記録に写さない）。
3. 【依頼者】直し方を選ぶ: 対象DB を DSL に合わせて戻すか、既定の DSL を生成し直して適用するか（`DSL_GENERATED`・`DSL_APPLIED` が監査に残る）。DSL を適用し直すと、ロールの権限の対象の名前が変わることがある（前の手順書の RB-32）。
4. `BUSINESS_DSL_NOT_APPLIED` のときは、元の手順書の RB-06。

**確かめ方**: 【依頼者】一覧・詳細が開ける。`"reason":"MISMATCH"` の WARN が新しく出ない。

## RB-42 業務データの 403 が多い（`ms-forbidden`）

**当たる場面**: `ms-forbidden`（直近 1 時間の 403 が 20 件を超える）が鳴った。業務データの権限の 403（権限の無いテーブル・カラム・操作の拒否）も数に入ります（使い捨ての環境で、業務データの 403 534 件で鳴った。`alarms.md` 3節・4節）。

**重さ**: 低（知らない試しが多いなら元の手順書の RB-09・RB-11 を考える）。

**手順**（`alarms.md` 4節の見分け方）

1. 業務データの 403 は**監査に残らない**（使い捨ての環境で、403 に当たる種類の行は 0 件）。利用者はログとトレースで追う。
2. 手元の監視が動いていれば、パネル 40（API ごとの要求の数）で `status="403"` の系列が `/api/tables/…` に出ているかを見る。`log-queries.md` の L4（code ごとの件数。この Intent では流していない形、Unverified）を流し、`ACCESS_DENIED` の行の `trace_id` から Tempo で要求を追う。
3. 動いていなければ件数だけを見る（業務データとほかの機能の分は分けられない）。
   ```bash
   docker compose logs --no-log-prefix --since 1h app | grep '要求をエラー応答に変換しました' | grep -c '"code":"ACCESS_DENIED"'
   docker compose logs --no-log-prefix --since 1h app | grep -c '権限が足りないため要求を拒否しました'   # 管理の API の入口の 403（前の手順書の RB-33）
   ```
4. 業務データの 403 が多いとき: 業務の結果（権限の無いテーブルを開いた、作業ロールを替えた後に開いたままの画面で操作した、古いブックマーク）で、障害とは限りません。【依頼者】使っている人に、どの画面のどの操作かを聞き、権限の設定を見直すかを決める（前の手順書の RB-32）。画面は 403 の後に送り直しません（U3-NFR8.7）。
5. 警報の題「管理画面への拒否の増加」と注記（「監査ログで利用者と接続元IPを確かめる」）は、業務データの 403 には当たりません。しきい値 20 は変えません（Q4: A）。題・注記・しきい値の見直しは、配備先が決まり利用者が増えたときの申し送りです（`alarms.md` 4節）。

## RB-43 手元の監視（lgtm）が止まった

**当たる場面**: 手元の監視（`docker compose --profile monitoring`）を動かし、アプリの外部エクスポートを有効にしている間に、lgtm が止まった・メモリの上限（1536m）で止められた。**配備したアプリは外部エクスポートが無効のため、今は起きません**（`alarms.md` 5節）。

**見分け方**（`measurement-notes.md` 7節の実測）

| 手がかり | 内容 |
|---|---|
| アプリのログ | `io.opentelemetry.exporter.internal.http.HttpExporter` の ERROR（送信の失敗）、`BatchSpanProcessor`・`OtlpMeterRegistry` の WARN。使い捨ての環境では ERROR 95 行・WARN 59 行と 17 行 |
| 警報 | 送信の失敗の ERROR が `ms-error-logs`（5 分に 5 件を超える）の数に入りうる。lgtm が止まると、警報そのものが評価されない |
| 原因の例 | 待ちの無い大量の要求のトレース（毎秒 1,500〜2,200 件）で、lgtm の中の受け手（otelcol）と Tempo が止められた |

**重さ**: 低（アプリの障害ではない）。

**手順**

1. アプリではなく受け手の側を先に見る。
   ```bash
   docker compose --profile monitoring ps -a lgtm
   docker inspect mastersmith-lgtm-1 --format '{{.State.Status}} OOMKilled={{.State.OOMKilled}} exit={{.State.ExitCode}}'
   docker compose logs --no-log-prefix --since 30m app | grep -c 'io.opentelemetry.exporter'
   ```
   - コンテナの名前は compose の既定（`mastersmith-lgtm-1`）の見込み（**推測**。`docker compose ps` の名前で読み替える）。
2. 【承認】lgtm を起動し直す（`docker compose --profile monitoring up -d lgtm`）。アプリより先に lgtm を網につなぐ（前の Intent の学び。先にアプリを起動すると送信の失敗の ERROR が出る）。
3. 見終わったら、README の「手元の監視（Grafana）」のとおり lgtm を止め、【依頼者】`.env` の外部エクスポートの2行をコメントに戻してアプリを作り直す。
4. 監視の式を確かめる要求は、負荷の試験の場面ではなく、待ちを入れた少ない要求で送る（`project.md` の学び）。

**確かめ方**: 送信の失敗の ERROR が新しく出ない。Grafana の画面が開き、警報の一覧が評価されている。

## RB-44 書き込みだけを止める（読み取りは動かしたまま、Q5: A）

**当たる場面**: 書き込みだけに問題がある（意図しない書き込み・書き込みの不具合の疑い・書き込みの資格情報の漏えいの疑いで急ぐとき）。イメージを戻す前の手として使います（`rollback-runbook.md` 1節）。止めると、作成・更新・削除は 409 `BUSINESS_WRITE_NOT_CONFIGURED`、読み取りは動き続けます。

**重さ**: 低（止めるかどうかは依頼者の判断）。

**`rollback-runbook.md` 3.1 との差**: 3.1 は配備の前に取った `.env` の複写（`$HOME/.mastersmith-env-before-writer`）を戻す形でした。その複写は 2026-10-25 にこの段で消したため（Q5: A）、`.env` の書き込みの2行を値を表示せずに消す形に替えます。結果（2行が無い `.env`）は同じです。

**手順**（【承認】。`.env` を開かず、値を表示しない）

1. 前提を件数で確かめ、`.env` をホームの下の権限 700 の場所へ中身を表示せずに複写する（戻すときに使う）。
   ```bash
   I="$HOME/.mastersmith-incidents"; mkdir -p "$I" && chmod 700 "$I"
   T=$(date +%Y%m%d%H%M)
   grep -c '^MASTERSMITH_TARGET_DB_WRITE_' .env                       # 2 であること
   (umask 077 && cp .env "$I/env-$T-before-write-stop")
   ls -l .env "$I/env-$T-before-write-stop"                           # 権限だけを見る（中身は開かない）
   ```
2. 書き込みの2行を消して確かめる。
   ```bash
   sed -i '' '/^MASTERSMITH_TARGET_DB_WRITE_/d' .env
   grep -c '^MASTERSMITH_TARGET_DB_WRITE_' .env                       # 0 であること
   diff <(cut -d= -f1 "$I/env-$T-before-write-stop") <(cut -d= -f1 .env)   # 項目の名前だけの差。消えた2行の名前だけが出ること
   ls -l .env                                                         # 権限が手順 1 と同じこと
   ```
   - `diff` は `=` より前（項目の名前）だけを比べるため、値は出ません。
3. アプリを作り直す（設定は起動のときにだけ読まれる）。
   ```bash
   docker compose --profile targetdb-postgres up -d --force-recreate --no-build app
   docker compose --profile targetdb-postgres ps app                  # healthy を待つ
   docker compose logs --no-log-prefix app | grep -c '対象DB の書き込みの設定に欠け・不正があるため'   # 0 であること（2行とも無いときは WARN は出ない）
   ```
   - 版を戻している間（RB-47）は、`MASTERSMITH_IMAGE_TAG=pre-business-data-views` を付ける。
4. 【依頼者】画面で、作成・更新・削除が「書き込みが設定されていません」になり、一覧・詳細は開けることを確かめる（409 は監査に残らない）。

**書き込みを戻すとき**（【承認】）

```bash
(umask 077 && cp "$I/env-$T-before-write-stop" .env)               # 中身は表示しない
grep -c '^MASTERSMITH_TARGET_DB_WRITE_' .env                       # 2 であること
docker compose --profile targetdb-postgres up -d --force-recreate --no-build app
```

- 戻した後（または戻さないと決めた後）、複写 `$I/env-$T-before-write-stop` を消す（`rm -f`。秘密を含むため残す期間を短くする）。
- 資格情報の漏えいの疑いで止めたときは、戻す前に RB-45 で値を入れ替える（複写には古い値が入っているため、複写を戻さず RB-45 の形で `.env` に足し直す。RB-45 の手順 4 の注記）。

## RB-45 書き込みの資格情報の入れ替え（漏えいの疑い、Q4: A）

**当たる場面**: 書き込みのアカウント `mastersmith_writer` のパスワードが漏れた疑いがある。このアカウントは、DSL の対象のスキーマ（見本の付与はスキーマの単位）の行を作成・更新・削除できます（README の「残る広さ」）。

**重さ**: 高。**急ぐときは、先に RB-44 で書き込みだけを止めます**（読み取りは動く）。

**手順**（配備のときの書き足しの形 `deployment-log.md` 3節・`cd-config.md` 3.2 をもとに、値を表示しない形にした。どのコマンドも値を画面に出さない）

1. 範囲を決める（元の手順書の RB-11 の手順 1）。何が・どこに・いつから。リポジトリに入ったなら、公開のため見られた前提で入れ替える。
2. 前提を件数で確かめ、`.env`・`.env.targetdb` をホームの下の権限 700 の場所へ中身を表示せずに複写する。
   ```bash
   I="$HOME/.mastersmith-incidents"; mkdir -p "$I" && chmod 700 "$I"
   T=$(date +%Y%m%d%H%M)
   grep -cE '^MASTERSMITH_TARGET_DB_WRITE_PASSWORD=.+' .env                    # 1（RB-44 で止めた後なら 0）
   grep -cE '^MASTERSMITH_SAMPLE_TARGETDB_WRITER_PASSWORD=.+' .env.targetdb    # 1
   (umask 077 && cp .env "$I/env-$T-before-writer-rotate" && cp .env.targetdb "$I/env-targetdb-$T-before-writer-rotate")
   ```
3. 【承認】乱数のパスワードを表示せずに作り、見本の PG の `ALTER ROLE` を psql の標準入力で渡す（コマンド行に値を入れない。`printf` はシェルの組み込みのため、ほかのプロセスの一覧に値は出ない）。
   ```bash
   W=$(openssl rand -hex 24)
   printf "ALTER ROLE mastersmith_writer WITH PASSWORD '%s';\n" "$W" \
     | docker compose --profile targetdb-postgres exec -T targetdb-postgres psql -U target_admin -d business -v ON_ERROR_STOP=1 -q
   echo "exit=$?"                                                     # 0 であること
   ```
   - 値は 16 進の 48 字のため、引用符を含みません。
   - 文が失敗すると、PostgreSQL のサーバーのログに文が残りうる（`log_min_error_statement` の既定は ERROR）。失敗したときは見本の PG のログを表示せず、件数だけを見て、作り直し（RB-46）を考える。
4. `.env` の `MASTERSMITH_TARGET_DB_WRITE_PASSWORD` と `.env.targetdb` の `MASTERSMITH_SAMPLE_TARGETDB_WRITER_PASSWORD` を同じ値に置き換える（値を表示しない。値は awk に環境変数で渡し、コマンド行に入れない）。
   ```bash
   (umask 077 && NEWPW="$W" awk '/^MASTERSMITH_TARGET_DB_WRITE_PASSWORD=/{print "MASTERSMITH_TARGET_DB_WRITE_PASSWORD=" ENVIRON["NEWPW"]; next} {print}' .env > .env.rotate.tmp) && mv .env.rotate.tmp .env
   (umask 077 && NEWPW="$W" awk '/^MASTERSMITH_SAMPLE_TARGETDB_WRITER_PASSWORD=/{print "MASTERSMITH_SAMPLE_TARGETDB_WRITER_PASSWORD=" ENVIRON["NEWPW"]; next} {print}' .env.targetdb > .env.targetdb.rotate.tmp) && mv .env.targetdb.rotate.tmp .env.targetdb
   unset W
   ```
   - 一時のファイル（`.env.rotate.tmp`・`.env.targetdb.rotate.tmp`）は `.gitignore` の `.env.*` に当たり、`mv` で消えます。
   - **RB-44 で止めた後**（`.env` に書き込みの2行が無い）は、置き換えではなく、配備のときの形で足す: `(umask 077 && printf 'MASTERSMITH_TARGET_DB_WRITE_USERNAME=mastersmith_writer\nMASTERSMITH_TARGET_DB_WRITE_PASSWORD=%s\n' "$W" >> .env)`（`unset W` の前に行う）。止めたときの複写は古い値のため戻さない。
5. 置き換えを件数と項目の名前で確かめる（値は出ない）。
   ```bash
   grep -cE '^MASTERSMITH_TARGET_DB_WRITE_PASSWORD=.+' .env                    # 1
   grep -cE '^MASTERSMITH_SAMPLE_TARGETDB_WRITER_PASSWORD=.+' .env.targetdb    # 1
   diff <(cut -d= -f1 "$I/env-$T-before-writer-rotate") <(cut -d= -f1 .env)     # 出力なし（項目の名前は同じ）
   cmp -s "$I/env-$T-before-writer-rotate" .env; echo "changed=$?"             # 1（中身が変わった）
   ls -l .env .env.targetdb                                                     # 権限 -rw-------
   ```
6. 【承認】アプリを作り直す（新しい書き込みのパスワードを読む）。
   ```bash
   docker compose --profile targetdb-postgres up -d --force-recreate --no-build app
   docker compose --profile targetdb-postgres ps app                            # healthy を待つ
   docker compose logs --no-log-prefix app | grep -c '対象DB の書き込みの設定に欠け・不正があるため'   # 0
   ```
7. 漏れた値でつながっている接続が残っていないかを件数で見て、残っていれば【承認】切る（アプリを作り直した直後は、アプリの書き込みのプールは最初の書き込みまで接続を持たない見込み。**推測**）。
   ```bash
   docker compose --profile targetdb-postgres exec -T targetdb-postgres psql -U target_admin -d business -tAc \
     "SELECT COUNT(*) FROM pg_stat_activity WHERE usename = 'mastersmith_writer'"
   docker compose --profile targetdb-postgres exec -T targetdb-postgres psql -U target_admin -d business -tAc \
     "SELECT COUNT(pg_terminate_backend(pid)) FROM pg_stat_activity WHERE usename = 'mastersmith_writer'"   # 【承認】0 でないときだけ
   ```
8. 見本の PG のコンテナの環境には、作り直すまで古い値が残ります（`.env.targetdb` は作り直しのときにだけ渡る）。そのまま `03-writer-account.sh` を流すと古いパスワードに戻るため、次に台本を流す前か、次の作り直しのときに `docker compose --profile targetdb-postgres up -d --force-recreate --no-deps targetdb-postgres` で渡す（見本の PG が数秒止まり、その間の業務データは 503）。
9. 確かめが済んだら、複写 `$I/env-$T-before-writer-rotate`・`$I/env-targetdb-$T-before-writer-rotate` を消す（古い値を含むため）。

**確かめ方**

- 項目の値が空でない行の件数が `.env` 1・`.env.targetdb` 1（手順 5）。
- 【依頼者】画面で業務データの書き込みを1件行い、通る（**監査に `BUSINESS_ROW_*` の `SUCCESS` が1行残る。送る前に伝える**）。
- WARN「対象DB の業務データを書き込めませんでした」の `"stage":"BORROW"` が新しく出ない。
- 漏れた疑いの期間の書き込みは、【依頼者】画面と、監査の `BUSINESS_ROW_*` の件数と時刻（RB-37 の手順 3 の形。`event_type`・`result`・`occurred_at` だけ）で見る。アプリを通らない書き込み（漏れた資格情報で対象DB に直接つないだもの）は監査に残らない。

## RB-46 見本の PG が壊れた・作り直す（Q3: A）

**当たる場面**: 見本の PG（`mastersmith-targetdb-postgres-1`）のボリュームが壊れて起動しない、初期化が「…が空です」で止まった、見本の行を初めの状態に戻したい。

**前提**（`incident-plan.md` 4節）: 見本の PG は RPO の外です。見本の行は初期化の台本（`docker/targetdb/postgres/01-sample-schema.sql`）が作る架空の値で、作り直すと台本の状態に戻ります。**アプリの書き込みの API で書いた行は失われます**。内部DB と監査の行は失われません（README の「手元で試す対象DB」の手順 5）。

**重さ**: 低（業務データが使えない間が長いなら高）。

**手順**（README の手順 5 と、初期化の台本 `01-sample-schema.sql`・`02-reader-account.sh`・`03-writer-account.sh` で確かめた）

1. 作り直す前に、【依頼者】見本の行で残したいものが無いかを確かめる（見本は架空の値で、残す前提ではない）。見本の PG のログは件数だけを見る（RB-36 の手順 2）。
2. 初期化に要る3つのパスワードの項目に値があることを件数で確かめる（空だと初期化が止まる）。
   ```bash
   grep -cE '^MASTERSMITH_SAMPLE_TARGETDB_(ADMIN|READER|WRITER)_PASSWORD=.+' .env.targetdb   # 3 であること
   ```
3. 【承認】ボリュームごと消して起動し直す（消してよいのは見本の PG のボリューム `mastersmith_mastersmith-targetdb-postgres` だけ。内部DB の `mastersmith_mastersmith-data` と監視の `mastersmith_mastersmith-monitoring` は消さない）。
   ```bash
   docker compose --profile targetdb-postgres rm -sf targetdb-postgres
   docker volume rm mastersmith_mastersmith-targetdb-postgres
   docker compose --profile targetdb-postgres up -d targetdb-postgres
   docker compose --profile targetdb-postgres exec -T targetdb-postgres pg_isready -U target_admin -d business   # accepting connections を待つ
   docker compose --profile targetdb-postgres logs targetdb-postgres | grep -c 'が空です'                      # 0 であること
   ```
   - E2E の上乗せ（`docker/e2e/compose.targetdb-port.yaml`）は付けない（番号を開けない）。
4. 書き込みのアカウントと `TEMPORARY` の取り上げが入り直したことを、`operation/deployment-pipeline/cd-config.md` 3.2 の順 7 と同じ問い合わせ（`pg_roles` の `mastersmith_writer` の数、行の操作の3つの付与、スキーマとデータベースの `CREATE`、PUBLIC の `TEMP`）で確かめる。期待は `1|t|t|t|f|f|f`。問い合わせの文はその節のものをそのまま使う。あわせて読み取りのアカウントも数える。
   ```bash
   docker compose --profile targetdb-postgres exec -T targetdb-postgres psql -U target_admin -d business -tAc \
     "SELECT COUNT(*) FROM pg_roles WHERE rolname IN ('mastersmith_reader','mastersmith_writer')"   # 2 であること
   docker compose --profile targetdb-postgres ps targetdb-postgres                                   # Up、PORTS は 5432/tcp だけ
   ```
5. アプリは作り直さなくてよい見込み（新しい接続で戻る。RB-36 の確かめ方。**推測**: パスワードは `.env.targetdb` の値で作り直され、`.env` と同じ値のまま）。503 が続くときは【承認】`docker compose --profile targetdb-postgres restart app`。
6. 適用中の DSL は、台本が同じ形の見本のスキーマを作るため合う見込み。`BUSINESS_TARGET_DB_MISMATCH` が出たら RB-41。

**確かめ方**: 手順 4 の期待どおり。【依頼者】業務データの一覧が開ける（読み取りは監査に残らない）。書き込みを確かめるなら1件（監査に残る。送る前に伝える）。

## RB-47 この Intent の版を戻す

中身は `operation/deployment-pipeline/rollback-runbook.md` です（戻しの練習 D1〜D5 で確かめ済み。`deployment-log.md` 6節）。ここには要点だけを書きます。

- 戻すきっかけと判断は `rollback-runbook.md` 1節。**書き込みだけに問題があるときは、先に RB-44**（`rollback-runbook.md` 3.1 の複写を戻す形は使えない。1節の直し）。アプリが `ExitOnOutOfMemoryError` で止まっただけのときは、すぐには戻さない（前の手順書の RB-30、`rollback-runbook.md` 7節）。
- 戻し先: `mastersmith:pre-business-data-views`（`sha256:f103aee1702a…`）。戻す前に `docker image inspect` で ID を確かめる。
- 戻している間のコマンドには、毎回 `MASTERSMITH_IMAGE_TAG=pre-business-data-views` と `--no-build` を付ける。
- 戻した後の起動で、Flyway の WARN「Schema "PUBLIC" has a version (13) that is newer than the latest available migration (12) !」が1件出る（想定どおり。練習の D1）。
- 戻している間は、業務データの画面と API がありません（業務のメニューの項目は前の版の準備中の画面になる）。`.env` の書き込みの2行・`mastersmith_writer`・`TEMPORARY` の取り上げ・V13 の3列は残り、前の版は使いません（練習の D2・D4）。
- **見本の PG に新しい版で書いた行は、どちらの戻しでも戻りません**（見本の対象DB は戻しの対象外）。
- 配備の前の複写（`~/.mastersmith-backup/mastersmith-data-202610210848-pre-business-data-views.tgz`）を戻す第二の手は、移行の失敗・データの破損のときだけです。配備の後のロール・割り当て・作業ロール・利用者の変更・DSL の操作と、配備の後の監査の行（`BUSINESS_ROW_*` を含む）が失われます（`rollback-runbook.md` 4節）。
- 上げ直すときは、移行は当たりません（V13 のまま）。`.env` の書き込みの2行が残っていれば、そのまま書き込みも使えます（`rollback-runbook.md` 2.3）。

## 2. 警報ごとの初動の表（この Intent の差）

前の4つの手順書の表に、次を足します。警報は 16 件 → **17 件**（`ms-biz-target-db` を足した。既存の 16 件は変えていない。`alarms.md` 1節）。**配備したアプリは外部エクスポートが無効のため、今は配備したアプリでは鳴りません。**

| 警報名 | 変わったこと | 鳴ることの確かめ | 参照する手順 |
|---|---|---|---|
| 業務データの対象DB に届かない（`ms-biz-target-db`、中、新しい） | 業務データの API の 503 が 5 分に1件以上（`for: 0s`）。状態コードで数えるため `UNAVAILABLE` と `TIMEOUT` の両方を拾う | 確かめた（止めてから 2分48秒で firing、戻してから 5分41秒で inactive） | RB-36（`TIMEOUT` なら RB-37 も） |
| 管理画面への拒否の増加（`ms-forbidden`） | 業務データの権限の 403 も数える。監査に残らない。題と注記は業務データには当たらない | 確かめた（534 件で 1分42秒） | RB-42 |
| 5xx の割合の増加（`ms-5xx-ratio`） | 業務データの 503 も数えるが、ほかの要求で割合が薄まる。対象DB を戻した後に firing になることがある（5 分の窓に 503 が残るため） | 鳴った（条件つき。止めて 7分09秒） | RB-36 |
| コネクションプールの待ち（`ms-pool-pending`） | 対象DB に届かない間の借りる待ち（3 秒）でも待ちが 1 になる。60 秒ごとの1点に当たったときだけ鳴る | 鳴った（条件つき。止めて 2分09秒、pause で 2分31秒） | RB-36 |
| 監査の書き込みの失敗（`ms-audit-fail`） | 業務データの出来事（`BUSINESS_ROW_*`）も数える（ログの文で数える） | Unverified（式の読み取りだけ） | RB-39 |
| 監査の書き込みの遅れ（`ms-audit-slow`） | 業務データの出来事も数える | Unverified（実測で1件数えた。しきい値 3 件に届かない） | 元の手順書の RB-08 |
| ERROR のログの増加（`ms-error-logs`） | 業務データの 503 は WARN のため数に入らない（実測で ERROR 0）。監査の書き込みの失敗の ERROR と、手元の監視が止まったときの送信の失敗の ERROR は入りうる | この Intent では鳴ることを確かめていない | RB-39・RB-43 |
| （警報なし） | 業務の拒否の 409・404・400・413、保存されたか分からない 1件ずつ、書き込みの設定が無い | — | RB-37・RB-38・RB-40・RB-41 |

## 3. 定期の確認（この Intent の差）

前の4つの手順書の表に、次を足します。

| 確かめ | コマンド・操作 | 通る条件 |
|---|---|---|
| 見本の PG が動いている（業務データを使う前） | `docker compose --profile targetdb-postgres ps -a targetdb-postgres` | `Up`。止まっていれば RB-36 |
| 前の日の対象DB の失敗 | `docker compose logs --no-log-prefix --since 24h app` を `grep -c '対象DB の業務データを読めませんでした'`・`grep -c '対象DB の業務データを書き込めませんでした'` で数える | どちらも 0 か少数（多ければ RB-36） |
| 保存されたか分からない書き込み（前の日） | RB-37 の手順 2 の件数 | 0（1 以上なら【依頼者】画面で確かめる） |
| 業務データの監査の失敗（前の日） | RB-39 の手順 1 の1つ目のコマンド（種類ごとの件数） | 0（1 以上なら RB-39） |
| 書き込みの設定（アプリを作り直した後） | RB-38 の手順 1 のコマンド（WARN「対象DB の書き込みの設定に欠け・不正があるため」の件数と、項目の件数） | WARN 0 件。書き込みを使うなら2項目とも 1、止めているなら 0 |
| 書き込みを止めた・入れ替えたときの `.env` の複写 | `ls -l ~/.mastersmith-incidents/`（名前と権限だけ） | RB-44・RB-45 の複写が、要らなくなった後に残っていない |
| 内部DB の複写の権限 | `ls -l ~/.mastersmith-backup/`（名前と権限だけ） | 置き場 700・複写 600（Environment Provisioning の学び） |

## 4. 確かめられなかったこと

| 項目 | 内容 |
|---|---|
| RB-37 | 503 `BUSINESS_TARGET_DB_TIMEOUT`（確定の段の失敗）を、使い捨ての環境で起こせなかった（`docker stop`・`docker pause` のどちらでも `UNAVAILABLE`。`measurement-notes.md` 6節）。結合テスト（ServerSocket の方式）と NFR 設計の試し（W7）を正とする。`UNKNOWN` を数える問い合わせは流していない |
| RB-38 | 書き込みの資格情報の誤りが 503 `BUSINESS_TARGET_DB_UNAVAILABLE`・`"stage":"BORROW"`・`sqlState` `28…` になることは推測（借りる段のどんな失敗も `UNAVAILABLE` にすることはソースで確かめた。`sqlState` の値は確かめていない） |
| RB-39 | `ms-audit-fail`・`ms-audit-slow` が業務データの失敗で鳴ることは Unverified（持ち主 feedback-optimization）。業務データの監査の書き込みの失敗は起こしていない |
| RB-40 | 500（`"reason":"UNEXPECTED"`）が監査に残らないことはソースの読み取りだけ |
| RB-42 | `log-queries.md` の L4（code ごとの件数）はこの Intent で流していない（Unverified） |
| RB-43 | lgtm のコンテナの名前（`mastersmith-lgtm-1`）は compose の既定からの推測 |
| RB-44 | `sed -i ''` で2行を消す形は、この段で流していない（配備したアプリと `.env` に触れないため）。権限が保たれることは手順の中の `ls -l` で確かめる |
| RB-45 | 手順の全体（`ALTER ROLE` を標準入力で渡す・awk での置き換え・接続の切り）は流していない。値を表示しない書き足しの形は Deployment Execution で確かめ済み（`deployment-log.md` 3節）。作り直した直後の書き込みのプールが接続を持たないことは推測 |
| RB-46 | 作り直しの手順は README と台本の読み取りで書いた。この段では流していない。作り直した後にアプリを作り直さずに戻ることは、使い捨ての環境で PG を止めて戻した結果からの推測（ボリュームを作り直した場合は確かめていない） |
| 警報 | 配備したアプリでは、外部エクスポートが無効のため、どの警報も鳴らない（`alarms.md` の Assumptions） |

## Sources

- `aidlc/spaces/default/intents/261016-business-data-views/operation/incident-response/incident-response-questions.md`（1節 F1〜F11 と古くなった箇所、2節の決まっていること、Q1〜Q5、確認済みの要約）
- 前の4つの手順書: `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/incident-response/runbooks.md`、`aidlc/spaces/default/intents/260925-user-management/operation/incident-response/runbooks.md`、`aidlc/spaces/default/intents/260930-user-admin/operation/incident-response/runbooks.md`、`aidlc/spaces/default/intents/261004-role-menu/operation/incident-response/runbooks.md`
- この Intent の Observability Setup: `operation/observability-setup/alarms.md`・`log-queries.md`・`dashboards.md`・`measurement-notes.md`
- この Intent の配備: `operation/deployment-pipeline/rollback-runbook.md`（1〜4節・7節）・`cd-config.md`（3.2・4節）・`deployment-strategy.md`、`operation/deployment-execution/deployment-log.md`（2〜4節・6節）
- NFR の設計と基盤の設計: `construction/u1-businessdata-read/nfr-design/reliability-design.md`・`security-design.md`、`construction/u2-businessdata-write/nfr-design/reliability-design.md`（2節・4節・5節）・`security-design.md`（3節・8節）、`construction/u1-businessdata-read/infrastructure-design/infrastructure-specification.md`、`construction/u2-businessdata-write/infrastructure-design/infrastructure-specification.md`（1節・4節）
- ソース（読み取りだけ）: `backend/src/main/java/cherry/mastersmith/businessdata/domain/BusinessDataProblemTypes.java`・`BusinessDataAuditReason.java`・`BusinessDataAuditResult.java`・`RowWriteStage.java`・`RowWriteFailureReason.java`・`RowQueryFailureReason.java`、`businessdata/service/BusinessDataWriteService.java`・`BusinessDataReadService.java`、`businessdata/repository/RowSqlFailureClassifier.java`、`businessdata/web/BusinessDataWebConfig.java`・`BusinessDataPaths.java`、`audit/service/AuditEventListener.java`、`audit/domain/AuditEventType.java`・`AuditEventFactory.java`・`AuditFailureReason.java`、`targetdb/config/TargetDataSourceConfig.java`
- リポジトリ: `README.md`（「対象DB」「業務データの読み取りの API」「業務データの書き込みの API」「手元で試す対象DB」「既にある見本の DB に書き込みのアカウントを足す（U2）」「手元の監視（Grafana）」「監査ログの確かめ方」）、`compose.yaml`（`targetdb-postgres`・`lgtm` の profile とボリューム）、`docker/targetdb/postgres/01-sample-schema.sql`・`02-reader-account.sh`・`03-writer-account.sh`、`docker/monitoring/provisioning/alerting/mastersmith.yaml`（`ms-biz-target-db` の注記）、`perf/README.md`（本文の上限）
- 決まり: `aidlc/spaces/default/memory/team.md`（Deployment）、`project.md`（Deployment・Forbidden・Corrections の学び）
- 読み取りだけで確かめたこと（2026-10-25）: `ls -la ~/.mastersmith-backup/`（名前と権限だけ。複写の2つの名前と権限 600）、`$HOME/.mastersmith-env-before-writer` が無いこと

## Assumptions & Open Questions

- [assumption] 書き込みの資格情報の誤りは 503 `BUSINESS_TARGET_DB_UNAVAILABLE`（借りる段）で、`sqlState` は `28` で始まる（RB-38。誤りの資格情報で起こしていない）。
- [assumption] 見本の PG を作り直した後・書き込みのパスワードを入れ替えた後に、アプリの接続は新しい接続で戻る（RB-45・RB-46）。
- [assumption] lgtm のコンテナの名前は `mastersmith-lgtm-1`（RB-43）。
- 503 `BUSINESS_TARGET_DB_TIMEOUT` の確定の段の失敗は、使い捨ての環境で起こせていません（RB-37）。結合テストを正とします。
- `ms-audit-fail`・`ms-audit-slow` が鳴ることは Unverified のままです（持ち主 feedback-optimization）。
