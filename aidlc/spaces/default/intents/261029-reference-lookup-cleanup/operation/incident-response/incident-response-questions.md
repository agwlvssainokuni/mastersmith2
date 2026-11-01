# Incident Response — 質問

障害の対応の記録（runbooks・incident-plan・escalation-matrix）は、これまでの Intent で作ってきました。

- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/incident-response/`（元の手順書。0節・RB-01〜RB-12）
- `aidlc/spaces/default/intents/260925-user-management/operation/incident-response/`（2つ目の手順書。RB-13〜RB-20）
- `aidlc/spaces/default/intents/260930-user-admin/operation/incident-response/`（3つ目の手順書。RB-21〜RB-27）
- `aidlc/spaces/default/intents/261004-role-menu/operation/incident-response/`（4つ目の手順書。RB-28〜RB-35）
- `aidlc/spaces/default/intents/261016-business-data-views/operation/incident-response/`（前の手順書。索引 RB-01〜RB-47、RB-36〜RB-47）

その後の Intent 261025-deps-perf-carryover・261027-perf-targets-review には、この段がありませんでした（RB の番号は RB-47 で止まっています）。この Intent で増えたのは、他テーブル参照の読み取り（U1：選択肢・ピッカー・ラベル、定義の取得の参照の情報）・保存前の参照の確かめ（U2）・参照の画面（U3）・k6 の結果の伏せ字（U4）・コンテナの版とそろえ・perf の文書（U5）です。増えた障害と、手順書の古くなった箇所を、読み取りだけで洗い出しました（2026-11-01）。この段では、コンテナを起動・停止しておらず、配備したアプリに要求を送っていません。

各問いは、選択肢の前に推奨とその理由を書いています（`aidlc/spaces/default/memory/team.md` の Corrections）。

## 1. この Intent で増えた、起こりうる主な障害と、今の手順書で扱えるか

code・状態コードは `backend/src/main/java/cherry/mastersmith/businessdata/domain/BusinessDataProblemTypes.java`、ログは `businessdata/service/ReferenceLookup.java`・`BusinessDataWriteService.java`、監査は U2 の `functional-design/rules.md`（BR5）で確かめました。警報とログの動きは Observability Setup の実測（`operation/observability-setup/alarms.md`・`log-queries.md`、2026-11-01、使い捨ての環境）です。

| # | 障害 | 症状・見分け（確かめた事実） | 今の手順書で扱えるか |
|---|---|---|---|
| F1 | **参照の読み取りで対象DB に届かない** | 503 `BUSINESS_TARGET_DB_UNAVAILABLE`。作成・更新の画面が開けない（定義の取得の `CREATE`・`UPDATE` は参照先を読む）、ピッカーの「検索できません」、ラベルが値だけになる。定義の `VIEW`（一覧・詳細）は 200 のまま。WARN はロガー `ReferenceLookup`（キー `purpose=REFERENCE`・`reason`・`sqlState`・`vendorCode`・`exceptionClass`・`budgetExceeded`、`stage` 無し）。`ms-biz-target-db` が止めてから 81 秒で firing（前の Intent と合わせて 1分半〜3 分）。監査には残らない（参照の読み取りは監査の外、U1 の BR1.7） | 一部。前の手順書の RB-36 は一覧・詳細・書き込みの 503 だけで、「定義の `CREATE`・`UPDATE` も 503 になる（作成・更新の画面が開けない）」ことと `ReferenceLookup` の WARN が無い |
| F2 | **保存前の確かめ（⑩）で対象DB に届かない・時間切れ** | 作成・更新が 503（`UNAVAILABLE`・`TIMEOUT`）。**保存されていないことが確か**（書き込みの接続を借りる前に止まる。RB-37 の「保存されたか分からない」とは違う）。WARN はロガー `BusinessDataWriteService` の `purpose=REFERENCE`・`stage`（`BORROW`・`PREVIOUS_ROW`・`COUNT_STATE`・`CHECK`）。書き込み（⑪）の WARN は同じロガーの `purpose=WRITE`。監査は `BUSINESS_ROW_CREATED`・`UPDATED` の `FAILURE`（理由は 503 の code） | 無い。RB-36・RB-37 は ⑪ だけを書いている。`purpose` の値が U1 と同じ `REFERENCE` のため、見分けはロガー（Loki の `scope_name`）か `stage` の有無（`log-queries.md` 2節 R1・R2・R4） |
| F3 | **参照の読み取りの全体の上限（`budgetExceeded`）** | 定義の取得で参照の項目が多い・参照先が重いと、全体の上限（既定 10 秒、`MASTERSMITH_TARGET_DB_REFERENCE_READ_TOTAL`）に達し、流さずに 503 `BUSINESS_TARGET_DB_TIMEOUT`。WARN の `budgetExceeded=true`・`reason=TIMEOUT`。手元では起こしていない（結合テスト T-T2・T-T3 で確かめ済み） | 無い（Q4） |
| F4 | **2つの新しい設定の誤り** | `MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_REFERENCE`・`MASTERSMITH_TARGET_DB_REFERENCE_READ_TOTAL` が 1 秒未満だと、点検の誤りで**対象DB 全体（一覧・詳細・書き込み・参照）が使えなくなる**（起動は続き、ログインは動く）。起動の WARN（`TargetDataSourceConfig`、キー `items`）に Spring の項目名 `mastersmith.target-db.query-timeout.reference`・`mastersmith.target-db.reference-read-total` が出る。`deployment-strategy.md` の S1 が書いた `QUERY_TIMEOUT_REFERENCE`・`REFERENCE_READ_TOTAL` では見つからない（`deployment-log.md` 3節の1） | 一部。手は `operation/deployment-pipeline/rollback-runbook.md` 3節（行を値を表示せずに消す）にある。見分け方（症状が F1 と同じ 503 に見える）と、探す項目名の直しが RB に無い |
| F5 | **「選べない値です」（409 `BUSINESS_REFERENCE_NOT_FOUND`）** | 理由は2つ: 項目の誤り `REFERENCE_NOT_FOUND`（参照先に無い値・読む権限の無い参照先の値。**応答・監査で見分けられない**ように作ってある、U2 の BR7.2）と `NOT_EDITABLE_NOT_NULL`（作成の必須の参照項目が読めない・候補が 201 件以上で空）。出どころは ⑩ の確かめと、⑪ の外部キーの違反の変換の2つ。監査に残る（`FAILURE`・`failure_reason=BUSINESS_REFERENCE_NOT_FOUND`、参照の値は入らない）。警報にはしない。診断の WARN は出ず、`GlobalExceptionHandler` の変換の WARN だけ | 一部。前の手順書の RB-40（code の見分け）は外部キーの違反だけを書いている（Q3） |
| F6 | **参照先が多すぎて選べない（`TOO_MANY`）・権限が無くて選べない（`NO_PERMISSION`）** | REFERENCE の参照先が 201 行以上になると、その項目は入力不可になり「候補が多いため選べません。管理者に、検索（ピッカー）への切り替えを依頼してください」と出る。作成の必須の項目なら保存できない（409 `NOT_EDITABLE_NOT_NULL`、承認済みの行き止まり M1: A）。直すのは DSL の `options` を LOOKUP に替えて適用し直すこと（`DSL_APPLIED` が監査に残る）。`NO_PERMISSION` は参照先のテーブルの主権限の不足で、ロールの設定で直す | 無い |
| F7 | **DSL と対象DB の食い違い（参照）** | 参照先のテーブル・カラムの型が DSL と食い違うと、参照の読み取りも保存前の確かめも 409 `BUSINESS_TARGET_DB_MISMATCH` | 一部。前の手順書の RB-41 に参照の経路が無い |
| F8 | **CI の画面のテストの「処理されない誤り」（Tooltip の時計）** | 全件 passed なのに `Errors 1 error`・`ReferenceError: window is not defined`（make-you-chic-ui の Tooltip の 300 ms の予約が jsdom の片付けの後に走る）。2026-11-01 の run 41264314230 で2回続けて起き、`frontend/vitest.setup.ts` の後片付け（B4、`cac6fa54`）と上流の直し（固定先 `cab4207`、`cb23466b`）で直した。二段の確かめ済み（`construction/ci-pipeline/flaky-repro-record.md` 8節） | 記録はある（`flaky-repro-record.md`）。手順書と事後の振り返りは無い（Q2） |
| F9 | **CI の結合テストの偶然の一致（`ReferenceSameResponseIT`）** | 目印 "abc" が乱数のトレースID に約 0.73% で当たった（`a75baec4` の1回目）。`a3099f80` で直した（`ci-config.md` 6節） | 記録はある。再発は無い |
| F10 | **ログの例外に対象DB のホスト名が出る（HikariCP）** | 対象DB が引けない間、`com.zaxxer.hikari.pool.PoolBase` の WARN のスタックトレース（`exception_stacktrace`）の `UnknownHostException` に接続先のホスト名が入る。ユーザー名・パスワード・JDBC の URL は入らない。**依頼者の決定 C（許容し、記録だけにする）** | 記録はある（`log-queries.md` 3節）。手順の中でスタックトレースを外へ出すときの扱いが無い |
| F11 | **k6 の結果に認証の値が残る** | U4 の伏せ字の仕組み（`perf/` の下）を通さずに `evidence/` へ置くと、JWT が記録に入る。前の Intent の Deployment Execution では、プッシュ後の CI の `gitleaksScan` で落ちて amend と force-push になった | 無い。元の手順書の RB-11（秘密情報の漏えいの疑い）は、署名鍵・パスワードの入れ替えだけを書く |
| F12 | **この Intent の版を戻す** | 戻し先は `mastersmith:pre-reference-lookup-cleanup`（`sha256:e0ede6ddd5c4…`）。イメージだけで戻る（移行・`.env` の変更なし）。U1・U2・U3 は同じ WAR のため一緒に戻る | 扱える（`rollback-runbook.md` 2節）。前の手順書の RB-47 の戻し先は古い |

古くなった箇所（前の手順書まで）:
- **RB-47 の戻し先**: 今は `mastersmith:pre-reference-lookup-cleanup` と、この Intent の `rollback-runbook.md`。
- **最新の複写の場所**: 配備の前 `~/.mastersmith-backup/mastersmith-data-202611011400-pre-reference-lookup-cleanup.tgz`、配備の後 `…-202611011409-after-reference-lookup-cleanup.tgz`（どちらも権限 600。`operation/deployment-execution/deployment-log.md` 2節）。
- **RB-36**: 定義の `CREATE`・`UPDATE` も 503 になる（作成・更新の画面が開けない）。`ms-biz-target-db` が鳴るまでの時間（前は 2分48秒、今回 81 秒）。
- **RB-40**: 409 `BUSINESS_REFERENCE_NOT_FOUND` の出どころと理由（F5）、`NOT_EDITABLE_NOT_NULL`。
- **RB-41**: 参照の経路の 409 `BUSINESS_TARGET_DB_MISMATCH`（F7）。
- **RB-43**: 手元の監視は lgtm 0.38.0（U5 で compose.yaml の版にそろえた）。入れ替えの後は `docker inspect` で版とダイジェストを確かめる（`team.md` の Deployment）。
- **元の手順書の RB-05・RB-12（対象DB の設定）**: 読み取りの7項目と書き込みの2項目に、参照の2項目が加わった（F4）。
- **Loki の問い合わせ**: 設計の `| json | purpose="REFERENCE"` は使えず、`| json` を付けずに `| purpose="REFERENCE"` で絞る（`log-queries.md` 2節）。

## 2. 決まっていること・読み取りで確かめたこと（質問にしません）

| 決まっていること | 出典 |
|---|---|
| 書き方: 前の5つの手順書を正とし、この Intent の差を **RB-48 から**書く。`runbooks.md` の冒頭に RB-01〜この Intent の最後までの索引を置き、今は使わない RB に置き換え先の印を付ける。古くなった箇所の直しは1節の表に書く | 前の手順書の段の Q1: A、`aidlc/spaces/default/memory/project.md` の学び |
| 受け手と判断者は依頼者1名。AI は頼まれたときに調べと手順の実行を補助する。止める・戻す・データを戻す・`.env` を変える操作は、依頼者の承認を得てから行う。知らせは Grafana の画面で見るだけ（通知の先は置かない） | 元の手順書の段の Q1: A、`team.md`・`project.md` の Deployment |
| 重さは2段（高・低）。RTO 1日・RPO は「最後の手での複写まで」（内部DB）。見本の PG は RPO の外で、壊れたときは初期化の台本で作り直す（前の手順書の RB-46） | 元の手順書の段の Q2・Q3: A、前の手順書の段の Q3: A |
| クラウドの仕組み（SSM・Incident Manager・AWS Backup）は、配備先が決まるまで作らない。手作業の手順で代える | `project.md` の Deployment |
| 戻しはイメージだけ（`mastersmith:pre-reference-lookup-cleanup`）が第一の手。2つの新しい設定の誤りだけのときは、イメージを替えずに行を消す手（`rollback-runbook.md` 3節）。配備の前の複写を戻すのは内部DB が壊れたときだけ | `operation/deployment-pipeline/rollback-runbook.md` 2〜4節 |
| 警報は足さない・変えない（17 件のまま）。参照の 503・保存前の確かめの 503 は既存の `ms-biz-target-db` で拾う。409 `BUSINESS_REFERENCE_NOT_FOUND` は警報にしない | Observability Setup の `alarms.md` 1節・3節 |
| 配備したアプリは外部エクスポートが無効のため、配備したアプリではどの警報も鳴らない。気づくのは画面の表示（「検索できません」・作成と更新の画面が開けない・「選べない値です」） | `alarms.md` 4節 |
| HikariCP の WARN のスタックトレースに対象DB のホスト名が出ることは許容し、直さない・次の Intent の項目にもしない（決定 C）。手順書では、ログを手順の外（上流への依頼・課題の文面など）へ写すときにスタックトレース（`exception_stacktrace`）を写さない、とだけ書く | `log-queries.md` 3節の依頼者の決定 |
| 業務データの行の値・参照の値とラベル・スキーマ・テーブル・カラムの名前と、個人に関する値は、手順・記録・報告に出さず、件数と code・キー・ロガーの名前だけで見る。参照先に値があるか・権限があるかの確かめは【依頼者】が画面（とロールの設定の画面）で行い、AI は見本の PG の業務データの行を読まない | `project.md` の Forbidden・Corrections、前の手順書の `escalation-matrix.md` 3節・4節 |
| 409 `BUSINESS_REFERENCE_NOT_FOUND` は、参照先に無い値と読む権限の無い値を、応答・監査で見分けられないように作ってある（列挙の防止）。手順書でこの区別を崩す手（AI がサーバー側で見分ける仕組みを足すなど）は書かない | U2 の `functional-design/rules.md`（BR7.2 の同一の応答） |
| 参照の読み取りは監査に残らない。⑩ の拒否・503 は既存の書き込みの監査（`BUSINESS_ROW_CREATED`・`UPDATED` の `FAILURE`）に残る | U1 の BR1.7、U2 の BR5、`log-queries.md` 4節 |
| k6 の結果を `evidence/` に置くときは、U4 の伏せ字の仕組みを通し、`eyJ` の形の検索と `gitleaks dir` で確かめる | `project.md` の Forbidden・学び、`team.md` の Testing Posture |
| 不安定なテスト・CI の失敗は `team.md` の「不安定なテストと CI の失敗」の決まりで扱う。CI の結果は試行ごと（`gh run view --json attempt,jobs`）に確かめる | `team.md`・`project.md` の学び |
| 画面の不具合を調べるときは、依頼者がした操作（どのボタン・どの開閉、文言と場所）を先に聞き、同じ操作を試す | `project.md` の学び |

## Q1. 新しい手順を使い捨ての環境で試すか

1節の F1・F2 の 503 と警報は、Observability Setup で使い捨ての環境で実測済みです（`docker stop`、`UNAVAILABLE` だけ）。F3（`budgetExceeded`）・F4（設定の誤り）・F6（`TOO_MANY`）は、結合テストと単体テストで確かめ済みですが、動いているコンテナでは試していません。

**推奨: A**。理由:
- 手順書に書く状態コード・code・ログのレベル・キー・監査の有無は、ソースと結合テスト、Observability Setup の実測で確かめられます（`project.md` の学び「ソースか実際の要求で確かめてから書く」を満たす）。
- F4 は設定の行を消すだけの手で、失うものがありません（`rollback-runbook.md` 3節）。F6 の直し（DSL の切り替え）は、既存の DSL の適用の手順そのものです。
- 試すと、使い捨ての環境の用意（DSL・ロール・種）に 1 時間ほどかかり、Performance Validation の前に VM のメモリを使います。

A. 試しません。ソース・テスト・Observability Setup の実測から書き、動くコンテナで確かめていない点は「推測」または「結合テストで確かめ済み」と書き分けます。4節「確かめられなかったこと」に並べます
B. F4 だけを試します。使い捨ての環境（`docker/perf/compose.yaml`、`-p mastersmith-perf`）のアプリに `MASTERSMITH_TARGET_DB_REFERENCE_READ_TOTAL=500ms` を渡して起動し、起動の WARN の項目名・業務データの API の 503 の code・ログインが動くことを確かめてから消します（30 分ほど。配備したアプリは止めない）
C. F3・F4・F6 を試します（F6 は参照先を 201 行にした表で作成の画面の表示を見る）。1〜2 時間ほど
X. Other (please specify)

[Answer]: A

## Q2. CI の画面のテストの失敗（F8、Tooltip の時計）の事後の振り返り

運用の段の決まり（`phases/operation.md`）は、重さ P1・P2 の障害に事後の振り返りを求めます。F8 は CI だけで起きた失敗で、配備したアプリと利用者には影響していません（重さは低）。仕組み・時刻・直し・二段の確かめは `construction/ci-pipeline/flaky-repro-record.md` に、上流への依頼は `make-you-chic-ui-request-7.md` にあります。

**推奨: A**。理由:
- 重さは低で決まりの対象の外ですが、同じ形（外された部品の時計が片付けの後に走る）が make-you-chic-ui のほかの部品やほかの画面のテストで起こりうるため、見分け方を手順書に残すと次に早く気づけます。
- 記録は `flaky-repro-record.md` にそろっているため、写さずに要点と参照だけを書けば量を抑えられます。
- 全文の振り返り（B）は、配備したアプリの障害ではないため、手間に見合いません。

A. `incident-plan.md` に「この Intent の事後の振り返り（重さ 低）」の短い節を置き、F8 と F9 の時刻の要点・原因・直し・再発を防ぐもの（`vitest.setup.ts` の後片付け B4 を外さない、上流の固定先 `cab4207` 以降を保つ）を書き、詳細は `flaky-repro-record.md` を参照します。あわせて `runbooks.md` に RB を1つ足し、CI で「全件 passed なのに `Errors 1 error`」になったときの見分け方（スタックの `Timeout._onTimeout`・`window is not defined`、Vitest の帰属の文は投げた場所とは限らない、D1・D2 の確かめ方）を書きます（推奨）
B. A に加えて、`incident-plan.md` に全文の振り返り（時刻ごとの経過・影響・原因・寄与した要因・うまくいったこと・直すこと）を書きます
C. 振り返りも RB も書きません（`flaky-repro-record.md` だけで足りるとする）
X. Other (please specify)

[Answer]: A

## Q3. 業務データの code の見分けの表（前の手順書の RB-40）の扱い

この Intent で、409 `BUSINESS_REFERENCE_NOT_FOUND` の出どころ（⑩ の確かめ・⑪ の外部キー）と理由（`REFERENCE_NOT_FOUND`・`NOT_EDITABLE_NOT_NULL`）、参照の経路の 503・409 `MISMATCH`、定義の取得の 503 が加わりました。利用者から「保存できない」「開けない」「選べない」と知らされたときに最初に開く表です。

**推奨: A**。理由:
- 障害のときに表を2つ（RB-40 と差の表）見比べずに済み、1つの表で code・状態コード・監査に残るか・どの経路かが引けます。
- 前の手順書の RB-40 は承認済みのため書き換えず、索引で「使わない → 新しい RB」と印を付ければ、書き方の決まり（差を書く、承認済みを書き換えない）と食い違いません。

A. 新しい RB に、RB-40 の表を写したうえで参照の行を足した「今の全体の表」を置き、索引で RB-40 を「使わない → 新しい RB」にします。409 `BUSINESS_REFERENCE_NOT_FOUND` の行には、権限の不足と値の不在を見分けないこと、確かめは【依頼者】が画面の表示（`NO_PERMISSION`・`TOO_MANY` の説明文）とロールの設定の画面で行うことを書きます（推奨）
B. 新しい RB には参照で増えた行だけを書き、RB-40 は「直しを合わせて読む」にします（表は2つに分かれる）
X. Other (please specify)

[Answer]: A

## Q4. 参照の読み取りの時間切れが続くとき（F2・F3）に、上限を延ばすことを手順に入れるか

参照の読み取りの上限は、文1回 5 秒（`MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_REFERENCE`）と定義の取得の全体 10 秒（`MASTERSMITH_TARGET_DB_REFERENCE_READ_TOTAL`）です。`.env` で延ばせますが、延ばすと最悪の待ち（今は全体で約 11 秒＋借りる待ち 3 秒）が長くなり、読み取りのプール（一覧・詳細と共有）を長く占めます。

**推奨: A**。理由:
- 時間切れの多くは、対象DB の状態（止まっている・重い）か参照先の作り（索引が無い・REFERENCE のまま行が多い）が原因で、上限を延ばしても直らず、ほかの画面の待ちを増やすだけです。
- 延ばすこと自体は設定の行を書くだけで戻せる（`rollback-runbook.md` 3節と同じ形）ため、原因を記録したうえで依頼者の判断で行う手として残せば足ります。
- 性能の目標（p95）を緩める手にはしません（`project.md` の学び「目標を緩めて満たしたことにしない」）。

A. 手順は、①対象DB の状態を見る（RB-36・F1 と同じ）、②WARN の `reason`・`budgetExceeded`・`sqlState` を R1・R2 で数える、③参照先の作り（REFERENCE の行数・LOOKUP の検索の列の索引）を【依頼者】と確かめる、の順にします。上限を延ばすのは最後の手とし、【承認】のうえで `.env` に行を足して作り直し、原因と延ばした値を記録し、戻すときは行を消す、と書きます（推奨）
B. 上限を延ばす手は書きません（時間切れは対象DB の側で直す）
C. 時間切れが出たら、まず上限を延ばして様子を見る手順にします
X. Other (please specify)

[Answer]: A

## Sources

- この Intent の Observability Setup: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/operation/observability-setup/alarms.md`・`log-queries.md`（3節の決定 C を含む）・`anomaly-config.md`・`dashboards.md`・`slo-config.md`・`observability-setup-questions.md`
- この Intent の配備: `operation/deployment-pipeline/rollback-runbook.md`・`cd-config.md`・`deployment-strategy.md`、`operation/deployment-execution/deployment-log.md`
- NFR 設計: `construction/u1-reference-read/nfr-design/reliability-design.md`（1節〜5節）・`security-design.md`、`construction/u2-reference-write-check/nfr-design/reliability-design.md`（1節〜6節）・`security-design.md`、`construction/u3-reference-screens/nfr-design/security-design.md`、`construction/u4-perf-evidence-redactor/nfr-design/security-design.md`、`construction/u5-container-perf-docs/nfr-design/security-design.md`
- 基盤の設計: 各単位の `infrastructure-design/infrastructure-specification.md`・`monitoring-design.md`・`cicd-pipeline.md`
- 機能設計: `construction/u2-reference-write-check/functional-design/rules.md`（BR1〜BR6）、`construction/u3-reference-screens/functional-design/functional-spec.md`（D5・D12・D32）
- CI: `construction/ci-pipeline/flaky-repro-record.md`・`ci-config.md`（6節）、`make-you-chic-ui-request-7.md`
- 前の手順書: `aidlc/spaces/default/intents/261016-business-data-views/operation/incident-response/runbooks.md`（索引・1節）・`incident-response-questions.md`
- コード（読み取り）: `backend/src/main/java/cherry/mastersmith/businessdata/domain/BusinessDataProblemTypes.java`・`ReferenceStatus.java`・`RequiredReferenceState.java`、`businessdata/service/ReferenceLookup.java`・`BusinessDataWriteService.java`、`targetdb/config/TargetDataSourceConfig.java`
- 決まり: `aidlc/spaces/default/memory/team.md`・`project.md`・`phases/operation.md`

## Assumptions & Open Questions

- [assumption] F4 の設定の誤りのとき、業務データの API は 503 `BUSINESS_TARGET_DB_UNAVAILABLE`、参照の WARN は `exceptionClass` の無い `reason=UNAVAILABLE` になる。U1 の `reliability-design.md` 1.2 と `ReferenceLookup` の `NotConfigured` の枝で読み取ったもので、動くコンテナでは確かめていない（Q1）。
- [assumption] F3（`budgetExceeded`）は手元で起こしていない。結合テスト T-T2・T-T3 の結果に頼る。
