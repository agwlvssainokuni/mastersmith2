# 品質の関門（quality-gates）

Intent `260925-user-management`（U1〜U8）の関門の一覧です。次のことを記録します。

- 合否の基準と、それを実行するタスク
- 証拠の出る場所
- **Build and Test が記録した検査と、CI の段との対応**（project.md の Deployment）

この文書は、既にある実装の記録です。新しい関門を足すものではありません。記録の対象は次のファイルです。
- `build.gradle.kts`・`backend/build.gradle.kts`
- `frontend/vitest.config.ts`
- `.pre-commit-config.yaml`
- `.github/workflows/ci.yml`・`.github/dependabot.yml`

前の Intent（`260923-dsl-schema-loader`）の `quality-gates.md` からの差分を中心に書きます。

## 0. 関門の3つの場所

| 場所 | 実行するもの | 統合を止めるか |
|---|---|---|
| コミットの直前 | pre-commit のフック（Gitleaks・Spotless・Prettier） | **止める**（コミットが失敗する） |
| **統合の前（手元）** | `./gradlew verify`（0〜9 の段）。colima が動いていること。画面・認証に関わる変更の統合の前とリリースの前は、Mailpit を起動して `./gradlew e2eTest` も流す | **止める。これが統合の関門**（team.md の Way of Working）。対象DB のテストが SKIPPED の状態では統合しない |
| 統合の後（CI） | GitHub Actions が同じ `./gradlew verify` を実行 | 統合は止めない（統合の後に動くため）。失敗したら次に進む前に直す |

## 1. 関門の基準と、この Intent で変わったもの

| 関門 | 合否の基準 | この Intent での変化 | 実行するタスク（段） |
|---|---|---|---|
| サブモジュールの不変 | 2つのサブモジュールの追跡されるファイルが変わっていれば失敗 | `vendor/java-mustache-processor` を足した（`mustacheVendorUnchanged`） | `verifyPrepare`（0） |
| フォーマット | 差分があれば失敗 | 変わらず | `verifyFormat`（1） |
| リンタ | error があれば失敗 | 変わらず | `verifyLint`（2） |
| ライセンスヘッダー | ヘッダーが無い・形が違えば失敗 | メールのテンプレート（Mustache のコメント `{{! ... }}`）を Spotless の対象に足した | `verifyLicense`（3）・`verifyFormat`（1） |
| ビルド | コンパイル・型・ビルドの誤りで失敗 | composite build の java-mustache-processor をビルドに含む | `verifyBuild`（4） |
| 全テスト | 1件でも失敗したら失敗。対象DB のテストは CI では飛ばさない | SubEtha SMTP でメールを受ける結合テスト（コンテナを使わない）を足した | `verifyUnitTest`（5）・`verifyIntegrationTest`（6） |
| カバレッジ | 行 **80%** 未満、または分岐 **70%** 未満で失敗。バックエンドは全体と、パッケージごと | 全体の合計で判定する既存のパッケージの一覧（`packagesJudgedByTotal`）を、22 から 12 に減らした。手を入れたパッケージを一覧から外し、パッケージごとの下限に戻した（team.md の Testing Posture） | `verifyCoverage`（7） |
| 秘密情報 | Gitleaks が1件でも検出したら失敗（履歴全体） | 変わらず | pre-commit と `gitleaksScan`（8） |
| Java の静的解析 | priority 1、パターン名が `SQL_` で始まるもの、`PREDICTABLE_RANDOM`、`SMTP_HEADER_INJECTION` の指摘が1件でもあれば失敗 | `PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION` を、priority によらず止める対象に足した（U1 の NFR2.8・NFR2.9） | `spotbugsGate`（8） |
| 依存の脆弱性 | 重大度 High 以上で失敗（npm の devDependencies は警告。成果物を作る道具と `MAL-` は止める） | java-mustache-processor の推移依存も `backend/gradle.lockfile` に載り、検査の対象になった（U1 の NFR8.3 の (b)） | `osvScan`（8） |
| 成果物 | WAR を作れない、WAR の中の JSON Schema が正本と違えば失敗。初回の JavaScript は 500KB を超えても警告だけ | 変わらず（WAR にメールのテンプレートが入る） | `verifyArtifact`（9） |

## 2. `./gradlew verify` の段

段の並び・失敗の条件・証拠の出る場所は、前の Intent の `quality-gates.md` の 2 節から変わっていません。この Intent で増えた中身は、1 節の「この Intent での変化」の列のとおりです。
- テストの JVM のヒープの上限は 1g です。
- テストの JVM・E2E の WAR・Dockerfile は、どれも `-Dh2.compactThreads=1` で動きます（project.md の Tech Stack）。

## 3. 依存の更新の知らせと、脆弱性の関門（この段の Q1〜Q3）

| 項目 | 扱い |
|---|---|
| Dependabot の版の更新（gradle・npm・github-actions・docker・docker-compose） | 週に1回。プルリクエストは GitHub の画面でマージしない。手元で版と lockfile をまとめて更新し、`./gradlew verify` を通してから統合し、プルリクエストは閉じる（team.md の Way of Working） |
| Dependabot の gradle の対象 | **`vendor/**` を外した**（`exclude-paths`、Q1: A）。サブモジュールの中のビルドの依存は、このリポジトリから更新できないため |
| Dependabot alerts（脆弱性の知らせ） | **無効のまま**（Q2: B）。脆弱性の関門は、`./gradlew verify` の `osvScan`（重大度 High 以上で失敗）だけとする |
| 開いたままのプルリクエスト 11 件 | 次の Intent でまとめて見直す（Q3: A） |

**team.md との差（Q2: B）**：team.md の Way of Working には「重大度 High 以上の知らせは、次の Bolt に入る前に取り込む」とあります。一方、この段の確かめで、Dependabot alerts が無効で、脆弱性の知らせが届いていないことが分かりました。依頼者の決定で、有効にはしません。代わりに、次の2つで High 以上の脆弱性を見つけます。
- 統合の前の `./gradlew verify` の `osvScan`
- 統合の後の CI の `osvScan`（まっさらな環境で毎回走る）

手元の `osvScan` は、lockfile が変わらなければ UP-TO-DATE で飛ばされます。手元で調べ直すときは `./gradlew osvScan --rerun` を使います（前の Intent の CI Pipeline の Q3）。

## 4. Build and Test が記録した検査と CI の段との対応

Build and Test のコマンドは `construction/build-and-test/test-results.md` の 2〜6 節にあり、目標の判定は `build-and-test-summary.md` の Target Verification Matrix にあります。これらを、CI の段に対応づけました。

| Build and Test のコマンド | 何を確かめたか | CI での実行 | 対応する CI の段 |
|---|---|---|---|
| `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` | 統合の前の関門の全体（0〜9 の段）、件数とカバレッジ | **実行する** | ジョブ `verify` の「1コマンドの検査を実行する」（`./gradlew verify`）。CI はまっさらな環境のため、テストは必ず走る |
| `./gradlew e2eTest`（Mailpit を起動） | E2E（010〜090、90 件）、実際のブラウザの検査、画面の時間、json の秘密の確かめ | **実行しない（意図して外す）** | 5 節 |
| `npx vitest run …DisplaySettingsProvider.test.tsx` の 50 回の繰り返し | U7 のときに1回落ちたテストの再現の試し | 実行しない | この段の外の一回だけの確かめ。同じテストは `verify` の 5 の段で毎回走る |
| `docker run … grafana/k6:2.3.0 inspect …`（24 場面） | k6 の台本が読み込めること | **実行しない（意図して外す）** | 5 節 |
| `gh run view` と `gh pr list` | CI の結果、Dependabot の状態 | — | この段の確かめ（`ci-config.md` の 6 節） |

目標の判定表の中で、統合の関門に当たるもの（フォーマット・リンタ・ライセンスヘッダー・ビルド・全テスト・カバレッジ・秘密情報・静的解析・依存の脆弱性・成果物）は、どれも CI が同じ `./gradlew verify` で実行します。U1-NFR8.3 の (d)（CI で verify が通る）は、`66fe981` で Not Met でした（`ci-config.md` の 6 節）。

## 5. 意図して CI の外に置く検査と、代わりの実行の場

| 検査 | CI の外に置く理由 | 代わりの実行の場 | 持ち主 |
|---|---|---|---|
| **E2E と実際のブラウザの検査**（`./gradlew e2eTest`、010〜090） | ブラウザー（Playwright の Chromium）と起動した WAR、手元の Mailpit が要る。team.md の Testing Posture で、`verify` と CI の外に置くと決めている | 画面・認証に関わる変更の統合の前と、リリースの前に手元で実行（README の E2E の節）。`66fe981` と作業フォルダの変更で 90 件すべて成功（3分25秒） | 依頼者と AI |
| **負荷の試験**（k6、`perf/README.md`） | 配備とは別の使い捨ての環境と、資源の上限をそろえた条件が要る（project.md の Testing Posture） | 手元の使い捨ての環境（`docker/perf/compose.yaml`） | performance-validation |
| **Dependabot のプルリクエストの取り込み** | 版と lockfile をまとめて更新し、手元で検査を通してから統合する決まり | 次の Intent | 依頼者と AI |

## 6. 止めるもの・止めないものの整理

| 関門 | 統合を止める | 警告にとどめる |
|---|---|---|
| pre-commit（Gitleaks・フォーマット） | ○ | — |
| 0 準備（サブモジュール2つの不変を含む）／1〜4 | ○ | — |
| 5 単体テスト／6 結合テスト | ○（1件でも失敗したら。手元で対象DB のテストが SKIPPED なら統合しない） | 手元の SKIPPED は警告を出す（統合はしない） |
| 7 カバレッジの下限 | ○（全体と、パッケージごとに、行 80%・分岐 70%） | — |
| 8 Gitleaks | ○ | — |
| 8 SpotBugs | ○（priority 1、`SQL_`、`PREDICTABLE_RANDOM`、`SMTP_HEADER_INJECTION`） | それ以外の priority 2・3 |
| 8 OSV-Scanner | ○（High 以上。npm の開発用は道具と `MAL-` だけ） | npm の開発用（道具と `MAL-` を除く）、重大度が不明 |
| 9 WAR の生成と JSON Schema の一致 | ○ | — |
| 9 初回の JavaScript の量 | × | 500KB を超えたら警告 |
| E2E（`./gradlew e2eTest`） | 手元で統合の前とリリースの前に実行し、失敗したら統合しない | —（`verify` と CI の外） |

## 7. CI（統合の後）に固有の関門

CI は `./gradlew verify` をそのまま呼びます。そのため、1〜6 節の関門は、すべて CI でも効きます。CI に固有のものは次の4つです。

| 関門 | 合否の基準 | 実行するもの |
|---|---|---|
| サブモジュールの取得 | 2つのサブモジュールを、固定先のコミットで取得できなければ失敗（`submodules: true`） | `actions/checkout` |
| 道具の真正性 | Gitleaks・OSV-Scanner のダウンロードの SHA-256 が合わなければ失敗 | `ci.yml` の「Gitleaks と OSV-Scanner を入れる」 |
| 対象DB のテストを飛ばさない | Docker に届かなければ、対象DB のテストが失敗する（`CI=true`） | `ContainerRuntimeCheck` |
| 成果物の存在 | WAR が見つからなければ失敗（`if-no-files-found: error`） | `actions/upload-artifact` |

**既知の失敗**：`66fe981` の CI は、2回とも別々のテストの時間切れで失敗しています。
- `H2CompactionByPoolSuspensionIT` の接続の待ち 10 秒
- `InvitationAdminPage.test.tsx` の1件の既定の 5 秒

依頼者の決定で、直すのは次の Intent です（`build-and-test/test-results.md` の 8.1 節）。それまで、CI の結果は統合の後の再確認として役に立ちません。team.md の「CI が失敗したら次に進む前に直す」との差として記録します。

## Sources

- `.github/workflows/ci.yml`・`.github/dependabot.yml`・`build.gradle.kts`・`backend/build.gradle.kts`・`frontend/vitest.config.ts`
- 各単位の `code-summary.md`（`construction/u1-mail/` 〜 `construction/u8-instance-appearance/` の `code-generation/`）
- `construction/build-and-test/build-and-test-summary.md`・`test-results.md`
- `construction/ci-pipeline/ci-pipeline-questions.md`（Q1〜Q3）
- `aidlc/spaces/default/memory/team.md`（Way of Working・Testing Posture・Code Style・Deployment）・`project.md`（Deployment・Tech Stack）
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/construction/ci-pipeline/quality-gates.md`（前の Intent の記録）

## Assumptions & Open Questions

- `exclude-paths` の効き目（Dependabot の gradle の実行が成功になること）は、依頼者のプッシュの後の次の実行で確かめます。
