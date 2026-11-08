# Build and Test のまとめ（Intent 261108-ci-size-coverage）

入力: `construction/code-generation/code-generation-plan.md`・`unit-test-instructions.md`・`code-summary.md`

## 1. 状態

- ビルド: 成功（手元の全件の `verify`、CI の手動の実行のどちらも通った）。
- テストの種類: Test Strategy は Minimal。新しい自動テストは置かない（依頼者の決定 Q4・F1）。既存の全テストを手元の全件の `verify` と CI で流した。追加のテストの手順のファイル（結合・性能・セキュリティ）は作らない。
- カバレッジ: アプリのソースを変えていないため、値は前の Intent と同じ（`test-results.md` 1節）。

## Target Verification Matrix

| Target ID | Source | Expected | Actual | Evidence | Owning Stage | Verdict |
|---|---|---|---|---|---|---|
| NFR1 | requirements.md NFR1 | 足した手順による job の延びが 2 分以内。job 全体が 42 分の線の内 | 足した手順 27 秒。job 全体 39分03秒 | test-results.md 3節（CI の実行 42485490431） | build-and-test | Met |
| NFR2 | requirements.md NFR2 | `verify` の段・下限・合否の決まりを変えない。要約の成否で CI の結果が変わらない | `verify` の段とアプリのソースは変えていない。足した手順はすべて `continue-on-error: true` | `.github/workflows/ci.yml` の差、code-summary.md 3.3節（報告が無いときも終了コード 0） | build-and-test | Met |
| NFR3 | requirements.md NFR3 | 要約と成果物にパス・行数・件数・割合だけ | 要約と成果物の中身を確かめ、秘密情報・個人に関する値は無い。数えるのは git が管理するファイルだけ | test-results.md 2・3節 | build-and-test | Met |
| NFR4 | requirements.md NFR4 | 同じコミットで手元と CI の規模の表が一致。報告に cloc の版を載せる | 大分類の表が一致（CI は cloc 2.06、手元は 2.10）。道具の行に版が出る | test-results.md 3節（依頼者が CI の画面で確かめた） | build-and-test | Met |
| NFR5 | requirements.md NFR5 | Node 版が Python 版と同じ表の値を出す | 表の部分の diff は差なし（2,461 ファイル） | code-summary.md 3.2節 | code-generation | Met |
| TP-COV-BE | team.md Testing Posture | バックエンドの行 80%・分岐 70%、パッケージごとの下限 | 行 97.68%・分岐 91.52%、`jacocoTestCoverageVerification` が通った | test-results.md 1節 | build-and-test | Met |
| TP-COV-FE | team.md Testing Posture | 画面の行 80%・分岐 70% | 行 95.49%・分岐 91.03% | test-results.md 1節 | build-and-test | Met |
| TP-SUITE | Testing Contract scope_floor | 既存のテストが緑のまま | 単体 2,937・結合 1,716（飛ばし 10）・画面 3,063 × 2、失敗 0 | test-results.md 1節 | build-and-test | Met |
| TP-CI-TIME | team.md Testing Posture | Build and Test で全件の `verify` と CI の時間を測って記録 | 手元 16分18秒（`-PitForks=3`）、CI の job 39分03秒 | test-results.md 1・3節 | build-and-test | Met |
| TC-STRATEGY | Testing Contract strategy_volume | 要件ごとの単体テスト | 置かない（依頼者の決定 Q4: C・F1。CI の報告の台本は本体の機能ではないため当たらないという読み方）。代わりに NFR5 の一致と手元・CI の実行で確かめた | requirements.md 6節、code-summary.md 5節 | code-generation | N/A |
| CI-BEFORE-MERGE | Testing Contract | CI での実行（CI Pipeline の段を流すとき） | この計画は CI Pipeline の段を流さない | aidlc-state.md | — | N/A |

## 2. 準備の状態

- ビルドの準備: できている。
- テストの準備: できている（既存の全テストが通った）。
- 配備の準備: アプリと WAR の中身は変わらないため、配備に響かない（`project.md` の Deployment の「配備に響かない Intent」）。

## 3. 決まりとの差・残ること

- E2E は流さない（依頼者の決定。理由は test-results.md 4節）。
- 新しい自動テストは置かない（依頼者の決定 Q4・F1）。
- 要約の割合は小数 2 桁の切り捨て（前の記録の四捨五入と 0.01 ずれうる）。
- CI の job が前の Intent より約 8 分長い（39分03秒）。42 分の線の内だが、次の CI でも長いままなら、テストの分け方を依頼者に諮る（`team.md` の Testing Posture）。

## 次の配備へ引き継ぐこと

- `develop` へのプッシュの CI で、要約の規模の表に差の列（「前の回との差」）が出ることを確かめる（統合の後の最初のプッシュ）。手動の実行の道（差が「—」）はこの段で確かめた。
- CI の job の時間（今回 39分03秒）を次の CI でも見て、42 分の線に近づいていないかを確かめる。
- 配備の段で確かめる項目は無い（WAR とイメージの中身は変わらない）。前の Intent（261107-verify-quick-local）の「次の配備へ引き継ぐこと」の一覧は、次に配備する Intent の Deployment Pipeline が読む。
