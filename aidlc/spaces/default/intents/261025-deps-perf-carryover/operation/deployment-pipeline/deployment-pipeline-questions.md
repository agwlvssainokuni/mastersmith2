# Deployment Pipeline の質問（Intent 261025-deps-perf-carryover）

この Intent（依存の更新、小さな直し、読み取りのプール 10、Spring Boot 4.1.2。Bolt B1〜B5）の配備の流れを決めるための質問です。前の Intent（`261016-business-data-views`・`261015-deps-ui-carryover`）の配備の手順と、既にある `Dockerfile`・`compose.yaml`・README を正とし、今回の差で判断が分かれる点だけを尋ねます（`project.md` の Deployment の学び）。

## 決まっていること（質問にしない）

| 項目 | 決まっていること | 根拠 |
|---|---|---|
| 配備先と配備の仕方 | 開発者の PC 上のコンテナ（`compose.yaml`、プロジェクト `mastersmith`）。自動の配備は持たず、依頼者の承認のうえで手で配備する。方式は1台の置き換え。本番配備の承認者は依頼者 | `team.md`・`project.md` の Deployment |
| 元の手順 | 前の Intent `261016-business-data-views` の手順（`aidlc/spaces/default/intents/261016-business-data-views/operation/deployment-pipeline/`）を正とし、今回の差だけを書く | `project.md` の Deployment の学び |
| 今動いている版 | `mastersmith-app-1` がイメージ `mastersmith:local`（`sha256:4cec01e79026…`、WAR の SHA-256 は 6426111d…）で healthy（Build and Test の後で `docker compose start app` により戻し済み）。`main` の先頭は `bdba082`。Mailpit は v1.31.5 のコンテナ、otel-lgtm は 0.34.0 のコンテナが 11 日前から停止中（どちらも `compose.yaml` の版と違う） | `docker ps`・`docker image ls`（読み取り）、Build and Test の `test-results.md` 9.8 |
| 配備する版 | `develop` の先頭（`bb06dc9`）。`main` からの差は、`aidlc/` の外を変えたのが B1〜B5 の 5 コミット（`98ef3e5`・`3292282`・`3bd1f4c`・`5bebcab`・`9bf6b70`）。アプリのコードの変更は Spring Boot 4.1.2、読み取りのプールの既定 10、時間切れ後の接続の破棄、符号なしの型名、`GET /api/me/table-access` の削除、パスワード変更・表示の設定の成功の知らせ、DSL の説明文、画面の依存（Playwright 以外の npm、vite 8.3.5、react-router 8.5.0） | `git diff --stat main develop`、`code-summary.md`、`test-results.md` 10.2 |
| 内部DB のスキーマ | **変更なし**（移行ファイルの差は 0。最後は V13）。前進のみ・後方互換のまま。H2 の版も変わらない | `git diff --name-only main develop -- backend/src/main/resources/db/migration`（空）、`gradle/libs.versions.toml` の差 |
| `.env` | **変更なし**。今の `.env` に `MASTERSMITH_TARGET_DB_POOL_MAXIMUM_SIZE` の項目は無く（項目の名前だけを数えた。値は開いていない）、読み取りのプールの既定の変更（5 → 10）が `.env` を変えなくても効く | 項目の名前の確認、要件 FR5.1 |
| 戻し方 | 直前の版のイメージの再配備（前の版でもプール 5 で動く）。入れ替えの前に今のイメージに `mastersmith:pre-deps-perf-carryover` を付ける。スキーマと `.env` の変更が無いため、配備の前の内部DB の複写と戻しの練習は行わない（前の Intent `260928-quality-followup` と同じ扱い。戻した版のイメージが起動することは、一時のボリュームで確かめる手順を `rollback-runbook.md` に置く） | 要件 FR5.1・NFR4、`project.md` の Deployment の学び |
| 配備の前の検査 | verify・osvScan・E2E 256 件は `bb06dc9` の中身（アプリのコードは `9bf6b70`）で Build and Test に通っており、CI も `9bf6b70` で 1 回目の試行で成功。配備の前に流し直さない | `test-results.md` 10 節 |
| 配備の前の k6 | 流さない。負荷の試験（`mastersmith:carryover`、WAR 80b19c53…）は Build and Test で流し済み。配備する WAR は B5（vite・react-router の更新）の分だけ画面の成果物が違い、バックエンドのコードは同じ。差を `cd-config.md` に記録する（`project.md` の「同じソースのイメージで試験済みのときだけ省く」との差） | `test-results.md` 9節・10.2 |
| WAR | `Dockerfile` は手元の WAR を写すだけのため、配備の前に `./gradlew :backend:bootWar --rerun-tasks` で作り直し、SHA-256 と中身（画面のビルド、読み取りのプールの既定 10）を確かめる | `project.md` の Deployment の学び |
| 救済の見込み | 前の手順のとおり、入れ替えの前に依頼者へ、初期管理者のパスワードを画面で変えた・止めた・印を外したかを尋ねる。救済が働くと初期管理者のパスワードが `.env` の値に戻る | 前の手順の順 4 |
| 監査に残る操作の扱い | スモークテストで監査に残るもの（ログイン・業務データの作成・更新・削除など）は、行う前に依頼者に伝え、期待の件数をその場で合わせる。値（メールアドレス・パスワード・業務データの値・主キーの値）は表示しない | `project.md` の Deployment・Corrections の学び |
| `main` への取り込み | 取り込みの前に、前の取り込み（`bdba082`）の後のすべてのコミットの CI を試行ごとに確かめ、再実行で通ったものは1回目の失敗とそのテストを記録する。依頼者の承認を得て fast-forward で取り込む。タグは付けない。`origin` への push は依頼者が行う | `team.md`、要件 FR5.3 |
| 見本の PG の状態 | Build and Test の E2E のために、E2E の上乗せ（`docker/e2e/compose.targetdb-port.yaml`）で作り直したまま、`127.0.0.1:15432` が開いている（ボリュームは引き継いでいる） | `docker ps` |

## Q1. 見本の PostgreSQL の開いている番号 `127.0.0.1:15432` をどうするか

E2E の確かめが終わったため、普段の形（PC に番号を開けない）に戻せます。戻すには、上乗せ無しで見本の PG のコンテナを作り直します（ボリュームは残る）。作り直す間は、アプリの業務データの読み取りが数秒失敗します。

A. 入れ替えの停止の間に、上乗せ無しで見本の PG を作り直して番号を閉じる。アプリが止まっている間なので、アプリの接続が途中で切れることは無い
B. 今の状態のまま残す（番号を開けたまま）。次に E2E を流すときに作り直さずに済むが、PC の外には出ない（127.0.0.1）ものの、普段の形とは違う状態が続く
X. Other (please specify)

[Answer]: A

## Q2. Mailpit と otel-lgtm のコンテナを、`compose.yaml` の版に合わせるか

`compose.yaml` の版は Mailpit v1.32.0、otel-lgtm 0.37.0。動いている Mailpit は v1.31.5、otel-lgtm は 0.34.0 の止まったコンテナです。Mailpit はメールを持たない作り（ボリュームなし）で、作り直すと受けたメールが消えます。otel-lgtm は常に動かしていません。

A. 今回は合わせない。アプリの配備と関係せず、次に使う（E2E・監視の確かめ）ときに、`docker compose --profile mail up -d mailpit` などで作り直すと新しい版になる。差を `cd-config.md` に記録する
B. Mailpit だけ、入れ替えの停止の間に v1.32.0 で作り直す（受けたメールは消える）。E2E の前提の版がそろう
C. Mailpit と otel-lgtm の両方を作り直す（otel-lgtm は約 3.4 GB を引き、VM のメモリの余裕を食う）
X. Other (please specify)

[Answer]: A

## Q3. スモークテストの範囲

今回は画面と API の形がほとんど変わらず（成功の知らせ・`table-access` の削除・DSL の説明文）、Spring Boot 4.1.2 とプール 10 の起動確認が中心です。前の Intent は、ロールを作る・権限を保存する・割り当てるまで含む S1〜S13 でした。

A. 小さな範囲: 健全性と起動のログ（S1・S2）、ログイン、業務のメニューから既存の業務データの一覧・詳細、パスワード変更と表示の設定の成功の知らせ（C5）、`/api/me/table-access` が 404 であること、プールの設定（`hikaricp.connections.max` が 10）。ロールは作らず、初期管理者の今の割り当てを使う。監査の確かめは配備の後の複写で数える
B. 前の Intent と同じ範囲（S1〜S13）: ロール 2 つを作って権限を保存し、割り当て、作業ロールの切り替え、業務データの作成・更新・削除、ビューまで確かめる。回数が多く、依頼者の操作と監査の期待の件数の合わせが増える
X. Other (please specify)

[Answer]: A

## Q4. `main` を取り込む時期と、WAR の作り直しの元

要件 FR5.2 は「`main` の先頭から WAR を作り直して配備し、配備した版と `main` をそろえる（A4）」と書きます。一方で、`main` へ取り込むのは「スモークテストが通った後」が前の Intent までの順でした。

A. 前の Intent と同じ順: `develop` の先頭から WAR を作り直して配備し、スモークテストが通った後に `main` を `develop` の先頭へ fast-forward で取り込む。取り込みの前に、WAR を作ったコミットと `main` の先頭の差が `aidlc/` だけであることを確かめて記録する（アプリのコードは同じなので、配備した版と `main` はそろう）。配備が通らなかったときに `main` を巻き戻さずに済む
B. 先に `main` を fast-forward で取り込み（CI の確かめと依頼者の承認の後）、`main` の先頭から WAR を作り直して配備する。要件の文のとおりだが、配備が通らなかったときは取り込み済みの `main` が配備されていない状態になる
X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

- Q1（A）: 入れ替えの停止の間に、上乗せ無しで見本の PG を作り直し、`127.0.0.1:15432` を閉じる。
- Q2（A）: Mailpit と otel-lgtm は今回は作り直さず、`compose.yaml` の版との差を `cd-config.md` に記録する。
- Q3（A）: スモークテストは小さな範囲（健全性・起動のログ・ログイン・既存の業務データの一覧と詳細・成功の知らせ・`table-access` の 404・プール 10 の確認）。ロールは作らない。
- Q4（A）: `develop` の先頭から WAR を作り直して配備し、スモークテストの後に `main` を fast-forward で取り込む。WAR を作ったコミットと `main` の先頭の差が `aidlc/` だけであることを確かめて記録する。
- 決まっていること: スキーマと `.env` の変更なし、戻しはイメージだけで練習と配備の前の複写は行わない、配備の前の k6 は流さない（差を記録）、救済の見込みを依頼者に尋ねる、`main` の取り込みの前に CI を試行ごとに確かめる。

Does this all look correct before I generate the artifacts?

- Looks correct
- Request changes

[Answer]: Looks correct
