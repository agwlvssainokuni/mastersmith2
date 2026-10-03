# 生成の記録（261003-user-admin-followup）

code-summary.md を書くための、生成の途中の実測と判断の記録。1回目の依頼（計画の Step 1〜9）の分。

## Step 1 準備

- 段を始める前の E2E の生成物（`frontend/playwright-report`・`frontend/test-results`）: どちらも無かった（消すものは無し）。
- `develop`（`f3b8535`）から短命のブランチ `fix/261003-user-admin-followup` を作った（worktree は使わない）。

## Step 2 make-you-chic-ui の固定先（C1 の中身）

- 前: `3d9521aa54b1d6277de473f9e935a496fb56ac1b`
- 後: `e82b651c53ac6e048066fcc049e49386c0eb282e`
- `git ls-files -s vendor/make-you-chic-ui` → `160000 e82b651c53ac6e048066fcc049e49386c0eb282e 0`。
- `git diff --cached --stat` はサブモジュールの1行だけ（`vendor/make-you-chic-ui | 2 +-`）。索引に入れたのはサブモジュールの固定先だけで、ほかのファイルは索引に入れていない。
- 3d9521a → e82b651 の上流の差は 5 ファイル（`Modal`・`ModalStackContext.tsx`・`useFocusTrap.ts` など、124 行の追加・8 行の削除）。
- `./gradlew vendorBuild frontendInstall`: 成功。`frontend/package-lock.json` は変わらなかった。

## Step 3 変更の前のテスト（基準）

- 画面（`unit-test-instructions.md` 2節のコマンド、useradmin の3ファイル）: 3 ファイル・76 件すべて通過。
- バックエンド（3.1 節の変更の前のコマンド）: `MailConfigurationIT` 7 件・`UserAdminOperationsApiIT` 6 件、すべて通過（失敗・飛ばし 0）。

## Step 4 S1 の直し（案 C）

- 案 A: `backend/src/main/resources/application.yaml` の `logging.level` に `org.hibernate.orm.jdbc.error: OFF` を足し、既存のドライバー・メールの部品の行と同じ形で「なぜ」と「障害の調べ方」をコメントに書いた。
- 案 B: `common.error.web` に判定の補助 `UniqueViolations`（パッケージの中だけで使う。連なりに `DataIntegrityViolationException`・Hibernate の `ConstraintViolationException`、または SQLState `23505` の `SQLException` があれば真）を足し、`GlobalExceptionHandler` の想定外の誤り（5xx）の ERROR で、行の排他の失敗と同じ形（原因をつながず `exceptionClass` だけ）にした。応答は 500 `INTERNAL_ERROR` のまま。
  - 計画の「型の2つ」に加えて SQLState `23505` でも見分ける（`RowLockFailures` と同じく、型の写し方が版で変わっても受けるため）。計画との小さな差として記録する。
- 業務処理の層（`UserAccountService`・`InvitationService`）は変えていない。
- 変えた Java のパッケージは `common.error.web` だけ（`packagesJudgedByTotal` に当たらない）。

## Step 5 漏えいの確かめのテスト

- 足したテスト:
  - `GlobalExceptionHandlerTest`: 9 件 → 13 件。一意の違反の連なり3通り（Spring の `DataIntegrityViolationException`、`IllegalStateException` に包んだ Hibernate の `ConstraintViolationException`、SQLState `23505` の `SQLException` だけ）で、ERROR が1件・原因なし・`exceptionClass` だけ・応答は 500 `INTERNAL_ERROR`（値と `UK_` を含まない）であることのパラメーターつきのテスト（3件）と、一意の違反でない SQL の誤り（SQLState `42000`）は原因を保つことのテスト（1件）。結果: 13 件すべて通過。
  - `user/service/UserUniqueViolationSecretLeakIT`（新規、2件）: INFO・TRACE。待ち合わせは `UserCreationIT` の `BarrierConfig`・`BarrierPasswordEncoder` をそのまま `@Import` して使った（同じパッケージ。待ち合わせの部品を写さないため）。
  - `invitation/service/InvitationUniqueViolationSecretLeakIT`（新規、2件）: INFO・TRACE。`TestInvitationBarrier.insertTogether(2)`、JVM の中の SMTP の受け手。
  - 確かめ: 重なりの結果（作成1・登録済み1／招待1・招待中1）、実行の後のアプリのログ（標準出力の差分と標準エラー出力）にメールアドレス（小文字・大文字・大文字小文字を無視）と違反の目印（`PUBLIC.UK_USERS_EMAIL`・`PUBLIC.UK_INVITATIONS_PENDING_EMAIL`）が無いこと、実行の後に足された `audit_events` の行の全部の列にメールアドレスと目印が無いこと。TRACE では `ENTER UserAccountService#createUser`・`ENTER InvitationService#invite` があること（空振りでないこと）、INFO では `ENTER ` が無いこと。招待では勝った側の監査の行が1件以上あることも確かめた。利用者の作成は監査の行を書かない（行は 0 件、計画の段の確かめと同じ）。
  - 結果: 2 クラス・4 件すべて通過。
- 再現の確かめ（直しを外して落ちることの確認）: `application.yaml` の `org.hibernate.orm.jdbc.error: OFF` の1行を消し、`GlobalExceptionHandler.java` を `HEAD` の内容に戻して、2つの漏えいのテストを流した。
  - 結果: 4 件すべて失敗（`[ログに秘密の値が含まれている]`）。生の出力の行で違反の目印を含むものは、ロガー `org.hibernate.orm.jdbc.error` の行だけ（各クラス 2 行）。ほかのロガーには出ていなかった。
  - 直しを戻し、`git diff --stat` で2つのファイルが直した内容（`GlobalExceptionHandler.java` +6/-1、`application.yaml` +10）に戻ったことと、2つの漏えいのテストが再び 4 件通ることを確かめた。
  - 案 B（`GlobalExceptionHandler`）の経路の漏えいは、今回の2つの経路では起きない（業務の結果の型で受けるため ERROR に届かない）。案 B の確かめは `GlobalExceptionHandlerTest` の単体のテストで行った。
- R-04 のトレースの属性: `UserAccountService`・`InvitationService` にスパンへ値を載せる処理（Observation・span の属性の設定）が無いことを grep で確かめ直した。外へ送る前の除き方は既存の `SanitizingSpanExporter`（新しいテストは足していない）。
- ArchUnit の境界テスト（全 13 クラス）と `spotlessCheck` は通過（`common.error.web` が Hibernate の例外の型を参照するが、禁じる決まりは無い）。

## Step 6 印を外した直後の 403 と監査（FR5.1）

- `UserAdminOperationsApiIT` に `revokeThenForbiddenIsAudited` を足した（6 件 → 7 件）。印のある利用者でログイン → 印がある間は一覧が 200 → 管理者が印を外す（204）→ 同じトークンで一覧が 403 `ACCESS_DENIED` → 監査の行を `ORDER BY audit_event_id OFFSET ? ROWS` で読み、ちょうど2行（`USER_ADMIN_REVOKED`・`SUCCESS`・理由なし・操作した人＝管理者・対象＝その利用者、続いて `ACCESS_DENIED NOT_ADMIN /api/admin/users`・`FAILURE`）。
- 本体のコードは変えていない。既存の `flagChangeTakesEffectOnTheNextRequest` は残した。
- 結果: 7 件すべて通過。

## Step 7 MailConfigurationIT の確かめの範囲（FR6.1・FR6.2）

- 各テストの起動の前に、出力の長さとテストのスレッドの名前（`Mark`）を覚え、`configRecords` はその後の出力から、`thread` がテストのスレッドで `logger` が `MailConfig` の行だけを読む形にした。JSON として読めない行は飛ばす。補助は `MailConfigurationIT` の中に置き、共通の `JsonLogRecords` は変えていない。
- 秘密の値の確かめ（`output.getAll()` に値が無いこと）は範囲を絞らずに残した。テストの数は 7 件のまま。
- 範囲を絞った後も、INFO の行を `singleElement` で読めていることから、`MailConfig` の起動の行はテストのスレッドから出ている（絞り込みで空振りしていない）。
- 単独の 5 回（`unit-test-instructions.md` 3.1 のループ）: 5 回とも 7 件すべて通過。
- 再現は試みていない（Q3: A）。
- 4 クラスまとめての実行（3.1 節の Step 5〜7 の後のコマンド）: `UserUniqueViolationSecretLeakIT` 2・`InvitationUniqueViolationSecretLeakIT` 2・`UserAdminOperationsApiIT` 7・`MailConfigurationIT` 7、計 18 件すべて通過。

## Step 8・9 送信中の言語の選択（FR3）

- `EditProfileDialog.tsx` の `RadioGroup` に `disabled={submitting}` を渡した（e82b651 の `RadioGroup` が `disabled` を各 `Radio` へ渡すことをソースで確かめた）。
- `EditProfileDialog.test.tsx`: 試しの部品（Harness）に省略できる `onChangeLanguage` の見張りを足し、テストを3件足した（10 件 → 13 件）。
  - 送信中は2つの選択肢が `disabled` で、押しても `onChangeLanguage` が呼ばれず選択が変わらない。
  - 状態が `open`・`failed` のときは選べる（`it.each` で2件）。
- 再現の確かめ: `RadioGroup` の `disabled={submitting}`（とコメントの1行）だけを外して流し、送信中のテスト1件だけが落ちる（12 件通過・1 件失敗）ことを確かめ、戻した（`git diff --stat` で直した内容 +2 に戻ったことを確認）。
  - 1回目の試しでは、置き換えの範囲を誤って「やめる」のボタンの `disabled={submitting}` も外してしまい、既存のテスト1件も落ちた。範囲を直してやり直した結果を上に書いた（どちらも直しは戻してある）。
- 計画 5節の「言語の欄で Enter を押して送信したときのフォーカス」: 捨てのテストで確かめた（終わった後にファイルを消した）。jsdom では、言語の選択肢で Enter を押すとフォームが送信され、送信中になった後もフォーカスは `disabled` になったその選択肢に残った（`INPUT/radio/disabled=true`）。実際のブラウザで欄から外れるか（body に落ちるか）は jsdom では分からず、確かめていない。code-summary.md の「依頼者に確かめたいこと」に載せる候補。
- 3ファイル（useradmin）の画面のテスト: 3 ファイル・79 件すべて通過（基準の 76 件から +3）。変えた2ファイルの Prettier の確かめは通過。

## 計画との差（この回の分）

- `UniqueViolations` の判定に、型の2つに加えて SQLState `23505` を足した（Step 4）。
- `UserUniqueViolationSecretLeakIT` の待ち合わせは、写さずに `UserCreationIT` の入れ子の設定を `@Import` して使った（Step 5）。
- 監査の行の確かめは、計画の「文の列（失敗の理由など）」ではなく、足された行の全部の列を文字列にして確かめた（広い方が強いため、Step 5）。

## 依頼者に確かめたいことの候補（この回の分）

- 送信中に言語の欄にフォーカスがあるとき（言語の欄で Enter を押して送信したとき）、`disabled` によって実際のブラウザでフォーカスが欄から外れうる。jsdom ではフォーカスは残ったが、ブラウザの動きは確かめていない（E2E で確かめるか、このままとするか）。

## 2回目の依頼（計画の Step 10〜21）の途中で決まったこと

Step 19 の verify で、`InvitationAdminPage.test.tsx` の「reloads after a 404 on revoke, and returns focus to the row after other failures」（615 行）が決まって落ちた。C1（make-you-chic-ui の固定先 e82b651）で、Modal が閉じた後のフォーカスの戻しが、閉じた時点ではなく ModalStack の effect（背景の inert を外した後）に遅れたため、一覧の読み直しの後に当てた見出しへのフォーカスを、Modal が「取り消す」へ戻して上書きする。オーケストレーターを通して依頼者に尋ね、次のとおり決まった（2026-10-04）。

- **G1: (a)**。招待の画面で順序を保証する。表示を先に閉じ、Modal が閉じ終わった後（フォーカスの戻しと inert を外すことが済んだ後）に、一覧の読み直しと見出し・行へのフォーカスを行う。Modal を閉じてからフォーカスを動かす招待の経路のすべてに同じ順序を当てる（取り消しの 404、取り消しの成功（見出しまたは空の表示）、`showPendingRow`（行の「送り直す」））。テストの応答を遅らせる案 (c) は採らない。615 行のテストは弱めずに通し、成功と `showPendingRow` の経路のテストが無ければ足す。直しを一時的に外してテストが落ちることを確かめる。DSL の管理の画面（`DslConfirmDialog`）と利用者の管理の画面（操作の成功の後の読み直しなど）に同じ形の競争があるかを確かめ、あれば同じに扱い、結果を記録する。
- **G2**: 氏名・言語の入力で、言語の選択肢で Enter を押して送信したときの、本物のブラウザでのフォーカスの行き先（送信中・送信の後）を E2E 120 で確かめる。欄から外れてもコードは変えず、code-summary.md に記録する。E2E の確かめは安定するときだけ足し、そうでなければ E2E を流したときの観察を記録する。
