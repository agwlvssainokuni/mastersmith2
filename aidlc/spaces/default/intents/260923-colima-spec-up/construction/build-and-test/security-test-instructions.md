# 安全の検査の手順（security-test-instructions）

本 Intent は、コンテナと JVM の起動の設定（`Dockerfile`・compose）、確かめのスクリプト、文書の変更だけで、認証・認可・監査のプログラムは変えていない。安全の検査は、統合の前の1コマンドの検査に含まれる既存の検査を通すことと、差分の確認に絞る。

## 1. 実行のコマンド

```bash
# verify の安全の検査の段（SpotBugs＋FindSecBugs、OSV-Scanner、Gitleaks）を含む全体
./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify
```

## 2. 確かめること

| 検査 | 合格の条件 | 関係する要件・決まり |
|---|---|---|
| 秘密情報の検出（Gitleaks） | `no leaks found` | NFR5、project.md の Forbidden・Mandated |
| 静的解析（SpotBugs＋FindSecBugs） | 重大度 High の指摘が0件 | team.md の Code Style |
| 依存関係の脆弱性（OSV-Scanner） | 統合を止める基準に当たる指摘が0件。本 Intent で依存関係は変えていない | project.md の Mandated |
| 差分の確認 | 新しい環境変数（メモリの上限・JVM の引数）の値は秘密情報ではない。`.env.example` には値を書かずコメントの行だけを加えた。`.env` は開いていない | NFR5 |

## 3. 観点（STRIDE）

- **権限の昇格・改ざん（Elevation / Tampering）**: `ENTRYPOINT` を `sh -c` にしたが、コンテナは root 以外の利用者（10001）で動き、`MASTERSMITH_JAVA_OPTIONS` を渡せるのは `.env`・一時の `app.env` を書ける者（PC の利用者）だけである。`set -f` でファイル名の展開を止め、値は空白で区切るだけにしている。JVM の引数を渡せることは、今の `.env` で環境変数（署名鍵など）を渡せることと同じ信頼の範囲にある。
- **情報の漏えい（Information Disclosure）**: `JAVA_TOOL_OPTIONS` を使わないため、起動時に引数の値が「Picked up ...」としてログに出ることは無い。
- **サービスの妨害（Denial of Service）**: 本修正は、高い負荷でのメモリの上限による停止（F3）を、上限を環境変数で上げられるようにして和らげる。既定の 1g のままでは再び起きうることは、既知の制約として README に記録した（FR6.5）。
