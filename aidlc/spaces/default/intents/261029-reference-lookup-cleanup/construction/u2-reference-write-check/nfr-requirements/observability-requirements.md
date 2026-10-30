# 監視の要件 — U2 u2-reference-write-check

出典: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/functional-design/functional-spec.md`・`aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/functional-design/rules.md`・`aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/requirements-analysis/requirements.md`

## 出典

- 要件 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/requirements-analysis/requirements.md`（NFR1・NFR2・NFR3）
- この単位の機能設計（`rules.md` の BR4.5・BR5・BR7.1、`functional-spec.md` の 9節「ログの目的の値」）
- U1 の監視の要件 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/nfr-requirements/observability-requirements.md`（ログの項目と警報の扱いをそろえる）
- 手元の監視の決まり（`docker/monitoring/` の警報 `ms-biz-target-db` とダッシュボードの業務データの区画）と `project.md` の学び（式は書く前に起動して名前を確かめる、要求は周期をまたいで少なく送る）

## ID の対応表

全体の表は `security-requirements.md` の「ID の振り方」にある。

| 上流の ID | この単位での扱い | この単位の ID |
|---|---|---|
| NFR1 セキュリティ | ログの出し方（漏えいの側） | NFR1.32 |
| NFR2 性能 | p95 の区画 | NFR2.29 |
| NFR3 接続 | 503 の警報とプールの待ちの区画 | NFR3.28 |

## 要件

| ID | 要件 | 確かめ方 | 持ち主の段 |
|---|---|---|---|
| NFR1.32 | 保存前の段の対象DB の失敗は、code に写す所で WARN を1行だけ、スタックトレースなしで出し、キーと値で次だけを出す: 目的（`REFERENCE` 系。値の名前はコード生成で決める）・`reason`・`sqlState`・`vendorCode`・`exceptionClass`（BR4.5・BR7.1）。参照の値・行の値・カラムの名前の値・接続先・例外のメッセージは出さない。参照の確かめの拒否（409）は既存の業務の誤りと同じ扱い（WARN 以下・スタックトレースなし）で、値を出さない。確かめのための読み取りは監査に残さない（BR5.3） | ログの JSON を読み、キーの名前と、値・メッセージの目印が出ていないことを、`security-requirements.md` の NFR1.25 の漏えいのテストの中で確かめる | Code Generation |
| NFR2.29 | 作成・更新の道（`/api/tables/rows`）は既存のダッシュボードの業務データの区画（`uri=~"/api/tables/.*"`）に入っているため、新しい区画・警報は足さない。参照の確かめで作成の時間が伸びたかは、サーバー側の p95 を参考に見て、合否は k6 で判定する（`performance-requirements.md` の NFR2.21） | Observability Setup で、使い捨てのアプリに参照つきの作成を待ちを入れた少ない数で送り、区画に入ることを確かめる（`+Inf` 以外のバケットの有無も確かめる） | Observability Setup |
| NFR3.28 | 保存前の確かめの 503（`TIMEOUT`・`UNAVAILABLE`）は、既存の警報 `ms-biz-target-db`（業務データの API の 503 が 5 分に1件以上）で拾う。新しい警報は足さない。読み取りと書き込みのプールの待ちは既存の区画で見る | 使い捨ての環境で対象DB を止め、参照つきの作成の 503 で警報が firing になることを確かめる。`docker pause` では借りる待ちの `UNAVAILABLE` になり、`TIMEOUT` は問い合わせの最中に止めないと起きない（`project.md` の学び）ことを踏まえて確かめ方を選ぶ | Observability Setup |

## 補足

- SLI・SLO は新しく置かない（`reliability-requirements.md` の補足）。
- 監査は、参照の確かめの拒否を既存の作成・更新の出来事に結果 `FAILURE`・理由 `BUSINESS_REFERENCE_NOT_FOUND` で残す（`security-requirements.md` の NFR1.30）。監査の行は監視の代わりにしない。
