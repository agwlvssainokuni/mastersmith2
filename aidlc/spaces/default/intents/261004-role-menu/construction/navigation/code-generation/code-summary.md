# コードの要約（Code Summary）— U5 業務のメニュー（navigation）、Bolt B7

コミットは依頼者の承認を得て計画 3節の区切りで行う（C1〜C3 と記録のコミット）。develop へは squash で1コミットになる。

計画: `code-generation-plan.md`（同じディレクトリ、13節の Q1〜Q6 はすべて A）。Step ごとのコマンドと結果は `generation-notes.md`。方法は test-after（Testing Contract のとおり、層ごとに実装を書き、同じ Bolt の中でその層のテストを書いて流した）。作業ブランチは `feature/261004-role-menu-b7`（`develop` の e64382e から）。

## 1. 作ったもの・変えたもの

| 区切り | 道 | 中身 |
|---|---|---|
| C1 | `backend/src/main/java/cherry/mastersmith/navigation/`（`package-info.java`、`domain/`・`service/`） | 値の型（`NavNode`・`NavTableRef`・`DisplayLabel`・`EmptyReason`・`NavigationMenu`・`TableAccessResult`）、絞る純粋な関数 `MenuFilter`（`MenuFilterInput`）、アイコンの照らし合わせ `NavIconPolicy`、一覧のファイルの読み込み `AllowedNavIconList`、待ち合わせの口 `NavigationBarrier`・`NoOpNavigationBarrier`、業務処理 `NavigationService` |
| C1 | `backend/src/main/resources/navigation/allowed-icons.txt` | `#` の行の Apache License 2.0 のヘッダーと 18 個の名前（13節 Q6: A） |
| C1 | `backend/src/test/java/cherry/mastersmith/navigation/domain/`・`service/`、`testsupport/` の `CountingDslProvider`・`CountingResolver`・`NavigationModels` | 単体テスト7クラス（`MenuFilterTest`・`MenuFilterPropertyTest`・`NavIconPolicyTest`・`NavNodeTest`・`AllowedNavIconListTest`・`NavigationCallCountTest`・`NavigationServiceTest`）と替え物 |
| C1 | `backend/src/test/java/cherry/mastersmith/role/RoleBoundaryArchitectureTest.java` | navigation の規則を `role.service`・`role.domain` に広げた（13節 Q1: A。ほかの規則は変えない） |
| C2 | `backend/src/main/java/cherry/mastersmith/navigation/web/` | `NavigationController`（`GET /api/me/navigation`・`GET /api/me/table-access`、`@ApiAccess(AUTHENTICATED)`）と応答の record 5つ |
| C2 | `backend/src/test/java/cherry/mastersmith/navigation/web/`・`NavigationBoundaryArchitectureTest.java`、`testsupport/` の T1（`ConnectionCountingDataSource`・`ConnectionCountingConfig`・`MeasureIdFilter`）・T2（`TestNavigationBarrier`）・`NavigationDslFixture`・`NavigationActors` | 結合テスト8クラスと境界テスト |
| C2 | `frontend/src/app/registry/allowedNavIcons.test.ts` | 一覧のファイルと `ALLOWED_ICONS` の一致（BR4.4・NFR6.7）。`allowedIcons.ts` は変えていない |
| C3 | `perf/make-nav-dsl.mjs`（新しい）・`perf/k6/scenarios.js`・`perf/README.md` | 悪い側・目安の DSL の生成、場面 `navPrepare`・`navMenu`・`tableAccessVisible`・`tableAccessDenied`・`navMenuBaseline`、`rolePrepare` の準備の YAML へのロール `perf-nav-worst` の追加（13節 Q5: A）、手順の節 |
| C3 | `README.md` | 「業務のメニューの API（Intent 261004-role-menu の U5、Bolt B7）」の節 |

変えていないもの（Step 16 で `git diff --stat develop` の差が無いことを確かめた）: `application.yaml`・`.env.example`・`compose.yaml`・`docker/perf/compose.yaml`・`Dockerfile`・`docker/monitoring/`・`.github/workflows/ci.yml`・`backend/build.gradle.kts`（`packagesJudgedByTotal` と除外を含む。ただし Step 18 の後に依頼者の決定 A で単体テストの JVM のヒープだけを 1.5g にした。4節）・`backend/gradle.lockfile`・`frontend/package-lock.json`（SHA-256 は Step 3 と同じ）・`frontend/src/app/registry/allowedIcons.ts`・移行（V1〜V12 のまま）・`vendor/`（固定先 e82b651・8d44c36 のまま）・navigation の外の `src/main`。

## 2. 実装で決めたこと

- **D-1〜D-14 は計画のとおり**。置き場は `resolve` を1回（READ・FULL のときだけ表示名のための `current()` を1回）、性質は 8 つ、`ACCESS_DENIED` は `ProblemTypeRegistry.findByCode` で起動時に引く（無ければ起動を止める）、欠け・空は `fieldErrors` の無い 400、空白だけの名前は通常の判定、主体は web で `userId` にして渡す、`NavigationMenu`・応答の `toString` は数と理由だけ。
- **`MenuFilter`・`NavIconPolicy` は Spring の部品にしない**（静的な純粋な関数と値の型）。`MenuFilterInput` は `BiFunction<String, String, MainPermission>` と `UnaryOperator<String>` の組。
- **`AllowedNavIconList`** は引数の無いコンストラクター1つでクラスパスのファイルを読み、読む口は静的な `load(Resource)`（無い・読めないを見分け、ERROR に `reason` の鍵で種類だけを出す）と `parse(String)`。誤りは `InvalidIconListException`（種類 `MISSING`・`UNREADABLE`・`EMPTY`・`NO_DEFAULT`・`DUPLICATE`）。
- **悪い側の DSL**（`perf/make-nav-dsl.mjs --worst`）: 最上位のまとまり 40 個 × 25 項目で 1,000 項目・深さ 5。鎖の項目もテーブルを指す（テーブルと子の両方を持つ項目）ため、1,000 項目すべてがテーブルを指し、100 テーブルのそれぞれをちょうど 10 回ずつ別々のまとまりから指す。
- **`perf-nav-worst`**: 1万カラムに READ と FULL を交互に明示し、テーブルは偶数の番号が `NONE`・奇数が `READ`（スキーマには明示しない）。準備の YAML のロールの合計 1,000 は変えず、埋めるロールを1つ減らした。
- **試験用の利用者**: `perf-nav-01`〜`10`（管理者の印なし。手順 2N の SQL）。ロールの場面の利用者 `perf-gr-*` と分けた（ロールの場面 `workRoleSwitch` などが作業ロールを変えるため、業務のメニューの場面の前提を崩さない）。

## 3. テストの量とカバレッジ

実測は Step 18 の `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（06:56:03〜07:10:33、14 分 30 秒、exit 0。colima の共有と README の環境変数を確かめてから流した）。件数は `backend/build/test-results/{test,integrationTest}/*.xml` の集計、カバレッジは `backend/build/reports/jacoco/test/jacocoTestReport.xml`。

| 対象 | 変更の前（Step 3） | 変更の後（Step 18） | 差 |
|---|---|---|---|
| 単体テスト | 232 クラス・2,015 件 | 240 クラス・2,082 件 | +8 クラス・+67 件（navigation の単体テスト） |
| 結合テスト | 214 クラス・1,109 件 | 222 クラス・1,165 件 | +8 クラス・+56 件（navigation の結合テスト） |
| 失敗・飛ばし | 0 | 0（対象DB の3種類の結合テストも飛ばされていない） | — |
| 画面のテスト | — | 114 ファイル・1,046 件 passed（`allowedNavIcons.test.ts` の 3 件を含む） | — |

| パッケージ | 行 | 分岐 |
|---|---|---|
| `navigation.domain` | 100.0%（77/77） | 92.9%（52/56） |
| `navigation.service` | 100.0%（67/67） | 95.5%（21/22） |
| `navigation.web` | 92.1%（35/38） | 100.0%（14/14） |
| `role.domain`（読むだけ） | 94.2%（600/637） | 85.4%（305/357）（Step 3 と同じ） |
| `role.service`（読むだけ） | 96.6%（976/1,010） | 90.3%（346/383）（Step 3 と同じ） |
| `dsl.domain`（読むだけ） | 100.0%（321/321） | 96.1%（148/154）（Step 3 と同じ） |
| `dsl.service`（読むだけ） | 96.6%（229/237） | 92.4%（97/105）（Step 3 と同じ） |
| 全体 | 97.9%（10,519/10,742）（Step 3 は 97.9%（10,340/10,560）） | 92.4%（4,001/4,328）（Step 3 は 92.4%（3,914/4,236）） |
| 画面（全体） | 97.52%（3,035/3,112） | 92.68%（1,965/2,120） |

- 新しいパッケージ `navigation.domain`・`navigation.service`・`navigation.web` は、パッケージごとの下限（行 80%・分岐 70%）を満たす。`packagesJudgedByTotal` と除外は変えていない。
- verify のほかの関門: フォーマット・リンタ・ライセンスヘッダー・ビルド・カバレッジの下限・Gitleaks（no leaks found）・SpotBugs の関門（priority 1・`SQL_`・`PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION` の指摘なし。警告は 278 行で、navigation の分は Step 13 の記録のとおり既存と同じ種類）・成果物を通った。OSV-Scanner は lockfile が Step 3 と同じため入力が変わらず UP-TO-DATE（前の成功の結果のまま）。
- `verify` の時間は 14 分 30 秒（B6 の 13 分 49 秒から +41 秒）。
- E2E（Step 19、`caffeinate -i ./gradlew e2eTest`）: 177 passed（7.5 分）、失敗・不安定・飛ばし 0。報告の確かめの部品で残してはならない値は 0 件。報告の置き場は消した。B7 は E2E を足していない（流れ I は U7）。
- テストの量（Standard）: `MenuFilter` の例 12 件と性質 8 つ、`NavIconPolicy` 14 件、`AllowedNavIconList` 6 件、`NavigationService` の呼び出しの回数 8 件と振る舞い 9 件、値の型 5 件、境界テスト 5 件、結合テスト 8 クラス・56 件（認可の表 8・API 8・信頼できない入力 26・一致 7・記録 1・差し替え 4・失敗 1・接続 1）、画面 3 件。

## 4. 計画との差

| 差 | 中身 | 理由 |
|---|---|---|
| `NavigationModels` を足した | 単体テストのモデルの組み立ての手伝い（`testsupport`）。結合テストの `NavigationDslFixture` もこれを使う | 計画 4.4 の一覧に無いが、C1 の単体テストで要った |
| `NavigationActors.schemaRole` を足した | スキーマの階層の明示の値でロールを作る | 下の `permission_settings.table_name` の長さの件 |
| `NavigationUntrustedInputIT` の権限の付け方 | 300 文字のテーブルを含む 23 種の名前の権限を、テーブルの階層ではなくスキーマの階層の明示の値（継承）で付けた | `permission_settings.table_name` が `VARCHAR(256)` で、300 文字のテーブルに明示の設定を入れられなかった（5節） |
| 試し N1 の 23 種の数え方 | 手順書の並び（22 項目）の「`&` と `=`」を2つに分けて 23 とした | 試し N1 の表の数に合わせた |
| `NavigationQueryExposureIT` のスパンの集め手 | テストの中の `SpanProcessor` の `@TestConfiguration` で、終わったスパンの属性を（サニタイズの前に）集めた。集まったスパンが空でないことも確かめる | 計画が名指しした `TracingAndLoggingIT` に、スパンを集める部品が無かった |
| `allowedNavIcons.test.ts` の道の組み立て | `import.meta.url` ではなく `import.meta.dirname` から組み立てた | 画面のテストの環境（jsdom）では `import.meta.url` が file の形にならず失敗した。既存の `eslintImportRules.test.ts` と同じ形で、cwd に頼らない点（R-04）は同じ |
| 試し走りの間、配備したアプリを止めなかった | 計画の Step 14 は「配備したアプリはこの間止める」。指揮役の指示で、VM の余裕（空き約 4.9 GiB）を確かめたうえで止めずに使い捨ての環境（上限 2g）を並べた | 1 VU・10 秒の試し走りで判定に使わないため。本番の長さの判定の回は Performance Validation で手順 0 のとおり止める |
| Step 18 の verify を Step 17 の記録と並べて流した | verify を背景で流している間に記録（`aidlc/` の下だけ）を書いた。テストを流している間に別のビルドは流していない | 待ちの時間を減らすため。記録の書き込みはビルドの入力に入らない |

| 単体テストの JVM のヒープを 1.5g にした（計画 4.7 で変えないとした `backend/build.gradle.kts` の変更） | `tasks.test` に `maxHeapSize = "1536m"`（1.5g。Gradle は `1.5g` の小数の表記を読めず、1回目の verify が `Cannot parse memory amount notation: 1.5g` で止まったため同じ量の `1536m` で書いた）を1行（と説明のコメント）足した。共通の `tasks.withType<Test>` の `maxHeapSize = "1g"` は変えていないため、結合テストの JVM は 1g のまま。依頼者の決定 A | Step 18 の1回目の verify で、単体テストの JVM が既存の `RoleTransferReaderTest`（大きな YAML の読み込み）で `OutOfMemoryError: Java heap space` になった（下の表） |

再現の試み（Step 18、上限 30 分。テストのファイルを一時的に作業フォルダの外へ退け、流した後に元に戻した。どれも `:backend:cleanTest :backend:test`）:

| 条件 | ヒープの不足 |
|---|---|
| 今の作業フォルダ（verify の中・単体だけ、各1回） | 起きる |
| `NavigationBoundaryArchitectureTest` を外す | 起きる |
| `MenuFilterPropertyTest` を外す | 起きる |
| navigation のテストをすべて外す（本体はある） | 起きる |
| navigation の本体とテストを外す（Step 3 の基準と同じ本体） | 起きない |

- 見立て（未検証。ヒープの中身は測っていない）: 単体テストは1つの JVM で流れ、境界テスト（15 クラス）がそれぞれ全体のクラスの取り込み（ArchUnit の `JavaClasses`）を静的な値として持ち続けるため、ヒープが上限の近くにある。navigation の本体のクラスが増えた分だけ各取り込みが大きくなり、大きな YAML を読むテストの一時の確保で上限を超えたと見られる。足したテストを外しても起きるため、引き金は本体のクラスが増えたこと。
- 前例: Intent 260923-dsl-schema-loader の U4 で、同じ形（構造の検査がクラスを持ち続け、10MB を超える DSL のテストでヒープが尽きた）のため、テストの JVM のヒープを 1g にした（`project.md` の学び、`backend/build.gradle.kts` の共通の設定の説明）。
- 指揮役の指示の「その1行だけ」は、`maxHeapSize = "1g"` が単体と結合の両方に効く共通の行のため、共通の行を変えずに単体テストのタスクにだけ値を置く形で行った（結合テストは変えない、の指示に合わせた）。

承認済みの文書は書き換えていない（`cicd-pipeline.md` 5.3 の流す順との差は `perf/README.md` に書いた D-8）。

## 5. 承認の場で確かめたいこと

1. **role の表の名前の列の長さと、DSL が許すテーブル名の長さ**（role の単位の事実。role は変えていない）: `permission_settings.table_name` は `VARCHAR(256)` のため、256 文字を超える名前のテーブルにはテーブルの階層（とその下のカラム）の明示の設定を置けず、スキーマの階層の継承でだけ権限が付く。一方、DSL の名前（`ModelValues.requireName`）は空でないことだけを求め、長さの上限が無い（機能設計 R-01 で置き場の問い合わせも長さでは拒否しないことにした）。そのため「DSL にあり、置き場の問い合わせとメニューには出るのに、テーブルの階層で権限を設定できないテーブル」がありうる。実際の名前（対象DB の識別子の上限: MySQL 64 文字、PostgreSQL 63 バイト）では起きない（NFR 設計の読み直し R-07 の補い）。扱い（受け入れた制約として記録する／後の Intent で DSL の名前の長さの上限を置く・role の列を広げる）を決めてほしい。
2. **計画との差**（4節）の受け入れ。とくに `NavigationUntrustedInputIT` の権限の付け方と、試し走りで配備したアプリを止めなかったこと。
3. **D-13（`RoleBoundaryArchitectureTest` の navigation の規則の広げ）**の結果の確かめ: 広げたのは navigation の規則だけで、`role.repository`・`role.store`・`role.transfer`・`role.web` への依存は今までどおり禁じている。

## 6. 後に回すこと

- **正しくない `%` の並びの全体の直し**（NFR 設計 R-10）: 共通の誤りの変換（`common.error.web`）で `InvalidParameterException` を 400 `VALIDATION_FAILED` にし、例外の文をログに出さない（学びの段で `project.md` への記録を諮る）。
- **Performance Validation**: 準備（`rolePrepare` → `navPrepare`）の後、`navMenu`・`tableAccessVisible`・`tableAccessDenied` をロールの読み取りの場面と同じ大きな DSL の上で流し、`roleExport`・`roleTransferLargeCheck`・`roleTransferLargeApply` の後、最後に `navMenuBaseline`。置き場の p95 は場面ごとに別に残し、基準の値として記録する（`perf/README.md` の業務のメニューの節）。
- **Observability Setup**: group の区画の `uri` の条件に `/api/me/(navigation|table-access)` を足し、式の名前（`_milliseconds_`）と `le="1000"` を起動して確かめる（`monitoring-design.md` 5節・7節、基盤の R-06）。
- **配備の段**: 移行も設定の変更も無く、戻しはイメージだけ。前の版では版 2 の DSL を読めず DSL が無い状態で起動し、メニューは出ない（dsl-v2 の `logical-components.md` 7節、基盤の R-05）。
- **U7 app-frame-ui（B9）**: 機能設計 9節の引き継ぎに加えて、置き場の引数は `encodeURIComponent` で必ずエンコードする。make-you-chic-ui の固定先を上げてアイコンが変わったら、`allowed-icons.txt` と `allowedIcons.ts` の両方を直す（`allowedNavIcons.test.ts` が止める）。

## 7. 見せるもの（`curl` の手順）

```bash
# 手元のアプリ（例 http://localhost:8080）。TOKEN はログインした利用者のアクセストークン（値は表示しない）
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/me/navigation
#   → 200 {"items":[{"id":"0","label":{"ja":…,"en":…},"icon":"list","table":{…}|null,"navigable":…,"items":[…]}],"emptyReason":null}
curl -s -H "Authorization: Bearer $TOKEN" "http://localhost:8080/api/me/table-access?schema=large&table=t_001"
#   → 200 {"schemaName":"large","tableName":"t_001","displayName":{"ja":…,"en":…},"main":"READ"} か 403 ACCESS_DENIED
curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $TOKEN" "http://localhost:8080/api/me/table-access?schema=large&table="
#   → 400（VALIDATION_FAILED）
```
