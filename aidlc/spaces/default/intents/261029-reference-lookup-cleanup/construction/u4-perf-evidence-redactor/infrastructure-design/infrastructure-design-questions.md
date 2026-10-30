# Infrastructure Design の質問 — U4 u4-perf-evidence-redactor（Intent 261029-reference-lookup-cleanup）

この単位には、依頼者に尋ねる質問はありません。U4 は種類が `library` のため、この段の成果物は cicd-pipeline.md と traceability.json だけです。配備するものも、新しい基盤もありません。

## 決まっていること（質問しません）

- 伏せ字の道具は手元と CI で同じ形で動き、`./gradlew verify` の中の `perfEvidenceScan`（仮の名前）として `verifySecurity` に足す（NFR 設計の logical-components.md）。CI は同じ `verify` を呼ぶため、ワークフローのファイル（`.github/workflows/`）は変えない見込み。
- 新しい依存と、Node の版の変更は足さない。道具のテストは `frontendTest` の中で流れる（置き場と `vitest.config.ts` の扱いはコード生成の計画で明記する）。
- `evidence/` に置いた後の `gitleaks dir` は手順として README に書く（verify には入れない）。
