# コード生成の計画（Intent 261107-verify-quick-local）

## Summary

- Builds: 統合の前の入口 `verifyQuick`（`verify` から3つのタスクを外し、JaCoCo は単体テストだけの参考値）、`-PitTests`・`-PitForks` の口、決まりと手順の文書の書き換え
- Touches: `build.gradle.kts`・`backend/build.gradle.kts`・新しい確かめの台本 `scripts/verify-graph-check.mjs`・`docs/verification.md`・`README.md`・`aidlc/spaces/default/memory/team.md`・`project.md`
- Tests: 確かめの台本1本（タスクの一覧と `-P` の誤りの扱いの 8 項目）＋ Build and Test での実行の確かめ（`-PitTests` の絞り込み、参考値、並べた `verify` の値の比べ）

## 前提と入力

- 単位は無い（bugfix の範囲、Units Generation と設計の段は無い）。入力は要件 `aidlc/spaces/default/intents/261107-verify-quick-local/inception/requirements-analysis/requirements.md`（FR1〜FR6・NFR1〜NFR5、対照表）と、Reverse Engineering の `aidlc/spaces/default/codekb/mastersmith2/architecture.md`（K-111・K-113・K-116）・`code-quality-assessment.md`（K-112・K-114・K-115・K-117）。
- 要件の承認の場で、レビューの指摘 R-01〜R-05 は受け入れたリスクとして承認された。この計画では、各指摘の直し方を下の「レビューの指摘の扱い」に書いて実装に入れる（要件の文書は書き換えない）。
- アプリのソース（`src/main`）は変えない。`packagesJudgedByTotal` のパッケージにも手を入れない。新しい依存は足さない。
- 触る前に確かめた今のコード（読み取り）:
  - `build.gradle.kts` 425〜513 行: 段の表 `verifyStages`、各段のタスクに前の段のすべてのタスクの後の `mustRunAfter` を付ける形、`verify` は段のタスク（`verifyPrepare` など）に `dependsOn`。
  - `build.gradle.kts` 216〜220 行: `frontendTest`（`npm run test`）と `frontendCoverage`（`npm run test:coverage`）。
  - `build.gradle.kts` 812〜818 行: `-Pe2e.files` を `providers.gradleProperty` で読み、カンマで分けて空を除く前例。
  - `backend/build.gradle.kts` 107〜133 行（全 `Test` の共通の設定）、147〜156 行（`integrationTest`、`includeTestsMatching("*IT")`）、175〜177 行（`coverageExecutionData`）、187〜196 行（`jacocoTestReport`）、223〜257 行（`jacocoTestCoverageVerification`）。

## 作りの要点

1. **`verifyQuick` の組み立て（FR1）**: `verify` の段の表 `verifyStages` はそのまま使い、`verifyQuick` 用の一覧を「段の表の各タスクから外すものを除いた並び」として作る。外すのは `frontendTest`・`:backend:integrationTest`（`-PitTests` が無いとき）・`:backend:jacocoTestCoverageVerification`。`verifyQuick` は段のタスク（`verifyPrepare` など。外したタスクも含んでしまう）ではなく、残したタスクそのものに `dependsOn` する。順は各タスクに付いている今の `mustRunAfter` がそのまま効く（`verify` と同じ順。順の決まりを足し直さないので重ならない。K-116）。
2. **JaCoCo の参考値（FR3）**: `verify` の `jacocoTestReport` は変えない。`verifyQuick` では、7 の段の `jacocoTestReport` の位置に、単体テストの記録（`test.exec`）だけを読む別のタスク `:backend:jacocoQuickReport` を置く。報告の置き場は `backend/build/reports/jacoco/quick/`（`verify` の報告を上書きしない）。報告を作った後に XML の全体の LINE・BRANCH の数から割合を出し、「単体テストだけの参考値。下限の判定はしない」の注記と報告の置き場とともに表示する。下限を下回っても失敗にしない。順は、0〜6 の段のタスクの後、8・9 の段のタスクの前（`mustRunAfter`。`verify` のグラフには入らないため `verify` には効かない）。
   - **依頼者に確かめたいこと**: Q1 の「タスクを外す（skip）形」から1点だけ外れ、`jacocoTestReport` を外して同じ位置に `jacocoQuickReport` を「置き換える」形にした。同じ `jacocoTestReport` を入口によって読む記録を変える形にすると、`verifyQuick` の後に `verify` の報告の置き場が単体テストだけの値で上書きされ、`verify` の出力が変わる（R-03）ため。外す形にこだわるなら、`jacocoTestReport` の読み先と置き場を入口で切り替える形にする。
3. **`-PitTests`（FR2）**: `backend/build.gradle.kts` で `providers.gradleProperty("itTests")` を読む。値はカンマ区切りのテストのクラスの形（Gradle のテストの絞り込みのワイルドカード）。
   - 値があるとき、`integrationTest` の絞り込みを `*IT` の代わりにその形にし、あわせてクラスのファイルを `**/*IT.class`・`**/*IT$*.class`（入れ子のクラス）に限る。これで結合テスト（`*IT`）だけが対象になる。
   - 空・空白・カンマだけの値は、理由を示して失敗にする（R-05）。
   - 当たるテストが無いときは、Gradle の既定（当たるテストが無ければ失敗）で失敗する（FR2.2）。この既定を明示に設定する。
   - ルートの `build.gradle.kts` では、値があるときだけ `integrationTest` を `verifyQuick` の 6 の段の位置に入れる。
   - `verify` と一緒に `-PitTests` を渡したときは、タスクのグラフが決まった時点で、理由（`verify` は全件を流す入口で、絞るなら `verifyQuick` を使う）を示して失敗にする（R-01: 失敗にする側に決める）。`:backend:integrationTest -PitTests=…` を単独で流すことは許す。
   - `-PitTests` が無いときの `integrationTest` の設定は今と同じ（`verify` を変えない）。
4. **`-PitForks`（FR4.2）**: `providers.gradleProperty("itForks")` を読み、`integrationTest` の `maxParallelForks` にする。無いときは設定しない（今までどおり1つの JVM）。1 以上の整数でない値は理由を示して失敗にする。並べたときの JaCoCo の記録は、各 JVM が同じ `integrationTest.exec` に追記する（JaCoCo の既定）。並べた `verify` の値が直列の値と一致するかは Build and Test で比べて記録する（R-02）。
5. **CI（FR4.3）**: `.github/workflows/ci.yml` は変えない。

## レビューの指摘の扱い（要件の承認の場で受け入れたリスク）

| 指摘 | この計画での扱い |
|---|---|
| R-01 `verify` に `-PitTests` を付けたときの挙動 | 失敗にする（作りの要点 3）。確かめの台本の項目 5 |
| R-02 並べた `verify` のカバレッジの値 | Build and Test で、直列の `verify` と `-PitForks` の `verify` の全体の行・分岐の値を並べて記録し、一致を確かめる。食い違ったら依頼者に諮る（`unit-test-instructions.md` の 4 節） |
| R-03 入口で入力を変える仕組みと `verify` の不変 | 別のタスク `jacocoQuickReport` にする（作りの要点 2）。`verify --dry-run` のタスクの一覧が変える前と一致することを台本の項目 1 で確かめる |
| R-04 決まりの書き換えの範囲 | Step 8 で、E2E の行は統合の前の検査の名前だけを変えること、`team.md` の Deployment の「ローカルの1コマンド検査と同じ検査を CI で実行する」と Testing Posture の「下回ったらビルドを失敗させる」を据え置くか書き換えるかを、前後の文と理由つきで示す |
| R-05 `-PitTests` の空の値 | 失敗にする（作りの要点 3）。台本の項目 6 |

## 手順

ブランチ: `develop` から `fix/261107-verify-quick-local` を作って作業し、統合は `develop` への squash（`team.md` の Way of Working）。コミットは場所ごとに区切り、積む前に毎回依頼者に尋ねる（`project.md` の Change Control）。

- [ ] **Step 1. 準備**: `develop` から `fix/261107-verify-quick-local` を作る。git の対象外の E2E の報告（`frontend/playwright-report`・`frontend/test-results`）を消す（`project.md` の Change Control）。変える前の `verify` のタスクの一覧を `./gradlew verify --dry-run` で取り、`scripts/verify-graph-baseline.txt` に保存する（台本の比べの元）。
- [ ] **Step 2. テストの道具の確かめ**: Node（26）で `node --version` を確かめ、確かめの台本を流すコマンド `node scripts/verify-graph-check.mjs` を `unit-test-instructions.md` に記録する（台本は Step 6 で書く）。
- [ ] **Step 3. バックエンドの口（実装）** `backend/build.gradle.kts`（FR2・FR3・FR4.2）:
  - `-PitTests` の読み取りと検証（空は失敗）、値があるときの `integrationTest` の絞り込み（`*IT` のクラスに限る、当たらなければ失敗）。
  - `-PitForks` の読み取りと検証、`maxParallelForks`。
  - `jacocoQuickReport`（`JacocoReport`、`test.exec` だけ、置き場 `build/reports/jacoco/quick/`、XML と HTML、`tasks.test` の後）と、報告の後の参考値の表示（XML の全体の LINE・BRANCH の数から割合を出す。DTD は読みに行かない）。
  - 日本語の説明文と、既存の `jacocoTestReport`・`jacocoTestCoverageVerification`・`packagesJudgedByTotal` を変えないこと。
- [ ] **Step 4. バックエンドの口（確かめ）**: `./gradlew :backend:spotlessCheck` と、`./gradlew :backend:jacocoQuickReport`（`test.exec` がある状態）で参考値が表示されること、`./gradlew :backend:integrationTest -PitTests=` が理由を示して失敗すること、`-PitForks=0` が理由を示して失敗することを流して確かめる。
  - コミット C1（`backend/build.gradle.kts`）を提案する。
- [ ] **Step 5. 入口 `verifyQuick`（実装）** `build.gradle.kts`（FR1・FR2.1・FR2.3）:
  - `verifyStages` から外すタスクを除いた並びで `verifyQuick` を登録する（`-PitTests` があるときだけ `integrationTest` を入れ、`jacocoTestReport` の位置に `jacocoQuickReport` を置く）。
  - `jacocoQuickReport` に、0〜6 の段のタスクの後・8〜9 の段のタスクの前の順を付ける。
  - `verify` と `-PitTests` が一緒のときに失敗にする確かめ（タスクのグラフが決まった時点）。
  - `verifyQuick` の説明文（`./gradlew tasks` に出る）。`verifyStages`・段のタスク・`verify` の定義は変えない。
- [ ] **Step 6. 確かめの台本（テスト）** `scripts/verify-graph-check.mjs`（新規、Apache License 2.0 のヘッダー付き）。`./gradlew … --dry-run` などを子のプロセスで流し、次の 8 項目を確かめて、1つでも違えば終了コード 1 で終わる（テストの説明文は英語）:
  1. `verify --dry-run` のタスクの一覧が `scripts/verify-graph-baseline.txt` と一致する（FR4.1・R-03）。
  2. `verifyQuick --dry-run`（`-P` なし）の一覧が、`verify` の一覧から `frontendTest`・`:backend:integrationTest`・`:backend:jacocoTestCoverageVerification`・`:backend:jacocoTestReport` を除き、`:backend:jacocoQuickReport` を足したものと一致する（FR1.1・FR1.3・FR3）。
  3. 2 の共通のタスクの順が `verify` の順と同じで、`:backend:jacocoQuickReport` が `:backend:test` の後・`:backend:spotbugsGate` の前にある（FR1.2）。
  4. `verifyQuick -PitTests=AuditTraceIdIT --dry-run` の一覧に `:backend:integrationTest` が `:backend:test` の後に入り、`jacocoTestCoverageVerification` は入らない（FR2.1）。
  5. `verify -PitTests=AuditTraceIdIT --dry-run` が失敗し、出力に理由がある（FR2.3・R-01）。
  6. `verifyQuick -PitTests=" , " --dry-run` が失敗し、出力に理由がある（R-05）。
  7. `verify -PitForks=0 --dry-run` と `-PitForks=x` が失敗し、出力に理由がある（FR4.2）。
  8. `verify -PitForks=2 --dry-run` が通り、一覧が 1 と同じ（FR4.1・FR4.2）。
  - 台本を流し、8 項目がすべて通ることを確かめる。
  - コミット C2（`build.gradle.kts`・`scripts/verify-graph-check.mjs`・`scripts/verify-graph-baseline.txt`）を提案する。
- [ ] **Step 7. 実行の確かめ（統合の前の検査）**: colima の環境変数を渡し、`caffeinate -dims` で包んで次を流す（`project.md` の Testing Posture）。
  - `./gradlew verifyQuick -PitTests=AuditTraceIdIT`: `AuditTraceIdIT` だけが流れ、参考値が表示される（FR2.1・FR3.2）。
  - `./gradlew verifyQuick -PitTests=NoSuchClassIT`: 当たるテストが無く失敗する（FR2.2）。
  - 前の回の `backend/build/jacoco/integrationTest.exec` が残った状態で `./gradlew verifyQuick` を流し、参考値が `test.exec` だけの値と一致する（`jacocoQuickReport` の XML が `test.exec` だけを読んだことを、報告の「Sessions」の一覧で確かめる。FR3.1）。
  - 全件の `verify` は、この段では流さない。Build and Test で時間を測りながら `:backend:cleanTest :backend:cleanIntegrationTest` を付けて1回流し、それが通ってから `develop` へ統合する（2026-11-08 依頼者の決定。長い全件を二重に流さないため）。
- [ ] **Step 8. 決まりの書き換え（FR5）**: `aidlc/spaces/default/memory/project.md` と `team.md` の当たる行を書き換える。書き換えの前と後の文を `code-summary.md` に並べる（FR5.5）。
  - `project.md` Mandated: 統合の前は `verifyQuick`、全件（全テストとカバレッジの判定）は全件の場面と CI で確かめる形に（FR5.1）。
  - `team.md` Way of Working: 統合の前の検査の行・E2E の行（統合の前の検査の名前だけ）・Dependabot の行（全件の `verify`）・コンテナの実行環境の行（`verifyQuick` でも飛ばした状態では統合しない）。全件の `verify` を流す場面（Build and Test・リリースの前・`packagesJudgedByTotal` のパッケージに手を入れる Bolt・Dependabot の取り込み）と、`-PitTests`・`-PitForks` の使い方を書く（FR5.2）。
  - `team.md` Testing Posture: 対象DB の結合テストを「`./gradlew verify` の中で毎回」の行を「全件の `verify` と CI で毎回」に、`packagesJudgedByTotal` の行（全件の `verify` で実測は据え置き）、時間の記録の行（Bolt の後は `verifyQuick`、Build and Test は `verify` と CI）（FR5.3）。
  - `project.md` Way of Working・Testing Posture の `verify` の行（FR5.4）。
  - R-04: `team.md` の Deployment の CI の行と、Testing Posture の「下回ったらビルドを失敗させる」の行は、`verify`（CI と全件の場面）では今のままなので据え置き、`verifyQuick` ではバックエンドの判定をしないことを Testing Posture に1文足す案を示す。
  - コミット C3（`aidlc/spaces/default/memory/` の2ファイル）を提案する。
- [ ] **Step 9. 手順の文書（FR6）**: `docs/verification.md` の「1コマンドの検査」の節に `verifyQuick` の対照表・`-PitTests`・`-PitForks`・参考値の読み方・全件の場面を足す。`README.md` の検査の行（Docker・colima の前提、対象DB の結合テストを毎回流す説明）を使い分けに合わせて直す。
  - コミット C4（`docs/verification.md`・`README.md`）を提案する。
- [ ] **Step 10. 記録**: `code-summary.md`（変えたもの、決まりの前後の文、依頼者に確かめたいこと）と `traceability.json` を書く。
- [x] **Step 11. 承認の場の Request Changes（2026-11-08 依頼者の決定「この内容ですべて直して」）**: コードのレビューの R-01〜R-05 を直す。
  - R-01: `-PitTests` を付けたときだけ、`integrationTest` の JVM にシステムプロパティ `mastersmith.requireContainerRuntime=true` を渡し、`ContainerRuntimeCheck` はこのプロパティがあるときも `CI=true` と同じく失敗にする（飛ばさない）。`-PitTests` が無いときの動きは変えない。`ContainerRuntimeCheck` の判定のテストを1件足す。`team.md`・`docs/verification.md` の「警告の有無を確かめる」の文を「失敗する」に直す。確かめ: 判定のテストと、確かめの台本の 8 項目。
  - R-02: `traceability.json` の NFR2 を Deferred（Build and Test の全件の `verify`）にする。
  - R-03: `docs/verification.md` に、台本は手で流す回帰の確かめであること、`verify` の段を変える Intent では比べの元を取り直すこと（手順）、台本を検査に載せるのは次の Intent に回すことを書く。`code-summary.md` にも書く。
  - R-04: `project.md`・`team.md` で今回書き換えた行の印の後ろに「（2026-11-08 Intent 261107-verify-quick-local で書き換え）」を足す。
  - R-05: `docs/verification.md` に、変えた `src/main` のファイルが `packagesJudgedByTotal` のパッケージに当たるかを一覧するコマンドを足し、`team.md` の全件の場面の行に「判定の結果を Bolt の統合の記録に書く」を足す。
  - コミットは場所ごとに区切り、積む前に依頼者に尋ねる。

## Testing Contract

```json
{
  "version": 1,
  "methodology": "test-after",
  "source": "team",
  "ordering": "テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。",
  "scope": "bugfix",
  "test_strategy": "minimal",
  "project_type": "brownfield",
  "applicable_notes": [
    {
      "layer": "org",
      "text": "We treat tests as a first-class deliverable in every Bolt. The specific\nmethodology (TDD, BDD, ATDD, or classic test-after) is affirmed at\npractices-discovery and recorded in `team.md` under this heading with explicit\n`Methodology` and `Ordering` fields; Code Generation resolves those fields\nindependently from coverage, tooling, and scope notes.\n\nWhen no posture has been affirmed, our default per scope is:\n- **Methodology**: test-after\n- **Ordering**: implement each applicable testable layer, then write and run\n  that layer's tests.\n- `mvp`, `enterprise`, `feature`, `infra`, `classic` add an 80% line-coverage\n  floor and CI execution before merge.\n- `bugfix`, `security-patch` add a targeted regression for the specific\n  bug/vulnerability and require the existing suite to remain green.\n- `express` uses the Minimal strategy: requirement-driven unit tests (one per\n  requirement, with a happy-path floor per component); existing tests remain\n  green.\n- `poc`, `refactor`, `workshop` add no extra new-test floor and require the\n  existing suite to remain green.\n\nThe active `Test Strategy` still applies in every scope and determines test\nvolume/types. Scope floors are additive; they never reduce or replace the\nselected strategy.\n\nBuild and Test verifies defined coverage floors and affirmed quality targets;\nthey may not be weakened to make a step pass.\n\nAffirm a stricter posture in `team.md` if the team commits to one."
    },
    {
      "layer": "team",
      "text": "- **Methodology**: test-after\n- **Ordering**: テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。\n- テストは各 Bolt の成果物の一部であり、テストのない機能は完成とみなさない。テスト量はワークフローの Test Strategy に従い、以下の下限はそれに追加される。\n- カバレッジの下限は、すべての Intent に共通で **行カバレッジ 80% 以上、分岐カバレッジ 70% 以上** とする。バックエンド・フロントエンドの両方に適用し、下回ったらビルドを失敗させる。道具はバックエンドが JaCoCo、フロントエンドが `@vitest/coverage-v8`（`thresholds` 設定）。\n- バックエンドでは、全体の合計に加えて、すべてのパッケージごとにも同じ下限（行 80%・分岐 70%）を当てる。ただし、既存のパッケージに単独で下限を下回るものがあれば、パッケージごとの下限は新しく作るパッケージだけに当てる（既存のパッケージは全体の合計で判定する）。どちらになるかは、パッケージごとの下限を入れる Bolt で既存のパッケージのカバレッジを実測して決め、結果を記録する。\n- 全体の合計で判定している既存のパッケージ（`backend/build.gradle.kts` の `packagesJudgedByTotal` の一覧。2026-10-16 の時点でも 7 パッケージ（`access.service`・`audit.repository`・`common.error.domain`・`common.error.service`・`common.health`・`common.i18n.domain`・`common.web`）。`user.domain`・`user.repository`・`user.service` は Intent 260925-user-management で一覧から外れ、含まない）に手を入れる Bolt では、テストを足してそのパッケージの下限（行 80%・分岐 70%）を満たし、一覧から外してパッケージごとの下限の対象に必ず戻す。「手を入れる」は、そのパッケージの本体のソース（`src/main`）の変更のすべて（説明文だけの直しを含む）を指し、テストだけの変更は含めない。手を入れる見込みのパッケージとそれに伴う作業は、Delivery Planning とコード生成の計画で、各パッケージの今の値を実測して見積もる。カバレッジは、その Bolt のテストを足した後に `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で実測し、値を記録する。一度外したパッケージは一覧に戻さず、一覧を増やす変更はしない。\n- フロントエンドのカバレッジの下限は、今の形（全体の合計に当てる `thresholds`）のままとする。新しい機能のフォルダ（`src/features/<featureId>/`）のカバレッジの値は、Build and Test で記録する。\n- カバレッジの計測から外すのは、アプリの起動クラス、設定値だけのクラス、自動生成コード、`vendor/` 配下に限る。除外を後から増やして実質的に下限を下げることはしない。パッケージごとの下限を満たすために除外を増やすこともしない。\n- DB を使うテストは、本番と同じ種類の DB をコンテナで起動して使う（Testcontainers）。そのため、ローカルと CI にコンテナの実行環境があることを前提条件として文書化する。テストごとにデータを用意して巻き戻し、実行順に依存させない。表を作る・消す操作（DDL）がその場で確定して巻き戻せない DB（MySQL・MariaDB など）で表を作るテストは、巻き戻す代わりに、テスト（またはテストのクラス）ごとに名前の重ならないスキーマ（MySQL・MariaDB ではデータベース）を作り、終わったら消す。対象DB の業務データを読むだけのテストでも表を作るため、この決まりを当てる。作る名前には実行ごとに一意の値を入れ、途中で止まった前の実行の残りに頼らない。\n- 対象DB（MySQL・MariaDB・PostgreSQL）の結合テストは、3種類すべてを `./gradlew verify` の中で毎回実行する（CI も同じ）。コンテナの実行環境が無いときの扱いは Way of Working のとおり。\n- テストを足した Bolt の後と Build and Test で、`./gradlew verify` の時間と CI の時間を測って記録する。CI の上限（`timeout-minutes: 60`）の7割の約 42 分を超えたら、テストの分け方を依頼者に諮る。\n- 画面からの一連の操作を確かめるテスト（E2E）は Playwright（`./gradlew e2eTest`）で書き、`./gradlew verify` と CI の外に置く。本数は代表的な流れに絞り、機能の Intent ごとに代表の流れを1本まで足す。新しく足す流れは、前のテストが作った状態に頼らず、利用者などの前提を自分で作る。複数の機能の Intent を1つに束ねた Intent では、束ねた元の Intent ごとに数える。既存の E2E を新しい形（権限の形など）に合わせて書き換えることは、本数に数えない。利用者の状態を変える操作（利用停止・管理の権限の変更・ロックなど）は、その流れで自分で作った利用者だけを対象にし、初期管理者の状態は変えない。役割・権限を変える操作も、その流れで自分で作った利用者と役割だけを対象にし、初期管理者は変えない（E2E は1つのアプリ・内部DB・初期管理者を全ファイルで共有して順に流すため）。E2E を流す時点は Way of Working のとおり（Intent の最後とリリースの前）とする。\n- 入力と出力の性質をランダムな入力で確かめるテスト（性質ベースのテスト）を、純粋な関数（ロック判定の回数計算、有効期限の判定、入力の検証など）に一部適用する。道具は Java が jqwik、フロントエンドが fast-check。失敗時の乱数の種を記録して再現できるようにする。\n- テストの説明文（テスト名、`describe` / `it`、`@DisplayName`）は英語で書く。テストデータは日本語でよい。\n- リポジトリは公開のため、テストデータのメールアドレスは予約のドメイン（`example.com` など）だけにし、実在しそうな氏名・宛先を置かない。\n- フロントエンドのテストは対象と同じ場所に `*.test.ts` / `*.test.tsx` として置き、Vitest ＋ Testing Library（jsdom）＋ user-event ＋ vitest-axe を使う。画面部品ごとにアクセシビリティ検査を1件入れる。\n- 画面のテストで、描画の後（`useEffect` などの効果）に反映される値は、操作の直後に同期で確かめず、`waitFor` で待って確かめる。テストの時間の上限（Vitest の既定 5 秒など）は、原因を確かめずに延ばさない。\n- Java のテストは `src/test/java` に、対象と同じパッケージ構成で置く。単体テストは `XxxTest`、Spring や DB を起動する結合テストは `XxxIT` とし、分けて実行できるようにする。\n- 時刻に依存する処理（有効期限、ロックの解除など）は注入可能な時計（`Clock` 等）から現在時刻を取得し、テストで `sleep` や実時刻に依存しない。\n- 不安定なテストと CI の失敗は、次の1つの決まりで扱う。\n  - 手元で再現した不安定なテストは、原因を直すまで統合しない（次へ進まない）。\n  - 手元で再現しないものは、再現の試みに先に時間の上限を決め、見立てと試みの範囲を記録したうえで、CI の再実行で通れば進めてよい（不安定と確かめられていない扱い）。\n  - 同じテストが二度目に落ちたら、原因を直すまで次へ進まない。\n  - 失敗を直さずに次の Intent へ持ち越すのは、依頼者の決定があるときだけとし、決まりとの差を記録する。\n  - 不安定なテストを直したときは、二段で確かめる。疑った競り合い・待ちの抜けを待ち合わせや順の入れ替えで確実に起こせたときは、直す前のテストが落ち、直した後のテストが通ることを記録する。起こせないときは、上の「手元で再現しないもの」と同じく、試みの時間の上限を決めて試み、見立てと試みの範囲を記録する。\n- 権限ごとの API の認可のテストの共通の形（下の役割・権限、業務データ、他テーブル参照の一覧から「共通の形」として参照する）: 未認証（401）・要る権限だけを欠く利用者（403。権限が何も無い利用者ではなく、ほかの権限は持つ利用者）・権限を持つ（200）を、サーバー側のテストで確かめる。API と権限の組は、パラメーターを使うテストで表として書く。\n- axe-core を上げて新しい規則の違反が出たときは、規則を外して検査を通さず、違反をすべて直してから上げる（固い制約は `project.md` の Forbidden）。違反の要素が make-you-chic-ui の部品のものなら依頼を出し、固定先の更新で取り込んでから上げる。上げたときは、実際のブラウザの axe（E2E）と画面のテストの vitest-axe の両方を流し、E2E の全件の通過と違反 0 件を件数で記録する。あわせて、axe-core の版が lockfile で1つにそろっているかを確かめる。\n- 性能・資源の目標の測り方（物差し）を変えるのは、要件定義で依頼者が明示に決めたときだけとし、変える理由を記録する。変えた後も、少なくとも1回は前の物差しの値を並べて記録する。\n- 負荷の試験の使い捨ての環境の対象DB の行は、perf の台本（`perf/` の下）で決めた種から作る（行の数は NFR 要件の段で決める）。k6 の場面の setup は、前の場面が残した状態に頼らない。\n\n- 内部DB（組み込みの H2）を使うテストは、コンテナではなく本番と同じ組み込みの H2 で行う。Testcontainers は、コンテナで動かす対象DB（後続 Intent D・E で扱う業務DB）のテストに使う。Walking Skeleton の「DB を使うテスト1件以上（本番と同じ種類の DB をコンテナで起動する）」も、内部DBについてはこの読み方とする。 (learned 2026-09-22) \n\n- 画面を廃止する・移すことで既存の E2E を書き換える・消すときは、本数に数えない。ただし、その画面で確かめていた検査（例: 管理の仮の画面の E2E 030・090・130 の 403 の表示・アクセシビリティ・管理の権限の確認の API）は、別の画面か別のテストへ移して残し、移す前と後の件数を Build and Test で記録する。"
    },
    {
      "layer": "project",
      "text": "- テストの件数やカバレッジを報告するときは、`./gradlew verify` がテストのタスクを UP-TO-DATE で飛ばすことがあるため、`:backend:cleanTest :backend:cleanIntegrationTest` を付けて実行し直し、実測の数字だけを報告する。 (learned 2026-09-23) \n- 実際のブラウザのアクセシビリティの検査（axe）は、誤りを出した状態と、現実に近いデータ（2語の氏名など）で、ブランドカラーとテーマのすべての組について行う。user-management の U7 の 080 で初めて、make-you-chic-ui の Avatar（頭文字が2文字のとき）と dark の FormField の誤りの文字のコントラスト不足が出た（050〜070 は誤りの状態を見ず、氏名が1語だったため出なかった）。すべての組で行うのは、その Intent で変えた画面だけとし、変えていない画面は E2E の既存の検査に任せる（2026-11-07 依頼者の決定で緩めた）。 (learned 2026-09-28) \n- Delivery Planning でのカバレッジの実測は、./gradlew verify 全体ではなく :backend:cleanTest :backend:cleanIntegrationTest :backend:test :backend:integrationTest :backend:jacocoTestReport で行い、jacocoTestReport.xml から手を入れる見込みのパッケージの値を読む（user-admin で約 5 分）。 (learned 2026-10-01) \n- 画面のはみ出しの確かめ（E2E・axe）では、画面全体の横のスクロールだけでなく、開いたメニュー・ポップアップなど画面に固定で置く部品が画面の中に収まることも確かめる。表の右端に置く make-you-chic-ui の Dropdown は placement に bottom-end を指定する（user-admin の配備の後のスモークテストで、行の「操作」のメニューが右へはみ出していたのを E2E 120 が拾えなかった）。 (learned 2026-10-03) \n- 木の画面の単体テスト・E2E・スモークテストでは、本物と同じ形の応答（子の有無の印を含む）を使い、すべての段の開閉を押して確かめる（role-menu の配備の後のスモークテストで、「自分の権限を見る」の木でテーブルの開閉を押すと自分自身を子に持って固まる不具合が見つかった。単体の見本のテーブルは子が無い形で本物と違い、E2E は選ぶだけで開閉を押していなかった）。 (learned 2026-10-14) \n- 画面の不具合を使い捨ての環境で再現するときは、依頼者がした操作（どのボタン・どの開閉を押したか、画面の文言とその場所）を先に聞き、同じ操作を試す。role-menu の S8 で、選ぶだけで開閉を押さずに再現せず「環境の差」と誤って見立て、依頼者の再試行で条件が分かった。 (learned 2026-10-14) \n- 長いテスト（verify・E2E・k6）は、試験の台本全体を `caffeinate -dims` で包む（場面ごとに起こし直すと切れ目で守りが外れる）。流す前に `pmset -g assertions` で眠らない状態か、流した後に `pmset -g log` で Sleep・DarkWake が無かったかを確かめ、入っていたら結果を使わずに流し直す（DarkWake の Thermal Emergency のスリープは `caffeinate -i` では止められない）。 (merged 2026-11-04)\n- colima の PC で `./gradlew verify` を流すときは、README の `DOCKER_HOST` と `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡す。渡さないと対象DB のテストが SKIPPED になり、パッケージごとのカバレッジの下限で失敗する。 (merged 2026-11-04)\n- Playwright の `webServer.env` に置いた値は json の報告に残る。E2E の仮の資格情報はプロセスの環境変数で渡し、報告の json の文字列の検索で、パスワード・トークン・メールアドレスが入っていないことを確かめる。 (merged 2026-11-04)"
    }
  ],
  "obligations": {
    "strategy": "minimal",
    "strategy_volume": [
      "One verifiable test per requirement at the narrowest effective level.",
      "At least one happy-path unit test per component.",
      "Unit tests are the default; a bugfix/security scope floor may require an integration or E2E regression when that is the narrowest level that reproduces the defect."
    ],
    "scope_floor": [
      "Include a targeted regression for the bug or vulnerability.",
      "Keep the existing test suite green."
    ],
    "combination_rule": "Apply every selected-strategy obligation and every scope-floor obligation; neither replaces the other, and a targeted scope regression may add the narrowest necessary test type beyond the strategy default."
  },
  "plan_profile": {
    "methodology": "test-after",
    "runner_step": "Verify the existing test runner/configuration and record the exact unit-scoped command.",
    "runner_ready_before_first_test": true,
    "testable_layers": [
      "Data model / database behavior",
      "Repository / data access",
      "Business logic",
      "API / endpoint",
      "Frontend behavior"
    ],
    "steps": [
      "Project structure and production configuration skeleton.",
      "Verify the existing test runner/configuration and record the exact unit-scoped command.",
      "Data model / database behavior - implement.",
      "Data model / database behavior - write and run its tests after implementation.",
      "Repository / data access - implement.",
      "Repository / data access - write and run its tests after implementation.",
      "Business logic - implement.",
      "Business logic - write and run its tests after implementation.",
      "API / endpoint - implement.",
      "API / endpoint - write and run its tests after implementation.",
      "Frontend behavior - implement.",
      "Frontend behavior - write and run its tests after implementation.",
      "Environment/build configuration.",
      "Documentation and traceability."
    ]
  },
  "input_sha256": "sha256:1e11fa394cc05fbdc1f29a31674095fb3194532a84744c2c927846a718a8434b",
  "contract_sha256": "sha256:fd252cc94988008020a418b875f974e35eee888b7c222e780e1793b56a23daa3"
}
```

契約の層のうち、この Bolt に当たるのはビルドの設定（Environment/build configuration）と文書（Documentation and traceability）だけで、データ・業務処理・API・画面の層は無い（アプリのソースを変えない）。test-after の順として、ビルドの設定の層ごとに実装（Step 3・Step 5）の後にその層の確かめ（Step 4・Step 6〜7）を流してから次へ進む。bugfix の範囲の「狙った回帰」は、確かめの台本の 8 項目（特に 1 の `verify` の不変と 2 の `verifyQuick` の中身）とする。既存のテストが通り続けることは Step 7 の全件の `verify` で確かめる。

## 要件との対応

| 手順 | 要件 |
|---|---|
| Step 3・4 | FR2.1・FR2.2・FR2.4・FR3.1・FR3.2・FR3.3・FR4.2 |
| Step 5 | FR1.1・FR1.2・FR1.3・FR1.4・FR2.1・FR2.3・FR4.1 |
| Step 6 | NFR4（FR1・FR2・FR4.1 の合否） |
| Step 7 | FR2.1・FR2.2・FR3.1・FR3.2・NFR3 |
| Step 8 | FR5.1〜FR5.5 |
| Step 9 | FR6.1・FR6.2 |
| 変えないもの | FR4.3（CI）・NFR2（`verify` の合格の条件） |
| Build and Test | NFR1（時間）・NFR3（並べた `verify`）・R-02（並べた値の比べ） |
