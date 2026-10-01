# Functional Design の質問 — u3-user-admin-api

単位 U3（`aidlc/spaces/default/intents/260930-user-admin/inception/units-generation/unit-of-work.md`、種類 service、大きさ XL）の機能設計のための質問です。受け持つストーリーは US1.1・US2.1・US3.1・US4.1・US5.1 のサーバー側の受け入れ基準で、前提と読み方・「要件との差」（差1〜差7）・依頼者の決定 M1〜M9 は `aidlc/spaces/default/intents/260930-user-admin/inception/user-stories/stories.md` に従います。契約は C1・C2・C3・C6・C8（`aidlc/spaces/default/intents/260930-user-admin/inception/contract-design/contract-summary.md`）、部品と決定は `components.md`・`decisions.md` の ADR-001・ADR-003・ADR-006・ADR-007 です。Contract Design からの申し送り R-04・R-06・R-07 をこの段で決めます。Bolt の計画（`bolt-plan.md`）のとおり、設計は U3 を1つの単位として通し、前半（B3: 一覧・氏名と言語）と後半（B4: 管理の操作・最後の管理者の保護）への分け方はコード生成の計画で決めます。既存のコードは `backend/src/main/java/cherry/mastersmith/` の `user/`・`auth/`（`LoginService`・`LoginAttemptStateRepository`・`LockPolicy`・`LockState`）・`audit/`（`AuditEventType`・`AuditFailureReason`・`AuditEventListener`）・`access/`（`AccessProblemTypes`・`AdminAccessDeniedEvent`）・`invitation/`（結果の型・`InvitationAdminController`・`InvitationRepository`・`InvitationBarrier`）・`common/observability/TraceAspect.java`・`common/error/web/GlobalExceptionHandler.java`・`dslmanage/web/DslWebConfig.java` と、`backend/src/main/resources/db/migration/`（V1〜V8）を確かめました。

## 設計の要点（案）

上流の決定とコードの確認から導ける、この単位の機能設計の見通しです。質問の答えで決まる点は（Qn）と書きます。

1. **パッケージと層**: 新しいパッケージ `useradmin` に、`web`（`/api/admin/users` の controller と要求・応答の record）・`service`（業務処理と結果の型、`UserAdminProblemTypeCatalog`）・`domain`（`UserAdminProblemTypes`、拒否の理由の判定の純粋な関数、監査の出来事の型）を置く。新しい境界テスト `UserAdminBoundaryArchitectureTest` で、`useradmin` が `user`・`auth` の service の口と値の型・`common.paging` だけを使うことを固定する（ADR-001）。エンティティは持たない（一覧の行は読み取りの形で保存しない、`components.md`）。表の列は足さず、スキーマの変更は無い見込み（停止の列は U1 の V9、監査は既存の列、ADR-006）。
2. **一覧（C3 `GET /`、C8 `findAdminPage`）**: 並びは `users.created_at` の古い順、同じ時刻は `user_id` の小さい順（AC1.1.1）。ページは C2 の `Paging.parsePage`・`offsetOf` で決め、最後のページより後は全体の件数つきの空の一覧（差7）。検索はメールアドレスか氏名の部分一致で、`%`・`_`・`\` をエスケープして文字どおりに一致させ、問い合わせは名前つきの引数だけで組み立てる。メールアドレスは保存のときに小文字にそろえてある（`EmailAddress.normalize` が `toLowerCase(Locale.ROOT)`）ため、氏名と検索の文字を小文字にそろえて比べる。小文字にそろえる範囲（ASCII だけか Unicode か）と、`created_at` の索引の要否は NFR 設計で確かめる。検索の文字の置き場・受け方・上限は Q1・Q2・Q3。
3. **ロックの判定の結果（C8 `lockViewsOf`）**: ページの利用者 ID（最大 20 件）のロックの状態の行を、排他なしの1回の問い合わせで読む。判定は既存の `LockPolicy` と同じ境界で、`locked` は「解除の予定の時刻があり、今の時刻 < 解除の予定の時刻」、`lockedUntil` はロック中のときだけ、`resettable` は「表の失敗回数が 1 以上」（解除の予定の時刻を過ぎた利用者を含む、M2 B）。行の無い利用者は `(false, null, false)`。判定は `auth.domain` の純粋な関数にし、性質ベースのテスト（jqwik）の対象にする。今の時刻は注入した `Clock` から取る。失敗回数そのもの・ダミーの行（負の ID）は `useradmin` へ渡さない（FR1.6）。
4. **`self` の項目と操作した人**: 操作する管理者の利用者 ID は、引数ではなく要求の文脈（認証の主体）から読む（`team.md` の Code Style、`InvitationRequestContextResolver` の前例）。一覧の各行の `self` はこの ID と比べて決める。
5. **拒否の理由の判定**: 操作ごとの当たりうる理由の表（`stories.md` の前提）を、判定の順「対象がいない → 自分自身 → 対象が停止中（印の操作だけ）→ 変えるものが無い → 最後の有効な管理者」の純粋な関数にし、最初に当たった理由1つを返す。業務処理は想定内の失敗を結果の型（sealed interface の record）で返し、controller が `switch` で場合を尽くして `BusinessException` に変える。拒否のときはトランザクションを何も変えずに確定させ、確定の後の監査（`AFTER_COMMIT`）が届くようにする。
6. **行の排他（C8、ADR-007）**: 印を付ける・外す・止めるは、`lockAdminRowsInIdOrder` で管理者の印を持つ行と対象の行を利用者 ID の小さい順に `PESSIMISTIC_WRITE` で排他し、排他の後に有効な管理者を数える。停止を解くは `lockUserRow` で対象の行だけを排他する。失敗回数を戻すの排他は Q4。氏名と言語の変更は排他せず、後に確定したものが勝つ（自分のプリファレンスの保存と同じ）。排他の待ちの上限切れは `Busy` として返し `USER_ADMIN_BUSY`（409）にする。既存の `LoginAttemptStateRepository.lockForUpdate` は上限切れを例外で投げるため、`auth.service` の中で受けて `Busy` に変える。受けた後にトランザクションが確定できるか（巻き戻しの印が付かないか）は NFR 設計で確かめる。
7. **待ち合わせの口**: 同時の重なりのテスト（AC2.1.6・AC2.1.12・AC3.1.5・AC4.1.5）のため、「有効な管理者を数える直前」に `useradmin.service` の待ち合わせの口（本番は何もしない既定の部品、`InvitationBarrier` と同じ形）を置く。「失敗回数の行を排他した直後」の口は `auth.service` に置く。どちらも本番の流れは変えない。
8. **止める（C1）**: 対象の停止の状態を変え、同じトランザクションの中で `revokeAllRefreshTokens` を呼ぶ。失敗回数とロックの状態は変えない（AC3.1.10）。止める操作とトークンの更新の重なりの隙は塞がない（M8 B）。
9. **失敗回数を戻す（C8 `resetLoginFailures`）**: 失敗回数を 0、解除の予定の時刻を無しに、明示の更新の問い合わせ1回で書く。行が無い・失敗回数が 0 は `NothingToReset`（`USER_ADMIN_NO_CHANGE`）で、行を作らない。停止中の利用者・自分自身にも許す（AC4.1.7・AC4.1.8）。
10. **氏名と言語の変更（C3 `PUT /{userId}/profile`、C8 `updateProfile`）**: 既存の `DisplayName`（254 コードポイント、前後の空白を除く、制御文字・書式の文字を拒否）と `Language`（ja・en）の決まりをそのまま使う（FR6.2）。誤りは既存の項目ごとの誤りの形の `VALIDATION_FAILED`（400）、対象がいなければ `USER_NOT_FOUND`（404）。同じ値でも成功（204）。成功・失敗とも監査しない（FR6.4）。要求の知らない項目は無視する（AC5.1.6）。
11. **監査の出来事（C6）**: `useradmin.domain` に監査の出来事の型を置き、`audit` が受けて記録する（`audit` から `useradmin` の出来事の型への依存を足す。ADR-001 の向き）。`AuditEventType` に5つ、`AuditFailureReason` に `SELF_OPERATION`・`TARGET_SUSPENDED`・`NO_CHANGE`・`LAST_ACTIVE_ADMIN` を足し、`USER_NOT_FOUND` は既存の値を使う。どの名前も 32 文字以内（最長は `LOGIN_FAILURES_RESET` の 20 文字）。対象がいないときは要求の `userId` をそのまま `target_user_id` に入れる（V7 の列は参照の制約を持たない）。操作した人が外れていたときと、上限切れのときの監査は Q6・Q7。
12. **漏えい（AC1.1.6、NFR3）**: 応答の record（`AdminUser` に当たるもの）と `UserAdminSummary` は `toString` でメールアドレスと氏名を伏せる。検索の文字は controller の引数の時点で `SearchText`（`toString` で伏せる record）で受ける（Q1・Q2）。URL の問い合わせの部分は既存の `UrlQueryStrippingObservationFilter` と `AdminAccessDeniedEvent.normalizeRequestPath` が除く。`q` を付けた TRACE の漏えいのテスト（`UserAdminSecretLeakIT`）で確かめる。
13. **カバレッジの一覧**: `packagesJudgedByTotal` の `auth.domain`・`auth.repository` は B1（U1）で一覧から外れる計画のため、U3 ではパッケージごとの下限を満たし続ける作業になる。`audit.repository`・`access.domain`・`access.service` には、Q6 の答えによっては手が入る（手を入れたら一覧から外す作業が付く、`team.md`）。`useradmin` の各パッケージは新しいパッケージとして自動で下限の対象になる。
14. **U3 で扱わないこと**: 停止中の管理者が `/api/admin/` で 401 を受けたときの「アクセスの拒否」の監査の理由（`stories.md` の「残る隙と申し送り」）は、アクセストークンの認証の入口の論点のため U1 の機能設計で扱う。

## 決まっていること（質問にしない）

| 決まっていること | 出典 |
|---|---|
| 新しい部品 UserAdministration（パッケージ `useradmin`）が `user`・`auth` の service の口と Paging を使い、監査には出来事で知らせる | ADR-001、ADR-006、`components.md` |
| 拒否の判定の順は「対象がいない → 自分自身 → 対象が停止中（印の操作だけ）→ 変えるものが無い → 最後の有効な管理者」で、最初の理由1つで応答と監査をそろえる | FR7.5、M7 B、`stories.md` の前提（差3） |
| code と状態は `USER_NOT_FOUND`（404）、`USER_ADMIN_SELF_OPERATION`・`USER_ADMIN_TARGET_SUSPENDED`・`USER_ADMIN_NO_CHANGE`・`USER_ADMIN_LAST_ADMIN`・`USER_ADMIN_BUSY`（いずれも 409）。入力の誤りは既存の `VALIDATION_FAILED`（400） | C3、エラーの code の一覧、Contract Design の Q3 A |
| 操作の成功は本文なしの 204。一覧は 200 で `AdminUserPage`（`items`・`page`・`size`・`total`） | C3、Contract Design の Q2 A |
| 監査の出来事の種類は `USER_ADMIN_GRANTED`・`USER_ADMIN_REVOKED`・`USER_SUSPENDED`・`USER_RESUMED`・`LOGIN_FAILURES_RESET`。成功も失敗も同じ種類で `result` で分ける | C6 |
| 監査に残さないもの: 一覧の閲覧・入力の誤り・氏名と言語の変更（成功・失敗とも） | FR6.4、FR7.3 |
| 監査は確定の後に記録し、監査の書き込みの失敗で操作を失敗させない | ADR-006、`AuditWriteFailureIT` |
| 対象がいないときは、要求の利用者 ID をそのまま監査の対象に残す（`target_user_id` は参照の制約なし） | FR7.6、C6 |
| 有効な管理者は、印を持ち停止していない利用者。ロック中も数える | FR4.1 |
| 最後の管理者の保護は、管理者の行と対象の行を利用者 ID の順にまとめて `PESSIMISTIC_WRITE` で排他し、排他の後に数える。負けた側は業務の誤りで 5xx にしない | ADR-007、C8、AC2.1.6 |
| 排他の待ちの上限は既存の 3000 ミリ秒にそろえる（上限切れの応答は約 3 秒後になり NFR5 の 1 秒を超えるが、起きにくい例外の道として扱う。値を変えるかは NFR 設計で測ってから） | ADR-007（既存の排他にそろえる）、`LoginAttemptStateRepository`・`InvitationRepository` |
| 上限切れは `Busy` で返して `USER_ADMIN_BUSY` にし、判定の順の外で状態は変わらない | C3、C8 |
| 後に動いた側の操作した人がすでに管理者でなくなっていても、同時の重なりの負けた側の理由は「最後の有効な管理者の保護」 | AC2.1.6（リードの決定） |
| 一覧の並びは登録した日時の古い順、同じ日時は利用者 ID の小さい順。1ページ 20 件 | AC1.1.1、FR1.3、C2 |
| ページの番号は文字列で受けて `Paging.parsePage` で検証し、最後のページより後は空の一覧 | C2、C3、差7 |
| 検索はメールアドレスか氏名の部分一致、大文字と小文字を区別しない、`%`・`_`・`\` は文字どおり、空なら絞らない | FR1.4、AC1.1.4 |
| ロックについて応答に含めるのは `locked`・`lockedUntil`・`resettable` の3つだけ。境界は「今 < 解除の予定の時刻」、`resettable` は表の失敗回数 1 以上、行が無ければ偽 | FR1.2、FR1.6、AC1.1.2・AC1.1.3、M2 B |
| 自分自身の失敗回数の戻しと、自分自身の氏名と言語の変更は許す。停止中の利用者への停止の解除・失敗回数の戻しは許す | `stories.md` の前提、AC4.1.7・AC4.1.8 |
| 止めるときはリフレッシュトークンをすべて同じトランザクションで無効にする。重なりの隙は塞がない | ADR-003、C1、M8 B |
| 失敗回数の戻しは 0 と解除の予定の時刻なしにし、行の無い利用者に行を作らない | `stories.md` の前提、C8 |
| 氏名と言語の検証は自分のプリファレンスと同じ規則。同じ値でも成功 | FR6.2、FR6.6、AC5.1.4・AC5.1.5 |
| 認可は既存の AccessControl のまま（未認証 401、管理者でない 403 `ACCESS_DENIED`、停止中の管理者は U1 の入口で 401） | FR8.2、FR3.8、C3 |
| 管理の API の外と氏名と言語の変更の API では、要求の本文の知らない項目を無視し、印・停止・失敗回数を変えない | FR8.3、AC2.1.7・AC5.1.6 |
| 検索の文字は controller の引数の時点で伏せ字の型 `SearchText` で受け、業務処理と UserAccount へもそのまま渡す | C3（レビューの R-01）、C8 |
| 日時は ISO 8601 の UTC（`Z` 付き）で渡す | C3 の共通の決まり |

---

## Q1. 検索の文字の型 `SearchText` の置き場

C8 の `findAdminPage` は `user` のパッケージにあり、引数で `SearchText` を受けます。ADR-001 の依存の向きは `useradmin` → `user` で、`user` は `useradmin` に依存できないため、契約が候補に挙げた `useradmin.domain` には、C8 の形のままでは置けません。`user.domain` には同じ流儀の `RedactedText`（`toString` で値を伏せる record）がすでにあります。
理由: 契約で U3 の機能設計に回され（C3 の漏えい）、置き場で境界テストと検証の置き場が変わるため。

A. `user.domain` に置く（`RedactedText` の隣。検索の対象が利用者の表のため。長さの検証もここに置く）（推奨: C8 の形を変えずに済み、依存の向きを増やさない）
B. `common` の下の新しいパッケージ（例: `common.text`）に置く（後の機能の一覧の検索でも使い回せる。新しいパッケージとしてパッケージごとの下限の対象になる）
C. `useradmin.domain` に置き、C8 の `findAdminPage` の引数は既存の `RedactedText` にする（`useradmin` で包み直して渡す。C8 の形を直す）
X. Other (please specify)

[Answer]: A

## Q2. 要求の文字列から `SearchText` への変換の作り方（申し送り R-06）

`TraceAspect.EXPRESSION` は、`cherry.mastersmith` の下の `web`・`service`・`domain`・`repository` の層のクラスを対象にし、フィルター・record・`@ConfigurationProperties`・`@Configuration`・起動クラス・`common.observability` を除きます。追跡は Spring の Bean の代理で行うため、Bean でない部品は対象になりません。`Converter<String, SearchText>` を Bean にして4つの層に置くと、変換の引数（生の `q`）が TRACE のログに出ます。既存の `dslmanage/web/DslWebConfig.java` は、`web` の層に置いた `@Configuration` の `WebMvcConfigurer` で部品を登録しています（`@Configuration` は対象の外）。なお、変換の誤りなどの例外は `GlobalExceptionHandler` が code と例外のクラスの名前だけをログに出し、値は出しません。
理由: 申し送り R-06 で、置き場と登録の仕方を決めないと AC1.1.6 を満たせないため。

A. `Converter` を Bean にせず、`useradmin.web` の設定（`@Configuration` の `WebMvcConfigurer`、例: `UserAdminWebConfig`）の `addFormatters` で `new` して登録する。変換は包むだけにし、長さなどの検証は controller が受けた後に行う（推奨: レビューの推しと既存の `DslWebConfig` の形に合い、`TraceAspect` の式を変えない）
B. `Converter` を Bean にし、4つの層の外の用途名の下位パッケージ（例: `useradmin.convert`）に置く（Bean のままで式に当たらない。置き場を境界テストで固定する必要がある）
C. 専用の `Converter` を作らず、`SearchText` の文字列を1つ受ける作りに Spring の既定の型の変換を任せる（record は式の対象外。ただし変換の仕組みが暗黙になり、読む人に分かりにくい）
D. `TraceAspect.EXPRESSION` に `Converter` を除く条件を足す（`common.observability` は `packagesJudgedByTotal` の一覧にあり、手を入れると下限を満たして一覧から外す作業が付く）
X. Other (please specify)

[Answer]: A

## Q3. 検索の文字の長さの上限と、前後の空白の扱い

FR1.5 で上限を機能設計に回されています（AC1.1.4 は上限ちょうどを受け付け、AC1.1.5 は上限＋1 文字を `VALIDATION_FAILED` にする）。メールアドレス（`EmailAddress.MAX_LENGTH`）と氏名（`DisplayName.MAX_CODE_POINTS`）の上限はどちらも 254 です。氏名は前後の空白（半角・全角）を除いてから保存しています。長さはパスワード・氏名と同じくコードポイントで数えます。検索の文字の使える文字は制限しません（`%`・`_`・`\` もエスケープして文字どおりに扱うため）。
理由: 上限の値と、上限・空の判定を空白を除く前と後のどちらで行うかで、境界のテストと画面の入力欄の制限（AC1.1.11）が決まるため。

A. 上限 254 コードポイント。前後の空白（半角・全角とも）を除いてから数え、除いた後が空なら絞らない（全件）（推奨: メールアドレス・氏名を丸ごと貼って探せ、氏名の保存と同じ空白の扱いで、貼り付けた値の前後の空白で当たらなくなるのを防ぐ）
B. 上限 254 コードポイント。値は入れたとおりに使い（前後の空白も文字どおりに一致させる）、空の文字列だけを「絞らない」とする
C. 上限 100 コードポイント（表示と入力欄に向く短さ）。空白の扱いは A と同じ
X. Other (please specify)

[Answer]: A

## Q4. ロックの状態の行と users の行を排他する順（申し送り R-07）

今のログインの経路（`LoginService`）は、トランザクションの前に利用者を排他なしで読み、トランザクションの中ではロックの状態の行（またはダミーの行）だけを `PESSIMISTIC_WRITE`・待ちの上限 3000 ミリ秒で排他し、同じトランザクションでリフレッシュトークンを追記します。users の行を排他する既存の経路はありません。`refresh_tokens` は `users` への外部キーを持ちます。H2 で外部キーの確かめが、排他された親の行（users）を待つかは確かめていません。
理由: 失敗回数を戻す操作が2種類の行を取ると、取る順によってはログインと行き詰まりうるため（レビューの R-07）。FR5.4 は「ログインの判定と同じ行の排他で順番をそろえる」としています。

A. 失敗回数を戻す操作は、ロックの状態の行だけを排他する（ログインと同じ1種類の行）。対象の利用者の有無は users の行を排他せずに読む。users の行を排他するのは印を付ける・外す・止める・停止を解くだけにし、どの操作も2種類の行を同時に排他しない（推奨: 取る順の決まりが要らず、ログインとの行き詰まりが起きない。FR5.4 の読みにも合う）
B. users の行（対象の行。Q5 で操作した人の行も）→ ロックの状態の行 の順に排他する（C8 の `lockUserRow` を使う。ログインは users を排他しないので順は逆にならないが、`refresh_tokens` の外部キーの確かめで users の行を待つ場合の行き詰まりを NFR 設計でテストして確かめる必要がある）
C. ロックの状態の行 → users の行 の順に排他する（ログインと同じ行を先に取る）
X. Other (please specify)

[Answer]: A

## Q5. 操作する管理者が今も有効な管理者かの確かめ直し（範囲と位置）

管理者の印は、要求の認可（`AdminAuthorizationManager`）の時点で DB から読まれます。その後、行の排他を待っている間に、ほかの管理者に印を外される・止められることがありえます（ADR-007）。C8 は `lockAdminRowsInIdOrder` の `activeAdminIds` で確かめ直すとし、外れていたら `ACCESS_DENIED` と同じ扱いにするとしていますが、どの操作で、判定の順のどこで確かめ直すかは決まっていません。AC2.1.6・AC2.1.12 は、同時の重なりの負けた側の操作した人がすでに管理者でなくても、理由を「最後の有効な管理者の保護」とします。確かめ直しを判定の最初に置くと、この負けた側が `ACCESS_DENIED` になります。
理由: 範囲と位置で、同時の重なりの結果とテスト（AC2.1.6・AC2.1.12・AC3.1.5）が変わるため。

A. 状態を変える5つの操作（印を付ける・外す・止める・停止を解く・失敗回数を戻す）で確かめ直す。印の操作と止めるは排他の後の `activeAdminIds` で、停止を解く・失敗回数を戻すは操作した人の行を読み直して確かめる（Q4 の答えに合わせ、排他するか読むだけかを決める）。位置は業務の理由の判定をすべて通った後、状態を変える直前。氏名と言語の変更は確かめ直さない（推奨: AC2.1.6・AC2.1.12 の理由を保ち、外された直後の管理者が権限や利用を戻す操作（印を付ける・停止を解く）を通す隙を塞ぐ）
B. ADR-007 の範囲の3つ（印を付ける・外す・止める）だけで確かめ直す。位置は A と同じ。停止を解く・失敗回数を戻す・氏名と言語の変更は、要求の認可の時点の判定のままにする
C. A の範囲で、位置を判定の最初（対象がいないより前）にする（AC2.1.6・AC2.1.12 の負けた側が `ACCESS_DENIED` になり、ストーリーとの差になる）
D. 氏名と言語の変更を含む6つの操作すべてで確かめ直す。位置は A と同じ
X. Other (please specify)

[Answer]: A

## Q6. 確かめ直しで操作した人が外れていたときの応答と監査

既存の 403 は `access.domain` の `AccessProblemTypes.ACCESS_DENIED` で、403 の入口では `AdminAccessDeniedEvent`（種類 `ACCESS_DENIED`、理由 `NOT_ADMIN`、要求のパス）が監査に残ります。`AuditFailureReason` には既存の値 `NOT_ADMIN` があります。ADR-001 は `useradmin` の依存を `user`・`auth` に限っており、`ACCESS_DENIED` の code を使うと `useradmin.web` から `access.domain` への依存が1本増えます（共通の code は機能の側で重ねて定義しない決まり）。画面（U4）は、`/api/admin/` の下の 403 `ACCESS_DENIED` を受けると「この画面を使う権限がありません」に切り替えて、ログインの状態を読み直します。
理由: C8 の「ACCESS_DENIED と同じ扱い」の細部（応答の code・監査の形・依存）が決まっていないため。

A. 応答は 403 `ACCESS_DENIED`（既存の code。`useradmin.web` から `AccessProblemTypes` への依存を境界テストに書く）。監査はその操作の出来事の種類の失敗として、既存の理由 `NOT_ADMIN`・対象は要求の利用者で残す（推奨: 誰が誰に何をしようとして拒否されたかが操作の監査の行に残り、新しい理由が要らない。画面は U4 の共通の扱いで権限の無い表示に移る）
B. 応答は 403 `ACCESS_DENIED`。監査は 403 の入口と同じ「アクセスの拒否」（`AdminAccessDeniedEvent`）で残す（入口の 403 と同じ行になるが、どの操作の誰が対象だったかは残らない。`useradmin` から `access` の出来事への依存が増える）
C. 応答は 403 `ACCESS_DENIED`。監査には残さない
D. 応答は新しい 409 の code（例: `USER_ADMIN_OPERATOR_NOT_ADMIN`）。監査は A と同じ（`access` への依存は増えないが、画面は 403 の共通の扱いに移らず、理由の文言を出すだけになる）
X. Other (please specify)

[Answer]: A

## Q7. 排他の待ちの上限切れ（`USER_ADMIN_BUSY`）の監査（申し送り R-04）

上限切れは業務の理由の判定の前に止まり、状態は変わりません（C3）。`BusinessException` に変えた応答は、`GlobalExceptionHandler` がアプリのログに WARN で code・状態・例外のクラスの名前を出すため、ログには `USER_ADMIN_BUSY` が残ります。監査の受け取り（`AuditEventListener`）は確定の後（`AFTER_COMMIT`）に記録するため、上限切れでトランザクションが巻き戻ると、出来事をトランザクションの外で出さない限り届きません。要件 FR7.2 が監査に残す拒否の理由は業務の理由だけで、上限切れは含みません。AC4.1.11 は、状態が変わらず決めた code で失敗することだけを求めます。
理由: 契約で U3 の機能設計に回され（C6 の not_recorded、Open questions）、レビューの R-04 が「監査に残す理由を決めるか not_recorded に確定する」を求めているため。

A. 監査に残さない（C6 の not_recorded に確定する。アプリのログの WARN の code で気づける）（推奨: 要件 FR7.2 の範囲のままで要件との差を作らず、業務の判定の前に止まって状態も変わらない。1インスタンスの H2 では起きにくい）
B. 監査に残す。その操作の出来事の種類の失敗として、新しい理由 `LOCK_TIMEOUT`（12 文字）・対象は要求の利用者 ID で残す。巻き戻しでも残るよう、出来事はトランザクションの外で出す（FR7.2 との差として記録する）
X. Other (please specify)

[Answer]: A

---

## Consolidated Summary Confirmation

答えのまとめと、U3 の機能設計の計画:

- 設計の要点（案）の 1〜14 のとおりに作る（新しいパッケージ `useradmin`、一覧の並び、ロックの判定の結果、拒否の判定の純粋な関数、行の排他、待ち合わせの口、止める、失敗回数を戻す、氏名と言語の変更、監査の出来事、漏えい、カバレッジ）。
- Q1 A: `SearchText` は `user.domain` の `RedactedText` の隣に置く。長さの検証もここに置く。C8 の形は変えない。
- Q2 A（R-06）: 文字列から `SearchText` への変換は Bean にしない。`useradmin.web` の `@Configuration`（`WebMvcConfigurer`）の `addFormatters` で `new` して登録する（`DslWebConfig` と同じ形）。変換は包むだけにし、検証は controller が受けた後に行う。
- Q3 A: 検索の上限は 254 コードポイント。前後の空白（半角・全角）を除いてから数え、除いた後が空なら絞らない。
- Q4 A（R-07）: 失敗回数を戻す操作は、ロックの状態の行だけを排他する。users の行を排他するのは、印の操作と止める・停止を解くだけ。どの操作も2種類の行を同時に排他しないため、排他の順の決まりは要らない。
- Q5 A: 状態を変える5つの操作で、操作する管理者を確かめ直す。位置は、業務の理由の判定をすべて通った後、状態を変える直前。氏名と言語の変更は確かめ直さない。
- Q6 A: 外れていたら 403 `ACCESS_DENIED` を返す。監査には、その操作の失敗として既存の理由 `NOT_ADMIN` で残す。
  - `useradmin.web` から `access.domain`（`AccessProblemTypes`）への依存が1本増える。
  - ADR-001 は `useradmin` の依存を `user`・`auth` としていたため、`UserAdminBoundaryArchitectureTest` にこの依存を書き、上流との差として functional-spec.md に記録する。
- Q7 A（R-04）: `USER_ADMIN_BUSY` は監査に残さない（C6 の not_recorded に確定）。アプリのログの WARN の code で気づける。
- U1 の機能設計の差 D3 により、`isSuspended`・`setSuspended` を存在しない利用者 ID で呼ぶと想定外の誤りになる。U3 は行の排他（C8）で対象の有無を確かめてから呼ぶ。
- U3 の設計は1つの単位として通し、B3（一覧と氏名・言語）と B4（5つの操作）への分け方は、コード生成の計画で決める（Delivery Planning の Q4 A）。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
