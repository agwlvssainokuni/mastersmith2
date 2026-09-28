# 仮の目標（slo-config）

前の Intent の記録（`aidlc/spaces/default/intents/260922-auth-audit-base/operation/observability-setup/slo-config.md`）を正とします。仮の目標（稼働 99.5%・30 日など）は、配備先が決まったときに正式に決めます（project.md の Deployment）。

## 1. 今回の差

- この Intent では、新しい仮の目標を置いていません。
- 招待と登録の API の時間の目標（U3 の NFR6.1〜NFR6.5: 招待と送り直しは送信を含めて p95 5 秒、ほかは p95 1 秒、どれも同時 10 件）と、プリファレンス・パスワードの変更の目標（U2 の NFR6.1〜NFR6.4）の判定は、performance-validation の段が使い捨ての環境の k6 で行います。
- ダッシュボードの「API ごとの時間（95 パーセンタイル）」の線（1 秒・5 秒）は、運用の中で見る目安です。トレースから作る指標で、境目が 2 倍刻みと粗いため、目標の判定には使いません（`dashboards.md` 2節）。

## 2. 運用の中の判定（Unverified）

手元の監視を常に動かしていないため、次の目標の運用の中の判定は `Unverified` とし、feedback-optimization に引き継ぎます（project.md の Deployment の決まり。目標を緩めて満たしたことにはしない）。

| 目標 | 判定 | 基準の値（この段の確かめ、使い捨ての環境・同時 1 件） | 配備先が決まったときの測り方 |
|---|---|---|---|
| 画面の時間（U4〜U7 の NFR6.x） | Unverified | 配備したアプリでは測らない（`./gradlew e2eTest` の中でだけ測る設計） | 実際の利用者の画面の時間を測る仕組みを配備先で決める |
| 招待と登録の API の p95（U3 の NFR6.1〜NFR6.5）の運用の中の判定 | Unverified | 招待 0.563 秒・送り直し 0.038 秒・一覧 0.018 秒・取り消し 0.015 秒・リンクの確かめ 0.008 秒・登録の完了 0.486 秒（トレースから作る指標の p95、粗い境目の補間） | アプリの指標にバケットを出した後（次の Intent）、`http_server_requests` の p95 で見る |
| メールの送信の時間 | Unverified | 0.042 秒（p95） | 同上。`mastersmith.mail.send` にバケットを出す |
| 見た目の設定の API（U8 の NFR9.3 ほか） | Unverified | 要求の数・状態コードは既存の全体のパネルで見える | 同上 |
| ログイン・トークンの更新・確認用 API の p95（前からある目標） | Unverified（前からの欠けが今回分かった） | 前の Intent までの記録では式が `NaN` を返していた（バケットが無いため。`alarms.md` 2節） | 次の Intent でバケットを出し、警報3件を働かせる |

## Sources

- `aidlc/spaces/default/intents/260925-user-management/operation/observability-setup/observability-setup-questions.md`（決まっていること、確認済みの要約）
- 前の Intent の同じ段の記録: `aidlc/spaces/default/intents/260922-auth-audit-base/operation/observability-setup/slo-config.md`
- 各単位の基盤の設計の `monitoring-design.md`（U2〜U8）、`construction/u3-invitation/nfr-requirements/performance-requirements.md`、`construction/u2-user-preferences/nfr-requirements/performance-requirements.md`
- `operation/observability-setup/dashboards.md`・`alarms.md`（この段）
- 実行したコマンドの出力: `docker exec mastersmith-lgtm-1 curl -s localhost:9090/api/v1/query?...`

## Assumptions & Open Questions

None.
