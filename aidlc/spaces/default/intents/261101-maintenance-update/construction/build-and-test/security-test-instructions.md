# 安全の検査の手順 — Build and Test（Intent 261101-maintenance-update）

要件 NFR2（統合の前に OSV-Scanner で High 以上が出ない・`ignore-scripts`・秘密と行の値を記録に含めない）と FR10.3（k6 の記録の伏せ字）。どれも既存の検査で確かめる。

## 1. 1コマンドの検査の中の安全の検査

`./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（`build-instructions.md` 3節）が次を流す。どれかが落ちれば `verify` が失敗する。

| 検査 | 合格 |
|---|---|
| `spotbugsGate`（SpotBugs 4.10.5 ＋ FindSecBugs） | priority 1 と `SQL_`・`PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION` の指摘 0。除外の設定（`backend/config/spotbugs-exclude.xml`）は緩めていない |
| `gitleaksScan`（Gitleaks 8.30.1） | 履歴に秘密 0 |
| `perfEvidenceScan` | `aidlc/**/evidence/` に JWT の形 0 |
| `osvScan`（OSV-Scanner 2.7.0） | 重大度 High 以上 0（frontend の実行時の依存・成果物を作る道具・`MAL-`）。Moderate 3件は持ち越し（`build-and-test-summary.md`） |

## 2. 依存の入れ方

- `frontend/.npmrc` の `ignore-scripts=true` のまま。`vendor/make-you-chic-ui` の `hasInstallScript` の付いた項目は fsevents の1つのまま（固定先の更新の記録 `generation-notes.md`）。

## 3. 負荷の試験の記録

- 記録（`evidence/`）に置くのは参照の場面の summary json だけ。`node perf/redact-k6-evidence.mjs` で伏せてから置き、`--scan`・`gitleaks dir --redact --no-banner --config .gitleaks.toml --exit-code 1`・`eyJ` の検索がどれも 0 件であること（`performance-test-instructions.md` 3節）。
- 使い捨てのアプリのログと k6 の出力は記録に置かず、試験用の利用者の名前・行の値・`eyJ` の件数だけを数える。
- この段の Markdown の記録を書いた後に、記録の置き場の全体に `gitleaks dir` を流す。

## 4. E2E の報告

`integration-test-instructions.md` 2節の文字列の検索。

## Sources

- 要件 NFR2・FR10.3
- `docs/verification.md`・`perf/README.md` の「記録（evidence/）に置く前の伏せ字」
- `aidlc/spaces/default/memory/team.md`（静的解析とセキュリティ検査）・`project.md`（Forbidden）

## Assumptions & Open Questions

None.
