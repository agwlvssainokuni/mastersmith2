# Observability Requirements — U3 招待と登録の完了（u3-invitation）

U3 の観測の要件です。U3 は新しい指標・警報・ダッシュボードを足さず、既存の仕組み（HTTP の指標、構造化ログ、トレースID、監査ログ）と、一覧の送信の結果の上で見えるようにします（この段の設計の要点 23、依頼者の Looks correct）。手元の監視は grafana/otel-lgtm を compose の profile で見たいときだけ起動し、外部エクスポートは既定で無効です（`aidlc/spaces/default/memory/project.md` の Deployment、`team.md` の Deployment）。出典の略号は `performance-requirements.md` と同じ。

## 要件

| ID | 要件 | 確かめ方と持ち主の段 | 出典 |
|---|---|---|---|
| NFR6.8 | U3 の API の応答時間と状態コードは、既存の HTTP の指標（Micrometer の `http.server.requests`、`uri` と `status` のラベル）で取れる。新しい指標は足さない。`uri` のラベルは道の型（例: `/api/admin/invitations/{invitationId}/resend`）になり、招待の ID・トークン・メールアドレスを含まない | 手元の監視を起動し、指標とラベルの名前を実際に確かめる（observability-setup） | NFR6、要点 23、`project.md` の Corrections（式は起動して確かめる） |
| NFR6.9 | 新しい警報とダッシュボードは足さない。送信の失敗は一覧の sendResult（FAILED）で管理者が見つけて送り直す | 設計の判断の記録（本書）。既存の警報の式が変わらないことを確かめる（observability-setup） | NFR6、要点 23、BR4.5 |
| NFR2.3 | U3 のアプリのログは既存の決まりに従う: キーと値の構造化ログ、トレースID を含む、想定内の誤り（4xx）は WARN 以下でスタックトレースなし、想定外（5xx）は ERROR でスタックトレース付き、例外のログは変換する境界で1回だけ。出してよいのは invitationId・言語・送信の結果の種類（SENT・FAILED と U1 の失敗の種類）・状態・定期の削除で消した件数までとし、トークン・ハッシュ・URL・メールアドレス・パスワード・SMTP の応答を出さない（`security-requirements.md` の NFR1.5・NFR2.1・NFR2.2）。送信の失敗は U3 が invitationId と失敗の種類を1件のログに出す（U1 のログと重ねない作りの細部は NFR 設計） | 既存のログの形のテストと `*SecretLeakIT` の形のテスト（code-generation） | NFR2、BR9.4・BR11.1、`team.md` の Code Style、PM の Forbidden |
| NFR9.5 | 招待・送り直し・取り消し・登録の完了・登録の失敗は、監査ログに1件ずつ、必須の項目（eventType、result、FAILURE のとき failureReason、actorUserId（招待の管理は操作した管理者、登録は空）、targetInvitationId（登録の失敗は招待が見つかったときだけ）、登録の完了は targetUserId、sourceIp・userAgent・traceId、occurredAt）つきで記録される。拒否（400・404・409・503）・送信の失敗・リンクの確かめ・入力の誤り・期限切れの置き換えは記録しない。監査ログをアプリのログで代用しない | 結合テストで出来事ごとに項目を確かめる（code-generation） | NFR9、BR8.1〜BR8.4・BR8.6、`team.md` の Testing Posture（監査ログ）・Code Style |

## 何を見れば分かるか

| 知りたいこと | 見るもの |
|---|---|
| 招待の API・登録の完了の API が遅い・誤りが多い | `http.server.requests` の `/api/admin/invitations*`・`/api/registration/*` の応答時間と状態コード（NFR6.8） |
| 招待メールが送れていない | 招待の一覧の sendResult FAILED（PENDING を含む）と、アプリのログの送信の失敗（invitationId と失敗の種類）（NFR6.9・NFR2.3） |
| 誤ったトークンでの登録の完了が増えている（残る危険 R1、`security-requirements.md`） | 監査ログの `REGISTRATION_FAILED` を failureReason ごと（とくに `INVITATION_NOT_FOUND`）・sourceIp ごとに数える（NFR9.5） |
| だれがいつ招待・取り消し・登録したか | 監査ログの `INVITATION_ISSUED`・`INVITATION_RESENT`・`INVITATION_CANCELLED`・`REGISTRATION_COMPLETED`（NFR9.5） |
| 定期の削除が動いている・失敗している | アプリのログの INFO（消した件数）と ERROR（NFR2.3） |
| 監査の書き込みが失敗した・遅い | 既存のアプリのログの ERROR「監査イベントの記録に失敗しました」と WARN「監査イベントの記録に時間がかかりました」 |
| 接続プールが足りない | 既存の hikaricp の指標（負荷の試験では使い捨ての環境だけ公開、`reliability-requirements.md` の NFR5.3） |

## SLI・SLO

当面の配備先は開発者の PC 上のコンテナで、手元の監視を常に動かしていないため、SLO は決めない（U2 と同じ）。配備先が決まったときに、招待・登録の完了の応答時間（NFR6.1・NFR6.3〜NFR6.5 の値を候補にする）と成功の割合を SLI の候補として決め直す（`project.md` の Deployment）。
