# 品質の関門（quality-gates）

Intent `261016-business-data-views`（U1〜U5、Bolt B1〜B7 と CI の不安定なテスト2件の直し）の関門の一覧です。次のことを記録します。

- 合否の基準と、それを実行するタスク
- **Build and Test が記録した検査と、CI の段との対応**（`project.md` の Deployment）
- 意図して CI の外に置く検査と、代わりの実行の場
- 不安定なテストと CI の失敗の扱いと、この Intent で当たった例

この文書は、既にある実装の記録です。新しい関門を足すものではありません（`ci-pipeline-questions.md` のまとめの確認）。記録の対象は `build.gradle.kts`・`backend/build.gradle.kts`・`backend/config/spotbugs-exclude.xml`・`frontend/vitest.config.ts`・`frontend/eslint.config.js`・`.gitleaks.toml`・`.pre-commit-config.yaml`・`.github/workflows/ci.yml`・`.github/dependabot.yml` です。前の Intent（`261004-role-menu` の同じ段）の `quality-gates.md` からの差を中心に書きます。

## 0. 関門の3つの場所

| 場所 | 実行するもの | 統合を止めるか |
|---|---|---|
| コミットの直前 | pre-commit のフック（Gitleaks・Spotless・Prettier） | **止める**（コミットが失敗する） |
| **統合の前（手元）** | `./gradlew verify`（0〜9 の段）。colima が動いていること。`frontend/src` の本体か、認証・権限・業務データの `src/main` に触れるブランチは、Mailpit と見本の PostgreSQL を起動して `./gradlew e2eTest` も流す | **止める。これが統合の関門**（`team.md` の Way of Working）。対象DB のテストが SKIPPED の状態では統合しない |
| 統合の後（CI） | GitHub Actions が同じ `./gradlew verify` を実行 | 統合は止めない（統合の後に動くため）。失敗したら、次に進む前に「不安定なテストと CI の失敗」の決まりで扱う（6節） |

## 1. 関門の基準と、この Intent で変わったもの

| 関門 | 合否の基準 | この Intent での変化 | 実行するタスク（段） |
|---|---|---|---|
| サブモジュールの不変 | 2つのサブモジュールの追跡されるファイルが変わっていれば失敗 | 変わらず。make-you-chic-ui の固定先を `a9309c4` から `b0a1e79` に上げた（専用のコミット `e34c260`、B3） | `verifyPrepare`（0） |
| 依存の取得 | lockfile どおりに入れられなければ失敗 | 変わらず（`frontend/.npmrc` の `ignore-scripts=true` は残る）。`axe-core` を 4.14.0 に上げた（B7。`npm ls axe-core` は1つ、`hasInstallScript` 無し） | `verifyPrepare`（0） |
| フォーマット | 差分があれば失敗 | 変わらず | `verifyFormat`（1） |
| リンタ | error があれば失敗 | 業務データの画面とその共有の部品・手を入れた骨組みの口に `no-console` を `error` で足した（B5〜B7。規則を足すだけ） | `verifyLint`（2） |
| ライセンスヘッダー | ヘッダーが無い・形が違えば失敗 | 変わらず | `verifyLicense`（3）・`verifyFormat`（1） |
| ビルド | コンパイル・型・ビルドの誤りで失敗 | 変わらず | `verifyBuild`（4） |
| 全テスト | 1件でも失敗したら失敗。対象DB のテストは CI では飛ばさない | テストが増えた（単体 **2,589**・結合 **1,389**・画面 **226 ファイル・1,975** 件。Build and Test の実測）。画面のテストを2つの project に分け、`src/eslintImportRules.test.ts` を先に1つだけで流す形にした（`22de65f`。時間の上限は延ばしていない）。E2E の補助の純粋な部品の単体テスト（`e2e/support/**/*.test.ts`）を対象に足した（`efa145d`） | `verifyUnitTest`（5）・`verifyIntegrationTest`（6） |
| カバレッジ | 行 **80%** 未満、または分岐 **70%** 未満で失敗。バックエンドは全体と、パッケージごと | 変わらず。`packagesJudgedByTotal` は **7** のまま（7 パッケージの本体のソースにどの Bolt も触れていない）。計測の除外は増やしていない（画面のカバレッジの対象は `src/` のまま）。新しいパッケージ（`businessdata.*` など）は自動でパッケージごとの下限の対象 | `verifyCoverage`（7） |
| 秘密情報 | Gitleaks が1件でも検出したら失敗（履歴全体） | 変わらず（`.gitleaks.toml` に Intent の間の差なし） | pre-commit と `gitleaksScan`（8） |
| Java の静的解析 | priority 1、パターン名が `SQL_` で始まるもの、`PREDICTABLE_RANDOM`、`SMTP_HEADER_INJECTION` の指摘が1件でもあれば失敗 | 関門の形は変わらず。除外を足した: `RowSqlExecutor` の `SQL_INJECTION_JDBC`・`SQL_PREPARED_STATEMENT_GENERATED_FROM_NONCONSTANT_STRING` を、読み取りの2メソッドと書き込みの5メソッドだけで外した（クラス・メソッド・パターン名の3つで絞り、理由を書いた。計画の A2・A3 で承認済み。`team.md` の Code Style のとおり、`Connection` の `prepareStatement`・`createStatement` を呼べる場所は `BusinessDataBoundaryArchitectureTest` で同じメソッドに絞っている）。`SQL_` の残り 0 件 | `spotbugsGate`（8） |
| 依存の脆弱性 | 重大度 High 以上で失敗（npm の devDependencies は警告。成果物を作る道具と `MAL-` は止める） | 変わらず（失敗の条件 0 件・警告 18 件。axe-core の指摘無し）。手元の扱いは 3節 | `osvScan`（8） |
| 成果物 | WAR を作れない、WAR の中の JSON Schema が正本と違えば失敗。初回の JavaScript は 500KB を超えても警告だけ | 変わらず。初回の JavaScript は gzip で 163.9 KB（B6。B5 の前 159.3 KB）。`frontend/dist` に axe-core は 0 件 | `verifyArtifact`（9） |

## 2. `./gradlew verify` の段

`build.gradle.kts` の `verifyStages` の並びです。この Intent で変わっていません（`build.gradle.kts` の変更は E2E のタスクだけ）。1つでも失敗したら、後ろの段は実行しません。CI も同じタスクを呼びます。

| 段 | タスク | 中身 |
|---|---|---|
| 0 | `verifyPrepare` | 道具の確認、make-you-chic-ui のビルド、2つのサブモジュールの不変、依存の取得 |
| 1 | `verifyFormat` | Spotless（ルートと backend）・Prettier |
| 2 | `verifyLint` | oxlint・ESLint・Stylelint |
| 3 | `verifyLicense` | 画面のライセンスヘッダー（Java・Kotlin DSL・メールのテンプレートは 1 の段の Spotless） |
| 4 | `verifyBuild` | Java のコンパイル（本体とテスト）、`tsc --noEmit`、Vite のビルド |
| 5 | `verifyUnitTest` | JUnit（ArchUnit の構造の検査と各機能の `*BoundaryArchitectureTest`、jqwik を含む）・Vitest（`eslint-config` の project を先に、`unit` を後に） |
| 6 | `verifyIntegrationTest` | 結合テスト（組み込みの H2 と対象DB の3種類のコンテナ。業務データの読み取り・書き込みの IT を含む） |
| 7 | `verifyCoverage` | JaCoCo の報告と検証、`@vitest/coverage-v8` |
| 8 | `verifySecurity` | `spotbugsGate`・`osvScan`・`gitleaksScan` |
| 9 | `verifyArtifact` | `bootWar`・`verifyDslSchemaInWar`・`frontendBundleSize` |

`team.md` の Deployment の実行順（フォーマット → リンタ → ライセンスヘッダー → ビルド → 単体テスト → 結合テスト → カバレッジ → セキュリティ）と一致します。

## 3. 依存の脆弱性の関門と、手元での流し直し

| 項目 | 扱い |
|---|---|
| 脆弱性の関門 | **`./gradlew verify` の `osvScan` だけ**（重大度 High 以上で失敗）。Dependabot alerts は使っていない（`team.md` の Way of Working） |
| CI（統合の後） | まっさらな環境で動くため `osvScan` は UP-TO-DATE にならず、**毎回** OSV のデータベースに照らして確かめる |
| 手元の `./gradlew verify` | lockfile が変わらなければ `osvScan` は UP-TO-DATE で飛ばされ、前回の結果のままになる。Build and Test の直しの後の verify でも UP-TO-DATE だった（`test-results.md` の 8.4） |
| 手元で確かめ直す手順 | `./gradlew osvScan --rerun`。結果は `build/reports/osv-scanner/osv.json` |
| 流し直す時機 | Dependabot の知らせを見送る依存（`.github/dependabot.yml` の `ignore`）があるため、統合の前の関門で verify の出力に `:osvScan UP-TO-DATE` が出たときと、リリースの前。CI の結果でも気づける |

この Intent では、B7 のレビューの直しの後に失敗の条件 0 件・警告 18 件を確かめています（`test-results.md` の 2.5）。Build and Test の直し（`22de65f`）の後の手元の verify では UP-TO-DATE のため、最新の OSV のデータベースでの確かめは `31d434c` の CI が受け持ちます（`ci-config.md` の 6.3）。

## 4. Build and Test が記録した検査と CI の段との対応

Build and Test のコマンドは `construction/build-and-test/build-instructions.md`（3〜5節）・`integration-test-instructions.md`・`security-test-instructions.md`・`performance-test-instructions.md` にあり、実測は `test-results.md` にあります。これらを CI の段に対応づけました。

| Build and Test のコマンド | 何を確かめたか | CI での実行 | 対応する CI の段 |
|---|---|---|---|
| `caffeinate -dims ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（colima の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡す） | 統合の前の関門の全体（0〜9 の段）。件数（単体 2,589・結合 1,389・画面 1,975）とカバレッジ（バックエンド 行 97.64%・分岐 91.23%、画面 行 95.06%・分岐 89.80%、パッケージごと）、時間 22分26秒 | **実行する** | ジョブ `verify` の「1コマンドの検査を実行する」（`./gradlew verify`）。CI はまっさらな環境のため、`clean` を付けなくてもテストは必ず走る。Docker はランナーのものを使う |
| 段ごと（`verifyFormat`・`verifyLint`・`verifyBuild`・`verifyUnitTest`・`verifyIntegrationTest`・`verifyCoverage`・`verifySecurity`・`verifyArtifact`） | 失敗した段だけの流し直し | **実行する**（`verify` の中で全段） | `verify` の 1〜9 の段 |
| `./gradlew vendorInstall vendorBuild vendorUnchanged`・`frontendInstall` | 依存を lockfile どおりに入れ、vendor が変わっていない | **実行する** | `verify` の 0 の段（`verifyPrepare`）と `actions/checkout` の `submodules: true` |
| `./gradlew :backend:integrationTest --tests '*BusinessDataReadIT'` ほか（3種類の対象DB） | 業務データの読み取り・書き込み・監査・漏えい・時間切れ・接続の数、AC3.4.5 の `readsLeaveNoAudit` | **実行する**（全件） | `verify` の 6 の段（`verifyIntegrationTest`） |
| 画面のテストの単独の流し直し（`eslintImportRules.test.ts` の5回、`--project eslint-config`） | 直したテストの単独の通過 | **実行する**（全件。`eslint-config` の project を先に流す） | `verify` の 5 の段（`frontendTest`）・7 の段（`frontendCoverage`） |
| `./gradlew osvScan --rerun` | 依存の脆弱性（失敗の条件 0 件・警告 18 件） | **実行する** | `verify` の 8 の段（`osvScan`）。CI は毎回走る（3節） |
| `spotbugsGate`・`gitleaksScan`（`verify` の 8 の段） | 静的解析（`SQL_` の残り 0 件）と秘密情報の検出（0 件） | **実行する** | `verify` の 8 の段。Gitleaks・OSV-Scanner は CI で版と SHA-256 を照合して入れる |
| `./gradlew :backend:bootWar`、`npm run build && npm run bundle:size` | WAR と初回の JavaScript の量（gzip 163.9 KB） | **実行する** | `verify` の 9 の段（`bootWar`・`frontendBundleSize`） |
| `grep -c -e axe-core -e axe.run -e 'Deque' -r frontend/dist` | `dist` に axe-core が入らない（0 件） | **実行しない**（`verify` の段に無い） | 手元の確かめ（B3・B7）。axe-core は devDependencies で、`frontendBundleSize` の対象の `dist` に入らないことを B7 で確かめた |
| `caffeinate -dims ./gradlew e2eTest`（Mailpit と見本の PostgreSQL を起動） | E2E 256 件、axe-core 4.14.0 の違反 0 件、報告の秘密 0 件、見本の PG の片付けの残り 0 | **実行しない（意図して外す）** | 5節 |
| `k6 inspect --include-system-env-vars`（業務データの 12 の場面）・`perf/dsl-mixed-memory.sh`（`dslMixed` の `memory.peak`） | 場面と閾値の名前（終了コード 0）、`memory.peak / memory.max` の既定の2回 0.8731・0.8696 | **実行しない（意図して外す）** | 5節 |
| `gh run list`・`gh run view`・`gh api …/attempts/<n>/jobs` | CI の試行ごとの結果（`test-results.md` の 6節） | — | この段と Build and Test の確かめ（`ci-config.md` の 6節） |

目標の判定表（`build-and-test-summary.md` の Target Verification Matrix）の中で統合の関門に当たるもの（`TC-01`〜`TC-05`・`TC-08`・`TC-11`・`TC-17`・`TC-18`。カバレッジ・3種類の対象DB・1コマンドの関門・脆弱性・秘密・`SQL_`）は、どれも CI が同じ `./gradlew verify` で実行します。CI の時間（`TC-07`・`U*-NFR7.*b`・`U3-NFR7.8c`）と CI の失敗の扱い（`TC-12`）の結果は `ci-config.md` の 6節にあります。

## 5. 意図して CI の外に置く検査と、代わりの実行の場

| 検査 | CI の外に置く理由 | 代わりの実行の場 | 持ち主 |
|---|---|---|---|
| **E2E と実際のブラウザの検査**（`./gradlew e2eTest`、180・185 を含む全件 256 件） | ブラウザー（Playwright の Chromium）と起動した WAR、手元の Mailpit と見本の PostgreSQL が要る。`team.md` の Testing Posture で、`verify` と CI の外に置くと決めている | 統合の前（`frontend/src` の本体か認証・権限・業務データの `src/main` に触れるブランチ。B2〜B7 は統合の前に全件を通した）、Intent の最後（Build and Test）、リリースの前に手元で実行（README の E2E の節）。B7 のレビューの直しの後に 256 件がすべて通過。Build and Test の直し（テスト・テストの設定・文書だけ）の後は流していない | 依頼者と AI |
| **axe-core の更新**（版と lockfile だけの変更でも） | 上と同じく E2E が要る | 統合の前に `./gradlew e2eTest` の全件と vitest-axe を流し、違反 0 件を件数で記録する（`.github/dependabot.yml` のコメント。`team.md` の Way of Working の例外） | 依頼者と AI |
| **負荷の試験**（k6、`perf/README.md`。業務データの読み取り・書き込みの場面） | 配備とは別の使い捨ての環境と、資源の上限をそろえた条件が要る（`project.md` の Testing Posture） | 手元の使い捨ての環境（`docker/perf/compose.yaml`）。この段までに行ったのは `k6 inspect` と試し走りだけで、判定は Build and Test から引き継いだ（Unverified 12 件） | Performance Validation |
| **`dslMixed` の `memory.peak`** | 同上 | Build and Test で使い捨ての環境で測った（`memory-peak-measurement.md`。既定の2回で Met） | Build and Test（済） |
| **画面の時間の本番での判定** | E2E の WAR は一時の内部DB で、配備したアプリと条件が違う | 185 の測りは Build and Test で Met（B6 の測り）。配備した環境での見え方は後の段 | Performance Validation・Observability Setup |
| **監視の確かめ**（`ms-forbidden` のしきい値、ダッシュボードの業務データの区画、既存の警報が書き込みも拾うこと） | 手元の監視（grafana/otel-lgtm）を起動して指標を流す必要がある | Build and Test から引き継いだ（Unverified 4 件） | Observability Setup |

## 6. 不安定なテストと CI の失敗の扱い

`team.md` の Testing Posture の「不安定なテストと CI の失敗」の決まりで扱います。CI は統合の後の再確認のため、失敗は統合を戻すのではなく、次の Bolt・次の段に進む前に扱います。

| 場合 | 扱い |
|---|---|
| 手元で再現した不安定なテスト | 原因を直すまで統合しない（次へ進まない） |
| 手元で再現しない CI の失敗 | 再現の試みに先に時間の上限を決め、見立てと試みの範囲を記録したうえで、CI の再実行（失敗したジョブの再実行）で通れば進めてよい（不安定と確かめられていない扱い） |
| 同じテストが二度目に落ちた | 原因を直すまで次へ進まない |
| 失敗を直さずに次の Intent へ持ち越す | 依頼者の決定があるときだけ。決まりとの差を記録する |
| 不安定なテストを直したとき | 二段で確かめる（疑った競り合い・待ちの抜けを確実に起こせたら、直す前は落ち・直した後は通ることを記録する） |
| 画面のテストの書き方 | 描画の後（`useEffect` などの効果・非同期の答え）に反映される値は `waitFor`・`findBy*` で待って確かめる。時間の上限は原因を確かめずに延ばさない |

**この Intent で当たった例**（詳しくは `ci-config.md` の 6.2、`build-and-test/test-results.md` の 4・5・8節）:

| テスト | 起きたこと | 扱い |
|---|---|---|
| `PasswordChangePage.test.tsx`「drops the answer when the screen is left while sending」 | CI の1回目で3回落ちた（`0427d45`・`05a9b37`・`1152202`） | `1152202` の時点で「同じテストが二度目」として B6 の統合を止め、`b7291d3` で直した（前の画面の後片付けを待つ形、二段の確かめ）。**`05a9b37` の1回目の失敗は記録に無く、実際の2度目はこの時点だった**（`ci-config.md` の 6.2 の記録の食い違い） |
| `H2CompactionByPoolSuspensionIT` | `1152202` の CI の2回目・3回目で2度落ちた | `b7291d3` で直した（補充が済むのを待つ形、二段の確かめ、待ちの上限は延ばしていない） |
| `PatternCheckerTest` | `ea23bf3` の CI の1回目で1度 | 手元で再現しないものとして記録し、再実行で通った。二度目は落ちていない |
| `eslintImportRules.test.ts` | 手元（B3 の試し・B7 の verify）で同じ形の時間切れが2度 | Build and Test の直し（`22de65f`）で、vitest の project を分けて先に1つで流す形にした。外から CPU の負荷を足して、直す前は落ち（13,350 ms）直した後は通る（848 ms）二段を記録。時間の上限は延ばしていない |
| E2E 185 のログイン・`logoutIntent.test.tsx` | 手元で1度ずつ | 「不安定と確かめられていない」扱いのまま。同じテストがもう一度落ちたら直すまで進まない |
| `registration.test.tsx`・`PreferencesPage.test.tsx`・`preferences/registration.test.tsx` | 人工の強い負荷の下でだけ落ちた | 依頼者の決定で記録だけ。CI や手元で落ちたら `team.md` の決まりで扱う |

## 7. 止めるもの・止めないものの整理

| 関門 | 統合を止める | 警告にとどめる |
|---|---|---|
| pre-commit（Gitleaks・フォーマット） | ○ | — |
| 0 準備（サブモジュール2つの不変を含む）／1〜4 | ○ | — |
| 5 単体テスト／6 結合テスト | ○（1件でも失敗したら。手元で対象DB のテストが SKIPPED なら統合しない） | 手元の SKIPPED は警告を出す（統合はしない） |
| 7 カバレッジの下限 | ○（全体と、パッケージごとに、行 80%・分岐 70%） | — |
| 8 Gitleaks | ○ | — |
| 8 SpotBugs | ○（priority 1、`SQL_`、`PREDICTABLE_RANDOM`、`SMTP_HEADER_INJECTION`） | それ以外の priority 2・3 |
| 8 OSV-Scanner | ○（High 以上。npm の開発用は道具と `MAL-` だけ） | npm の開発用（道具と `MAL-` を除く）、重大度が不明。今は警告 18 件 |
| 9 WAR の生成と JSON Schema の一致 | ○ | — |
| 9 初回の JavaScript の量 | × | 500KB を超えたら警告 |
| E2E（`./gradlew e2eTest`） | 手元で、対象のブランチの統合の前とリリースの前に実行し、失敗したら統合しない | —（`verify` と CI の外） |

## 8. CI（統合の後）に固有の関門

CI は `./gradlew verify` をそのまま呼びます。そのため、1〜7節の関門は、すべて CI でも効きます。CI に固有のものは次の4つで、前の Intent から変わっていません。

| 関門 | 合否の基準 | 実行するもの |
|---|---|---|
| サブモジュールの取得 | 2つのサブモジュールを、固定先のコミットで取得できなければ失敗（`submodules: true`） | `actions/checkout` |
| 道具の真正性 | Gitleaks・OSV-Scanner のダウンロードの SHA-256 が合わなければ失敗 | `ci.yml` の「Gitleaks と OSV-Scanner を入れる」 |
| 対象DB のテストを飛ばさない | Docker に届かなければ、対象DB のテストが失敗する（`CI=true`） | `ContainerRuntimeCheck` |
| 成果物の存在 | WAR が見つからなければ失敗（`if-no-files-found: error`） | `actions/upload-artifact` |

## Sources

- `.github/workflows/ci.yml`・`.github/dependabot.yml`・`build.gradle.kts`（`verifyStages` と E2E のタスク）・`backend/config/spotbugs-exclude.xml`・`frontend/vitest.config.ts`・`frontend/eslint.config.js`・`frontend/package.json`（読み取り）
- `git diff da29216 develop`、`git log --first-parent da29216..31d434c`（読み取り）
- `construction/build-and-test/build-instructions.md`・`integration-test-instructions.md`・`security-test-instructions.md`・`performance-test-instructions.md`・`test-results.md`・`build-and-test-summary.md`・`memory-peak-measurement.md`
- `construction/ci-pipeline/ci-pipeline-questions.md`（まとめの確認 Looks correct）・`ci-config.md`
- `gh run list`・`gh run view`・`gh api …/attempts/<n>/jobs`・`gh run view <id> --attempt 1 --log-failed`（読み取り）
- `aidlc/spaces/default/memory/team.md`（Way of Working・Testing Posture・Code Style・Deployment）・`project.md`（Deployment・Testing Posture・Change Control）
- `aidlc/spaces/default/intents/261004-role-menu/construction/ci-pipeline/quality-gates.md`（前の同じ段の記録、形の手本）

## Assumptions & Open Questions

- 1節のテストの件数は Build and Test の実測（`develop` の `22de65f` と同じ中身）です。CI の run の中の件数は、この段では数えていません（結果だけを確かめた）。
- 4節の `dist` に axe-core が入らない確かめは `verify` の段に無く、手元の確かめだけです。axe-core は devDependencies のため、今の形では成果物に入らない見込みです。
