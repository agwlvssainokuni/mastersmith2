# 観測の要件 — u1-internal-db-platform

この単位が足す観測の口は、起動時の失敗のログと、起動のログの 1 行、ヘルスチェックです。指標・警報・ダッシュボードは、接続プールの共通の指標を使い回し、この単位では足しません。

## 要件

| ID | 要件 | 確かめ方 | 由来 |
|---|---|---|---|
| NFR2.2 | 起動時の失敗は、ERROR の構造化ログ 1 件で出す。項目は failureKind・exceptionTypes・sqlState・migrationVersion・settingName・supportedKinds だけ。キーと値（SLF4J のキー・値 API）で渡し、文字列の連結にしない。 | 単体テストで、項目の形と、漏れの無いこと（セキュリティの要件の NFR2.2 と同じ） | BR5.5、`team.md` の Code Style |
| NFR4.10 | 起動のログに、内部DB の種類だけを INFO で 1 行出す。 | 信頼性の要件の NFR4.10 と同じ | 新しい要件 |
| NFR4.5 | ヘルスチェックは、内部DB の種類によらず、届けば UP・届かなければ DOWN。 | 信頼性の要件の NFR4.5 と同じ | BR3.6 |
| NFR4.11 | 内部DB の接続プール（名前 `mastersmith-db`）の指標（`hikaricp_connections_*`）は、PostgreSQL のときも同じ名前・同じラベルで出る。既存の警報（`hikaricp_connections_pending` を使う式）とダッシュボードを、この単位で書き換えない。 | PostgreSQL の結合テストで、`mastersmith-db` の指標が出ること（BR7.3 の外。U3 が持つ確かめを、この単位のテスト基盤で流せる形にする）。`docker/monitoring/` の差分が無いこと | 警報の式（`docker/monitoring/provisioning/alerting/mastersmith.yaml`） |
| NFR4.12 | `TraceAspect` の TRACE のログに、内部DB の接続先・利用者名・パスワードを出さない。接続先を扱う部品は層の外に置く（NFR2.4）。 | 境界テストと、TRACE を有効にした起動で、ログに値が出ないこと | BR5.6、`project.md` の Code Style |

## 警報・ダッシュボード

- 新しい警報は足さない。接続プールの待ちの警報（既存）が、PostgreSQL のときも同じ式で効く（NFR4.11）。
- 起動時の失敗は、アプリが起動しないため警報の対象にならない。運用者がログで見る（信頼性の要件の「起動の失敗と戻しの扱い」）。
- 運用の道具のうち H2 に固有のもの（`docker/hikari-pool.sh` の compact・H2 のファイルの複写）は、PostgreSQL のときは使わないことを U4 の文書に書く（規模の要件の NFR7.5）。

## SLI・SLO

この Intent では数値の SLO を置きません（配備先が決まっていないため）。確かめる項目だけ置きます。

| 項目 | 見方 |
|---|---|
| 内部DB の種類 | 起動のログの INFO 1 行（NFR4.10） |
| 起動時の失敗の原因 | ERROR 1 件の failureKind（NFR2.2） |
| 内部DB の生死 | `/actuator/health` の UP・DOWN（NFR4.5） |
| 接続プールの待ち | `hikaricp_connections_pending`（NFR4.11） |

## Sources

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/functional-design/rules.md`（BR3.6・BR5.5・BR5.6）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/functional-design/entities.md`（ENT-005）
- `docker/monitoring/provisioning/alerting/mastersmith.yaml`（`hikaricp_connections_pending` の式）
- `aidlc/spaces/default/memory/team.md`（Code Style: 構造化ログのキーと値）
- `aidlc/spaces/default/memory/project.md`（Code Style: `TraceAspect`）

## Assumptions & Open Questions

- [assumption] 接続プールの指標は、接続先の種類によらず同じ名前で出る（HikariCP と Micrometer の共通の指標のため）。PostgreSQL のときに出ることは、結合テストで 1 度確かめる（NFR4.11）。
