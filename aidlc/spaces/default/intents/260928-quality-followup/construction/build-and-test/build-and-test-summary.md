# Build and Test のまとめ（Intent 260928-quality-followup）

scope bugfix・Test Strategy Minimal・単位の分割なし。入力はコード生成の計画 `aidlc/spaces/default/intents/260928-quality-followup/construction/code-generation/code-generation-plan.md`（Testing Contract を含む）・`unit-test-instructions.md`・`code-summary.md` と、要件 `requirements.md`。NFR 要件・NFR 設計の段は無いため、測れる目標は要件の NFR1〜NFR6、機能要件の受け入れの基準、Testing Contract のカバレッジの下限から集めた。

## ビルドの状態と前提

- 統合の後の `develop`（`d1fda19`）で手元の `verify`・`e2eTest` と CI の `verify` が通った（`test-results.md` の 1〜3節）。
- 前提: colima、`DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE`、E2E は Mailpit（`build-instructions.md`）。

## 作った手順書

| 手順書 | 内容 |
|---|---|
| `build-instructions.md` | 前提の環境、`verify` の流し方、`osvScan --rerun`、よくあるつまずき |
| `integration-test-instructions.md` | この Intent に関わる結合テスト、E2E、診断の確かめの手順 |
| `performance-test-instructions.md` | 使い捨ての環境での警報の確かめ（k6 の負荷の試験は行わない） |
| `security-test-instructions.md` | 関門と、この Intent に特有の確かめ（診断・公開の範囲・E2E の報告） |

Test Strategy は Minimal だが、要件が CI・監視・セキュリティの確かめを求めるため、4つとも作った。

## カバレッジの期待

単位の分割は無い。バックエンドは全体の合計とパッケージごと（`packagesJudgedByTotal` の一覧のパッケージは全体の合計）で行 80%・分岐 70%、画面は行 80%・分岐 70%（Testing Contract、`team.md`）。この Intent で一覧のパッケージには手を入れていない（バケットは設定だけで済んだ）。

## Target Verification Matrix

| Target ID | Source | Expected | Actual | Evidence | Owning Stage | Verdict |
|---|---|---|---|---|---|---|
| TC-COV-BE | Testing Contract・`team.md` Testing Posture | バックエンド 行 80% 以上・分岐 70% 以上（全体とパッケージごと） | 行 98.78%・分岐 94.38%、パッケージごとの検証も通過 | `test-results.md` 1節 | build-and-test | Met |
| TC-COV-FE | 同上 | 画面 行 80% 以上・分岐 70% 以上 | 行 97.44%・分岐 92.67% | `test-results.md` 1節 | build-and-test | Met |
| NFR1 | `requirements.md` NFR1 | ブランドカラーとテーマのすべての組で文字のコントラストが WCAG 2.1 AA（既知の違反は make-you-chic-ui の2件に限る） | E2E 110 件通過。050〜080 の既知の違反は空、100 の既知の違反はタブと hover の2件だけ | `test-results.md` 2節 | build-and-test | Met |
| NFR2 | `requirements.md` NFR2 | 変更の後の CI の `verify` が1回以上通る | 実行 36567275650 success | `test-results.md` 3節 | build-and-test | Met |
| NFR3 | `requirements.md` NFR3 | 要求は6個、メールは7個の境界と `+Inf` だけ | `HistogramBucketsIT` 通過、Step 7 と 6節で `le` を確かめた（処理中の数の指標にも同じ境界が付くのは G1: A で受け入れ） | `test-results.md` 1・6節、`code-summary.md` | build-and-test | Met |
| NFR4 | `requirements.md` NFR4 | カバレッジの下限・SpotBugs・OSV・Gitleaks を緩めない、High を出さない | 設定の変更なし、関門すべて通過、OSV の失敗 0 件 | `test-results.md` 1節 | build-and-test | Met |
| NFR5 | `requirements.md` NFR5 | 診断に秘密情報を出さない、公開の範囲を変えない | 診断は数と固定の文だけ、`ExposureIT` 通過 | `test-results.md` 1・5節 | build-and-test | Met |
| NFR6 | `requirements.md` NFR6 | 既存のテストがすべて通り、統合の前に E2E を流す | 単体 1234・結合 565・画面 732・E2E 110 すべて通過 | `test-results.md` 1・2節 | build-and-test | Met |
| FR3.3 | `requirements.md` FR3 の受け入れの基準 | わざと時間切れにしたとき決めた項目が出る | 2件とも出た | `test-results.md` 5節 | build-and-test | Met |
| FR4.3 | `requirements.md` FR4 の受け入れの基準・O3 | p95 の式が値を持ち、しきい値を超えると警報が鳴る | 値 487.5・95・95 ms、しきい値を 50 ms にした写しで3件が firing | `test-results.md` 6節 | build-and-test | Met |
| FR5 | `requirements.md` FR5 の受け入れの基準 | 取り込んだ更新を入れて `verify` と `e2eTest` が通り、一覧の全行に判定がある | どちらも通過、15 行すべてに判定 | `test-results.md` 7節、`code-summary.md` 8節 | build-and-test | Met |

## 準備の状態

- ビルド: 準備できている（手元と CI の `verify` が通過）。
- テスト: 準備できている（E2E を含めてすべて通過）。
- 配備: 準備できている。配備（イメージの作り直しと起動）は Deployment Pipeline・Deployment Execution の段で行う。

## 残る点（承認の場で確かめる）

- **`ms-check-p95` のしきい値は 300 ms**: 要件の前提 A3 は3件とも 1000 ms として書いていた。300 ms はバケットの境界に無く、しきい値の近くの判定は近似になる（`test-results.md` 6節）。
- **新しく届いた Dependabot の3件**: #18 `@types/node` 26.6.3、#19 spotless 8.10.3、#20 Jackson の BOM 3.1.7（`test-results.md` 7節）。
- **閉じ待ちのプルリクエスト**: #5・#6・#7・#8・#9・#13（閉じるのは依頼者）。
- **時間切れの原因**: 確かめていない（`team.md` の決まりとの差。`test-results.md` 4節）。次の数回の CI を見る。
- **作業ブランチ** `fix/260928-quality-followup`: `develop` に統合済み。消すかは依頼者が決める。
