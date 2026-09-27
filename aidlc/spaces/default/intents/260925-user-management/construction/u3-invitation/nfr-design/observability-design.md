# Observability Design — U3 招待と登録の完了（u3-invitation）

U3 の観測の設計です。承認済みの `construction/u3-invitation/nfr-requirements/observability-requirements.md`（NFR6.8・NFR6.9・NFR2.3・NFR9.5）を満たす作りを決めます。U3 は新しい指標・警報・ダッシュボードを足さず、既存の仕組み（HTTP の指標、構造化ログ、トレースID、監査ログ）と U1 の送信の観測の上で見えるようにします。手元の監視は grafana/otel-lgtm を compose の profile で見たいときだけ起動し、外部エクスポートは既定で無効のままです。出典の略号は `performance-design.md` と同じ。

## 1. 指標とトレース（NFR6.8・NFR6.9）

| 見るもの | 仕組み | U3 がすること |
|---|---|---|
| U3 の API の応答時間と状態コード | 既存の Micrometer の `http.server.requests`（`uri`・`status`・`method` のラベル） | 何も足さない。`uri` は道の型（例: `/api/admin/invitations/{invitationId}/resend`・`/api/registration/complete`）になり、招待の ID・トークン・メールアドレスを含まない。コントローラーの道を `@PathVariable` の型で書き、トークンを道に置かない（`security-design.md` 4節）ことで守る |
| 招待メールの送信の時間と結果 | U1 の Observation `mastersmith.mail.send`（時間の指標と span、タグは `mail.template`・`mail.language`・`mail.outcome`・`mail.failure.kind`） | 何も足さない。送信は要求の中で呼ぶため、送信の span は招待・送り直しの要求の span の子になる。`mail.template` は `invitation` |
| 接続プール | 既存の hikaricp の指標 | 何も足さない。負荷の試験では使い捨ての環境だけ公開する（`reliability-design.md` 5節） |

- U3 は自前の Observation と指標を作らない。U1 の送信の観測と HTTP の指標で、招待・送り直しの時間のうち送信にかかった分を分けて見られる。
- 新しい警報とダッシュボードは足さない（NFR6.9）。送信の失敗は、管理者が一覧の sendResult（FAILED）で見つけて送り直す。既存の警報の式が変わらないことは observability-setup で確かめる。
- `uri` のラベルの値は、observability-setup で手元の監視を起動し、実際の名前を確かめる（`project.md` の Corrections）。

## 2. アプリのログ（NFR2.3、Q5 A）

### 2.1 送信の失敗

- 失敗の WARN は U1 の1件だけにする。U1 は送信ごとにログを1件出し（成功 INFO・失敗 WARN、項目は templateId・language・failureKind・exceptionType、トレースIDは送信の span のもの）、どの招待かは持たない。
- U3 は、送信の結果を記録した後、**失敗のときだけ** INFO のログを1件出す。項目は SLF4J のキーと値で `invitationId`・`operation`（`INVITE`・`RESEND`）・`failureKind`（U1 の失敗の種類）。成功では U3 のログを出さない（U1 の INFO で足りる）。
- 2つのログは同じ要求のトレースIDでつながる（U1 の行のスパンIDは送信の span、U3 の行は要求の span）。ログの設定（`logback-spring.xml` の MDC は traceId・spanId だけ）と U1 は変えない。

```java
if (result instanceof MailSendResult.Failed failed) {
    LOGGER.atInfo()
            .addKeyValue("invitationId", issued.invitationId())
            .addKeyValue("operation", operation)            // INVITE・RESEND
            .addKeyValue("failureKind", failed.kind())
            .log("招待メールを送れませんでした。一覧から送り直せます");
}
```

### 2.2 そのほかのログ

| 場面 | レベル | 項目 |
|---|---|---|
| 起動時のベース URL が不正（形が合わない） | WARN 1件 | 設定の項目の名前だけ（値は出さない）（BR1.3） |
| 起動時の招待の使える設定 | INFO 1件 | enabled と unavailableReasons（BASE_URL_NOT_CONFIGURED・SMTP_NOT_CONFIGURED） |
| 想定内の業務の誤り（400・404・409・503） | 既存の `GlobalExceptionHandler` の境界で WARN 1回、スタックトレースなし | 既存の項目（code・状態コード）。`fieldErrors` の中身・招待のメールアドレスを載せない |
| 想定外の誤り（500、行の排他の時間切れ・結果の記録の失敗・送信の入口の確かめ） | 既存の境界で ERROR 1回、スタックトレース付き | 既存の項目。例外の文言にトークン・URL・メールアドレスを入れない |
| 定期の削除 | INFO（消した件数 `deleted`）、失敗は ERROR（スタックトレース付き） | `reliability-design.md` 4節 |
| 監査の書き込みの失敗・遅さ | 既存の ERROR「監査イベントの記録に失敗しました」・WARN「監査イベントの記録に時間がかかりました」 | 既存の項目（U2 が足した対象の2列を含む） |

- 出してよい値は invitationId・言語・状態・送信の結果の種類・failureKind・消した件数まで。トークン・ハッシュ・URL・メールアドレス・パスワード・SMTP の応答を出さない（`security-design.md` 5節・8節）。例外のログは変換する境界で1回だけ。
- 確かめ: 既存のログの形のテストと `InvitationSecretLeakIT`（`security-design.md` 5節）。送信の失敗で U3 の INFO が1件だけ出て WARN を出さないこと、項目が3つであることをログの取り込み（既存のテストのログの受け手）で確かめる。

## 3. 監査ログ（NFR9.5）と残る危険 R1 の見つけ方

- 招待・送り直し・取り消し・登録の完了・登録の失敗は、監査ログに1件ずつ必須の項目つきで記録される（`reliability-design.md` 7節）。拒否（400・404・409・503）・送信の失敗・リンクの確かめ・入力の誤り・期限切れの置き換えは記録しない。監査ログをアプリのログで代用しない。
- 確かめ（結合テスト）: 出来事ごとに eventType・result・failureReason（FAILURE のとき）・actorUserId（招待の管理は管理者、登録は空）・targetInvitationId（登録の失敗は招待が見つかったときだけ）・targetUserId（登録の完了）・sourceIp・userAgent・traceId・occurredAt。

### 3.1 誤ったトークンの登録の完了の増え（R1）

公開の API に回数の制限が無く、監査の急な増えに自動で気づく仕組み（警報）も無い（`security-design.md` の R1）。管理者は、内部DB の監査の表を次の形の問い合わせで数えて見つける。時間の幅は見たい期間に合わせる。

```sql
SELECT failure_reason, source_ip, COUNT(*) AS failures
  FROM audit_events
 WHERE event_type = 'REGISTRATION_FAILED'
   AND occurred_at >= :since
 GROUP BY failure_reason, source_ip
 ORDER BY failures DESC;
```

- とくに `INVITATION_NOT_FOUND`（存在しない・改ざん・形の誤りのトークン）が1つの送り元から多いときは総当たりの疑いとする。問い合わせを流す手順（アプリを止めずに内部DB を読む方法を含む）は、observability-setup で既存の監査の確かめの手順に合わせて書く。
- 表と列の名前（`audit_events`・`event_type`・`failure_reason`・`source_ip`・`occurred_at`）は既存の V4 のとおり。書いた後に実際に流して確かめる（`project.md` の Corrections）。

## 4. 何を見れば分かるか

| 知りたいこと | 見るもの |
|---|---|
| 招待・登録の完了の API が遅い・誤りが多い | `http.server.requests` の `/api/admin/invitations*`・`/api/registration/*` |
| 招待・送り直しの遅さが送信のせいか | U1 の `mastersmith.mail.send` の時間と、要求の span の中の送信の span |
| 招待メールが送れていない | 一覧の sendResult FAILED（PENDING を含む）、U1 の WARN と U3 の INFO（invitationId） |
| 誤ったトークンの登録の完了が増えている | 3.1 の問い合わせ |
| だれがいつ招待・取り消し・登録したか | 監査ログの INVITATION_ISSUED・INVITATION_RESENT・INVITATION_CANCELLED・REGISTRATION_COMPLETED |
| 定期の削除が動いている・失敗している | INFO の消した件数と ERROR |
| 接続プールが足りない | hikaricp の指標（負荷の試験の使い捨ての環境だけ） |

## 5. SLI・SLO

当面の配備先は開発者の PC 上のコンテナで、手元の監視を常に動かしていないため、SLO は決めない（承認どおり、U2 と同じ）。配備先が決まったときに、招待・登録の完了の応答時間（NFR6.1・NFR6.3〜NFR6.5 の値を候補にする）と成功の割合を SLI の候補として決め直し、あわせて 3.1 の数え上げを警報にするかを考える。

## 上流との差

| ID | 上流 | 上流の記載 | この設計 | 理由と扱い |
|---|---|---|---|---|
| OD-D1 | NFR 要件の NFR2.3 | 送信の失敗は U3 が invitationId と失敗の種類を1件のログに出す（U1 のログと重ねない作りの細部は NFR 設計） | U3 は失敗のときだけ INFO を1件（invitationId・operation・failureKind）、失敗の WARN は U1 の1件だけにした（2.1） | Q5 A。1つの失敗に WARN を2件並べず、ログの設定と U1 を変えない |
| OD-D2 | NFR 要件の「何を見れば分かるか」 | 誤ったトークンの増えは監査の REGISTRATION_FAILED を failureReason ごと・sourceIp ごとに数える | 数える問い合わせの形を書き、警報が無いことを明記した（3.1） | 承認の場の決定（2026-09-27、R1 に書き足す）。要件の文書は書き換えない |
