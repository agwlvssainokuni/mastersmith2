# Build and Test のまとめ — Intent 261016-business-data-views

## 1. 全体の状態と前提

- 範囲: 5つの単位（U1 業務データの読み取り・U2 書き込み・U3 画面・U4 axe の持ち越し・U5 持ち越しのテスト）、Bolt B1〜B7 と CI の不安定なテスト2件の直し。すべて `develop` に統合済み（最後のコードのコミットは `d220447`、段の承認の記録は `288736d`）。
- Test Strategy: Standard（`aidlc-state.md`）。範囲は classic、深さは Standard。
- 依頼者の決定（`build-and-test-questions.md`）:
  - Q1: B — verify と E2E の全件は `develop` の今の形では流さない。各 Bolt の作業ブランチの上の最後の結果と CI の結果を正とする。
  - Q2: A — `dslMixed` の `memory.peak` の内訳をこの段で測る（別の担当。結果は `memory-peak-measurement.md`、後でできる）。
  - Q3: A — 業務データの k6 の場面は Performance Validation で判定する。この段は手順書と `k6 inspect` の確かめまで。
  - Q4: A — B5 の統合の後のすべてのコミットの CI を試行ごとに確かめて記録する（指揮役が後で足す）。
- ビルドの状態: 作業ブランチの上の最後の回（B7 のレビューの直しの後）で `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` が通過（23分24秒）、E2E の全件 256 件が通過（13分2秒）。
- 前提の道具と環境: `build-instructions.md` の 1・2節（JDK 25・Node 24・Gitleaks・OSV-Scanner・colima と2つの環境変数・Mailpit・見本の PG の 15432）。

## 2. 作ったテストの手順書

| ファイル | 中身 |
|---|---|
| `build-instructions.md` | 依存の入れ方・環境・`verify` と段ごとのコマンド・確かめ・よくある失敗 |
| `integration-test-instructions.md` | 単位の境目ごとの結合テスト、3種類の対象DB、E2E の流し方とデータの後始末 |
| `performance-test-instructions.md` | 業務データの k6 の場面（Performance Validation）、`dslMixed` の `memory.peak`（この段）、画面の時間（E2E 185）、時間の記録 |
| `security-test-instructions.md` | SpotBugs・OSV-Scanner・Gitleaks、構造の検査、認可・IDOR・SQL・漏えいの結合テスト、性質ベースのテスト、画面と E2E の確かめ |
| `test-results.md` | Bolt ごとの build と test の結果、失敗と扱い、不安定と確かめられていない3件、CI の不安定なテスト2件の直し。CI と `memory.peak` は後で足す |
| `cross-unit-traceability.md` | 要件の FR・NFR と受け入れ基準の網羅（2段の連鎖） |

## 3. 単位ごとのカバレッジ

| 単位 | 対象 | 値（最後に記録した回） | 下限 |
|---|---|---|---|
| U1 | `businessdata.domain`・`repository`・`service`・`web`、`targetdb.*`、`role.domain` | B2: 97.7%・90.7%、94.9%・86.0%、98.6%・93.6%、97.0%・74.3%、`targetdb.*` 90.5%〜100%・87.5%〜100%、`role.domain` 94.2%・85.7% | パッケージごとに行 80%・分岐 70% |
| U2 | `businessdata.*`・`targetdb.*`・`audit.domain`・`audit.service` | B4: `businessdata.web` 97.6%・77.9%、`targetdb.service` 89.1%・75.4%、`audit.service` 98.2%・84.8% ほか（`test-results.md` 2.2） | 同上 |
| U3 | `src/features/tables/` | 行 93.94%・分岐 88.39%（記録だけ。下限は全体の合計に当てる） | 全体の合計で行 80%・分岐 70% |
| U4 | `src/shared/row-actions/rowActionsName.ts` | 100%・100% | 同上 |
| U5 | 本体のソースを変えていない | — | — |
| 全体 | バックエンド・フロントエンド | バックエンド 行 97.64%・分岐 91.23%、フロントエンド 行 95.06%・分岐 89.82% | 行 80%・分岐 70% |

## Target Verification Matrix

全単位の NFR 要件・NFR 設計の測れる目標と、Testing Contract（team.md・org.md の Testing Posture の写し）の測れる目標を1行ずつ並べた。Actual と Evidence は作業ブランチの上の実測（Q1: B）。`Pending` は後で足す行（CI の試行ごとの結果と `memory.peak` の測り）、`Unverified` は持ち主の後の段が判定する行。

| Target ID | Source | Expected | Actual | Evidence | Owning Stage | Verdict |
|---|---|---|---|---|---|---|
| TC-01 | Testing Contract（team.md のカバレッジの下限） | バックエンドの全体の合計で行 80% 以上・分岐 70% 以上 | 行 97.64%・分岐 91.23% | U3 の `generation-notes.md` 11.4（B7 のレビューの直しの後の verify） | Build and Test | Met |
| TC-02 | Testing Contract（パッケージごとの下限、新しいパッケージ） | `businessdata.*`・`targetdb.*` などの新しいパッケージごとに行 80%・分岐 70% 以上 | `jacocoTestCoverageVerification` が通過。最も低い分岐は `targetdb.service` 75.4%・`businessdata.web` 77.9%（B4） | U2 の `code-summary.md` 3節、U3 の `generation-notes.md` 11.4 | Build and Test | Met |
| TC-03 | Testing Contract（フロントエンドの全体の合計） | 行 80% 以上・分岐 70% 以上 | 行 95.06%・分岐 89.82% | U3 の `generation-notes.md` 11.4 | Build and Test | Met |
| TC-04 | Testing Contract（除外を増やさない） | カバレッジの除外を増やさない | どの Bolt も除外を増やしていない | 各単位の `code-summary.md` の3節 | Build and Test | Met |
| TC-05 | Testing Contract（`packagesJudgedByTotal`） | 一覧の7パッケージの本体に手を入れたら下限を満たして一覧から外す。一覧を増やさない | どの Bolt も7パッケージの本体に触れていない。一覧は変わらない | U1・U2 の `code-summary.md` 1節 | Build and Test | Met |
| TC-06 | Testing Contract（verify の時間） | `./gradlew verify` が CI の上限の7割の約 42 分以内 | 23分24秒（最後の回） | `test-results.md` 2.1 | Build and Test | Met |
| TC-07 | Testing Contract（CI の時間） | CI の時間が約 42 分以内 | 後で足す | `test-results.md` 6節 | Build and Test | Pending |
| TC-08 | Testing Contract（3種類の対象DB） | MySQL・MariaDB・PostgreSQL の結合テストを verify の中で毎回、コンテナの飛ばし無し | どの回も3種類がコンテナで流れた。SKIPPED 9 はすべて DB の種類の `assumeTrue` | `test-results.md` 2.1 | Build and Test | Met |
| TC-09 | Testing Contract（E2E の本数） | 業務データの代表の流れは1本まで。持ち越しの直しと書き換えは数えない | 180 の1本 | U3 の `code-summary.md` 全体のまとめ | Build and Test | Met |
| TC-10 | Testing Contract（統合の前の E2E） | frontend の本体か認証・権限・業務データの `src/main` に触れるブランチは統合の前に E2E を通す | B2・B3・B4・B5・B6・B7 は統合の前に全件を通した。B1 と CI の直しはテストだけで verify のみ | `test-results.md` 2.3 | Build and Test | Met |
| TC-11 | Testing Contract（統合の前の1コマンドの関門） | フォーマット・リンタ・ライセンス・ビルド・全テスト・カバレッジ・秘密・静的解析・脆弱性が通ってから統合 | どの Bolt も通過した回で統合した | `test-results.md` 2.1・2.5 | Build and Test | Met |
| TC-12 | Testing Contract（CI の失敗の扱い） | 統合の後の CI の失敗を、次の Bolt の前に決まりどおりに扱う。試行ごとに記録する | B5 までの経緯は記録済み（`test-results.md` 5節）。B5 の後のコミットの試行ごとの結果は後で足す | `test-results.md` 5・6節 | Build and Test | Pending |
| TC-13 | Testing Contract（不安定なテストの決まり） | 同じテストが二度目に落ちたら、原因を直すまで次へ進まない | `eslintImportRules.test.ts` が B3 の 4.14.0 の試しと B7 の verify で同じ形（読み込みの時間切れ）で2度落ちたが、B7 は「不安定と確かめられていない」扱いで進めた | `test-results.md` 4節、U4 の `generation-notes.md` N-10、U3 の `generation-notes.md` 10.10 | Build and Test | Not Met |
| TC-14 | Testing Contract（性質ベースのテスト） | jqwik・fast-check の性質ベースのテストを置き、失敗時の乱数の種を記録 | jqwik 7 クラス・fast-check の性質のテストを置いた。B7 の `-0` は種 `1380462492`・path `79` を記録して再現した | U3 の `generation-notes.md` 10.6・10.10 | Build and Test | Met |
| TC-15 | Testing Contract（axe-core の引き上げ） | 規則を外さず違反を 0 件にして上げ、E2E の全件の通過と違反 0 件を件数で記録、vitest-axe も流し、版が lockfile で1つ | 4.14.0。E2E 256 件通過・違反 0 件、vitest-axe 86 ファイル・113 か所で違反 0 件、`npm ls axe-core` は1つ | `test-results.md` 2.4 | Build and Test | Met |
| TC-16 | Testing Contract（Standard の量） | 部品ごとに 5〜8 件の目安で単体テスト、境目の結合テスト | 単体 2,589・結合 1,386・画面 1,975 件。境目は `integration-test-instructions.md` 2節 | `test-results.md` 2.1 | Build and Test | Met |
| TC-17 | Testing Contract（project.md の Mandated の関門） | 脆弱性の High 以上 0 件・秘密の検出 0 件 | OSV-Scanner の失敗の条件 0 件（警告 18）、Gitleaks 0 件 | `test-results.md` 2.5 | Build and Test | Met |
| TC-18 | Testing Contract（SpotBugs の `SQL_` の関門） | `SQL_` の指摘は priority によらず止め、除外はクラス・メソッド・パターン名の3つで絞る | `SQL_` の残り 0 件。除外は `RowSqlExecutor` の7メソッドと2パターンだけ | U1・U2 の `code-summary.md` 3節 | Build and Test | Met |
| U1-NFR1.1 | U1 `nfr-requirements/performance-requirements.md`、`nfr-design/performance-design.md` | 読み取りの5場面（`bizDefinition`・`bizSearchFirst`・`bizSearchContains`・`bizSearchLast`・`bizDetail`）と `bizSearchLastSorted` を 10 VU・3 分、`iteration_duration` の p95 < 1000 ms・`checks` の率 1 | 試し走り（PG・1 VU・10 秒、判定ではない）の p95 は 2.1〜162.7 ms | U1 の `code-summary.md` 7節、`perf/README.md` | Performance Validation | Unverified |
| U1-NFR1.2 | U1 `performance-requirements.md` | 場面の判定の決まり。`k6 inspect --include-system-env-vars` で場面と閾値の名前、各場面 1 VU・10 秒の試し走りで指標が出る | 8場面の名前と閾値を確かめ、試し走りを2回流して全場面が通った | U1 の `code-summary.md` 3節（G28・G29） | Code Generation（B2） | Met |
| U1-NFR1.3 | U1 `performance-requirements.md` | 全体の件数と深い OFFSET を NFR1.1 の測りに含めて満たす | 測っていない | `perf/k6/scenarios.js` の `bizSearchFirst`・`bizSearchLast` | Performance Validation | Unverified |
| U1-NFR2.1 | U1 `scalability-requirements.md`、`nfr-design/scalability-design.md` | 悪い側の規模（`biz_item` 100,000 行・100 カラム、`biz_pair` 1,000 行、`biz_wide` 200 行）を種の台本で作り、行の数を記録 | PG の試し走りで 100,000・1,000・200 行。MySQL・MariaDB の種はまだ流していない | `perf/README.md` の試し走りの結果 | Performance Validation | Unverified |
| U1-NFR2.2 | U1 `scalability-requirements.md` | 1回の問い合わせで読む行が一覧はページの件数、詳細は 2 行を超えない（LIMIT と `setMaxRows`） | 3種類の DB の結合テストが通過 | `RowSqlExecutorTest`・`AbstractBusinessDataReadIT`（AC1.3.16） | Code Generation（B2） | Met |
| U1-NFR2.3 | U1 `scalability-requirements.md` | 1要求の接続の最大が定義は内部DB 1・対象DB 0、一覧と詳細は内部DB 1・対象DB 1、対象DB を借りている間は内部DB 0 本 | 結合テストが通過 | `BusinessDataConnectionUsageIT` | Code Generation（B2） | Met |
| U1-NFR2.4 | U1 `scalability-requirements.md` | 読み取りのプール 5 のまま、10 VU で p95 < 1 秒・待ちの時間切れの累計 0・503 の件数 0 | 試し走り（1 VU）で待ちの時間切れの累計 0。10 VU は測っていない | U1 の `code-summary.md` 7節 | Performance Validation | Unverified |
| U1-NFR2.5a | U1 `scalability-requirements.md` | 長い文字を一覧 100 字・詳細 10,000 字で切り印を付ける。受け取る量を上限 + 1 字に絞る | 切り方の境界と絞りの結合テストが3種類で通過 | `AbstractBusinessDataReadIT`・`ColumnValueCodecTest` | Code Generation（B2） | Met |
| U1-NFR2.5b | U1 `scalability-requirements.md`、`nfr-design/scalability-design.md` | `bizSearchWide` を 10 VU で流し、アプリの `memory.peak` < 上限 2g の 90%、`anon` の最大を並べる | 試し走り（1 VU）で 506〜621 MiB。10 VU は測っていない | `perf/README.md` の試し走りの結果 | Performance Validation | Unverified |
| U1-NFR3.1 | U1 `reliability-requirements.md`、`nfr-design/reliability-design.md` | 3種類の DB で定義・一覧・検索・詳細が同じ振る舞い（型・大文字と小文字・NULL の並び・部分一致・境界の値） | 3種類の結合テストが通過 | `PostgresBusinessDataReadIT`・`MysqlBusinessDataReadIT`・`MariadbBusinessDataReadIT` | Code Generation（B2） | Met |
| U1-NFR3.2 | U1 `performance-requirements.md` | NFR1.1 の対象DB の4場面を3種類で1つずつ流し、どれも満たす | 測っていない | `perf/README.md` の「業務データの場面」 | Performance Validation | Unverified |
| U1-NFR3.3 | U1 `reliability-requirements.md`、`nfr-design/reliability-design.md` 3節 | 時差つきの型の値はオフセット無しを UTC、`Z`・`±hh:mm` も受け、返す値は `+00:00`。JVM の既定のタイムゾーンに左右されない | 3種類の結合テストが通過 | `AbstractBusinessDataReadIT` | Code Generation（B2） | Met |
| U1-NFR4.1 | U1 `security-requirements.md`、`nfr-design/security-design.md` | SQL を組み立てて実行するのは1つの部品だけ。識別子は DSL の名前と照らして引用、値はバインド。`SQL_` の除外は3つで絞る | 境界テストと SpotBugs の関門が通過。引用符などを含む入力の結合テストが通過 | `BusinessDataBoundaryArchitectureTest`・`backend/config/spotbugs-exclude.xml` | Code Generation（B2） | Met |
| U1-NFR4.2 | U1 `security-requirements.md` | 行の値・検索の値・接続情報をログ・トレースの属性・応答に含めない。JDBC の例外のメッセージを出さない | TRACE の漏えいのテストとトレースのテストが通過 | `BusinessDataSecretLeakIT`・`BusinessDataTraceExposureIT` | Code Generation（B2） | Met |
| U1-NFR4.3 | U1 `security-requirements.md` | 読み取りの API は既定の候補にしない読み取りの `targetDataSource` を貸す形の口からだけ使う | 境界テストが通過 | `BusinessDataBoundaryArchitectureTest`・`JdbcTargetReadAccessTest` | Code Generation（B2） | Met |
| U1-NFR4.4 | U1 `security-requirements.md` | 一覧の問い合わせ1回ごと 5 秒・詳細 5 秒、通信の待ちは最長の上限 + 5 秒 | 設定の結合テストが通過 | `targetdb` の設定のテスト | Code Generation（B2） | Met |
| U1-NFR4.5 | U1 `security-requirements.md`、`nfr-design/security-design.md` 6節 | 本文 64 KB（ちょうどは受け、超えると 413）、検索の値と主キー 1,000 字（1,001 字は 400） | 境界のテストが通過 | `BusinessDataRequestLimitIT` | Code Generation（B2） | Met |
| U1-NFR4.6 | U1 `security-requirements.md` | 3本の API を「ログインだけ」に分類し、テーブルの権限は業務処理の層で判定 | 分類の網羅のテストが通過 | `ApiAccessConsistencyIT`・`ApiAccessArchitectureTest` | Code Generation（B2） | Met |
| U1-NFR4.8 | U1 `reliability-requirements.md`、`nfr-design/reliability-design.md` 5節 | 接続できない・答えないときに 503（UNAVAILABLE・TIMEOUT）。応答しない対象DB で約 13 秒以内（設計の線は3種類とも 25 秒以内） | つないだ後に止まる相手の 503 TIMEOUT は3種類とも約 10 秒 | U1 の `generation-notes.md`、`BusinessDataTimeoutIT` | Code Generation（B2） | Met |
| U1-NFR7.1 | U1 `security-requirements.md` | team.md の業務データの必須のテストのうち読み取りの分 | 書いて通過 | U1 の `traceability.json`（OK 165） | Code Generation（B2） | Met |
| U1-NFR7.2 | U1 `security-requirements.md` | 許す操作の関数と識別子の照合に jqwik の性質ベースのテスト | 通過 | `TableAccessPolicyPropertyTest`・`IdentifierMatchingPropertyTest`・`SearchOperatorPolicyPropertyTest`・`ContainsPatternPropertyTest` | Code Generation（B2） | Met |
| U1-NFR7.3a | U1 `performance-requirements.md` | B2 の後と Build and Test で verify の時間を記録し、約 42 分を超えたら諮る | B2 18分32秒、最後 23分24秒 | `test-results.md` 2.1 | Build and Test | Met |
| U1-NFR7.3b | U1 `performance-requirements.md` | CI の時間を記録し、約 42 分を超えたら諮る | 後で足す | `test-results.md` 6節 | Build and Test | Pending |
| U1-NFR8.1 | U1 `observability-requirements.md` | 警報 `ms-forbidden` のしきい値（20）を見直す | 確かめていない | U1 の `traceability.json`（Deferred） | Observability Setup | Unverified |
| U1-NFR8.2 | U1 `observability-requirements.md` | 失敗のログは WARN・スタックトレースなしで `reason`・`sqlState`・`vendorCode`・`exceptionClass` だけ | ログの JSON のテストが通過 | `BusinessDataSecretLeakIT` | Code Generation（B2） | Met |
| U1-NFR8.3 | U1 `observability-requirements.md` | ダッシュボードの業務データの区画（3本の p95・503 の数・プールの待ち）と既存の警報で拾えること | 確かめていない | U1 の `traceability.json`（Deferred） | Observability Setup | Unverified |
| U2-NFR1.4 | U2 `nfr-requirements/performance-requirements.md`、`nfr-design/performance-design.md` | `bizCreate`・`bizUpdate`・`bizDelete` を 10 VU で、p95 < 1000 ms・`checks` の率 1 | 試し走り（PG・1 VU）の p95 は 1.3〜4.6 ms | U2 の `code-summary.md` 3節 | Performance Validation | Unverified |
| U2-NFR1.5 | U2 `performance-requirements.md` | `bizWritePoolLimit` を 10 VU・3 分で p95 < 1 秒、かつ NFR2.7 の条件 | 試し走り（1 VU）の p95 1.8 ms | U2 の `code-summary.md` 3節 | Performance Validation | Unverified |
| U2-NFR1.6 | U2 `performance-requirements.md`、`nfr-design/performance-design.md` 3節 | 100 カラムに重い式と長い値を置いた検証の全体が実時間で 400 ms 以内（締め切りは注入した時計） | 単体テストが verify の中で通過 | `DeadlinePatternTimingTest` | Code Generation（B4） | Met |
| U2-NFR2.6 | U2 `scalability-requirements.md` | 作成・更新・削除の1要求で内部DB 1・書き込み 1・読み取り 0、書き込みを借りている間は内部DB 0 本 | 結合テストが通過 | `BusinessDataConnectionUsageIT` | Code Generation（B4） | Met |
| U2-NFR2.7 | U2 `scalability-requirements.md`、`nfr-design/scalability-design.md` 2節 | 書き込みのプール 5、10 VU で p95 < 1 秒・待ちの時間切れの累計 0・503 の件数 0 | 試し走り（1 VU）で累計 0・待ちの最大 5.9 ms | U2 の `code-summary.md` 3節 | Performance Validation | Unverified |
| U2-NFR2.8 | U2 `scalability-requirements.md`、`nfr-design/scalability-design.md` 4節 | 4 MiB に近い本文を 10 VU で、アプリの `memory.peak` < 上限 2g の 90%、`anon` の最大を並べる | 試し走り（1 VU・1 回）で 618 MiB・`anon` 610 MiB | U2 の `code-summary.md` 3節 | Performance Validation | Unverified |
| U2-NFR2.9 | U2 `scalability-requirements.md` | 監査の行の数と内部DB の大きさを数え、見積もり（1行 約 0.5 KB）と並べる | 測っていない | `perf/README.md` の「業務データの書き込みの場面」 | Performance Validation | Unverified |
| U2-NFR3.4 | U2 `performance-requirements.md` | NFR1.4・NFR1.5 の4場面を3種類で流し、どれも満たす | 測っていない | `perf/README.md` | Performance Validation | Unverified |
| U2-NFR3.5 | U2 `reliability-requirements.md`、`nfr-design/reliability-design.md` | 3種類で作成・更新・削除が同じ振る舞い（件数・2行以上・制約の違反・丸めない・後勝ち） | 3種類の結合テストが通過（別の接続で読み直し） | `AbstractBusinessDataWriteIT` を継ぐ3つの IT | Code Generation（B4） | Met |
| U2-NFR3.6 | U2 `reliability-requirements.md` | 時差つきの型の入力を UTC にそろえ型つきでバインド、JVM のタイムゾーンに左右されない | 3種類の結合テストが通過 | `AbstractBusinessDataWriteIT` | Code Generation（B4） | Met |
| U2-NFR4.1 | U2 `security-requirements.md` | 書き込みの SQL も `RowSqlExecutor` だけ。識別子の照合・型つきのバインド・`SQL_` の除外は書き込みのメソッドに絞る | 境界テストと SpotBugs の関門が通過。引用符などを含む入力の結合テストが通過 | `BusinessDataBoundaryArchitectureTest`・`backend/config/spotbugs-exclude.xml` | Code Generation（B4） | Met |
| U2-NFR4.2 | U2 `security-requirements.md` | 行の値・主キーの値・接続先をログ・トレース・応答・監査の失敗のログに含めない | 漏えいとトレースの結合テストが通過 | `BusinessDataWriteSecretLeakIT`・`BusinessDataWriteTraceExposureIT` | Code Generation（B4） | Met |
| U2-NFR4.3 | U2 `security-requirements.md` | 書き込みの接続は別のプール、資格情報は `.env` だけ、名前つきで既定の候補にしない。書き込みのアカウントは DDL を拒まれる | 結合テストが3種類で通過 | `TargetWriteTransactionManagerInjectionIT`・`TargetDbSampleInitScriptIT`・`AbstractBusinessDataWriteIT` | Code Generation（B4） | Met |
| U2-NFR4.4 | U2 `security-requirements.md` | 書き込みの文ごとに 5 秒、通信の待ち 10 秒、文は最大3つ | 設定と時間切れの結合テストが通過 | `BusinessDataWriteTimeoutIT` | Code Generation（B4） | Met |
| U2-NFR4.7 | U2 `security-requirements.md` | pattern は値ごと 100 ms・合計 300 ms、超えたら `PATTERN_TIMEOUT`、別のスレッドを残さない | 境界（ちょうど・超え）の単体テストと性質のテストが通過 | `DeadlinePatternMatcherTest`・`DeadlinePatternMatcherPropertyTest` | Code Generation（B4） | Met |
| U2-NFR4.9 | U2 `security-requirements.md` | 作成・更新 4 MiB・削除 64 KB の境界（超えると 413）、値 10,000 字・主キー 1,000 字の境界 | 境界のテストが通過 | `BusinessDataWriteRequestLimitIT`・`WriteRequestValidatorTest` | Code Generation（B4） | Met |
| U2-NFR4.10 | U2 `security-requirements.md` | 3本を「ログインだけ」に分類、書けないカラムは 400、`definitionId` の差し替えは 409、401・403・200 の表 | 結合テストが通過 | `BusinessDataWriteAuthorizationApiIT`・`ApiAccessConsistencyIT` | Code Generation（B4） | Met |
| U2-NFR4.11 | U2 `security-requirements.md` | 書き込みの設定の片方だけなら項目の名前だけの WARN、起動と読み取りは続き、書き込みは 409 | 結合テストが通過 | `BusinessDataWriteNotConfiguredIT` | Code Generation（B4） | Met |
| U2-NFR4.12 | U2 `reliability-requirements.md`、`nfr-design/reliability-design.md` | 失敗を保存されたかで分けて返す。応答しない対象DB の最悪はおよそ 18 秒（設計の確かめの線は3種類とも 25 秒以内） | 書き込みの文・確定・違反の後の時間切れは3種類とも 10.01〜10.06 秒で 503 | U2 の `generation-notes.md` N22、`BusinessDataWriteTimeoutIT` | Code Generation（B4） | Met |
| U2-NFR4.13 | U2 `security-requirements.md` | 監査の3列と `detail` の主キー、16,384 字の境界で切り `keyTruncated`、理由の値は 32 字以内 | 結合テストと単体テストが通過 | `BusinessDataWriteAuditIT`・`AuditDetailBuilderTest`・`BusinessDataAuditReasonTest` | Code Generation（B4） | Met |
| U2-NFR4.14 | U2 `security-requirements.md` | 主キー・ビューの 409 は補助権限を持つ人の拒否だけを監査に残す | 結合テストが通過 | `BusinessDataWriteAuditIT` | Code Generation（B4） | Met |
| U2-NFR4.15 | U2 `reliability-requirements.md` | 時間切れの監査の行が「確定したかは分からない」と読める（`UNKNOWN`） | 結合テストが通過 | `BusinessDataWriteAuditIT`・`AuditEventListenerTest` | Code Generation（B4） | Met |
| U2-NFR4.16 | U2 `security-requirements.md` | MySQL・MariaDB の書き込みのアカウントの付与の範囲を確かめ、残る広さを書く | 初期化の台本の結合テストが通過。残る広さは README | `TargetDbSampleInitScriptIT`、`README.md` の「既にある見本の DB に書き込みのアカウントを足す」 | Infrastructure Design・Code Generation（B4） | Met |
| U2-NFR7.4a | U2 `performance-requirements.md` | B4 の後と Build and Test で verify の時間を記録し、約 42 分を超えたら諮る | B4 20分6秒（直しの後 21分4秒）、最後 23分24秒 | `test-results.md` 2.1 | Build and Test | Met |
| U2-NFR7.4b | U2 `performance-requirements.md` | CI の時間を記録し、約 42 分を超えたら諮る | 後で足す | `test-results.md` 6節 | Build and Test | Pending |
| U2-NFR7.5 | U2 `security-requirements.md` | team.md の業務データの必須のテストのうち書き込みの分（スキーマをクラスごとに作り、別の接続で読み直す） | 書いて通過 | U2 の `traceability.json`（OK 150） | Code Generation（B4） | Met |
| U2-NFR7.6 | U2 `security-requirements.md` | `WriteRequestValidator` と pattern の打ち切りに jqwik の性質ベースのテスト | 通過 | `WriteRequestValidatorPropertyTest`・`DeadlinePatternMatcherPropertyTest` | Code Generation（B4） | Met |
| U2-NFR8.4 | U2 `observability-requirements.md` | 書き込みの失敗のログは WARN でキー4つだけ、監査の失敗の ERROR から `detail`・主キーを外す | 結合テストが通過 | `BusinessDataWriteSecretLeakIT`・`BusinessDataWriteAuditIT` | Code Generation（B4） | Met |
| U2-NFR8.5 | U2 `observability-requirements.md` | ダッシュボードに書き込みの3本の p95・409 と 503 の数・書き込みのプールの待ちを足す | 確かめていない | U2 の `traceability.json`（Deferred） | Observability Setup | Unverified |
| U2-NFR8.6 | U2 `observability-requirements.md` | 既存の警報（`ms-pool-pending`・`ms-audit-fail`・`ms-audit-slow`・`ms-5xx-ratio`・`ms-forbidden`）が書き込みも拾う | 確かめていない | U2 の `traceability.json`（Deferred） | Observability Setup | Unverified |
| U3-NFR1.7a | U3 `nfr-requirements/performance-requirements.md`、`nfr-design/performance-design.md` | 件数 100 を選んでから 100 件 × 100 カラムの表まで 1.0 秒以内（中央値） | 中央値 70.1 ms（B6）。B5 は中央値 72.4 ms・最大 88.3 ms | U3 の `code-summary.md` B6 3節、`generation-notes.md` Step 13 | Build and Test | Met |
| U3-NFR1.7b | 同上 | メニューから既定の 20 件の表まで 0.5 秒以内（中央値） | 中央値 50.1 ms（B6）。B5 は 54.1 ms・最大 58.6 ms | 同上 | Build and Test | Met |
| U3-NFR1.7c | 同上 | 行の「詳細」から 100 カラムが出て見出しにフォーカスが移るまで 0.5 秒以内（中央値） | 中央値 45.2 ms（B6）。B5 は 46.6 ms・最大 47.5 ms | 同上 | Build and Test | Met |
| U3-NFR1.7d | 同上 | 「更新」から 100 カラムの入力まで 0.5 秒以内（中央値） | 中央値 32.8 ms（B6） | U3 の `code-summary.md` B6 3節 | Build and Test | Met |
| U3-NFR1.7e | 同上（R-11） | 詳細から 100 件の一覧へ戻るまで 1.0 秒以内（中央値） | 中央値 179.0 ms（B6）。B5 は 176.1 ms・最大 177.5 ms | 同上 | Build and Test | Met |
| U3-NFR1.8 | U3 `performance-requirements.md` | 測りは 185 の中の1件、既定の1組、各 5 回の中央値で判定し最大と1回目を並べる、値は名前と時間だけ | この形で測った | `frontend/e2e/support/businessDataMeasure.ts` | Code Generation（B5・B6） | Met |
| U3-NFR1.9 | U3 `performance-requirements.md` | 定義は記憶にあれば読まない、一覧の操作1回で一覧の API は1本、古い答えを捨てる、ポーリングをしない | フックのテストが通過 | `useRowSearch.test.ts` ほか | Code Generation（B5・B6） | Met |
| U3-NFR1.10 | U3 `performance-requirements.md` | 画面を `lazy` で登録し、入口と画面の chunk の gzip を前と後で記録（目安 500 KB） | 入口 159.3 → 162.0 → 163.9 KB、画面の chunk を記録 | `test-results.md` 2.5 | Code Generation（B5・B6） | Met |
| U3-NFR2.10 | U3 `performance-requirements.md` | 100 × 100 の表を仮想化なしで描き、記憶は 100 テーブル分・印 100 個 | 単体テストと測りが通過 | `businessDataStore.test.ts`、U3-NFR1.7a | Code Generation（B5） | Met |
| U3-NFR2.11 | U3 `performance-requirements.md` | 一覧は CSS で省略し DOM の文字を切らない、切ったセルに印 | 部品のテストが通過 | `BusinessDataTable.test.tsx` | Code Generation（B5） | Met |
| U3-NFR3.7 | U3 `performance-requirements.md` | 画面は DB の種類で分岐しない、時差つきは `+00:00` で出し送る | 書式の単体テストが通過 | `valueFormat.test.ts` | Code Generation（B5・B6） | Met |
| U3-NFR4.2 | U3 `nfr-requirements/security-requirements.md` | 主キー・行の値を道・履歴・ブラウザの保存・DOM の `id`・`data-testid`・`console`・E2E の記録に置かない | 本体の検索で 0 件、E2E の秘密の確かめで 0 件 | U3 の `generation-notes.md` 4.1・7.1、`test-results.md` 2.3 | Code Generation（B5・B6） | Met |
| U3-NFR4.20 | 同上 | 値と表示名を文字として描き、リンクの先は道を作る1か所の関数だけ | 部品のテストが通過、`dangerouslySetInnerHTML` 0 件 | `BusinessDataTable.test.tsx` | Code Generation（B5・B6） | Met |
| U3-NFR4.21 | 同上 | 行の操作の `data-testid` は `tables-row-actions-r<n>` で主キーを含めない | 部品のテストが通過 | `RowActionsMenu.test.tsx` | Code Generation（B5） | Met |
| U3-NFR4.22 | 同上 | 印は最大 100 個で古いものから捨てる、一覧の状態は 100 テーブル分、ログアウト・トークンの更新の失敗・別の利用者のログインで全部消す | 単体テストが通過 | `businessDataStore.test.ts`・`loginScope.test.ts` | Code Generation（B5） | Met |
| U3-NFR4.23 | 同上 | 値の長さと本文の大きさを画面でも確かめ、上限 + 1 は送らない、413 の文を出す | 単体テストと画面のテストが通過 | `businessDataApi.test.ts`・`formValidation.test.ts` | Code Generation（B5・B6） | Met |
| U3-NFR4.24 | 同上 | 応答を `unknown` で受け、`formPart`・`nullable`・`required` の欠けと `lookup` と知らない値は形の崩れ | 写しの関数の単体テストと性質のテストが通過 | `businessDataParse.test.ts`・`businessDataParse.property.test.ts` | Code Generation（B5・B6） | Met |
| U3-NFR4.25 | 同上 | E2E の対象DB の資格情報はプロセスの環境変数で渡し、報告の確かめで値が無い | 見つかった件数 0（B6・B7） | `frontend/e2e/support/secretValues.ts`、`test-results.md` 2.3 | Code Generation（B6） | Met |
| U3-NFR4.26 | 同上 | 画面の出し分けをサーバーの判定の代わりにしない、直接移ると 403 の表示 | 画面のテストが通過（サーバーは U1・U2 の結合テスト） | `BusinessDataListPage.test.tsx` | Code Generation（B5・B6） | Met |
| U3-NFR5.6 | 同上、`nfr-design/security-design.md` 7節 | 実際のブラウザの axe を 20 組と 200% の2件で、規則・要素を外さず流す。部品ごとに vitest-axe | 違反 0 件（E2E 185 を含む全件）、vitest-axe 違反 0 件 | `test-results.md` 2.4 | Code Generation（B5・B6）、Build and Test | Met |
| U3-NFR5.7 | 同上 | 開いたメニュー・Modal・Tooltip が画面の中に収まり、画面全体が横にスクロールしない（375px を含む） | E2E 185 のはみ出しの確かめが通過 | `frontend/e2e/185-business-data-accessibility.e2e.ts` | Code Generation（B5・B6） | Met |
| U3-NFR5.8 | 同上 | B5・B6 の 4.14 の試しで違反と区分を記録、選択子を出さない | B5・B6 とも違反 0 件、行の操作 3,160・行のリンク 2,440 が passes | U3 の `generation-notes.md` 4.3・7.3 | Code Generation（B5・B6） | Met |
| U3-NFR5.9 | 同上 | 行のリンクの見える文字が空にならず、名前に含まれる | 性質のテストと部品のテストが通過 | `rowLabel.test.ts` | Code Generation（B5） | Met |
| U3-NFR5.10 | 同上 | キーボードだけでメニューから削除の確かめまで行える、`aria-live`・フォーカスの移動 | E2E 180 と部品のテストが通過 | `frontend/e2e/180-business-data-flow.e2e.ts` | Code Generation（B5・B6） | Met |
| U3-NFR6.3 | 同上 | 日英の文言、413 の文を含む、鍵が日英でそろう | 単体テストが通過 | `messages.test.ts` | Code Generation（B5・B6） | Met |
| U3-NFR6.4 | 同上 | 保存の 403 の別の文を日英で用意する | 用意した | `frontend/src/features/tables/problemMessage.ts` | Code Generation（B6） | Met |
| U3-NFR7.8a | U3 `performance-requirements.md` | E2E の全件の時間が基準の 1.5 倍（約 15分53秒）を超えたら諮る | 13分2秒（約 1.23 倍） | `test-results.md` 2.3 | Build and Test | Met |
| U3-NFR7.8b | 同上 | verify の時間を B5・B6 の後と Build and Test で記録 | B5 20分27秒、B6 20分43秒、最後 23分24秒 | `test-results.md` 2.1 | Build and Test | Met |
| U3-NFR7.8c | 同上 | CI の時間を記録 | 後で足す | `test-results.md` 6節 | Build and Test | Pending |
| U3-NFR7.9 | U3 `security-requirements.md` | team.md の業務データの必須のテストのうち画面の分（`waitFor` で待つ、時間の上限を延ばさない） | 書いて通過 | U3 の `traceability.json`（OK 125） | Code Generation（B5・B6） | Met |
| U3-NFR7.10 | 同上 | 純粋な関数に fast-check の性質ベースのテスト、種を記録 | 通過、B7 の `-0` は種を記録して再現した | `businessDataParse.property.test.ts`・`formDiff.property.test.ts` | Code Generation（B5・B6） | Met |
| U3-NFR7.11 | 同上 | 「1回だけ飛ばす」の旗のテスト | 単体テストと画面のテストが通過（R-05 の直しの二段を含む） | `useUnsavedGuard.test.tsx` | Code Generation（B6・B7） | Met |
| U3-NFR7.12 | 同上 | フロントエンドの下限を保ち、`src/features/tables/` の値を記録 | 全体 行 95.06%・分岐 89.82%、`src/features/tables/` 行 93.94%・分岐 88.39% | `test-results.md` 2.2 | Build and Test | Met |
| U3-NFR7.13 | 同上 | 代表の流れ 180 を1本、前提の確かめ、見本と本物の形を毎回比べる | 180 が通過、前提の確かめ (a)〜(d) が毎回通過 | `frontend/e2e/180-business-data-flow.e2e.ts` | Code Generation（B6）、Build and Test | Met |
| U3-NFR8.7 | 同上 | 403 を受けても自動で送り直さない | フックのテストが通過 | `frontend/src/features/tables/tableHookSupport.ts` のテスト | Code Generation（B5・B6） | Met |
| U4-NFR4.2 | U4 `nfr-requirements/security-requirements.md` | 名前の関数と ShellLayout は名前の値をログ・トレースに出さない | 5つのファイルの検索で 0 件 | U4 の `generation-notes.md` Step 14 | Code Generation（B3） | Met |
| U4-NFR4.5 | 同上 | axe の記録は規則・`data-testid`・要素の種類だけ、選択子と値を出さない | 単体テストと json の報告の検索で値なし | `axeElementKey.test.ts` | Code Generation（B3） | Met |
| U4-NFR4.6 | 同上 | 名前は文字列として作り HTML として描かない | 性質のテストが通過 | `rowActionsName.property.test.ts` | Code Generation（B3） | Met |
| U4-NFR4.7 | 同上 | E2E とテストの値は架空のものだけ、報告の秘密の確かめが通る | 見つかった件数 0 | `test-results.md` 2.3 | Code Generation（B3） | Met |
| U4-NFR4.8 | 同上、`nfr-design/security-design.md` 4節 | 採る axe-core の版の確かめ（版・license・`hasInstallScript`・`npm ls` が1つ・`dist` に入らない） | 4.14.0・MPL-2.0・`hasInstallScript` 無し・1つ・`dist` 0 件 | U3 の `generation-notes.md` 10.3・10.10 | Code Generation（B7） | Met |
| U4-NFR4.9 | 同上 | 固定先を上げる手順（上流の `main` から辿れる・vendor の lockfile の差・`hasInstallScript`・前後のハッシュ・専用のコミット） | `a9309c4` → `b0a1e79`、上流から辿れる、lockfile の差なし、`hasInstallScript` は1件のまま | U4 の `generation-notes.md` Step 2・3 | Code Generation（B3） | Met |
| U4-NFR5.1 | 同上 | WCAG 2.0・2.1 の A・AA で 20 組、`REQUIRED_RULES` に `label-content-name-mismatch` | 足して全件を流した | `frontend/e2e/support/axe.ts` | Code Generation（B7） | Met |
| U4-NFR5.2 | 同上 | B7 の時点で E2E の全件が通り、E2E と vitest-axe の違反 0 件、`KNOWN_VIOLATIONS` は空 | 256 件通過・違反 0 件・vitest-axe 違反 0 件・空のまま | `test-results.md` 2.4 | Code Generation（B7） | Met |
| U4-NFR5.3 | 同上、`nfr-design/logical-components.md` 3節 | 対象の区分（C1〜C9）が passes に入り incomplete・violations に入らない、要素 0 件は失敗 | C1 882・C2 40・C3 129・C4 420（130 の場面）・C5 1,078・C6 176・C7 80・C8 3,160・C9 2,440、incomplete・violations 0 | `test-results.md` 2.4 | Code Generation（B7） | Met |
| U4-NFR5.4 | 同上 | B3 の試しで区分と違反を記録し、4.13 に戻したことを差で確かめる | 違反 0 件、戻した後の差なし | U4 の `generation-notes.md` Step 19・20 | Code Generation（B3） | Met |
| U4-NFR5.5 | 同上 | jsdom では名前を `getByRole` の `name` で確かめる | 部品のテストが通過 | `UserRowActions.test.tsx` ほか | Code Generation（B3） | Met |
| U4-NFR6.1 | 同上 | 鍵を日英で用意し、英語の `busySuffix` の先頭の空白が保たれる | 単体テストが通過 | `frontend/src/features/useradmin/messages.ts` のテスト | Code Generation（B3） | Met |
| U4-NFR6.2 | 同上 | 氏名が空なら頭文字と区切りを省く | 部品のテストが通過 | `ShellLayout.test.tsx` | Code Generation（B3） | Met |
| U4-NFR7.1 | U4 `nfr-requirements/performance-requirements.md` | 名前を作るために描画の回数も API の呼び出しも増やさない | 一覧の API の呼び出しが1回のままのテストが通過 | `ShellLayout.test.tsx`・3つのページのテスト | Code Generation（B3） | Met |
| U4-NFR7.2 | 同上、`nfr-design/performance-design.md` 3節 | E2E の全件の時間を B3・B7・Build and Test で記録し基準と並べる（上限なし） | B3 10分43秒、B7 13分29秒、最後 13分2秒（基準 約 10分35秒） | `test-results.md` 2.3 | Build and Test | Met |
| U4-NFR7.3a | U4 `performance-requirements.md` | verify の時間を B3・B7 の後に記録 | B3 17分37秒、B7 21分50秒、最後 23分24秒 | `test-results.md` 2.1 | Build and Test | Met |
| U4-NFR7.3b | 同上 | CI の時間を記録 | 後で足す | `test-results.md` 6節 | Build and Test | Pending |
| U4-NFR7.4 | U4 `security-requirements.md` | `rowActionsName` に fast-check の性質ベースのテスト（4つの性質） | 通過、失敗なし | `rowActionsName.property.test.ts` | Code Generation（B3） | Met |
| U4-NFR7.5 | 同上 | 書き換えの対象のテストと E2E を名前の検索で洗い出し直す | 検索の流し直しで残りは別の文言だけ | U4 の `generation-notes.md` Step 14 | Code Generation（B3） | Met |
| U4-NFR7.6 | 同上 | 固定先の後も説明を確かめる既存のテストが書き換えずに通る | 6 ファイル・62 件が通過 | U4 の `generation-notes.md` Step 4 | Code Generation（B3） | Met |
| U4-NFR7.7 | 同上 | フロントエンドの下限を保ち除外を増やさない | 行 95.37%・分岐 90.34%（B3） | U4 の `code-summary.md` 3節 | Code Generation（B3） | Met |
| U5-BR1 | U5 `functional-design/`（BR1.1〜BR1.6）、要件 FR15 | `RoleConcurrencyIT` の #7 を待ち合わせで確実に作り、直す前は落ち直した後は通る二段、5回の繰り返し | 直す前の確かめ方で落ち、2つの順の場合が通過、5回とも5件通過・`ROLE_BUSY` 0 件 | U5 の `generation-notes.md` Step 4〜6 | Code Generation（B1） | Met |
| U5-BR2 | U5 `functional-design/`（BR2.1〜BR2.5） | `AdminForbiddenProvider.test.tsx` の対象の1件の二段（T1 で落ち T2 で通る）と5回の繰り返し | T1 は5回とも落ち、T2 は5回とも通過、直したファイル 12 件を5回通過 | U5 の `generation-notes.md` Step 7・8 | Code Generation（B1） | Met |
| U5-BR-CI | U5 `code-summary.md` 4節（Build and Test に残ること） | 直した2件の CI の試行ごとの結果 | 後で足す | `test-results.md` 6節 | Build and Test | Pending |
| CIFIX-01 | 段の `construction/code-generation/generation-notes.md`（CI の不安定なテスト2件の直し） | `PasswordChangePage.test.tsx` と `H2CompactionByPoolSuspensionIT` を待ち合わせで確実に起こし、直す前は落ち直した後は通る二段と5回の繰り返し | どちらも二段を記録、5回とも通過、直しの後の verify 通過 | `test-results.md` 5節 | Code Generation | Met |
| U5-BR3.1 | U5 `functional-design/`（BR3.1〜BR3.3）、`nfr-design/logical-components.md` | 記録のループと1回の測りの台本、列が正しいこと、切れた行と `trap` の後始末 | 空回しで5行・`NA` 0 件、5つのキーが一致、`--tsv-check` と TERM・INT の確かめが通った | U5 の `generation-notes.md` Step 11・12 と 4節 | Code Generation（B1） | Met |
| U5-BR3.4 | U5 `functional-design/`（BR3.4） | 4回の測りで条件をそろえ、WARN を数えてから片付ける | 後で足す | `memory-peak-measurement.md`（後でできる） | Build and Test | Pending |
| U5-BR3.5 | U5 `functional-design/`（BR3.5）、要件 FR16.4 | 既定の2回で `memory.peak / memory.max < 0.9` が2回とも成り立つ。物差しは変えない | 後で足す | `memory-peak-measurement.md`（後でできる） | Build and Test | Pending |
| U5-BR4.1 | U5 `functional-design/`（BR4.1）、`nfr-requirements/security-requirements.md` の NFR4.7 | 試しの2回で `RETENTION_TIME` が 0、既定の回が 45000 であることを `settings.txt` で確かめる | 後で足す（確かめの関数は Code Generation で 19 件通過） | `memory-peak-measurement.md`（後でできる） | Build and Test | Pending |
| U5-BR4.2 | U5 `functional-design/`（BR4.2・BR4.3） | 試しの結果で、取り込むかを依頼者に諮る（効いた・効かなかったときの記録） | 後で足す | `memory-peak-measurement.md`（後でできる） | Build and Test | Pending |
| U5-NFR4.5 | U5 `nfr-requirements/security-requirements.md`、`nfr-design/security-design.md` S2 | 秘密の検索の件数がどれも 0、片付けで一時ディレクトリとボリュームが消える | 後で足す | `memory-peak-measurement.md`（後でできる） | Build and Test | Pending |
| U5-NFR4.6 | 同上 | 新しい記録に書くのは数値・時刻・状態・ロガーの名前と件数だけ（`env.txt` と TSV の件数の確かめ） | 後で足す（列の形は Code Generation で確かめた） | `memory-peak-measurement.md`（後でできる） | Build and Test | Pending |
| U5-NFR4.7 | 同上、`nfr-design/security-design.md` S1 | `PERF_DB_URL` は決めた形だけを受け付け、`INIT` を受け付けない。外れたら環境を作らずに止まる | 試し 19 件がすべて期待どおり、値の漏れ 0 件、悪い値で終了コード 1 | U5 の `generation-notes.md` Step 9・10 | Code Generation（B1） | Met |
| U5-NFR4.8 | 同上 | 指標の公開は一時の `app.env` だけ、待ち受けは 127.0.0.1:18080 | `dsl-timing.sh` の形のまま | `perf/dsl-timing.sh` | Code Generation（B1） | Met |
| U5-NFR4.9 | 同上 | 記録のループは読むだけで追加の権限を付けない | `--privileged`・`--user` を付けない形 | `perf/dsl-mixed-memory.sh` | Code Generation（B1） | Met |
| U5-NFR7.1 | 同上 | 失敗の文に token・email を出さない、補助を `testsupport` に置く | `Actor.toString` で伏せ、失敗の文は `detail` だけ | `RoleActorsTest` | Code Generation（B1） | Met |
| U5-NFR7.2 | 同上、`nfr-design/logical-components.md` 4節 | 待ち合わせの後始末と、放した理由の判定 | `disarm()` を `@AfterEach` で呼ぶ、`releasedByTimeout` を確かめる | `AuditWriteReorderBarrierTest` | Code Generation（B1） | Met |
| U5-NFR7.3 | 同上 | 時間に頼らない（`sleep` と上限の延長なし） | `CountDownLatch` とスレッドの状態で順を作った | `RoleConcurrencyIT` | Code Generation（B1） | Met |

判定ごとの件数（147 行）: Met 117・Not Met 1・Unverified 16・Pending 13。

- Unverified の内訳: Performance Validation 12（U1 の NFR1.1・NFR1.3・NFR2.1・NFR2.4・NFR2.5b・NFR3.2、U2 の NFR1.4・NFR1.5・NFR2.7・NFR2.8・NFR2.9・NFR3.4）、Observability Setup 4（U1 の NFR8.1・NFR8.3、U2 の NFR8.5・NFR8.6）。どちらの段も実行の計画にある（`aidlc-state.md`）。目標は緩めていない。
- Pending の内訳: CI 7（TC-07・TC-12・U1-NFR7.3b・U2-NFR7.4b・U3-NFR7.8c・U4-NFR7.3b・U5-BR-CI）、`memory.peak` 6（U5-BR3.4・U5-BR3.5・U5-BR4.1・U5-BR4.2・U5-NFR4.5・U5-NFR4.6）。

## 4. この段の手順書が受け持つテストの種類

| 種類 | 手順書 | 流す場 |
|---|---|---|
| 単体テスト（JUnit・Vitest・vitest-axe・jqwik・fast-check） | 各単位の `unit-test-instructions.md` | `verify` の 5 の段 |
| 結合テスト（Spring・H2・Testcontainers の3種類の対象DB・ArchUnit） | `integration-test-instructions.md` | `verify` の 6 の段 |
| E2E（Playwright、axe の 20 組・200%・画面の時間の測り） | `integration-test-instructions.md`・`performance-test-instructions.md` 4節 | `./gradlew e2eTest`（`verify` と CI の外） |
| セキュリティ（SpotBugs・OSV-Scanner・Gitleaks・漏えいと認可の結合テスト） | `security-test-instructions.md` | `verify` の 8 の段と結合テスト |
| 性能（k6 の業務データの場面、`dslMixed` の `memory.peak`） | `performance-test-instructions.md` | 使い捨ての環境（Performance Validation とこの段） |

## 5. 既知の制約（承認の場で受け入れたもの・後の Intent へ持ち越すもの）

| # | 中身 | 出典 |
|---|---|---|
| G13 | MySQL・MariaDB の整数の型は DSL の型の名前から符号なしかが分からず、符号つきの範囲の外の値は 400 でなく DB の拒否の 409 になる | U2 の `code-summary.md` 6節 |
| G15 | MySQL の件数の問い合わせが止まると、時間切れの後に止まった接続がプールに戻り、次の1回も 503 になる | U2 の `code-summary.md` 6節 |
| U1 R-04（G7） | 主キーが NONE のテーブルでも主キーで並べ、深いページの先読みに使う | 段の `memory.md` |
| U3 R-04 | 1語の氏名のトップバーのトリガーは `label-content-name-mismatch` が incomplete（658 件）のまま判定が出ない | U3 の `generation-notes.md` 11.5 |
| U3 R-07 | 行のリンクを新しいタブで開く・写すと一覧へ戻る（主キーを道に置かない設計のため） | 同上 |
| U3 R-09 | 保存の後に画面を離れると、権限の取り上げと定義の更新の知らせも捨てられる。次に一覧を開いたときに読み直す | 段の `memory.md`（承認の場の決定） |
| 詰め直しの道具 | `docker/hikari-pool.sh compact` が補充の途中に流すと1本残って詰め直さずに終わりうる（流し直せば済む） | 段の `generation-notes.md` 4節 |
| 切り替えの直後の知らせ | パスワードの変更と表示の設定の画面で、切り替えの直後の短い間に答えが届くと成功の知らせが出うる | 同上 |
| 不安定と確かめられていない3件 | `eslintImportRules.test.ts`・E2E 185 のログイン・`logoutIntent.test.tsx`（同じテストがもう一度落ちたら直すまで進まない） | `test-results.md` 4節 |
| 依頼（5）の持ち越し | make-you-chic-ui の依頼（5）が上流の `main` に無く、固定先の2回目の更新をしなかった。自前の `ScrollRegion`・`PageSizeSelect` は次の Intent で置き換える（AC4.1.2・AC4.1.4 の依頼（5）の分は次の Intent へ） | U3 の `code-summary.md` B7 |
| U5 R-04 | 固定の値（`K6_IMAGE` の digest と H2 の jar の取り方）の2か所書き | U5 の `code-summary.md` 7節 |
| 一意の制約の無いカラムの同時の重なり・同じ行の同時の更新の後勝ち | 防ぐ仕組みを足さない | U2 の `reliability-requirements.md` の補足 |

## 6. 準備の判定

| 判定 | 状態 | 根拠 |
|---|---|---|
| build-ready | 準備できている | 作業ブランチの上の最後の回で `verify` の全段が通過し、WAR ができる |
| test-ready | 条件つき | 単体・結合・E2E・セキュリティの検査は通過。ただし `develop` の今の形では流しておらず（Q1: B）、CI の試行ごとの結果（Pending 7）と `memory.peak` の測り（Pending 6）がまだそろっていない。TC-13 が Not Met |
| deployment-ready | まだ | 負荷の目標（Unverified 12）は Performance Validation、監視の目標（Unverified 4）は Observability Setup が判定する。段の定義の失敗の判定では、`Unverified` と `Not Met` が残る間はこの段は成功の結果にならない |

## 7. 段の定義の失敗の判定との関わり

段の定義（Step 9）は、`Not Met` か `Unverified` の目標が1つでもあれば Build and Test を失敗とし、`Pending` を Step 9 の終わりに残さない。この文書の時点では Step 9 の結果（CI と `memory.peak`）を足す前のため `Pending` が残る。足した後も、Unverified 16 件（後の段が持ち主で、実行の計画にある）と TC-13 の Not Met 1 件が残る見込みで、失敗の段の扱い（段の中の直し・持ち越しの決定）を承認の場で依頼者に諮る必要がある。目標は緩めない。

## 8. 承認の場に出すこと

1. **Unverified 16 件**（Performance Validation 12・Observability Setup 4）が残ったまま、この段を進めるか。段の定義では失敗の結果になる。
2. **Pending 13 件**（CI 7・`memory.peak` 6）。指揮役と別の担当が足した後に、判定をそろえる。`memory.peak` の結果しだいで、FR16.4（`cross-unit-traceability.md`）と U5-BR3.5 が決まる。
3. **TC-13（Not Met）**: `eslintImportRules.test.ts` が B3 の試しと B7 で同じ形で2度落ちた。team.md の「同じテストが二度目に落ちたら直す」に当たるかを確かめる。
4. **AC3.4.5 が覆われていない**: 一覧・詳細の読み取りとその拒否が監査に残らないことを確かめるテストの行が、どの単位の traceability にも無い（U2 が U1 へ Deferred にしたが、U1 に行が無い）。`cross-unit-traceability.md` の 4節。
5. **記録の食い違い**（直すかを確かめる。承認済みの記録は書き換えていない）:
   - U2 の `code-generation/traceability.json` の NFR1.6・NFR4.1・NFR4.4・NFR4.7・NFR4.10・NFR4.11・NFR7.5 の target が、NFR 要件のその ID の中身と違うテストを指している（例: NFR4.10「認可・一括代入・IDOR」が `DeadlinePatternMatcherTest`、NFR4.11「書き込みの設定の欠け」が境界テストと SpotBugs の除外）。中身に合うテスト（`DeadlinePatternTimingTest`・`BusinessDataWriteNotConfiguredIT` など）は実在し、この文書の行はそちらを根拠にした。`cross-unit-traceability.md` の 5節。
   - `README.md` の 38 行が make-you-chic-ui の固定先を `a9309c4` と書いたまま（今は `b0a1e79`）。
   - `perf/README.md` の U5 の節が「Performance Validation の段が無いため Build and Test で測る」と書く（この Intent には Performance Validation がある。持ち主は U5 の設計と Q2: A のとおり Build and Test のまま）。
6. **既知の制約**（5節）を受け入れたまま次の段へ進めること。

## Sources

- 各単位の `code-generation/code-generation-plan.md`（Testing Contract）・`unit-test-instructions.md`・`code-summary.md`・`generation-notes.md`・`traceability.json`
- 各単位の `nfr-requirements/`・`nfr-design/`
- 段の記録 `construction/code-generation/generation-notes.md`・`memory.md`、`build-and-test-questions.md`
- `aidlc-state.md`（Test Strategy・後の段の実行の計画）
- `aidlc/spaces/default/memory/org.md`・`team.md`・`project.md`

## Assumptions & Open Questions

- [assumption] 作業ブランチの上の最後の結果を `develop` の中身の結果と見る（Q1: B）。合わせた形は CI が確かめる。
- [assumption] U3-NFR1.7 は B6 の測りの値（中央値）で判定した。B7 とレビューの直しの後の 185 の測りの値は記録に写されていない（B7 は画面の測りの部品と表示の道に触れないが、R-01 で作成・更新の画面に手を入れた）。
- U1・U2 の負荷の目標の判定は Performance Validation に委ね、この段では目標を緩めず `Unverified` のままにする。
