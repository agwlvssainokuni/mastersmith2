# セキュリティの試験の手順（security-test-instructions）

Intent `260930-user-admin` のセキュリティの目標の確かめ方です（品質の担当がリードし、セキュリティの観点（devsecops）を兼ねて書いた）。確かめは、すべて自動の検査とテストで行います。CodeQL・OWASP ZAP・コンテナイメージの検査は採用していません（`team.md` の Code Style）。

## 1. 静的な検査（`./gradlew verify` の 8 の段）

| 検査 | 止める基準 | この Intent での扱い |
|---|---|---|
| SpotBugs＋FindSecBugs（`spotbugsGate`） | priority 1、パターン名が `SQL_` で始まるもの、`PREDICTABLE_RANDOM`、`SMTP_HEADER_INJECTION`（priority によらない） | 除外を足さずに通す（U1 の NFR9.4、U2 の NFR9.4、U3 の NFR9.2）。`backend/config/spotbugs-exclude.xml` は Intent の間で変わっていない。足した問い合わせ（停止の列の更新、まとめての無効化、一覧の検索、行の排他）は名前つきの引数の JPQL かネイティブの問い合わせで、文字列をつなげない。新しいコードの指摘は priority 2・3 の警告だけ（`EI_EXPOSE_REP2`・`CT_CONSTRUCTOR_THROW`・`SPRING_ENDPOINT`・`SERVLET_HEADER_USER_AGENT`） |
| OSV-Scanner（`osvScan`） | 実行時の依存の重大度 High 以上、悪意のあるパッケージ（MAL-）、成果物を作る道具の High 以上 | lockfile が変わらないと `verify` の中では UP-TO-DATE になるため、`--rerun-tasks` で流し直して記録する。`vendor/make-you-chic-ui/package-lock.json` も対象 |
| Gitleaks（`gitleaksScan`） | 秘密情報の検出（履歴全体） | `.gitleaks.toml` は Intent の間で変わっていない |
| 画面のリンタ（`verifyLint`） | oxlint のセキュリティ系の決まり（`react/no-danger`・`no-eval`・`no-new-func`・`no-script-url` など）、ESLint、型検査（`any` の禁止） | U4 の NFR9.5、U5 の NFR9.1。HTML を直接埋め込まない |
| npm のインストール | `frontend/.npmrc` の `ignore-scripts=true` | U5 の NFR9.4（パッケージのスクリプトを動かさない） |

報告: `backend/build/reports/spotbugs/main.xml`、`build/reports/osv-scanner/osv.json`

## 2. 必ず書くテスト（`team.md` の Testing Posture）の確かめ先

この Intent に当たる項目（利用停止・管理者の印の変更・ロックの解除・最後の管理者の保護・管理の API の認可・要求の改ざん・管理の操作の監査・利用者の管理の漏えい）と、認証・認可・監査の失敗の場合のテスト（`project.md` の Mandated）の確かめ先です。テストのクラスは `backend/src/test/java/cherry/mastersmith/` の下です。

| 観点 | 主なテスト | 決めた側の動作（★の決定） |
|---|---|---|
| 利用停止（3つの入口のそれぞれで拒否、解いた直後は受け付け、止める前のトークン） | `auth/web/SuspendedUserAuthenticationIT`・`LoginApiIT`・`AccessTokenApiIT`、`auth/service/LoginServiceTest`・`TokenRefreshServiceTest`、`auth/web/AccessTokenAuthenticationProviderTest`、`useradmin/web/UserAdminOperationsApiIT`（止めるとリフレッシュトークンがすべて無効、解いても戻らない） | 停止は応答から推測できない（ほかの失敗と同じ状態コード・code・本文、照合と読み書きの回数も同じ。U1 の NFR2.1・NFR2.2）。リフレッシュトークンは止めるときにまとめて無効にし戻さない。アクセストークンは停止中は拒否、解いた後は有効期限まで使える（U1 の NFR1.3） |
| 管理者の印の変更（直後の次の要求で 403／200 が切り替わる） | `useradmin/web/UserAdminOperationsApiIT#flagChangeTakesEffectOnTheNextRequest` | 印を外す前に出したトークンは無効にしない（管理の API だけが 403）。自分の印を外す操作は 409 `USER_ADMIN_SELF_OPERATION`（U3 の NFR1.2） |
| ロックの解除（解除の直後に入れる、解除の後の失敗回数の境界、ロックの状態の行が無い利用者） | `useradmin/web/UserAdminResetLoginFailuresApiIT`、`useradmin/service/ResetLoginConcurrencyIT`、`auth/service/FailureResetPortIT`、`auth/domain/LockViewTest`（jqwik） | 操作は「失敗回数を 0 に戻す（ロック中なら解除を兼ねる）」。戻すものが無いときは 409 `USER_ADMIN_NO_CHANGE`。時刻は注入した時計 |
| 最後の管理者の保護（拒否で状態が変わらない、同時に互いの印を外す・止めても 0 人にならない） | `useradmin/service/UserAdminConcurrencyIT`、`useradmin/domain/RejectionPolicyTest`（jqwik）、`useradmin/service/UserAdminServiceTest` | 有効な管理者は「印あり・停止なし」（ロック中も数える）。重なりは待ち合わせの手伝いで確実に作る |
| 管理の API の認可（7つの API の 401・403・200（204）、停止中の管理者は 401） | `useradmin/web/UserAdminListApiIT#authorization`・`UserAdminOperationsApiIT#authorization`・`UserAdminProfileApiIT`、`useradmin/service/UserAdminConcurrencyIT`（確かめ直しの 403） | 画面で操作を隠すことはサーバー側の判定の代わりにしない（U4 の NFR1.1、U5 の NFR1.1） |
| 要求の改ざん（`/api/me` などの本文で印・停止・失敗回数を変えられない） | `useradmin/web/UserAdminMassAssignmentIT`、`user/web/MePasswordApiIT`・`MePreferencesApiIT` | — |
| 管理の操作の監査（操作した人・対象・結果） | `audit/service/UserAdminAuditIT`・`UserAdminAuditWriteFailureIT`、`audit/service/AuditEventListenerTest`（`b126bdc` で足した組み立ての失敗の2件） | 業務の拒否（`LAST_ACTIVE_ADMIN`・`NOT_ADMIN` など）も FAILURE の行で残す。入口の 401・403 は既存のアクセスの拒否だけが残る。監査の書き込みが失敗しても応答と判定を変えない |
| 利用者の管理の漏えい（応答と監査にハッシュ値・トークン・ロックの判定の内部の値が無い、TRACE でもメールアドレス・氏名が出ない） | `useradmin/web/UserAdminSecretLeakIT`・`UserAdminListApiIT`（行の11項目だけ）、`auth/web/AuthSuspensionSecretLeakIT`、`auth/web/AuthLockTimeoutLeakIT`・`user/web/MePreferencesLockTimeoutLeakIT`・`invitation/web/InvitationLockTimeoutLeakIT`（排他の上限切れの例外の文） | 個人に関する値は controller から repository の口まで伏せ字の型（`SearchText`・`RedactedText` など）で渡す |
| 構造（書き換えの口を呼ぶのは `useradmin.service` だけ、`user` は `auth` を知らない） | `useradmin/UserAdminBoundaryArchitectureTest`、`ArchitectureTest`、既存の `*BoundaryArchitectureTest`（変えていない） | — |
| 画面（S6 に原因を出さない、押せない形は表示だけ、失敗の文言は code から選ぶ、値をブラウザの外へ出さない） | 画面の `features/useradmin/*.test.ts(x)`・`app/admin-forbidden/*.test.tsx`・`shared/api-client/adminForbidden.test.ts`、E2E の 110・120・130 | 120 は `/api/admin/` の下の GET 以外を本物へ通さない（差し替えの口 `frontend/e2e/support/adminApiRoute.ts`、U5 の NFR3.5） |
| E2E の報告に秘密が入らない | `frontend/playwright-secret-check-reporter.ts`（値の種類 7 を json の報告と添付から探す） | 報告は記録してから消し、共有しない |

## 3. 流し方

```bash
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify
caffeinate -i ./gradlew osvScan --rerun-tasks

docker compose --profile mail up -d mailpit
caffeinate -i ./gradlew e2eTest
```

- E2E の後は、報告の部品の1行（見つかった件数 0）を記録し、`frontend/test-results/` を中を開かずに消します（`integration-test-instructions.md` の 4節）。
- この段の実測は `test-results.md` の 2節・3節・4節です（`b126bdc` の verify と osvScan、B5 の関門の E2E）。

## 4. この段では確かめないもの・残る危険

| 目標・危険 | 扱い |
|---|---|
| 一意の制約の違反（23505）の例外の文に重なった値が入り、TRACE の `TraceAspect` が出しうる（U3 の Q-H、未検証） | 後の Intent。直すときは先に再現のテスト（TRACE と INFO で一意の制約に当たる要求を送り、出力に重なった値が無いこと）を書く（`construction/code-generation/gate-decisions.md` の4節） |
| 出力を捕まえるテストの範囲の弱さ（`CapturedOutput` が別の文脈の背景のスレッドの出力を含む） | 後の Intent。CI で同じテストが落ちたら二度目として原因を直すまで進まない |
| 管理の API に要求の回数の制限が無い（U3 の R1） | 受け入れた危険。見直しは社外に公開する配備先が決まったとき |
| 止める確定と同時の更新・ログインが作ったトークンが残りうる（U1 の NFR1.5・R1） | 受け入れた隙。停止の間は次の要求で拒否されることだけをテストで確かめる |
| 停止を解く・失敗回数を戻すの確かめ直しの隙（U3 の R-02・R2） | 受け入れた隙。最後の管理者の保護は崩れない |
| V9 の後に1つ前の版へ戻すと停止が効かない（U1 の NFR10.3・R2） | 戻す前に停止中の利用者を確かめる手順を deployment-pipeline で決める |
| 停止中のログインとパスワードの誤りの応答時間のわずかな違い（U1 の R3） | 回数がそろうことで足りるとした。見直しは社外に公開する配備先が決まったとき |
| 8KB を超える要求の行は Tomcat の `text/html` の 400 になる（U2・U5 の Q-C） | 受け入れた危険。`UserAdminListApiIT` で挙動を確かめ、画面は一般の失敗として扱う |
| 配備した環境の `.env`・戻しの手順・スモークテストで監査に残る要求 | deployment-pipeline・deployment-execution |

## Sources

- 各単位の `nfr-requirements/security-requirements.md`・`nfr-design/security-design.md`（`aidlc/spaces/default/intents/260930-user-admin/construction/u1-user-suspension/` 〜 `u5-user-admin-ui/`）
- 各単位の `code-generation-plan.md`・`unit-test-instructions.md`・`code-summary.md`、`aidlc/spaces/default/intents/260930-user-admin/construction/code-generation/gate-decisions.md`
- `backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminOperationsApiIT.java`・`UserAdminListApiIT.java`、`backend/src/test/java/cherry/mastersmith/audit/service/UserAdminAuditIT.java`（読み取り）
- `aidlc/spaces/default/memory/team.md`（Testing Posture・Code Style・Deployment）・`aidlc/spaces/default/memory/project.md`（Forbidden・Mandated・Testing Posture）
- `README.md`（1コマンドの検査、依存関係の脆弱性の判定）

## Assumptions & Open Questions

None.
