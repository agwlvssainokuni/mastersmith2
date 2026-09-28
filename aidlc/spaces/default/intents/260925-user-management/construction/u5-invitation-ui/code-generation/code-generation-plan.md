# Code Generation Plan — U5 招待の管理の画面（u5-invitation-ui）

U5 のコード生成の計画を示す。作るものは、管理者が招待中の人を一覧で確かめ、招待・送り直し・取り消しを行う画面（新しい置き場 `frontend/src/features/invitation/`、画面 S1・S1-M1・S1-M2、画面の URL `/admin/invitations`、サイドバーの「利用者の招待」）と、日時の書式の関数 `formatDateTime` の共通の置き場への移動（`frontend/src/features/dsl/format.ts` → `frontend/src/shared/format/`）、実際のブラウザのアクセシビリティの検査と画面の時間の測定（`frontend/e2e/` の新しいファイル）である。Bolt は B5（U5・U6・U7 の画面、`inception/delivery-planning/bolt-plan.md`）で、U5 はその最初の単位。B1（U1）・B2（U2）・U8・B3（U3）・B4（U4、make-you-chic-ui の固定先は `735ef04`）は `develop` に統合済みで、先頭は `fd44e79`。U5 は ui の単位で、サーバーのコード・内部DB・Flyway・`vendor/` の中身を変えない。

この計画の中のパスは、特に断らない限りリポジトリのルートからの相対パスで、記録のディレクトリは `aidlc/spaces/default/intents/260925-user-management/`（以下「記録」）とする。既存のコードの説明文にある「U1〜U5」は前の Intent の単位番号のことがあり、この計画の U5 は u5-invitation-ui を指す。

## 1. 入力にした設計

| 文書（記録の下） | 使うところ |
|---|---|
| `construction/u5-invitation-ui/functional-design/functional-spec.md`・`frontend-components.md`・`functional-design-questions.md`・`traceability.json` | 置き場と登録（2節）、決まり D1〜D14、状態の移り変わり（4.1〜4.3）、画面の流れ W1〜W10、応答ごとの動き（6.1〜6.4）、文言（7節）、テストの方針（8節）、上流との差 (a)〜(d)・(f)〜(j)、承認の場の直し（11節の1〜7）、部品の階層（部品 1節）、置き場ごとのモジュール（部品 2節）、`useInvitationAdmin` の状態と操作とフォーカスの行き先（部品 3節）、props と state（部品 4節）、API との受け渡し（部品 5節）、機能の登録（部品 6節）、テストで確かめる内容（部品 7節）、既存への影響（部品 8節）、答え Q1 A・Q2 B・Q3 A、網羅の OK 37 件・Deferred 9 件 |
| `construction/u5-invitation-ui/nfr-requirements/` の全文書と `nfr-requirements-questions.md` | NFR1.1・NFR2.1、NFR6.1〜NFR6.6、NFR7.1〜NFR7.4、NFR8.1・NFR8.2、NFR9.1〜NFR9.9、答え Q1 B（幅 375px の6組を検査に足す）・Q2 B（一覧 2 秒・次のページ 1.5 秒、測って記録し関門にしない） |
| `construction/u5-invitation-ui/nfr-design/` の全文書と `nfr-design-questions.md` | 部品の一覧と失敗の範囲（`logical-components.md` 1〜4節）、実際のブラウザの検査の置き場・組・状態の出し方・ログインの開き方・記録（同 5節）、Table の足りない口の回避（同 6.2）、テストの作り（同 7節）、一覧の読み方と重なった読み直し（`performance-design.md` 2節）、待ちの見せ方（同 3節）、画面の時間の測り方と 21 件の招待の用意（同 4節）、初回の JavaScript（同 5節）、トークン・URL・メールアドレスの扱い・失敗の文言と応答の読み方・204・認可・CSP・検査の差し替えの安全・依存（`security-design.md` 1〜7節）、答え Q1 A・Q2 A・Q3 A |
| `construction/u5-invitation-ui/infrastructure-design/` の全文書と `infrastructure-design-questions.md` | 実行の形・配信とキャッシュ・CSP・ブラウザの保存を変えない（`infrastructure-specification.md`）、`./gradlew verify` の段と関門・依存・統合の形・検査のファイルの中身・測定の招待の用意・Mailpit に届く 21 通・結果のファイルと秘密（`cicd-pipeline.md`）、SLI と `Unverified` の引き継ぎ（`monitoring-design.md`）、答え Q1 A（測定のメールは消さず README に書く） |
| `inception/contract-design/contract-summary.md` の C5・C9 と共通の決まり | 招待の管理の4本の API（一覧 200・招待 201・送り直し 200・取り消し 204）、`Invitation`・`InvitationPage` の項目、409 の `invitationId`・`page`、503 の `unavailableReasons`、表示の設定の口 |
| `inception/refined-mockups/`（`mockups.md` の S1・S1-M1・S1-M2、`interaction-spec.md` の 2〜4節、`accessibility-checklist.md`、`design-system-mapping.md`） | 画面の配置と部品の動き。承認済みの機能設計が上書きした点（9節の (a)・(f)・(j)）は機能設計を正とする |
| `inception/units-generation/unit-of-work.md`・`unit-of-work-story-map.md` | U5 の責務と境界（API は U3、表示の設定は U4、AccessControl と AdminArea は変えない）、US1.1 → US2.1 → US2.2 の順 |
| `inception/requirements-analysis/requirements.md`・`inception/user-stories/stories.md` | FR1・FR3・FR7・NFR7・NFR8、US1.1・US2.1・US2.2 の受け入れ基準、共通の決まり CR1・CR6 |
| `inception/delivery-planning/bolt-plan.md` の B5 | 完了の条件（3つの画面が画面イメージのとおり動き画面部品ごとにアクセシビリティの検査が通る、E2E の代表の流れ1本、ja・en の文言）、見せ方 |
| B1〜B4・U8 の `code-summary.md`（`construction/u1-mail/`・`u2-user-preferences/`・`u3-invitation/`・`u4-display-foundation/`・`u8-instance-appearance/` の `code-generation/`） | U3 の API の実際の形（`InvitationAdminController`・`InvitationResponse`・`InvitationPageResponse`、409 と 503 の追加の項目、`invitedBy` は氏名だけで行が無ければ空、400 の `fieldErrors`）、U3 の契約との差（README の「招待と登録の完了（U3）」）、U4 の口（`useDisplaySettings`・`LANGUAGE_NAMES`）と 050 の結果（20 組・既知の違反・時間 約 0.1 秒）、U4 の実測（フロントエンド 55 ファイル 408 件、行 98.04%・分岐 94.27%、初回の JavaScript 114.1 KB）、`frontend/playwright-secret-check-reporter.ts` |
| 既存のコード | `frontend/src/features/dsl/`（`useDslAdmin.ts`・`failureMessage.ts`・`useDslText.ts`・`messages.ts`・`format.ts`・`registration.ts`・`testing/renderDsl.tsx`・`DslStatusPanel.tsx`・`DslConfirmDialog.tsx`・`DslHistoryTable.tsx`）、`frontend/src/shared/api-client/`（`apiRequest`・`ApiError`・`problem`）、`frontend/src/app/`（`registry/types.ts`・`validateRegistrations.ts`・`display-settings/`・`testing/renderWithProviders.tsx`・`layout/ShellLayout.tsx`）、`frontend/e2e/010〜050` と `frontend/e2e/support/`（`axe.ts`・`displayCombos.ts`・`overflow.ts`・`pageProblems.ts`・`appearanceFixture.ts`）、`frontend/playwright.config.ts`（`adminEmail`・`adminPassword`・`webServer.env` の SMTP とベース URL）、`frontend/vitest.config.ts`・`tsconfig.json`（`include` に `e2e`）・`package.json`、`vendor/make-you-chic-ui` の `735ef04` の Table・Modal・Button・RadioGroup・AppShell、`backend/src/main/java/cherry/mastersmith/invitation/web/`、`backend/src/test/java/cherry/mastersmith/invitation/web/InvitationAdminApiIT.java`（`notConfigured`）、`README.md`（「ビルドした WAR での画面の確認（E2E）」「手元でメールを見る」「画面の表示の設定（U4）」） |

## 2. 承認の場の決定と、この計画での読み方

### 2.1 承認の場の決定（監査ログから洗い出したもの）

`project.md` の学び（計画の前に承認の場の決定を洗い出す）に従い、監査ログ（`audit/sakura-local-4e42a93f87ce.md`）の Functional Design・NFR Requirements・NFR Design・Infrastructure Design の `DECISION_RECORDED`・`GATE_APPROVED`・`GATE_REJECTED` と、レビューの記録（`.aidlc-reviews/*/units/u5-invitation-ui/*/1.json`）の指摘を読んだ。U5 と B5 に関わるものと、この計画での扱いは次のとおり。

| 段 | 決定・指摘 | この計画での扱い | 手順 |
|---|---|---|---|
| 機能設計（Request Changes、2026-09-27、U5 R-01 Major） | 送り直しの流れ（W8）を応答の表（6.3）に合わせる | 直した後の W8 と 6.3 のとおりに作る: 200・404・503 は今のページを読み直し、403・そのほかの 4xx・5xx・通信の失敗は読み直さず、フォーカスを同じ行の「送り直す」に残す。応答の種類ごとに画面のテストで確かめる | Step 13・14 |
| 機能設計（Request Changes、U5 R-02 Minor） | U3 R-02（有効期限の時間の数をメールへ差し込む）に頼ることを9節に書く | 機能設計の 9節の (i) で済み。画面の文言に「24 時間」を書かない（文言の確かめで、`invitation.*` の値に `24` を含まないことを見る） | Step 3・14 |
| 機能設計（Request Changes） | make-you-chic-ui の新しい部品（Table の `labels`、Modal の `closeOnBackdropClick`・`alertdialog`・`closeLabel`、Button の `aria-disabled` の `loading`、RadioGroup の `legend`・選択肢の `lang`）に置き換える | 素の `table` と自前のページ送り、背景の押下の見張りの複写は作らない。B4 で固定先 `735ef04` が入ったことと、使う口が `735ef04` にあることを Step 2 で確かめる（計画を書く時点で、`git submodule status` が `735ef04`、Table の `labels`・`aria-label`、Modal の `closeOnBackdropClick`・`role`・`closeLabel`・`initialFocusRef`、RadioGroup の `legend`・選択肢の `lang`、Button の `loading`（`aria-disabled`・`aria-busy`）があることを読み取りで確かめた） | Step 2・11 |
| 機能設計（承認の場、2026-09-27 Approve、G1） | 契約 C5 の `invitedBy` との差（U3 R-01）は後の段で契約を直す一覧に入れる | U5 は氏名だけを出し、空なら「（不明）」（D7）。契約の文書は書き換えない。U3 の README の「契約との差」に C5 の `invitedBy` があることを Step 2 で確かめ、U5 の README の節から参照する | Step 2・19 |
| 機能設計（承認の場、G3〜G5） | Table・RadioGroup で足りない点・弱まる点（行の class・`caption`・横の領域のフォーカスと名前・20 件以下のページ送り・読み込み中のページ送り・`th` の `scope`・`aria-describedby`）は受け入れて回避の作りで進め、追加は別に相談する | 回避の作り（見える見出し `h2` と Table の `aria-label`、`:has()` の印、行のボタンで横の領域に届く）で作る（NFR 設計の `logical-components.md` 6.2）。make-you-chic-ui への追加の相談は、この単位では行わない（9節の決定 6 の (b) で、回避で足りないときだけ止める） | Step 11・15・16 |
| 機能設計（承認の場、G6） | 契約への反映（C5 `invitedBy`・C8 `targetUserId` と `EMAIL_ALREADY_REGISTERED`・C10 有効期限の文言）は遅くともコード生成の計画で確かめる | U3 の `code-summary.md` の「契約との差」と README の「招待と登録の完了（U3）」の「契約との差」に C5・C8・C10 の差が書かれていることを、計画を書く時点で読み取りで確かめた。U5 が受ける側の差は C5 の `invitedBy` だけで、Step 2 で README の記述をもう一度確かめて `code-summary.md` に記録する | Step 2・22 |
| 機能設計のレビュー（承認の場、U5 R-01 Minor、Accepted risk） | 「横の領域は矢印のキーで横に動かせる」の記述は Table の実装（包む要素に `tabIndex` も `keydown` も無い）と食い違う。コード生成で実際の動きに合わせる | 矢印のキーの処理は作らない。確かめるのは、Tab で行の「取り消す」とページ送りのボタンに届き、フォーカスした要素が見える位置に入ること（Playwright の 375px の組で、フォーカスした「取り消す」が表示の幅の中にあること）。実際の動き（矢印のキーでの手動の横の移動は無い）を `code-summary.md` と README の U5 の節に書く | Step 12・15・16・19 |
| NFR 要件（承認の場、Minor 12 件の拾い上げ、U5 分） | 取り消しの成功は 204（R-01） | `cancelInvitation` は本文を読まず、`Response.ok` だけで成功とする。200・201 の読めない本文は通信の失敗、204 の空の本文は成功であることを `invitationApi.test.ts` で確かめる（`security-design.md` 3.3） | Step 9・10 |
| NFR 要件（Q1 B・Q2 B） | 幅 375px の6組を検査に足す。一覧 2 秒・次のページ 1.5 秒を測って記録し、関門にしない | 検査の 20 組に (c) を含め、測定のテストを同じファイルに置く。時間では失敗させない | Step 15・16 |
| NFR 設計（承認の場、U5 R-01 Minor） | 念のための検査の意図を書く（招待した管理者が空の行の見本は、今の API では起きない状態を確かめる防御のためのもの） | 一覧の見本（`frontend/e2e/support/invitationFixtures.ts`）と画面部品のテストの見本（`frontend/src/features/invitation/testing/fixtures.ts`）の、`invitedBy` が空の行に「契約 C5 では必須で、U2 で氏名は必須のため今の API では起きない。D7 の表示を確かめる防御のための行」とコメントを書く | Step 12・15 |
| NFR 設計（承認の場、U5 R-02 Minor） | B5 で飛ばす判定の前提を確かめる（招待を使える設定が無い WAR でも、ログインと一覧の API は成功すること） | 計画を書く時点で、U3 の結合テスト `InvitationAdminApiIT.notConfigured` が、設定の無い3通り（ベース URL が無い・SMTP が無い・両方無い）で、管理者のアクセストークンの取得と一覧の 200（`invitationEnabled: false` と理由）を確かめていることを読み取りで確かめた。E2E の初期管理者の用意（`frontend/playwright.config.ts` の `MASTERSMITH_AUTH_INITIAL_ADMIN_*`）は SMTP の設定に頼らない。この2点を 060 の飛ばす判定のコメントと `code-summary.md` に書く。E2E の WAR には SMTP とベース URL が渡る（`webServer.env`）ため、飛ばす判定は念のための備えであることもコメントに書く（NFR 設計の U5 R-01 と同じく意図を書く） | Step 2・15・16 |
| NFR 設計（承認の場、U6 R-02・U7 R-02） | 検査のファイルの番号は B5 でそろえる | 9節の決定 2 で B5 の4つのファイルの番号と並びを決め、U6・U7 の計画に引き継ぐ | Step 15、9節 |
| NFR 設計（承認の場、U7 R-01・U6 R-01、上流との差 A9） | U6 との突き合わせの手順を B5 の最初に決める、B5 で `handOffToLogin` を確かめる、閲覧の履歴（A9）を B5 で確かめる | どれも U6・U7 の画面の話で、U5 の画面は関わらない。U6・U7 のコード生成の計画に引き継ぐ（「Build and Test に引き継ぐこと」の B5 の行）。U5 は、B5 の単位の順（U5 → U6 → U7）と共用の手伝いの形（9節の決定 2）だけを決める | 9節 |
| NFR 設計（Q1 A・Q2 A・Q3 A） | 検査の状態は一覧の答えの差し替えで出す。測定は同じファイルで、招待は API で 21 件置く。ログインの後の画面はコンテキストごとにログインの画面から開く | そのとおりに作る。測定の準備は Playwright の要求の口でログインの API を呼び、画面の計測の各回はログインの画面から開く（`performance-design.md` の上流との差のとおり） | Step 15・16 |
| 基盤の設計（承認の場、U7 R-01 Accepted risk） | Mailpit を片付けるコマンドの書き方が U6 と違う。B5 で README の文言をそろえる | U5 が B5 で最初に README の E2E の節に手を入れるため、9節の決定 5 で1つの書き方に決め、U6・U7 の計画はそれに従う | Step 19、9節 |
| 基盤の設計（承認の場、D7） | U5・U6・U7 の E2E は Mailpit のメールを消さず、開発者が止めて消す | 060 は Mailpit の API に書き込まない。README に「測定のテストは1回の実行で 21 通を送る」ことと片付けの手順（決定 5 の書き方）を書く | Step 15・19 |
| 基盤の設計（承認の場、N2） | 050 だけでも Mailpit が要る（B4・B5） | 060 だけを流すときも Mailpit の起動が要ることを `unit-test-instructions.md` と README に書く | Step 16・19 |
| 基盤の設計（承認の場、N7） | Mailpit の API を実物で確かめる（B5） | U5 は Mailpit の API を読まない（測定のメールは送るだけ）。U6 の E2E-1 の計画に引き継ぐ | 引き継ぎ |
| 基盤の設計（承認の場、N9） | json の結果に操作の題が入るか（B5） | 060 の `test.step` の題と注記・添付に、アクセストークン・パスワード・初期管理者のメールアドレス・測定の招待のメールアドレスを入れない。Step 16 で json の結果を文字列で検索して確かめ、題が入る形（`test.step` の題が `steps` に入るか）を記録する | Step 15・16 |
| 基盤の設計（Q1 A） | 測定のテストが Mailpit に置く 21 通は消さない | そのとおり。README に書く | Step 19 |

### 2.2 この計画での読み方

- **設計の文書どおりに作るもの**: 部品の階層（部品 1節）、置き場ごとのモジュール（部品 2節）、`useInvitationAdmin` の状態・操作・フォーカスの行き先（部品 3節）、props と state（部品 4節）、API との受け渡し（部品 5節）、機能の登録（部品 6節）、画面の流れと応答ごとの動き（`functional-spec.md` の 4〜6節）、文言（同 7節の表の鍵と文。一般の文言は DSL の管理画面と同じ文を複写する）、重なった読み直しの扱い（`performance-design.md` 2節）、待ちの見せ方（同 3節）、トークン・URL・メールアドレスの扱いと失敗の文言（`security-design.md` 1〜3節）、検査の置き場・組・状態の出し方・ログインの開き方（`logical-components.md` 5節）、測定の手順（`performance-design.md` 4節）。
- **名前を決めるもの**（「コード生成で決める」とされたもの）:
  - 機能の置き場 `frontend/src/features/invitation/` のモジュールは部品 2節の名前のまま（`registration.ts`・`messages.ts`・`api/types.ts`・`api/invitationApi.ts`・`paging.ts`・`focusTarget.ts`・`unavailable.ts`・`inviteInput.ts`・`failureMessage.ts`・`useInvitationText.ts`・`useInvitationAdmin.ts`、部品の `.tsx` と同じ場所の `.css`）。テストの補助は `testing/renderInvitation.tsx`（文言を登録した Provider と Toast・Modal の Provider の中に描く。DSL の `renderDsl.tsx` と同じ形）と `testing/fixtures.ts`（応答の見本）。
  - `api/invitationApi.ts` は4つの関数と `readPendingProblem`・`readUnavailableReasons` を名前付きで出し、部品へ差し替えやすいよう `invitationApi`（関数をまとめた定数）と型 `InvitationApi` を出す（DSL の `dslApi`・`DslApi` と同じ形）。
  - 日時の書式は `frontend/src/shared/format/formatDateTime.ts`（関数 `formatDateTime` と、言語の型 `FormatLanguage = 'ja' | 'en'`）。`shared` は `app` に依存しない今の形を保つ（機能設計の 9節の (d)）。
  - 実際のブラウザの検査のファイルは `frontend/e2e/060-invitation-accessibility.e2e.ts`（9節の決定 2）。共用の手伝いに足すのは `frontend/e2e/support/adminLogin.ts`（ログインの画面のフォームからの管理者のログインと、サイドバーの項目で画面を開く関数）・`invitationFixtures.ts`（一覧の見本。`src/features/invitation/api/types.ts` の型を付ける）・`invitationSeed.ts`（測定の準備: 要求の口でのアクセストークンの取得、一覧の `invitationEnabled` の読み取り、招待の API での N 件の用意）。既存の `axe.ts` の既知の違反の一覧を、画面の状態ごとに持てる形に広げる（9節の決定 4。050 の判定は変えない）。
  - `data-testid` は `invitation-` で始める（例: `invitation-admin-page`・`invitation-invite-button`・`invitation-unavailable-alert`・`invitation-failure-alert`・`invitation-failure-dismiss`・`invitation-list`・`invitation-list-heading`・`invitation-list-loading`・`invitation-list-empty`・`invitation-list-failed`・`invitation-list-retry`・`invitation-row-email-<id>`・`invitation-row-resend-<id>`・`invitation-row-revoke-<id>`・`invitation-live-region`・`invitation-invite-dialog`・`invitation-invite-email-input`・`invitation-invite-language`・`invitation-invite-submit`・`invitation-invite-cancel`・`invitation-invite-show-row`・`invitation-invite-dialog-alert`・`invitation-revoke-dialog`・`invitation-revoke-confirm`・`invitation-revoke-cancel`）。Table が中に持つ要素（`data-testid="table"` など）は探さず、表は役割と名前（`getByRole('table', { name: '招待中の人' })`）で探す。
- **既存に手を入れるもの**: `frontend/src/features/dsl/format.ts`（`formatDateTime` を移し、`shortHash`・`formatBytes` を残す）・`format.test.ts`（`formatDateTime` の確かめを移す）、`DslStatusPanel.tsx`・`DslConfirmDialog.tsx`・`DslHistoryTable.tsx`（読み込み先だけ）、`frontend/e2e/support/axe.ts`（既知の違反の一覧の形）、`README.md`。骨組み（`frontend/src/app/`）・AdminArea（`frontend/src/features/admin/`）・ApiClient・機能の登録の仕組み・`frontend/playwright.config.ts`・`playwright-secret-check-reporter.ts`・`vitest.config.ts` は変えない。
- **サーバーの側は変えない**: `backend/` のコード・`application.yaml`（CSP を含む）・Flyway・`compose.yaml`・`Dockerfile`・`build.gradle.kts`・`.github/`・`.gitignore`・`frontend/package.json`・`frontend/package-lock.json` は変えない（NFR9.4、`infrastructure-specification.md`）。`vendor/` の中身も固定先も変えない。
- **既存の ArchUnit** はバックエンドの検査で、U5 は触れない。
- **既存の E2E（010〜050）は変えない**: サイドバーに「利用者の招待」が加わっても、030 の `getByRole('link', { name: '管理', exact: true })` は変わらず通る見込み。Step 20 で確かめる。

## 3. 作業の場とコミットの区切り

- **作業のブランチ**: `develop` の先頭（`fd44e79`）から短命のブランチ `feature/260925-user-management-b5-u5` を作る（`team.md` の Way of Working。9節の決定 1）。
- **統合の形**: U5 は固定先の更新を含まないため、`develop` へ **squash** で統合する（`team.md` の Way of Working、`cicd-pipeline.md` 3節）。U6・U7 はそれぞれの計画で別の単位として作る。9節の決定 1 のとおり、B5 の3単位を単位ごとに統合する（`team.md` の「1 Bolt が `develop` の1コミット」とは違うが、この Bolt の扱いとして依頼者が承認した）。統合の前に `./gradlew verify` を通し（Step 21）、画面に関わる変更のため Mailpit を起動して `./gradlew e2eTest` の全件も通す（Step 20）。統合は依頼者の承認を得て行う。
- **コミット**: 生成の担当はコミットしない。生成の後に、依頼者の承認を得て、次の区切りでまとめてコミットする（`project.md` の Change Control の学び）。メッセージは日本語で、U5 の内容が分かる件名にする。squash のため、`develop` には U5 の1つのコミット（件名の案「B5 U5 招待の管理の画面（一覧・招待・送り直し・取り消し、日時の書式の共通化、実際のブラウザの検査）」）になり、細かい区切りは作業ブランチに残る。

| 区切り | 中身 | 手順 |
|---|---|---|
| C1 | 日時の書式の移動（`frontend/src/shared/format/`、`frontend/src/features/dsl/format.ts`・`format.test.ts`・3つの部品の読み込み先） | Step 5・6 |
| C2 | 本番のコード（`frontend/src/features/invitation/` のテスト以外、文言と CSS） | Step 3・7・9・11・13 |
| C3 | テスト（`frontend/src/features/invitation/` の `*.test.ts(x)` と `testing/`） | Step 8・10・12・14 |
| C4 | 実際のブラウザの検査（`frontend/e2e/060-invitation-accessibility.e2e.ts`・`frontend/e2e/support/` の足したファイルと `axe.ts`） | Step 15・16 |
| C5 | 文書（`README.md`、必要なら `frontend/src/features/README.md`） | Step 19 |
| C6 | この段の記録（記録の `construction/u5-invitation-ui/code-generation/` の下） | Step 22 |

- `origin` への `git push` は依頼者が行う。AI はプッシュしない。
- 統合の squash のコミットのメッセージの末尾に `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` を付ける（作業ブランチの各コミットも同じ）。

## 4. 作るもの・手を入れるもの

| 置き場 | 部品 | 新しい・手を入れる | 役割 |
|---|---|---|---|
| `shared/format/formatDateTime.ts`（と `formatDateTime.test.ts`） | `formatDateTime(iso, language, timeZone?)`・`FormatLanguage` | 新しい（移す） | D6、Q1 A、NFR9.8 |
| `features/dsl/format.ts`・`format.test.ts` | `shortHash`・`formatBytes` だけを残す | 手を入れる | 部品 8節 |
| `features/dsl/DslStatusPanel.tsx`・`DslConfirmDialog.tsx`・`DslHistoryTable.tsx` | `formatDateTime` の読み込み先を `shared/format` に | 手を入れる | 部品 8節 |
| `features/invitation/api/types.ts` | `Invitation`・`InvitationPage`・`InvitationRequest`・`SendResult`（`'SENT' \| 'FAILED'`）・`UnavailableReason`（`'SMTP_NOT_CONFIGURED' \| 'BASE_URL_NOT_CONFIGURED'`）・`PendingProblem`（`invitationId`・`page`）。`enum` を使わない | 新しい | 部品 2節・5節、NFR1.1 |
| `features/invitation/api/invitationApi.ts` | `listInvitations`・`createInvitation`・`resendInvitation`・`cancelInvitation`・`readPendingProblem`・`readUnavailableReasons`・`invitationApi`・`InvitationApi`。成功の本文は一覧・招待・送り直しだけ JSON として読み項目ごとに確かめる（知らない項目は捨てる、`sendResult` の知らない値は `FAILED` の側）。取り消しは本文を読まない | 新しい | 部品 5節、`security-design.md` 1節・3.3・3.4 |
| `features/invitation/paging.ts` | `PAGE_SIZE`（20）・`pageCount`・`pageRange`・`correctedPage`・`pagerButtonDisabledAfter` | 新しい | D1、W2 |
| `features/invitation/focusTarget.ts` | フォーカスの行き先の型と、行の有無・押せるかで実際に当てる所を決める関数 | 新しい | 部品 3.3、W7〜W9 |
| `features/invitation/unavailable.ts` | `unavailableReasons` から警告の文の形（1つ・2つ・知らない理由だけ・空）を決める | 新しい | W3 |
| `features/invitation/inviteInput.ts` | メールアドレスが空（空白だけを含む）かの判定 | 新しい | D8 |
| `features/invitation/failureMessage.ts` | 失敗（`ApiError`）から `code`・状態コードの種類・文言の鍵を選ぶ。`detail`・`title` を使わない | 新しい | D4、NFR9.1 |
| `features/invitation/messages.ts` | `invitationMessages`（ja・en、機能設計 7節の鍵と文） | 新しい | NFR8.1 |
| `features/invitation/useInvitationText.ts` | 文言の鍵と差し込む値から文言を返す（DSL の `useDslText` と同じ形。値は React の文字として描く） | 新しい | 7節、NFR9.1 |
| `features/invitation/useInvitationAdmin.ts` | 画面の状態と操作（部品 3節）。読み直しの番号と部品が付いているかの印、`useToast` の成功の知らせ | 新しい | W1〜W10、D1〜D3・D9・D10、NFR6.3・NFR6.4 |
| `features/invitation/InvitationAdminPage.tsx`（と CSS） | 画面。見出し `h1`・「招待する」・警告・失敗の知らせ・一覧・2つの Modal・件数の範囲の読み上げの領域。テストのための任意の props `api`・`timeZone` | 新しい | 部品 1節・4節 |
| `features/invitation/InvitationUnavailableAlert.tsx` | 招待を使えないときの警告（Alert の警告の種類） | 新しい | W3、AC1.1.6・AC2.2.9 |
| `features/invitation/InvitationList.tsx`（と CSS） | 一覧の見出し `h2`（`tabIndex=-1`）、読み込み中・空・読めなかった・表（make-you-chic-ui の Table、列は9節の決定 3、`labels` の7項目、`aria-label`、`getRowId`）、列の `render` の Badge・行のボタン・メールアドレスのセルの受け口と目立たせた印、フォーカスの当て方 | 新しい | W1・W2・W7〜W9、D5〜D7・D13、`logical-components.md` 6.2 |
| `features/invitation/InviteDialog.tsx`（と CSS） | S1-M1。Modal（`closeOnBackdropClick={false}`・`closeLabel`・`initialFocusRef`）、FormField と TextInput（`type="email"`・`autocomplete="off"`、フォームは `noValidate`）、RadioGroup（`legend`・`lang`）、「やめる」「招待する」（`loading`）、招待中の案内の button | 新しい | W4〜W7、D8・D9・D12 |
| `features/invitation/CancelConfirmDialog.tsx` | S1-M2。Modal（`role="alertdialog"`・`closeOnBackdropClick={false}`・`closeLabel`・`initialFocusRef` は「やめる」）、「取り消す」（`danger`、`loading`）・「やめる」 | 新しい | W9、D9・D12 |
| `features/invitation/registration.ts` | 機能の登録（`featureId: 'invitation'`、画面 `/admin/invitations`（遅延読み込み、`SHELL`・`ADMIN`）、サイドバー `invitation`（order 220、`visibleWhen: 'ADMIN'`）、文言） | 新しい | 部品 6節、NFR9.2 |
| `features/invitation/testing/renderInvitation.tsx`・`fixtures.ts` | テストの補助（テストからだけ使う） | 新しい（テストの支え） | 部品 2節 |
| `frontend/e2e/060-invitation-accessibility.e2e.ts` | 20 組の検査（一覧・警告・招待の入力の Modal・取り消しの確かめの Modal）と画面の時間の測定（5回） | 新しい | NFR6.1・NFR6.2・NFR7.3・NFR7.4・NFR9.3・NFR9.9 |
| `frontend/e2e/support/adminLogin.ts`・`invitationFixtures.ts`・`invitationSeed.ts` | ログインと画面の開き方、一覧の見本、測定の準備 | 新しい（B5 の共用の手伝い） | `logical-components.md` 5.3・5.4、`performance-design.md` 4.2 |
| `frontend/e2e/support/axe.ts` | 既知の違反の一覧を画面の状態ごとに持てる形にし、U5 の状態の一覧を足す（050 の一覧と判定は変えない） | 手を入れる | 9節の決定 4 |
| `README.md` | 「招待の管理の画面（U5）」の節、E2E の節の 060 の行と 21 通・Mailpit の片付けの書き方 | 手を入れる | `cicd-pipeline.md` 4.3・4.4 |

## 5. 手順

各層で実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む（test-after、Testing Contract の `ordering`）。U5 の層は「既存の日時の書式の移動 → 業務処理（純粋な関数）→ API（`invitationApi`）→ 画面の振る舞い（画面部品 → 画面と状態と登録）→ 実際のブラウザの検査」の順とする。U5 は内部DB とブラウザの保存を持たないため、データの形（DB）とデータアクセスの層は無い。日時の書式の移動を先に置くのは、既存の DSL の画面への影響を U5 の新しいコードと混ぜずに確かめるため。

### Step 1: 作業の場の用意と、変更の前の基準（依頼者が承認した git の操作。9節の決定 1）

- [x] `develop` の先頭のハッシュ（`fd44e79`）と、サブモジュールの固定先（`vendor/make-you-chic-ui` が `735ef04ce6eb618cb875f5c4b31c1645a1f84c28`、`vendor/java-mustache-processor` が `8d44c36`）を記録し、`develop` から短命のブランチ `feature/260925-user-management-b5-u5` を作る（コミットはしない。統合は squash）
- [x] 変更の前の基準をとる: colima が動いていることを確かめ、README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡して `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を実行し、テストの件数（バックエンドの単体・結合、フロントエンドのファイル数と件数、失敗・飛ばした）と、フロントエンドの全体のカバレッジ（行・分岐）を記録する（brownfield の Test Baseline、`project.md` の Testing Posture。U4 の後の実測は 55 ファイル 408 件、行 98.04%・分岐 94.27%）
- [x] 同じ実行の成果物で、変更の前の値を記録する: `frontendBundleSize` の初回の JavaScript（gzip）、`frontend/dist/assets/` の JavaScript のファイルの数と合計、`backend/build/libs/mastersmith.war` の大きさ（NFR6.6、`performance-design.md` 5節。測る道具は足さず、`du`・`stat` と既存の検査の出力）
- [x] 対応: B5 の共通の完了の条件、NFR6.6（前の値）

### Step 2: 前提の確かめ（読み取りだけ）

- [x] `git submodule status` で `vendor/make-you-chic-ui` が `735ef04` であること（B4 の固定先の更新が済んでいること、NFR9.5）と、U5 が使う口が `735ef04` にあることを、`vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/` の Table（`labels` の7項目・`aria-label`・`page`・`pageSize`・`totalCount`・`onPageChange`・`getRowId`・`columns` の `render`。`labels.pageStatus` は `(page, totalPages, totalCount)` を受ける関数）・Modal（`closeOnBackdropClick`・`role`・`closeLabel`・`initialFocusRef`）・RadioGroup（`legend`・選択肢の `lang`）・Button（`loading` で `aria-disabled`・`aria-busy`）・Alert・Badge・`useToast` で確かめて記録する。口が無い・形が違うときは、9節の決定 6 の (a) のとおり止めて諮る
- [x] AppShell（`735ef04`）に狭い幅の切り替え（`@media`）が無く、375px でもサイドバーが出たままであること、中身の領域 `.mycui-app-shell-content` が `overflow-y: auto` の要素であることを記録する（375px の組で、サイドバーの「利用者の招待」をそのまま押せる前提と、表が中身の領域の中で横に動く見込みの根拠。Step 16 で実際に確かめる）
- [x] U3 の API の実際の形を `backend/src/main/java/cherry/mastersmith/invitation/web/` で確かめて記録する: `InvitationResponse` の8項目、`InvitationPageResponse` の6項目、409 `INVITATION_ALREADY_PENDING` の追加の項目 `invitationId`・`page`、503 `INVITATION_NOT_CONFIGURED` の `unavailableReasons`、一覧の不正なページは 400 `VALIDATION_FAILED`、取り消しは 204（契約 C5 と機能設計 5節どおりであること）
- [x] NFR 設計の承認の場の U5 R-02: `backend/src/test/java/cherry/mastersmith/invitation/web/InvitationAdminApiIT.java` の `notConfigured` が、招待を使える設定の無い3通りで管理者のアクセストークンの取得と一覧の 200（`invitationEnabled: false`・理由）を確かめていることと、E2E の初期管理者の用意（`frontend/playwright.config.ts`）が SMTP の設定に頼らないことを記録する
- [x] 機能設計の承認の場の G6: README の「招待と登録の完了（U3）」の「契約との差」に C5 の `invitedBy`（氏名だけ、行が無ければ空）が書かれていることを確かめて記録する
- [x] 機能どうしの読み込みが今のコードに無いこと（`frontend/src/features/*/` の本番のコードが別の機能を読み込んでいない。テストだけが登録を組み合わせる）を確かめ、U5 もこの形を守る
- [x] 対応: NFR9.5、機能設計の承認の場の G6・Request Changes、NFR 設計の承認の場の U5 R-02

### Step 3: 骨組み（型・文言・文言の引き方）

- [x] `frontend/src/features/invitation/api/types.ts` を作る: 契約 C5 の `Invitation`（`invitationId: number`・`email`・`language: 'ja' | 'en'`・`invitedBy`・`invitedAt`・`expiresAt`・`sendResult: SendResult`・`expired: boolean`）、`InvitationPage`（`items`・`page`・`size`・`total`・`invitationEnabled`・`unavailableReasons: UnavailableReason[]`）、`InvitationRequest`（`email`・`language`）、`PendingProblem`。値の一覧は `as const` の配列と、そこから作る文字列リテラルの union（`enum` を使わない）。トークン・URL の項目を持たない（NFR1.1）
- [x] `frontend/src/features/invitation/messages.ts` に `invitationMessages`（ja・en）を作る。鍵と文は機能設計 7節の表のとおり（`invitation.` で始める。一般の文言の3つは DSL の `messages.ts` と同じ文を複写する。有効期限の長さ「24 時間」を書かない。「取り消す」の英語は Revoke）
- [x] `frontend/src/features/invitation/useInvitationText.ts` を作る（DSL の `useDslText` と同じ形。`{{ }}` の値は React の文字として描く）
- [x] 新しいファイルはすべて先頭に Apache License 2.0 のヘッダー（`/* ... */`、2026、agwlvssainokuni）と、日本語の説明のコメントを置く。エクスポートは名前付きだけ（`export default` を使わない）
- [x] 対応: 部品 2節、NFR1.1・NFR8.1、機能設計 7節、`team.md` の Code Style

### Step 4: テストの実行の準備（最初のテストより前）

- [x] `unit-test-instructions.md` 2節の単体のコマンド（Vitest の対象を `src/features/invitation`・`src/shared/format` と、移動で手を入れる DSL の4つのテストのファイルに絞ったもの）が、この時点の状態で動き、既存のテストが通ることを確かめる。U5 の新しいテストはまだ無いが、DSL の4つのテストのファイルに当たるため「当たるテストが無い」にはならない
- [x] `fast-check`（既存）と `vitest-axe`（既存）が使えることを確かめる。偽の時計（`vi.useFakeTimers`）を画面部品のテストで使えることを確かめる（NFR6.3 の自動の読み直しが無いことの確かめ）
- [x] `(cd frontend && npx playwright test --list)` で既存の E2E の一覧（010〜050）が読めることを確かめる（060 はまだ無い）
- [x] 対応: Testing Contract の `runner_step`

### Step 5: 既存に手を入れる — 日時の書式の移動（実装）

- [x] `frontend/src/shared/format/formatDateTime.ts` を作り、`frontend/src/features/dsl/format.ts` の `formatDateTime` と、その中だけで使う `part` を移す。言語の引数の型は `app` を読まずに `FormatLanguage = 'ja' | 'en'` をこのファイルに置く（U4 の `DisplayLanguage` と同じ値。機能設計の 9節の (d)）。ふるまい（`ja` は `2026-09-24 10:15 JST`、`en` は `Sep 24, 2026, 10:15 GMT+9`、読めない値はそのまま返す）は変えない
- [x] `frontend/src/features/dsl/format.ts` から `formatDateTime` を消し、`shortHash`・`formatBytes`（と `SHORT_HASH_LENGTH`）を残す。`DslStatusPanel.tsx`・`DslConfirmDialog.tsx`・`DslHistoryTable.tsx` の読み込み先を `../../shared/format/formatDateTime` に変える（ふるまいは変えない）
- [x] 対応: D6、Q1 A、部品 8節、NFR9.8

### Step 6: 既存に手を入れる — 日時の書式の移動（テスト）

- [x] `frontend/src/features/dsl/format.test.ts` の `formatDateTime` の確かめ（ja・en・時差・不正な値）を、確かめの中身を変えずに `frontend/src/shared/format/formatDateTime.test.ts` へ移す。`shortHash`・`formatBytes` の確かめは残す
- [x] 単体のコマンドで、移した先のテストと、DSL の `format.test.ts`・`DslStatusPanel.test.tsx`・`DslConfirmDialog.test.tsx`・`DslHistoryTable.test.tsx` が期待を変えずに通ることを確かめる。期待の変更が要るときは止めて諮る（9節の決定 6 の (e)）
- [x] 対応: NFR9.8

### Step 7: 業務処理（純粋な関数）— 実装

- [x] `paging.ts`: `PAGE_SIZE = 20`、`pageCount(total)`（`total` が 0 なら 0、ほかは切り上げ）、`pageRange(page, total)`（`from`・`to`）、`correctedPage(page, total, itemCount)`（`items` が空で `total` が 1 以上なら最後のページ、そうでなければ `undefined`）、`pagerButtonDisabledAfter(direction, page, total)`
- [x] `focusTarget.ts`: 行き先（`resend`・`email`・`heading`・`empty`）と、今の `items`・招待を使えるか・行の処理中の集合から、実際に当てる所（行の「送り直す」・行のメールアドレスのセルの受け口・一覧の見出し・空の表示の文）を返す関数（部品 3.3 の表）
- [x] `unavailable.ts`: `unavailableReasons` から、1つ（その理由の文）・2つ（見出しと2つの理由）・知らない値だけ（見出しと一般の文）・空の形を返す（知らない値は出さない）
- [x] `inviteInput.ts`: 空（空白だけを含む）かの判定。値は正規化しない（D8）
- [x] `failureMessage.ts`: `ApiError` の `code`（`VALIDATION_FAILED`・`INVITATION_EMAIL_REGISTERED`・`INVITATION_ALREADY_PENDING`・`INVITATION_NOT_CONFIGURED`・`INVITATION_NOT_FOUND`）と状態コードの種類から文言の鍵を返す。知らない `code`・`code` なし・通信の失敗は `invitation.errorGeneral.client`・`server`・`network`。`detail`・`title` を読まない（DSL の `failureMessage.ts` と同じ考え方で、この機能の `code` を持つ）
- [x] どの関数も React・`window`・API に触れない
- [x] 対応: D1・D3・D4・D8、W2・W3・W7〜W9、部品 2節・3.3

### Step 8: 業務処理 — テスト（単体・性質ベース）

- [x] `paging.test.ts`: ページの数（0・20・21・43 件）、件数の範囲（1ページ目・途中・最後のページ）、ページの補正（空の `items` と `total` で最後のページ、補正しない場合）、押したボタンが押せなくなるかの判定（1ページ目・最後のページ・途中）。性質ベース（fast-check）: 任意の全件数と有効なページで `from ≦ to ≦ total`、`to − from + 1 ≦ 20`、補正の結果が 1 以上ページの数以下（NFR9.7）
- [x] `focusTarget.test.ts`: 行があり押せる → 送り直す、押せない（招待を使えない・行の処理中） → メールアドレスのセルの受け口、行が無い → 一覧の見出し、空 → 空の表示の文
- [x] `unavailable.test.ts`: 理由が1つずつ・両方・知らない値だけ・知らない値と知っている値の混ざり・空
- [x] `inviteInput.test.ts`: 空・空白だけ（半角・全角の空白・タブ・改行）・前後に空白のある値（空でない）。性質ベース: 空白だけの文字列はすべて空と判定（NFR9.7）
- [x] `failureMessage.test.ts`: 知っている `code` の鍵、知らない `code`・`code` なし（4xx・5xx）・通信の失敗の一般の鍵、`detail` に目印を入れても鍵に影響しない
- [x] 失敗時の fast-check の `seed`・`path` はテストの出力に残る（既存の `submitInput.test.ts` と同じ扱い）
- [x] 単体のコマンドを実行して通す
- [x] 対応: NFR9.7、D1・D4・D8、部品 7節の純粋な関数の行

### Step 9: API（`invitationApi`）— 実装

- [x] `api/invitationApi.ts`: 4つの関数は既存の `apiRequest` を通す（アクセストークン・401 での更新・`Accept-Language` は ApiClient に任せる）。`listInvitations(page)` は `GET /api/admin/invitations?page=${page}`、`createInvitation({ email, language })` は `POST`（JSON、本文は2項目だけ）、`resendInvitation(invitationId)`・`cancelInvitation(invitationId)` は `invitationId` を数として扱い `encodeURIComponent` を通してパスに入れる
- [x] 成功の本文: 一覧・招待・送り直しは JSON として読み、項目ごとに型を確かめて決まった項目だけの値を作る（知らない項目は捨てる、`sendResult` の知らない値は `FAILED`、`unavailableReasons` の知らない値は捨てる）。読めない・形が違うときは通信の失敗（`networkError()`）として扱う。取り消しの 204 は本文を読まない（`security-design.md` 3.3）
- [x] 失敗は ApiClient の `ApiError` をそのまま投げる。`readPendingProblem(error)` は 409 `INVITATION_ALREADY_PENDING` の `problem` の `invitationId`（整数）と `page`（1 以上の整数）の型を確かめて返し、合わなければ `undefined`。`readUnavailableReasons(error)` は 503 `INVITATION_NOT_CONFIGURED` の `unavailableReasons` のうち知っている値だけを返す
- [x] 応答の値を `console`・ブラウザの保存に出さない
- [x] 対応: 部品 5節、NFR1.1・NFR2.1・NFR9.1、`security-design.md` 1節・3.3・3.4、C5

### Step 10: API — テスト（単体）

- [x] `api/invitationApi.test.ts`（`vi.stubGlobal('fetch', ...)` で差し替え、`resetApiClient` をテストごとに呼ぶ）:
  - 4つの要求のメソッド・パス・本文（招待の本文は `email`・`language` の2つだけ、送り直し・取り消しは本文なし、`invitationId` のパス）
  - 一覧・招待・送り直しの成功の値が決まった項目だけ（応答に `token`・`url` を足しても値に入らない。NFR1.1）、`sendResult` の知らない値が `FAILED`、`unavailableReasons` の知らない値が捨てられる
  - 200・201 の読めない本文・形の違う本文が通信の失敗、204 の空の本文が成功（NFR 要件の承認の場の R-01）
  - 400・404・409・503・500 と通信の失敗で `ApiError` の種類・状態コード・`code` がそのまま届く
  - `readPendingProblem`: 正しい形・`invitationId` が数でない・`page` が 0・小数・欠けた項目・別の `code`
  - `readUnavailableReasons`: 2つ・1つ・知らない値の混ざり・配列でない・別の `code`
- [x] 単体のコマンドを実行して通す
- [x] 対応: NFR1.1・NFR9.1、部品 7節の `invitationApi.ts` の行、`security-design.md` 3.5

### Step 11: 画面の振る舞い — 画面部品（InvitationList・InvitationUnavailableAlert・InviteDialog・CancelConfirmDialog）— 実装

- [x] `InvitationList.tsx`（部品 4節の props）:
  - 一覧の見出し `h2`「招待中の人」（`tabIndex=-1`）をいつも描く。最初の読み込み・空・読めなかったときは Table を描かず、読み込み中の表示（文字つき、`role="status"`）・空の表示の文（`tabIndex=-1`）・読めなかった表示（`role="alert"`、一般の文と「もう一度読み込む」）を出す。一度表示した後の読み直しの間は Table を描いたまま `data` を空にし、`totalCount` は最後に使った一覧の値にする（4.1）
  - Table: `columns` は9節の決定 3 のとおり8列（機能設計 W1 の4の項目と 7節の `invitation.column.*` の8つの見出し: メールアドレス・言語・招待した管理者・招待した日時・有効期限・送信の結果・状態・操作）、`data`、`totalCount`、`page`、`pageSize` 20、`onPageChange`、`getRowId`（`invitationId` の文字）、`aria-label`「招待中の人」、`labels` の7項目すべてを画面の言語で（`pageStatus` は `paging.ts` で件数の範囲を計算して `invitation.pager.status` を返す関数）。`sortable`・`selectedRowIds`・`renderDetail`・`editable` は渡さない
  - 列の `render` で、決まった項目を1つずつ読んで描く（行を丸ごと広げて渡さない。`security-design.md` 1.1）: メールアドレスのセルの受け口（`tabIndex=-1` の要素、目立たせた行ではその中に `data-invitation-highlighted` の印）、言語（`LANGUAGE_NAMES` と `lang`）、招待した管理者（空なら `invitation.invitedBy.unknown`）、日時（`formatDateTime`）、送信の結果と状態の Badge（文字で示す）、行の「送り直す」（`aria-label` に `invitation.row.resendName`、行の処理中は `loading` と「送信しています」、招待を使えないときは `disabled`）・「取り消す」（`aria-label` に `invitation.row.revokeName`、行の処理中は `disabled`）
  - 行の要素への参照を `invitationId` ごとに持ち、読み込み中でない描画の確定の後（`useEffect`）に `focusTarget.ts` の結果の要素へフォーカスし、`onFocusApplied` を呼ぶ。Table の中の class・`data-testid` を探さない
  - ページ送りの `onPageChange` を「前へ」「次へ」のどちらかを添えて上へ渡す（ページが1つ減ったか増えたかで決める）
  - 矢印のキーの処理は作らない（機能設計の承認の場の U5 R-01）
  - 同じ場所の CSS: 目立たせた行（`.invitation-list tr:has([data-invitation-highlighted])` に枠の線と背景。make-you-chic-ui のトークンだけ）、見出し・読み込み中の表示の配置。Table の中の class を上書きしない。`style` 属性で差し込まない（NFR9.3）
- [x] `InvitationUnavailableAlert.tsx`: `invitationEnabled` が偽のときだけ Alert の警告の種類で描く。文の形は `unavailable.ts`
- [x] `InviteDialog.tsx`（部品 4節の props）: Modal（見出し `invitation.invite.title`、`closeLabel` は `invitation.action.close`、`closeOnBackdropClick={false}`、`initialFocusRef` はメールアドレス、役割は既定の `dialog`）。送信中は `onClose` を呼ばない（Esc・閉じるボタンを受けない）。フォームは `noValidate`。メールアドレスは FormField と TextInput（`type="email"`・`autocomplete="off"`、名前は `invitation.invite.email`、誤りは `aria-invalid`・`aria-describedby`）。言語は RadioGroup（`legend` は `invitation.invite.language`、選択肢は `LANGUAGE_NAMES` と `lang`、案内 `invitation.invite.languageHint`）。「やめる」（secondary、送信中は `disabled`）と「招待する」（primary、送信中は `loading` と「送信しています」）。`fieldError` が出たら描いた後にメールアドレスへフォーカス。招待中の誤りで `pending` があれば「一覧でこの招待を見る」（見た目がリンクの button）。Modal の中の知らせは Alert の失敗の種類（`role="alert"`）
- [x] `CancelConfirmDialog.tsx`: Modal（見出し `invitation.revoke.title`、`role="alertdialog"`、`closeLabel`、`closeOnBackdropClick={false}`、本文 `invitation.revoke.body`（その行のメールアドレス）、`initialFocusRef` は「やめる」）。要求中は `onClose` を呼ばない。「取り消す」は `danger`、要求中は `loading` と「取り消しています」、「やめる」は要求中 `disabled`
- [x] 部品の文言はすべて `useInvitationText` で引き、表示の言語は `useDisplaySettings().language`（日時の書式の言語も同じ）。`setPreview`・`setLanguage` などは呼ばない。`console` を呼ばない
- [x] 対応: W1〜W4・W7〜W10、D3・D5〜D7・D9・D12・D13、NFR1.1・NFR6.4・NFR7.1・NFR8.2・NFR9.3、`logical-components.md` 6.1・6.2

### Step 12: 画面の振る舞い — 画面部品 — テスト（vitest-axe を含む）

- [x] `testing/renderInvitation.tsx`（文言を登録した Provider と Toast・Modal の Provider の中に描く。既存の `renderWithProviders` を使い、画面の言語は既定の ja、選択で en）と `testing/fixtures.ts`（`Invitation`・`InvitationPage` の見本。`example.com`・`example.test` の下のメールアドレス、架空の氏名。`invitedBy` が空の行には 2.1 の NFR 設計の U5 R-01 のコメント）を作る
- [x] `InvitationList.test.tsx`（時差を固定して描く）: 列と値（言語の名前と `lang`、日時の書式、招待した管理者、空の `invitedBy` の「（不明）」）、送信の結果の文字と状態の文字（色だけでない）、ボタンの名前にメールアドレス、`token`・`url` を足した行でも画面の文字と属性にその値が出ない（NFR1.1）、メールアドレスに `<`・`>`・`&` を含む値が文字として出る（NFR9.1）、読み込み中・空の文・読めなかった表示と「もう一度読み込む」、行の処理中にその行の「送り直す」が `aria-disabled`・`aria-busy` で押しても `onResend` が呼ばれずフォーカスを保ち「取り消す」が押せない（ほかの行は押せる）、招待を使えないとき「送り直す」だけ押せず「取り消す」は押せる、期限切れの行も押せる、目立たせた行の印、フォーカスの行き先（送り直す・受け口・一覧の見出し・空の表示の文）、表の名前（`aria-label`）と一覧の見出し、表の中のボタンに Tab で届く、Table のページ送りの状態の文（件数の範囲とページ）と端で押せないこと、en の画面で Table の文言に日本語の既定の文言（「前へ」「次へ」「件」「ページ」など）が出ない（NFR8.2）、読み直しの間も Table とページ送りのボタンが残る、vitest-axe の違反 0 件
- [x] `InvitationUnavailableAlert.test.tsx`: 理由ごとの文・両方の並び・知らない値だけ・使えるときは描かない、vitest-axe
- [x] `InviteDialog.test.tsx`: 開いた時点の言語が画面の言語（en の管理者で en）、メールアドレスが空、フォーカスがメールアドレス、空のまま送ると誤りと結び付き（`aria-invalid`・`aria-describedby`）とフォーカス、送信中に「招待する」が `aria-disabled`・`aria-busy` で「送信しています」になりフォーカスが残る、送信中は Esc・閉じる・やめるで閉じない、背景のクリックで閉じない、閉じるボタンの名前が ja・en、誤りの後も値が残る、招待中の案内と「一覧でこの招待を見る」、Modal の中の知らせ、言語の選択肢の `lang`、vitest-axe
- [x] `CancelConfirmDialog.test.tsx`: 役割が `alertdialog`、本文のメールアドレス、はじめのフォーカスが「やめる」、Esc・やめるで閉じて何も呼ばない、背景のクリックで閉じない、要求中に「取り消す」が `loading` で「やめる」が押せない、閉じるボタンの名前が ja・en、vitest-axe
- [x] 単体のコマンドを実行して通す
- [x] 対応: NFR1.1・NFR6.4・NFR7.1・NFR7.2・NFR8.2・NFR9.1、部品 7節の画面部品の行、AC1.1.1・AC1.1.3・AC1.1.4・AC1.1.9・AC2.1.1・AC2.1.2・AC2.1.4・AC2.1.6〜AC2.1.8・AC2.2.2・AC2.2.9〜AC2.2.12、CR6.1〜CR6.3・CR6.5〜CR6.8

### Step 13: 画面の振る舞い — 画面・状態・登録（useInvitationAdmin・InvitationAdminPage・registration）— 実装

- [x] `useInvitationAdmin.ts`（部品 3節）: 状態（`page`・`list`・`loadState`・`loadFailureKey`・`failure`・`highlightedId`・`resendingIds`・`invite`・`cancelTarget`・`cancelBusy`・`focusTarget`・`liveMessage`）と操作（`retry`・`goToPage`・`openInvite`・`changeInviteEmail`・`changeInviteLanguage`・`submitInvite`・`closeInvite`・`showPendingRow`・`resend`・`requestCancel`・`closeCancel`・`confirmCancel`・`dismissFailure`・`onFocusApplied`）
  - 読み直しごとに番号を増やし、最後の番号の答えだけを使う。部品を外した後の答えは捨てる（D2、`useDslAdmin` の `statusSeq`・`mounted` と同じ形）。古い要求は中断しない（`performance-design.md` 2.1）。決まった間隔の読み直しを置かない（NFR6.3）
  - `items` が空で `total` が 1 以上なら `correctedPage` の最後のページを読み直す（W2 の5）
  - 応答ごとの動きは機能設計 6.1〜6.4 の表のとおり（招待の 201 の SENT・FAILED、400・409（2つ）・503・一般の失敗、送り直しの 200 の SENT・FAILED・404・503・一般の失敗、取り消しの 204・404・一般の失敗）。401 は画面の文言を出さない。403 は一般の 4xx の文言
  - 成功は `useToast` の成功の種類で知らせる。失敗の知らせは1つだけで、次の操作の結果（成功を含む）とページ送り・次の操作の開始で置き換える・消す（D10）
  - 件数の範囲の読み上げ（`liveMessage`）を読み終わったときに作る（W2 の3）
  - 招待の言語の初期値は、開いた時点の `useDisplaySettings().language`（W4、AC1.1.1）
- [x] `InvitationAdminPage.tsx`（と CSS）: 見出し `h1`「利用者の招待」、右に「招待する」（primary、`list` が null か招待を使えないときは `disabled`）、警告、失敗の知らせ（Alert の失敗の種類、閉じるボタン `invitation.action.dismissAlert`）、`InvitationList`、2つの Modal、見えない `aria-live="polite"` の件数の範囲の領域。テストのための任意の props `api`（既定は `invitationApi`）と `timeZone`。ページ送りで `navigate`・`history` を呼ばず、URL を変えない（NFR2.1）
- [x] `registration.ts`（部品 6節）: `featureId: 'invitation'`、画面 `path: '/admin/invitations'`（`INVITATION_ADMIN_PATH` として出す）・`screen`（`lazy` の遅延読み込み、DSL と同じ形）・`layout: 'SHELL'`・`access: 'ADMIN'`、サイドバー `id: 'invitation'`・`labelKey: 'invitation.nav.label'`・`order: 220`・`visibleWhen: 'ADMIN'`、`messages: invitationMessages`
- [x] 対応: W1〜W10、D1〜D4・D9〜D11・D14、NFR2.1・NFR6.3・NFR6.4・NFR9.2、部品 3節・6節

### Step 14: 画面の振る舞い — 画面・状態・登録 — テスト（vitest-axe を含む）

- [x] `InvitationAdminPage.test.tsx`（`api` を差し替え、時差を固定する。部品 7節の `InvitationAdminPage` の行を1つずつ）:
  - 開くと1ページ目を読む、ページ送りと件数の範囲の読み上げ、押したボタンが読んだ先で押せなくなったときは一覧の見出しにフォーカス、`items` が空で `total` が 1 以上のとき最後のページを読む
  - 重なった読み直しで古い答えを捨てる（先の答えを後に返しても後の読み直しの答えだけ）、部品を外した後の答えで状態が変わらない（`performance-design.md` 2.2）、偽の時計を数分進めても一覧の API が追加で呼ばれない（NFR6.3）
  - 招待: SENT で Modal を閉じて Toast と1ページ目の読み直し・フォーカスが「招待する」、FAILED で失敗の知らせと1ページ目、400 でメールアドレスの項目の誤り、409 の登録済み、409 の招待中の案内から行へ移る（同じページ・別のページ・行が無い・応答の `page` が最後を超える）、503 で Modal の中の理由の文と閉じた後の読み直し、一般の失敗（403・知らない `code`・500・通信の失敗）で Modal の中の一般の文言
  - 送り直し: 200 の SENT・FAILED（読み直しと同じ行の「送り直す」へのフォーカス）、404（読み直しと行が無ければ一覧の見出し）、503（読み直しと受け口へのフォーカス）、403・5xx・通信の失敗（読み直さずフォーカスが「送り直す」のまま）。機能設計の承認の場の U5 R-01 の3区分を1つずつ
  - 取り消し: 204（Toast・読み直し・一覧の見出しへのフォーカス、最後の1件で前のページ、空になれば空の表示の文）、404、一般の失敗（読み直さず元の「取り消す」）
  - 失敗の知らせが1つだけで置き換わる・閉じられる、一覧を読む前と読めなかった間に「招待する」が押せない、設定が無いときの警告と押せなさ、en の文言
  - `detail` に目印の文字を入れた応答（知っている `code`・知らない `code`・`code` なし）で、その文字が画面に出ない（NFR9.1）
  - 403 の応答の表示（一般の 4xx の文言、NFR9.2）
  - 一覧の表示・招待・送り直し・取り消しの流れ（成功・失敗・通信の失敗を含む）の後に、localStorage・sessionStorage のすべての鍵の値にテストのメールアドレス・氏名が無い、ページ送りの後も `location` のパス・問い合わせ・`#` の後が変わらない、`console` の `log`・`info`・`warn`・`error`・`debug` がどの流れでも呼ばれない（呼ばれたら引数にメールアドレス・氏名が無いことも見て失敗の説明に出す）（NFR2.1、`security-design.md` 2.2）
  - vitest-axe の違反 0 件（行ありの状態）
- [x] `registration.test.ts`: 画面とサイドバーの項目の値（`/admin/invitations`・`SHELL`・`ADMIN`・order 220・`visibleWhen: 'ADMIN'`）、既存の登録（auth・admin・dsl）と合わせて `validateRegistrations` が通る（パスの重なり・ja・en の鍵のそろい・接頭辞）、文言の ja・en の鍵が一致して空でない、文言の値に `24` と HTML のタグの形が無い（NFR8.1、機能設計の R-02 の扱い）
- [x] 単体のコマンドを実行して通す。続けて U5 の範囲のカバレッジを見て、行 80%・分岐 70% に届かない U5 の新しいファイルがあればテストを足す
- [x] 対応: NFR2.1・NFR6.3・NFR6.4・NFR7.2・NFR8.1・NFR9.1・NFR9.2、部品 7節の `InvitationAdminPage`・`registration.ts` の行、AC1.1.2・AC1.1.4〜AC1.1.8・AC1.1.10・AC2.1.4・AC2.1.5・AC2.2.3・AC2.2.4・AC2.2.7〜AC2.2.12、CR1.1・CR1.4・CR6.3・CR6.4

### Step 15: 実際のブラウザの検査（060 と e2e/support）— 実装

- [x] `frontend/e2e/support/adminLogin.ts`: `loginAsAdmin(page)`（`/` を開き、ログインの画面のフォーム（`login-form-email-input`・`login-form-password-input`・`login-form-submit-button`）から `playwright.config.ts` の `adminEmail`・`adminPassword` でログインし、`home-page` が見えるまで待つ。既存の 030・040 と同じ道）と `openSidebarItem(page, name)`（サイドバーのリンクを名前で押す）。初期管理者の設定は変えず読むだけ（9節の決定 2）。既存の 030・040 は変えない
- [x] `frontend/e2e/support/invitationFixtures.ts`: `src/features/invitation/api/types.ts` の型を付けた一覧の見本1つと、その組み立ての関数（行ありは 20 行・全件数 21 以上・`invitationEnabled: true`、行に SENT・FAILED、期限内・期限切れ、`invitedBy` が空（2.1 の NFR 設計の U5 R-01 のコメント）、言語 ja・en を含める。警告は `invitationEnabled: false`・理由2つ・行あり）。メールアドレスは `example.com` の下の固定の値、氏名は架空の値
- [x] `frontend/e2e/support/invitationSeed.ts`（測定の準備。`performance-design.md` 4.2）: `requestAdminAccessToken(request)`（Playwright の `request` で `POST /api/auth/login`、アクセストークンを返す。値を注記・添付・標準出力に出さない）、`readInvitationList(request, token)`、`seedInvitations(request, token, count, runTag)`（招待の API で `u5-perf-<印>-<番号>@example.com`・言語 `ja` の招待を置き、各応答が 201 であることを確かめる。`sendResult` は SENT・FAILED のどちらでもよい）。実行の印は実行ごとに重ならない値（時刻と乱数から作る）
- [x] `frontend/e2e/support/axe.ts`: 既知の違反の一覧を、050 の今の形（ログインの画面の2つの名前）を変えずに、画面の状態ごとの一覧を渡せる形に広げる（例: `splitKnownViolations(summary, brandColor, knownTestIds?)` の任意の引数。省略すると今の `KNOWN_VIOLATIONS` のまま）。U5 の状態ごとの一覧（green・orange の組の `color-contrast` で、primary の Button で、名前が一致するもの）は、Step 16 の最初の実行の結果で当たる名前を確かめてから書く（9節の決定 4）
- [x] `frontend/e2e/060-invitation-accessibility.e2e.ts`:
  - 20 組のテスト（組ごとに1つのテスト。題は組の名前だけで、メールアドレス・値を入れない）: `prepareCombo`（U4 の手伝い）で組を当て、一覧の API を `page.route('**/api/admin/invitations?**', ...)`（`page=` の付いた `GET` だけ）で見本の行ありの答えに差し替え、`loginAsAdmin` → `openSidebarItem('利用者の招待')` → 一覧（行あり）が出て組が効いたこと（`expectComboApplied`）を確かめる → axe と横のはみ出し → 「招待する」で招待の入力の Modal → axe と横のはみ出し → 「やめる」 → 1行目の「取り消す」で取り消しの確かめの Modal → axe と横のはみ出し → 「やめる」。(a)・(b) の組だけ、差し替えを警告の見本に替えてページを読み込み直し（Cookie のリフレッシュトークンで復元）、警告の表示 → axe と横のはみ出し。Modal は開くだけで、招待・取り消しの要求を送らない（差し替えは一覧の `GET` だけで、`POST` は差し替えない。要求の一覧で招待の管理の `POST` が0件であることを確かめる）
  - (c) の組では、Tab でフォーカスした行の「取り消す」が表示の幅の中に入ること（`boundingBox` が表示の幅の中）を確かめる（機能設計の承認の場の U5 R-01。矢印のキーは使わない）
  - 各状態の記録（組・状態・想定外の違反の件数と規則の名前・既知の違反・`incomplete` の規則の名前と件数・はみ出し）を注記（`test.info().annotations`）と添付（JSON）に残す。U4 の手伝い（`runAxe`・`missingRequiredRules`・`splitKnownViolations`・`measureHorizontalOverflow`・`watchPage`）を使い、CSP の違反と画面の問題（`watchPage`）を各組で失敗の条件にする
  - 画面の時間の測定のテスト（1つ）: 先に `requestAdminAccessToken` と `readInvitationList` で `invitationEnabled` を確かめ、偽なら `test.skip` とし理由（招待を使える設定が無い）を注記に残す（コメントに、E2E の WAR には SMTP とベース URL が渡るため念のための備えであること、ログインと一覧は設定が無くても成功すること（`InvitationAdminApiIT.notConfigured`）を書く。NFR 設計の承認の場の U5 R-01・R-02）。真なら `seedInvitations` で 21 件を置き、5回くり返す: 新しいコンテキスト（`browser.newContext`、キャッシュが空）→ 監視（`watchPage`）を張る → `loginAsAdmin` → サイドバーの「利用者の招待」を押す直前から表の本文の 20 行目（位置 `nth(19)`）が見えるまで → 「次へ」を押す直前から2ページ目の最初の行が見えるまで → 「前へ」で1ページ目へ戻す → CSP の違反 0 件と画面の問題 0 件を確かめる → コンテキストを閉じる。一覧の API と `/api/appearance` は差し替えない。時間では失敗させず、5回の値（ミリ秒）と目標以内の回数（一覧 2,000・次のページ 1,500）を注記と添付に残す
  - 1回目で、本物の一覧の応答（`page.waitForResponse`）の項目の名前と型が `invitationFixtures.ts` の見本と一致することを確かめる（`project.md` の Corrections の「見本を1つにして本物の応答と照らす」。一致しなければ失敗）
  - `test.step` の題・注記・添付に、アクセストークン・パスワード・初期管理者のメールアドレス・測定の招待のメールアドレスを入れない（基盤の設計の N9、`cicd-pipeline.md` 5節）
  - 既存の 010〜050 は変えない
- [x] 対応: NFR6.1・NFR6.2・NFR7.3・NFR7.4・NFR9.3・NFR9.9、NFR 設計の Q1 A・Q2 A・Q3 A と承認の場の U5 R-01・R-02、基盤の設計の N9・D7

### Step 16: 実際のブラウザの検査 — 実行と確かめ

- [x] `./gradlew :backend:bootWar` で WAR を作り、`docker compose --profile mail up -d mailpit` で Mailpit を起動して（基盤の設計の N2）、`(cd frontend && caffeinate -i npx playwright test e2e/060-invitation-accessibility.e2e.ts)` で 060 だけを流す（`unit-test-instructions.md` 2節）
- [x] 1回目の結果で、green・orange の組の各状態で当たった `color-contrast` の違反の名前（`data-testid`）が、U5 の primary の Button（`invitation-invite-button`・`invitation-invite-submit` など）だけかを確かめる。9節の決定 4 のとおり、U5 の状態ごとの既知の違反の一覧に書いて流し直す。primary の Button 以外（danger の「取り消す」・Badge・Table の要素・AppShell など）の違反や、blue・purple の組の違反が出たときは、U5 のコード（CSS・部品の選び方）で直せるかを見て、直せるなら直して流し直す。直すのに make-you-chic-ui の変更が要るときは、生成を止めて依頼者に諮る（9節の決定 6 の (b)）
- [x] 375px の組で、文書の横のはみ出しが無いこと（表は Table の包む要素か AppShell の中身の領域の中だけで横に動く）と、`scrollable-region-focusable` の違反が無いことを確かめる。はみ出しや違反が AppShell（`vendor/`）に因り U5 のコードで直せないときは、止めて諮る（9節の決定 6 の (c)）
- [x] 20 組の結果（状態ごとの成否・違反の件数・既知の違反・`incomplete`）と、画面の時間（5回の一覧と次のページの値、目標以内の回数）、CSP の違反の件数、本物の一覧の応答と見本の照合の結果、測定の準備の結果（`invitationEnabled`・21 件の 201・`sendResult` の内訳）を記録する。目標を超えた回があれば、要求の一覧と時刻で一覧の API・画面の塊の読み込み・描画のどれが遅いかを切り分けて記録し、承認の場で依頼者に相談する（目標を緩めない。時間は関門にしない。NFR 要件の2節）
- [x] `frontend/test-results/e2e-results.json` を開き、入っているのが組の名前・状態・テストの題・`test.step` の題・時間・規則の名前・件数・要素の名前だけで、アクセストークン（`eyJ` で始まる値）・仮のパスワード・初期管理者のメールアドレス・測定の招待のメールアドレス（`u5-perf-`）が入らないことを、値の文字列の検索で確かめて記録する（報告の部品 `playwright-secret-check-reporter.ts` の確かめも通ること）。`test.step` の題が json の `steps` に入るかを記録する（基盤の設計の N9）。結果のファイルはコミット・共有しない
- [x] 060 を2回続けて流し、結果（組・状態の成否）が同じであることを確かめる（不安定な検査を残さない、`team.md` の Testing Posture）。2回目の実行でも測定が 21 件を新しく置き、1ページ目が 20 行になることを確かめる（前の実行の招待は一時の内部DB とともに消えている）
- [x] 終わったら Mailpit は止めず消さない（README の手順のとおり開発者が片付ける。Step 20 でも使う）
- [x] 対応: NFR6.1・NFR6.2・NFR7.3・NFR7.4・NFR9.3・NFR9.9、基盤の設計の N2・N9

### Step 17: ビルドの成果物の確かめ（CSP・埋め込み・依存・大きさの前後）

- [x] `backend/src/main/resources/application.yaml` の CSP に差分が無いこと（`git diff --stat` に出ない）を確かめる（NFR9.3）
- [x] ビルドした `frontend/dist/index.html` に埋め込みのスクリプト・スタイルが無いこと（`<script>` は `src` つきだけ、`<style>` なし）を確かめる（NFR9.3）
- [x] `frontend/package.json`・`frontend/package-lock.json` に差分が無いこと（NFR9.4）と、`vendor/make-you-chic-ui` の固定先と中身に差分が無いこと（`git submodule status`・`./gradlew vendorUnchanged`、NFR9.5）を確かめる
- [x] 変更の後の値を記録し、Step 1 と比べる: 初回の JavaScript（gzip、500 KB の目安の警告の有無）、U5 の画面の塊が入口と別のファイルに出ること（`dist/.vite/manifest.json` で `src/features/invitation/InvitationAdminPage.tsx` が動的な読み込みの塊であること）とその塊の大きさ（圧縮前と gzip）、`dist/assets/` の JavaScript の合計、WAR の大きさ（NFR6.6、`performance-design.md` 5節）
- [x] 対応: NFR6.6・NFR9.3〜NFR9.5

### Step 18: 静的検査とカバレッジ

- [x] `./gradlew frontendFormatCheck frontendLint frontendLintCss frontendLicenseCheck frontendTypecheck` を通す（Prettier・oxlint・ESLint（`react-hooks`）・Stylelint・ライセンスヘッダー・`tsc`。`frontend/e2e/` の新しいファイルも対象。一覧の見本と `api/types.ts` の型の食い違いはここで止まる）。リンタの決まり（`react/no-danger` など）を緩めない
- [x] `./gradlew frontendCoverage` でフロントエンドのカバレッジの下限（行 80%・分岐 70%）を満たすことを確かめ、全体の値と U5 の新しいファイル（`shared/format/formatDateTime.ts` を含む）の値を記録する。計測の除外を増やさない（`frontend/e2e/` は既存どおり計測の対象外。NFR9.6）。届かないときはテストを足し、それでも届かなければ生成を止めて諮る（9節の決定 6 の (d)）
- [x] 対応: NFR9.6、`team.md` の Code Style・Testing Posture

### Step 19: 文書（README）

- [x] `README.md` に新しい節「招待の管理の画面（U5）」を足す: 画面の置き場と開き方（サイドバーの「利用者の招待」、`/admin/invitations`、管理者だけに出すが判定はサーバー側）、一覧（20 件ごと・サーバーの順・今のページは URL に載せない・自動の読み直しは無く、開いたまま期限を過ぎた行は次に読むまで「期限内」のまま）、招待・送り直し・取り消しの画面の動き（送信の間は閉じない・画面の側に時間切れを置かない既知の限界）、招待を使えないときの警告、横の領域の実際の動き（Tab で届いた要素が見える位置まで動く。矢印のキーでの手動の横の移動は無い。機能設計の承認の場の U5 R-01）、日時の書式の関数の置き場（`frontend/src/shared/format/`）、契約との差（C5 の `invitedBy` は U3 の節の差を参照）、ブランドカラーの既知の制約が U5 の primary のボタンにも当たること（「画面の表示の設定（U4）」の既知の制約を参照）
- [x] 「画面の表示の設定（U4）」の「既知の制約（ブランドカラーのコントラスト）」の節に、U5 の画面（招待の管理の画面の primary のボタン。当たる名前は Step 16 で確かめたもの）も当たることと、060 の検査が状態ごとの一覧でこれを既知の違反として扱うことを書く（9節の決定 4）
- [x] 「ビルドした WAR での画面の確認（E2E）」の表に `060-invitation-accessibility.e2e.ts` の行（招待の管理の画面の 20 組のアクセシビリティの検査（一覧・警告・招待の入力・取り消しの確かめ）と横のはみ出し、一覧と次のページの時間の測定（5回）。ログインする。時間は記録だけで失敗させないこと、流れの E2E に数えないこと、測定は招待を 21 件置き Mailpit に 21 通届くこと、060 だけを流すときも Mailpit の起動が要ること）を足す（`cicd-pipeline.md` 4.3・4.4）
- [x] E2E の節に、E2E のメールの片付けの書き方を1つにまとめて書く: E2E は Mailpit のメールを消さず、開発者が `e2eTest` の後に9節の決定 5 の書き方で止めて消す（「手元でメールを見る」の手順を参照）。U6・U7 はこの書き方に従う（基盤の設計の承認の場の U7 R-01・D7）
- [x] 対応: `cicd-pipeline.md` 4.3・4.4、基盤の設計の承認の場の U7 R-01・D7、機能設計の承認の場の U5 R-01・G1

### Step 20: E2E の全件（統合の前。`team.md` の Testing Posture）

- [x] Mailpit を起動したまま（止まっていれば `docker compose --profile mail up -d mailpit`）`caffeinate -i ./gradlew e2eTest` を流し、既存の 010〜040 の 6 件・050 の 21 件・060 の 21 件がすべて通ることを確かめる。030 のサイドバーの「管理」の確かめが変わらず通ることを確かめる
- [x] 060 の時間の値を Step 16 の値と並べて記録する。Mailpit は止めず消さない
- [x] 代表の流れの E2E-1（招待から登録の完了まで）は U6 が B5 で書く。U5 の段では E2E-1 は無いため、U5 の変更の後に E2E-1 が通ることの確かめは U6 の計画に引き継ぐ（NFR9.9）
- [x] 対応: NFR9.9、`team.md` の Testing Posture（画面に関わる変更を統合する前に E2E）

### Step 21: 1コマンドの検査（統合の前の関門）

- [x] colima を動かし、README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡して `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を実行し、すべての段が通り、対象DB のテストが SKIPPED でないことを確かめる
- [x] テストの件数（バックエンド・フロントエンド）と、フロントエンドの全体のカバレッジと、バックエンドの全体のカバレッジ（変わらない見込み）を実測の数字で記録し、Step 1 と比べる（既存のテストが減っていない・失敗していない）
- [x] 秘密情報の検出（Gitleaks）と依存関係の脆弱性検査（OSV-Scanner）が通ることを確かめる（lockfile を変えないため OSV-Scanner は UP-TO-DATE になりうる。依存を変えていないことを Step 17 で確かめているため、UP-TO-DATE のときはその旨を記録する）。`vendorUnchanged` が通ることを確かめる
- [x] 対応: B5 の共通の完了の条件、`project.md` の Mandated（統合の前の確認）

### Step 22: 記録とコミットの提案

- [x] `code-summary.md`（作ったもの、上流と計画との差、Step 1・Step 21 の実測、Step 2 の前提の確かめ（固定先・口・AppShell・API の形・U5 R-02・G6）、Step 16・20 の 060 の結果と時間と json の確かめ、Step 17 の成果物の確かめと大きさの前後、9節の決定の反映、横の領域の実際の動き、既知の違反の一覧に足した名前）、`source-manifest.json`（作った・変えたアプリのソースのすべてのパス）、`traceability.json`（機能設計の OK 37 件と NFR の枝番から手順と部品へ。Deferred は機能設計の分け方にそろえる）を作る
- [ ] 3節の C1〜C6 の区切りで、コミットの内容とメッセージの案を依頼者に示して承認を得る（生成の担当はコミットしない）
- [ ] 対応: 段の記録、`project.md` の Change Control

## 6. ストーリー・要件と手順の対応

| ストーリー・要件 | 受け入れ基準・決まり・NFR | 手順 |
|---|---|---|
| US1.1 利用者を招待する | AC1.1.1（W4、言語の初期値） | Step 11〜14 |
| US1.1 | AC1.1.2・AC1.1.8（W6、Toast と一覧への追加、成功で閉じる差は機能設計 9節の (a)） | Step 13・14 |
| US1.1 | AC1.1.3・AC1.1.10（W6、400・409 の項目の誤り） | Step 7〜14 |
| US1.1 | AC1.1.4（W6・W7、招待中の案内から行へ） | Step 7〜14 |
| US1.1 | AC1.1.5・AC1.1.7（W6、201 の FAILED の失敗の知らせ） | Step 13・14 |
| US1.1 | AC1.1.6（W3・W6、警告と押せなさ・503） | Step 7・8・11〜14 |
| US1.1 | AC1.1.9（W5、送信中は押せず「送信しています」） | Step 11・12 |
| US1.1 | AC1.1.11〜AC1.1.13 | Deferred（u3-invitation、機能設計の網羅の記録のとおり） |
| US2.1 招待の状況を一覧で確かめる | AC2.1.1・AC2.1.2・AC2.1.6〜AC2.1.8（W1、列・文字で示す状態・ボタンの名前・トークンを出さない・招待した管理者） | Step 3・5・9〜12 |
| US2.1 | AC2.1.4・AC2.1.5（W1・W2、空の表示・サーバーの順・20 件ごと） | Step 7・8・11〜14 |
| US2.1 | AC2.1.3 | Deferred（u3-invitation） |
| US2.2 招待を送り直す・取り消す | AC2.2.2・AC2.2.3・AC2.2.8・AC2.2.12（W8、送り直しと応答） | Step 11〜14 |
| US2.2 | AC2.2.4・AC2.2.10・AC2.2.11（W9、取り消しの確かめ） | Step 11〜14 |
| US2.2 | AC2.2.7・AC2.2.9（W8・W9、404・503） | Step 7〜14 |
| US2.2 | AC2.2.1・AC2.2.5・AC2.2.6・AC2.2.13 | Deferred（u3-invitation） |
| CR1 言語の適用 | CR1.1・CR1.4（7節の文言、画面の言語） | Step 3・12・14 |
| CR6 画面の共通の決まり | CR6.1〜CR6.8（D4〜D13） | Step 11〜14 |
| CR6 | CR6.9 | Deferred（u6-registration-ui・u7-preferences-ui） |
| トークン・URL・個人に関する値 | NFR1.1・NFR2.1 | Step 3・9〜14・15・16 |
| 性能 | NFR6.1・NFR6.2（Step 15・16・20 で記録、Build and Test に引き継ぐ）、NFR6.3（Step 13・14）、NFR6.4（Step 11〜14）、NFR6.5（既知の限界の記録、Step 19）、NFR6.6（Step 1・17） | Step 1・11〜17・19・20 |
| アクセシビリティ | NFR7.1・NFR7.2（Step 11〜14）、NFR7.3・NFR7.4（Step 15・16・20） | Step 11〜16・20 |
| 多言語 | NFR8.1・NFR8.2 | Step 3・11・12・14 |
| 画面の側のセキュリティ | NFR9.1（Step 7〜14）、NFR9.2（Step 13・14）、NFR9.3（Step 11・15〜17） | Step 7〜17 |
| 依存と make-you-chic-ui | NFR9.4・NFR9.5 | Step 2・17・21 |
| テストとカバレッジ | NFR9.6（Step 18・21）・NFR9.7（Step 8）・NFR9.8（Step 5・6）・NFR9.9（Step 15・16・20） | Step 4〜8・15・16・18・20・21 |

## 7. テストの量（Standard）

部品ごとに 5〜8 件の単体テストと、境界の確かめ（ApiClient と fetch、make-you-chic-ui の部品との結び目、実際のブラウザの検査と測定）を置く。成功の場合と、少なくとも2つの失敗・境目の場合を含める（`phases/construction.md` の Testing Standards）。画面部品ごとに vitest-axe の検査を1件入れる（`team.md` の Testing Posture）。

| 部品 | 単体（Vitest） | 実際のブラウザ（Playwright） |
|---|---|---|
| 日時の書式（`shared/format/formatDateTime.ts`） | `formatDateTime.test.ts`（DSL から移す既存の確かめ。件数は変えない） | — |
| ページの計算（`paging.ts`） | `paging.test.ts` 6 件＋性質ベース 1 件 | — |
| フォーカスの行き先（`focusTarget.ts`） | `focusTarget.test.ts` 5 件 | — |
| 警告の文の形（`unavailable.ts`） | `unavailable.test.ts` 5 件 | — |
| 空の判定（`inviteInput.ts`） | `inviteInput.test.ts` 4 件＋性質ベース 1 件 | — |
| 失敗の文言の鍵（`failureMessage.ts`） | `failureMessage.test.ts` 6 件 | — |
| API（`api/invitationApi.ts`） | `invitationApi.test.ts` 8 件（要求の形・決まった項目だけ・読めない本文と 204・失敗の受け渡し・2つの追加の項目の読み取り） | — |
| `InvitationList` | `InvitationList.test.tsx` 8 件（場面をまとめる。vitest-axe を含む） | — |
| `InvitationUnavailableAlert` | `InvitationUnavailableAlert.test.tsx` 5 件（vitest-axe を含む） | — |
| `InviteDialog` | `InviteDialog.test.tsx` 8 件（vitest-axe を含む） | — |
| `CancelConfirmDialog` | `CancelConfirmDialog.test.tsx` 6 件（vitest-axe を含む） | — |
| `InvitationAdminPage` と `useInvitationAdmin` | `InvitationAdminPage.test.tsx` 16〜20 件（読み込みと重なり 4・招待 5・送り直し 4・取り消し 3・漏えいと 403 と en と axe 4。応答ごとの動きが多いため Standard の目安を超える） | — |
| 機能の登録と文言（`registration.ts`・`messages.ts`） | `registration.test.ts` 5 件 | — |
| 既存の DSL の画面（読み込み先の変更） | 既存の `DslStatusPanel`・`DslConfirmDialog`・`DslHistoryTable`・`format` のテストを期待を変えずに通す | — |
| 実際のブラウザの検査（`060-invitation-accessibility.e2e.ts`） | — | 20 組のテスト 20 件（1組に3〜4つの状態）、画面の時間の測定 1 件（本物の一覧の応答と見本の照合、CSP の違反を含む） |

この Intent の代表の流れの E2E（招待から登録の完了まで）は U6 が B5 で足す。060 は流れの E2E ではないため本数に数えない（NFR9.9）。

## 8. この計画で決めたこと・承認済みの文書との差

承認済みの文書は書き換えず、差をここと `code-summary.md` に記録する（`project.md` の決まり）。

| 対象 | 承認済みの形 | この計画での扱い | 理由 |
|---|---|---|---|
| 層の順番 | Testing Contract の `plan_profile.steps` は「データの形 → DB アクセス → 業務処理 → API → 画面の振る舞い」 | データの形（DB）とデータアクセスの層は無し。先に既存の日時の書式の移動を置き、業務処理（純粋な関数）→ API → 画面の振る舞い（画面部品 → 画面と状態と登録）→ 実際のブラウザの検査の順 | U5 は内部DB とブラウザの保存を持たない。移動を先に分けると、DSL の画面への影響を U5 の新しいコードと混ぜずに確かめられる（test-after の方法は変えない） |
| 検査のファイルの番号 | 「`050-` の後の番号、B5 のコード生成の計画で U6・U7 とそろえる」（`logical-components.md` 5.1） | `060-invitation-accessibility.e2e.ts`。B5 の4つのファイルの番号は9節の決定 2 | 承認の場の U6 R-02・U7 R-02 の決定のとおりこの計画で決める |
| 共用の手伝いの名前と形 | 「ログインの関数と一覧の見本を足す。形と置き場は B5 の計画で U6・U7 とそろえる」 | `support/adminLogin.ts`（`loginAsAdmin`・`openSidebarItem`）・`support/invitationFixtures.ts`・`support/invitationSeed.ts`。初期管理者の設定は変えない | 同上。U6 の E2E-1 と U7 の測りが同じログインの関数を使える形にする |
| 既知の違反の一覧の形 | U4 の `axe.ts` はログインの画面の2つの名前の1つの一覧 | 画面の状態ごとの一覧を任意の引数で渡せる形に広げる（050 の判定は変えない）。U5 の primary の Button の名前を状態ごとに書く | green・orange の primary の Button のコントラスト不足は、U4 の決定で「ほかの画面の primary のボタンも同じ」既知の制約と README に書かれている。状態ごとに当たる名前が違うため（9節の決定 4） |
| 一覧の API の差し替えの範囲 | 「一覧の API（`GET /api/admin/invitations`）の答えだけ」 | 問い合わせ `page=` の付いた `GET` だけを受け、`POST` は差し替えない。要求の一覧で招待の管理の `POST` が0件であることを確かめる | Modal を開くだけで要求を送らないことを検査の中で確かめるため |
| 横の領域の矢印のキー | 機能設計の W1 の5・9節の (j)「矢印のキーで横に動かせ」 | 矢印のキーの処理は作らず、Tab で届いた要素が見える位置に入ることだけを確かめる | 機能設計の承認の場の U5 R-01（Accepted risk、コード生成で実際の動きに合わせる） |
| 狭い幅でのサイドバー | NFR 設計は「サイドバーの『利用者の招待』を押して開く」 | 375px でもサイドバーが出たままのため（AppShell に狭い幅の切り替えが無い）、同じ手伝いで押す。押せないときは止めて諮る（9節の決定 6 の (c)） | 計画を書く時点で AppShell の CSS を読み取りで確かめた |
| 招待した管理者が空の行の見本 | NFR 設計の 5.3（行に「（不明）」を含める） | 見本と画面部品のテストの見本に、今の API では起きない状態を確かめる防御のための行であるとコメントを書く | NFR 設計の承認の場の U5 R-01 |
| 飛ばす判定の前提 | NFR 設計の 4.2（`invitationEnabled` が偽なら `test.skip`） | 前提（設定が無くてもログインと一覧は成功する）を `InvitationAdminApiIT.notConfigured` で確かめたことを 060 のコメントと `code-summary.md` に書く | NFR 設計の承認の場の U5 R-02 |
| 測定の実行の印 | 「実行の印と番号を組み合わせた `u5-perf-<印>-<番号>@example.com`」 | 実行の印は時刻と乱数から作る。json・注記・添付・題に出さない | 公開のリポジトリ・json の結果に値を残さないため（`cicd-pipeline.md` 5節） |
| 画面部品のテストの補助の名前 | 部品 2節「`testing/`（文言を登録した Provider の中に描く補助と、応答の見本）」 | `testing/renderInvitation.tsx`・`testing/fixtures.ts` | DSL の `testing/renderDsl.tsx`・`fixtures.ts` の形に合わせた |
| `InvitationAdminPage.test.tsx` の件数 | Standard の目安は部品ごとに 5〜8 件 | 16〜20 件 | 応答ごとの動き（6.1〜6.4）と漏えいの確かめを1つの画面で確かめるため。目安を超える側で、減らさない |
| Table の列の数 | 機能設計の W1 の4・7節は列の見出しの鍵を8つ（メールアドレス・言語・招待した管理者・招待した日時・有効期限・送信の結果・状態・操作）、部品 4節は「`columns` は7列」 | 見出しの鍵の8つをそのまま8列にする（見出しの鍵ごとに1列） | 部品 4節の「7列」は数え違いと読み、機能設計 7節の鍵と W1 の4の項目の並びを正とした（9節の決定 3 で依頼者が決めた） |

## 9. 依頼者の決定

計画の承認の前に諮った論点について、依頼者がすべて推奨のとおり（A）に決めた。計画の各 Step と8節はこの決定に合わせてある。

1. **作業の場と統合の単位**: A。単位ごとに `develop` から短命のブランチを作り、単位ごとに squash で統合する。U5 は `feature/260925-user-management-b5-u5` を作り、U5 の分を squash で `develop` へ統合する。U6・U7 もそれぞれの計画で別のブランチにする。`develop` には B5 の3単位が3つのコミットで入る。`team.md` の「1 Bolt が `develop` の1コミット」とは違うが、この Bolt の扱いとして依頼者が承認した（理由: ブランチを短命に保ち、単位ごとに `verify` と E2E の全件を通して統合できる。B4 で U8 を先に独立して統合した前例がある）。コミットは生成の後に依頼者の承認を得て 3節の C1〜C6 でまとめて行う（生成の担当はコミットしない）。push は依頼者。
2. **B5 の検査のファイルの番号と並び、共用の手伝い（U6・U7 に引き継ぐ）**: A。`060-invitation-accessibility.e2e.ts`（U5）・`070-registration-accessibility.e2e.ts`（U6 の検査）・`080-preferences-accessibility.e2e.ts`（U7 の検査と測り）・`090-invitation-registration-flow.e2e.ts`（U6 の E2E-1、代表の流れ）。代表の流れを最後に置き、前のファイルが残した招待・利用者・監査に頼らないことを毎回確かめる。共用の手伝い `support/adminLogin.ts`（ログインと画面の開き方）・`support/invitationSeed.ts`（要求の口での用意）・`support/invitationFixtures.ts`（一覧の見本）は U5 が作り、U6・U7 はこれを使う。初期管理者の設定（`adminEmail`・実行ごとに作るパスワード）は変えない。B5 の単位の順は U5 → U6 → U7（U6 の E2E-1 が U5 の画面を通り、U7 が U6 の `shared/validation` を使うため）。
3. **Table の列の数**: A。機能設計 7節の見出しの鍵の8つをそのまま8列にする（メールアドレス・言語・招待した管理者・招待した日時・有効期限・送信の結果・状態・操作）。`frontend-components.md` 4節の「7列」は数え違いとして8節と `code-summary.md` に差を記録する。
4. **ブランドカラー green・orange の primary の Button のコントラスト不足（U4 の既知の制約）**: A。U4 の既知の制約の範囲とし、U5 の画面の状態ごとの既知の違反の一覧に、U5 の primary の Button の名前（`data-testid`）だけを足す。規則は `color-contrast`、組は green・orange、要素は primary の Button に限り、ほかの規則・要素・組の違反は失敗にし、一覧と一致しない（消えた・増えた）ときも失敗にする。050 の一覧と判定は変えない。README の「画面の表示の設定（U4）」の既知の制約の節に、U5 の画面（招待の管理の画面の primary のボタン）も当たることを書く（Step 19）。
5. **Mailpit を片付けるコマンドの書き方（基盤の設計の承認の場の U7 R-01）**: A。README の既存の「手元でメールを見る」の2行（`docker compose stop mailpit`・`docker compose rm -f mailpit`）にそろえ、E2E の節からそこを参照する。U6（`docker compose rm -sf mailpit`）・U7（`docker compose --profile mail stop mailpit` など）の設計の書き方は、U6・U7 の計画でこの書き方に置き換えて差に記録する。
6. **生成を止めて諮る場面**: A。次のときは、生成をその手順で止め、結果と候補を示して依頼者に諮る。
     - (a) Step 2 で、U5 が使う make-you-chic-ui の口が `735ef04` に無い・形が違う、または U3 の API の形が契約 C5・機能設計と違う
     - (b) Step 16 で、U5 の画面に、決定 4 の既知の違反の外の axe の違反（danger の Button・Badge・Table の要素のコントラストなど）が出て、U5 のコード（CSS・部品の選び方）で直せない、または直すのに make-you-chic-ui の変更が要る
     - (c) Step 16 で、375px の組の横のはみ出し・`scrollable-region-focusable` の違反・サイドバーの項目を押せないことが AppShell（`vendor/`）に因り、U5 のコードで直せない
     - (d) Step 18 でテストを足してもフロントエンドのカバレッジの下限（行 80%・分岐 70%）に届かない
     - (e) 8節に挙げたもの以外に、既存の画面のテスト（DSL など）・既存の E2E（010〜050）の期待を変える必要が出る（緩めずに作れる形を先に探す）
     - (f) Step 16 で、本物の一覧の応答と見本の形が一致しない、または json の結果にアクセストークン・パスワード・メールアドレスが入り、手伝いの側で防げない
     - なお、画面の時間が目標（一覧 2 秒・次のページ 1.5 秒）を超えたとき、測定が `test.skip`（招待を使える設定が無い）になったときは、止めずに切り分けて記録し、承認の場で相談する（時間は関門にしない。飛ばした測定は Build and Test で `Unverified`）

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

Testing Contract の `plan_profile.steps` との対応: 骨組みと本番の設定は Step 1〜3（作業の場 Step 1・前提の確かめ Step 2・型と文言 Step 3）、テストの実行の準備は Step 4（最初のテストの Step 6 より前）、データの形（DB）とデータアクセスの層は U5 に無い（内部DB とブラウザの保存を持たない）、既存に手を入れる日時の書式の移動は Step 5・6（業務処理の一部として先に分ける。8節）、業務処理（純粋な関数）は Step 7・8、API（`invitationApi`）は Step 9・10、画面の振る舞いは Step 11〜14（画面部品 → 画面と状態と登録）と実際のブラウザの検査 Step 15・16、環境とビルドの確かめは Step 17・18・20・21、文書と記録は Step 19・22。

## Build and Test に引き継ぐこと

| 項目 | 引き継ぐ内容 | 持ち主 |
|---|---|---|
| カバレッジの実測 | `:backend:cleanTest :backend:cleanIntegrationTest` を付けた verify で、フロントエンドの全体と U5 の新しいファイル、バックエンドの全体の値をもう一度実測して記録する | Build and Test |
| verify の時間 | U5 の後の `./gradlew verify` の時間を測り、B4 の後の実測（5 分 44 秒）と比べる | Build and Test |
| 画面の時間（NFR6.1・NFR6.2） | `./gradlew e2eTest` の 060 の測定のテストの5回の値（一覧・次のページ）と目標以内の回数を `frontend/test-results/e2e-results.json` から写して記録する（統合の関門にしない。目標を超えたら切り分けて依頼者に相談、目標を緩めない）。測定が `test.skip` だったときは `Unverified`（持ち主 Build and Test）。運用の中での判定は `Unverified` | Build and Test（記録）・observability-setup・feedback-optimization（運用の判定） |
| 実際のブラウザの検査（NFR7.3・NFR7.4） | 060 の 20 組・状態ごとの成否・違反の件数と規則の名前・既知の違反・`incomplete` を json の報告から写して記録する。json に秘密（アクセストークン・パスワード・初期管理者と測定の招待のメールアドレス）が入らないことをもう一度確かめる | Build and Test |
| CSP（NFR9.3） | 060 の各組と測定の5回で CSP の違反が 0 件であることを記録する | Build and Test |
| 配信物の大きさ（NFR6.6） | コード生成の前後の値（`code-summary.md`）を Build and Test の結果に写す。上限は置かない | Build and Test |
| 招待の API の時間 | 一覧・取り消し p95 1 秒、招待・送り直し p95 5 秒（U3 の NFR6.1・NFR6.3、U3 の引き継ぎのとおり k6） | performance-validation（無ければ Build and Test、`project.md` の学び） |
| 配備と戻し | イメージを作り直して `docker compose up -d`、ヘルスチェックとスモークテスト（管理者でログインし、サイドバーの「利用者の招待」から一覧が開けること）。招待の操作と Mailpit での確かめは U3 のスモークテストで行い、利用者と監査が残ることを先に依頼者に伝える。戻しはイメージだけ（U5 はサーバーの状態とブラウザの保存を持たない）（`cicd-pipeline.md` 6節・7節） | deployment-pipeline（手順）・deployment-execution（実行） |
| B5 の後の単位への引き継ぎ | 9節の決定 1・2・5 の結果（統合の単位、検査のファイルの番号と並び、共用の手伝い `support/adminLogin.ts`・`support/invitationSeed.ts`・`support/invitationFixtures.ts`、既知の違反の一覧の状態ごとの形、Mailpit の片付けの書き方）を U6・U7 の計画で使う。U6: E2E-1 が U5 の画面を通ること（U5 の `data-testid` と画面の名前）、Mailpit の API を実物で確かめること（基盤の設計の N7）、`handOffToLogin` の確かめ（NFR 設計の U6 R-01）、閲覧の履歴の残る危険の確かめ（上流との差 A9）、U7 との `shared/validation` の突き合わせの手順（NFR 設計の U7 R-01）。U7: 飛ばす道は念のための道（基盤の設計の N10）。U5 の変更の後に E2E-1 と既存の E2E（010〜060）が通ることを記録する（NFR9.9） | B5（U6・U7 のコード生成の計画） |
