# Logical Components — U2 利用者のプリファレンスとパスワードの変更（u2-user-preferences）

U2 の論理的な部品の一覧と、NFR の作りがどこに当たるかです。アプリは1つの WAR・1台で、部品はどれも同じ JVM の中のパッケージです（`team.md` の Code Style の層の分け方）。出典の略号は `performance-design.md` と同じ。

## 1. 部品

| 部品 | パッケージ | 新しい・手を入れる | 役目 | 当たる NFR の作り |
|---|---|---|---|---|
| MeController | `user.web`（新しい） | 新しい | GET・PUT `/api/me/preferences`、POST `/api/me/password`。要求の DTO（文字列で受ける）と結果の型から応答への変換 | 認可と本人の取り方（`security-design.md` 1節・2節）、項目ごとの誤り（同 3節） |
| MeRequestContextResolver | `user.web` | 新しい | `Authentication#getName()` から本人の利用者 ID、要求から送り手の情報を作る。401 の問題の種類を `ProblemTypeRegistry` から起動時に引く | `security-design.md` 2節 |
| UserPreferencesService | `user.service` | 新しい | 取得・保存・パスワードの変更。トランザクションの境界（`TransactionTemplate`）、結果の型 | `performance-design.md` 2節・3節、`reliability-design.md` 2節・3節 |
| UserAccountService | `user.service` | 手を入れる | `UserSummary` に4つを足す、`createUser` を C2 の形（NewUser・結果の型、bcrypt の前の登録済みの確かめ、巻き戻しの印）に広げる | `performance-design.md` 4節・5節、`reliability-design.md` 4節 |
| InitialAdminInitializer | `user.service` | 手を入れる | 初期値つきで `createUser` を呼び、結果の型で判定する | BR5.4、`reliability-design.md` 4節 |
| DisplayName・表示の設定の値・FieldError と理由の列挙 | `user.domain` | 新しい | DB を使わない純粋な検証。項目ごとの誤りの型（U3 も使う） | `security-design.md` 3節・5節 |
| RequestOrigin | `user.domain` | 新しい | 送り手の情報の値（sourceIp・userAgent を 512 文字で切る・traceId） | `security-design.md` 2節 |
| PasswordChangedEvent | `user.domain` | 新しい | パスワードの変更の出来事（パスワード・ハッシュ・メールアドレスを持たない） | `reliability-design.md` 1節 |
| UserProblemTypes・UserProblemTypeCatalog | `user.domain`・`user.service` | 新しい | PASSWORD_CURRENT_MISMATCH（400、ja・en） | `security-design.md` 6節 |
| User・UserRepository | `user.domain`・`user.repository` | 手を入れる | 4列の対応、4列だけ・password_hash だけの更新の問い合わせ（条件つき） | `reliability-design.md` 2節・3節 |
| AuditEvent・AuditEventType・AuditFailureReason・AuditEventFactory | `audit.domain` | 手を入れる | 対象の2列、PASSWORD_CHANGED・CURRENT_PASSWORD_MISMATCH、出来事からの組み立て | `reliability-design.md` 1節 |
| AuditEventListener | `audit.service` | 手を入れる | PasswordChangedEvent の受け取り、失敗の ERROR の項目に2列 | `reliability-design.md` 1節、`observability-design.md` 2節 |
| ログイン・更新の応答 | `auth.service`・`auth.web` | 手を入れる | `CurrentUserResponse` に4つを足す（`UserSummary` から写す） | `performance-design.md` 4節 |
| V7 | `db/migration` | 新しい | users の4列、audit_events の2列 | `reliability-design.md` 6節 |

- `PasswordChangedEvent` を `user.domain` に置くのは、監査の組み立て（`audit.domain.AuditEventFactory`）が既存の出来事をどれも各機能の `domain` から読むため（`security-design.md` の S-D3）。
- 依存の向き: `user` は `auth`・`audit` に依存しない（既存の ArchUnit を緩めない）。`audit` → `user.domain`、`auth` → `user.service` の向きだけ。`user.web` は `common.error`・`common.observability` に依存してよい。

## 2. 後の単位が頼る形

後の単位の設計とコード生成は、次の2つを正として読みます。形の中身は `security-design.md` の節に1回だけ書き、ここには置き場と使い方だけを書きます。

| 形 | 正の置き場 | 使う単位 | 使い方 |
|---|---|---|---|
| 本人の利用者 ID と送り手の情報の取り方（Q1 A） | `security-design.md` 2節 | U2 の `user.web`。`user` の中に API を足す後の単位 | `MeRequestContextResolver` を使う。U3 は `invitation` が `auth` に依存してよいかを自分の設計で決め、依存できるなら既存の `ClientInfoResolver` を使ってよい |
| 入力の誤りの項目ごとの誤り `fieldErrors: [{field, reason}]` と reason の一覧（Q2 A） | `security-design.md` 3節 | U3（登録の完了、C6）、U6・U7（画面の読み取り） | U3 は `user.domain` の FieldError と理由の列挙を使い、C6 の項目名で返す。U7 は `fieldErrors.ts` でこの形だけを読む（U7 の W12・10節の (c)）。知らない reason は一般の文言で出す |

- 契約 C4（と U3 の C6）への `fieldErrors` の反映は後の段で行う。遅くとも U2 のコード生成の計画で、契約の文書に項目の追加（安全な変更）として反映したかを確かめる。

## 3. 障害の範囲

| 障害 | 範囲 | 範囲を狭める作り |
|---|---|---|
| bcrypt の CPU の混み | パスワードの変更とログイン・登録の完了が同じ CPU を分け合う | bcrypt の間は接続を持たない。cost は変えない |
| 接続プールの不足 | 内部DB を使うすべての API | U2 は成功のパスワードの変更だけが2本。bcrypt の間は0本 |
| 監査の書き込みの失敗 | 監査の1件だけ | 受け止めて ERROR、元の応答は変えない |
| V7 の失敗 | アプリの起動 | 起動を止め、バックアップから戻す |
| `AuthenticatedUserToken#getName()` の意味の変更 | `/api/me/` の3本 | 結合テストで固定し、`auth` の側のコメントに頼られていることを書く |

## 4. 共有するもの

| 共有するもの | 持ち主 | U2 の使い方 |
|---|---|---|
| 内部DB（組み込みの H2）と接続プール（上限 30・待ち 5 秒） | 既存 | 設定を変えない |
| `PasswordEncoder`（bcrypt、cost 12） | `user.service.UserAccountConfig` | 照合と新しいハッシュに使う |
| SecurityFilterChain と `/api/` の既定のログイン必須 | 既存（`config`・`common.security`・`auth`） | 変えない |
| 問題の種類の一覧（`ProblemTypeRegistry`） | `common.error.service` | PASSWORD_CURRENT_MISMATCH を足し、AUTHENTICATION_REQUIRED を code で引く |
| 監査の表と記録の仕組み | `audit` | 2列と種類・理由を足す（列の一覧の正は U2、C8） |

## 5. テストとカバレッジの作り（NFR9.6・NFR9.7）

- パッケージごとの下限に戻す範囲: 手を入れる `user.domain`・`user.repository`・`user.service`・`audit.domain`・`audit.service`・`auth.service`・`auth.web` を `backend/build.gradle.kts` の `packagesJudgedByTotal` から外し、行 80%・分岐 70% を満たす。ほかに実際に手を入れた既存のパッケージ（例: `audit.repository`）があれば同じく外す。新しい `user.web` は自動で対象になる。Q1 A・Q2 A により `common.*` と `access.*` には手を入れない。
- 前の記録で単独では下回っていた `audit.service`（行 77.2%）は、PasswordChangedEvent の受け取りと既存の分岐のテストを足して上げる。一覧・計測の除外は増やさない。
- 実測は、テストを足した後に `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で行い、パッケージごとの値を記録する（`project.md` の Testing Posture）。
- 必須のテスト: パスワードの変更（今のパスワードの確かめ、規則の境界、変更の後にほかの端末のリフレッシュトークンとアクセストークンが使えること、同時の変更 Q3 A）、認可（401・200・204）、監査（成功と失敗の必須の項目、書き込みの失敗、巻き戻し）、秘密の漏えい（`*SecretLeakIT` の形）、V7 の後方互換の自動の確かめ（`reliability-design.md` 6.2 の (1)）。DisplayName・PasswordPolicy・表示の設定の値・項目ごとの誤りの理由の決め方は jqwik の性質ベースのテスト（失敗時の種を記録）。説明文は英語、単体は `XxxTest`・結合は `XxxIT`。
- ArchUnit: 既存の層と機能の境界のテストを緩めない。`user.web` が `user.repository` を直接呼ばないこと、`user` が `auth`・`audit` に依存しないことは既存のテストで確かめられる。
