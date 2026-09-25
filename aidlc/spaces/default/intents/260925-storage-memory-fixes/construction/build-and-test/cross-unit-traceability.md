# 要件の網羅の確かめ（cross-unit-traceability）

## 判定

**不合格（条件つき）**: 要件定義の FR・NFR 28 件のうち、`traceability.json` で OK が 19 件、N/A が 5 件（FR1.3・FR1.6・FR1.7・FR3.3・NFR3）、Deferred が 4 件（FR2.4・FR5.1・FR5.2・FR5.3）。段の定義では OK 以外は「網羅されていない」ため、承認の場で扱いを確かめる。

- N/A の 5 件は、依頼者の決定（コード生成の Q2: A・Q3、D7）で作らない・行わないとした要件との差で、`code-generation-plan.md` の「要件との差」と `code-summary.md` に記録がある。
- Deferred の 4 件は、この段で実行して目標をすべて満たした（`build-and-test-summary.md` の Target Verification Matrix）。`traceability.json` はコード生成の成果物で、確定の後は書き換えていない。
- この Intent は bugfix で User Stories の段が無いため、AC は数えない。Unit の分割も無い（ゼロ Unit）。
- 追跡は、要件定義の FR・NFR から `code-generation/traceability.json` への1段でたどった（機能設計の BR・NFR 要件の枝番の段が無いため）。

## 要件ごとの網羅

| ID | traceability.json の状態 | 持ち主 | 対象のファイル | 判定 |
|---|---|---|---|---|
| FR1.1 | OK | code-generation（ゼロ Unit） | `backend/src/test/java/cherry/mastersmith/config/H2CompactionByPoolSuspensionIT.java` | 対応済み（ファイルあり: はい） |
| FR1.2 | OK | code-generation（ゼロ Unit） | `backend/src/test/java/cherry/mastersmith/config/H2CompactionByPoolSuspensionIT.java` | 対応済み（ファイルあり: はい） |
| FR1.3 | N/A | code-generation（ゼロ Unit） | なし | 未対応（依頼者の決定による要件との差）: 作らない。依頼者の決定 Q2: A で入口は JMX（docker/hikari-pool.sh）だけとし、管理者向けの HTTP API と 401・403・200 のテストは作らない（code-summary.md の要件との差） |
| FR1.4 | OK | code-generation（ゼロ Unit） | `backend/src/test/java/cherry/mastersmith/config/H2CompactionByPoolSuspensionIT.java` | 対応済み（ファイルあり: はい） |
| FR1.5 | OK | code-generation（ゼロ Unit） | `backend/src/test/java/cherry/mastersmith/config/H2CompactionByPoolSuspensionIT.java` | 対応済み（ファイルあり: はい） |
| FR1.6 | N/A | code-generation（ゼロ Unit） | なし | 未対応（依頼者の決定による要件との差）: 行わない。依頼者の決定 Q3 で HikariCP の標準の機能だけで行い、詰め直しの操作は監査ログ・アプリのログに残さない（前と後の大きさと時間は道具の出力で見る） |
| FR1.7 | N/A | code-generation（ゼロ Unit） | なし | 未対応（依頼者の決定による要件との差）: 変えない。依頼者の決定 Q3 により監査の決まりは今のまま。一時停止の間は監査の記録も失敗ではなく再開まで待つ（code-summary.md の要件との差） |
| FR1.8 | OK | code-generation（ゼロ Unit） | `backend/src/test/java/cherry/mastersmith/config/H2CompactionByPoolSuspensionIT.java` | 対応済み（ファイルあり: はい） |
| FR1.9 | OK | code-generation（ゼロ Unit） | `README.md` | 対応済み（ファイルあり: はい） |
| FR1.10 | OK | code-generation（ゼロ Unit） | `docker/hikari-pool.sh` | 対応済み（ファイルあり: はい） |
| FR1.11 | OK | code-generation（ゼロ Unit） | `backend/src/test/java/cherry/mastersmith/config/H2CompactionByPoolSuspensionIT.java` | 対応済み（ファイルあり: はい） |
| FR2.1 | OK | code-generation（ゼロ Unit） | `aidlc/spaces/default/intents/260925-storage-memory-fixes/construction/code-generation/baseline-measurement.md` | 対応済み（ファイルあり: はい） |
| FR2.2 | OK | code-generation（ゼロ Unit） | `aidlc/spaces/default/intents/260925-storage-memory-fixes/construction/code-generation/code-generation-plan.md` | 対応済み（ファイルあり: はい） |
| FR2.3 | OK | code-generation（ゼロ Unit） | `Dockerfile` | 対応済み（ファイルあり: はい） |
| FR2.4 | Deferred | build-and-test | `build/perf-results/bt/` | 未対応（traceability.json では Deferred）。この段で確かめた: T-D1（dslMixed、anon 1,384.9MiB・max 0） |
| FR3.1 | OK | code-generation（ゼロ Unit） | `aidlc/spaces/default/intents/260925-storage-memory-fixes/construction/code-generation/code-summary.md` | 対応済み（ファイルあり: はい） |
| FR3.2 | OK | code-generation（ゼロ Unit） | `backend/src/test/java/cherry/mastersmith/common/testsupport/LoopbackPortCollisionTest.java` | 対応済み（ファイルあり: はい） |
| FR3.3 | N/A | code-generation（ゼロ Unit） | なし | 未対応（依頼者の決定による要件との差）: D7 で直したため、再現できなかったときの扱い（不安定と確かめられていない扱いで統合）は当たらない |
| FR4.1 | OK | code-generation（ゼロ Unit） | `perf/dsl-timing.sh` | 対応済み（ファイルあり: はい） |
| FR5.1 | Deferred | build-and-test | `build/perf-results/bt/` | 未対応（traceability.json では Deferred）。この段で確かめた: T-D1・T-D6・T-REFRESH・T-CYCLE（Build and Test で実行） |
| FR5.2 | Deferred | build-and-test | `build/perf-results/bt/` | 未対応（traceability.json では Deferred）。この段で確かめた: test-results.md 2.1 節（直す前の値との比べ） |
| FR5.3 | Deferred | build-and-test | `build/perf-results/bt/` | 未対応（traceability.json では Deferred）。この段で確かめた: T-D6・T-D5（詰め直しの直後 15.2MiB、3,537 ms）。配備先の環境が要る目標は無し |
| NFR1 | OK | code-generation（ゼロ Unit） | `README.md` | 対応済み（ファイルあり: はい） |
| NFR2 | OK | code-generation（ゼロ Unit） | `docker/jmx/HikariPoolControl.java` | 対応済み（ファイルあり: はい） |
| NFR3 | N/A | code-generation（ゼロ Unit） | なし | 未対応（依頼者の決定による要件との差）: 依頼者の決定 Q2: A でサーバー側の管理者の検査は無い。代わりに JMX を外に公開せず、この PC で Docker を使える人だけが操作できる（application.yaml と README.md の説明、code-summary.md の要件との差） |
| NFR4 | OK | code-generation（ゼロ Unit） | `backend/build.gradle.kts` | 対応済み（ファイルあり: はい） |
| NFR5 | OK | code-generation（ゼロ Unit） | `aidlc/spaces/default/intents/260925-storage-memory-fixes/construction/code-generation/code-summary.md` | 対応済み（ファイルあり: はい） |
| NFR6 | OK | code-generation（ゼロ Unit） | `aidlc/spaces/default/intents/260925-storage-memory-fixes/construction/code-generation/code-summary.md` | 対応済み（ファイルあり: はい） |

## 網羅されていない要素

- 依頼者の決定による要件との差（N/A）: FR1.3（管理者向けの HTTP API と 401・403・200 のテスト）、FR1.6（詰め直しの監査）、FR1.7（詰め直しの最中の監査の扱い。実際は再開まで待つ）、FR3.3（再現できなかったときの扱い。直したため当たらない）、NFR3（サーバー側の管理者の検査。代わりに JMX を外に公開しない）。
- この段で確かめた Deferred: FR2.4・FR5.1・FR5.2・FR5.3（すべて Met）。

## Sources

- 要件: `aidlc/spaces/default/intents/260925-storage-memory-fixes/inception/requirements-analysis/requirements.md`
- 追跡: `aidlc/spaces/default/intents/260925-storage-memory-fixes/construction/code-generation/traceability.json`
- 計画: `aidlc/spaces/default/intents/260925-storage-memory-fixes/construction/code-generation/code-generation-plan.md`
- 単体テストの手順: `aidlc/spaces/default/intents/260925-storage-memory-fixes/construction/code-generation/unit-test-instructions.md`
- 生成のまとめ: `aidlc/spaces/default/intents/260925-storage-memory-fixes/construction/code-generation/code-summary.md`
