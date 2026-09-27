# Observability Design — U2 利用者のプリファレンスとパスワードの変更（u2-user-preferences）

U2 の観測の設計です。承認済みの `construction/u2-user-preferences/nfr-requirements/observability-requirements.md`（NFR6.8・NFR6.9・NFR2.4・NFR9.5）を満たす作りを決めます。U2 は新しい指標・警報・ダッシュボードを足さず、既存の仕組み（Micrometer の HTTP の指標、SLF4J のキーと値の構造化ログ、Micrometer Tracing のトレースID、監査ログ）の上で見えるようにします。出典の略号は `performance-design.md` と同じ。

## 1. 指標（NFR6.8・NFR6.9）

- 3本の API は Spring MVC の既存の観測で `http.server.requests`（Prometheus では `http_server_requests_seconds_*`）に入る。`uri` のラベルはパスの型（`/api/me/preferences`・`/api/me/password`）になり、利用者の識別を含まない（URL に利用者 ID を入れない作りのため、NFR4.3 とも合う）。`status` は 200・204・400・401・500。
- 新しい指標は足さない。パスワードの変更の成功と誤りの数は、指標ではなく監査ログで数える（3節）。400 の中の VALIDATION_FAILED と PASSWORD_CURRENT_MISMATCH は指標では分けない（code のラベルを足すと、既存の指標の形を変えるため）。
- 警報・ダッシュボードは足さない。既存のログインの p95 の警報（`docker/monitoring/provisioning/alerting/mastersmith.yaml` の `ms-login-p95`）は、ログイン・更新の応答の広げ（NFR6.5）の後もそのまま使う。
- 確かめ（observability-setup）: 手元の監視（grafana/otel-lgtm、compose の profile）を起動し、3本を呼んで `uri`・`status` のラベルの実際の名前と値を確かめ、既存の警報の式を流し直す（`project.md` の Corrections「式は起動して確かめる」）。

## 2. アプリのログ（NFR2.4）

| 場面 | 出すログ | 出す場所 |
|---|---|---|
| 入力の誤り（400 VALIDATION_FAILED） | WARN 以下1回、スタックトレースなし。code だけで、`fieldErrors` の中身（項目・理由）は載せない | 既存の `GlobalExceptionHandler` の業務エラーの変換 |
| 今のパスワードの誤り（400 PASSWORD_CURRENT_MISMATCH） | 同上 | 同上 |
| 本人がいない（401） | 同上 | 同上 |
| 想定外（500） | ERROR 1回、スタックトレース付き。内部の例外のメッセージは応答に載せない | 同上 |
| 監査の書き込みの失敗・遅い書き込み | 既存の ERROR「監査イベントの記録に失敗しました」・WARN「監査イベントの記録に時間がかかりました」（200 ミリ秒超） | 既存の `AuditEventListener` |

- U2 の業務処理・画面入出力は、成功の場面で新しいログを出さない（監査ログと二重にしない。監査記録をログで代用しない）。
- どのログにもパスワード（3つの値）・ハッシュ・トークン・メールアドレスを載せない。既存のログの形（キーと値、トレースID を含む）はそのまま（`security-design.md` 4節）。
- 監査の失敗の ERROR の項目に targetUserId・targetInvitationId を足す（記録しようとした全項目を載せる既存の決まりに合わせる）。PASSWORD_CHANGED の失敗の ERROR には、今の組み立て（actorUserId があると DSL の項目も並べる）により dslHash などが空で並ぶ。載る値は空だけで害が無いため、組み立ての整理はコード生成に任せる。

## 3. 監査ログ（NFR9.5）と総当たりの見つけ方

- パスワードの変更の成功と今のパスワードの誤りを、PASSWORD_CHANGED で1件ずつ記録する（`reliability-design.md` 1節）。必須の項目は event_type・result・failure_reason（FAILURE のとき CURRENT_PASSWORD_MISMATCH）・actor_user_id・target_user_id・source_ip・user_agent・trace_id・occurred_at。入力の誤りとプリファレンスの保存は記録しない。
- 監査の行の trace_id は、同じ要求のアプリのログとトレースに同じ値で出る。監査の行から要求のログ（4xx の WARN）へたどれる。
- 残る危険 R1（今のパスワードの総当たり、`security-design.md` 7節）の見つけ方: 手元で内部DB に次の問い合わせを流し、利用者ごとの誤りの件数を見る（読み取りだけの調べ物。定期の実行や警報にはしない）。

```sql
SELECT actor_user_id, COUNT(*) AS failures, MIN(occurred_at), MAX(occurred_at)
  FROM audit_events
 WHERE event_type = 'PASSWORD_CHANGED' AND result = 'FAILURE'
   AND failure_reason = 'CURRENT_PASSWORD_MISMATCH'
   AND occurred_at >= :since
 GROUP BY actor_user_id ORDER BY failures DESC
```

- 確かめ（code-generation）: 結合テストで、成功・誤りのそれぞれで必須の項目が入り、入力の誤りと保存では件数が増えないこと。問い合わせは observability-setup で実際に流して列の名前を確かめ、手元の監視の手順（README）に置く。

## 4. トレース

- 既存の Micrometer Tracing のまま。U2 は新しい span を作らず、span の属性に利用者の値（氏名・メールアドレス・パスワード）を足さない。bcrypt の時間は HTTP の span の中に含まれ、別の span に分けない（原因の切り分けは p95 と CPU の実測で足りるため）。
- 外部エクスポートは既定で無効のまま（既存の配備の設定）。

## 5. SLI・SLO

- 配備先が決まるまで SLO の判定はしない（`project.md` の Deployment）。U2 の目標（NFR6.1〜NFR6.4）は、performance-validation の値を基準の値として記録する。配備先が決まったら、`http.server.requests` の `uri` ごとの p95（`/api/me/preferences` は GET・PUT、`/api/me/password` は status 204 と 400 を分けて）を SLI にする。
