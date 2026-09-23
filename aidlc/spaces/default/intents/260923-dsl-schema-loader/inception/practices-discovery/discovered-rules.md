# Discovered Rules

> 依頼者が Q9 で固い制約として選んだ4件（A・B・C・D）だけを書く。既存の `aidlc/spaces/default/memory/project.md` の `## Mandated`（8件）と `## Forbidden`（4件）は引き続き有効で、ここには重ねて書かない。承認されると、各節のコメント以外の行が日付つきで `project.md` に追記される。

## Mandated

- ALWAYS 対象DB の結合テストは、版を固定したイメージのコンテナで起動した実際の MySQL・MariaDB・PostgreSQL で行い、H2 などの別の DB やモックで代用しない
- ALWAYS 利用者が投入する DSL（YAML）は信頼できない入力として扱い、大きさ・入れ子の深さ・別名の数の上限を明示し、タグと任意の型の生成を拒否し、重複キーをエラーにする

## Forbidden

- NEVER 対象DB の接続情報（接続先・ユーザー名・パスワード・資格情報を含む JDBC の URL）を、生成した DSL・プレビューの応答・画面・ログ・監査ログ・エラー応答に含めず、画面・API・DSL から受け取らない（設定だけから受け取る）
- NEVER JDBC の例外や、YAML・JSON Schema の部品の例外のメッセージを、そのままエラー応答に含めない
<!-- 保留: 「NEVER 対象DB のスキーマやデータを変更しない」は、「適用」が対象DB に書き込むかが要件定義で決まるまで規則にしない -->
