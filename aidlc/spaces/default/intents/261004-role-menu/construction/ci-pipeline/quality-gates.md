# 品質の関門（quality-gates）

Intent `261004-role-menu`（U1〜U7、Bolt B1〜B9）の関門の一覧です。次のことを記録します。

- 合否の基準と、それを実行するタスク
- **Build and Test が記録した検査と、CI の段との対応**（`project.md` の Deployment）
- 意図して CI の外に置く検査と、代わりの実行の場
- OSV-Scanner を CI では毎回確かめ、手元では `--rerun` で流し直す手順（`ci-pipeline-questions.md` の Q2: A）
- 不安定なテストと CI の失敗の扱い

この文書は、既にある実装の記録です。新しい関門を足すものではありません（Q1: A）。記録の対象は `build.gradle.kts`・`backend/build.gradle.kts`・`.gitleaks.toml`・`.pre-commit-config.yaml`・`.github/workflows/ci.yml`・`.github/dependabot.yml` です。前の Intent（`260930-user-admin`）の `quality-gates.md` からの差を中心に書きます。

## 0. 関門の3つの場所

| 場所 | 実行するもの | 統合を止めるか |
|---|---|---|
| コミットの直前 | pre-commit のフック（Gitleaks・Spotless・Prettier） | **止める**（コミットが失敗する） |
| **統合の前（手元）** | `./gradlew verify`（0〜9 の段）。colima が動いていること。画面・認証に関わる変更の統合の前とリリースの前は、Mailpit を起動して `./gradlew e2eTest` も流す | **止める。これが統合の関門**（`team.md` の Way of Working）。対象DB のテストが SKIPPED の状態では統合しない |
| 統合の後（CI） | GitHub Actions が同じ `./gradlew verify` を実行 | 統合は止めない（統合の後に動くため）。失敗したら、次に進む前に「不安定なテストと CI の失敗」の決まりで扱う（6節） |

## 1. 関門の基準と、この Intent で変わったもの

| 関門 | 合否の基準 | この Intent での変化 | 実行するタスク（段） |
|---|---|---|---|
| サブモジュールの不変 | 2つのサブモジュールの追跡されるファイルが変わっていれば失敗 | 変わらず。make-you-chic-ui の固定先を `e82b651` から `5bf1ffe` に上げた（専用のコミット `e911e59`、入れ子のサイドバー `navSections`） | `verifyPrepare`（0） |
| 依存の取得 | lockfile どおりに入れられなければ失敗 | 変わらず（`frontend/.npmrc` の `ignore-scripts=true` は残る。lockfile・`package.json`・`libs.versions.toml` に Intent の間の差なし） | `verifyPrepare`（0） |
| フォーマット | 差分があれば失敗 | 変わらず | `verifyFormat`（1） |
| リンタ | error があれば失敗 | 変わらず（機能どうしの import を止める ESLint の制限は B2 で画面の設定に入った。設定の中身の変更で、関門の形は同じ） | `verifyLint`（2） |
| ライセンスヘッダー | ヘッダーが無い・形が違えば失敗 | 変わらず | `verifyLicense`（3）・`verifyFormat`（1） |
| ビルド | コンパイル・型・ビルドの誤りで失敗 | 変わらず | `verifyBuild`（4） |
| 全テスト | 1件でも失敗したら失敗。対象DB のテストは CI では飛ばさない | テストが増えた（単体 **2,102**・結合 **1,165**・画面 **1,581** 件。Build and Test の実測）。単体テストの JVM のヒープを 1g から **1536m** に上げた（`3a30982`、B7。結合テストは 1g のまま） | `verifyUnitTest`（5）・`verifyIntegrationTest`（6） |
| カバレッジ | 行 **80%** 未満、または分岐 **70%** 未満で失敗。バックエンドは全体と、パッケージごと | 変わらず。`packagesJudgedByTotal` は **7** のまま（7 パッケージの `src/main` に Intent の間の差 0 件）。計測の除外は増やしていない。新しいパッケージ（`group.*`・`role.*`・`navigation.*` など）は自動でパッケージごとの下限の対象 | `verifyCoverage`（7） |
| 秘密情報 | Gitleaks が1件でも検出したら失敗（履歴全体） | 変わらず（`.gitleaks.toml` に Intent の間の差なし） | pre-commit と `gitleaksScan`（8） |
| Java の静的解析 | priority 1、パターン名が `SQL_` で始まるもの、`PREDICTABLE_RANDOM`、`SMTP_HEADER_INJECTION` の指摘が1件でもあれば失敗 | 変わらず（`backend/config/spotbugs-exclude.xml` に差なし。関門の指摘 0 件、警告 278 件） | `spotbugsGate`（8） |
| 依存の脆弱性 | 重大度 High 以上で失敗（npm の devDependencies は警告。成果物を作る道具と `MAL-` は止める） | 変わらず（失敗の条件 0 件・警告 19 件）。手元の扱いは 3節 | `osvScan`（8） |
| 成果物 | WAR を作れない、WAR の中の JSON Schema が正本と違えば失敗。初回の JavaScript は 500KB を超えても警告だけ | 照合する JSON Schema を `dsl-schema-v1.json` から **`dsl-schema-v2.json`** に替えた（`44677cc`、B1 の DSL の書式の版 2）。初回の JavaScript は gzip で 158.4 KB | `verifyArtifact`（9） |

## 2. `./gradlew verify` の段

`build.gradle.kts` の `verifyStages` の並びです。前の Intent から変わっていません。1つでも失敗したら、後ろの段は実行しません。CI も同じタスクを呼びます。

| 段 | タスク | 中身 |
|---|---|---|
| 0 | `verifyPrepare` | 道具の確認、make-you-chic-ui のビルド、2つのサブモジュールの不変、依存の取得 |
| 1 | `verifyFormat` | Spotless（ルートと backend）・Prettier |
| 2 | `verifyLint` | oxlint・ESLint・Stylelint |
| 3 | `verifyLicense` | 画面のライセンスヘッダー（Java・Kotlin DSL・メールのテンプレートは 1 の段の Spotless） |
| 4 | `verifyBuild` | Java のコンパイル（本体とテスト）、`tsc --noEmit`、Vite のビルド |
| 5 | `verifyUnitTest` | JUnit（ArchUnit の構造の検査と各機能の `*BoundaryArchitectureTest` を含む）・Vitest |
| 6 | `verifyIntegrationTest` | 結合テスト（組み込みの H2 と対象DB の3種類のコンテナ） |
| 7 | `verifyCoverage` | JaCoCo の報告と検証、`@vitest/coverage-v8` |
| 8 | `verifySecurity` | `spotbugsGate`・`osvScan`・`gitleaksScan` |
| 9 | `verifyArtifact` | `bootWar`・`verifyDslSchemaInWar`・`frontendBundleSize` |

`team.md` の Deployment の実行順（フォーマット → リンタ → ライセンスヘッダー → ビルド → 単体テスト → 結合テスト → カバレッジ → セキュリティ）と一致します。

## 3. 依存の脆弱性の関門と、手元での流し直し（Q2: A）

| 項目 | 扱い |
|---|---|
| 脆弱性の関門 | **`./gradlew verify` の `osvScan` だけ**（重大度 High 以上で失敗）。Dependabot alerts は使っていない（`team.md` の Way of Working） |
| CI（統合の後） | まっさらな環境で動くため `osvScan` は UP-TO-DATE にならず、**毎回** OSV のデータベースに照らして確かめる |
| 手元の `./gradlew verify` | lockfile が変わらなければ `osvScan` は UP-TO-DATE で飛ばされ、**前回の結果のまま**になる。新しく公表された脆弱性は確かめていない |
| 手元で確かめ直す手順 | `./gradlew osvScan --rerun`（このタスクだけを流し直す。`build-instructions.md` の `--rerun-tasks` でも同じく流れる）。結果は `build/reports/osv-scanner/osv.json` |
| 流し直す時機 | Dependabot の知らせを見送る依存（`.github/dependabot.yml` の `ignore`）があるため、統合の前の関門で verify の出力に `:osvScan UP-TO-DATE` が出たときと、リリースの前。CI の結果でも気づける |
| ビルドの設定 | 変えない（Q2: A。毎回流す形にすると手元の verify が長くなるため選ばなかった） |
| README | 「依存関係の脆弱性の判定（OSV-Scanner）」の節に同じ手順を足した（この段） |

この Intent の Build and Test では、verify の中の `osvScan` が UP-TO-DATE だったため `./gradlew osvScan --rerun` で流し直し、失敗の条件 0 件・警告 19 件（`vendor/make-you-chic-ui/package-lock.json` の開発用の npm `undici` など）を確かめました（`build-and-test/test-results.md` の 5節）。

## 4. Build and Test が記録した検査と CI の段との対応

Build and Test のコマンドは `construction/build-and-test/build-instructions.md`（4節・5節）・`integration-test-instructions.md`・`security-test-instructions.md`・`performance-test-instructions.md` にあり、実測は `test-results.md` にあります。これらを CI の段に対応づけました。

| Build and Test のコマンド | 何を確かめたか | CI での実行 | 対応する CI の段 |
|---|---|---|---|
| `caffeinate -dims ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（colima の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡す） | 統合の前の関門の全体（0〜9 の段）。件数（単体 2,102・結合 1,165・画面 1,581）とカバレッジ（バックエンド 行 97.9%・分岐 92.5%、画面 行 95.42%・分岐 90.31%、パッケージごと） | **実行する** | ジョブ `verify` の「1コマンドの検査を実行する」（`./gradlew verify`）。CI はまっさらな環境のため、`clean` を付けなくてもテストは必ず走る。Docker はランナーのものを使う |
| `./gradlew :backend:integrationTest`（`--tests` で絞るものを含む） | 単位の間の結合（group と role の削除の問う口、解決の口と navigation、DSL の版 2 と権限の対象）、対象DB の3種類 | **実行する**（全件） | `verify` の 6 の段（`verifyIntegrationTest`） |
| 画面のテスト（`npm run test` などで1ファイルを絞るもの。`ShellLayout.test.tsx` の直しの確かめ） | 直したテストの単独の通過 | **実行する**（全件） | `verify` の 5 の段（`frontendTest`） |
| `caffeinate -dims ./gradlew osvScan --rerun-tasks`（実際は `--rerun`） | 依存の脆弱性（失敗の条件 0 件・警告 19 件） | **実行する** | `verify` の 8 の段（`osvScan`）。CI は毎回走る（3節） |
| `spotbugsGate`・`gitleaksScan`（`verify` の 8 の段） | 静的解析と秘密情報の検出 | **実行する** | `verify` の 8 の段。Gitleaks・OSV-Scanner は CI で版と SHA-256 を照合して入れる |
| `verifyDslSchemaInWar`・`frontendBundleSize`（`verify` の 9 の段） | WAR の中の DSL の JSON Schema（v2）、初回の JavaScript（gzip 158.4 KB） | **実行する** | `verify` の 9 の段 |
| `docker run --rm mastersmith:bt-q2 -XX:+PrintFlagsFinal -version` ほか（`ExitOnOutOfMemoryError` の確かめ） | イメージの JVM の設定（`Dockerfile`） | **実行しない**（CI はイメージを作らない） | 5節。配備の段で扱う |
| `caffeinate -dims ./gradlew e2eTest`（Mailpit を起動） | E2E 18 ファイル（010〜170）・230 件。この Intent で足した 140・150・160・170 を含む。報告の秘密 0 件 | **実行しない（意図して外す）** | 5節 |
| 権限の YAML の上限の測り（Q1、使い捨ての環境）・k6 の場面 | 上限（節 500,000・5 MiB）の確かめと適用の時間とヒープ | **実行しない（意図して外す）** | 5節 |
| `gh run list`・`gh run view` | CI の結果（`ci-config.md` の 6節） | — | この段と Build and Test の確かめ |

目標の判定表（`build-and-test-summary.md` の Target Verification Matrix）の中で統合の関門に当たるもの（`TC-1`〜`TC-5`・`SEC-1`・`SEC-2`・`ART-1`。フォーマット・リンタ・ライセンスヘッダー・ビルド・全テスト・カバレッジ・秘密情報・静的解析・依存の脆弱性・成果物）は、どれも CI が同じ `./gradlew verify` で実行します。`TC-7b`（Build and Test の直しの統合の後の CI）の結果は `ci-config.md` の 6.1 にあります。

## 5. 意図して CI の外に置く検査と、代わりの実行の場

| 検査 | CI の外に置く理由 | 代わりの実行の場 | 持ち主 |
|---|---|---|---|
| **E2E と実際のブラウザの検査**（`./gradlew e2eTest`、010〜170 の 18 ファイル） | ブラウザー（Playwright の Chromium）と起動した WAR、手元の Mailpit が要る。`team.md` の Testing Posture で、`verify` と CI の外に置くと決めている | 画面・認証に関わる変更の統合の前と、リリースの前に手元で実行（README の E2E の節）。この Intent で足したのは 140（ロールの管理の画面の axe と時間の測り）・150（流れ F）・160（骨組みの画面の axe と時間の測り）・170（流れ I）。流れの E2E は 150・170 の2本（`team.md` の上限どおり）。Build and Test の直しの後に 230 件がすべて通過 | 依頼者と AI |
| **負荷の試験**（k6、`perf/README.md`。グループ・ロール・割り当て・作業ロール・受け渡し・メニュー・DSL の場面）と `perf/dsl-timing.sh` | 配備とは別の使い捨ての環境と、資源の上限をそろえた条件が要る（`project.md` の Testing Posture） | 手元の使い捨ての環境（`docker/perf/compose.yaml`）。この Intent の k6 の場面は Build and Test から引き継いだ（Unverified 20 件） | performance-validation |
| **権限の YAML の上限の測り**（配備と同じ最大ヒープ 1 GiB での時間とヒープ） | 同上。テストの JVM のヒープと条件が違う | Build and Test の Q1 で使い捨ての環境で測った（`test-results.md` の 3.2）。k6 の `roleTransferLargeCheck`・`roleTransferLargeApply` での判定は後の段 | build-and-test（済）・performance-validation |
| **イメージの JVM の設定**（`ExitOnOutOfMemoryError`・`MaxRAMPercentage`） | CI はイメージを作らない（成果物は WAR） | Build and Test で `docker run` で確かめた。配備の段でイメージを作るときに手順に書く | deployment-pipeline・deployment-execution |
| **画面の時間の本番での判定** | E2E の WAR は一時の内部DB で、配備したアプリと条件が違う。記録のみ | 配備した環境 | performance-validation・observability-setup |

## 6. 不安定なテストと CI の失敗の扱い

`team.md` の Testing Posture の「不安定なテストと CI の失敗」の決まりで扱います。CI は統合の後の再確認のため、失敗は統合を戻すのではなく、次の Bolt・次の段に進む前に扱います。

| 場合 | 扱い |
|---|---|
| 手元で再現した不安定なテスト | 原因を直すまで統合しない（次へ進まない） |
| 手元で再現しない CI の失敗 | 再現の試みに先に時間の上限を決め、見立てと試みの範囲を記録したうえで、CI の再実行（失敗したジョブの再実行）で通れば進めてよい（不安定と確かめられていない扱い） |
| 同じテストが二度目に落ちた | 原因を直すまで次へ進まない |
| 失敗を直さずに次の Intent へ持ち越す | 依頼者の決定があるときだけ。決まりとの差を記録する |
| 画面のテストの書き方 | 描画の後（`useEffect` などの効果・非同期の答え）に反映される値は `waitFor`・`findBy*` で待って確かめる。時間の上限は原因を確かめずに延ばさない |

**この Intent で当たった例**：`ShellLayout.test.tsx` の「moves the switcher to the top of the content area below 768px and keeps its name」が、`0415b94` の CI（run 38398376712 の1回目）と、Build and Test の手元の verify の1回目・2回目の計3回落ちました。手元の2回目で「同じテストが二度目に落ちた」に当たり、原因（非同期の作業ロールの読み込みの答えの後に出るボタンを同期の `getByRole` で探していた）を直しました（`005730a`。`findByRole` で待つ形。時間の上限は延ばしていない）。CI の1回目の失敗は、依頼者が失敗したジョブを再実行して2回目で通っています。詳しくは `ci-config.md` の 6.2 にあります。

## 7. 止めるもの・止めないものの整理

| 関門 | 統合を止める | 警告にとどめる |
|---|---|---|
| pre-commit（Gitleaks・フォーマット） | ○ | — |
| 0 準備（サブモジュール2つの不変を含む）／1〜4 | ○ | — |
| 5 単体テスト／6 結合テスト | ○（1件でも失敗したら。手元で対象DB のテストが SKIPPED なら統合しない） | 手元の SKIPPED は警告を出す（統合はしない） |
| 7 カバレッジの下限 | ○（全体と、パッケージごとに、行 80%・分岐 70%） | — |
| 8 Gitleaks | ○ | — |
| 8 SpotBugs | ○（priority 1、`SQL_`、`PREDICTABLE_RANDOM`、`SMTP_HEADER_INJECTION`） | それ以外の priority 2・3（今は 278 件） |
| 8 OSV-Scanner | ○（High 以上。npm の開発用は道具と `MAL-` だけ） | npm の開発用（道具と `MAL-` を除く）、重大度が不明。今は警告 19 件 |
| 9 WAR の生成と JSON Schema（v2）の一致 | ○ | — |
| 9 初回の JavaScript の量 | × | 500KB を超えたら警告 |
| E2E（`./gradlew e2eTest`） | 手元で統合の前とリリースの前に実行し、失敗したら統合しない | —（`verify` と CI の外） |

## 8. CI（統合の後）に固有の関門

CI は `./gradlew verify` をそのまま呼びます。そのため、1〜7節の関門は、すべて CI でも効きます。CI に固有のものは次の4つで、前の Intent から変わっていません。

| 関門 | 合否の基準 | 実行するもの |
|---|---|---|
| サブモジュールの取得 | 2つのサブモジュールを、固定先のコミットで取得できなければ失敗（`submodules: true`） | `actions/checkout` |
| 道具の真正性 | Gitleaks・OSV-Scanner のダウンロードの SHA-256 が合わなければ失敗 | `ci.yml` の「Gitleaks と OSV-Scanner を入れる」 |
| 対象DB のテストを飛ばさない | Docker に届かなければ、対象DB のテストが失敗する（`CI=true`） | `ContainerRuntimeCheck` |
| 成果物の存在 | WAR が見つからなければ失敗（`if-no-files-found: error`） | `actions/upload-artifact` |

## Sources

- `.github/workflows/ci.yml`・`.github/dependabot.yml`・`.gitleaks.toml`・`build.gradle.kts`（`verifyStages`）・`backend/build.gradle.kts`（`packagesJudgedByTotal`・`tasks.test` の `maxHeapSize`・`verifyDslSchemaInWar`）・`frontend/.npmrc`・`README.md`（読み取り。README は 3節の手順を足した）
- `git diff 82c36b6 develop`（`.github`・`.gitleaks.toml`・`backend/config`・lockfile・`libs.versions.toml`・`build.gradle.kts`・`backend/build.gradle.kts`・`vendor/`）、`git show 3a30982`・`44677cc`（読み取り）
- `construction/build-and-test/build-instructions.md`・`integration-test-instructions.md`・`security-test-instructions.md`・`performance-test-instructions.md`・`test-results.md`・`build-and-test-summary.md`
- `construction/ci-pipeline/ci-pipeline-questions.md`（Q1: A・Q2: A、まとめの確認 Looks correct）・`ci-config.md`
- `gh run list`・`gh run view`・`gh api …/runs/38398376712/attempts/1`（読み取り）
- `aidlc/spaces/default/memory/team.md`（Way of Working・Testing Posture・Code Style・Deployment）・`project.md`（Deployment・Testing Posture）
- `aidlc/spaces/default/intents/260930-user-admin/construction/ci-pipeline/quality-gates.md`（前の Intent の記録、形の手本）

## Assumptions & Open Questions

- 1節のテストの件数は Build and Test の実測（作業ブランチ `fix/261004-role-menu-bt` の作業ツリー、`005730a` と同じ中身）です。CI の run の中の件数は、この段では数えていません（結果だけを確かめた）。
- 3節の手順は README と同じく `--rerun` で書きました。`build-instructions.md` は `--rerun-tasks` と書いていますが、`osvScan` だけを流すときはどちらでも同じく流れ直します（承認済みの記録は書き換えない）。
