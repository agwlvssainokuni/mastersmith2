# 監視の要件 — U1 u1-reference-read

出典: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/functional-design/functional-spec.md`・`aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/functional-design/rules.md`・`aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/requirements-analysis/requirements.md`

## 出典

- 要件 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/requirements-analysis/requirements.md`（NFR1・NFR2・NFR3、FR1.6・FR3.4）
- この単位の機能設計 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/functional-design/`（`rules.md` の BR1.7・BR8.7・BR10.2・BR10.3）
- この段の答え `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/nfr-requirements/nfr-requirements-questions.md`（「決まっていること」）
- 設定（読み取りだけ）: `docker/monitoring/provisioning/alerting/mastersmith.yaml`（`ms-biz-target-db` は `uri=~"/api/tables/.*"` の 503 を数える）、`docker/monitoring/dashboards/mastersmith-overview.json`（業務データの区画は `uri=~"/api/tables/.*"` で要求の数と p95 を出す）、`backend/src/main/resources/application.yaml`（`http.server.requests` のバケットの境界 100・250・500・1000・2000・5000 ms）
- `aidlc/spaces/default/memory/team.md`（構造化ログはキーと値、想定内の失敗は WARN 以下）と `project.md` の学び（警報とダッシュボードの式は書く前に起動して名前を確かめ、書いた後にすべての式を流す。p95 の式は `+Inf` 以外のバケットを確かめる。指標の送信の周期を複数またいで送る）

## ID の対応表

全体の表は `security-requirements.md` の「ID の振り方」にある。上流の要件に監視の NFR が無いため、意味の近い NFR に寄せた。

| 上流の ID | この単位での扱い | この単位の ID |
|---|---|---|
| NFR1 セキュリティ | ログの出し方（漏えいの側） | NFR1.15 |
| NFR2 性能 | p95 の区画 | NFR2.10 |
| NFR3 接続 | 503 とプールの待ちの区画と警報 | NFR3.8 |

## 要件

| ID | 要件 | 確かめ方 | 持ち主の段 |
|---|---|---|---|
| NFR1.15 | 参照先の読み取りの対象DB の失敗は、code に写す所で WARN を1行だけ、スタックトレースなしで出し、キーと値で次だけを出す: `purpose`（= `REFERENCE`）・`reason`・`sqlState`・`vendorCode`・`exceptionClass`（BR8.7）。名前・値・ラベル・接続先・例外のメッセージは出さない。読み取りは監査に残さない（BR1.7） | ログの JSON を読み、キーの名前と、値・メッセージの目印が出ていないことを、`ReferenceSecretLeakIT`（`security-requirements.md` の NFR1.5）の中で確かめる。監査の行が増えないこと | Code Generation |
| NFR2.10 | 手元の監視（grafana/otel-lgtm）のダッシュボードの業務データの区画に、新しい道 `POST /api/tables/reference/rows` が既存の式（`uri=~"/api/tables/.*"`）で入ることを確かめ、区画の説明に足す。警報は足さない。部分一致の線 3,000 ms はバケットの境界に無い（2,000 と 5,000 の間）ため、サーバーの側の p95 は参考とし、合否は k6 で判定する（`performance-requirements.md`） | 書く前に使い捨てのアプリを起動して、`uri` のラベルの値と、`+Inf` 以外のバケットの有無を確かめる。書いた後にすべての式を流す。要求は指標の送信の周期（1 分）を複数またいで、待ちを入れた少ない数で送る（`project.md`） | Observability Setup |
| NFR3.8 | 参照先の読み取りの 503（`TIMEOUT`・`UNAVAILABLE`）は、既存の警報 `ms-biz-target-db`（業務データの API の 503 が 5 分に1件以上）で拾う。定義の取得ごとの 503 も同じ道の 503 として数える。対象DB の読み取りのプールの待ちは既存の区画で見る | 使い捨ての環境で対象DB を止め、新しい道と定義の道の 503 で警報が firing になることを確かめる。`docker pause` では借りる待ちの `UNAVAILABLE` になり、`TIMEOUT` は問い合わせの最中に止めないと起きない（`project.md` の学び）ことを踏まえて確かめ方を選ぶ | Observability Setup |

## 補足

- 道は固定で、名前・値は本文で運ぶため、`uri` のラベルは1つ増えるだけで、参照先ごとに系列が増えない。参照先ごとの遅さは区画では見分けない（受け入れた制約）。
- 3,000 ms のバケットを足すかは、系列の数を絞る既存の決め（境界の数だけ）に関わるため、この単位では足さない。足すなら Observability Setup で依頼者に諮る。
