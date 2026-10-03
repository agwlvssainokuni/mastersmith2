# コード生成の要約（261003-user-admin-followup）

- 範囲: bugfix（深さ Minimal、Test Strategy は Minimal）。単位の分割は無い（zero-Unit）。作業ブランチは `fix/261003-user-admin-followup`。
- 進め方: test-after（計画の Testing Contract）。層ごとに実装を書き、同じ段の中でその層のテストを書いて流した。
- 依頼者の決定: D1 = C、D2 = A、D3 = A、D4 = A（計画の承認の前）。G1・G2（生成の途中、2026-10-04。下の 4節）。
- 生成の途中の実測と判断の細かい記録は `generation-notes.md` にある。

## 1. 変えたファイル

| 区分 | ファイル | 中身 | 要件 |
|---|---|---|---|
| サブモジュール | `vendor/make-you-chic-ui` | 固定先を上げた（中身は変えない。コミット C1 `a272fd3` に済み） | FR1.1 |
| バックエンド | `backend/src/main/resources/application.yaml` | `logging.level.org.hibernate.orm.jdbc.error: OFF`（なぜと障害の調べ方をコメントに書いた） | FR8.2（案 A） |
| バックエンド | `backend/src/main/java/cherry/mastersmith/common/error/web/UniqueViolations.java`（新規） | 例外の連なりに一意の制約の違反（Spring の `DataIntegrityViolationException`・Hibernate の `ConstraintViolationException`・SQLState `23505`）があるかの判定 | FR8.2（案 B） |
| バックエンド | `backend/src/main/java/cherry/mastersmith/common/error/web/GlobalExceptionHandler.java` | 想定外の誤り（5xx）の ERROR で、一意の違反の連なりはクラスの名前だけを出す（行の排他の失敗と同じ形）。応答は 500 `INTERNAL_ERROR` のまま | FR8.2（案 B） |
| バックエンドのテスト | `backend/src/test/java/cherry/mastersmith/common/error/web/GlobalExceptionHandlerTest.java` | 一意の違反の3通りと、違反でない SQL の誤りのテスト（9 件 → 13 件） | FR8.2 |
| バックエンドのテスト | `backend/src/test/java/cherry/mastersmith/user/service/UserUniqueViolationSecretLeakIT.java`（新規） | 同時の利用者の作成の漏えいの確かめ（INFO・TRACE） | FR8.1・FR8.3 |
| バックエンドのテスト | `backend/src/test/java/cherry/mastersmith/invitation/service/InvitationUniqueViolationSecretLeakIT.java`（新規） | 同時の招待の漏えいの確かめ（INFO・TRACE） | FR8.1・FR8.3 |
| バックエンドのテスト | `backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminOperationsApiIT.java` | 印を外した直後の 403 と監査の2行を1つのテストで確かめる（6 件 → 7 件） | FR5.1 |
| バックエンドのテスト | `backend/src/test/java/cherry/mastersmith/mail/config/MailConfigurationIT.java` | 確かめを、テストのスレッドが起動の後に出した `MailConfig` の行だけに絞る（7 件のまま） | FR6.1・FR6.2 |
| 画面 | `frontend/src/features/useradmin/ConfirmActionDialog.tsx`・`EditProfileDialog.tsx` | 省略できる `finalFocusRef` を受けて Modal へ渡す。`EditProfileDialog` は送信中に言語の `RadioGroup` を `disabled` にする | FR1.2・FR3.1 |
| 画面 | `frontend/src/features/useradmin/UserAdminPage.tsx` | 描画ごとに作り直さない ref を1つ持ち、行の「操作」から表示を開くときにその行の「操作」のボタンを入れて2つの表示へ渡す | FR1.2 |
| 画面 | `frontend/src/features/useradmin/UserRowActions.tsx` | Dropdown に `placement="bottom-end"` | FR2.1 |
| 画面 | `frontend/src/shared/modal/afterModalClosed.ts`（新規） | Modal が閉じ終わった後（背景の inert が外れ、Modal のフォーカスの戻しが済んだ後）に処理を始める口 | G1 |
| 画面 | `frontend/src/features/invitation/useInvitationAdmin.ts` | 取り消しの成功・404 と、招待中の行へ移る経路で、Modal が閉じ終わった後に一覧を読み直す | G1 |
| 画面のテスト | `frontend/src/features/useradmin/ConfirmActionDialog.test.tsx`・`EditProfileDialog.test.tsx`・`UserAdminPage.test.tsx` | `finalFocusRef` の受け渡し（やめる・Escape）、画面で Escape で閉じた後に行の「操作」へ戻ること、送信中の言語の選択のテスト | FR1.2・FR3.2 |
| 画面のテスト | `frontend/src/shared/modal/afterModalClosed.test.ts`（新規） | 口の5件 | G1 |
| 画面のテスト | `frontend/src/features/invitation/InvitationAdminPage.test.tsx` | 閉じ終わった後に読み直すことの確かめ2件を足した。615 行付近の失敗の後の確かめを `waitFor` にした（下の 4節） | G1 |
| E2E | `frontend/e2e/110-user-admin-flow.e2e.ts` | 確かめの表示を閉じた後に行の「操作」が `toBeFocused` であることを確かめる形に替えた（inert を待つ形をやめた） | FR1.3 |
| E2E | `frontend/e2e/120-user-admin-accessibility.e2e.ts` | inert を待つ形をやめ、閉じた後のフォーカスを確かめる。Escape・閉じるボタン・「管理者の印を付ける」の Escape を足した。開いたメニューの矩形の確かめを足した。G2 の観察のテストを1件足した | FR1.3・FR2.2・G2 |
| E2E | `frontend/e2e/support/overflow.ts` | 開いたメニューの矩形が表示の中に収まるかを測る `measureViewportContainment` と説明の `describeContainment` | FR2.2 |
| E2E | `frontend/e2e/040-dsl-admin.e2e.ts`・`060-invitation-accessibility.e2e.ts` | 「やめる」で閉じた後に開いた元のボタンへフォーカスが戻ることの確かめを足した（流れは足していない） | FR1.4 |
| 負荷の台本 | `perf/k6/scenarios.js` | 場面 `userAdminPoolLimit`（`constant-vus`、閾値なし。状態コードの件数を Counter で出す）。既存の場面は変えていない | FR4.1 |
| 手順 | `perf/README.md` | 表に場面を足し、`baseUnit` の注意書きと「接続プールの上限を下げて流す」の節を足した | FR4.3・FR7.1 |

- make-you-chic-ui の固定先: 前 `3d9521aa54b1d6277de473f9e935a496fb56ac1b` → 後 `e82b651c53ac6e048066fcc049e49386c0eb282e`（`project.md` の Mandated）。`frontend/package-lock.json` は変わらなかった。
- 変えた Java のパッケージは `common.error.web` だけ。`packagesJudgedByTotal` の一覧（7 個）には当たらない。
- 業務処理の層（`UserAccountService`・`InvitationService`）は変えていない。

## 2. 主な判断

- **FR8（S1）の直し方は案 C**（依頼者の決定 D1）。確かめた漏えいは、Hibernate がアプリへ例外を渡す前に自分で出す WARN（`org.hibernate.orm.jdbc.error`）だったため、案 A で止めた。案 B は、想定外の一意の違反が `GlobalExceptionHandler` の ERROR に原因の連なりごと出る経路の守りにした。
- **R-04 のトレースの属性**: `UserAccountService`・`InvitationService` には、スパンに値を載せる処理が無い（grep で確かめ直した）。外へ送る前の除き方は既存の `SanitizingSpanExporter`（`SanitizingSpanExporterTest` で確かめ済み）に任せ、新しいテストは足していない。エラー応答には、2つの経路とも業務の結果の型で受けるため例外の文が届かない。
- **FR1.2 の ref の作り方**: `useRef` を1つ持ち、表示を開くときに `.current` を入れる。Dropdown は trigger の `ref` を置き換えるため、上流の手引きの例（trigger の Button に ref を付ける形）は使っていない（K-17）。
- **FR1.2 の対象の外**: 401・403 の共通の扱い（`handledCommonly`）で閉じたときは、画面がログイン・権限が無い画面へ移るため、行の「操作」は無い。
- **FR1.2 の閉じ方ごとの動き**:
  - 確かめの表示は、やめる・Escape・閉じるボタン・成功・操作の失敗のどれでも閉じて、行の「操作」へ戻る。
  - 氏名・言語の入力は、保存の失敗（`failed`・`notFound`・入力の誤り）では閉じずに表示に残る。その後に閉じたときは、行の「操作」へ戻る。
- **FR2.1**: jsdom は矩形を持たないため、回帰のテストは E2E 120 の矩形の確かめとした（画面部品のテストは足していない）。
- **G1 の作り（4節）**: 背景の inert がすべて外れたことを `MutationObserver` で待つ。inert を外してフォーカスを戻す ModalStackProvider の効果が終わった後に知らせが届くため、読み直しとその後のフォーカスは、必ず Modal のフォーカスの戻しより後になる。答えを遅らせる案 (c) は採っていない。

## 3. テストの結果（実測）

### 3.1 変更の前の基準（Step 3）

- 画面（useradmin の3ファイル）: 76 件すべて通過。
- バックエンド（`MailConfigurationIT`・`UserAdminOperationsApiIT`）: 7 件・6 件すべて通過。

### 3.2 段の途中の確かめ

- `GlobalExceptionHandlerTest`: 13 件通過。
- 漏えいのテスト2クラス: 4 件通過。
- 4 クラスまとめて: 18 件通過。
- `MailConfigurationIT` の単独の実行: 5 回とも 7 件通過（FR6.2）。
- 画面（useradmin の3ファイル）: 84 件通過（基準の 76 件から +8）。
- 画面（useradmin・invitation・shared）: 37 ファイル・355 件通過。
- typecheck・lint・format:check・license:check: すべて通過。
- E2E（040・060・110・120 の4ファイル）: 44 件通過。
- `k6 inspect --include-system-env-vars`: `userAdminPoolLimit` が `constant-vus` で閾値なし。`userAdminOps`・`userAdminPool` の閾値は変わっていない。

### 3.3 再現の確かめ（直しを一時的に外して落ちること。どれも戻した）

| 不具合 | 外したもの | 結果 |
|---|---|---|
| FR8 | `application.yaml` の1行と `GlobalExceptionHandler` の変更 | 漏えいのテスト4件がすべて失敗した。違反の目印を含む行は `org.hibernate.orm.jdbc.error` の行だけだった |
| FR3 | `RadioGroup` の `disabled={submitting}` | 送信中のテスト1件だけが失敗した |
| FR1.2 | 2つの表示から Modal への `finalFocusRef` の受け渡し | 足した表示のテスト4件が失敗した。画面のテストは jsdom では inert でフォーカスが止まらないため落ちず、body に落ちる不具合の再現は E2E が受け持つ |
| FR2 | `placement="bottom-end"`（WAR を作り直した） | E2E 120 の 20 組すべてが失敗した。広い幅は右端 1371px > 1280px、375px の幅は右端 467px > 375px。直した後の右端は 1251/1280px・346/375px |
| G1 | `useInvitationAdmin` の「閉じ終わった後に始める」を、すぐに呼ぶ形に戻した | 615 行のテストと、足した2件の計3件が失敗した |
| FR1（固定先を戻しての再現） | — | 行っていない（依頼者の決定 D3: A） |

### 3.4 統合の前の全体の検査（Step 19）

コマンドは `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` で、colima の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡し、全体を `caffeinate -i` で包んだ。

- 結果: **BUILD SUCCESSFUL**（8 分 59 秒、2026-10-04 05:22〜05:31）。
- バックエンド: 単体 1,512 件・結合 694 件で、失敗・飛ばしは 0 件。対象DB（MySQL・MariaDB・PostgreSQL）のテストも流れた。
- フロントエンド: 110 ファイル・970 件すべて通過。
- そのほかの検査: spotbugsGate・gitleaks（漏れ無し）・osvScan・カバレッジの検証はすべて通過した。

### 3.5 E2E（Step 20）

コマンドは `./gradlew e2eTest`（`caffeinate -i` で包んだ。Mailpit は起動済み）。

- 結果: **153 件すべて通過**（6 分 34 秒）。G2 の観察のテストを足したため、前回の 152 件から +1。
- json の報告の秘密の値の確かめ: 見つかった件数 0。
- 110・120 のフォーカスと矩形の確かめ、040・060 のフォーカスの確かめは通った。
- R-05 の AC（AC2.1.8・AC3.1.7・AC4.1.9・AC5.1.7）と U5-NFR7.1 の判定の記録は、Build and Test で行う。

### 3.6 FR1.4: 利用者の管理の画面の外の Modal（画面ごとの結果）

| 画面 | 表示 | 開いた元 | 閉じ方 | フォーカスの戻り | 直したか |
|---|---|---|---|---|---|
| 招待 | `InviteDialog` | 「招待する」 | やめる | 戻る（E2E 060、20 組） | 直していない |
| 招待 | `CancelConfirmDialog` | 行の「取り消す」 | やめる | 戻る（E2E 060、20 組） | 直していない |
| DSL の管理 | `DslConfirmDialog`（適用） | 「適用する」 | やめる | 戻る（E2E 040） | 直していない |

- 成功で閉じる場合は、計画どおり各画面のフォーカスの決まりに任せた。そのうち招待の画面は、G1 で順序を直した（4節）。

### 3.7 カバレッジ（Step 19 の verify の実測）

| 対象 | 行 | 分岐 |
|---|---|---|
| バックエンドの全体 | 98.9%（6287/6357） | 94.8%（2348/2476） |
| `common.error.web`（変えたパッケージ。パッケージごとの下限の対象） | 97.2% | 89.4% |
| `user.service`（テストを足した） | 99.7% | 95.1% |
| `invitation.service`（テストを足した） | 100% | 93.5% |
| `mail.config`（テストを変えた） | 98.0% | 96.2% |
| `useradmin.web`（テストを足した） | 98.8% | 93.8% |
| フロントエンドの全体 | 97.44% | 92.77% |
| `src/features/useradmin` | 96.22% | 90.93% |
| `src/features/invitation` | 95.74% | 90.58% |
| `src/shared/modal` | 100% | 100% |

- フロントエンドの全体は、文 97.27%・関数 97.92%。
- 下限（行 80%・分岐 70%）・除外・`packagesJudgedByTotal` の一覧は変えていない。

## 4. 計画との差

### 4.1 依頼者が決めたこと（生成の途中、2026-10-04）

#### G1: (a) 招待の画面で、Modal が閉じ終わった後に読み直す

- **見つかったこと**: Step 19 の verify で、`InvitationAdminPage.test.tsx` の「reloads after a 404 on revoke, and returns focus to the row after other failures」が決まって落ちた（単独でも verify でも再現した）。
  - 原因は C1 の固定先の更新（make-you-chic-ui e82b651）。3d9521a では Modal は閉じた時点ですぐにフォーカスを戻していた。e82b651 では、戻しが ModalStack の効果（背景の inert を外した後）に遅れる。
  - そのため、閉じると同時に始めた一覧の読み直しの後に当てた見出しへのフォーカスを、後から来る Modal の戻し（「取り消す」へ）が上書きした。本物のブラウザでも、一覧の応答が速ければ起こりうる競争だった。
- **決定**: 表示を先に閉じ、Modal が閉じ終わった後（フォーカスの戻しと inert を外すことが済んだ後）に、読み直しと見出し・行へのフォーカスを行う。
  - 当てた経路は3つ: 取り消しの 404（見出し）、取り消しの成功（見出しまたは空の表示）、`showPendingRow`（行の「送り直す」）。
  - 答えを遅らせる案 (c) は採らなかった。
- **作り**: `frontend/src/shared/modal/afterModalClosed.ts`（新規）と、`useInvitationAdmin.ts` の `afterDialogClosed`（画面を離れたら待ちをやめる）。
- **テスト**
  - 615 行のテストは弱めずに通る。
  - 一覧の読み直しが始まった時点で、表示が閉じ終わっていた（背景に inert が無く、dialog・alertdialog が無い）ことを記録して確かめるテストを2件足した。1件は取り消しの成功と 404、もう1件は `showPendingRow`。
  - 直しを外すと、この2件と 615 行のテストの計3件が落ちることを確かめた。
- **同じ形の確かめ（直していない）**
  - DSL の管理の画面（`DslConfirmDialog`）: 確かめの表示は「適用する」などを押したクリックの中で閉じ、操作の要求はその後に始まる。Modal の戻しは、操作の応答より前に済む。開いた元が残ったまま後でプレビューの見出しへ移る既存のテスト（「confirms the replacement, loads the schema and moves to the new preview」）が、答えの速い jsdom でも通っていることで確かめた。競争は無いと判断し、直していない。
  - 利用者の管理の画面: 成功・失敗の後の読み直しで当てる先は、Modal の戻し先（`finalFocusRef` の行の「操作」）と同じ要素である。行が消えたとき（見出しへ移る）は、戻し先の要素が文書から外れていて戻しは何もしない。競争は無いと判断し、直していない。
- **追加で直したテストの確かめ方**
  - verify の中のカバレッジの実行（`frontendCoverage`、計測で遅い）で、同じテストの「ほかの失敗の後に行の『取り消す』へ戻る」確かめ（625 行付近）が1回落ちた。Modal の戻しが効果に遅れたのに、同期で確かめていたため。
  - team.md の「描画の後に反映される値は `waitFor` で待つ」に合わせて `waitFor` にした。直した後、カバレッジの実行を3回流して3回とも 970 件通った。
  - 同期で確かめている Modal の戻しは、ほかに4か所ある（`InvitationAdminPage.test.tsx` の Escape、`DslConfirmDialog.test.tsx` の2か所、`DslAdminPage.test.tsx` の「やめる」）。どれもクリック・キーの操作の中で閉じる形で、失敗は出ていないため変えていない。

#### G2: 言語の選択肢で Enter を押して送信したときのフォーカス（本物のブラウザ）

- E2E 120 に観察のテスト「edit dialog submitted with Enter on the language radio (G2 observation)」を1件足した。
  - 氏名・言語の保存の答えを止め、送信中のフォーカスの行き先を注記 `edit-enter-focus` に記録する。
  - コードは変えていない。
- 観察（Chromium、合わせて 21 回）:
  - Enter で送信される。
  - 送信の直後は、`disabled` になった言語の選択肢に `document.activeElement` が残ることが多い（10 回中 9 回、全体の実行でも同じ）。
  - 描画を2回待つと、**フォーカスは body に落ちる（10 回中 10 回）**。送信中、フォーカスは表示の中のどこにも無い。
  - 送信が終わって表示が閉じた後は、行の「操作」へ戻る（21 回とも）。
- テストが確かめる（失敗にする）のは、安定している「送信の後に行の『操作』へ戻ること」と、送信中に選択肢が押せないことだけ。送信中の行き先は記録だけにした。
- 最初の試しでは、はじめのフォーカス（氏名）が当たる前に選択肢へ移したため、氏名へ戻されて観察がぶれた。はじめのフォーカスを待つ形に直した後は、20 回続けて通った。

### 4.2 生成の中で決めた小さな差

- `UniqueViolations` の判定に、型の2つに加えて SQLState `23505` を足した（`RowLockFailures` と同じく、型の写し方が版で変わっても受けるため）。
- `UserUniqueViolationSecretLeakIT` の待ち合わせは、写さずに `UserCreationIT` の入れ子の設定を `@Import` して使った。
- 監査の行の確かめは、計画の「文の列（失敗の理由など）」ではなく、足された行の全部の列を文字列にして確かめた（広い方が強いため）。
- `userAdminPoolLimit` の状態コードの件数は、数え分けのタグではなく、k6 の Counter（`userAdminPoolLimit_204`・`_409`・`_500`・`_other`）で出した。k6 の要約は、閾値の無いタグの内訳を出さないため。
- `perf/README.md` の上限を下げる手順では、手元の監視（`grafana/otel-lgtm`）を使い捨ての環境のネットワークに名前 `lgtm`・画面 `127.0.0.1:13000` で起動する形にした。警報の決まりはリポジトリのファイルをそのまま読ませる。
- `baseUnit` の注意書きには、前の Intent の Performance Validation の実測（エクスポート無効は seconds、有効は milliseconds）を書いた。

### 4.3 検査を流したときに起きたこと（記録）

- **verify の2回目**: PC の自動のスリープ（`pmset -g log` で約 15 分のスリープを3回確認）で約1時間かかって打ち切られたため、結果を使っていない。以後は `caffeinate -i` で包んだ（`project.md` の学び）。
- **不安定と確かめられていない失敗**: `caffeinate -i` で包んだ verify の1回（05:14、スリープなし）で、変更に関わらない `frontend/src/features/preferences/PasswordChangePage.test.tsx` の「drops the answer when the screen is left while sending」が1回落ちた。画面を離れた後に成功の Toast が出ていた。
  - team.md の決まりどおり、時間を区切って再現を試みた: 単独で 10 回、全体で2回（スリープの無い実行）。どれも再現しなかった。
  - コードは変えず、流し直した verify（3.4）で通った。見立ては「送信の答えと画面を離れる操作の順の、時間に依存する競争」だが、確かめていない。
  - CI で再び落ちたときは、同じ決まり（2回目なら原因を直すまで進まない）で扱う。
- **スリープと重なった実行**: 全体の画面のテストの1回は、PC のスリープ（231 秒）と重なり、新しい `afterModalClosed.test.ts` の1件が時間切れになった。この回は結果に使っていない。

## 5. 依頼者に確かめたいこと

1. **G2 の観察の扱い**: 氏名・言語の入力で、言語の選択肢で Enter を押して送信すると、送信中はフォーカスが body に落ちる（表示の中に無い）。送信が終われば行の「操作」へ戻る。氏名の欄は `readOnly`（フォーカスは残る）、言語は `disabled`（フォーカスが外れる）で扱いが違う。次の Intent に持ち越すか、このままとするかを決めてほしい。
   - 持ち越すときの直し方の候補は2つ: 送信中は `disabled` の代わりに、変更を受け付けない形にする。または、送信中はフォーカスを「保存」のボタンへ移す。
2. **G1 の直し方の確かめ**: 招待の画面だけに当てた。DSL・利用者の管理の画面は競争が無いと判断して変えていない（4.1）。この判断でよいか。
3. **不安定と確かめられていない失敗**（`PasswordChangePage.test.tsx`、4.3）: 統合の後の CI でも落ちたときの扱い（直すまで次の Bolt に進まない）でよいか。

## 6. Build and Test に引き継ぐこと

- T1（FR4.2）の負荷の試験。計画の 7.1 節の値（依頼者の決定 D2: A）と、`perf/README.md` の「接続プールの上限を下げて流す」の手順で行う。
- R-05 の AC と U5-NFR7.1 の判定（Met）を、3.5 の E2E の結果で記録する。
- 統合の後の CI の結果の確かめ（4.3 の不安定なテストを含む）。
