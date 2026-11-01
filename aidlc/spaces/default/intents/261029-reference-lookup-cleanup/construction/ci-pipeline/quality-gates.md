# 品質の関門（quality-gates）

Intent `261029-reference-lookup-cleanup`（U1〜U5、Bolt B1〜B7、不安定なテストの直し `a3099f80`、Build and Test の直し `df79634c`）の関門の一覧です。次のことを記録します。

- 合否の基準と、それを実行するタスク
- **Build and Test が記録した検査と、CI の段との対応**（`project.md` の Deployment）
- 意図して CI の外に置く検査と、代わりの実行の場
- 不安定なテストと CI の失敗の扱いと、この Intent で当たった例
- Build and Test の N/A の行 **BT-CI-MERGE**（統合の前の CI の義務）の扱いと、org の既定との差

この文書は、既にある実装の記録です。新しい関門を足すものではありません（`ci-pipeline-questions.md`）。前の同じ段の記録（Intent `261016-business-data-views` の `quality-gates.md`）からの差を中心に書きます。

## 0. 関門の3つの場所と、BT-CI-MERGE の扱い

| 場所 | 実行するもの | 統合を止めるか |
|---|---|---|
| コミットの直前 | pre-commit のフック（Gitleaks・Spotless・Prettier） | **止める**（コミットが失敗する） |
| **統合の前（手元）** | `./gradlew verify`（0〜9 の段。CI と同じタスク）。colima が動いていること。`frontend/src` の本体か、認証・権限・業務データの `src/main` に触れるブランチは、Mailpit と見本の PostgreSQL を起動して `./gradlew e2eTest` も流す | **止める。これが統合の関門**（`team.md` の Way of Working）。対象DB のテストが SKIPPED の状態では統合しない |
| 統合の後（CI） | GitHub Actions が同じ `./gradlew verify` を実行 | 統合は止めない（統合の後に動くため）。失敗したら、次に進む前に「不安定なテストと CI の失敗」の決まりで扱う（6節） |

### 0.1 BT-CI-MERGE（統合の前に CI を流す義務）と org の既定との差

| 項目 | 内容 |
|---|---|
| 求め | 各単位の Testing Contract の applicable_notes（org の Testing Posture: `classic` などのスコープは「CI execution before merge」を足す）。Build and Test の Target Verification Matrix で BT-CI-MERGE として N/A・持ち主 ci-pipeline とされた |
| この段の扱い | **今の形のまま受け持つ**（依頼者の決定、`ci-pipeline-questions.md`）。新しい仕組みは作らない |
| 今の形 | 統合の前に、CI と同じタスク `./gradlew verify` を手元で1コマンドで流し、すべて通ってから `develop` に統合する。これが統合の前の関門で、CI は統合の後に同じタスクで再確認する。手元と CI の実行の中身は同じ（`build.gradle.kts` の `verifyStages`、CI は `./gradlew verify` を呼ぶだけ）。CI に固有の関門は 8節の4つだけ |
| org の既定との差 | org の既定は「統合の前に CI を流す」（プルリクエストの上の CI など）。このチームはプルリクエストを使わず（`team.md` の Way of Working）、CI は `develop` への push で動くため、**CI の結果は統合の前にはそろわない**。CI と同じ検査を統合の前に手元で流すことで、統合の前の検査の中身は満たすが、「まっさらな CI の環境で」流すことは統合の後になる |
| 差を埋める手当て | (1) CI が失敗したら、次の Bolt・次の段に進む前に `team.md` の決まりで扱う（6節）。(2) `main` への取り込みの前に、前の取り込みの後のすべてのコミットの CI を試行ごとに確かめる（`project.md` の Deployment の学び）。(3) 手元の verify で `osvScan` が UP-TO-DATE になる差は、CI の毎回の実行で補う（3節） |
| この Intent での結果 | すべての統合（B1〜B7・`a3099f80`・`df79634c`）は、統合の前に手元の verify を通し、統合の後の CI は `a75baec4` の1回目（6節）を除いて1回目で success（`ci-config.md` 6節）。その後の記録だけの push `e1e028d1` の CI は1回目が画面のテストの未処理の例外で failure、2回目は確認中（6節）。統合の後の CI で初めて出た失敗で、統合の前の手元の verify では起きていない、という形の差がここに表れる。**追記（結果）**: 2回目も同じ誤りで failure。差を埋める手当て (1) のとおり次へ進まず、依頼者の決定で直し（`cac6fa54`・`cb23466b`、`ci-config.md` 6.4）、統合の前に手元の verify と E2E 277 件を通し、`cb23466b` の CI（run 41270200479）の1回目で success を確かめた |

## 1. 関門の基準と、この Intent で変わったもの

| 関門 | 合否の基準 | この Intent での変化 | 実行するタスク（段） |
|---|---|---|---|
| サブモジュールの不変 | 2つのサブモジュールの追跡されるファイルが変わっていれば失敗 | 変わらず。make-you-chic-ui の固定先を `ecb07e9` から `4c63d11` に上げた（専用のコミット `476785a4`） | `verifyPrepare`（0） |
| 依存の取得 | lockfile どおりに入れられなければ失敗 | 変わらず（`frontend/.npmrc` の `ignore-scripts=true` は残る）。依存の入れ直し（`41b8bf51`）。`hasInstallScript` の付いた項目は fsevents の1つのまま | `verifyPrepare`（0） |
| フォーマット | 差分があれば失敗 | **`perfToolFormatCheck` を足した**（`perf/redact-k6-evidence.mjs` に画面と同じ設定の Prettier。`13883f6c`） | `verifyFormat`（1） |
| リンタ | error があれば失敗 | **`perfToolLint` を足した**（同じ道具に画面と同じ設定の oxlint。`13883f6c`）。道具に ESLint と型の検査は当たらない（U4 R-03、既知の制約） | `verifyLint`（2） |
| ライセンスヘッダー | ヘッダーが無い・形が違えば失敗 | **対象に `perf/` の `.mjs`・`.js` を加えた**（`check-license-header.mjs`、`13883f6c`） | `verifyLicense`（3）・`verifyFormat`（1） |
| ビルド | コンパイル・型・ビルドの誤りで失敗 | 変わらず | `verifyBuild`（4） |
| 全テスト | 1件でも失敗したら失敗。対象DB のテストは CI では飛ばさない | テストが増えた（単体 **2,807**・結合 **1,654**・画面 **238 ファイル・2,312** 件。Build and Test の実測）。U4 の道具のテスト（3ファイル・115 件、性質ベースのテストを含む）が画面のテストの中で流れる。設定（`vitest.config.ts`）は変えていない | `verifyUnitTest`（5）・`verifyIntegrationTest`（6） |
| カバレッジ | 行 **80%** 未満、または分岐 **70%** 未満で失敗。バックエンドは全体と、パッケージごと | 変わらず。`packagesJudgedByTotal` は **7** のまま（7 パッケージの `src/main` の差 0）。計測の除外は増やしていない。U4 の道具は画面のカバレッジの対象の外（行 94.2%・分岐 90.61% だが、下がっても気づけない。U4 R-02、既知の制約） | `verifyCoverage`（7） |
| 画面のテストの未処理の例外【この段の直しで追記】 | テストが全件通っても、Vitest が未処理の例外を1件でも数えたら失敗（`dangerouslyIgnoreUnhandledErrors` は使わない。前からの扱い） | `frontend/vitest.setup.ts` に、各テストの `cleanup()` の後に残った `window.setTimeout`・`setInterval` の予約を取り消す後片付けを足した（`cac6fa54`、jsdom のときだけ）。時間の上限・除外は変えていない。make-you-chic-ui の固定先を `cab4207` に上げた（`cb23466b`、Tooltip の後片付け） | `verifyUnitTest`（5）・`verifyCoverage`（7） |
| 秘密情報 | Gitleaks が1件でも検出したら失敗（履歴全体） | 変わらず（`.gitleaks.toml` の差なし） | pre-commit と `gitleaksScan`（8） |
| **記録の JWT の形** | `aidlc/` の下のすべての `evidence/` の通常のファイルに JWT の形などが1件でもあれば失敗（決めたバイナリの拡張子以外で NUL を含むものも失敗）。出力には件数・パス・位置だけを出し、値は出さない | **新しい関門**（`perfEvidenceScan`、`13883f6c`）。入力・出力を宣言せず毎回流れる。H1（架空の JWT の形を一時に置いた1回）で終了コード 4・Gradle の失敗・出力に値 0 件を確かめた（`test-results.md` 13節） | `perfEvidenceScan`（8） |
| Java の静的解析 | priority 1、パターン名が `SQL_` で始まるもの、`PREDICTABLE_RANDOM`、`SMTP_HEADER_INJECTION` の指摘が1件でもあれば失敗 | 変わらず。`backend/config/spotbugs-exclude.xml` の差 0、19 個の `*ArchitectureTest.java` の差 0（参照の SQL は既存の `RowSqlExecutor`・`RowDialect` に足し、除外と境界テストを緩めていない。`team.md` の Code Style） | `spotbugsGate`（8） |
| 依存の脆弱性 | 重大度 High 以上で失敗（npm の devDependencies は警告。成果物を作る道具と `MAL-` は止める） | 変わらず。手元の扱いは 3節 | `osvScan`（8） |
| 成果物 | WAR を作れない、WAR の中の JSON Schema が正本と違えば失敗。初回の JavaScript は 500KB を超えても警告だけ | 変わらず | `verifyArtifact`（9） |

## 2. `./gradlew verify` の段

`build.gradle.kts` の `verifyStages` の並びです。段の並びと数は変わらず、1・2・8 の段にタスクが1つずつ加わりました（太字）。1つでも失敗したら、後ろの段は実行しません。CI も同じタスクを呼びます。

| 段 | タスク | 中身 |
|---|---|---|
| 0 | `verifyPrepare` | 道具の確認、make-you-chic-ui のビルド、2つのサブモジュールの不変、依存の取得 |
| 1 | `verifyFormat` | Spotless（ルートと backend）・Prettier・**`perfToolFormatCheck`** |
| 2 | `verifyLint` | oxlint・ESLint・Stylelint・**`perfToolLint`** |
| 3 | `verifyLicense` | 画面と **`perf/` の `.mjs`・`.js`** のライセンスヘッダー（Java・Kotlin DSL・メールのテンプレートは 1 の段の Spotless） |
| 4 | `verifyBuild` | Java のコンパイル（本体とテスト）、`tsc --noEmit`、Vite のビルド |
| 5 | `verifyUnitTest` | JUnit（ArchUnit の構造の検査と各機能の `*BoundaryArchitectureTest`、jqwik を含む）・Vitest（U4 の道具のテストを含む） |
| 6 | `verifyIntegrationTest` | 結合テスト（組み込みの H2 と対象DB の3種類のコンテナ。参照の読み取り・書き込みの確かめの IT を含む） |
| 7 | `verifyCoverage` | JaCoCo の報告と検証、`@vitest/coverage-v8` |
| 8 | `verifySecurity` | `spotbugsGate`・`osvScan`・`gitleaksScan`・**`perfEvidenceScan`** |
| 9 | `verifyArtifact` | `bootWar`・`verifyDslSchemaInWar`・`frontendBundleSize` |

`team.md` の Deployment の実行順（フォーマット → リンタ → ライセンスヘッダー → ビルド → 単体テスト → 結合テスト → カバレッジ → セキュリティ）と一致します。

## 3. 依存の脆弱性の関門と、手元での流し直し

| 項目 | 扱い |
|---|---|
| 脆弱性の関門 | **`./gradlew verify` の `osvScan` だけ**（重大度 High 以上で失敗）。Dependabot alerts は使っていない（`team.md` の Way of Working） |
| CI（統合の後） | まっさらな環境で動くため `osvScan` は UP-TO-DATE にならず、**毎回** OSV のデータベースに照らして確かめる。記録だけのコミットの CI も、新しい公表で落ちうる（`project.md` の学び） |
| 手元の `./gradlew verify` | lockfile が変わらなければ `osvScan` は UP-TO-DATE で飛ばされ、前回の結果のままになる |
| 手元で確かめ直す手順 | `./gradlew osvScan --rerun`。結果は `build/reports/osv-scanner/osv.json` |
| 流し直す時機 | 統合の前の関門で verify の出力に `:osvScan UP-TO-DATE` が出たときと、リリースの前。CI の結果でも気づける |

この Intent の最後の手元の verify（fix ブランチ）で `osvScan` は通過しています（`test-results.md` 2.1）。最新の OSV のデータベースでの確かめは、`df79634c` の CI（1回目で success）が受け持ちます。`e1e028d1` の CI は1回目が `osvScan` より前の `:frontendTest` で止まったため、2回目（確認中、`ci-config.md` 6.3）の結果で確かめます。**追記**: run 41264314230 は2回とも `osvScan` より前の段で止まったため、最新の確かめは `cb23466b` の CI（run 41270200479、1回目で success）が受け持ちました。

## 4. Build and Test が記録した検査と CI の段との対応

Build and Test のコマンドは `construction/build-and-test/build-instructions.md`（3〜5節）・`integration-test-instructions.md`・`security-test-instructions.md`・`performance-test-instructions.md` にあり、実測は `test-results.md` にあります。これらを CI の段に対応づけました。

| Build and Test のコマンド | 何を確かめたか | CI での実行 | 対応する CI の段 |
|---|---|---|---|
| `caffeinate -dims ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（colima の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡す） | 統合の前の関門の全体（0〜9 の段）。件数（単体 2,807・結合 1,654・画面 2,312）とカバレッジ（バックエンド 行 97.6%・分岐 91.3%、画面 行 95.21%・分岐 90.37%、パッケージごと）、時間 26分4秒 | **実行する** | ジョブ `verify` の「1コマンドの検査を実行する」（`./gradlew verify`）。CI はまっさらな環境のため、`clean` を付けなくてもテストは必ず走る。Docker はランナーのものを使う |
| 段ごと（`verifyFormat`〜`verifyArtifact`） | 失敗した段だけの流し直し | **実行する**（`verify` の中で全段） | `verify` の 1〜9 の段 |
| `perfToolFormatCheck`・`perfToolLint`・ライセンスヘッダーの検査（`perf/`） | U4 の道具の書式・リンタ・ヘッダー | **実行する** | `verify` の 1・2・3 の段 |
| `./gradlew perfEvidenceScan` | `evidence/` に JWT の形が残っていない（BT-SEC-GATES、U4-NFR8.68） | **実行する**（毎回） | `verify` の 8 の段 |
| H1: 架空の JWT の形を `evidence/` に一時に置いた1回の `./gradlew perfEvidenceScan`（`security-test-instructions.md` 3節） | 関門が実際に落ち、出力に値が出ない（U4-NFR8.64） | **実行しない（意図して外す）**。わざと失敗させる1回きりの確かめで、置いたファイルは消した | 手元（Build and Test、2026-11-01T02:12:07Z） |
| `gitleaks dir`（`evidence/` に対して、H4） | 記録に秘密が無い（U4-NFR8.69） | 一部を実行する: CI の `gitleaksScan` は履歴全体を `gitleaks git` で調べ、コミットされた `evidence/` も対象になる。`gitleaks dir` の形そのものは CI に無い | `verify` の 8 の段（`gitleaksScan`） |
| `./gradlew :backend:integrationTest`（参照の IT、3種類の対象DB） | 参照の読み取り・書き込みの確かめ・認可・漏えい・時間切れ・接続の数（`team.md` の (a)〜(k)） | **実行する**（全件） | `verify` の 6 の段 |
| `./gradlew osvScan --rerun` | 依存の脆弱性 | **実行する** | `verify` の 8 の段（`osvScan`）。CI は毎回走る（3節） |
| `spotbugsGate`・`gitleaksScan` | 静的解析と秘密情報の検出。除外と境界テストの差 0 の確かめ（`git diff --stat`） | 検査は **実行する**。差 0 の確かめは手元の読み取り | `verify` の 8 の段。Gitleaks・OSV-Scanner は CI で版と SHA-256 を照合して入れる |
| `./gradlew :backend:bootWar`、WAR の中の文言の鍵の検索（`unzip -p`） | WAR が組める、消した鍵 `tables.reference.loading` が 0 件 | WAR は **実行する**。鍵の検索は **実行しない**（手元の確かめ） | `verify` の 9 の段 |
| `caffeinate -dims ./gradlew e2eTest`（Mailpit と見本の PostgreSQL を起動） | E2E 277 件（190・195 を含む）、axe の違反 0、報告の json に資格情報 0 | **実行しない（意図して外す）** | 5節 |
| `k6 inspect --include-system-env-vars`（参照の6場面）・試し走り・種の確かめ | 場面と閾値の名前、試し走り | **実行しない（意図して外す）** | 5節 |
| `gh run list`・`gh run view --attempt <n>` | CI の試行ごとの結果（`test-results.md` 7節） | — | この段と Build and Test の確かめ（`ci-config.md` 6節） |

目標の判定表（`build-and-test-summary.md` の Target Verification Matrix）の中で統合の関門に当たるもの（BT-SEC-GATES・カバレッジ・3種類の対象DB・U4 の NFR8.68 など）は、どれも CI が同じ `./gradlew verify` で実行します。CI の時間（BT-CI-TIME・U1-NFR5.3・U2-NFR5.23・U3-NFR2.45）と CI の試行の記録（BT-CI-ATTEMPTS・BT-FLAKY-FIX）の結果は `ci-config.md` 6節にあります。BT-CI-MERGE は 0.1 のとおりです。

## 5. 意図して CI の外に置く検査と、代わりの実行の場

| 検査 | CI の外に置く理由 | 代わりの実行の場 | 持ち主 |
|---|---|---|---|
| **E2E と実際のブラウザの検査**（`./gradlew e2eTest`、190・195 を含む全件 277 件） | ブラウザー（Playwright の Chromium）と起動した WAR、手元の Mailpit と見本の PostgreSQL が要る。`team.md` の Testing Posture で、`verify` と CI の外に置くと決めている | 統合の前（`frontend/src` の本体か認証・権限・業務データの `src/main` に触れるブランチ）、Intent の最後（Build and Test。2回目で 277 passed、14.0 分。1回目は colima の VM の中の `apt upgrade` で Docker のデーモンが起動し直した環境の止まりで、コードの失敗ではない）、リリースの前に手元で実行 | 依頼者と AI |
| **H1（関門をわざと落とす確かめ）** | 記録にわざと JWT の形を置いて落とすため、毎回の検査にはできない | Build and Test で1回（2026-11-01T02:12:07Z）。道具を変えたときに `security-test-instructions.md` 3節の形で流し直す | Build and Test（済） |
| **負荷の試験**（k6、`perf/README.md`。参照の6場面ほか） | 配備とは別の使い捨ての環境と、資源の上限をそろえた条件が要る（`project.md` の Testing Posture） | 手元の使い捨ての環境（`docker/perf/compose.yaml`）。Build and Test は種・`k6 inspect`（参照の6場面）・試し走りまで。78 場面の inspect は流しておらず（BT-K6-INSPECT、承認の場で6場面＋閾値の台本の照合で足りるとして Approve）、3種類の判定の回・`memory.peak` は後の段 | Performance Validation |
| **監視の確かめ**（パネル 46・47、警報 `ms-biz-target-db` の firing） | 手元の監視（grafana/otel-lgtm）を起動して指標を流す必要がある | Build and Test から引き継いだ | Observability Setup |

## 6. 不安定なテストと CI の失敗の扱い

`team.md` の Testing Posture の「不安定なテストと CI の失敗」の決まりで扱います。CI は統合の後の再確認のため、失敗は統合を戻すのではなく、次の Bolt・次の段に進む前に扱います。

| 場合 | 扱い |
|---|---|
| 手元で再現した不安定なテスト | 原因を直すまで統合しない（次へ進まない） |
| 手元で再現しない CI の失敗 | 再現の試みに先に時間の上限を決め、見立てと試みの範囲を記録したうえで、CI の再実行で通れば進めてよい（不安定と確かめられていない扱い） |
| 同じテストが二度目に落ちた | 原因を直すまで次へ進まない |
| 失敗を直さずに次の Intent へ持ち越す | 依頼者の決定があるときだけ。決まりとの差を記録する |
| 不安定なテストを直したとき | 二段で確かめる（疑った競り合い・待ちの抜けを確実に起こせたら、直す前は落ち・直した後は通ることを記録する） |
| 画面のテストの書き方 | 描画の後に反映される値は `waitFor`・`findBy*` で待って確かめる。時間の上限は原因を確かめずに延ばさない |

**この Intent で当たった例**（詳しくは `ci-config.md` 6.2、`build-and-test/test-results.md` 7・8節）:

| テスト・run | 起きたこと | 扱い |
|---|---|---|
| `ReferenceSameResponseIT`（`a75baec4`、run 41161783903 の1回目） | 応答の本文に目印 "abc" が無いことを確かめる形で、乱数の16進のトレースID に偶然に当たって落ちた（1回の実行で約 0.73%）。2回目で success | 原因が分かったため `a3099f80` で直した（テストだけ、目印を16進に無い文字を含む値に）。二段の確かめ（直す前は落ち、直した後は通る。1,000,000 件で新しい目印の当たり 0）。以後の CI はすべて1回目で success。同じテストの二度目の失敗は無い |
| `cf887ad8`（run 41025421073 の1回目） | cancelled（job 0 件） | テストの失敗ではない。2回目で success |
| `BusinessDataListPage.write.test.tsx` の実行中の未処理の例外（`e1e028d1`、run 41264314230 の1回目） | 画面のテスト 2,312 件はすべて passed だが、`ReferenceError: window is not defined`（make-you-chic-ui の `Tooltip` の 300 ms の遅らせた表示の時計が、jsdom の片付けの後に鳴った見立て）を Vitest が未処理の例外として数え、`:frontendTest` が失敗。`df79634c..e1e028d1` は `aidlc/` の下だけの変更で、同じテストのコードの `df79634c` の CI は success | **扱いは未決**。今回が1度目。2回目は確認中。手元での再現の試み（時間の上限と範囲）と、再実行で通ったときに「不安定と確かめられていない」扱いで進めるかを依頼者に確かめる。同じ形でもう一度落ちたら、原因（テストの終わりに時計を片付ける・待つ形など）を直すまで次へ進まない。**追記（結果）**: 2回目（`:frontendCoverage`）も同じ誤りで落ちたため、直すまで進めないと判断した。30 分の再現の試み（依頼者の決定 9594abae）では再現せず、仕組みはソースで確かめた（`flaky-repro-record.md`）。依頼者の決定（e4538e84）B4＋A で、`cac6fa54`（`vitest.setup.ts` の片付け）と上流の直しの取り込み `cb23466b`（固定先 `cab4207`）を入れた。二段の確かめ: D1 で直す前は3回とも `ReferenceError`（4 errors）、直した後は3回とも 0。D2 で片付けの後に鳴った予約が直す前 4・直した後 0。`cb23466b` の CI は1回目で success |
| E2E の1回目（Build and Test） | 1 failed・26 件未実行（Mailpit・見本の PG・配備したアプリが止まった） | 環境の止まり（colima の VM の中の `apt upgrade` で Docker のデーモンが起動し直した）として扱い、不安定なテストとしては扱わない。依頼者の決定で起動し直して全件を流し直し、277 件が通った |

## 7. 止めるもの・止めないものの整理

| 関門 | 統合を止める | 警告にとどめる |
|---|---|---|
| pre-commit（Gitleaks・フォーマット） | ○ | — |
| 0 準備（サブモジュール2つの不変を含む）／1〜4（perf の道具の書式・リンタ・ヘッダーを含む） | ○ | — |
| 5 単体テスト／6 結合テスト | ○（1件でも失敗したら。手元で対象DB のテストが SKIPPED なら統合しない） | 手元の SKIPPED は警告を出す（統合はしない） |
| 7 カバレッジの下限 | ○（全体と、パッケージごとに、行 80%・分岐 70%） | — |
| 8 Gitleaks | ○ | — |
| 8 `perfEvidenceScan` | ○（1件でも） | — |
| 8 SpotBugs | ○（priority 1、`SQL_`、`PREDICTABLE_RANDOM`、`SMTP_HEADER_INJECTION`） | それ以外の priority 2・3 |
| 8 OSV-Scanner | ○（High 以上。npm の開発用は道具と `MAL-` だけ） | npm の開発用（道具と `MAL-` を除く）、重大度が不明 |
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

- `.github/workflows/ci.yml`・`.github/dependabot.yml`・`build.gradle.kts`（`verifyStages`・`perfToolFormatCheck`・`perfToolLint`・`perfEvidenceScan`）・`frontend/scripts/check-license-header.mjs`（読み取り）
- `git diff --stat d797ef74..HEAD -- .github`、`git show --stat 13883f6c`（読み取り）
- `construction/build-and-test/build-instructions.md`・`security-test-instructions.md`・`performance-test-instructions.md`・`integration-test-instructions.md`・`test-results.md`（2〜8節・11〜13節）・`build-and-test-summary.md`（4・5節、Target Verification Matrix）
- `construction/ci-pipeline/ci-pipeline-questions.md`・`ci-config.md`
- `gh run view --attempt <n>`・`--log-failed`（読み取り）
- 各単位の `code-generation/code-summary.md`（特に `u4-perf-evidence-redactor`）
- `aidlc/spaces/default/memory/org.md`（Testing Posture）・`team.md`（Way of Working・Testing Posture・Code Style・Deployment）・`project.md`（Way of Working・Deployment・Testing Posture）
- `aidlc/spaces/default/intents/261016-business-data-views/construction/ci-pipeline/quality-gates.md`（前の同じ段の記録、形の手本）

## Assumptions & Open Questions

- 0.1 の BT-CI-MERGE は、依頼者の決定で今の形のまま受け持ち、org の既定との差を記録しました。org の既定に合わせる（プルリクエストの上の CI など）変更は、`team.md` の決まりを変えることになるため、この段では行いません。
- `e1e028d1` の CI の1回目の未処理の例外（6節）の扱いと、2回目の結果を依頼者に確かめます。
  - **追記（結果）**: 2回目も failure。直して（`cac6fa54`・`cb23466b`）`cb23466b` の CI の1回目で success。閉じた。
- U4 の道具のカバレッジが下限の外にあること（U4 R-02）と、道具に ESLint と型の検査が当たらないこと（U4 R-03）は、Build and Test で既知の制約として記録済みで、関門を足すかは後の Intent の候補です。
