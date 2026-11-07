# 要件定義（Intent 261107-verify-quick-local）

## 意図の分析

- **したいこと**: Bolt の統合の前に流す検査の時間を縮め、1回の統合の待ちを減らす。そのうえで、結合テストとバックエンドのカバレッジの下限の判定は、全件の検査を流す決めた場面と CI で必ず通す。
- **種類**: 検査の道具（Gradle のタスク）と開発の決まり・手順の文書の変更。アプリのソース（`src/main`）と WAR・イメージの中身は変えない。
- **範囲**: ルートの `build.gradle.kts`、`backend/build.gradle.kts`、決まり（`aidlc/spaces/default/memory/project.md`・`team.md`）、手順の文書（`docs/verification.md`・`README.md`）。CI（`.github/workflows/ci.yml`）は変えない。
- **今の形**（Reverse Engineering で確かめた事実）: 入口の `./gradlew verify` は 0〜9 の 10 の段を順の決まり（`mustRunAfter`）でつなぐ（`aidlc/spaces/default/codekb/mastersmith2/architecture.md` の K-111）。結合テスト（`*IT` 278 クラス）は1つの JVM で順に流れ、並列の設定も絞る口も無い（K-113）。画面のテスト（Vitest）は計測なしと計測つきの2回流れる（`code-quality-assessment.md` の K-112）。バックエンドのカバレッジの判定は結合テストの実行記録に依る（K-114）。CI は `./gradlew verify` を引数なしで1本流す（K-115）。業務の概要は `business-overview.md`、ファイルの置き場は `code-structure.md` による。

## 機能要件

### FR1 統合の前の入口 `verifyQuick`

- **FR1.1** `./gradlew verifyQuick` という入口を足す。`verify` の段の表（`build.gradle.kts` の `verifyStages`）をもとに、次の3つのタスクを外して組み立てる: `frontendTest`（画面のテスト、計測なし）、`:backend:integrationTest`（結合テスト）、`:backend:jacocoTestCoverageVerification`（バックエンドのカバレッジの下限の判定）。
  - 合否: `./gradlew verifyQuick --dry-run` の出力に3つのタスクが現れず、下の対照表で ○ のタスクはすべて現れる。
- **FR1.2** 残すタスクとその段の順は `verify` と同じにする。段の表と順の決まりは `verify` と共有し、`verifyQuick` 用に別の順を作らない（順の決まりが重なって循環しないように。K-116）。
  - 合否: `verifyQuick --dry-run` に現れるタスクの順が、`verify --dry-run` の同じタスクの順と一致する。
- **FR1.3** `verifyQuick` の中の画面のテストは `frontendCoverage`（計測つき・画面のカバレッジの下限の判定を含む）の1回だけにする。画面のカバレッジの下限は今までどおり判定し、下回れば失敗する。
- **FR1.4** 1つのタスクが失敗したら後ろの段を流さず、全体を失敗とする（`verify` と同じ）。

`verify` と `verifyQuick` の対照表（依頼者が確かめた表。`requirements-analysis-questions.md` の Q6 の下と同じ）:

| 段 | タスク | `verify`（変えない） | `verifyQuick` |
|---|---|---|---|
| 0 準備 | `checkToolchain`・`vendorInstall`・`vendorBuild`・`vendorUnchanged`・`mustacheVendorUnchanged`・`frontendInstall` | ○ | ○ |
| 1 フォーマット | `spotlessCheck`（ルート・`:backend`）・`frontendFormatCheck`・`perfToolFormatCheck` | ○ | ○ |
| 2 リンタ | `frontendLint`・`frontendLintCss`・`perfToolLint`・`perfToolEslint` | ○ | ○ |
| 3 ライセンス | `frontendLicenseCheck`・`frontendSvgCheck` | ○ | ○ |
| 4 ビルド | `compileJava`・`compileTestJava`・`frontendTypecheck`・`perfToolTypecheck`・`frontendBuild`・`frontendDistCheck` | ○ | ○ |
| 5 単体テスト | `:backend:test`（`*Test`） | ○ | ○ |
| 5 単体テスト | `frontendTest`（計測なし） | ○ | 外す |
| 6 結合テスト | `:backend:integrationTest`（`*IT`） | ○ 全件（`-PitForks=N` で手元だけ並べられる） | 外す。`-PitTests=…` を付けたときだけ当たる IT を流す（FR2） |
| 7 カバレッジ | `jacocoTestReport` | ○（単体＋結合の記録） | ○ 参考値（FR3） |
| 7 カバレッジ | `jacocoTestCoverageVerification` | ○ | 外す |
| 7 カバレッジ | `frontendCoverage`（計測つき・下限の判定） | ○ | ○ |
| 8 セキュリティ | `spotbugsGate`・`osvScan`・`gitleaksScan`・`perfEvidenceScan` | ○ | ○ |
| 9 成果物 | `bootWar`・`verifyDslSchemaInWar`・`verifyFaviconInWar`・`frontendBundleSize` | ○ | ○ |

### FR2 `-PitTests` で絞った結合テスト

- **FR2.1** `./gradlew verifyQuick -PitTests=<パターン>` のとき、`:backend:integrationTest` を 6 の段の位置に入れ、パターンに当たる結合テストのクラスだけを流す。パターンはテストのクラス名の形（Gradle のテストの絞り込みと同じワイルドカード）で、カンマで区切って複数を指定できる。
  - 合否: `-PitTests=AuditTraceIdIT` で流すと、`AuditTraceIdIT` だけが流れる。カンマで2つ指定すると2つが流れる。
- **FR2.2** パターンに当たる結合テストが1つも無いときは、黙って通さず失敗にする（打ち間違いで何も流れないまま通らないように）。
- **FR2.3** `-PitTests` を `verify` に付けても絞らない（`verify` は全件のまま）か、付けたことを知らせて失敗にするかのどちらかとし、`verify` が絞った結合テストで通ることは無い。
- **FR2.4** コンテナの実行環境が無いときの扱い（手元は警告を出して対象DB のテストを飛ばす、`CI=true` なら失敗）は今の `ContainerRuntimeCheck` のまま変えない。

### FR3 `verifyQuick` の JaCoCo の参考値

- **FR3.1** `verifyQuick` のバックエンドのカバレッジの報告は、単体テストの実行記録（`test.exec`）だけから作る。`-PitTests` で流した結合テストの記録も、前の回の `verify` が残した `integrationTest.exec` も混ぜない。
  - 合否: 前の回の `integrationTest.exec` が `build/jacoco` に残った状態で `verifyQuick` を流しても、報告の値が単体テストだけの値と一致する。
- **FR3.2** 報告の後に、全体の行カバレッジと分岐カバレッジの値を、「単体テストだけの参考値で、下限の判定はしない」という注記と報告の置き場とともに表示する。下限（行 80%・分岐 70%）を下回っても失敗にしない。
- **FR3.3** `verify` のカバレッジの報告と判定（単体＋結合の記録、全体とパッケージごとの下限）は変えない。

### FR4 `verify` と `-PitForks`

- **FR4.1** `verify` に含まれるタスクとその順は変えない（画面のテストの2回を含む）。
  - 合否: 変更の前と後で `./gradlew verify --dry-run` のタスクの一覧が一致する。
- **FR4.2** `-PitForks=<N>` を付けると、`:backend:integrationTest` を N 個の JVM に分けて並べて流す。付けないときは今までどおり1つの JVM で流す。N が 1 以上の整数でないときは、理由を示して失敗にする。`verify` にも、`-PitTests` を付けた `verifyQuick` にも効く。
- **FR4.3** CI（`.github/workflows/ci.yml`）は変えず、引数なしの `./gradlew verify` を流し続ける。

### FR5 決まりの書き換え

`aidlc/spaces/default/memory/` の決まりのうち、「統合の前に全件の `verify`」を前提にした文（`code-quality-assessment.md` の K-117 の一覧）を書き換える。

- **FR5.1** `project.md` の Mandated の「ALWAYS 統合前に、フォーマット・リンタ・ビルド・全テスト・秘密情報の検出・依存関係の脆弱性検査（重大度 High 以上）が通っていることを確認する」を、統合の前は `verifyQuick`（結合テストとバックエンドのカバレッジの判定を除く）で確かめ、全件（全テストとカバレッジの判定）は FR5.2 の場面と CI で確かめる形に書き換える。
- **FR5.2** `team.md` の Way of Working の、統合の前の検査の行・E2E の行・Dependabot の行・コンテナの実行環境の行を書き換え、次の場面では全件の `verify` を流すことを書く: Build and Test、リリースの前（`develop` から `main` への取り込みの前）、`packagesJudgedByTotal` のパッケージに手を入れる Bolt、Dependabot の知らせ（依存の版と lockfile の更新）の取り込み。それ以外の Bolt の統合の前は `verifyQuick` とし、変えた所に関わる結合テストを `-PitTests` で流してよいことを書く。
- **FR5.3** `team.md` の Testing Posture の、対象DB の結合テストを「`./gradlew verify` の中で毎回実行する」行、`packagesJudgedByTotal` の実測の行、`verify` と CI の時間を測って記録する行を、FR5.2 の場面に合わせて書き換える。
- **FR5.4** `project.md` の Way of Working（1コマンドの検査の入口）と Testing Posture（`cleanTest`・`cleanIntegrationTest` を付けた実測、colima の環境変数）の行を、`verify` と `verifyQuick` の使い分けに合わせて書き換える。
- **FR5.5** 書き換えの前と後の文を、Code Generation の記録に並べて残す。

### FR6 手順の文書

- **FR6.1** `docs/verification.md` の「1コマンドの検査」の節に、`verifyQuick` の中身（FR1 の対照表）、`-PitTests`・`-PitForks` の使い方、JaCoCo の参考値の読み方、全件の `verify` を流す場面を書き足す。`verify` で画面のテストが2回流れる説明はそのまま正しい。
- **FR6.2** `README.md` の検査に関わる行（Docker・colima の前提、対象DB の結合テストを毎回流す説明など）を、`verify` と `verifyQuick` の使い分けに合わせて直す。

## 非機能要件

- **NFR1 時間**: 数値の目標は置かない。Build and Test で、同じ PC・同じ条件（`caffeinate -dims` で包み、colima の環境変数を渡す）で、`verify`、`verifyQuick`（`-PitTests` なし）、`-PitForks` で並べた `verify` の時間を測って並べて記録する。`verifyQuick` が `verify` より短いことを合格の条件とする。`-PitForks` の N の値は Build and Test で試して決め、記録する。
- **NFR2 全件の検査と CI を緩めない**: `verify` の合格の条件（カバレッジの下限・パッケージごとの下限・`packagesJudgedByTotal`・セキュリティの基準）を変えない。CI の検査の内容と時間の物差しは変わらない。
- **NFR3 既存のテスト**: 変更の後、引数なしの `./gradlew verify`（`:backend:cleanTest :backend:cleanIntegrationTest` を付けて流す）が通る。`-PitForks` で並べた `verify` も通る（並べて落ちるテストがあれば、決まりの「不安定なテスト」の扱いに従う）。
- **NFR4 確かめ方（Test Strategy: Minimal）**: FR1・FR2・FR4.1 の合否はタスクの一覧（`--dry-run`）と実際の実行で確かめ、FR3.1 は前の回の `integrationTest.exec` を残した状態で確かめる。結果は Build and Test に記録する。
- **NFR5 ライセンスと書き方**: 変えるファイルの決まり（Apache License 2.0 のヘッダー、日本語のコメント、Spotless の書式）を守る。新しい依存は足さない。

## 制約

- `verify` は変えない（依頼者の決定、Q1）。`verifyQuick` は `verify` からタスクを外す形で組む。
- CI は `verify` を1本で流す（依頼文）。
- 決まりの書き換えは Code Generation の変更として行い、コミットは依頼者の承認を得る（`project.md` の Change Control）。
- `vendor/` の中は変えない。
- コンテナの実行環境が無いときに対象DB のテストを飛ばした状態では統合しない、という決まりは変えない。

## 前提

- 結合テストを並べたとき（`-PitForks`）に、対象DB のコンテナの数・テストの JVM のヒープ（1g × N）・`ServerSocket(0)` の番号の取り合い（`TargetDbStartupIT`・`MailTestApplication`）で失敗が出うる（K-116、未検証の見立て）。N の値と、失敗が出たときの扱いは Build and Test で実測して決める。
- `verifyQuick` の JaCoCo の参考値は単体テストだけから出すため、結合テストでしか通らない層（`web` など）は低く出る。この値は判定に使わない。
- 統合の前に `verifyQuick` を流しても、結合テストでしか分からない誤りは CI（統合の後の再確認）か次の全件の `verify` で見つかる。そのときは `team.md` の「CI の失敗」の扱いに従う。
- `-PitTests` のパターンの形（Gradle のテストの絞り込みのワイルドカード、カンマ区切り）と、当たるものが無いときに失敗にすること（FR2.2）は、依頼文の「`-PitTests` で絞った IT」から置いた前提で、Code Generation の計画で確かめる。
- `team.md` の「テストを足した Bolt の後に `verify` と CI の時間を測る」行は、Bolt の後は `verifyQuick` の時間、Build and Test では `verify` と CI の時間を測る形に書き換える想定（FR5.3）。文は Code Generation の計画で示して確かめる。

## 範囲の外

- CI の変更（並べる・分ける）。
- 結合テストの中身の変更（並べて落ちるテストの直しは、Build and Test で見つかったときに扱いを決める）。
- E2E（`./gradlew e2eTest`）の流し方。
- アプリのソースと WAR・イメージの中身。
- カバレッジの下限の値と `packagesJudgedByTotal` の一覧。

## 残っている問い

- `-PitForks` の推奨の N の値（Build and Test で実測して決める）。
- `-PitForks` で並べて失敗するテストが出たときに、テストを直すか、並べる数を下げるか（出たときに依頼者に諮る）。

## Sources

- 依頼文: `aidlc/spaces/default/intents/261107-verify-quick-local/project-description.json`
- 質問と答え: `aidlc/spaces/default/intents/261107-verify-quick-local/inception/requirements-analysis/requirements-analysis-questions.md`（Q1〜Q6 と対照表）
- Reverse Engineering: `aidlc/spaces/default/codekb/mastersmith2/business-overview.md`・`architecture.md`（K-111・K-113・K-116、図 8）・`code-structure.md`・`code-quality-assessment.md`（K-112・K-114・K-115・K-117）
- 決まり: `aidlc/spaces/default/memory/org.md`・`team.md`・`project.md`・`phases/inception.md`
- 読んだ知識の置き場（`team.md` の Knowledge Pointers）: `aidlc/spaces/default/knowledge/aidlc-shared/` の `checklist-auth-audit.md`・`checklist-business-data.md`・`checklist-dsl.md`・`checklist-mail.md`・`checklist-nav-menu.md`・`checklist-reference.md`・`checklist-roles.md`・`checklist-timezone.md`・`ops-notes.md`。今回は検査の道具の変更で、機能のチェックリストに当たる項目は無い。`ops-notes.md` の長いテストの扱いは NFR1 の測り方に反映した。
