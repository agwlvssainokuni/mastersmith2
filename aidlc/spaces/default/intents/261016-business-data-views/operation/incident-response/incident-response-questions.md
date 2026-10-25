# Incident Response — 質問

障害の対応の記録（runbooks・incident-plan・escalation-matrix）は、これまでの Intent で作ってきました。

- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/incident-response/`（元の手順書。0節・RB-01〜RB-12）
- `aidlc/spaces/default/intents/260925-user-management/operation/incident-response/`（2つ目の手順書。RB-13〜RB-20）
- `aidlc/spaces/default/intents/260930-user-admin/operation/incident-response/`（3つ目の手順書。RB-21〜RB-27）
- `aidlc/spaces/default/intents/261004-role-menu/operation/incident-response/`（前の手順書。索引 RB-01〜RB-35、RB-28〜RB-35）

その後の Intent 261015-deps-ui-carryover には、この段がありませんでした。この Intent で増えたのは、業務データの一覧・詳細・作成・更新・削除（U1 読み取り・U2 書き込み・U3 画面）と、持ち越しの直し（U4・U5）です。増えた障害と、手順書の古くなった箇所を、読み取りだけで洗い出しました（2026-10-25）。この段では、コンテナを起動・停止しておらず、配備したアプリに要求を送っていません。

## 1. この Intent で増えた、起こりうる主な障害と、今の手順書で扱えるか

code は `backend/src/main/java/cherry/mastersmith/businessdata/domain/` の問題の種類の定義で確かめました。警報の動きは Observability Setup の実測（`operation/observability-setup/alarms.md`・`measurement-notes.md`）です。

| # | 障害 | 今の手順書・記録で扱えるか |
|---|---|---|
| F1 | **対象DB に届かない**: 503 `BUSINESS_TARGET_DB_UNAVAILABLE`（借りる待ち 3 秒の時間切れ）。対象DB を `docker stop` しても `docker pause` しても、実測ではこの code になった。新しい警報 `ms-biz-target-db` が 2分48秒で鳴る。読み取り・書き込みのどちらも止まり、定義（`/api/tables/definition`）だけは動く | 一部。前の手順書までの RB-04（見本の対象DB が止まった）は、DSL の照合の時代の手順で、業務データの API と新しい警報を書いていない |
| F2 | **保存されたか分からない**: 503 `BUSINESS_TARGET_DB_TIMEOUT`（問い合わせの時間切れ）。書き込みでは確定したか分からず、監査の結果は `UNKNOWN`。画面は「保存されたか分からない」と出す。実測では起こせなかった（問い合わせの最中に止める必要がある見込み） | 無い。確かめ方（行が保存されたか）と、監査の `UNKNOWN` の数え方が手順に無い |
| F3 | **書き込みの設定が無い・誤り**: 書き込みの2項目が無いと 409 `BUSINESS_WRITE_NOT_CONFIGURED`（読み取りは動く）。片方だけなら起動の WARN が1回。資格情報の誤りは F1 と同じ 503 になる見込み（推測） | 一部。`operation/deployment-pipeline/rollback-runbook.md` 3節（書き込みだけを止める・アカウントを消す）。誤りの見分け方の RB が無い |
| F4 | **対象DB の確定の後に監査の書き込みが失敗**: 対象DB の行は変わったのに監査の行が無い（承認済みの既知の制約）。`ms-audit-fail` が拾う（業務データの出来事でも文は同じ） | 一部。3つ目の手順書の RB-25（5つの操作の確定の後の監査の失敗）と同じ形。業務データの出来事は、ERROR から `detail`・主キーの値を外し、主キーのカラムの数とクラスの名前だけを出す（手で補うときの手がかりが少ない） |
| F5 | **業務の拒否**: 409 `BUSINESS_DUPLICATE_VALUE`・`BUSINESS_REFERENCE_NOT_FOUND`・`BUSINESS_ROW_OPERATION_NOT_ALLOWED`（主キーの無いテーブル・ビュー）・`BUSINESS_ROW_NOT_UNIQUE`・`BUSINESS_DEFINITION_CHANGED`・`BUSINESS_SAVE_REJECTED`、404 `BUSINESS_ROW_NOT_FOUND`・`BUSINESS_TABLE_NOT_FOUND`。監査に残る（403 は残らない） | 無い。利用者から「保存できない」と知らされたときに、どの code かを見分ける表が無い |
| F6 | **DSL と対象DB の食い違い**: 適用中の DSL の後に対象DB のテーブル・カラムが変わると、`BUSINESS_TARGET_DB_MISMATCH` になる見込み。DSL が適用されていないと `BUSINESS_DSL_NOT_APPLIED` | 一部。元の手順書の RB-05・RB-06（対象DB の設定・DSL の読み込み）。業務データの code との対応が無い |
| F7 | **業務データの 403 が多い**: `ms-forbidden` の数に入り、監査に残らない。実測では 534 件で鳴った | 一部。前の手順書の RB-33（置き場の 403）と同じ見分け方。業務データの道（`/api/tables/`）を足す必要がある |
| F8 | **大きな書き込み**: 本文の上限 4 MiB を超えると 413 `PAYLOAD_TOO_LARGE`。4 バイトの字を `\u` でエスケープする送り手では、同じ値でも超えうる | 無い（`perf/README.md` にだけある） |
| F9 | **手元の監視（lgtm）が止まった**: lgtm を動かして外部エクスポートを有効にしている間に lgtm が止まると、アプリに送信の失敗の ERROR が出て、`ms-error-logs` の数に入りうる。配備したアプリは外部エクスポートが無効のため、今は起きない | 無い（`operation/observability-setup/alarms.md` 5節にだけある） |
| F10 | **この Intent の版を戻す**: 戻し先は `mastersmith:pre-business-data-views`。書き込みだけを止める手もある | 扱える（`rollback-runbook.md`。戻しの練習 D1〜D5 で確かめ済み）。前の手順書の RB-35 の戻し先は古い |
| F11 | **書き込みの資格情報の漏えいの疑い**: 書き込みのアカウント `mastersmith_writer` は `DSL` の対象のスキーマの行を変えられる | 無い。元の手順書の RB-11（秘密情報の漏えいの疑い）は、署名鍵・初期管理者のパスワードの入れ替えだけを書く |

古くなった箇所（前の手順書まで）:
- **RB-35 の戻し先**: 今は `mastersmith:pre-business-data-views` と、この Intent の `rollback-runbook.md`。
- **RB-04**: 業務データの API と新しい警報（F1）を足す必要がある。
- **警報ごとの初動の表**: `ms-biz-target-db` が無い。Observability Setup の実測（`ms-5xx-ratio`・`ms-pool-pending`・`ms-forbidden` は使い捨ての環境で鳴った、条件つき）を反映していない。
- **警報の確かめの区別の表**: 前の手順書は `ms-5xx-ratio` を Unverified としていたが、この Intent で使い捨ての環境では鳴った（条件つき）。
- **最新の複写の場所**: 今は `~/.mastersmith-backup/mastersmith-data-202610210848-pre-business-data-views.tgz`（配備の前）と `…-after-business-data-views.tgz`（配備の後）。

## 2. 決まっていること・読み取りで確かめたこと（質問にしません）

| 決まっていること | 出典 |
|---|---|
| 受け手と判断者は依頼者1名。AI は頼まれたときに調べと手順の実行を補助する。止める・戻す・データを戻す操作は、依頼者の承認を得てから行う。知らせは Grafana の画面で見るだけ（通知の先は置かない） | 元の手順書の段の Q1: A、`aidlc/spaces/default/memory/team.md`・`project.md` の Deployment |
| 重さは2段（高・低） | 元の手順書の段の Q2: A |
| クラウドの仕組み（SSM・Incident Manager・AWS Backup）は、配備先が決まるまで作らない。手作業の手順で代える | `project.md` の Deployment |
| RTO 1日・RPO は「最後の手での複写まで」（内部DB）。ロール・権限の設定は、画面で書き出した YAML でも守る（前の手順書の RB-31） | 元の手順書の段の Q3: A、前の手順書の段の Q2: A |
| 戻しはイメージだけ（`mastersmith:pre-business-data-views`）が第一の手。書き込みだけに問題が出たときは、書き込みの設定を外して 409 にする手がある。配備の前の複写を戻すのは、移行の失敗・データの破損のときだけ | `operation/deployment-pipeline/rollback-runbook.md` 2〜4節 |
| 警報は、この Intent で足した `ms-biz-target-db` のほかは足さない・変えない。`ms-forbidden` のしきい値は 20 のまま | Observability Setup の F1: A・Q4: A |
| 業務データの行の値（主キーの値を含む）・スキーマ名・テーブル名・カラム名と、個人に関する値は、手順・記録・報告に出さず、件数と code・キーの名前だけで見る。確かめで行の値を見る必要があるときは、依頼者が画面で見る | `project.md` の Forbidden・Corrections |
| 業務データの行の作成・更新・削除は、依頼者が画面で行う。AI は管理者や利用者の資格情報で業務データの API を呼ばない。書き込みは対象DB と監査に残り、監査は消せない | 前の手順書の `escalation-matrix.md` 3節・4節、`project.md` の Corrections |
| 画面の不具合を調べるときは、依頼者がした操作（どのボタン・どの開閉、文言と場所）を先に聞き、同じ操作を試す | `project.md` の学び |

## Q1. 成果物の書き方

A. 前の4つの手順書を正とし、この Intent の差（RB-36 から）と古くなった箇所の直しだけを書く。`runbooks.md` の冒頭に、RB-01〜この Intent の最後までの索引の表を置く（前の手順書と同じ列。「今は使わない（置き換え先）」の印を付ける）（推奨）
B. 前の4つの手順書を写して直し、今の全体を1つの手順書にまとめ直す。以後の Intent は、このまとめを正として差を書く
C. 差と索引だけを書く（古い RB の印は付けず、直しの表だけで示す）
X. Other (please specify)

推奨の理由: 前の Intent と同じ書き方で量を抑えながら、障害のときに最初に開く索引だけで、どの RB が今は使えないかが分かります。まとめ直し（B）は、配備先が決まって手順を作り直すときにまとめて行うほうが、二度手間になりません。

[Answer]: A

## Q2. 「保存されたか分からない」（F2、503 `BUSINESS_TARGET_DB_TIMEOUT`）の確かめ方

書き込みが問い合わせの時間切れになると、対象DB で確定したかが分かりません。監査の結果は `UNKNOWN` になり、画面は「保存されたか分からない」と出します。

A. 手順は次のとおりにします。
   - 依頼者が画面の一覧・詳細で、その行が保存されたかを見て、保存されていなければもう一度作成・更新・削除します。AI は行の値を見ません。
   - AI は、内部DB の複写を読み取りで開き、結果が `UNKNOWN` の監査の行を、種類ごとに件数だけ数えます（アプリを約 10 秒止めるため、依頼者の承認を得てから）。
   - 時間切れが続くときは、F1 と同じく対象DB の状態を見ます。
   - 自動でやり直す仕組みは置きません（推奨）
B. A に加えて、AI が見本の PG に管理者で入り、`UNKNOWN` の行の主キーで行の有無を数えます。主キーの値を扱うため、値を表示しない台本を手順に書きます
C. 手順は書かず、画面の文言（もう一度確かめてください）に任せます
X. Other (please specify)

推奨の理由: 行の値と主キーを AI が扱わずに済み、`project.md` の Forbidden（行の値をログ・記録に出さない）に沿います。依頼者1人の使い方では、画面で見る方が早く確かです。

[Answer]: A

## Q3. 対象DB（見本の PG）のデータの守り方

RTO・RPO は内部DB の目標です。業務データの行は対象DB（今は見本の PG の `mastersmith_mastersmith-targetdb-postgres` のボリューム）にあり、内部DB の複写には入りません。見本の PG の行は、初期化の台本が作る架空の値です（本物の業務データではありません）。

A. 見本の PG は RPO の外とし、壊れたときは初期化の台本で作り直す（ボリュームを消して起動し直す）手順を書きます。本物の対象DB をつなぐときは、その DB の持ち主のバックアップに任せる、と `incident-plan.md` に書きます。作り直すと、書き込みのアカウントと `TEMPORARY` の取り上げも初期化の台本で入り直すことを確かめる手順も書きます（推奨）
B. A に加えて、見本の PG の内容を `pg_dump` でリポジトリの外（`~/.mastersmith-backup/`、権限 600）に取る手順と、戻す手順を書きます
C. 手順は書きません
X. Other (please specify)

推奨の理由: 見本の PG の行は架空の値で、台本から作り直せます。本物の業務データの守り方は、つなぐ DB の側で決まるもので、このアプリが持つ目標ではありません。

[Answer]: A

## Q4. 書き込みの資格情報の入れ替え（F11）

書き込みのアカウント `mastersmith_writer` のパスワードが漏れた疑いがあるときの手順が無いため、足すかを決めます。

A. RB を1つ足します。手順は次のとおりです。
   - 【承認】AI が、乱数のパスワードを表示せずに作ります。
   - 見本の PG の `ALTER ROLE mastersmith_writer PASSWORD …` を、psql の標準入力で渡します（コマンド行に入れない）。
   - `.env` の `MASTERSMITH_TARGET_DB_WRITE_PASSWORD` と `.env.targetdb` の `MASTERSMITH_SAMPLE_TARGETDB_WRITER_PASSWORD` を同じ値に置き換え、アプリを作り直します。
   - 確かめは、項目の行の数と書き込みの成否（依頼者が画面で1件）だけで行います。
   - 急ぐときは、まず書き込みだけを止める手（`rollback-runbook.md` 3.1）を使います（推奨）
B. 手順は書かず、漏れた疑いのときは書き込みのアカウントを消す手（`rollback-runbook.md` 3.2）だけにします
C. A と同じ手順を、読み取りのアカウント `mastersmith_reader` にも書きます
X. Other (please specify)

推奨の理由: 書き込みのアカウントは行を変えられるため、入れ替えの手順が要ります。値を表示しない形は、配備で書き足したときの形と同じで、確かめ済みです。

[Answer]: A

## Q5. 配備の前に取った `.env` の複写（`~/.mastersmith-env-before-writer`）

`rollback-runbook.md` 3.1 は、書き込みだけを止める手で、配備の前に取った `.env` の複写を戻す形にしています。その複写は「配備と戻しの練習が済み、戻す見込みが無くなってから消す。消すのは Deployment Execution の終わり」としていましたが、まだ残っています（権限 600、10/21 08:48 に作成。中身は表示していない）。

A. 今消します。書き込みだけを止める手は、複写を戻す形から、`.env` の書き込みの2行を値を表示せずに消す形（`sed` で項目の名前の行を消す）に替えて RB に書きます。`rollback-runbook.md` は書き換えず、差を `runbooks.md` に書きます。Deployment Execution の記録との差も `runbooks.md` に書きます（推奨）
B. 次の Intent の配備まで残します（書き込みだけを止める手を今のまま使えるようにする）。残す理由を `runbooks.md` に書きます
X. Other (please specify)

推奨の理由: 前の `.env` の複写は秘密を含むファイルで、残す期間は短い方が安全です。書き込みだけを止める手は、2行を消すだけで同じ結果になります。

[Answer]: A

## Consolidated Summary Confirmation

答え（Q1〜Q5: すべて A）から、この段で行うことを次のようにまとめました。

1. **書き方（Q1: A）**: 前の4つの手順書を正とし、この Intent の差を RB-36 から書きます。
   - `runbooks.md` の冒頭に、RB-01〜この Intent の最後までの索引を置き、今は使わない RB に置き換え先の印を付けます。
   - 古くなった箇所（RB-35 の戻し先・RB-04・警報ごとの初動の表・警報の確かめの区別の表・最新の複写の場所）の直しを、1節の表に書きます。
2. **足す RB（1節の F1〜F11 から）**
   - 対象DB に届かない（`ms-biz-target-db`、503 `BUSINESS_TARGET_DB_UNAVAILABLE`）
   - 保存されたか分からない（503 `BUSINESS_TARGET_DB_TIMEOUT`、Q2: A）
     - 依頼者が画面で行の有無を見て、無ければやり直す。
     - AI は内部DB の複写で `UNKNOWN` の件数だけを数える（アプリを止める前に承認を得る）。
     - 自動のやり直しは置かない。
   - 書き込みの設定が無い・誤り（409 `BUSINESS_WRITE_NOT_CONFIGURED`）
   - 対象DB の確定の後の監査の書き込みの失敗
   - 業務の拒否の code の見分けの表（409・404・413）
   - DSL と対象DB の食い違い
   - 業務データの 403（`ms-forbidden`）
   - 手元の監視が止まったとき
   - この Intent の版を戻す（`mastersmith:pre-business-data-views`、`rollback-runbook.md`）
   - 書き込みの資格情報の入れ替え（Q4: A）
     - 乱数を表示せずに作り、`ALTER ROLE` を標準入力で渡す。
     - `.env` と `.env.targetdb` の2項目を置き換えて、アプリを作り直す。
     - 確かめは、行の数と依頼者の画面での書き込み1件だけで行う。
3. **対象DB の守り方（Q3: A）**
   - 見本の PG は RPO の外とし、壊れたときは初期化の台本で作り直す手順を書きます（書き込みのアカウントと `TEMPORARY` の取り上げが入り直すことを確かめる）。
   - 本物の対象DB は、その持ち主のバックアップに任せると `incident-plan.md` に書きます。
4. **`.env` の複写（Q5: A）**
   - `~/.mastersmith-env-before-writer` を、この段の中で消します（中身は表示しない）。
   - 書き込みだけを止める手は、`.env` の書き込みの2行を値を表示せずに消す形に替えて RB に書きます。
   - `rollback-runbook.md` は書き換えず、Deployment Execution の記録との差も `runbooks.md` に書きます。
5. **成果物**: `runbooks.md`・`incident-plan.md`・`escalation-matrix.md` です。コンテナの起動・停止と、配備したアプリへの要求は行いません（読み取りだけで書きます）。

- Looks correct
- Request changes

[Answer]: Looks correct
