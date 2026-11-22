# 観測の要件 — u2-portable-sql

この単位が足す観測の口はありません。新しいログ・指標・警報・ダッシュボード・トレースの属性は足しません。上流の NFR には観測だけを扱う項目が無いため、安全な出力（NFR2）と信頼性（NFR4）の中の観測に当たる要件だけを、ここに集めて書きます（`NFR2.x`・`NFR4.x` の番号は、それぞれの文書の ID と同じ意味）。

## 要件

| ID | 要件 | 確かめ方 | 由来 |
|---|---|---|---|
| NFR2.6 | 書き込みの手順は、鍵の重なり・やり直しの回数・上限切れをログに出さない（値を含まない出力でも、新しい口を足さない）。失敗は、今までと同じ経路（`GlobalExceptionHandler`・各機能の分類）で、例外のクラス名だけが出る。 | 単体テストで、重なった場合にも出力が増えないこと | 要件 NFR2、`project.md` の Code Style（例外のログは変換する境界で 1 回だけ） |
| NFR4.8 | 既存の指標・警報・ダッシュボード（`docker/monitoring/`）は変えない。内部DB の接続プール `mastersmith-db` の指標の名前・ラベルも変わらない（接続を借りる回数が増えないため）。 | `docker/monitoring/` に差分が無いこと | 要件 NFR4（運用の継続） |
| NFR4.9 | 監査の記録（作業ロールの保存の監査・DSL 管理の監査）の内容・タイミング（確定の後に記録）は変えない。書き込みの手順の変更が、監査の行の件数・種類を変えない。 | 既存の監査の結合テスト（`AuditAuthenticationEventsIT`・`DslManage` の監査のテストなど）が通ること | 要件 NFR4、`checklist-auth-audit.md`（監査の決まり） |

## SLI・SLO

数値の SLO は、この Intent では置きません（配備先が決まっていないため）。確かめる項目: 既存のヘルスチェック（`/actuator/health`）と接続プールの待ち（`hikaricp_connections_pending`）が、この単位の変更で変わらないこと。

## Sources

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/requirements-analysis/requirements.md`（NFR2・NFR4）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u2-portable-sql/functional-design/rules.md`（BR2.4・BR2.7）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/nfr-requirements/observability-requirements.md`
- `aidlc/spaces/default/memory/project.md`（Code Style）・`aidlc/spaces/default/knowledge/aidlc-shared/checklist-auth-audit.md`

## Assumptions & Open Questions

None.
