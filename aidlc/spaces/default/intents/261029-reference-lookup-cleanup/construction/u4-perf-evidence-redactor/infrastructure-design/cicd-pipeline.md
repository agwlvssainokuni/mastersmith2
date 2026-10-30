# CI/CD の組み込み — U4 u4-perf-evidence-redactor

- 単位: U4 u4-perf-evidence-redactor（種類 `library`）。部品 PerfEvidenceRedactor（`perf/` に置く Node の 1 ファイルの道具。ADR-004）
- この段の成果物は、この文書と `traceability.json` だけ（`library` の単位のため、`infrastructure-specification.md` と `monitoring-design.md` は作らない）。
- 道具はアプリ（WAR）に含まれず、配備されない。新しい基盤（コンテナ・ボリューム・`.env` の項目・監視）も足さない。この文書は、道具とそのテストを、統合の前の関門 `./gradlew verify` と CI にどう組み込むかだけを決める。
- 質問は無い（`infrastructure-design-questions.md`。決まっていることだけ）。名前に「仮」と付けたもの（タスク名 `perfEvidenceScan`・道具のファイル名・検査の口の旗 `--scan`）はコード生成で決める。
- この文書は設計で、Gradle の組み込みの実装はコード生成で行う。コードの断片は考え方を示す短いものに限る。

## 1. 今の形（読み取りで確かめた事実）

| 項目 | 今の形 | 出どころ |
|---|---|---|
| 入口 | ローカルも CI も `./gradlew verify` の 1 つのタスク。10 の段（`verifyPrepare` 〜 `verifyArtifact`）を `verifyStages` の表で並べ、各段の一員に前の段のすべての一員への `mustRunAfter` を付ける | ルートの `build.gradle.kts`（`verifyStages`・`stageTasks`・`verify`） |
| 安全の検査の段 | `verifySecurity`（8 番目の段）の一員は `spotbugsGate`・`osvScan`・`gitleaksScan` | 同上 |
| 単体テストの段 | `verifyUnitTest`（5 番目）の一員は backend の `test` と `frontendTest`（`npm run test` = `vitest run`） | 同上・`frontend/package.json` |
| カバレッジの段 | `verifyCoverage`（7 番目）の `frontendCoverage`（`npm run test:coverage` = `vitest run --coverage`）。Vitest のテストはこの段でもう一度流れる | 同上 |
| Node の版 | `checkToolchain` が Node 24・npm・git を確かめる | 同上 |
| CI | `.github/workflows/ci.yml` の `verify` の job は、`develop` への push・`v*` のタグ・手での起動で動き、Node 24・JDK 25・Gitleaks・OSV-Scanner を入れて `./gradlew verify` を 1 回呼ぶだけ。`timeout-minutes: 60` | `.github/workflows/ci.yml` |
| Vitest の対象 | `unit` の `include` は `src/**/*.test.{ts,tsx}` と `e2e/support/**/*.test.ts`。`perf/` は入っていない。`coverage.include` は `src/**/*.{ts,tsx}` だけ | `frontend/vitest.config.ts` |
| 今の evidence | `aidlc/` の下に 2 つ（Intent 261025-deps-perf-carryover と 261027-perf-targets-review の Build and Test）。中身は開いていない | ディレクトリの一覧だけ |

## 2. 組み込む形

### 2.1 段と関門の対応（stage → gate）

| verify の段 | 足す・使うもの | 何を止めるか | 失敗の条件 | CI |
|---|---|---|---|---|
| 0 `verifyPrepare` | 既存の `checkToolchain` を使う（足さない） | Node 24 が無いまま道具を動かすこと | 既存のとおり | 同じ |
| 1〜4（フォーマット・リンタ・ライセンスヘッダー・ビルド） | 道具のファイルとテストのファイルを各検査の対象に入れる方法は、コード生成の計画の義務（`logical-components.md` 4節 P6・P7）。前提: 計画で各検査（特にライセンスヘッダーの検査）の対象に入れること。入るまでは、これらの段が道具を守るとは言えない | 書式・リンタ・ヘッダー・型の誤り | 既存のとおり | 同じ |
| 5 `verifyUnitTest` | 前提: コード生成の計画で、テストの置き場を `vitest.config.ts` の `unit` の `include` に当たる道に選び（`include` を足す場合を含む）、fast-check を解決できる形にすること（P1〜P4）。この前提が満たされたときにだけ、道具の仕組みのテスト（性質のテストと例のテスト）が `frontendTest` の中で流れる。今の `include` に `perf/` は入っておらず、テストが一件も流れなくても verify は成功するため、この段では「流れる」とは言わない。新しいタスクは足さない | （前提が満たされたとき）伏せ字・残存の検査・evidence の検査の仕組みの退行 | テストの失敗（`vitest run` の非 0） | 同じ |
| 7 `verifyCoverage` | 5 と同じ前提のもとで、`frontendCoverage` が同じテストをもう一度流す。下限（行 80%・分岐 70%）と除外は変えない。道具の `.mjs` を `coverage.include` に入れるかはコード生成の計画で書く（P5） | 同上と、入れた場合の下限 | 既存のとおり | 同じ |
| 8 `verifySecurity` | 新しいタスク `perfEvidenceScan`（仮）を一員に足す | 作業フォルダの `aidlc/` の下の `evidence/` に JWT の形・飛ばしてはいけない NUL 混じり・上限超え・読めないファイルが残ること | 道具の終わりのコード 1・2・4（すべて非 0） | 同じ |
| 9 `verifyArtifact` | 変えない（道具は WAR に入らない） | — | — | 同じ |

### 2.2 `perfEvidenceScan`（仮）のタスク

```text
// 考え方（Gradle の Kotlin DSL。実装はコード生成）
val perfEvidenceScan = tasks.register<Exec>("perfEvidenceScan") {
    description = "aidlc/ の下の evidence/ に JWT の形などが残っていないかを調べる（見つかれば失敗）。"
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    dependsOn(checkToolchain)
    commandLine("node", "perf/<道具>.mjs", "--scan", "aidlc")
}
// verifyStages の verifySecurity の一覧: spotbugsGate, osvScan, gitleaksScan, perfEvidenceScan
```

- 依存: `checkToolchain` だけ。`frontendInstall`・`vendorBuild` には依存しない（道具は Node の組み込みだけを読む。NFR9.61）。単独で `./gradlew perfEvidenceScan` としても流せる。
- 毎回流す: 入力と出力を宣言せず、`UP-TO-DATE` で飛ばさない（出力を宣言しない `Exec` は元々 `UP-TO-DATE` にならないため、`outputs.upToDateWhen { false }` は付けない）。git の対象外・まだコミットしていないファイルも、作業フォルダの上のものを毎回調べるため（L-c）。
- 失敗の扱い: `Exec` の既定（`isIgnoreExitValue` を付けない）で、終わりのコードが 0 以外ならタスクが失敗する。検査の口が返すのは 0・1・2・4 だけで（3・5 は使わない。`security-design.md` 5.4節）、1（根が無い・引数の誤り）・2（入力の誤り）・4（残存の検出）のどれでも verify が失敗する。`osvScan` のように終わりのコードを Gradle の側で読み替えることはしない。
- `build.gradle.kts` で変える所は 2 か所: (1) `verifyStages` の `verifySecurity` の一員の一覧（今の `spotbugsGate`・`osvScan`・`gitleaksScan`）に `perfEvidenceScan` を足す。(2) `verifySecurity` の段の説明の文言（「8 安全の検査（SpotBugs＋FindSecBugs、OSV-Scanner、Gitleaks。…）」）に、evidence の検査を足す。コード生成の計画には、この 2 か所の両方を変えることを書く。

### 2.3 既存の安全の検査との並び

- 段と段の並びは既存の仕組みのまま: `perfEvidenceScan` は `verifyStages` の一員になるため、前の段（0〜7）のすべての一員の後に流れ、`verifyArtifact` より前に流れる。
- 段の中（`spotbugsGate`・`osvScan`・`gitleaksScan` との間）の並びは決めない（既存の 3 つの間にも並びは無い）。どれかが失敗すれば、Gradle の既定（`--continue` なし）で、後ろの段（`verifyArtifact`）は流れない。
- `gitleaksScan`（L-f、コミット済みの履歴を見る）と `perfEvidenceScan`（L-c、作業フォルダの `evidence/` を見る）は見る所が違い、置き換え合わない。`gitleaks dir`（L-d、置いた後の手順）は verify に入れない（`security-design.md` 9節。13節の未決の点のまま）。
- 時間: 今の `evidence/` は 97 ファイルの小さなテキストで、道具は 1 回なめるだけのため、verify と CI の時間にほとんど足さない見込み。64 MiB に近い入力での `--scan` の所要時間はコード生成で 1 度測って記録する（`security-design.md` 2.1節）。CI の時間の線（約 42 分）の確かめは、既存のとおり Build and Test で行う。

### 2.4 CI（`.github/workflows/ci.yml`）

- 変えない。CI の `verify` の job は `./gradlew verify` を 1 回呼ぶだけで、段の中身は `build.gradle.kts` が決めるため、`perfEvidenceScan` と道具のテストは CI でも同じ順と同じ条件で流れる。
- 道具に要るもの（Node 24）は CI の `actions/setup-node`（`node-version: "24"`）で既に入る。新しい Actions・新しい道具の取り込み・秘密情報は要らない（CI は秘密情報を使わないまま）。
- `actions/checkout` は `fetch-depth: 0` で作業フォルダ全体を取り出すため、コミットされた `evidence/` は CI の検査でも対象になる（CI の作業フォルダにはコミット済みのファイルしか無い）。

### 2.5 手元と CI の違い

| 観点 | 手元（`./gradlew verify`） | CI |
|---|---|---|
| 調べる `evidence/` | 作業フォルダの上のすべて（git の対象外・まだコミットしていないものを含む） | コミット済みのものだけ |
| コンテナの実行環境（colima） | `perfEvidenceScan` と道具のテストは要らない。コンテナが無いときに飛ばす対象DB のテストの扱い（team.md の Way of Working）は変えない | 同じ（道具の側は要らない） |
| 外の道具 | Node 24（`checkToolchain`） | 同じ（`setup-node`） |
| 終わりのコードと失敗 | 同じ | 同じ |

- 手元で通って CI で落ちる差は、作業フォルダの側が広いため、原則として起きない（手元の検査が CI の検査を含む）。逆に手元で落ちて CI で通ることは起きうる（コミットしていない記録だけに残っているとき）。その場合も統合の前に手元で直す（verify は統合の前の関門）。

## 3. 失敗したときの見立て（値を表示しない）

- 表示は道具の報告の部品（LC5）だけを通る。出るのは、決めた文言・件数・位置（行と桁、またはバイトの位置）・パス（`redactJwt` を通した後のもの）だけで、値とその断片は出ない（NFR8.64）。Gradle の `Exec` は道具の標準出力・標準エラーをそのまま流すため、手元のログと CI のログにも値は出ない見込み。ただしこの段では、Gradle の失敗の要約（`Process 'command node' finished with non-zero exit value …` など）を含めて実際のログに値が出ないことまでは確かめておらず、根拠は報告の部品のテスト（NFR8.64）による。実際の出力での確かめは Build and Test の項目に加える（7節）。
- 見立ての手順:

| 終わりのコード | 表示に出るもの | 見立てと直し方 |
|---|---|---|
| 4（残存の検出） | パス・件数・位置（1 ファイルにつき先頭の 10 件まで） | そのファイルを道具の JSON の口・テキストの口で置き直す（記録はコミットしない）。除外の口は設けない。過去の（コミット済みの）記録で起きたら、自分の判断で置き直さず依頼者に諮る（`security-design.md` 8.6節） |
| 2（入力の誤り） | パスと理由（NUL 混じり・上限超えと大きさ・読めない誤りの `code`） | 一覧に無い拡張子の NUL 混じりは、置いたものを確かめる。バイナリの一覧に足すときは理由を書いて依頼者の承認を得る（8.3節） |
| 1（呼び出しの誤り） | 決めた文言だけ | 根の `aidlc` が無い・タスクの引数の誤り。`build.gradle.kts` の組み込みを見直す |
| 0 | 調べた数・バイナリとして読んだ数・リンクの数とそのパス | — |

- 位置を確かめるために中身を見る必要があるときも、`cat`・`grep` で行を表示せず、道具の検査をもう一度流して件数と位置で確かめる（project.md の「個人に関する値・秘密を表示しない」の扱いと同じ）。
- 道具のテストの失敗（`verifyUnitTest`・`verifyCoverage`）は、性質のテストの種と道（`counterexamplePath`）と回数だけで再現する（NFR5.61。反例の値は出さない）。CI でだけ落ちたときは team.md の「不安定なテストと CI の失敗」の決まりで扱う。

## 4. 戻し方

- 道具には配備した状態が無く、アプリ・内部DB・`.env`・コンテナに触れないため、戻しは「組み込みのコミットを戻す」ことだけで済む。
- 第一の手: `perfEvidenceScan` を `verifySecurity` の一覧から外す（またはタスクの登録ごと戻す）コミットを作る。`.github/workflows/ci.yml` は変えていないため、戻しも要らない。
- 第二の手: 道具とテストのファイルを含む U4 の統合のコミットを戻す。U5 の README の節が道具を指していれば、同じ戻しで README の手順も戻す（契約 C7 の使う側）。
- どちらの戻しでも、前の守り（L-f の `gitleaksScan` と、project.md の「記録に置く前に `[REDACTED-JWT]` に置き換え、`eyJ…` の形の検索と `gitleaks dir` で確かめる」手順）はそのまま残る。戻すと L-c（作業フォルダの `evidence/` の検査）が無くなることを、戻しの記録に書く。
- 戻すきっかけの例: 誤検知で verify が止まり、置き直しでは直せないとき。その場合も除外の口は足さず、戻すかどうかを依頼者に諮る。

## 5. 導入の順

1. コード生成で道具とテストを作り、テストが `frontendTest` で実際に流れる（passed 1 件以上・skipped と todo 0 件）ことを確かめる（P1〜P4）。
2. 組み込む前に、検査の口を `aidlc` に単独で 1 回流し、件数（調べた数・バイナリ・リンク・検出 0・入力の誤り 0）を記録する。1 件でも検出か NUL 混じりがあれば止めて依頼者に諮る（`security-design.md` 8.6節）。
3. `perfEvidenceScan` を `verifySecurity` に足し、`./gradlew verify` を通してから統合する。
4. 統合の後の CI で `perfEvidenceScan` が流れて成功したことを、Build and Test で試行ごとに記録する（project.md の CI の記録の決まり）。

## 6. 上流との差

- 無い。`logical-components.md` 3節の組み込み（`verifySecurity` の一員、`checkToolchain` だけに依存、毎回流す、CI は同じ `verify`）をそのまま具体にした。
- 足した決め: 終わりのコードを Gradle の側で読み替えない（`osvScan` と違い `isIgnoreExitValue` を付けない）、`verifySecurity` の段の説明の文言に evidence の検査を足す、手元と CI で調べる範囲の差（2.5節）を明記した。どれも上流の決まりを狭める・緩めるものではない。

## 7. 後の段に回すこと

| 段 | 回すこと |
|---|---|
| コード生成（計画） | タスク名・道具のファイル名・旗の名前の確定。`logical-components.md` 4節 P1〜P7（テストの置き場を `include` に当たる道に選ぶこと、`vitest.config.ts` の `include`・`coverage.include`、ライセンスヘッダーなど各検査の対象への入れ方、型の手当て）。これが 2.1節の前提で、traceability.json で Deferred にした NFR5.61・5.62・5.63・8.62・8.63・8.65・8.67・9.65 の持ち主。`build.gradle.kts` の `verifySecurity` の一員の一覧と段の説明の文言の両方の変更（2.2節） |
| コード生成 | 5節の 2 の一度の単独の実行と件数の記録、64 MiB に近い入力での `--scan` の所要時間の記録、組み込んだ後の `./gradlew verify` の通過。レビューでテストのソースを検索し、完成した JWT の形が無いことを確かめる（NFR8.71） |
| Build and Test | `perfEvidenceScan` が手元の verify と CI で流れて成功したこと（CI は試行ごと）、U4 のテストのファイルごとの passed の件数（skipped・todo 0）、verify と CI の時間（42 分の線）、テストのソースに完成した JWT の形が無いことの確かめ（NFR8.71）。あわせて、わざと残存を作った 1 回の verify: 作業フォルダの `aidlc/` の下の `evidence/` に、架空の JWT の形のテスト用の値（本物のトークンは使わない）を入れたファイルを一時に置き、`./gradlew verify`（または `perfEvidenceScan`）が終わりのコード 4 で失敗すること、Gradle の失敗の要約を含む出力に値とその断片が出ていないことを確かめる。このファイルはコミットせず、確かめた後に消し、消したことと `git status` に残っていないことを記録する |
| 未決のまま（承認の場） | `gitleaks dir` を verify に入れるか、バイナリの先頭のバイトを確かめるか（`security-design.md` 13節）。この文書はどちらも入れない形で書いた |

## 8. 承認の場の決定

- 承認の場で依頼者は Request Changes を選び、レビュー（`reviews/review-01.md`）の R-01〜R-04 の示した直しをすべて当てることに決めた。直した中身は次のとおり。
  - R-01（Major）: traceability.json の NFR5.61・NFR5.62・NFR5.63・NFR8.62・NFR8.63・NFR8.65・NFR8.67・NFR9.65 を Deferred にし、持ち主をコード生成（テストの置き場・`vitest.config.ts` の `include`・`coverage.include`・ライセンスヘッダーの検査への入れ方を決める計画 P1〜P7）とした。target に前提（計画で `include` に当たる置き場を選ぶこと、検査の対象に入れること）を書いた。2.1節の表も同じ前提つきの書き方にそろえた。
  - R-02（Minor）: NFR8.71 を Deferred（コード生成のレビューでの検索と Build and Test）にした。
  - R-03（Minor）: 2.2節の断片から冗長な `outputs.upToDateWhen { false }` を外し、`build.gradle.kts` で `verifySecurity` の一員の一覧と段の説明の文言の両方を変えることを計画に書くと明記した。
  - R-04（Minor）: 7節の Build and Test の項目に、架空の JWT の形のテスト用の値でわざと残存を作った 1 回の verify（出力に値が出ないことの確かめ、ファイルはコミットせず確かめた後に消す）を加え、3節にその位置づけを書いた。

## Sources

- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u4-perf-evidence-redactor/infrastructure-design/infrastructure-design-questions.md`（質問なし、決まっていること）
- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u4-perf-evidence-redactor/nfr-design/security-design.md`（1節 守りの層、5.4節 終わりのコード、7.4節、8節 evidence の検査、9節、12節・13節）
- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u4-perf-evidence-redactor/nfr-design/logical-components.md`（3節 LC8、4節 P1〜P7、5節）
- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u4-perf-evidence-redactor/nfr-requirements/security-requirements.md`・`tech-stack-decisions.md`
- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/contract-design/contract-summary.md`（C7）
- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u4-perf-evidence-redactor/functional-design/functional-spec.md`（3つの口と終わりのコード。CI の段の振る舞いの根拠）
- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/domain-design/components.md`（部品 PerfEvidenceRedactor と FR5 の割り当て）
- 読み取りで確かめたもの: ルートの `build.gradle.kts`（`checkToolchain`・`frontendTest`・`frontendCoverage`・`gitleaksScan`・`osvScan`・`verifyStages`・`verify`）、`.github/workflows/ci.yml`、`frontend/package.json`（`test`・`test:coverage`）、`frontend/vitest.config.ts`、`perf/` のファイルの一覧と `perf/README.md`、`aidlc/` の下の `evidence/` のディレクトリの一覧（中身は開いていない）
