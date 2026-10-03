# 開発担当のコードの調べ（Intent 261003-user-admin-followup）

- 範囲: 依頼者が選んだ Focused scan。深さ Minimal。調べた時点の `develop` の先頭は `47541e3`。
- 調べ方: 読み取りだけ（ファイルの読み・検索、`git -C vendor/make-you-chic-ui show` による e82b651 の読み）。ビルド・テスト・コンテナは動かしていない。
- 書き方: 各所見を「事実（コードで確かめた）」と「見立て（確かめていない）」に分ける。パスはリポジトリのルートからの相対パス。
- 依頼の束の ID（K1〜K3・T1〜T4・S1）は、Intent 260930-user-admin の `operation/feedback-optimization/feedback-loop.md` の第1の束の ID をそのまま使う。コード知識ベースの所見の番号（K-1〜K-16）とは別のもの。

## Developer Code Scan Results

### Scan Coverage
- **Analyzed deeply**:
  - frontend/src/features/useradmin/（`UserRowActions.tsx`・`ConfirmActionDialog.tsx`・`EditProfileDialog.tsx`・`UserAdminPage.tsx` のフォーカスの効果・`useUserAdmin.ts` の閉じる・保存の処理・`focusTarget.ts`・`UserAdminPage.test.tsx` と `EditProfileDialog.test.tsx` の該当のテスト）
  - frontend/e2e/（`110-user-admin-flow.e2e.ts`・`120-user-admin-accessibility.e2e.ts`・`support/overflow.ts`）
  - backend/src/main/java/cherry/mastersmith/useradmin/（一意の制約に当たる処理が無いことの確かめ）
  - backend/src/test/java/cherry/mastersmith/useradmin/（`web/UserAdminOperationsApiIT.java`・`web/UserAdminListApiIT.java` の監査の行の読み方）
  - backend/src/main/java/cherry/mastersmith/audit/（`service/AuditEventListener.java` の監査の失敗のログ）
  - backend/src/main/java/cherry/mastersmith/user/（`service/UserAccountService.java` の一意の違反の扱い）
  - backend/src/main/java/cherry/mastersmith/invitation/（`service/InvitationService.java` の一意の違反の扱い）
  - backend/src/test/java/cherry/mastersmith/mail/config/（`MailConfigurationIT.java`）
  - perf/（`README.md` の接続プールの節・`k6/scenarios.js` の利用者の管理の場面）
  - docker/monitoring/（`provisioning/alerting/mastersmith.yaml` の `ms-error-logs`・`ms-audit-fail`・`ms-pool-pending`）
- **Skimmed only**:
  - vendor/make-you-chic-ui/（固定先 3d9521a の `Modal`・`Dropdown`・`RadioGroup`・`Dropdown.css`、上流のコミット e82b651 の差分） — 部品 `make-you-chic-ui`
  - backend/src/main/java/cherry/mastersmith/common/（`observability/TraceAspect.java`・`LockFailureSafeTraceInterceptor.java`・`error/web/GlobalExceptionHandler.java`） — 部品 `common-observability`・`common-error`
  - backend/src/main/java/cherry/mastersmith/auth/service/LoginService.java（`createRow` の一意の違反の扱いだけ） — 部品 `auth`
  - backend/src/main/resources/（`application.yaml` の `logging.level`・`mastersmith.trace`・hikari、`logback-spring.xml`、`db/migration` の UNIQUE の定義） — 部品 `config`・`build-and-verify`
  - backend/src/test/java/cherry/mastersmith/common/testsupport/JsonLogRecords.java — 部品 `backend-test-support`
  - aidlc/spaces/default/intents/260930-user-admin/ の出どころの記録（`feedback-loop.md`・`gate-decisions.md` 4節・`test-results.md`・`nfr-validation-matrix.md`）
- **部品 ID の対応**: 今のコード知識ベース（Intent 260930-user-admin の始めに作った）には、利用者の管理の部品が無い。深く読んだ範囲は、既存の ID では `user`・`invitation`・`audit`・`mail`（テストだけ）・`frontend-e2e`・`perf-and-monitoring` に当たる。`backend/.../useradmin/` と `frontend/src/features/useradmin/` には既存の ID が無いため、新しい ID として `useradmin`・`frontend-feature-useradmin` を提案する（アーキテクトが決める）。

### Packages Found
- `cherry.mastersmith.useradmin`（web・service・domain） — バックエンドの機能 — Java — 利用者の管理の API（一覧・5つの操作・氏名と言語の変更）
- `frontend/src/features/useradmin` — 画面の機能 — TypeScript（React） — 利用者の管理の画面（一覧・行の「操作」・確かめの表示・氏名と言語の入力）
- `frontend/e2e` — E2E — TypeScript（Playwright） — 110（代表の流れ）・120（アクセシビリティと画面の時間）
- `perf/k6/scenarios.js` — 負荷の台本 — JavaScript（k6） — `userAdminOps`・`userAdminPool` など
- `docker/monitoring` — 監視の設定 — YAML・JSON — 警報の決まりとダッシュボード
- `vendor/make-you-chic-ui` — サブモジュール（固定先 `3d9521aa54b1d6277de473f9e935a496fb56ac1b`、`git ls-files -s` で確かめた） — TypeScript — デザインシステム

### Build System
- **Type**: Gradle（Kotlin DSL）＋ npm（`frontend/`）。今回の束はビルドの設定を変えない見込み。
- **Config Files**: `backend/build.gradle.kts`・`frontend/package.json`・`frontend/playwright.config.ts`（今回は読んでいない）
- **Build Dependencies**: `frontend` → `vendor/make-you-chic-ui`（npm の `file:` の依存）。K1 は固定先の更新（専用のコミット、team.md の fast-forward の統合の決まり）を伴う。

### APIs Discovered
- REST — `useradmin/web/UserAdminController.java` — 今回は変えない。T2 は既存の `GET /api/admin/users` と `POST /api/admin/users/{id}/revoke-admin` をテストから呼ぶだけ。

### Frameworks & Libraries
- make-you-chic-ui — 固定先 3d9521a（上流の e82b651 は手元に取得済み、未固定） — Modal・Dropdown・RadioGroup
- Playwright — E2E 110・120
- Spring Boot（OutputCaptureExtension・CapturedOutput） — `MailConfigurationIT`
- Hibernate（JPA）＋ H2 — 一意の制約の違反の例外（S1）
- k6（`grafana/k6:2.3.0`）・Grafana の警報（Prometheus・Loki） — T1

### Test Coverage
- **Test Directories**: `frontend/src/features/useradmin/*.test.ts(x)`・`frontend/e2e/`・`backend/src/test/java/cherry/mastersmith/useradmin/`・`backend/src/test/java/cherry/mastersmith/mail/config/`
- **Test Frameworks**: Vitest＋Testing Library、Playwright、JUnit 5＋AssertJ
- **Coverage Config**: present（今回は数値を測っていない）

### Code Quality Indicators
- **Linting**: 既存の設定のまま（今回は読んでいない）
- **CI/CD**: `./gradlew verify` が入口（E2E と k6 は外）
- **Documentation**: 各ファイルの先頭の日本語の説明が厚く、設計の ID（W・D・NFR・AC）で出どころをたどれる

### 所見（束の項目ごと）

#### K1 閉じた後のフォーカス（N-19）

事実:
- 確かめの表示（`ConfirmActionDialog.tsx`）と氏名・言語の入力（`EditProfileDialog.tsx`）は、状態が `null` のとき `null` を返し、Modal ごと外す（`open` を false にするのではない）。どちらも `initialFocusRef` だけを渡し、閉じた後の行き先は渡していない。
- 行の「操作」は `UserRowActions.tsx` で、`Dropdown` の `trigger` に `Button` を渡す。項目を選ぶと、Dropdown は `item.onClick` の後に `close(true)` で trigger へフォーカスを戻す（`Dropdown.tsx`）。
- 固定先 3d9521a の `useFocusTrap` は、後始末で `previouslyFocused?.focus()` を直接呼ぶ。e82b651 はこれをやめ、`onDeactivate` で開く前の要素を Modal に渡し、Modal は `setFocusToRestore(finalFocusRef?.current ?? previouslyFocused)` で、`ModalStackContext` の inert を付け外しする効果の中で inert を外した後にフォーカスを戻す。`finalFocusRef` は新しい任意の prop。
- e82b651 は `Modal.tsx`・`ModalStackContext.tsx`・`useFocusTrap.ts`・`Modal.test.tsx`・`docs/integration-guide.md` だけを変え、`Dropdown.tsx` は 3d9521a と同じ（`diff` で確かめた）。
- `Dropdown` は trigger を `cloneElement` し、`ref` を自分の関数で置き換える（`data-testid` も `dropdown-trigger` に置き換える）。
- `useFocusTrap` の効果の依存は `[active]` だけ（`eslint-disable` あり）。`onDeactivate` の関数は、効果を張った描画の時点のものが後始末で呼ばれる。
- 成功したとき（`useUserAdmin.ts` の操作・保存の処理）は、`reload({ focus: { kind: 'row', userId } })` で、一覧を読み直した後に `UserAdminPage.tsx` の効果が `actionRefs`（行の「操作」の `span`）の `querySelector('button')` へフォーカスを当てる。やめる・閉じる（`closeConfirm`・`closeEdit`）は `setConfirm(null)`・`setEdit(null)` だけで、行き先を指定しない（Modal の戻しに任せている）。
- 画面の単体テスト `UserAdminPage.test.tsx`（600〜618 行）は、やめた後に行の「操作」へフォーカスが戻ることを `waitFor` で確かめており、通っている。
- E2E 110 の `confirmAction`（124〜133 行）と 120 の `expectBackgroundInteractive`（173〜178 行）は、閉じた後に `body > [inert]` が 0 件になるのを待つだけで、閉じた後のフォーカスの行き先を確かめていない（`toBeFocused` は「やめる」に対してだけ）。

見立て（確かめていない）:
- jsdom は `inert` でフォーカスを止めないため、単体テストでは戻り、実際のブラウザだけで body に落ちていた（e82b651 のコミットの文の原因の説明と合う）。
- e82b651 に上げるだけで、やめる・閉じる場合は trigger（開く前にフォーカスのあった要素）へ戻る見込み。`finalFocusRef` は、trigger の要素が描き直しで入れ替わった場合の守りになる。
- 上流の `docs/integration-guide.md` の例（`trigger={<Button ref={menuTriggerRef} ...>}` と `finalFocusRef={menuTriggerRef}`）は、Dropdown が trigger の `ref` を置き換えるため、そのままでは `menuTriggerRef` が埋まらないおそれがある。このリポジトリでは、既にある `actionRefs`（行の `span`）から button を引く形の方が確実。`finalFocusRef` に渡すのは描画ごとに作り直さない ref（`useRef` で持ち、`.current` を更新する）にする必要がある（`onDeactivate` が効果を張った時点の関数で呼ばれるため）。
- 成功の場合は、Modal の戻し（inert を外す効果の中）と、一覧の読み直しの後の画面の効果の2つがフォーカスを動かす。順は読み直しの完了の後の画面の効果が後になる見込みで、結果は行の「操作」になるはずだが、E2E で確かめる。
- 招待の画面など、ほかの Modal を使う画面も e82b651 で同じように直る見込み（未確認。今回の範囲の外）。

直す場所: `vendor/make-you-chic-ui` の固定先（3d9521a → e82b651、専用のコミット）、`ConfirmActionDialog.tsx`・`EditProfileDialog.tsx`（`finalFocusRef` を受ける props の追加）、`UserAdminPage.tsx`（行の「操作」の button を指す ref を渡す）、E2E 110 の `confirmAction` と 120 の閉じる3か所（`check the confirm dialog`・`check the edit dialog`・Escape で閉じる流れ）で、閉じた後に行の「操作」が `toBeFocused` であることを確かめる形。

#### K2 行の「操作」のメニューのはみ出し

事実:
- `UserRowActions.tsx` の `Dropdown` は `placement` を渡しておらず、既定の `bottom-start` になる。Dropdown の `placement` は `'bottom-start' | 'bottom-end'` を受ける。
- メニューは `createPortal` で body に置かれ、`Dropdown.css` で `position: fixed`。位置は `computeFloatingPosition(triggerRect, menuRect, placement)` で決まる。
- E2E 120 のはみ出しの判定（`support/overflow.ts`）は `document.documentElement.scrollWidth > window.innerWidth` だけを見る。メニューを開いた状態（`02-self-menu-open`・`11-disabled-item-hover`・`11-disabled-item-focus`）でも同じ判定。
- `frontend/src` で `Dropdown` を使うのは `UserRowActions.tsx` だけ（`frontend/src/features/README.md` を除く）。

見立て（確かめていない）:
- `position: fixed` の要素は文書の横の大きさ（`scrollWidth`）に入らないため、メニューが右へはみ出しても今の判定は通る。開いたメニューの `getBoundingClientRect()` の右端が `window.innerWidth` 以下（左端が 0 以上）であることを確かめる関数を `support/overflow.ts` に足す形が要る。
- `computeFloatingPosition` が画面の端で寄せるか（clamp・flip）は読んでいない。

#### K3 送信中の言語の欄

事実:
- `EditProfileDialog.tsx` は送信中（`status === 'submitting'`）に、氏名の `TextInput` を `readOnly` にするが、言語の `RadioGroup` には何も渡しておらず、送信中も選び直せる。`onChangeLanguage` は `useUserAdmin.ts` の `setEdit` で状態を書き換える。
- make-you-chic-ui の `RadioGroup` は `disabled?: boolean` を受け、各 `Radio` に渡す（固定先 3d9521a で確かめた）。
- `EditProfileDialog.test.tsx` には、氏名だけの「送信中は読み取りだけ」のテスト（187〜193 行）があり、言語のテストは無い。

見立て（確かめていない）:
- `disabled` にすると、送信中に言語の欄にあったフォーカスが外れうる（ConfirmActionDialog の「やめる」の `disabled` と同じ扱い）。`disabled` にするか、`onChange` を送信中に無視する形にするかは設計で決める。

#### T1 上限 10 の (B) と警報3件の確かめ直し

事実:
- `perf/k6/scenarios.js` の `userAdminOpsRound`（867〜879 行）は、印を付ける → 外す → 止める → 解く → 準備のログインの失敗（`/api/auth/login` に誤ったパスワード、`tags.name = userAdminPrepLogin`）→ 失敗回数を戻す、の順。準備のログインは `check` に数えないが、要求は送る。
- `perf/README.md`（321〜337 行）の上限 10 の手順は、使い捨てのアプリの `app.env` に `MASTERSMITH_DB_MAXIMUM_POOL_SIZE=10` を足し、`SCENARIO=userAdminOps` を `VUS=5`（A）・`VUS=10`（B）で流し、`hikaricp.connections.timeout`・`hikaricp.connections.acquire` を `/actuator/metrics` から読む。
- `application.yaml`: `maximum-pool-size: ${MASTERSMITH_DB_MAXIMUM_POOL_SIZE:30}`、`connection-timeout: 5000`。指標の送信の周期は `step: 60s`（306 行）。
- 警報（`docker/monitoring/provisioning/alerting/mastersmith.yaml`）:
  - `ms-error-logs`: `sum(increase(logback_events_total{service_name="mastersmith",level="error"}[5m])) > 5`、`for: 0s`
  - `ms-audit-fail`: Loki の `sum(count_over_time({service_name="mastersmith"} |= "監査イベントの記録に失敗しました" [1h])) > 0`、`for: 0s`
  - `ms-pool-pending`: `max(hikaricp_connections_pending{service_name="mastersmith"}) > 0`、`for: 1m`
- 監査の失敗のログは `audit/service/AuditEventListener.java` の `FAILURE_MESSAGE`（72 行）を ERROR（`setCause(e)` 付き、297〜301 行）で出す。
- 前回の (B) は、時間切れ 0・待ちの最大 6.0 ms・警報3件は 22 回すべて inactive だった（`nfr-validation-matrix.md` の 2節・3.1節）。

見立て（確かめていない）:
- 1つの組の時間の大半が準備のログインの bcrypt で、操作の同時の数が上限に届かなかった（前回の見立てと同じ）。準備のログインを外す（例: 止める・解くだけ、または印を付ける・外すだけをくり返す新しい場面）か、ロックの状態の行を作るのを `setup` へ移すと、同時の数が上がる見込み。上限を 10 よりさらに下げる案（4〜6）は前回の記録の選択肢 A にある。
- `ms-pool-pending` は 1 分ごとに送る pending のゲージの瞬間の値を見て `for: 1m` を求めるため、短い待ちでは鳴りにくい。鳴らすには、待ちが送信の時点をまたいで 1 分以上続く負荷（長めの場面）が要る。式の見直しは前の Intent で「配備先が決まったとき」に持ち越されている（`gate-decisions.md` 4節）。
- `ms-error-logs` は 5 分で 6 件以上の ERROR、`ms-audit-fail` は監査の失敗 1 件で鳴る。接続の待ちが `connection-timeout`（5 秒）を超えて監査の2本目を借りられないと、監査の失敗の ERROR が出て両方が鳴る見込み。409 `USER_ADMIN_BUSY`（L3・L4 の結び付き）は、行の排他の待ちの上限切れで出るもので、プールの待ちだけでは出ないかもしれない。

#### T2 AC2.2.6（印を外した直後の 403 と監査を1つのテストで）

事実:
- `useradmin/web/UserAdminOperationsApiIT.java` の `flagChangeTakesEffectOnTheNextRequest`（264〜287 行）は、印を付ける・外す直後の一覧の 200・403 `ACCESS_DENIED` を確かめるが、監査の行は見ない。
- 監査の行の読み方の前例は `useradmin/web/UserAdminListApiIT.java` にある（`audit_events` を `ORDER BY audit_event_id OFFSET ? ROWS` で読み、`ACCESS_DENIED NOT_ADMIN <path>` の形で比べる。360〜368 行）。`UserAdminOperationsApiIT` も `audit_events` の件数を数える問い合わせ（65 行）を持つ。

置き場の見立て: `flagChangeTakesEffectOnTheNextRequest` に監査の行の確かめを足すか、同じクラスに新しいテストを足す。本体のコードの変更は要らない見込み（テストだけ）。

#### T3 `MailConfigurationIT` の出力を捕まえる範囲

事実:
- `MailConfigurationIT` は `@ExtendWith(OutputCaptureExtension.class)` で、`CapturedOutput` の `getOut()` を `JsonLogRecords.parse` で JSON として読み、`MailConfig` のロガーの WARN・INFO を `singleElement()` で確かめ、`getAll()` に秘密の値が無いことを確かめる。
- `CapturedOutput` は JVM 全体の標準出力を捕まえる。前の Intent の B4 の関門で、別のテストの文脈の背景のスレッドが出した OTLP の指標の送信の WARN を取り込んで1回落ちた（`test-results.md` 134 行。単独では通過）。

見立て（確かめていない）:
- どの確かめで落ちたか（`JsonLogRecords.parse` が JSON でない行で落ちたのか、`singleElement` か）は記録に無い。捕まえた行を、自分の起動した文脈のもの（スレッドの名前、またはテストの中で起動した文脈だけのロガー）に絞るか、捕まえる前に別の文脈が止まっていることを待つ形が考えられる。OTLP の送信が有効になっている別のテストの文脈が閉じられずに残っているかは確かめていない。

#### T4 `perf/README.md` の hikaricp の単位

事実:
- `perf/README.md` の 259 行・307 行・330 行は `hikaricp.connections.acquire` を「借りるまでの待ちの最大」として読むが、単位（`baseUnit`）の注意書きは無い。前の Intent の Performance Validation で、外部エクスポートの有無で秒とミリ秒が変わったと記録されている（`nfr-validation-matrix.md` 65 行）。

直す場所: `perf/README.md` の 259 行の段落（接続プール）と、手順の 307・330 行の近くに、`/actuator/metrics` の応答の `baseUnit` を見て読むことを書く。

#### S1 一意の制約の違反の例外の文（Q-H）

事実:
- 一意の制約（`db/migration`）: `uk_users_email`（V2）・`uk_refresh_tokens_token_hash`（V3）・`uk_dsl_previews_preview_id`・`uk_dsl_applied_revisions_revision_id`（V5）・`uk_invitations_token_hash`・`uk_invitations_pending_email`（V8）。
- 利用者の作成 `user/service/UserAccountService.createUser` は、先に `existsByRedactedEmail` で確かめ、同時の作成で `saveAndFlush` が `DataIntegrityViolationException` を投げたら、原因の連なりの Hibernate の `ConstraintViolationException` の制約の名前が `UK_USERS_EMAIL` を含むときだけ結果の型 `EmailAlreadyUsed` にする。ほかの制約は投げ直す。登録の完了（`invitation`）もこの口を使う。
- 招待 `invitation/service/InvitationService.issueWithOneRetry` は、`UK_INVITATIONS_PENDING_EMAIL` の違反だけを受けて勝った側を読み直し、2回目は `IllegalStateException`（原因に元の例外を付ける）を投げる。ほかの制約は投げ直す。
- ログインのロックの状態の行（`auth/service/LoginService.createRow`）は `MERGE` で作り、`DataIntegrityViolationException` を DEBUG（`userId` だけ）で受ける。
- `useradmin` の本体には `DataIntegrityViolationException` を扱う処理が無く、氏名・言語の変更と5つの操作は一意の制約のある列に書かない。
- 追跡の `TraceAspect` は web・service・domain・repository の層の Bean（と repository のインターフェースのメソッド）を対象に、例外のときに `EXCEPTION ...: $[exception]`（例外の文字列）を TRACE で出し、`log-exception-stack-trace: true`（既定）で原因の連なりも出す。例外の文を伏せるのは `LockFailureSafeTraceInterceptor` で、行の排他の失敗（`RowLockFailures.isLockFailure`）だけ。
- 想定外の例外は `GlobalExceptionHandler` で 500 `INTERNAL_ERROR` にし、ERROR を `setCause(ex)`（原因の連なり付き）で出す。応答には例外の文を載せない。行の排他の失敗だけはクラスの名前だけを出す。
- `application.yaml` の `logging.level` は、JDBC ドライバー3つとメールの部品を OFF にしているが、Hibernate（`org.hibernate.engine.jdbc.spi.SqlExceptionHelper` など）の水準は指定していない（root は INFO）。

見立て（確かめていない）:
- H2 の 23505 の文は、重なった値（例: メールアドレス）を含む。経路は3つ考えられる。
  1. Hibernate の `SqlExceptionHelper` が、JDBC の例外を変換するときに WARN（SQL Error と SQLState）と ERROR（例外の文）を出す既定の動き。これが当たると、TRACE を有効にしなくても、既定の INFO で、アプリが受けて結果の型にする場合（同時の利用者の作成・同時の招待）でもメールアドレスがアプリのログに出る。**最も確かめたい点**。
  2. `TraceAspect` の TRACE（repository の `saveAndFlush` を対象にするか、service の層を例外が通るとき）。`createUser` はメソッドの中で受けるため service の層の EXCEPTION は出ないが、repository の呼び出しが対象なら出る。Spring Data の継承したメソッド（`saveAndFlush`）が `execution(* cherry.mastersmith..repository..*.*(..))` に当たるかは確かめていない。
  3. 投げ直された例外（想定外の制約、または招待の2回目の `IllegalStateException`）が `GlobalExceptionHandler` の ERROR（原因の連なり付き）に載る経路。応答には載らない。
- 監査（`audit_events`）・トレースの属性に例外の文が入る経路は、今回読んだ範囲では見つからなかった（監査の失敗の ERROR は監査の書き込み自体の失敗だけ）。
- 確かめのテストは、同時の作成か、テストから直接の重なった INSERT で `uk_users_email`・`uk_invitations_pending_email` に当て、INFO と TRACE の両方で、出力に重なった値が無いことを `*SecretLeakIT` の形で確かめる形が考えられる。出ると分かったときの直し方（Hibernate のロガーの水準を下げる、`TraceAspect` の伏せる対象を一意の違反に広げる、など）は設計で決める。

### Technical Debt Signals
- E2E 120 のはみ出しの判定が `position: fixed` の部品を見ない（`frontend/e2e/support/overflow.ts`）。
- E2E 110・120 が閉じた後のフォーカスを確かめていない（`frontend/e2e/110-user-admin-flow.e2e.ts` 124〜133 行、`120-user-admin-accessibility.e2e.ts` 173〜178 行）。
- `CapturedOutput` が JVM 全体の出力を捕まえる（`backend/src/test/java/cherry/mastersmith/mail/config/MailConfigurationIT.java`）。同じ形のテストはほかにもある見込み（数えていない）。
- Hibernate の SQL の誤りのログの水準が指定されていない（`backend/src/main/resources/application.yaml` の `logging.level`）。
- コード知識ベースに利用者の管理の部品（`useradmin`・`frontend-feature-useradmin`）が無い。

### 関わる既存の所見（コード知識ベース `aidlc/spaces/default/codekb/mastersmith2/`）
- K-8（一覧の型と応答は、TRACE のログとメールアドレスの決まりに当たる。`component-inventory.md` の `user`）: S1 の TRACE の経路と同じ論点。
- K-12・K-13（make-you-chic-ui の固定先と E2E 100 の既知の違反）: 記録上の固定先 `077f5b4…` は古く、今は 3d9521a。K1 で e82b651 に上げる。
- K-15（`ms-check-p95` のしきい値）: 警報のファイルを直した前例。今回の `ms-pool-pending` とは別の警報。
- K-5・K-9（管理の画面と API の前例）: 利用者の管理の部品の元になった前例。
- コード知識ベースは Intent 260930-user-admin の始め（利用者の管理を作る前）の記録のため、今回の範囲の多く（`useradmin` の両側、E2E 110・120、k6 の利用者の管理の場面）は記録に無い。

## Handoff Summary
- **Intent-relevant finding**: K1 は固定先を e82b651 に上げれば、閉じた後の inert とフォーカスの順が直る（`ModalStackContext.tsx` の効果の中で戻す）。ただし Dropdown は trigger の `ref` を置き換えるため（`Dropdown.tsx` の `cloneElement`）、`finalFocusRef` には上流の手引きの例の形ではなく、既存の `actionRefs`（`UserAdminPage.tsx`）から行の「操作」の button を指す、描画ごとに作り直さない ref を渡す必要がある。K2 は `UserRowActions.tsx` に `placement="bottom-end"` を足し、E2E 120 の判定を開いたメニューの矩形で見る形に広げる（今の `scrollWidth` の判定は `position: fixed` のメニューを見ない）。K3 は `EditProfileDialog.tsx` の `RadioGroup` に送信中の `disabled` を渡す（部品に口がある）。S1 は、Hibernate の `SqlExceptionHelper` の既定のログが INFO で H2 の 23505 の文（重なったメールアドレス）を出しうることを、TRACE の経路より先に確かめるべき。
- **Risks / follow-up**:
  - K1 の固定先の更新は team.md の決まりで専用のコミット（fast-forward の統合）になり、更新前後のハッシュ（3d9521a → e82b651）を記録する。e82b651 が上流に公開済みかは確かめていない（手元に取得済みであることだけ確かめた）。
  - K1 の成功の場合は、Modal の戻しと一覧の読み直しの後の画面の効果の2つがフォーカスを動かす。E2E で最後の行き先を確かめる。
  - T1 は台本（`perf/k6/scenarios.js`）と手順（`perf/README.md`）の両方の変更が要り、`ms-pool-pending`（`for: 1m`、1 分ごとのゲージ）は短い待ちでは鳴りにくい。完了の目安（警報3件が Alerting）を満たすには、待ちが 1 分以上続く負荷が要る見込み。
  - S1 の確かめで値が出ると分かったときは、`project.md` の Forbidden（メールアドレスをアプリのログに含めない）に当たるため、直しが要る。直し方によっては `common` のパッケージに手が入る。`backend/build.gradle.kts` 225〜233 行の `packagesJudgedByTotal`（今は 7 個）のうち `common.error.domain`・`common.error.service`・`common.web` などに当たれば、team.md の決まりでそのパッケージの下限を満たして一覧から外す作業が付く（`common.observability` と `common.error.web` は一覧に無い）。
  - T3 は原因の確かめ（どの確かめで落ちたか）が記録に無く、再現も難しい。直し方は見立てのまま。
