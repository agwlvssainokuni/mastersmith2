# Code Generation Plan — U3 招待と登録の完了（u3-invitation）

U3 のコード生成の計画を示す。作るものは、新しいパッケージ `cherry.mastersmith.invitation`（招待の表の V8、招待・一覧・送り直し・取り消しの API（契約 C5）、ログインなしのリンクの確かめと登録の完了の API（契約 C6）、招待メールのテンプレート（契約 C10）と U1 への送信の依頼（契約 C1）、U2 の利用者の作成（契約 C2）の呼び出し、招待と登録の監査の出来事（契約 C8）、保存期間を過ぎた招待の定期の削除）である。Bolt は B3（`inception/delivery-planning/bolt-plan.md`）。B1（U1 メール）・B2（U2 利用者の設定）・U8（見た目の設定。B4 から先に独立して作った）は `develop` に統合済みで、先頭は `8d2077d`。U3 は service の単位で、画面を持たない。

この計画の中のパスは、特に断らない限りリポジトリのルートからの相対パスで、記録のディレクトリは `aidlc/spaces/default/intents/260925-user-management/`（以下「記録」）とする。既存のコードの説明文にある「U2・U3」は前の Intent の単位番号で、この計画の U3 は u3-invitation を指す。

## 1. 入力にした設計

| 文書（記録の下） | 使うところ |
|---|---|
| `construction/u3-invitation/functional-design/functional-spec.md`・`rules.md`・`entities.md`・`functional-design-questions.md` | 状態の移り変わり（PENDING・COMPLETED・CANCELLED・REPLACED、期限切れは保存しない）、手順 2.1〜2.8、失敗の場合の表、決まり BR1.1〜BR11.3、答え Q1 C・Q2 C・Q3 A・Q4 A、上流との差（6節）と承認の場の Request Changes の直し（6.1 の R-01・R-02） |
| `construction/u3-invitation/nfr-requirements/` の全文書と `nfr-requirements-questions.md` | NFR1.1〜NFR1.6・NFR2.1〜NFR2.3・NFR3.1〜NFR3.3・NFR4.1〜NFR4.5・NFR5.1〜NFR5.4・NFR6.1〜NFR6.10・NFR8.1・NFR8.2・NFR9.1〜NFR9.13・NFR10.1〜NFR10.3、答え Q1 A（登録の完了は p95 1 秒）・Q2 A（公開の API に回数の制限を設けない、残る危険 R1）・Q3 C（有効期限の長さに上限なし、R2）・Q4 A（登録の完了は bcrypt もトランザクションの中）、新しい依存を足さないこと |
| `construction/u3-invitation/nfr-design/` の全文書と `nfr-design-questions.md` | 部品の一覧と置き場・依存の向き（`logical-components.md`）、生成列 `pending_email` と2つの一意の制約（Q2 A）、公開の道のアクセストークンの扱いを変えない（Q3 A、R3）、送信の入口のトランザクションの外の確かめと使用中の接続 0 の結合テスト（Q4 A）、送信の失敗は U3 の INFO 1件だけ（Q5 A）、order 310、伏せ字の値の型、拒否の応答の1つの経路、行の排他（待ち 3 秒）、定期の削除の形、上流との差 PD-D1・PD-D2・SD-D1〜SD-D5・RD-D1〜RD-D4・OD-D1・OD-D2 |
| `construction/u3-invitation/infrastructure-design/` の全文書と `infrastructure-design-questions.md` | ベース URL は既存の `MASTERSMITH_WEB_BASE_URL`（Q1 A、Origin の確かめとエラー応答の type と共有、I-D1）、招待の3つの設定の項目、V8 の自動の確かめ、負荷の試験の環境に Mailpit とベース URL（I-D2・C-D1）、k6 の8つの場面、E2E への渡し方（`frontend/playwright.config.ts`）、README に足すこと（8節）、上流との差 I-D1〜I-D4・C-D1〜C-D3・M-D1〜M-D3 |
| `inception/contract-design/contract-summary.md` の C1・C2・C5・C6・C8・C10 とエラーの code の一覧 | 送信の口、利用者の作成と読み取りの口、招待の管理の API の形、登録の完了の API の形、監査の種類と理由、招待メールの決まり、5つの code と状態コード |
| `inception/domain-design/components.md`・`decisions.md` | Invitation の持ち物と `depends_on`（UserAccount・Mail）、ADR-001（招待は別の表）・ADR-008（監査）・ADR-009（確定の後にトランザクションの外で送る）・ADR-010（招待中を1件に限る見通しと切り替え先）・ADR-011（`/api/registration/` を差し込み口で公開） |
| `inception/units-generation/unit-of-work.md`・`unit-of-work-story-map.md` | U3 の責務と境界、US1.1・US2.1・US2.2・US3.2 が主、US3.1 に関わる、CR1.2・CR1.4・CR3〜CR5 |
| `inception/requirements-analysis/requirements.md`・`inception/user-stories/stories.md` | FR1〜FR4・FR9・FR10、NFR1〜NFR6・NFR8〜NFR10、US1.1・US2.1・US2.2・US3.1（U3 の受け持ち）・US3.2 の受け入れ基準（機能設計の `traceability.json` の OK と Deferred の分け方） |
| `inception/delivery-planning/bolt-plan.md` の B3 | 完了の条件、見せ方（API の結合テストで招待 → メールの受け取り → 登録の完了 → ログイン）、統合の形（squash） |
| B1・B2・U8 の `code-summary.md`（`construction/u1-mail/`・`u2-user-preferences/`・`u8-instance-appearance/` の `code-generation/`） | U1 の `MailSendResult` の実際の形（`outcome`・`failureKind` の record と `isSent()`）、U1 のテンプレートの置き場と検査、U2 の `NewUser`・`CreateUserResult`・`FieldError`・`RequestOrigin`・`DisplayName`、`packagesJudgedByTotal` の今の12件、U8 の order の割り当て（invitation は 300 台）と引き継ぎ（310 を B3 で確かめる） |

## 2. 承認の場の決定と、この計画での読み方

### 2.1 承認の場の決定（監査ログから洗い出したもの）

`project.md` の学び（計画の前に承認の場の決定を洗い出す）に従い、監査ログ（`audit/sakura-local-4e42a93f87ce.md`）の Functional Design・NFR Requirements・NFR Design・Infrastructure Design・Code Generation の `DECISION_RECORDED`・`GATE_APPROVED`・`GATE_REJECTED` と、レビューの記録（`.aidlc-reviews/*/units/u3-invitation/*/1.json`）の指摘を読んだ。U3 と B3 に関わるものと、この計画での扱いは次のとおり。

| 段 | 決定・指摘 | この計画での扱い | 手順 |
|---|---|---|---|
| 機能設計（Request Changes の U3 R-01、Critical） | 一覧の招待した管理者（invitedBy）は `findDisplayName` の氏名だけにし、利用者の行が無ければ空の文字列。メールアドレスへ切り替えない | BR5.3 のとおり作る。氏名が空のときの空の文字列は業務処理の単体テストで確かめる（今は利用者を消す操作が無いため結合テストでは起きない） | Step 16・17・23 |
| 機能設計（Request Changes の U3 R-02、Major） | 有効期限の長さ（時間の数）を招待メールの差し込み `validityHours` にする。U1 の一覧の差し込みにも足す | テンプレートの一覧の行を `registrationUrl`・`validityHours` の2つにし、48 時間の設定で本文に「48」が出て「24」が出ず、`expiresAt` が＋48 時間になることを招待と送り直しの両方で確かめる | Step 10・11・12・23 |
| 機能設計（承認の場の G1・G6） | 契約 C5 の invitedBy との差は後の段で契約を直す一覧に入れる。契約への反映（C5 invitedBy・C8 targetUserId と EMAIL_ALREADY_REGISTERED・C10 有効期限の文言）は遅くともコード生成の計画で確かめる | 9節の決定 2 のとおり、`contract-summary.md` は書き換えず、差を `code-summary.md` と README の U3 の節に記録する（B2 の決定 2 と同じ扱い） | Step 27・31、9節 |
| 機能設計のレビュー（2回目の U3 R-01、Major、Accepted risk） | 契約 C5 の invitedBy の項目の説明（無ければメールアドレス）との食い違いが機能設計の差の一覧に無い | 上の G1・G6 と同じ扱いで、差の一覧に C5 の invitedBy の項目の説明を明記する | Step 27・31 |
| NFR 要件（承認の場、Minor の拾い上げ） | U3 の BR7.4 の経路は Unverified とし、監査の急な増えに気づく仕組みが無いことを残る危険に書き足す（NFR 設計で反映済み）。U8 の order の説明の U3 の明確化 | BR7.4 の経路は正しさだけを結合テストで確かめ、性能の場面に入れない（Build and Test に引き継ぐ）。order の説明文は U8 で書き直し済みのため、B3 は 310 を使うだけ | Step 19・23、Build and Test に引き継ぐこと |
| NFR 要件のレビュー（U3 R-01・R-02、Minor、Accepted risk） | BR7.4 の性能の扱いの明記、R1 の急な増えに誰がいつ気づくか | BR7.4 は上のとおり。R1 の問い合わせを流す頻度と担当は observability-setup に引き継ぐ | Build and Test に引き継ぐこと |
| NFR 設計（承認の場、U3 R-01、Major） | 説明用の断片は `MailSendResult` を枝分かれする型として書いていた。U1 の設計（実装）を正として、コード生成で形を合わせる | U1 の実装どおり `result.isSent()`（または `outcome()`）と `result.failureKind()` で扱う。`MailSendResult.Failed` の型は使わない | Step 12・16 |
| NFR 設計（承認の場、U3 R-02、Minor） | R1 の問い合わせの運用の手順（頻度・担当）を observability-setup か deployment-pipeline に引き継ぐ | 引き継ぎの表に書く | Build and Test に引き継ぐこと |
| NFR 設計（承認の場、上流との差 A5） | `common.security` の説明文の書き直しとカバレッジの一覧の扱いは B3 か B4 の計画で確かめる | U8 で済み（説明文を機能の名前の割り当てに書き直し、`common.security` を `packagesJudgedByTotal` から外した）。B3 は `common.security` に手を入れない | Step 25 |
| NFR 設計（Q2 A、`reliability-design.md` 2.3） | 生成列 `pending_email` と一意の制約を、B3 の最初の結合テストで Flyway の V8 と Hibernate の `validate` の上で確かめる。成り立たなければ固定の1行の排他に切り替える | Step 7 で (a)〜(d) を確かめる。成り立たないときは、切り替えは V8 の中身が変わるため、生成をその手順で止めて依頼者に諮る（9節の決定 4 の (a)） | Step 7、9節 |
| NFR 設計（Q3 A・R3） | 公開の2つの道でもアクセストークンを読む今の扱いを変えない | 壊れたトークン付きの POST が 401 になることを結合テストで固定する。`auth` と `common.security` には手を入れない | Step 22・23 |
| NFR 設計（Q4 A） | 送信の入口でトランザクションの中なら止める確かめと、送信の最中の使用中の接続 0 の結合テスト | `InvitationMailDispatcher` の入口の確かめと、単体テスト・結合テストの両方で確かめる | Step 12・13・23 |
| NFR 設計（Q5 A） | 送信の失敗の WARN は U1 の1件だけ。U3 は失敗のときだけ INFO 1件（invitationId・operation・failureKind） | そのとおり作り、ログの取り込みで件数と項目を確かめる | Step 12・13・23 |
| NFR 設計（`security-design.md` 3.1）・U8 の引き継ぎ | 差し込み口の order は 310（U8 の割り当て、SD-D1） | `InvitationSecurityContributor` の order を 310 にする。承認の場でこの値を確かめてもらう（U8 の `code-summary.md` の引き継ぎ） | Step 22・24 |
| NFR 設計（SD-D3） | 招待と登録の完了の 400 に U2 の `fieldErrors` を載せる。C5・C6 への反映は遅くとも U3 のコード生成の計画で行う | 実装は U2 の `security-design.md` 3節の形。契約の文書の扱いは9節の決定 2 | Step 22・23・27 |
| 基盤の設計（承認の場、D6・Q1 A） | ベース URL を既存の `MASTERSMITH_WEB_BASE_URL` と共有し、配備した環境では `http://localhost:8080` を入れる。README に localhost で開くことを書く | README に書く。配備した `.env` の変更は deployment-execution（B3 では `.env` を変えない） | Step 27、Build and Test に引き継ぐこと |
| 基盤の設計（承認の場、N3・N4・N5） | N3 招待の定期の削除の失敗の警報（observability-setup）、N4 127.0.0.1 からのログインで ms-origin が増える（README）、N5 配備の後の確かめで内部DB と監査に行が残るため先に伝える（deployment-execution） | N4 は README に書く。N3・N5 は引き継ぐ | Step 27、Build and Test に引き継ぐこと |
| 基盤の設計（`cicd-pipeline.md` 4節・5節） | 負荷の試験の使い捨ての環境に Mailpit（profile `mail`、PC にポートを公開しない）とベース URL `http://app:8080` を足す。k6 の8つの場面。E2E の `webServer.env` にベース URL | `docker/perf/compose.yaml` の Mailpit と `frontend/playwright.config.ts` のベース URL は B3 で足す。k6 の場面・`perf/README.md` の手順・一時の環境ファイルの行は Build and Test に引き継ぐ（トークンの用意の方法を手順書の段で決める承認のため） | Step 26、Build and Test に引き継ぐこと |
| B1 の引き継ぎ（U1 の `code-summary.md`） | 招待のメールを Mailpit の画面で見る確かめ（NFR11.1）、WAR の中の招待のテンプレートの数え上げのもう一度の確かめ、契約 C10 の文言、`MailSendResult` の形 | Step 28 で AI だけで確かめる（9節の決定 6）。NFR11.1 の「受け手の画面に出る」は Mailpit の API での確かめで代える（8節の差） | Step 10・28 |
| B2 の引き継ぎ（U2 の計画の「Build and Test に引き継ぐこと」） | 登録の完了が `createUser` の `EmailAlreadyUsed` で必ず巻き戻すこと、同じ `DisplayName` の関数で入力を検証すること、C6 の `fieldErrors` が U2 の形を使うこと | 業務処理で自分の巻き戻しの印を付け、`UnexpectedRollbackException` が起きないことを結合テストで確かめる。入力は U2 の `DisplayName`・`PreferenceValueRules`・`PasswordPolicy` で検証する | Step 18・19・22 |

### 2.2 この計画での読み方

- **設計の文書どおりに作るもの**: 部品の構成と置き場と依存の向き（`logical-components.md` 1節・2節）、招待・送り直しの流れと2つの短いトランザクション（`performance-design.md` 2節）、送信の入口の確かめ（同 2.1）、登録の完了の流れと自分の巻き戻しの印（同 3節、`reliability-design.md` 3節）、生成列と招待中の1件（`reliability-design.md` 2節）、行の排他の待ち 3 秒（`performance-design.md` 6節）、定期の削除（`reliability-design.md` 4節）、監査の組み立て（同 7節）、公開の決まり（`security-design.md` 3節）、トークンと URL（同 4節）、項目ごとの誤り（同 6節）、拒否の応答の同一（同 7節）、ログ（`observability-design.md` 2節）。
- **設定の項目の名前と型**（「コード生成で決める」とされたもの）: `mastersmith.invitation.validity`（`Duration`、既定 `24h`、環境変数 `MASTERSMITH_INVITATION_VALIDITY`）、`mastersmith.invitation.retention`（`Duration`、既定 `90d`、`MASTERSMITH_INVITATION_RETENTION`）、`mastersmith.invitation.cleanup.cron`（既定 `0 45 3 * * *`、`MASTERSMITH_INVITATION_CLEANUP_CRON`）。削除の件数の上限は 1000 で定数（既存のリフレッシュトークンの削除と同じ）。有効期限は 0 以下・1 時間で割り切れない値（例 `90m`）で、保存の日数は 0 以下・1 日で割り切れない値で起動を止める。形の誤りは Spring の結び付けで起動が止まる。`validityHours` は `validity.toHours()` の10進の文字列（BR1.6）。ベース URL は既存の `MastersmithWebProperties#baseUrl` を読むだけで、新しい項目を足さない（基盤の設計 3.1）。
- **テンプレート**: `backend/src/main/resources/mail/templates/invitation_ja.html`・`invitation_en.html` を置き、`MailTemplateCatalog.DEFINITIONS` に `invitation`（差し込み `registrationUrl`・`validityHours`）の行を足す。件名（`<title>`）の案は ja「MasterSmith への招待」・en「Invitation to MasterSmith」、リンクの文言の案は ja「登録を完了する」・en「Complete your registration」、有効な期間の文は ja「このリンクは {{validityHours}} 時間有効です」・en「This link is valid for {{validityHours}} hours.」（BR10.2・BR10.3）。U1 の検査（`MailTemplateLintTest`・起動時の置き場の数え上げ・Spotless の Mustache のコメントのヘッダー・`mailTemplateLicenseHeader`）に自動で入る。
- **伏せ字の値の型**（既存の `TraceAspect` が `web`・`service`・`domain`・`repository` の Bean の引数と戻り値を TRACE で文字列にするため）: `security-design.md` 5節の表（`InvitationToken`・`RegistrationUrl`・要求の DTO）に加えて、招待先のメールアドレスを `InvitationEmail`（`invitation.domain`、正規化済みの値、文字列にすると伏せる）で受け渡し、DB アクセスの問い合わせでは SpEL（`:#{#email.value()}`）で取り出す（U2 の `Preferences`・`PasswordHash` と同じ形）。業務処理の戻り値（`InvitationSummary`・`InvitationPage`・`InvitationView`）と入力（`RegistrationCommand`）も、メールアドレス・氏名・パスワードを伏せた文字列にする。U3 が呼ぶ `user.service` の口の扱いは9節の決定 3。
- **送信の結果の形**: U1 の実装どおり `MailSendResult(outcome, failureKind)` の record で、`isSent()` と `failureKind()` を使う（NFR 設計の承認の場の U3 R-01）。U1 の `MailUnexpectedException` は捕まえず既存の 500 の扱いに任せる（招待は確定したまま PENDING、一覧では FAILED）。
- **一覧のページと招待中の案内の page**: 並びは `invited_at` の降順・`invitation_id` の降順。案内の page は「その招待より前に並ぶ PENDING の件数＋1」を位置として `ceil(位置 ÷ 20)`（BR2.3）。計算は `invitation.domain` の純粋な関数、件数は DB アクセスの問い合わせで数える。
- **同時の操作の待ち合わせ**: U2 の `PasswordChangeBarrier` と同じ考え方で、`invitation.service` に待ち合わせの口 `InvitationBarrier`（本番は何もしない既定の Bean）を置き、招待の追記の直前と、行の排他を得た直後で呼ぶ。テストだけで差し替えて同時の操作を確実に重ねる（`reliability-design.md` 2.3・3節）。
- **既存の ArchUnit を緩めない**: `ArchitectureTest` と既存の機能ごとの境界テスト（`MailBoundaryArchitectureTest` の「mail は invitation に依存しない」、`AuthBoundaryArchitectureTest`、`AuditBoundaryArchitectureTest` など）は変えずに通す。`InvitationBoundaryArchitectureTest` を足す（`team.md` の Code Style）。
- **既存の共通の仕組みを変えない**: `SecurityConfig`・`common.security`・`auth`・`access`・`GlobalExceptionHandler`・`ProblemBaseUrlResolver`・`OriginVerifier`・`logback-spring.xml`・`compose.yaml`・`Dockerfile` は変えない。

## 3. 作業の場とコミットの区切り

- **作業のブランチ**: `develop` の先頭（`8d2077d`）から短命のブランチ `feature/260925-user-management-b3` を作る（`team.md` の Way of Working。9節の決定 1）。
- **統合の形**: B3 はサブモジュールの固定先の更新を含まないため、`develop` へ **squash** で統合する（1 Bolt が `develop` の1コミット）。統合コミットのメッセージは日本語で、Bolt の内容が分かる件名にする（例: 「B3 招待と登録の完了（招待の管理の API・登録の完了の API・招待メール・監査の出来事）」）。統合の前に `./gradlew verify` を通す（Step 30）。
- **コミット**: 生成の担当はコミットしない。生成の後に、依頼者の承認を得て、次の区切りでまとめてコミットする（`project.md` の Change Control の学び）。メッセージは日本語。

| 区切り | 中身 | 手順 |
|---|---|---|
| C1 | スキーマの変更 `backend/src/main/resources/db/migration/V8__u3_invitation.sql` と、V8 の確かめに使う V1〜V7 の複写（テストの資源） | Step 6・7 |
| C2 | 招待メールのテンプレート（ja・en）と `MailTemplateCatalog` の一覧の行 | Step 10 |
| C3 | 本番のコード（`invitation`、`audit.domain`・`audit.service`、9節の決定 3 の `user.domain`・`user.service`・`user.repository`）と `application.yaml` | Step 2・4・6・8・12・14・16・18・20・22 |
| C4 | テスト（単体・結合・境界の構造の検査・テストの支え。既存のテストの直しを含む） | Step 3・5・7・9・11・13・15・17・19・21・23・24 |
| C5 | 基盤の設定（`docker/perf/compose.yaml`・`frontend/playwright.config.ts`・`.env.example`） | Step 26 |
| C6 | 文書（`README.md`） | Step 27 |
| C7 | この段の記録（記録の `construction/u3-invitation/code-generation/` の下） | Step 31 |

- `origin` への `git push` は依頼者が行う。AI はプッシュしない。

## 4. 作るもの・手を入れるもの

| パッケージ | 部品 | 新しい・手を入れる | 役割 |
|---|---|---|---|
| `invitation.domain` | `InvitationState`（PENDING・COMPLETED・CANCELLED・REPLACED）、`SendResult`（PENDING・SENT・FAILED と API の値 SENT・FAILED への写し）、JPA の `AttributeConverter` | 新しい | BR4.4、`entities.md` |
| `invitation.domain` | `InvitationToken`（作成・形の確かめ・SHA-256・伏せ字）、`RegistrationUrl`（ベース URL ＋ `/register#token=` ＋ トークン、伏せ字）、`BaseUrlRule`（BR1.3 の形の確かめと末尾の `/` の除去） | 新しい | BR1.3・BR3.1〜BR3.4、NFR1.1・NFR1.4・NFR1.6 |
| `invitation.domain` | `InvitationValidity`（有効・期限切れの判定、拒否の理由 `LinkRejection` の決め方）、`InvitationPaging`（page の読み取り・位置からの page・読み始めの位置） | 新しい | BR2.3・BR3.3・BR5.2・BR7.6、NFR9.7 |
| `invitation.domain` | `InvitationEmail`（正規化済みの値、伏せ字）、`InvitationRequestValidation`（email・language）、`RegistrationValidation`（displayName・password・passwordConfirmation・language・theme・fontSize。U2 の `FieldError` と `FieldErrorReason` を使う） | 新しい | BR1.1・BR1.2・BR7.2、NFR9.12、`security-design.md` 6節 |
| `invitation.domain` | `InvitationAvailability`・`UnavailableReason`（BASE_URL_NOT_CONFIGURED・SMTP_NOT_CONFIGURED の順）、`LinkRejection` | 新しい | BR1.4・BR7.6 |
| `invitation.domain` | 出来事5つ `InvitationIssuedEvent`・`InvitationResentEvent`・`InvitationCancelledEvent`・`RegistrationCompletedEvent`・`RegistrationFailedEvent`（invitationId・利用者 ID・理由・日時・送り手の情報だけ） | 新しい | BR8.1〜BR8.3・BR8.6、C8 |
| `invitation.domain` | `InvitationProblemTypes`（5つの code、ja・en） | 新しい | BR9.3、NFR8.1 |
| `invitation.domain` | `Invitation`（エンティティ。`pending_email` を持たない。置き換え・送り直し・取り消し・完了の操作） | 新しい | `entities.md`、`reliability-design.md` 2.1 |
| `invitation.repository` | `InvitationRepository`（同じメールアドレスの PENDING・ID・トークンのハッシュの排他の読み取り（待ち 3 秒）、ハッシュの読み取り、一覧と件数、前に並ぶ件数、送信の結果の条件つきの更新、2つの削除） | 新しい | BR2.2・BR4.3・BR5.1・BR5.2・BR6.4・BR11.1、`performance-design.md` 5節・6節 |
| `invitation.service` | `InvitationProperties`（設定の値だけの record）、`InvitationSettings`（起動時の確かめ、`validityHours`、使える設定の判定、WARN 1件と INFO 1件） | 新しい | BR1.3〜BR1.6、`observability-design.md` 2.2 |
| `invitation.service` | `InvitationTokenIssuer`（`SecureRandom` を1つ持つ）、`InvitationMailDispatcher`（入口の確かめ、U1 の `send` を1回、失敗のときだけ INFO） | 新しい | BR3.1・BR4.1・BR4.2、`performance-design.md` 2.1、`observability-design.md` 2.1 |
| `invitation.service` | `InvitationService`（招待・一覧・送り直し・取り消し。`@Transactional` なし、`TransactionTemplate`）、結果の型 `InviteResult`・`ResendResult`・`CancelResult`、`InvitationSummary`・`InvitationPage` | 新しい | BR1.5・BR2.1〜BR2.7・BR4.3〜BR4.5・BR5.1〜BR5.4・BR6.1〜BR6.4・BR8.1 |
| `invitation.service` | `RegistrationService`（リンクの確かめと登録の完了）、結果の型 `VerifyResult`・`CompleteResult`、`InvitationView`・`RegistrationCommand` | 新しい | BR7.1〜BR7.6・BR8.2・BR8.3 |
| `invitation.service` | `InvitationBarrier`・`NoOpInvitationBarrier`（待ち合わせの口）、`InvitationCleanupJob`・`InvitationSchedulingConfig`（`@EnableScheduling`）、`InvitationProblemTypeCatalog` | 新しい | NFR9.8・NFR9.10、BR11.1、RD-D3 |
| `invitation.web` | `InvitationAdminController`、`RegistrationController`、要求・応答の record（`InvitationRequest`・`InvitationResponse`・`InvitationPageResponse`・`TokenRequest`・`InvitationViewResponse`・`CompleteRequest`）、`InvitationRequestContextResolver`、`InvitationSecurityContributor`（order 310） | 新しい | C5・C6、`security-design.md` 2節・3節・6節・7節 |
| `db/migration` | `V8__u3_invitation.sql`（`invitations`、生成列 `pending_email`、`uk_invitations_token_hash`・`uk_invitations_pending_email`・`ck_invitations_state`、招待した管理者・完了した利用者への参照） | 新しい | BR11.3、`reliability-design.md` 2.1・8節 |
| `mail.template` と置き場 | `MailTemplateCatalog`（一覧の行と説明文）、`mail/templates/invitation_ja.html`・`invitation_en.html` | 手を入れる・新しい | BR10.1〜BR10.3、C10 |
| `audit.domain`・`audit.service` | `AuditEventType`（5つ）・`AuditFailureReason`（5つ）・`AuditEventFactory`（出来事5つからの組み立てと種類からの結果）・`AuditEventListener`（受け取り5つ） | 手を入れる | BR8.1〜BR8.5、C8 |
| `user.domain`・`user.service`・`user.repository` | 9節の決定 3 のとおり: `RedactedText`（文字列にすると伏せる文字列の値）、`UserAccountService.existsByEmail(RedactedText)` と、`createUser` の中の登録済みの確かめを伏せ字の引数の検索に替える、`findDisplayName` の戻り値を `Optional<RedactedText>` にする、`UserRepository` に伏せ字の引数の検索を足す | 手を入れる | NFR2.1、`project.md` の Forbidden（メールアドレスをアプリのログに含めない） |
| `application.yaml` | `mastersmith.invitation.*` の3項目 | 手を入れる | BR1.6 |

## 5. 手順

各層で実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む（test-after、Testing Contract の `ordering`）。U3 の層は「ドメイン（値の型と純粋な関数）→ データの形（V8・エンティティ）→ DB アクセス → 招待メールのテンプレート → 業務処理（設定と送信の入口 → 監査 → 招待の管理 → 登録の完了 → 定期の削除）→ API」の順とする。画面は持たない。

### Step 1: 作業の場の用意と、変更の前の基準（依頼者が承認した git の操作。9節の決定 1）

- [x] `develop` の先頭のハッシュを記録し、`develop` から短命のブランチ `feature/260925-user-management-b3` を作る（コミットはしない。統合は squash）
- [x] 変更の前の基準をとる: colima が動いていることを確かめ、README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡して `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を実行し、テストの件数（単体・結合、失敗・飛ばした）と、全体のカバレッジと、手を入れる予定の既存のパッケージ（`audit.domain`・`audit.service`・`mail.template`、9節の決定 3 で手を入れる `user.domain`・`user.service`・`user.repository`）の行・分岐のカバレッジを記録する（brownfield の Test Baseline、`project.md` の Testing Posture）
- [x] 対応: B3 の共通の完了の条件、NFR9.6

### Step 2: 骨組み

- [x] `backend/src/main/java/cherry/mastersmith/invitation/package-info.java` と `web`・`service`・`domain`・`repository` の `package-info.java`（日本語の説明、ライセンスヘッダー）を作る
- [x] `application.yaml` に `mastersmith.invitation.validity`・`retention`・`cleanup.cron` を `${環境変数:既定}` の形で、説明のコメントつきで足す（2.2）
- [x] `invitation.service.InvitationProperties`（`@ConfigurationProperties("mastersmith.invitation")` の record、`Duration` と cron の文字列）を作る。確かめは Step 12 の `InvitationSettings` で行う
- [x] 対応: BR1.6、`infrastructure-specification.md` 3.2、`logical-components.md` 1節

### Step 3: テストの実行の準備（最初のテストより前）

- [x] `unit-test-instructions.md` の単体のコマンドが変更の前の状態で動き、既存のテストが通ることを確かめる。`invitation` のテストはまだ無いが、`audit`・`mail`・`user`・`ArchitectureTest`・`common.security`・`AppearanceSecurityContributorTest` に当たるため、Gradle の「当たるテストが無い」の失敗にならない
- [x] 結合のコマンドが動き、既存のテストが通ることを確かめる（U3 の結合テストは組み込みの H2 と JVM の中の SubEtha SMTP だけを使い、コンテナを使わない）
- [x] `backend/src/test/resources/junit-platform.properties` の jqwik の設定（失敗した例の記録、種の再現）がそのまま使えることを確かめる
- [x] 対応: Testing Contract の `runner_step`

### Step 4: ドメインの値の型と純粋な関数（invitation.domain）— 実装

- [x] `InvitationState`・`SendResult`（API の値は PENDING を FAILED にする。BR4.4）
- [x] `InvitationToken`: `generate(SecureRandom)`（32 バイト、URL で使える Base64、埋め草なし、43 文字）、`parse(String)`（43 文字で `A-Z`・`a-z`・`0-9`・`-`・`_` だけなら値、ほかは空。DB を引かない）、`hash()`（UTF-8 の SHA-256、32 バイト）、`toString` は伏せ字（BR3.1・BR3.2）
- [x] `BaseUrlRule`: 無い・空白だけは「使える値なし（警告なし）」、形の誤り（http・https でない、ホストなし、`?`・`#`・利用者情報を含む、解釈できない）は「使える値なし（警告あり）」、使える値は前後の空白と末尾の `/` を除いた値（BR1.3）。`RegistrationUrl.of(baseUrl, token)`（伏せ字）
- [x] `InvitationValidity`: `isValid(state, expiresAt, now)`（PENDING かつ now < expiresAt）、`isExpired`、`rejectionOf(state, expiresAt, now)`（BR7.6: COMPLETED → INVITATION_ALREADY_USED、CANCELLED → INVITATION_CANCELLED、REPLACED と期限切れの PENDING → INVITATION_EXPIRED）
- [x] `InvitationPaging`: page の文字列の読み取り（無ければ 1、1 以上の整数だけ）、`pageOf(position)`（切り上げ）、`offsetOf(page)`、1ページ 20 件（BR2.3・BR5.2）
- [x] `InvitationEmail`・`InvitationRequestValidation`: 正規化の前に CR・LF を含めば INVALID_CHARACTER、空は REQUIRED、正規化の後に 254 文字を超えれば TOO_LONG、形式の誤りは INVALID_VALUE。language は ja・en の完全な一致（REQUIRED・INVALID_VALUE）。誤りは email・language の順にすべて集める（BR1.1・BR1.2、`security-design.md` 6節）
- [x] `RegistrationValidation`: displayName（U2 の `DisplayName`）・password（`PasswordPolicy`: REQUIRED・TOO_SHORT・TOO_LONG）・passwordConfirmation（REQUIRED・MISMATCH）・language・theme・fontSize（U2 の `PreferenceValueRules`: REQUIRED・INVALID_VALUE）を C6 の項目の順に集め、1つの項目に最初の理由だけ。token は項目にしない（BR7.2）
- [x] `InvitationAvailability`・`UnavailableReason`・`LinkRejection`、出来事5つ（秘密を持たない）、`InvitationProblemTypes`（INVITATION_EMAIL_REGISTERED 409・INVITATION_ALREADY_PENDING 409・INVITATION_NOT_CONFIGURED 503・INVITATION_NOT_FOUND 404・REGISTRATION_LINK_INVALID 404、ja・en）
- [x] 9節の決定 3 のとおり: `user.domain.RedactedText`（値と伏せ字の `toString`）
- [x] 対応: BR1.1〜BR1.4・BR2.3・BR3.1〜BR3.4・BR4.4・BR5.2・BR7.2・BR7.6・BR8.6・BR9.3、NFR1.1・NFR1.6・NFR8.1・NFR9.7・NFR9.12

### Step 5: ドメインの値の型と純粋な関数 — テスト（単体・性質ベース）

- [x] `InvitationTokenTest`: 作った値は 43 文字で使える文字だけ、ハッシュは 32 バイト、42・43・44 文字と使えない文字（`+`・`/`・`=`・空白・全角）と空・null の読み取り、文字列に値が出ない。jqwik（種を記録）: 読み取れるのは「43 文字かつ使える文字だけ」のときに限る、作った値はいつも読み取れる
- [x] `InvitationValidityTest`: 有効期限の直前（−1 ナノ秒）は有効、ちょうどと直後は無効、PENDING でない状態はいつも無効、拒否の理由の表（BR7.6）。jqwik: 有効 ⇔ PENDING かつ now < expiresAt
- [x] `InvitationPagingTest`: 位置 1〜20 → 1、21 → 2、40 → 2、41 → 3、page の `0`・`-1`・`1.5`・`abc`・空の拒否と無いときの 1、読み始めの位置。jqwik: `pageOf(p)` は `(p−1)/20+1` と一致し、その page の範囲に p が入る
- [x] `BaseUrlRuleTest`・`RegistrationUrlTest`: http・https・パスあり・末尾の `/` の除去、ftp・ホストなし・`?`・`#`・`user@` の拒否、空白だけ、URL は `ベース URL/register#token=トークン` で文字列に値が出ない
- [x] `InvitationRequestValidationTest`: 254 文字は通り 255 は TOO_LONG（正規化の後で数える）、前後の空白と大文字は正規化される、先頭・末尾・途中の CR・LF は正規化の前に INVALID_CHARACTER、形式の誤り、language の `JA`・` ja`・空。jqwik: CR か LF を含む任意の文字列はいつも拒否
- [x] `RegistrationValidationTest`: 項目の順、1項目1理由、11・12 コードポイント、72・73 バイト、絵文字 11・12 文字、確かめの不一致と前後の空白、氏名の空白だけ・255 コードポイント・Cc・Cf、値を理由に含めない
- [x] `InvitationAvailabilityTest`（理由の並び）、`InvitationProblemTypesTest`（code・状態コード・ja・en が空でない）、出来事の文字列にトークン・メールアドレスが無いことの確かめ、`SendResultTest`
- [x] 単体のコマンドを実行して通す

### Step 6: データの形（V8・エンティティ）— 実装

- [x] `V8__u3_invitation.sql`（先頭にライセンスヘッダーのコメント、既存の V1〜V7 と同じ形）: `invitations`（`invitation_id` IDENTITY の主キー、`email` VARCHAR(254)、`language` VARCHAR(2)、`token_hash` BINARY(32)、`invited_by_user_id` BIGINT（users への参照）、`invited_at`・`expires_at` TIMESTAMP WITH TIME ZONE、`send_result` VARCHAR(8)、`state` VARCHAR(16)、`ended_at` TIMESTAMP WITH TIME ZONE（空を許す）、`completed_user_id` BIGINT（users への参照、空を許す）、`pending_email` の生成列、`uk_invitations_token_hash`・`uk_invitations_pending_email`・`ck_invitations_state`）。V1〜V7 は変えない
- [x] `Invitation`: 列の対応（状態・送信の結果・言語は `AttributeConverter`、言語は U2 の `Language` を使う）、作る操作、`replace(now)`・`resend(newHash, expiresAt)`・`cancel(now)`・`complete(userId, now)`（PENDING でなければ想定外の誤り）、`toString` は上書きしない
- [x] 対応: BR2.4・BR2.6・BR6.1・BR6.2・BR7.3・BR11.3、NFR10.1

### Step 7: データの形 — テスト（結合、生成列と V8 の後方互換の自動の確かめ）

- [x] V1〜V7 の移行ファイルの複写をテストの資源 `backend/src/test/resources/db/migration-through-v7/` に置く。本番の V1〜V7 と1バイトも違わないことをテストの中で確かめる
- [x] `V8MigrationIT`（Spring を起動しない。組み込みの H2 の一時のファイルに Flyway の API で当てる）: `reliability-design.md` 2.1 の (a) PENDING の2件目の追記が一意の違反（SQLState 23505）、(b) PENDING でない行は同じメールアドレスで複数、(c) PENDING を CANCELLED にすると新しい PENDING を作れる、(d) 確定していない同時の追記は先の確定を待った後に一意の違反で拒まれ PENDING は1件だけ（2つの接続とテストの中の待ち合わせ）。もう一度 migrate しても V8 は1回だけ当たる
- [x] `InvitationSchemaIT`（Spring を起動する）: Hibernate の `validate` で起動でき、エンティティで保存・読み取りができ、`pending_email` は書き込みで値を渡さず DB が計算する
- [x] `V8BackwardCompatibilityIT`: 今の Flyway で V8 まで当てた DB に、V7 までの複写だけを知る Flyway の `validate`・`migrate` が失敗しない（NFR10.2 の自動の確かめ）
  - **見込みが外れたとき**（(a)〜(d) のどれかが成り立たない、または V7 までの Flyway が失敗する）: この手順で生成を止め、結果と候補（固定の1行の排他への切り替えの形など）を示して依頼者に諮る（9節の決定 4 の (a)・(b)）
- [x] 単位の結合のコマンドを実行して通す

### Step 8: DB アクセス（InvitationRepository）— 実装

- [x] `findPendingByEmailForUpdate(InvitationEmail)`・`findByIdForUpdate(long)`・`findByTokenHashForUpdate(byte[])`（`PESSIMISTIC_WRITE`、`jakarta.persistence.lock.timeout` 3000。既存の `LoginAttemptStateRepository` と同じ形）、`findByTokenHash(byte[])`（リンクの確かめの読み取り）
- [x] `findPendingPage(Pageable)`（`invited_at` の降順・`invitation_id` の降順）・`countPending()`・`countPendingBefore(invitedAt, invitationId)`（前に並ぶ件数）
- [x] `recordSendResult(invitationId, tokenHash, result)`（`send_result = 'PENDING'` とハッシュが同じ行だけを書き換え、書き換えた行の数を返す。BR4.3）
- [x] `deleteEndedBefore(cutoff, limit)`・`deleteExpiredPendingBefore(cutoff, limit)`（対象を選ぶ副問い合わせと外側の DELETE の両方に状態と日時の条件を置く。BR11.1）
- [x] どれも名前つきの引数（SpEL を含む）だけで組み立て、文字列の連結をしない（NFR9.3）
- [x] 対応: BR2.2・BR2.3・BR4.3・BR5.1・BR5.2・BR6.4・BR11.1、`performance-design.md` 5節・6節

### Step 9: DB アクセス — テスト（結合）

- [x] `InvitationRepositoryIT`: メールアドレス・ID・ハッシュでの読み取り、一覧の並び（同じ時刻は ID の大きい順）と 20 件の区切りと件数、前に並ぶ件数、送信の結果の条件つきの更新（ハッシュが違う・PENDING でない行は書き換えない）、2つの削除の境目（ちょうど・直前）と件数の上限、送り直しで有効期限が新しくなった行を消さない
- [x] 単位の結合のコマンドを実行して通す

### Step 10: 招待メールのテンプレート（U1 の置き場と一覧）— 実装

- [x] `backend/src/main/resources/mail/templates/invitation_ja.html`・`invitation_en.html`: Mustache のコメント（`{{! ... }}`）のライセンスヘッダー、`<html lang>`、`<title>` が件名、招待された旨・登録を終えるとログインできる旨・心当たりが無ければ何もしなくてよい旨、`href="{{registrationUrl}}"`（二重引用符）のリンクと URL の文字、有効な期間の文（`{{validityHours}}`）。有効期限の日時・招待した管理者の氏名は載せない。エスケープされる差し込みだけを使う（BR10.1〜BR10.3、`project.md` の Forbidden）
- [x] `MailTemplateCatalog.DEFINITIONS` に `new MailTemplateDefinition("invitation", Set.of("registrationUrl", "validityHours"))` を足し、説明文を B3 の状態に直す
- [x] 対応: BR10.1〜BR10.3、C10、AC3.1.2・AC3.1.3・AC3.1.8・AC3.1.9

### Step 11: 招待メールのテンプレート — テスト（単体）

- [x] 既存の `MailTemplateLintTest` が本番の一覧の `invitation` を自動で対象にし（禁じた差し込みなし、属性の値の二重引用符、差し込みの名前が一覧と一致、差し込む値すべての5つの文字のエスケープ、件名が空でない、`{{` の残りとライセンスヘッダーが出ない、`lang`）、通ることを確かめる。`filesMatchTheCatalogs` の説明文の「production location is empty in B1」を今の状態に直す（確かめの中身は変えない）
- [x] 既存の `MailTemplateRegistryTest.emptyCatalog` は本番の一覧が空であることを確かめているため、空の一覧と無い置き場の確かめはそのまま残し、本番の一覧は `invitation` が ja・en で準備されることを確かめる形に直す（緩めない。8節）
- [x] `InvitationTemplateContentTest`（`backend/src/test/java/cherry/mastersmith/mail/template/`）: 本番の一覧で ja・en を描き、BR10.2 の文面（`validityHours` が 24 なら ja「このリンクは 24 時間有効です」・en の同じ意味の文、48 なら 48 が出て 24 が出ない）、リンクの文言と URL の文字、日時と管理者の氏名が無いことを確かめる
- [x] 単体のコマンドを実行して通す

### Step 12: 業務処理 — 設定・トークン・送信の入口（invitation.service）— 実装

- [x] `InvitationSettings`: 起動時に `InvitationProperties` を確かめ（有効期限と保存の日数、項目の名前だけの例外で起動を止める）、`validityHours` を作り、`BaseUrlRule` でベース URL を1回だけ判定し（形の誤りは項目の名前だけの WARN 1件、値は出さない）、U1 の `MailSender#isConfigured()` と合わせて `InvitationAvailability` を決め、INFO 1件（enabled と unavailableReasons）を出す
- [x] `InvitationTokenIssuer`（`SecureRandom` を1つ持ち、`InvitationToken` を作る）
- [x] `InvitationMailDispatcher`: 入口で `TransactionSynchronizationManager.isActualTransactionActive()` なら `IllegalStateException`、`MailRequest("invitation", 招待の言語, 招待のメールアドレス, {registrationUrl, validityHours})` で `send` を1回。結果が送れていなければ INFO 1件（`invitationId`・`operation`（INVITE・RESEND）・`failureKind`）。成功では出さない。`MailUnexpectedException` は捕まえない
- [x] `InvitationProblemTypeCatalog`
- [x] 対応: BR1.3〜BR1.6・BR3.1・BR4.1・BR4.2、NFR1.1・NFR2.3・NFR5.1、`performance-design.md` 2.1、`observability-design.md` 2節

### Step 13: 業務処理 — 設定・トークン・送信の入口 — テスト（単体）

- [x] `InvitationSettingsTest`: 有効期限 1 時間は通る、24 時間 → "24"、48 時間 → "48"、0・負・`90m` で起動を止める、保存の日数 0・負・1 日で割り切れない値で止める、ベース URL の場合ごとの使える設定と理由の並び（ベース URL だけ・SMTP だけ・両方）、WARN 1件に値が無い、INFO の項目
- [x] `InvitationMailDispatcherTest`: トランザクションの中で呼ぶと止まる、依頼の中身（templateId・言語・宛先・差し込み2つ）、失敗の INFO が1件で項目が3つ・WARN を出さない・宛先が無い、成功ではログなし、想定外の例外がそのまま伝わる
- [x] `InvitationTokenIssuerTest`・`InvitationProblemTypeCatalogTest`
- [x] 単体のコマンドを実行して通す

### Step 14: 業務処理 — 監査の記録（audit.domain・audit.service）— 実装

- [x] `AuditEventType` に INVITATION_ISSUED・INVITATION_RESENT・INVITATION_CANCELLED・REGISTRATION_COMPLETED・REGISTRATION_FAILED、`AuditFailureReason` に INVITATION_EXPIRED・INVITATION_ALREADY_USED・INVITATION_CANCELLED・INVITATION_NOT_FOUND・EMAIL_ALREADY_REGISTERED を足す（BR8.4、最長 24 文字）
- [x] `AuditEventFactory`: 出来事5つから `AuditEvent.withTarget` で組み立てる（招待の管理は actor と targetInvitationId、登録の完了は targetInvitationId と targetUserId、登録の失敗は targetInvitationId（分かるときだけ）と理由）。`resultOf(AuditEventType)` の網羅の `switch` に5つを足す（REGISTRATION_FAILED だけ FAILURE）
- [x] `AuditEventListener` に受け取り5つ（AFTER_COMMIT・`fallbackExecution = true`、失敗は受け止めて ERROR、項目は既存の組み立てのまま対象の2項目を含む）
- [x] 対応: BR8.1〜BR8.6、C8、NFR9.4・NFR9.5

### Step 15: 業務処理 — 監査の記録 — テスト（単体）

- [x] `AuditEventFactoryTest` に5つの出来事の組み立てと種類からの結果を足す。`AuditEventListenerTest` に受け取り5つと、記録の失敗の ERROR の項目（対象の2項目があり、メールアドレス・トークンが無い）を足す。既存の `AuditEventTest` の長さの確かめが新しい値を自動で対象にすることを確かめる
- [x] 単体のコマンドを実行して通す

### Step 16: 業務処理 — 招待・一覧・送り直し・取り消し（InvitationService）— 実装

- [x] 招待: 入力の検証 → 使える設定か（だめなら内部DB を使わずに `NotConfigured`）→ 短いトランザクション（登録済み → `EmailRegistered`、同じメールアドレスの PENDING の排他の読み取り → 期限内なら `AlreadyPending(invitationId, page)`・期限切れなら REPLACED → 待ち合わせの口 → トークンと PENDING の追記 → INVITATION_ISSUED を知らせる）→ `RegistrationUrl` → トランザクションの外で送信 → 別の短いトランザクションで結果を記録 → `Invited(summary)`。同時の招待の一意の違反（`uk_invitations_pending_email` だけ）はトランザクションの外で受け、勝った側の PENDING の invitationId と page を引いて `AlreadyPending`、見つからなければ1回だけやり直し、2回目も違反なら想定外の誤り（`reliability-design.md` 2.2）
- [x] 一覧: page の検証、読み取り専用のトランザクションで 20 件と件数、各行の expired（時計）・sendResult（PENDING を FAILED）・invitedBy（ページの中の異なる invitedByUserId ごとに `findDisplayName` を1回、空なら空の文字列）、invitationEnabled と unavailableReasons
- [x] 送り直し: 使える設定か（だめなら何も変えず `NotConfigured`）→ 排他の読み取り（無い・PENDING でない → `NotFound`）→ 新しいトークン・有効期限・PENDING を確定、INVITATION_RESENT → 招待と同じ送信と記録 → `Resent(summary)`
- [x] 取り消し: 設定を確かめない → 排他の読み取り → CANCELLED と終わった日時、INVITATION_CANCELLED → `Cancelled`
- [x] 9節の決定 3 のとおり: `UserAccountService.existsByEmail(RedactedText)` と `UserRepository` の伏せ字の引数の検索を足し、`createUser` の中の登録済みの確かめをそれに替え、`findDisplayName` の戻り値を `Optional<RedactedText>` にする。既存の `existsByEmail(String)`（初期管理者が使う）とログインの経路は変えない
- [x] 対応: US1.1・US2.1・US2.2、BR1.5・BR2.1〜BR2.7・BR4.1〜BR4.5・BR5.1〜BR5.4・BR6.1〜BR6.4・BR8.1、NFR5.1・NFR9.8・NFR9.9・NFR9.13

### Step 17: 業務処理 — 招待・一覧・送り直し・取り消し — テスト（単体・結合）

- [x] `InvitationServiceTest`（`InvitationRepository`・`UserAccountService`・`InvitationMailDispatcher`・`ApplicationEventPublisher` を Mockito で差し替え、`TransactionTemplate` は渡した処理をそのまま実行する差し替え）: 判定の順（入力 → 設定 → 登録済み → 招待中）、使えない設定では DB も送信も呼ばない、期限切れの置き換え、送信の失敗でも `Invited` と FAILED、記録の呼び出しは送信の後、一覧の PENDING → FAILED と invitedBy の空の文字列、送り直し・取り消しの `NotFound`
- [x] `InvitationConcurrencyIT`（待ち合わせの口で同時の招待を確実に重ねる。上限の時間つき、`sleep` なし）: 1件だけ作られ、もう一方は `AlreadyPending`（勝った側の ID）、送信は1回、INVITATION_ISSUED は1件
- [x] `InvitationSendResultIT`: 送信の最中にもう一度送り直された招待では、古い結果で書き換えない。記録の前に止まった形（PENDING のまま）が一覧で FAILED に見える
- [x] 9節の決定 3 のとおり: `UserAccountServiceTest` の `findDisplayName`・`existsByEmail` の確かめを新しい形に直し、伏せ字の文字列化を足す
- [x] 単位の単体・結合のコマンドを実行して通す

### Step 18: 業務処理 — リンクの確かめと登録の完了（RegistrationService）— 実装

- [x] リンクの確かめ: 形の確かめ → ハッシュの読み取り → 有効でない・登録済みなら `Rejected`、有効なら `Valid(InvitationView)`。内部DB を変えず、出来事を出さない（BR7.1）
- [x] 登録の完了: 入力の検証（誤りは `Invalid(errors)`、トークンを引かない）→ 形の誤りはトランザクションを始めずに拒否（INVITATION_NOT_FOUND）→ 1つのトランザクション（ハッシュで排他の読み取り → 待ち合わせの口 → 有効でなければ自分の巻き戻しの印を付けて `Rejected(理由)` → `createUser(NewUser(招待の email, 氏名, パスワード, 言語, テーマ, 文字の大きさ, false))` → `EmailAlreadyUsed` なら自分の印を付けて `Rejected(EMAIL_ALREADY_REGISTERED)` → `complete` と REGISTRATION_COMPLETED）→ 拒否ならトランザクションの外で REGISTRATION_FAILED を知らせる → `Completed` か `Rejected`
- [x] 対応: US3.2、BR3.2・BR3.3・BR7.1〜BR7.7・BR8.2・BR8.3、NFR3.1・NFR5.2・NFR9.13

### Step 19: 業務処理 — リンクの確かめと登録の完了 — テスト（単体・結合）

- [x] `RegistrationServiceTest`: 入力の誤りではトークンを引かない、形の誤りではトランザクションを始めない、拒否の理由の決め方、`createUser` に渡す値（admin false、email は招待の値）
- [x] `RegistrationRollbackIT`: 完了の時点で同じメールアドレスの利用者がいると利用者は増えず招待は PENDING のまま、成功の監査が残らず REGISTRATION_FAILED・EMAIL_ALREADY_REGISTERED が残り、`UnexpectedRollbackException` が起きない（BR7.4。性能は Unverified のまま）
- [x] `RegistrationConcurrencyIT`（待ち合わせで重ねる）: 同じ招待の2回の完了で利用者は1人、取り消しが先なら利用者は作られない、送り直しが先なら前のトークンの完了は拒否される、完了が先なら取り消し・送り直しは `NotFound`
- [x] 単位の単体・結合のコマンドを実行して通す

### Step 20: 業務処理 — 定期の削除 — 実装

- [x] `InvitationCleanupJob`（`@Scheduled(cron = "${mastersmith.invitation.cleanup.cron}")`、境目は時計の今 − 保存の日数、2つの削除を件数の上限 1000 ごとに `TransactionTemplate` で上限に届かなくなるまで、消した件数を INFO、失敗は ERROR（スタックトレース付き）で例外を外へ出さない。ログにメールアドレス・ハッシュを出さない）
- [x] `InvitationSchedulingConfig`（`@EnableScheduling`、既存の `auth` の設定に黙って頼らない。RD-D3）
- [x] 対応: BR11.1・BR11.2、NFR9.10

### Step 21: 業務処理 — 定期の削除 — テスト（単体・結合）

- [x] `InvitationCleanupJobTest`: 上限ちょうどのとき繰り返す、失敗で ERROR と −1、INFO の件数
- [x] `InvitationCleanupIT`（`MutableClock`、cron はテストの設定で止める）: 終わった招待の境目ちょうどは消え直前は残る、期限切れの PENDING も同じ、1000 件を超える行、送り直した行は残る、監査の記録は消えない
- [x] 単位の単体・結合のコマンドを実行して通す

### Step 22: API（invitation.web）— 実装

- [x] `InvitationAdminController`: `POST /api/admin/invitations`（201）、`GET /api/admin/invitations?page=`（200、page は文字列で受ける）、`POST /api/admin/invitations/{invitationId}/resend`（200）、`POST /api/admin/invitations/{invitationId}/cancel`（204）。結果の型を業務エラーにする: 400 VALIDATION_FAILED（`fieldErrors`、page の誤りは項目なし）、409 INVITATION_EMAIL_REGISTERED、409 INVITATION_ALREADY_PENDING（`invitationId`・`page`）、503 INVITATION_NOT_CONFIGURED（`unavailableReasons`）、404 INVITATION_NOT_FOUND
- [x] `RegistrationController`: `POST /api/registration/verify`（200、email と language）、`POST /api/registration/complete`（204）。拒否は理由によらず1つの経路（`BusinessException(REGISTRATION_LINK_INVALID)`、追加の項目なし）、入力の誤りは 400（`fieldErrors`）。トークンは本文の `token` だけで受ける
- [x] 要求・応答の record（項目はすべて文字列で受け、要求の DTO の `toString` はメールアドレス・トークン・パスワードを伏せる）。応答にトークン・ハッシュ・URL を持たない
- [x] `InvitationRequestContextResolver`（U2 の `MeRequestContextResolver` と同じ形で、管理者の利用者 ID を認証の文脈から、送り手の情報を `RequestOrigin` で。AUTHENTICATION_REQUIRED を起動時に code で引く）
- [x] `InvitationSecurityContributor`（order 310、`POST` の2つの道だけ `permitAll`）
- [x] 対応: C5・C6、BR5.4・BR7.5・BR9.1〜BR9.4、NFR1.4・NFR3.1・NFR4.1〜NFR4.4・NFR8.1

### Step 23: API — テスト（結合。SubEtha SMTP を JVM の中で起動し、本番のテンプレートで送る）

- [x] テストの支え（`backend/src/test/java/cherry/mastersmith/invitation/testsupport/`）: 本番のテンプレートとベース URL・受け手の番号・短い時間切れでアプリを起動する部品、招待の API の呼び出し、受けたメールからリンクとトークンを取り出す部品、待ち合わせの口の差し替え
- [x] `InvitationAdminApiIT`: 4本の API の未認証 401・管理者でない 403（何も読み書きせず送らない）・成功、入力の誤り（254・255 文字、CR・LF、形式、言語）の `fieldErrors` と値を載せないこと、登録済み 409、招待中 409 の invitationId と page（21 番目 → 2）、503 の理由の3つの場合（送らない）、送り直しの 503 で前のリンクが有効なまま、完了済み・取り消し済み・置き換え済み・無い ID の 404 の本文が同じ、一覧の並び・ページ・最後を超えた空・page の誤り・expired・invitedBy・sendResult、応答にトークン・ハッシュ・URL が無い、ja・en の説明文
- [x] `InvitationMailIT`: ja・en の宛先・件名・本文・`lang`、リンクがベース URL で始まり `#token=` を含む、要求の Host を変えてもベース URL のまま、ベース URL が無いときに Host を変えても 503 で送らない、既定の本文に「24」、有効期限 48 時間の設定で招待と送り直しの本文に「48」が出て「24」が出ず `expiresAt` が＋48 時間、宛先に CR・LF を含む招待は 400 で送らず受けたメールのヘッダーが増えない
- [x] `InvitationSendFailureIT`: 閉じた番号（接続を拒む）・何も返さない受け手（時間切れ 1 回、テストの設定の時間切れの後に応答）・宛先を拒む受け手で 201・200 と FAILED、受け手の接続は1回だけ、応答とログに宛先・SMTP の応答・資格情報が無い、U3 の INFO が1件で WARN を出さない、送り直しの送信の失敗でも監査は SUCCESS
- [x] `InvitationSendConnectionIT`: 何も返さない受け手への送信の最中（受け手が接続を受け付けたことを上限の時間つきで待つ）に、使用中の接続が 0 で、一覧の API が時間切れを待たずに応答する（NFR5.1）
- [x] `RegistrationApiIT`: 確かめの 200（email・language）と拒否（内部DB が変わらず監査されない）、完了の 204 の後にログインでき、応答にトークンが無く、その利用者は招待の管理で 403、入力の誤りでは招待を消費せず監査しない、拒否の理由ごと（期限のちょうど・直後、直前は通る、使用済み、取り消し済み、置き換え済み、存在しない・改ざん・42・44 文字、送り直しの前のトークン、定期の削除で消えた招待）の応答の状態コード・code・本文の項目の名前と並び（`traceId` の値を除く）が同じで、監査の理由が BR7.6 のとおり
- [x] `RegistrationPublicScopeIT`: トークンなしの2つの POST は処理される、壊れたトークン付きの POST は 401、GET `/api/registration/verify` と `/api/registration/other` は 401（NFR4.2、Q3 A）
- [x] `InvitedPersonAuthenticationIT`: 招待中の人のメールアドレスでのログインは存在しない利用者と同じ 401、招待の ID（利用者の ID と重ならない値）と招待のメールアドレスを主体にした正しい鍵のアクセストークンは 401、招待中の人を主体にしたリフレッシュトークンの経路は無く、でたらめな値での更新は 401（BR7.7、NFR3.3）
- [x] `InvitationAuditIT`: 5つの出来事の必須の項目、実際のアクセストークンの管理者が actor、送り手の情報がログインの監査と同じ取り方（513 文字の User-Agent が 512 文字）、記録しない場合（拒否・送信の失敗・確かめ・入力の誤り・置き換え）
- [x] `InvitationAuditWriteFailureIT`: 既存の `FailingAuditEventRepositoryConfig` の形で、5つの出来事ごとに1件、応答が変わらず ERROR に秘密が無い
- [x] `InvitationSecretLeakIT`: `cherry.mastersmith` を TRACE にして、招待・送り直し・送信の失敗・確かめ・完了の成功と拒否を流し、ログ・監査の行（全列）・応答に、トークン・ハッシュの16進・URL・パスワードと、招待先・招待した管理者のメールアドレス（ログとエラー応答）が無いことを確かめる（9節の決定 3: `user.service`・`user.repository` の TRACE の記録も対象から外さない。ただし U3 の流れが通らない既存のログインの経路と初期管理者の `existsByEmail(String)` は据え置きで、このテストの場面に含まない）
- [x] `InvitationFlowIT`（B3 の見せ方）: 管理者が招待 → 受け手でメールを受ける → リンクのトークンで確かめ → 完了 → 新しい利用者でログイン
- [x] 単位の単体・結合のコマンドを実行して通す

### Step 24: 構造の検査と静的解析

- [x] `InvitationBoundaryArchitectureTest` を足す（`logical-components.md` 2節の6つ: `auth`・`audit` に依存しない、`user` へは `user.service` と `user.domain` の値の型だけ（エンティティと `user.repository` を使わない）、`mail` へは `mail.service` と `mail.domain` だけ、`dsl`・`dslmanage`・`targetdb`・`access` に依存しない、外から依存してよいのは `audit` だけで `invitation.domain` の出来事だけ、トランザクションの境界は `invitation.service` だけ）。既存の境界テストは変えずに通す。緩める必要が出たときは、緩めずに作れる形を先に探し、それでも要るなら生成を止めて依頼者に諮る（9節の決定 4 の (d)）
- [x] `./gradlew :backend:spotbugsGate` を除外を足さずに通す（`SQL_`・`PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION`）
- [x] `./gradlew spotlessCheck` で新しいファイルのフォーマットとライセンスヘッダー（Java・V8 の SQL・テンプレート）を確かめる
- [x] 対応: NFR9.3、`team.md` の Code Style

### Step 25: カバレッジの実測（packagesJudgedByTotal の確かめ）

- [x] 手を入れた既存のパッケージが `backend/build.gradle.kts` の `packagesJudgedByTotal`（今の12件）に無いことを確かめる（`audit.domain`・`audit.service`・`user.*`・`mail.template` は一覧に無い）。一覧のパッケージに手を入れた場合は外して下限を満たす。一覧に足さない、計測の除外を足さない（`team.md` の Testing Posture、NFR9.6）
- [x] colima を動かし、README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡して `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を実行し、`invitation` の4つのパッケージと手を入れたパッケージの行・分岐を実測して記録する
- [x] 下限（行 80%・分岐 70%）を下回ればテストを足してやり直す。届かないときは生成を止めて諮る（9節の決定 4 の (c)）

### Step 26: 環境とビルドの設定

- [x] `docker/perf/compose.yaml` にサービス `mailpit`（profile `mail`、`compose.yaml` と同じ `axllent/mailpit:v1.31.2` のダイジェストで固定、メモリの上限 `256m`、ボリュームなし、`restart: "no"`、PC へのポートの公開なし）を足す（`cicd-pipeline.md` 4.1）
- [x] `frontend/playwright.config.ts` の `webServer.env` に `MASTERSMITH_WEB_BASE_URL: \`http://localhost:${port}\`` を足す（`cicd-pipeline.md` 5節）
- [x] `.env.example` に「招待（U3）」の節（3つの項目、値は空のコメント、有効期限を長くしすぎない旨）を足す。`compose.yaml`・`Dockerfile` は変えない
- [x] 対応: `infrastructure-specification.md` 1節・3.2、C-D1

### Step 27: 文書（README）

- [x] 新しい節「招待と登録の完了（U3）」: 6本の API、管理者だけ（401・403）と公開の2つの POST、`fieldErrors` と reason、5つの code、sendResult（内部の PENDING は API で FAILED）、回数の制限が無いこと（R1）と見つけ方の問い合わせ、ログ・監査にトークン・URL・メールアドレスを出さないこと、契約との差（9節の決定 2）
- [x] 「コンテナでの起動と確認」に、`MASTERSMITH_WEB_BASE_URL` を入れた環境は `http://localhost:8080/` で開くこと（`127.0.0.1` はログインが 403、`ms-origin` が増える。N4）、招待メールを見るときは profile `mail` を起動すること
- [x] 環境変数の表: `MASTERSMITH_WEB_BASE_URL` が招待のリンクの元・Origin の確かめ・エラー応答の type の3つに使われること、招待の3つの項目と有効期限を長くしすぎない旨（R2）
- [x] 「API のアクセス制御（U3）」の公開の一覧に2つの道、差し込み口の表に order 310、「監査ログ（U4）」に5つの種類と理由、「監査ログの確かめ方」に R1 の問い合わせを U2 の問い合わせと並べる、「戻し方」に戻して戻し直したときの招待の期限切れと戻しの練習のベース URL の上書き、「スキーマの変更（Flyway）」に V8
- [x] 対応: `infrastructure-specification.md` 8節、`monitoring-design.md` 6節

### Step 28: WAR の中のテンプレートの数え上げと、Mailpit の API での招待メールの確かめ（B1 の引き継ぎ。9節の決定 6、AI だけで行う）

- [x] `./gradlew :backend:bootWar` で作った WAR に `WEB-INF/classes/mail/templates/invitation_ja.html`・`invitation_en.html` があることを確かめる
- [x] WAR をホームの下の作業用の場所（権限 700）へ写し、一覧に無い `stray_ja.html` をディレクトリの項目つきで足した写しでは `UNKNOWN_TEMPLATE` で起動が止まることを確かめる
- [x] 足していない写しを、使い捨ての内部DB・仮の署名鍵・仮の初期管理者（パスワードはシェルで作り表示しない）・`SPRING_MAIL_HOST=localhost`・`SPRING_MAIL_PORT=1025`・差出人（`example.com`）・ベース URL `http://localhost:<番号>` で起動し、起動時の INFO が `templates=invitation` と招待を使える設定であることを確かめる
- [x] `docker compose --profile mail up -d mailpit` で Mailpit を起動し、仮の初期管理者で API から `example.com` の宛先へ ja と en の招待を1件ずつ送る。Mailpit の API で、受けた件数が2件であること、件名が空でないこと、本文の `<html lang>` がそれぞれ ja・en であること、本文にベース URL で始まり `#token=` を含むリンクがあること、有効な期間の文に「24」があることを、AI が機械的に確かめる（結果は有無と件数だけを記録し、本文・トークン・宛先・URL の値は画面の出力にも記録にも写さない）。依頼者は Mailpit の画面を見ない
- [x] 確かめの後に写しのアプリを止め、作業用の場所を消し、Mailpit を止めて消す（受けたメールも消える）。配備したアプリと `.env` は変えない
- [x] 結果（WAR の中の2つのファイル、`UNKNOWN_TEMPLATE` での停止、起動時の INFO、Mailpit の API での確かめの各項目の成否）を `code-summary.md` に記録し、NFR11.1 の「受け手の画面に出る」を API での確かめで代えたことを差として書く（8節）
- [x] 対応: U1 の NFR11.1・P1 の引き継ぎ、BR10.2・BR10.3

### Step 29: 既存の E2E（9節の決定 5）

- [x] Mailpit を起動して `./gradlew e2eTest` を流し、既存の E2E（6件）が通ることを確かめる（公開の道とベース URL の渡し方が既存のログイン・更新・管理の流れを壊さない。`cicd-pipeline.md` 5節）。終わったら Mailpit を止めて消す

### Step 30: 1コマンドの検査（統合の前の関門）

- [x] colima を動かし、README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡して `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を実行し、すべての段が通り、対象DB のテストが SKIPPED でないことを確かめる
- [x] テストの件数と全体・パッケージごとのカバレッジを実測の数字で記録し、Step 1 と比べる（既存のテストが減っていない・失敗していない）
- [x] 秘密情報の検出（Gitleaks）と依存関係の脆弱性検査（OSV-Scanner）が通ることを確かめる（新しい依存は足していない）
- [x] 対応: B3 の共通の完了の条件、`project.md` の Mandated（統合の前の確認）

### Step 31: 記録とコミットの提案

- [x] `code-summary.md`（作ったもの、上流と計画との差、Step 1・Step 30 の実測、パッケージごとのカバレッジ、Step 7 の生成列の確かめの結果、Step 28・29 の結果、9節の決定の反映、契約との差）、`source-manifest.json`、`traceability.json`（機能設計の OK と Deferred の分け方にそろえる）を作る
- [x] 3節の C1〜C7 の区切りで、コミットの内容とメッセージの案を依頼者に示して承認を得る（生成の担当はコミットしない）
- [x] 対応: 段の記録、`project.md` の Change Control

## 6. ストーリー・要件と手順の対応

| ストーリー・要件 | 受け入れ基準・決まり・NFR | 手順 |
|---|---|---|
| US1.1 利用者を招待する（主） | AC1.1.2〜AC1.1.7・AC1.1.10〜AC1.1.13（BR1.1〜BR1.6・BR2.1〜BR2.6・BR3.4・BR4.1〜BR4.5） | Step 4・5・12・13・16・17・22・23 |
| US1.1 | AC1.1.1・AC1.1.8・AC1.1.9（画面） | Deferred（u5-invitation-ui） |
| US2.1 招待の状況を一覧で確かめる（主） | AC2.1.1〜AC2.1.3・AC2.1.5・AC2.1.7・AC2.1.8（BR3.3・BR4.4・BR5.1〜BR5.4） | Step 4・8・9・16・17・22・23 |
| US2.1 | AC2.1.4・AC2.1.6（画面） | Deferred（u5-invitation-ui） |
| US2.2 招待を送り直す・取り消す（主） | AC2.2.1〜AC2.2.10・AC2.2.13（BR6.1〜BR6.4・BR2.4・BR2.7・BR1.5・BR4.3） | Step 8・9・16・17・19・22・23 |
| US2.2 | AC2.2.11・AC2.2.12（画面） | Deferred（u5-invitation-ui） |
| US3.1 自分の言語で招待メールを受け取る（関わる） | AC3.1.2・AC3.1.3・AC3.1.8・AC3.1.9（BR10.1〜BR10.3・BR3.4・BR4.2） | Step 10・11・23・28 |
| US3.2 招待のリンクから登録を完了する（主） | AC3.2.2〜AC3.2.15（BR3.2・BR3.3・BR3.5・BR7.1〜BR7.7・BR9.1） | Step 4・5・18・19・22・23 |
| US3.2 | AC3.2.1・AC3.2.16〜AC3.2.18（画面） | Deferred（u6-registration-ui） |
| CR1.2・CR1.4 | 説明文の言語（BR9.3）、送信の依頼の言語（BR4.2） | Step 4・12・23 |
| CR3 | 監査（BR8.1〜BR8.6、NFR9.4・NFR9.5） | Step 4・14・15・23 |
| CR4 | 認可と公開の範囲（BR9.1・BR9.2、NFR4.1〜NFR4.4） | Step 22・23・24 |
| CR5 | 秘密と個人情報（BR3.5・BR8.6・BR9.4、NFR1.5・NFR2.1・NFR2.2） | Step 4・12・22・23 |
| 保存期間と定期の削除 | BR11.1・BR11.2、NFR9.10 | Step 8・9・20・21・23 |
| スキーマの変更 | BR11.3、NFR10.1・NFR10.2（自動の確かめ） | Step 6・7・27 |
| テストと品質の関門 | NFR9.1〜NFR9.3・NFR9.6・NFR9.7・NFR9.12 | Step 3〜25・30 |
| 性能・接続・観測（測定は後の段） | NFR5.2〜NFR5.4・NFR6.1〜NFR6.9 | Build and Test に引き継ぐこと |
| E2E の代表の流れ | NFR9.11 | u6-registration-ui と B5（B3 は既存の E2E だけ、Step 29） |

## 7. テストの量（Standard）

部品ごとに 5〜8 件の単体テストと、境界の結合テストを置く。成功の場合と、少なくとも2つの失敗・境目の場合を含める（`phases/construction.md` の Testing Standards）。

| 部品 | 単体 | 結合 |
|---|---|---|
| トークン・URL・ベース URL（`invitation.domain`） | `InvitationTokenTest` 7 件＋性質ベース 2 件、`BaseUrlRuleTest` 7 件、`RegistrationUrlTest` 3 件 | — |
| 有効の判定・拒否の理由・ページ（`invitation.domain`） | `InvitationValidityTest` 6〜8 件＋性質ベース 1 件、`InvitationPagingTest` 6 件＋性質ベース 1 件 | — |
| 入力の検証（`invitation.domain`） | `InvitationRequestValidationTest` 8 件＋性質ベース 1 件、`RegistrationValidationTest` 8 件 | — |
| 値の型と code（`invitation.domain`） | `InvitationAvailabilityTest`・`InvitationProblemTypesTest`・出来事・`SendResultTest`（合わせて 6〜8 件） | — |
| スキーマ（V8） | — | `V8MigrationIT` 5 件、`InvitationSchemaIT` 3 件、`V8BackwardCompatibilityIT` 3 件 |
| DB アクセス（`invitation.repository`） | — | `InvitationRepositoryIT` 8 件 |
| テンプレート（`mail.template`） | 既存の `MailTemplateLintTest`（自動）、`MailTemplateRegistryTest` の直し、`InvitationTemplateContentTest` 5 件 | — |
| 設定・トークン・送信の入口（`invitation.service`） | `InvitationSettingsTest` 8 件、`InvitationMailDispatcherTest` 6 件、`InvitationTokenIssuerTest`・`InvitationProblemTypeCatalogTest` 3 件 | — |
| 監査（`audit.domain`・`audit.service`） | `AuditEventFactoryTest` に足す 5〜6 件、`AuditEventListenerTest` に足す 5〜6 件 | `InvitationAuditIT` 6〜8 件、`InvitationAuditWriteFailureIT` 5 件 |
| 招待の管理（`invitation.service`） | `InvitationServiceTest` 8 件 | `InvitationConcurrencyIT` 1〜2 件、`InvitationSendResultIT` 2 件 |
| 登録の完了（`invitation.service`） | `RegistrationServiceTest` 6〜8 件 | `RegistrationRollbackIT` 2 件、`RegistrationConcurrencyIT` 4 件 |
| 定期の削除（`invitation.service`） | `InvitationCleanupJobTest` 4 件 | `InvitationCleanupIT` 5 件 |
| API（`invitation.web`） | — | `InvitationAdminApiIT` 8 件（場面は多い）、`InvitationMailIT` 6〜8 件、`InvitationSendFailureIT` 5 件、`InvitationSendConnectionIT` 1 件、`RegistrationApiIT` 8 件、`RegistrationPublicScopeIT` 3 件、`InvitedPersonAuthenticationIT` 3 件、`InvitationSecretLeakIT` 1〜2 件（場面をまとめる）、`InvitationFlowIT` 1 件 |
| 構造の検査 | `InvitationBoundaryArchitectureTest` 6 件、既存の境界テストをそのまま通す | — |

## 8. この計画で決めたこと・承認済みの文書との差

承認済みの文書は書き換えず、差をここと `code-summary.md` に記録する（`project.md` の決まり）。

| 対象 | 承認済みの形 | この計画での扱い | 理由 |
|---|---|---|---|
| 設定の項目の名前と型 | 「名前はコード生成で決める。例 `mastersmith.invitation.*`」「有効期限は時間の単位の正の整数」（BR1.6、`infrastructure-specification.md` 3.2） | `validity`（`Duration`、既定 `24h`）・`retention`（`Duration`、既定 `90d`）・`cleanup.cron`（既定 `0 45 3 * * *`）と環境変数 `MASTERSMITH_INVITATION_*`。1 時間（保存は 1 日）で割り切れない値で起動を止める（2.2） | BR1.6 の「1 時間で割り切れない値（例 90 分）で止める」を表せる形にするため。依頼者の決定（9節の決定 7） |
| 削除の件数の上限・時刻 | 案 1000 件・3 時 45 分（RD-D2） | 案のとおり。件数の上限は定数 | 既存のリフレッシュトークンの削除と同じ形 |
| 伏せ字の値の型 | `security-design.md` 5節の表はトークン・URL・ハッシュ・要求の DTO・パスワード | 加えて招待先のメールアドレス（`InvitationEmail`）と、業務処理の戻り値・入力の文字列化で伏せる（2.2） | DB アクセスの層の引数と業務処理の戻り値も TRACE の追跡に出るため。`project.md` の Forbidden（メールアドレスをアプリのログに含めない）と NFR2.1 の範囲の中の作り |
| U3 が呼ぶ `user` の口 | 契約 C2 の `existsByEmail(email)`・`findDisplayName → Optional<String>` | 9節の決定 3 のとおり、U3 が通る経路だけを伏せ字の値（`user.domain.RedactedText`）で受け渡す口に変える: `existsByEmail(RedactedText)` と `UserRepository` の伏せ字の引数の検索を足し、`createUser` の中の登録済みの確かめをそれに替え、`findDisplayName` の戻り値を `Optional<RedactedText>` にする。既存のログインの経路（`verifyPassword` の `findByEmail(String)`）と初期管理者の `existsByEmail(String)` は据え置く。契約 C2 の型の差と据え置きを `code-summary.md` に記録する | 同上。`user.service`・`user.repository` の TRACE に招待先と管理者のメールアドレス（氏名の初期値）が出るため。据え置いた2つの経路は、TRACE にすると今もメールアドレスが出る（`project.md` の Forbidden との食い違いが残る。B2 の決定 3 と同じく記録だけ） |
| NFR11.1 の確かめ方（U1 の NFR 要件、B1 の引き継ぎ） | 招待のメールが受け手（Mailpit）の画面に出ることを確かめる | 依頼者の決定（9節の決定 6）で、AI が Mailpit の API で件数・件名・`lang`・リンクの形・有効な期間の文を機械的に確かめることで代え、画面での目視の確かめはしない（Step 28） | 画面に出ることは、Mailpit が API で返すメールと同じものを画面に出すことで裏付ける。目視の確かめが要るときは、配備の後（deployment-execution）に改めて行う |
| 業務処理の部品の分け方 | `logical-components.md` 1節は `InvitationService` の1つ（例） | 招待の管理の `InvitationService` と登録の完了の `RegistrationService` に分ける | 1つのクラスが大きくなりすぎないため。置き場と境界は同じ |
| 待ち合わせの口 | 「待ち合わせ（テストの中の同期の仕組み）で確実に重ねる」（`reliability-design.md` 2.3・3節） | `invitation.service` に `InvitationBarrier`（本番は何もしない既定の Bean） | U2 の `PasswordChangeBarrier` と同じ考え方で、本番の流れを変えずに重なりを確実に作るため |
| 既存のテンプレートのテストの直し | U1 の `MailTemplateRegistryTest.emptyCatalog` は本番の一覧が空であることを確かめる | 空の一覧と無い置き場の確かめは残し、本番の一覧は `invitation` が準備されることを確かめる形に直す。`MailTemplateLintTest` の説明文の「empty in B1」を直す | B3 で一覧に行を足すことは承認済み（BR10.1）で、直さないと本来の確かめの前に失敗する。確かめは緩めない |
| `fieldErrors` の組み立て | U2 の `MeController` にある | `invitation.web` に同じ形の小さな組み立てを置く | `invitation` は `user.web` に依存しない（`InvitationBoundaryArchitectureTest`） |
| 負荷の試験の環境の分け方 | 使い捨ての環境に Mailpit とベース URL、k6 の場面（`cicd-pipeline.md` 4節、場面の用意と手順書は Build and Test） | `docker/perf/compose.yaml` の Mailpit は B3、一時の環境ファイルの行・k6 の場面・`perf/README.md` は Build and Test | トークンの用意の方法を手順書の段で決める承認のため |
| 生成列が成り立たないときの切り替え | 承認どおり固定の1行の排他に切り替え、記録する（`reliability-design.md` 2.3） | 切り替える前に生成を止めて依頼者に諮る（9節の決定 4 の (a)） | 切り替えは V8 の中身（排他に使う行の表）を変え、形が設計に無いため |
| B3 の見せ方の結合テスト | 「API の結合テストで招待 → メールの受け取り → 登録の完了 → ログインまでを通す」（`bolt-plan.md`） | `InvitationFlowIT` を足す | 設計のとおり |

## 9. 依頼者の決定

計画の承認の前に諮った論点について、依頼者が次のとおり決めた。計画の各 Step と8節はこの決定に合わせてある。

1. **作業の場と git の操作**: A。Step 1 で `develop` から短命のブランチ `feature/260925-user-management-b3` を作る。サブモジュールの更新を含まないため、`develop` へは squash で統合する。コミットは生成の後に、依頼者の承認を得て C1〜C7 でまとめて行う（3節、Step 1・Step 31）。
2. **契約 C5・C6・C8・C10 の差の反映**（機能設計の承認の場の G1・G6、2回目のレビューの R-01、NFR 設計の SD-D3）: A。実装は承認済みの機能設計・NFR 設計のとおりに作り、`inception/contract-design/contract-summary.md` は書き換えない。差（C5 の invitedBy が氏名だけで行が無ければ空の文字列、C5・C6 の 400 の `fieldErrors`、C5 の送り直しの「24 時間」が設定の長さになること、C8 の失敗の理由 EMAIL_ALREADY_REGISTERED、C10 の差し込み `validityHours` と「このリンクは {{validityHours}} 時間有効です」の文言）を `code-summary.md` と README の U3 の節の「契約との差」に記録する（Step 27・31。B2 の決定 2 と同じ扱い）。
3. **TRACE の追跡で `user` の口からメールアドレスが出ること**: A。`user.domain` に伏せ字の値の型 `RedactedText` を足し、U3 が通る経路だけを伏せ字で受け渡す口に変える（`existsByEmail(RedactedText)` と `UserRepository` の伏せ字の引数の検索を足し、`createUser` の中の登録済みの確かめをそれに替え、`findDisplayName` の戻り値を `Optional<RedactedText>` にする。Step 4・16・17）。`InvitationSecretLeakIT` は `cherry.mastersmith` 全体を TRACE にして、U3 の流れで招待先・招待した管理者のメールアドレスが出ないことを確かめる（Step 23）。既存のログインの経路（`verifyPassword` の `findByEmail(String)`）と初期管理者の `existsByEmail(String)` は据え置き、TRACE にすると今もメールアドレスが出ることと `project.md` の Forbidden との食い違いが残ることを、8節と `code-summary.md` に記録する（B2 の決定 3 と同じく記録だけ）。契約 C2 の型の差も `code-summary.md` に記録する。
4. **生成を止めて諮る場面**: A。次のときは、生成をその手順で止め、結果と候補を示して依頼者に諮る。
   - (a) Step 7 で生成列の (a)〜(d) のどれかが成り立たない（固定の1行の排他へ切り替える前に止める）
   - (b) Step 7 で V7 までしか知らない Flyway の `validate`・`migrate` が V8 の後の DB で失敗する
   - (c) Step 25 でテストを足してもパッケージごとの下限（行 80%・分岐 70%）に届かない
   - (d) 既存の ArchUnit の境界テストを緩める必要が出る（緩めずに作れる形を先に探す）
   - (e) 8節に挙げたもの以外に既存のテストの期待を変える必要が出る（緩めずに作れる形を先に探す）
5. **統合の前に既存の E2E を流すか**: A。B3 は画面を持たないが、公開の道と `/api/admin/` の下の API を足し、`frontend/playwright.config.ts` にベース URL を渡すため、`team.md` の「画面・認証に関わる変更を統合する前に E2E を手元で実行する」に当たる。Step 29 のとおり、Mailpit を起動して `./gradlew e2eTest` を流し、既存の6件が通ってから統合する（U8 と同じ）。
6. **WAR の中の招待のテンプレートの数え上げと、招待メールの確かめ**（B1 の引き継ぎ）: B（AI だけで確かめる。推奨の A ではない）。依頼者は Mailpit の画面を見ない。Step 28 のとおり、AI が写した WAR を使い捨ての内部DB・仮の署名鍵・仮の初期管理者（パスワードは表示しない）で起動して ja・en の招待を送り、Mailpit の API で件数・件名・`lang`・リンクの形・有効な期間の文などを確かめて記録する。本文・トークン・宛先・URL の値は記録に写さない。確かめの後に写しと Mailpit を消す。配備したアプリと `.env` には触れない。NFR11.1 の「受け手の画面に出る」は API での確かめで代え、そのことを8節に差として記録する。
7. **設定の項目の型**（8節の1行目）: A。`Duration`（`24h`・`90d`）で受け、1 時間（保存の日数は 1 日）で割り切れない値・0 以下の値で起動を止める（2.2、Step 2・12・13）。

## Testing Contract

```json
{
  "version": 1,
  "methodology": "test-after",
  "source": "team",
  "ordering": "テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。",
  "scope": "classic",
  "test_strategy": "standard",
  "project_type": "brownfield",
  "applicable_notes": [
    {
      "layer": "org",
      "text": "We treat tests as a first-class deliverable in every Bolt. The specific\nmethodology (TDD, BDD, ATDD, or classic test-after) is affirmed at\npractices-discovery and recorded in `team.md` under this heading with explicit\n`Methodology` and `Ordering` fields; Code Generation resolves those fields\nindependently from coverage, tooling, and scope notes.\n\nWhen no posture has been affirmed, our default per scope is:\n- **Methodology**: test-after\n- **Ordering**: implement each applicable testable layer, then write and run\n  that layer's tests.\n- `mvp`, `enterprise`, `feature`, `infra`, `classic` add an 80% line-coverage\n  floor and CI execution before merge.\n- `bugfix`, `security-patch` add a targeted regression for the specific\n  bug/vulnerability and require the existing suite to remain green.\n- `express` uses the Minimal strategy: requirement-driven unit tests (one per\n  requirement, with a happy-path floor per component); existing tests remain\n  green.\n- `poc`, `refactor`, `workshop` add no extra new-test floor and require the\n  existing suite to remain green.\n\nThe active `Test Strategy` still applies in every scope and determines test\nvolume/types. Scope floors are additive; they never reduce or replace the\nselected strategy.\n\nBuild and Test verifies defined coverage floors and affirmed quality targets;\nthey may not be weakened to make a step pass.\n\nAffirm a stricter posture in `team.md` if the team commits to one."
    },
    {
      "layer": "team",
      "text": "- **Methodology**: test-after\n- **Ordering**: テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。\n- テストは各 Bolt の成果物の一部であり、テストのない機能は完成とみなさない。テスト量はワークフローの Test Strategy に従い、以下の下限はそれに追加される。\n- カバレッジの下限は、すべての Intent に共通で **行カバレッジ 80% 以上、分岐カバレッジ 70% 以上** とする。バックエンド・フロントエンドの両方に適用し、下回ったらビルドを失敗させる。道具はバックエンドが JaCoCo、フロントエンドが `@vitest/coverage-v8`（`thresholds` 設定）。\n- バックエンドでは、全体の合計に加えて、すべてのパッケージごとにも同じ下限（行 80%・分岐 70%）を当てる。ただし、既存のパッケージに単独で下限を下回るものがあれば、パッケージごとの下限は新しく作るパッケージだけに当てる（既存のパッケージは全体の合計で判定する）。どちらになるかは、パッケージごとの下限を入れる Bolt で既存のパッケージのカバレッジを実測して決め、結果を記録する。\n- 全体の合計で判定している既存のパッケージ（`backend/build.gradle.kts` の `packagesJudgedByTotal` の一覧。`user.domain`・`user.repository`・`user.service` を含む 22 パッケージ）に手を入れる Bolt では、テストを足してそのパッケージの下限（行 80%・分岐 70%）を満たし、一覧から外してパッケージごとの下限の対象に必ず戻す。カバレッジは、その Bolt のテストを足した後に `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で実測し、値を記録する。一度外したパッケージは一覧に戻さず、一覧を増やす変更はしない。\n- カバレッジの計測から外すのは、アプリの起動クラス、設定値だけのクラス、自動生成コード、`vendor/` 配下に限る。除外を後から増やして実質的に下限を下げることはしない。パッケージごとの下限を満たすために除外を増やすこともしない。\n- DB を使うテストは、本番と同じ種類の DB をコンテナで起動して使う（Testcontainers）。そのため、ローカルと CI にコンテナの実行環境があることを前提条件として文書化する。テストごとにデータを用意して巻き戻し、実行順に依存させない。表を作る・消す操作（DDL）がその場で確定して巻き戻せない DB（MySQL・MariaDB など）で表を作るテストは、巻き戻す代わりに、テスト（またはテストのクラス）ごとに名前の重ならないスキーマ（MySQL・MariaDB ではデータベース）を作り、終わったら消す。\n- 対象DB（MySQL・MariaDB・PostgreSQL）の結合テストは、3種類すべてを `./gradlew verify` の中で毎回実行する（CI も同じ）。Intent `260923-dsl-schema-loader` の Build and Test で `verify` の時間と colima の VM のメモリを実測したうえで、この形に決めた。コンテナの実行環境が無いときの扱いは Way of Working のとおり。\n- 外へ送るメール（SMTP）を使う機能のテストは、送信の部品をモックで置き換えて済ませず、テストの中で JVM の中に起動するテスト用の SMTP の受け手で実際に受け、宛先・件名・本文（HTML）・言語を確かめる。受け手にコンテナは使わない（コンテナの実行環境が無いときに飛ばしてよいテストは、対象DB のテストだけのままとする）。送信の失敗（受け手が接続を拒む・応答しない）のテストも必ず入れる。実在の宛先へは送らない。受け手の具体の道具は、採用の前のライセンスの確認とあわせて設計の段で決める。\n- 画面からの一連の操作を確かめるテスト（E2E）は Playwright（`./gradlew e2eTest`）で書き、`./gradlew verify` と CI の外に置く。本数は代表的な流れに絞り、機能の Intent ごとに代表の流れを1本まで足す。新しく足す流れは、前のテストが作った状態に頼らず、利用者などの前提を自分で作る。E2E は、画面・認証に関わる変更を統合する前と、リリースの前に手元で実行する。\n- 入力と出力の性質をランダムな入力で確かめるテスト（性質ベースのテスト）を、純粋な関数（ロック判定の回数計算、有効期限の判定、入力の検証など）に一部適用する。道具は Java が jqwik、フロントエンドが fast-check。失敗時の乱数の種を記録して再現できるようにする。\n- テストの説明文（テスト名、`describe` / `it`、`@DisplayName`）は英語で書く。テストデータは日本語でよい。\n- フロントエンドのテストは対象と同じ場所に `*.test.ts` / `*.test.tsx` として置き、Vitest ＋ Testing Library（jsdom）＋ user-event ＋ vitest-axe を使う。画面部品ごとにアクセシビリティ検査を1件入れる。\n- Java のテストは `src/test/java` に、対象と同じパッケージ構成で置く。単体テストは `XxxTest`、Spring や DB を起動する結合テストは `XxxIT` とし、分けて実行できるようにする。\n- 時刻に依存する処理（有効期限、ロックの解除など）は注入可能な時計（`Clock` 等）から現在時刻を取得し、テストで `sleep` や実時刻に依存しない。不安定なテストは放置せず、原因を直すまで統合しない。\n- 認証・認可・監査に関わる機能では、次のテストを必ず書く。★印の項目は要件（しきい値や動作）が未確定のため、要件定義で決めてからテストを書く。\n  - アカウントロック: 失敗回数のしきい値の境界（しきい値−1回ではロックされない／しきい値ちょうどでロックされる）、ロック中は正しいパスワードでも拒否、ログイン成功時の失敗回数の扱い。★しきい値、回数を数える期間、ロックの解除方法\n  - ログイン: 存在しないユーザーとパスワード誤りで、応答からユーザーIDの存在を推測できないこと\n  - トークン: 有効期限の境界（直前は有効／直後は無効）、署名の改ざん、署名方式の指定を悪用した改ざん（`alg: none` 等）、ログアウト後のリフレッシュトークンの拒否、ログアウト後もアクセストークンが有効期限まで使えること（決定済みの仕様として明示する）。★各トークンの有効期限の値、リフレッシュトークンを使うたびに作り直すか\n  - 初期管理者の自動作成: 2回目以降の起動で重複作成しない、設定が無い／不正なときの動作、パスワードがログに出ない。★設定が無いときに起動を止めるか\n  - 認可: 未認証（401）、管理者フラグなし（403）、管理者（200）をサーバー側のテストで確かめる（画面で管理メニューを隠すことはサーバー側の検査の代わりにしない）\n  - 監査ログ: 対象イベントごとに必須項目が記録されること、存在しないユーザーIDでのログイン失敗も記録されること。★監査ログの書き込みに失敗したときに操作を失敗させるか\n  - 秘密情報の漏えい: ログ・監査ログの出力にパスワード・トークンの値が含まれないこと\n  - 構造化ログ・分散トレース: 決めた形式で出る、トレースIDがログに含まれる、外部エクスポートが既定で無効であること\n  - 画面: ログイン画面のアクセシビリティ検査、ロック時のメッセージ表示、ログアウトでトークンが破棄されること\n  - 招待と登録の完了: 招待の有効期限の境界（直前は有効／直後は無効）、使い終えた招待・取り消した招待の再使用の拒否、改ざんした招待・存在しない招待の拒否（応答から利用者の存在を推測できないこと）、招待中（登録が終わっていない）の利用者はログイン・トークンの更新・アクセストークンの認証のどれでも拒否されること。★招待の有効期限の値、再送・取り消しの扱い\n  - パスワードの変更: 今のパスワードの確かめ、パスワードの規則の境界、変更の後のリフレッシュトークンの扱い（決めた側の動作を明示したテストにする）。★パスワードの規則、変更の後にほかの端末のリフレッシュトークンを無効にするか\n- 利用者へメールを送る機能では、Code Style と `project.md` のメールの決まりを確かめる次のテストを必ず書く（認証・認可・監査と同じ扱い）。★印の項目は要件が未確定のため、要件定義で決めてからテストを書く。\n  - 本文のエスケープ: 差し込む利用者の値（氏名・メールアドレスなど）に `<`・`>`・`&`・`\"`・`'` を含めても、タグや属性としてそのまま出ないこと。利用者の値をエスケープしない差し込み（`{{{ }}}`・`{{& }}`）で入れていないこと、属性の値を二重引用符で囲んでいること（エンジンの `{{ }}` は `'` を置き換えないため）\n  - テンプレートの描画: 日本語と英語のすべてのテンプレートを描けて、件名が空でないこと、差し込み漏れ（`{{` の残り）が無いこと、ライセンスヘッダーが描いた本文に出ないこと\n  - 招待の URL: 設定したベース URL だけから組み立て、要求の Host ヘッダーから組み立てないこと。ベース URL の設定が無いときに要求の Host を変えても、招待の URL に出ないこと。★ベース URL の設定が無いときの動作\n  - トークンと URL の漏えい: 招待のトークンと招待の URL が、アプリのログ・監査ログ・トレースの属性・エラー応答に含まれないこと（既存の `*SecretLeakIT` と同じ形で確かめる）\n  - ヘッダーへの差し込み: 宛先・件名・差し込む値に改行（CR・LF）を含めても、メールのヘッダーが増えず拒否されること\n  - 送信の失敗: 送信が失敗したとき（受け手が接続を拒む・応答しない）に、宛先のメールアドレス・SMTP の応答・資格情報が応答とログに含まれないこと。★送信が失敗したときの業務の動作（招待を失敗させるか、再送に回すか）\n- 利用者が投入する DSL（YAML）を読み込む機能では、Code Style の「信頼できない入力」の決まりを確かめる次のテストを必ず書く（認証・認可・監査と同じ扱い）。上限の具体的な数値は設計の段で決め、決めた値の境界で確かめる。\n  - 大きさ: 上限ちょうどは受け付け、上限を超えると拒否される\n  - 入れ子の深さ: 上限ちょうどは受け付け、上限を超えると拒否される\n  - 別名（アンカー）: 展開の数が上限を超えると拒否され、別名の展開の爆発で処理が止まらない\n  - タグ: 任意の型を作るタグ（`!!` など）を含む DSL は拒否され、型が作られない\n  - 重複キー: 同じキーが重なる DSL はエラーになり、後の値で黙って上書きされない\n  - JSON Schema の `$ref`: 外部の URL を取りに行かない\n  - 拒否の応答: Problem Details の形で返り、YAML・JSON Schema の部品の例外のメッセージを含まない\n\n- 内部DB（組み込みの H2）を使うテストは、コンテナではなく本番と同じ組み込みの H2 で行う。Testcontainers は、コンテナで動かす対象DB（後続 Intent D・E で扱う業務DB）のテストに使う。Walking Skeleton の「DB を使うテスト1件以上（本番と同じ種類の DB をコンテナで起動する）」も、内部DBについてはこの読み方とする。 (learned 2026-09-22)"
    },
    {
      "layer": "project",
      "text": "- テストの件数やカバレッジを報告するときは、`./gradlew verify` がテストのタスクを UP-TO-DATE で飛ばすことがあるため、`:backend:cleanTest :backend:cleanIntegrationTest` を付けて実行し直し、実測の数字だけを報告する。 (learned 2026-09-23) \n- 負荷の環境や配備先が決まらないと測れない目標（応答時間のパーセンタイル、運用の指標、ファイルの権限など）は、Build and Test で `Unverified` とし、持ち主の段（performance-validation・observability-setup・deployment-execution）を明記して引き継ぐ。目標を緩めて「満たした」ことにはしない。 (learned 2026-09-23) \n- 負荷の試験は、配備した環境とは別の使い捨ての環境（仮の署名鍵・仮の利用者、終わったら消す）で行い、本物のデータと監査ログを汚さない。手順は perf/README.md。 (learned 2026-09-23) \n- 負荷の試験で、アプリが止まる・極端に遅いなどの結果が出たときは、環境を起動し直して再現させ、原因をログと状態（OOMKilled など）で確かめてから記録する。 (learned 2026-09-23) \n- 同時の重なりを確実に作るため、本番のコードを変えずに、監査の書き込みの時間を測る LongSupplier（AuditEventListener で2本目を借りる直前に呼ばれる）をテストで差し替えて待ち合わせる方式にした。既存の LoginConcurrencyIT は 8 スレッドでプールの 10 に届かず、前回の失敗のログインで尽きなかった理由の1つと見られる。 (learned 2026-09-23) \n- Intent の流れに Performance Validation の段が無く、負荷の環境（使い捨ての環境）を手元で用意できるときは、k6 の試験と NMT の測定の持ち主を Build and Test とし、Unverified で引き継がずにその段で実行する。 (learned 2026-09-23) \n- 修正の前の設定（例: 上限 1g）も修正の後の環境（例: CPU 4 の VM）で流し（pre1g）、要件の前提（VM を上げても F3 が起きる）を実測で裏付ける。 (learned 2026-09-23) \n- パッケージごとのカバレッジの下限と SpotBugs の SQL_ の関門を U1 の計画に入れた。team.md の決まりだが今のビルドに無く、この Intent で最初に作る単位のため。U1 の設計の文書には無い作業。 (learned 2026-09-24) \n- U4 で既存の AuditSecretLeakIT の列の一覧に V6 の4列を足し、テストの JVM のヒープを 1g にした。前者は承認済みの V6 と必ず食い違うため、後者は構造の検査がクラスを持ち続け U3 の 10MB 超えのテストでヒープが尽きたため。どちらも計画に無い変更で、依頼者に確かめる。 (learned 2026-09-24) \n- 応答しない対象DB の TIMEOUT の確かめに、テストの中で開いた ServerSocket（受け付けて何も返さない）を使う。外の端末に頼らず確実に再現できる代わりに、本物の DB の遅延ではない。 (learned 2026-09-24) \n- パッケージごとのカバレッジの下限は新しいパッケージだけに当てた。実測で audit.service（行 77.2%）・common.health（行 79.2%）・auth.repository（分岐 50.0%）が単独で下回ったため（team.md の決まりどおり）。既存の 22 パッケージを一覧で外し、新しいパッケージは自動で対象になる。 (learned 2026-09-24) \n- 負荷の試験で接続プールが尽きたかを確かめるときは、使い捨てのアプリにだけ MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics を渡して /actuator/metrics の hikaricp の値を読み、数秒ごとの使用中の数ではなく、待ちの時間切れの累計と借りるまでの待ちの最大で判断する（配備したアプリの公開の範囲は変えない）。 (learned 2026-09-24) \n- k6 などの長い試験は caffeinate -i を付けて流し、PC の自動のスリープで要求が止まって結果が崩れるのを防ぐ（バッテリー駆動のまま 414 秒スリープし、要求が 6分52秒止まった）。内部DB に SQL で直接入れた試験用の利用者はロックの状態の行が無いため、同時のログインを流す前に1人ずつログインさせて行を作る。 (learned 2026-09-24) \n- 軽い API の性能は、投入を重ねて内部DB のファイルが膨らんだ状態（悪い側の条件）のまま測る。メモリの最大（memory.peak）を比べる試験の前は、アプリのコンテナを作り直して前の最大の値を消す。 (learned 2026-09-24) \n- Performance Validation の段が無いため、2件目の直しの負荷の試験での確かめ（Q5: A）の持ち主を Build and Test とした（project.md の学びどおり）。 (learned 2026-09-24) \n- colima の PC で ./gradlew verify を流すときは、README の DOCKER_HOST と TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE をシェルに渡さないと対象DB のテストが SKIPPED になり、パッケージごとのカバレッジの下限で失敗した。付けて流し直した結果を基準とした。 (learned 2026-09-25) \n- unit-test-instructions.md の k6 inspect のコマンドは --include-system-env-vars が無いと場面の名前が undefined になり確かめにならなかった。承認済みの文書は変えず、実際には付けて流し、code-summary.md に差を記録した。 (learned 2026-09-25) \n- 1回目の verify で AccessTokenApiIT の 6 件が接続の失敗で落ちた。変更の経路に触れず、同じ時刻に Gradle の作業プロセスとの接続も時間切れだったため PC の負荷による一時的な失敗と見立て、段の中の直しの1回目としてコードを変えずに verify を流し直して通した。原因は確かめていない。 (learned 2026-09-25) \n- 依頼者の判断で、試験のイメージを mastersmith:local ではなく mastersmith:followup-fixes で作り、perf/README の手順（local を作り直す）から外れた。配備したアプリは k6 のあいだ止め、約 17 分後に同じコンテナで起動し直した。 (learned 2026-09-25) \n- 試験の後にロックの状態の行の数を数える問い合わせの列の名前を誤り、使い捨ての環境を消した後で取り直せなかった。合格の条件（ログインの checks と 500 の件数）で判定した。消す前に確かめの結果を見てから片付けるべきだった。 (learned 2026-09-25) \n- Q4（確かめられたときだけ直す）が team.md の「不安定なテストは原因を直すまで統合しない」と食い違いうるため、追加の質問 F3 で、再現できなければ不安定と確かめられていない扱いとして統合してよいことを確かめた。 (learned 2026-09-25) \n- 要件 FR2.2（目標は計画の承認の場で決める）を満たすため、依頼者の決定（Q1: A）で、計画を書く前に dslMixed でメモリの内訳を測ることにした。測る間は配備したアプリを止める。 (learned 2026-09-25) \n- 生成で、テストの既定で MBean の登録を無効にする置き場を、計画の候補の TestDatabase ではなくテストの EnvironmentPostProcessor にした。TestDatabase を使わずに Spring を起動するテストのクラスが 15 あり、漏れるため。再現の結合テストは 12 回では伸びが小さく判定がはっきりしないため、24 回・履歴の上限 3 にした。 (learned 2026-09-25) \n- AccessTokenApiIT は、同じ例外の文言を仕組み（colima の IPv4 の転送と Java の IPv6 の待ち受けの番号の重なり）ごと再現できたことを原因の確認とみなし、依頼者の決定（D7）でテストの JVM に preferIPv4Stack を付けて直した。実際の1回目がこれで起きたかは確かめられず、今回の繰り返しでは重なりは起きなかった。 (learned 2026-09-25) \n- Test Strategy は Minimal だが、要件 FR5 と計画の「Build and Test に引き継ぐこと」のため、結合・性能・セキュリティの手順書も作り、この段で dslMixed・--storage --compact（40 回）・詰め直しの最中のログイン・refresh・dslCycle を流した。 (learned 2026-09-25) \n- 計画の dslCycle を最初の台本に入れ忘れ、配備したアプリをもう一度（約 4 分）止めて流した。負荷の試験の台本を書く前に、計画の「Build and Test に引き継ぐこと」の項目を一つずつ台本の手順と突き合わせる。 (learned 2026-09-25)"
    }
  ],
  "obligations": {
    "strategy": "standard",
    "strategy_volume": [
      "Five to eight tests per component.",
      "Unit tests plus integration tests for key boundaries.",
      "Add E2E, performance, or security tests when requirements demand them."
    ],
    "scope_floor": [
      "Keep the existing test suite green.",
      "This scope adds no extra new-test floor beyond the selected test strategy."
    ],
    "combination_rule": "Apply every selected-strategy obligation and every scope-floor obligation; neither replaces the other, and a targeted scope regression may add the narrowest necessary test type beyond the strategy default."
  },
  "plan_profile": {
    "methodology": "test-after",
    "runner_step": "Verify the existing test runner/configuration and record the exact unit-scoped command.",
    "runner_ready_before_first_test": true,
    "testable_layers": [
      "Data model / database behavior",
      "Repository / data access",
      "Business logic",
      "API / endpoint",
      "Frontend behavior"
    ],
    "steps": [
      "Project structure and production configuration skeleton.",
      "Verify the existing test runner/configuration and record the exact unit-scoped command.",
      "Data model / database behavior - implement.",
      "Data model / database behavior - write and run its tests after implementation.",
      "Repository / data access - implement.",
      "Repository / data access - write and run its tests after implementation.",
      "Business logic - implement.",
      "Business logic - write and run its tests after implementation.",
      "API / endpoint - implement.",
      "API / endpoint - write and run its tests after implementation.",
      "Frontend behavior - implement.",
      "Frontend behavior - write and run its tests after implementation.",
      "Environment/build configuration.",
      "Documentation and traceability."
    ]
  },
  "input_sha256": "sha256:591505487a7fb6a8983ed2b2b5ed40524251e26ce603ffd5e0b713cb57a870cd",
  "contract_sha256": "sha256:b0e0f1eed9e80e43e2a774e6087e20f8a66c419412203955de70d736ee76c67f"
}
```

Testing Contract の `plan_profile.steps` との対応: 骨組みと本番の設定は Step 1・2、テストの実行の準備は Step 3（最初のテストの Step 5 より前）、ドメインの値の型と純粋な関数（業務処理の土台）は Step 4・5、データの形（V8・エンティティ）は Step 6・7、DB アクセスは Step 8・9、招待メールのテンプレートと業務処理は Step 10〜21、API は Step 22・23、構造の検査・静的解析・カバレッジ・環境とビルドの設定・確かめは Step 24〜26・28〜30、文書と記録は Step 27・31。U3 は画面を持たないため、画面の層（Frontend behavior）の手順は無い（`frontend/playwright.config.ts` の1行は E2E の設定で、画面の振る舞いではない）。

## Build and Test に引き継ぐこと

| 項目 | 引き継ぐ内容 | 持ち主 |
|---|---|---|
| カバレッジの実測 | `:backend:cleanTest :backend:cleanIntegrationTest` を付けた verify で、全体と `invitation` の4つのパッケージと手を入れたパッケージの値をもう一度実測して記録する | Build and Test |
| verify の時間 | U3 の結合テストを足した後の `./gradlew verify` の時間を測り、B2・U8 の後の実測と比べる | Build and Test |
| 負荷の試験の用意 | 一時の環境ファイル（`app.env`）に `SPRING_MAIL_HOST=mailpit`・`SPRING_MAIL_PORT=1025`・差出人・`MASTERSMITH_WEB_BASE_URL=http://app:8080` を足す手順、`--profile mail` の起動、試験の後にアプリのログに `/register#token=` と `@example.com` が無いことを件数で確かめる手順を `perf/README.md` に書く。`perf/k6/scenarios.js` に8つの場面（招待・送り直し 5 秒、一覧（1ページ目と最後のページ）・取り消し・リンクの確かめ・登録の完了の成功・入力の誤り・リンクの拒否 1 秒、同時 10 件、BR7.4 は入れない）を足す。登録の完了のトークンの用意（Mailpit の API から取り出すか、既知のトークンのハッシュで招待の行を直接入れるか）をこの段で決める。台本を書く前に、この表の項目を台本の手順と1つずつ突き合わせる（`project.md` の Testing Posture） | Build and Test（用意）・performance-validation（測定） |
| 性能と接続の測定 | NFR6.1〜NFR6.5、NFR5.3（使い捨ての環境の hikaricp の待ちの時間切れの累計 0・借りるまでの待ちの最大、流した成功の件数と監査の INVITATION_ISSUED・REGISTRATION_COMPLETED の件数の一致を消す前に数える）。`caffeinate -i`、測る間は配備したアプリを止め、VM の余裕を読み取りで確かめ、Mailpit と k6 の分が混ざることを明記する。BR7.4 の経路は Unverified | performance-validation |
| 指標・警報・運用 | 6本の API の `http_server_requests_milliseconds_*` の `uri`・`method`・`status` と U1 の `mastersmith.mail.send`（`mail.template=invitation`）の実際の名前、既存の警報（`ms-origin` を含む）の流し直し、R1 の問い合わせの列の名前と、流す頻度と担当（NFR 要件・NFR 設計のレビューの R-02）、招待の定期の削除の失敗の見え方（N3） | observability-setup |
| 配備と戻し | 配備の `.env` の複写と、秘密でない行（`MASTERSMITH_WEB_BASE_URL=http://localhost:8080`・`SPRING_MAIL_HOST=mailpit`・`SPRING_MAIL_PORT=1025`・差出人）の追加（Q1 A）、V7・V8 の前のバックアップと戻し用のタグ、戻しの練習（V7・V8 をまとめて1回、ベース URL の上書き1行、Hibernate の `validate` が余分な表を見ないこと）、配備の後の確かめで利用者と監査の行が残ることを先に伝える（N5）、配備の後にアプリのログの件数の確かめ | deployment-pipeline（手順）・deployment-execution（実行） |
| 契約の反映 | 9節の決定 2 のとおり、C5・C6・C8・C10 の差が `code-summary.md` と README に記録されているかを確かめる。U5・U6 が `fieldErrors` と C5 の invitedBy（氏名だけ）を使うことは B5 の計画で確かめる | B5（U5・U6 のコード生成の計画） |
| E2E の代表の流れ | 招待から登録の完了までの E2E-1 と、Mailpit の API でリンクを取り出す助けの部品（U3 は本文に URL の文字を載せる） | B5（u6-registration-ui） |
| 残る危険 | R1（回数の制限なし、急な増えに自動で気づく仕組みなし）・R2（有効期限の上限なし）・R3（公開の道のアクセストークン）は受け入れ済み。README に書いたことを確かめる | 受け入れ済み（記録だけ） |
