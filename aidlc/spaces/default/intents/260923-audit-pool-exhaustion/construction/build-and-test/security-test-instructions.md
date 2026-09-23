# 安全の検査の手順（security-test-instructions）

本 Intent は設定の変更（接続プールの上限）とテスト・文書の追加だけで、認証・認可・監査のプログラムは変えていない。安全の検査は、統合の前の1コマンドの検査に含まれる既存の検査を通すことと、差分の確認に絞る。

## 1. 実行のコマンド

```bash
# verify の安全の検査の段（SpotBugs＋FindSecBugs、OSV-Scanner、Gitleaks）を含む全体
./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify

# 安全の検査の段だけを動かすとき
./gradlew verifySecurity
```

## 2. 確かめること

| 検査 | 合格の条件 | 関係する要件・決まり |
|---|---|---|
| 秘密情報の検出（Gitleaks、リポジトリの履歴全体） | `no leaks found` | NFR4、project.md の Forbidden・Mandated |
| 静的解析（SpotBugs＋FindSecBugs） | 重大度 High の指摘が0件 | team.md の Code Style |
| 依存関係の脆弱性（OSV-Scanner） | 統合を止める基準（実行時の依存の High 以上など）に当たる指摘が0件。本 Intent で依存関係は変えていない | project.md の Mandated |
| 差分の確認 | 新しい設定の値（プールの上限）は秘密情報ではない。`.env.example` には値を書かずコメントの行だけを加えた。テストの秘密情報の役の値はソースに書いていない | NFR4 |
| 監査の決まりの維持 | 監査の既存の結合テスト（失敗の場合を含む: `AuditWriteFailureIT`・`AuditRollbackIT`・`AuditSecretLeakIT` など）が変更なしで通る | FR4.1、project.md の Mandated（認証・監査の変更には失敗の場合のテストを含める） |

## 3. 観点（STRIDE の Denial of Service）

- 本修正は、同時のログインで監査の記録が欠ける（否認の防止の弱まり）状態を、プールの上限の引き上げで和らげるものである。
- 同時の要求がプールの上限（30）に達すると再び起きうる。これは既知の制約として README に記録した（FR5.1）。Tomcat のスレッドの上限の設定は範囲の外（要件の「範囲の外」）。
