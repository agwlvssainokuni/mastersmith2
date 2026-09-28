# Code Generation Plan — U7 プリファレンスとパスワードの変更の画面（u7-preferences-ui）

U7 のコード生成の計画を示す。作るものは次のとおり。

- ログインした利用者が自分の氏名・言語・テーマ・文字の大きさを変えて保存する画面 S4（`/me/preferences`）と、自分のパスワードを変える画面 S5（`/me/password`）。新しい置き場 `frontend/src/features/preferences/`、どちらも `layout: 'SHELL'`・`access: 'LOGGED_IN'`、遅延読み込み。
- 骨組みの変更（機能設計の Q1 A、9節）: ユーザーメニューの項目に `path` を足し（`frontend/src/app/registry/types.ts`）、登録の検査を2つ足し（`validateRegistrations.ts`）、`ShellLayout.tsx` で make-you-chic-ui の Dropdown の `href` と `onClick` から読み込み直しなしで移る。
- 実際のブラウザのアクセシビリティの検査と画面の時間の測り（`frontend/e2e/080-preferences-accessibility.e2e.ts`）と、その共用の手伝い（招待から利用者を作る関数など）。

Bolt は B5（U5・U6・U7 の画面、`inception/delivery-planning/bolt-plan.md`）で、U7 はその最後の単位。B1（U1）・B2（U2）・U8・B3（U3）・B4（U4、make-you-chic-ui の固定先は `735ef04`）・B5 の U5・U6 は `develop` に統合済みで、先頭は `08c9183`。U7 は ui の単位で、サーバーのコード・内部DB・Flyway・`vendor/` の中身を変えない。代表の流れの E2E は足さない（この Intent の1本は U6 の 090）。

この計画の中のパスは、特に断らない限りリポジトリのルートからの相対パスで、記録のディレクトリは `aidlc/spaces/default/intents/260925-user-management/`（以下「記録」）とする。既存のコードの説明文にある「U1〜U5」は前の Intent の単位番号のことがあり、この計画の U7 は u7-preferences-ui を指す。

## 1. 入力にした設計

| 文書（記録の下） | 使うところ |
|---|---|
| `construction/u7-preferences-ui/functional-design/functional-spec.md`・`frontend-components.md`・`functional-design-questions.md`・`traceability.json` | 決まり D1〜D14、状態の移り変わり（4節）、画面の流れ W1〜W13、応答ごとの動き（6節）、文言（7節）、テストの方針（8節）、骨組みへの変更（9節）、上流との差 (a)〜(i)（10節）、承認の場の直し（12節の1〜7）、部品の階層（部品 1節）、置き場ごとのモジュール（部品 2節）、2つのフック（部品 3.1・3.2）、U4 の口の使い方（部品 3.3）、props と state（部品 4節）、API との受け渡し（部品 5節）、機能の登録（部品 6節）、テストで確かめる内容（部品 7節）、既存への影響（部品 8節）、答え Q1 A・Q2 A・Q3 A、網羅の OK 21 件・Deferred 10 件・N/A 3 件 |
| `construction/u7-preferences-ui/nfr-requirements/` の全文書と `nfr-requirements-questions.md` | NFR2.1、NFR6.1〜NFR6.6、NFR7.1〜NFR7.5、NFR8.1・NFR8.2、NFR9.1〜NFR9.10、答え Q1 A（開く 2 秒・保存 1.5 秒・パスワードの変更 2.5 秒、5回測って記録し関門にしない） |
| `construction/u7-preferences-ui/nfr-design/` の全文書と `nfr-design-questions.md` | 部品と失敗の範囲（`logical-components.md` 1〜4節）、検査の置き場・組・ログインと組の当て方・検査する状態・記録（同 5節）、テストと依存の順（同 7節）、読み込みの番号・送信中の印（`useRef`）・時間切れを置かないこと（`performance-design.md` 2・3節）、測りの置き場と利用者の用意と手順と記録（同 4節）、初回の JavaScript（同 5節）、氏名とパスワードの持ち方（`security-design.md` 1・2節）、応答の値・`fieldErrors` の読み取り・理由の対応の表（同 3節）、今のパスワードの誤りと 401（同 4節）、骨組みの変更と外の URL（同 5節）、検査と測りの安全（同 6節）、依存（同 7節）、答え Q1 A（測りは U7 の検査のファイルの中）・Q2 A（ログインの後の組は `GET /api/me/preferences` の答えの差し替えで D2 の本物の道を通して当てる）・Q3 A（最初の状態と画面の確かめの誤りの状態の2つ） |
| `construction/u7-preferences-ui/infrastructure-design/` の全文書と `infrastructure-design-questions.md` | 実行の形・配信とキャッシュ・CSP・保存を持たないこと（`infrastructure-specification.md`）、`./gradlew verify` の段と関門・`./gradlew e2eTest` の置き場と中身・測りの利用者と Mailpit・実行の時点・資格情報と結果のファイル・統合・配備・戻し（`cicd-pipeline.md`）、SLI と `Unverified` の引き継ぎ（`monitoring-design.md`）。質問なし、Looks correct |
| `inception/contract-design/contract-summary.md` の C4・C9 と共通の決まり | `GET`・`PUT /api/me/preferences`（`Preferences` の4つ）、`POST /api/me/password`（204、400 `VALIDATION_FAILED`・`PASSWORD_CURRENT_MISMATCH`）、表示の設定の口。C4 の `fieldErrors` の追加は README の「利用者のプリファレンスとパスワードの変更（U2）」の「契約との差」を正とする |
| `inception/refined-mockups/`（`mockups.md` の S4・S5、`interaction-spec.md` の 1節・7〜9節、`accessibility-checklist.md`、`design-system-mapping.md`） | 画面の配置と部品の動き。承認済みの機能設計が上書きした点（10節の (b)、案内の `aria-describedby` の置き方）は機能設計を正とする |
| `inception/units-generation/unit-of-work.md`・`unit-of-work-story-map.md` | U7 の責務と境界（API と保存は U2、表示の設定を当てる仕組みは U4）、US4.1・US5.1 の画面の受け入れ基準、CR1・CR6 の受け持ち |
| `inception/requirements-analysis/requirements.md`・`inception/user-stories/stories.md` | FR5.1〜FR5.5・FR6.1〜FR6.3・FR10.2・NFR7・NFR8、US4.1（AC4.1.1〜AC4.1.13）・US5.1（AC5.1.1〜AC5.1.9）、共通の決まり CR1・CR6 |
| `inception/delivery-planning/bolt-plan.md` の B5 | 完了の条件（3つの画面が画面イメージのとおり動き画面部品ごとにアクセシビリティの検査が通る、E2E の代表の流れ1本、ja・en の文言） |
| B1〜B4・U8・U5・U6 の `code-summary.md`（`construction/u1-mail/`・`u2-user-preferences/`・`u3-invitation/`・`u4-display-foundation/`・`u8-instance-appearance/`・`u5-invitation-ui/`・`u6-registration-ui/` の `code-generation/`） | U2 の API の実際の形（`fieldErrors` と `reason` の一覧、`MALFORMED_REQUEST`、C4 の差）、U4 の口（`useDisplaySettings` の値と4つの操作、`LANGUAGE_NAMES`、`renderWithProviders`・`fakeProvider`・`resetDisplayTestState`、050 の手伝い）、U5 の引き継ぎ（`loginPreferences.ts` の当て方、`adminLogin.ts`・`invitationSeed.ts`・`axe.ts` の `splitKnownViolations` の第3引数、060 が招待 21 件とログインの監査を残す、測りの飛ばし方）、U6 の引き継ぎ（`shared/validation` の関数と誤りの種類、検査のファイルの番号 080、`support/mailpit.ts`・`support/registrationFixtures.ts`、API で登録を完了する関数は U7 の計画で足す、Mailpit の片付けの書き方）、U6 の実測（フロントエンド 81 ファイル 625 件、行 97.54%・分岐 93.06%、初回の JavaScript 121.9 KB、`dist/assets/` の JavaScript 10 ファイル 472,521 バイト、WAR 101,587,150 バイト、verify 6分5秒、e2eTest 69 件 1分50秒） |
| 既存のコード | `frontend/src/app/`（`registry/types.ts`・`validateRegistrations.ts`・`validateRegistrations.test.ts`・`registrationModules.ts`、`layout/ShellLayout.tsx`・`ShellLayout.test.tsx`、`navigation/navigationItems.ts`・`navigationItems.test.ts`、`routing/decideRoute.ts`、`display-settings/`（`DisplaySettingsProvider.tsx` の `useDisplaySettings`、`displaySettingsTypes.ts` の型と `LANGUAGE_NAMES`・`THEME_CHOICES`・`FONT_SIZES`）、`i18n/I18nProvider.tsx` の `useMessages`、`testing/renderWithProviders.tsx`）、`frontend/src/shared/api-client/`（`apiRequest`・`ApiError` の `kind`・`status`・`code`・`problem`、401 の更新と送り直し）、`frontend/src/shared/validation/`（U6）、`frontend/src/features/auth/registration.ts`（ログアウトの項目 `auth-logout`、`action` だけ）、`frontend/src/features/invitation/testing/renderInvitation.tsx`（ToastProvider で包む形）、`frontend/e2e/010〜090` と `frontend/e2e/support/`、`frontend/playwright.config.ts`・`playwright-secret-check-reporter.ts`・`vitest.config.ts`・`package.json`、`vendor/make-you-chic-ui` の `735ef04` の Dropdown（`MenuItem` の `href`・`onClick(event)`）・RadioGroup・Button・FormField・TextInput・Alert・Toast、`backend/src/main/java/cherry/mastersmith/user/web/`（`MeController`・`PreferencesRequest`・`PreferencesResponse`・`PasswordChangeRequest`）、`build.gradle.kts` の `e2eTest`、`README.md`（「ビルドした WAR での画面の確認（E2E）」「手元でメールを見る」「利用者のプリファレンスとパスワードの変更（U2）」「画面の表示の設定（U4）」「後の単位（U2・U3・U4）が使う差し込み口」） |

## 2. 承認の場の決定と、この計画での読み方

### 2.1 承認の場の決定（監査ログから洗い出したもの）

`project.md` の学び（計画の前に承認の場の決定を洗い出す）に従い、監査ログ（`audit/sakura-local-4e42a93f87ce.md`）の Functional Design・NFR Requirements・NFR Design・Infrastructure Design の `DECISION_RECORDED`・`GATE_APPROVED`・`GATE_REJECTED` と、レビューの記録（`.aidlc-reviews/*/units/u7-preferences-ui/*/1.json`）の指摘、U5・U6 のコード生成の計画と `code-summary.md` の B5 の決定を読んだ。U7 と B5 に関わるものと、この計画での扱いは次のとおり。

| 段 | 決定・指摘 | この計画での扱い | 手順 |
|---|---|---|---|
| 機能設計（Request Changes、2026-09-27） | make-you-chic-ui の新しい部品（RadioGroup の `legend`・`lang`、Dropdown の `href`、Button の `aria-disabled` の `loading`）に置き換える | 機能設計の 12節の3〜5のとおり作る。自前のラジオの部品は作らない。計画を書く時点で、固定先が `735ef04` で、Dropdown の `MenuItem` が `href`（渡すと `<a href>`）と `onClick(event)`（`event.preventDefault()` で移動を引き受けられる）を持つこと、RadioGroup が `legend`（渡すと `fieldset`・`legend`）と選択肢の `lang` を持つことを読み取りで確かめた。Step 2 で記録する | Step 2・9・11 |
| 機能設計（承認の場、G2〜G5） | RadioGroup が `aria-describedby` を外から受けない点は受け入れて回避の作り（案内と選択の誤りは `legend` の中、テーマと文字の大きさの案内は結び付けない文字） | そのとおりに作る（U6 と同じ置き方）。make-you-chic-ui への追加の相談はこの単位では行わない | Step 11 |
| 機能設計（承認の場、G6） | 契約への反映（C4 の `fieldErrors` など）は遅くともコード生成の計画で確かめる | U2 が README の「契約との差」に `fieldErrors: [{ field, reason }]` と `reason` の6つを記録済みで、`MeController` の実装と一致することを読み取りで確かめた（Step 2 で記録）。契約の文書は書き換えない | Step 2・5 |
| 機能設計のレビュー（U7 R-01・R-02、承認の場で直し済み） | 網羅の来歴、U6 の共用の関数への依存の明記 | 直し済み。依存の順は下の NFR 設計の R-01 の行 | — |
| NFR 要件（承認の場、Minor 12 件の拾い上げの U7 分、R-01） | U7 の画面の時間の測る場所を決める | NFR 設計の Q1 A で「U7 の検査のファイルの中」に決まった。番号は U5 の計画の決定 2 で `080-preferences-accessibility.e2e.ts`。この計画でそれに従い、R-01 を閉じる | Step 15 |
| NFR 要件のレビュー（U7 R-02） | U6 の側でも「U7 より先に共用の確かめの関数を作る」が同じ内容で記録されているか突き合わせる | U6 の計画（2.1 の U7 R-01 の行・Step 5）と U6 の `code-summary.md` の 3.3・8節に「U6 が先に作り、名前と union を U7 に渡す」が記録され、`frontend/src/shared/validation/` が `develop`（`08c9183`）にあることを確かめた | Step 2 |
| NFR 設計（承認の場、U7 R-01） | U6 との突き合わせの手順を B5 の最初に決める | B5 の単位の順は U5 → U6 → U7（U5 の計画の決定 2）で、U6 の計画が先に確定し、U6 の生成の後に突き合わせを行った（U6 の `code-summary.md` の 3.3）。最終の突き合わせの記録は U7 の計画に残す（U6 の計画の決め方）。計画を書く時点で、`shared/validation` の実物の名前と union（`validateDisplayName` → `required`・`tooLong`・`invalidCharacter`、`validateNewPassword` → `required`・`tooShort`・`tooLong`、`validatePasswordConfirmation` → `required`・`mismatch`）が `security-design.md` の 3.3 の「寄せる理由」と一致することを読み取りで確かめた。今のパスワードの空は U7 の確かめ（共用の関数に無い）で、これも 3.3 の表のとおり。Step 2 で記録し、`errorMessages.ts` を作った後にもう一度確かめる | Step 2・5 |
| NFR 設計（承認の場、U6 R-02・U7 R-02） | 検査のファイルの番号は B5 でそろえる | U5 の計画の決定 2 のとおり `080-preferences-accessibility.e2e.ts`（070 と 090 の間。090 は最後のまま） | Step 15 |
| NFR 設計（承認の場、U5 R-02） | B5 で飛ばす判定の前提を確かめる | 080 の測りの飛ばす道（招待を使えない・Mailpit に届かない）について、E2E の WAR の `webServer.env` に SMTP とベース URL が渡り `invitationEnabled` が真になること、`e2eTest` が Mailpit の前提を確かめることを読み取りと実行の記録で確かめる（下の基盤の設計の N10 と同じ） | Step 2・16 |
| NFR 設計（上流との差 A9） | 閲覧の履歴の確かめは B5 | U7 は招待のリンクをブラウザで開かない（基盤の設計の C-D2）。確かめは U6 が済ませた（U6 の `code-summary.md` の 6.2）。U7 は扱わない | — |
| 基盤の設計（承認の場、U7 R-01 Accepted risk） | Mailpit を片付けるコマンドの書き方が U6 と違う。B5 で README の文言をそろえる | U5 の計画の決定 5 のとおり、README の「手元でメールを見る」の2行（`docker compose stop mailpit`・`docker compose rm -f mailpit`）にそろえる。基盤の設計の `docker compose --profile mail stop mailpit`・`docker compose --profile mail rm -f mailpit` は使わない（8節の差）。README の E2E の節の既存の1か所の書き方に従うため、U7 は片付けの文を新しく足さない | Step 19 |
| 基盤の設計（承認の場、D7） | U5・U6・U7 の E2E は Mailpit のメールを消さず、開発者が止めて消す | 080 の測りは Mailpit の API を `GET` で読むだけ（U6 の `support/mailpit.ts`）。書き込まず、消さない | Step 15 |
| 基盤の設計（承認の場、N2） | 050 だけでも Mailpit が要る（B4・B5） | 080 だけを流すときも Mailpit の起動が要ることを `unit-test-instructions.md` と README に書く | Step 16・19 |
| 基盤の設計（承認の場、N9） | json の結果に操作の題が入るか（B5） | 080 の実行の後に `frontend/test-results/e2e-results.json` を文字列で検索し、秘密と個人に関する値が入らないことと、`test.step` の題が `steps` に入るかを記録する | Step 16 |
| 基盤の設計（承認の場、N10） | U7 の飛ばす道は念のための道（B5） | 飛ばす道は残し、通常の実行では通らないことを Step 16 の結果で記録する（飛ばされたら Step 16 で止めて諮る、9節の決定 5 の (e)） | Step 15・16 |
| U5 のコード生成（Step 16 の決定 A と `code-summary.md` の7節） | ログインの後の画面の組は `support/loginPreferences.ts`（ログインと復元の応答の `user.theme`・`user.fontSize` の書き換え）で当てる。U7 の NFR 設計 5.3 の `GET /api/me/preferences` の差し替えとの関係は U7 の計画で決める | 9節の決定 1（依頼者の決定 A）のとおり、NFR 設計の Q2 A のまま `GET /api/me/preferences`（と `/api/appearance`）の差し替えだけで当て、`loginPreferences.ts` は使わない。README の 060 の節の引き継ぎの文を直す。`loginPreferences.ts` だけでは、プリファレンスの画面を開いたときに D2 が本物の `GET` の値（初期管理者の `system`・`md`）でそろえ直し、組が外れるため | Step 15、9節 |
| U5 のコード生成（決定 4 の形） | 既知の違反は、green・orange の組の `color-contrast` の primary の Button だけを、画面の状態ごとの名前の一覧で扱う | `support/axe.ts` に `PREFERENCES_KNOWN_VIOLATIONS` と型 `PreferencesAxeState` を足す。当たる名前は Step 16 の最初の実行で確かめる。050〜070 の一覧と判定は変えない | Step 15・16 |
| U5 のコード生成（決定 1） | 単位ごとに短命のブランチを作り、単位ごとに squash で統合する | 3節のとおり | Step 1・3節 |
| U6 のコード生成（決定 4、`code-summary.md` の8節） | API で登録を完了する関数（リンクからトークンを取り出して `POST /api/registration/complete` を呼ぶ）は U7 の計画で足す | 9節の決定 3 で、新しい共用の手伝い `frontend/e2e/support/registeredUser.ts` に置く。招待は U5 の `invitationSeed.ts` の `requestAdminAccessToken`、リンクの取り出しは U6 の `mailpit.ts` の `findInvitationLink` を使う | Step 15 |
| U6 のコード生成（`code-summary.md` の 2節の #1・#2） | 副作用の中で同期に状態を変える形はリンタ（`react-hooks/set-state-in-effect`）で止まる | U7 のフックでも同じ決まりが当たる。部品 3.1 の「部品が付いたときに読み込みを始める」は、副作用の中で要求を送り、答えの後に状態を移す形にする（U6 と同じ）。動きは設計のとおり | Step 13 |

### 2.2 この計画での読み方

- **設計の文書どおりに作るもの**: 部品の階層（部品 1節）、置き場ごとのモジュール（部品 2節）、2つのフックの状態・操作・副作用（部品 3.1・3.2）、U4 の口の使い方（部品 3.3）、props と state（部品 4節）、API との受け渡し（部品 5節）、機能の登録（部品 6節）、画面の流れと応答ごとの動き（`functional-spec.md` の 4〜6節）、文言（同 7節の鍵と文）、骨組みの変更（同 9節）、`fieldErrors` の読み取りと理由の対応の表（`security-design.md` 3.2・3.3）、送信中の印（`performance-design.md` 3節）、検査の組・当て方・状態（`logical-components.md` 5節）、測りの利用者と手順（`performance-design.md` 4節）。
- **名前を決めるもの**（「コード生成で決める」とされたもの）:
  - 機能の置き場 `frontend/src/features/preferences/` のモジュールは部品 2節の名前のまま（`registration.ts`・`messages.ts`（`preferencesMessages`）・`preferencesApi.ts`・`fieldErrors.ts`・`errorMessages.ts`・`usePreferencesForm.ts`・`usePasswordChangeForm.ts`、部品の `.tsx` と同じ場所の `.css`）。画面の確かめを集める純粋な関数を `formChecks.ts` に分ける（8節の差）。テストの補助は `testing/renderPreferences.tsx`（`renderWithProviders` に preferences と auth の機能の登録を渡し、`ToastProvider` で包み、ログイン中の偽の提供元で `/me/preferences` か `/me/password` から描く）と `testing/fixtures.ts`（`Preferences` の見本、失敗の応答の見本、`fetch` の差し替えの偽のサーバーと答えを後で決める約束）。
  - `preferencesApi.ts` は `getPreferences`・`savePreferences`・`changePassword`、パスの定数 `ME_PREFERENCES_PATH`・`ME_PASSWORD_PATH`、型 `Preferences`（`displayName`・`language`・`theme`・`fontSize`、型は U4 の `DisplayLanguage`・`ThemeChoice`・`FontSize`）・`PasswordChangeInput`（3つ）・項目名の union `PreferencesField = 'displayName' | 'language' | 'theme' | 'fontSize'`・`PasswordField = 'currentPassword' | 'newPassword' | 'newPasswordConfirmation'` を名前付きで出す。成功の応答の形の確かめはこのファイルの1か所に置き、形が違うときは形の誤りの失敗（`PreferencesShapeError`、`ApiError` とは別の種類。部品 5節）として投げる。
  - `fieldErrors.ts` は `readFieldErrors(problem, fields)`（`fields` は画面ごとの項目名の一覧）で、`{ field, reason }` の配列（`reason` は文字列のまま）を返す。
  - `errorMessages.ts` は理由の union `FieldReason = 'required' | 'tooShort' | 'tooLong' | 'invalidCharacter' | 'mismatch' | 'invalidValue' | 'unknown'`、サーバーの `reason` を寄せる `toFieldReason(serverReason)`、項目と理由から文言の鍵を選ぶ `fieldMessageKey(field, reason)` を出す（`security-design.md` 3.3 の表）。
  - `formChecks.ts` は `checkPreferencesForm(form)`・`checkPasswordChangeForm(form)`。どちらも項目ごとの理由（`FieldReason`）と、画面の項目の並びで最初の誤りの項目を返す。共用の関数（`shared/validation`）だけを呼び、決まりを別に書かない（D7）。今のパスワードは空だけを見る（W11 の3）。
  - 実際のブラウザの検査と測りのファイルは `frontend/e2e/080-preferences-accessibility.e2e.ts`（U5 の決定 2）。共用の手伝いに足すのは `frontend/e2e/support/preferencesFixtures.ts`（`Preferences` の型を付けた見本の組み立て `preferencesSample(theme, fontSize)`、固定のテストの氏名 `検査 太郎`、本物の応答の形の確かめ `hasPreferencesShape`）と `frontend/e2e/support/registeredUser.ts`（9節の決定 3）。`support/axe.ts` に `PREFERENCES_KNOWN_VIOLATIONS` と型 `PreferencesAxeState = 'preferences-ready' | 'preferences-invalid' | 'password-ready' | 'password-invalid'` を足す。
  - `data-testid` は `preferences-` で始める（例: `preferences-page`・`preferences-heading`・`preferences-loading`・`preferences-load-failed`・`preferences-reload-button`・`preferences-form`・`preferences-alert`・`preferences-display-name-input`・`preferences-language`・`preferences-theme`・`preferences-font-size`・`preferences-reset-button`・`preferences-save-button`、パスワードの画面は `preferences-password-page`・`preferences-password-heading`・`preferences-password-form`・`preferences-password-alert`・`preferences-password-current-input`・`preferences-password-new-input`・`preferences-password-confirm-input`・`preferences-password-submit-button`）。make-you-chic-ui の部品が中に持つ要素（`data-testid="radio-group"` など）は探さず、包む要素の `data-testid` か役割と名前で探す（U6 と同じ）。ユーザーメニューの項目は役割 `menuitem` と名前で探す（既存の E2E と同じ）。
- **既存に手を入れるもの**: `frontend/src/app/registry/types.ts`・`validateRegistrations.ts`・`validateRegistrations.test.ts`、`frontend/src/app/layout/ShellLayout.tsx`・`ShellLayout.test.tsx`、`frontend/src/app/navigation/navigationItems.test.ts`（`path` の項目の並びの確かめを足すだけ。`navigationItems.ts` は変えない）、`frontend/e2e/support/axe.ts`（一覧を足すだけ。既存の一覧と判定は変えない）、`README.md`、`frontend/src/features/README.md`（ユーザーメニューの `path` の項目の書き方を1節足す）。
- **変えないもの**: ApiClient（`frontend/src/shared/api-client/`）・`frontend/src/shared/validation/`（使うだけ）・`frontend/src/app/display-settings/`（使うだけ）・`frontend/src/app/navigation/navigationItems.ts`・`renderWithProviders.tsx`・`frontend/src/features/auth/`・`invitation/`・`registration/`・`dsl/`・`admin/`・`frontend/playwright.config.ts`・`playwright-secret-check-reporter.ts`・`vitest.config.ts`・`tsconfig.json`・既存の E2E（010〜070・090）と既存の手伝い（`adminLogin.ts`・`invitationSeed.ts`・`loginPreferences.ts`・`mailpit.ts` など。`axe.ts` は一覧を足すだけ）。
- **サーバーの側は変えない**: `backend/` のコード・`application.yaml`（CSP を含む）・`SecurityConfig`・Flyway・`compose.yaml`・`Dockerfile`・`build.gradle.kts`・`.github/`・`.gitignore`・`frontend/package.json`・`frontend/package-lock.json` は変えない（NFR9.10、`infrastructure-specification.md`）。`vendor/` の中身も固定先も変えない。
- **機能どうしの依存**: `features/preferences` はほかの機能を読み込まない（機能設計の2節）。テストの補助だけが、ユーザーメニューとログイン状態を描くために `features/auth` の登録を読む（U6 のテストと同じ扱い）。E2E の手伝い `registeredUser.ts` は `src/features/registration/` の型と `readRegistrationToken` を読む（`e2e/` の中だけ。画面の成果物には入らない）。
- **既存の ArchUnit** はバックエンドの検査で、U7 は触れない。

## 3. 作業の場とコミットの区切り

- **作業のブランチ**: `develop` の先頭（`08c9183`）から短命のブランチ `feature/260925-user-management-b5-u7` を作る（`team.md` の Way of Working、U5 の計画の決定 1）。
- **統合の形**: U7 は固定先の更新を含まないため、`develop` へ **squash** で統合する（`team.md` の Way of Working、`cicd-pipeline.md` 5節）。B5 は単位ごとに統合すると依頼者が決めた（U5 の計画の決定 1。`team.md` の「1 Bolt が `develop` の1コミット」とは違うが、この Bolt の扱いとして承認済み）。統合の前に `./gradlew verify` を通し（Step 21）、画面・認証に関わる変更（骨組みのユーザーメニュー・パスワードの変更）のため Mailpit を起動して `./gradlew e2eTest` の全件も通す（Step 20）。統合は依頼者の承認を得て行う。U7 の統合で B5 の3つの単位がそろう。
- **コミット**: 生成の担当はコミットしない。生成の後に、依頼者の承認を得て、次の区切りでまとめてコミットする（`project.md` の Change Control の学び）。メッセージは日本語で、U7 の内容が分かる件名にする。squash のため、`develop` には U7 の1つのコミット（件名の案「B5 U7 プリファレンスとパスワードの変更の画面（氏名と表示の設定の保存・選んだ時点の見せ方・パスワードの変更、ユーザーメニューから画面へ移る骨組み、実際のブラウザの検査と画面の時間）」）になり、細かい区切りは作業ブランチに残る。

| 区切り | 中身 | 手順 |
|---|---|---|
| C1 | 骨組みの変更（`frontend/src/app/registry/types.ts`・`validateRegistrations.ts`・`layout/ShellLayout.tsx` とそのテスト、`navigation/navigationItems.test.ts`） | Step 9・10 |
| C2 | 本番のコード（`frontend/src/features/preferences/` のテスト以外、文言と CSS） | Step 3・5・7・11・13 |
| C3 | テスト（`frontend/src/features/preferences/` の `*.test.ts(x)` と `testing/`） | Step 6・8・12・14 |
| C4 | 実際のブラウザの検査と測り（`frontend/e2e/080-preferences-accessibility.e2e.ts`、`frontend/e2e/support/` の足したファイルと `axe.ts`） | Step 15・16 |
| C5 | 文書（`README.md`・`frontend/src/features/README.md`） | Step 19 |
| C6 | この段の記録（記録の `construction/u7-preferences-ui/code-generation/` の下） | Step 22 |

- `origin` への `git push` は依頼者が行う。AI はプッシュしない。
- 統合の squash のコミットのメッセージの末尾に `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` を付ける（作業ブランチの各コミットも同じ）。

## 4. 作るもの・手を入れるもの

| 置き場 | 部品 | 新しい・手を入れる | 役割 |
|---|---|---|---|
| `app/registry/types.ts` | `UserMenuItemRegistration` を `action` だけの形と `path` だけの形の union にする | 手を入れる | 機能設計 9.1 |
| `app/registry/validateRegistrations.ts` | ユーザーメニューの項目の「`action` と `path` のどちらも無い・両方ある」と「登録されていない `path`」の検査 | 手を入れる | 9.2、NFR9.4 |
| `app/layout/ShellLayout.tsx` | `path` の項目を `MenuItem` の `href` と `onClick`（`event.preventDefault()` の後に `navigate(path)`）にする。`action` の項目は今までどおり | 手を入れる | 9.3、W1 |
| `features/preferences/preferencesApi.ts` | 3つの関数・パスの定数・型・成功の応答の形の確かめ・`PreferencesShapeError` | 新しい | 部品 5節、`security-design.md` 3.1 |
| `features/preferences/fieldErrors.ts` | `readFieldErrors`（例外を出さない読み取り） | 新しい | W12、`security-design.md` 3.2 |
| `features/preferences/errorMessages.ts` | `FieldReason`・`toFieldReason`・`fieldMessageKey` | 新しい | W11・W12、D13、`security-design.md` 3.3 |
| `features/preferences/formChecks.ts` | `checkPreferencesForm`・`checkPasswordChangeForm`（共用の関数で確かめ、理由と最初の誤りの項目を返す） | 新しい | W11、D7・D9 |
| `features/preferences/messages.ts` | `preferencesMessages`（ja・en、機能設計 7節の鍵と文） | 新しい | NFR8.1 |
| `features/preferences/usePreferencesForm.ts` | S4 の状態・操作・副作用（読み込みの番号、送信中の印 `useRef`、部品が付いているかの印、D2 のそろえ、見せ方、保存、Toast の印） | 新しい | 部品 3.1、W2〜W8 |
| `features/preferences/usePasswordChangeForm.ts` | S5 の状態・操作（送信中の印、成功で空に、今のパスワードの誤りの結び付け） | 新しい | 部品 3.2、W9・W10 |
| `features/preferences/PreferencesPage.tsx`（と CSS） | h1・読み込み中・読み込みの失敗・フォームの出し分け、外れるときの `clearPreview` はフック | 新しい | 部品 1・4節 |
| `features/preferences/PreferencesLoadFailure.tsx` | 失敗の Alert（`role="alert"`）と「もう一度読み込む」 | 新しい | W2 |
| `features/preferences/PreferencesForm.tsx`（と CSS） | 氏名・3つの RadioGroup（`legend`・`lang`）・案内・「元に戻す」・「保存する」・画面の知らせ | 新しい | W3〜W8、D9〜D14 |
| `features/preferences/PasswordChangePage.tsx` | h1 と `PasswordChangeForm` | 新しい | 部品 1・4節 |
| `features/preferences/PasswordChangeForm.tsx`（と CSS） | 3つのパスワードの項目・「変更する」・画面の知らせ | 新しい | W9・W10 |
| `features/preferences/registration.ts` | 機能の登録（`featureId: 'preferences'`、2つの画面、ユーザーメニューの2つの `path` の項目（order 80・90）、文言）。サイドバーの項目は無い | 新しい | 部品 6節 |
| `features/preferences/testing/renderPreferences.tsx`・`fixtures.ts` | テストの補助（テストからだけ使う） | 新しい（テストの支え） | 部品 7節 |
| `frontend/e2e/080-preferences-accessibility.e2e.ts` | 20 組のテスト（組ごとに2画面×2状態）と測りのテスト1件 | 新しい | NFR6.1〜NFR6.3・NFR7.3・NFR7.4・NFR9.9 |
| `frontend/e2e/support/preferencesFixtures.ts` | `preferencesSample`・`hasPreferencesShape`（`Preferences` の型付き） | 新しい（B5 の共用の手伝い） | `logical-components.md` 5.3、`project.md` の Corrections |
| `frontend/e2e/support/registeredUser.ts` | `createRegisteredUser(request, runTag)`（招待の API → Mailpit からリンク → 登録の完了の API）・`loginWithForm(page, email, password)`・前提の確かめ `registrationPrerequisites(request, token)` | 新しい（B5 の共用の手伝い） | `performance-design.md` 4.2、9節の決定 3 |
| `frontend/e2e/support/axe.ts` | `PREFERENCES_KNOWN_VIOLATIONS`・`PreferencesAxeState` を足す | 手を入れる | U5 の決定 4 の形 |
| `README.md` | 「プリファレンスとパスワードの変更の画面（U7）」の節、E2E の表の 080 の行と「080 について」、060 の節の「後の単位（U6・U7）も同じ当て方」の文の直し、「画面の表示の設定（U4）」の既知の制約に U7 の行、「後の単位が使う差し込み口」の画面の差し込み口の行にユーザーメニューの `path` | 手を入れる | `cicd-pipeline.md` 3.4 |
| `frontend/src/features/README.md` | ユーザーメニューの項目で画面へ移る書き方（`path`） | 手を入れる | 機能設計 9節 |

## 5. 手順

各層で実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む（test-after、Testing Contract の `ordering`）。U7 の層は「業務処理（画面の純粋な関数）→ API（`preferencesApi`）→ 画面の振る舞い（骨組みの変更 → 画面部品 → 画面と状態と登録）→ 実際のブラウザの検査と測り（080）」の順とする。U7 は内部DB とブラウザの保存を持たないため、データの形（DB）とデータアクセスの層は無い。骨組みの変更を画面部品より先に置くのは、機能の登録（`path` の項目）が新しい型と検査を前提にするため。

### Step 1: 作業の場の用意と、変更の前の基準（依頼者が承認した git の操作）

- [ ] `develop` の先頭のハッシュ（`08c9183`）と、サブモジュールの固定先（`vendor/make-you-chic-ui` が `735ef04ce6eb618cb875f5c4b31c1645a1f84c28`、`vendor/java-mustache-processor` が `8d44c36b2bbaf36a35fe0ce397ac1c0fb7bc0ba4`）を記録し、`develop` から短命のブランチ `feature/260925-user-management-b5-u7` を作る（コミットはしない。統合は squash）
- [ ] 変更の前の基準をとる: colima が動いていることを確かめ、README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡して `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を実行し、テストの件数（バックエンドの単体・結合、フロントエンドのファイル数と件数、失敗・飛ばした）と、フロントエンドの全体のカバレッジ（行・分岐）を記録する（brownfield の Test Baseline、`project.md` の Testing Posture。U6 の後の実測は 81 ファイル 625 件、行 97.54%・分岐 93.06%）
- [ ] 同じ実行の成果物で、変更の前の値を記録する: `frontendBundleSize` の初回の JavaScript（gzip）、`frontend/dist/assets/` の JavaScript のファイルの数と合計、`backend/build/libs/mastersmith.war` の大きさ（NFR6.6、`performance-design.md` 5節。測る道具は足さず、`du`・`stat` と既存の検査の出力）
- [ ] 対応: B5 の共通の完了の条件、NFR6.6（前の値）

### Step 2: 前提の確かめ（読み取りだけ）

- [ ] `git submodule status` で `vendor/make-you-chic-ui` が `735ef04` であること（NFR9.10）と、U7 が使う口が `vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/` にあることを確かめて記録する: Dropdown（`MenuItem` の `href` を渡すと `<a href role="menuitem">`、`onClick(event)` をマウスと Enter・Space で呼び、選んだ後にメニューを閉じて開き口へフォーカスを戻す、`href` なしは `<button role="menuitem">`）、RadioGroup（`legend` を渡すと `fieldset`・`legend`、`options[].label` は ReactNode、選択肢の `lang`、`name`・`value`・`onChange`、`disabled` を使わない）、Button（`loading` で `aria-disabled`・`aria-busy`、押しても `onClick` を呼ばない、`type="submit"` でもフォームを送らない）、FormField（`label`・`error`・`helperText`・`required`、誤りと案内の結び付け）、TextInput（`forwardRef`・`readOnly`・`type`・`autoComplete`）、Alert（`danger` は `role="alert"`、`dismissLabel`）、Toast（`ToastProvider`・`useToast` の `show`、`aria-live="polite"`、種類 success）。口が無い・形が違うときは、9節の決定 5 の (a) のとおり止めて諮る
- [ ] U4 の口を確かめて記録する: `useDisplaySettings()` の `language`・`theme`・`fontSize`・`displayName`・`setPreview(theme?, fontSize?)`・`clearPreview()`・`applyUserPreferences(prefs)`（ログイン中だけ効く。`UserDisplaySettings` は3つの軸と `displayName`）、`LANGUAGE_NAMES`・`THEME_CHOICES`・`FONT_SIZES`、骨組みの文言 `display.theme.*`・`display.fontSize.*`、`renderWithProviders` の `route`・`registrations`・`provider`・`languages` と `fakeProvider`・`resetDisplayTestState`・`stubBrowserLanguages`、`renderWithProviders` が `ToastProvider` を持たないこと（U5 の `renderInvitation.tsx` が包んでいる形）
- [ ] 骨組みの今の形を確かめて記録する: `ShellLayout.tsx` のユーザーメニューが `buildUserMenuItems` の `action` を `onClick` に渡す形で、U4 の B4 の氏名の変更が入っていること（機能設計 9.3 の前提）、既存の `ShellLayout.test.tsx`・`validateRegistrations.test.ts`・`navigationItems.test.ts` の確かめ、`features/auth/registration.ts` のログアウトの項目（`auth-logout`、`action` だけ、order 100）
- [ ] U2 の API の実際の形を `backend/src/main/java/cherry/mastersmith/user/web/` と README の「利用者のプリファレンスとパスワードの変更（U2）」で確かめて記録する: `PreferencesRequest`・`PreferencesResponse`（4つ）、`PasswordChangeRequest`（`currentPassword`・`newPassword`・`newPasswordConfirmation`）、`PUT` の 200、`POST` の 204、400 `VALIDATION_FAILED` の `fieldErrors: [{ field, reason }]`（`reason` は `REQUIRED`・`TOO_SHORT`・`TOO_LONG`・`INVALID_CHARACTER`・`INVALID_VALUE`・`MISMATCH`、1項目に1つ）、400 `PASSWORD_CURRENT_MISMATCH`、本文が読めないときの 400 `MALFORMED_REQUEST`（`fieldErrors` なし）、401 `AUTHENTICATION_REQUIRED`（`security-design.md` 3.2 の形と一致すること。機能設計の承認の場の G6）
- [ ] U6 の共用の確かめの関数（`frontend/src/shared/validation/`）の名前と誤りの種類を確かめ、`security-design.md` 3.3 の表と突き合わせた結果を記録する（2.1 の NFR 設計の U7 R-01・NFR 要件の U7 R-02。計画を書く時点の読み取りで一致を確かめた）
- [ ] 080 の測りの飛ばす道の前提（2.1 の U5 R-02・N10）: `frontend/playwright.config.ts` の `webServer.env` に SMTP の接続先（`localhost:1025`）とベース URL が入り、`build.gradle.kts` の `e2eTest` が Mailpit の API に届くかを始める前に確かめることを読み取りで確かめて記録する
- [ ] フロントエンドのリンタの設定（`frontend/eslint.config.js`・`.oxlintrc.json`）を読み、U7 に当たる決まり（`react-hooks` の推奨、`export default`・`enum` の禁止、`react/no-danger`）を確かめる
- [ ] 対応: NFR9.10、機能設計の承認の場の Request Changes と G6、NFR 設計の承認の場の U7 R-01・U5 R-02、基盤の設計の N10

### Step 3: 骨組み（型・文言）

- [ ] `frontend/src/features/preferences/preferencesApi.ts` の型の部分を作る: `Preferences`（`displayName: string`・`language: DisplayLanguage`・`theme: ThemeChoice`・`fontSize: FontSize`）、`PasswordChangeInput`（3つの文字列）、`PreferencesField`・`PasswordField`、`ME_PREFERENCES_PATH`・`ME_PASSWORD_PATH`。`enum` を使わない
- [ ] `frontend/src/features/preferences/messages.ts` に `preferencesMessages`（ja・en）を作る。鍵と文は機能設計 7節の表のとおり（`preferences.` で始める。英語の文言は画面の幅と既存の言い回しをこの Step で確かめる）
- [ ] 新しいファイルはすべて先頭に Apache License 2.0 のヘッダー（`/* ... */`、2026、agwlvssainokuni）と、日本語の説明のコメントを置く。エクスポートは名前付きだけ（`export default` を使わない）
- [ ] 対応: 部品 2節・5節、NFR8.1、機能設計 7節、`team.md` の Code Style

### Step 4: テストの実行の準備（最初のテストより前）

- [ ] `unit-test-instructions.md` 2節の単体のコマンド（Vitest の対象を `src/features/preferences` と骨組みの3つのテストのファイルに絞ったもの）を実行し、設定が読み込まれ、既存の骨組みのテスト（`validateRegistrations.test.ts`・`ShellLayout.test.tsx`・`navigationItems.test.ts`）が通って終了の状態が 0 であることを確かめる（`src/features/preferences` にはまだテストが無い。`--passWithNoTests` を付けた `src/features/preferences` だけの実行も終了の状態 0 であることを確かめる）
- [ ] `fast-check`（既存）と `vitest-axe`（既存）が使えること、make-you-chic-ui の `ToastProvider` で包んだ描画で `useToast` の `show` が `aria-live` の領域に出ることを確かめる（既存の `renderInvitation.tsx` の形）
- [ ] `(cd frontend && npx playwright test --list)` で既存の E2E の一覧（010〜070・090、8 ファイル 69 件）が読めることを確かめる（080 はまだ無い）
- [ ] 対応: Testing Contract の `runner_step`

### Step 5: 業務処理 — 画面の純粋な関数（`fieldErrors`・`errorMessages`・`formChecks`）— 実装

- [ ] `fieldErrors.ts`: `readFieldErrors(problem, fields)`。`problem` が無い・オブジェクトでない、`fieldErrors` が無い・配列でない → 空の一覧。要素がオブジェクトでない・`field` か `reason` が文字列でない → その要素を捨てる。`field` が `fields` に無い → 捨てる。同じ `field` の2つ目以降 → 捨てる。`reason` は文字列のまま。`detail`・`traceId` など、ほかの値を写さない。例外を出さない（`security-design.md` 3.2）
- [ ] `errorMessages.ts`: `toFieldReason`（`REQUIRED` → `required`、`TOO_SHORT` → `tooShort`、`TOO_LONG` → `tooLong`、`INVALID_CHARACTER` → `invalidCharacter`、`MISMATCH` → `mismatch`、`INVALID_VALUE` → `invalidValue`、ほか → `unknown`）と `fieldMessageKey(field, reason)`（`security-design.md` 3.3 の表。表に無い組と `unknown` は、文字の項目が `preferences.field.invalid`、選択のまとまりが `preferences.choice.invalid`）。今のパスワードの誤りの鍵 `preferences.password.currentMismatch` と画面の知らせの鍵（`preferences.form.invalid`・`preferences.save.failed`・`preferences.password.failed`・`preferences.load.failed`）の定数もここに置く
- [ ] `formChecks.ts`: `checkPreferencesForm(form)` は氏名を `validateDisplayName` で確かめる（言語・テーマ・文字の大きさは選択肢から選ぶため確かめない、W11 の2）。`checkPasswordChangeForm(form)` は今のパスワードの空（`required`。規則は当てない）、新しいパスワードを `validateNewPassword`、確かめを `validatePasswordConfirmation` で確かめる。どちらも項目ごとの理由（1項目に1つ）と、画面の並びで最初の誤りの項目を返す（プリファレンスは氏名、パスワードは今 → 新しい → 確かめの順）
- [ ] どの関数も React・`window`・API・ブラウザの保存に触れない。`console` を呼ばない
- [ ] 作った後に、`FieldReason` に寄せる理由と共用の関数の union が `security-design.md` 3.3 と一致することをもう一度確かめる（2.1 の NFR 設計の U7 R-01 の最終の突き合わせ。結果を `code-summary.md` に記録する）
- [ ] 対応: W11・W12、D7〜D9・D13、NFR8.2・NFR9.2、`security-design.md` 3.2・3.3

### Step 6: 業務処理 — 画面の純粋な関数 — テスト（単体・性質ベース）

- [ ] `fieldErrors.test.ts`: 知っている項目だけを返す、`field`・`reason` が文字列でない要素と知らない項目を捨てる、同じ項目の2つ目を捨てる、`problem` が無い・`fieldErrors` が配列でない・`null`・数・文字列で空の一覧、`detail` の値が結果に写らない。性質ベース（fast-check）: `fc.jsonValue()` と `fc.anything()` を `problem` に渡しても例外を出さない、返す項目の名前は `fields` の中だけで重ならない（NFR9.6）
- [ ] `errorMessages.test.ts`: 6つのサーバーの `reason` と知らない値の寄せ方、`security-design.md` 3.3 の表のすべての組の鍵、表に無い組と `unknown` の一般の文言（文字の項目・選択のまとまり）、表のすべての鍵が `preferencesMessages` の ja・en の両方にあって空でない（NFR8.2）
- [ ] `formChecks.test.ts`: 氏名の誤りなし・空白だけ・255 コードポイント・254 コードポイントちょうど（誤りなし）・内側の改行と U+200B、パスワードの画面の誤りなし・今のパスワードの空・今のパスワードに規則を当てない（11 文字でも誤りなし）・新しいパスワードの空・11 コードポイント・12 コードポイント・「あ」24 文字（72 バイト）・「あ」24 文字＋「a」（73 バイト）・絵文字 11・12 文字・確かめの空と不一致、複数の誤りのときの最初の項目の順（共用の関数を差し替えない。NFR9.7、AC5.1.3）
- [ ] 失敗時の fast-check の `seed`・`path` はテストの出力に残る（既存の `submitInput.test.ts` と同じ扱い）
- [ ] 単体のコマンドを実行して通す
- [ ] 対応: NFR8.2・NFR9.2・NFR9.6・NFR9.7、AC4.1.7・AC5.1.3、部品 7節の `fieldErrors.ts`・`errorMessages.ts` の行

### Step 7: API（`preferencesApi`）— 実装

- [ ] `getPreferences()`: 既存の `apiRequest` で `GET /api/me/preferences`。成功の本文を JSON として読み、`displayName` が文字列、`language` が `ja`・`en`、`theme` が `light`・`dark`・`system`、`fontSize` が `sm`・`md`・`lg` のときだけ4つを返す（知らない項目は捨てる）。読めない・形が違うときは `PreferencesShapeError` を投げる
- [ ] `savePreferences(prefs)`: `PUT /api/me/preferences`、`Content-Type: application/json`、本文は4つだけ。成功の本文は `getPreferences` と同じ形の確かめ
- [ ] `changePassword(input)`: `POST /api/me/password`、本文は3つだけ。204 の本文は読まない
- [ ] `Authorization`・`Accept-Language` を呼び出し側で付けない（ApiClient に任せる）。失敗は ApiClient の `ApiError` をそのまま投げる。要求の本文と応答の値を `console`・ブラウザの保存に出さない。フォームの `method`・`action` を使う道を作らない（`security-design.md` 2節）
- [ ] 対応: 部品 5節、NFR6.4・NFR9.1・NFR9.2、`security-design.md` 3.1、C4

### Step 8: API — テスト（単体）

- [ ] `preferencesApi.test.ts`（`vi.stubGlobal('fetch', ...)` で差し替え、`resetApiClient` をテストごとに呼ぶ）:
  - 3つの要求のメソッド・パス・本文（保存は4つだけ、変更は3つだけ）、呼び出し側で `Authorization`・`Accept-Language` を付けていない（ApiClient が付ける値だけ）
  - 取得と保存の成功の値が決まった4つだけ、`language`・`theme`・`fontSize` が決めた値の外・`displayName` が文字列でない・本文が読めないと `PreferencesShapeError`
  - 変更の 204 の空の本文が成功
  - 400（`VALIDATION_FAILED` と `fieldErrors`・`PASSWORD_CURRENT_MISMATCH`）・500 と通信の失敗で `ApiError` の種類・状態コード・`code`・`problem` がそのまま届く
  - 401 `AUTHENTICATION_REQUIRED` で ApiClient の更新と送り直しが1回だけ動き、400 `PASSWORD_CURRENT_MISMATCH` では更新の要求が送られない（NFR9.3）
- [ ] 単体のコマンドを実行して通す
- [ ] 対応: NFR6.4・NFR9.1〜NFR9.3、部品 5節、`security-design.md` 3.1・4節

### Step 9: 画面の振る舞い — 骨組みの変更（`types`・`validateRegistrations`・`ShellLayout`）— 実装

- [ ] `types.ts`: `UserMenuItemRegistration` を機能設計 9.1 の形の union（`action` だけ、または `path` だけ）にする。既存のログアウトの項目の登録はそのまま型に合う。説明のコメントを日本語で足す
- [ ] `validateRegistrations.ts`: ユーザーメニューの項目ごとに、(1) `action` と `path` のどちらも無い・両方ある、(2) `path` が登録された画面の URL（ホームを含む）と完全に一致しない、を問題として集める（文言は機能設計 9.2 の形。サイドバーの項目と同じ判定）。既存の検査と順は変えない。型の外から来る値（`action` が関数でない、`path` が文字列でない）も (1) として扱う
- [ ] `ShellLayout.tsx`: `path` の項目は `href: path` と `onClick: (event) => { event.preventDefault(); void navigate(path) }`、`action` の項目は今までどおり `onClick: item.action`（`href` を渡さない）。`buildUserMenuItems` は変えない
- [ ] 対応: 機能設計 9節、W1、NFR9.4、`security-design.md` 5節

### Step 10: 画面の振る舞い — 骨組みの変更 — テスト

- [ ] `validateRegistrations.test.ts` に足す: `path` の項目が通る、ホームの `path` が通る、既存の `action` だけの項目が通る、`action` と `path` のどちらも無い・両方あると問題、登録されていない `path`・絶対 URL（`https://example.com/`・`javascript:alert(1)`）・`//example.com` で問題、ほかの問題と一緒に集まる（NFR9.4）
- [ ] `ShellLayout.test.tsx` に足す: `path` の項目が `href` を持つリンク（`<a>`、`role="menuitem"`）で描かれ、マウスで選ぶと読み込み直しなしでその画面へ移る（既定の移動が止められる）、キーボード（Enter・Space）でも同じ、既存のログアウトの `action` の項目はボタンのまま呼ばれる、vitest-axe（`path` の項目を含むメニューを開いた状態）
- [ ] `navigationItems.test.ts` に足す: `path` の項目と `action` の項目が order の順に並ぶ（`buildUserMenuItems` は変えない）
- [ ] 単体のコマンドを実行して通す。既存の骨組みのテストも変えずに通る
- [ ] 対応: NFR9.4・NFR9.8、部品 7節の `validateRegistrations`・`ShellLayout` の行、AC5.1.9

### Step 11: 画面の振る舞い — 画面部品（PreferencesLoadFailure・PreferencesForm・PasswordChangeForm）— 実装

- [ ] `PreferencesLoadFailure.tsx`（部品 4節の props）: 失敗の Alert（`danger`、`role="alert"`、`preferences.load.failed`）と secondary の Button「もう一度読み込む」（`retryRef`）
- [ ] `PreferencesForm.tsx`（部品 4節の props）:
  - `form`（`aria-labelledby` に見出しの id、`noValidate`、送信の出来事で既定の動きを止めて `save()`）。画面の知らせはフォームの上の Alert（`danger`、`dismissLabel` に `preferences.alert.dismiss`）で1つだけ
  - 氏名: FormField（`required`、`error`）と TextInput（`autoComplete="name"`、`saving` の間 `readOnly`）
  - 言語の RadioGroup: `legend` は名前 `preferences.language` と2行目の小さな文字の案内 `preferences.language.hint`（と、あれば選択の誤り）、選択肢は `LANGUAGE_NAMES` と `lang`
  - テーマと文字の大きさの案内 `preferences.appearance.hint` を2つのまとまりの前に1回だけ文字として置く
  - テーマ・文字の大きさの RadioGroup: `legend` は名前（と、あれば選択の誤り）、選択肢は `display.theme.*`（「OS に合わせる」「ライト」「ダーク」の順）・`display.fontSize.*`、`lang` を渡さない
  - 3つの RadioGroup は `disabled` を使わず、`key` を言語で変えない。選択のまとまりのフォーカスの行き先は、包む要素（`preferences-language` など）の中の選ばれているラジオ
  - 「元に戻す」（secondary、`disabled` は `!dirty || saving`）と「保存する」（primary、`type="submit"`、`loading` は `saving`、文言は `preferences.saving`・`preferences.save`）
  - 同じ場所の CSS: コンテンツの幅 640px 前後、768px 未満で1列、ラジオの選択肢の横並びと折り返し（自分のクラス。make-you-chic-ui の内部のクラスを上書きしない。`style` 属性で差し込まない）
- [ ] `PasswordChangeForm.tsx`: 3つの FormField と TextInput（`type="password"`・`required`、`autoComplete` は `current-password`・`new-password`・`new-password`、`sending` の間 `readOnly`）、新しいパスワードの `helperText` に `preferences.password.hint`、画面の知らせ（Alert）、「変更する」（primary、`type="submit"`、`loading` は `sending`、文言 `preferences.password.submitting`）。フォームに `method`・`action` を置かない
- [ ] 部品の文言はすべて `useMessages()` で引き、誤りは文言の鍵で受けて描画のたびに今の言語で引く（W13 の2）。`console` を呼ばない。サーバーの `detail` を受け取る口を作らない（D13）
- [ ] 対応: W2〜W5・W7〜W10・W12・W13、D9〜D11・D13・D14、CR6.1〜CR6.4・CR6.6・CR6.9、NFR7.1・NFR7.5・NFR9.1・NFR9.2

### Step 12: 画面の振る舞い — 画面部品 — テスト（vitest-axe を含む）

- [ ] `testing/renderPreferences.tsx`（`renderWithProviders` に preferences と auth の機能の登録を渡し、`ToastProvider` で包み、`fakeProvider` のログイン中の状態で `route` の既定を `/me/preferences` にして描く。画面の言語はブラウザの言語設定で ja・en を切り替える）と `testing/fixtures.ts`（`Preferences` の見本（氏名は `example.test` の下のメールアドレスの形の初期値と架空の氏名）、`fieldErrors` と `detail` の目印つきの失敗の応答の見本、`fetch` の差し替えの偽のサーバー（要求の記録、答えを後で決める約束））を作る
- [ ] `PreferencesLoadFailure.test.tsx`: `role="alert"` と文言、「もう一度読み込む」で `onRetry`、en の文言、vitest-axe
- [ ] `PreferencesForm.test.tsx`（値と操作は props で渡す）: 3つのまとまりが `fieldset`（`role="group"`）で名前が `legend` の文字（言語は案内を含む）、言語の選択肢の文字の `lang="ja"`・`lang="en"`、テーマと文字の大きさに `lang` が無い、テーマの並び、矢印キーで選べる、氏名の誤りの文字と `aria-invalid`・`aria-describedby`、選択の誤りが `legend` の中に出る、「元に戻す」の押せる・押せない、`saving` で「保存しています」・`aria-disabled`・`aria-busy`・氏名の `readOnly`・「元に戻す」の `disabled`、画面の知らせの `role="alert"` と閉じるボタンの文言、vitest-axe
- [ ] `PasswordChangeForm.test.tsx`: 3つの `type="password"` と `autocomplete`（CR6.9）、「12 文字以上」の案内が入力の前に出る、誤りの結び付け、`sending` で「変更しています」・`aria-disabled`・3つの `readOnly`、フォームに `method`・`action` が無い、vitest-axe
- [ ] 単体のコマンドを実行して通す
- [ ] 対応: NFR7.2・NFR7.5・NFR8.1・NFR9.1、部品 7節のアクセシビリティの行、CR6.1〜CR6.3・CR6.6・CR6.9

### Step 13: 画面の振る舞い — 画面・状態・登録（2つのフック・2つの画面・registration）— 実装

- [ ] `usePreferencesForm.ts`（部品 3.1、`performance-design.md` 2・3節）:
  - 部品が付いたときの副作用で `getPreferences` を送り、読み込みの番号が最後のものと一致し部品が付いているときだけ答えを使う（StrictMode の二重の実行でも1つだけ。U6 の #1・#2 と同じく、副作用の中で同期に状態を変えない形にする）
  - 成功: 今の設定とフォームに入れて `ready`。読んだ4つが `useDisplaySettings()` の当たっている値と違えば、読んだ値で `applyUserPreferences` を1回呼ぶ（D2。知らせない）。失敗（`ApiError`・`PreferencesShapeError`）: `loadFailed`。「もう一度読み込む」の後は成功で氏名の項目、失敗で「もう一度読み込む」へフォーカス（W2 の4）
  - `setTheme`・`setFontSize` はフォームの値を変え、フォームのテーマと文字の大きさで `setPreview` を呼ぶ。`setLanguage`・`setDisplayName` は値だけを変える（D4）。送信の間の変更と選択は受け付けない（D10）
  - `reset()`: フォームを今の設定に戻し、誤りと知らせを消し、`clearPreview`、フォーカスを「保存する」へ（W5）
  - `save()`: 送信中の印（`useRef`）が立っていれば何もしない。知らせを消し、`checkPreferencesForm` で確かめ、誤りなら項目の誤りと最初の誤りの項目へのフォーカスで止める（見せ方は残す）。通れば印を立て `saving` にして `savePreferences`。200: 応答の値で `applyUserPreferences`、今の設定とフォームを応答の値にし、誤りを消して `ready`、`pendingSavedNotice` を立てる。400 `VALIDATION_FAILED`: `readFieldErrors` → `toFieldReason` → `fieldMessageKey` で項目の誤り、最初の誤りの項目へフォーカス、対応づけられる誤りが無ければ `preferences.form.invalid`。`PreferencesShapeError` とほかの失敗: `preferences.save.failed`（フォーカスは動かさない、値と見せ方と今の設定は変えない）。部品が外れた後の答えは、200 のときだけ `applyUserPreferences` を呼び、ほかは捨てる（W6 の3）
  - `pendingSavedNotice` が立った描画の後に `useToast` の `show`（success、その描画の文言の `preferences.saved`）を1回出して印を下ろす（D12）
  - 部品が外れるとき `clearPreview()`（D6）
- [ ] `usePasswordChangeForm.ts`（部品 3.2）: `submit()` は送信中の印が立っていれば何もしない。知らせを消し、`checkPasswordChangeForm` で確かめ、誤りなら止めて最初の誤りの項目へフォーカス。通れば `sending` にして `changePassword`。204: 3つを空、誤りを消し、Toast `preferences.password.changed`（フォーカスは動かさない）。400 `PASSWORD_CURRENT_MISMATCH`: 今のパスワードの項目に `preferences.password.currentMismatch` を結び付けてフォーカス、3つの値を残す。400 `VALIDATION_FAILED`: 項目の誤り（W12）。ほか: `preferences.password.failed`、値を残す。部品が外れた後の答えは捨てる
- [ ] `PreferencesPage.tsx`（と CSS）: h1 `preferences.page.title`（id をフォームの名前に使う）、`loading` は `preferences.loading` の文字とフォームの置き場の `aria-busy`、`loadFailed` は `PreferencesLoadFailure`、ほかは `PreferencesForm`
- [ ] `PasswordChangePage.tsx`: h1 `preferences.password.title` と `PasswordChangeForm`。開いても API を呼ばない（NFR6.4）
- [ ] `registration.ts`: `featureId: 'preferences'`、画面 `/me/preferences`・`/me/password`（`lazy` の遅延読み込み、既存の `features/auth/registration.ts` と同じ形、`SHELL`・`LOGGED_IN`）、ユーザーメニュー `preferences-open`（`preferences.menu.preferences`、`/me/preferences`、order 80）・`preferences-password`（`preferences.menu.password`、`/me/password`、order 90）、`messages: preferencesMessages`。サイドバーの項目は無い
- [ ] 氏名・パスワード・応答の値を `console`・ブラウザの保存・URL に出さない。U7 はブラウザの保存に触れない（U4 の `applyUserPreferences` だけ）
- [ ] 対応: W1〜W13、D1〜D14、NFR2.1・NFR6.4〜NFR6.6・NFR9.1〜NFR9.3、部品 3・6節

### Step 14: 画面の振る舞い — 画面・状態・登録 — テスト（vitest-axe を含む）

- [ ] `PreferencesPage.test.tsx`（`fetch` を差し替え、ApiClient・U4 の口・ログイン状態は本物。部品 7節の `PreferencesPage` の行を1つずつ）:
  - 読み込み: 読み込み中の表示と `aria-busy`、成功で4つが入る、テーマ `system` の利用者は「OS に合わせる」が選ばれ（OS の配色の差し替えでダーク）今の見た目の値ではない、初期値の利用者（氏名がメールアドレスの形・ja・system・md）がそのまま出る、GET は1回だけ（NFR6.4）、失敗（500・通信の失敗・形の誤り）で `role="alert"` と「もう一度読み込む」が出てフォームは出ない、押すと読み直して成功で氏名へフォーカス（AC4.1.1・AC4.1.10）
  - そろえ: 読んだ値が当たっている値と違えば、ユーザーメニューの名前・`<html lang>`・`data-theme` が読んだ値になる。同じなら `applyUserPreferences` が呼ばれない（口を差し替えたテスト1件）
  - 見せ方: dark・lg を選ぶと保存の前から `<html>` の `data-theme`・`data-font-size` が dark・lg、言語 en を選んでも文言と `<html lang>` は変わらず案内が出ている、「元に戻す」で light・md とフォームの値に戻り誤りと知らせが消えフォーカスが「保存する」、保存せずに部品を外す・ほかの画面へ移ると light・md に戻る（AC4.1.11）
  - 保存の成功: 言語 en・氏名「山田 花子」で保存すると、文言と `<html lang>` が en、ユーザーメニューの名前が「山田 花子」、Toast が en の「Saved」、「保存する」で送ったときフォーカスが送信の間も後も「保存する」に残る、氏名の前後の空白は応答の値（除いた値）でフォームに入る、言語を変えても3つの RadioGroup と「保存する」が同じ要素のまま（AC4.1.4・AC4.1.8・AC4.1.12）
  - 画面の確かめ: 空白だけ・255 コードポイント・内側の改行・ゼロ幅の空白の氏名は送らずに項目の下に出して氏名へフォーカス、254 コードポイントちょうどは送る、見せ方は残る（AC4.1.7、CR6.1）
  - サーバーの誤り: 400 `VALIDATION_FAILED` の `displayName` の誤りが氏名の下に出てフォーカス、`language` の `INVALID_VALUE` が言語の `legend` の中に出て選ばれているラジオへフォーカス、知らない項目だけ・`fieldErrors` なしで画面の知らせ「入力を確かめてください。」、400 `MALFORMED_REQUEST`・500・通信の失敗・200 の形の誤りで保存できなかった知らせ（値と見せ方が残りフォーカスは動かない）、`detail` の目印が画面に出ない（CR6.4、NFR9.2）
  - 送信中: 「保存する」が `aria-disabled`・`aria-busy` で「保存しています」、2回押す・Enter で送っても PUT は1回、氏名は `readOnly`・ラジオを選んでも値と見た目が変わらない・「元に戻す」は押せない、氏名で Enter を押して送ったときフォーカスが氏名に残る（NFR6.5、D10、CR6.3）
  - 離れた後の答え: 送信中に部品を外し、200 が来ると `applyUserPreferences` が応答の値で呼ばれ Toast は出ない。失敗は捨てる（W6 の3）
  - 見せ方の最中のログイン状態の更新（機能設計 10節の (f)）: 見せ方の最中にログイン状態が新しくなったときの画面の値とフォームの値を確かめて記録する。食い違いが起きても生成は止めず、U4 は変えない（9節の決定 4）
  - 漏えい: 読み込み・保存の成功・保存の失敗の後に、localStorage・sessionStorage のすべての鍵の値に氏名（メールアドレスの形の初期値を含む）が無い（U4 の3つの表示の設定の鍵だけ）、`console` の5つがどの流れでも呼ばれない（jsdom が axe の色の検査で出す「Not implemented: HTMLCanvasElement…」だけを除く、U6 と同じ）、URL に値が無い（NFR2.1）
  - vitest-axe の違反 0 件（`ready` の画面全体）
- [ ] `PasswordChangePage.test.tsx`（部品 7節の `PasswordChangePage` の行を1つずつ）:
  - 開いても API を呼ばない（NFR6.4）
  - 画面の確かめ: 今のパスワードが空、新しいパスワードが空・11 コードポイント・73 バイト（長すぎる旨の文言）・絵文字 11 文字、確かめの不一致で送らずに項目の下に出して最初の誤りの項目へフォーカス。12 コードポイント・72 バイト・絵文字 12 文字は送る。今のパスワードに規則を当てない（AC5.1.3、NFR9.7）
  - 成功: 204 で Toast「パスワードを変更しました」、3つの項目が空、フォーカスが送信を始めた要素に残る、ログイン状態が変わらずログインの画面へ移らない、トークンの更新の要求が送られない（AC5.1.8）
  - 今のパスワードの誤り: 400 `PASSWORD_CURRENT_MISMATCH` で今のパスワードの項目に誤りが結び付き（`aria-describedby`・`aria-invalid`）そこへフォーカス、新しいパスワードの2つの値が残る、トークンの更新の要求が送られず、ログイン状態が変わらず、ログインの画面へ移らない（AC5.1.6・AC5.1.7、NFR9.3）
  - ほかの失敗と送信中: 400 `VALIDATION_FAILED` の項目ごとの誤り、500・通信の失敗の画面の知らせと値の保持、送信の間「変更する」が `aria-disabled` で「変更しています」、3つの項目が `readOnly`、2回押しても POST は1回（CR6.3・CR6.4、NFR6.5）
  - 漏えい: 送信の前・成功の後・失敗の後に、localStorage・sessionStorage のどの鍵にもパスワードの値が無い、`location` の `search`・`hash` に値が無い、`console` が呼ばれない、送信中に部品を外すと答えを捨てる（NFR9.1）
  - vitest-axe の違反 0 件
- [ ] `registration.test.tsx`: 2つの画面が `SHELL`・`LOGGED_IN`・遅延読み込み、ユーザーメニューの2つがログアウトより前（order 80・90 < 100）で `path` を持つ、サイドバーの項目が無い、既存の登録（auth・admin・dsl・invitation・registration）と合わせて `validateRegistrations` が通る、文言の ja・en の鍵がそろい空でなく `preferences.` で始まる、画面の言語が en でも言語の選択肢が「日本語」と出る、未ログインで `/me/preferences` を開くとログインの画面へ移る（W1 の3、NFR8.1）
- [ ] ユーザーメニューからの移り（`registration.test.tsx` か `PreferencesPage.test.tsx`）: ユーザーメニューを開いて「プリファレンス」「パスワードの変更」「ログアウト」の順に並び、選ぶと読み込み直しなしでそれぞれの画面が出る（AC5.1.9、W1）
- [ ] 単体のコマンドを実行して通す。続けて U7 の範囲のカバレッジを見て、行 80%・分岐 70% に届かない U7 の新しいファイルがあればテストを足す
- [ ] 対応: NFR2.1・NFR6.4・NFR6.5・NFR7.2・NFR7.5・NFR8.1・NFR9.1〜NFR9.3・NFR9.7、部品 7節、AC4.1.1・AC4.1.4・AC4.1.7・AC4.1.8・AC4.1.10〜AC4.1.12・AC5.1.3・AC5.1.6〜AC5.1.9、CR1.1・CR1.4・CR6.1〜CR6.4・CR6.6・CR6.9

### Step 15: 実際のブラウザの検査と測り（080 と e2e/support）— 実装

- [ ] `frontend/e2e/support/preferencesFixtures.ts`: `import type { Preferences }`（`src/features/preferences/preferencesApi.ts`）を付けた `preferencesSample(theme, fontSize)`（氏名は固定のテストの値 `検査 太郎`、言語 `ja`）と `hasPreferencesShape(body)`（項目の名前と値の型、3つの列挙が決めた値の中か。値は返さない）。見本はこのファイルの1つだけ（`project.md` の Corrections）
- [ ] `frontend/e2e/support/registeredUser.ts`（9節の決定 3）:
  - `registrationPrerequisites(request, token)`: U5 の `readInvitationList` で `invitationEnabled` を読み、Node の組み込みの `fetch` で Mailpit の `GET /api/v1/info` に届くかを確かめる（`support/mailpit.ts` の `MAILPIT_API_URL`）。どちらかが使えないときは理由の種類だけを返す
  - `createRegisteredUser(request, runTag)`: U5 の `requestAdminAccessToken` で初期管理者のアクセストークンを取り、招待の API（`POST /api/admin/invitations`、宛先 `u7-perf-<印>@example.com`・言語 `ja`）で1件を置き 201 と `sendResult` SENT を確かめる → U6 の `findInvitationLink(宛先)` でリンクを取り出し、`readRegistrationToken`（`src/features/registration/registrationToken.ts`）でトークンを取る → 登録の完了の API（`POST /api/registration/complete`、`CompleteRegistrationRequest` の型付きの7項目。氏名は架空の固定の値、パスワードは `randomBytes` から作る `e2e-u7-pw-<16進>`（12 コードポイント以上 72 バイト以下）、`ja`・`light`・`md`）で 204 を確かめる → 宛先とパスワードを返す
  - `newRunPassword()`: パスワードの変更の測りで交互に使う2つ目のパスワードを同じ形で作る
  - `loginWithForm(page, email, password)`: ログインの画面のフォーム（`login-form-email-input`・`login-form-password-input`・`login-form-submit-button`）でログインし、`home-page` が見えるまで待つ（`adminLogin.ts` の `loginAsAdmin` と同じ道。`adminLogin.ts` は変えない）
  - トークン・パスワード・アクセストークン・宛先は戻り値とテストの変数だけに持ち、失敗の知らせ・`console`・注記・添付・標準出力に出さない（件数と種類だけ）。Mailpit は `GET` だけ（書き込まず消さない、基盤の設計の D7）
- [ ] `frontend/e2e/support/axe.ts`: `PREFERENCES_KNOWN_VIOLATIONS`（4つの状態）と型 `PreferencesAxeState` を足す。当たる名前は Step 16 の最初の実行で確かめる（見込みは `preferences-ready`・`preferences-invalid` が `preferences-save-button`、`password-ready`・`password-invalid` が `preferences-password-submit-button`）。既存の一覧と `splitKnownViolations` は変えない
- [ ] `frontend/e2e/080-preferences-accessibility.e2e.ts`:
  - 20 組のテスト（組ごとに1つのテスト、題は組の名前だけ）: `prepareCombo`（U4 の手伝い。表示の幅と `/api/appearance` の差し替え）→ `page.route` で `GET /api/me/preferences` だけを `preferencesSample(組のテーマ, 組の文字の大きさ)` の 200 に差し替える（PUT・POST は差し替えない。9節の決定 1）→ `watchPage` と `/api/me/` の要求のメソッドの記録を張る → `loginAsAdmin`（U5 の手伝い）→ ユーザーメニュー（氏名のボタン）を開いて「プリファレンス」→ フォーム（「保存する」）が出て `expectComboApplied`（U4 の手伝い。違えば結果を使わずに失敗）→ (1) `preferences-ready`: axe と横のはみ出し → (2) `preferences-invalid`: 氏名を空にして「保存する」、フォーカスが氏名に移るのを待って axe と横のはみ出し → ユーザーメニューの「パスワードの変更」で移り `expectComboApplied` → (3) `password-ready`: axe と横のはみ出し → (4) `password-invalid`: 3つを空のまま「変更する」、フォーカスが今のパスワードに移るのを待って axe と横のはみ出し → `/api/me/` への PUT・POST が0件、`GET /api/me/preferences` の差し替えが使われたこと、CSP の違反と画面の問題が0件
  - 測りのテスト1件（`performance-design.md` 4節）: 初期管理者のアクセストークンで `registrationPrerequisites` を確かめ、使えなければ理由を注記に残して `test.skip`（Build and Test で `Unverified`）→ `createRegisteredUser` → (開く) 画面ごとに5回、`browser.newContext()`（`baseURL`・`viewport`・`locale: 'ja-JP'`、キャッシュが空）→ `watchPage` → `loginWithForm` → ユーザーメニューを開く（時間に入れない）→ 項目を押す直前から「保存する」または「変更する」が見えるまで → 1回目の開くのプリファレンスでは本物の `GET /api/me/preferences` の応答を `page.waitForResponse` で受け、`hasPreferencesShape` が真であることを確かめる（値は比べない）→ (保存) 1つのコンテキストでプリファレンスの画面を開き、テーマを回ごとに `light`・`dark` で交互に選び、「保存する」を押す直前から Toast「保存しました」が見えるまでを5回（前の回の Toast が消えてから次の回を始めるか、Toast の数で判定する）→ (パスワードの変更) 同じコンテキストでパスワードの変更の画面を開き、今と新しいパスワードを回ごとに入れ替えて、「変更する」を押す直前から Toast「パスワードを変更しました」が見えるまでを5回（1回ずつ順に送る）→ 各コンテキストで CSP の違反と画面の問題が0件（失敗の条件）。時間では失敗させず、場面ごとの5回の値（ミリ秒）と目標以内の回数（2,000・1,500・2,500）を注記と添付（JSON）に残す
  - 各状態の記録（組・画面・状態・想定外の違反の件数と規則の名前・既知の違反・`incomplete` の規則の名前と件数・はみ出し）を注記と添付（JSON）に残す。U4・U5 の手伝い（`runAxe`・`missingRequiredRules`・`splitKnownViolations` の第3引数・`measureHorizontalOverflow`・`watchPage`）を使う
  - `test.step` の題・注記・添付・失敗の知らせに、初期管理者のメールアドレスとパスワード・作った利用者の宛先とパスワード・アクセストークン・招待のトークンとリンクを入れない（`security-design.md` 6節）。差し替えの答えの氏名は `検査 太郎` だけ
  - 既存の 010〜070・090 は変えない。流れの E2E ではないため本数に数えない（NFR9.9、`project.md` の学び）。測りが置く招待・利用者・監査・Mailpit の1通は後の 090 が頼らない（090 は自分で前提を作る）
- [ ] 対応: NFR6.1〜NFR6.3・NFR7.1・NFR7.3・NFR7.4・NFR9.8・NFR9.9、NFR 設計の Q1 A・Q2 A・Q3 A、`security-design.md` 6節、`project.md` の Corrections（見本と本物の応答の形の照合）

### Step 16: 実際のブラウザの検査と測り — 実行と確かめ

- [ ] `./gradlew :backend:bootWar` で WAR を作り、`docker compose --profile mail up -d mailpit` で Mailpit を起動して（基盤の設計の N2）、`(cd frontend && caffeinate -i npx playwright test e2e/080-preferences-accessibility.e2e.ts)` で 080 だけを流す（`unit-test-instructions.md` 2節）
- [ ] 1回目の結果で、green・orange の組の各状態で当たった `color-contrast` の違反の名前（`data-testid`）が、U7 の primary の Button（「保存する」「変更する」）だけかを確かめる。U5 の決定 4 の形のとおり、`PREFERENCES_KNOWN_VIOLATIONS` に書いて流し直す。primary の Button 以外（Alert・ラジオ・アプリシェル・Toast など）の違反や、blue・purple の組の違反が出たときは、U7 のコード（CSS・部品の選び方）で直せるかを見て、直せるなら直して流し直す。直すのに make-you-chic-ui の変更が要るときは、生成を止めて依頼者に諮る（9節の決定 5 の (b)）
- [ ] 375px の組で、文書の横のはみ出しが無いこと（1列に積まれ、ラジオの選択肢が折り返す）を確かめる。はみ出しが make-you-chic-ui・`ShellLayout` に因り U7 のコードで直せないときは、止めて諮る（9節の決定 5 の (c)）
- [ ] 20 組の結果（画面・状態ごとの成否・違反の件数・既知の違反・`incomplete`）、CSP の違反の件数、PUT・POST が0件であること、080 の時間（検査の部分と測りの部分）を記録する
- [ ] 測りの結果（場面ごとの5回の値と目標以内の回数、本物の応答と見本の形の照合、飛ばされなかったこと）を記録する。目標を超えた回があれば、要求の一覧と時刻で API・画面の塊の読み込み・描画のどこが遅いかを切り分けて記録し、承認の場で依頼者に相談する（目標を緩めない。時間は関門にしない）。測りが飛ばされたときは止めて諮る（9節の決定 5 の (e)。通常の実行では通らない道のため、基盤の設計の N10）
- [ ] `frontend/test-results/e2e-results.json` を値の文字列で検索し、`u7-perf-`・`e2e-u7-pw-`・`#token=`・`token=`・`eyJ`・初期管理者のメールアドレスが入らないことと、`test.step` の題が json の `steps` に入るかを記録する（基盤の設計の N9）。報告の部品 `playwright-secret-check-reporter.ts` の確かめも通ること。結果のファイルはコミット・共有しない
- [ ] 080 を2回続けて流し、結果（組・画面・状態の成否と、測りが通ること）が同じであることを確かめる（不安定な検査を残さない、`team.md` の Testing Posture）
- [ ] Mailpit は止めず消さない（Step 20 でも使う。README の手順のとおり開発者が片付ける）
- [ ] 対応: NFR6.1〜NFR6.3・NFR7.3・NFR7.4・NFR9.9、基盤の設計の N2・N9・N10、NFR 要件の承認の場の U7 R-01（測る場所）

### Step 17: ビルドの成果物の確かめ（CSP・埋め込み・依存・大きさの前後）

- [ ] `backend/src/main/resources/application.yaml` の CSP と `backend/src/main/java/cherry/mastersmith/config/SecurityConfig.java` に差分が無いこと（`git diff --stat` に出ない）を確かめる
- [ ] ビルドした `frontend/dist/index.html` に埋め込みのスクリプト・スタイルが無いこと（`<script>` は `src` つきだけ、`<style>` なし）と、`frontend/src/features/preferences/` に外部の URL（`http:`・`https:` の値。ライセンスの URL を除く）が無いことを確かめる
- [ ] `frontend/package.json`・`frontend/package-lock.json` に差分が無いこと（NFR9.10）と、`vendor/make-you-chic-ui` の固定先と中身に差分が無いこと（`git submodule status`・`./gradlew vendorUnchanged`）を確かめる
- [ ] 変更の後の値を記録し、Step 1 と比べる: 初回の JavaScript（gzip、500 KB の目安の警告の有無）、2つの画面の塊が入口と別のファイルに出ること（`dist/.vite/manifest.json` で `src/features/preferences/PreferencesPage.tsx`・`PasswordChangePage.tsx` が動的な読み込みの塊で、入口から静的にたどれないこと）とその塊の大きさ（圧縮前と gzip）、`dist/assets/` の JavaScript の合計、WAR の大きさ（NFR6.6、`performance-design.md` 5節）
- [ ] 対応: NFR6.6・NFR9.10、`infrastructure-specification.md` の CSP と配信物の大きさ

### Step 18: 静的検査とカバレッジ

- [ ] `./gradlew frontendFormatCheck frontendLint frontendLintCss frontendLicenseCheck frontendTypecheck` を通す（Prettier・oxlint・ESLint（`react-hooks`）・Stylelint・ライセンスヘッダー・`tsc`。`frontend/e2e/` の新しいファイルも対象。見本と `preferencesApi.ts` の型の食い違いはここで止まる）。リンタの決まり（`react/no-danger` など）を緩めない
- [ ] `./gradlew frontendCoverage` でフロントエンドのカバレッジの下限（行 80%・分岐 70%）を満たすことを確かめ、全体の値と U7 の新しいファイルと骨組みの変えたファイルの値を記録する。計測の除外を増やさない（`frontend/e2e/` は既存どおり計測の対象外。NFR9.5）。届かないときはテストを足し、それでも届かなければ生成を止めて諮る（9節の決定 5 の (d)）
- [ ] 対応: NFR9.5、`team.md` の Code Style・Testing Posture

### Step 19: 文書（README・features/README）

- [ ] `README.md` に新しい節「プリファレンスとパスワードの変更の画面（U7）」を足す: 画面の置き場と開き方（ユーザーメニューの「プリファレンス」「パスワードの変更」、URL `/me/preferences`・`/me/password`、ログインが要る、サイドバーには無い）、開いた時点のそろえ（内部DB の値が正で、ほかの PC で保存した値に合わせて言語やテーマが変わることがある）、選んだ時点の見せ方（テーマと文字の大きさは選ぶと画面に当たり、言語は保存で切り替わる、「元に戻す」と離れると戻る）、保存の後（ユーザーメニューの名前・言語・Toast）、パスワードの変更（ログインしたまま、ほかの端末もそのまま、今のパスワードの誤りの表示）、画面の確かめは共用の関数（`frontend/src/shared/validation/`）でサーバーが正、項目ごとの誤り（`fieldErrors`）の受け方、見せ方の最中のトークンの更新（Step 14 の確かめの結果）、ブランドカラーの既知の制約が U7 の primary のボタンにも当たること
- [ ] 「ビルドした WAR での画面の確認（E2E）」の表に `080-preferences-accessibility.e2e.ts`（プリファレンスとパスワードの変更の画面の 20 組 × 2 画面 × 2 状態のアクセシビリティの検査と横のはみ出し、画面を開く・保存・パスワードの変更の時間の測定（5回ずつ））の行を足し、「080 について」を書く（流れの本数に数えない、組は `GET /api/me/preferences` と `/api/appearance` の答えの差し替えで当てる（9節の決定 1）、PUT・POST は検査では送らない、測りは招待から利用者を1人作り招待とパスワードの変更の監査を一時の内部DB に残し Mailpit に1通届く、時間は記録だけで失敗させない、080 だけを流すときも Mailpit の起動が要る、片付けは既存の「手元でメールを見る」の2行）。060 の節の「後の単位（U6・U7）の検査も同じ当て方を使います」を、U6（ログインの前、U4 の鍵）と U7（プリファレンスの答えの差し替え）の実際に合わせて直す
- [ ] 「画面の表示の設定（U4）」の「既知の制約（ブランドカラーのコントラスト）」の節に、U7 の画面（「保存する」「変更する」。当たる名前は Step 16 で確かめたもの）も当たることと、080 の検査が状態ごとの一覧（`PREFERENCES_KNOWN_VIOLATIONS`）でこれを既知の違反として扱うことを書く。「050・060・070 の検査は」を「050〜080 の検査は」に直す
- [ ] 「後の単位（U2・U3・U4）が使う差し込み口」の「画面の差し込み口」の行に、ユーザーメニューの項目は `action`（操作）か `path`（登録済みの画面の URL）のどちらか一方を持つことを足す
- [ ] `frontend/src/features/README.md` に「ユーザーメニューの項目で画面へ移る（`path`）」の節を足す: 書き方、登録の検査（どちらか一方、登録済みの URL だけ、外の URL は起動を止める）、`ShellLayout` がサイドバーと同じく読み込み直しなしで移ること
- [ ] 対応: `cicd-pipeline.md` 3.4、基盤の設計の承認の場の U7 R-01・N2・D7、機能設計 9節

### Step 20: E2E の全件（統合の前。`team.md` の Testing Posture）

- [ ] Mailpit を起動したまま（止まっていれば `docker compose --profile mail up -d mailpit`）`caffeinate -i ./gradlew e2eTest` を流し、既存の 010〜040 の 6 件・050 の 21 件・060 の 21 件・070 の 20 件・080 の 21 件・090 の 1 件がすべて通ることを確かめる。骨組みの変更（ユーザーメニュー）の後に既存の E2E（特にユーザーメニューの「ログアウト」を使う 020・030・040・090）が変えずに通ることを記録する（NFR9.4・NFR9.8）
- [ ] `e2eTest` の全体の時間と、検査の部分（050・060・070・080 の検査）と測りの部分（080 の測り）と流れの部分（010〜040・090）の時間を分けて記録する。080 の測りの値を Step 16 の値と並べて記録する。Mailpit は止めず消さない
- [ ] 対応: NFR9.8・NFR9.9、`team.md` の Testing Posture（画面・認証に関わる変更を統合する前に E2E）

### Step 21: 1コマンドの検査（統合の前の関門）

- [ ] colima を動かし、README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡して `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を実行し、すべての段が通り、対象DB のテストが SKIPPED でないことを確かめる
- [ ] テストの件数（バックエンド・フロントエンド）と、フロントエンドの全体のカバレッジと、バックエンドの全体のカバレッジ（変わらない見込み）を実測の数字で記録し、Step 1 と比べる（既存のテストが減っていない・失敗していない）
- [ ] 秘密情報の検出（Gitleaks）と依存関係の脆弱性検査（OSV-Scanner）が通ることを確かめる（lockfile を変えないため OSV-Scanner は UP-TO-DATE になりうる。依存を変えていないことを Step 17 で確かめているため、UP-TO-DATE のときはその旨を記録する）。`vendorUnchanged` が通ることを確かめる
- [ ] 対応: B5 の共通の完了の条件、`project.md` の Mandated（統合の前の確認）

### Step 22: 記録とコミットの提案

- [ ] `code-summary.md`（作ったもの、上流と計画との差、Step 1・Step 21 の実測、Step 2 の前提の確かめ（固定先・make-you-chic-ui の口・U4 の口・骨組みの今の形・U2 の API の形・飛ばす道の前提）、Step 5 の `shared/validation` と `errorMessages.ts` の最終の突き合わせ、Step 14 の見せ方の最中のログイン状態の更新の確かめ、Step 16・20 の 080 の結果と時間と json の確かめ、Step 17 の成果物の確かめと大きさの前後、9節の決定の反映、既知の違反の一覧に足した名前、B5 の3つの単位がそろったこと）、`source-manifest.json`（作った・変えたアプリのソースのすべてのパス）、`traceability.json`（機能設計の OK と NFR の枝番から手順と部品へ。Deferred・N/A は機能設計の分け方にそろえる）を作る
- [ ] 3節の C1〜C6 の区切りで、コミットの内容とメッセージの案を依頼者に示して承認を得る（生成の担当はコミットしない）
- [ ] 対応: 段の記録、`project.md` の Change Control

## 6. ストーリー・要件と手順の対応

| ストーリー・要件 | 受け入れ基準・決まり・NFR | 手順 |
|---|---|---|
| US4.1 自分の表示の設定と氏名を変える | AC4.1.1（W1〜W3、D3、今の値と system の選択） | Step 7・8・11〜14 |
| US4.1 | AC4.1.4（W8、保存で en の文言と `<html lang>`） | Step 13・14 |
| US4.1 | AC4.1.7（W7・W11・W12、画面の確かめと項目ごとの誤り、254 ちょうどは送る） | Step 5・6・13・14 |
| US4.1 | AC4.1.8（W8、保存の直後のユーザーメニューの名前） | Step 13・14 |
| US4.1 | AC4.1.10（W3、初期値の利用者） | Step 13・14 |
| US4.1 | AC4.1.11（W4〜W6、選んだ時点の見せ方、言語は保存で、離れると戻る） | Step 13・14 |
| US4.1 | AC4.1.12（W8、D10・D12、新しい言語の Toast とフォーカス） | Step 13・14 |
| US4.1 | AC4.1.2・AC4.1.3・AC4.1.5・AC4.1.6・AC4.1.9（u4-display-foundation）、AC4.1.13（u2-user-preferences） | Deferred（機能設計の網羅の記録のとおり） |
| US5.1 自分のパスワードを変える | AC5.1.3（W11、AC3.2.4 と同じ境界） | Step 5・6・14 |
| US5.1 | AC5.1.6（W10、今のパスワードの誤りでログインしたまま、更新の流れに乗らない） | Step 7・8・13・14 |
| US5.1 | AC5.1.7（W10・W12、今のパスワードの項目への結び付けと値の保持） | Step 11〜14 |
| US5.1 | AC5.1.8（W10、Toast・3つを空・ログインしたまま） | Step 13・14・15 |
| US5.1 | AC5.1.9（W1、ユーザーメニューからパスワードの変更の画面を開く） | Step 9・10・13〜16・20 |
| US5.1 | AC5.1.1・AC5.1.2・AC5.1.4・AC5.1.5 | Deferred（u2-user-preferences） |
| CR1 言語の適用 | CR1.1・CR1.4（W13、文言を画面の言語で、ja・en の両方） | Step 3・11〜14 |
| CR6 画面の共通の決まり | CR6・CR6.1〜CR6.4・CR6.6・CR6.9（D9〜D11・D14） | Step 11〜14 |
| CR6 | CR6.5・CR6.7・CR6.8 | N/A（U7 の画面に当たる状態・操作・表示が無い） |
| 骨組み（FR10.2） | 機能設計 9節、ユーザーメニューの `path` の項目、外の URL を作らない | Step 9・10・20 |
| 個人に関する値 | NFR2.1 | Step 13・14 |
| 性能 | NFR6.1〜NFR6.3（Step 15・16・20 で記録、Build and Test に引き継ぐ）、NFR6.4（Step 7・8・13・14）、NFR6.5（Step 13・14）、NFR6.6（Step 1・13・17） | Step 1・7・8・13〜17・20 |
| アクセシビリティ | NFR7.1・NFR7.2・NFR7.5（Step 11〜14）、NFR7.3・NFR7.4（Step 15・16・20） | Step 11〜16・20 |
| 多言語 | NFR8.1・NFR8.2 | Step 3・5・6・11〜14 |
| 画面の側のセキュリティ | NFR9.1（Step 7・11〜14）、NFR9.2（Step 5〜8・11〜14）、NFR9.3（Step 7・8・13・14）、NFR9.4（Step 9・10・20） | Step 5〜14・20 |
| テストと依存 | NFR9.5（Step 18・21）・NFR9.6（Step 6）・NFR9.7（Step 6・14）・NFR9.8（Step 10・20・21）・NFR9.9（Step 15・16）・NFR9.10（Step 2・17・21） | Step 2・4〜6・10・14〜18・20・21 |

## 7. テストの量（Standard）

部品ごとに 5〜8 件の単体テストと、境界の確かめ（ApiClient と `fetch`、U4 の口とログイン状態、make-you-chic-ui の部品との結び目、骨組み、実際のブラウザの検査と測り）を置く。成功の場合と、少なくとも2つの失敗・境目の場合を含める（`phases/construction.md` の Testing Standards）。画面部品ごとに vitest-axe の検査を1件入れる（`team.md` の Testing Posture）。

| 部品 | 単体（Vitest） | 実際のブラウザ（Playwright） |
|---|---|---|
| 項目ごとの誤りの読み取り（`fieldErrors.ts`） | `fieldErrors.test.ts` 6 件＋性質ベース 2 件 | — |
| 文言の鍵の選び方（`errorMessages.ts`） | `errorMessages.test.ts` 5 件 | — |
| 画面の確かめ（`formChecks.ts`） | `formChecks.test.ts` 7 件 | — |
| API（`preferencesApi.ts`） | `preferencesApi.test.ts` 8 件 | 080 の測り（本物の応答の形の照合） |
| 登録の検査（`validateRegistrations.ts`、足す分） | `validateRegistrations.test.ts` に 6 件 | — |
| `ShellLayout`（足す分） | `ShellLayout.test.tsx` に 4 件（vitest-axe を含む） | 080（ユーザーメニューで2つの画面へ移る） |
| ユーザーメニューの並び（足す分） | `navigationItems.test.ts` に 1 件 | — |
| `PreferencesLoadFailure` | `PreferencesLoadFailure.test.tsx` 4 件（vitest-axe を含む） | — |
| `PreferencesForm` | `PreferencesForm.test.tsx` 8 件（vitest-axe を含む） | — |
| `PasswordChangeForm` | `PasswordChangeForm.test.tsx` 6 件（vitest-axe を含む） | — |
| `PreferencesPage` と `usePreferencesForm` | `PreferencesPage.test.tsx` 22〜28 件（読み込み 4・そろえ 2・見せ方 3・保存の成功 3・画面の確かめ 2・サーバーの誤り 4・送信中 2・離れた後 1・ログイン状態の更新 1・漏えい 1・axe 1。応答ごとの動きが多いため Standard の目安を超える） | 080（`preferences-ready`・`preferences-invalid`）、測り（開く・保存） |
| `PasswordChangePage` と `usePasswordChangeForm` | `PasswordChangePage.test.tsx` 12〜16 件（開く 1・画面の確かめ 3・成功 1・今のパスワードの誤り 2・ほかの失敗と送信中 3・漏えい 2・axe 1） | 080（`password-ready`・`password-invalid`）、測り（開く・パスワードの変更） |
| 機能の登録と文言（`registration.ts`・`messages.ts`） | `registration.test.tsx` 6 件 | — |
| 実際のブラウザの検査と測り（`080-preferences-accessibility.e2e.ts`） | — | 20 組のテスト 20 件（1組に2画面×2状態）と測り 1 件 |

この Intent の代表の流れの E2E は U6 の 090 の1本だけで、U7 は足さない。080 は流れではないため本数に数えない（NFR9.9）。

## 8. この計画で決めたこと・承認済みの文書との差

承認済みの文書は書き換えず、差をここと `code-summary.md` に記録する（`project.md` の決まり）。

| 対象 | 承認済みの形 | この計画での扱い | 理由 |
|---|---|---|---|
| 層の順番 | Testing Contract の `plan_profile.steps` は「データの形 → DB アクセス → 業務処理 → API → 画面の振る舞い」 | データの形（DB）とデータアクセスの層は無し。業務処理（画面の純粋な関数）→ API → 画面の振る舞い（骨組みの変更 → 画面部品 → 画面と状態と登録）→ 080 の順 | U7 は内部DB とブラウザの保存を持たない。機能の登録が骨組みの新しい型と検査を前提にするため、骨組みを先に置く（test-after の方法は変えない） |
| 検査のファイルの番号 | 「`050-` の後の番号、例 `05x-preferences-accessibility.e2e.ts`、B5 の計画でそろえる」（`logical-components.md` 5.1、`cicd-pipeline.md` 3.1） | `080-preferences-accessibility.e2e.ts` | U5 の計画の決定 2 で決まった（承認の場の U6 R-02・U7 R-02、基盤の設計の C-D5） |
| 080 の組の当て方 | NFR 設計 5.3（Q2 A）は `GET /api/me/preferences` と `/api/appearance` の差し替え。README の 060 の節は「後の単位（U6・U7）も `loginPreferences.ts` の同じ当て方を使う」 | NFR 設計のとおり `GET /api/me/preferences`（`preferencesFixtures.ts` の見本）と `/api/appearance`（U4 の `prepareCombo`）の差し替えだけで当て、`loginPreferences.ts` は使わない。README の 060 の節の文を直す（9節の決定 1） | プリファレンスの画面は開いた時点に内部DB の値でそろえる（D2）ため、ログインの応答だけを書き換えると、本物の `GET` の値で組が外れる |
| Mailpit を片付けるコマンドの書き方 | 基盤の設計 `cicd-pipeline.md` 3.4 は `docker compose --profile mail stop mailpit`・`docker compose --profile mail rm -f mailpit` | README の「手元でメールを見る」の2行（`docker compose stop mailpit`・`docker compose rm -f mailpit`）の既存の1か所の書き方に従い、U7 は片付けの文を新しく足さない | U5 の計画の決定 5（基盤の設計の承認の場の U7 R-01 を閉じる） |
| 画面の確かめの置き場 | 部品 2節のモジュールの一覧に無い | `formChecks.ts`（`checkPreferencesForm`・`checkPasswordChangeForm`）を足す | 共用の関数の呼び出しと最初の誤りの項目の決め方を、フックから外した純粋な関数にして単体で確かめるため（U6 の `formProblems.ts` と同じ形）。決まりは共用の関数だけに置く（D7） |
| 形の誤りの失敗の種類 | 部品 5節「`ApiError` とは別の失敗の種類（形の誤り）」 | `preferencesApi.ts` の `PreferencesShapeError` | 名前は「コード生成で決める」とされたもの |
| 単体テストでの API の差し替え | 部品 7節・機能設計 8節「API は `preferencesApi.ts` の関数を差し替える」 | 画面とフックのテストは `fetch` を差し替え（偽のサーバー）、`preferencesApi.ts`・ApiClient・U4 の口・ログイン状態は本物で動かす（9節の決定 2）。口の呼ばれ方だけを見るテストは口を差し替える（機能設計 8節のとおり） | NFR9.3（今のパスワードの誤りでトークンの更新が呼ばれない）と NFR6.4・NFR6.5（要求の回数）を、ApiClient の実際の動きで確かめるため。U5・U6 の画面のテストと同じ形 |
| 画面部品ごとの vitest-axe | NFR7.2 は `PreferencesPage`・`PasswordChangePage` に1件ずつ | 加えて `PreferencesLoadFailure`・`PreferencesForm`・`PasswordChangeForm` と `ShellLayout` の `path` の項目にも1件ずつ | `team.md` の「画面部品ごとに1件」のとおり。追加で、食い違いではない |
| テストの描画の支え | 部品 7節「U4 の土台は `renderWithProviders` で本物を使う」 | `testing/renderPreferences.tsx` で `renderWithProviders` を `ToastProvider` で包む（`renderWithProviders` は変えない） | `renderWithProviders` が `ToastProvider` を持たないため（U5 の `renderInvitation.tsx` と同じ形） |
| 共用の手伝いの名前と形 | NFR 設計「プリファレンスの答えの差し替えと、測りの利用者の用意の関数を足す。形と置き場は B5 の計画で決める」、基盤の設計「利用者を作る関数は U6 の E2E-1 と同じもの」 | `support/preferencesFixtures.ts`（見本と形の照合）と `support/registeredUser.ts`（招待から利用者を作る・フォームでログインする・前提の確かめ）。招待とトークンの取得は U5 の `invitationSeed.ts`、リンクの取り出しは U6 の `mailpit.ts` の `findInvitationLink` を使う（9節の決定 3） | U6 の E2E-1 は画面で登録を完了するため、API で完了する関数は U6 に無い（U6 の決定 4）。リンクの取り出しの部分が E2E-1 と同じ関数になる |
| 見本と本物の応答の形の照合 | NFR 設計 5.3「本物の応答の形の確かめは、画面部品のテスト・U2 のサーバー側のテスト・測り（本物の応答）で行う」 | 080 の測りの1回目の開くで、本物の `GET /api/me/preferences` の応答と見本の形を `hasPreferencesShape` で毎回確かめる | `project.md` の Corrections（見本を1つにして型を付け、本物と毎回照合する）。U7 に流れの E2E は無いため、本物の応答を使う測りで行う（U5 の 060 と同じ） |
| 保存の測りの Toast の待ち方 | `performance-design.md` 4.3「Toast が見えるまで」 | 前の回の Toast が消えてから次の回を始めるか、Toast の数で判定する（連続の5回で前の Toast を拾わないため） | 作りの細部 |
| `PreferencesPage.test.tsx` の件数 | Standard の目安は部品ごとに 5〜8 件 | 22〜28 件 | 応答ごとの動き（6節）と漏えいの確かめを1つの画面で確かめるため。目安を超える側で、減らさない |
| 見せ方の最中のログイン状態の更新 | 機能設計 10節の (f)「コード生成で実際に起きるかを確かめる」 | 単体テストで確かめて結果を記録し、U4 は変えない（9節の決定 4） | 保存・「元に戻す」・次の選択で解けるため |

## 9. 依頼者の決定

計画の承認の前に諮った論点について、依頼者がすべて推奨のとおり（A）に決めた。計画の各 Step と8節はこの決定に合わせてある。

1. **080 のログインの後の組の当て方**: A。NFR 設計の Q2 A のとおり、`GET /api/me/preferences`（`preferencesFixtures.ts` の見本）と `/api/appearance`（U4 の `prepareCombo`）の答えだけを差し替え、画面の D2 のそろえ（本物の `applyUserPreferences`）で組を当てる。`loginPreferences.ts` は使わない。README の 060 の節の「後の単位（U6・U7）も同じ当て方」の引き継ぎの文を、実際の当て方に合わせて直す（Step 19）。D2 の本物の道を検査のたびに通せ、差し替えの範囲も狭い。ログインの直後のホームは初期管理者の本物の設定で出るが、検査しない。選ばなかった B は、`loginPreferences.ts` と `GET /api/me/preferences` の差し替えを両方使う案。`loginPreferences.ts` だけでは、プリファレンスの画面を開いたときに D2 が本物の `GET` の値で組を外すため、候補にしなかった（Step 15・16）。
2. **画面とフックの単体テストでの API の差し替えの深さ**: A。`fetch` を差し替える偽のサーバーで、`preferencesApi.ts`・ApiClient・U4 の口・ログイン状態を本物で動かす（U5・U6 と同じ形）。今のパスワードの誤りでトークンの更新が送られないこと（NFR9.3）や要求の回数（NFR6.4・NFR6.5）を実際の ApiClient で確かめる。機能設計 8節・部品 7節の「`preferencesApi.ts` の関数を差し替える」との差として、8節と `code-summary.md` に記録する。口の呼ばれ方だけを見るテストは口を差し替える（機能設計 8節のとおり）（Step 12・14）。
3. **「招待から利用者を作る」手伝い（U6 の決定 4 の持ち越し）の置き場と形**: A。新しい `frontend/e2e/support/registeredUser.ts` に、`createRegisteredUser`（招待の API → U6 の `findInvitationLink` → 登録の完了の API）、`newRunPassword`、`loginWithForm`（フォームでのログイン）、`registrationPrerequisites`（`invitationEnabled` と Mailpit に届くかの確かめ）を置く。招待とトークンの取得は U5 の `invitationSeed.ts` を使う。既存の手伝い（`adminLogin.ts`・`invitationSeed.ts`・`mailpit.ts`・`loginPreferences.ts` など）は変えない（Step 15）。
4. **見せ方の最中のログイン状態の更新（機能設計 10節の (f)）**: A。Step 14 の単体テストで、見せ方の最中にログイン状態が新しくなったときの画面とフォームの値を確かめ、結果を `code-summary.md` と README に記録するだけにする。食い違いが起きても生成は止めず、U4 は変えない（保存・「元に戻す」・次の選択で解ける）（Step 14・19・22）。
5. **生成を止めて諮る場面**: A（(a)〜(g) のとおり）。次のときは、生成をその手順で止め、結果と候補を示して依頼者に諮る。
   - (a) Step 2 で、U7 が使う make-you-chic-ui・U4・U2・U6 の口が無い・形が違う、または `ShellLayout` に U4 の B4 の変更が入っていない
   - (b) Step 16 で、U7 の画面に、既知の違反（green・orange の組の primary の Button）の外の axe の違反が出て、U7 のコード（CSS・部品の選び方）で直せない、または直すのに make-you-chic-ui の変更が要る
   - (c) Step 16 で、375px の組の横のはみ出しが make-you-chic-ui・`ShellLayout` に因り、U7 のコードで直せない
   - (d) Step 18 でテストを足してもフロントエンドのカバレッジの下限（行 80%・分岐 70%）に届かない
   - (e) Step 16 で、080 の測りが飛ばされた（招待を使えない・Mailpit に届かない。通常の実行では通らない道）、本物の `GET /api/me/preferences` の応答と見本の形が一致しない、または json の結果に秘密・個人に関する値が入り手伝いの側で防げない
   - (f) 8節に挙げたもの以外に、既存の画面のテスト・既存の E2E（010〜070・090）の期待や、U7 の範囲の外（ApiClient・`display-settings`・`navigationItems.ts`・`renderWithProviders`・ほかの機能・`playwright.config.ts`・サーバー）を変える必要が出る（緩めずに作れる形を先に探す）
   - (g) 9節の決定 4 が B のときに、見せ方の最中のログイン状態の更新で食い違いが起きた（決定 4 は A に決まったため、この場面では止めない。食い違いは Step 14 で記録するだけにする）
   - なお、画面の時間が目標（開く 2 秒・保存 1.5 秒・パスワードの変更 2.5 秒）を超えたときは、止めずに切り分けて記録し、承認の場で相談する（時間は関門にしない）
6. **作業の場と統合の単位**: A（U5 の計画の決定 1 のとおり）。`feature/260925-user-management-b5-u7` を作り、U7 の分を squash で `develop` へ統合する。統合の前に Mailpit を起動して `./gradlew e2eTest` の全件と `./gradlew verify` を通す。コミットは生成の後に依頼者の承認を得て 3節の C1〜C6 でまとめて行う。push は依頼者。

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

Testing Contract の `plan_profile.steps` との対応: 骨組みと本番の設定は Step 1〜3（作業の場 Step 1・前提の確かめ Step 2・型と文言 Step 3）、テストの実行の準備は Step 4（最初のテストの Step 6 より前）、データの形（DB）とデータアクセスの層は U7 に無い（内部DB とブラウザの保存を持たない）、業務処理は Step 5・6（画面の純粋な関数）、API（`preferencesApi`）は Step 7・8、画面の振る舞いは Step 9〜14（骨組みの変更 → 画面部品 → 画面と状態と登録）と実際のブラウザの検査と測り Step 15・16、環境とビルドの確かめは Step 17・18・20・21、文書と記録は Step 19・22。

## Build and Test に引き継ぐこと

| 項目 | 引き継ぐ内容 | 持ち主 |
|---|---|---|
| カバレッジの実測 | `:backend:cleanTest :backend:cleanIntegrationTest` を付けた verify で、フロントエンドの全体と U7 の新しいファイル・骨組みの変えたファイル、バックエンドの全体の値をもう一度実測して記録する | Build and Test |
| verify の時間 | U7 の後の `./gradlew verify` の時間を測り、U6 の後の実測（6分5秒）と比べる | Build and Test |
| 画面の時間（NFR6.1〜NFR6.3） | `./gradlew e2eTest` の 080 の測りの場面ごとの5回の値と目標以内の回数を `frontend/test-results/e2e-results.json` から写して記録する（統合の関門にしない。目標を超えたら切り分けて依頼者に相談、目標を緩めない）。飛ばされたときは `Unverified` と理由。運用の中での判定は `Unverified`。E2E の WAR（PC の上、空の内部DB）と配備したアプリ（コンテナ、使い続けた内部DB）の違いを明記する | Build and Test（記録）・observability-setup・feedback-optimization（運用の判定） |
| 実際のブラウザの検査（NFR7.3・NFR7.4） | 080 の 20 組 × 2 画面 × 2 状態の成否・違反の件数と規則の名前・既知の違反・`incomplete` を json の報告から写して記録する。json に秘密・個人に関する値が入らないことをもう一度確かめる | Build and Test |
| 本物の応答と見本の形 | 080 の測りの `GET /api/me/preferences` の形の照合の結果を記録する | Build and Test |
| e2eTest の時間 | 全体の時間と、検査の部分（050・060・070・080 の検査）・測りの部分（080 の測り）・流れの部分（010〜040・090）を分けて記録する | Build and Test |
| CSP | 080 の各組と測りの各コンテキストで CSP の違反が 0 件であることを記録する | Build and Test |
| 配信物の大きさ（NFR6.6） | コード生成の前後の値（`code-summary.md`）を Build and Test の結果に写す。上限は置かない（500 KB の目安の警告だけ） | Build and Test |
| 自分の設定の API の時間 | 取得・保存 p95 1 秒、パスワードの変更の成功 p95 2 秒・誤り p95 1 秒（U2 の NFR6.1〜NFR6.4、U2 の引き継ぎのとおり k6） | performance-validation（無ければ Build and Test、`project.md` の学び） |
| 見せ方の最中のログイン状態の更新 | Step 14 の確かめの結果（機能設計 10節の (f)）を残る危険・既知の動きの記録に写す | Build and Test（記録） |
| 配備と戻し | イメージを作り直して `docker compose up -d`、ヘルスチェックと既存のスモークテスト。配備の後の確かめで保存・パスワードの変更を行うかと、行うときに先に依頼者に伝えることは配備の段で決める（配備した内部DB の設定と監査ログに残り、パスワードが要る操作は依頼者が行う）。戻しはイメージだけで、U7 はサーバーの状態を変えないため消すものは無い（`cicd-pipeline.md` 5節・6節） | deployment-pipeline（手順）・deployment-execution（実行） |
| B5 の完了の条件 | U5・U6・U7 の3つの画面がそろったこと（画面部品ごとのアクセシビリティの検査、E2E-1、ja・en の文言）を B5 の完了として記録する | Build and Test |
