# Deployment Pipeline の質問（Intent 261004-role-menu）

この Intent（F: ロールベースの権限、I: N 階層のメニュー。U1〜U7、Bolt B1〜B9）の配備の流れを決めるための質問です。前の Intent（`261004-safety-carryover`）の配備の手順と、既にある `Dockerfile`・`compose.yaml`・README を正とし、今回の差で判断が分かれる点だけを尋ねます（`project.md` の Deployment の学び）。

## 決まっていること（質問にしない）

| 項目 | 決まっていること | 根拠 |
|---|---|---|
| 配備先と配備の仕方 | 開発者の PC 上のコンテナ（`compose.yaml`、プロジェクト `mastersmith`）。自動の配備は持たず、依頼者の承認のうえで手で配備する。方式は1台の置き換え。本番配備の承認者は依頼者 | `team.md`・`project.md` の Deployment |
| 今動いている版 | `mastersmith-app-1` がイメージ `mastersmith:local`（`fefeafbd299b`、前の Intent で配備した `273c021` の版）で healthy。Mailpit・見本の対象DB（PostgreSQL）も動いている。colima の VM は CPU 4・メモリ 6GiB（2026-10-14 に読み取りだけで確かめた） | `docker ps`・`docker image inspect`・`colima list`、`aidlc/spaces/default/intents/261004-safety-carryover/operation/deployment-execution/deployment-log.md` |
| 配備する版 | `develop` の先頭。アプリのコードは `005730a`（その後のコミットは `aidlc/` の記録と README の1行だけ）。`005730a` の push の CI（run 38432556919）は1回目で success | `construction/ci-pipeline/ci-config.md` 6節、`git diff 005730a HEAD` |
| 配備の前の検査 | verify・CI・E2E は Build and Test と CI Pipeline の結果を正とし、流し直さない。E2E（010〜170 の 18 ファイル・230 件）は Build and Test で `005730a` と同じ中身で通っており、その後にアプリのコードの変更は無い（`team.md` の「リリースの前に E2E」はこれで満たす） | `construction/build-and-test/test-results.md` 1節 |
| 変わるもの | アプリのソース（ロール・グループ・権限・メニュー・骨組みの画面、DSL の書式の版 2）、内部DB の移行 V10〜V12（表と監査の3列を足すだけ。`users.admin_flag` は残す）、`application.yaml`（Tomcat の `max-swallow-size: 11MB`、`StatementCreatorUtils` のログを OFF）、`Dockerfile`（`-XX:+ExitOnOutOfMemoryError`）。`compose.yaml`・`.env.example` は変わらないため `.env` は変えない | `git diff 273c021 HEAD`、各単位の `infrastructure-design/cicd-pipeline.md` |
| 戻し先のタグ | 入れ替えの前に、今のイメージに `mastersmith:pre-role-menu` を付ける。ID が `fefeafbd299b` で始まることを確かめる | README の「戻し方」、前の Intent の手順 |
| 配備の前の内部DB の複写 | 入れ替えの前にアプリを止めて、内部DB を `~/.mastersmith-backup/`（権限 700）へ複写する（バックアップを兼ねる）。スキーマが変わるため省かない | `construction/group/infrastructure-design/infrastructure-specification.md` 4節、`construction/role/infrastructure-design/infrastructure-specification.md` 4節、README の「内部DBのバックアップと戻し方」 |
| 戻しの基本の形 | イメージだけで戻す（スキーマは前進のみで、前の版は足した表と列を見ない）。配備の前の複写を戻すのは、データが壊れたときと移行が途中で失敗したときだけ（その後のデータと監査の行を失う） | `team.md` の Deployment、README の「戻し方」 |
| 戻したときの管理の権限 | 管理の画面と管理の API の可否は、新しい版でも前の版でも `users.admin_flag` で決まる。ロールは業務データの権限だけに使い、業務データの画面はまだ無い。そのため、戻したときの権限のずれは「管理の可否が `admin_flag` のとおりで、戻す直前と変わらないこと」で確かめる（確かめ方は Q3） | `inception/requirements-analysis/requirements.md` の C1・FR1.3・FR10.3、`team.md` の Deployment |
| 前の版が版 2 の DSL を読めないこと | 前の版は版 2 を読めず、DSL が無い状態で起動する。版 2 のプレビューが残ったまま戻すと表示が 500 になり、適用は失敗するため、戻す前にプレビューを破棄する | `construction/dsl-v2/nfr-design/logical-components.md` 7節、`inception/user-stories/stories.md` の AC6.1.8 |
| G-19 の前提 | 前の版（`273c021`）の `backend/src/main` で `AuditEventRepository.findAllByOrderByOccurredAtAsc` を呼ぶ箇所は無い（宣言だけ。2026-10-14 に `git grep` で読み取りだけで確かめた）。戻しの練習の合格の条件には「前の版が監査の行を実体として読む経路が無いことの再確認」を入れる（依頼者の答え A と、レビューの R-01 の絞り方） | `construction/group/code-generation/code-summary.md` 4節・8節 |
| 監査に残る操作の扱い | スモークテストや DSL の操作で監査に残るもの（ログイン・DSL の生成・投入・適用・破棄、ロール・グループの操作）は、行う前に依頼者に伝え、期待の件数をその場で合わせてから数える。値（メールアドレス・パスワード）は表示せず、件数とキーの名前だけで見る | `project.md` の Deployment・Corrections の学び |
| 入れ替えの後の画面 | 画面が大きく変わるため、入れ替えの後に開いているタブを読み込み直す | `construction/build-and-test/build-and-test-summary.md` の引き継ぎ |
| `main` への取り込み | スモークテストが通った後、依頼者の承認を得て `main` を `develop` の先頭へ fast-forward で取り込む。タグは付けない（`v*` のタグの push で公開のリリースが作られるため、配備先が決まるまで付けない）。`origin` への push は依頼者が行う | `team.md` の Deployment・Way of Working、前の Intent の Q3: A |

## Q1. 前の版へ戻すときの DSL の扱い

前の版は版 2 の DSL を読めないため、戻すと DSL が無い状態で起動します（権限の対象と業務のメニューは前の版にはもともと無いため、影響は DSL の画面と DSL を使う機能だけです）。戻す前の版 2 のプレビューの破棄は決まっています。戻した後に DSL をどうするかを決めます。

A. 設計のとおり、戻した後に前の版の DSL の画面で既定の DSL（版 1）を生成し、プレビューを確かめて適用する（監査に残る）。新しい版へ上げ直すときは、その版 1 がまた使われなくなるため、版 2 を生成し直して適用する。手順書（`rollback-runbook.md`）にこの順を書く
B. 戻した後は DSL を生成し直さず、DSL が無い状態のまま新しい版へ上げ直すのを待つ。戻しの手順は短くなるが、戻している間は DSL を使う機能が使えない
C. 戻すときに、配備の前の複写を戻して版 1 の DSL を含む元の状態にする。DSL は元どおりになるが、配備の後のデータ（ロール・グループ・利用者の変更）と監査の行を失う
X. Other (please specify)

推奨は A です。承認済みの設計（AC6.1.8、`logical-components.md` 7節）のとおりで、戻しの練習（Q3）で同じ手順を確かめられます。代わりに、戻した後と上げ直した後に DSL の操作が2回ずつ要り、監査の行が増えます。

[Answer]: A

## Q2. 配備の後に、適用中の DSL（版 1）を版 2 にする方法

新しい版は版 2 の DSL だけを受け付けるため、今の内部DB に版 1 の DSL が適用されていれば、入れ替えの後は DSL が無い扱いになり、権限の設定の対象と業務のメニューが空になります。今の内部DB に版 1 の適用中の DSL やプレビューがあるかは、配備の前の複写を読み取りで開いて、DSL の表（`dsl_applied_revisions`・`dsl_previews`）の行の数だけを数えて確かめます（中身は表示しない）。

A. 入れ替えの前に、版 1 のプレビューが残っていれば依頼者が画面で破棄する（残すと入れ替えの後に 422 の誤りの一覧になるため）。入れ替えの後に、依頼者が DSL の画面で版 2 の既定の DSL を生成し、プレビューを確かめて適用する。AI は起動のログ（DSL が無い扱いになった旨）と監査の件数（`DSL_PREVIEW_DISCARDED`・`DSL_GENERATED`・`DSL_APPLIED`）で裏付ける
B. 入れ替えの前に、今の版 1 の DSL を依頼者がダウンロードし、手元で版 2 に書き換えて（スキーマの階層とメニューの組を足す）、入れ替えの後に投入・適用する。版 1 に手で直した点があれば保てるが、書き換えの手間と誤りの危険がある
C. 入れ替えの後も DSL は適用し直さず、DSL が無い状態のまま配備を終える。スモークテストで権限の対象と業務のメニューを確かめられない
X. Other (please specify)

推奨は A です。今の適用中の DSL が既定の DSL の生成から作ったもので、手で直した点が無ければ、生成し直すのが最も確かです（承認済みの設計 `logical-components.md` 7節の 1 のとおり）。代わりに、版 1 で手で直した点があれば失われます。手で直した点がある場合は B を選んでください。

[Answer]: A

## Q3. 戻しの練習（U3・U4 の NFR3.6b、G-19、AC6.1.8）の場と合格の条件

Build and Test から `deployment-execution` が持ち主として引き継いだ確かめです。前の版のイメージを、移行 V10〜V12 の後の内部DB の複写と一時のボリュームで起動して確かめます。練習の環境での操作は複写の中の監査にだけ残り、配備した内部DB は汚れません。

A. 配備とスモークテストの後に行う。アプリを止めて配備の後の複写を取り（配備の後のバックアップを兼ねる。約 10 秒の停止）、すぐ起動し直す。複写を一時のボリュームに展開し、前の版のイメージ（`mastersmith:pre-role-menu`）を別の名前のコンテナ・別の番号（例 18080）・`.env` の複写（末尾に `MASTERSMITH_WEB_BASE_URL` を足す）で起動して、次をすべて確かめる。終わったら練習の環境と一時のボリュームを消す
  1. `/actuator/health` の 200 を待つ（イメージに HEALTHCHECK が無いため `running` を健全と読まない）。Flyway が V10〜V12 を無視して起動し、ERROR が 0 件
  2. 版 2 の適用中の DSL が読めず、DSL が無い状態で起動する（ログの件数で）
  3. 依頼者がログインでき、管理の画面が開ける。管理者の印を持つ利用者の数が、戻す直前の配備の後の複写と同じで、管理の API の 200／403 が `admin_flag` のとおり（権限のずれの確かめ）
  4. G-19: `GROUP_*`・`ROLE_*`・`WORK_ROLE_SWITCHED` の監査の行と新しい3列の値がある複写で、起動とログインと管理の操作が失敗しない。前の版のソースに監査の行を実体として読む経路が無いことを再確認する
  5. Q1 の答えの手順（A なら、前の版で既定の DSL（版 1）を生成し、プレビューを経て適用でき、DSL の画面が使える）
B. 入れ替えの前に行う。配備の前の複写に新しい版を一時の環境で起動して V10〜V12 を当て、版 2 の DSL を適用し、その複写で前の版を起動して A の 1〜5 を確かめてから入れ替える。入れ替えの前に戻せることが分かるが、アプリが止まっている時間が長くなり（または一度起動し直す）、配備した後の実際の状態（スモークテストで作ったロール・グループ）での確かめにはならない
C. A と B の両方を行う
X. Other (please specify)

推奨は A です。前の Intent までと同じ形（配備の後のバックアップと練習の複写を1回の停止で取る）で、配備した後の実際の状態で確かめられます。代わりに、練習で問題が見つかったときは、すでに新しい版が動いている状態から戻し方を考えることになります（スキーマは前進のみで新しい版はそのまま動き続けられるため、急いで戻す必要は無い見込み）。

[Answer]: A

## Q4. 配備の前の k6（負荷の試験）

Build and Test では、権限の YAML の上限の測り（`BT-Q1c`、配備と同じ上限の使い捨ての環境で確かめ 1.11〜5.42 秒・適用 2.35〜11.41 秒、OOM なし）だけを行い、k6 の場面と DSL の時間の測りは Performance Validation（この Intent の流れで Deployment Execution の後にある）に回しました（Unverified 20 件）。`project.md` の学びは「配備するものと同じソースのイメージで負荷の試験済みのときは省く」で、今回は全部は試験済みではありません。

A. 配備の前には流さず、Performance Validation に任せる。`project.md` の学びとの差を `cd-config.md` に記録し、配備の後に問題が出たら戻す（前の Intent `260925-user-management` の Q2: B と同じ扱い）
B. 配備の前に、使い捨ての環境（配備と同じ上限）で重い操作の場面だけ（権限の YAML の確かめと適用、10 MiB の版 2 の DSL の投入とプレビュー）を短く流し、OOM・OOMKilled が無いことを確かめてから入れ替える。配備したアプリは動かしたまま（VM の内に収まる見込み）か、収まらなければ止める
C. 配備の前に Performance Validation の k6 の場面をすべて流す（その段の前倒し）
X. Other (please specify)

推奨は A です。重い操作の安全側の確かめ（上限ちょうどで OOM なし）は `BT-Q1c` で済んでおり、ヒープが尽きても `ExitOnOutOfMemoryError` で JVM が止まり動き続けません（Q6）。代わりに、95 パーセンタイルの目標を満たすかは配備の後まで分からず、満たさなかったときに戻すかを後で決めることになります。

[Answer]: A

## Q5. 配備の後の確かめ（スモークテスト）の範囲

健全性と起動のログ（ERROR 0、移行 V10〜V12 の3件が当たった、秘密とメールアドレスそのものがログに無い）、依頼者のログイン、監査の `LOGIN_SUCCEEDED`、Q2 の DSL の操作は、どの選択肢でも行います。ロール・グループの操作を行うかを決めます。ロール・グループの操作は監査に残り（`ROLE_*`・`GROUP_*`・`WORK_ROLE_SWITCHED`・`ROLE_TRANSFER_APPLIED`）、追記だけで消せません。

A. 依頼者が画面で、確かめ用のロールとグループを1つずつ作り、ロールにテーブル1つの読み取りの権限を付けて自分に割り当て、作業ロールを切り替えて、業務のメニューにそのテーブルが出ること・「自分の権限」の画面に出ることを確かめる。グループにメンバーを足し、利用者の管理の画面の詳細にロールが出ることも見る。終わった後に作ったロールとグループを残すか消すかは依頼者が決める。AI は監査の種類ごとの件数を、操作の前に合わせた期待の件数と比べる
B. ロール・グループの操作は行わず、画面が開けること（ロール・グループの管理の画面、権限の設定の木、業務のメニュー、「自分の権限」）と、作業ロールが無い状態の表示だけを確かめる。監査はログインと DSL の操作だけ
C. A に加えて、権限の YAML の書き出しと、同じ YAML の確かめと適用の往復（`ROLE_TRANSFER_APPLIED`）も確かめる
X. Other (please specify)

推奨は A です。権限の設定から割り当て・作業ロール・業務のメニューまでのつながりを、配備した環境で1回通して確かめられ、Q3 の練習の複写にロールとグループの行が入るため G-19 の確かめにもなります。代わりに、監査に確かめの行が残ります。

[Answer]: A

## Q6. `ExitOnOutOfMemoryError` を足したことによる運用の差

新しい版はヒープが尽きると JVM が止まり（Build and Test では終了コード 3・OOMKilled false）、`compose.yaml` の `restart: "no"` のため起き直しません。

A. `restart: "no"` のまま、手順書（`rollback-runbook.md` と `cd-config.md`）に「アプリが止まったとき」の手順を書く。`docker compose --profile targetdb-postgres ps -a app` で止まったことを確かめ、`docker inspect` の終了コードと OOMKilled、ログの `OutOfMemoryError` の行の件数と例外のクラスの名前だけを見て、直前の操作（権限の YAML・DSL の投入など）を依頼者に確かめて記録し、`docker compose start app` で起動し直して healthy とログインを確かめる。同じ入力で繰り返し止まるときは、戻しても直らない（入力の大きさ・形の問題）ため、戻すかを依頼者に諮る。配備のときには、動いているコンテナの PID 1 のコマンド行に `-XX:+ExitOnOutOfMemoryError` があることを確かめる
B. A に加えて、`compose.yaml` の `app` を `restart: on-failure` にして自動で起き直すようにする。止まっている時間は短くなるが、`compose.yaml` の変更（統合の前の検査のやり直し）が要り、Build and Test の Q2: A の前提（起き直さず原因を見てから起動し直す）と違い、同じ入力での止まりの繰り返しに気づきにくい
C. 手順書には書かず、README の `Dockerfile` の説明（起き直さない旨）だけに任せる
X. Other (please specify)

推奨は A です。Build and Test の Q2: A の決定（中途半端な状態で動き続けず、原因を見てから起動し直す）のとおりで、設定の変更が要りません。代わりに、止まったときは開発者が気づいて起動し直すまでアプリが止まったままです。

[Answer]: A

## Sources

- 段の定義: `.claude/aidlc-common/stages/operation/deployment-pipeline.md`
- 前の Intent の手順: `aidlc/spaces/default/intents/261004-safety-carryover/operation/deployment-pipeline/deployment-pipeline-questions.md`・`cd-config.md`・`rollback-runbook.md`、`aidlc/spaces/default/intents/261004-safety-carryover/operation/deployment-execution/deployment-log.md`
- この Intent の基盤の設計: `construction/dsl-v2/infrastructure-design/cicd-pipeline.md`（8節・9節）、`construction/group/infrastructure-design/cicd-pipeline.md`（3節・8節）・`infrastructure-specification.md`（4節）、`construction/role/infrastructure-design/cicd-pipeline.md`（3節・9節）・`infrastructure-specification.md`（4節）
- この Intent の NFR 設計: `construction/dsl-v2/nfr-design/logical-components.md`（7節）、`construction/role/nfr-design/reliability-design.md`（5節）
- この Intent の CI と Build and Test: `construction/ci-pipeline/ci-config.md`（2.2・6節）・`quality-gates.md`、`construction/build-and-test/build-and-test-summary.md`（`BT-Q1c`・`BT-Q2`・`U3-NFR3.6b`・`U4-NFR3.6b`・引き継ぎ）・`test-results.md`・`build-instructions.md`・`build-and-test-questions.md`（Q2・Q4）・`cross-unit-traceability.md`（AC6.1.8）・`security-test-instructions.md`
- G-19: `construction/group/code-generation/code-summary.md`（4節・8節）と、そのレビュー（R-01）
- 要件とストーリー: `inception/requirements-analysis/requirements.md`（C1・FR1.3・FR10.3）、`inception/user-stories/stories.md`（US6.1、AC6.1.6〜AC6.1.8）
- リポジトリ（読み取りだけ）: `Dockerfile`、`compose.yaml`（`restart: "no"`）、`.env.example`、`backend/src/main/resources/application.yaml`、`backend/src/main/resources/db/migration/`（V10〜V12）、`backend/src/main/java/cherry/mastersmith/audit/domain/` の監査の種類、README（「コンテナでの起動と確認」「戻し方」「内部DBのバックアップと戻し方」「スキーマの変更（Flyway）」）、`git diff 273c021 HEAD`・`git diff 005730a HEAD`・`git grep`（`273c021` の `findAllByOrderByOccurredAtAsc`）
- 実行環境（読み取りだけ、2026-10-14）: `docker ps`、`docker images`、`docker image inspect mastersmith:local`、`colima list`
- 決まり: `aidlc/spaces/default/memory/team.md`（Way of Working・Testing Posture・Deployment）、`aidlc/spaces/default/memory/project.md`（Deployment・Testing Posture・Change Control・Corrections の学び）

## Consolidated Summary Confirmation

答えのまとめ: Q1 A（戻した後に前の版で既定の DSL（版 1）を生成して適用する）、Q2 A（入れ替えの前に版 1 のプレビューを破棄し、入れ替えの後に版 2 の既定の DSL を生成して適用する。版 1 に手で直した点は無い前提）、Q3 A（配備とスモークテストの後に、配備の後の複写で前の版を別の名前のコンテナ・別の番号で起動して、起動・DSL が無い状態・管理の 200／403・G-19・Q1 の手順を確かめる。配備したアプリは止めない）、Q4 A（配備の前の k6 は流さず Performance Validation に任せ、project.md の「試験済みのときだけ省く」との差を記録する）、Q5 A（確かめ用のロールとグループで、権限・割り当て・作業ロール・業務のメニュー・自分の権限の画面までを1回通し、監査は種類ごとの件数を前もって合わせた期待と比べる）、Q6 A（restart は no のまま、止まったときの手順を書き、配備のときに設定が効いていることを確かめる）。この内容で進めてよいか。

- Looks correct
- Request changes

[Answer]: Looks correct
