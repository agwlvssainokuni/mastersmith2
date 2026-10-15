# Build and Test のまとめ（Intent 261015-deps-ui-carryover）

## 1. 全体の状態

- ビルド: 成功（`develop` の先頭 `fb6f5e7`、clean を付けた `./gradlew verify`）。E2E 232 件 passed。CI は4つの統合のすべてで成功（`776738e` は2回目の試行で成功）。
- 性能の測り直し: (a) ロールの書き込みの待ちの最大・(b) ヒープ・(c) ロールの3場面は Met。(b) の `dslMixed` の `memory.peak` だけ Not Met（94.4%・93.1%）で、依頼者の決定で持ち越す。
- 判定: **条件つきの準備完了**。Not Met 1件（受け入れた失敗）と Unverified 2件（配備の段で確かめる）が残る。

## 2. 作った手順書とテストの種類

- `build-instructions.md`（ビルドと1コマンドの検査）
- `integration-test-instructions.md`（この Intent の結合テストと、Intent の最後の E2E）
- `performance-test-instructions.md`（性能の測り直しの順と合否。本体は `perf/README.md`）
- `security-test-instructions.md`（Gitleaks・SpotBugs・OSV-Scanner、漏えいの確かめ、信頼できない入力）
- Test Strategy は Minimal。新しい単体・結合テストはコード生成の段で作った（`unit-test-instructions.md`）。

## 3. カバレッジ（この段の実測）

| 範囲 | 行 | 分岐 | 下限 |
|---|---|---|---|
| backend 全体 | 98.0% | 92.6% | 行 80%・分岐 70%（全体とパッケージごと。検証は通過） |
| frontend 全体 | 95.36% | 90.36% | 行 80%・分岐 70% |

- 手を入れた backend のパッケージは、コード生成の段の実測でどれも下限の内（`dsl.parse` 98.4・89.2、`dsl.domain` 100・96.1、`dsl.service` 97.1・93.5、`dslmanage.service` 97.9・97.0、`dslmanage.generate` 99.4・100、`role.service` 96.9・91.1 など）。`packagesJudgedByTotal` の7パッケージには手を入れていない。

## Target Verification Matrix

| Target ID | Source | Expected | Actual | Evidence | Owning Stage | Verdict |
|---|---|---|---|---|---|---|
| T-01 | requirements.md NFR1 | `./gradlew verify` が通る（対象DB の SKIPPED 0） | 成功、SKIPPED 0 | test-results.md 1節 | build-and-test | Met |
| T-02 | code-generation-plan.md Testing Contract・team.md | backend 行 80%・分岐 70%（全体とパッケージごと） | 行 98.0%・分岐 92.6%、パッケージの検証通過 | test-results.md 1節 | build-and-test | Met |
| T-03 | code-generation-plan.md Testing Contract・team.md | frontend 行 80%・分岐 70% | 行 95.36%・分岐 90.36% | test-results.md 1節 | build-and-test | Met |
| T-04 | requirements.md NFR5・FR2.5 | E2E と実際のブラウザの axe が通る | 232 件 passed | test-results.md 2節 | build-and-test | Met |
| T-05 | requirements.md NFR6 | OSV-Scanner の止める指摘 0 | 0（警告 18） | test-results.md 1節 | build-and-test | Met |
| T-06 | requirements.md NFR4・FR3.1・FR4.1 | `*SecretLeakIT` が通る | 通過 | test-results.md 1節 | build-and-test | Met |
| T-07 | requirements.md FR1.4 | 統合の後の CI が通る（試行ごとに記録） | 4つの統合で成功（`776738e` は2回目） | test-results.md 3節 | build-and-test | Met |
| T-08 | requirements.md FR9.2 | 1行の上限の境界（ちょうどは受け付け、超えると拒否、部品の例外を含めない） | 境界のテストが通過 | test-results.md 1節 | build-and-test | Met |
| T-09 | requirements.md FR7.2 | ロールの3場面が手順書の順と単独で fails 0 | fails 0、`ROLE_NO_CHANGE` 0 | perf-run-notes.md (c) | build-and-test | Met |
| T-10 | requirements.md FR8.4 | 5 回すべて acquire の MAX が 20 ms 未満 | 最大 12.62 ms（5 回とも 20 ms 未満） | perf-run-notes.md (a) | build-and-test | Met |
| T-11 | requirements.md FR9.4 前半 | OOM・JVM の停止なし、OOMKilled false | なし・false（ヒープの最大 682.0 MB） | perf-run-notes.md (b) | build-and-test | Met |
| T-12 | requirements.md FR9.4 後半 | `dslMixed` の `memory.peak` が上限の 90% 未満 | 94.4%・93.1% | perf-run-notes.md (b)・memory.peak の内訳 | build-and-test | Not Met |
| T-13 | requirements.md FR9.3 | 測りをもとに設定を変えるか決め、判断と根拠を記録 | 変えない（依頼者の決定「持ち越す」） | test-results.md 4節 | build-and-test | Met |
| T-14 | code-generation-plan.md 6節の 7 | 新しい jdk のイメージで `hikari-pool.sh status` が新しい JRE のアプリに対して動く | 前の版の JVM に対してだけ動いた | perf-run-notes.md | deployment-execution | Unverified |
| T-15 | requirements.md FR11.2・コード生成のレビュー R-03 | 新しい書体（5.3.1）で画面の文字が表示される | ビルドに書体のファイルが入ることだけ確かめた | test-results.md 2節 | deployment-execution | Unverified |

## 4. 準備の判断

- ビルドとテスト: 準備完了。
- 配備: 条件つきで準備完了。T-12 は依頼者の決定で持ち越す受け入れた失敗、T-14・T-15 は Deployment Execution で確かめる。

## 5. 既知の制約と、受け入れた差

### 5.1 依頼者の決定で受け入れた差

- `dslMixed` の `memory.peak` の Not Met を持ち越す（T-12）。設定は変えず、後の Intent で `memory.stat` の時系列の内訳を根拠に、判定の物差しを決め直すかを検討する。目標は緩めていない。
- E2E を Intent の最後に1回だけにした（③・④ は統合の前に E2E を流していない）。`team.md` の「画面・認証に関わる変更は統合の前に E2E」との差。この段の E2E は通った。
- axe-core 4.14.0 の見送りと、WCAG 2.5.3 の違反（make-you-chic-ui の Dropdown のトリガー、作業ロールのメニューの項目）の後の Intent への持ち越し。

### 5.2 コード生成のレビューの持ち越し（承認の場の推奨どおりこの段で扱った）

- R-01・R-02・R-04: `cross-unit-traceability.md` の注に記録した。
- R-03: E2E をこの段で流し、書体はビルドへの取り込みを確かめた。表示は T-15。
- R-05: 3つの台本を使い捨ての環境で先に確かめた（`perf-run-notes.md`）。

### 5.3 この段で見つかった後の段・後の Intent への持ち越し

- `perf/README.md` の (c) の判定の `grep -o '"checks": {...}'` は、k6 の要約の JSON が複数行のため何も出さない（この段は python で読んで判定した）。手順書の直しは後の Intent で行う。
- (a) で 204 の件数が回ごとに減った（745,497 → 535,880）。原因は未確認（見立て: 作り直しても残る内部DB に監査の行が積み上がる）。
- 1回目の WARN 43 行の内訳は取り直せなかった（確かめの結果を見てから片付ける決まりの漏れ）。
- CI の `AdminForbiddenProvider.test.tsx` の1回目の失敗（再現せず、再実行で通過）。同じテストが二度目に落ちたら直す。
- 配備の前に、配備済みの DSL の最も長い行を測り直す（D-5: A）。④ の Dependabot の知らせ #50〜#53 が閉じたかを確かめる。

## Sources

- 要件: `aidlc/spaces/default/intents/261015-deps-ui-carryover/inception/requirements-analysis/requirements.md`
- 計画と記録: `aidlc/spaces/default/intents/261015-deps-ui-carryover/construction/code-generation/`（`code-generation-plan.md` の Testing Contract と 6節、`code-summary.md`、`traceability.json`）
- 質問と答え: `aidlc/spaces/default/intents/261015-deps-ui-carryover/construction/build-and-test/build-and-test-questions.md`
- 性能の記録: `aidlc/spaces/default/intents/261015-deps-ui-carryover/construction/build-and-test/perf-run-notes.md`、手順は `perf/README.md`
- 決まり: `aidlc/spaces/default/memory/team.md`（Testing Posture・Way of Working）、`project.md`（Testing Posture の学び）

## Assumptions & Open Questions

- `memory.peak` を押し上げているのが回収できるページキャッシュだという見立ては確かめていない（後の Intent で物差しとあわせて検討する）。
- T-14・T-15 は Deployment Execution で確かめる。
