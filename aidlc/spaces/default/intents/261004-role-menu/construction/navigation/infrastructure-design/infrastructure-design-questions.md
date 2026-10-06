# 基盤の設計の質問 — U5 navigation

単位 U5 navigation（kind: service。B7 で作る）の基盤の設計の前に、決まっていない点を確かめます。作る成果物は、段の定義の `produces_kinds` により `infrastructure-specification.md`・`monitoring-design.md`・`cicd-pipeline.md`・`traceability.json` の4つです。

読んだもの:

- この単位の承認済みの NFR 設計の7つ（`nfr-design/performance-design.md`・`security-design.md`・`scalability-design.md`・`reliability-design.md`・`observability-design.md`・`logical-components.md`・`traceability.json`）と、各ファイルの末尾の「承認の場の決定と直し」
- NFR 要件（`nfr-requirements/`）、機能設計（`functional-design/functional-spec.md`）
- `inception/domain-design/components.md`、`inception/contract-design/contract-summary.md`（C1・C3・C5・C9）、`inception/delivery-planning/bolt-plan.md`（B7）
- group と role の基盤の設計の成果物と、その読み直しの指摘（読み取りだけ）
- 既存のもの（読み取りだけ）: `.github/workflows/ci.yml`、`backend/build.gradle.kts`、`application.yaml`、`compose.yaml`、`docker/perf/compose.yaml`、`docker/monitoring/`、`perf/README.md`・`perf/k6/scenarios.js`、`frontend/vitest.config.ts`、`frontend/e2e/040-dsl-admin.e2e.ts`
- `team.md`・`project.md`

## 決まっていること（質問にしない）

### 配備と基盤の範囲

- 配備先が決まるまで、基盤の設計は開発者の PC 上のコンテナの範囲に限ります。クラウドの基盤（IaC・検証環境・警報の通知の先）は作りません（`project.md` の Deployment）。
- navigation は読み取りだけの2本の API です（`GET /api/me/navigation`・`GET /api/me/table-access?schema=…&table=…`）。表・移行・書き込み・監査は足しません（`reliability-requirements.md`、`tech-stack-decisions.md`）。
- 次のものは変えません。
  - 配備の形（1つのアプリのコンテナ、組み込みの H2、単一のインスタンス）
  - `compose.yaml`・`docker/perf/compose.yaml`・`Dockerfile`・`.env.example`・`application.yaml`
  - 接続プール
- 新しい依存・秘密・外への接続は足しません。
- 戻しは直前の版のイメージだけで行います。移行が無いため、navigation 自身の戻しの手当ては要りません。
  - 前の版のアプリには2本の API が無く、版 2 の DSL も読めず、DSL が無い状態で起動します（dsl-v2 の配備の段への引き継ぎ。role の読み直しの R-03 の指摘）。
  - そのため、戻した版には業務のメニューが出ません。これは dsl-v2 と U3・U4 の配備の段への引き継ぎにまとめて書くもので、navigation の成果物には「戻すとメニューが出ない」ことだけを書きます。

### 同梱するファイル

- アイコンの一覧のファイル `backend/src/main/resources/navigation/allowed-icons.txt`（18 個）を足します（B7）。
  - 起動時に1回読み、誤りなら起動を止めます（`reliability-design.md` 4節）。WAR の `WEB-INF/classes/navigation/` に入ります。
  - 画面のテスト `frontend/src/app/registry/allowedNavIcons.test.ts`（Vitest）が、このファイルを `frontend/` の外の相対の道で読み、`app/registry` の一覧と照らします。
  - CI はリポジトリ全体を取得するため、`verify` の `frontendTest`（段 5）で毎回動きます。
- このファイルは `#` の行を読み飛ばします。ライセンスヘッダーの検査の対象（Java・Gradle・画面のファイル・メールのテンプレート）には入りません。

### CI と1コマンドの検査（既にあるものの記録）

- CI・Gradle のタスク・`packagesJudgedByTotal` は変えません。
  - `navigation.web`・`navigation.service`・`navigation.domain` は新しいパッケージで、自動でパッケージごとの下限の対象になります。
  - `NoOpNavigationBarrier` は本番の経路で呼ばれるため、カバレッジから外しません（`logical-components.md` 5節）。
- 足すテストは既存の振り分けに入ります（`logical-components.md` 5節）。
  - `*Test` は段 5: `NavigationCallCountTest`・`MenuFilterPropertyTest`・`NavIconPolicyTest`・`AllowedNavIconListTest`・`NavigationBoundaryArchitectureTest`。
  - `*IT` は段 6（組み込みの H2 だけ）: `NavigationAuthorizationApiIT` ほか8つ。
  - 画面のテストは段 5。
- テストの名前（group の読み直しの R-01 の手当て）: jqwik のテストは `MenuFilterPropertyTest` で、`*Test` に当たります。承認済みの設計に `Properties` で終わる名前はありません。
- テストだけの部品は `src/test/java` の `navigation/testsupport` に置き、WAR に入りません（`logical-components.md` 1節の T1〜T3）。
  - T1: `ConnectionCountingDataSource`・それをかぶせる `BeanPostProcessor`・測る要求の印のフィルター
  - T2: `TestNavigationBarrier`
  - T3: 数える替え物
- `NavigationDslSwapIT` は、待ち合わせの口と合図で重なりを作ります。待ちの上限 20 秒は、止まったままになったときの守りだけです。`verify` の時間は Build and Test で `:backend:cleanTest :backend:cleanIntegrationTest` を付けて実測して記録します。
- 統合は B7 の短命のブランチから `develop` へ squash で戻します。
- B7 は認証済みの利用者の API（`ApiAccess(AUTHENTICATED)`）を足すため、統合の前に手元で `./gradlew e2eTest` の全体を流します。navigation のために E2E のファイルは足しません（I の流れは U7）。

### 監視・ログ・SLO（group の Q1 A にそろえる）

- 独自の指標と新しい警報は足しません。
- group の Q1 A のダッシュボードの区画（この Intent の API）に、navigation の `uri`（`/api/me/navigation`・`/api/me/table-access`）を足します。区画を書いて式を確かめるのは Observability Setup で、B7 は `docker/monitoring/` に触れません。
- 承認済みの `observability-design.md` 4節が名指しした2つの式の扱いは、次のとおりです。
  - 2つとも `http_server_requests_seconds_bucket` で書かれています。この手元の監視の既存の式（警報とダッシュボード）は `http_server_requests_milliseconds_bucket` で、名前と単位の付き方が違います。
  - 承認済みの文書は書き換えません。成果物の「上流との差」に書き、Observability Setup で起動して実際の名前（と `uri` の値 `/api/me/table-access`）を確かめてから区画の式に入れます（`project.md` の学び）。
- SLO（2本の API の p95 1 秒）は、手元の監視を常に動かしていない間は `Unverified` とします。Performance Validation の値を基準の値として記録します。
- 受け入れた制約（`observability-design.md` 3節・5節）はそのまま引き継ぎます。正しくない `%` の並びで値が ERROR のログに出る件は、アプリ全体の直しを後の Intent に回します。この段では、監視で見るもの（`ms-error-logs` が数える）だけを書きます。

### 負荷の試験（k6）

- 台本（4つの場面 `navMenu`・`tableAccessVisible`・`tableAccessDenied`・`navMenuBaseline`）は B7 のコード生成が書き、流すのは Performance Validation です（`performance-design.md` 3節）。
  - 場面ごとに別の `k6 run` で流します。`tableAccessDenied` は `expectedStatuses(403)` を宣言します。
  - 台本を書いた後に、`k6 inspect --include-system-env-vars` と短い試し走りを行います。
- **場面の順**（role の読み直しの R-05 の手当て）: role の `roleTransferLarge` は、置き換える先のロールの設定を入れ替えます。`navMenuBaseline` は、目安の DSL を適用し直します。そのため、Performance Validation では次の順で流します。
  1. 準備
  2. navigation と role の読み取りの場面（大きな DSL と1万カラムのロールを使うもの）
  3. `navMenuBaseline`
  4. `roleTransferLarge`
  - 順を守れないときは、準備をやり直してから流します。
- **試験用の利用者と `setupTimeout`**（group の読み直しの R-05 の手当て）:
  - `nav-vu-01`〜`nav-vu-10` は、`perf/README.md` の手順 2'' の形で入れます。直接入れた利用者は、同時のログインの前に1人ずつログインさせてロックの状態の行を作ります（`project.md` の学び）。
  - 10 人分のトークンを `setup()` で取る場面は、場面の一覧（例 `NAV_SETUP_SCENARIOS`）に足して `setupTimeout` を広げます。
- 接続プールの上限を下げた場面は置きません（承認済み）。接続の数は結合テスト `NavigationConnectionUsageIT` で決定的に確かめます。そのため、`acquire` の単位の読み方（role の読み直しの Suggestion）は navigation の合否には関わりません。
- DSL の投入と適用は、対象DB が無くても行えます。照合は「対象DB の接続先が設定されていません」の警告になり、適用できます（既存の `frontend/e2e/040-dsl-admin.e2e.ts` の前提と同じ）。

## Q1 負荷の試験の悪い側のデータ（ロール・グループ・割り当て・1万カラムの設定）の入れ方

### 背景

承認済みの navigation の `scalability-design.md` 1節は、悪い側のデータの入れ方を次のように書いています。

- DSL（menus 1,000 項目・深さ 5、100 テーブル）は、`perf/make-large-dsl.mjs` に倣った生成の手順で作る。目安の DSL（menus 100 項目・深さ 1）は既定の DSL の生成で作る。
- ロール・グループ・割り当て・1万カラムの明示の設定は、使い捨ての環境の内部DB に SQL で直接入れる。

その後に決まった role の基盤の設計の Q2: A では、同じ悪い側のデータ（1万カラムのロールなど）を、次のように入れる準備の台本を B6 で `perf/` に足すことになりました。

- DSL は DSL の管理の API で投入・適用する。
- ロールと権限は role の import の API で一度に入れる。
- 利用者は手順 2'' で入れる。

navigation の試験は、同じ使い捨ての環境で role の場面と続けて流す見込みです。入れ方が2通りあると、準備の台本が2つになり、表の形に縛られる SQL と、アプリの検証を通る API の両方を保つことになります。

さらに、目安の DSL を「既定の DSL の生成」で作るには対象DB（PostgreSQL の profile）が要ります。生成の部品で作って投入するなら対象DB は要りません。

同じ段の先の単位の答えを、後の単位の推奨に反映します（`project.md` の学び）。

### 選択肢

A. **role の準備の台本（Q2: A）に乗せ、navigation の分を足す（推奨）**
  - B6 が作る準備の台本を、B7 で次のように広げます。
    - 悪い側の DSL（menus 1,000 項目・深さ 5）と目安の DSL（menus 100 項目・深さ 1）は、どちらも生成の部品で作って DSL の管理の API で投入・適用します。目安の DSL も既定の DSL の生成を使わず、対象DB を要しない形にします。
    - 1万カラムのロールは、role の import の API で入れます。
    - グループと所属は、group の手順（`setup()` で API）で作ります。
    - `nav-vu-01`〜`nav-vu-10` を 100 のグループに入れ、グループへの割り当てと、作業ロールの保存（`PUT /api/me/work-role`）は API で作ります。
  - 承認済みの `scalability-design.md` 1節（SQL で直接入れる）との差は、成果物の「上流との差」に書きます。
  - 理由: 準備の台本が1つになり、アプリの検証を通ったデータだけが入ります。role の場面と同じ環境で続けて流せます。目安の DSL のために対象DB を用意する手間も無くなります。

B. **承認済みのとおり、SQL で直接入れる**
  - navigation の分（ロール・グループ・割り当て・設定）は SQL で入れ、role の準備の台本とは別に持ちます。
  - 承認済みの設計のまま進められます。ただし、準備が2通りになり、表の形（名前の鍵の作り方など）に台本が縛られます。目安の DSL は既定の DSL の生成で作るため、使い捨ての環境に対象DB（PostgreSQL の profile）と、それに合う表を用意します。

C. **navigation の場面は role の準備をそのまま使い、足りない分だけ SQL で入れる**
  - 1万カラムのロールと DSL は role の準備に頼り、グループの所属・割り当て・作業ロールの保存だけを SQL で入れます。
  - 準備の要求の数は少なくなります。ただし、API と SQL が混ざり、どちらの形が変わっても台本が壊れます。

X. Other (please specify)

[Answer]: A **Mode:** guided

## Consolidated Summary Confirmation

答えのまとめ（navigation の基盤の設計）:

- Q1 A: メニューの負荷の試験の悪い側のデータは、role の Q2 A の準備の台本に navigation の分を足して入れる。DSL は2つとも生成の部品で作って DSL の管理の API で適用し（対象DB は要らない）、ロールは import の API、グループ・割り当て・作業ロールの保存も API、利用者は perf/README の手順 2'' で入れる。承認済みの `scalability-design.md` 1節（SQL で直接入れる）との差を成果物の上流との差に書く。
- 先に手当てした点と決まっていること（k6 の場面の順は 準備 → 大きな DSL と1万カラムのロールを使う読み取りの場面 → `navMenuBaseline` → `roleTransferLarge` で、守れなければ準備をやり直す、setupTimeout は場面の一覧に足す、jqwik のテストは `MenuFilterPropertyTest`、acquire は合否に関わらない、戻すと前の版では DSL が無い状態で起動しメニューが出ないため手順は dsl-v2 と U3・U4 の引き継ぎにまとめる、`allowed-icons.txt` は WAR に同梱し画面のテストが verify で照らす、監視の式の名前は承認済みの `_seconds_` ではなく手元の既存の式の `_milliseconds_` で上流との差に書き Observability Setup で確かめる、監視は group の Q1 A にそろえる、DSL は対象DB が無くても照合の警告のまま適用できる）は案のとおり。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
