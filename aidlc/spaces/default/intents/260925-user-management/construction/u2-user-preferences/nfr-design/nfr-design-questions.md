# NFR Design の質問 — u2-user-preferences

単位 U2（`aidlc/spaces/default/intents/260925-user-management/inception/units-generation/unit-of-work.md`、種類 service）の NFR 設計のための質問です。service の単位のため、成果物は `performance-design.md`・`security-design.md`・`scalability-design.md`・`reliability-design.md`・`observability-design.md`・`logical-components.md`・`traceability.json` のすべてです。この段は設計の段なので、承認済みの NFR 要件を満たす作り・方針・判断を書きます。

読んだ上流:

- この単位の承認済みの NFR 要件 `construction/u2-user-preferences/nfr-requirements/`（`performance-requirements.md`・`security-requirements.md`・`scalability-requirements.md`・`reliability-requirements.md`・`observability-requirements.md`・`tech-stack-decisions.md`・`traceability.json`）。NFR 設計へ回された点は NFR5.1（bcrypt をトランザクションの外に置く作りの細部）・NFR6.5（ログイン・更新の応答で問い合わせを増やさない）・NFR4.4（SecurityFilterChain のレビュー）・NFR10.3・NFR10.4（V7 の後方互換の確かめ方）
- この単位の承認済みの機能設計 `construction/u2-user-preferences/functional-design/`（`rules.md` の BR1〜BR9、`functional-spec.md` の2節・3節・6節・7節、`entities.md`）
- 契約 `inception/contract-design/contract-summary.md` の C2・C3・C4・C8、部品 `inception/domain-design/components.md`、ADR `inception/domain-design/decisions.md`（ADR-003・ADR-004・ADR-008）
- 同じ段の後の単位が頼る点: U3 の NFR 要件 `construction/u3-invitation/nfr-requirements/`（登録の完了は bcrypt の間も接続を持つ Q4 A、同じメールアドレスの拒否 BR7.4 を 1 秒の目標から外した NFR6.5）、U7 の機能設計 `construction/u7-preferences-ui/functional-design/functional-spec.md` の W12・10節の (c)（項目ごとの誤りの形は U2 が決める）
- 既存のコード: `backend/src/main/java/cherry/mastersmith/user/service/UserAccountService.java`（`createUser` は `@Transactional` の中で `passwordEncoder.encode` を呼ぶ、`verifyPassword` はトランザクションの外）、`user/domain/User.java`（全列を持つエンティティ）、`user/service/InitialAdminInitializer.java`、`audit/service/AuditEventListener.java`（AFTER_COMMIT・`fallbackExecution = true`、失敗の ERROR に項目を載せる）、`common/error/web/GlobalExceptionHandler.java`・`common/error/domain/BusinessException.java`（今の VALIDATION_FAILED は項目ごとの誤りを返さない、追加の項目は BusinessException の properties で載る、DSL は `errors` を使う）、`auth/web/AuthenticatedUserToken.java`（`getName()` は利用者 ID）・`auth/web/ClientInfoResolver.java`・`auth/domain/ClientInfo.java`、`backend/src/test/java/cherry/mastersmith/auth/AuthBoundaryArchitectureTest.java`（「user does not depend on auth」「auth and user do not depend on audit」）、`backend/src/main/resources/application.yaml`（Flyway の `validate-on-migrate`、Hibernate の `ddl-auto: validate`、接続プールの上限 30・借りる待ち 5 秒）、`backend/src/main/resources/db/migration/V2__u2_user_account.sql`、`backend/build.gradle.kts` の `packagesJudgedByTotal`、`backend/gradle.lockfile`（Flyway 12.4.0）
- 決まり `aidlc/spaces/default/memory/team.md`（Code Style・Testing Posture・Deployment）・`project.md`（Forbidden・Mandated・Corrections）

## 設計の要点（案）

承認済みの NFR 要件とコードの確認から導ける、この単位の作りの見通しです。質問の答えで決まる点は（Qn）と書きます。

### 性能

1. **プリファレンスの取得と保存（NFR6.1・NFR6.2）**: 取得は利用者の1行の読み取り1回（読み取り専用のトランザクション）。保存は検証を DB の外で済ませ、通ったときだけ短いトランザクションで4列を書き換える。キャッシュは置かない（要求ごとに内部DB から読む BR3.1、1行の読み取りで足りる）。
2. **パスワードの変更の流れ（NFR5.1・NFR6.3・NFR6.4）**: (1) 入力の検証（DB を使わない）→ (2) 保存されたハッシュの読み取り（短い読み取りのトランザクション、接続はすぐ返す）→ (3) 照合（bcrypt、接続を持たない）→ 不一致なら (4a) トランザクションの外で失敗の出来事を知らせ（監査はその場で記録）、400 を返す → 一致なら (4b) 新しいハッシュの計算（bcrypt、接続を持たない）→ (5) 短いトランザクションで password_hash だけを書き換え、同じトランザクションの中で成功の出来事を知らせる（確定の後に監査が2本目の接続で記録される）。接続を持つのは (2) と (5) の短い間だけ。(2) と (5) の間に別の変更が入ったときの扱いは（Q3）。
3. **トランザクションの境界の置き方**: 境界は業務処理の層（`user.service`）にだけ置く（`team.md` の Code Style、ArchUnit）。同じクラスの中の呼び出しでは `@Transactional` が効かないため、(2)・(5) は既存の `LoginService` と同じく `TransactionTemplate`（または別の部品の `@Transactional` の操作）で囲む。どちらにするかはコード生成で決め、トランザクションの境界はテストで確かめる。
4. **ログイン・更新の応答の広げ（NFR6.5）**: 既存の `UserSummary` に4つを足す。ログインは `verifyPassword` の `findByEmail`、更新は `findById` の読み取りから作るため、内部DB への問い合わせは増えない。
5. **利用者の作成（C2、U3 の登録の完了）**: `createUser` は、メールアドレスをそろえた後、bcrypt の前に登録済みかを1回の読み取りで確かめ、登録済みなら計算せずに EmailAlreadyUsed を返す。同時の作成で一意の制約に当たった場合も EmailAlreadyUsed を返す（BR5.2）。一意の制約に当たったときは呼び出し元のトランザクションが巻き戻しの印を持つが、U3 は EmailAlreadyUsed で必ず巻き戻す（U3 の BR7.4）ため食い違わない。bcrypt は U3 の決定（U3 の Q4 A）のとおり呼び出し元のトランザクションの中で計算する（契約 C2 と BR5.3 を変えない）。
6. **bcrypt の cost 12 は変えない**（NFR6.6）。

### セキュリティ

7. **認可（NFR4.1〜NFR4.4）**: `/api/me/` は既存の SecurityFilterChain の `/api/` の既定のログイン必須にそのまま乗る。新しい公開の決まり・Origin の確かめ・CSRF の設定は足さない（ベアラーの認証）。対象の利用者は認証の結果だけから取り、要求の DTO に利用者の識別の項目を持たせない。本人の利用者 ID と送り手の情報の取り方は（Q1）。
8. **入力の受け取り**: 要求の DTO（`record` の `XxxRequest`）の項目はすべて文字列で受け、Java の列挙や `@NotNull` で受けない。列挙で受けると決めていない値（例: `EN`）が JSON の読み取りの失敗（400 MALFORMED_REQUEST）になり、項目ごとの誤りを返せず、4つをまとめて検証する決まり（BR3.2・BR4.1）を守れないため。検証は DisplayName（BR1.6）・表示の設定の値（BR2.1）・PasswordPolicy の純粋な関数で業務処理の前に行う。要求の本文の上限は既存の 1MB のまま（NFR9.2）。
9. **項目ごとの誤りの形（BR3.2・BR4.1、U7 が頼る）**: （Q2）。
10. **秘密（NFR2.1〜NFR2.3）**: パスワードは既存の `Password` の型のまま扱い、要求の DTO の `toString` にも出さない。ハッシュは `user` の外へ出さない（既存の ArchUnit「only classes inside user read the password hash of User」を保つ）。項目ごとの誤りは項目の名前と理由の値だけを載せ、入れた値を載せない。監査の失敗の ERROR（`AuditEventListener` の項目）に足すのは対象の2列（targetUserId・targetInvitationId）だけで、パスワード・メールアドレスは載せない。既存の `*SecretLeakIT` の列の一覧に V7 の2列を足す。
11. **今のパスワードの総当たり（NFR4.5）**: 制限の仕組みは作らない（承認済みの Q2 A、残る危険 R1）。見つけ方は監査ログの PASSWORD_CHANGED・FAILURE・CURRENT_PASSWORD_MISMATCH を actorUserId ごとに数えること（`observability-design.md` に問い合わせの形を書く）。
12. **静的解析（NFR9.3）**: 新しい除外を足さない。乱数・SQL の組み立て・メールのヘッダーを使わないため、関門の対象の指摘は出ない見込み。

### 拡張性

13. **1台・組み込みの H2 のまま（NFR6.7・NFR5.3）**: アプリのメモリに状態を持たない（セッション・キャッシュ・回数の数え上げなし）。接続の見積もりは、成功のパスワードの変更だけが最大2本、ほかは1本（NFR5.3 のとおり）。複数台にするときの影響は NFR 要件の記録のとおりで、今は作らない。

### 信頼性

14. **監査（NFR9.4・NFR9.5・BR7.3）**: 出来事 `PasswordChangedEvent` は `user.service` に置き（既存の `UserCreatedEvent` と同じ置き場、`user` は `audit` に依存しない）、`AuditEventListener` に受け取りの操作を1つ足す（AFTER_COMMIT・`fallbackExecution = true`、最優先の順番、失敗は受け止めて ERROR）。成功は確定の後に、不一致はトランザクションの外でその場で記録される。
15. **部分の書き換え（BR3.4・NFR9.8）**: 今の `User` は全列を持つエンティティで、変更の検出で更新すると全列を書くため、同時のプリファレンスの保存とパスワードの変更で相手の列を古い値で上書きしうる。そのため、書き換えは `user.repository` の更新の問い合わせ（プリファレンスの4列だけ、password_hash だけ）で行う。更新した行の数が 0 なら本人がいない（401）として扱う。
16. **2本目の接続（NFR5.2）**: 作りは既存のまま（AFTER_COMMIT で `REQUIRES_NEW`）。performance-validation の k6 の場面と、hikaricp の待ちの時間切れの累計・待ちの最大・監査の件数の突き合わせは NFR 要件の NFR5.2 のとおり。
17. **V7 の後方互換（NFR10.1〜NFR10.4）**: V7 の中の順序は、language・theme・fontSize を既定の値つき・必須で足す → display_name を空を許す形で足す → 既存の行に email を入れる → display_name を必須にする → 監査の2列を空を許す形で足す。既定の値の有無と確かめ方は（Q4）。

### 観測

18. **新しい指標・警報・ダッシュボードは足さない（NFR6.8・NFR6.9）**: `http.server.requests` の `uri` が `/api/me/preferences`・`/api/me/password` の決まった値になることを observability-setup で起動して確かめる。4xx のログは既存の `GlobalExceptionHandler` の境界で1回だけ WARN（スタックトレースなし）、項目ごとの誤りの中身はログに載せない。

### テストとカバレッジ（NFR9.6・NFR9.7）

19. **パッケージごとの下限に戻す範囲**: 手を入れる見込みは `user.domain`・`user.repository`・`user.service`・`audit.domain`・`audit.service`・`auth.service`・`auth.web`（`audit.repository` は列の追加で手を入れる場合に加える）。新しい `user.web` は自動で対象になる。どれも一覧から外し、行 80%・分岐 70% を満たす。実測は `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で行い、値を記録する（`team.md` の決まりのとおりで、作りの選択肢は無い）。Q1・Q2 の答えによって、手を入れる既存のパッケージが増える（Q1 B・Q2 B）。
20. **ArchUnit**: 既存の層と機能の境界のテストを緩めない（Q1 C を選んだ場合だけ、コード生成の計画に明記して承認を得る）。`user.web` が `user.repository` を直接呼ばないことは既存の全体の決まりで確かめられる。

## 決まっていること（質問にしない）

| 決まっていること | 出典 |
|---|---|
| パスワードの変更の bcrypt はトランザクションの外、接続を持つのはハッシュの読み取りと書き込みの短い間だけ | NFR5.1 |
| 登録の完了（U3）は bcrypt の間も接続と招待の行の排他を持つ。契約 C2 と BR5.3 は変えない | U3 の NFR5.2（Q4 A） |
| 2本目の接続の経路は performance-validation の k6 で、待ちの時間切れの累計 0 と監査の件数で確かめる | NFR5.2、`project.md` の Corrections・Testing Posture |
| 今のパスワードの誤りが続いても制限しない | NFR4.5（Q2 A）、BR4.5 |
| ログイン・更新の応答の4つは既存の読み取りから作り、問い合わせを増やさない | NFR6.5、BR6.1 |
| 新しい依存・指標・警報は足さない | `tech-stack-decisions.md`、NFR6.8・NFR6.9 |
| 手を入れた `packagesJudgedByTotal` のパッケージは一覧から外して下限を満たす。一覧・除外を増やさない | `team.md` の Testing Posture、NFR9.6 |
| V7 は前進のみ。V1〜V6 は書き換えない。戻しの手順とバックアップは deployment-pipeline・deployment-execution | NFR10.1・NFR10.5 |
| 監査の対象の2列は空を許す整数、参照の制約と索引は置かない | BR9.2 |
| `user` は `auth` と `audit` に依存しない（既存の ArchUnit） | `AuthBoundaryArchitectureTest` |

---

## Q1. `/api/me/` の API で、本人の利用者 ID と送り手の情報（接続元 IP・User-Agent・トレースID）をどう取りますか？

理由: 新しい `user.web` は、認証された本人の利用者 ID（BR8.1）と、監査に載せる送り手の情報（`entities.md` の「既存の ClientInfo と同じ取り方」）を要ります。ところが、主体の型 `AuthenticatedUser` と `ClientInfo`・`ClientInfoResolver` は `auth` にあり、既存の構造の検査が「user does not depend on auth」を禁じています（`auth` が `user` の照合を使うため、逆に頼ると互いに依存する形になる）。DSL の管理（`dslmanage`）は `auth` に依存してよいため、この問題は今回初めて出ます。

A. `user.web` の中で取る。本人の利用者 ID は Spring Security の標準の `Authentication#getName()`（既存の `AuthenticatedUserToken` が利用者 ID を返す）から読み、数でなければ 401 にする。送り手の情報は、既存の `ClientInfoResolver` と同じ取り方（接続元 IP、User-Agent を 512 文字で切る、トレースIDは `common.observability.TraceIdProvider`）で `user` の中の小さな型に作る。既存のコードと構造の検査を変えず、手を入れる既存のパッケージも増えない。代わりに、取り方が2か所に並び、`getName()` が利用者 ID であることに頼る（どちらも結合テストで固定する）
B. `ClientInfo`・`ClientInfoResolver` を `common` へ移し、主体から利用者 ID を取る小さな口（例: `common.security` のインターフェース）を置いて `AuthenticatedUser` がそれを満たす形にする。取り方が1か所になる代わりに、`auth`・`access`・`dslmanage` の既存のコードの参照を直し、手を入れる既存のパッケージ（`common.web` または `common.security`・`access.web` など）が増えて、それぞれをパッケージごとの下限に戻す作業が加わる
C. `user.web` から `auth.domain`（`AuthenticatedUser`・`ClientInfo`）への依存を許すよう構造の検査を緩める。コードは最も少ないが、`user` と `auth` が互いに依存する形になり、既存の境界のテストを緩めるためコード生成の計画に明記して依頼者の承認が要る（`team.md` の Code Style）
X. Other (please specify)

[Answer]: A

## Q2. 入力の誤り（400 VALIDATION_FAILED）の項目ごとの誤りを、どの形で返しますか？

理由: 機能設計で、プリファレンスの保存とパスワードの変更の入力の誤りに「項目の名前と理由（入れた値は返さない）」を付けると決め（BR3.2・BR4.1・BR8.4）、U7 の画面はその形を1か所で読むと設計しています（U7 の W12・10節の (c)）。ところが、今の共通のエラー応答の仕組み（`GlobalExceptionHandler`）は VALIDATION_FAILED に項目ごとの誤りを載せず、契約 C4 にも形がありません。U3 の登録の完了も同じ決まりの関数で入力を検証するため、同じ形を使う見込みです。既存の DSL の誤りの一覧は追加の項目 `errors` を使っています。

A. 業務処理が誤りをすべて集め、既存の `BusinessException`（VALIDATION_FAILED と追加の項目）で返す。追加の項目の名前は `fieldErrors`（DSL の `errors` と重ねない）、形は `[{ "field": "<C4 の項目名>", "reason": "<理由の値>" }]`、1つの項目に理由は1つ（決まりの順で最初に当たったもの）。理由の値は決まった一覧（`REQUIRED`・`TOO_SHORT`・`TOO_LONG`・`INVALID_CHARACTER`・`INVALID_VALUE`・`MISMATCH`）。`GlobalExceptionHandler` は変えず、ほかの API の VALIDATION_FAILED は今のまま。形は U2 の成果物に書き、U3・U7 はそれに合わせる。契約 C4 には後の段で項目の追加（安全な変更）として反映する
B. Bean Validation の注釈で要求の DTO を検証し、`GlobalExceptionHandler` を広げて、すべての API の VALIDATION_FAILED に項目ごとの誤りを載せる。仕組みは1か所になるが、氏名の Cc・Cf の判定などを独自の注釈にする必要があり、既存のログインなどの VALIDATION_FAILED の応答も変わる。手を入れる既存のパッケージ（`common.error.web`）が増え、パッケージごとの下限に戻す作業が加わる
C. A と同じ作りで、追加の項目を RFC 9457 の例にならった名前 `invalid-params`・`[{ "name", "reason" }]` にする（`reason` は A の理由の値）。標準の例に近いが、名前の中の `-` のため JavaScript で読むときに添字の書き方が要る
X. Other (please specify)

[Answer]: A

## Q3. パスワードの変更で、照合から書き込みまでの間に同じ利用者の別の変更が入ったときはどうしますか？

理由: NFR5.1 で bcrypt をトランザクションの外に置くため、保存されたハッシュを読んで照合してから新しいハッシュを書くまでに約 0.3〜0.6 秒の間があり、その間は行を排他しません。同じ利用者のパスワードの変更が2つ同時に来ると（2つの端末、または漏れたトークンと本人）、どちらも古いパスワードで照合を通り、後に書いた方が残ります。機能設計の BR3.4 は、プリファレンスの4列どうしは後勝ちとしましたが、パスワードどうしの同時の変更は決めていません。

A. 書き込みを「読んだときのハッシュのままなら書き換える」条件つきの更新にする。書き換えた行が 0 なら読み直し、本人がいなければ 401、ハッシュが変わっていれば今のパスワードがもう今のものではないとして 400 PASSWORD_CURRENT_MISMATCH を返し、PASSWORD_CHANGED・FAILURE・CURRENT_PASSWORD_MISMATCH を監査に残す（新しい code・理由は足さない）。古いパスワードでの上書きを防げる代わりに、先に通った変更の直後に本人が送った変更も拒否されうる（まれ）
B. 条件をつけず、password_hash だけを書き換える（後に確定した方が残る。BR3.4 の考え方をパスワードにも当てる）。作りは最も簡単だが、先に変えた新しいパスワードが、古いパスワードで照合を通った別の要求で黙って上書きされうる（どちらも成功の監査が残る）
X. Other (please specify)

[Answer]: A

## Q4. V7 の displayName の列の後方互換を、どう作ってどう確かめますか？

理由: 承認済みの BR9.1 では displayName を「既存の行にメールアドレスを入れてから必須にする」とし、既定の値を持ちません。1つ前の版のアプリが利用者を作ると追記が失敗しますが、前の版が利用者を作るのは利用者が1人もいないときの初期管理者の作成だけで、V7 を当てた内部DB には必ず利用者がいるため起きない見込みです（NFR10.4）。ほかの見込み（Flyway 12 の既定が知らない新しい移行を無視する、Hibernate の `validate` が余分な列を許す）もまだ確かめていません（NFR10.3）。実地の確かめ（戻しの練習）は deployment-pipeline・deployment-execution が持ち主です。

A. 承認どおり既定の値なし・必須のまま作る。確かめは2段にする: (1) 自動の結合テストで、V7 まで当てた内部DB に対して V1〜V6 だけを知る Flyway の設定（移行の置き場を V6 までの複写に向ける）で検証が失敗しないこと、1つ前の版と同じく足した4列を渡さない利用者の追記は display_name が無いため失敗し（language・theme・fontSize は既定の値で入る）、足した2列を渡さない監査の追記は通ること（既知の限界を固定する）を確かめる。(2) deployment-execution の戻しの練習で、1つ前の版のイメージを V7 の後の内部DB の複写で起動し、健全性・ログイン・トークンの更新・監査の記録を確かめる（手順は deployment-pipeline で書く）
B. A の (2) だけにする（自動の結合テストを足さない）。テストのコードは増えないが、見込みが外れていたと分かるのが配備の段まで遅れる
C. displayName を内部DB では空を許す列のままにし、アプリの側だけで必須を守る。1つ前の版が初期管理者を作っても失敗しなくなり、既知の限界（NFR10.4）が無くなる代わりに、承認済みの BR9.1（必須にする）と違う作りになり、空の行を読んだときの扱い（メールアドレスで代える）が要る。確かめ方は A と同じ
X. Other (please specify)

[Answer]: A

---

## Consolidated Summary Confirmation

答えのまとめ:

- 設計の要点（案）は冒頭の 20 件のとおり（パスワードの変更は 検証 → 短い読み取り → 接続なしで照合と新しいハッシュ → 短い書き込み、トランザクションの境界は `user.service` で `TransactionTemplate` など、利用者の作成は bcrypt の前に登録済みを確かめる、要求の DTO は文字列で受けてまとめて検証、部分の書き換えは更新の問い合わせ、監査は `user.service` の出来事を `AuditEventListener` で受ける、新しい指標・警報・依存・制限は足さない、手を入れた既存のパッケージはパッケージごとの下限に戻す）
- Q1 A: 本人の利用者 ID は `user.web` の中で `Authentication#getName()` から読み、送り手の情報（接続元 IP・User-Agent・トレースID）は同じ取り方で `user` の中の小さな型に作る。既存のパッケージと構造の検査（user は auth に依存しない）は変えない。U3 も同じ考え方を引き継ぐ
- Q2 A: 項目ごとの誤りは既存の `BusinessException` の追加の項目 `fieldErrors: [{field, reason}]` で返し、reason は REQUIRED・TOO_SHORT・TOO_LONG・INVALID_CHARACTER・INVALID_VALUE・MISMATCH とする。共通の変換（GlobalExceptionHandler）は変えない。U3 の登録の完了と U7 の画面もこの形に頼る（契約 C4 への反映は後の段）
- Q3 A: パスワードの書き換えは、読んだときのハッシュのままなら書き換える条件つきの更新にする。書き換えた行が 0 なら、利用者がいなければ 401、ハッシュが変わっていれば 400 PASSWORD_CURRENT_MISMATCH を返し、FAILURE を監査に残す
- Q4 A: displayName は承認どおり既定の値なしの必須の列のまま作り、2段で確かめる。自動の結合テストで V6 までしか知らない Flyway の検証が通ることと既知の限界（displayName を渡さない追記は失敗する）を固定し、実地では deployment-execution の戻しの練習で1つ前の版のイメージを V7 の後の内部DB の複写で起動する

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
