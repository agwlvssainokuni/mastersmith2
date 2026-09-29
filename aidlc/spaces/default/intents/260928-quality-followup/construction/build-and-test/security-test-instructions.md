# セキュリティの確かめの手順（Intent 260928-quality-followup）

この Intent の変更に関わるセキュリティの確かめを書く。道具と関門は既存のものを使い、変えていない（`team.md` の Code Style、要件の NFR4）。コード生成の記録は `aidlc/spaces/default/intents/260928-quality-followup/construction/code-generation/code-summary.md`。

## 関門（`./gradlew verify` の中）

| 道具 | 止める基準 | この Intent での確かめ |
|---|---|---|
| OSV-Scanner（`backend/gradle.lockfile`・`frontend/package-lock.json`・`vendor/make-you-chic-ui/package-lock.json`） | 実行時の依存の High 以上、開発時の画面の道具（`config/npm-build-tools.txt`）の High 以上、悪意のあるパッケージ | 依存の更新と Jackson 3.1.6 の後に `./gradlew osvScan --rerun` で流し直す |
| SpotBugs＋FindSecBugs | priority 1 と、`SQL_`・`PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION` | `verify` の `spotbugsGate` |
| Gitleaks | 漏えいの検出 | `verify` と pre-commit |

## この Intent に特有の確かめ

- **診断に秘密情報を出さない（NFR5）**: 診断の確かめ（`integration-test-instructions.md`）で出た失敗の文言とログに、数と固定の文だけが出て、接続先・利用者・メールアドレス・トークンが出ないことを目で確かめる。
- **公開の範囲（NFR5）**: バケットを足しても、Web に公開する窓口が health だけのまま（`ExposureIT`）。
- **E2E の報告（`project.md` の Testing Posture）**: `frontend/test-results/e2e-results.json` にメールアドレスとトークン（`eyJ` で始まる値）が含まれないことを文字列の検索で確かめる。
- **依存の更新の供給網**: 取り込んだ更新は lockfile どおりに入れる。サブモジュールは固定先だけを変え、中身は変えない（`project.md` の Forbidden）。
