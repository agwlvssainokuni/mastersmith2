# NFR Design の質問 — u3-invitation

単位 U3（`aidlc/spaces/default/intents/260925-user-management/inception/units-generation/unit-of-work.md`、種類 service）の NFR 設計のための質問です。service の単位のため、成果物は `performance-design.md`・`security-design.md`・`scalability-design.md`・`reliability-design.md`・`observability-design.md`・`logical-components.md`・`traceability.json` のすべてです。この段は設計の段なので、承認済みの NFR 要件を満たす作り・方針・判断を書きます。

読んだ上流:

- この単位の承認済みの NFR 要件 `construction/u3-invitation/nfr-requirements/`（`performance-requirements.md`・`security-requirements.md`・`scalability-requirements.md`・`reliability-requirements.md`・`observability-requirements.md`・`tech-stack-decisions.md`・`traceability.json`）と、承認の場の決定（2026-09-27 Approve の Minor: BR7.4 の拒否の経路は Unverified とし、監査の急な増えに気づく仕組みが無いことを残る危険 R1 に書き足す）。NFR 設計へ回された点は NFR5.1（送信の間に接続を持たない作りと観測の仕方）・NFR6.10 と NFR9.8（同じメールアドレスの招待中を1件に限る仕組みと行の排他）・NFR10.2（招待の表を足した後の後方互換の確かめ方）・NFR2.3（送信の失敗のログを U1 のログと重ねない作り）・NFR4.3（SecurityFilterChain のレビュー）
- この単位の承認済みの機能設計 `construction/u3-invitation/functional-design/`（`functional-spec.md` の2節の手順・3節の失敗・7節の後の段へ渡すこと、`rules.md` の BR1〜BR11、`entities.md`）
- 依存する単位の NFR 設計（同じ段、レビュー READY）: `construction/u1-mail/nfr-design/`（`logical-components.md` の1節〜6節: `MailSender` の `isConfigured`・`send`、結果の型 `MailSendResult`・`MailFailureKind`、送信ごとに1件のログ、Observation `mastersmith.mail.send`、U3 との境目）、`construction/u2-user-preferences/nfr-design/`（`security-design.md` の2節 Q1 A: 本人の利用者 ID は `Authentication#getName()`、送り手の情報は機能の中の小さな型。3節 Q2 A: `fieldErrors: [{field, reason}]`。`reliability-design.md` の4節: `createUser` は同じメールアドレスに当たったとき EmailAlreadyUsed を返し、今のトランザクションに巻き戻しの印を付ける。`performance-design.md` の5節: bcrypt の前に登録済みを確かめる）、`construction/u8-instance-appearance/nfr-design/security-design.md`（2.3: 差し込み口の order は機能の名前で 100 台ずつ、invitation は 300 台で u3-invitation は 310）
- 契約 `inception/contract-design/contract-summary.md` の C1・C2・C5・C6・C8、部品 `inception/domain-design/components.md`（Invitation の `depends_on` は UserAccount と Mail だけ）、ADR `inception/domain-design/decisions.md`（ADR-009・ADR-010・ADR-011）
- 既存のコード: `backend/src/main/java/cherry/mastersmith/auth/web/ClientInfoResolver.java`・`auth/domain/ClientInfo.java`・`auth/web/AuthenticatedUserToken.java`（`getName()` は利用者 ID）、`dslmanage/web/DslRequestContextResolver.java`（`dslmanage` は `auth.web`・`auth.domain` に依存している前例）、`auth/web/AuthSecurityContributor.java`（order 110、アクセストークンを読まないのは `/api/auth/` の下だけ）、`config/SecurityConfig.java`、`common/security/SecurityRuleContributor.java`、`auth/repository/LoginAttemptStateRepository.java`（`PESSIMISTIC_WRITE` と待ちの上限 3 秒）、`auth/service/RefreshTokenCleanupJob.java`・`auth/repository/RefreshTokenRepository.java`（件数の上限 1000 ごとの削除、`TransactionTemplate`）・`auth/service/AuthSchedulingConfig.java`（`@EnableScheduling` は `auth` にだけある）、`common/observability/TraceAspect.java`（`web`・`service`・`domain`・`repository` の Bean の引数と戻り値を TRACE で文字列にする）、`backend/src/main/resources/logback-spring.xml`（MDC は traceId・spanId だけを出す）、`backend/src/main/resources/application.yaml`（接続プールの上限 30・借りる待ち 5 秒、H2 のファイルの接続先）、`backend/src/test/java/cherry/mastersmith/ArchitectureTest.java`・`auth/AuthBoundaryArchitectureTest.java`、`backend/build.gradle.kts` の `packagesJudgedByTotal`、`backend/gradle.lockfile`（H2 2.4.240・Flyway 12.4.0・Hibernate 7.4.5）
- 決まり `aidlc/spaces/default/memory/team.md`（Code Style・Testing Posture・Deployment）・`project.md`（Forbidden・Mandated・Corrections）

## 設計の要点（案）

承認済みの NFR 要件・機能設計・依存する単位の NFR 設計とコードの確認から導ける、この単位の作りの見通しです。質問の答えで決まる点は（Qn）と書きます。

### 性能と接続

1. **招待・送り直しの流れ（NFR5.1・NFR6.1・NFR6.2、ADR-009）**: 入力の検証（内部DB を使わない）→ 使える設定か → 短いトランザクション（行の排他・確定・出来事を知らせる）→ 確定の後に監査の記録（既存の AFTER_COMMIT、2本目の接続）→ 接続をすべて返す → 招待の URL をベース URL だけから組み立てる → U1 の `MailSender.send` を1回（接続は0本）→ 別の短いトランザクションで送信の結果を書く → 応答。取りまとめの業務処理のクラス（例: `InvitationService`）には `@Transactional` を付けず、2つの短いトランザクションは既存の `LoginService`・`RefreshTokenCleanupJob` と同じく `TransactionTemplate` で囲む（トランザクションの境界は `invitation.service` の中だけ、`team.md` の Code Style）。送信の間に接続を持たないことの確かめ方は（Q4）。
2. **送信の結果の記録（BR4.3、NFR9.9）**: 「送ったトークンのハッシュを今も持ち、送信の結果が PENDING の行」だけを書き換える条件つきの更新の問い合わせ1回にする（`invitation_id`・`token_hash`・`send_result = 'PENDING'` を条件に含める）。更新した行が 0 なら何もしない（同時の送り直しの新しい結果を古い結果で上書きしない）。記録のトランザクションが内部DB の障害で失敗したときは、機能設計の3節のとおり既存の 500 の扱いとし、行は PENDING のまま一覧では FAILED に見えて送り直せる。
3. **登録の完了（NFR5.2・NFR6.4・NFR9.13、BR7.3）**: 入力の検証（U2 の DisplayName・表示の設定の値・PasswordPolicy の純粋な関数、内部DB を使わない）→ トークンの形の確かめ（内部DB を使わない）→ 1つのトランザクション（`TransactionTemplate`）で、招待の行をトークンのハッシュで排他して読み、`createUser`（C2、同じトランザクション、bcrypt を含む）を呼び、Created なら招待を COMPLETED にして出来事を知らせ、確定する。承認どおり bcrypt の間も接続と招待の行の排他を持つ。
4. **拒否のときの巻き戻し（BR7.4・BR7.5、U2 の `reliability-design.md` 4節）**: トランザクションの中で拒否（見つからない・有効でない・EmailAlreadyUsed）が決まったら、`TransactionTemplate` の中で自分の `TransactionStatus#setRollbackOnly` を付けてから抜ける。`createUser` が EmailAlreadyUsed で付けた巻き戻しの印は参加したトランザクション全体の印になるため、呼び出し元が自分の印を付けずに確定しようとすると `UnexpectedRollbackException` になる。自分の印を付けると、確定の処理は例外なしで巻き戻す。巻き戻しの後、トランザクションの外で REGISTRATION_FAILED の出来事を知らせ（既存の `fallbackExecution = true` でその場で記録）、同じ 404 を返す。
5. **BR7.4 の経路の性能（承認の場の決定、NFR6.5）**: U2 の `createUser` は bcrypt の前に登録済みを読み取り1回で確かめる（U2 の `performance-design.md` 5節）ため、ふつうは bcrypt を計算しない。同時の作成で一意の制約に当たる場合だけ計算の後に決まる。この経路は NFR6.5 の目標から外したまま `Unverified` とし、performance-validation の場面に入れない。結合テストでは正しさ（巻き戻し・利用者が増えない・招待が PENDING のまま・同じ 404・監査の理由 EMAIL_ALREADY_REGISTERED）だけを確かめる。`performance-design.md` に Unverified と理由を書く。
6. **行の排他の待ち（BR6.4、NFR9.8）**: 送り直し・取り消し・登録の完了・招待（同じメールアドレスの PENDING の行）の排他の読み取りは、既存の `LoginAttemptStateRepository` と同じ `PESSIMISTIC_WRITE` で、待ちの上限 3 秒の指定を付ける。いちばん長く排他を持つのは登録の完了（bcrypt の約 0.3〜1 秒）で、3 秒に収まる見込み。待ちの時間切れは想定外として既存の 500 の扱いにする。
7. **一覧・取り消し・リンクの確かめ（NFR6.3）**: 一覧は PENDING の行を並べて 20 件を読む問い合わせ1回と件数の問い合わせ1回。招待した管理者の氏名は、ページの中の異なる `invitedByUserId` ごとに C2 の `findDisplayName` を1回ずつ引く（多くても 20 回、契約 C2 は変えない）。キャッシュは置かない。索引は、トークンのハッシュの一意と、招待中の一意（Q2）の2つだけ（行は利用者の数の程度で、並べ替えの索引は足さない）。
8. **接続の見積もり（NFR5.4）**: 承認どおり（招待・送り直し・取り消し・登録の完了の成功は最大2本、送信の間は0本、ほかは1本）。実際に尽きないことは performance-validation の k6（NFR5.3）で確かめる。bcrypt の cost 12 は変えない（NFR6.6）。

### セキュリティ

9. **トークン（NFR1.1・NFR1.2・NFR1.6）**: 既存の `RefreshTokenValues` と同じく `SecureRandom` の 32 バイトを URL で使える Base64（埋め草なし、43 文字）にし、UTF-8 の SHA-256 を `MessageDigest` で計算する。形の確かめ（43 文字・使える文字だけ）は内部DB を使わない純粋な関数。
10. **追跡のログへの漏れ（NFR1.5）**: 既存の `TraceAspect` は `invitation` の `web`・`service`・`domain`・`repository` の Bean の引数と戻り値を TRACE で文字列にする。そのため、トークンと招待の URL は文字列のまま業務処理の引数・戻り値にせず、文字列化で伏せ字にする小さな値の型（例: `InvitationToken`・`RegistrationUrl`）で受け渡す。要求の DTO（`TokenRequest`・`CompleteRequest`）の `toString` もトークンとパスワードを伏せる（既存の `Password` と同じ考え方）。トークンのハッシュは既存のリフレッシュトークンと同じくバイト列のまま扱い、応答・監査・ログに出さない。
11. **公開の決まり（NFR4.2、ADR-011）**: Invitation の差し込み口（例: `InvitationSecurityContributor`、`SecurityRuleContributor`）の order を **310** にする。U3 の承認済みの機能設計と NFR 要件は「順番の値はコード生成で決める」としているが、U8 の NFR 設計（`construction/u8-instance-appearance/nfr-design/security-design.md` の2.3）が機能の名前で 100 台ずつ割り当て、invitation を 300 台・u3-invitation を 310 と決めたため、その割り当てを採る。決まりは `POST` の `/api/registration/verify` と `/api/registration/complete` の2つの道だけを `permitAll` にし、ほかのメソッド・道は `/api/**` の既定のログイン必須に落ちる。公開の道に届いたアクセストークンの扱いは（Q3）。Origin の確かめと CSRF の設定は足さない（NFR4.3、クッキーを使わない）。
12. **認可（NFR4.1）**: `/api/admin/invitations` の下は既存の `/api/admin/**` の管理者の決まり（`access`）にそのまま乗り、新しい決まりを足さない。操作した管理者の利用者 ID と送り手の情報の取り方は（Q1）。
13. **入力と項目ごとの誤り（NFR9.12、BR1.1・BR7.2）**: 要求の DTO の項目はすべて文字列で受け、Java の列挙・Bean Validation の注釈で受けない（U2 と同じ理由）。登録の完了の 400 VALIDATION_FAILED には、U2 の Q2 A の形（`fieldErrors: [{field, reason}]`、`user.domain` の FieldError と理由の列挙）を C6 の項目名（`displayName`・`password`・`passwordConfirmation`・`language`・`theme`・`fontSize`）で載せる。トークンの形の誤りは項目ごとの誤りにせず、承認どおり 404（BR3.2・BR7.5）。招待の入力の誤り（`email`・`language`）にも同じ形を載せる（安全な追加で、U5 は形に頼らない）。一覧の page の誤りは今の VALIDATION_FAILED のまま（項目ごとの誤りなし）。
14. **拒否の応答の同一（NFR3.1）**: リンクの確かめと登録の完了の拒否は、理由によらず1つの応答の経路（REGISTRATION_LINK_INVALID の `BusinessException`）で作り、理由は出来事（監査）にだけ渡す。応答の時間の差はそろえない（NFR3.2）。
15. **残る危険 R1 の書き足し（承認の場の決定）**: `security-design.md` の残る危険 R1 に、監査の REGISTRATION_FAILED の急な増えに自動で気づく仕組み（警報）が無く、管理者が監査ログを数える問い合わせ（`observability-design.md` に形を書く）で見つけるだけであることを書き足す。見直す時点は承認どおり（社外に公開する配備先が決まったとき、または急な増えが見つかったとき）。
16. **静的解析（NFR9.3）**: 新しい除外を足さない。乱数は `SecureRandom`、SQL は名前付きの引数だけで組み立て、メールのヘッダーは U1 を通すため、関門の対象の指摘は出ない見込み。

### 拡張性

17. **1台・組み込みの H2 のまま（NFR6.7・NFR6.10）**: アプリのメモリに状態を持たない（回数の数え上げ・キャッシュなし）。招待中を1件に限る仕組み（Q2）と行の排他、定期の削除（複数台での重なりを防ぐ仕組みを持たない）は、どれも内部DB の単一インスタンスを前提にする。複数台にするときは、この前提を見直すまで行わない（承認どおり）。

### 信頼性

18. **監査の出来事（NFR9.4・NFR9.5、C8）**: 出来事の型5つ（招待・送り直し・取り消し・登録の完了・登録の失敗）は `invitation.domain` に置き（既存の出来事と同じく、監査の組み立て `audit.domain.AuditEventFactory` が各機能の `domain` から読む）、`AuditEventListener` に受け取りを足す（AFTER_COMMIT・`fallbackExecution = true`、失敗は受け止めて ERROR）。`invitation` は `audit` に依存しない。出来事の型はトークン・ハッシュ・URL・メールアドレス・パスワードを持たない。
19. **定期の削除（NFR9.10、BR11.1）**: 既存の `RefreshTokenCleanupJob` と同じ形（`@Scheduled(cron)`、件数の上限ごとに `TransactionTemplate` で1回ずつ、INFO で消した件数、失敗は ERROR で外へ出さない）。消す問い合わせは、対象の行を選ぶ副問い合わせだけでなく外側の DELETE の条件にも状態と日時の条件を重ね、選んだ後に送り直された行を消さない。既定の案は、時刻 `0 45 3 * * *`（既存のリフレッシュトークンの削除 3 時 30 分と重ならない）、件数の上限 1000（既存と同じ）。値の最終はコード生成で決める（承認どおり）。`@EnableScheduling` は今 `auth.service.AuthSchedulingConfig` にしか無いため、`invitation` の中にも置き（重ねて置いても害が無い）、`auth` の設定に黙って頼らない。
20. **後方互換（NFR10.1・NFR10.2）**: 招待の表は V8 の1つの前進の変更で新しく足し、V1〜V7 を変えない。確かめは U2 の V7（U2 の NFR 設計 Q4 A）と同じ2段: (1) 自動の結合テストで、V8 まで当てた内部DB に対して V7 までしか知らない Flyway の設定で検証が失敗しないことを確かめる。(2) deployment-execution の戻しの練習で、1つ前の版のイメージを V8 の後の内部DB の複写で起動する（手順は deployment-pipeline、U2 の練習と合わせて行う）。

### 観測

21. **新しい指標・警報・ダッシュボードは足さない（NFR6.8・NFR6.9）**: `http.server.requests` の `uri` が道の型になることを observability-setup で起動して確かめる。U1 の Observation `mastersmith.mail.send` は招待・送り直しの要求の span の子になり、送信の時間は U1 の指標で見られる（U3 は新しい Observation を作らない）。送信の失敗のログの出し方は（Q5）。定期の削除の INFO・ERROR は 19 のとおり。

### テストとカバレッジ（NFR9.6・NFR9.7）

22. **境界の検査（ArchUnit）**: `InvitationBoundaryArchitectureTest` を足す: `invitation` は `audit` に依存しない、`user` へは `user.service`・`user.domain` の値の型だけ（エンティティと `user.repository` を使わない）、`mail` へは `mail.service`・`mail.domain` だけ、`auth`・`user`・`mail`・`audit` 以外の機能（`dsl` など）に依存しない、ほかの機能は `invitation` に依存しない（`audit` が `invitation.domain` の出来事を読むのを除く）。`invitation` から `auth` への依存は（Q1）で決まる。既存の境界のテストは緩めない。
23. **カバレッジ**: 新しい `invitation` の下のパッケージは自動でパッケージごとの下限の対象。B2 の後も `packagesJudgedByTotal` に残る既存のパッケージには、Q1 A・Q3 A なら手を入れない見込み（`audit.domain`・`audit.service` は B2 で一覧から外れている）。実測は `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で行い、値を記録する。
24. **純粋な関数と性質ベースのテスト**: 有効の判定（BR3.3）・トークンの形（BR3.2）・一覧のページの計算（BR2.3・BR5.2）を jqwik で確かめる（承認どおり）。

## 決まっていること（質問にしない）

| 決まっていること | 出典 |
|---|---|
| 招待・送り直しは、確定の短いトランザクション → 接続を持たない送信 → 結果の記録の短いトランザクション。要求は結果を待って応答する | ADR-009、NFR5.1、BR4.1 |
| 登録の完了は bcrypt の間も接続と招待の行の排他を持つ。契約 C2 と U2 の BR5.3 は変えない | NFR5.2（Q4 A） |
| 送信は1回だけ、自動の再試行と全体の上限なし。遅れ続ける受け手の既知の限界を引き継ぐ | U1 の NFR6.3、NFR6.2 |
| 公開の2つの API に回数の制限を設けない。応答の時間の差はそろえない | NFR4.5（Q2 A）、NFR3.2 |
| BR7.4 の拒否の経路は性能の目標から外し Unverified。監査の急な増えに気づく仕組みが無いことを R1 に書き足す | 承認の場の決定（2026-09-27）、NFR6.5 |
| 差し込み口の order は機能の名前で 100 台ずつ、u3-invitation は 310 | U8 の `security-design.md` 2.3 |
| 項目ごとの誤りは `fieldErrors: [{field, reason}]`、本人の利用者 ID は `Authentication#getName()` の考え方 | U2 の NFR 設計 Q1 A・Q2 A |
| `createUser` は EmailAlreadyUsed のとき巻き戻しの印を付け、呼び出し元は必ず巻き戻す | U2 の `reliability-design.md` 4節、BR7.4 |
| 新しい依存・指標・警報は足さない | `tech-stack-decisions.md`、NFR6.8・NFR6.9 |
| 定期の削除の既定の時刻と件数の上限、設定の項目の名前はコード生成で決める | NFR9.10、機能設計の7節 |
| 1台・組み込みの H2 の前提を変えない | ADR-010、NFR6.10 |

---

## Q1. 招待の管理の API（管理者）と登録の完了の API（ログインなし）で、操作した管理者の利用者 ID と送り手の情報（接続元 IP・User-Agent・トレースID）をどう取りますか？

理由: 監査の出来事には、招待の管理では操作した管理者の利用者 ID（actorUserId）、どの出来事にも送り手の情報（sourceIp・userAgent・traceId）が要ります（NFR9.5、`entities.md` の「既存の ClientInfo と同じ取り方」）。取り方の部品 `AuthenticatedUser`・`ClientInfo`・`ClientInfoResolver` は `auth` にあります。U2 は構造の検査（user は auth に依存しない）のため `user` の中で取る形（U2 の Q1 A）にし、U3 は `auth` に依存してよいかを自分の設計で決めることになっています。構造の検査は `invitation` から `auth` への依存を禁じておらず、`dslmanage` が `auth.web` の `ClientInfoResolver` を使う前例があります。一方、承認済みの部品の一覧（`components.md`）では Invitation の `depends_on` は UserAccount と Mail だけです。

A. U2 と同じ考え方で `invitation.web` の中で取る。管理者の利用者 ID は `Authentication#getName()` を数として読み（読めなければ 401、`ProblemTypeRegistry` で AUTHENTICATION_REQUIRED を起動時に引く）、送り手の情報は U2 の `user.domain` の値の型（`RequestOrigin`）を同じ取り方で作る（`invitation` は `user` に依存してよい）。承認済みの部品の一覧のとおりで、`auth` に依存しない。代わりに取り方が `auth`・`user.web`・`invitation.web` の3か所に並ぶ（同じ形の結合テストで値の取り方がそろうことを固定する）
B. `dslmanage` の前例と同じく、`invitation.web` から既存の `auth.web.ClientInfoResolver` と `auth.domain` の `AuthenticatedUser`・`ClientInfo` を使う。取り方が増えない代わりに、Invitation から Authentication への依存が増え、承認済みの部品の一覧との差（`logical-components.md` に記録）になる。`auth` が `invitation` に依存しないことを境界のテストで固定する
X. Other (please specify)

[Answer]: A

## Q2. 同じメールアドレスの招待中（PENDING）を1件に限る仕組みを、どう作りますか？

理由: BR2.5 は「同じメールアドレスの PENDING は常に1件まで、同時の招待では1件だけ作る」とし、仕組み（索引か行の排他か）を NFR 設計で決めることにしています（ADR-010）。組み込みの H2 は条件つきの一意の索引（WHERE 付きの索引）を持ちません。まだ行の無いメールアドレスは行の排他で守れないため、2つの同時の招待はどちらも「招待中なし」と読みえます。この段で、H2 2.4.240 の生成列（計算で決まる列）と一意の制約の組み合わせを使い捨ての試し（本番のコードとは別、メモリの中の H2）で確かめ、PENDING の2件目が一意の違反（SQLState 23505）で拒まれること、PENDING でない行は同じメールアドレスでいくつでも持てること、確定していない同時の追記は待たされた後に一意の違反になり1件だけ残ることを確かめました。Flyway の移行と Hibernate の `ddl-auto: validate` との組み合わせは、まだ確かめていません。

A. 内部DB の制約で守る。招待の表に、state が PENDING のときだけメールアドレスになり、それ以外は空（NULL）になる生成列（例: `pending_email`）を置き、その列に一意の制約を付ける（空は重なってよい）。エンティティはこの列を持たない（Hibernate の `validate` は余分な列を見ない）。同時の招待で負けた側は、一意の違反を受けてトランザクションが巻き戻った後、短い読み取りで勝った側の invitationId と page を引いて 409 INVITATION_ALREADY_PENDING を返す（勝った側が既に無ければ、招待の作成を最初から1回だけやり直す）。確かめは B3 の最初の結合テストで、Flyway の V8 と Hibernate の `validate` の上で行い、成り立たなければ B に切り替える
B. 業務処理で順番に並べる。招待の作成のトランザクションの初めに、固定の1行（例: 招待の作成の番の行）を `PESSIMISTIC_WRITE` で排他して読み、招待の作成を1つずつ行う。H2 の生成列に頼らない代わりに、違うメールアドレスの招待どうしも待たされ（1件は数十ミリ秒の見込み）、DB の制約の守りが無いため、排他を通らない書き込みの経路を作ると守れなくなる
C. アプリのメモリの中の排他（メールアドレスごとの錠）で順番に並べる。DB に手を入れない代わりに、1台の前提をコードの中にも持ち込み、確定の前後と錠の範囲の合わせ方を誤ると守れない
X. Other (please specify)

[Answer]: A

## Q3. 公開の2つの道（`/api/registration/verify`・`/api/registration/complete`）に `Authorization` の見出しでアクセストークンが届いたとき、サーバーはどう扱いますか？

理由: 既存の `AuthSecurityContributor` は、`/api/auth/` の下を除くすべての道でアクセストークンを読みます。そのため、公開にした2つの道でも、期限切れ・壊れたトークンが付いていると、`permitAll` の判定の前に 401 になります。画面の側は U4 の ApiClient が公開の API にトークンを付けない（U4 の NFR9.1、U6 の NFR9.1）ため、ふつうは起きません。どちらの扱いでも招待・利用者の有無は漏れません（401 は招待のトークンと関係なく決まる）。

A. サーバーは今のまま（公開の道でも届いたトークンを読み、正しくなければ 401）とし、トークンを付けないことは画面の側（U4 の ApiClient）に任せる。`auth` と `common.security` に手を入れない。結合テストで「トークンなしなら処理される」「壊れたトークン付きなら 401」を固定し、画面の側の前提を `security-design.md` に書く
B. サーバーでも公開の2つの道ではアクセストークンを読まないようにする。`common.security` に「トークンを読まない道」を差し出す小さな口を足し、`AuthSecurityContributor` の読み取りがそれを見る（`auth` は `invitation` の道を知らない）。画面の側の誤りにも強くなる代わりに、`common.security`（B2 の後も `packagesJudgedByTotal` に残る）と `auth.web` に手を入れ、`common.security` をパッケージごとの下限に戻す作業が加わる
C. B と同じ扱いを、`AuthSecurityContributor` の「トークンを読まない道」に `/api/registration/` を書き足して作る。変更は最も小さいが、`auth` が招待の道の名前を知る（名前だけの結び付き）
X. Other (please specify)

[Answer]: A

## Q4. 送信を待つ間に内部DB の接続を持たないこと（NFR5.1、AC1.1.7）を、どう確かめ、どう守りますか？

理由: 機能設計の7節と NFR5.1 は、この作りの「観測の仕方」を NFR 設計で決めることにしています。承認済みの確かめ方は「受け手が応答しない送信の最中に、ほかの API（一覧など）が時間切れを待たずに応答する」結合テストです。これだけでは、接続が 30 本あるため、送信の間に1本を持ち続けていても一覧は応答でき、見落としうる点があります。なお、テストの既定では接続プールの MBean の登録を無効にしています（`project.md` の学び）が、`HikariDataSource#getHikariPoolMXBean` は登録なしで読めます。

A. 結合テストを2つの確かめにする: (1) 受け手が応答しない `ServerSocket` への送信の最中に、接続プールの使用中の数（`getHikariPoolMXBean().getActiveConnections()`）が 0 であることを待ち合わせて確かめる、(2) 承認どおり、同じ最中に一覧が時間切れを待たずに応答する。あわせて、送信を呼ぶ部品の入口で「トランザクションの中で呼ばれていない」ことを確かめ（`TransactionSynchronizationManager.isActualTransactionActive()` が真なら想定外の誤りとして止める）、後の変更で送信がトランザクションの中に入ったときに気づけるようにする
B. A の (1)・(2) の結合テストだけにし、本番のコードに確かめを置かない。コードは増えないが、後の変更で送信がトランザクションの中に入ったとき、テストの場面に当たらない経路では気づけない
C. 承認どおりの (2) だけにする。テストは最も少ないが、上のとおり接続を持ち続けていても通りうる
X. Other (please specify)

[Answer]: A

## Q5. 招待メールの送信が失敗したときのアプリのログを、U1 のログとどう分けますか？

理由: U1 は送信ごとにログを1件出します（失敗は WARN、項目は templateId・language・failureKind・exceptionType、トレースIDは送信の span のもの）が、どの招待の送信かは分かりません。NFR2.3 は「送信の失敗は U3 が invitationId と失敗の種類を1件のログに出す（U1 のログと重ねない作りの細部は NFR 設計）」としています。今のログの設定（`logback-spring.xml`）は MDC のうち traceId・spanId だけを出します。

A. U3 は送信の結果を記録した後に、失敗のときだけ INFO のログを1件出す（項目は invitationId・操作（招待か送り直しか）・failureKind）。失敗の WARN は U1 の1件だけにし、2つは同じトレースIDでつながる。ログの設定と U1 は変えない
B. U3 が送信の間だけ MDC に invitationId を入れ、ログの設定で invitationId を出す MDC の名前に加える。U1 の WARN の1件に invitationId が載り、U3 は別のログを出さない。行は1件で済む代わりに、共通のログの設定を変え、送信の間に出るほかのログにも invitationId が載る
C. U3 も失敗のときに WARN を1件出す（項目は A と同じ）。見つけやすい代わりに、1つの失敗に WARN が2件並ぶ
X. Other (please specify)

[Answer]: A

---

## Consolidated Summary Confirmation

答えのまとめ（答えを受けた後に、選んだ答えでこの節を書き直します）:

- 設計の要点（案）は冒頭の 24 件のとおり（招待・送り直しは短い確定 → 接続なしの送信 → 条件つきの更新で結果の記録、登録の完了は1つのトランザクションで排他・`createUser`・完了とし拒否は自分で巻き戻しの印を付けて静かに巻き戻す、BR7.4 の経路は Unverified、行の排他は `PESSIMISTIC_WRITE` と待ち 3 秒、トークンと URL は伏せ字の値の型で受け渡す、差し込み口の order は 310、項目ごとの誤りは U2 の `fieldErrors`、R1 に警報が無いことを書き足す、出来事は `invitation.domain`、定期の削除は既存の形で既定の案は 3 時 45 分・1000 件、後方互換は U2 と同じ2段、新しい指標・警報・依存は足さない）
- Q1 A: U2 と同じく `invitation.web` の中で取る。利用者 ID は `Authentication#getName()`、送り手の情報は U2 の `user.domain.RequestOrigin` で作る。`auth` には依存しない（承認済みの部品の一覧のとおり）
- Q2 A: H2 の生成列 `pending_email`（PENDING のときだけ値を持つ）に一意の制約を付ける。同時の招待で負けた側は巻き戻した後に勝った側を読んで 409 を返す。B3 の最初の結合テストで Flyway と Hibernate の validate との組み合わせを確かめ、成り立たなければ固定の1行の排他に切り替える
- Q3 A: 公開の2つの道のサーバーの扱いは今のまま（壊れたトークンが付いていれば 401）。トークンを付けないことは U4 の ApiClient（承認済みの R-01 の直し）に任せ、テストでこの動きを固定する
- Q4 A: 結合テストで送信の最中の使用中の接続が 0 であることと一覧が応答することの両方を確かめ、送信の入口に「トランザクションの外で呼ばれていること」の確かめを置く
- Q5 A: 送信の失敗のとき U3 は invitationId・操作・failureKind を INFO で1件出し、失敗の WARN は U1 の1件だけにする。2つは同じトレースIDでつながる

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
