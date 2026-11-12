# Build and Test のまとめ（Intent 261110-deps-forks-review）

入力: `construction/code-generation/code-generation-plan.md`・`unit-test-instructions.md`・`code-summary.md`

## 1. 状態

- ビルド: 成功（手元の全件の `verify`、E2E 全体、CI の手動の実行6回とプッシュの CI）。
- テストの種類: Test Strategy は Minimal。この Intent は依存・道具・イメージの更新と記述の直しで、新しい自動テストは置いていない（計画どおり。bugfix の範囲の「再現のテスト」に当たる不具合の直しは無い）。既存の全件が緑のままであることで確かめた。追加のテストの手順のファイル（結合・性能・セキュリティ）は作らない。
- カバレッジ: バックエンドは全体と、すべてのパッケージごとの下限を満たす。画面は全体の下限を満たす。

## Target Verification Matrix

| Target ID | Source | Expected | Actual | Evidence | Owning Stage | Verdict |
|---|---|---|---|---|---|---|
| FR4.1 | requirements.md FR4.1 | 同じコミットで CI の手動の実行を 2・3 交互に各3回。起こす前に承認を得る | B5 の承認の後、`e566faa4` で 2・3・2・3・2・3 の6回を流した | test-results.md 3.1節 | build-and-test | Met |
| FR4.1a | requirements.md FR4.1a | 6回すべてが同じコミット | 6回とも `headSha` が `e566faa4`。比べの間のプッシュなし | test-results.md 3.1節 | build-and-test | Met |
| FR4.2 | requirements.md FR4.2 | 試行ごとの job の時刻・成否・失敗したテストを記録。プッシュの CI は参考 | 6回とも試行 1 で success、失敗 0。プッシュの CI は参考として別に記録 | test-results.md 3.1・3.2節 | build-and-test | Met |
| FR4.3 | requirements.md FR4.3 | 中央値が短い方（差 1 分未満なら 2）。選んだ値の最も遅い回が 42 分以内 | N=2 28分40秒・N=3 21分32秒、差 7分08秒で N=3。最も遅い回 27分07秒 | test-results.md 3.1節 | build-and-test | Met |
| FR4.4 | requirements.md FR4.4 | `IT_FORKS_DEFAULT` とコメント・`docs/verification.md` を結果に合わせる | 値は 3 のまま、コメントと文書に結果を書いた（`c59185b9`） | `.github/workflows/ci.yml`・`docs/verification.md` | build-and-test | Met |
| FR4.5 | requirements.md FR4.5 | 既定の値を変えたときにそのプッシュの CI を記録 | 値を変えていない。参考に `a65fb5af` のプッシュの CI を記録 | test-results.md 3.2・3.3節 | build-and-test | N/A |
| FR5.1・FR5.3 | requirements.md FR5.1・FR5.3 | Spring Boot・H2・JRE が変わったときに負荷の試験 | どれも変わらず FR5.2 に当たる（依頼者の決定 B2: A） | test-results.md 5節 | build-and-test | N/A |
| FR5.2 | requirements.md FR5.2 | 省いた理由を記録 | 版の前後の表と、Jackson を対象に入れない根拠（R-03）を書いた | test-results.md 5節、code-summary.md 2節 | build-and-test | Met |
| R-02 | Code Generation のレビュー R-02 | CI の OSV-Scanner 2.7.1 の結果 | プッシュの CI と比べの6回で `verify` が通り、High 以上なし | test-results.md 3.2節 | build-and-test | Met |
| B3 | 依頼者の決定 B3: A | 上げた otel-lgtm 0.40.0・collector 0.165.0 が起動する | Grafana の health 200、ダッシュボード 4・警報 17、版とダイジェストが一致 | test-results.md 4節 | build-and-test | Met |
| NFR1 | requirements.md NFR1 | 下限・物差し・除外を変えない | 変えていない。下限を満たす | test-results.md 1節 | build-and-test | Met |
| NFR2 | requirements.md NFR2 | OSV-Scanner で High 以上なし、ignore-scripts のまま | 手元 2.7.0・CI 2.7.1 とも High 以上なし | test-results.md 1・3.2節 | build-and-test | Met |
| NFR3 | requirements.md NFR3 | clean 付きの全件の `verify` と E2E 全体、実測で報告 | 全件の `verify` 14分36秒・E2E 298 件が通った | test-results.md 1・2節 | build-and-test | Met |
| NFR4 | requirements.md NFR4 | 画面の機能・API・DB スキーマを変えない | 変えていない。既存のテストが緑のまま | test-results.md 1・2節、code-summary.md | code-generation | Met |
| NFR5 | requirements.md NFR5 | 全件の `verify` と CI の job の時間を記録、42 分を超えたら諮る | 記録した。`e566faa4` のプッシュの CI が 46分41秒で超えたが、原因は cloc の apt-get でテストではない。依頼者に諮り B6: B（記録だけ） | test-results.md 3.2・6節 | build-and-test | Met |
| TP-COV-BE | team.md Testing Posture | 行 80%・分岐 70%、すべてのパッケージごと | 行 97.75%・分岐 91.52%、下回るパッケージ 0（最低 `config` の分岐 76.7%） | test-results.md 1節 | build-and-test | Met |
| TP-COV-FE | team.md Testing Posture | 行 80%・分岐 70% | 行 95.49%・分岐 91% | test-results.md 1節 | build-and-test | Met |
| TC-STRATEGY | Testing Contract strategy_volume | 要件ごとの単体テスト（Minimal） | 新しい振る舞いが無く、テストは足していない。既存の全件で確かめた | code-generation-plan.md、code-summary.md 7節 | code-generation | N/A |
| CI-BEFORE-MERGE | Testing Contract | CI での実行（CI Pipeline の段を流すとき） | この計画は CI Pipeline の段を流さない（統合の後の CI で確かめた） | aidlc-state.md | — | N/A |
| FR6.1〜FR6.3 | requirements.md FR6 | 配備・古いイメージの片付け・`main` への取り込み | 配備の段で行う | — | deployment-pipeline・deployment-execution | N/A |

## 2. 準備の状態

- ビルドの準備: できている。
- テストの準備: できている。
- 配備の準備: 依存の更新（Jackson 3.1.8・画面の依存など）で WAR とイメージの中身が変わるため、通常どおり配備の段を通す（FR6.1）。スキーマと `.env` の変更は無い。

## 3. 決まりとの差・残ること

- R-01（networknt のコメントの食い違い）は、依頼者の決定 B1・B4 でこの段で直した（`e566faa4`）。全件の `verify` と E2E はこの直しを含むコミットで流した。
- プッシュの CI の cloc の手順の遅れは、依頼者の決定 B6: B で記録だけにした（`ci.yml` は変えない）。同じ遅れが続くときは、別の Intent で cloc の入れ方を見直す。
- 負荷の試験は B2: A で省いた（FR5.2）。

## 次の配備へ引き継ぐこと

- 配備の段で、前の配備されていない Intent の「次の配備へ引き継ぐこと」の一覧を読んで手順に入れる（FR6.1）。
- 配備の後に、固定先から外れた古い版のイメージ（otel-lgtm 0.39.0・collector 0.164.0・Mailpit v1.33.0・postgres と mysql の前のダイジェストなど）と、戻し先より古い `mastersmith` のタグを、使うコンテナが 0 件であることを確かめて、承認を得てから消す（FR6.2）。
- `main` への取り込みの前に、前の取り込みの後のすべてのコミットの CI を試行ごとに確かめる（FR6.3）。`b4c8d77e` のプッシュの CI は cancelled（待ちの入れ替え）である。
