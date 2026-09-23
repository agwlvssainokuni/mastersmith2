# テストの結果（test-results）

実行日時: 2026-09-23（UTC 05:50〜06:00）。対象のコミット: `d948544`（`develop`）。すべてリポジトリのルートで実行した。

## 1. ビルドと統合の前の検査

`./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` → **BUILD SUCCESSFUL（1分55秒）**

| 段 | 結果 |
|---|---|
| 準備・フォーマット・リンタ・ライセンスヘッダー・ビルド | 成功 |
| 単体テスト（`test`） | 387 件、失敗 0、エラー 0、スキップ 0 |
| 結合テスト（`integrationTest`） | 244 件、失敗 0、エラー 0、スキップ 0 |
| 画面のテスト（Vitest） | 167 件すべて通過 |
| カバレッジ（バックエンド、JaCoCo） | 行 96.14%（1393/1449）、分岐 91.45%（417/456）。下限 行 80%・分岐 70% を満たす |
| カバレッジ（画面、`@vitest/coverage-v8`） | 行 98.73%、分岐 94.02%。下限 行 80%・分岐 70% を満たす |
| 静的解析（SpotBugs＋FindSecBugs） | `spotbugsGate` 通過。重大度 High は0件（priority 2・3 の警告 52 件は、本 Intent で変えていない既存のコードのもの） |
| 依存関係の脆弱性（OSV-Scanner） | `osvScan` は入力（lockfile）が変わらないため UP-TO-DATE（前回の判定のまま通過）。本 Intent で依存関係は変えていない |
| 秘密情報の検出（Gitleaks、履歴全体） | `no leaks found` |
| 成果物 | `backend/build/libs/mastersmith.war` |

件数は `backend/build/test-results/`、カバレッジは `backend/build/reports/jacoco/test/jacocoTestReport.xml` と `verify` の出力から読んだ実測の値である。

## 2. 本 Intent のテストのコマンド（`unit-test-instructions.md`）

同じコマンドは1回ずつ実行した。

| # | コマンド | 結果 |
|---|---|---|
| 1 | `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.auth.web.ConcurrentLoginAuditIT' --rerun` | BUILD SUCCESSFUL（N=10・20 とも通過） |
| 2 | `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.config.DataSourcePoolIT' --rerun` | BUILD SUCCESSFUL（3件通過） |
| 3 | `MASTERSMITH_DB_MAXIMUM_POOL_SIZE=10 ./gradlew :backend:integrationTest --tests 'cherry.mastersmith.auth.web.ConcurrentLoginAuditIT' --rerun` | **BUILD FAILED（期待どおり）**。2件とも失敗し、F2 を検出した |
| 4 | `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.auth.service.LoginConcurrencyIT' --rerun` | BUILD SUCCESSFUL |

コマンド3の失敗の内容（上限 10 に戻したとき。修正の前と同じ形で再現する）:

| N | 応答 | 待ち合わせ | `LOGIN_SUCCEEDED` の行 | 監査の失敗 | 時間切れを含むログ | かかった時間 |
|---|---|---|---|---|---|---|
| 10 | 200 が 10 件 | 10 件そろった | 0 件 | 10 件 | 20 件 | 5,806 ms |
| 20 | 200 が 10 件、500 が 10 件 | 10 件だけ | 2 件 | 8 件 | 36 件 | 25,044 ms |

コマンド3は、テストが F2 を検出できること（検出力）と、環境変数が効くこと（FR1.2）を確かめるためのもので、失敗が合格である。成果物の不具合ではない。

## 3. 目標の確かめの表

`build-and-test-summary.md` の「Target Verification Matrix」に最終の判定を書いた。まとめは次のとおり。

| 判定 | 件数 | 内訳 |
|---|---|---|
| Met | 9 | カバレッジの下限（バックエンド・画面の行と分岐）、bugfix の回帰テスト、既存のテストが通る、NFR1、NFR3、NFR4 |
| Not Met | 0 | — |
| Unverified | 2 | FR6.2（k6 による同時 10 件のログイン）と NFR2（1g のコンテナの内側）。どちらも配備した環境が要り、持ち主は実行の計画に EXECUTE として入っている `deployment-execution` の段 |

## 4. 失敗の判定と、止まった点

- 実行したコマンドのうち、成果物の不具合による失敗は1件も無い（コマンド3の失敗は期待どおりの検出である）。
- ステージ定義の判定では、`Unverified` が2件残るため「成功」とはならない。
- 失敗の段階の手順（ladder）の当てはめ:
  1. この段の中での直し: 当たらない。2件は配備した環境が要る確かめで、この段のテストや設定の不備ではない。
  2. 原因の分類: 生成したコードや Code Generation の選択に原因は無い。コード生成に戻っても直るものではない。
  3. 自動の戻し: Construction Autonomy Mode が未設定のため行わない。
  4. 人に判断を仰ぐ: 行う。

### 選べる手（決定済み）

依頼者の決定: **A**（「A. 配備の段に引き継ぐ（推奨）」）。`Unverified` の2件（FR6.2・NFR2）を `deployment-execution` の段に引き継ぐこととして、本ステージの承認に進む。`Unverified` を `Met` に書き換えたり、目標を緩めたりはしていない。

以下は、決定の前に示した案である。

| 手 | 内容 | 影響の見積もり |
|---|---|---|
| **A（推奨）** | `Unverified` の2件を `deployment-execution` の段に引き継ぐこととして、本ステージを承認する | 追加の作業なし。危険は「負荷をかけた確かめが済まないまま配備の段に進む」ことだが、その確かめ自体が配備の段の役目で、実行の計画に入っている |
| B | この段で、使い捨てのコンテナを起動して k6 で同時 10 件のログインを流し、2件をここで確かめる | 30〜60 分。費用なし（手元の colima）。危険は、配備の段と同じ確かめを二重に行うことと、配備の手順（Deployment Pipeline）を決める前に環境を作ること |

**`Unverified` を `Met` に書き換えることや、目標を緩めて「満たした」ことにすることはしない。**

## Sources

- 実行の出力（`./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`、2章の4つのコマンド）
- `backend/build/test-results/test/`、`backend/build/test-results/integrationTest/`
- `backend/build/reports/jacoco/test/jacocoTestReport.xml`
- `aidlc/spaces/default/intents/260923-audit-pool-exhaustion/construction/code-generation/unit-test-instructions.md`
- `aidlc/spaces/default/intents/260923-audit-pool-exhaustion/aidlc-state.md`（Test Strategy: Minimal、実行の計画）

## Assumptions & Open Questions

- None.
