# 障害ごとの手順書（runbooks）

前の5つの手順書を正とし、この Intent で増えた障害（RB-48 から）と、古くなった箇所の直しだけを書きます（`incident-response-questions.md` 2節の書き方）。この Intent で増えたものは次のとおりです。
- U1 他テーブル参照の読み取り（選択肢・ピッカー・ラベル、定義の取得の参照の情報）
- U2 保存前の参照の確かめ（⑩）
- U3 参照の画面（選択・ピッカー・読み取りだけの参照の項目）
- U4 k6 の結果の伏せ字（`perf/` の下）
- U5 コンテナの版のそろえ（Mailpit・otel-lgtm 0.38.0）と perf の文書

障害のときは、まず下の「索引」で RB の置き場と、今も使えるかを引きます。

- **元の手順書**: `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/incident-response/runbooks.md`（0節・RB-01〜RB-12・13節〜15節）
- **2つ目の手順書**: `aidlc/spaces/default/intents/260925-user-management/operation/incident-response/runbooks.md`（RB-13〜RB-20）
- **3つ目の手順書**: `aidlc/spaces/default/intents/260930-user-admin/operation/incident-response/runbooks.md`（RB-21〜RB-27）
- **4つ目の手順書**: `aidlc/spaces/default/intents/261004-role-menu/operation/incident-response/runbooks.md`（RB-28〜RB-35）
- **5つ目の手順書**: `aidlc/spaces/default/intents/261016-business-data-views/operation/incident-response/runbooks.md`（RB-36〜RB-47。その中で「前の手順書」と書かれているのは4つ目の手順書のこと）
- 間の Intent 261025-deps-perf-carryover・261027-perf-targets-review には、この段がありません。
- どの障害でも最初に行うことは、元の手順書の 0節のままです（状態・ログを見る、作り直す前にログを保存する、記録を始める）。
- 記号: 【承認】は、AI が行うときに依頼者の承認が要る操作です。【依頼者】は依頼者が行う操作です（`.env` の中身を決める編集、秘密情報が要るログイン、画面の操作、ロールと DSL の設定）。印の無いものは読み取りだけです。
- 配備したアプリの compose のコマンドには、今までどおり `--profile targetdb-postgres` を付けます（`operation/deployment-pipeline/rollback-runbook.md`）。
- **値を出さない。** 次のものは、コマンドの出力・記録・報告に出しません。件数と、ロガー・キー・code の名前だけで見ます（`aidlc/spaces/default/memory/project.md` の Corrections・Forbidden）。
  - 業務データの行の値（主キーの値を含む）、**参照の値とラベル**、スキーマ・テーブル・カラムの名前
  - メールアドレス・氏名・利用者 ID・送り元の IP・パスワード・トークン（JWT を含む）、ロール・グループの名前
  - `.env`・`.env.targetdb` の値（数えるのは項目の名前と「値が空でないか」だけ）
- **スタックトレースを手順の外へ写さない。** 対象DB が引けない間、HikariCP 自身の WARN（ロガー `com.zaxxer.hikari.pool.PoolBase`）のスタックトレース（コンテナのログのキー `exception`、Loki の `exception_stacktrace`）に対象DB のホスト名が入ります。依頼者の決定 C で許容し直しませんが、ログを上流への依頼・課題の文面などへ写すときは、スタックトレースを写さず、ロガー・レベル・キーの名前と件数だけを写します（`operation/observability-setup/log-queries.md` 3節）。
- **参照先に値があるか・読む権限があるかの確かめは【依頼者】が画面（作成・更新の画面の説明文、ロールの設定の画面）で行います。** AI は利用者の資格情報で業務データの API を呼ばず、見本の PG に入って業務データ・参照先の行を読みません（`escalation-matrix.md` 4節）。
- 確かめの区別: 次の手順の各文の根拠を、「**実測**」（この Intent の Observability Setup の使い捨ての環境、2026-11-01）、「**結合テストで確かめ済み**」（Build and Test で Met の結合テスト・単体テスト。動くコンテナの上では試していない）、「**ソースの読み取り**」、「**推測**」のどれかで書きます。この段では新しい手順を使い捨ての環境で試していません（`incident-response-questions.md` Q1: A）。
- 警報の確かめの区別（5つ目の手順書の冒頭の表に、この Intent の差を足す）:

  | 区別 | 警報 |
  |---|---|
  | 鳴ることを確かめた（使い捨ての環境） | `ms-biz-target-db`（この Intent: 参照の道・定義の道・保存前の確かめつきの作成の 503 を含む 503 で、止めてから **81 秒**で firing、戻してから 4分50秒で inactive。前の Intent は 2分48秒。合わせて止めてから **1分半〜3 分**の見込み）・`ms-forbidden`・`ms-app-absent`（前の Intent まで） |
  | 鳴った（条件つき） | `ms-5xx-ratio`（この Intent でも、戻した後に 5 分の窓に 503 が残って firing）・`ms-pool-pending`（この Intent では pending までで firing にならなかった） |
  | 鳴ることを確かめていない（Unverified） | `ms-audit-fail`・`ms-audit-slow` ほか（5つ目の手順書の区別のまま） |

  警報の決まりは **17 件のまま**で、lgtm 0.38.0 で 17 件とも読み込まれ評価が `ok` だった（`alarms.md` 2節）。**配備したアプリは外部エクスポートが無効のため、今はどの警報も配備したアプリでは鳴りません。** 配備したアプリで気づくのは画面の表示（「検索できません」・作成と更新の画面が開けない・「選べない値です」）です。

## 索引（RB-01〜RB-56）

「今」の列: **使う**＝そのまま使う。**直しを合わせて読む**＝1節の直しと一緒に読む。**使わない**＝置き換え先を読む。

| RB | 症状 | 重さ | 置き場 | 今 |
|---|---|---|---|---|
| RB-01 | アプリが止まる・healthy にならない | 高 | 元の手順書 | 直しを合わせて読む（JVM が止まって起き直さないときは RB-30） |
| RB-02 | メモリの上限（2g）に近い・OOMKilled | OOMKilled なら高、近いだけなら低 | 元の手順書 | 直しを合わせて読む（終了コード 3 は RB-30。業務データの大きな本文は 1節） |
| RB-03 | 内部DB のファイルの伸び | 低（ディスクが尽きたら高） | 元の手順書 | 直しを合わせて読む（業務データの書き込みの監査の行も伸びる。1節） |
| RB-04 | 見本の対象DB が止まった・応答しない | 低 | 元の手順書 | 直しを合わせて読む（業務データの 503 は RB-36・RB-48、作り直しは RB-46） |
| RB-05 | 対象DB の設定の誤り | 低 | 元の手順書 | 直しを合わせて読む（書き込みの設定は RB-38、参照の2項目は RB-51） |
| RB-06 | 起動のときに適用中の DSL を読めない | 低（依頼者の判断で高） | 元の手順書 | 直しを合わせて読む（業務データの code は RB-41） |
| RB-07 | `DSL_BUSY` が続く | 低 | 元の手順書 | 使う |
| RB-08 | 監査の書き込みの失敗・接続プールの待ち | 低（続く・多い、内部DB の不調なら高） | 元の手順書 | 直しを合わせて読む（業務データの出来事は RB-39） |
| RB-09 | ログインの失敗の多発・ロック・回り込みの試み・送り元の不一致 | 低（知らないログインの成功などは高） | 元の手順書 | 直しを合わせて読む（3つ目の手順書の 1節） |
| RB-10 | 配備の後の不具合（戻す） | 高 | 元の手順書 | 使わない → RB-56 |
| RB-11 | 秘密情報の漏えいの疑い | 高 | 元の手順書 | 直しを合わせて読む（書き込みの資格情報は RB-45、k6 の記録の認証の値は RB-55。1節） |
| RB-12 | そのほかの起動のときのログ | 低 | 元の手順書 | 直しを合わせて読む（書き込みの設定の WARN は RB-38、参照の2項目の WARN は RB-51） |
| RB-13 | メールの受け手が止まっている・招待のメールが届かない | 低 | 2つ目の手順書 | 使う |
| RB-14 | 招待を使えない設定（503 `INVITATION_NOT_CONFIGURED`） | 低 | 2つ目の手順書 | 使う |
| RB-15 | 起動が止まる設定の誤り（招待の長さ・メールのテンプレート） | 高 | 2つ目の手順書 | 使う |
| RB-16 | 招待の定期の削除の失敗 | 低 | 2つ目の手順書 | 使う |
| RB-17 | 総当たりの疑い（登録の完了・今のパスワード） | 低（知らない登録があれば高） | 2つ目の手順書 | 直しを合わせて読む（3つ目の手順書の 1節） |
| RB-18 | 招待のトークン・URL が漏れた疑い | 高 | 2つ目の手順書 | 直しを合わせて読む（3つ目の手順書の 1節） |
| RB-19 | `127.0.0.1` で開いたときの拒否・見た目の設定の不正 | 低（最初の描画が止まるなら高） | 2つ目の手順書 | 使う |
| RB-20 | Intent 260925-user-management の版を戻すときの注意 | 高 | 2つ目の手順書 | 使わない → RB-56 |
| RB-21 | 管理者の誤った操作（印・停止・失敗回数） | 低 | 3つ目の手順書 | 使う |
| RB-22 | 使える管理者がいなくなった | 高 | 3つ目の手順書 | **使わない** → README の「使える管理者がいなくなったとき（初期管理者の救済）」（4つ目の手順書の 1節） |
| RB-23 | 停止中の利用者が使えない・停止を解きたい | 低 | 3つ目の手順書 | 使う |
| RB-24 | 行の排他の時間切れ（409 `USER_ADMIN_BUSY`、既存の経路の 500） | 低（500 が続くなら高） | 3つ目の手順書 | 使う（ロール・グループは RB-28） |
| RB-25 | 5つの操作の確定の後の監査の書き込みの失敗 | 低（続く・多いなら高） | 3つ目の手順書 | 直しを合わせて読む（4つ目の手順書の 1節。業務データは RB-39） |
| RB-26 | Intent 260930-user-admin の版を戻すときの注意 | 高 | 3つ目の手順書 | 使わない → RB-56 |
| RB-27 | 行の「操作」のメニューのはみ出し | 低 | 3つ目の手順書 | **使わない**（Intent 261003-user-admin-followup の FR2 で直った） |
| RB-28 | ロール・グループの取り合い（409 `ROLE_BUSY`・`GROUP_BUSY`） | 低 | 4つ目の手順書 | 使う |
| RB-29 | 権限の受け渡しがうまくいかない | 低 | 4つ目の手順書 | 使う |
| RB-30 | ヒープが尽きて JVM が止まり、起き直さない | 高 | 4つ目の手順書 | 直しを合わせて読む（業務データの大きな本文。1節） |
| RB-31 | ロール・権限の設定を YAML で保存する・設定だけを戻す | 低 | 4つ目の手順書 | 使う |
| RB-32 | 業務のメニュー・テーブルが急に見えなくなった | 低 | 4つ目の手順書 | 直しを合わせて読む（業務データの画面の 403・404 は RB-52・RB-42） |
| RB-33 | 置き場の 403 が多い（`ms-forbidden`） | 低 | 4つ目の手順書 | 直しを合わせて読む（業務データの 403 は RB-42） |
| RB-34 | 画面が固まる・表示が崩れる・押しても反応しない | 低（業務が止まるなら高） | 4つ目の手順書 | 使う（業務データの画面も同じ） |
| RB-35 | Intent 261004-role-menu の版を戻す・DSL の版 | 高 | 4つ目の手順書 | **使わない** → RB-56（戻し先が古い） |
| RB-36 | 業務データの対象DB に届かない（503 `BUSINESS_TARGET_DB_UNAVAILABLE`、`ms-biz-target-db`） | 低（業務データが使えない間が長いなら高） | 5つ目の手順書 | 直しを合わせて読む（参照の読み取りと定義の取得の 503 は RB-48、保存前の確かめの 503 は RB-49。1節） |
| RB-37 | 保存されたか分からない（503 `BUSINESS_TARGET_DB_TIMEOUT`、監査の `UNKNOWN`） | 低（続くなら RB-36 と同じく高） | 5つ目の手順書 | 使う |
| RB-38 | 書き込みの設定が無い・誤り（409 `BUSINESS_WRITE_NOT_CONFIGURED`、資格情報の誤り） | 低 | 5つ目の手順書 | 使う |
| RB-39 | 対象DB の確定の後に監査の書き込みが失敗した | 低（続く・多いなら高） | 5つ目の手順書 | 使う |
| RB-40 | 業務データの「保存できない」「開けない」の code の見分け（409・404・400・413） | 低 | 5つ目の手順書 | **使わない** → RB-52（今の全体の表） |
| RB-41 | DSL と対象DB の食い違い（409 `BUSINESS_TARGET_DB_MISMATCH`・404 `BUSINESS_DSL_NOT_APPLIED`） | 低 | 5つ目の手順書 | 直しを合わせて読む（参照の経路の `MISMATCH`。1節・RB-52） |
| RB-42 | 業務データの 403 が多い（`ms-forbidden`） | 低 | 5つ目の手順書 | 使う |
| RB-43 | 手元の監視（lgtm）が止まった | 低 | 5つ目の手順書 | 直しを合わせて読む（lgtm は 0.38.0。1節） |
| RB-44 | 書き込みだけを止める（読み取りは動かしたまま） | 低（止めるのは依頼者の判断） | 5つ目の手順書 | 使う（`rollback-runbook.md` 3.1 の複写を戻す形の置き換え） |
| RB-45 | 書き込みの資格情報の入れ替え（漏えいの疑い） | 高 | 5つ目の手順書 | 使う |
| RB-46 | 見本の PG が壊れた・作り直す | 低（業務データが使えない間が長いなら高） | 5つ目の手順書 | 使う |
| RB-47 | この Intent の版を戻す | 高 | 5つ目の手順書 | **使わない** → RB-56（戻し先が古い） |
| RB-48 | 参照の読み取りで対象DB に届かない（作成・更新の画面が開けない、ピッカーの「検索できません」、503） | 低（業務データが使えない間が長いなら高） | この手順書 | 使う |
| RB-49 | 保存前の参照の確かめ（⑩）の 503（保存されていない） | 低（続くなら RB-48 と同じく高） | この手順書 | 使う |
| RB-50 | 参照の読み取りの時間切れが続く・全体の上限（`budgetExceeded`） | 低 | この手順書 | 使う |
| RB-51 | 参照の2つの設定の誤り（対象DB 全体が使えない） | 高（業務データがすべて止まる） | この手順書 | 使う |
| RB-52 | 業務データの「保存できない」「開けない」「選べない」の code の見分け（今の全体の表） | 低 | この手順書 | 使う |
| RB-53 | 参照の項目が選べない（`TOO_MANY`・`NO_PERMISSION`） | 低（作成の必須の項目で業務が止まるなら高） | この手順書 | 使う |
| RB-54 | CI の画面のテストが「全件 passed なのに `Errors 1 error`」で落ちる | 低（配備したアプリに影響しない） | この手順書 | 使う |
| RB-55 | k6 の結果（`evidence/`）に認証の値が入った | 高（公開のリポジトリに入るおそれ） | この手順書 | 使う |
| RB-56 | この Intent の版を戻す | 高 | この手順書 | 使う |

| 節 | 中身 | 置き場 |
|---|---|---|
| 警報ごとの初動の表 | 17 件の警報ごとの最初に見るもの | 元の手順書の 13節、2つ目の手順書の 21節、3つ目・4つ目・5つ目の手順書の 2節、この手順書の 2節 |
| 定期の確認 | アプリを使う前の確かめ | 元の手順書の 14節、2つ目の手順書の 22節、3つ目・4つ目・5つ目の手順書の 3節、この手順書の 3節 |
| 確かめられなかったこと | 推測と未確認の一覧 | 元の手順書の 15節、2つ目の手順書の 23節、3つ目・4つ目・5つ目の手順書の 4節、この手順書の 4節 |
| 事後の振り返り | この Intent の CI の失敗（重さ 低） | `incident-plan.md` 6節 |

## 1. 前の手順書から直す箇所

承認済みの手順書・記録は書き換えず、差をここに書きます。

| 箇所 | 前の記述 | 今 |
|---|---|---|
| 5つ目の手順書の RB-47、元の RB-10・2つ目の RB-20・3つ目の RB-26・4つ目の RB-35（版を戻す） | 戻し先 `mastersmith:pre-business-data-views`、手順は Intent 261016-business-data-views の `rollback-runbook.md` | 今の戻し先は `mastersmith:pre-reference-lookup-cleanup`（`sha256:e0ede6ddd5c4…`、前の Intent 261027-perf-targets-review で配備した版）。手順はこの Intent の `operation/deployment-pipeline/rollback-runbook.md`（RB-56） |
| 5つ目の手順書の incident-plan の「今ある複写」 | `…-202610210848-pre-business-data-views.tgz`・`…-202610210915-after-business-data-views.tgz` | 配備の前: `~/.mastersmith-backup/mastersmith-data-202611011400-pre-reference-lookup-cleanup.tgz`。配備の後: `~/.mastersmith-backup/mastersmith-data-202611011409-after-reference-lookup-cleanup.tgz`（どちらも権限 600。`operation/deployment-execution/deployment-log.md` 2節。名前と権限は 2026-11-01 に `ls` で確かめた） |
| 5つ目の手順書の RB-36（対象DB に届かない） | 「定義（`POST /api/tables/definition`）は対象DB に接続しないため動く」。WARN は「対象DB の業務データを読めませんでした」「…書き込めませんでした」の2つ | 定義の取得は、`purpose` が `CREATE`・`UPDATE` で参照の項目を持つ表なら参照先を読むため、**503 になります（作成・更新の画面が開けない）**。`VIEW`（一覧・詳細）は今も 200（**実測**: PG を止めた間、`VIEW` 6 件すべて 200、`CREATE` 6 件すべて 503）。WARN は「対象DB の参照先を読めませんでした」（参照の読み取り）と「保存の前に対象DB の参照先を読めませんでした」（保存前の確かめ）が加わる。参照の経路は RB-48・RB-49 |
| 5つ目の手順書の RB-36 の警報の時間 | 止めてから 2分48秒で firing | この Intent の実測は 81 秒。合わせて **1分半〜3 分**の見込み（差の理由は確かめていない。`alarms.md` 2節） |
| 5つ目の手順書の RB-40（code の見分け） | 409 `BUSINESS_REFERENCE_NOT_FOUND` は「参照先に無い値」（外部キーの違反の変換）だけ | **RB-52 に置き換えます**（今の全体の表。保存前の確かめの出どころ、理由 `NOT_EDITABLE_NOT_NULL`、参照の道の 503・400・403、定義の取得の 503 を足した）。RB-40 は使いません |
| 5つ目の手順書の RB-41（DSL と対象DB の食い違い） | 一覧・詳細・書き込みの経路だけ | 参照先のテーブル・カラムが対象DB と食い違うと、参照の読み取り（定義の取得の `CREATE`・`UPDATE`、ピッカー、ラベル）と保存前の確かめも 409 `BUSINESS_TARGET_DB_MISMATCH` になる（**ソースの読み取り**: U1・U2 の `reliability-design.md` 2節の写し、`ReferenceFailureReason.MISMATCH`）。参照の読み取りの WARN は `reason=MISMATCH`。手順は RB-41 のまま（DSL の画面の照合で、どの参照先が食い違うかを【依頼者】が見る） |
| 5つ目の手順書の RB-43（手元の監視が止まった） | lgtm の版を書いていない | 手元の監視は `grafana/otel-lgtm` 0.38.0（U5 で compose.yaml の版にそろえた）。入れ替え・起動し直しの後は、`docker inspect` でコンテナのイメージの版とダイジェスト（`RepoDigests`）が `compose.yaml` と一致することを確かめ、食い違えば `--force-recreate --no-deps` で作り直す（`team.md` の Deployment） |
| 5つ目の手順書の `log-queries` の問い合わせの書き方 | — | 設計の `{service_name="mastersmith"} \| json \| purpose="REFERENCE"` は使えません（行の本文が JSON でなく `JSONParserErr`）。`\| json` を付けずに `\| purpose="REFERENCE"` で絞ります（**実測**。`log-queries.md` 2節の R1〜R5） |
| 元の手順書の RB-05・RB-12（対象DB の設定と起動のときのログ） | 読み取りの7項目と書き込みの2項目 | 参照の2項目（`MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_REFERENCE`・`MASTERSMITH_TARGET_DB_REFERENCE_READ_TOTAL`）が加わった。既定（5 秒・10 秒）で動くため `.env` には書いていない。誤りは RB-51 |
| Deployment Pipeline の `deployment-strategy.md` の S1（点検の WARN の探し方） | 項目名 `QUERY_TIMEOUT_REFERENCE`・`REFERENCE_READ_TOTAL` で探す | 起動の WARN（`TargetDataSourceConfig`、キー `items`）に出るのは Spring の項目名 `mastersmith.target-db.query-timeout.reference`・`mastersmith.target-db.reference-read-total`（**ソースの読み取り**: `targetdb/config/TargetDbSettings.java`）。前の書き方では WARN が出ていても 0 件になる（`deployment-log.md` 3節の1）。RB-51 は Spring の項目名で探す |
| 元の手順書の RB-11（秘密情報の漏えいの疑い） | 署名鍵・パスワードの入れ替え | k6 の結果（`evidence/`）に認証の値が入ったときは RB-55 |

## RB-48 参照の読み取りで対象DB に届かない

**当たる場面**: 作成・更新の画面が開けない（定義の取得が 503）、ピッカーが「検索できません」、ラベルが値だけになる。一覧・詳細は開ける（定義の `VIEW` は参照先を読まない）。手元の監視を有効にしているときは `ms-biz-target-db` が鳴る。

**見分け方**

| 手がかり | 内容 | 根拠 |
|---|---|---|
| 応答 | 503 `BUSINESS_TARGET_DB_UNAVAILABLE`（借りる待ち 3 秒の時間切れ・接続の失敗・読み取りの設定が無い）、または 503 `BUSINESS_TARGET_DB_TIMEOUT`（文1回の上限 既定 5 秒・全体の上限 既定 10 秒） | ソースの読み取り（`BusinessDataProblemTypes.java`）。`UNAVAILABLE` は実測、`TIMEOUT` は結合テストで確かめ済み |
| 道 | `POST /api/tables/definition`（`purpose` が `CREATE`・`UPDATE`）・`POST /api/tables/reference/rows`（ピッカーの検索・ラベルの1件読み） | 実測（パネル 46・47 の系列） |
| アプリのログ | WARN「対象DB の参照先を読めませんでした」（ロガー `cherry.mastersmith.businessdata.service.ReferenceLookup`）。キーは `purpose=REFERENCE`・`reason`（`TIMEOUT`・`UNAVAILABLE`・`MISMATCH`）・`sqlState`・`vendorCode`・`exceptionClass`・`budgetExceeded`。`stage` は無い。読み取りの設定が無いときは `sqlState`・`exceptionClass` も無い | 実測（`reason=UNAVAILABLE`・`sqlState=08001`・`exceptionClass=java.sql.SQLTransientConnectionException` 24 件）。設定が無いときの形はソースの読み取り（`ReferenceLookup` の `NotConfigured` の枝） |
| ほかのログ | `GlobalExceptionHandler` の変換の WARN（`code`・`status`）、HikariCP の `PoolBase` の WARN（スタックトレースに対象DB のホスト名。手順の外へ写さない）。ERROR は出ない | 実測 |
| 監査 | **残らない**（参照の読み取りは監査の外） | 実測（`BUSINESS_%` の種類に参照の読み取りは無い）、U1 の BR1.7 |
| 警報 | `ms-biz-target-db`（止めてから 81 秒で firing）。`ms-5xx-ratio` は条件つき | 実測 |

**重さ**: 低（ログイン・管理の画面・一覧・詳細は動く）。作成・更新ができないことで業務が止まる長さになるなら高。

**手順**

1. 理由の件数を見る（値は出ない）。
   ```bash
   docker compose logs --no-log-prefix --since 1h app | grep '対象DB の参照先を読めませんでした' | grep -oE '"reason":"[A-Z_]*"' | sort | uniq -c
   docker compose logs --no-log-prefix --since 1h app | grep '対象DB の参照先を読めませんでした' | grep -oE '"budgetExceeded":"?(true|false)"?' | sort | uniq -c
   docker compose logs --no-log-prefix --since 1h app | grep '対象DB の参照先を読めませんでした' | grep -oE '"sqlState":"[0-9A-Z]*"' | sort | uniq -c
   ```
   - 真偽値の出方（引用符の有無）は確かめていないため、両方に当たる形にした（**推測**）。
   - 手元の監視が動いていれば `log-queries.md` 2節の R1（`ReferenceLookup` の `reason`・`budgetExceeded`）と R3（code ごと）。
2. 振り分ける。
   - `reason=UNAVAILABLE` で、一覧・詳細も 503 → 対象DB そのものの問題。5つ目の手順書の RB-36 の手順 2〜4（見本の PG の状態・起動・作り直し）。
   - `reason=UNAVAILABLE` で `sqlState`・`exceptionClass` が無い、かつ一覧・詳細も 503 で、起動の WARN「対象DB の設定に欠け・不正があるため」がある → 設定の誤り。RB-51。
   - `reason=TIMEOUT` → RB-50。
   - `reason=MISMATCH` → 5つ目の手順書の RB-41（参照先の表・カラムの食い違い）。
3. 対象DB が戻った後、アプリは作り直さなくても新しい接続で戻る（**実測**: PG を戻した後に要求が成功し、警報は戻してから 4分50秒で inactive）。

**確かめ方**: 【依頼者】参照の項目を持つ表の作成の画面が開き、選択肢・ピッカーの検索が出る（参照の読み取りは監査に残らない）。WARN「対象DB の参照先を読めませんでした」が新しく出ない。

## RB-49 保存前の参照の確かめ（⑩）の 503

**当たる場面**: 参照の項目を持つ表の作成・更新で、画面が「対象DB に接続できません」などの 503 を出した。

**見分け方**

| 手がかり | 内容 | 根拠 |
|---|---|---|
| 意味 | ⑩ は書き込みの接続を借りる**前**に読み取りの接続で確かめる。そのため ⑩ の 503 は「**保存されていない**」ことが確か。5つ目の手順書の RB-37（確定の段の「保存されたか分からない」）には当たらない | ソースの読み取り（U2 の BR4.1・BR4.5）、結合テストで確かめ済み（T-T1・T-F1: 書き込みを借りた回数 0、行が増えない） |
| 応答 | 503 `BUSINESS_TARGET_DB_UNAVAILABLE`・`BUSINESS_TARGET_DB_TIMEOUT`（全体の上限を含む）、型の食い違いは 409 `BUSINESS_TARGET_DB_MISMATCH` | ソースの読み取り（U2 の `reliability-design.md` 2節） |
| アプリのログ | WARN「保存の前に対象DB の参照先を読めませんでした」（ロガー `cherry.mastersmith.businessdata.service.BusinessDataWriteService`）。キーは `purpose=REFERENCE`・`stage`（`BORROW`・`PREVIOUS_ROW`・`COUNT_STATE`・`CHECK`）・`reason`・`sqlState`・`vendorCode`・`exceptionClass`・`budgetExceeded` | 実測（`stage=BORROW`・`reason=UNAVAILABLE` 14 件） |
| 書き込み（⑪）との見分け | ⑪ の失敗は同じロガーの WARN「対象DB の業務データを書き込めませんでした」で `purpose=WRITE`。⑩ と RB-48 の参照の読み取りは `purpose` の値がどちらも `REFERENCE` のため、ロガー（Loki の `scope_name`）か `stage` の有無で見分ける | 実測（`log-queries.md` 1.1節・2節の R2・R4） |
| 監査 | 残る（`BUSINESS_ROW_CREATED`・`BUSINESS_ROW_UPDATED` の結果 `FAILURE`、失敗の理由は 503 の code）。参照の値は入らない | 実測（`FAILURE`・`BUSINESS_TARGET_DB_UNAVAILABLE` 21 件のうち保存前の確かめの分を含む）、U2 の BR5 |

**重さ**: 低。続くなら RB-48 と同じく対象DB の状態として扱う。

**手順**

1. ⑩ と ⑪ を分けて数える。
   ```bash
   docker compose logs --no-log-prefix --since 1h app | grep '保存の前に対象DB の参照先を読めませんでした' | grep -oE '"(stage|reason)":"[A-Z_]*"' | paste - - | sort | uniq -c
   docker compose logs --no-log-prefix --since 1h app | grep '対象DB の業務データを書き込めませんでした' | grep -oE '"(stage|reason)":"[A-Z_]*"' | paste - - | sort | uniq -c
   ```
   - キーの並び（`stage` の後に `reason`）は WARN の作り方の順（**ソースの読み取り**）。`stage` の無い行（読み取りの設定が無い）は1つ目の `paste` がずれるため、件数が合わないときは `grep -c` だけで数える。
2. 原因の振り分けは RB-48 の手順 2 と同じ（対象DB の状態・設定の誤り・時間切れ・食い違い）。
3. 【依頼者】利用者に、503 は保存されていないので、対象DB が戻った後に保存し直してよいと伝える（自動でやり直す仕組みは無い）。

**確かめ方**: 【依頼者】参照つきの作成が1件保存できる（監査に `BUSINESS_ROW_CREATED`・`SUCCESS` が残る。送る前に伝える）。WARN「保存の前に対象DB の参照先を読めませんでした」が新しく出ない。

## RB-50 参照の読み取りの時間切れが続く・全体の上限（`budgetExceeded`）（Q4: A）

**当たる場面**: RB-48・RB-49 の WARN で `reason=TIMEOUT` が続く。または `budgetExceeded=true`（定義の取得・⑩ の全体の上限 既定 10 秒に達し、残り 1 秒未満で次の文を流さずに止めた）。

**見分け方**

| 手がかり | 内容 | 根拠 |
|---|---|---|
| 文1回の時間切れ | `reason=TIMEOUT`・`budgetExceeded=false`。`sqlState` は PG `57014`、MariaDB `70100`、MySQL はドライバーの時間切れの例外 | 結合テストで確かめ済み（U1 の T-T1）、NFR 設計の試し（U1 の `trial-results.md`） |
| 全体の上限 | `reason=TIMEOUT`・`budgetExceeded=true`・`exceptionClass` 無し | 結合テストで確かめ済み（U1 の T-T2・T-T3、U2 の T-T3）。動くコンテナでは起こしていない |
| 応答 | 503 `BUSINESS_TARGET_DB_TIMEOUT`。⑩ のときは保存されていない（RB-49） | ソースの読み取り |
| 最悪の待ち | 全体の上限 10 秒に対し、切り上げで約 11 秒。前に借りる待ち（上限 3 秒）が乗る | U1 の `reliability-design.md` 1.3 |
| 警報 | `ms-biz-target-db`（状態コード 503 で数えるため `TIMEOUT` も拾う） | 式の読み取り（`alarms.md` 2節）。`TIMEOUT` での firing は確かめていない |

**重さ**: 低。

**手順**（上から順に。上限を延ばすのは最後）

1. 対象DB の状態を見る（5つ目の手順書の RB-36 の手順 2。止まりかけ・`OOMKilled`・負荷）。`docker stats --no-stream mastersmith-targetdb-postgres-1` でメモリと CPU を見る。
2. WARN の `reason`・`budgetExceeded`・`sqlState` を数える（RB-48 の手順 1、RB-49 の手順 1。手元の監視が動いていれば `log-queries.md` 2節の R1・R2）。
   - `budgetExceeded=true` が多い → 参照の項目が多い表、または参照先が重い表の作成・更新の画面で起きている見込み（定義の取得は参照先ごとに文を流す）。
   - `budgetExceeded=false` の `TIMEOUT` が多い → 1つの文が遅い（部分一致の検索・並べ替え）。
3. 【依頼者】参照先の作りを確かめる（AI は参照先の表を読まない）。
   - REFERENCE の参照先の行が多くないか（201 行以上なら選択肢は出ず RB-53。200 行に近いと読む量が増える）。
   - LOOKUP の検索の列に索引があるか（見本の PG では DSL と初期化の台本で決まる）。
   - 直し方の候補: REFERENCE を LOOKUP に替える（RB-53）、対象DB の側に索引を足す（対象DB の持ち主の判断。見本の PG では台本の変更になり、この手順書の外）。
4. **最後の手として**、【承認】上限を延ばす。
   - 依頼者の承認を得てから、`.env` に `MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_REFERENCE`（文1回）か `MASTERSMITH_TARGET_DB_REFERENCE_READ_TOTAL`（全体）の行を足し、`docker compose --profile targetdb-postgres up -d --force-recreate --no-deps app` で作り直す。値は 1 秒以上（1 秒未満は RB-51 の誤りになる）。
   - 延ばすと、読み取りのプール（一覧・詳細と共有）を長く占め、最悪の待ちが長くなる。性能の目標（p95）は緩めない（`project.md` の学び）。
   - 原因（手順 1〜3 で分かったこと）と延ばした値・日時を、障害の記録に書く。
   - 戻すときは足した行を消して作り直す（`rollback-runbook.md` 3節と同じ形。既定の 5 秒・10 秒に戻る）。
   - 足した行は起動の WARN「対象DB の設定に欠け・不正があるため」が 0 件であることで確かめる（RB-51 の手順 1）。

**確かめ方**: 【依頼者】同じ画面が開く。`reason=TIMEOUT` の WARN が新しく出ない。

## RB-51 参照の2つの設定の誤り（対象DB 全体が使えない）

**当たる場面**: 起動の後、業務データの一覧・詳細・作成・更新・削除・参照のすべてが 503 になる。ログイン・管理の画面は動く。`.env` に参照の2項目を書いた（または変えた）後に起きた。

**見分け方**

| 手がかり | 内容 | 根拠 |
|---|---|---|
| 原因 | `MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_REFERENCE`・`MASTERSMITH_TARGET_DB_REFERENCE_READ_TOTAL` が 1 秒未満（0・負・`500ms` など）だと、対象DB の設定の点検が「不正」にし、対象DB 全体を使わない。起動は続く | 結合テスト・単体テストで確かめ済み（U1 の T-T4）。U1 の `reliability-design.md` 1.1・1.2 |
| 起動の WARN | 「対象DB の設定に欠け・不正があるため、対象DB を使いません。項目を直して再起動してください」（ロガー `TargetDataSourceConfig`、キー `items` に Spring の項目名 `mastersmith.target-db.query-timeout.reference`・`mastersmith.target-db.reference-read-total`）。値は出ない | ソースの読み取り（`TargetDataSourceConfig.java`・`TargetDbSettings.java`） |
| 応答 | 業務データの API は 503 `BUSINESS_TARGET_DB_UNAVAILABLE`。参照の WARN は `reason=UNAVAILABLE` で `sqlState`・`exceptionClass` が無い | **推測**（U1 の設計と `ReferenceLookup` の `NotConfigured` の枝の読み取り。動くコンテナでは試していない） |
| `Duration` として読めない値（`abc` など） | 設定の結び付けで**起動が止まる**（ほかの時間の設定と同じ） | U1 の `reliability-design.md` 1.2（既存の扱い） |

**重さ**: 高（業務データがすべて止まる）。直すのは行を消すだけで、失うものは無い。

**手順**

1. 起動の WARN と項目の有無を数える（値は出ない）。
   ```bash
   docker compose logs --no-log-prefix app | grep '対象DB の設定に欠け・不正があるため' | grep -oE 'query-timeout\.reference|reference-read-total' | sort | uniq -c
   grep -c '^MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_REFERENCE=\|^MASTERSMITH_TARGET_DB_REFERENCE_READ_TOTAL=' .env
   ```
   - 探すのは Spring の項目名（`deployment-strategy.md` の S1 の書き方ではない。1節）。
   - WARN の `items` にほかの項目名（読み取りの7項目）があれば、元の手順書の RB-05。
2. 【承認】該当の行を、値を表示せずに行ごと消して作り直す（`rollback-runbook.md` 3節）。
   ```bash
   sed -i '' '/^MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_REFERENCE=/d;/^MASTERSMITH_TARGET_DB_REFERENCE_READ_TOTAL=/d' .env
   docker compose --profile targetdb-postgres up -d --force-recreate --no-deps app
   ```
   - 消した値は既定（5 秒・10 秒）に戻すだけのため、`.env` の複写は取らない（`rollback-runbook.md` 3節）。`.env` の権限が変わらないことを `ls -l .env` で見る。
   - `sed -i ''` の形は、この段では流していない（**推測**。5つ目の手順書の RB-44 と同じ形）。
   - 延ばしたい事情があって書いた行なら、消さずに 1 秒以上の値に【依頼者】が直す（RB-50 の手順 4）。

**確かめ方**: 起動の WARN「対象DB の設定に欠け・不正があるため」が 0 件。【依頼者】業務データの一覧（`customers`）が開く（`deployment-strategy.md` の S2）。

## RB-52 業務データの「保存できない」「開けない」「選べない」の code の見分け（今の全体の表、Q3: A）

5つ目の手順書の RB-40 の表を写し、この Intent の参照の行を足した**今の全体の表**です。RB-40 は使いません。

**当たる場面**: 【依頼者】から「保存できない」「開けない」「選べない」と知らされた。画面の文言と、ブラウザの開発者ツールの Network の状態コード・`code`・項目の誤りの `reason` だけを聞く（値は聞かない・写さない。4つ目の手順書の RB-34 の手順 3）。

**表**（code と状態コードは `businessdata/domain/BusinessDataProblemTypes.java`、項目の誤りの理由は `BusinessDataFieldErrorReason.java`、監査の有無は `BusinessDataAuditReason.java`・`BusinessDataWriteService.java`・U2 の BR5 で確かめた。**読み取り（定義・一覧・詳細・ピッカー・ラベル）は、どの結果も監査に残らない**）

| 状態 | code | 意味 | 出る道（この Intent の差は太字） | 書き込みの監査 | 手当て |
|---|---|---|---|---|---|
| 404 | `BUSINESS_DSL_NOT_APPLIED` | DSL が適用されていない | すべて | 残らない | 5つ目の手順書の RB-41 |
| 404 | `BUSINESS_TABLE_NOT_FOUND` | 適用中の DSL に無いテーブル | すべて | 残らない | 【依頼者】メニューから選び直す。RB-41 |
| 404 | `BUSINESS_ROW_NOT_FOUND` | 主キーで指した行が無い（ほかの人が削除した）。**更新の ⑩ で更新前の行が無いときも** | 詳細・更新・削除 | 残る（更新・削除。`FAILURE`） | 【依頼者】一覧を読み直す |
| 409 | `BUSINESS_ROW_OPERATION_NOT_ALLOWED` | 主キーが無い・主キーを見られない・ビューで、行を1件に決める操作を行えない | 詳細・更新・削除 | その操作の補助権限を持つ人の書き込みだけ残る | 障害ではない（決まりどおり） |
| 409 | `BUSINESS_ROW_NOT_UNIQUE` | 主キーで2行以上が当たる | 詳細・更新・削除 | 残る（書き込み） | DSL の主キーの設定を見直す（【依頼者】DSL の画面） |
| 409 | `BUSINESS_TARGET_DB_MISMATCH` | DSL と対象DB が合わない。**参照先の表・カラムの食い違いも** | 一覧・詳細・書き込み・**定義（`CREATE`・`UPDATE`）・ピッカー・ラベル・⑩** | 書き込みは残る | RB-41（1節の直しと合わせて） |
| 409 | `BUSINESS_DEFINITION_CHANGED` | 画面を開いた後に定義（DSL か作業ロール）が変わった | 書き込み | 残らない | 【依頼者】画面を読み直してから保存し直す |
| 409 | `BUSINESS_WRITE_NOT_CONFIGURED` | 書き込みの接続の設定が無い | 書き込み | 残らない | 5つ目の手順書の RB-38（RB-44 で止めているなら決まりどおり） |
| 409 | `BUSINESS_DUPLICATE_VALUE` | 一意の項目にほかの行と同じ値 | 作成・更新 | 残る（`FAILURE`） | 【依頼者】値を変える |
| 409 | `BUSINESS_REFERENCE_NOT_FOUND`（項目の誤り `REFERENCE_NOT_FOUND`） | 参照の値が、参照先に無い、**または読む権限の無い参照先の値**。**応答・監査で2つを見分けられないように作ってある**（列挙の防止、U2 の BR7.2）。出どころは **⑩ の保存前の確かめ**と、⑪ の外部キーの違反の変換（確かめの後に参照先が消されたすき間も） | 作成・更新 | 残る（`FAILURE`・`BUSINESS_REFERENCE_NOT_FOUND`。参照の値は入らない） | 【依頼者】画面で、その項目に「参照先を読む権限がないため変更できません」の説明文が出ていないか、ロールの設定の画面で参照先のテーブルの権限を見る（RB-53）。権限があれば、参照先にある値を選び直す。AI は区別を確かめない（見本の PG を読まない） |
| 409 | **`BUSINESS_REFERENCE_NOT_FOUND`（項目の誤り `NOT_EDITABLE_NOT_NULL`）** | **作成の必須の参照項目が、読む権限が無い（`NO_PERMISSION`）か候補が 201 件以上（`TOO_MANY`）で空のまま**（承認済みの行き止まり M1: A）。画面は D13 の理由ごとの文を出す | 作成 | 残る（`FAILURE`） | RB-53 |
| 409 | `BUSINESS_SAVE_REJECTED` | 対象DB の決まり（参照されている行の削除・必須・長さ・桁・CHECK など）に合わない | 書き込み | 残る（`FAILURE`） | 【依頼者】入力を見直す |
| 403 | `ACCESS_DENIED` | テーブル・カラム・操作の権限が無い。**参照の道（ピッカー・ラベル）では、DSL に無い名前・参照元の主権限が FULL でない・参照先を読めない（`NO_PERMISSION`）・ラベルの値の行が無い、をすべて同じ 403 にする**（U1 の BR1.3。見分けない）。定義の取得では参照先の権限の不足を 403 にせず、`reference.status` の `NO_PERMISSION` で返す（U1 の BR1.1） | すべて | 残らない | 5つ目の手順書の RB-42、4つ目の手順書の RB-32 |
| 400 | `VALIDATION_FAILED` | 入力の誤り。**⑨ でほかに誤りがあるとき、`TOO_MANY` の必須の参照項目が `REQUIRED` として並ぶことがある**（U2 の D4）、**ピッカーの検索の条件の誤り**も | すべて | 残らない | 【依頼者】入力を直す |
| 413 | `PAYLOAD_TOO_LARGE` | 作成・更新の本文が 4 MiB、そのほかの本文が 64 KB を超えた | すべて | 残らない | 5つ目の手順書の RB-40 の注のとおり |
| 503 | `BUSINESS_TARGET_DB_UNAVAILABLE` | 対象DB に届かない（書き込みは保存されていない） | すべて（**定義の `CREATE`・`UPDATE`・ピッカー・ラベル・⑩ を含む**。定義の `VIEW` は出ない） | 書き込みは残る（`FAILURE`） | RB-48・RB-49・5つ目の手順書の RB-36 |
| 503 | `BUSINESS_TARGET_DB_TIMEOUT` | 読み取り・⑩: 時間切れ（**全体の上限を含む**。⑩ は保存されていない）。⑪ の確定の段: 保存されたか分からない | すべて | ⑩ は `FAILURE`、⑪ の確定の段は `UNKNOWN` | RB-50・RB-49・5つ目の手順書の RB-37 |
| 500 | （共通の 500） | 想定外の失敗 | すべて | 残らない（ソースの読み取り） | 元の手順書の RB-01・RB-12 |

- 「出る道」のうち、⑩ は参照の値を送る作成・更新だけで通る。参照の値を送らない作成・更新と削除は ⑩ を通らない（U2 の BR1.2）。
- 件数を見るとき（値は出ない。5つ目の手順書の RB-40 のコマンドに参照の WARN を足す）:
  ```bash
  docker compose logs --no-log-prefix --since 1h app | grep '要求をエラー応答に変換しました' | grep -oE '"code":"(BUSINESS_[A-Z_]*|ACCESS_DENIED|VALIDATION_FAILED|PAYLOAD_TOO_LARGE)"' | sort | uniq -c
  ```
  409 `BUSINESS_REFERENCE_NOT_FOUND` は診断の WARN を出さず、この変換の WARN だけで数える（**実測**）。手元の監視が動いていれば `log-queries.md` 2節の R3 とパネル 43。監査で数えるときは `event_type`・`result`・`failure_reason` だけを選ぶ（5つ目の手順書の RB-37 の手順 3 の形）。
- 409 `BUSINESS_REFERENCE_NOT_FOUND` は警報にしない（利用者の入力による業務の誤り。`alarms.md` 3節）。急に増えたときは、DSL の適用し直し・ロールの権限の変更・参照先の行の削除が直前に無かったかを【依頼者】と確かめる。

**重さ**: 低。

## RB-53 参照の項目が選べない（`TOO_MANY`・`NO_PERMISSION`）

**当たる場面**: 作成・更新の画面で、参照の項目が入力不可になり説明文が出ている。作成の必須の項目なら保存が 409（RB-52 の `NOT_EDITABLE_NOT_NULL`）になる。

**見分け方**（画面の説明文。U3 の `functional-spec.md` D12・D13。応答は定義の `reference.status`）

| 説明文 | 状態 | 原因 | 根拠 |
|---|---|---|---|
| 「候補が多いため選べません。管理者に、検索（ピッカー）への切り替えを依頼してください」 | `TOO_MANY` | REFERENCE の参照先が 201 行以上になった（取り出しは 201 行で止まり、長さが 10,000 字を超える行は数えない） | 結合テストで確かめ済み（U1 の T-B1〜T-B4） |
| 「参照先を読む権限がないため変更できません」 | `NO_PERMISSION` | 作業ロールに参照先のテーブル（またはその値・表示のカラム）を読む権限が無い。その用途で参照元のカラムを入力できないときは、この文は出ず既存の「入力できない」の表示になる | 結合テストで確かめ済み、U3 の D5 |

**重さ**: 低。作成の必須の項目で業務が止まるなら高。

**手順**

1. 【依頼者】画面の説明文と、どのテーブルの作成・更新の画面かを聞く（項目の名前・値は記録に写さない）。
2. `TOO_MANY` のとき: 【依頼者】DSL の画面で、そのカラムの `options` を REFERENCE から LOOKUP（ピッカー）に替えて適用し直す。
   - 書き方は README と DSL の JSON Schema に従う（この手順書では DSL の書き方を書かない）。
   - DSL の投入・適用は監査に残る（`DSL_SUBMITTED`・`DSL_APPLIED` など。5つ目の手順書までの DSL の手順と同じ）。適用し直すと、ロールの権限の対象の名前が変わることがある（4つ目の手順書の RB-32）。
   - 参照先の行を減らすのは対象DB の持ち主の判断で、この手順書の外。
3. `NO_PERMISSION` のとき: 【依頼者】ロールの設定の画面で、作業ロールに参照先のテーブルを読む権限を足すかを決める（`ROLE_PERMISSION_CHANGED` が監査に残る）。権限を足さない判断なら、その利用者はその項目を変えられない（決まりどおり）。
4. 必須の参照項目が `NO_PERMISSION` のまま作成できないのは、承認済みの行き止まり（M1: A）。文字入力に切り替える手は採らない（権限を欠く人が参照先の存在を試せるため）。

**確かめ方**: 【依頼者】作業ロールを選び直して作成の画面を開き直し、選択肢かピッカーが出る（画面の権限の判定は定義の応答だけで決まる）。

## RB-54 CI の画面のテストが「全件 passed なのに `Errors 1 error`」で落ちる（Q2: A）

**当たる場面**: CI（または手元の `./gradlew verify`）の `:frontendTest`・`:frontendCoverage` で、Test Files・Tests がすべて passed なのに、Vitest が処理されない誤りを報告して失敗した。

**見分け方**（`construction/ci-pipeline/flaky-repro-record.md`）

| 手がかり | 内容 |
|---|---|
| スタック | `Timeout._onTimeout` から始まり、`setState`（`dispatchSetState` → `requestUpdateLane` → `resolveUpdatePriority`）で `ReferenceError: window is not defined`。外された部品の時計（`setTimeout`・`setInterval`）の予約が、jsdom の片付け（大域の `window` が消える）の後に走った形 |
| Vitest の帰属の文 | 「…を流している間に起きた」は、そのファイルが投げたとは限らない（同じ作業プロセスで前に流れたファイルの残りでも出る） |
| この Intent の例 | make-you-chic-ui の Tooltip の 300 ms の予約（2026-11-01、run 41264314230 の2回の試行） |

**重さ**: 低（配備したアプリと利用者に影響しない。外された部品への更新を React は無視する）。

**手順**

1. CI の結果を試行ごとに確かめる（`gh run view <run> --json attempt,jobs`。`project.md` の学び）。
2. 再発を防ぐもの（2つ）が外れていないかを見る。
   - `frontend/vitest.setup.ts` の後片付け（`cac6fa54`、各テストの `cleanup()` の後に残った時計の予約を取り消す）が残っているか。
   - `vendor/make-you-chic-ui` の固定先が `cab4207`（Tooltip に外されたときの後片付け）以降か（`git -C vendor/make-you-chic-ui merge-base --is-ancestor cab42074cf040e3c03127db8c92582cc329f934d HEAD`）。
3. どちらも残っているのに同じ形で落ちたら、別の部品の予約が原因の見込み。`team.md` の「不安定なテストと CI の失敗」の決まりで扱う（手元で時間の上限を決めて再現を試み、二度目なら直すまで進まない）。
4. 決定的に確かめる手段は `flaky-repro-record.md` 6節の D1（片付けの後に `window` を外して 400 ms 待つ一時の確かめ。コミットしない）と D2（片付けの後に鳴った予約を数える）。直す前に落ち、直した後に通ることを記録する。
5. 原因の部品が make-you-chic-ui のものなら、依頼の文面を `<record>/make-you-chic-ui-request-<n>.md` に置き、上流の直しを上流のソースで確かめてから固定先を上げる（`team.md` の Way of Working）。

**確かめ方**: 直した後に、単独の繰り返し・画面のテストの全件1回・`./gradlew verify`・CI の1回目が通る（`flaky-repro-record.md` 8節と同じ形）。

- 同じ Intent の結合テストの偶然の一致（`ReferenceSameResponseIT`、目印 "abc" が乱数のトレースID に約 0.73% で当たった）は、`a3099f80` で直り再発していない（`construction/ci-pipeline/ci-config.md` 6節）。目印の文字列で「応答に値が無い」ことを確かめるテストを書くときは、16進の字に無い文字を含む目印にする。

## RB-55 k6 の結果（`evidence/`）に認証の値が入った

**当たる場面**: 負荷の試験の記録（`<record>/…/evidence/`）に JWT（`eyJ…` の形）・リフレッシュトークン・Authorization ヘッダーが入っている、または入ったまま コミット・プッシュした疑い。`gitleaksScan`（`./gradlew verify`・CI）が落ちた。

**重さ**: 高（公開のリポジトリに入るおそれ。`project.md` の Forbidden）。ただし値は使い捨ての環境の仮の署名鍵のもので、配備したアプリでは通らない（`project.md` の学び。負荷の試験は使い捨ての環境で行う）。

**手順**

1. 中身を表示せずに数える（`perf/README.md` の「記録（evidence/）に置く前の伏せ字」）。
   ```bash
   node perf/redact-k6-evidence.mjs --scan <記録の置き場>/evidence
   gitleaks dir --redact --no-banner --config .gitleaks.toml --exit-code 1 <記録の置き場>/evidence
   ```
2. まだコミットしていないとき: 元の json から伏せ字の道具を通して置き直す（`node perf/redact-k6-evidence.mjs <元> <記録の置き場>/evidence/<場面>.json`、テキストは `--text`）。手順 1 で 0 件になってからコミットする。
3. コミットした・プッシュしたとき: 【依頼者】に伝え、履歴の直し方（amend・rebase と force-push）を依頼者が決める（`origin` へのプッシュは依頼者。`team.md`）。
   - プッシュした後は、旧コミットを持つ Dependabot のブランチが公開側に残る間、CI の `gitleaksScan`（全ブランチ・履歴全体を見る）が同じ件数で落ちる。PR を閉じてブランチを消し、付け替えを待ってから CI を流し直す（`project.md` の学び）。
   - GitHub の secret scanning の知らせがあれば、依頼者が画面で扱う。
4. 値が配備したアプリのもの（使い捨ての環境のものでない）と分かったときは、元の手順書の RB-11 の署名鍵の入れ替えも行う（**推測**: 今の手順では起きない）。

**確かめ方**: 手順 1 の2つがどちらも 0 件。CI の `gitleaksScan` が1回目で通る。

## RB-56 この Intent の版を戻す

- 手順はこの Intent の `operation/deployment-pipeline/rollback-runbook.md` のとおりです（戻し先 `mastersmith:pre-reference-lookup-cleanup`、`sha256:e0ede6ddd5c4…`）。
- 移行と `.env` の変更が無いため、**イメージだけ**で戻ります。U1・U2・U3 は同じ WAR のため一緒に戻ります。戻すと保存前の参照の確かめが無くなり、外部キーの無い参照先へ、参照先に無い値を保存できる前の状態に戻ります（`rollback-runbook.md` 2.2）。
- 参照の2つの設定の誤りだけなら、戻さずに RB-51。
- 配備の前の複写（`…-202611011400-pre-reference-lookup-cleanup.tgz`）を戻すのは内部DB が壊れたときだけです（`rollback-runbook.md` 4節）。
- 戻した後は、コンテナのイメージ ID が戻し先と一致することを `docker inspect` で確かめます（`project.md` の学び）。

## 2. 警報ごとの初動の表（この Intent の差）

警報の決まりは変えていません（17 件のまま。`alarms.md` 1節）。前の5つの手順書の表に、次を足します。**配備したアプリは外部エクスポートが無効のため、今は配備したアプリでは鳴りません。**

| 警報名 | 変わったこと | 鳴ることの確かめ | 参照する手順 |
|---|---|---|---|
| 業務データの対象DB に届かない（`ms-biz-target-db`） | 参照の道（`/api/tables/reference/rows`）・定義の取得（`CREATE`・`UPDATE`）・保存前の確かめつきの作成と更新の 503 も数える（式 `uri=~"/api/tables/.*"`） | 確かめた（この Intent: 止めてから 81 秒で firing、戻して 4分50秒で inactive。`TIMEOUT` での firing は式の読み取り） | RB-48・RB-49（`TIMEOUT` なら RB-50） |
| 5xx の割合の増加（`ms-5xx-ratio`） | 参照の 503 も数える | 鳴った（条件つき。戻した後に firing） | RB-48 |
| コネクションプールの待ち（`ms-pool-pending`） | 参照の読み取りと ⑩ は一覧・詳細と同じ読み取りのプールを使う | この Intent では pending まで | RB-48・RB-50 |
| （警報なし） | 409 `BUSINESS_REFERENCE_NOT_FOUND`（パネル 43・`log-queries.md` R3）、選べない項目（画面だけ）、設定の誤り（起動の WARN）、CI の失敗、記録の認証の値 | — | RB-52・RB-53・RB-51・RB-54・RB-55 |

## 3. 定期の確認（この Intent の差）

前の5つの手順書の表に、次を足します。

| 確かめ | コマンド・操作 | 通る条件 |
|---|---|---|
| 前の日の参照の読み取りの失敗 | `docker compose logs --no-log-prefix --since 24h app` を `grep -c '対象DB の参照先を読めませんでした'`・`grep -c '保存の前に対象DB の参照先を読めませんでした'` で数える | どちらも 0 か少数（多ければ RB-48・RB-49） |
| 全体の上限に達した回数 | 同じログを RB-48 の手順 1 の2つ目で数える | `budgetExceeded` が真の行が 0（あれば RB-50） |
| 参照の2つの設定（アプリを作り直した後） | RB-51 の手順 1 | WARN の該当の項目名 0 件 |
| 手元の監視の版（入れ替え・起動し直しの後） | `docker inspect mastersmith-lgtm-1 --format '{{.Config.Image}} {{.Image}}'` と `compose.yaml` の記述を比べる（コンテナの名前は compose の既定の見込み、**推測**） | 0.38.0 とダイジェストが一致 |
| 記録に置いた k6 の結果（コミットの前） | RB-55 の手順 1 | どちらも 0 件 |

## 4. 確かめられなかったこと

この段では新しい手順を動くコンテナで試していません（Q1: A）。

| 項目 | 内容 |
|---|---|
| RB-48・RB-49 の `TIMEOUT` | 手元では起こしていない（`docker stop` では `UNAVAILABLE` だけ）。結合テスト（ServerSocket の方式、U1・U2 の T-T1）で確かめ済み |
| RB-48 の WARN の真偽値の出方 | `budgetExceeded` の値が引用符つきで出るかは確かめていない（両方に当たる形で数える） |
| RB-50 | `budgetExceeded=true` は結合テスト（U1 の T-T2・T-T3、U2 の T-T3）で確かめ済み。動くコンテナでは起こしていない。上限を延ばしたときのプールの待ちへの響きは測っていない |
| RB-51 | 設定の誤りのときに業務データの API が 503 `UNAVAILABLE` になり、参照の WARN に `sqlState` が無いことは推測（点検の振る舞いは単体テスト T-T4 で確かめ済み）。`sed -i ''` で行を消す形は流していない |
| RB-52 | `NOT_EDITABLE_NOT_NULL`・`TOO_MANY` の `REQUIRED`・ピッカーの 400・403 は結合テストで確かめ済み。動くコンテナでは見ていない |
| RB-53 | DSL を REFERENCE から LOOKUP に替えて適用し直す流れは、この段では流していない |
| RB-54 | 再発を防ぐ2つ（B4・上流の直し）は CI（`cb23466b` の1回目）と D1・D2 で確かめ済み。ほかの部品の予約で同じ形が起きるかは確かめていない |
| RB-55 | 手順 4（配備したアプリの値が入った場合）は今の手順では起きない見込みで、推測 |
| 警報 | 配備したアプリでは、外部エクスポートが無効のため、どの警報も鳴らない |

## Sources

- この段の質問 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/operation/incident-response/incident-response-questions.md`（1節 F1〜F12、2節の決まっていること、Q1〜Q4: すべて A）
- 前の5つの手順書: `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/incident-response/runbooks.md`、`aidlc/spaces/default/intents/260925-user-management/operation/incident-response/runbooks.md`、`aidlc/spaces/default/intents/260930-user-admin/operation/incident-response/runbooks.md`、`aidlc/spaces/default/intents/261004-role-menu/operation/incident-response/runbooks.md`、`aidlc/spaces/default/intents/261016-business-data-views/operation/incident-response/runbooks.md`
- この Intent の Observability Setup: `operation/observability-setup/alarms.md`・`log-queries.md`・`dashboards.md`・`anomaly-config.md`・`slo-config.md`
- この Intent の配備: `operation/deployment-pipeline/rollback-runbook.md`・`deployment-strategy.md`、`operation/deployment-execution/deployment-log.md`
- NFR 設計: `construction/u1-reference-read/nfr-design/reliability-design.md`・`security-design.md`・`trial-results.md`、`construction/u2-reference-write-check/nfr-design/reliability-design.md`・`security-design.md`
- 基盤の設計: `construction/u1-reference-read/infrastructure-design/infrastructure-specification.md`・`monitoring-design.md`・`cicd-pipeline.md`、`construction/u2-reference-write-check/infrastructure-design/infrastructure-specification.md`・`monitoring-design.md`
- 機能設計: `construction/u2-reference-write-check/functional-design/rules.md`、`construction/u3-reference-screens/functional-design/functional-spec.md`（D5・D12・D13・D32）
- CI: `construction/ci-pipeline/flaky-repro-record.md`・`ci-config.md`（6節）、`make-you-chic-ui-request-7.md`
- ソース（読み取りだけ）: `backend/src/main/java/cherry/mastersmith/businessdata/domain/BusinessDataProblemTypes.java`・`BusinessDataFieldErrorReason.java`・`ReferenceStatus.java`・`ReferenceStepOutcome.java`、`businessdata/service/ReferenceLookup.java`・`BusinessDataWriteService.java`、`targetdb/config/TargetDataSourceConfig.java`・`TargetDbSettings.java`
- リポジトリ: `perf/README.md`（「記録（evidence/）に置く前の伏せ字」）、`perf/redact-k6-evidence.mjs`、`README.md`
- 決まり: `aidlc/spaces/default/memory/team.md`・`project.md`
- 読み取りだけで確かめたこと（2026-11-01）: `ls ~/.mastersmith-backup/`（この Intent の複写2つの名前）

## Assumptions & Open Questions

- [assumption] 参照の2つの設定の誤りのとき、業務データの API は 503 `BUSINESS_TARGET_DB_UNAVAILABLE` になる（RB-51。動くコンテナでは試していない）。
- [assumption] `budgetExceeded` の真偽値のログの出方（RB-48）。
- [assumption] lgtm のコンテナの名前は `mastersmith-lgtm-1`（3節）。
- 新しい手順は使い捨ての環境で試していません（Q1: A）。4節に並べました。
