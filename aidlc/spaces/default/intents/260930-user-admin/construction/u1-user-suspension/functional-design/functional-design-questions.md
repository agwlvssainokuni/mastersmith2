# Functional Design の質問 — u1-user-suspension

単位 U1（`aidlc/spaces/default/intents/260930-user-admin/inception/units-generation/unit-of-work.md`、種類 library）の機能設計のための質問です。受け持つストーリーは US3.2（AC3.2.1〜AC3.2.10）、関わるストーリーは US3.1（止める・解く操作は U3、`aidlc/spaces/default/intents/260930-user-admin/inception/user-stories/stories.md`）。契約は C1（停止の状態とトークンのまとめての無効化）・C7（停止中のログインの失敗の理由）と、C8 のうち U1 に関わる前提（`aidlc/spaces/default/intents/260930-user-admin/inception/contract-design/contract-summary.md`）。ADR は ADR-002・ADR-003・ADR-007 の項目4・ADR-008（`aidlc/spaces/default/intents/260930-user-admin/inception/domain-design/decisions.md`）。既存のコードは `backend/src/main/java/cherry/mastersmith/auth/`（`LoginService`・`TokenRefreshService`・`AccessTokenService`・`web/AccessTokenAuthenticationProvider`・`web/TokenAuthenticationEntryPoint`・`repository/`・`domain/`）、`user/`（`UserAccountService`・`UserSummary`・`domain/User`）、`access/domain/AccessDeniedReason`、`audit/domain/`（`AuditFailureReason`・`AuditEventFactory`）、`backend/src/main/resources/db/migration/`（V1〜V8）、`backend/build.gradle.kts` の `packagesJudgedByTotal` を確かめました。

## 設計の要点（案）

上流の決定とコードの確認から導ける、この単位の機能設計の見通しです。質問の答えで決まる点は（Qn）と書きます。

1. **エンティティ User を広げる**: 既存の User（`users` の表）に、停止の状態 `suspended`（真偽、必須、既定は false）を足す。エンティティにするのはこの列だけで、リフレッシュトークン（RefreshToken）とロックの状態（LoginAttemptState）は既存のものを使い、形を変えない。
2. **スキーマの変更（Flyway V9）**: `users` に `suspended BOOLEAN DEFAULT FALSE NOT NULL` を足す（V7 の `language` などと同じ書き方）。既存の利用者は既定の値で「有効」になる（NFR10）。1つ前の版のアプリが利用者を作る経路（初期管理者の自動作成・登録の完了）は列を書かないが、既定の値で入るため後方互換を保てる。1つ前の版のアプリは停止の列を読まないため、戻した間は停止が効かない（戻し方の手順に書く点。基盤の設計・配備の段で扱う）。ファイル名は既存の形にそろえて `V9__u1_user_suspension.sql` とする。
3. **入口が読む利用者の要約に停止の状態を足す**: `UserSummary`（`user.service`、`auth` に渡す形）に `suspended` を足す。`UserSummary` を作るのは `UserAccountService.toSummary` の1か所だけで、ログインの照合（`verifyPassword` の結果）・トークンの更新（`findById`）・アクセストークンの認証（`findById`）の3つの入口が同じ値を読む。読みの回数は増えない（ADR-002）。`CurrentUserResponse`・`TokenResponse` などの応答には載せない（停止中の利用者は応答を受け取れないため）。
4. **C1 の UserAccount の口**: `isSuspended`（利用者 ID から今の停止の状態を返す）と `setSuspended`（呼び出し元のトランザクションに入り、拒否の判定はしない）を `UserAccountService` に置く。U1 の中では呼ぶ側が無く、U3 の止める・解く処理が使う。U1 のテストは、この口かテストのデータで停止の状態を入れて確かめる（`unit-of-work.md` の確かめ）。
5. **ログインの照合（ADR-008）**: `LoginService.decide` の中で、利用者の行を排他つきで読んだ後、`LockPolicy.decide`（ロックの判定）より前に停止を確かめる。停止中なら、失敗回数と解除の予定の時刻を読んだ値のまま書き戻す更新を1回行い、出来事 `LOGIN_FAILED`（理由 `ACCOUNT_SUSPENDED`）を知らせて、ほかの失敗と同じ `AUTHENTICATION_FAILED`（401）で終える。パスワードの照合は今までどおりトランザクションの外で必ず1回行う（`verifyPassword` は停止の状態にかかわらず照合する）。どの行で読み書きの回数をそろえるかは（Q1）。
6. **トークンの更新（FR3.2・FR3.4）**: `TokenRefreshService.refresh` で利用者を読んだ（`findById`）直後に停止を確かめ、停止中なら今の失敗と同じ `REFRESH_FAILED`（401）で拒否する。出されたリフレッシュトークンの扱いは（Q2）。
7. **アクセストークンの認証（FR3.2・FR3.8）**: `AccessTokenAuthenticationProvider.authenticate` で利用者を読んだ直後に停止を確かめ、停止中なら `TokenAuthenticationException` を投げて、今の 401 `AUTHENTICATION_REQUIRED` にする（`TokenAuthenticationEntryPoint` は変えない）。止めた直後の次の要求から効く（要求ごとに DB から読むため）。停止を解いた後の、止める前のアクセストークンは有効期限まで再び使える（AC3.2.7、ストーリーの差1）。例外の区分と、管理の API の 401 の監査の扱いは（Q3）。
8. **リフレッシュトークンのまとめての無効化（C1、ADR-003）**: `RefreshTokenRepository` に「利用者 ID の、まだ無効にしていない行（`revoked_at` が空）の `revoked_at` を今の時刻にする」更新の問い合わせを1つ足し、`auth.service` の口 `revokeAllRefreshTokens` から呼ぶ。トランザクションは呼び出し元に入る（MANDATORY）。期限切れの行も対象に含める（害が無く、問い合わせが単純になる）。`refresh_tokens.user_id` には既存の索引（`ix_refresh_tokens_user_id`）がある。無効にした件数を返し、0 件でも成功とする。この口は監査の出来事を出さず、止める操作の監査（U3 の `USER_SUSPENDED`、C6）に含めて扱う。アプリのログには利用者 ID と件数だけを DEBUG で出す（トークンの値・ハッシュは出さない）。
9. **監査（C7）**: `LoginFailureReason`（`auth.domain`）と `AuditFailureReason`（`audit.domain`）に `ACCOUNT_SUSPENDED`（17 文字、32 文字に収まる）を足し、`AuditEventFactory` の変換に1行足す。出来事の種類は既存の `LOGIN_FAILED` のまま。監査の表の列は足さない。監査は確定の後に記録し、書き込みの失敗で元の操作を変えない（既存の決まり）。
10. **列挙の防止（NFR2）**: 3つの入口とも、応答（状態コード・`code`・説明文）はほかの失敗と同じにする。ログインは、パスワードの照合1回、ロックの状態の行の排他つきの読み取り1回、更新1回、出来事1件で、パスワードの誤りと同じ回数になる（Q1 で行を決める）。確かめは既存の `LoginServiceTest` の形（読み書きと照合の呼び出しの回数を数える）で行う（AC3.2.3）。
11. **画面**: 応答がほかの失敗と同じのため、画面は変えない（AC3.2.9・AC3.2.10 は今の画面の動作で満たす）。画面の代表の流れの確かめは U5 の E2E（ストーリーの M9 A）で行う。
12. **停止中の管理者の管理の API（AC3.2.5）**: 3つの入口の判定の結果として 401 になる。U1 の時点では U3 の API がまだ無いため、既存の管理の API（招待の一覧など）で確かめ、U3 の API でも U3 が確かめる。
13. **構造の決まり（NFR11）**: `user` は `auth` を知らないまま。停止の判定は `auth` が `UserSummary` の値で行い、`user` には列と口だけを足す。既存の ArchUnit の境界テストは緩めない。
14. **リポジトリの作業**: `.idea/.gitignore` に `dataSources.xml` を足す。`/dataSources/` はすでにある（今は `/dataSources/` と `/dataSources.local.xml` だけ）。
15. **カバレッジの一覧から戻す**: 手を入れる `auth.domain`（`LoginFailureReason`・`TokenFailureReason`）と `auth.repository`（`RefreshTokenRepository`）は、テストを足して下限（行 80%・分岐 70%）を満たし、`packagesJudgedByTotal` から外す（`team.md`）。`auth.repository` の分岐は今 50%（2つのうち1つ。`lockDummyForUpdate` の「空いたダミーの行が無い」側）で、ダミーの行 8 つをすべて排他した状態を作るテストが要る。Q3 の答えによっては `access.domain`（一覧にあり、Delivery Planning では見積もっていない）にも手が入る。

## 決まっていること（質問にしない）

| 決まっていること | 出典 |
|---|---|
| 停止の状態は `users` の列として UserAccount が持つ。入口は今と同じ口で利用者を読み、停止の判定は Authentication が行う | ADR-002 |
| 停止の状態は真偽で、既存の利用者は false（有効）。口は `isSuspended`・`setSuspended` | C1 |
| スキーマの変更は Flyway の V9、前進のみ・1つ前の版のアプリが動く後方互換 | NFR10、`team.md` の Deployment |
| 停止の理由は持たない | RQ12、FR3.1 |
| 停止中は3つの入口のすべてでサーバー側で拒否する。応答は入口ごとのほかの失敗と同じ（ログインは `AUTHENTICATION_FAILED`、更新は `REFRESH_FAILED`、認証は `AUTHENTICATION_REQUIRED`、いずれも 401） | FR3.2・FR3.4、AC3.2.1〜AC3.2.3、PM の Mandated |
| ログインは停止の確かめをロックの判定より前に置き、失敗回数・解除の予定の時刻・ロックの状態を変えない | ADR-008、ストーリーの差5、AC3.2.8 |
| ログインの読み書きの回数とパスワードの照合の回数を、パスワードを誤ったときと同じにする | ADR-008、ADR-007 の項目4、AC3.2.3 |
| 停止中のログインの失敗は、監査に理由 `ACCOUNT_SUSPENDED` で残す（出来事の種類は `LOGIN_FAILED`） | C7、AC3.2.8 |
| トークンの更新・アクセストークンの認証で停止中だったことには、新しい監査を足さない | C7 の not_recorded |
| 止めたときのリフレッシュトークンの無効化は、止める処理と同じトランザクションで直接呼ぶ口（`revokeAllRefreshTokens`、無効にした件数を返す） | ADR-003、C1 |
| 停止を解いても、止める前のリフレッシュトークンは使えない | FR3.3、AC3.2.4 |
| アクセストークンに失効の仕組みは持たない。停止を解いた後、止める前のアクセストークンは有効期限まで使える | PM の DECIDED、ストーリーの差1、AC3.2.7 |
| 止める操作とトークンの更新が同時に重なったときの隙は塞がない | ストーリーの M8 B、ADR-003 |
| 停止中の利用者のメールアドレスは「登録済み」のまま（招待を拒否する） | 要件の前提 A1、AC3.2.6 |
| 止める・解く操作そのもの（拒否の判定・監査の出来事 `USER_SUSPENDED`・`USER_RESUMED`）は U3 | `unit-of-work.md`、C6 |
| 時刻は注入した時計（`Clock`）で動かし、実時刻と `sleep` に頼らない | `team.md` の Testing Posture、AC3.2.7 |
| `.idea/.gitignore` の作業は U1（最初の Bolt B1） | UQ2 A、`bolt-plan.md` の B1 |

---

## Q1. 停止中のログインで、読み書きの回数をそろえる行

ADR-008 は「停止中でも、パスワードの照合とロックの状態の行の読み書きの回数を、パスワードを誤ったときと同じにする（ダミーの行を使うなどの形は機能設計で決める）」としています。今の `LoginService` は、利用者がいれば本人の行を排他つきで読み（`lockForUpdate`）、判定して更新を1回行います。利用者がいなければダミーの行（ID −1〜−8）を排他つきで読み（`lockDummyForUpdate`）、読んだ値のまま更新を1回行います。本人の行が無いとき（古い利用者など）は、別のトランザクションで行を作ってから判定をやり直します。
理由: ADR-007 の項目4で、回数をそろえる形が成り立つかをこの段で確かめて決めることになっているため。どちらの形でも回数はそろいますが、排他する行と、行が無いときの流れが変わります。

A. 本人の行を排他つきで読み、読んだ値のまま書き戻す（推奨。パスワードの誤りとまったく同じ流れで、本人の行が無いときの「行を作ってからやり直す」流れも同じになる。U3 の失敗回数の取り消しと同じ行で順番がそろう。行を作っても失敗回数 0・ロックなしのため、状態は変わらない）
B. ダミーの行を排他つきで読み、読んだ値のまま書き戻す（存在しないメールアドレスと同じ流れ。本人の行に触れないが、本人の行が無い利用者ではパスワードの誤り（行を作る流れ）と回数が変わる）
X. Other (please specify)

[Answer]: A

## Q2. 停止中のトークンの更新で、出されたリフレッシュトークンを無効にするか

今の `TokenRefreshService.refresh` は、出されたリフレッシュトークンを条件つきで無効にした後に利用者を読み、失敗はすべて例外で巻き戻します（無効にした書き込みも戻る）。止めたときにリフレッシュトークンはまとめて無効になるため、停止中に届くのは、止める操作とトークンの更新が同時に重なって無効化を逃れたトークン（ストーリーの M8 B の隙）か、すでに無効にしたトークンだけです。
理由: 停止中の更新を拒否するとき、出されたトークンをそのまま残すか、無効にしてから拒否するかで、M8 B の隙が停止を解いた後まで残るかどうかが変わるため。

A. 今の失敗と同じく巻き戻し、出されたトークンは変えない（推奨。M8 B の「塞がない」決定と、更新の失敗はすべて巻き戻す今の決まりのまま。トークンの更新の流れに分岐を足さない）
B. 出されたトークンを無効にして確定してから拒否する（停止中に一度でも使われた隙のトークンは、停止を解いた後に使えなくなる。失敗の道で書き込みを確定させる例外の扱いが1つ増える）
X. Other (please specify)

[Answer]: A

## Q3. アクセストークンの認証で停止中だったときの区分と、管理の API の監査

アクセストークンの認証の失敗は `TokenFailureReason`（`auth.domain`）の区分で表し、`/api/admin/` の下では `AccessDeniedReason.of`（`access.domain`）がこの区分を監査の理由に変えて「アクセスの拒否」を残します（有効期限切れ `TOKEN_EXPIRED` だけは残さない）。`AccessDeniedReason.of` は区分を漏れなく並べる `switch` のため、区分を足すと `access.domain` も直す必要があります。`access.domain` は `packagesJudgedByTotal` の一覧にあり、手を入れるとパッケージごとの下限を満たして一覧から外す作業が付きます（Delivery Planning の B1 では見積もっていない）。C7 は「アクセストークンの認証で停止中だったことには新しい監査を足さない」としています。ストーリーは、既存の区分（利用者がいない など）に寄せると監査を読む人が取り違えると注意しています。
理由: 停止中の管理者が管理の API を呼んだときの監査の残り方と、手を入れるパッケージ（カバレッジの作業）が答えで変わるため。

A. 区分 `USER_SUSPENDED` を足し、管理の API の監査には残さない（有効期限切れと同じ扱い）（推奨。C7 のとおり。停止そのものは U3 の `USER_SUSPENDED`、ログインの試みは `ACCOUNT_SUSPENDED` で残る。`access.domain` に手が入り、一覧から外す作業が B1 に加わる）
B. 区分 `USER_SUSPENDED` を足し、管理の API の監査に新しい理由（`ACCOUNT_SUSPENDED` を使い回す）で「アクセスの拒否」として残す（停止中の管理者の試みが監査で分かる。C7 を直す必要がある（持ち主は U1）。`access.domain` に手が入る）
C. 区分を足さず、既存の `USER_NOT_FOUND` で拒否する（`access.domain` に手が入らない。管理の API の監査には「利用者がいない」として残り、読む人が取り違えうる）
X. Other (please specify)

[Answer]: A

---

## Consolidated Summary Confirmation

答えのまとめと、U1 の機能設計の計画:

- 設計の要点（案）の 1〜15 のとおりに作る。
  - `users` に `suspended`（真偽・既定 false）を V9 で足す。
  - `UserSummary` に `suspended` を足し、3つの入口はこの値で停止を判定する。
  - 停止の判定は `auth` が行い、`user` は `auth` を知らないまま。
  - リフレッシュトークンをまとめて無効にする問い合わせを1つ足す。
  - 監査の理由 `ACCOUNT_SUSPENDED` を足す。
  - `.idea/.gitignore` に `dataSources.xml` を足す。
- Q1 A: 停止中のログインでは、本人のロックの状態の行を排他つきで読み、読んだ値のまま書き戻す。照合1回・排他つきの読み取り1回・更新1回・出来事1件で、パスワードの誤りと同じ回数になる（ADR-007 の項目4は成り立つ）。
- Q2 A: 停止中のトークンの更新は、今の失敗と同じく巻き戻す。出されたリフレッシュトークンは変えない（M8 B の隙は塞がない）。
- Q3 A: アクセストークンの認証の失敗の区分に `USER_SUSPENDED` を足す。管理の API の監査には残さない（有効期限切れと同じ扱い、C7 のとおり）。
  - `AccessDeniedReason.of`（`access.domain`）の網羅の `switch` に1行足すため、`access.domain` にも手が入る。
  - `access.domain` は今、行・分岐とも 100% で下限を満たしているため、B1 で `packagesJudgedByTotal` から外す。
  - Delivery Planning の B1 の見積もりに無かった作業として、functional-spec.md の「上流との差」に記録する。
- カバレッジの一覧から外すのは、B1 では `auth.domain`・`auth.repository`・`access.domain` の3つになる。
  - `auth.repository` の足りない分岐は、`lockDummyForUpdate` の「空いたダミーの行が無い」側。ダミーの行8つをすべて排他した状態を作るテストを足す。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
