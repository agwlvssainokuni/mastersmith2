# Logical Components — U3 招待と登録の完了（u3-invitation）

U3 の論理的な部品の一覧と、NFR の作りがどこに当たるかです。アプリは1つの WAR・1台で、部品はどれも同じ JVM の中のパッケージです（`team.md` の Code Style の層の分け方）。この文書は作りと判断を書き、完成したコードはコード生成で書きます。出典の略号は `performance-design.md` と同じ。

| 要件の節 | 設計の場所 |
|---|---|
| NFR5.1・NFR5.2・NFR6.1〜NFR6.6（性能と接続） | `performance-design.md` |
| NFR1.x・NFR2.1・NFR2.2・NFR3.x・NFR4.x・NFR8.x・NFR9.1〜NFR9.3・NFR9.12（セキュリティ） | `security-design.md` |
| NFR6.7・NFR5.4・NFR6.10（拡張性） | `scalability-design.md` |
| NFR5.3・NFR9.4・NFR9.8〜NFR9.10・NFR9.13・NFR10.x（信頼性） | `reliability-design.md` |
| NFR6.8・NFR6.9・NFR2.3・NFR9.5（観測） | `observability-design.md` |
| NFR9.6・NFR9.7・NFR9.11（テストの道具と品質の関門） | この文書の5節 |

## 1. 部品

パッケージ `cherry.mastersmith.invitation` を新しく作り、既存の4つの層に分けます。

| 部品（例） | パッケージ | 新しい・手を入れる | 役目 | 当たる NFR の作り |
|---|---|---|---|---|
| InvitationAdminController | `invitation.web` | 新しい | 招待・一覧・送り直し・取り消し（C5）。文字列で受ける DTO、結果の型から応答への変換 | `security-design.md` 2節・6節 |
| RegistrationController | `invitation.web` | 新しい | リンクの確かめと登録の完了（C6、ログインなし）。拒否の応答の同一 | `security-design.md` 6節・7節 |
| InvitationRequestContextResolver | `invitation.web` | 新しい | `Authentication#getName()` から管理者の利用者 ID、要求から `user.domain.RequestOrigin` を作る（Q1 A） | `security-design.md` 2節 |
| InvitationSecurityContributor | `invitation.web` | 新しい | 公開の2つの POST（order 310） | `security-design.md` 3節 |
| InvitationService | `invitation.service` | 新しい | 取りまとめ（`@Transactional` なし）、`TransactionTemplate` の短いトランザクション、同時の招待の負けの扱い | `performance-design.md` 2節・3節、`reliability-design.md` 1節〜3節 |
| InvitationMailDispatcher | `invitation.service` | 新しい | 送信の入口の確かめ（トランザクションの外）、U1 の `send` を1回、失敗のときの INFO | `performance-design.md` 2.1、`observability-design.md` 2.1 |
| InvitationAvailability と起動時の設定の確かめ | `invitation.service`（設定の型は `invitation` の中） | 新しい | ベース URL の形、U1 の `isConfigured`、有効期限・保存の日数・時刻の確かめ | `security-design.md` 4節 |
| InvitationCleanupJob・InvitationSchedulingConfig | `invitation.service` | 新しい | 定期の削除、`@EnableScheduling` | `reliability-design.md` 4節 |
| InvitationProblemTypeCatalog | `invitation.service` | 新しい | 5つの code（ja・en） | `security-design.md` 9節 |
| Invitation（エンティティ）・InvitationState・SendResult | `invitation.domain` | 新しい | 招待の行（`pending_email` を持たない） | `reliability-design.md` 2節 |
| InvitationToken・RegistrationUrl・有効の判定・ページの計算 | `invitation.domain` | 新しい | 伏せ字の値の型と純粋な関数 | `security-design.md` 4節・5節、5節（jqwik） |
| 出来事5つ・InvitationProblemTypes | `invitation.domain` | 新しい | 監査の出来事（秘密を持たない）、問題の種類 | `reliability-design.md` 7節 |
| InvitationRepository | `invitation.repository` | 新しい | 排他の読み取り（待ち 3 秒）、条件つきの更新、一覧、削除 | `performance-design.md` 5節・6節、`reliability-design.md` 4節 |
| V8 | `db/migration` | 新しい | 招待の表、生成列と2つの一意の制約 | `reliability-design.md` 2節・8節 |
| 招待メールのテンプレートと一覧の行 | U1 の置き場（`mail/templates`、`MailTemplateCatalog`） | 手を入れる | invitation の ja・en、差し込み `registrationUrl`・`validityHours` | `security-design.md` 9節 |
| AuditEventType・AuditFailureReason・AuditEventFactory・AuditEventListener | `audit.domain`・`audit.service` | 手を入れる | 種類5つ・理由5つ、出来事からの組み立て、受け取り5つ | `reliability-design.md` 7節 |

- 依存の向き: `invitation` → `user.service`・`user.domain`（C2 と `RequestOrigin`・`FieldError`）、`invitation` → `mail.service`・`mail.domain`（C1）、`invitation` → `common`。`audit` → `invitation.domain`（出来事）。`invitation` は `auth` と `audit` に依存しない（Q1 A）。

## 2. 境界の検査（ArchUnit）

既存の `DslBoundaryArchitectureTest` と同じ形で `InvitationBoundaryArchitectureTest`（`backend/src/test/java/cherry/mastersmith/invitation/`）を足します。既存の全体の決まり（`ArchitectureTest`）と機能ごとの境界のテストは緩めません。

| 決まり | 理由 |
|---|---|
| `invitation` は `auth` と `audit` に依存しない | Q1 A、承認済みの `components.md` の `depends_on`。監査は出来事で知らせる |
| `invitation` から `user` へは `user.service` と `user.domain` の値の型だけ（`user.domain` のエンティティと `user.repository` を使わない） | C2 の口だけを使う（`auth` の既存の決まりと同じ考え方） |
| `invitation` から `mail` へは `mail.service` と `mail.domain` だけ | U1 の境界（U1 の `logical-components.md` 3節）と合わせる |
| `invitation` は `dsl`・`dslmanage`・`targetdb`・`access` に依存しない | 関係が無い |
| `invitation` の外で `invitation` に依存してよいのは `audit`（`invitation.domain` の出来事だけ） | 逆向きの知らせは出来事で行う（`team.md` の Code Style） |
| `invitation` の中のトランザクションの境界は `invitation.service` だけ | 既存の全体の決まりと同じ |

## 3. 障害の範囲

| 障害 | 範囲 | 範囲を狭める作り |
|---|---|---|
| SMTP の受け手の停止・遅さ | その招待・送り直しの要求だけ（最長で U1 の時間切れのあたり） | 送信の間は接続を持たない（`performance-design.md` 2.1）。招待は確定したまま FAILED |
| 招待を使える設定でない | 招待と送り直しだけ（503） | 一覧・取り消し・リンクの確かめ・登録の完了は動く |
| bcrypt の CPU の混み | 登録の完了とログイン・パスワードの変更が同じ CPU を分け合う | cost は変えない。登録の完了は1人1回の操作 |
| 接続プールの不足 | 内部DB を使うすべての API | U3 の2本の経路は短い。送信の間は0本 |
| 行の排他の待ち | 同じ招待・同じメールアドレスの操作だけ | 待ち 3 秒で打ち切る |
| 監査の書き込みの失敗 | 監査の1件だけ | 受け止めて ERROR、元の応答は変えない |
| 定期の削除の失敗 | その回の削除だけ | 次の回に任せる |
| V8 の失敗 | アプリの起動 | 起動を止め、バックアップから戻す（deployment-pipeline） |
| U3 の不具合 | 招待と登録の完了の API（ログイン・ほかの機能は動く） | 機能をパッケージで閉じ、共通の設定を変えない |

## 4. 共有するもの

| 共有するもの | 持ち主 | U3 の使い方 |
|---|---|---|
| 内部DB（組み込みの H2）と接続プール（上限 30・待ち 5 秒） | 既存 | 設定を変えない。招待の表を V8 で足す |
| `PasswordEncoder`（bcrypt、cost 12） | `user.service.UserAccountConfig` | `createUser` の中で使われる（U3 は直接使わない） |
| SecurityFilterChain と差し込み口の order | 既存（`config`・`common.security`）、割り当ては U8 の設計 | order 310 の決まりを1つ足す。`auth` のトークンの読み取りは変えない（Q3 A） |
| 問題の種類の一覧（`ProblemTypeRegistry`） | `common.error.service` | 5つの code を足し、AUTHENTICATION_REQUIRED を code で引く |
| 監査の表と記録の仕組み | `audit`（列の一覧の正は U2） | 種類と理由を足す。列は足さない |
| 送信の部品とテンプレートの置き場 | U1（`mail`） | 口（C1）だけを使い、invitation のテンプレートと一覧の行を足す |
| 定期の処理の仕組み（`@EnableScheduling`） | Spring（今は `auth` の設定だけ） | `invitation` の中にも置く |
| ログの設定（`logback-spring.xml`） | 既存 | 変えない（Q5 A） |

## 5. テストの道具とカバレッジ（NFR9.6・NFR9.7・NFR9.11）

- **カバレッジ（NFR9.6）**: 新しい `invitation.web`・`invitation.service`・`invitation.domain`・`invitation.repository` は、自動でパッケージごとの下限（行 80%・分岐 70%）の対象になる。手を入れる既存の `audit.domain`・`audit.service` は B2 で `packagesJudgedByTotal` から外れている前提で、下限を満たす。Q1 A・Q3 A・Q5 A により `auth.*`・`common.*`・`config` には手を入れない。B3 の時点でほかに手を入れた一覧のパッケージがあれば、一覧から外して下限を満たす。一覧と計測の除外を増やさない。実測は `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で行い、パッケージごとの値を記録する。
- **性質ベースのテスト（NFR9.7）**: 有効の判定（BR3.3、注入した時計）・トークンの形（BR3.2）・一覧のページの計算（BR2.3・BR5.2）を DB を使わない純粋な関数（`invitation.domain`）にし、jqwik で確かめる。失敗時の乱数の種を記録する。
- **E2E（NFR9.11）**: 招待から登録の完了までの代表の流れ1本は、画面の単位（u6-registration-ui）と基盤の設計で決める。U3 は、E2E が招待メールのリンクを受け手（Mailpit の API）から取り出せるよう、送るメールの本文に URL の文字を載せる（BR10.3）以上のことをしない。
- **メールのテスト**: U1 が足す SubEtha SMTP を JVM の中で起動して実際に受ける（モックで済ませない）。失敗は閉じたポートと、受け付けて何も返さない `ServerSocket` で起こす。
- **同時の操作のテスト**: 待ち合わせ（テストの中の同期の仕組み）で確実に重ね、`sleep` と実時刻に頼らない（`reliability-design.md` 2.3・3節）。
- 単体は `XxxTest`・結合は `XxxIT`、説明文は英語、内部DB のテストは組み込みの H2（`team.md` の Testing Posture）。
- 性能は既存の k6（`perf/k6`）に場面を足し、受け手に Mailpit を使う（`performance-design.md` 7節、手順書は build-and-test）。

## 上流との差

なし（部品の置き場と依存の向きは承認済みの `components.md` のとおり）。各設計の文書の差は、それぞれの「上流との差」に書いた（`performance-design.md` の PD-D1・PD-D2、`security-design.md` の SD-D1〜SD-D5、`reliability-design.md` の RD-D1〜RD-D4、`observability-design.md` の OD-D1・OD-D2）。
