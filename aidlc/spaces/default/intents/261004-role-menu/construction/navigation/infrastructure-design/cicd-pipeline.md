# CI/CD Pipeline — U5 navigation

U5 の検査の流れ（CI と1コマンドの検査）、同梱するファイル、負荷の試験の台本と準備、E2E、統合、戻し方、B7 で確かめることを示します。

U5 は読み取りだけの2本の API を持つ service の単位で、B7 で作ります。新しい依存・設定・秘密・移行は足さず、CI と1コマンドの検査の段・関門は **変えません**。この文書は既にある仕組みの記録として、どの段で何を確かめるかを書きます。配備先は開発者の PC 上のコンテナだけです（`aidlc/spaces/default/memory/project.md` の Deployment）。

## 出典

- 答え: `infrastructure-design-questions.md`（Q1: A、まとめの確認は Looks correct）
- 上流（どれも `aidlc/spaces/default/intents/261004-role-menu/` の下）:
  - `construction/navigation/nfr-design/` の `performance-design.md`（3節）・`scalability-design.md`（1節・2節）・`reliability-design.md`（2〜4節）・`security-design.md`（1〜7節）・`observability-design.md`・`logical-components.md`（1節・2節・5節）
  - `construction/navigation/nfr-requirements/tech-stack-decisions.md`
  - `construction/navigation/functional-design/functional-spec.md`
  - `inception/domain-design/components.md`
  - `inception/contract-design/contract-summary.md`（C1・C3・C5・C9）
  - `inception/delivery-planning/bolt-plan.md`（B7）
  - role の基盤の設計の `cicd-pipeline.md`（5.3 の準備の台本）
- 既にある仕組み（正とする。読むだけ）:
  - `.github/workflows/ci.yml`
  - `build.gradle.kts`・`backend/build.gradle.kts`
  - `frontend/vitest.config.ts`
  - `perf/README.md`（手順 2''）・`perf/k6/scenarios.js`
  - `docker/perf/compose.yaml`
  - `frontend/e2e/040-dsl-admin.e2e.ts`

## 1. CI（GitHub Actions）

`.github/workflows/ci.yml` は変えません。

| 項目 | 今の形 | U5 での扱い |
|---|---|---|
| きっかけ | `develop` へのプッシュ・`v*` のタグ・手動 | 変えない |
| 実行 | `./gradlew verify`（制限時間 60 分） | 変えない。リポジトリ全体を取得するため、画面のテストが `backend/` のアイコンの一覧のファイルを読める |
| 依存の入れ方 | lockfile どおり | 変えない。U5 は lockfile を変えない（NFR6.6） |
| 秘密 | 使わない | 変えない |
| 成果物 | WAR をコミットのハッシュの名前で保存 | 変えない。WAR にアイコンの一覧のファイルが1つ増える |

CI が失敗したときは、`team.md` の Testing Posture「不安定なテストと CI の失敗」の決まりで扱います。

## 2. 1コマンドの検査（`./gradlew verify`）の段と関門

段・関門・設定は増やさず、変えません。

| 段 | U5 で確かめること | 関門（失敗の条件） | 当たる要件 |
|---|---|---|---|
| 0〜3 準備・フォーマット・リンタ・ライセンスヘッダー | 足す Java と画面のテストのファイル。アイコンの一覧のファイル（`.txt`）はライセンスヘッダーの検査の対象外（`#` の行を読み飛ばす） | 既存のとおり | — |
| 4 ビルド | `navigation.*` のコンパイル、`processResources` でアイコンの一覧のファイルを同梱 | コンパイルの誤り | NFR6.8 |
| 5 単体テスト | `NavigationCallCountTest`・`MenuFilterPropertyTest`（jqwik の8つの性質、失敗のときの乱数の種は `exceptionFormat = FULL` で残る）・`NavIconPolicyTest`・`AllowedNavIconListTest`・`NavigationBoundaryArchitectureTest`、U1 の `ApiAccessArchitectureTest`、画面の `frontend/src/app/registry/allowedNavIcons.test.ts`（`frontendTest`） | 1件でも失敗。アイコンの一覧のファイルと画面の一覧がずれたら落ちる | NFR1.3・NFR2.3・NFR6.1・NFR6.2・NFR6.5・NFR6.7・NFR6.8 |
| 6 結合テスト | `NavigationAuthorizationApiIT`・`NavigationApiIT`・`NavigationUntrustedInputIT`・`NavigationMenuAccessConsistencyIT`・`NavigationQueryExposureIT`・`NavigationDslSwapIT`・`NavigationFailureIT`・`NavigationConnectionUsageIT`、U1 の `ApiAccessConsistencyIT`。組み込みの H2 だけを使い、コンテナを使わない | 1件でも失敗 | NFR1.1〜NFR1.4・NFR1.6〜NFR1.12・NFR2.6 |
| 7 カバレッジ | 3節のとおり | 全体またはパッケージごとの行 80%・分岐 70% を下回る。除外は足さない | NFR6.4 |
| 8 安全の検査 | SpotBugs ＋ FindSecBugs、OSV-Scanner、Gitleaks を除外なしで通す | 既存の基準 | NFR6.6 |
| 9 成果物 | 既存の `bootWar`・`verifyDslSchemaInWar`・初回の読み込みの量 | 既存のとおり | — |

補足:

- テストだけの部品（`ConnectionCountingDataSource`・それをかぶせる `BeanPostProcessor`・測る要求の印のフィルター・`TestNavigationBarrier`・数える替え物）は、`src/test/java` の `navigation/testsupport` に置き、WAR に入りません。
- `NavigationDslSwapIT` は、待ち合わせの口と合図で重なりを作ります。待ちの上限 20 秒は、止まったままになったときの守りだけで、合否は応答の中身で決めます（`reliability-design.md` 2節）。
- テストの名前: jqwik のテストは `MenuFilterPropertyTest` で `*Test` に当たります（group の読み直しの R-01 の確かめ）。

## 3. カバレッジ（段 7）

| 対象 | 扱い |
|---|---|
| `packagesJudgedByTotal` | 変えない。navigation は新しいパッケージで一覧に無い |
| `navigation.web`・`navigation.service`・`navigation.domain` | 自動でパッケージごとの下限（行 80%・分岐 70%）の対象。`NoOpNavigationBarrier` も本番の経路で呼ばれるため、外さない |
| 画面 | `app/registry` の追加のテストは、全体の合計の下限の中 |
| 計測の除外 | 増やさない |

実測は `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で行い、値を記録します。`verify` の時間の延びも同じ時に記録します（目標の数値は置かない）。

## 4. E2E（`./gradlew e2eTest`、verify と CI の外）

- E2E は意図して `verify` と CI の外に置いています。代わりに、統合の前とリリースの前に手元で流します（`team.md` の Testing Posture）。
- B7 は認証済みの利用者の API（`ApiAccess(AUTHENTICATED)`）を足すため、統合の前に手元で `./gradlew e2eTest` の全体を流します。
- navigation のために E2E のファイルは足しません。I の流れは U7 が持ちます（NFR6.3 は N/A）。

## 5. 負荷の試験（verify と CI の外）

### 5.1 持ち主と場面

| 作業 | 持ち主 | 出典 |
|---|---|---|
| `perf/k6/scenarios.js` に `navMenu`・`tableAccessVisible`・`tableAccessDenied`・`navMenuBaseline` を足す（1回の繰り返しに要求1つ、`tableAccessDenied` は `expectedStatuses(403)`） | B7 | `performance-design.md` 3節 |
| `k6 inspect --include-system-env-vars` と短い試し走り（各場面 1 VU・10 秒） | B7 | 同上 |
| 5.2 の準備の台本に navigation の分を足し、`perf/README.md` に書く | B7（role の B6 が作った台本を広げる） | この段の Q1: A |
| 使い捨ての環境で流して判定する（場面ごとに別の `k6 run`） | Performance Validation | `monitoring-design.md` 3節 |

### 5.2 悪い側のデータの用意（Q1: A）

role の Q2 A の準備の台本（role の `cicd-pipeline.md` 5.3）に、navigation の分を足します。

| データ | 入れ方 |
|---|---|
| 試験用の利用者 `nav-vu-01`〜`nav-vu-10` | `perf/README.md` の手順 2''（使い捨ての環境を止め、H2 の道具で SQL で入れる）。同時のログインの前に1人ずつログインさせて、ロックの状態の行を作る（`project.md` の学び） |
| 悪い側の DSL（menus 1,000 項目・深さ 5、100 テーブルを平均 10 回ずつ別々のまとまりから指す） | 生成の部品（`perf/make-large-dsl.mjs` に倣い、Node の標準の部品だけ）で作り、DSL の管理の API で投入・適用する。対象DB は要らない（照合は警告のまま適用できる。`frontend/e2e/040-dsl-admin.e2e.ts` の前提と同じ） |
| 目安の DSL（menus 100 項目・深さ 1） | 同じ生成の部品で作る（既定の DSL の生成は使わず、対象DB を要しない）。`navMenuBaseline` の直前に DSL の管理の API で適用し直す |
| 1万カラムを明示したロール | role の import の API（`/api/admin/role-transfer/check` → `/apply`）。role の準備と同じ YAML の生成の部品 |
| グループ（100）と所属 | group の手順（`setup()` で API）。`nav-vu-*` を 100 のグループに入れる |
| グループへの割り当てと作業ロールの保存 | API（割り当ては管理者、作業ロールの保存は各利用者のトークンで `PUT /api/me/work-role`） |
| `setupTimeout` | 10 人分のトークンを取る場面と準備の要求が多い場面を、場面の一覧（例 `NAV_SETUP_SCENARIOS`）に足して広げる（group の読み直しの R-05） |

### 5.3 流す順（role の読み直しの R-05 の手当て）

Performance Validation では、同じ使い捨ての環境で次の順に流します。

1. 準備（role の分と navigation の分）
2. 大きな DSL と1万カラムのロールを使う読み取りの場面: navigation の `navMenu`・`tableAccessVisible`・`tableAccessDenied`、role の読み取りの場面
3. `navMenuBaseline`（目安の DSL を適用し直す）
4. role の `roleTransferLarge`（ロールの設定を置き換える）

順を守れないときは、準備をやり直してから流します。台本全体を `caffeinate -i` で包み、手順 0 のとおり配備したアプリを止めます。

### 5.4 そのほか

- 接続プールの上限を下げた場面は置きません（承認済み）。接続の数は `NavigationConnectionUsageIT` で決定的に確かめるため、`acquire` の単位の読み方は navigation の合否に関わりません。
- 使い捨ての環境の一時の `app.env` に、navigation のための値は足しません。

## 6. 統合・配備・戻し方・秘密

| 項目 | 扱い | 出典 |
|---|---|---|
| 作業ブランチ | `develop` から作る短命のブランチ（例 `feature/261004-role-menu-b7`） | `team.md` の Way of Working |
| 統合の前の関門 | `./gradlew verify`（コンテナの実行環境あり）と E2E の全体（4節） | `team.md` |
| 統合の形 | `develop` への squash（1コミット） | `team.md` |
| プッシュ | 依頼者自身が行う | `team.md` |
| 配備 | 既存の手順のまま | `infrastructure-specification.md` 1節 |
| 戻し | 直前の版のイメージ。戻した版では DSL が無い状態で起動し、メニューが出ない（手順は dsl-v2 と U3・U4 の引き継ぎにまとめる） | `infrastructure-specification.md` 4節 |
| 秘密 | 足さない。負荷の試験の資格情報は、今までどおりリポジトリの外の一時の環境ファイルで渡す。試験のデータは予約のドメインだけ | `perf/README.md`、`project.md` の Forbidden |
| 依存 | 足さない | NFR6.6 |

## 7. B7 で確かめること

| 確かめ | 方法 | 成り立たないとき |
|---|---|---|
| (i) アイコンの一覧のファイルが WAR に入り、起動時に読める。画面の一覧と一致する | 段 4・段 5・段 6 の起動 | ファイルか画面の一覧を直す |
| (ii) 2節の段 5・6 のテストがすべて通る | `verify` | 原因を直す |
| (iii) カバレッジの実測と `verify` の時間 | 3節 | 除外を増やさずテストを足す |
| (iv) k6 の4場面と準備の台本が `k6 inspect` と試し走りで動く | 5.1 | 台本を直す |
| (v) E2E の全体 | 4節 | 統合しない。原因を直す |
| (vi) `application.yaml`・依存・`packagesJudgedByTotal`・`docker/monitoring/`・compose が変わっていない | コード生成のレビュー | 元に戻す |

## 8. 受け入れた制約（承認済みのまま）

- 正しくない `%` の並びの要求は 500 で、ERROR のログの例外の文に引数の名前と値が入ります（`observability-design.md` 3節）。アプリ全体の直しは後の Intent です。
- エンコードしない文字・8 KiB を超える問い合わせは、Tomcat の HTML の 400 になります（`security-design.md` 7節）。
- p95 の警報は足しません（`monitoring-design.md` 2節）。

## 9. 監視の差の参照

監視の式の名前の差は、`monitoring-design.md` 7節に書きます。

## 10. 上流との差（承認済みの文書は書き換えない）

| 上流 | 上流の書き方 | この設計 | 理由 |
|---|---|---|---|
| `scalability-design.md` 1節 | ロール・グループ・割り当て・1万カラムの明示の設定は、使い捨ての環境の内部DB に SQL で直接入れる。目安の DSL は既定の DSL の生成で作る | role の準備の台本に navigation の分を足し、DSL は2つとも生成の部品で作って DSL の管理の API で適用する。ロールは import の API、グループ・割り当て・作業ロールの保存は API、利用者だけは手順 2'' の SQL（5.2） | Q1: A。role の Q2 A と準備を1つにし、アプリの検証を通ったデータだけを入れるため。目安の DSL のための対象DB も要らなくなる |
| `performance-design.md` 3節 | 場面ごとに別の実行（流す順は書いていない） | role の場面と合わせた流す順を決めた（5.3） | role の読み直しの R-05 の手当て |
