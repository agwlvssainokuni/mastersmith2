# コード生成の計画（Intent 261110-deps-forks-review）

## Summary

- Builds: Java 25 系・Node 26 系のパッチ、Spring Boot・npm と Gradle の依存・wrapper・検査の道具・GitHub Actions・コンテナのイメージの最新化、連動する固定の見直し、決まりの文書と `dependabot.yml` の古い記述の直し（サブモジュールは上げない）
- Touches: `gradle/`・`build.gradle.kts`・`backend/`（版と lockfile）・`frontend/`（`package.json`・lockfile・設定）・`Dockerfile`・`compose.yaml`・`docker/`・`perf/versions.env`・`.github/`・`docs/`・`README.md`・`aidlc/spaces/default/memory/project.md`
- Tests: 新しいテストは置かない見込み（固定を外すときの回帰の確かめは既存の `OtlpLogExportIT`・`ExternalExportIT`・`H2CompactThreadsTest`・`H2CompactionByPoolSuspensionIT`）。既存の全件の `./gradlew verify`（全テスト・3種類の対象DB）と E2E の全件、外部エクスポートを有効にした手元の起動で確かめる

## 前提と方針

- 単位の分割の無い bugfix のため、設計の段は無い。要件 `inception/requirements-analysis/requirements.md`（FR1〜FR6・NFR1〜NFR5）とコードの知識ベース（`aidlc/spaces/default/codekb/mastersmith2/`、所見 K-138〜K-143）から作業を決める。
- 「最新」は生成の時点で公開されている安定版（プレリリース・RC を除く）。各版はこの計画では決め打ちせず、各 Step の始めに公式の配布元（Maven Central・npm・Docker Hub の index・GitHub の Releases）で確かめ、前後の版とダイジェストを `generation-notes.md` と `code-summary.md` に書く。
- 統合の流れは `team.md` のとおり。`develop` から短命のブランチを作り、まとまりごとに `develop` へ squash で戻す。サブモジュールは上げない（FR1.9）。
- 統合の前の確かめ: 依存の更新の取り込みのため、どのブランチも全件の `./gradlew verify` で確かめる（`verifyQuick` で代えない。NFR3）。形は `DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock" TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock caffeinate -dims ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify -PitForks=3`。E2E の全件（`./gradlew e2eTest`、見本の PG と `docker/e2e/compose.targetdb-port.yaml`）も各ブランチの統合の前に1回流す（依存の更新で画面の部品・WAR が変わるため）。流す前に `pmset -g assertions`、流した後に `pmset -g log` で眠りが無かったかを確かめる。長い検査は1つのまとまりにつき1回にまとめ、重ねて流さない。
- コミットは場所・機能ごとに区切り、最初のコミットの時点でコンパイルが通るようにする（`project.md`）。コミットのメッセージは日本語で、末尾に `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` を付ける。`aidlc/` の下だけの記録を除くコミットは、依頼者の承認を得てから積む。`git push` は依頼者が行う。
- 秘密の値（`.env`・`.env.targetdb`）は開かない。`vendor/` の中は直接変えない。axe の規則・カバレッジの下限・ArchUnit の境界テスト・SpotBugs の除外は緩めない。

## 見送りの判定基準（要件 FR1.4・FR1.4a）

更新は、次のどれかに当たるときだけ見送る。見送ったものは `.github/dependabot.yml` の `ignore` に、理由（当たった基準）と外す時期を書く。

1. **直しの大きさ**: 直すのにアプリの本体（`src/main`・`frontend/src`）の5ファイルを超える変更、画面の見た目・API の応答の形・DB スキーマの変更、または既存の決まり（`team.md`・`project.md`）の書き換えが要る（FR1.4）。
2. **部品の原因**: make-you-chic-ui の部品（peer 依存・部品の色のコントラストなど）が原因で通らない。上流への依頼の文面を `<record>/make-you-chic-ui-request-<n>.md` に置き、依頼は次の Intent に回す（FR1.4a）。
3. **上流の未対応**: 依存する部品が新しい大きな版をまだ支援していない、と配布元の記述で確かめられた。
4. **決まりとの衝突**: 上げると決まり（axe の規則を外さない・Maven Central だけ・`ignore-scripts`・ライセンスなど）を破る必要がある。

基準に当たるか決めきれないもの、見送ると脆弱性（OSV の High 以上）が残るものは、生成を止めて依頼者に諮る。

## 版の固定先の一覧（影響の範囲、K-138・K-139）

正本は `aidlc/spaces/default/codekb/mastersmith2/technology-stack.md` の「版の固定先」の節と `.github/dependabot.yml` のコメント。各 Step で値を変えたら、`grep` で古い値が残っていないことを確かめる。

| 対象 | 今の版 | そろえる場所 |
|---|---|---|
| Java | 25（CI は大きな版だけの指定） | `gradle/libs.versions.toml` の `java`・`ci.yml` の `java-version`・README と `docs/` の SDKMAN の版 |
| JRE のイメージ | `eclipse-temurin:25.0.5_8-jre-noble` | `Dockerfile`・`perf/versions.env`（`PERF_JRE_IMAGE`）・`docs/internal-db.md`・`docs/dsl.md`・`docs/operations.md` |
| JDK のイメージ | `eclipse-temurin:25.0.5_8-jdk-noble` | `docker/hikari-pool.sh`（`JDK_IMAGE`）・`docs/internal-db.md`・`perf/README.md` |
| Node | 26 | `frontend/package.json` の `engines`・`build.gradle.kts`・`ci.yml`・README（大きな版は変えない。パッチは手元と CI の入れ方で決まる） |
| Spring Boot | 4.1.2 | `gradle/libs.versions.toml` |
| appender・networknt | 2.28.1-alpha・3.0.6 | `gradle/libs.versions.toml`（コメント）・`dependabot.yml` の `ignore` |
| H2 の `-Dh2.compactThreads=1` | — | `Dockerfile`・`backend/build.gradle.kts`・`frontend/playwright.config.ts`・`H2CompactThreadsTest`・`docs/internal-db.md` |
| Gradle Wrapper | 9.8.2 | `gradle/wrapper/gradle-wrapper.properties`・`gradle-wrapper.jar` |
| 対象DB | postgres 18.6・mysql 26.10.0・mariadb 13.0.2 | `compose.yaml`・`docker/perf/compose.yaml`・`TargetDbImages.java`（版とダイジェスト）・`docs/` |
| Mailpit・otel-lgtm・otel-collector・k6 | v1.33.0・0.39.0・0.164.0・2.3.0 | `compose.yaml`・`docker/perf/compose.yaml`・`perf/versions.env`・`dependabot.yml`（collector の `-386` の `ignore`）・`docs/`・`perf/README.md` |
| Gitleaks・OSV-Scanner | 8.30.1・2.7.0 | `ci.yml`（版と SHA-256）・`.pre-commit-config.yaml` の `rev`・README |
| GitHub Actions | checkout v7.0.1・setup-java v6.1.0・setup-node v7.1.0・setup-gradle v6.4.1・upload-artifact v7.0.2・download-artifact v8.0.2 | `.github/workflows/*.yml`（SHA と版のコメント） |
| Playwright | `@playwright/test` 1.64.1 | `frontend/package.json`・README |

## 手順

### ① 実行環境と依存（ブランチ `feature/261110-deps`、squash で統合）

- [x] **Step 1**（FR2.1・FR5.2）: 前の版を記録する。今の Spring Boot・JRE のイメージのタグ・H2・OpenTelemetry・Jackson の版を `generation-notes.md` に書く。Maven Central で Spring Boot の最新の安定版と、その BOM（`spring-boot-dependencies`）が管理する OpenTelemetry・Jackson・H2 の版、networknt の最新の版が求める Jackson の版、opentelemetry-logback-appender の最新の版が求める OpenTelemetry の版を確かめて書く。
- [x] **Step 2**（Testing Contract の runner_step、NFR3）: 変える前の `develop` で、下の「単位のテストの手順書」の絞ったコマンド（固定に関わる4つのテスト）が通ることを確かめ、時間を書く。
- [x] **Step 3**（FR1.3）: Spring Boot を最新の安定版にする（`gradle/libs.versions.toml`）。Jackson は上書きしない。
- [x] **Step 4**（FR1.4・FR1.5・FR1.8）: backend の Gradle の依存（SpotBugs と FindSecBugs・palantir-java-format・JaCoCo・Testcontainers・jqwik・ArchUnit ほか）と Gradle の wrapper（`./gradlew wrapper --gradle-version <最新>` を2回流して jar も更新）を最新にし、`backend/gradle.lockfile`・`settings-gradle.lockfile` を `--write-locks` で更新する。新しく増えた依存のライセンスを確かめる。
- [x] **Step 5**（FR2.2・FR2.3・FR2.4）: 連動する固定を Step 1 の結果で分ける。
  - (a) appender: 管理の OpenTelemetry が 1.63 以上になったときだけ、最新の版に上げて試す。合格の条件は、全件の `verify`（`OtlpLogExportIT`・`ExternalExportIT` を含む）と E2E に加えて、外部エクスポートを有効にした手元の起動（compose の lgtm を起動し、アプリを `MASTERSMITH_OTEL_*` などの外部エクスポートの設定を有効にして起動し、起動が失敗しないことと lgtm の Loki にアプリのログが届くことを確かめる。設定の名前は `docs/operations.md` で確かめる）。
  - (b) networknt: 管理の Jackson が 3.2 系以上になったときだけ、最新の版に上げて全件の `verify`（DSL の読み込みのテスト）で試す。
  - (c) `-Dh2.compactThreads=1`: H2 の版が 2.4.240 から上がったときだけ、5か所（上の表）から外して `H2CompactionByPoolSuspensionIT` を含む全件の `verify` と E2E で試す。
  - 前提が変わらないものは外さず、`libs.versions.toml` のコメントの版の記述だけを今の値に直す。外せたものは `dependabot.yml` の `ignore` の行も外し、外せなかったものは理由を `project.md` の学びの言い方で記録する。
- [x] **Step 6**（FR1.4・FR1.4a・FR1.5・FR1.7・FR1.8）: frontend の npm の依存を、大きな版を含めて最新にする（`npm outdated` で一覧し、`npm install`（`ignore-scripts=true` のまま）で更新、`package-lock.json` を更新）。大きな版ごとに型の検査・リンタ・画面のテストを流し、崩れたら直す。上の判定基準に当たるものは見送って `ignore` に書く。`@playwright/test` を上げたらブラウザも同じ版にする。axe-core が上がったら、vitest-axe と E2E の axe の両方を流し、新しい規則の違反は規則を外さずにすべて直す（部品が原因なら基準2）。axe-core の版が lockfile で1つにそろっているかを確かめる。
- [x] **Step 7**（FR1.4）: `dependabot.yml` の `ignore` を1行ずつ見直す（appender・networknt は Step 5、`typescript` の大きな版は Step 6 の結果で外すか残す、`eclipse-temurin` の大きな版は残す、collector の `0.164.0-386` は ② で）。
- [x] **Step 8**（FR2.5）: E2E の WAR の JVM の時差を確かめる。`frontend/playwright.config.ts` の `webServer.command`・`webServer.env` と、テストの JVM・Dockerfile・compose の値を並べ、`team.md` の「4か所を同じ値にそろえる」に合うかを記録する。合わないときは、直し方の案（`-Duser.timezone=Asia/Tokyo` を `webServer.command` に足す など）を添えて生成を止め、依頼者に諮る（決まりの読み方に関わるため）。
- [x] **Step 9**（NFR1〜NFR3・NFR5）: 全件の `verify`（clean 付き、`-PitForks=3`）と E2E を流し、カバレッジ（backend の全体とパッケージごと、frontend の全体）と `verify` の時間を記録する。通ったら `develop` へ squash で統合する。

### ② CI・コンテナのイメージ・道具（ブランチ `feature/261110-ci-images`、squash で統合）

- [x] **Step 10**（FR1.5）: `.github/workflows/` の Actions を最新の版の SHA にし、版をコメントに書く。Gitleaks と OSV-Scanner を最新にし、`ci.yml` の版と SHA-256（配布元の checksums で確かめる）・`.pre-commit-config.yaml` の `rev`・README をそろえる。
- [x] **Step 11**（FR1.1・FR1.6）: コンテナのイメージを最新にする。始める前と終わった後に、`Dockerfile`・`compose.yaml`・`docker/`・`perf/`・`TargetDbImages.java`・`docs/` のイメージの参照（`FROM`・`image:`・`*_IMAGE=`・`@sha256:`）を `grep` で一覧にし、上の表に無い参照が無いこと・古い版が残っていないことを確かめる。JRE・JDK（Java 25 の最新のパッチ）、対象DB（postgres・mysql・mariadb）、Mailpit、otel-lgtm、otel-collector、k6。どれも版のタグと複数アーキテクチャの index のダイジェストで固定し、上の表の場所すべてをそろえる。対象DB は `verify`（3種類の結合テスト）で通らない版なら、通る中で最新の版にする。collector の `-386` のタグの `ignore` を今のタグに合わせる。
- [x] **Step 12**（FR3.2）: `.github/dependabot.yml` の gradle の説明の「4.1.1」を Step 3 の Spring Boot の版に直す。
- [x] **Step 13**（NFR3）: 全件の `verify`（clean 付き）と E2E（見本の PG を新しいイメージで作り直す。サービス名を指定して `docker compose` を使い、ほかのサービスを作り直さない）を流す。入れ替えたコンテナ（Mailpit・lgtm・見本の PG）は `docker inspect` で版とダイジェストが `compose.yaml` と一致することを確かめる。通ったら `develop` へ squash で統合する。

### ③ 決まりの文書の直し（ブランチ `docs/261110-rules`、squash で統合）

- [x] **Step 14**（FR3.1・FR3.3）: `aidlc/spaces/default/memory/project.md` の Tech Stack の「Jackson の上書きは 3.1.7 とする（…）」の行と、opentelemetry-logback-appender の行の「Spring Boot 4.1.1」を、今の形（上書きは無く Jackson は Spring Boot の BOM の版。Step 3 の版）に書き換え、行の末尾に「（<書き換えのコミットの日付> Intent 261110-deps-forks-review で書き換え）」を付ける。Step 5 で固定を外した・残した結果も同じ節に反映する。前の文と後の文を `generation-notes.md` に並べる。書き換える文面は、コミットの前に依頼者に見せて承認を得る。
- [ ] **Step 15**（NFR3）: 文書だけの変更のため、`verifyQuick` を流して通ったら `develop` へ squash で統合する。

### 記録

- [ ] **Step 16**（FR5.2・FR4）: `code-summary.md`（版の前後の表（JRE のイメージのタグ・Spring Boot・H2 を含み、Build and Test の負荷の試験の分岐に使う）・見送ったもの・固定の結果・FR2.5 の結果・計画との差・Build and Test に引き継ぐこと）と `traceability.json`（FR・NFR ごとの状態と手順）を書く。

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
      "text": "- **Methodology**: test-after\n- **Ordering**: テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。\n- テストは各 Bolt の成果物の一部であり、テストのない機能は完成とみなさない。テスト量はワークフローの Test Strategy に従い、以下の下限はそれに追加される。\n- カバレッジの下限は、すべての Intent に共通で **行カバレッジ 80% 以上、分岐カバレッジ 70% 以上** とする。バックエンド・フロントエンドの両方に適用し、下回ったらビルドを失敗させる。道具はバックエンドが JaCoCo、フロントエンドが `@vitest/coverage-v8`（`thresholds` 設定）。\n- 統合の前の `./gradlew verifyQuick` は、バックエンドのカバレッジの下限を判定しない（単体テストの記録だけから参考値を `backend/build/reports/jacoco/quick/` に出して表示し、下回っても失敗にしない）。バックエンドの下限は、全件の `./gradlew verify` を流す場面（Way of Working）と CI で判定する。画面のカバレッジの下限は `verifyQuick` でも判定する。（2026-11-08 Intent 261107-verify-quick-local で追加）\n- バックエンドでは、全体の合計に加えて、すべてのパッケージごとにも同じ下限（行 80%・分岐 70%）を当てる（外すパッケージは無い）。全体の合計で判定していた既存のパッケージの一覧（`backend/build.gradle.kts` の `packagesJudgedByTotal`。最後は 7 パッケージ（`access.service`・`audit.repository`・`common.error.domain`・`common.error.service`・`common.health`・`common.i18n.domain`・`common.web`））は、Intent 261108-ci-checks-release で `common.health` にテストを足して下限を満たし、一覧とそれを使った除外の仕組みを消した。パッケージを外す一覧を再び作らず、下限を満たさないパッケージはテストを足して満たす。（2026-11-08 Intent 261107-verify-quick-local で書き換え）（2026-11-09 Intent 261108-ci-checks-release で書き換え）\n- フロントエンドのカバレッジの下限は、今の形（全体の合計に当てる `thresholds`）のままとする。新しい機能のフォルダ（`src/features/<featureId>/`）のカバレッジの値は、Build and Test で記録する。\n- カバレッジの計測から外すのは、アプリの起動クラス、設定値だけのクラス、自動生成コード、`vendor/` 配下に限る。除外を後から増やして実質的に下限を下げることはしない。パッケージごとの下限を満たすために除外を増やすこともしない。\n- DB を使うテストは、本番と同じ種類の DB をコンテナで起動して使う（Testcontainers）。そのため、ローカルと CI にコンテナの実行環境があることを前提条件として文書化する。テストごとにデータを用意して巻き戻し、実行順に依存させない。表を作る・消す操作（DDL）がその場で確定して巻き戻せない DB（MySQL・MariaDB など）で表を作るテストは、巻き戻す代わりに、テスト（またはテストのクラス）ごとに名前の重ならないスキーマ（MySQL・MariaDB ではデータベース）を作り、終わったら消す。対象DB の業務データを読むだけのテストでも表を作るため、この決まりを当てる。作る名前には実行ごとに一意の値を入れ、途中で止まった前の実行の残りに頼らない。\n- 対象DB（MySQL・MariaDB・PostgreSQL）の結合テストは、3種類すべてを全件の `./gradlew verify` の中と CI で毎回実行する。統合の前の `./gradlew verifyQuick` では、`-PitTests` で絞ったときに当たるものだけを流す。コンテナの実行環境が無いときの扱いは Way of Working のとおり。（2026-11-08 Intent 261107-verify-quick-local で書き換え）\n- テストを足した Bolt の後は、統合の前に流した1コマンドの検査（`./gradlew verifyQuick`。全件の場面に当たる Bolt では `./gradlew verify`）の時間を、Build and Test では全件の `./gradlew verify` の時間と CI の時間を測って記録する。CI の上限（`timeout-minutes: 60`）の7割の約 42 分を超えたら、テストの分け方を依頼者に諮る。（2026-11-08 Intent 261107-verify-quick-local で書き換え）\n- 画面からの一連の操作を確かめるテスト（E2E）は Playwright（`./gradlew e2eTest`）で書き、`./gradlew verify` と CI の外に置く。本数は代表的な流れに絞り、機能の Intent ごとに代表の流れを1本まで足す。新しく足す流れは、前のテストが作った状態に頼らず、利用者などの前提を自分で作る。複数の機能の Intent を1つに束ねた Intent では、束ねた元の Intent ごとに数える。既存の E2E を新しい形（権限の形など）に合わせて書き換えることは、本数に数えない。利用者の状態を変える操作（利用停止・管理の権限の変更・ロックなど）は、その流れで自分で作った利用者だけを対象にし、初期管理者の状態は変えない。役割・権限を変える操作も、その流れで自分で作った利用者と役割だけを対象にし、初期管理者は変えない（E2E は1つのアプリ・内部DB・初期管理者を全ファイルで共有して順に流すため）。E2E を流す時点は Way of Working のとおり（Intent の最後とリリースの前）とする。\n- 入力と出力の性質をランダムな入力で確かめるテスト（性質ベースのテスト）を、純粋な関数（ロック判定の回数計算、有効期限の判定、入力の検証など）に一部適用する。道具は Java が jqwik、フロントエンドが fast-check。失敗時の乱数の種を記録して再現できるようにする。\n- テストの説明文（テスト名、`describe` / `it`、`@DisplayName`）は英語で書く。テストデータは日本語でよい。\n- リポジトリは公開のため、テストデータのメールアドレスは予約のドメイン（`example.com` など）だけにし、実在しそうな氏名・宛先を置かない。\n- フロントエンドのテストは対象と同じ場所に `*.test.ts` / `*.test.tsx` として置き、Vitest ＋ Testing Library（jsdom）＋ user-event ＋ vitest-axe を使う。画面部品ごとにアクセシビリティ検査を1件入れる。\n- 画面のテストで、描画の後（`useEffect` などの効果）に反映される値は、操作の直後に同期で確かめず、`waitFor` で待って確かめる。テストの時間の上限（Vitest の既定 5 秒など）は、原因を確かめずに延ばさない。\n- Java のテストは `src/test/java` に、対象と同じパッケージ構成で置く。単体テストは `XxxTest`、Spring や DB を起動する結合テストは `XxxIT` とし、分けて実行できるようにする。\n- 時刻に依存する処理（有効期限、ロックの解除など）は注入可能な時計（`Clock` 等）から現在時刻を取得し、テストで `sleep` や実時刻に依存しない。\n- 不安定なテストと CI の失敗は、次の1つの決まりで扱う。\n  - 手元で再現した不安定なテストは、原因を直すまで統合しない（次へ進まない）。\n  - 手元で再現しないものは、再現の試みに先に時間の上限を決め、見立てと試みの範囲を記録したうえで、CI の再実行で通れば進めてよい（不安定と確かめられていない扱い）。\n  - 同じテストが二度目に落ちたら、原因を直すまで次へ進まない。\n  - 失敗を直さずに次の Intent へ持ち越すのは、依頼者の決定があるときだけとし、決まりとの差を記録する。\n  - 不安定なテストを直したときは、二段で確かめる。疑った競り合い・待ちの抜けを待ち合わせや順の入れ替えで確実に起こせたときは、直す前のテストが落ち、直した後のテストが通ることを記録する。起こせないときは、上の「手元で再現しないもの」と同じく、試みの時間の上限を決めて試み、見立てと試みの範囲を記録する。\n- 権限ごとの API の認可のテストの共通の形（下の役割・権限、業務データ、他テーブル参照の一覧から「共通の形」として参照する）: 未認証（401）・要る権限だけを欠く利用者（403。権限が何も無い利用者ではなく、ほかの権限は持つ利用者）・権限を持つ（200）を、サーバー側のテストで確かめる。API と権限の組は、パラメーターを使うテストで表として書く。\n- axe-core を上げて新しい規則の違反が出たときは、規則を外して検査を通さず、違反をすべて直してから上げる（固い制約は `project.md` の Forbidden）。違反の要素が make-you-chic-ui の部品のものなら依頼を出し、固定先の更新で取り込んでから上げる。上げたときは、実際のブラウザの axe（E2E）と画面のテストの vitest-axe の両方を流し、E2E の全件の通過と違反 0 件を件数で記録する。あわせて、axe-core の版が lockfile で1つにそろっているかを確かめる。\n- 性能・資源の目標の測り方（物差し）を変えるのは、要件定義で依頼者が明示に決めたときだけとし、変える理由を記録する。変えた後も、少なくとも1回は前の物差しの値を並べて記録する。\n- 負荷の試験の使い捨ての環境の対象DB の行は、perf の台本（`perf/` の下）で決めた種から作る（行の数は NFR 要件の段で決める）。k6 の場面の setup は、前の場面が残した状態に頼らない。\n\n- 内部DB（組み込みの H2）を使うテストは、コンテナではなく本番と同じ組み込みの H2 で行う。Testcontainers は、コンテナで動かす対象DB（後続 Intent D・E で扱う業務DB）のテストに使う。Walking Skeleton の「DB を使うテスト1件以上（本番と同じ種類の DB をコンテナで起動する）」も、内部DBについてはこの読み方とする。 (learned 2026-09-22) \n\n- 画面を廃止する・移すことで既存の E2E を書き換える・消すときは、本数に数えない。ただし、その画面で確かめていた検査（例: 管理の仮の画面の E2E 030・090・130 の 403 の表示・アクセシビリティ・管理の権限の確認の API）は、別の画面か別のテストへ移して残し、移す前と後の件数を Build and Test で記録する。"
    },
    {
      "layer": "project",
      "text": "- テストの件数やカバレッジを報告するときは、`./gradlew verify` がテストのタスクを UP-TO-DATE で飛ばすことがあるため、`:backend:cleanTest :backend:cleanIntegrationTest` を付けた全件の `./gradlew verify` で実行し直し、実測の数字だけを報告する。`./gradlew verifyQuick` の JaCoCo の値は単体テストだけの参考値で、カバレッジの報告には使わない。 (learned 2026-09-23)  （2026-11-08 Intent 261107-verify-quick-local で書き換え）\n- 実際のブラウザのアクセシビリティの検査（axe）は、誤りを出した状態と、現実に近いデータ（2語の氏名など）で、ブランドカラーとテーマのすべての組について行う。user-management の U7 の 080 で初めて、make-you-chic-ui の Avatar（頭文字が2文字のとき）と dark の FormField の誤りの文字のコントラスト不足が出た（050〜070 は誤りの状態を見ず、氏名が1語だったため出なかった）。すべての組で行うのは、その Intent で変えた画面だけとし、変えていない画面は E2E の既存の検査に任せる（2026-11-07 依頼者の決定で緩めた）。 (learned 2026-09-28) \n- Delivery Planning でのカバレッジの実測は、./gradlew verify 全体ではなく :backend:cleanTest :backend:cleanIntegrationTest :backend:test :backend:integrationTest :backend:jacocoTestReport で行い、jacocoTestReport.xml から手を入れる見込みのパッケージの値を読む（user-admin で約 5 分）。 (learned 2026-10-01) \n- 画面のはみ出しの確かめ（E2E・axe）では、画面全体の横のスクロールだけでなく、開いたメニュー・ポップアップなど画面に固定で置く部品が画面の中に収まることも確かめる。表の右端に置く make-you-chic-ui の Dropdown は placement に bottom-end を指定する（user-admin の配備の後のスモークテストで、行の「操作」のメニューが右へはみ出していたのを E2E 120 が拾えなかった）。 (learned 2026-10-03) \n- 木の画面の単体テスト・E2E・スモークテストでは、本物と同じ形の応答（子の有無の印を含む）を使い、すべての段の開閉を押して確かめる（role-menu の配備の後のスモークテストで、「自分の権限を見る」の木でテーブルの開閉を押すと自分自身を子に持って固まる不具合が見つかった。単体の見本のテーブルは子が無い形で本物と違い、E2E は選ぶだけで開閉を押していなかった）。 (learned 2026-10-14) \n- 画面の不具合を使い捨ての環境で再現するときは、依頼者がした操作（どのボタン・どの開閉を押したか、画面の文言とその場所）を先に聞き、同じ操作を試す。role-menu の S8 で、選ぶだけで開閉を押さずに再現せず「環境の差」と誤って見立て、依頼者の再試行で条件が分かった。 (learned 2026-10-14) \n- 長いテスト（verify・E2E・k6）は、試験の台本全体を `caffeinate -dims` で包む（場面ごとに起こし直すと切れ目で守りが外れる）。流す前に `pmset -g assertions` で眠らない状態か、流した後に `pmset -g log` で Sleep・DarkWake が無かったかを確かめ、入っていたら結果を使わずに流し直す（DarkWake の Thermal Emergency のスリープは `caffeinate -i` では止められない）。 (merged 2026-11-04)\n- colima の PC で `./gradlew verify`、または `-PitTests` を付けた `./gradlew verifyQuick` を流すときは、README の `DOCKER_HOST` と `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE`（書き方は `docs/target-db.md`）をシェルに渡す。渡さないと対象DB のテストが SKIPPED になり、`verify` ではパッケージごとのカバレッジの下限で失敗する。`-PitTests` を付けた `verifyQuick` では、バックエンドのカバレッジの下限を判定しないため、飛ばさずに失敗する（CI と同じ扱い）。 (merged 2026-11-04)（2026-11-08 Intent 261107-verify-quick-local で書き換え）\n- Playwright の `webServer.env` に置いた値は json の報告に残る。E2E の仮の資格情報はプロセスの環境変数で渡し、報告の json の文字列の検索で、パスワード・トークン・メールアドレスが入っていないことを確かめる。 (merged 2026-11-04)"
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
  "input_sha256": "sha256:232d68268948db0bbf748d59d639a34d3b1ba0f63a5166d7e932eaf018dba00e",
  "contract_sha256": "sha256:3046723ed9b439dbeb5b04d57ca54fe04b51c1afac4b366aa5441a5798d5f872"
}
```

### Testing Contract の層の当てはめ

依存と道具の更新で、新しい振る舞いのコードは書かない見込み。そのため、層ごとの「実装してからその層のテスト」は、次のように当てはめる。

- 環境・ビルドの設定（`plan_profile` の Environment/build configuration）: Step 3〜7・10〜12。各 Step の後に、その場所に当たる既存のテスト（下の手順書の絞ったコマンド、frontend の型の検査・リンタ・画面のテスト）を流す。
- 大きな版の更新で本体のソースを直したとき（判定基準の範囲の中）は、直した層の既存のテストを流し、テストが無い振る舞いの変更があればその層のテストを足す。
- bugfix の回帰の下限: この Intent に不具合の直しは無い。固定を外したときは、固定の理由を確かめる既存のテスト（`OtlpLogExportIT`・`ExternalExportIT`・`H2CompactThreadsTest`・`H2CompactionByPoolSuspensionIT`）と手元の外部エクスポートの起動を回帰の確かめとし、固定の理由が残るときはテストの期待（`H2CompactThreadsTest` の値など）を変えない。

## 単位のテストの手順書

`unit-test-instructions.md` に置く。

## Build and Test に引き継ぐこと

- FR4（`-PitForks` の比べ）: 依存の更新を統合してプッシュした後、同じコミットで CI の手動の実行を 2・3 交互に各3回流し、中央値で既定の値を決め、`.github/workflows/ci.yml` の `IT_FORKS_DEFAULT` とコメント・`docs/verification.md` の節を直す（直すコミットは Build and Test で依頼者の承認を得て積む）。
- FR5（負荷の試験）: Step 16 の版の前後の表で、Spring Boot・H2・JRE のどれかが変わっていれば、判定の場面を3種類の対象DB で1回ずつ流す。
- E2E の全件（Intent の最後）と、全件の `verify` の時間・CI の検査の job の時間の記録。
- axe-core を上げたときの E2E と vitest-axe の件数（違反 0 件）。

## 承認の場で確かめたいこと

- Q-A: 統合を3つのまとまり（① 実行環境と依存、② CI・イメージ・道具、③ 決まりの文書）に分け、それぞれ squash で `develop` に戻す形でよいか。① と ② の統合の前に、それぞれ全件の `verify` と E2E を流す（合わせて約 1.5 時間ずつ）。
- Q-B: Step 8（E2E の WAR の JVM の時差）で `team.md` の「4か所」に合わないと分かったときは、生成を止めて直し方を諮る形でよいか。
