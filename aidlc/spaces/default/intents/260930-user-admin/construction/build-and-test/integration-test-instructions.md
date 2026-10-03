# 結合テストの手順（integration-test-instructions）

Intent `260930-user-admin` の結合テストの手順です。Test Strategy は Standard です。そのため、単位の境目と単位の間のやり取りを結合テストで確かめます。対象は次の2つです。

- **バックエンドの結合テスト**（`*IT`、`./gradlew verify` の 6 の段）
- **画面からの一連の操作**（E2E、`./gradlew e2eTest`）。実際のブラウザの検査（axe）と画面の時間の測りを含みます。

単体テストの手順は、各単位の `unit-test-instructions.md`（`aidlc/spaces/default/intents/260930-user-admin/construction/u*/code-generation/`）にあります。この文書では繰り返しません。

## 1. 準備

- `build-instructions.md` の 3.1（colima と `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE`）と 3.2（Mailpit）を済ませておきます。
- テストのデータは、テストごとに用意して巻き戻します。実行の順には依存させません（`team.md` の Testing Posture）。
  - 内部DB は組み込みの H2 です。この Intent の結合テストはすべて内部DB を使い、対象DB のコンテナのテストは足していません（既存の対象DB のテストは毎回流れます）。
  - 時刻は注入した時計で動かし、`sleep` と実時刻に頼りません（ロックの判定・アクセストークンの有効期限）。
  - 同時の重なりは、スレッドの数ではなく待ち合わせの手伝い（`TestUserAdminBarrier`・`TestLoginAttemptBarrier`・`H2SessionWaits`・`RowLockHolder`）で確実に作ります。待ち合わせで止める時間は排他の待ちの上限（3000 ミリ秒）より短い 2000 ミリ秒です（U3 の NFR4.4）。
- E2E は1つの WAR と内部DB と初期管理者を共有するため、ファイル名の番号の順に1本ずつ流します（`workers: 1`）。110 は前のテストが作った状態に頼らず、利用者 U を自分で作り、状態を変えるのは U だけです。初期管理者の印・停止・ロックは変えません。

## 2. 流し方

```bash
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock

# バックエンドの結合テスト（件数とカバレッジを実測するときは clean を付ける）
caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify

# 結合テストだけ（開発中の途中の実行）
./gradlew :backend:integrationTest

# 1つのクラスだけ
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.useradmin.web.UserAdminOperationsApiIT'

# E2E（Mailpit を起動して。もとから動いていれば止めない）
docker compose --profile mail up -d mailpit
caffeinate -i ./gradlew e2eTest
```

- 結果は `frontend/test-results/e2e-results.json` に出ます。html の報告（`frontend/playwright-report/`）は作られません（U5 の E2E の設定の直し）。
- 報告の部品（`frontend/playwright-secret-check-reporter.ts`）が、E2E のたびに、残してはならない値（署名鍵などの環境変数の3つ・初期管理者のメールアドレスとパスワード・110 が作る U の宛先・パスワード・氏名・`runTag` の 7 種類）が json の報告と添付に無いことを確かめ、結果を1行で出します（U5 の NFR3.4）。

## 3. 単位の境目の結合テスト（この Intent で足したもの・直したもの）

| 境目 | 主なテストクラス（`backend/src/test/java/cherry/mastersmith/` の下） | 確かめること |
|---|---|---|
| U1 利用停止（3つの入口 ↔ 利用者の要約 ↔ 内部DB） | `auth/web/SuspendedUserAuthenticationIT`・`LoginApiIT`・`AccessTokenApiIT`・`AuthSuspensionSecretLeakIT`、`auth/service/RefreshTokenRevocationServiceIT`、`user/service/UserSuspensionIT`、`user/repository/UserSchemaIT`、`access/web/AccessDeniedEventsIT`、`audit/service/AuditAuthenticationEventsIT`・`AuditWriteFailureIT`、`user/web/MePasswordApiIT`・`MePreferencesApiIT`、`invitation/web/InvitationAdminApiIT` | ログインの照合・トークンの更新・アクセストークンの認証のそれぞれで停止中は拒否、解いた直後は受け付け、応答がほかの失敗と同じ、止めるときのリフレッシュトークンのまとめての無効化、停止中のログインの監査、一括代入で停止を変えられない、停止中の利用者のメールアドレスへの招待は登録済みの拒否 |
| U2 ページ送り（共通の部品 ↔ 招待の一覧） | `invitation/web/InvitationAdminApiIT`、`invitation/repository/InvitationRepositoryIT` | 参照先の変更だけで招待の一覧の既存の確かめ（page の誤りの 400 `VALIDATION_FAILED` を含む）が通ること |
| U3 利用者の管理の API（web ↔ 業務処理 ↔ U1・U2 の口 ↔ 内部DB ↔ 監査） | `useradmin/web/UserAdminListApiIT`・`UserAdminListQueryCountIT`・`UserAdminProfileApiIT`・`UserAdminOperationsApiIT`・`UserAdminResetLoginFailuresApiIT`・`UserAdminBusyApiIT`・`UserAdminMassAssignmentIT`・`UserAdminSecretLeakIT`、`useradmin/service/UserAdminOperationsIT`・`UserAdminConcurrencyIT`・`ResetLoginConcurrencyIT`・`SuspendWhileLoginIT`、`user/repository/UserAdminQueriesIT`・`UserRowLockRepositoryIT`、`user/service/UserAdminAccountIT`・`UserAdminLockPortsIT`、`auth/service/FailureResetPortIT`、`audit/service/UserAdminAuditIT`・`UserAdminAuditWriteFailureIT` | 7つの API の 401・403・200（204）、印の付け外しの直後の次の要求での 403／200 の切り替え、停止中の管理者は 401、業務の拒否の code と状態が変わらないこと、最後の有効な管理者の保護（同時に互いの印を外す・止める）、失敗回数を戻す操作とログインの重なり、排他の上限切れの 409 `USER_ADMIN_BUSY`、一覧の問い合わせの回数、監査の行と書き込みの失敗、TRACE でも個人に関する値が出ないこと |
| 既存の経路の上限切れの漏えい（U3 で直した） | `auth/web/AuthLockTimeoutLeakIT`・`user/web/MePreferencesLockTimeoutLeakIT`・`invitation/web/InvitationLockTimeoutLeakIT` | 新しい排他で待たされうる既存の書き込みの経路で、上限切れの例外の文の行の値がログと応答に出ないこと |
| 既存のテストの片付け（U1） | `V7MigrationIT`・`V7BackwardCompatibilityIT`・`V8MigrationIT`・`V8BackwardCompatibilityIT` を消し、確かめを `InvitationSchemaIT`・`InvitationConcurrencyIT` に移した | 移した確かめが残っていること（U1 の `code-summary.md` 3節） |

- この Intent で増えた件数（clean 付きの実測）: 単体 1243 → 1508、結合 566 → 689（`test-results.md` の 2節）。

## 4. 画面からの一連の操作（E2E）と実際のブラウザの検査

| ファイル | 中身 | この段で写すもの |
|---|---|---|
| `010`〜`100` | 既存の流れと検査（骨格・認証・管理画面・DSL の管理・表示・招待・登録・プリファレンス・招待から登録の流れ・文字のコントラスト） | 成否（make-you-chic-ui の固定先を上げた後も通ること。U5 の NFR9.3） |
| `110-user-admin-flow.e2e.ts` | 代表の流れ E2E-M9: 検索 → ロック → 失敗回数を戻す → 409・400 の照らし合わせ → 止める → 入れない → 停止を解く → 入れる → 画面と本物のサーバーを通した 403 | 成否、`skip-reason` の注記が無いこと、注記 `user-admin-flow`（lockAttempts・管理者の画面の問題・CSP の違反） |
| `120-user-admin-accessibility.e2e.ts` | U5 の画面の 20 組 × 12 回の axe と、画面の時間の測り（`user-admin-screen-ms`） | 組ごとの成否・違反の件数・はみ出し・差し替えの口の記録（受けた件数・GET 以外の打ち切り）・CSP の違反、測りの値（記録のみ） |
| `130-admin-forbidden-accessibility.e2e.ts` | U4 の S6（権限が無い）の 20 組の axe | 組ごとの成否・違反の件数・はみ出し |

- 流れの E2E の本数に数えるのは 110 だけです。120・130 は検査と測りで、本数に数えません（`project.md` の Corrections）。
- **この段では E2E を流し直していません**（`build-and-test-questions.md` の Q2: A）。B5 の関門（`81d2423`）の結果を正とします。その後の変更は U3 の直し（`b126bdc`、監査の組み立てに失敗したときのログの値だけ）で、画面と API の応答は変わっていません。
- 次に E2E を流すとき（画面・認証の変更の統合の前、リリースの前、make-you-chic-ui の固定先を上げるとき）の報告の扱い（基盤の設計の U4・U5 の申し送り）:
  1. `caffeinate -i ./gradlew e2eTest` を流す。
  2. `frontend/test-results/e2e-results.json` の `stats` と、テストごとの題・状態・時間・注記、添付 `user-admin-screen-ms`（base64 を復号した数だけ）を読み、記録する。値を含みうるほかの添付は開かない。
  3. 報告の部品の1行（見つかった件数 0）を記録する。
  4. `frontend/test-results/`（`frontend/playwright-report/` があればそれも）を中を開かずに消す。
  5. 消したこと（ファイルとディレクトリの数）と、共有していないことを記録する。
- make-you-chic-ui の N-19 の直しを取り込むときは、`aidlc/spaces/default/intents/260930-user-admin/construction/u5-user-admin-ui/code-generation/generation-notes.md` の「C2′ の後に流し直すもの」の手順で、110 の `confirmAction`・120 の `expectBackgroundInteractive` を「閉じた後にフォーカスが行の『操作』に戻る」ことを確かめる形に替えてから流します（後の Intent）。

## 5. 期待する結果

- `./gradlew verify` が `BUILD SUCCESSFUL` になること。対象DB のテストの飛ばしが 0 件であること。
- 結合テストの失敗が 0 件であること。
- E2E のすべてのファイルが通ること（13 ファイル・152 件）。110 に `skip-reason` が無いこと。報告の部品の見つかった件数が 0 であること。
- カバレッジが全体と各パッケージで行 80%・分岐 70% 以上であること（`build-instructions.md` の 4節）。

実測は `test-results.md` に写し、目標ごとの判定は `build-and-test-summary.md` の表に書きます。

## Sources

- 各単位の `unit-test-instructions.md`・`code-generation-plan.md`（「Build and Test に引き継ぐこと」）・`code-summary.md`（`aidlc/spaces/default/intents/260930-user-admin/construction/u1-user-suspension/` 〜 `u5-user-admin-ui/` の `code-generation/`）
- `aidlc/spaces/default/intents/260930-user-admin/construction/u5-user-admin-ui/code-generation/generation-notes.md`（Step 18、「Step 22（C2′ の前）」、「C2′ の後に流し直すもの」）
- `aidlc/spaces/default/intents/260930-user-admin/construction/infrastructure-design/gate-decisions.md`（U4・U5 の E2E の報告の片付けの申し送り）
- `backend/src/test/java/cherry/mastersmith/` の `*IT.java`（この Intent で足した・直したもの。`git diff --name-status ded2653~1 7689ade`）、`frontend/e2e/110-user-admin-flow.e2e.ts`・`120-user-admin-accessibility.e2e.ts`・`130-admin-forbidden-accessibility.e2e.ts`
- `aidlc/spaces/default/memory/team.md`（Testing Posture）・`aidlc/spaces/default/memory/project.md`（Testing Posture・Corrections）
- `aidlc/spaces/default/intents/260930-user-admin/construction/build-and-test/build-and-test-questions.md`（Q2）

## Assumptions & Open Questions

None.
