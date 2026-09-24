# 非機能の目標の判定（nfr-validation-matrix）

この段が持ち主の3件の判定と、ほかの性能の目標の扱い。実測は `test-results.md`、計画は `load-test-plan.md`。試験日はすべて 2026-09-25。

## 1. この段が持ち主の目標

| ID | 目標 | 実測 | 判定 | 根拠 |
|---|---|---|---|---|
| NFR1.10 | 今の状態・履歴・破棄・適用の 95% が 1 秒以内（想定の規模の DSL が適用中・プレビュー中、履歴 20 件） | 適用 6.9ms・破棄 5.1ms（各 135 件、1人）。今の状態 1.1ms・履歴 1.1ms（各 521,123 件、同時 5）。失敗 0 | **Met** | `test-results.md` 2.1・2.2 |
| NFR1.12 | 10MB の DSL の処理が、**1g のコンテナ**（ヒープ 75%）で、ログインと同時でも失敗しない | 1g: 負荷をかけ始めて約 13 秒で OOMKilled（exit 137）、要求の 94.4% が失敗。参考の 2g: 失敗 0・OOMKilled なし、プロセスのメモリの最大は上限の 93% | **Not Met** | `test-results.md` 3.1 |
| U4-POOL | 適用などの操作で、内部DB のプール（上限 30）をログインと共有しても尽きない | 2g: 待ちの時間切れの累計 0、借りるまでの待ちの最大 6.0ms（4,019 回）、使用中の最大 2・待ち 0。1回目の 2g も時間切れ 0。1g は止まるまで時間切れ 0 | **Met** | `test-results.md` 3.1・3.2 |

- NFR1.12 は目標を緩めていない。要件（`construction/u4-dsl-management/nfr-requirements/performance-requirements.md` 2節）は「足りなければコンテナの上限の既定の見直しを依頼者に諮る」としており、承認の場で諮る（`test-results.md` 5節 F1）。配備の既定は、前の Intent（260923-colima-spec-up）で既に 2g にしてあり、2g では失敗しなかった。
- U4-POOL は上限 2g の測定で判定した（1g はアプリが止まったため、プールの判定に使える時間が短い）。

## 2. ほかの性能の目標（この段では判定を変えない）

| ID | 目標 | 判定 | 持ち主・根拠 |
|---|---|---|---|
| NFR1.4・NFR1.5・NFR1.6・NFR1.7・NFR1.8・NFR1.11・NFR2.5・U2-PATTERN-COMPILE | 1回ずつの時間・大きさ | Met | Build and Test（`construction/build-and-test/build-and-test-summary.md` の B 表） |
| NFR1.9 | 照合の待ちの上限（接続 3 秒・問い合わせ 5 秒） | Met | Build and Test（結合テスト） |
| NFR1.13・NFR1.14・NFR1.15 | 重い処理は同時に1つ、`DSL_BUSY` は状態を変えない、軽い処理は制限しない | Met | Build and Test（結合テスト）。この段でも、同時 5 の軽い API に `DSL_BUSY` や失敗は無かった（`test-results.md` 2.2） |
| U4-STORAGE-RUN | 動いている間の H2 のファイルの大きさ | Not Met（依頼者が受け入れた既知の制約） | Build and Test。この段でも `dslCycle` で 2.4GB まで増え、止めると 9.5MB に戻った（`test-results.md` 4節） |
| U4-STORAGE-RESTART | 起動し直した後の大きさとデータの無事 | Met | Build and Test |
| NFR1.18〜NFR1.21 | 画面の時間と描き方 | Met | Build and Test（時間は統合の関門にせず E2E で測って記録。`construction/u5-dsl-admin-ui/nfr-requirements/performance-requirements.md`） |

- 手元の監視のダッシュボード（`operation/observability-setup/dashboards.md` の「DSL の操作」の行）は、この段では使っていない。試験は使い捨ての環境で行い、Hikari とメモリの値はアプリと cgroup から直接読んだ。
- 自動で数を増やす仕組み（オートスケーリング）は無い（アプリは1つ、`construction/u4-dsl-management/nfr-design/scalability-design.md` 2節）。そのため確かめる対象が無い。

## 3. 容量の見立て

- 利用者は管理者が数名で、DSL の操作は手動でまれ。軽い API は同時 5・待ち時間なしで秒あたり約 4,300 周をこなし、95 パーセンタイルは 1.1ms だった。利用の実態に対して大きな余裕がある。
- 制約になるのはメモリで、10MB の DSL を扱うにはコンテナの上限 2g が要る。2g でもプロセスのメモリは上限の 93% に達するため、10MB の DSL の投入を続けて行うときの余裕は小さい。U4-STORAGE-RUN（動いている間のファイルの伸び）と合わせて、起動し直しの運用（README の「DSL の管理」）で抑える。
- 接続プール（上限 30）は、使用中の最大 2 で十分な余裕がある。

## Sources

- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/performance-validation/test-results.md`・`load-test-plan.md`・`performance-validation-questions.md`
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/construction/u4-dsl-management/nfr-requirements/performance-requirements.md`・`scalability-requirements.md`
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/construction/u4-dsl-management/nfr-design/performance-design.md`・`scalability-design.md`
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/construction/u5-dsl-admin-ui/nfr-requirements/performance-requirements.md`
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/construction/build-and-test/build-and-test-summary.md`
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/observability-setup/dashboards.md`
- `aidlc/spaces/default/memory/project.md`（Intent 260923-colima-spec-up の学び: 上限 1g では止まるため配備の既定を 2g にした）

## Assumptions & Open Questions

- [assumption] NFR1.12 の Not Met への対応（要件の条件を配備の既定 2g に合わせるか、メモリの使い方を減らすか）は、承認の場で依頼者が決める。
