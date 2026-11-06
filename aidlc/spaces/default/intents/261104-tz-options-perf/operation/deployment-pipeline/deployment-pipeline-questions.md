# Deployment Pipeline の質問（Intent 261104-tz-options-perf）

この Intent は bugfix です。表示のタイムゾーンの選択肢の組み立てを、Temporal を使って速くしました。変えたのは画面の側の純粋な関数とそのテストだけです（`develop` の 985b7e6c）。この質問は、その配備の流れを決めるためのものです。

前の配備の記録（Intent `261101-maintenance-update` の `cd-config.md`・`deployment-strategy.md`・`rollback-runbook.md`）と、既にある `Dockerfile`・`compose.yaml`・`docs/deployment.md` を正とします。質問は、今回の差で判断が分かれる点だけです（`project.md` の Deployment の学び）。CI Pipeline と Infrastructure Design の段はこの Intent の流れに無いため、上流は Build and Test の記録とリポジトリです。

## 調べて分かったこと（判断の前提）

- **前の Intent `261102-timezone-ui-polish` は配備していません。** あの Intent の範囲（classic）は、配備の段（4.1〜4.7）を初めから飛ばす形でした。そのため `main`（`f388fb8d`、261101 の配備）から `develop` の先頭まで 49 コミットあり、261102 の変更（時刻の画面・表示のタイムゾーン・内部DB の移行 V14・アイコンと favicon・make-you-chic-ui の固定先の更新など）がまだ配備されていません。
- この Intent の直しは 261102 の作った部品（`src/shared/format/timeZone/`）の上にあるため、この Intent だけを分けて配備することはできません。
- 261102 の記録は、次の確かめを配備の段に回しています。
  - 1つ前の版のアプリを V14 の後の内部DB で起動する確かめ（戻しの練習）。`docs/deployment.md` の V14 の項。
  - Firefox・Safari での画面の確かめ（Unverified。持ち主は Deployment Execution のスモークテスト）。261102 の U1 の `infrastructure-design/cicd-pipeline.md`。
- この Intent の Build and Test でも、Safari など Temporal の無いブラウザの道（Intl の道）は jsdom の単体テストでしか確かめていません。
- この PC には Chrome・Firefox・Safari が入っています。

## 決まっていること（質問にしない）

| 項目 | 決まっていること | 根拠 |
|---|---|---|
| 配備先と配備の仕方 | 開発者の PC 上のコンテナ（`compose.yaml`、プロジェクト `mastersmith`）。自動の配備は持たず、依頼者の承認のうえで手で配備する。1つのコンテナを入れ替える形 | `team.md`・`project.md` の Deployment |
| 今動いている版 | `mastersmith-app-1` がイメージ `mastersmith:local`（`ffd2b8380460`、261101 で配備した版）で healthy。見本の PG（`postgres:18.6`）と Mailpit（`v1.33.0`）が動いている | `docker ps`・`docker inspect`（読み取り） |
| 内部DB の移行 | V14 が1つ（`users` に空を許す列 `display_time_zone` を足すだけ。前進のみ・後方互換） | `V14__u2_display_time_zone.sql`・`docs/deployment.md` |
| `.env`・Dockerfile・compose | 変えない（`main..develop` の差 0） | `git diff --stat main..develop` |
| 監査の種類 | 表示のタイムゾーンの保存は監査に残らない（`AuditEventType` に当たる種類が無い） | `AuditEventType.java` |
| WAR | `develop` の先頭から作り直し（`./gradlew :backend:bootWar --rerun-tasks`）、SHA-256 と中身（V14 の移行・`offsetLabel` の入った `dist`）を確かめる。Dockerfile は手元の WAR を写すだけのため | `project.md` の学び（WAR の確かめ） |
| 入れ替え | 入れ替えの前に今のイメージ（`ffd2b8380460`）に戻し先のタグ `mastersmith:pre-tz-options-perf` を付ける。`docker compose up -d --build app` の後、`docker inspect` でコンテナのイメージ ID と `mastersmith:local` の ID の一致を確かめ、食い違えば `--force-recreate --no-deps` で作り直す | `project.md` の学び |
| 配備の前の検査 | verify・E2E・CI は Build and Test の結果を正とし、流し直さない（verify 通過・E2E 298 件 passed・前の配備の後の CI はすべて success。前の配備の後のすべてのコミットの CI の試行は Deployment Execution で確かめ直す） | `test-results.md` |
| 配備の前の k6 | 流さない。261102 の Build and Test が 19 場面と MySQL・MariaDB の追加の回を流して満たし、この Intent の差は画面の側の関数だけ | `project.md` の学び（試験済みのときは省く） |
| `main` への取り込み | 前の取り込み（`f388fb8d`）の後のすべてのコミットの CI を試行ごとに確かめてから、依頼者の承認を得て fast-forward で行う。タグは付けない。`git push` は依頼者が行う | `team.md`・`project.md` |
| 秘密・個人に関する値 | `.env` の値、初期管理者のメールアドレス、業務データの行の値を表示しない。内部DB の複写はリポジトリの外（`~/.mastersmith-backup/`、置き場 700・ファイル 600） | `project.md` の Forbidden と学び |

## Q1. 配備する範囲

**推奨: A。** 理由:

- この Intent の直しは 261102 の上にあるため、配備すれば 261102 も一緒に入ります。
- 261102 が配備の段に回した確かめ（戻しの練習・Firefox・Safari）を、この Intent の配備の段で引き受けられます。
- B では、まだ配備されていない変更（V14 の移行を含む 49 コミット）がさらに積み重なります。

A. `develop` の先頭を配備し、261102 の配備もこの Intent で引き受ける。261102 が回した確かめ（Q2・Q3）も行い、引き受けたことを `cd-config.md` に書く
B. この Intent では配備しない（Deployment Execution を飛ばす形にする）。261102 とこの Intent の配備を、後の Intent にまとめて持ち越す
X. Other (please specify)

[Answer]: A

## Q2. 内部DB の複写と戻しの練習（V14）

V14 は列を足すだけで、前の版のアプリはその列を読み書きしません。そのため、戻すときはイメージだけを戻します（`.env` は変えない、列は残る）。`DisplayTimeZoneMigrationCompatibilityIT` は、V1〜V13 だけを知る Flyway が V14 の後の内部DB で止まらないことを確かめています。ただし、前の版のアプリ全体を V14 の後の内部DB で起動する確かめは、まだ誰もしていません。

**推奨: A。** 理由:

- 戻せることを、実物のイメージと実物の内部DB の写しで一度確かめられます。
- 練習は、配備の後の内部DB の複写を使う使い捨てのコンテナで行います。動いているアプリと内部DB には触れません。
- 複写は、移行の前のバックアップも兼ねます。

A. 配備の前（入れ替えの停止の間）と配備の後（スモークテストの後）の2回、内部DB を複写する。配備の後の複写で、前の版のイメージ（`mastersmith:pre-tz-options-perf`）を使い捨てのコンテナで起動し、healthy と起動のログ（ERROR 0・Flyway の誤り 0）を確かめる。使い捨てのコンテナと練習用の複写は、終わったら消す
B. 複写は配備の前の1回だけ（バックアップ）。戻しの練習はせず、結合テストで確かめ済みとして差を `rollback-runbook.md` に書く
C. 複写を2回取るが、戻しの練習はしない
X. Other (please specify)

[Answer]: A

## Q3. スモークテストのブラウザ

Chrome・Edge は Temporal を持つため、速い道（Temporal の道）を通ります。Firefox は版によって違い、Safari は Temporal を持たないため Intl の道を通ります。

**推奨: A。** 理由:

- この Intent の Intl の道と、261102 が回した Firefox・Safari の確かめを、一度に済ませられます。
- 保存は監査に残らないため、ブラウザを増やしても監査の件数の期待は変わりません。

A. Chrome で全部の流れを確かめ、Firefox と Safari でも表示のタイムゾーンの選択肢（一覧が出る・時差の表記・保存して時刻の表示が変わる）を確かめる
B. Chrome だけで確かめる。Firefox・Safari は Unverified のまま、次の機会を `cd-config.md` に書く
X. Other (please specify)

[Answer]: A

## Q4. 古いイメージの片付け

`mastersmith` のタグに、`pre-maintenance-update`（`d8a467b60f6f`。新しい戻し先 `pre-tz-options-perf` より古い）と `perf-tz-bt`（`3a3fd38094b8`。261102 の Build and Test の k6 だけに使った）があります。

**推奨: A。** 理由:

- どちらも、新しい戻し先より古いか、試験だけに使ったイメージです。
- 片付けは配備の後に行い、使うコンテナが 0 件であることを確かめ、消す一覧の承認を得てから消します。

A. この2つを片付けの候補にし、Deployment Execution の終わりに一覧を示して承認を得てから消す
B. 片付けない（今回は残す）
X. Other (please specify)

[Answer]: A

## 追加の質問の前に分かったこと

Q1 の答え（261102 の配備を引き受ける）の後に、261102 の各単位の設計の記録（`construction/u2-display-time-zone/` から `u6-app-icon-assets/` までの `infrastructure-design/cicd-pipeline.md` と、U6 の `nfr-design/security-design.md` の SD4.3）を読みました。どの単位も、配備の後のスモークテストの項目を書き、手順は Deployment Pipeline で書くとしています。

- U2（表示のタイムゾーン）S1〜S8: V14 の当たり・選択の表示・保存と管理の画面の時差・保存が監査に残らないこと・片付けで「ブラウザに合わせる」に戻す。
- U3（対象DB の日時）: 見本の PG の日時の列の値と端数の桁、既定の DSL の生成（監査に残る `DSL_GENERATED`。確かめた後にプレビューを破棄する `DSL_PREVIEW_DISCARDED`）。
- U4（業務データの時刻）S1〜S7: `sales.customers` の `created_at` の一覧・詳細・作成と更新の欄・検索の範囲。作成・更新は目印つきの行で行い、終わりに削除する（`BUSINESS_ROW_CREATED`・`UPDATED`・`DELETED`）。S7 は手元の監視（lgtm）のダッシュボードで 5xx を見る。
- U5（メニューのアイコンとホーム）S1〜S7: ホームのカード・サイドバーのアイコン・ロゴ・`/admin` が「見つからない」。S6 は管理者でない利用者（用意できるとき）。S7 は lgtm。
- U6（アプリのアイコン）S1〜S6: `/favicon.ico`・`/favicon.svg` の未認証の 200・`Content-Type`・`nosniff`・先頭のバイト、ブラウザのタブの favicon、テーマで色が追従すること。
- 監視の設定（`docker/`）は `main..develop` で変わっていません。U2 の記録には、colima の VM のメモリの余裕が約 100 MiB で、戻しの練習の間は lgtm を起動しないとあります。

## F1. スモークテストの範囲

**推奨: A。** 理由:

- 各単位の設計で決めた確かめで、配備した版で初めて見られるもの（実物の内部DB の V14・実物の見本の PG・実際のブラウザ）です。
- 書き込みは目印つきの確かめ用の行だけで、終わりに削除します。前からある見本の行は変えません。
- DSL の生成は、確かめた後にプレビューを破棄し、適用はしません。

A. U2〜U6 の設計の確かめをすべて入れる（監視の区画は F2 で決める）。監査に残る操作は、ログイン・DSL の生成と破棄・業務データの作成・更新・削除の各1件。U5 の S6（管理者でない利用者）は、そういう利用者がいるときだけ行う
B. 読み取りと表示の確かめだけにする（U4 の作成・更新・削除と U3 の DSL の生成を省く）。監査に残るのはログインだけ。省いた確かめは結合テストと E2E で確かめ済みとし、差を `cd-config.md` に書く
X. Other (please specify)

[Answer]: A

## F2. 監視の区画（U4・U5 の S7）

**推奨: A。** 理由:

- この Intent には Observability Setup の段が無く、監視の設定も変わっていません。
- 5xx と ERROR は、アプリのログを同じ時間の範囲で数えれば同じことが分かります。
- lgtm を起動しないため、VM のメモリの余裕が少ない点にも触れません。

A. lgtm を起動せず、スモークテストの間のアプリのログで、業務データとメニューの API の 5xx と ERROR を数えて代える。差を `cd-config.md` に書く
B. lgtm を起動して、ダッシュボードの「業務データ」「業務のメニューと置き場」の区画で 5xx を見る（戻しの練習の前に止める）
X. Other (please specify)

[Answer]: A

## Sources

- `git log`・`git diff --stat main..develop`、`docker ps`・`docker images`・`docker inspect`（読み取り）
- `aidlc/spaces/default/intents/261102-timezone-ui-polish/aidlc-state.md`（範囲と飛ばした段）
- `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u1-time-zone-conversion/infrastructure-design/cicd-pipeline.md`
- `aidlc/spaces/default/intents/261101-maintenance-update/operation/deployment-pipeline/`
- `docs/deployment.md`・`backend/src/main/resources/db/migration/V14__u2_display_time_zone.sql`・`backend/src/main/java/cherry/mastersmith/audit/domain/AuditEventType.java`
- `construction/build-and-test/test-results.md`・`build-and-test-summary.md`
