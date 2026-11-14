# Discovered Rules

> 依頼者が述べた固い制約だけを書く。面談の Q6 で依頼者が A と B の両方を選んだ2つを、文を変えずに置いた。今の `aidlc/spaces/default/memory/project.md` の Mandated・Forbidden にあるものは重ねて書かない。

## Mandated

- ALWAYS 内部DB を PostgreSQL で動かす経路の結合テストは、版を固定したイメージのコンテナで起動した実際の PostgreSQL で行い、H2 で代用しない

## Forbidden

- NEVER 内部DB の接続情報（パスワード・資格情報を含む JDBC の URL）をログ・監査ログ・エラー応答に含めず、環境変数（.env）以外から受け取らない
