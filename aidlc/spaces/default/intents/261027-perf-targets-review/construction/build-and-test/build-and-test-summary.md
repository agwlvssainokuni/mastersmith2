# Build and Test のまとめ（Intent 261027-perf-targets-review）

## 結論

- 緩めた新しい線（FR1）では、測り直した全項目が **Met**。新しい線で Not Met は 0 件。
- 前の線では 5 件が Not Met のまま（MySQL Contains、MariaDB の 3 場面、dslMixed memory.peak）。事実として並べて記録し、目標の緩め直しはしていない。
- `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` 成功（単体 2612 件・結合 1375 件、失敗 0。skip 10 件は DB の種類別の前提）。カバレッジはバックエンド 行 97.7%・分岐 91.4%、フロントエンド 行 95.06%・分岐 89.79%。
- E2E 256 件通過（Intent の最後の 1 回）。CI は develop の先頭 ab0d806 が 1 回目で成功（検査の job 31 分 56 秒、42 分の線の内）。
- 詳細は `test-results.md`（確認表）、`cross-unit-traceability.md`、`evidence/remeasure-results.md`。

## 実行した検査

| 検査 | 結果 |
|---|---|
| 測り直し（k6・dslMixed・対象DB の anon、配備アプリを 55 分 21 秒止めて実行） | 新しい線で全 Met |
| verify（cleanTest 付き、21 分 28 秒） | 成功 |
| thresholds-test.sh / dsl-mixed-memory-test.sh | 全件 ok |
| E2E（13.0 分） | 256 passed |
| CI（試行ごと） | 再実行なし、1 回目で成功 |
| 記録の機微な値の件数確認 | 0 件 |

## 計画との差・注意

- E2E の 1 回目は見本の PG のポートが閉じていて前提の確かめで失敗（テストは未実行）。手順どおり開けて流し直し、通過後に閉じた。
- 測り直しで配備アプリが一度試験用のタグで作り直された（約 1 分、中身は同一）。
- 作業ブランチの 5 コミットは未プッシュのため、CI の確認は develop への統合・依頼者のプッシュの後に行う。
- 対象DB の anon は読み取りの範囲の値。書き込みを含めた最大は今回未測定（前回値と範囲が違うと明記済み）。
- MariaDB の Contains・LastSorted は線に近い（余裕 256 ms・169 ms）。

## 次の段へ引き継ぐこと

1. 作業ブランチを squash で `develop` に統合（依頼者の承認、aidlc の未コミット変更を先にコミット）。
2. Deployment Pipeline・Execution: Q5: B に従い通常どおり配備（WAR は現行と同一の見込み）、スモークテスト、戻し方。
3. PR #80 を理由つきで閉じる（実行前に依頼者に確認）。`main` への fast-forward は依頼者の承認、`git push` は依頼者が行う。
