# Infrastructure Design の質問 — U2 u2-reference-write-check（Intent 261029-reference-lookup-cleanup）

この単位には、依頼者に尋ねる質問はありません。U2 は新しい設定・接続・コンテナを足さず、基盤の論点は U1 と NFR 設計で決まっています。

## 決まっていること（質問しません）

- 配備先は開発者の PC 上のコンテナのまま。クラウドの基盤は作らない。
- 保存前の確かめの全体の上限は、U1 と同じ設定 `MASTERSMITH_TARGET_DB_REFERENCE_READ_TOTAL`（既定 10 秒）を使う（NFR 設計の Q2: A）。問い合わせ 1 回の上限も U1 の `MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_REFERENCE`。U2 だけの設定は足さない。
- 書き込みの接続と資格情報は既存のもの（`MASTERSMITH_TARGET_DB_WRITE_*`）を使い、書き込みのアカウントの権限は変えない。
- 監視は、U1 の基盤の設計の決定（Q2: A）のとおり、既存の警報 `ms-biz-target-db` が作成・更新の道の 503 も数える。新しい警報は足さない。
- 性能の試験の新しい場面 `refCreateChecked`（仮）は、U1 の種と DSL の変更の後に足す（NFR 要件）。対象DB のコンテナのメモリの値は変えない見込みで、種を入れた後に測る。
- CI のワークフローは変えない。U2 は `businessdata` の `src/main` に触れるため、統合の前に E2E を流す（`team.md`）。
