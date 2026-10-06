# 基盤の設計の質問 — U3 group

単位 U3 group（kind: service。B3 で作る）の基盤の設計の前に、決まっていない点を確かめます。作る成果物は、段の定義の `produces_kinds` により `infrastructure-specification.md`・`monitoring-design.md`・`cicd-pipeline.md`・`traceability.json` の4つです。

読んだもの:

- この単位の承認済みの NFR 設計の7つ（`nfr-design/performance-design.md`・`security-design.md`・`scalability-design.md`・`reliability-design.md`・`observability-design.md`・`logical-components.md`・`traceability.json`）と、各ファイルの末尾の「承認の場の決定と直し」
- NFR 要件（`nfr-requirements/`）、機能設計（`functional-design/functional-spec.md`・`entities.md`）
- `inception/domain-design/components.md`、`inception/contract-design/contract-summary.md`（C1・C4・C6・C10）、`inception/delivery-planning/bolt-plan.md`（B3）
- 既存のもの（読み取りだけ）: `.github/workflows/ci.yml`、`backend/build.gradle.kts`、`backend/src/main/resources/db/migration/`（V1〜V9）、`application.yaml`（HikariCP・Flyway・`slo` のバケット・ログ）、`compose.yaml`、`docker/perf/compose.yaml`、`docker/monitoring/`（警報の決まり 16 件とダッシュボード）、`perf/README.md`・`perf/k6/scenarios.js`
- `team.md`・`project.md`

## 決まっていること（質問にしない）

### 配備と基盤の範囲

- 配備先が決まるまで、基盤の設計は開発者の PC 上のコンテナの範囲に限ります。クラウドの基盤（IaC・検証環境・警報の通知の先）は作りません（`project.md` の Deployment）。
- 次のものは変えません。
  - 配備の形: 1つのアプリのコンテナ、組み込みの H2、単一のインスタンス
  - `compose.yaml`・`Dockerfile`・`.env.example`
  - `application.yaml`（接続プールの上限の既定 30・借りる待ち 5000 ms、`org.hibernate.orm.jdbc.error: OFF`、Flyway の設定）
- 新しい依存・秘密・外への接続は足しません。グループの操作は内部DB だけを使います（`logical-components.md` 3節、`tech-stack-decisions.md`）。

### 内部DB の移行・戻し

- 移行は「コード生成の時点の次の空き番号」とし、V10 と固定しません（要件 NFR3.6、`tech-stack-decisions.md`）。
  - 今の最後は `V9__u1_user_suspension.sql` です。先に作る B1（dsl-v2）と B2（cross-cutting）は移行を足さないため、今の見込みは V10 です。
  - 名前は既存の形 `V<番号>__u<単位の番号>_<内容>.sql`（例 `V10__u3_group.sql`）に合わせます。
  - U4 role の移行はその後の空き番号になります。
- 中身は次の3つを足すだけで、既存の列と行は変えません（`reliability-design.md` 3節、`logical-components.md` 5節）。
  - `groups` の表
  - `group_members` の表と `user_id` の索引
  - `audit_events` の3列
- 前進のみで、広げるだけの変更です（`team.md` の Deployment）。前の版のアプリは足した表と列を見ないため、同じ内部DB で動きます。理由は2つです。
  - `ddl-auto: validate` は余分な表と列を許します。
  - Flyway は、前の版が知らない移行を既定で無視します。
- 戻しは直前の版のイメージだけで行います。グループのデータは残りますが、前の版にはグループとロールの機能が無いため使われません（`reliability-requirements.md` の「障害と戻し」）。
- **配備の段への引き継ぎ**（中身は deployment-pipeline・deployment-execution の段で決める）: スキーマの変更があるため、前の Intent の手順に合わせて、次の2つを手順に入れます（`project.md` の学び、前の Intent の配備の記録）。
  - 入れ替えの前にアプリを止めて内部DB を複写する（バックアップを兼ねる）。
  - 戻しの練習として、前の版のイメージを複写した内部DB（V10 の後）で起動し、ヘルスチェックが通ることを確かめる。
  - この Intent は B1〜B9 をまとめて配備する見込みのため、U4 の移行と合わせて1回で扱います。

### CI と1コマンドの検査（既にあるものの記録）

- CI（`ci.yml`）・Gradle のタスク・`packagesJudgedByTotal` は変えません（`project.md` の学び）。
  - 本体に手が入るパッケージは、`group.*`（新しい。`group.store` を含む）・`audit.domain`・`audit.service`・`user.domain`・`user.service`・`useradmin.domain` です。どれも一覧に無く、パッケージごとの下限がそのまま当たります（`logical-components.md` 1節）。
- 足すテストは既存の振り分けに入ります（`logical-components.md` 5節の 18 件）。
  - `*Test`（`GroupStoreClassificationTest`・`GroupNameProperties`・`GroupBoundaryArchitectureTest` など）は段 5。
  - `*IT`（`GroupConnectionUsageIT`・`GroupConcurrencyIT`・`GroupBusyLogIT`・`GroupAdminMetricsIT` など）は段 6。組み込みの H2 だけを使い、コンテナは使いません。
  - 同時の重なりのテストは、4つの点の待ち合わせの口（`GroupBarrier`）で時間に頼らずに作ります。上限切れのテストは H2 の上限（行 3 秒・一意の鍵 約 2 秒）まで待つため、`verify` の時間が延びます。延びた分は Build and Test で `:backend:cleanTest :backend:cleanIntegrationTest` を付けて実測して記録します。
- 統合は B3 の短命のブランチから `develop` へ squash で戻します。
- B3 は画面に手を入れませんが、認可（`ApiAccess(ADMIN)`）と、利用者の管理の code の移し替え（`USER_NOT_FOUND`。`useradmin.domain`・`user.domain`）に手が入ります。そのため、統合の前に手元で `./gradlew e2eTest` を流し、利用者の管理の既存の E2E（110・120・130）を含む全体が通ることを確かめます。

### 負荷の試験（k6）と使い捨ての環境

- 台本は B3 のコード生成が書きます。`perf/k6/scenarios.js` に7つの場面と `groupPoolLimit` を足します。`groupListFirst` と `groupListLast` を分けて数えると8つです（`performance-design.md` 2節、`scalability-design.md` 2.2）。データの用意の手順は `perf/README.md` に書きます。
- 流すのは Performance Validation で、使い捨ての環境（`docker/perf/compose.yaml`）で行います。配備した環境のデータと監査ログには触れません（`project.md` の学び）。
- 接続プールの合否の回と記録の回の値は、使い捨ての環境の一時の `app.env` に足して渡します。配備したアプリの公開の範囲は変えません（`scalability-design.md` 2.2、`project.md` の学び）。
  - 合否の回は `MASTERSMITH_DB_MAXIMUM_POOL_SIZE=11`、記録の回は `10`。
  - `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics`。
  - `docker/perf/compose.yaml` は変えません。
- データの用意:
  - 試験用の利用者（詳細の 1,000 人と、メンバーの追加・外しの分）は、`perf/README.md` の既存の手順 2（アプリを止めて SQL で入れる）の人数を広げて入れます。利用者を作る API は招待とメールの流れのため、試験のデータに向きません。
  - グループとメンバーは、承認済みのとおり場面ごとの `setup()` で API を通して作ります（`performance-design.md` 2.1）。
  - `setup()` の要求の数が多い場面（グループ 1,000・メンバー 1,000 人）は、k6 の `setupTimeout`（既定 60 秒）を広げます。台本を書いた後に `k6 inspect --include-system-env-vars` で確かめます。
- 判定は `iteration_duration` の p95 と `checks` の率で行います。長い試験は台本全体を `caffeinate -i` で包みます（`project.md` の学び）。

### 監視・ログ・SLO

- 独自の指標は足しません。グループの API は既存の `http.server.requests` に、道の型の `uri` で入ります。`slo` のバケット（100・250・500・1000・2000・5000 ms）があるため、p95 を計算できます（`observability-design.md` 2節）。
- ログは `GROUP_BUSY` の WARN だけを足します（`RowLockFailures.warn`、キーは3つ）。ログの収集の設定は変えません。
- SLO（グループの管理の API の p95 1 秒）は、手元の監視を常に動かしていない間は `Unverified` とします。Performance Validation と配備の後の値を、基準の値として記録します（`project.md` の学び）。
- 警報・ダッシュボードの式は、書く前に起動して指標とラベルの名前を確かめます。書いた後は、要求を指標の送信の周期（1 分）を複数またいで送ってから、すべての式を流します（`project.md` の学び）。持ち主は Observability Setup です。

## Q1 グループの API の応答時間を、監視でどう見るか（既存の警報の範囲の食い違い）

### 背景

承認済みの `observability-design.md` 4節は、次のように書いています。

- 「新しい警報とダッシュボードは足さない。既存の決まり（5xx の率・p95）が `uri` のラベルでグループの API にも効く」
- Observability Setup への引き継ぎとして、「グループの API の p95 1 秒を拾う既存の警報の式を名指しする」

既存の警報の決まり（`docker/monitoring/provisioning/alerting/mastersmith.yaml`）を読み取りで確かめると、次のとおりでした。

- **5xx の割合**（`5xx の割合の増加`）は `uri` で絞らない全体の式です。グループの API にも効きます（設計のとおり）。
- **p95 の警報は3つだけで、どれも1つの道に固定**されています。
  - `uri="/api/auth/login"`
  - `uri="/api/auth/session/refresh"`
  - `uri="/api/admin/check"`
- **グループの API の p95 を拾う既存の警報はありません**。ダッシュボード（`mastersmith-overview.json`）にも、グループの API を含む p95 のパネルはありません。

つまり、承認済みの「既存の p95 が `uri` のラベルでグループの API にも効く」は、今の決まりと食い違います。

`project.md` には次の決まりがあります。

- 「既存の警報で拾う」と書く前にソースで確かめる。
- 確定済みの成果物の記録の食い違いは隠さず明記し、直すかを依頼者に確かめる。

承認済みの文書は書き換えません。そのうえで、この段でどう扱うかを決める必要があります。

前の Intent では、次のように扱いが分かれています。

- 招待と登録（260925-user-management）は、API ごとの件数・p95・5xx のダッシュボードの区画を足しました（警報は足していません）。
- 利用者の管理（260930-user-admin）は、何も足しませんでした。

U4 role・U5 navigation も管理の API を足すため、この答えを後の単位の推奨にそろえます（`project.md` の学び）。

### 選択肢

A. **ダッシュボードに区画を足し、警報は足さない（推奨）**
  - `mastersmith-overview.json` に、この Intent の管理の API の区画を1つ足します（例「ロール・グループ・メニュー（Intent 261004-role-menu）」）。中身は API ごとの要求の数・p95・5xx の件数（`uri=~"/api/admin/groups.*"`）で、招待と登録の区画と同じ形です。U4・U5 は同じ区画に自分の `uri` を足します。
  - 新しい警報は足しません。p95 1 秒の判定は Performance Validation の k6 で行い、SLO は `Unverified` のままにします。
  - 区画を書いて式を確かめる持ち主は Observability Setup で、B3 のコード生成は `docker/monitoring/` に触れません。
  - 食い違い（グループの p95 を拾う警報は無い）は、この段の `monitoring-design.md` の「上流との差」に明記します。
  - 理由:
    - 手元の監視は見たいときだけ起動する運用のため、鳴らす先の無い警報を増やすより、起動したときに見られる区画の方が役に立ちます。
    - 招待と登録の前例と同じ形で、ファイルで置く決まりに合います。
    - グループの 5xx は既存の全体の警報が拾います。

B. **ダッシュボードの区画に加え、p95 の警報も1つ足す**
  - A の区画に加えて、`uri=~"/api/admin/groups.*"` の p95 が 1000 ms を超えたら鳴る警報（既存の p95 の警報と同じ形、`for: 5m`）を `mastersmith.yaml` に足します。持ち主は Observability Setup です。
  - SLO の破れに気づけます。ただし、管理の API は要求が少なく、5 分の窓では系列が少ないため、p95 の値が揺れやすく、空振りと見落としが出やすくなります。U4・U5 でも警報の数が増えます。

C. **何も足さない**
  - 利用者の管理（260930-user-admin）と同じ扱いです。p95 は Performance Validation の k6 だけで確かめます。
  - 食い違いは `monitoring-design.md` の「上流との差」に明記します。
  - 作業はいちばん少ない代わりに、配備の後にグループの API の応答時間を見る手段がありません。基準の値は Prometheus の式をその場で流して記録することになります。

X. Other (please specify)

[Answer]: A **Mode:** guided

## Consolidated Summary Confirmation

答えのまとめ（group の基盤の設計）:

- Q1 A: 手元の監視のダッシュボードに、この Intent の管理の API の区画を1つ足し、API ごとの要求の数・p95・5xx を出す（招待と登録の区画と同じ形。role・navigation も同じ区画に自分の API を足す）。警報は足さず、p95 は Performance Validation の k6 で判定し、SLO は `Unverified` のままとする。区画を書いて式を確かめるのは Observability Setup で、B3 は `docker/monitoring/` に触れない。承認済みの `observability-design.md` 4節（既存の p95 の警報がグループの API にも効く）は事実と食い違うため、差をこの段の成果物に記録する。
- 決まっていること（移行は次の空き番号で見込み `V10__u3_group.sql`、表を足すだけの前進のみで戻しはイメージだけ、配備の段で入れ替えの前の内部DB の複写と前の版のイメージでの戻しの練習を U4 の移行と合わせて1回で扱う、CI・Gradle・`packagesJudgedByTotal`・compose・Dockerfile・`application.yaml`・依存・秘密は変えない、足すテストは段 5・6 で verify の時間は Build and Test で実測、B3 は統合の前に手元で E2E 全体を流す、k6 の台本は B3 が書き Performance Validation が流す、接続プールの上限と metrics の公開は使い捨ての環境の一時の app.env で渡す、試験用の利用者 1,000 人は perf/README の SQL の手順を広げ、グループとメンバーは setup() で API を通して作り setupTimeout を広げる、独自の指標は足さない）は案のとおり。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
