# Requirements Analysis の質問（Intent 261107-verify-quick-local）

## 決まっていること（質問にしない）

依頼文・Reverse Engineering（`aidlc/spaces/default/codekb/mastersmith2/` の `architecture.md` の K-111・K-113・K-116、`code-quality-assessment.md` の K-112・K-114・K-115・K-117）・`team.md`・`project.md` で決まっている点です。

- 新しい入口 `verifyQuick` を足し、Bolt の統合の前はこれを流す。結合テスト（`:backend:integrationTest`、`*IT`）とバックエンドのカバレッジの下限の判定（`jacocoTestCoverageVerification`）は含めない。JaCoCo の値は参考として表示する。
- `-PitTests` で絞った結合テストだけを `verifyQuick` の中で流せるようにする。
- 全件の `verify` は、Build and Test・リリースの前・`packagesJudgedByTotal` のパッケージに手を入れる Bolt で流す。
- `-PitForks` で、手元だけ結合テストを並べて流せるようにする。`-P` を渡さないときは今までどおり並べない（CI は引数なしのため変わらない）。
- 画面のテスト（Vitest）は `verify`・`verifyQuick` の中で1回だけ流す。
- CI は今までどおり `./gradlew verify` を1本で流す（`.github/workflows/ci.yml` は変えない見込み）。
- 書き換える決まり: `project.md` の Mandated（統合の前の検査）、`team.md` の Way of Working と Testing Posture（K-117 の一覧）。
- コンテナの実行環境が無いときの扱い（手元は警告を出して対象DB のテストを飛ばす、飛ばした状態では統合しない、CI では必ず流す）は今の決まりのまま `verifyQuick` にも当てる。
- E2E（`./gradlew e2eTest`）を流す時点（Intent の最後とリリースの前）は変えない。

## Q1. [開発者] `verifyQuick` に残す段

`verify` は 0〜9 の 10 の段（準備・フォーマット・リンタ・ライセンス・ビルド・単体テスト・結合テスト・カバレッジ・セキュリティ・成果物）でできています。結合テストとバックエンドのカバレッジの判定を外すのは決まっています。ほかの段をどこまで残すかを決めます。

推奨: **A**。`project.md` の Mandated は統合の前に秘密情報の検出と依存の脆弱性の検査を求めており、外すと決まりを大きく書き換えることになります。成果物の段（WAR を作って中身を確かめる）は短く、配備に響く誤りをここで拾えます。

A. 結合テストとバックエンドのカバレッジの判定だけを外し、ほかの段（画面のカバレッジの下限の判定・セキュリティ・成果物を含む）はすべて残す
B. A に加えて、成果物の段（`bootWar`・WAR の中身の確かめ・バンドルの大きさ）も外す
C. A に加えて、セキュリティの段（SpotBugs・OSV-Scanner・Gitleaks・記録の JWT の検査）も外す
X. Other (please specify)

[Answer]: A（依頼者の補足: 「Aのを基本として修正。verifyは変えない。verifyQuickは基本的にverifyをベースとしてタスクを外す(skip)する形で組み立てる。verifyQuickでも単体テストだけでJaCoCoのカバレッジを参考値として算出する(基準を下回ってもNG判定はしない)。」）

## Q2. [開発者] 画面のテストを1回にするときの置き場所

いまは単体テストの段（5）で計測なしの1回、カバレッジの段（7）で計測つきの1回が流れます（K-112）。計測つきの1回（`frontendCoverage`。画面のカバレッジの下限の判定を含む）だけを残す形が自然です。残す場所を決めます。

推奨: **A**。結合テストは長いので、画面のテストの誤りを結合テストの前に知れます。`verify` と `verifyQuick` で同じ位置に置くため、段の順の決まりが重ならず循環しません（K-116）。

A. 単体テストの段（5）に置く（バックエンドの単体テストと並ぶ。誤りが結合テストの前に分かる）
B. カバレッジの段（7）に置く（今の `frontendCoverage` の位置のまま。カバレッジの判定が1つの段にまとまる）
X. Other (please specify)

[Answer]: B（Q6: A の答えから決まる。`verify` は変えず、`verifyQuick` は `frontendTest` を外して 7 の段の `frontendCoverage` だけを残す）

## Q3. [依頼者] 全件の `verify` を流す場面を足すか

依頼文で決まっている場面は、Build and Test・リリースの前・`packagesJudgedByTotal` のパッケージに手を入れる Bolt です。いまの `team.md` は Dependabot の知らせを取り込むときも「`./gradlew verify` を通してから統合する」と書いています。依存の更新（Spring Boot・Testcontainers・JDBC のドライバーなど）は結合テストでしか分からない壊れ方をすることがあります。

推奨: **A**。Dependabot の取り込みは Bolt の外の作業で回数も少なく、全件を流す手間が小さいためです。

A. 依頼文の場面に加えて、Dependabot の知らせ（依存の版と lockfile の更新）を取り込むときも全件の `verify` を流す
B. 依頼文の場面に加えて、Dependabot の取り込みと、ビルドの設定（`build.gradle.kts`・`settings.gradle.kts`）・DB の移行・Testcontainers の設定を変える Bolt でも全件の `verify` を流す
C. 依頼文の場面だけにする（Dependabot の取り込みも `verifyQuick` でよい）
X. Other (please specify)

[Answer]: A

## Q4. [依頼者] 時間の目標

速くなったかの判定の仕方を決めます。今の `verify` の時間はまだ測っていません。

推奨: **A**。今の時間が分からないうちに数値の目標を決めると、根拠の無い値になります。決まりを変えたかどうかの判断には、前後の実測の比べで足ります。

A. 数値の目標は置かない。Build and Test で、変える前の `verify`、変えた後の `verify`・`verifyQuick`（`-PitTests` なし）・`-PitForks` で並べた `verify` の時間を測って並べ、`verifyQuick` が変える前の `verify` より短いことを合格の条件にする
B. `verifyQuick` を変える前の `verify` の半分以下にする、という数値の目標を置く
X. Other (please specify)

[Answer]: A（Q1 の答えで `verify` は変えないため、比べるのは `verify`・`verifyQuick`（`-PitTests` なし）・`-PitForks` で並べた `verify` の時間）

## Q5. [依頼者] 手順の文書も直すか

決まり（`team.md`・`project.md`）のほかに、検査の手順を書いた文書があります。`docs/verification.md` の「1コマンドの検査」の節（段の表と「画面のテストが2回流れる」の説明）と、`README.md`（Docker・colima の前提の行など）です（K-117）。

推奨: **A**。画面のテストの回数や新しい口（`verifyQuick`・`-PitTests`・`-PitForks`）が文書に無いと、手順と実物が食い違います。

A. 決まりと一緒に、`docs/verification.md` と `README.md` の当たる箇所も直す
B. 決まりだけを直し、文書は後の Intent に回す
X. Other (please specify)

[Answer]: A

## Q6. [依頼者] 画面のテストを1回にするのは `verifyQuick` だけか

Q1 の答えで「`verify` は変えない」「`verifyQuick` は `verify` からタスクを外して組む」と決まりました。一方で、依頼文には「画面のテスト（Vitest）を1回にする」とあります。`verify` を変えないなら、`verify` では画面のテストが今までどおり2回流れます（5 の段の `frontendTest` と 7 の段の `frontendCoverage`）。この質問への答えで Q2（置き場所）も決まります。

推奨: **A**。「`verify` は変えない」のお考えに沿う形です。`verifyQuick` では計測なしの `frontendTest` を外し、画面のカバレッジの下限の判定を含む `frontendCoverage` を 7 の段に残せば、タスクを外すだけで組めます。CI と全件の `verify` の時間は今のままです。

A. `verify` は2回のまま変えない。`verifyQuick` だけ `frontendTest` を外し、`frontendCoverage`（7 の段、計測つき・下限の判定）の1回にする
B. `verify` でも `frontendTest` を外して1回にする（`verify` から1つのタスクを抜くだけの変更。CI と全件の `verify` も短くなる）
X. Other (please specify)

[Answer]: A

### Q1・Q2・Q6 の答えによる `verify` と `verifyQuick` の対照表（依頼者が答えの一部として載せることを指示）

| 段 | タスク | `verify`（変えない） | `verifyQuick`（`verify` から外す形） |
|---|---|---|---|
| 0 準備 | `checkToolchain`・`vendorInstall`・`vendorBuild`・`vendorUnchanged`・`mustacheVendorUnchanged`・`frontendInstall` | ○ | ○ |
| 1 フォーマット | `spotlessCheck`（ルート・`:backend`）・`frontendFormatCheck`・`perfToolFormatCheck` | ○ | ○ |
| 2 リンタ | `frontendLint`・`frontendLintCss`・`perfToolLint`・`perfToolEslint` | ○ | ○ |
| 3 ライセンス | `frontendLicenseCheck`・`frontendSvgCheck` | ○ | ○ |
| 4 ビルド | `compileJava`・`compileTestJava`・`frontendTypecheck`・`perfToolTypecheck`・`frontendBuild`・`frontendDistCheck` | ○ | ○ |
| 5 単体テスト | `:backend:test`（`*Test`） | ○ | ○ |
| 5 単体テスト | `frontendTest`（画面のテスト、計測なし） | ○ | 外す |
| 6 結合テスト | `:backend:integrationTest`（`*IT`） | ○ 全件（`-PitForks=N` で手元だけ並べられる） | 外す。`-PitTests=…` を付けたときだけ、当たる IT を流す |
| 7 カバレッジ | `jacocoTestReport` | ○（単体＋結合の記録） | ○ 参考値: 単体テストの記録（`test.exec`）だけで出す。`-PitTests` で流した IT の記録も、前の回の結合テストの記録も混ぜない |
| 7 カバレッジ | `jacocoTestCoverageVerification`（全体とパッケージごとの下限） | ○ | 外す（下限を下回っても失敗にしない） |
| 7 カバレッジ | `frontendCoverage`（画面のテスト、計測つき・下限の判定） | ○ | ○（画面のテストはこの1回だけ） |
| 8 セキュリティ | `spotbugsGate`・`osvScan`・`gitleaksScan`・`perfEvidenceScan` | ○ | ○ |
| 9 成果物 | `bootWar`・`verifyDslSchemaInWar`・`verifyFaviconInWar`・`frontendBundleSize` | ○ | ○ |

- `verifyQuick` で外すのは `frontendTest`・`integrationTest`・`jacocoTestCoverageVerification` の3つ。`-PitTests` を付けたときは `integrationTest` を絞って入れる。
- `verify` で変わるのは `-PitForks` の口が足されることだけ。付けなければ今までどおり並べず、CI の動きは変わらない。
- 段の順は `verify` の段の表をそのまま共有し、順の決まりを重ねない。
- 7 の段の参考値は単体テストの記録だけから出すため、IT でしか通らない層（`web` など）は低く出る。表示にその旨の注記を添える。
