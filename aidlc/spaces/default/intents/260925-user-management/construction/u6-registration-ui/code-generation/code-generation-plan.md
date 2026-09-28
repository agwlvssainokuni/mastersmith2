# Code Generation Plan — U6 登録の完了の画面（u6-registration-ui）

U6 のコード生成の計画を示す。作るものは、招待のリンク（`/register#token=…`）から開く、ログインなしの単独の登録の完了の画面（新しい置き場 `frontend/src/features/registration/`、画面 S2、`layout: 'STANDALONE'`・`access: 'PUBLIC'`）と、U7 と共用する入力の確かめの関数（新しい置き場 `frontend/src/shared/validation/`）、登録の完了の画面の実際のブラウザのアクセシビリティの検査（`frontend/e2e/070-registration-accessibility.e2e.ts`）、この Intent の代表の流れの E2E（E2E-1、`frontend/e2e/090-invitation-registration-flow.e2e.ts`）と、Mailpit から招待のリンクを取り出す共用の手伝いである。Bolt は B5（U5・U6・U7 の画面、`inception/delivery-planning/bolt-plan.md`）で、U6 はその2番目の単位。B1（U1）・B2（U2）・U8・B3（U3）・B4（U4、make-you-chic-ui の固定先は `735ef04`）・B5 の U5 は `develop` に統合済みで、先頭は `dc21a5a`。U6 は ui の単位で、サーバーのコード・内部DB・Flyway・`vendor/` の中身を変えない。

この計画の中のパスは、特に断らない限りリポジトリのルートからの相対パスで、記録のディレクトリは `aidlc/spaces/default/intents/260925-user-management/`（以下「記録」）とする。既存のコードの説明文にある「U1〜U5」は前の Intent の単位番号のことがあり、この計画の U6 は u6-registration-ui を指す。

## 1. 入力にした設計

| 文書（記録の下） | 使うところ |
|---|---|
| `construction/u6-registration-ui/functional-design/functional-spec.md`・`frontend-components.md`・`functional-design-questions.md`・`traceability.json` | 決まり D1〜D12、状態の移り変わり（3節）、画面の流れ W1〜W13、応答ごとの動き（5節）、確かめの関数の形（6節）、文言（7節）、秘密と個人情報（8節）、テストの方針（9節）、上流との差（10節）、承認の場の直し（11節の1〜6）、部品の階層（部品 1節）、置き場ごとのモジュール（部品 2節）、API との受け渡し（部品 3節）、`useRegistration` の値・操作・副作用（部品 4節）、props と state（部品 5節）、RadioGroup の使い方（部品 6節）、U4 の口（部品 7節）、テストで確かめる内容（部品 8節）、既存への影響（部品 9節）、答え Q1 A・Q2 B・Q3 A・Q4 B、網羅の OK 21 件・Deferred 12 件 |
| `construction/u6-registration-ui/nfr-requirements/` の全文書と `nfr-requirements-questions.md` | NFR1.1〜NFR1.5・NFR2.1・NFR3.1、NFR6.1〜NFR6.3、NFR7.1〜NFR7.5、NFR8.1・NFR8.2、NFR9.1〜NFR9.11、答え Q1 B（リンクを開いてからフォームまで 2 秒、5 回測って記録し関門にしない）・Q2 B（フォームの状態は確かめの API の差し替えで出す） |
| `construction/u6-registration-ui/nfr-design/` の全文書と `nfr-design-questions.md` | 部品と依存の向き・失敗の範囲（`logical-components.md` 1〜3節）、実際のブラウザのファイルと非干渉・見本と照合（同 5節）、測り方と同じ5回の3つの確かめ（`performance-design.md` 2節）、待ちの表示と画面の塊（同 3節）、検査の回数の見込み（同 4節）、トークンの流れと決まり・残る危険（`security-design.md` 1節）、メールアドレスと列挙の防止（同 2節）、呼び出し・想定外の応答・400（同 3節）、パスワード・二重の送信・CSP（同 4節）、差し替えと照合（同 5節）、答え Q1 A（見本1つに型を付け E2E-1 で毎回照合）・Q2 A（400 は機能設計の Q3 A のまま、`fieldErrors` を読まない） |
| `construction/u6-registration-ui/infrastructure-design/` の全文書と `infrastructure-design-questions.md` | 実行の形・配信とキャッシュ・保存を変えない（`infrastructure-specification.md`）、`./gradlew verify` の段と関門・検査のファイル・E2E-1 の流れ・Mailpit からの取り出しの部品・5回の測り・残るものと片付け・統合・配備・戻し（`cicd-pipeline.md`）、SLI と `Unverified` の引き継ぎ（`monitoring-design.md`）、答え Q1 C（E2E-1 は Mailpit のメールを消さず、API に書き込まない）・Q2 A（HTML の報告とトレースに使い捨てのトークンが載ることを NFR1.5 の範囲として受け入れ、注記・添付・知らせ・標準出力・json には出さない） |
| `inception/contract-design/contract-summary.md` の C6・C9 と共通の決まり | 確かめ `POST /api/registration/verify`（200 `email`・`language`、404 `REGISTRATION_LINK_INVALID`）、完了 `POST /api/registration/complete`（204、400 `VALIDATION_FAILED`、404 `REGISTRATION_LINK_INVALID`）、表示の設定の口 |
| `inception/refined-mockups/`（`mockups.md` の S2、`interaction-spec.md` の 5節、`accessibility-checklist.md`、`design-system-mapping.md`） | 画面の配置と部品の動き。承認済みの機能設計が上書きした点（10節の使えないリンクの文と導線、CR6.4 の差）は機能設計を正とする |
| `inception/units-generation/unit-of-work.md`・`unit-of-work-story-map.md` | U6 の責務と境界（API は U3、表示の設定を当てる仕組みは U4）、US3.2 の画面の受け入れ基準、E2E の代表の流れ1本は U6 |
| `inception/requirements-analysis/requirements.md`・`inception/user-stories/stories.md` | FR4・FR7・FR10.3・NFR1・NFR3・NFR7・NFR8、US3.2 の受け入れ基準 AC3.2.1〜AC3.2.18、E2E-1、共通の決まり CR1・CR6 |
| `inception/delivery-planning/bolt-plan.md` の B5 | 完了の条件（3つの画面が画面イメージのとおり動き画面部品ごとにアクセシビリティの検査が通る、E2E の代表の流れ1本「管理者でログイン → 招待 → 手元の受け手でリンクを取り出す → 登録の完了 → 新しい利用者でログイン → 管理画面に入れない → ログアウト」、ja・en の文言） |
| B1〜B4・U8・U5 の `code-summary.md`（`construction/u1-mail/`・`u2-user-preferences/`・`u3-invitation/`・`u4-display-foundation/`・`u8-instance-appearance/`・`u5-invitation-ui/` の `code-generation/`） | U3 の登録の API の実際の形（README の「招待と登録の完了（U3）」と「契約との差」の C6 の `fieldErrors`）、U4 の口（`useDisplaySettings`・`saveBrowserDisplaySettings`・`LANGUAGE_NAMES`・`handOffToLogin`・`TOKENLESS_API_PATHS`）と 050 の手伝い、U5 の引き継ぎ（検査のファイルの番号 070 と代表の流れ 090、共用の手伝い `adminLogin.ts`・`invitationSeed.ts`・`invitationFixtures.ts`・`loginPreferences.ts`、`axe.ts` の `splitKnownViolations` の第3引数、ログインの前の画面は U4 の切り替え方のまま、U5 の `data-testid` は `invitation-` で始まる、060 が招待 21 件とログインの監査を残す）、U5 の実測（フロントエンド 68 ファイル 509 件、行 97.6%・分岐 93.73%、初回の JavaScript 119.2 KB、verify 6分2秒、e2eTest 48 件 1分22秒） |
| 既存のコード | `frontend/src/features/auth/`（`registration.ts`・`authSession.ts` の `logout`・`LoginForm.tsx` の `takeLoginHandoff` と `login-form-registered-alert`）、`frontend/src/app/`（`registry/types.ts`・`registrationModules.ts`・`validateRegistrations.ts`・`routing/decideRoute.ts`・`layout/StandaloneLayout.tsx`・`login-state/LoginStateGate.tsx` の `useLoginState`・`i18n/I18nProvider.tsx` の `useMessages`・`display-settings/`・`login-handoff/loginHandoff.ts`・`testing/renderWithProviders.tsx`・`pages/NotFoundPage.tsx`）、`frontend/src/shared/api-client/`（`apiRequest`・`ApiError`・`TOKENLESS_API_PATHS`）、`frontend/src/features/invitation/`（E2E-1 が通る U5 の画面）、`frontend/e2e/010〜060` と `frontend/e2e/support/`、`frontend/playwright.config.ts`・`playwright-secret-check-reporter.ts`・`vitest.config.ts`・`tsconfig.json`（`include` に `e2e`）・`package.json`、`vendor/make-you-chic-ui` の `735ef04` の Card・FormField・TextInput・Button・Alert・RadioGroup、`backend/src/main/java/cherry/mastersmith/invitation/web/`（`RegistrationController`・`TokenRequest`・`CompleteRequest`・`InvitationViewResponse`）、`backend/src/main/java/cherry/mastersmith/config/WebConfig.java`・`SecurityConfig.java`、`build.gradle.kts` の `e2eTest`（`mailpitInfoUrl`）、`compose.yaml` の `mailpit`、`README.md`（「ビルドした WAR での画面の確認（E2E）」「手元でメールを見る」「招待と登録の完了（U3）」「招待の管理の画面（U5）」「画面の表示の設定（U4）」） |

## 2. 承認の場の決定と、この計画での読み方

### 2.1 承認の場の決定（監査ログから洗い出したもの）

`project.md` の学び（計画の前に承認の場の決定を洗い出す）に従い、監査ログ（`audit/sakura-local-4e42a93f87ce.md`）の Functional Design・NFR Requirements・NFR Design・Infrastructure Design の `DECISION_RECORDED`・`GATE_APPROVED`・`GATE_REJECTED` と、レビューの記録（`.aidlc-reviews/*/units/u6-registration-ui/*/1.json`）の指摘、U5 のコード生成の計画と `code-summary.md` の B5 の決定を読んだ。U6 と B5 に関わるものと、この計画での扱いは次のとおり。

| 段 | 決定・指摘 | この計画での扱い | 手順 |
|---|---|---|---|
| 機能設計（Request Changes、2026-09-27） | make-you-chic-ui の新しい部品（RadioGroup の `legend`・選択肢の `lang`、Button の `aria-disabled` の `loading`）に置き換える。U4 R-01（登録の完了の API を公開のパスに足す）を直す | 自前の RadioFieldset と `frontend/src/shared/ui/` は作らない。3つの選択は RadioGroup に `legend` を渡し、言語の選択肢に `lang`。送信中・ログアウト中は Button の `loading`。計画を書く時点で、固定先が `735ef04` で、RadioGroup の `legend`・`lang`・`value`・`onChange`、Button の `loading`（`aria-disabled`）、Alert の種類ごとの役割（`danger`・`warning` は `alert`、`info` は `status`）、TextInput の `forwardRef` があることと、`TOKENLESS_API_PATHS` に `/api/registration/verify`・`/api/registration/complete` があることを読み取りで確かめた。Step 2 で記録する | Step 2・11 |
| 機能設計のレビュー（U6 R-01 Minor、承認の場で直し済み） | 完了の成功は Toast ではなくログインの画面の Alert で知らせる（CR6.4 との差） | 完了の 204 で Toast を出さない。ログインの画面の `login-form-registered-alert`（U4）が出ることを画面部品のテストと E2E-1 で確かめる | Step 14・17・18 |
| 機能設計（承認の場、G2〜G5） | 網羅の記録のずれは受け入れる。RadioGroup が `aria-describedby` を渡す口を持たない点は受け入れて回避の作り（言語の案内は `legend` の中、テーマと文字の大きさの案内は文字として置く） | そのとおりに作る。make-you-chic-ui への追加の相談はこの単位では行わない | Step 11 |
| NFR 要件（承認の場、Minor 12 件の拾い上げ、U6 分） | U6 の検査の回数・時間の見積もり（R-01）と、差し替えのフォームと本物の応答の形の照合（R-02）。2 状態×20 組で検査が長くなることは受け入れる | 070 の 40 回の検査の時間と `e2eTest` の全体の時間を Step 16・22 で実測して記録する。照合は 090 で毎回行う（NFR 設計の Q1 A）。照合が通ることで R-02 を閉じる | Step 15〜18・22 |
| NFR 設計（承認の場、U6 R-01） | B5 で `handOffToLogin` を確かめる | 計画を書く時点で、`frontend/src/app/login-handoff/loginHandoff.ts` が `handOffToLogin(email: string)`（モジュールの中の変数1つだけに持ち、URL・保存・`history.state` に載せない。パスワード・トークンの口は無い）を出し、`frontend/src/features/auth/LoginForm.tsx` が `takeLoginHandoff`・`clearLoginHandoff` で読み、`login-form-registered-alert` を出すことを読み取りで確かめた。Step 2 で記録し、画面部品のテスト（Step 14）と E2E-1（Step 18）で実際の受け渡しを確かめる | Step 2・14・18 |
| NFR 設計（承認の場、U6 R-02・U7 R-02） | 検査のファイルの番号は B5 でそろえる | U5 の計画の決定 2 のとおり、U6 の検査は `070-registration-accessibility.e2e.ts`、E2E-1 は `090-invitation-registration-flow.e2e.ts`（最後に置き、前のファイルの招待・利用者・監査に頼らない）。080 は U7 | Step 15・17 |
| NFR 設計（承認の場、U7 R-01） | U6 との突き合わせの手順を B5 の最初に決める | B5 の単位の順は U5 → U6 → U7（U5 の決定 2）。U6 の計画が先に確定し、`shared/validation` の名前と理由の union を機能設計の 6節のまま固定する。計画を書く時点で、U7 の NFR 設計 `security-design.md` の 3.3 の表（寄せる理由 `required`・`tooShort`・`tooLong`・`invalidCharacter`・`mismatch`、関数 `validateDisplayName`・`validateNewPassword`・`validatePasswordConfirmation`）が U6 の機能設計の 6節と一致することを読み取りで確かめた。実装の後の最終の突き合わせの記録は U7 の計画に残す（U6 の `code-summary.md` には、作った関数と union の一覧を載せて U7 に渡す） | Step 5・24 |
| NFR 設計（承認の場、上流との差 A9） | 閲覧の履歴（機能設計 W2 の4 の「フラグメントの無いアドレスだけが残る」）は機能設計を書き換えず残る危険として受け入れ、B5 で実際の動きを確かめて記録する | 9節の決定 2 のとおり、生成の中で1回だけ、使い捨てのプロファイルで確かめて結果を記録する（E2E に常設しない）。U7 はリンクをブラウザで開かないため、確かめは U6 の持ち物（U7 の基盤の設計の C-D2） | Step 18、9節 |
| NFR 設計（Q1 A・Q2 A） | 見本1つに型を付け E2E-1 で毎回照合。400 は `fieldErrors` を読まない | そのとおりに作る | Step 7〜10・15・17 |
| 基盤の設計（Q1 C、承認の場の D7） | E2E-1 は Mailpit のメールを消さず、Mailpit の API に書き込まない。開発者が止めて消す | 取り出しの部品は `GET` の API だけを使う。README の片付けの書き方は U5 の決定 5 のとおり「手元でメールを見る」の2行（`docker compose stop mailpit`・`docker compose rm -f mailpit`）にそろえ、基盤の設計の `docker compose rm -sf mailpit` は使わない（8節の差） | Step 17・21 |
| 基盤の設計（Q2 A、承認の場の D8） | HTML の報告と失敗のときのトレースに使い捨てのトークンが載ることを受け入れる。注記・添付・知らせ・標準出力・json には出さない | リンクは `page.goto` のまま開く。`test.step` の題・注記・添付・失敗の知らせに URL・トークン・宛先・パスワードを入れない | Step 17・18 |
| 基盤の設計（承認の場、N2） | 050 だけでも Mailpit が要る（B4・B5） | 070 だけ・090 だけを流すときも Mailpit の起動が要ることを `unit-test-instructions.md` と README に書く | Step 16・18・21 |
| 基盤の設計（承認の場、N7） | Mailpit の API を実物で確かめる（B5） | 取り出しの部品を書く前に、起動した Mailpit で `GET /api/v1/info`・`GET /api/v1/search`・`GET /api/v1/message/{ID}` の応答の項目の名前を、値を出さずに確かめて記録する（基盤の設計の C-D4） | Step 17 |
| 基盤の設計（承認の場、N9） | json の結果に操作の題が入るか（B5） | 090 の実行の後に `frontend/test-results/e2e-results.json` を文字列で検索し、`#token=`・`token=`・宛先の接頭辞・パスワードの接頭辞・`eyJ` が入らないこと、`test.step` の題が `steps` に入るかを記録する（U5 では `test.step` の題だけが入り、`page.goto` の操作は入らなかった） | Step 18 |
| 基盤の設計（C-D5） | Mailpit の保持の上限 | README の「手元でメールを見る」に既に書かれている（既定で最大 500 通、超えると古いものから消える）。E2E-1 は実行ごとに違う宛先で探すため影響しない。追加の確かめはしない | — |
| U5 のコード生成（Step 16 の決定 A と `code-summary.md` の7節） | ログインの後の画面の組は `support/loginPreferences.ts` で当てる。ログインの前の画面（U6 の登録の完了など）は U4 の切り替え方（初めのスクリプトで U4 の鍵を置く）のまま | 070 はログインしない画面のため、U4 の `prepareCombo` だけで組を当てる（`loginPreferences.ts` は使わない）。確かめの 200 で招待の言語 `ja` を当てても、テーマと文字の大きさはブラウザの保存の値（組の値）のまま（D6） | Step 15 |
| U5 のコード生成（決定 4 の形） | 既知の違反は、green・orange の組の `color-contrast` の primary の Button だけを、画面の状態ごとの名前の一覧で扱う | `support/axe.ts` に `REGISTRATION_KNOWN_VIOLATIONS`（`ready`・`unavailable`）を足す。当たる名前は Step 16 の最初の実行で確かめてから書く。050・060 の一覧と判定は変えない | Step 15・16 |
| U5 のコード生成（決定 1） | 単位ごとに短命のブランチを作り、単位ごとに squash で統合する | 3節のとおり | Step 1・3節 |

### 2.2 この計画での読み方

- **設計の文書どおりに作るもの**: 部品の階層（部品 1節）、置き場ごとのモジュール（部品 2節）、API との受け渡し（部品 3節）、`useRegistration` の値・操作・副作用（部品 4節）、props と state（部品 5節）、RadioGroup の使い方（部品 6節）、U4 の口の使い方（部品 7節）、画面の流れと応答ごとの動き（`functional-spec.md` の 3〜5節）、確かめの関数の名前と union と定数（同 6節）、文言（同 7節の鍵と文）、トークンの流れと決まり（`security-design.md` 1節）、差し替えと照合（同 5節）、測り方（`performance-design.md` 2節）、Mailpit からの取り出しの手順（`cicd-pipeline.md` 4.3）。
- **名前を決めるもの**（「コード生成で決める」とされたもの）:
  - 機能の置き場 `frontend/src/features/registration/` のモジュールは部品 2節の名前のまま（`registration.ts`・`registrationApi.ts`・`registrationToken.ts`・`failureKind.ts`・`formProblems.ts`・`useRegistration.ts`、部品の `.tsx` と同じ場所の `.css`）。文言は U5 と同じく `messages.ts`（`registrationMessages`）に分け、`registration.ts` から渡す。文言は埋める値を持たないため、骨組みの `useMessages()` で引く（部品 5節）。テストの補助は `testing/renderRegistration.tsx`（`renderWithProviders` に登録の完了の機能と骨組みの文言を渡して `/register#token=…` から描く）と `testing/fixtures.ts`（確かめの応答・失敗の応答の見本と `fetch` の差し替えの組み立て）。
  - `registrationApi.ts` は `verifyRegistration`・`completeRegistration`・`REGISTRATION_VERIFY_PATH`・`REGISTRATION_COMPLETE_PATH` と、型 `VerifiedInvitation`（`email`・`language`）・`CompleteRegistrationRequest` を名前付きで出す。確かめの応答の形の確かめ（`language` が `ja`・`en`、`email` が文字列）はこのファイルの1か所に置き、形が違うときは通信の失敗（`ApiError` の `network` の種類）として投げる（部品 3節の「`loadFailed` と同じ扱い」）。
  - `shared/validation/` は `validateDisplayName.ts`・`validatePassword.ts`（`validateNewPassword`・`validatePasswordConfirmation`）・`codePoints.ts`（`countCodePoints`・`utf8ByteLength`・`trimDisplayName`）・`limits.ts`（3つの定数）と、誤りの種類の型（`DisplayNameProblem = 'required' | 'tooLong' | 'invalidCharacter'`・`NewPasswordProblem = 'required' | 'tooShort' | 'tooLong'`・`PasswordConfirmationProblem = 'required' | 'mismatch'`）。誤りが無いときは `undefined` を返す。`index.ts` は置かず、各ファイルから名前付きで読む（既存の `shared/format/` と同じ形）。
  - 実際のブラウザの検査のファイルは `frontend/e2e/070-registration-accessibility.e2e.ts`、E2E-1 は `frontend/e2e/090-invitation-registration-flow.e2e.ts`（U5 の決定 2）。共用の手伝いに足すのは `frontend/e2e/support/mailpit.ts`（Mailpit の API の場所の定数と `findInvitationLink(address)`）と `frontend/e2e/support/registrationFixtures.ts`（確かめの応答の見本1つ `VERIFY_SAMPLE`、見本のトークン `A11Y_SAMPLE_TOKEN = 'a11y-sample-token'`、項目の名前と型だけを比べる `shapeOf`）。`support/axe.ts` に `REGISTRATION_KNOWN_VIOLATIONS` と型 `RegistrationAxeState = 'ready' | 'unavailable'` を足す。
  - `data-testid` は `registration-` で始める（例: `registration-page`・`registration-heading`・`registration-verifying`・`registration-load-failed`・`registration-reload-button`・`registration-unavailable`・`registration-to-login-link`・`registration-logged-in-notice`・`registration-logout-button`・`registration-home-button`・`registration-form`・`registration-failure-alert`・`registration-email-input`・`registration-display-name-input`・`registration-password-input`・`registration-password-confirmation-input`・`registration-language`・`registration-theme`・`registration-font-size`・`registration-submit-button`）。make-you-chic-ui の部品が中に持つ要素（`data-testid="alert"` など）は探さず、包む要素の `data-testid` か役割と名前で探す（`LoginForm.tsx` と同じ）。
- **既存に手を入れるもの**: `frontend/e2e/support/axe.ts`（`REGISTRATION_KNOWN_VIOLATIONS` を足すだけ。既存の一覧と判定は変えない）、`README.md`、`frontend/src/features/README.md`（共用の確かめの関数の使い方を1節足す）、`build.gradle.kts`（`mailpitInfoUrl` の上に、TypeScript の側の定数 `frontend/e2e/support/mailpit.ts` を参照するコメントを1行足すだけ。動きは変えない。9節の決定 3）。U7 が使う API で登録を完了する関数は U6 では作らない（9節の決定 4）。
- **変えないもの**: 骨組み（`frontend/src/app/`）・ApiClient（`frontend/src/shared/api-client/`）・`frontend/src/features/auth/`（`logout` を呼ぶだけ）・`frontend/src/features/invitation/`・`frontend/src/shared/format/`・機能の登録の仕組み・`frontend/playwright.config.ts`・`playwright-secret-check-reporter.ts`・`vitest.config.ts`・`tsconfig.json`・既存の E2E（010〜060）と既存の手伝い（`adminLogin.ts` など）。
- **サーバーの側は変えない**: `backend/` のコード・`application.yaml`（CSP を含む）・`SecurityConfig`（`Referrer-Policy`）・Flyway・`compose.yaml`・`Dockerfile`・`build.gradle.kts`（9節の決定 3 のコメント1行を除く）・`.github/`・`.gitignore`・`frontend/package.json`・`frontend/package-lock.json` は変えない（NFR1.4・NFR9.5・NFR9.6、`infrastructure-specification.md`）。`vendor/` の中身も固定先も変えない（NFR9.7）。
- **機能どうしの依存**: `features/registration` から `features/auth/authSession` の `logout` を読む依存が1本増える（機能設計の 10節、Q1 A で受け入れ済み）。今の本番のコードに機能どうしの読み込みは無く、これが最初の1本になる。依存の向きは registration → auth の1本だけとし、auth は registration を知らない。フロントエンドのリンタに機能どうしの読み込みを止める決まりが無いことを Step 2 で確かめる（あれば止めて諮る。9節の決定 5 の (a)）。
- **既存の ArchUnit** はバックエンドの検査で、U6 は触れない。

## 3. 作業の場とコミットの区切り

- **作業のブランチ**: `develop` の先頭（`dc21a5a`）から短命のブランチ `feature/260925-user-management-b5-u6` を作る（`team.md` の Way of Working、U5 の計画の決定 1）。
- **統合の形**: U6 は固定先の更新を含まないため、`develop` へ **squash** で統合する（`team.md` の Way of Working、`cicd-pipeline.md` 5節）。B5 は単位ごとに統合すると依頼者が決めた（U5 の計画の決定 1。`team.md` の「1 Bolt が `develop` の1コミット」とは違うが、この Bolt の扱いとして承認済み）。統合の前に `./gradlew verify` を通し（Step 23）、画面・認証に関わる変更のため Mailpit を起動して `./gradlew e2eTest` の全件も通す（Step 22）。統合は依頼者の承認を得て行う。
- **コミット**: 生成の担当はコミットしない。生成の後に、依頼者の承認を得て、次の区切りでまとめてコミットする（`project.md` の Change Control の学び）。メッセージは日本語で、U6 の内容が分かる件名にする。squash のため、`develop` には U6 の1つのコミット（件名の案「B5 U6 登録の完了の画面（リンクの確かめ・入力と表示の設定・ログインの画面への受け渡し、入力の確かめの共通化、実際のブラウザの検査と代表の流れの E2E）」）になり、細かい区切りは作業ブランチに残る。

| 区切り | 中身 | 手順 |
|---|---|---|
| C1 | 入力の確かめの関数（`frontend/src/shared/validation/` の本番とテスト。U7 と共用） | Step 5・6 |
| C2 | 本番のコード（`frontend/src/features/registration/` のテスト以外、文言と CSS） | Step 3・7・9・11・13 |
| C3 | テスト（`frontend/src/features/registration/` の `*.test.ts(x)` と `testing/`） | Step 8・10・12・14 |
| C4 | 実際のブラウザの検査と E2E-1（`frontend/e2e/070-registration-accessibility.e2e.ts`・`090-invitation-registration-flow.e2e.ts`・`frontend/e2e/support/` の足したファイルと `axe.ts`、`build.gradle.kts` の参照のコメント1行） | Step 15〜18 |
| C5 | 文書（`README.md`・`frontend/src/features/README.md`） | Step 21 |
| C6 | この段の記録（記録の `construction/u6-registration-ui/code-generation/` の下） | Step 24 |

- `origin` への `git push` は依頼者が行う。AI はプッシュしない。
- 統合の squash のコミットのメッセージの末尾に `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` を付ける（作業ブランチの各コミットも同じ）。

## 4. 作るもの・手を入れるもの

| 置き場 | 部品 | 新しい・手を入れる | 役割 |
|---|---|---|---|
| `shared/validation/codePoints.ts` | `countCodePoints`・`utf8ByteLength`・`trimDisplayName` | 新しい | 6節、U2 の BR1.1 |
| `shared/validation/limits.ts` | `DISPLAY_NAME_MAX_CODE_POINTS`（254）・`PASSWORD_MIN_CODE_POINTS`（12）・`PASSWORD_MAX_UTF8_BYTES`（72） | 新しい | 6節 |
| `shared/validation/validateDisplayName.ts` | `validateDisplayName`・`DisplayNameProblem` | 新しい | 6節、U2 の BR1.2〜BR1.4 |
| `shared/validation/validatePassword.ts` | `validateNewPassword`・`validatePasswordConfirmation`・`NewPasswordProblem`・`PasswordConfirmationProblem` | 新しい | 6節、U2 の BR4.1・U3 の BR7.2 |
| `features/registration/registrationApi.ts` | `verifyRegistration`・`completeRegistration`・2つのパスの定数・`VerifiedInvitation`・`CompleteRegistrationRequest`。確かめの応答の形の確かめ | 新しい | 部品 3節、NFR9.1、`security-design.md` 3.1 |
| `features/registration/registrationToken.ts` | フラグメントの文字列から `token` の値を取り出す | 新しい | D1、NFR1.1 |
| `features/registration/failureKind.ts` | 確かめ・完了の失敗を `unavailable`・`loadFailed`・`validationFailed`・`submitFailed` に振り分ける（状態コードと `code` だけ） | 新しい | 5節、NFR3.1・NFR9.2 |
| `features/registration/formProblems.ts` | フォームの値から項目ごとの誤りの文言の鍵と最初の誤りの項目を決める | 新しい | W7、D8 |
| `features/registration/messages.ts` | `registrationMessages`（ja・en、機能設計 7節の鍵と文） | 新しい | NFR8.1 |
| `features/registration/useRegistration.ts` | 状態・操作・副作用（部品 4節）。古い答えを捨てる番号と部品が付いているかの印 | 新しい | W2〜W12、D1〜D12 |
| `features/registration/RegistrationPage.tsx`（と CSS） | Card・アプリ名・h1、状態ごとの子、外れるときの `clearPreview` | 新しい | 部品 1節・5節 |
| `features/registration/RegistrationStatus.tsx` | 確かめ中（`role="status"`）・読み込めない（失敗の Alert と「もう一度読み込む」） | 新しい | W4、NFR6.2 |
| `features/registration/RegistrationUnavailable.tsx` | 使えないリンクの警告の Alert と「ログインの画面へ」のリンク | 新しい | W11、Q4 B |
| `features/registration/RegistrationLoggedInNotice.tsx` | ログイン中の案内（情報の Alert）と2つのボタン | 新しい | W3、Q1 A |
| `features/registration/RegistrationForm.tsx`（と CSS） | フォーム（メールアドレス・氏名・パスワード2つ・表示の設定の見出しと3つの RadioGroup・送信）、失敗の知らせ、フォーカスの当て方 | 新しい | W5〜W10 |
| `features/registration/registration.ts` | 機能の登録（`featureId: 'registration'`、画面 `/register`（`REGISTRATION_PATH`、遅延読み込み、`STANDALONE`・`PUBLIC`）、文言）。サイドバー・ユーザーメニューの項目は無い | 新しい | W1、NFR6.3 |
| `features/registration/testing/renderRegistration.tsx`・`fixtures.ts` | テストの補助（テストからだけ使う） | 新しい（テストの支え） | 部品 8節 |
| `frontend/e2e/070-registration-accessibility.e2e.ts` | 20 組のテスト（組ごとに `ready`・`unavailable` の2状態） | 新しい | NFR7.3・NFR7.5・NFR9.5 |
| `frontend/e2e/090-invitation-registration-flow.e2e.ts` | E2E-1（招待から登録の完了まで、5回の測りと3つの確かめ、応答の形の照合） | 新しい | NFR1.2・NFR1.3・NFR6.1・NFR9.10、W13 |
| `frontend/e2e/support/mailpit.ts` | Mailpit の API の場所の定数、`findInvitationLink(address)`（`GET` だけ、Node の `fetch`） | 新しい（B5 の共用の手伝い） | `cicd-pipeline.md` 4.3 |
| `frontend/e2e/support/registrationFixtures.ts` | `VERIFY_SAMPLE`（`VerifiedInvitation` の型付き）・`A11Y_SAMPLE_TOKEN`・`shapeOf` | 新しい（B5 の共用の手伝い） | `security-design.md` 5.2 |
| `frontend/e2e/support/axe.ts` | `REGISTRATION_KNOWN_VIOLATIONS`・`RegistrationAxeState` を足す | 手を入れる | U5 の決定 4 の形 |
| `README.md` | 「登録の完了の画面（U6）」の節、E2E の表の 070・090 の行と「070 について」「090 について」 | 手を入れる | `cicd-pipeline.md` 5節 |
| `frontend/src/features/README.md` | 入力の確かめの関数（`shared/validation`）の使い方 | 手を入れる | U7 への引き継ぎ |
| `build.gradle.kts` | `mailpitInfoUrl` の上に `frontend/e2e/support/mailpit.ts` の定数を参照するコメントを1行（動きは変えない） | 手を入れる（コメントだけ） | 9節の決定 3 |

## 5. 手順

各層で実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む（test-after、Testing Contract の `ordering`）。U6 の層は「業務処理（共用の確かめの関数 → 画面の純粋な関数）→ API（`registrationApi`）→ 画面の振る舞い（画面部品 → 画面と状態と登録）→ 実際のブラウザの検査（070）→ E2E-1（090）」の順とする。U6 は内部DB とブラウザの新しい保存を持たないため、データの形（DB）とデータアクセスの層は無い。共用の確かめの関数を最初に置くのは、U7 が使う形を U6 の画面より先に固定するため（2.1 の U7 R-01）。

### Step 1: 作業の場の用意と、変更の前の基準（依頼者が承認した git の操作）

- [x] `develop` の先頭のハッシュ（`dc21a5a`）と、サブモジュールの固定先（`vendor/make-you-chic-ui` が `735ef04ce6eb618cb875f5c4b31c1645a1f84c28`、`vendor/java-mustache-processor` が `8d44c36b2bbaf36a35fe0ce397ac1c0fb7bc0ba4`）を記録し、`develop` から短命のブランチ `feature/260925-user-management-b5-u6` を作る（コミットはしない。統合は squash）
- [x] 変更の前の基準をとる: colima が動いていることを確かめ、README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡して `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を実行し、テストの件数（バックエンドの単体・結合、フロントエンドのファイル数と件数、失敗・飛ばした）と、フロントエンドの全体のカバレッジ（行・分岐）を記録する（brownfield の Test Baseline、`project.md` の Testing Posture。U5 の後の実測は 68 ファイル 509 件、行 97.6%・分岐 93.73%）
- [x] 同じ実行の成果物で、変更の前の値を記録する: `frontendBundleSize` の初回の JavaScript（gzip）、`frontend/dist/assets/` の JavaScript のファイルの数と合計、`backend/build/libs/mastersmith.war` の大きさ（NFR6.3、`performance-design.md` 3節。測る道具は足さず、`du`・`stat` と既存の検査の出力）
- [x] 対応: B5 の共通の完了の条件、NFR6.3（前の値）

### Step 2: 前提の確かめ（読み取りだけ）

- [x] `git submodule status` で `vendor/make-you-chic-ui` が `735ef04` であること（NFR9.7）と、U6 が使う口が `vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/` にあることを確かめて記録する: RadioGroup（`legend`・`options` の `label` が ReactNode・選択肢の `lang`・`name`・`value`・`onChange`、`legend` を渡すと `fieldset`・`legend`）、Button（`loading` で `aria-disabled`・`aria-busy`、押しても `onClick` を呼ばない）、FormField（`label`・`error`・`helperText`・`required`、誤りと案内の結び付け）、TextInput（`forwardRef`・`readOnly`・`type`・`autoComplete`）、Alert（`danger`・`warning` は `role="alert"`、`info` は `role="status"`）、Card。口が無い・形が違うときは、9節の決定 5 の (a) のとおり止めて諮る
- [x] U4 の口を確かめて記録する: `frontend/src/app/login-handoff/loginHandoff.ts` の `handOffToLogin(email)`（NFR 設計の承認の場の U6 R-01）と `LoginForm.tsx` の `takeLoginHandoff`・`clearLoginHandoff`・`login-form-registered-alert`、`useDisplaySettings()` の `setPreview`・`clearPreview`・`setLanguage`、`displaySettingsStore.ts` の `saveBrowserDisplaySettings`、`displaySettingsTypes.ts` の `LANGUAGE_NAMES`・`THEME_CHOICES`・`FONT_SIZES`、骨組みの文言 `app.name`・`display.theme.*`・`display.fontSize.*`、`shared/api-client/apiClient.ts` の `TOKENLESS_API_PATHS` に `/api/registration/verify`・`/api/registration/complete` があること（U4 の D10）
- [x] U3 の API の実際の形を `backend/src/main/java/cherry/mastersmith/invitation/web/` で確かめて記録する: `TokenRequest`（`token`）、`InvitationViewResponse`（`email`・`language`）、`CompleteRequest`（7項目）、完了は 204、400 `VALIDATION_FAILED` に `fieldErrors`（U6 は読まない）、404 `REGISTRATION_LINK_INVALID`（契約 C6 と README の「契約との差」の C6 のとおりであること）
- [x] 振り分けと配信を確かめて記録する: `decideRoute.ts` が PUBLIC の画面をログイン状態によらず表示すること、`WebConfig.java` が画面の URL に `index.html` を返すこと、`NotFoundPage.tsx` の `not-found-page`（E2E-1 で管理者だけの画面を開いたときの表示）、`ShellLayout.tsx` のユーザーメニューの名前が氏名であること
- [x] AuthUi の `logout`（`frontend/src/features/auth/authSession.ts`）が API の失敗でも画面の側の破棄を行い例外を外へ出さないことと、`useLoginState()`（`frontend/src/app/login-state/LoginStateGate.tsx`）がログアウトの後に未ログインを知らせることを確かめて記録する
- [x] フロントエンドのリンタの設定（`frontend/eslint.config.js`・`.oxlintrc.json`）に機能どうしの読み込みを止める決まりが無いことを確かめる（あれば 9節の決定 5 の (a) で止めて諮る）
- [x] 対応: NFR9.7、機能設計の承認の場の Request Changes、NFR 設計の承認の場の U6 R-01

### Step 3: 骨組み（型・文言）

- [x] `frontend/src/features/registration/registrationApi.ts` の型の部分を作る: `VerifiedInvitation`（`email: string`・`language: DisplayLanguage`）、`CompleteRegistrationRequest`（`token`・`displayName`・`password`・`passwordConfirmation`・`language`・`theme`・`fontSize`。型は U4 の `DisplayLanguage`・`ThemeChoice`・`FontSize`）、`REGISTRATION_VERIFY_PATH`・`REGISTRATION_COMPLETE_PATH`。`enum` を使わない
- [x] `frontend/src/features/registration/messages.ts` に `registrationMessages`（ja・en）を作る。鍵と文は機能設計 7節の表のとおり（`registration.` で始める。有効期限の長さの数を書かない。トークン・メールアドレスを含めない）
- [x] 新しいファイルはすべて先頭に Apache License 2.0 のヘッダー（`/* ... */`、2026、agwlvssainokuni）と、日本語の説明のコメントを置く。エクスポートは名前付きだけ（`export default` を使わない）
- [x] 対応: 部品 2節・3節、NFR8.1、機能設計 7節、`team.md` の Code Style

### Step 4: テストの実行の準備（最初のテストより前）

- [x] `unit-test-instructions.md` 2節の単体のコマンド（Vitest の対象を `src/features/registration`・`src/shared/validation` に絞ったもの）を、この時点では当たるテストが無いため `--passWithNoTests` を付けて実行し、設定が読み込まれて終了の状態が 0 であることを確かめる。あわせて、環境が実際にテストを流せることを、U6 が使う既存の口のテスト（`src/app/login-handoff/loginHandoff.test.ts`）を同じ形のコマンドで流して確かめる
- [x] `fast-check`（既存）と `vitest-axe`（既存）が使えることを確かめる。`renderWithProviders` の `route` に `/register#token=…` を渡すと、React Router の場所の `hash` に値が入ることを確かめる（フラグメントを読む作りの前提）
- [x] `(cd frontend && npx playwright test --list)` で既存の E2E の一覧（010〜060）が読めることを確かめる（070・090 はまだ無い）
- [x] 対応: Testing Contract の `runner_step`

### Step 5: 業務処理 — 入力の確かめの関数（`shared/validation`、U7 と共用）— 実装

- [x] `codePoints.ts`: `countCodePoints`（コードポイントの数。サロゲートの組は1つ、組になっていないサロゲートも1つ）、`utf8ByteLength`（`TextEncoder` と同じ数え方。組になっていないサロゲートは置換文字の3バイト）、`trimDisplayName`（前後から Unicode の White_Space の性質を持つ文字だけを除く。集合は U2 の BR1.1 とサーバーの `backend/src/main/java/cherry/mastersmith/user/domain/DisplayName.java` の White_Space の判定と同じ文字を定数の集合で持ち、生成の中で両者を突き合わせる。JavaScript の標準の `trim` は使わない）
- [x] `limits.ts`: 3つの定数
- [x] `validateDisplayName.ts`: `trimDisplayName` の後に、長さ 0 は `required`、254 コードポイントを超えれば `tooLong`、Cc・Cf の文字（`\p{Cc}`・`\p{Cf}` の Unicode の性質のクラス）があれば `invalidCharacter`、どれでもなければ `undefined`（判定の順もこのとおり）
- [x] `validatePassword.ts`: `validateNewPassword`（空は `required`、12 コードポイント未満は `tooShort`、UTF-8 で 72 バイトを超えれば `tooLong`）、`validatePasswordConfirmation(password, confirmation)`（確かめが空は `required`、文字の並びとして完全に一致しなければ `mismatch`。正規化・前後の空白の除去をしない）
- [x] どの関数も React・`window`・ブラウザの保存・`app/`・`features/` に触れない。文言を持たない（誤りの種類だけを返す）
- [x] 関数の名前と union が機能設計の 6節と、U7 の NFR 設計 `security-design.md` の 3.3 の「寄せる理由」と一致することを、作った後にもう一度確かめる（2.1 の U7 R-01）
- [x] 対応: 機能設計 6節、D8、NFR9.3、U2 の BR1.1〜BR1.4・BR4.1、U3 の BR7.2

### Step 6: 業務処理 — 入力の確かめの関数 — テスト（単体・性質ベース）

- [x] `codePoints.test.ts`: 絵文字（サロゲートの組）・結合文字・組になっていないサロゲートの数とバイト数、`trimDisplayName` の除く文字（U+3000・U+00A0・U+0085・U+2028 は除かれ、U+FEFF・U+200B は除かれない）。性質ベース（fast-check）: `countCodePoints` は `Array.from` の長さと同じ、`utf8ByteLength` は `TextEncoder` の長さと同じ、`trimDisplayName` は2回かけても同じ・前後に White_Space が残らない・内側を変えない（NFR9.9）
- [x] `validateDisplayName.test.ts`: 空・空白だけ（半角・全角・タブ）→ `required`、253・254 コードポイント → 誤りなし、255 → `tooLong`、内側の改行・タブ・ゼロ幅の空白（U+200B）・U+FEFF → `invalidCharacter`、前後の空白を除いた後の長さで判定、判定の順（長すぎかつ使えない文字は `tooLong`）。性質ベース: 1〜254 コードポイントの Cc・Cf・White_Space を含まない文字列はすべて誤りなし
- [x] `validatePassword.test.ts`: 空 → `required`、11 コードポイント → `tooShort`、12 → 誤りなし、「あ」24 文字（72 バイト）→ 誤りなし、「あ」24 文字＋「a」（73 バイト）→ `tooLong`、絵文字 11 文字 → `tooShort`・12 文字 → 誤りなし（AC3.2.4）。確かめ: 空 → `required`、1文字違い・前後の空白の違い・正規化の違い（NFC と NFD）→ `mismatch`、同じ → 誤りなし。性質ベース: 境界（11・12 コードポイント、72・73 バイト）の前後で結果が変わる
- [x] 失敗時の fast-check の `seed`・`path` はテストの出力に残る（既存の `submitInput.test.ts` と同じ扱い）
- [x] 単体のコマンドを実行して通す
- [x] 対応: NFR9.9、AC3.2.4・AC3.2.9、部品 8節の `shared/validation` の行

### Step 7: 業務処理 — 画面の純粋な関数（`registrationToken`・`failureKind`・`formProblems`）— 実装

- [x] `registrationToken.ts`: フラグメントの文字列（先頭の `#` の有無によらない）を `key=value` の並び（`&` 区切り）として読み、`token` の値を URL の符号化を戻して返す。無い・空・フラグメントが無いときは `undefined`。形（長さ・文字の種類）は確かめない（D1）。符号化の戻しに失敗する値（壊れた `%`）は `undefined` にする
- [x] `failureKind.ts`: `ApiError`（`kind`・`status`・`code`）から、確かめなら `unavailable`（404 かつ `REGISTRATION_LINK_INVALID`）・`loadFailed`（ほか・通信の失敗）、完了なら `unavailable`（404 かつ `REGISTRATION_LINK_INVALID`）・`validationFailed`（400 かつ `VALIDATION_FAILED`）・`submitFailed`（ほか・通信の失敗）を返す。`detail`・`title`・`type`・`fieldErrors` は読まない（5節、NFR3.1・NFR9.2、`security-design.md` 3.3）
- [x] `formProblems.ts`: フォームの値（氏名・パスワード・確かめ）から `shared/validation` の結果を集め、項目ごとの文言の鍵（例: `registration.password.tooShort`）と、最初の誤りの項目（氏名 → パスワード → 確かめの順）を返す
- [x] どの関数も React・`window`・API に触れない
- [x] 対応: D1・D5・D8・D10、W2・W4・W7・W10、5節、部品 2節

### Step 8: 業務処理 — 画面の純粋な関数 — テスト（単体・性質ベース）

- [x] `registrationToken.test.ts`: `#token=abc` から `abc`、`token=abc`（`#` なし）、ほかの鍵と並んだ値、符号化を戻した値、無い・空・ほかの鍵だけ・空の文字列・壊れた `%` は `undefined`。性質ベース: 任意の文字列を `encodeURIComponent` して `#token=` に付けると元に戻る
- [x] `failureKind.test.ts`: 5節の表の全行（確かめ・完了ごとに、404 と `REGISTRATION_LINK_INVALID`、404 で `code` が違う・無い、400 と `VALIDATION_FAILED`、400 で `code` が違う・無い、401・403・429・503・500、通信の失敗）。`detail` に目印の文字を入れても結果が変わらない（NFR3.1・NFR9.2）
- [x] `formProblems.test.ts`: 誤りなし、1つの項目の誤り、複数の項目の誤りで最初の項目が氏名 → パスワード → 確かめの順、確かめの `mismatch` の鍵
- [x] 単体のコマンドを実行して通す
- [x] 対応: NFR1.1・NFR3.1・NFR9.2、部品 8節の `registrationToken`・`failureKind` の行

### Step 9: API（`registrationApi`）— 実装

- [x] `verifyRegistration(token)`: 既存の `apiRequest` で `POST /api/registration/verify`、`Content-Type: application/json`、本文 `{ token }`。成功の本文を JSON として読み、`email` が文字列で `language` が `ja`・`en` のときだけ `{ email, language }` を返す（知らない項目は捨てる）。読めない・形が違うときは通信の失敗の `ApiError` を投げる（部品 3節）
- [x] `completeRegistration(request)`: `POST /api/registration/complete`、本文は `CompleteRegistrationRequest` の7項目だけ。204 の本文は読まない
- [x] `Authorization`・`Accept-Language` を呼び出し側で付けない（ApiClient に任せる。NFR9.1）。トークンは本文だけに入れ、パス・問い合わせ・見出しに入れない（D3）。失敗は ApiClient の `ApiError` をそのまま投げる
- [x] 応答の値・失敗の値を `console`・ブラウザの保存に出さない
- [x] 対応: 部品 3節、NFR1.1・NFR9.1、`security-design.md` 3.1、C6

### Step 10: API — テスト（単体）

- [x] `registrationApi.test.ts`（`vi.stubGlobal('fetch', ...)` で差し替え、`resetApiClient` をテストごとに呼ぶ）:
  - 2つの要求のメソッド・パス・本文（確かめは `token` だけ、完了は7項目だけ）、トークンがパス・問い合わせに無い、呼び出し側で `Authorization`・`Accept-Language` を付けていない（ApiClient が付ける `Accept-Language` は画面の言語）
  - ApiClient の公開のパスとして扱われる（ログイン中の状態でもアクセストークンが付かない。`TOKENLESS_API_PATHS` と同じ値であること）
  - 確かめの成功の値が決まった項目だけ、`language` が `ja`・`en` でない・`email` が文字列でない・本文が読めないと通信の失敗
  - 完了の 204 の空の本文が成功
  - 400・404・500 と通信の失敗で `ApiError` の種類・状態コード・`code` がそのまま届く
- [x] 単体のコマンドを実行して通す
- [x] 対応: NFR1.1・NFR9.1、部品 8節、`security-design.md` 3.1

### Step 11: 画面の振る舞い — 画面部品（RegistrationStatus・RegistrationUnavailable・RegistrationLoggedInNotice・RegistrationForm）— 実装

- [x] `RegistrationStatus.tsx`（部品 5節の props）: `verifying` は `role="status"` の文字（`registration.verifying`）、`loadFailed` は失敗の Alert（`role="alert"`、`registration.loadFailed`）と secondary の Button「もう一度読み込む」
- [x] `RegistrationUnavailable.tsx`: 警告の Alert（`role="alert"`、記号つき、`registration.unavailable`）と React Router のリンク「ログインの画面へ」（`/login`、ボタンの見た目にしない）。外部の URL を置かない（NFR1.4）
- [x] `RegistrationLoggedInNotice.tsx`: 情報の Alert（`registration.loggedIn.message`）、primary の Button「ログアウトして続ける」（`loggingOut` の間は `loading` で文言 `registration.loggedIn.loggingOut`）、secondary の Button「ホームへ戻る」
- [x] `RegistrationForm.tsx`（部品 5節の props）:
  - `form`（`aria-labelledby` に `headingId`、`noValidate`）。失敗の知らせはフォームの上の Alert（`role="alert"`、`registration.validationFailed`・`registration.submitFailed`）で、フォーカスを受けられる要素（`tabIndex=-1`）で包む
  - 項目は W5 の順と属性: メールアドレス（TextInput、`readOnly`・`type="email"`・`autoComplete="username"`・`disabled` にしない）、氏名（FormField の `required`・案内 `registration.displayName.hint`）、パスワード（`type="password"`・`autoComplete="new-password"`・案内 `registration.password.hint`）、確かめ（同じ属性）。誤りは FormField の `error` で項目の下に文字で出し、`aria-invalid`・`aria-describedby` で結び付ける
  - h2「表示の設定」と3つの RadioGroup（部品 6節の表のとおり。言語の `legend` は文字と2行目の小さな文字の案内、選択肢は `LANGUAGE_NAMES` と `lang`。テーマ・文字の大きさは `display.theme.*`・`display.fontSize.*` で `lang` を渡さない。`key` を言語で変えない。`disabled` を使わない）。文字の大きさの RadioGroup の下に `registration.appearance.hint` を文字として置く
  - 送信の Button（`type="submit"`、primary）。`submitting` の間は `loading` で文言 `registration.submitting`、氏名・パスワードの欄は `readOnly`
  - 描画の確定の後に `focusTarget` の要素（項目・知らせ）へフォーカスを移し、移したことを上へ知らせる
  - 同じ場所の CSS: カードの中の配置、`legend` の2行目、768px 未満で1列、ラジオの選択肢の折り返し（make-you-chic-ui のトークンだけ。部品の内部のクラスを上書きしない。`style` 属性で差し込まない、NFR9.5）
- [x] 部品の文言はすべて `useMessages()` で引く。`console` を呼ばない。サーバーの `detail` を受け取る口を作らない（D10）
- [x] 対応: W3〜W11、D5・D8〜D10、CR6.1〜CR6.3・CR6.5・CR6.6・CR6.9、NFR6.2・NFR7.1・NFR7.4・NFR9.3・NFR9.5

### Step 12: 画面の振る舞い — 画面部品 — テスト（vitest-axe を含む）

- [x] `testing/renderRegistration.tsx`（`renderWithProviders` に登録の完了の機能の登録と骨組みの文言を渡し、`route` の既定を `/register#token=<見本のトークン>` にして描く。画面の言語はブラウザの言語設定で ja・en を切り替える）と `testing/fixtures.ts`（確かめの 200 の見本（`example.test` の下のメールアドレス）、失敗の応答の見本、見分けやすいトークン `registration-test-token-value`、`fetch` の差し替えの組み立て）を作る
- [x] `RegistrationStatus.test.tsx`: 確かめ中の `role="status"` と文言、読み込めないの `role="alert"` と文言、「もう一度読み込む」で `onReload`、ja・en の文言、vitest-axe（2つの表示）
- [x] `RegistrationUnavailable.test.tsx`: 警告の `role="alert"` と文言（AC3.2.2 の趣旨）、リンクの先が `/login`、トークン・メールアドレスを含まない、en の文言、vitest-axe
- [x] `RegistrationLoggedInNotice.test.tsx`: 情報の Alert の文言、2つのボタン、`loggingOut` で「ログアウトしています」・`aria-disabled`・`aria-busy`・押しても `onLogout` が呼ばれない・フォーカスがボタンに残る、「ホームへ戻る」で `onHome`、vitest-axe
- [x] `RegistrationForm.test.tsx`（値と操作は props で渡す）: メールアドレスの欄の `readOnly`・`autocomplete="username"`・`disabled` でない、パスワードの2つの欄の `autocomplete="new-password"`、「12 文字以上」の案内が入力の前に出る、誤りの文字と `aria-invalid`・`aria-describedby`、3つのまとまりが `fieldset`（`role="group"`）で名前が `legend` の文字（言語は案内を含む）、言語の選択肢の文字が `lang="ja"`・`lang="en"`・テーマと文字の大きさに `lang` が無い、矢印キーで選べる、`submitting` で「登録しています」・`aria-disabled`・`aria-busy`・欄が `readOnly`、失敗の知らせの `role="alert"`、`focusTarget` の要素へフォーカスが移る、vitest-axe
- [x] 単体のコマンドを実行して通す
- [x] 対応: NFR6.2・NFR7.2・NFR7.4・NFR8.1、部品 8節の画面部品の行、AC3.2.2・AC3.2.16、CR6.1〜CR6.3・CR6.5・CR6.6・CR6.9

### Step 13: 画面の振る舞い — 画面・状態・登録（useRegistration・RegistrationPage・registration）— 実装

- [x] `useRegistration.ts`（部品 4節）:
  - 状態の初期値を作る関数の中で、React Router の場所（`useLocation().hash`）から `registrationToken` でトークンを取り出す。無い・空なら `unavailable`、あればログイン状態（`useLoginState()`）でログイン中なら `loggedIn`、未ログインなら `verifying`（W2・W3）
  - 描画の確定の後の副作用で1回だけ、フラグメントがあれば同じパス（問い合わせを含む）へ `navigate(..., { replace: true })` でフラグメントを消す（D2。`history.state` に何も載せない）
  - `verifying` で確かめを送り、最後に送った要求の答えだけで移す（番号）。部品が外れた後の答えは捨てる（印）。200 で `setLanguage(invitation.language)`、`values` を初期値（氏名はメールアドレス・言語は招待の言語・テーマ `system`・文字の大きさ `md`・パスワード2つは空）にして `ready`。失敗は `failureKind` で移す
  - `logoutAndContinue()` は `loggingOut` にして AuthUi の `logout` を呼び、ログイン状態が未ログインになったら `verifying` へ。`loggingOut` の間の2回目は無視する。`goHome()` は `/` へ
  - `selectLanguage`・`selectTheme`・`selectFontSize` は値を変え、選んだ軸だけを `setLanguage`・`setPreview(theme)`・`setPreview(undefined, fontSize)` で当てる（D7）
  - `submit()`: `submitting` の間は何もしない。`formProblems` で確かめ、誤りなら `problems` と `focusTarget`（最初の誤りの項目）を置いて送らない。通れば前の知らせを消して `submitting` にし、完了を送る。204 で `saveBrowserDisplaySettings({ language, theme, fontSize })` → `handOffToLogin(invitation.email)` → `/login` へ `replace` の移動（この順、D11）。400 `VALIDATION_FAILED` で `failure: 'validationFailed'` と知らせへのフォーカス、404 で値を捨てて `unavailable`、ほかと通信の失敗で `failure: 'submitFailed'`（値を残す）。`submitting` の間の値の変更と選択は無視する
  - 部品が外れるとき `clearPreview()`（D12）
  - トークン・パスワード・メールアドレスを `console`・ブラウザの保存・URL・`history.state` に出さない
- [x] `RegistrationPage.tsx`（と CSS）: Card の中にアプリ名（`app.name`）と h1（`registration.heading`、id をフォームの名前に使う）、`phase` で子を1つ描く。`completed` では何も描かない。幅は中央の 480px 前後
- [x] `registration.ts`: `featureId: 'registration'`、画面 `path: '/register'`（`REGISTRATION_PATH` として出す）・`screen`（`lazy` の遅延読み込み、既存の `features/auth/registration.ts` と同じ形）・`layout: 'STANDALONE'`・`access: 'PUBLIC'`（`role` なし）、`messages: registrationMessages`。サイドバー・ユーザーメニューの項目は無い
- [x] 対応: W1〜W12、D1〜D12、NFR1.1〜NFR1.3・NFR2.1・NFR6.2・NFR6.3・NFR8.2・NFR9.4、部品 4節

### Step 14: 画面の振る舞い — 画面・状態・登録 — テスト（vitest-axe を含む）

- [x] `RegistrationPage.test.tsx`（`fetch` を差し替え、U4 の口とログイン状態は本物。部品 8節の `RegistrationPage`・`RegistrationForm` の流れの行を1つずつ）:
  - 開く: 言語 ja の招待・ブラウザの言語 en・保存の値 dark・lg で開くと、確かめの後に ja の文言・`<html lang="ja">`、氏名にメールアドレス・言語 ja・テーマ system・文字の大きさ md が選ばれ、画面はブラウザの保存の値（dark・lg）で表示される。アプリシェルの外（AC3.2.1）
  - トークン: 確かめの要求の本文にだけトークンがあり URL に無い、描画の後に `location.hash` が空で履歴の項目が増えない（置き換え）、フラグメントが無い・空なら要求を送らずに使えないリンクの表示、読み込み直し（同じ URL をフラグメント無しで描き直す）で使えないリンクの表示（NFR1.1・NFR1.3）
  - 確かめの 404 で使えないリンクの文と「ログインの画面へ」（フォームが無い）。`detail` だけを変えた2つの 404 で表示が同じで `detail` が出ない（NFR3.1）。確かめの 500 と通信の失敗で「読み込めませんでした」と「もう一度読み込む」、押すと同じトークンで確かめ直して表示される（D5）。確かめ中は `role="status"`（NFR6.2）
  - ログイン中: 確かめを送らずに案内を出す。「ログアウトして続ける」でログアウトの要求の後に確かめを送る。ログアウト中に2回押してもログアウトの要求は1回。「ホームへ戻る」で `/` へ（Q1 A）
  - 選ぶ: テーマを選ぶと画面のテーマだけが変わり文字の大きさは保存の値のまま（逆も同じ）、保存の値は変わらない、フォーカスは選んだラジオのまま。言語 en を選ぶと文言・`<html lang>` が en、出ていた誤りも en、次の完了の要求の `Accept-Language` が en、言語を切り替えた後もフォーカスが同じラジオに残る（CR1・NFR8.2）
  - 確かめの誤り: 境界の値（11・12 コードポイント、72・73 バイト、絵文字 11・12 文字、空、不一致、氏名の 254・255 コードポイント、空白だけ）で、要求を送らず、最初の誤りの項目にフォーカス、値が消えない（AC3.2.4・AC3.2.9、CR6.1）
  - 送信: 送信の間は「登録しています」・`aria-disabled`・`aria-busy`・フォーカスがボタンに残る。2回押す・Enter キーで送る・ラジオを選ぶの操作をしても完了の要求は1回で本文の値が変わらない（NFR9.4）
  - 完了の 204: 保存の値が選んだ en・dark・lg、ログインの画面（`/login`）へ移り `login-form-registered-alert` とメールアドレスの欄に招待のメールアドレス、URL にメールアドレスが無い、ログインの要求が送られない、受け渡しはメールアドレスだけ、Toast を出さない（AC3.2.5・AC3.2.17・AC3.2.18、NFR2.1、CR6.4 の差）
  - 完了の 400 で入力を確かめる知らせと知らせへのフォーカス、値が残り、同じ画面から送り直して 204 で完了できる（AC3.2.10）。完了の 404 で使えないリンクの表示に移り、フォームと値が消える（AC3.2.11）。500 と通信の失敗で登録できない知らせ、値が残る、`detail` の文字が出ない（CR6.4、D10）
  - 離れる: 完了せずに部品が外れると、画面の言語・テーマ・文字の大きさがブラウザの保存の値に戻る（D12）
  - 漏えい: 開く・確かめの成功と失敗（404・500・通信の失敗）・完了の成功と失敗（400・404・500・通信の失敗）の流れの後に、localStorage・sessionStorage のすべての鍵の値にトークン・パスワード・メールアドレスが無い（保存の値の3つだけ）、`console` の `log`・`info`・`warn`・`error`・`debug` がどの流れでも呼ばれない（呼ばれたら引数にトークン・パスワード・メールアドレスが無いことも見て失敗の説明に出す）、画面の文字にトークンが出ない（NFR1.2・NFR2.1・NFR9.3）
  - vitest-axe の違反 0 件（`ready` の画面全体。ほかの状態は子の部品の検査で見る。8節）
- [x] `registration.test.tsx`: 画面の値（`/register`・`STANDALONE`・`PUBLIC`・`role` なし、遅延読み込み）、サイドバーとユーザーメニューの項目が無い、既存の登録（auth・admin・dsl・invitation）と合わせて `validateRegistrations` が通る（パスの重なり・ja・en の鍵のそろい・接頭辞）、文言の ja・en の鍵が一致して空でない、画面の言語が en でも言語の選択肢が「日本語」と出る（NFR8.1）
- [x] 単体のコマンドを実行して通す。続けて U6 の範囲のカバレッジを見て、行 80%・分岐 70% に届かない U6 の新しいファイルがあればテストを足す
- [x] 対応: NFR1.1〜NFR1.3・NFR2.1・NFR3.1・NFR6.2・NFR7.2・NFR7.4・NFR8.1・NFR8.2・NFR9.2〜NFR9.4、部品 8節、AC3.2.1・AC3.2.2・AC3.2.4・AC3.2.5・AC3.2.9〜AC3.2.11・AC3.2.17・AC3.2.18、CR1.2〜CR1.4・CR6.1・CR6.3・CR6.4

### Step 15: 実際のブラウザの検査（070 と e2e/support）— 実装

- [x] `frontend/e2e/support/registrationFixtures.ts`: `import type { VerifiedInvitation }`（`src/features/registration/registrationApi.ts`）を付けた `VERIFY_SAMPLE`（`email` は `example.com` の下の固定の値、`language: 'ja'`）、`A11Y_SAMPLE_TOKEN = 'a11y-sample-token'`（どの招待にも当たらない低いエントロピーの値。Gitleaks に掛からない）、`shapeOf(body)`（項目の名前ごとに型の名前を返す。`language` は `ja`・`en` のどちらかかも見る。値は返さない）
- [x] `frontend/e2e/support/axe.ts`: `REGISTRATION_KNOWN_VIOLATIONS`（`ready`・`unavailable`）と型 `RegistrationAxeState` を足す。当たる名前は Step 16 の最初の実行の結果で確かめてから書く（見込みは `ready` が `registration-submit-button`、`unavailable` はなし）。既存の `KNOWN_VIOLATIONS`・`INVITATION_KNOWN_VIOLATIONS` と `splitKnownViolations` は変えない
- [x] `frontend/e2e/070-registration-accessibility.e2e.ts`:
  - 20 組のテスト（組ごとに1つのテスト。題は組の名前だけ）: `prepareCombo`（U4 の手伝い）で組を当て、`watchPage` を張る → (1) `ready`: `page.route('**/api/registration/verify', ...)` で `POST` だけを受けて 200 の `VERIFY_SAMPLE` を返し、`/register#token=a11y-sample-token` を開く → フォーム（メールアドレスの欄）が出て組が効いたこと（`expectComboApplied`）を確かめる → axe と横のはみ出し → (2) `unavailable`: 差し替えを外し、フラグメントの無い `/register` を開き直す → 使えないリンクの表示 → axe と横のはみ出し
  - ログインしない。完了の要求を送らない（要求の一覧で `/api/registration/complete` が0件、`/api/registration/verify` は `ready` の1回だけで `unavailable` では0件であることを確かめる）。サーバーの状態（内部DB・監査・招待）を変えない（`security-design.md` 5.1）
  - 各状態の記録（組・状態・想定外の違反の件数と規則の名前・既知の違反・`incomplete` の規則の名前と件数・はみ出し）を注記と添付（JSON）に残す。U4 の手伝い（`runAxe`・`missingRequiredRules`・`splitKnownViolations` の第3引数・`measureHorizontalOverflow`・`watchPage`）を使い、CSP の違反と画面の問題を各組で失敗の条件にする
  - `test.step` の題・注記・添付に見本のトークンと見本のメールアドレスの値を入れない（見本でも出さない形にそろえる）
  - 既存の 010〜060 は変えない。流れの E2E ではないため本数に数えない（NFR9.11、`project.md` の学び）
- [x] 対応: NFR7.3・NFR7.5・NFR9.5、NFR 設計の Q1 A、`security-design.md` 5節

### Step 16: 実際のブラウザの検査 — 実行と確かめ

- [x] `./gradlew :backend:bootWar` で WAR を作り、`docker compose --profile mail up -d mailpit` で Mailpit を起動して（基盤の設計の N2）、`(cd frontend && caffeinate -i npx playwright test e2e/070-registration-accessibility.e2e.ts)` で 070 だけを流す（`unit-test-instructions.md` 2節）
- [x] 1回目の結果で、green・orange の組の各状態で当たった `color-contrast` の違反の名前（`data-testid`）が、U6 の primary の Button だけかを確かめる。U5 の決定 4 の形のとおり、`REGISTRATION_KNOWN_VIOLATIONS` に書いて流し直す。primary の Button 以外（Alert・リンク・ラジオ・Card など）の違反や、blue・purple の組の違反が出たときは、U6 のコード（CSS・部品の選び方）で直せるかを見て、直せるなら直して流し直す。直すのに make-you-chic-ui の変更が要るときは、生成を止めて依頼者に諮る（9節の決定 5 の (b)）
- [x] 375px の組で、文書の横のはみ出しが無いこと（1列に積まれ、ラジオの選択肢が折り返す）を確かめる。はみ出しが make-you-chic-ui・`StandaloneLayout` に因り U6 のコードで直せないときは、止めて諮る（9節の決定 5 の (c)）
- [x] 20 組の結果（状態ごとの成否・違反の件数・既知の違反・`incomplete`）、CSP の違反の件数、070 の時間（検査の部分の実測、見込みは約 1〜2 分）を記録する
- [x] `frontend/test-results/e2e-results.json` を開き、見本のトークンと見本のメールアドレスの値が入らないことを文字列の検索で確かめて記録する（報告の部品 `playwright-secret-check-reporter.ts` の確かめも通ること）
- [x] 070 を2回続けて流し、結果（組・状態の成否）が同じであることを確かめる（不安定な検査を残さない、`team.md` の Testing Posture）
- [x] Mailpit は止めず消さない（Step 18・22 でも使う）
- [x] 対応: NFR7.3・NFR7.5・NFR9.5、NFR 要件の承認の場の U6 R-01（時間の実測）

### Step 17: E2E-1（090）と Mailpit からの取り出しの部品 — 実装

- [x] Mailpit の API の形を実物で確かめる（基盤の設計の N7・C-D4）: 起動した Mailpit で `GET /api/v1/info` の応答と、Mailpit にあるメッセージ（無ければ 090 の最初の実行の後）に対する `GET /api/v1/search?query=to:"<宛先>"` と `GET /api/v1/message/{ID}` の応答の項目の名前（`messages`・`ID`・`To`・`Address`・`HTML`・`Text` など）を、値を出さずに項目の名前だけ（`jq 'keys'` など）で確かめて記録する。設計と違えば取り出しの部品をその形に合わせて直し、差を記録する。`GET` の search・message の API そのものが無い・使えないときは止めて諮る（9節の決定 5 の (f)）
- [x] `frontend/e2e/support/mailpit.ts`: Mailpit の API の場所の定数（`http://127.0.0.1:8025`。9節の決定 3。定数のコメントに Gradle の `build.gradle.kts` の `mailpitInfoUrl` を参照先として書き、`build.gradle.kts` の側にもこの定数を参照するコメントを1行足す。値は2か所になる）と `findInvitationLink(address)`:
  - (a) `GET /api/v1/search`（問い合わせは宛先の `to:`）で探し、宛先がそのアドレスと完全に一致するものだけを残す。1通でないとき（0通・2通以上）は失敗
  - (b) `GET /api/v1/message/{ID}` で HTML と本文の文字を読み、`/register#token=` を含む URL を取り出す。HTML の `href` と本文の文字の URL が同じであることを確かめる
  - (c) 上限 10 秒・間隔 500 ミリ秒（`expect.poll`）で待つ。上限を超えたら「Mailpit の起動と U1 の SMTP の設定を確かめる」旨だけの失敗にする
  - (d) Node の組み込みの `fetch` で呼び、Playwright の `request` を使わない（Mailpit の応答の本文をトレースと報告に記録させない）。失敗の知らせ・`console`・注記・添付・標準出力にアドレス・URL・トークン・本文を載せない（件数と「見つからない」などの種類だけ）。書き込み・消す API は使わない（Q1 C）
- [x] `frontend/e2e/090-invitation-registration-flow.e2e.ts`（W13 の流れ。前のテストが作った状態に頼らない）:
  - 準備: 宛先 `e2e-invitee-<実行時刻>-<乱数の16進>@example.com`、パスワード `e2e-invitee-pw-<乱数の16進>`（12 コードポイント以上。実行ごとに作り、リポジトリに置かない）、氏名は架空の固定の値（例「招待 花子」）
  - 招待: `loginAsAdmin`（U5 の手伝い）→ `openSidebarItem('利用者の招待')` → U5 の画面の「招待する」（`invitation-invite-button`）→ メールアドレス（`invitation-invite-email-input`）→ 言語 ja → 「招待する」（`invitation-invite-submit`）→ Modal が閉じ、失敗の知らせ（`invitation-failure-alert`）が無いこと（送信の結果 SENT）を確かめる。管理者のコンテキストはここで閉じる（9節の決定 1）
  - 取り出し: `findInvitationLink(宛先)` でリンクを取り出し、`baseURL` ＋ `/register#token=` で始まることを確かめる（失敗の知らせには真偽だけを出す。U3 がベース URL だけから組み立てることの裏付け）
  - 測り（NFR6.1、`performance-design.md` 2節）: 5回とも `browser.newContext()`（`baseURL`・`viewport`・`locale: 'ja-JP'`、キャッシュが空）→ `watchPage` → `page.goto(リンク)` の直前からメールアドレスの欄（`registration-email-input`）が見えるまでをテストの側の時計で測る → (a) `page.url()` に `#token=` が無い、(b) CSP の違反が0件、(c) localStorage・sessionStorage のすべての鍵の値にリンクのトークンが無い（この3つは失敗の条件）→ コンテキストを閉じる。時間では失敗させず、5回の値（ミリ秒）と目標以内の回数（2,000）を注記と添付に残す
  - 登録: 新しいコンテキスト（招待された人のブラウザ。9節の決定 1）でリンクを開き、本物の確かめの応答を `page.waitForResponse` で受けて、状態コード 200、`shapeOf(本文)` が `shapeOf(VERIFY_SAMPLE)` と同じことを確かめる（値は比べず、失敗の知らせには項目の名前と型だけを出す。NFR 設計の Q1 A、NFR 要件の承認の場の R-02）→ アドレス欄に `#token=` が無い → メールアドレスの欄の値が宛先と同じ（真偽だけを確かめる）→ 氏名を入れ、パスワード2つを入れ、「登録を完了する」
  - 完了の後: ログインの画面（`login-layout`）へ移り、`login-form-registered-alert` が見え、メールアドレスの欄に宛先が入り、`page.url()` にメールアドレス（`@`・`%40`）が無い。localStorage の表示の設定がフォームの値（ja・system・md）で、どの鍵の値にもトークン・パスワード・宛先が無い（NFR1.2・NFR2.1 の実際のブラウザでの裏付け）
  - 新しい利用者でログイン: パスワードを入れてログイン → ホーム（`home-page`）→ サイドバーに「管理」「利用者の招待」「DSL の管理」のリンクが無い → `/admin/invitations` と `/admin` を直接開いても `not-found-page` が出る → ユーザーメニュー（氏名の名前のボタン）から「ログアウト」→ ログインの画面
  - 流れ全体の時間の目標は置かない（Playwright の既定の時間切れのまま）。CSP の違反と画面の問題（`watchPage`）は流れの各コンテキストで失敗の条件にする
  - `test.step` の題・注記・添付・失敗の知らせに、リンク・トークン・宛先・パスワード・初期管理者のメールアドレスを入れない（基盤の設計の N9・Q2 A）。リンクは `page.goto` のまま開く（HTML の報告とトレースに載ることは受け入れる）
  - Mailpit のメールを消さない。既存の 010〜070 は変えない
- [x] 対応: NFR1.2・NFR1.3・NFR1.5・NFR2.1・NFR6.1・NFR9.5・NFR9.10、W13、E2E-1、基盤の設計の N7・N9・Q1 C・Q2 A、NFR 設計の Q1 A

### Step 18: E2E-1 — 実行と確かめ

- [x] WAR と Mailpit を用意し（Step 16 のまま）、`(cd frontend && caffeinate -i npx playwright test e2e/090-invitation-registration-flow.e2e.ts)` で 090 だけを流す（`unit-test-instructions.md` 2節）
- [x] 5回の時間（ミリ秒）と目標以内の回数、3つの確かめの結果、応答の形の照合の結果、Mailpit の取り出しの結果（1通・`baseURL` で始まる）を記録する。2 秒を超えた回があれば、要求の一覧と時刻で確かめの API・画面の塊と入口の読み込み・U4 のゲートの待ちのどれが遅いかを切り分けて記録し、承認の場で依頼者に相談する（目標を緩めない。時間は関門にしない。NFR 要件の2節）
- [x] `frontend/test-results/e2e-results.json` を値の文字列で検索し、`#token=`・`token=`・宛先の接頭辞 `e2e-invitee-`・パスワードの接頭辞 `e2e-invitee-pw-`・アクセストークン（`eyJ`）・初期管理者のメールアドレスが入らないことと、`test.step` の題が json の `steps` に入るかを記録する（基盤の設計の N9）。報告の部品の仮の資格情報の確かめも通ること。結果のファイルはコミット・共有しない
- [x] A9（閲覧の履歴）の確かめ: 9節の決定 2 のとおりに1回だけ確かめ、結果（残る・残らない・そのブラウザが履歴を持たない）を記録する。生成の担当の作業の場所（スクラッチパッド、リポジトリの外）の使い捨ての確かめの台本で、`chromium.launchPersistentContext(<一時のプロファイル>)` で 090 と同じ手順で得たリンク（使い終える前の招待）を開いて閉じ、Node の組み込みの `node:sqlite` でプロファイルの `History` の `urls` の表を読み取り専用で開き、`#token=` を含む行の件数だけを数える（値は出さない）。終わったらプロファイルを消す。台本はコミットしない
- [x] 090 を2回続けて流し、どちらも通ることを確かめる（実行ごとに違う宛先。不安定な検査を残さない）
- [x] Mailpit は止めず消さない（README の手順のとおり開発者が片付ける。Step 22 でも使う）
- [x] 対応: NFR1.3・NFR6.1・NFR9.10、基盤の設計の N9、NFR 設計の承認の場の A9

### Step 19: ビルドの成果物の確かめ（CSP・Referrer-Policy・埋め込み・依存・大きさの前後）

- [x] `backend/src/main/resources/application.yaml` の CSP と `backend/src/main/java/cherry/mastersmith/config/SecurityConfig.java` の `Referrer-Policy` に差分が無いこと（`git diff --stat` に出ない）を確かめる（NFR1.4・NFR9.5）
- [x] ビルドした `frontend/dist/index.html` に埋め込みのスクリプト・スタイルが無いこと（`<script>` は `src` つきだけ、`<style>` なし）と、`frontend/src/features/registration/` に外部の URL（`http:`・`https:` の値）が無いことを確かめる（NFR1.4・NFR9.5）
- [x] `frontend/package.json`・`frontend/package-lock.json` に差分が無いこと（NFR9.6）と、`vendor/make-you-chic-ui` の固定先と中身に差分が無いこと（`git submodule status`・`./gradlew vendorUnchanged`、NFR9.7）を確かめる
- [x] 変更の後の値を記録し、Step 1 と比べる: 初回の JavaScript（gzip、500 KB の目安の警告の有無）、登録の完了の画面の塊が入口と別のファイルに出ること（`dist/.vite/manifest.json` で `src/features/registration/RegistrationPage.tsx` が動的な読み込みの塊で、入口から静的にたどれないこと）とその塊の大きさ（圧縮前と gzip）、`shared/validation` が入口に入らないこと、`dist/assets/` の JavaScript の合計、WAR の大きさ（NFR6.3、`performance-design.md` 3節）
- [x] 対応: NFR1.4・NFR6.3・NFR9.5〜NFR9.7

### Step 20: 静的検査とカバレッジ

- [x] `./gradlew frontendFormatCheck frontendLint frontendLintCss frontendLicenseCheck frontendTypecheck` を通す（Prettier・oxlint・ESLint（`react-hooks`）・Stylelint・ライセンスヘッダー・`tsc`。`frontend/e2e/` の新しいファイルも対象。見本と `registrationApi.ts` の型の食い違いはここで止まる）。リンタの決まり（`react/no-danger` など）を緩めない
- [x] `./gradlew frontendCoverage` でフロントエンドのカバレッジの下限（行 80%・分岐 70%）を満たすことを確かめ、全体の値と U6 の新しいファイル（`shared/validation/` を含む）の値を記録する。計測の除外を増やさない（`frontend/e2e/` は既存どおり計測の対象外。NFR9.8）。届かないときはテストを足し、それでも届かなければ生成を止めて諮る（9節の決定 5 の (d)）
- [x] 対応: NFR9.8、`team.md` の Code Style・Testing Posture

### Step 21: 文書（README・features/README）

- [x] `README.md` に新しい節「登録の完了の画面（U6）」を足す: 画面の置き場と開き方（招待メールのリンク `/register#token=…`、ログインなし、アプリの枠の外）、トークンの扱い（開いた直後にアドレス欄から消し画面のメモリだけに持つ、読み込み直すと「このリンクは使えません」になりメールのリンクを開き直せば続けられる、閲覧の履歴の確かめの結果（Step 18））、ログインしたまま開いたとき（案内と「ログアウトして続ける」）、確かめの失敗の表示（使えないリンクと読み込めないの違い）、表示の設定（開いた時点は招待の言語とブラウザの保存のテーマ・文字の大きさ、選んだ時点で当てる、完了でブラウザに保存）、完了の後（自動でログインしない、ログインの画面の案内とメールアドレスの持ち越し）、入力の確かめの関数の置き場（`frontend/src/shared/validation/`、U7 と共用）、契約との差（C6 の `fieldErrors` は読まない）、ブランドカラーの既知の制約が U6 の primary のボタンにも当たること（当たる名前は Step 16 で確かめたもの）
- [x] 「画面の表示の設定（U4）」の「既知の制約（ブランドカラーのコントラスト）」の節に、U6 の画面も当たることと、070 の検査が状態ごとの一覧（`REGISTRATION_KNOWN_VIOLATIONS`）でこれを既知の違反として扱うことを書く
- [x] 「ビルドした WAR での画面の確認（E2E）」の表に `070-registration-accessibility.e2e.ts`（登録の完了の画面の 20 組×2 状態のアクセシビリティの検査と横のはみ出し。ログインしない。確かめの API だけを見本に差し替え、完了は送らない）と `090-invitation-registration-flow.e2e.ts`（この Intent の代表の流れ「管理者でログイン → 招待 → Mailpit からリンクを取り出す → 登録の完了 → 新しい利用者でログイン → 管理画面に入れない → ログアウト」と、リンクを開いてからフォームまでの時間の測定（5回））の行を足し、「070 について」「090 について」を書く（流れの本数に数えるのは 090 だけ、時間は記録だけで失敗させない、090 は招待を1件置き Mailpit に1通届く、090 は Mailpit の API を読むだけで消さない、070・090 だけを流すときも Mailpit の起動が要る、HTML の報告と失敗のときのトレースには使い捨てのトークンを含むリンクが載るため共有しない、片付けは既存の「手元でメールを見る」の2行）
- [x] `frontend/src/features/README.md` に「入力の確かめの関数（`src/shared/validation/`）」の節を足す: 関数の名前・誤りの種類の union・定数、文言は機能ごとに持つこと、U7 が使うこと
- [x] 対応: `cicd-pipeline.md` 4.5・5節、`infrastructure-specification.md` 4節、基盤の設計の承認の場の U7 R-01・D7・D8、2.1 の U7 R-01

### Step 22: E2E の全件（統合の前。`team.md` の Testing Posture）

- [x] Mailpit を起動したまま（止まっていれば `docker compose --profile mail up -d mailpit`）`caffeinate -i ./gradlew e2eTest` を流し、既存の 010〜040 の 6 件・050 の 21 件・060 の 21 件・070 の 20 件・090 の 1 件がすべて通ることを確かめる。U5 の変更の後に E2E-1 と既存の E2E（010〜060）が通ることを記録する（U5 の引き継ぎ、NFR9.11）
- [x] `e2eTest` の全体の時間と、検査の部分（050・060・070）と流れの部分（010〜040・090）の時間を分けて記録する（NFR 要件の承認の場の U6 R-01、`performance-design.md` 4節）。090 の5回の時間を Step 18 の値と並べて記録する。Mailpit は止めず消さない
- [x] 対応: NFR9.10・NFR9.11、`team.md` の Testing Posture（画面・認証に関わる変更を統合する前に E2E）

### Step 23: 1コマンドの検査（統合の前の関門）

- [x] colima を動かし、README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡して `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を実行し、すべての段が通り、対象DB のテストが SKIPPED でないことを確かめる
- [x] テストの件数（バックエンド・フロントエンド）と、フロントエンドの全体のカバレッジと、バックエンドの全体のカバレッジ（変わらない見込み）を実測の数字で記録し、Step 1 と比べる（既存のテストが減っていない・失敗していない）
- [x] 秘密情報の検出（Gitleaks）と依存関係の脆弱性検査（OSV-Scanner）が通ることを確かめる（lockfile を変えないため OSV-Scanner は UP-TO-DATE になりうる。依存を変えていないことを Step 19 で確かめているため、UP-TO-DATE のときはその旨を記録する）。`vendorUnchanged` が通ることを確かめる
- [x] 対応: B5 の共通の完了の条件、`project.md` の Mandated（統合の前の確認）

### Step 24: 記録とコミットの提案

- [x] `code-summary.md`（作ったもの、上流と計画との差、Step 1・Step 23 の実測、Step 2 の前提の確かめ（固定先・口・U4 の受け渡し・U3 の API の形・振り分け・ログアウト）、Step 16・18・22 の 070・090 の結果と時間と json の確かめ、Mailpit の API の実物の形、A9 の確かめの結果、Step 19 の成果物の確かめと大きさの前後、9節の決定の反映、既知の違反の一覧に足した名前、U7 に渡す `shared/validation` の関数と union の一覧）、`source-manifest.json`（作った・変えたアプリのソースのすべてのパス）、`traceability.json`（機能設計の OK 21 件と NFR の枝番から手順と部品へ。Deferred は機能設計の分け方にそろえる）を作る
- [ ] 3節の C1〜C6 の区切りで、コミットの内容とメッセージの案を依頼者に示して承認を得る（生成の担当はコミットしない）
- [ ] 対応: 段の記録、`project.md` の Change Control

## 6. ストーリー・要件と手順の対応

| ストーリー・要件 | 受け入れ基準・決まり・NFR | 手順 |
|---|---|---|
| US3.2 招待のリンクから登録を完了する | AC3.2.1（W1・W5、ログインなしの単独の画面、招待の言語、初期値、テーマと文字の大きさは保存の値） | Step 3・11〜14 |
| US3.2 | AC3.2.2（W4・W10・W11、理由によらず同じ使えないリンクの表示と導線） | Step 7・8・11〜14 |
| US3.2 | AC3.2.4（W7、パスワードの境界） | Step 5・6・11〜14 |
| US3.2 | AC3.2.5（W9、自動でログインしない・ログインの画面へ）、E2E-1 で新しい利用者のログイン | Step 13・14・17・18 |
| US3.2 | AC3.2.9（W7、氏名の境界と空白だけ） | Step 5・6・13・14 |
| US3.2 | AC3.2.10（W10、400 の後に送り直せる） | Step 7・8・13・14 |
| US3.2 | AC3.2.11（W10・W11、完了の 404） | Step 7・8・13・14 |
| US3.2 | AC3.2.16（W5、変えられないメールアドレスと `autocomplete="username"`） | Step 11・12 |
| US3.2 | AC3.2.17（W9、受け渡しとログインの画面の案内、URL にメールアドレスが無い） | Step 13・14・17・18 |
| US3.2 | AC3.2.18（W9、選んだ3つを保存しログインの画面がその値） | Step 13・14 |
| US3.2 | AC3.2.3・AC3.2.6〜AC3.2.8・AC3.2.12〜AC3.2.15 | Deferred（u3-invitation、機能設計の網羅の記録のとおり） |
| E2E-1 招待から登録の完了まで | W13（管理者でログイン → 招待 → Mailpit からリンク → 登録の完了 → 新しい利用者でログイン → 管理画面に入れない → ログアウト） | Step 17・18・22 |
| CR1 言語の適用 | CR1.2・CR1.3・CR1.4（W5・W6、選んだ言語の文言・`<html lang>`・`Accept-Language`） | Step 3・11〜14 |
| CR1 | CR1.1・CR1.5 | Deferred（u4-display-foundation） |
| CR6 画面の共通の決まり | CR6.1〜CR6.6・CR6.9（D8〜D10、W5・W7・W8・W10・W11。CR6.4 の成功の Toast の差は機能設計 10節） | Step 11〜14 |
| CR6 | CR6.7・CR6.8 | Deferred（u5-invitation-ui） |
| 招待のトークン・メールアドレス・列挙の防止 | NFR1.1〜NFR1.5・NFR2.1・NFR3.1 | Step 7〜10・13〜18 |
| 性能 | NFR6.1（Step 17・18・22 で記録、Build and Test に引き継ぐ）、NFR6.2（Step 11〜14）、NFR6.3（Step 1・13・19） | Step 1・11〜14・17〜19・22 |
| アクセシビリティ | NFR7.1・NFR7.2・NFR7.4（Step 11〜14）、NFR7.3・NFR7.5（Step 15・16・22） | Step 11〜16・22 |
| 多言語 | NFR8.1・NFR8.2 | Step 3・11〜14 |
| 画面の側のセキュリティ | NFR9.1（Step 9・10）、NFR9.2（Step 7・8・14）、NFR9.3（Step 5・6・11〜14）、NFR9.4（Step 13・14）、NFR9.5（Step 11・15〜19） | Step 5〜19 |
| 依存と make-you-chic-ui | NFR9.6・NFR9.7 | Step 2・19・23 |
| テストとカバレッジ | NFR9.8（Step 20・23）・NFR9.9（Step 6・8）・NFR9.10（Step 17・18・22）・NFR9.11（Step 16・22・23） | Step 4〜8・15〜18・20・22・23 |

## 7. テストの量（Standard）

部品ごとに 5〜8 件の単体テストと、境界の確かめ（ApiClient と `fetch`、U4 の口とログイン状態、make-you-chic-ui の部品との結び目、実際のブラウザの検査、E2E-1）を置く。成功の場合と、少なくとも2つの失敗・境目の場合を含める（`phases/construction.md` の Testing Standards）。画面部品ごとに vitest-axe の検査を1件入れる（`team.md` の Testing Posture）。

| 部品 | 単体（Vitest） | 実際のブラウザ（Playwright） |
|---|---|---|
| コードポイントと前後の除去（`shared/validation/codePoints.ts`） | `codePoints.test.ts` 5 件＋性質ベース 3 件 | — |
| 氏名の確かめ（`validateDisplayName.ts`） | `validateDisplayName.test.ts` 6 件＋性質ベース 1 件 | — |
| パスワードの確かめ（`validatePassword.ts`） | `validatePassword.test.ts` 7 件＋性質ベース 1 件 | — |
| トークンの取り出し（`registrationToken.ts`） | `registrationToken.test.ts` 5 件＋性質ベース 1 件 | — |
| 失敗の振り分け（`failureKind.ts`） | `failureKind.test.ts` 6 件（5節の表の全行をまとめる） | — |
| フォームの誤り（`formProblems.ts`） | `formProblems.test.ts` 5 件 | — |
| API（`registrationApi.ts`） | `registrationApi.test.ts` 7 件 | — |
| `RegistrationStatus` | `RegistrationStatus.test.tsx` 5 件（vitest-axe を含む） | — |
| `RegistrationUnavailable` | `RegistrationUnavailable.test.tsx` 5 件（vitest-axe を含む） | — |
| `RegistrationLoggedInNotice` | `RegistrationLoggedInNotice.test.tsx` 5 件（vitest-axe を含む） | — |
| `RegistrationForm` | `RegistrationForm.test.tsx` 8 件（vitest-axe を含む） | — |
| `RegistrationPage` と `useRegistration` | `RegistrationPage.test.tsx` 20〜26 件（開く 2・トークン 3・確かめの失敗 3・ログイン中 3・選ぶ 2・確かめの誤り 3・送信 1・完了と失敗 4・離れる 1・漏えい 2・axe 1。応答ごとの動きが多いため Standard の目安を超える） | 070（`ready` の状態）、090 |
| 機能の登録と文言（`registration.ts`・`messages.ts`） | `registration.test.tsx` 5 件 | — |
| 実際のブラウザの検査（`070-registration-accessibility.e2e.ts`） | — | 20 組のテスト 20 件（1組に2つの状態） |
| E2E-1（`090-invitation-registration-flow.e2e.ts`） | — | 1 件（流れ、5回の測りと3つの確かめ、応答の形の照合、Mailpit からの取り出し） |

この Intent の代表の流れの E2E は 090 の1本だけ。070 は流れではないため本数に数えない（NFR9.11）。

## 8. この計画で決めたこと・承認済みの文書との差

承認済みの文書は書き換えず、差をここと `code-summary.md` に記録する（`project.md` の決まり）。

| 対象 | 承認済みの形 | この計画での扱い | 理由 |
|---|---|---|---|
| 層の順番 | Testing Contract の `plan_profile.steps` は「データの形 → DB アクセス → 業務処理 → API → 画面の振る舞い」 | データの形（DB）とデータアクセスの層は無し。業務処理（共用の確かめの関数 → 画面の純粋な関数）→ API → 画面の振る舞い（画面部品 → 画面と状態と登録）→ 070 → 090 の順 | U6 は内部DB とブラウザの新しい保存を持たない。共用の関数を先に固定して U7 に渡す（test-after の方法は変えない） |
| 検査のファイルの番号 | 「B5 のコード生成の計画で U5・U7 とそろえる」（`logical-components.md` 5.1、LC-D2） | `070-registration-accessibility.e2e.ts`・`090-invitation-registration-flow.e2e.ts` | U5 の計画の決定 2 で決まった（承認の場の U6 R-02・U7 R-02） |
| 070 の組の当て方 | U4 の検査の作り（初めのスクリプトで U4 の鍵を置く） | そのまま（`prepareCombo`）。U5 の `loginPreferences.ts` は使わない | 登録の完了の画面はログインしないため、ブラウザの保存の値が当たる（U5 の `code-summary.md` の7節） |
| 既知の違反の一覧の形 | U4・U5 の `axe.ts` | `REGISTRATION_KNOWN_VIOLATIONS`（状態ごとの名前）を足す。050・060 の一覧と判定は変えない | U5 の決定 4 と同じ形 |
| Mailpit を片付けるコマンドの書き方 | 基盤の設計 `cicd-pipeline.md` 4.5 は `docker compose rm -sf mailpit` | README の「手元でメールを見る」の2行（`docker compose stop mailpit`・`docker compose rm -f mailpit`）にそろえる | U5 の計画の決定 5（基盤の設計の承認の場の U7 R-01） |
| 文言の置き場 | 部品 2節は `registration.ts` に文言 ja・en | `messages.ts`（`registrationMessages`）に分け、`registration.ts` から渡す | U5・DSL と同じ形。登録の形は変わらない |
| 文言の引き方 | 部品 5節の `useMessages()` | そのまま（埋める値が無いため U5 のような専用の関数は作らない） | — |
| `RegistrationPage` の vitest-axe の範囲 | NFR 要件の NFR7.2「確かめ中・使えない・ログイン中の案内・読み込めないの各表示をどう含めるかはコード生成で決める」 | `RegistrationPage` の検査は `ready` の画面全体（Card・h1・フォーム）で1件。確かめ中・読み込めないは `RegistrationStatus`、使えないは `RegistrationUnavailable`、ログイン中の案内は `RegistrationLoggedInNotice` のそれぞれの検査で見る | 画面部品ごとに1件の決まりのとおりで、どの表示も1回は検査に掛かる |
| 確かめの応答の形が違うとき | 部品 3節「形の誤りとして `loadFailed` と同じ扱い」 | `registrationApi.ts` が通信の失敗の `ApiError` として投げ、`failureKind` で `loadFailed` に振り分ける | 振り分けを `failureKind` の1か所に保つ |
| 共用の手伝いの名前 | 基盤の設計「名前はコード生成で決める。例 `mailpit.ts`」、NFR 設計「例 `support/registrationFixtures.ts`」 | `support/mailpit.ts`（`findInvitationLink`）・`support/registrationFixtures.ts`（`VERIFY_SAMPLE`・`A11Y_SAMPLE_TOKEN`・`shapeOf`） | 例の名前のまま |
| Mailpit の API の場所の定数 | 基盤の設計「U1 の前提の確かめと同じ1か所の定数を共有し、2か所で値を持たない」 | TS の側は `support/mailpit.ts` に1つ置き、Gradle の `mailpitInfoUrl` とはコメントで互いに参照する（`build.gradle.kts` にはコメント1行だけを足し、動きは変えない）。値は `mailpit.ts` と `build.gradle.kts` の2か所になる（9節の決定 3） | Gradle（Kotlin）と Playwright（TypeScript）の間で1つの値を読み合う仕組みが無く、ビルドの仕組みを ui の単位で変えないため |
| E2E-1 でリンクを開くブラウザ | W13 は「リンクを開く」とだけ書き、管理者のログインの扱いを決めていない | 招待の後に管理者のコンテキストを閉じ、新しいコンテキスト（招待された人のブラウザ）で開く（9節の決定 1）。W3 は画面部品のテストで確かめる | 管理者のコンテキストのまま開くと W3 のログイン中の案内になるため |
| E2E-1 の招待の出し方 | W13「招待の画面（U5）で招待」 | U5 の画面から招待する（U5 の測定の `invitationSeed.ts` の API は使わない） | 代表の流れは画面を通る。U5 の画面の `data-testid` を使う |
| E2E-1 の追加の確かめ | W13・NFR1.3・NFR6.1 | 完了の後のログインの画面で localStorage の表示の設定の値と、トークン・パスワード・宛先が無いことも見る | 画面部品のテスト（jsdom）の実際のブラウザでの裏付け。読み取りだけで費用が小さい（`performance-design.md` の PD-D3 と同じ考え） |
| 閲覧の履歴（A9） | 機能設計 W2 の4（「フラグメントの無いアドレスだけが残る」）、承認の場で「B5 で実際の動きを確かめて記録する」 | 生成の中で1回だけ確かめて記録し、E2E に常設しない（9節の決定 2） | 閲覧の履歴の記録はブラウザと起動の形（headless など）に依存し、常設の検査にすると不安定になりうるため |
| U7 の「招待から利用者を作る関数」 | U7 の基盤の設計「利用者を作る関数は U6 の E2E-1 と同じものを使う」 | U6 は `support/mailpit.ts` を作り、API で登録を完了する関数は U7 の計画で足す（9節の決定 4） | U6 の E2E-1 は画面で登録を完了するため、API で完了する関数を使わない |
| `RegistrationPage.test.tsx` の件数 | Standard の目安は部品ごとに 5〜8 件 | 20〜26 件 | 応答ごとの動き（5節）と漏えいの確かめを1つの画面で確かめるため。目安を超える側で、減らさない |
| 機能どうしの依存 | 機能設計 10節（registration → auth の1本） | そのまま。今のコードに機能どうしの読み込みが無いため、これが最初の1本 | 承認済み（Q1 A） |

## 9. 依頼者の決定

計画の承認の前に諮った論点について、依頼者がすべて推奨のとおり（A）に決めた。計画の各 Step と8節はこの決定に合わせてある。

1. **E2E-1 で招待のリンクを開くブラウザ**: A。招待の後に管理者のコンテキストを閉じ、新しいコンテキスト（招待された人のブラウザ）でリンクを開いて登録を完了する。実際の使われ方（招待された人は自分のブラウザで開く）に合い、流れの最後の「新しい利用者でログイン」とも食い違わない。ログインしたまま開いたときの案内（W3）は画面部品のテストで確かめる（Step 14・17）。
2. **閲覧の履歴（A9）の確かめ方**: A。生成の中で1回だけ確かめて記録する（Step 18）。スクラッチパッドの使い捨ての台本で、使い捨てのプロファイル（`chromium.launchPersistentContext`）でリンクを開いて閉じ、Node の組み込みの `node:sqlite` でプロファイルの `History` を読み取り専用で開き、`#token=` を含む行の件数だけを数える（値は出さない、プロファイルは消す、台本はコミットしない、新しい依存は足さない）。結果（残る・残らない・履歴を持たない）を `code-summary.md` と README に書き、残るときは機能設計を書き換えず残る危険として記録する（承認の場の A9 の決定のまま）。090 には常設しない。
3. **Mailpit の API の場所の定数（基盤の設計の「1か所の定数」）**: A。TypeScript の側は `frontend/e2e/support/mailpit.ts` に1つだけ置き、Gradle の `build.gradle.kts` の `mailpitInfoUrl` とはコメントで互いに参照する（`mailpit.ts` の定数のコメントに `mailpitInfoUrl` を、`build.gradle.kts` の `mailpitInfoUrl` の上に `mailpit.ts` を書く。`build.gradle.kts` はコメント1行だけで、動きは変えない）。値は2か所になるが、どちらも `127.0.0.1:8025`（`compose.yaml` の公開の番号）に結び付く。基盤の設計の「2か所で値を持たない」との差として8節と `code-summary.md` に記録する（Step 17）。
4. **U7 が使う「招待から利用者を作る」手伝い**: A。U6 は `frontend/e2e/support/mailpit.ts`（Mailpit からのリンクの取り出し）を作る。招待は U5 の `invitationSeed.ts` を使い、API で登録を完了する関数（リンクからトークンを取り出して `POST /api/registration/complete` を呼ぶ）は、それを使う U7 の計画で足す。U6 の E2E-1 は画面で登録を完了するため、U6 には使わない関数を置かない（「Build and Test に引き継ぐこと」の U7 の行）。
5. **生成を止めて諮る場面**: A。次のときは、生成をその手順で止め、結果と候補を示して依頼者に諮る。
   - (a) Step 2 で、U6 が使う make-you-chic-ui・U4・U3 の口が無い・形が違う、またはリンタに機能どうしの読み込みを止める決まりがある
   - (b) Step 16 で、U6 の画面に、既知の違反（green・orange の組の primary の Button）の外の axe の違反が出て、U6 のコード（CSS・部品の選び方）で直せない、または直すのに make-you-chic-ui の変更が要る
   - (c) Step 16 で、375px の組の横のはみ出しが make-you-chic-ui・`StandaloneLayout` に因り、U6 のコードで直せない
   - (d) Step 20 でテストを足してもフロントエンドのカバレッジの下限（行 80%・分岐 70%）に届かない
   - (e) 8節に挙げたもの以外に、既存の画面のテスト・既存の E2E（010〜060）の期待や、U6 の範囲の外（骨組み・ApiClient・`features/auth`・`features/invitation`・`playwright.config.ts`・サーバー）を変える必要が出る（緩めずに作れる形を先に探す）
   - (f) Step 17・18 で、Mailpit の `GET` の search・message の API が使えない、本物の確かめの応答と見本の形が一致しない、または json の結果にトークン・宛先・パスワード・アクセストークンが入り、手伝いの側で防げない
   - なお、リンクを開いてからフォームまでの時間が目標（2 秒）を超えたときは、止めずに切り分けて記録し、承認の場で相談する（時間は関門にしない）
6. **作業の場と統合の単位**: A（U5 の計画の決定 1 のとおり）。`feature/260925-user-management-b5-u6` を作り、U6 の分を squash で `develop` へ統合する。コミットは生成の後に依頼者の承認を得て 3節の C1〜C6 でまとめて行う。push は依頼者。

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

Testing Contract の `plan_profile.steps` との対応: 骨組みと本番の設定は Step 1〜3（作業の場 Step 1・前提の確かめ Step 2・型と文言 Step 3）、テストの実行の準備は Step 4（最初のテストの Step 6 より前）、データの形（DB）とデータアクセスの層は U6 に無い（内部DB とブラウザの新しい保存を持たない）、業務処理は Step 5〜8（共用の確かめの関数 Step 5・6 → 画面の純粋な関数 Step 7・8）、API（`registrationApi`）は Step 9・10、画面の振る舞いは Step 11〜14（画面部品 → 画面と状態と登録）と実際のブラウザの検査 Step 15・16・E2E-1 Step 17・18、環境とビルドの確かめは Step 19・20・22・23、文書と記録は Step 21・24。

## Build and Test に引き継ぐこと

| 項目 | 引き継ぐ内容 | 持ち主 |
|---|---|---|
| カバレッジの実測 | `:backend:cleanTest :backend:cleanIntegrationTest` を付けた verify で、フロントエンドの全体と U6 の新しいファイル（`shared/validation/` を含む）、バックエンドの全体の値をもう一度実測して記録する | Build and Test |
| verify の時間 | U6 の後の `./gradlew verify` の時間を測り、U5 の後の実測（6 分 2 秒）と比べる | Build and Test |
| リンクを開いてからフォームまでの時間（NFR6.1） | `./gradlew e2eTest` の 090 の5回の値と目標以内の回数を `frontend/test-results/e2e-results.json` から写して記録する（統合の関門にしない。目標を超えたら切り分けて依頼者に相談、目標を緩めない）。運用の中での判定は `Unverified` | Build and Test（記録）・observability-setup・feedback-optimization（運用の判定） |
| 実際のブラウザの検査（NFR7.3・NFR7.5） | 070 の 20 組×2 状態の成否・違反の件数と規則の名前・既知の違反・`incomplete` を json の報告から写して記録する。json に見本・秘密の値が入らないことをもう一度確かめる | Build and Test |
| E2E-1（NFR9.10） | 090 の成否、3つの確かめ（アドレス欄・CSP・ブラウザの保存）、応答の形の照合、json の確かめ（N9）を記録する | Build and Test |
| e2eTest の時間 | 全体の時間と、検査の部分（050・060・070）と流れの部分（010〜040・090）を分けて記録する（NFR 要件の承認の場の U6 R-01。見込みは検査の部分で約 3〜8 分） | Build and Test |
| CSP（NFR9.5） | 070 の各組と 090 の5回・流れの各コンテキストで CSP の違反が 0 件であることを記録する | Build and Test |
| 配信物の大きさ（NFR6.3） | コード生成の前後の値（`code-summary.md`）を Build and Test の結果に写す。上限は置かない（500 KB の目安の警告だけ） | Build and Test |
| 登録の API の時間 | 確かめ・完了の成功とも同時 10 件で p95 1 秒（U3 の NFR6.3・NFR6.4、U3 の引き継ぎのとおり k6） | performance-validation（無ければ Build and Test、`project.md` の学び） |
| 閲覧の履歴（A9） | Step 18 の確かめの結果を残る危険の記録に写す。ブラウザと起動の形に依存する旨を残す | Build and Test（記録） |
| 配備と戻し | イメージを作り直して `docker compose up -d`、ヘルスチェックとスモークテスト（フラグメントの無い `/register` を `http://localhost:8080` で開いて「このリンクは使えません」の表示が出ること。API を呼ばず内部DB・監査を変えない）。招待から登録の完了までの配備の後の確かめは内部DB に利用者と監査の行が残るため、送る前に依頼者に伝える。戻しはイメージだけで、戻している間に届いた招待のリンクは開けない（トークンは有効期限まで使えるため、戻しを直せば同じリンクで続けられる）（`cicd-pipeline.md` 5節・6節） | deployment-pipeline（手順）・deployment-execution（実行） |
| U7 への引き継ぎ | `shared/validation` の関数の名前・誤りの種類の union・定数（`code-summary.md` の一覧）と U7 の `errorMessages.ts` の表の最終の突き合わせ（2.1 の U7 R-01、記録は U7 の計画）。検査のファイルの番号 080、共用の手伝い `support/mailpit.ts`・`support/registrationFixtures.ts` と U5 の手伝い、9節の決定 4（API で登録を完了する関数は U7 の計画で足す）、Mailpit の片付けの書き方、ログインの後の組の当て方（U5 の `loginPreferences.ts`）。U7 の飛ばす道は念のための道（基盤の設計の N10） | B5（U7 のコード生成の計画） |
