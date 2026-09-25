# Build and Test のまとめ — 内部DB の詰め直し・最大ヒープ 50%・テストの JVM の IPv4

## 1. ビルドの状態と前提

- 対象: `fix/260925-storage-memory-fixes` の `ab758ff`（C1〜C5）。`./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` は終わりの値 0。
- 前提: colima（CPU 4・メモリ 6GiB）が動いていること、`DOCKER_HOST` と `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡すこと（`build-instructions.md`）。
- 成果物のイメージ: `mastersmith:storage-memory-fix`（配備のタグ `local` は上書きしていない）。

## 2. テストの種類の一覧

| 種類 | 手順書 | 結果 |
|---|---|---|
| 単体（バックエンド・画面） | `unit-test-instructions.md`（コード生成）・`build-instructions.md` | 733 件・316 件すべて成功 |
| 結合（バックエンド） | `integration-test-instructions.md` | 385 件すべて成功、`AccessTokenApiIT` のくり返し 10 回成功 |
| E2E | `build-instructions.md` | 6 件成功 |
| 性能・負荷 | `performance-test-instructions.md` | `dslMixed`・`--storage --compact`（40 回）・詰め直しの最中のログイン・`refresh` 2 回・`dslCycle` |
| セキュリティ | `security-test-instructions.md` | Gitleaks・SpotBugs・OSV の関門を通過、JMX の公開なし、道具の出力に秘密情報なし |

Test Strategy は Minimal だが、要件 FR5 と計画の「Build and Test に引き継ぐこと」のため、結合・性能・セキュリティの手順書も作った。

## 3. カバレッジの見込み

- 下限（行 80%・分岐 70%、パッケージごとの下限を含む）は変えていない。実測はバックエンド 行 98.08%・分岐 93.83%、画面 行 97.86%・分岐 93.6%。

## Target Verification Matrix

| Target ID | Source | Expected | Actual | Evidence | Owning Stage | Verdict |
|---|---|---|---|---|---|---|
| T-VERIFY | code-generation-plan.md Step 15・team.md Way of Working（NFR5） | `./gradlew verify` のすべての検査が通る | 終わりの値 0 | `build/perf-results/bt/verify.log`・test-results.md 1 節 | build-and-test | Met |
| T-COV-BE | Testing Contract（team 層）・NFR4 | バックエンド 行 80% 以上・分岐 70% 以上、パッケージごとの下限 | 行 98.08%・分岐 93.83%、`jacocoTestCoverageVerification` 通過 | `backend/build/reports/jacoco/test/jacocoTestReport.xml` | build-and-test | Met |
| T-COV-FE | Testing Contract（team 層）・NFR4 | 画面 行 80% 以上・分岐 70% 以上 | 行 97.86%・分岐 93.6% | `verify.log` の Coverage summary | build-and-test | Met |
| T-E2E | NFR6 | E2E がすべて通る | 6 件成功 | `build/perf-results/bt/e2e.log` | build-and-test | Met |
| T-SEC | team.md Code Style（静的解析とセキュリティ検査）・NFR2 | Gitleaks・SpotBugs・OSV の関門を通過、道具の出力に秘密情報なし | すべて通過、秘密情報 0 件 | test-results.md 1・3 節 | build-and-test | Met |
| T-D1 | code-generation-plan.md D1（T1）・FR2.4 | `dslMixed` で `checks` 全成功、`OOMKilled` false、`anon` 最大 1,536MiB 以下、`memory.events` の `max` 0 | 1,304 件成功、false、1,384.9MiB、0 回 | `build/perf-results/bt/mixed-mem-samples.tsv`・`dslMixed.json` | build-and-test | Met |
| T-D6 | code-generation-plan.md D6（G1）・FR1.4 | 40 回の後の詰め直しの直後 250MiB 以下 | 15.2MiB（圧縮の効かない本文は計画の前の測定で 210.4MiB） | `build/perf-results/bt/storage/postgres/storage.tsv`・`compact.txt` | build-and-test | Met |
| T-D5 | code-generation-plan.md D5（A）・NFR1 | 一時停止から再開まで全体 45 秒の内側 | 3,537 ms（`refresh` の後の 1,318MiB でも 4,881 ms） | `compact.txt`・`compact-login.txt` | build-and-test | Met |
| T-D3 | code-generation-plan.md D3（W1）・FR1.5 | 一時停止の最中のログインが再開の後に成功 | 5.1 秒待って 200 | `build/perf-results/bt/compact-login-result.txt` | build-and-test | Met |
| T-DATA | FR1.8 | 詰め直しの前後で本文が壊れない | データ一致、履歴 20 版の戻し 20／20 | `storage/postgres/compact.txt`・`stop-state.txt` | build-and-test | Met |
| T-REFRESH | code-generation-plan.md「Build and Test に引き継ぐこと」・FR5 | `refresh` 2 回とも止まらず `checks` 全成功 | どちらも `OOMKilled` false、全成功 | `refresh-1.json`・`refresh-2.json`・`refresh-state.txt` | build-and-test | Met |
| T-CYCLE | FR5.1（`dslCycle`） | `checks` 全成功、適用・破棄の p95 1 秒以内 | 全成功、8.65 ms・7 ms | `build/perf-results/bt/dslCycle.console.txt` | build-and-test | Met |
| T-FR3 | code-generation-plan.md D7・FR3.2 | `AccessTokenApiIT` と `LoopbackPortCollisionTest` が通る | 10 回すべて成功、3 件成功 | `build/perf-results/bt/access-*.log`・`unit-cmds.log` | build-and-test | Met |
| T-FR4 | FR4.1 | `perf/dsl-timing.sh` の説明と既定の値が 2g で一致 | 36 行・58 行とも 2g | `grep` の結果（test-results.md 1 節） | build-and-test | Met |

## 4. 準備の状態の判定

- build-ready: はい。test-ready: はい（すべてのコマンドが通り、すべての目標が Met）。
- deployment-ready: はい。配備は Deployment Pipeline・Deployment Execution で行う（`develop` への fast-forward を含む。依頼者の決定 D9）。

## 5. 分かっている制約と残る点

- 詰め直しの操作は監査ログにもアプリのログにも残らない（依頼者の決定 Q3）。操作しない間のファイルの伸びは受け入れ、README の既知の制約に書いた。
- 観察（新しい所見）: `refresh` の 2 分で内部DB のファイルが 1,318MiB まで伸びた。ファイルの伸びは DSL の投入と適用だけでなく、トークンの更新の多い使い方でも起きる。README の既知の制約は DSL の場合だけを書いている。`compact` で戻せる（111.9MiB）。後続で README の書き方を広げるかを決めたい。
- 観察: 最大ヒープを 50% にした後、`refresh` の毎秒の件数は前の Intent の記録（75%）より約 25〜30% 少なかった（完全な GC は 0 回。条件が同じでないため判定には使っていない）。確認の指摘 R-02（GC の増えの判定の基準）に当たる。`dslMixed` では GC の回数と確保の失敗が増えたが、要求の失敗は無く p95 は短くなった。
- 道具の「借りている接続が 10 秒の内に返らない」経路は、実際のコンテナで起こせず確かめていない（コード生成の気になる点）。
- `AccessTokenApiIT` の実際の1回目の失敗の原因は確かめられないままである（仕組みの再現に基づいて予防した）。

## Sources

- 計画: `aidlc/spaces/default/intents/260925-storage-memory-fixes/construction/code-generation/code-generation-plan.md`
- 単体テストの手順: `aidlc/spaces/default/intents/260925-storage-memory-fixes/construction/code-generation/unit-test-instructions.md`
- 生成のまとめ: `aidlc/spaces/default/intents/260925-storage-memory-fixes/construction/code-generation/code-summary.md`
- 結果の詳細: `aidlc/spaces/default/intents/260925-storage-memory-fixes/construction/build-and-test/test-results.md`
