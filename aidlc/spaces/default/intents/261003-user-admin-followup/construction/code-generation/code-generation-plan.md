# コード生成の計画（261003-user-admin-followup）

- 範囲: bugfix（深さ Minimal、Test Strategy は Minimal）。brownfield。単位の分割は無い（zero-Unit）。成果物の置き場は `aidlc/spaces/default/intents/261003-user-admin-followup/construction/code-generation/`。
- 入力: 要件 `inception/requirements-analysis/requirements.md`（FR1〜FR9・NFR1〜NFR5、承認済み）、質問と答え `requirements-analysis-questions.md`（Q1: D・Q2: A・Q3: A・Q4: B）、要件のレビュー（R-01〜R-07。承認の場で「後の段で具体にする」と受け入れられた）、コード知識ベース `aidlc/spaces/default/codekb/mastersmith2/`（K-17〜K-24）、開発担当のスキャン `inception/reverse-engineering/developer-scan.md`。
- 計画の基準のコード: `develop` の先頭 `8cad222`（Requirements Analysis の承認の記録）。最後に通った `./gradlew verify` は前の Intent（260930-user-admin）の統合の時点のもので、計画の段では全体の verify を流していない。
- 計画の段で行った確かめ: S1 の捨ての確かめ（6節）。捨てのテストのファイルとビルドの出力は消し、`git status` に残りが無いことを確かめた。

## 1. 変える範囲（影響の範囲）

| 区分 | ファイル | 変更 | 要件 |
|---|---|---|---|
| サブモジュール | `vendor/make-you-chic-ui`（固定先 `3d9521aa54b1d6277de473f9e935a496fb56ac1b` → `e82b651c53ac6e048066fcc049e49386c0eb282e`） | 固定先だけを上げる（中身は変えない） | FR1.1 |
| 画面 | `frontend/src/features/useradmin/ConfirmActionDialog.tsx` | `finalFocusRef` を受けて `Modal` へ渡す props を足す | FR1.2 |
| 画面 | `frontend/src/features/useradmin/EditProfileDialog.tsx` | `finalFocusRef` を受けて渡す。送信中は言語の `RadioGroup` に `disabled` を渡す | FR1.2・FR3.1 |
| 画面 | `frontend/src/features/useradmin/UserAdminPage.tsx` | 描画ごとに作り直さない ref（`useRef`）を1つ持ち、行の「操作」から表示を開くときに `actionRefs` から行の「操作」の button を `.current` に入れ、2つの表示へ渡す | FR1.2 |
| 画面 | `frontend/src/features/useradmin/UserRowActions.tsx` | `Dropdown` に `placement="bottom-end"` | FR2.1 |
| 画面のテスト | `ConfirmActionDialog.test.tsx`・`EditProfileDialog.test.tsx`・`UserAdminPage.test.tsx`（同じフォルダ） | 2節の Step のテストを足す | FR1.2・FR3.2 |
| E2E | `frontend/e2e/110-user-admin-flow.e2e.ts`・`120-user-admin-accessibility.e2e.ts` | `inert` が外れるのを待つ形をやめ、閉じた後に行の「操作」が `toBeFocused` であることを確かめる。120 に開いたメニューの矩形の確かめを足す | FR1.3・FR2.2 |
| E2E | `frontend/e2e/support/overflow.ts` | 開いたメニューの矩形が viewport の中に収まるかを測る関数を足す | FR2.2 |
| E2E | `frontend/e2e/060-invitation-accessibility.e2e.ts`・`040-dsl-admin.e2e.ts` | 閉じた後に開いた元へフォーカスが戻ることの確かめを足す（流れは足さない） | FR1.4 |
| 画面（条件つき） | `frontend/src/features/invitation/InviteDialog.tsx`・`CancelConfirmDialog.tsx`・`InvitationAdminPage.tsx`、`frontend/src/features/dsl/DslConfirmDialog.tsx`・`DslAdminPage.tsx` と同じフォルダのテスト | FR1.4 の確かめで戻らない画面があったときだけ、`finalFocusRef` で直す | FR1.4 |
| バックエンドの設定 | `backend/src/main/resources/application.yaml` | 案 A・C のとき: `logging.level.org.hibernate.orm.jdbc.error: OFF`（理由と障害の調べ方をコメントに書く） | FR8.2 |
| バックエンド（案 B・C のとき） | `backend/src/main/java/cherry/mastersmith/common/error/web/GlobalExceptionHandler.java`（と同じパッケージの判定の補助） | 想定外の誤りの ERROR で、一意の制約の違反の連なりはクラスの名前だけを出す（行の排他の失敗の扱いと同じ形） | FR8.2 |
| バックエンドのテスト | `backend/src/test/java/cherry/mastersmith/user/service/UserUniqueViolationSecretLeakIT.java`（新規） | 同時の利用者の作成の漏えいの確かめ | FR8.1 |
| バックエンドのテスト | `backend/src/test/java/cherry/mastersmith/invitation/service/InvitationUniqueViolationSecretLeakIT.java`（新規） | 同時の招待の漏えいの確かめ | FR8.1 |
| バックエンドのテスト（案 B・C のとき） | `backend/src/test/java/cherry/mastersmith/common/error/web/GlobalExceptionHandlerTest.java` | 一意の違反の連なりの ERROR がクラスの名前だけであること | FR8.2 |
| バックエンドのテスト | `backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminOperationsApiIT.java` | 印を外した直後の 403 と監査の行を1つのテストで確かめる | FR5.1 |
| バックエンドのテスト | `backend/src/test/java/cherry/mastersmith/mail/config/MailConfigurationIT.java` | 出力の確かめを、テストのスレッドが起動の後に出した `MailConfig` のロガーの行だけに絞る | FR6.1 |
| 負荷の台本 | `perf/k6/scenarios.js` | 準備のログインを含まない新しい場面 `userAdminPoolLimit` を足す（既存の `userAdminOps` は変えない） | FR4.1 |
| 手順 | `perf/README.md` | 上限を下げる手順・新しい場面・警報の確かめ方・`baseUnit` の注意書き | FR4.3・FR7.1 |

### 1.1 カバレッジとパッケージの一覧（NFR4）

- `backend/build.gradle.kts` の `packagesJudgedByTotal`（今は 7 個: `access.service`・`audit.repository`・`common.error.domain`・`common.error.service`・`common.health`・`common.i18n.domain`・`common.web`）のうち、この計画が `src/main` を変えるものは **無い**。
  - 案 A は `application.yaml`（リソース）だけで、Java のパッケージに当たらない。
  - 案 B・C で変える `common.error.web` は、Intent 260930-user-admin の B4 で一覧から外れており、パッケージごとの下限（行 80%・分岐 70%）の対象である。足す分岐は同じ Step のテストで覆う。
  - そのため、計画の段のカバレッジの実測（`:backend:cleanTest :backend:cleanIntegrationTest :backend:test :backend:integrationTest :backend:jacocoTestReport`）は行っていない。
- 変える画面の部品（useradmin）は Vitest の `thresholds`（行 80%・分岐 70%）の対象のまま。足す分岐（`finalFocusRef` の受け渡し、`disabled`）は同じ Step のテストで覆う。
- カバレッジの数値は Step 19 の `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` の実測だけを記録する（project.md の Testing Posture の学び）。

## 2. 手順

Testing Contract（4節の前の `## Testing Contract`）の `plan_profile.steps` を順の基準にする（test-after。層ごとに実装を書き、同じ Bolt の中でその層のテストを書いて流し、通ってから次の層へ進む）。今回の変更に当たらない層は次のとおり。

- データモデル・DB の振る舞い: 移行（`db/migration`）・表の変更は無い。該当なし。
- リポジトリ・データアクセス: 変更は無い。該当なし。

### 2.1 準備（構成と依存）

- [ ] **Step 1**（準備。FR1.1・NFR3）: 段を始める前の片付けと作業ブランチ。
  - git の対象外の E2E の生成物（`frontend/playwright-report`・`frontend/test-results`）が残っていれば消す（project.md の学び 2026-10-03。段の途中では消さない）。
  - この計画の記録（`aidlc/` の下）のコミットは、オーケストレーターが依頼者に提案して承認を得てから行う。
  - `develop` から短命のブランチ `fix/261003-user-admin-followup` を作る（team.md の Way of Working。worktree は使わない）。
- [ ] **Step 2**（FR1.1）: make-you-chic-ui の固定先を上げる（専用のコミット C1 の中身）。
  - `git -C vendor/make-you-chic-ui checkout e82b651c53ac6e048066fcc049e49386c0eb282e` の後に `git add vendor/make-you-chic-ui`。
  - `git ls-files -s vendor/make-you-chic-ui` で固定先が `e82b651c…` になったこと、`git diff --cached --stat` がサブモジュールの1行だけであることを確かめる。
  - 前後のハッシュ（`3d9521aa54b1d6277de473f9e935a496fb56ac1b` → `e82b651c53ac6e048066fcc049e49386c0eb282e`）を code-summary.md に記録する（project.md の Mandated）。
  - `./gradlew vendorBuild frontendInstall` で `packages/make-you-chic-ui/dist` を作り直す。e82b651 は `package.json` を変えないため、`frontend/package-lock.json` は変わらない見込み。変わったときは止めて、オーケストレーターに知らせる。
  - このコミットにはほかのファイルを入れない。
- [ ] **Step 3**（テストの実行の準備。Testing Contract の `runner_step`）: `unit-test-instructions.md` の範囲を絞ったコマンドが今のまま動くことを、変更の前に1回流して確かめる。
  - 画面: `unit-test-instructions.md` の 2節の Vitest のコマンド（useradmin の3ファイル）。
  - バックエンド: `MailConfigurationIT` と `UserAdminOperationsApiIT` の結合テストのコマンド。
  - 結果（件数・成否）を code-summary.md に記録する。

### 2.2 業務処理の層（バックエンド。S1・FR8）

- [ ] **Step 4**（FR8.2・NFR2）: 計画の承認の場で決めた直し方（6節の案 A・B・C）を入れる。
  - 案 A（C も同じ）: `application.yaml` の `logging.level` に `org.hibernate.orm.jdbc.error: OFF` を足す。
    - 既存の JDBC ドライバー・メールの部品の行と同じ形で、「なぜ」（一意の制約の違反の文に重なった値が入り、メールアドレスをアプリのログに出さない決まりに反する）と「障害の調べ方」（受ける所の業務の結果の型・想定外の誤りの ERROR の例外のクラスの名前、`23505` の SQLState）をコメントに書く。
    - テストは同じ `application.yaml` を読むため、テストの設定には足さない。
  - 案 B（C も同じ）: `GlobalExceptionHandler` の想定外の誤り（5xx）の ERROR で、例外の連なりに一意の制約の違反（`DataIntegrityViolationException` と Hibernate の `ConstraintViolationException`）があれば、行の排他の失敗（`RowLockFailures.isLockFailure`）と同じ形で、例外の文と原因の連なりを付けず、クラスの名前だけを出す。応答は今までどおり 500 `INTERNAL_ERROR`。
  - 業務処理の層（`UserAccountService`・`InvitationService`）の受け方は変えない。
- [ ] **Step 5**（FR8.1・FR8.3・NFR2・NFR5）: 漏えいの確かめのテストを書き、流す。
  - `UserUniqueViolationSecretLeakIT`（`user/service`）: `UserCreationIT` と同じ待ち合わせ（パスワードのハッシュの計算で `CyclicBarrier`）で、同じメールアドレスの作成を2つ重ね、`Created` と `EmailAlreadyUsed` が1つずつになることを確かめる。
  - `InvitationUniqueViolationSecretLeakIT`（`invitation/service`）: `InvitationConcurrencyIT` と同じ待ち合わせ（`TestInvitationBarrier`）で、同じメールアドレスの招待を2つ重ねる。
  - 確かめる出力（どちらのテストも、既定の INFO と、`cherry.mastersmith` のロガーを TRACE にした場合の両方。既存の `*LeakIT` の形）:
    - アプリのログ: 実行の前の出力の長さを覚え、その後に出た標準出力と標準エラー出力に、重なったメールアドレス（大文字・小文字の両方）と H2 の違反の文の目印（`PUBLIC.UK_USERS_EMAIL`・`PUBLIC.UK_INVITATIONS_PENDING_EMAIL` の索引の文）が無いこと。
    - TRACE の場合は、追跡が有効であること（`ENTER UserAccountService#createUser` などがあること）も確かめ、確かめが空振りしていないことを示す。
    - 監査: 重なりの後に `audit_events` の行の文の列（失敗の理由など）に、違反の文の目印とメールアドレスが入っていないこと。
  - 不具合を再現するテストであることの確かめ: テストが通った後に、Step 4 の直しを一時的に外して流し、落ちることを1回確かめて結果を記録し、直しを戻す（`git diff` で戻ったことを確かめる）。
  - エラー応答とトレースの属性は、7節の R-04 のとおり扱う。
  - 案 B・C のとき: `GlobalExceptionHandlerTest` に、一意の違反の連なりを持つ想定外の例外で、ERROR がクラスの名前だけ（原因の連なりと違反の文が無い）になり、応答が 500 `INTERNAL_ERROR` であることのテストを足す。

### 2.3 API の層のテスト（テストだけの変更。T2・T3）

- [ ] **Step 6**（FR5.1）: `UserAdminOperationsApiIT` に、管理者の印を外した直後の要求の 403 と、印を外した操作の監査の行を、1つのテストで続けて確かめるテストを足す。
  - 流れ: 印を付けた利用者でログイン → 管理者がその利用者の印を外す（204）→ その利用者のトークンで `GET /api/admin/users` が 403 `ACCESS_DENIED` → 監査の行を読む。
  - 監査の行の読み方は `UserAdminListApiIT` の前例（`ORDER BY audit_event_id OFFSET ? ROWS`）に合わせる。印を外した操作の行（操作した人・対象の利用者・結果）と、続く 403 の行（`ACCESS_DENIED NOT_ADMIN <path>`）を順に確かめる。
  - 本体のコードは変えない。既存の `flagChangeTakesEffectOnTheNextRequest` は残す。
- [ ] **Step 7**（FR6.1・FR6.2）: `MailConfigurationIT` の確かめを絞る。
  - 起動の前に `output.getOut()` の長さとテストのスレッドの名前を覚え、`configRecords` はその後の出力から、`thread` がテストのスレッドで、`logger` が `MailConfig` の行だけを読む。
  - JSON として読めない行（ほかのスレッドの出力が混ざった行）は飛ばす。補助は `MailConfigurationIT` の中に置き、共通の `JsonLogRecords` は変えない。
  - 秘密の値の確かめ（`getAll()` に値が無いこと）は、範囲を絞らずに残す（無いことの確かめは広い方が強いため）。
  - 再現は試みない（Q3: A）。直した後に、このテストのクラスを単独で **5 回** 流してすべて通ること（R-06）と、Step 19 の verify で通ることを確かめる。

### 2.4 画面の層（FR3・FR1・FR2）

- [ ] **Step 8**（FR3.1）: `EditProfileDialog.tsx` の `RadioGroup` に `disabled={submitting}` を渡す（氏名の欄の `readOnly` と同じ扱い。make-you-chic-ui の `RadioGroup` は固定先 3d9521a・e82b651 のどちらでも `disabled` を受ける）。
- [ ] **Step 9**（FR3.2・NFR5）: `EditProfileDialog.test.tsx` に、送信中は言語の2つの選択肢が押せないこと（`disabled` で、押しても `onChangeLanguage` が呼ばれない）と、送信が終わった状態（`open`・`failed`）では押せることのテストを足す。直しを一時的に外して落ちることを1回確かめ、戻す。
- [ ] **Step 10**（FR1.2）: 閉じた後のフォーカスの戻り先を渡す。
  - `ConfirmActionDialog`・`EditProfileDialog` に省略できる props `finalFocusRef?: RefObject<HTMLElement | null>` を足し、`Modal` へそのまま渡す。
  - `UserAdminPage.tsx` に `useRef<HTMLElement | null>(null)` を1つ持つ（描画ごとに作り直さない。`useFocusTrap` の後始末は、効果を張った時点の関数で `finalFocusRef.current` を読むため）。
  - 行の「操作」から表示を開く `onSelect` で、`actionRefs.current.get(userId)?.querySelector('button')` を `.current` に入れてから `state.selectAction` を呼ぶ。2つの表示へ同じ ref を渡す。
  - Dropdown は trigger の `ref` を置き換えるため、上流の手引きの例（trigger の `Button` に ref を付ける形）は使わない（K-17）。
  - 閉じ方ごとの動き（R-06。コードで確かめた今の動き）:
    - 確かめの表示（`ConfirmActionDialog`）: やめる・Escape・閉じるボタン・成功・操作の失敗のどれでも閉じる。成功と失敗では、さらに一覧の読み直しの後に `focusTarget`（行）の効果が行の「操作」へフォーカスを当てる。行き先はどちらも同じ行の「操作」。
    - 氏名・言語の入力（`EditProfileDialog`）: やめる・Escape・閉じるボタン・成功で閉じる。保存の失敗（`failed`・`notFound`・入力の誤り）では閉じずに表示に残る（フォーカスは表示の中）。その後にやめる・Escape・閉じるボタンで閉じたときに行の「操作」へ戻る。
    - 401・403 の共通の扱い（`handledCommonly`）で閉じたときは、画面がログイン・権限が無い画面へ移るため、行の「操作」は無い。FR1.2 の対象の外とし、code-summary.md に書く。
- [ ] **Step 11**（FR1.2）: 画面部品のテスト。
  - `ConfirmActionDialog.test.tsx`・`EditProfileDialog.test.tsx`: `finalFocusRef` に渡した要素へ、やめる・Escape で閉じた後にフォーカスが移ることを `waitFor` で確かめる（jsdom は `inert` でフォーカスを止めないため、ここでは受け渡しの確かめに留まる。body に落ちる不具合の再現は E2E が受け持つ）。
  - `UserAdminPage.test.tsx`: 既存の「やめた後に行の『操作』へ戻る」テスト（600〜618 行付近）が通ることを確かめ、Escape で閉じた場合を1件足す。
  - 既存のアクセシビリティ検査（vitest-axe）を残す。
- [ ] **Step 12**（FR2.1）: `UserRowActions.tsx` の `Dropdown` に `placement="bottom-end"` を渡す。jsdom は矩形を持たないため、画面部品のテストは足さず、E2E 120（Step 14）を回帰のテストにする（不具合を再現する最も狭い段）。
- [ ] **Step 13**（FR2.2）: `frontend/e2e/support/overflow.ts` に、渡した要素（開いたメニュー）の `getBoundingClientRect()` の左端が 0 以上、右端が `window.innerWidth` 以下、下端が `window.innerHeight` 以下であるかを測る関数を足す。今の `scrollWidth` の判定は残す。
- [ ] **Step 14**（FR1.3・FR2.2・NFR1・NFR5）: E2E 110・120 を直す。
  - 110 の `confirmAction` と 120 の `expectBackgroundInteractive` の3か所で、`body > [inert]` が 0 件になるのを待つ形をやめ、閉じた後に対象の行の「操作」のボタンが `toBeFocused` であることを確かめる形に替える。
  - 120 の確かめの表示（利用を止める）と氏名・言語の入力で、やめるに加えて Escape と閉じるボタンで閉じる場合を確かめる。
  - 120 に「管理者の印を付ける」の確かめの表示を開いて Escape で閉じる確かめを足す（状態を変えない操作。AC2.1.8 の閉じた後のフォーカスを覆うため）。
  - 120 のメニューを開いた状態（`02-self-menu-open`・`11-disabled-item-hover`・`11-disabled-item-focus`）で、Step 13 の関数でメニューの矩形が viewport の中にあることを確かめる。狭い幅（120 の組の viewport）と広い幅の両方で、表の右端の行の「操作」を開いて確かめる。
  - 再現の確かめ: Step 12 の直しを一時的に外して 120 を流し、矩形の確かめが落ちることを1回確かめ、直しを戻す（落ちないときは、その幅でははみ出さないことを記録し、オーケストレーターに知らせる）。
  - FR1 の再現（固定先 3d9521a で新しいフォーカスの確かめが落ちること）の確かめは行わない（4節の依頼者に確かめたいこと D3）。
- [ ] **Step 15**（FR1.4・NFR5）: 利用者の管理の画面の外の `Modal` を確かめる。
  - 対象は `frontend/src` で `<Modal` を使う画面のすべて（計画の段で洗い出した。`App.tsx` は `ModalStackProvider` だけ）: 招待の画面の `InviteDialog`（開く元は「招待する」のボタン）・`CancelConfirmDialog`（行の「取り消す」）、DSL の管理の画面の `DslConfirmDialog`（置き換え・適用・破棄のボタン）。
  - 確かめは E2E で行う（手元のブラウザでは記録が残らないため）。060 の `invite dialog`・`revoke confirmation dialog` の手順で、やめるで閉じた後に開いた元のボタンが `toBeFocused` であることを足す。040 では、確かめの表示を一度「やめる」で閉じて開いた元（「適用する」のボタン）へ戻ることを確かめてから、今の適用の手順を続ける。
  - 成功で閉じる場合（招待の作成・取り消し・適用）は、開いた元が描き直しで消える・押せなくなるため、今の画面の決まり（各画面のフォーカスの効果）に任せ、この Step では確かめない。
  - 戻らない画面があったときは、その画面で開いた元のボタンを指す ref を `finalFocusRef` に渡して直し、同じフォルダの画面部品のテストに受け渡しのテストを1件足す。直した画面の E2E の確かめが、その画面の回帰のテストになる（NFR5 の対象に含める。R-03）。結果（画面ごとの戻る・戻らない・直したか）を code-summary.md に表で書く。
- [ ] **Step 16**（NFR3）: 画面の検査を、変えたファイルについて流す。
  - `unit-test-instructions.md` の 2節の Vitest のコマンド（変えた画面のテストのファイル）。
  - `npm run typecheck`・`npm run lint`・`npm run format:check`・`npm run license:check`（`frontend/` で。どれも設定どおりの範囲）。
  - 通らなければ直してから次へ進む。

### 2.5 環境と台本（負荷の試験。T1・T4）

- [ ] **Step 17**（FR4.1）: `perf/k6/scenarios.js` に場面 `userAdminPoolLimit` を足す。
  - 各 VU は自分の対象（`perf-uat<VU>`）に、印を付ける → 外す → 止める → 解くの4つをくり返す（どれも 204 で状態が戻る。準備のログインの失敗と失敗回数を戻す操作は入れない）。
  - 実行の形は `constant-vus`（`VUS`・`DURATION` で決める。手順の既定は 7 節）。準備（`setupUserAdmin`）は今の `userAdminOps` と同じ利用者を使う。
  - この場面は接続の時間切れ（500）を起こす目的のため、`p(95)` と `checks` の閾値を置かない。状態コードごとの件数（204・409・500）を `checks` ではなく数え分けのタグで出す。
  - 既存の `userAdminOps`・`userAdminPool` は変えない（前の Intent の (A) の記録と比べられるように残す）。
  - 確かめ: `grafana/k6:2.3.0` の `k6 inspect --include-system-env-vars`（`-e SCENARIO=userAdminPoolLimit`）で読み込めて、場面の名前が出ること（project.md の学び 2026-09-25）。負荷はこの段では流さない。
- [ ] **Step 18**（FR4.3・FR7.1）: `perf/README.md` を直す。
  - 上限 10 の節（321〜337 行付近）の後に、上限を下げる場面の手順（`app.env` に `MASTERSMITH_DB_MAXIMUM_POOL_SIZE=4`、`SCENARIO=userAdminPoolLimit`・`VUS=12`・`DURATION=5m`、`caffeinate -i` で台本の全体を包む、外部エクスポートと手元の監視を有効にして警報を 30 秒ごとに読む、BUSY の L3・L4 の数え方）を書く。
  - 接続プールの段落（259 行付近）と手順（307・330 行付近）に、`hikaricp.connections.acquire` の値は `/actuator/metrics` の応答の `baseUnit`（`seconds`・`milliseconds`）を見て読むこと（外部エクスポートの有無で単位が変わる）を書く。
- [ ] **Step 19**（NFR3・NFR4）: 統合の前の全体の検査。
  - colima を動かし、README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡して `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を流す（対象DB のテストが SKIPPED にならないこと。project.md の学び 2026-09-25）。
  - 通ったときのバックエンドの全体とパッケージごとの値（変えたパッケージ）と、フロントエンドの全体の値を code-summary.md に記録する。
  - 落ちたときは、team.md の「不安定なテストと CI の失敗」の決まりに従う（手元で再現すれば直すまで進まない）。
- [ ] **Step 20**（NFR3・FR1・FR2）: E2E を手元で流す（画面に関わる変更のため、統合の前。team.md）。
  - `docker compose --profile mail up -d mailpit` の後に `./gradlew e2eTest`（全ファイル）。
  - 110・120 のフォーカスと矩形の確かめ、040・060 のフォーカスの確かめが通ることを確かめ、結果（件数・成否）を code-summary.md に記録する。json の報告の秘密の値の確かめ（報告の部品）が通ることも確かめる。
  - E2E の生成物（`frontend/test-results`）はこの段の中では消さない（project.md の学び 2026-10-03）。

### 2.6 文書と記録

- [ ] **Step 21**（記録）: `code-summary.md`・`traceability.json`・`source-manifest.json` を書く。
  - `code-summary.md` には、計画との差、S1 の確かめの結果、固定先の前後のハッシュ、FR1.4 の画面ごとの結果、カバレッジの実測、「依頼者に確かめたいこと」の節を置く（project.md の学び 2026-09-28）。
  - `source-manifest.json` には、この段で作った・変えた・消したアプリのファイル（サブモジュールの固定先を含む）をすべて並べる。
- [ ] **Step 22**（コミットと統合。生成の担当はコミットしない）: 生成の後に、オーケストレーターが次のコミットを依頼者に提案し、承認を得てから行う（project.md の Change Control）。メッセージは日本語。
  - C1: make-you-chic-ui の固定先を 3d9521a から e82b651 へ上げる（Step 2 だけ。専用のコミット）
  - C2: 確かめの表示を閉じた後のフォーカスを行の「操作」へ戻す（Step 10・11・14 のフォーカスの部分・15。FR1 の回帰のテストの E2E を同じコミットに含める）
  - C3: 行の「操作」のメニューを右端で欠けないようにする（Step 12・13・14 の矩形の部分）
  - C4: 送信中は言語の選択を押せなくする（Step 8・9）
  - C5: 一意の制約の違反の文をログに出さない（Step 4・5）
  - C6: 印を外した直後の 403 と監査、MailConfigurationIT の確かめの範囲（Step 6・7）
  - C7: 接続プールの上限の負荷の場面と手順（Step 17・18）
  - 段の承認の場は、最後のコードのコミットの後、`aidlc/` の下だけの記録のコミットを積む前に開く（project.md の学び 2026-10-03）。
  - 段の承認の後、`aidlc/` の下の未コミットの変更（監査ログを含む）をコミットしてから、`fix/261003-user-admin-followup` を `develop` へ **fast-forward** で統合し、ブランチを消す（team.md のサブモジュールの例外。C1 を専用のコミットとして残すため squash にしない）。`origin` への push は依頼者が行う。

## Testing Contract

```json
{
  "version": 1,
  "methodology": "test-after",
  "source": "team",
  "ordering": "テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。",
  "scope": "bugfix",
  "test_strategy": "minimal",
  "project_type": "brownfield",
  "applicable_notes": [
    {
      "layer": "org",
      "text": "We treat tests as a first-class deliverable in every Bolt. The specific\nmethodology (TDD, BDD, ATDD, or classic test-after) is affirmed at\npractices-discovery and recorded in `team.md` under this heading with explicit\n`Methodology` and `Ordering` fields; Code Generation resolves those fields\nindependently from coverage, tooling, and scope notes.\n\nWhen no posture has been affirmed, our default per scope is:\n- **Methodology**: test-after\n- **Ordering**: implement each applicable testable layer, then write and run\n  that layer's tests.\n- `mvp`, `enterprise`, `feature`, `infra`, `classic` add an 80% line-coverage\n  floor and CI execution before merge.\n- `bugfix`, `security-patch` add a targeted regression for the specific\n  bug/vulnerability and require the existing suite to remain green.\n- `express` uses the Minimal strategy: requirement-driven unit tests (one per\n  requirement, with a happy-path floor per component); existing tests remain\n  green.\n- `poc`, `refactor`, `workshop` add no extra new-test floor and require the\n  existing suite to remain green.\n\nThe active `Test Strategy` still applies in every scope and determines test\nvolume/types. Scope floors are additive; they never reduce or replace the\nselected strategy.\n\nBuild and Test verifies defined coverage floors and affirmed quality targets;\nthey may not be weakened to make a step pass.\n\nAffirm a stricter posture in `team.md` if the team commits to one."
    },
    {
      "layer": "team",
      "text": "- **Methodology**: test-after\n- **Ordering**: テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。\n- テストは各 Bolt の成果物の一部であり、テストのない機能は完成とみなさない。テスト量はワークフローの Test Strategy に従い、以下の下限はそれに追加される。\n- カバレッジの下限は、すべての Intent に共通で **行カバレッジ 80% 以上、分岐カバレッジ 70% 以上** とする。バックエンド・フロントエンドの両方に適用し、下回ったらビルドを失敗させる。道具はバックエンドが JaCoCo、フロントエンドが `@vitest/coverage-v8`（`thresholds` 設定）。\n- バックエンドでは、全体の合計に加えて、すべてのパッケージごとにも同じ下限（行 80%・分岐 70%）を当てる。ただし、既存のパッケージに単独で下限を下回るものがあれば、パッケージごとの下限は新しく作るパッケージだけに当てる（既存のパッケージは全体の合計で判定する）。どちらになるかは、パッケージごとの下限を入れる Bolt で既存のパッケージのカバレッジを実測して決め、結果を記録する。\n- 全体の合計で判定している既存のパッケージ（`backend/build.gradle.kts` の `packagesJudgedByTotal` の一覧。2026-09-29 の時点で 12 パッケージ。`user.domain`・`user.repository`・`user.service` は Intent 260925-user-management で一覧から外れ、含まない）に手を入れる Bolt では、テストを足してそのパッケージの下限（行 80%・分岐 70%）を満たし、一覧から外してパッケージごとの下限の対象に必ず戻す。「手を入れる」は、そのパッケージの本体のソース（`src/main`）の変更のすべて（説明文だけの直しを含む）を指し、テストだけの変更は含めない。手を入れる見込みのパッケージとそれに伴う作業は、Delivery Planning とコード生成の計画で、各パッケージの今の値を実測して見積もる。カバレッジは、その Bolt のテストを足した後に `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で実測し、値を記録する。一度外したパッケージは一覧に戻さず、一覧を増やす変更はしない。\n- カバレッジの計測から外すのは、アプリの起動クラス、設定値だけのクラス、自動生成コード、`vendor/` 配下に限る。除外を後から増やして実質的に下限を下げることはしない。パッケージごとの下限を満たすために除外を増やすこともしない。\n- DB を使うテストは、本番と同じ種類の DB をコンテナで起動して使う（Testcontainers）。そのため、ローカルと CI にコンテナの実行環境があることを前提条件として文書化する。テストごとにデータを用意して巻き戻し、実行順に依存させない。表を作る・消す操作（DDL）がその場で確定して巻き戻せない DB（MySQL・MariaDB など）で表を作るテストは、巻き戻す代わりに、テスト（またはテストのクラス）ごとに名前の重ならないスキーマ（MySQL・MariaDB ではデータベース）を作り、終わったら消す。\n- 対象DB（MySQL・MariaDB・PostgreSQL）の結合テストは、3種類すべてを `./gradlew verify` の中で毎回実行する（CI も同じ）。Intent `260923-dsl-schema-loader` の Build and Test で `verify` の時間と colima の VM のメモリを実測したうえで、この形に決めた。コンテナの実行環境が無いときの扱いは Way of Working のとおり。\n- 外へ送るメール（SMTP）を使う機能のテストは、送信の部品をモックで置き換えて済ませず、テストの中で JVM の中に起動するテスト用の SMTP の受け手で実際に受け、宛先・件名・本文（HTML）・言語を確かめる。受け手にコンテナは使わない（コンテナの実行環境が無いときに飛ばしてよいテストは、対象DB のテストだけのままとする）。送信の失敗（受け手が接続を拒む・応答しない）のテストも必ず入れる。実在の宛先へは送らない。受け手の具体の道具は、採用の前のライセンスの確認とあわせて設計の段で決める。\n- 画面からの一連の操作を確かめるテスト（E2E）は Playwright（`./gradlew e2eTest`）で書き、`./gradlew verify` と CI の外に置く。本数は代表的な流れに絞り、機能の Intent ごとに代表の流れを1本まで足す。新しく足す流れは、前のテストが作った状態に頼らず、利用者などの前提を自分で作る。利用者の状態を変える操作（利用停止・管理者の印の変更・ロックなど）は、その流れで自分で作った利用者だけを対象にし、初期管理者の状態は変えない（E2E は1つのアプリ・内部DB・初期管理者を全ファイルで共有して順に流すため）。E2E は、画面・認証に関わる変更を統合する前と、リリースの前に手元で実行する。\n- 入力と出力の性質をランダムな入力で確かめるテスト（性質ベースのテスト）を、純粋な関数（ロック判定の回数計算、有効期限の判定、入力の検証など）に一部適用する。道具は Java が jqwik、フロントエンドが fast-check。失敗時の乱数の種を記録して再現できるようにする。\n- テストの説明文（テスト名、`describe` / `it`、`@DisplayName`）は英語で書く。テストデータは日本語でよい。\n- リポジトリは公開のため、テストデータのメールアドレスは予約のドメイン（`example.com` など）だけにし、実在しそうな氏名・宛先を置かない。\n- フロントエンドのテストは対象と同じ場所に `*.test.ts` / `*.test.tsx` として置き、Vitest ＋ Testing Library（jsdom）＋ user-event ＋ vitest-axe を使う。画面部品ごとにアクセシビリティ検査を1件入れる。\n- 画面のテストで、描画の後（`useEffect` などの効果）に反映される値は、操作の直後に同期で確かめず、`waitFor` で待って確かめる。テストの時間の上限（Vitest の既定 5 秒など）は、原因を確かめずに延ばさない。\n- Java のテストは `src/test/java` に、対象と同じパッケージ構成で置く。単体テストは `XxxTest`、Spring や DB を起動する結合テストは `XxxIT` とし、分けて実行できるようにする。\n- 時刻に依存する処理（有効期限、ロックの解除など）は注入可能な時計（`Clock` 等）から現在時刻を取得し、テストで `sleep` や実時刻に依存しない。\n- 不安定なテストと CI の失敗は、次の1つの決まりで扱う。\n  - 手元で再現した不安定なテストは、原因を直すまで統合しない（次へ進まない）。\n  - 手元で再現しないものは、再現の試みに先に時間の上限を決め、見立てと試みの範囲を記録したうえで、CI の再実行で通れば進めてよい（不安定と確かめられていない扱い）。\n  - 同じテストが二度目に落ちたら、原因を直すまで次へ進まない。\n  - 失敗を直さずに次の Intent へ持ち越すのは、依頼者の決定があるときだけとし、決まりとの差を記録する。\n- 認証・認可・監査に関わる機能では、次のテストを必ず書く。★印の項目は要件（しきい値や動作）が未確定のため、要件定義で決めてからテストを書く。\n  - アカウントロック: 失敗回数のしきい値の境界（しきい値−1回ではロックされない／しきい値ちょうどでロックされる）、ロック中は正しいパスワードでも拒否、ログイン成功時の失敗回数の扱い。★しきい値、回数を数える期間、ロックの解除方法\n  - ログイン: 存在しないユーザーとパスワード誤りで、応答からユーザーIDの存在を推測できないこと\n  - トークン: 有効期限の境界（直前は有効／直後は無効）、署名の改ざん、署名方式の指定を悪用した改ざん（`alg: none` 等）、ログアウト後のリフレッシュトークンの拒否、ログアウト後もアクセストークンが有効期限まで使えること（決定済みの仕様として明示する）。★各トークンの有効期限の値、リフレッシュトークンを使うたびに作り直すか\n  - 初期管理者の自動作成: 2回目以降の起動で重複作成しない、設定が無い／不正なときの動作、パスワードがログに出ない。★設定が無いときに起動を止めるか\n  - 認可: 未認証（401）、管理者フラグなし（403）、管理者（200）をサーバー側のテストで確かめる（画面で管理メニューを隠すことはサーバー側の検査の代わりにしない）\n  - 監査ログ: 対象イベントごとに必須項目が記録されること、存在しないユーザーIDでのログイン失敗も記録されること。★監査ログの書き込みに失敗したときに操作を失敗させるか\n  - 秘密情報の漏えい: ログ・監査ログの出力にパスワード・トークンの値が含まれないこと\n  - 構造化ログ・分散トレース: 決めた形式で出る、トレースIDがログに含まれる、外部エクスポートが既定で無効であること\n  - 画面: ログイン画面のアクセシビリティ検査、ロック時のメッセージ表示、ログアウトでトークンが破棄されること\n  - 招待と登録の完了: 招待の有効期限の境界（直前は有効／直後は無効）、使い終えた招待・取り消した招待の再使用の拒否、改ざんした招待・存在しない招待の拒否（応答から利用者の存在を推測できないこと）、招待中（登録が終わっていない）の利用者はログイン・トークンの更新・アクセストークンの認証のどれでも拒否されること。★招待の有効期限の値、再送・取り消しの扱い\n  - パスワードの変更: 今のパスワードの確かめ、パスワードの規則の境界、変更の後のリフレッシュトークンの扱い（決めた側の動作を明示したテストにする）。★パスワードの規則、変更の後にほかの端末のリフレッシュトークンを無効にするか\n  - 利用停止: 停止中の利用者は、ログインの照合・トークンの更新・アクセストークンの認証の3つの入口のすべてで拒否されることを、入口ごとにサーバー側のテストで確かめる。停止を解いた直後は3つの入口のすべてで受け付けること。停止の前に出したリフレッシュトークン・アクセストークンの扱いは、決めた側の動作を明示したテストにする。★停止を応答から推測できてよいか、停止の前に出したトークンの扱い\n  - 管理者の印の変更: 印を付けた直後・外した直後の次の要求で、管理の API の 403／200 がサーバー側で切り替わること（画面が持つ古い印に頼らない）。印を外す前に出したトークンの扱いと、自分の印を外す操作の扱いは、決めた側の動作を明示したテストにする。★印を外す前に出したトークンの扱い、自分の印を外す操作の扱い\n  - ロックの解除: 解除の直後に正しいパスワードで入れること、解除の後の失敗回数の境界（しきい値−1回ではロックされない／しきい値ちょうどでロックされる）、ロックの状態の行が無い利用者の解除。時刻は注入した時計で動かし、実時刻と `sleep` に頼らない。★解除の後の失敗回数の扱い、ロックの状態の行が無いときの応答\n  - 最後の管理者の保護: 最後の有効な管理者の印を外す・利用を止める操作は拒否され、拒否の後に状態が変わっていないこと。2人の管理者が同時に互いの印を外す・利用を止めても、有効な管理者が 0 人にならないこと。同時の重なりはスレッドの数に頼らず、待ち合わせで確実に作る。★最後の管理者の数え方（止めた管理者を数えるか）\n  - 管理の API の認可: 足す管理の API のすべてについて、未認証（401）・管理者フラグなし（403）・管理者（200）をサーバー側のテストで確かめる。停止中の管理者は管理の API を呼べないこと。\n  - 要求の改ざん: 管理の API の外（`/api/me` など）から、要求の本文の値を変えて自分の管理者の印や利用者の状態を変えられないこと（一括代入の防止）。\n  - 管理の操作の監査: 利用者の権限・状態を変える操作（印の付け外し・利用停止と再開・ロックの解除）ごとに、操作した人・対象の利用者・結果が記録されること。★拒否した操作（403・最後の管理者の拒否など）を記録するか\n  - 利用者の管理の漏えい: 一覧・詳細の応答と監査の行に、パスワードのハッシュ値・リフレッシュトークン・ロックの判定の内部の値が含まれないこと。TRACE のログを有効にして一覧・詳細を読んでも、メールアドレス・氏名がアプリのログに出ないこと（既存の `*SecretLeakIT` と同じ形で確かめる）。\n- 利用者へメールを送る機能では、Code Style と `project.md` のメールの決まりを確かめる次のテストを必ず書く（認証・認可・監査と同じ扱い）。★印の項目は要件が未確定のため、要件定義で決めてからテストを書く。\n  - 本文のエスケープ: 差し込む利用者の値（氏名・メールアドレスなど）に `<`・`>`・`&`・`\"`・`'` を含めても、タグや属性としてそのまま出ないこと。利用者の値をエスケープしない差し込み（`{{{ }}}`・`{{& }}`）で入れていないこと、属性の値を二重引用符で囲んでいること（エンジンの `{{ }}` は `'` を置き換えないため）\n  - テンプレートの描画: 日本語と英語のすべてのテンプレートを描けて、件名が空でないこと、差し込み漏れ（`{{` の残り）が無いこと、ライセンスヘッダーが描いた本文に出ないこと\n  - 招待の URL: 設定したベース URL だけから組み立て、要求の Host ヘッダーから組み立てないこと。ベース URL の設定が無いときに要求の Host を変えても、招待の URL に出ないこと。★ベース URL の設定が無いときの動作\n  - トークンと URL の漏えい: 招待のトークンと招待の URL が、アプリのログ・監査ログ・トレースの属性・エラー応答に含まれないこと（既存の `*SecretLeakIT` と同じ形で確かめる）\n  - ヘッダーへの差し込み: 宛先・件名・差し込む値に改行（CR・LF）を含めても、メールのヘッダーが増えず拒否されること\n  - 送信の失敗: 送信が失敗したとき（受け手が接続を拒む・応答しない）に、宛先のメールアドレス・SMTP の応答・資格情報が応答とログに含まれないこと。★送信が失敗したときの業務の動作（招待を失敗させるか、再送に回すか）\n- 利用者が投入する DSL（YAML）を読み込む機能では、Code Style の「信頼できない入力」の決まりを確かめる次のテストを必ず書く（認証・認可・監査と同じ扱い）。上限の具体的な数値は設計の段で決め、決めた値の境界で確かめる。\n  - 大きさ: 上限ちょうどは受け付け、上限を超えると拒否される\n  - 入れ子の深さ: 上限ちょうどは受け付け、上限を超えると拒否される\n  - 別名（アンカー）: 展開の数が上限を超えると拒否され、別名の展開の爆発で処理が止まらない\n  - タグ: 任意の型を作るタグ（`!!` など）を含む DSL は拒否され、型が作られない\n  - 重複キー: 同じキーが重なる DSL はエラーになり、後の値で黙って上書きされない\n  - JSON Schema の `$ref`: 外部の URL を取りに行かない\n  - 拒否の応答: Problem Details の形で返り、YAML・JSON Schema の部品の例外のメッセージを含まない\n\n- 内部DB（組み込みの H2）を使うテストは、コンテナではなく本番と同じ組み込みの H2 で行う。Testcontainers は、コンテナで動かす対象DB（後続 Intent D・E で扱う業務DB）のテストに使う。Walking Skeleton の「DB を使うテスト1件以上（本番と同じ種類の DB をコンテナで起動する）」も、内部DBについてはこの読み方とする。 (learned 2026-09-22)"
    },
    {
      "layer": "project",
      "text": "- テストの件数やカバレッジを報告するときは、`./gradlew verify` がテストのタスクを UP-TO-DATE で飛ばすことがあるため、`:backend:cleanTest :backend:cleanIntegrationTest` を付けて実行し直し、実測の数字だけを報告する。 (learned 2026-09-23) \n- 負荷の環境や配備先が決まらないと測れない目標（応答時間のパーセンタイル、運用の指標、ファイルの権限など）は、Build and Test で `Unverified` とし、持ち主の段（performance-validation・observability-setup・deployment-execution）を明記して引き継ぐ。目標を緩めて「満たした」ことにはしない。 (learned 2026-09-23) \n- 負荷の試験は、配備した環境とは別の使い捨ての環境（仮の署名鍵・仮の利用者、終わったら消す）で行い、本物のデータと監査ログを汚さない。手順は perf/README.md。 (learned 2026-09-23) \n- 負荷の試験で、アプリが止まる・極端に遅いなどの結果が出たときは、環境を起動し直して再現させ、原因をログと状態（OOMKilled など）で確かめてから記録する。 (learned 2026-09-23) \n- 同時の重なりを確実に作るため、本番のコードを変えずに、監査の書き込みの時間を測る LongSupplier（AuditEventListener で2本目を借りる直前に呼ばれる）をテストで差し替えて待ち合わせる方式にした。既存の LoginConcurrencyIT は 8 スレッドでプールの 10 に届かず、前回の失敗のログインで尽きなかった理由の1つと見られる。 (learned 2026-09-23) \n- Intent の流れに Performance Validation の段が無く、負荷の環境（使い捨ての環境）を手元で用意できるときは、k6 の試験と NMT の測定の持ち主を Build and Test とし、Unverified で引き継がずにその段で実行する。 (learned 2026-09-23) \n- 修正の前の設定（例: 上限 1g）も修正の後の環境（例: CPU 4 の VM）で流し（pre1g）、要件の前提（VM を上げても F3 が起きる）を実測で裏付ける。 (learned 2026-09-23) \n- パッケージごとのカバレッジの下限と SpotBugs の SQL_ の関門を U1 の計画に入れた。team.md の決まりだが今のビルドに無く、この Intent で最初に作る単位のため。U1 の設計の文書には無い作業。 (learned 2026-09-24) \n- U4 で既存の AuditSecretLeakIT の列の一覧に V6 の4列を足し、テストの JVM のヒープを 1g にした。前者は承認済みの V6 と必ず食い違うため、後者は構造の検査がクラスを持ち続け U3 の 10MB 超えのテストでヒープが尽きたため。どちらも計画に無い変更で、依頼者に確かめる。 (learned 2026-09-24) \n- 応答しない対象DB の TIMEOUT の確かめに、テストの中で開いた ServerSocket（受け付けて何も返さない）を使う。外の端末に頼らず確実に再現できる代わりに、本物の DB の遅延ではない。 (learned 2026-09-24) \n- パッケージごとのカバレッジの下限は新しいパッケージだけに当てた。実測で audit.service（行 77.2%）・common.health（行 79.2%）・auth.repository（分岐 50.0%）が単独で下回ったため（team.md の決まりどおり）。既存の 22 パッケージを一覧で外し、新しいパッケージは自動で対象になる。 (learned 2026-09-24) \n- 負荷の試験で接続プールが尽きたかを確かめるときは、使い捨てのアプリにだけ MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics を渡して /actuator/metrics の hikaricp の値を読み、数秒ごとの使用中の数ではなく、待ちの時間切れの累計と借りるまでの待ちの最大で判断する（配備したアプリの公開の範囲は変えない）。 (learned 2026-09-24) \n- k6 などの長い試験は caffeinate -i を付けて流し、PC の自動のスリープで要求が止まって結果が崩れるのを防ぐ（バッテリー駆動のまま 414 秒スリープし、要求が 6分52秒止まった）。内部DB に SQL で直接入れた試験用の利用者はロックの状態の行が無いため、同時のログインを流す前に1人ずつログインさせて行を作る。 (learned 2026-09-24) \n- 軽い API の性能は、投入を重ねて内部DB のファイルが膨らんだ状態（悪い側の条件）のまま測る。メモリの最大（memory.peak）を比べる試験の前は、アプリのコンテナを作り直して前の最大の値を消す。 (learned 2026-09-24) \n- Performance Validation の段が無いため、2件目の直しの負荷の試験での確かめ（Q5: A）の持ち主を Build and Test とした（project.md の学びどおり）。 (learned 2026-09-24) \n- colima の PC で ./gradlew verify を流すときは、README の DOCKER_HOST と TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE をシェルに渡さないと対象DB のテストが SKIPPED になり、パッケージごとのカバレッジの下限で失敗した。付けて流し直した結果を基準とした。 (learned 2026-09-25) \n- unit-test-instructions.md の k6 inspect のコマンドは --include-system-env-vars が無いと場面の名前が undefined になり確かめにならなかった。承認済みの文書は変えず、実際には付けて流し、code-summary.md に差を記録した。 (learned 2026-09-25) \n- 1回目の verify で AccessTokenApiIT の 6 件が接続の失敗で落ちた。変更の経路に触れず、同じ時刻に Gradle の作業プロセスとの接続も時間切れだったため PC の負荷による一時的な失敗と見立て、段の中の直しの1回目としてコードを変えずに verify を流し直して通した。原因は確かめていない。 (learned 2026-09-25) \n- 依頼者の判断で、試験のイメージを mastersmith:local ではなく mastersmith:followup-fixes で作り、perf/README の手順（local を作り直す）から外れた。配備したアプリは k6 のあいだ止め、約 17 分後に同じコンテナで起動し直した。 (learned 2026-09-25) \n- 試験の後にロックの状態の行の数を数える問い合わせの列の名前を誤り、使い捨ての環境を消した後で取り直せなかった。合格の条件（ログインの checks と 500 の件数）で判定した。消す前に確かめの結果を見てから片付けるべきだった。 (learned 2026-09-25) \n- Q4（確かめられたときだけ直す）が team.md の「不安定なテストは原因を直すまで統合しない」と食い違いうるため、追加の質問 F3 で、再現できなければ不安定と確かめられていない扱いとして統合してよいことを確かめた。 (learned 2026-09-25) \n- 要件 FR2.2（目標は計画の承認の場で決める）を満たすため、依頼者の決定（Q1: A）で、計画を書く前に dslMixed でメモリの内訳を測ることにした。測る間は配備したアプリを止める。 (learned 2026-09-25) \n- 生成で、テストの既定で MBean の登録を無効にする置き場を、計画の候補の TestDatabase ではなくテストの EnvironmentPostProcessor にした。TestDatabase を使わずに Spring を起動するテストのクラスが 15 あり、漏れるため。再現の結合テストは 12 回では伸びが小さく判定がはっきりしないため、24 回・履歴の上限 3 にした。 (learned 2026-09-25) \n- AccessTokenApiIT は、同じ例外の文言を仕組み（colima の IPv4 の転送と Java の IPv6 の待ち受けの番号の重なり）ごと再現できたことを原因の確認とみなし、依頼者の決定（D7）でテストの JVM に preferIPv4Stack を付けて直した。実際の1回目がこれで起きたかは確かめられず、今回の繰り返しでは重なりは起きなかった。 (learned 2026-09-25) \n- Test Strategy は Minimal だが、要件 FR5 と計画の「Build and Test に引き継ぐこと」のため、結合・性能・セキュリティの手順書も作り、この段で dslMixed・--storage --compact（40 回）・詰め直しの最中のログイン・refresh・dslCycle を流した。 (learned 2026-09-25) \n- 計画の dslCycle を最初の台本に入れ忘れ、配備したアプリをもう一度（約 4 分）止めて流した。負荷の試験の台本を書く前に、計画の「Build and Test に引き継ぐこと」の項目を一つずつ台本の手順と突き合わせる。 (learned 2026-09-25) \n- 実際のブラウザのアクセシビリティの検査（axe）は、誤りを出した状態と、現実に近いデータ（2語の氏名など）で、ブランドカラーとテーマのすべての組について行う。user-management の U7 の 080 で初めて、make-you-chic-ui の Avatar（頭文字が2文字のとき）と dark の FormField の誤りの文字のコントラスト不足が出た（050〜070 は誤りの状態を見ず、氏名が1語だったため出なかった）。 (learned 2026-09-28) \n- Playwright の webServer.env に置いた値は json の報告に残る。E2E の仮の資格情報はプロセスの環境変数で渡し、報告の部品の確かめと json の文字列の検索で、パスワード・トークン・メールアドレスが入っていないことを確かめる（user-management の U4 で見つかった）。 (learned 2026-09-28) \n- CI（66fe981）の verify が2回とも別々のテストの時間切れ（H2CompactionByPoolSuspensionIT の接続の待ち 10 秒、InvitationAdminPage.test.tsx の既定 5 秒）で失敗し、U4〜U7 の NFR7.1 が Not Met だったが、依頼者の決定「次のintentでコントラストと一緒に直す。このintentではこのまま進める。」で受け入れた失敗とした。team.md の「CI が失敗したら次に進む前に直す」と食い違うため test-results.md 8.1 に差を記録した。 (learned 2026-09-28) \n- k6 の登録の完了と取り消しの場面は、決定の文言（招待を VU の数だけ）ではなく既定 100 回・招待 100 件とした。トークンは1回しか使えず 10 件では p95 の意味が薄いため、U3 の NFR6.4 の「流す回数以上の招待を用意する」に合わせた。承認の場で確かめる。 (learned 2026-09-28) \n- caffeinate -i は場面ごとではなく、試験の台本全体を包む形で付ける。場面ごとに起こし直すと切れ目で守りが外れ、PC が眠る（user-management の Performance Validation で 92 秒眠り、要求が 1 分 29 秒遅れた）。遅れが出たときは pmset -g log でスリープを確かめる。 (learned 2026-09-28) \n- 応答しないメールの受け手は、Mailpit を docker pause で一時停止して作る（接続は受け付け、何も返さない）。コンテナを止めると接続の失敗になり、時間切れの確かめにならない（user-management の Performance Validation）。 (learned 2026-09-28) \n- Q1 で原因を確かめずに上限を延ばす答え（C）が team.md の「不安定なテストは原因を直すまで統合しない」と食い違うため追加の質問 F1 で確かめ、延ばしたうえで失敗時の診断を足し、差を記録する形（B）になった。Q3 の「すべて試す」は verify で判定できない Temurin 26・logback-appender の扱いを F2 で確かめ、見送り（C）になった。 (learned 2026-09-29) \n- 診断の確かめで、上限 1 ミリ秒は Awaitility の問い合わせの間隔（100 ミリ秒）より短く設定の誤りで弾かれ、150 ミリ秒では手元で接続がすぐ 0 本になり時間切れにならなかった。上限 500 ミリ秒と、待ちの条件を一時的に満たせない形（== -1）にして時間切れを起こした。確かめた後に git checkout で戻した。 (learned 2026-09-29) \n- 警報が鳴ることは、しきい値を 50 ms に下げた警報の決まりの写しを使い捨ての環境に読み込ませて確かめた（Q1: A）。本当に遅い応答を作るより早く PC の負荷も小さいが、しきい値の値そのもので鳴ることは確かめていない。途中で ms-check-p95 のしきい値が 1000 ms ではなく 300 ms（バケットの境界に無い）と分かり、要件の前提 A3 と食い違った。 (learned 2026-09-29) \n- 表示の設定を画面の操作の直後に同期で確かめるテストが、負荷の高い CI で2件（ShellLayout・PreferencesPage）落ちた。描画の後の効果（useEffect）で反映される値は waitFor で待って確かめる。同じ形の RegistrationPage も合わせて直した。 (learned 2026-09-29) \n- Delivery Planning でのカバレッジの実測は、./gradlew verify 全体ではなく :backend:cleanTest :backend:cleanIntegrationTest :backend:test :backend:integrationTest :backend:jacocoTestReport で行い、jacocoTestReport.xml から手を入れる見込みのパッケージの値を読む（user-admin で約 5 分）。 (learned 2026-10-01) \n- 接続プールの見積もりを確かめる負荷の試験には、上限に届く形（プールの上限を下げた場面など）を含める。上限に届かない負荷では、見積もりが誤っていても合格する（user-admin の U3 の NFR 要件のレビュー R-02）。 (learned 2026-10-01) \n- 画面のはみ出しの確かめ（E2E・axe）では、画面全体の横のスクロールだけでなく、開いたメニュー・ポップアップなど画面に固定で置く部品が画面の中に収まることも確かめる。表の右端に置く make-you-chic-ui の Dropdown は placement に bottom-end を指定する（user-admin の配備の後のスモークテストで、行の「操作」のメニューが右へはみ出していたのを E2E 120 が拾えなかった）。 (learned 2026-10-03)"
    }
  ],
  "obligations": {
    "strategy": "minimal",
    "strategy_volume": [
      "One verifiable test per requirement at the narrowest effective level.",
      "At least one happy-path unit test per component.",
      "Unit tests are the default; a bugfix/security scope floor may require an integration or E2E regression when that is the narrowest level that reproduces the defect."
    ],
    "scope_floor": [
      "Include a targeted regression for the bug or vulnerability.",
      "Keep the existing test suite green."
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
  "input_sha256": "sha256:a12ea66b215791cc328cf03b9a768a556a6ec3472606c7da7bbbd52fe4b2d6d5",
  "contract_sha256": "sha256:370d724f2c76797a5b2d15ff33d3cde8c48605cdd0a44fed5736f15348a3f02b"
}
```

## 4. 依頼者に確かめたいこと（計画の承認の場）

### D1 S1 の直し方（FR8.2、Q1: D）

確かめの結果（6節）では、重なったメールアドレスが既定の INFO のアプリのログに出ます。直し方を選んでください。

- **案 A**: ログの設定で `org.hibernate.orm.jdbc.error` を `OFF` にする（`application.yaml`。アプリのコードは変えない）。確かめた漏えいの経路を止める。失うのは Hibernate の `HHH000247: ErrorCode: 23505, SQLState: 23505` の行（調べ方はコメントに書く）。
- **案 B**: 違反を受ける所で、例外の文を出さずにクラスの名前だけを出す。**ただし、確かめた漏えいは Hibernate がアプリへ例外を渡す前に自分で出すログのため、B だけでは止まらない。** B が止めるのは、想定外の制約の違反や招待の2回目の失敗（`IllegalStateException` に元の例外を付けて投げる）が `GlobalExceptionHandler` の ERROR に原因の連なりごと出る経路（コードで読んだ見立て。計画の段では流していない）。
- **案 C**: A と B の両方。
- **推奨: 案 C**。A は確かめた漏えいを止めるのに必要で、B だけでは止まらないためです。B は、行の排他の失敗と同じ「受けた所でクラスの名前だけ」の扱い（project.md の学び 2026-10-01）を一意の違反に広げるもので、起こりにくい経路の守りになります。変えるのは `common.error.web`（パッケージごとの下限の対象）で、`packagesJudgedByTotal` には当たりません。手数を小さくしたいときは案 A でも、確かめた漏えいは止まります。

### D2 T1 の合否の基準と負荷の形（FR4.2、R-01）

7節の「Build and Test に引き継ぐこと」の値（上限 4・同時 12・5 分、(a) の基準「時間切れの累計 1 以上、または待ちの最大 1,000 ms 以上」、(c) の合格の条件、鳴らなかったときの扱い）でよいかを確かめてください。

### D3 FR1 の再現の確かめ（NFR5）

FR1 の回帰のテストは E2E 110・120 のフォーカスの確かめです（jsdom では `inert` でフォーカスが止まらず、画面部品のテストでは再現しないため）。固定先を 3d9521a に戻して作り直し、新しい確かめが落ちることを1回流して見ることもできますが、サブモジュールの戻しと WAR の作り直しが要ります。計画では行わず、前の Intent の配備の後のスモークテストと make-you-chic-ui の対応の報告（e82b651 の `Modal.test.tsx`）を根拠にします。行うかを選んでください。

### D4 コミットの分け方（Mandated の「同じコミット」と専用のコミット）

固定先の更新は専用のコミット（C1）にする決まりのため、FR1 の直しの本体（固定先を上げること）と再現のテスト（E2E のフォーカスの確かめ）は別のコミットになります。計画では、回帰のテストを画面の側の直し（`finalFocusRef`）と同じ C2 に入れ、C1 の直後に置きます。この扱いでよいかを確かめてください。

### 依頼者の決定（2026-10-04、計画の承認の前）

- D1: C（案 C。`org.hibernate.orm.jdbc.error` を `OFF` にし、違反を受ける所でもクラスの名前だけを出す）
- D2: A（7節の値のとおり）
- D3: A（FR1 の再現の確かめは行わない）
- D4: A（C1 は固定先の更新だけ、FR1 の回帰のテストは C2）

## 5. 要件との差／計画で決めたこと

- **R-01（FR4.2 の測れる基準）**: 7節に、上限の下げ先・負荷の形・(a) の数値の基準・(b) の3件が鳴る筋道・(c) の合格の条件・鳴らなかったときの扱いを書いた。台本は既存の `userAdminOps` を変えず、新しい場面 `userAdminPoolLimit` を足す（前の (A) の記録と比べられるように残すため）。
- **R-02（配備の完了の基準）**: Deployment Pipeline の段で扱う（起動確認・スモークテストで K1 のフォーカスの戻りと K2 の右端のメニューを配備したアプリで確かめる・戻し方）。この計画では扱わない。
- **R-03（FR1.4）**: 対象を `frontend/src` で `<Modal` を使う画面のすべてと確定した（招待の `InviteDialog`・`CancelConfirmDialog`、DSL の `DslConfirmDialog`。ほかに無い）。確かめは E2E（040・060 への確かめの追加）で行い、結果は code-summary.md に表で記録する。戻らない画面を直したときは、その E2E の確かめを回帰のテストとし、NFR5 の対象に含める（Step 15）。新しい流れの E2E は足さない（team.md の「Intent ごとに代表の流れを1本まで」に数えない、確かめの追加）。
- **R-04（FR8.1 の経路）**: 確かめる経路を次の2つに絞る。
  - `uk_users_email`: 同時の利用者の作成（`UserAccountService.createUser`。登録の完了もこの口を使う）
  - `uk_invitations_pending_email`: 同時の招待（`InvitationService.issueWithOneRetry`）
  - ほかの一意の制約は、利用者が重なった値を作れないため対象にしない: `uk_refresh_tokens_token_hash`（乱数のトークンのハッシュ）・`uk_invitations_token_hash`（同じ）・`uk_dsl_previews_preview_id`・`uk_dsl_applied_revisions_revision_id`（アプリが作る ID）。ログインのロックの状態の行は `MERGE` で作り、違反は DEBUG で `userId` だけを出す（スキャンの事実）。
  - 生成の途中でほかの経路が見つかったときは、計画に足す前にオーケストレーターに知らせ、依頼者の承認を得る。
  - トレースの属性: 既存の `SanitizingSpanExporter` が外へ送る前に `exception.message`・`exception.stacktrace` を除き、`SanitizingSpanExporterTest` で確かめ済み。Hibernate の SQL の実行はスパンを作らない。新しいテストは足さず、この扱いを code-summary.md に書く（生成で、2つの経路のスパンに値が載る処理が無いことをコードで確かめ直す）。
  - エラー応答: 2つの経路は業務の結果の型（`EmailAlreadyUsed`・招待中の勝った側）で受け、例外の文は応答に届かない。想定外の誤りの応答は既存の決まり（`include-stacktrace: never`、`GlobalExceptionHandler` が 500 `INTERNAL_ERROR` で文を載せない）で、案 B・C ではそのテストに一意の違反の場合を足す。
- **R-05（条件つきだった AC の行き先）**: 前の Intent の AC2.1.8（印の付け外し）・AC3.1.7（止める・解く）・AC4.1.9（失敗回数を戻す）・AC5.1.7（氏名・言語の入力）と U5-NFR7.1 は、Step 14 の E2E 110・120 のフォーカスの確かめが通ることで Met とする。AC ごとの確かめの場所は 8節の表に書く。判定の記録は Build and Test が行う。
- **R-06**: FR6.2 の「単独で数回」は **5 回** とした。FR1.2 の「操作の失敗」は、確かめの表示では閉じて行の「操作」へ戻り、氏名・言語の入力では閉じずに表示に残る（今のコードの動き。Step 10）。
- **R-07（出典の印）**: 要件の文書の直しで、この段では扱わない（承認済みの文書は書き換えない）。
- **FR1.2 の ref の作り方**: 要件の「`actionRefs` から作った、描画ごとに作り直さない ref」を、`useRef` を1つ持ち、表示を開くときに `.current` を入れる形にした。
- **FR3.1 の押せなくし方**: 送信中は `disabled` にする（`onChange` を無視する形ではない。要件の「氏名の欄と同じ扱い」と、部品に口があることから）。言語の欄で Enter を押して送信したときは、`disabled` でフォーカスが欄から外れうる（確かめの表示の「やめる」の `disabled` と同じ扱い）。生成で実際の動きを画面部品のテストで確かめ、外れるときは code-summary.md の「依頼者に確かめたいこと」に書く。
- **FR2.1 の確かめの段**: jsdom は矩形を持たないため、回帰のテストは E2E 120 の矩形の確かめとし、画面部品のテストは足さない（Minimal の「最も狭く効く段」）。
- **FR6.1 の絞り方**: スレッドの名前と、起動の前の出力の長さの2つで絞る。共通の `JsonLogRecords` は変えない。

## 6. S1 の捨ての確かめの結果（計画の段、2026-10-04）

- 方法: `user/service` に捨ての結合テストを置き、`UserCreationIT` と同じ待ち合わせ（パスワードのハッシュの計算で `CyclicBarrier`）で、同じメールアドレス（`example.com` の UUID 入りの値）の利用者の作成を2つ重ねた。既定の INFO と、`cherry.mastersmith` を TRACE にした場合の2回を、1つの Spring の文脈で流し、捕まえた出力からメールアドレスを含む行のロガー・水準・項目の名前だけを書き出した（値は書き出していない）。流したのは `./gradlew :backend:integrationTest --tests '<捨てのテスト>'` だけ。終わった後にテストのファイルとビルドの出力（クラス・結果の XML・報告）を消し、`git status` に残りが無いことを確かめた。
- 結果（事実）:
  - 2回とも、作成の結果は `Created` と `EmailAlreadyUsed` が1つずつ（業務の扱いは期待どおり）。
  - **既定の INFO で、ロガー `org.hibernate.orm.jdbc.error` が WARN を2行出し、そのうち1行の `message` に重なったメールアドレスが入る**（`ユニークインデックス、またはプライマリキー違反: "PUBLIC.UK_USERS_EMAIL INDEX … ON PUBLIC.USERS(EMAIL NULLS FIRST) VALUES ( /* n */ '<メールアドレス>' )"`）。もう1行は `HHH000247: ErrorCode: 23505, SQLState: 23505` で値は無い。アプリが違反を受けて結果の型にする前に、Hibernate が自分で出す。
  - TRACE を有効にした場合も、メールアドレスを含む行は同じ Hibernate の1行だけ。追跡は働いていた（`ENTER` の行 13）が、`saveAndFlush` の追跡の行は無く（Spring Data の継承したメソッドは追跡の対象に当たらない）、`createUser` の中で例外を受けるため `EXCEPTION` の行も無い。`createUser` の引数の行にメールアドレスは出なかった（`NewUser` の伏せ字）。
  - 監査（`audit_events`）: 作成の口は監査の行を書かず、行は 0 件（メールアドレスを含む行も 0）。
  - 確かめていないこと: 招待の経路（`uk_invitations_pending_email`）は流していない。同じ Hibernate のロガーが同じ形で出す見込み（見立て）。外部エクスポートを有効にした場合は、この WARN がログとして外へ送られる見込み（見立て）。案 B の対象の `GlobalExceptionHandler` の経路は流していない。
- 判断: `project.md` の Forbidden（メールアドレスをアプリのログに含めない）に当たる。FR8.2 のとおり、この Intent で直す（4節の D1）。

## 7. Build and Test に引き継ぐこと

### 7.1 T1（FR4.2）の負荷の試験

- **持ち主**: Build and Test（この Intent の流れに Performance Validation の段が無く、使い捨ての環境を手元で用意できるため。project.md の学び）。
- **環境**: `perf/README.md` の使い捨ての環境（仮の署名鍵・仮の利用者。終わったら消す）。外部エクスポートを有効にし、手元の監視（`grafana/otel-lgtm`）を起動して、本物の警報の決まり（`docker/monitoring/provisioning/alerting/mastersmith.yaml`）のまま読み込ませる。`/actuator/metrics` は使い捨てのアプリにだけ `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics` で公開する。
- **配備したアプリ**: colima の VM（CPU 4・6GiB）で、使い捨ての環境と配備したアプリを同時に動かせるかを、始める前にメモリの上限で確かめる。止める必要があるときは、止める前に依頼者に伝え、`docker compose stop` で止めて終わったら `start` で戻す。
- **負荷の形**: `app.env` に `MASTERSMITH_DB_MAXIMUM_POOL_SIZE=4`。`SCENARIO=userAdminPoolLimit`・`VUS=12`・`DURATION=5m`。`caffeinate -i` で台本の全体を包む（project.md の学び 2026-09-28）。
  - なぜ: 操作1件は1本目の接続を持ったまま、確定の後の監査の記録で2本目を借りる。同時の操作が上限の本数（4）以上になると、全員が2本目を待ち、`connection-timeout`（5 秒）で時間切れになる。同時 12 は上限の3倍で、準備のログインが無いため、前回のように同時の数が上限に届かないことは起きにくい。5 分は、`ms-pool-pending`（1 分ごとの値で `for: 1m`）が2回以上の送信の時点をまたぐため。
- **(a) の合格の条件**: 試験の間の `hikaricp.connections.timeout` の累計が **1 以上**、または `hikaricp.connections.acquire` の最大が **1,000 ms 以上**（`baseUnit` を見てミリ秒にそろえる。前回の上限 30 の水準は 4.9〜9.0 ms）。
- **(b) の合格の条件**: 試験の始めから終わりの後 5 分までを 30 秒ごとに読み、3件のそれぞれが少なくとも1回 `Alerting` になる。鳴る筋道:
  - `ms-pool-pending`: 待ちの数（`hikaricp_connections_pending`）が 0 より大きい状態が、1 分ごとの送信の2回以上で続く。
  - `ms-audit-fail`: 監査の記録で2本目を借りられず時間切れになると、`AuditEventListener` が「監査イベントの記録に失敗しました」を ERROR で出し、Loki の1件で鳴る。
  - `ms-error-logs`: 上の監査の失敗の ERROR と、1本目の時間切れの 500 の ERROR（`GlobalExceptionHandler`）が、5 分で 6 件以上になる。
- **(c) の合格の条件**: 409 `USER_ADMIN_BUSY` が1件以上出たときは、L3（エラー応答への変換の行、`code="USER_ADMIN_BUSY"`）の各行に、同じ `traceId` の L4（「行の排他を取れませんでした」の WARN）の行が1件ずつあり、件数が一致する。BUSY は行の排他の待ちの上限切れで出るもので、接続の待ちだけでは出ない見込み。0 件のときは (c) を `Unverified` と記録し、(c) のためだけに場面を変えて流し直さない（依頼者の判断が要る）。
- **鳴らなかったときの扱い**（警報の決まりの値は変えない。Q2: A）:
  1. まず、その警報の条件が起きたかを指標とログで確かめる（待ちの数・時間切れ・ERROR の件数・監査の失敗の行）。
  2. 条件が起きたのに鳴らなかったときは、警報の側の不具合として `Not Met` と記録し、依頼者に諮る。決まりは直さない。
  3. 条件が起きなかったときは、時間の上限 1 時間の中で1回だけ、上限 2・同時 20・8 分に強めて流し直す。それでも起きなければ記録して依頼者に諮る（`Unverified` で持ち越すかは依頼者が決める）。
- **片付けの前に**: 結果（指標・警報の状態・L3/L4 の件数）を確かめて記録してから、使い捨ての環境を消す（project.md の学び 2026-09-25）。
- **台本の確かめ**: 試験の台本を書く前に、この 7.1 の項目を1つずつ台本の手順と突き合わせる（project.md の学び 2026-09-25）。

### 7.2 そのほか

- Step 19・20 の verify と E2E の結果を正とし、CI（統合の後の push で動く）の結果を確かめる。
- R-05 の AC と U5-NFR7.1 の判定（Met）を、Step 20 の E2E の結果で記録する。
- FR6.2 の 5 回の結果と verify の結果を記録する。

## 8. traceability の予定

| 要件 | 手順 | テスト・確かめ |
|---|---|---|
| FR1.1 | Step 2 | `git ls-files -s`、Step 19 の verify（`vendorUnchanged`）、Step 20 の E2E |
| FR1.2 | Step 10・11 | `ConfirmActionDialog.test.tsx`・`EditProfileDialog.test.tsx`・`UserAdminPage.test.tsx`、E2E 110・120 |
| FR1.3 | Step 14 | E2E 110（`confirmAction`）・120（閉じる3か所と Escape・閉じるボタン） |
| FR1.4 | Step 15 | E2E 060（`invite dialog`・`revoke confirmation dialog`）・040（確かめの表示のやめる）、直したときは画面部品のテスト |
| FR2.1 | Step 12 | E2E 120 の矩形の確かめ（再現の確かめつき） |
| FR2.2 | Step 13・14 | E2E 120（`overflow.ts` の新しい関数） |
| FR3.1・FR3.2 | Step 8・9 | `EditProfileDialog.test.tsx`（再現の確かめつき） |
| FR4.1 | Step 17 | `k6 inspect` |
| FR4.2 | 7.1 節 | Build and Test の k6 の試験 |
| FR4.3 | Step 18 | 文書の確認 |
| FR5.1 | Step 6 | `UserAdminOperationsApiIT` の新しいテスト |
| FR6.1・FR6.2 | Step 7 | `MailConfigurationIT` の単独 5 回と verify |
| FR7.1 | Step 18 | 文書の確認 |
| FR8.1・FR8.3 | Step 5 | `UserUniqueViolationSecretLeakIT`・`InvitationUniqueViolationSecretLeakIT`（INFO・TRACE、再現の確かめつき） |
| FR8.2 | Step 4・5 | 同上、案 B・C のときは `GlobalExceptionHandlerTest` |
| FR9.1 | なし | Deployment Pipeline・Deployment Execution の段 |
| NFR1 | Step 10・14 | E2E 110・120（AC2.1.8: 120 の印を付けるの Escape、AC3.1.7: 110 の止める・解く と 120 の止める、AC4.1.9: 110 のロックを解除、AC5.1.7: 120 の氏名・言語の入力、U5-NFR7.1: 120 の全体） |
| NFR2 | Step 4・5 | FR8 と同じ |
| NFR3 | Step 16・19・20 | `./gradlew verify`・`./gradlew e2eTest` |
| NFR4 | Step 19 | `verify` のカバレッジの検証（1.1 節） |
| NFR5 | Step 5・9・14・15 | 各 Step の再現の確かめと、回帰のテストを直しと同じコミットに入れる（2.6 節の C2〜C5） |

## Sources

- 要件: `aidlc/spaces/default/intents/261003-user-admin-followup/inception/requirements-analysis/requirements.md`、`requirements-analysis-questions.md`
- 要件のレビュー: `aidlc/spaces/default/intents/261003-user-admin-followup/.aidlc-reviews/requirements-analysis/stage/b4f7dd3f58c6c169/1.json`（R-01〜R-07）
- スキャン: `aidlc/spaces/default/intents/261003-user-admin-followup/inception/reverse-engineering/developer-scan.md`
- 前の Intent: `aidlc/spaces/default/intents/260930-user-admin/operation/performance-validation/nfr-validation-matrix.md`（2節・3.1節）、`operation/observability-setup/log-queries.md`（L3・L4）
- make-you-chic-ui の `e82b651`（`git -C vendor/make-you-chic-ui show e82b651`）
- 決まり: `aidlc/spaces/default/memory/team.md`・`project.md`
