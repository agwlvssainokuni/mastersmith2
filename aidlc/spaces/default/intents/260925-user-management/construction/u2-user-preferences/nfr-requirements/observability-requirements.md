# Observability Requirements — U2 利用者のプリファレンスとパスワードの変更（u2-user-preferences）

U2 の観測の要件です。U2 は新しい指標・警報・ダッシュボードを足さず、既存の仕組み（HTTP の指標、構造化ログ、トレースID、監査ログ）の上で見えるようにします（この段の設計の要点 18、依頼者の Looks correct）。手元の監視は grafana/otel-lgtm を compose の profile で見たいときだけ起動し、外部エクスポートは既定で無効です（`aidlc/spaces/default/memory/project.md` の Deployment、`team.md` の Deployment）。出典の略号は `performance-requirements.md` と同じ。

## 要件

| ID | 要件 | 確かめ方と持ち主の段 | 出典 |
|---|---|---|---|
| NFR6.8 | 3本の API の応答時間と状態コードは、既存の HTTP の指標（Micrometer の `http.server.requests`、`uri` と `status` のラベル）で取れる。新しい指標は足さない。`uri` のラベルは `/api/me/preferences`・`/api/me/password` の決まった値になり、利用者の識別を含まない | 手元の監視を起動し、指標とラベルの名前を実際に確かめる（observability-setup） | NFR6、要点 18、`project.md` の Corrections（式は起動して確かめる） |
| NFR6.9 | 新しい警報とダッシュボードは足さない。既存のログインの p95 の警報（`ms-login-p95`）は、ログイン・更新の応答の広げ（NFR6.5）の後もそのまま使う | 既存の警報の式を流し直して確かめる（observability-setup） | NFR6、要点 18 |
| NFR2.4 | U2 のアプリのログは既存の決まりに従う: キーと値の構造化ログ、トレースID を含む、想定内の誤り（4xx）は WARN 以下でスタックトレースなし、想定外（5xx）は ERROR でスタックトレース付き、例外のログは変換する境界で1回だけ。パスワード・ハッシュ・トークン・メールアドレスをログに出さない（NFR2.1・NFR2.2） | 既存のログの形のテストと `*SecretLeakIT` の形のテスト（code-generation） | NFR2、`team.md` の Code Style、PM の Forbidden |
| NFR9.5 | パスワードの変更の成功と今のパスワードの誤りは、監査ログに1件ずつ、必須の項目（eventType `PASSWORD_CHANGED`、result、FAILURE のとき failureReason `CURRENT_PASSWORD_MISMATCH`、actorUserId と targetUserId に本人、sourceIp・userAgent・traceId、occurredAt）つきで記録される。入力の誤りとプリファレンスの保存は記録しない。監査ログをアプリのログで代用しない | 結合テストで項目ごとに確かめる（code-generation） | NFR9、BR7.2、BR3.5、BR4.1、`team.md` の Testing Posture（監査ログ） |

## 何を見れば分かるか

| 知りたいこと | 見るもの |
|---|---|
| 3本の API が遅い・誤りが多い | `http.server.requests` の `/api/me/*` の応答時間と状態コード（NFR6.8） |
| 今のパスワードの誤りが続いている（残る危険 R1、`security-requirements.md`） | 監査ログの `PASSWORD_CHANGED`・`FAILURE`・`CURRENT_PASSWORD_MISMATCH` を利用者（actorUserId）ごとに数える（NFR9.5） |
| 監査の書き込みが失敗した・遅い | 既存のアプリのログの ERROR「監査イベントの記録に失敗しました」と WARN「監査イベントの記録に時間がかかりました」 |
| 接続プールが尽きかけている | 既存の hikaricp の指標とダッシュボードの「コネクションプールの待ち」 |

## SLI・SLO

配備先が決まるまで、手元の監視は常には動いていないため、SLO の判定は行いません（`project.md` の Deployment）。U2 の目標（NFR6.1〜NFR6.4）は、performance-validation の負荷の試験の値を基準の値として記録し、配備先が決まったときに `http.server.requests` の p95 で測る SLI にします。
