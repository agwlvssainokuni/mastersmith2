# 単体テストの手順（Unit Test Instructions）— U5 業務のメニュー（navigation）、Bolt B7

計画: `code-generation-plan.md`（同じディレクトリ）。方法は test-after（層ごとに実装を書き、同じ Bolt の中でその層のテストを書いて流す）。Test Strategy は Standard。

計画の 13節の依頼者の答え（Q1〜Q6、どれも A）を反映した。この手順書に当たるのは次の6つ。
- Q1: 既存の `role/RoleBoundaryArchitectureTest` の navigation の規則を `role.service`・`role.domain` に広げる（ほかの規則は変えない）。Step 5 と Step 11 で流す。
- Q2: `ACCESS_DENIED` は `ProblemTypeRegistry` から code で引き、navigation は access に依存しない。`NavigationBoundaryArchitectureTest` に「access に依存しない」を書き、既存の `access/AccessBoundaryArchitectureTest` は変えずに Step 11 で流す。
- Q3: 置き場の引数の欠け・空は、どちらも `fieldErrors` の無い 400 `VALIDATION_FAILED`。`NavigationUntrustedInputIT` で両者の本文が `instance` を除き同じことを確かめる。
- Q4: 空白だけの名前は空と見なさず通常の判定（READ は 200、NONE と DSL に無い名前は 403）。`NavigationUntrustedInputIT` の表に足す。
- Q5: 負荷の悪い側のロール `perf-nav-worst` は `rolePrepare` の YAML に足し、残りの準備は `navPrepare`（4節）。
- Q6（`allowed-icons.txt` の `#` の行のヘッダー）は、`AllowedNavIconListTest` の本番のファイルの読み取り（ヘッダーの行を読み飛ばし 18 個になる）で確かめる。

ここに書くコマンドは、どれもこの単位のテストだけを名指しで流す。素の `./gradlew test`・`./gradlew verify`・`npm test` はこの単位の確かめには使わない（統合の前の関門の `verify` は計画の Step 18 で別に流す）。

## 1. テストの道具と設定（既存のものをそのまま使う）

| 対象 | 道具 | 設定（変えない） |
|---|---|---|
| バックエンドの単体テスト | JUnit 5・AssertJ・Mockito・ArchUnit・jqwik | `backend/build.gradle.kts` の `test` タスク（名前が `Test` で終わるクラスだけ） |
| バックエンドの結合テスト | Spring Boot Test・spring-security-test・組み込みの H2（クラスごとの一時の内部DB、`common/testsupport/TestDatabase`） | `integrationTest` タスク（名前が `IT` で終わるクラスだけ） |
| カバレッジ | JaCoCo（行 80%・分岐 70%、全体とパッケージごと） | `jacocoTestReport`・`jacocoTestCoverageVerification`（除外と `packagesJudgedByTotal` は変えない） |
| 画面のテスト | Vitest（`frontend/vitest.config.ts` の `include: ['src/**/*.test.{ts,tsx}']`） | 変えない |
| 負荷の台本の確かめ | k6（`k6 inspect`） | `perf/k6/scenarios.js` |

- テストのクラスの名前は必ず `Test`（単体）か `IT`（結合）で終わらせる。jqwik のテストは `MenuFilterPropertyTest`。
- テストの説明文（`@DisplayName`・`describe`・`it`）は英語、テストデータは日本語でよい。メールアドレスは予約のドメイン（`example.com`）だけ。
- navigation の結合テストは組み込みの H2 だけを使い、コンテナを使わない。下のコマンドはコンテナの実行環境が無くても流せる。統合の前の `verify`（計画の Step 18）だけは colima と次の環境変数が要る（`project.md` の学び）。

```bash
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

- テストを流している間に、同じ作業フォルダで別のビルドを流さない。コマンドは1つずつ順に流す。長いコマンドは `caffeinate -i` で包む。

## 2. テストを始める前に動くことを確かめるコマンド（計画の Step 2）

既存のテストで、名指しのコマンドが動くことを確かめる（変更の前。失敗 0 を記録する）。

```bash
./gradlew :backend:test --tests 'cherry.mastersmith.role.RoleBoundaryArchitectureTest'
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.role.web.MyPermissionsApiIT'
cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/app/registry/validateRegistrations.test.ts; cd ..
```

## 3. この単位のテストを流すコマンド（層ごと。計画の手順の番号）

```bash
# Step 5: ドメイン（例のテスト・性質ベースのテスト・アイコン・節の形）と、広げた role の境界テスト
./gradlew :backend:test \
  --tests 'cherry.mastersmith.navigation.domain.*' \
  --tests 'cherry.mastersmith.role.RoleBoundaryArchitectureTest'

# Step 7: 業務処理（一覧のファイル・呼び出しの回数・振る舞い）
./gradlew :backend:test --tests 'cherry.mastersmith.navigation.service.*'

# Step 10: API と結合（この単位の結合テストすべて）
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.navigation.*'

# Step 11: 境界と構造の検査（navigation の境界テストと、関わる既存の検査）
./gradlew :backend:test \
  --tests 'cherry.mastersmith.navigation.NavigationBoundaryArchitectureTest' \
  --tests 'cherry.mastersmith.ArchitectureTest' \
  --tests 'cherry.mastersmith.ApiAccessArchitectureTest' \
  --tests 'cherry.mastersmith.role.RoleBoundaryArchitectureTest' \
  --tests 'cherry.mastersmith.access.AccessBoundaryArchitectureTest' \
  --tests 'cherry.mastersmith.dsl.DslBoundaryArchitectureTest' \
  --tests 'cherry.mastersmith.auth.AuthBoundaryArchitectureTest'
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.ApiAccessConsistencyIT'

# Step 12: 画面（アイコンの一覧のファイルと app/registry の一覧の一致）
cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/app/registry/allowedNavIcons.test.ts; cd ..

# Step 13: バックエンドの区切りの確かめ（この単位の単体と結合をまとめて）
./gradlew :backend:test --tests 'cherry.mastersmith.navigation.*' --tests 'cherry.mastersmith.role.RoleBoundaryArchitectureTest'
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.navigation.*' --tests 'cherry.mastersmith.ApiAccessConsistencyIT'
```

性質ベースのテストが落ちたときは、jqwik の報告（`exceptionFormat = FULL`）に出る `seed` を `@Property(seed = "…")` に一時的に入れて名指しで流し直し、原因を直した後に外す。種は `generation-notes.md` に記録する。

## 4. 負荷の台本の確かめ（計画の Step 14。verify と CI の外）

```bash
# 場面と閾値の名前（navigation の5つの場面と、perf-nav-worst を足した rolePrepare。計画 13節 Q5: A）
for s in rolePrepare navPrepare navMenu tableAccessVisible tableAccessDenied navMenuBaseline; do
  k6 inspect --include-system-env-vars -e SCENARIO=$s perf/k6/scenarios.js
done
# 生成の部品（版 2 の DSL の menus の塊を置き換える）
node perf/make-nav-dsl.mjs --worst build/perf-results/role-data/dsl.yaml build/perf-results/nav-data/worst.yaml
node perf/make-nav-dsl.mjs --baseline build/perf-results/role-data/dsl.yaml build/perf-results/nav-data/baseline.yaml
```

短い試し走り（各場面 1 VU・10 秒）は、`perf/README.md` の使い捨ての環境の手順で流す（配備したアプリを止める。前もって依頼者に伝える）。本番の長さ（10 VU・3 分）で流して判定するのは Performance Validation。

## 5. カバレッジの目標

| 対象 | 目標 | 確かめ方 |
|---|---|---|
| `navigation.domain`・`navigation.service`・`navigation.web` | パッケージごとに行 80% 以上・分岐 70% 以上（新しいパッケージのため自動で対象） | 計画の Step 18 の `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `verify`。名指しの途中の値は判定に使わない |
| 全体の合計 | 行 80% 以上・分岐 70% 以上（変更の前の値を Step 3 で記録し、下がりを `code-summary.md` に書く） | 同上 |
| 画面 | 全体の合計の下限の中（足すのはテストのファイルだけ） | `verify` の `frontendTest` |

除外は足さない。下限に届かないときはテストを足し、設定の値を下げない。

## 6. モックと替え物の指針

- **絞る関数（`MenuFilter`）**: 純粋な関数のため替え物を使わない。入力の `tableMain` と `iconOf` は表（`Map`）で渡す。
- **呼び出しの回数（`NavigationCallCountTest`）**: Spring を起動せず、`testsupport` の数える替え物（解決の口 `CountingResolver`、DSL の提供口 `CountingDslProvider`）を `NavigationService` に直に渡す。数えるのは navigation が呼ぶ回数だけ（口の内側の問い合わせの数は role の `EffectivePermissionQueryCountIT`）。
- **結合テスト**: 解決の口と DSL の提供口は本物を使う（role の `RoleBarrier` は使わない）。DSL は `testsupport/NavigationDslFixture` で `ActiveDslModelHolder` に置く（role の `RoleDslFixture` と同じ置き方）。
- **重なり（`NavigationDslSwapIT`）**: `testsupport/TestNavigationBarrier`（`@Primary`）の点で止め、止まった合図を受けてから DSL を差し替え、差し替えの確定を待って放す。`sleep`・経過の時間・壁時計を使わない。待ちの上限 20 秒は止まったままになったときの守りだけ。
- **想定外の失敗（`NavigationFailureIT`）**: 解決の口を例外を投げる替え物（`@Primary`）にする。
- **接続の数（`NavigationConnectionUsageIT`）**: `testsupport/ConnectionCountingConfig`（`@TestConfiguration`）をこの IT だけが `@Import` する。ログイン（トークンの取得）をすべて終えた後、測る要求の直前に `reset()` を呼び、要求の頭 `X-Test-Measure-Id` を付けて1つずつ送る。印ごとの最大が 1 であることを確かめ、0 なら用意の誤りとして落とす。
- **記録の確かめ（`NavigationQueryExposureIT`）**: スパンは既存の `TracingAndLoggingIT` と同じテストの集め手、ログは既存の `common/testsupport/LogEvents` を使う。
- 時刻に頼る処理は無い。

## 7. テストデータ

- **利用者・ロール・割り当て・作業ロール**: `testsupport/NavigationActors` が role の口（テストの手伝い `RoleFixtures`・`RoleActors`）で作る。403 の主体は「要るテーブルだけ NONE で、ほかのテーブルは READ の作業ロールを持つ利用者」にする（`team.md`）。停止中の利用者は既存の手伝いで作る。
- **DSL**: 版 2 のスキーマ1つ（例 `SALES`）に数テーブルの小さな DSL を基本にする。名前は日本語でよい。
- **信頼できない名前**: 試し N1 の 23 種（`/`・`..`・`../x`・`?`・`#`・`%`・`%25`・空白・`;`・`javascript:alert(1)`・`https://example.com/x`・`\`・`&` と `=`・`<'"&>`・受注明細・絵文字・300 文字・タブ・LF・CR・NUL・DEL）と、空白だけの名前（空と見なさず通常の判定。計画 13節 Q4: A）。DSL の検証が拒みうる名前もモデルを直に置いて使う（計画の D-9）。
- **悪い側の組**（`NavigationMenuAccessConsistencyIT` の組1）: menus 1,000 項目・深さ 5・100 テーブル × 100 カラム、作業ロールは1万カラムに明示の値でテーブルの半分が NONE。モデルはメモリで組み立て、設定の行は SQL で直接入れる。期待の表は設定の作り方と同じ規則（テーブルの番号が偶数なら NONE など）から求める。
- **一致のテストの組**: 計画 4.4 の7つの組。組1〜3・5・6 の DSL には、どのメニューの項目も指さない READ のテーブルを1つ以上置く（組6 は NONE のテーブルと別に READ のテーブルを1つ置く）。
- 各結合テストのクラスは自分の一時の内部DB を使い、ほかのテストが作った状態に頼らない。
