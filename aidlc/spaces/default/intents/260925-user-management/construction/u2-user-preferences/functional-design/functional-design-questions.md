# Functional Design の質問 — u2-user-preferences

単位 U2（`aidlc/spaces/default/intents/260925-user-management/inception/units-generation/unit-of-work.md`、種類 service）の機能設計のための質問です。受け持つストーリーは US4.1・US5.1、関わるストーリーは US3.2（利用者の作成）、共通の決まりは CR1・CR3・CR4・CR5（`aidlc/spaces/default/intents/260925-user-management/inception/user-stories/stories.md`）。契約は C2・C3・C4・C8（`aidlc/spaces/default/intents/260925-user-management/inception/contract-design/contract-summary.md`）。既存のコードは `backend/src/main/java/cherry/mastersmith/user/`・`auth/`・`audit/` と `backend/src/main/resources/db/migration/`（V1〜V6）を確かめました。

## 設計の要点（案）

上流の決定とコードの確認から導ける、この単位の機能設計の見通しです。質問の答えで決まる点は（Qn）と書きます。

1. **エンティティ User を広げる**: 既存の User（`users` の表: userId・email・passwordHash・admin・createdAt）に、displayName（氏名）・language（ja・en）・theme（light・dark・system）・fontSize（sm・md・lg）の4つを足す。エンティティにするのはこの表だけで、出来事（パスワードの変更の知らせ）とトークンは既存のものを使う。
2. **スキーマの変更（Flyway V7）**: 4つの列を足し、既存の利用者には氏名＝メールアドレス・言語 ja・テーマ system・文字の大きさ md を入れる（NFR10、要件の前提 A3）。言語・テーマ・文字の大きさの列は既定の値を持たせ、1つ前の版のアプリの追記の形のままでも入るようにする。氏名の列は既存の行に値を入れてから必須にする。1つ前の版のアプリが利用者を作る経路は初期管理者の自動作成だけで、利用者がすでにいれば動かないため、後方互換は保てる見込み（細部は NFR 設計・基盤の設計で確かめる）。
3. **監査の表の変更（同じく V7）**: `audit_events` に `target_user_id`・`target_invitation_id`（どちらも空を許す）を足す。この2列の一覧の正は U2 が持つ（C8）。出来事の種類 `PASSWORD_CHANGED` と失敗の理由 `CURRENT_PASSWORD_MISMATCH`（25 文字）を足す。既存の列の長さ（`event_type`・`failure_reason` とも 32）に収まる。DSL の受け付けなかった理由の列 `rejection_kind` は使わない（C8 の未解決の点をコードの確認で解く）。U3 の出来事の種類と失敗の理由も 32 文字に収まることを確かめ済み（最長は `REGISTRATION_COMPLETED` の 22 文字・`INVITATION_ALREADY_USED` の 23 文字）。
4. **氏名の決まり**: 空にできない（FR5.2）。最大長（Q1）・使える文字（Q2）・前後の空白と空白だけの扱い（Q3）は1つの純粋な関数（`EmailAddress`・`PasswordPolicy` と同じ形）にまとめ、プリファレンスの保存（C4）と利用者の作成（C2、U3 の登録の完了）の両方で同じ決まりを使う。長さはパスワード・監査と同じくコードポイントで数える。Unicode の正規化（NFC など）はしない。性質ベースのテスト（jqwik）の対象にする（`team.md`）。
5. **利用者の作成を広げる（C2）**: 作成の操作が氏名・言語・テーマ・文字の大きさを受け取る。登録済みのメールアドレスは結果の型の `EmailAlreadyUsed` で返し、例外にしない（C2、契約の共通の決まり）。トランザクションは呼び出し元（U3 の招待の使用済みの記録）に参加し、既存の `UserCreatedEvent`（ロックの状態の用意）を保つ。初期管理者の自動作成も同じ作成の操作を通し、氏名＝メールアドレス・言語 ja・テーマ system・文字の大きさ md を入れる（要件の前提 A3 と同じ初期値）。
6. **プリファレンスの読み書き（C4）**: `GET /api/me/preferences` は要求ごとに内部DB から読んだ4つを返す。`PUT /api/me/preferences` は4つをまとめて検証し、どれか1つでも誤りなら何も変えずに `VALIDATION_FAILED`（400）で拒否する（AC4.1.7、一部だけの保存をしない）。通れば4つを1つのトランザクションで置き換える。同時の保存は後に確定したものが勝つ（利用者本人だけが変える値のため、版による排他は持たない）。監査しない（FR9.2、AC4.1.13）。
7. **パスワードの変更（C4、ADR-004）**: `POST /api/me/password`。順序は (1) 入力の検証（新しいパスワードの規則は既存の `PasswordPolicy`、新しいパスワードと確かめの2回の入力の一致をサーバーでも確かめる。誤りは `VALIDATION_FAILED`）→ (2) 今のパスワードの照合（誤りは `PASSWORD_CURRENT_MISMATCH`（400）、401 にしない、AC5.1.2）→ (3) 新しいハッシュの保存。入力の誤りでは照合しないため、(1) で拒否した要求は監査に残らない。監査に残すのは (2) の誤り（`FAILURE`・`CURRENT_PASSWORD_MISMATCH`）と (3) の成功（`SUCCESS`）で、どちらも `actor_user_id` と `target_user_id` に本人を入れる。リフレッシュトークンには触れず、アクセストークンも有効期限まで使える（FR6.3、AC5.1.4・AC5.1.5）。今のパスワードの誤りが続いたときの制限は NFR 要件の段で決める。
8. **ログインと更新の応答（C3）**: 利用者の要約（今は userId・email・admin）に4つを足し、`TokenResponse.user` に displayName・language・theme・fontSize を載せる。ログイン・更新・ログアウト・トークンの認証の決まりは変えない。
9. **エラーの説明文の言語**: サーバーは今までどおり要求の `Accept-Language` で決め、利用者の言語は画面が `Accept-Language` に入れて送る（ADR-005）。サーバーで利用者の言語から言語を決め直す仕組みは足さない。
10. **エラーの code**: `PASSWORD_CURRENT_MISMATCH`（400）を新しい `UserProblemTypes`（user の機能の code の一覧）に置く。ほかは既存の `VALIDATION_FAILED`（400）・`AUTHENTICATION_REQUIRED`（401）を使う。
11. **認可**: `/api/me/` の下は `/api/` の既定のログイン必須に乗る（未認証は 401、ログインした利用者は管理者でなくても成功、CR4）。`/api/auth/` の下には置かない（K-6）。画面入出力の層は新しいパッケージ `user.web` に置く。
12. **秘密と個人情報（CR5）**: 今のパスワード・新しいパスワードは既存の `Password` の型（文字列化で伏せる）で扱い、ログ・トレースの属性・エラー応答・監査ログに入れない。メールアドレスはアプリのログとエラー応答に入れない。
13. **カバレッジの一覧から戻す**: 手を入れる既存のパッケージ（`user.domain`・`user.repository`・`user.service`・`audit.domain`・`audit.repository`・`audit.service`、応答を広げる `auth.service`・`auth.web`）はテストを足して下限（行 80%・分岐 70%）を満たし、`packagesJudgedByTotal` から外す（`team.md`）。新しい `user.web` は自動でパッケージごとの下限の対象になる。

## 決まっていること（質問にしない）

| 決まっていること | 出典 |
|---|---|
| 氏名とプリファレンスは利用者の表の列として UserAccount が持つ | ADR-003、Domain Design の Q3 A |
| パスワードの変更は UserAccount に置き、照合・規則・保存を1か所にする | ADR-004、Domain Design の Q4 A |
| 監査は既存の AuditLog に出来事の種類と対象の2列を足す。結果は既存の `result`（`SUCCESS`・`FAILURE`）と `failure_reason` を使う | ADR-008、C8 |
| 言語は ja・en、テーマは light・dark・system、文字の大きさは sm・md・lg に限る。氏名は空にできない | FR5.2 |
| 既存の利用者の初期値は、氏名＝メールアドレス・言語 ja・テーマ system・文字の大きさ md | 要件の前提 A3、NFR10、AC4.1.10 |
| スキーマの変更は Flyway の V7 以降、前進のみ・1つ前の版のアプリが動く後方互換 | NFR10、`team.md` の Deployment |
| API は `GET`・`PUT /api/me/preferences`（4つをまとめて置き換える）と `POST /api/me/password`（今のパスワード・新しいパスワード・確かめの3項目） | Contract Design の Q3 A、C4 |
| 2回の入力の一致はサーバーでも確かめ、不一致は `VALIDATION_FAILED`（400） | C4 の `PasswordChangeRequest` と応答 |
| 今のパスワードの誤りは `PASSWORD_CURRENT_MISMATCH`（400）で、401 にしない。拒否されてもログインしたまま | C4、エラーの code の一覧、AC5.1.2・AC5.1.6 |
| パスワードの規則は既存の `PasswordPolicy`（12 文字以上（コードポイント）・UTF-8 で 72 バイト以内） | FR4.4・FR6.2、AC5.1.3 |
| パスワードを変えても、リフレッシュトークン（どの端末も）は無効にせず、アクセストークンも有効期限まで使える | FR6.3、Requirements の Q10、`project.md` の DECIDED |
| 監査に残すのはパスワードの変更の成功と今のパスワードの誤りだけ。氏名・言語・テーマ・文字の大きさの変更は残さない | FR9.1・FR9.2、CR3 |
| 出来事の種類は `PASSWORD_CHANGED`（成功も失敗もこの種類で、`result` で分ける）、失敗の理由は `CURRENT_PASSWORD_MISMATCH` | C8 |
| 監査は確定の後に記録し、書き込みの失敗で元の操作を失敗させない（既存の決まり） | ADR-008、`AuditEventListener` |
| 利用者の作成は結果の型（`Created`・`EmailAlreadyUsed`）で返し、呼び出し元のトランザクションに参加し、`UserCreatedEvent` を保つ | C2、FR4.5 |
| ログインと更新の応答の利用者の情報に displayName・language・theme・fontSize を足す | C3 |
| エラーの説明文の言語は `Accept-Language` で決め、ログインの後は画面が利用者の言語を入れて送る | ADR-005、FR7.2、CR1.1 |
| 未認証は 401、ログインした利用者は成功（管理者でなくてもよい）をサーバー側のテストで確かめる | CR4、NFR4 |
| 登録の後も、プリファレンスの画面で本人がいつでも氏名を変えられる | Requirements の F2 A |
| 今のパスワードの誤りが続いたときの制限は NFR 要件の段で決める | 要件の未解決の点、`stories.md` の「後の段に回す点」 |
| テーマ system の開いている間の追従は、画面の単位（U4）の論点 | 単位の分け方（U4 の責務）、C9 の未解決の点 |

---

## Q1. 氏名の最大長

氏名は画面のユーザーメニュー（M7）・招待の一覧の「招待した管理者」（M9）に出ます。初期値はメールアドレスで、メールアドレスは 254 文字まで入ります（`EmailAddress.MAX_LENGTH`）。上限をこれより短くすると、長いメールアドレスの利用者は初期値のままでは保存できなくなります。長さはコードポイントで数えます（パスワード・監査と同じ）。
理由: 要件の未解決の点で、境界のテスト（AC3.2.9・AC4.1.7 の「上限ちょうどは通る・1文字超えると拒否」）の値が決まらないため。

A. 254 文字（メールアドレスの上限と同じ。初期値のメールアドレスがいつでもそのまま収まる）
B. 100 文字（表示に向く長さ。初期値のメールアドレスが 100 文字を超えるときは、初期値を先頭の 100 文字に切り詰める）
C. 50 文字（表示にさらに向く長さ。初期値の扱いは B と同じく切り詰める）
X. Other (please specify)

[Answer]: A

## Q2. 氏名に使える文字

氏名は画面に出すほか、後続の Intent でメールなどにも使われえます。画面は React のため HTML としては出ませんが、改行・タブなどの制御文字や、見えない書式の文字（ゼロ幅の文字、表示の向きを入れ替える U+202E など）は、表示の崩れや、別の人に見せかける表示に使われえます。
理由: 要件の未解決の点で、入力の検証の決まり（拒否する文字）が決まらないため。

A. 制御文字（改行・タブなど、Unicode の Cc）を含む氏名を拒否し、ほかの文字は許す
B. 制御文字（Cc）に加えて、見えない書式の文字（Unicode の Cf。ゼロ幅の文字・表示の向きの上書きなど）も拒否し、ほかの文字は許す
C. 文字の種類は制限しない（空と長さだけを確かめる）
D. 許す文字を決める（漢字・かな・英数字・空白と、一部の記号だけ）
X. Other (please specify)

[Answer]: B

## Q3. 前後の空白と、空白だけの氏名

氏名は空にできません（FR5.2）。空白だけの氏名（半角・全角の空白だけ）をどう扱うかと、前後の空白を保存するかは決まっていません。なお、メールアドレスの正規化（`EmailAddress.normalize`）は半角の空白（U+0020 以下）だけを除き、全角の空白（U+3000）は除きません。
理由: `stories.md` の「後の段に回す点」でこの段の持ち主とされ、AC3.2.9・AC4.1.7 の判定が決まらないため。

A. 前後の空白（半角・全角とも、Unicode の空白の文字）を除いてから保存し、除いた後が空なら拒否する。長さの上限は除いた後の値で数える
B. 値は入れたとおりに保存し、空白の文字だけでできている氏名（半角・全角とも）は拒否する
C. 空の文字列だけを拒否し、空白だけの氏名も許す
X. Other (please specify)

[Answer]: A

## Q4. 今と同じパスワードへの変更

新しいパスワードが今のパスワードと同じときの扱いは、要件（FR6.2 は「FR4.4 と同じ規則で検証する」だけ）でも受け入れ基準でも決まっていません。拒否するには、今のパスワードの照合に加えて、新しいパスワードを今のハッシュと照合する処理が1回増えます。
理由: パスワードの変更の決まりとして、同じ値を通すか拒否するかで、テストと応答（`code`）が変わるため。

A. 許す（要件のとおり、規則に合えば同じパスワードでも変更として扱い、成功の監査を残す）
B. 拒否する（入力の誤りの `VALIDATION_FAILED` として、新しいパスワードの項目に結び付けて返す。パスワードは変わらず、監査は残さない）
X. Other (please specify)

[Answer]: A

---

## Consolidated Summary Confirmation

答えのまとめ:

- 設計の要点（案）は冒頭の「設計の要点（案）」の 13 件のとおり（エンティティ、V7 の移行、監査の2列、氏名の決まり、作成の広げ、プリファレンスの読み書き、パスワードの変更の順序、応答の広げ、エラーの言語、code、認可、秘密、カバレッジの一覧から戻す作業）
- Q1 A: 氏名の最大長は 254 文字（コードポイント）。初期値のメールアドレスがいつでもそのまま収まる
- Q2 B: 制御文字（Cc）と見えない書式の文字（Cf。ゼロ幅・表示の向きの上書きなど）を含む氏名を拒否し、ほかの文字は許す
- Q3 A: 前後の空白（半角・全角とも）を除いてから保存し、除いた後が空なら拒否する。長さは除いた後の値で数える
- Q4 A: 今と同じパスワードへの変更も、規則に合えば変更として扱い、成功の監査を残す

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
