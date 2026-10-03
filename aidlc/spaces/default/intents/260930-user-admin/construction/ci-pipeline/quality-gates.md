# 品質の関門（quality-gates）

Intent `260930-user-admin`（U1〜U5、Bolt B1〜B5）の関門の一覧です。次のことを記録します。

- 合否の基準と、それを実行するタスク
- **Build and Test が記録した検査と、CI の段との対応**（`project.md` の Deployment）
- 意図して CI の外に置く検査と、代わりの実行の場

この文書は、既にある実装の記録です。新しい関門を足すものではありません。記録の対象は `build.gradle.kts`・`backend/build.gradle.kts`・`.gitleaks.toml`・`.pre-commit-config.yaml`・`.github/workflows/ci.yml`・`.github/dependabot.yml` です。前の Intent（`260925-user-management`）の `quality-gates.md` からの差分を中心に書きます。

## 0. 関門の3つの場所

| 場所 | 実行するもの | 統合を止めるか |
|---|---|---|
| コミットの直前 | pre-commit のフック（Gitleaks・Spotless・Prettier） | **止める**（コミットが失敗する） |
| **統合の前（手元）** | `./gradlew verify`（0〜9 の段）。colima が動いていること。画面・認証に関わる変更の統合の前とリリースの前は、Mailpit を起動して `./gradlew e2eTest` も流す | **止める。これが統合の関門**（`team.md` の Way of Working）。対象DB のテストが SKIPPED の状態では統合しない |
| 統合の後（CI） | GitHub Actions が同じ `./gradlew verify` を実行 | 統合は止めない（統合の後に動くため）。失敗したら、次の Bolt に進む前に「不安定なテストと CI の失敗」の決まりで扱う |

## 1. 関門の基準と、この Intent で変わったもの

| 関門 | 合否の基準 | この Intent での変化 | 実行するタスク（段） |
|---|---|---|---|
| サブモジュールの不変 | 2つのサブモジュールの追跡されるファイルが変わっていれば失敗 | 変わらず。make-you-chic-ui の固定先を `3d9521a` に上げた（専用のコミット `364e9d6`） | `verifyPrepare`（0） |
| 依存の取得 | lockfile どおりに入れられなければ失敗 | `frontend/.npmrc` に `ignore-scripts=true`（パッケージのスクリプトを動かさない。`17af97d`） | `verifyPrepare`（0） |
| フォーマット | 差分があれば失敗 | 変わらず | `verifyFormat`（1） |
| リンタ | error があれば失敗 | 変わらず | `verifyLint`（2） |
| ライセンスヘッダー | ヘッダーが無い・形が違えば失敗 | 変わらず | `verifyLicense`（3）・`verifyFormat`（1） |
| ビルド | コンパイル・型・ビルドの誤りで失敗 | 変わらず | `verifyBuild`（4） |
| 全テスト | 1件でも失敗したら失敗。対象DB のテストは CI では飛ばさない | テストが増えた（単体 1243→**1508**・結合 566→**689**・画面 732→**955** 件。`b126bdc`・`81d2423` の実測） | `verifyUnitTest`（5）・`verifyIntegrationTest`（6） |
| カバレッジ | 行 **80%** 未満、または分岐 **70%** 未満で失敗。バックエンドは全体と、パッケージごと | 全体の合計で判定する既存のパッケージの一覧（`packagesJudgedByTotal`）を **12 から 7** に減らした（B1 で `auth.domain`・`auth.repository`・`access.domain`、B4 で `common.error.web`・`common.observability`）。除外は増やしていない | `verifyCoverage`（7） |
| 秘密情報 | Gitleaks が1件でも検出したら失敗（履歴全体） | `.gitleaks.toml` に、監査ログのレビューの記録のフォルダの識別子の誤検知の除外を足した（パスと値の形の両方で絞る。`f299400`） | pre-commit と `gitleaksScan`（8） |
| Java の静的解析 | priority 1、パターン名が `SQL_` で始まるもの、`PREDICTABLE_RANDOM`、`SMTP_HEADER_INJECTION` の指摘が1件でもあれば失敗 | 変わらず（`backend/config/spotbugs-exclude.xml` も変えていない） | `spotbugsGate`（8） |
| 依存の脆弱性 | 重大度 High 以上で失敗（npm の devDependencies は警告。成果物を作る道具と `MAL-` は止める） | 変わらず。Jackson の High 4 件を 3.1.7 への上書きで解いた（`f299400`） | `osvScan`（8） |
| 成果物 | WAR を作れない、WAR の中の JSON Schema が正本と違えば失敗。初回の JavaScript は 500KB を超えても警告だけ | 変わらず（初回の JavaScript は gzip で 125.5 KB） | `verifyArtifact`（9） |

## 2. `./gradlew verify` の段

`build.gradle.kts` の `verifyStages` の並びです。1つでも失敗したら、後ろの段は実行しません。CI も同じタスクを呼びます。

| 段 | タスク | 中身 |
|---|---|---|
| 0 | `verifyPrepare` | 道具の確認、make-you-chic-ui のビルド、2つのサブモジュールの不変、依存の取得 |
| 1 | `verifyFormat` | Spotless（ルートと backend）・Prettier |
| 2 | `verifyLint` | oxlint・ESLint・Stylelint |
| 3 | `verifyLicense` | 画面のライセンスヘッダー（Java・Kotlin DSL・メールのテンプレートは 1 の段の Spotless） |
| 4 | `verifyBuild` | Java のコンパイル（本体とテスト）、`tsc --noEmit`、Vite のビルド |
| 5 | `verifyUnitTest` | JUnit・Vitest |
| 6 | `verifyIntegrationTest` | 結合テスト（組み込みの H2 と対象DB のコンテナ） |
| 7 | `verifyCoverage` | JaCoCo の報告と検証、`@vitest/coverage-v8` |
| 8 | `verifySecurity` | `spotbugsGate`・`osvScan`・`gitleaksScan` |
| 9 | `verifyArtifact` | `bootWar`・`verifyDslSchemaInWar`・`frontendBundleSize` |

`team.md` の Deployment の実行順（フォーマット → リンタ → ライセンスヘッダー → ビルド → 単体テスト → 結合テスト → カバレッジ → セキュリティ）と一致します。

## 3. 依存の更新の知らせと、脆弱性の関門

| 項目 | 扱い |
|---|---|
| Dependabot の版の更新（gradle・npm・github-actions・docker・docker-compose） | 週に1回。プルリクエストは GitHub の画面でマージしない。手元で版と lockfile をまとめて更新し、`./gradlew verify` を通してから統合し、プルリクエストは閉じる（`team.md` の Way of Working）。開いたままは 0 件（2026-10-03） |
| Dependabot alerts（脆弱性の知らせ） | **使っていない**（前の Intent の CI Pipeline の Q2: B） |
| 脆弱性の関門 | **`./gradlew verify` の `osvScan` だけ**（重大度 High 以上で失敗）。手元の統合の前と、統合の後の CI（まっさらな環境で毎回走る）で働く |

**`team.md` との差**：`team.md` の Way of Working には「重大度 High 以上の知らせは、次の Bolt に入る前に取り込む」とあります。一方、Dependabot alerts を使っていないため、脆弱性の知らせは届きません。前の Intent の CI Pipeline の Q2: B（依頼者の決定）で、有効にせず、`osvScan` だけを関門としました。`team.md` の Way of Working にも「依存関係の脆弱性の関門は、1コマンドの検査の中の OSV-Scanner とする」と記録されています。`osvScan` は `verify` と CI を流したときにだけ働き、新しい脆弱性の公表の時点では知らせません。

この Intent では、B1 の前（2026-10-02）に `osvScan` が Jackson 3.1.6 の High 4 件で止め、`f299400` で 3.1.7 に上げて解きました。Jackson の BOM は Dependabot の `ignore` に入っているため、この形（知らせではなく次の `verify` で気づく）が実際に働いた例です。

手元の `osvScan` は、lockfile が変わらなければ UP-TO-DATE で飛ばされます。手元で調べ直すときは `./gradlew osvScan --rerun-tasks` を使います（`build-and-test/build-instructions.md`）。

## 4. Build and Test が記録した検査と CI の段との対応

Build and Test のコマンドは `construction/build-and-test/build-instructions.md`（4節）・`security-test-instructions.md`（1節・3節）・`integration-test-instructions.md`・`performance-test-instructions.md` にあり、実測は `test-results.md` にあります。これらを CI の段に対応づけました。

| Build and Test のコマンド | 何を確かめたか | CI での実行 | 対応する CI の段 |
|---|---|---|---|
| `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（colima の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡す） | 統合の前の関門の全体（0〜9 の段）。件数（単体 1508・結合 689・画面 955）とカバレッジ（全体と、パッケージごと） | **実行する** | ジョブ `verify` の「1コマンドの検査を実行する」（`./gradlew verify`）。CI はまっさらな環境のため、`clean` を付けなくてもテストは必ず走る。Docker はランナーのものを使う |
| `./gradlew :backend:integrationTest`（`--tests` で1クラスを絞るものを含む） | 単位の境目の結合テスト（U1〜U3、上限切れの漏えい） | **実行する**（全件） | `verify` の 6 の段（`verifyIntegrationTest`） |
| `caffeinate -i ./gradlew osvScan --rerun-tasks` | 依存の脆弱性（失敗の条件 0 件・警告 16 件） | **実行する** | `verify` の 8 の段（`osvScan`）。CI はまっさらな環境のため UP-TO-DATE にならず、毎回走る |
| `spotbugsGate`・`gitleaksScan`（`verify` の 8 の段） | 静的解析と秘密情報の検出 | **実行する** | `verify` の 8 の段。Gitleaks・OSV-Scanner は CI で版と SHA-256 を照合して入れる |
| `frontendBundleSize`（`verify` の 9 の段） | 初回の JavaScript（gzip 125.5 KB） | **実行する** | `verify` の 9 の段（500KB を超えても警告だけ） |
| `caffeinate -i ./gradlew e2eTest`（Mailpit を起動） | E2E 13 ファイル・152 件（代表の流れ 110、実際のブラウザの検査 120・130、画面の時間の記録、json の報告の秘密の確かめ） | **実行しない（意図して外す）** | 5節 |
| `docker run … grafana/k6:2.3.0 inspect --include-system-env-vars …` | k6 の5つの場面と既存の場面が読み込めること | **実行しない（意図して外す）** | 5節 |
| `gh run list`・`gh run view` | CI の結果（`ci-config.md` の 6節） | — | この段と Build and Test の確かめ |

目標の判定表（`build-and-test-summary.md` の Target Verification Matrix）の中で統合の関門に当たるもの（フォーマット・リンタ・ライセンスヘッダー・ビルド・全テスト・カバレッジ・秘密情報・静的解析・依存の脆弱性・成果物）は、どれも CI が同じ `./gradlew verify` で実行します。B1〜B5 の統合の後と承認の後の push の CI は、すべて success でした（`ci-config.md` の 6.1）。

## 5. 意図して CI の外に置く検査と、代わりの実行の場

| 検査 | CI の外に置く理由 | 代わりの実行の場 | 持ち主 |
|---|---|---|---|
| **E2E と実際のブラウザの検査**（`./gradlew e2eTest`、010〜130 の 13 ファイル） | ブラウザー（Playwright の Chromium）と起動した WAR、手元の Mailpit が要る。`team.md` の Testing Posture で、`verify` と CI の外に置くと決めている | 画面・認証に関わる変更の統合の前と、リリースの前に手元で実行（README の E2E の節）。この Intent では B1（110 件）・B2（130 件）・B4（130 件）・B5（152 件）の統合の前の関門で、すべて expected。B3 は条件に当たらず流していない | 依頼者と AI |
| **負荷の試験**（k6、`perf/README.md`。`userAdminList`・`userAdminProfile`・`userAdminOps`・`userAdminSuspendWorst`・`userAdminPool` と既存の場面） | 配備とは別の使い捨ての環境と、資源の上限をそろえた条件が要る（`project.md` の Testing Posture） | 手元の使い捨ての環境（`docker/perf/compose.yaml`）。台本の読み込みは B4 の Step 39 で確かめた | performance-validation |
| **画面の時間の本番での判定**（U5-NFR5.1・NFR5.2） | E2E の WAR は一時の内部DB で、配備したアプリと条件が違う。記録のみ | 配備した環境 | performance-validation・observability-setup・feedback-optimization |

## 6. 止めるもの・止めないものの整理

| 関門 | 統合を止める | 警告にとどめる |
|---|---|---|
| pre-commit（Gitleaks・フォーマット） | ○ | — |
| 0 準備（サブモジュール2つの不変を含む）／1〜4 | ○ | — |
| 5 単体テスト／6 結合テスト | ○（1件でも失敗したら。手元で対象DB のテストが SKIPPED なら統合しない） | 手元の SKIPPED は警告を出す（統合はしない） |
| 7 カバレッジの下限 | ○（全体と、パッケージごとに、行 80%・分岐 70%） | — |
| 8 Gitleaks | ○ | — |
| 8 SpotBugs | ○（priority 1、`SQL_`、`PREDICTABLE_RANDOM`、`SMTP_HEADER_INJECTION`） | それ以外の priority 2・3（この Intent の新しい指摘は `EI_EXPOSE_REP2`・`CT_CONSTRUCTOR_THROW`・`SPRING_ENDPOINT`・`SERVLET_HEADER_USER_AGENT` の警告だけ） |
| 8 OSV-Scanner | ○（High 以上。npm の開発用は道具と `MAL-` だけ） | npm の開発用（道具と `MAL-` を除く）、重大度が不明。今は警告 16 件（すべて npm の開発用） |
| 9 WAR の生成と JSON Schema の一致 | ○ | — |
| 9 初回の JavaScript の量 | × | 500KB を超えたら警告 |
| E2E（`./gradlew e2eTest`） | 手元で統合の前とリリースの前に実行し、失敗したら統合しない | —（`verify` と CI の外） |

## 7. CI（統合の後）に固有の関門

CI は `./gradlew verify` をそのまま呼びます。そのため、1〜6節の関門は、すべて CI でも効きます。CI に固有のものは次の4つです。

| 関門 | 合否の基準 | 実行するもの |
|---|---|---|
| サブモジュールの取得 | 2つのサブモジュールを、固定先のコミットで取得できなければ失敗（`submodules: true`） | `actions/checkout` |
| 道具の真正性 | Gitleaks・OSV-Scanner のダウンロードの SHA-256 が合わなければ失敗 | `ci.yml` の「Gitleaks と OSV-Scanner を入れる」 |
| 対象DB のテストを飛ばさない | Docker に届かなければ、対象DB のテストが失敗する（`CI=true`） | `ContainerRuntimeCheck` |
| 成果物の存在 | WAR が見つからなければ失敗（`if-no-files-found: error`） | `actions/upload-artifact` |

**この Intent の既知の失敗（解消済み）**：`3c800d3`・`eb7c982`・`7f3de43` の CI は、`gitleaksScan` が監査ログのレビューの記録のフォルダの識別子を誤検知して失敗しました（`ci-config.md` の 6.2）。B1 の統合の前に `f299400` で除外を足して直し、その後の CI はすべて success です。

## Sources

- `.github/workflows/ci.yml`・`.github/dependabot.yml`・`.gitleaks.toml`・`build.gradle.kts`（`verifyStages`）・`backend/build.gradle.kts`（`packagesJudgedByTotal`）・`frontend/.npmrc`
- `construction/build-and-test/build-instructions.md`・`security-test-instructions.md`・`integration-test-instructions.md`・`performance-test-instructions.md`・`test-results.md`・`build-and-test-summary.md`
- 各単位の `code-summary.md`（`construction/u1-user-suspension/` 〜 `construction/u5-user-admin-ui/` の `code-generation/`）
- `construction/ci-pipeline/ci-pipeline-questions.md`・`ci-config.md`
- `git log`・`git show --stat f299400`、`gh run list`・`gh run view`・`gh pr list`（読み取り）
- `aidlc/spaces/default/memory/team.md`（Way of Working・Testing Posture・Code Style・Deployment）・`project.md`（Deployment・Testing Posture・Tech Stack）
- `aidlc/spaces/default/intents/260925-user-management/construction/ci-pipeline/quality-gates.md`（前の Intent の記録、CI Pipeline の Q2: B）

## Assumptions & Open Questions

- 1節のテストの件数の増え方は、Intent の前の基準（`782a9f6`、U1 の `code-summary.md` の2節）と、Build and Test の実測（`b126bdc`・`81d2423`）の差です。CI の run の中の件数は、この段では数えていません（結果の success だけを確かめた）。
