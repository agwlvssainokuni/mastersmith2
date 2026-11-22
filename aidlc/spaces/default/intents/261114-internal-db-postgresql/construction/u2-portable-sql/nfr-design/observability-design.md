# 観測の設計 — u2-portable-sql

この単位が足す観測の口はありません。新しいログ・指標・警報・ダッシュボード・トレースの属性は足しません。

## 設計の決定

- **ログ**: 書き込みの手順（`KeyedUpsertExecutor`）と `UserRepositoryImpl` は、鍵の重なり・更新のやり直し・上限切れをログに出さない（NFR2.6）。失敗は、今までと同じ経路で（`GlobalExceptionHandler` と各機能の分類が、変換する境界で 1 回だけ、例外のクラス名だけを出す。`project.md` の Code Style）扱う。
- **指標・警報・ダッシュボード**: 既存のもの（`docker/monitoring/` の `hikaricp_connections_pending` の警報など）を書き換えない。内部DB の接続プール `mastersmith-db` の指標の名前・ラベルは、接続を借りる回数が増えないため変わらない（NFR4.8）。`docker/monitoring/` に差分が無いことを確かめる。
- **監査**: 作業ロールの保存・DSL 管理の監査の内容・タイミング（確定の後に記録）は変えない（NFR4.9）。確かめは、既存の監査の結合テスト（`AuditAuthenticationEventsIT`・DSL 管理・作業ロールの監査のテスト）が通ること。
- **ヘルスチェック**: `/actuator/health` の内部DB の確認（`TimeBoundedDbHealthIndicator`）は変えない。

## NFR 要件の審査の指摘への答え

- NFR4.8・NFR4.9 の由来: 上流の NFR4 は「PostgreSQL で上限なく待ち続けない」と「起動時の移行が接続ごとの待ちの上限で失敗しない」で、後者は U1 の持ち分（U2 は移行を持たない）。この 2 つの要件は、NFR4 の「運用の継続」（監視・監査を変えない）の考え方に基づく、この単位の追加の確かめで、上流の NFR4 の文言そのものからは派生しない（この設計の記録で、由来を「U2 の変更が既存の運用の口を変えないことの確かめ」と読み直す）。

## SLI・SLO

数値の SLO は、この Intent では置かない（配備先が決まっていないため）。確かめる項目:

| 項目 | 見方 |
|---|---|
| 内部DB の生死 | `/actuator/health` の UP・DOWN（変えない） |
| 接続プールの待ち | `hikaricp_connections_pending`（変えない） |
| 書き込みの失敗 | 今までと同じログ・応答（変えない） |

## 採らなかった形

| 形 | 理由 |
|---|---|
| 重なりの回数を指標にする | 稀な出来事で、既存の警報・ダッシュボードに足す理由が無い。ログに出すと値を含む文との境が増える |
| 重なりを DEBUG のログに出す | 出力に値が混ざる面が増える（NFR2.1）。確かめの価値より危険が大きい |

## Sources

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u2-portable-sql/nfr-requirements/observability-requirements.md`（NFR2.6・NFR4.8・NFR4.9）・`reviews/review-01.md`（R-02）
- `docker/monitoring/provisioning/alerting/mastersmith.yaml`（`hikaricp_connections_pending`）
- `aidlc/spaces/default/memory/project.md`（Code Style）

## Assumptions & Open Questions

None.
