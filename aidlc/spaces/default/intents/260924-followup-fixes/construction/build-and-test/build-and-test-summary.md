# Build and Test のまとめ — 前の Intent で後に回した小さな修正7件

Intent 260924-followup-fixes（scope: bugfix、Depth Minimal、Test Strategy Minimal）の Build and Test の段のまとめ。実測の結果は `test-results.md`。

## 1. ビルドの状態と前提

- 対象: 作業ブランチ `fix/260924-followup-fixes`（Code Generation の C1〜C6 と記録のコミットを含む）。
- 前提: colima の VM（CPU 4・メモリ 6GiB）、`DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡したシェル（`build-instructions.md`）。
- 試験のイメージは `mastersmith:followup-fixes`（依頼者の判断。配備の `mastersmith:local` は上書きしない）。k6 を流すあいだ配備したアプリを止める（依頼者の判断）。

## 2. テストの種類の一覧

| 種類 | 手順 | この段で流すか |
|---|---|---|
| 単体テスト・結合テスト（組み込みの H2 と対象DB のコンテナ）・画面のテスト・カバレッジの下限・安全の検査 | `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（`build-instructions.md`・`integration-test-instructions.md`・`security-test-instructions.md`） | 流す |
| E2E | `./gradlew e2eTest` | 流す |
| コンテナの設定 | `MASTERSMITH_IMAGE_TAG=followup-fixes ./docker/check-container-limits.sh` | 流す |
| 秘密情報の置き場（R-03） | `security-test-instructions.md` の 3節 | 流す |
| 負荷の試験（`dslMixed`）と起動のログ | `performance-test-instructions.md` | 流す（Performance Validation の段が無いため、この段が持ち主） |

## 3. カバレッジの見込み

行 80%・分岐 70%（バックエンドは全体の合計と新しいパッケージごと、画面は全体）。Code Generation の Step 18 の実測はバックエンド 行 98.08%・分岐 93.83%、画面 行 97.86%・分岐 93.6%。

## Target Verification Matrix

設計の段（NFR Requirements・NFR Design）が無いため、目標は、要件の NFR と測る合格の条件のある FR、承認済みの Testing Contract（`code-generation-plan.md`）、team.md・project.md の下限から集めた。

| Target ID | Source | Expected | Actual | Evidence | Owning Stage | Verdict |
|---|---|---|---|---|---|---|
| TC-COV-BE | Testing Contract・team.md Testing Posture | バックエンドの行 80% 以上・分岐 70% 以上（全体と新しいパッケージごと） | 行 98.08%・分岐 93.83%。全体と新しいパッケージごとの下限の検査は通過 | `test-results.md` 1節（verify の2回目） | build-and-test | Met |
| TC-COV-FE | Testing Contract・team.md Testing Posture | 画面の行 80% 以上・分岐 70% 以上 | 行 97.86%・分岐 93.6% | `test-results.md` 1節 | build-and-test | Met |
| TC-SUITE | Testing Contract（bugfix の下限）・NFR6 | 既存を含む全テストが通る（失敗 0・対象DB のテストを飛ばさない）。E2E も通る | 2回目の verify で単体 726・結合 382・画面 316 件がすべて成功（飛ばし 0）。E2E 6 件成功。1回目は AccessTokenApiIT の 6 件が接続の失敗で落ち、クラスの再実行 3 回と verify の2回目で通った（原因は未確認） | `test-results.md` 1節 | build-and-test | Met |
| TC-REGRESSION | Testing Contract（bugfix の下限）・FR2.2 | 不具合を再現するテストが直しと同じコミットにあり、直す前に失敗し直した後に通る | 再現のテストと直しは同じコミット 37e60be。直す前は新しい結合テスト 4 件が主キーの重複で失敗し、直した後は通る（Code Generation の Step 4）。この段の verify でも通過 | `aidlc/spaces/default/intents/260924-followup-fixes/construction/code-generation/code-summary.md`・`test-results.md` 1節 | build-and-test | Met |
| NFR3-GATE | requirements.md NFR3・team.md Way of Working | `./gradlew verify` のすべての検査が通る（Gitleaks・SpotBugs・OSV-Scanner を含む） | verify の2回目が BUILD SUCCESSFUL（Gitleaks 検出なし、SpotBugs の止める指摘なし、OSV-Scanner 通過） | `test-results.md` 1節 | build-and-test | Met |
| NFR1-SECRET | requirements.md NFR1・FR1.2・FR1.4 | 外部へ送るログで個人に関する値が伏せられ、パスワード・トークン・署名鍵が送られない | OtlpLogExportIT・SanitizingLogRecordExporterTest・既存の *SecretLeakIT が成功 | `test-results.md` 1節・`security-test-instructions.md` | build-and-test | Met |
| NFR4-ONELINE | requirements.md NFR4・FR4.1 | 標準出力の JSON のログは1件1行、メッセージの値に改行が無い。起動の Hibernate の案内も同じ | JsonLogFormatTest が成功。使い捨てのアプリの起動のログ 74 行・k6 の後の 213 行で改行を含むメッセージ 0 件、Hibernate の案内（org.hibernate.orm.connections.pooling）は ⏎ で1件 | `test-results.md` 4節 | build-and-test | Met |
| NFR5-A11Y | requirements.md NFR5 | 画面部品ごとの vitest-axe の検査が通る | 画面のテスト 316 件（vitest-axe の検査を含む）が成功 | `test-results.md` 1節 | build-and-test | Met |
| FR5-MEM | requirements.md FR5.1・FR5.2 | 既定のメモリの上限が 2g で、確かめのスクリプトが 2147483648 を期待して通る | check-container-limits.sh 20 件すべて OK（変数なしで両方の compose が 2147483648） | `test-results.md` 2節 | build-and-test | Met |
| FR6-ENV | requirements.md FR6.2・FR6.3（R-03） | `app` が `MASTERSMITH_SAMPLE_TARGETDB_*` を持たず、`.env.targetdb` は Git 管理外、見本は値が空 | 節5 が OK、check-ignore 0・1、ls-files は見本だけ、見本の値は空 | `test-results.md` 2節 | build-and-test | Met |
| FR8-LOGIN500 | requirements.md FR8.2 | 行が無い試験用の利用者のまま `dslMixed` を流し、ログインの `checks` の率 1（500 が 0 件） | dslMixed のログインの checks 100%、http_req_failed 0%、アプリのログの 500・主キーの重複 0 件 | `test-results.md` 3節 | build-and-test | Met |
| FR3-NOOVERLAP | requirements.md FR3.1（R-01） | `loginLoop-user` の行の数が VUS と同じで、利用者の重なりが 0 件 | loginLoop-user の行 10、重なり 0 件 | `test-results.md` 3節 | build-and-test | Met |

## 4. 準備の状態の判定

- ビルドの準備: できている（verify の2回目が成功、実行可能 WAR ができる）。
- テストの準備: できている（すべての目標が Met）。ただし 1回目の verify で `AccessTokenApiIT` が接続の失敗で落ち、原因を確かめていない（`test-results.md` 1節）。team.md の「不安定なテストは原因を直すまで統合しない」に当たるかを承認の場で確かめる。
- 配備の準備: 試験のイメージ `mastersmith:followup-fixes` で使い捨ての環境を動かして確かめた。配備（`mastersmith:local` の作り直しと `.env` の移し替え）は Deployment Pipeline・Deployment Execution の段で行う。

## 5. 分かっている制約と残る点

- `AccessTokenApiIT` の1回目の失敗の原因は未確認（上の 4節）。
- `dslMixed` でプロセスのメモリが上限 2g の 93%（`memory.events` の `max` 2,340 回、止まってはいない）。前の振り返りの束 2 と同じで、この Intent の範囲の外。
- 試験の後にロックの状態の行の数を内部DB で数える記録は取れなかった（判定には影響しない。`test-results.md` 3節）。
- `perf/dsl-timing.sh` 33 行の説明（「要件の条件は 1g」）が前の Intent の決定（2g）と食い違う。直していない。

- 値の入った `.env` を読んだ状態でのアプリのコンテナの環境変数の確かめ（名前だけ）と、この PC の `.env` から `.env.targetdb` への移し替えは Deployment Execution の段で行う。
