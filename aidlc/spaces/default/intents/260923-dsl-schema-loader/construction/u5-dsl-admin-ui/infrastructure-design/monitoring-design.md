# Monitoring Design — U5 DSL の管理画面（u5-dsl-admin-ui）

| 対象 | 監視 | 理由 |
|---|---|---|
| 画面の操作の結果 | U4 の指標（`mastersmith.dsl.operation`）と既存の HTTP の指標（`http.server.requests`）で見る | 画面の操作は必ず U4 の API を通るため |
| 画面の側の指標・エラーの収集 | 足さない | 利用は管理者が数名で、画面の側の収集の仕組みは既存にも無い |
| 画面の時間（NFR1.18〜NFR1.20） | Build and Test の E2E で測って記録する（運用の監視にはしない） | 配備先が決まるまで運用の監視は手元だけ |

警報は足さない。
