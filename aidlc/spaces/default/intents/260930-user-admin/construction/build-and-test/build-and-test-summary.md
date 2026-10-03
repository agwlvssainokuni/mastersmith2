# Build and Test のまとめ（build-and-test-summary）

Intent `260930-user-admin`（U1〜U5、Bolt B1〜B5）の Build and Test の結果です。実測の詳細は `test-results.md` にあります。

## 1. 全体の状態

| 観点 | 状態 |
|---|---|
| ビルド（手元） | 準備済み。`b126bdc`（`develop` の `7689ade` とアプリのソースが同じ）で clean 付きの `./gradlew verify` が成功（10 分 27 秒） |
| テスト（手元） | 準備済み。バックエンド 単体 1508 件・結合 689 件、画面 955 件、E2E 152 件がすべて通過 |
| CI | B1〜B5 の統合の後と、承認の後の push の CI（run 37106195579、約 15 分）がすべて success |
| 配備 | **条件つき**。Not Met 1 件（N-19、後の Intent への持ち越しとして承認済み）と、後の段が持つ Unverified 22 件が残る |

前提は `build-instructions.md` のとおりです。
- colima（CPU 4・メモリ 6GiB）と `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE`
- 2つのサブモジュール（make-you-chic-ui は `3d9521a`）
- E2E の前の Mailpit

この段では検査を流し直していません（`build-and-test-questions.md` の Q1: A・Q2: A）。バックエンドと osvScan は `b126bdc` の実測、画面のテストと E2E は B5 の関門（`81d2423`）の実測を正とします。

## 2. 作った手順書とテストの種類

| 文書 | 中身 |
|---|---|
| `build-instructions.md` | 道具・取得・環境・ビルド・確認・手当て（固定先 `3d9521a`、`ignore-scripts=true`、V9、E2E 13 ファイル） |
| `integration-test-instructions.md` | 単位の境目の結合テスト（U1〜U3、上限切れの漏えい）と E2E（110・120・130）、E2E の報告の扱い |
| `performance-test-instructions.md` | 画面の時間と初回の JavaScript（この段で写す）、k6 の5つの場面（測定は performance-validation） |
| `security-test-instructions.md` | 静的な検査、必ず書くテストの確かめ先、残る危険 |
| `test-results.md` | この段の実測と、Bolt ごとの関門と CI のまとめ（段全体のレビューの R-03） |
| `cross-unit-traceability.md` | 要件と受け入れ基準 129 件の網羅（未網羅 0 件、根拠を補った3件、条件つき4件） |

Test Strategy は Standard です。単体テスト（JUnit・Vitest、jqwik・fast-check の性質ベースを含む）、結合テスト（Spring と内部DB の `*IT`）、E2E（Playwright の代表の流れ 110）、実際のブラウザのアクセシビリティの検査（120・130）、静的なセキュリティ検査（SpotBugs＋FindSecBugs・Gitleaks・OSV-Scanner）を使いました。

## 3. 単位ごとのカバレッジ（この段の実測）

- バックエンドの全体：行 98.9%（6278/6348）・分岐 94.8%（2335/2462）（`b126bdc`）
- 画面の全体：行 97.42%（2881/2957）・分岐 92.85%（1870/2014）（`81d2423`）
- パッケージごとの下限を当てるパッケージは、すべて行 80%・分岐 70% 以上です（`test-results.md` の 2.1）。

| 単位 | 主なパッケージ・範囲（行・分岐） |
|---|---|
| U1 | `auth.domain` 98.2%・100.0%、`auth.repository` 100.0%・100.0%、`access.domain` 100.0%・100.0%（3つとも一覧から外した）、`auth.service` 99.3%・93.0%、`auth.web` 100.0%・95.5%、`user.web` 96.5%・84.6% |
| U2 | `common.paging` 100.0%・100.0%、`invitation.domain` 98.3%・96.4%、`invitation.service` 100.0%・93.5% |
| U3 | `useradmin.web` 98.8%・93.8%、`useradmin.service` 99.3%・95.9%、`useradmin.domain` 100.0%・100.0%、`common.persistence` 100.0%・100.0%、`invitation.lock` 100.0%・100.0%、`common.observability` 97.7%・95.5%、`common.error.web` 97.0%・88.9%、`audit.service` 100.0%・86.1%、`audit.domain` 99.6%・98.4%、`user.service` 99.7%・95.1% |
| U4 | 画面の全体に含む。U4 で作った4ファイルは行・分岐とも 100%、`AdminForbiddenProvider.tsx` 100%・95% |
| U5 | 画面の全体に含む。`features/useradmin` の合計 96.49%・92.17% |

## Target Verification Matrix

略記:
- Source は `aidlc/spaces/default/intents/260930-user-admin/` を省いた相対パスで書く。
- 「verify」は `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡した `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` の `b126bdc` の実測（BUILD SUCCESSFUL 10 分 27 秒、単体 1508 件・結合 689 件、失敗 0・飛ばし 0）。画面のテストは `verify` の中の Vitest で、値は B5 の関門（`81d2423`、109 ファイル・955 件）を写す。
- 「e2e」は B5 の関門（`81d2423`）の `caffeinate -i ./gradlew e2eTest`（13 ファイル・152 件 expected、飛ばし 0・想定外 0・不安定 0）。
- テストのクラスの名前は `backend/src/test/java/cherry/mastersmith/`・`frontend/src/`・`frontend/e2e/` を省いて書く。
- 目標の ID は、単位の NFR 要件の ID（単位の中で .1 から振ったもの）に単位の記号を付けた。要件の中に、この段で確かめられる部分と後の段が持つ部分が混ざるものは a・b に分けた。NFR 設計の文書は、その `traceability.json` の上流がすべて同じ単位の NFR 要件の ID であるため、NFR 要件の ID の行の Expected と Evidence に含めた。

| Target ID | Source | Expected | Actual | Evidence | Owning Stage | Verdict |
|---|---|---|---|---|---|---|
| TC-1 | construction/u*/code-generation/code-generation-plan.md の Testing Contract（team.md の Testing Posture） | バックエンドの全体の行 80%・分岐 70% 以上 | 行 98.9%（6278/6348）・分岐 94.8%（2335/2462） | verify の jacocoTestReport.xml | build-and-test | Met |
| TC-2 | 同上 | 画面の全体の行 80%・分岐 70% 以上（vitest.config.ts の thresholds） | 行 97.42%（2881/2957）・分岐 92.85%（1870/2014） | B5 の関門の verify の coverage-summary.json | build-and-test | Met |
| TC-3 | 同上 | 新しいパッケージと一覧から外したパッケージはパッケージごとに行 80%・分岐 70% 以上 | すべて満たす。最も低い分岐は `user.web` 84.6%・`audit.service` 86.1% | verify の jacocoTestReport.xml・test-results.md 2.1 | build-and-test | Met |
| TC-4 | 同上・team.md の Testing Posture | `packagesJudgedByTotal` を増やさない、計測の除外を増やさない、手を入れた一覧のパッケージは外して下限を満たす | 12 → 7（B1 で3つ、B4 で2つ外した）。除外の変更なし | backend/build.gradle.kts・U1・U3 の code-summary.md 2節 | build-and-test | Met |
| TC-5 | 同上 | 全テストが通る、対象DB のテストを飛ばさない | 単体 1508・結合 689 の失敗 0・飛ばし 0、画面 955 件通過 | verify | build-and-test | Met |
| TC-6 | 同上（Methodology・Ordering） | test-after、層ごとに実装とテストを書いて通してから次の層へ | 各単位の計画の層の順の Step がすべてチェック済み | 各単位の code-generation-plan.md 5節 | build-and-test | Met |
| TC-7 | org.md・team.md の Way of Working（classic の CI の実行） | 統合の後の CI が通る | B1〜B5 のすべての統合の後の CI が success（B3 9 分 21 秒、B4 12 分 29 秒、B5 約 12 分） | construction/code-generation/gate-decisions.md 1節、run 37015756225・37036697597・37080444724 | build-and-test | Met |
| CI-PUSH | 同上 | 承認の後の push（`b126bdc`・`7689ade`）の CI が通る | success（`7689ade`、2026-10-03T07:23:38Z〜07:38:52Z、約 15 分）。ジョブ `./gradlew verify` は success、`リリースに WAR を添付する` は skipped（タグでないため）。失敗したテストは無く、`MailConfigurationIT` も落ちていない | run 37106195579（オーケストレーターが確かめた結果） | build-and-test | Met |
| SEC-1 | team.md の Code Style（静的解析とセキュリティ検査）・project.md の Mandated | SpotBugs の関門・Gitleaks・OSV-Scanner の High 以上を除外を足さずに通す | SpotBugs 通過（priority 2・3 の警告だけ）、Gitleaks no leaks、osvScan 失敗の条件 0・警告 16（npm の開発用） | verify・`osvScan --rerun-tasks`（b126bdc）、spotbugs-exclude.xml・.gitleaks.toml に差なし | build-and-test | Met |
| E2E-ALL | team.md の Testing Posture（E2E は画面・認証の変更の統合の前とリリースの前） | 既存と新しい E2E がすべて通る | 13 ファイル・152 件 expected | e2e の json の stats | build-and-test | Met |
| U3-PLAN4.6 | construction/u3-user-admin-api/code-generation/code-generation-plan.md 4.6（Q-C） | 手元の verify が B3 の基準から +5 分以内、CI が 30 分以内 | 6 分 37 秒 → 10 分 27 秒（+3 分 50 秒）、CI は最大 12 分 29 秒 | verify・CI の run | build-and-test | Met |
| IDR-R02 | construction/infrastructure-design/gate-decisions.md（U5 R-02・U4 の申し送り） | E2E の後は json から記録してから報告を消し、消したことと共有していないことを記録する | E2E を流した B1・B2・B4・B5 の関門で記録してから消した（B3 は E2E を流していない）。B5 は test-results/ のファイル 2・ディレクトリ 106、html は作られない。この段は E2E を流していない | 各単位の code-summary.md・generation-notes.md、test-results.md 3.2 | build-and-test | Met |
| U1-NFR1.1 | construction/u1-user-suspension/nfr-requirements/security-requirements.md | 3つの入口のすべてでサーバー側で停止を判定し拒否（ログインはロックの判定より前） | 入口ごとの拒否（ログインは正しい・誤った・ロック中）が通過 | verify・SuspendedUserAuthenticationIT・LoginServiceTest | build-and-test | Met |
| U1-NFR1.2 | 同上 | 停止を解いた直後から3つの入口で受け付ける | 通過 | verify・SuspendedUserAuthenticationIT | build-and-test | Met |
| U1-NFR1.3 | 同上 | 止めるときにリフレッシュトークンをまとめて無効にし戻さない、アクセストークンは停止中は拒否・解いた後は有効期限まで（時計で境界） | 通過 | verify・SuspendedUserAuthenticationIT・UserAdminOperationsApiIT#suspendRevokesRefreshTokens | build-and-test | Met |
| U1-NFR1.4 | 同上 | 利用者自身の API の本文で停止を変えられない | 通過 | verify・MePasswordApiIT・MePreferencesApiIT | build-and-test | Met |
| U1-NFR1.5 | 同上 | 止める確定と同時に作られたトークンの隙は受け入れ、停止の間は次の要求で拒否 | 拒否の部分は通過。隙は承認済みの決定として記録 | verify・SuspendedUserAuthenticationIT、security-requirements.md の R1 | build-and-test | Met |
| U1-NFR2.1 | 同上 | 停止中の拒否はほかの失敗と同じ状態コード・code・本文 | 通過 | verify・SuspendedUserAuthenticationIT | build-and-test | Met |
| U1-NFR2.2 | 同上 | 停止中のログインはパスワードの誤りと同じ照合と読み書きの回数 | 通過（CountingPasswordEncoder・SqlStatementCounter） | verify・LoginServiceTest・LoginApiIT | build-and-test | Met |
| U1-NFR2.3 | 同上 | `USER_SUSPENDED` を応答とアクセスの拒否の監査に出さない、停止中の管理者は業務に届かない | 通過 | verify・AccessDeniedReasonTest・AccessDeniedEventsIT・UserAdminOperationsApiIT#authorization | build-and-test | Met |
| U1-NFR3.1 | 同上 | 停止中の3つの入口のログ・監査・応答にメールアドレス・パスワード・トークンを出さない | 通過（起動から最後の要求までの出力に利用者と初期管理者のメールアドレスが無い） | verify・AuthSuspensionSecretLeakIT | build-and-test | Met |
| U1-NFR3.2 | 同上 | C1 の口は ID・真偽・件数だけ、無効化のログは DEBUG で ID と件数だけ | 通過 | verify・RefreshTokenRevocationServiceTest | build-and-test | Met |
| U1-NFR5.1a | 同上 | 停止の判定で3つの入口の問い合わせを増やさない | 通過（SqlStatementCounter） | verify・SuspendedUserAuthenticationIT | build-and-test | Met |
| U1-NFR5.1b | 同上 | ログインと更新の既存の目標（同時 10 件で p95 1 秒）を変えない | 未測定（既存の k6 の `login`・`refresh`） | performance-test-instructions.md 2節 | performance-validation | Unverified |
| U1-NFR5.2 | 同上 | まとめての無効化を含む止める操作が、未無効 100 件・無効 1,000 件の悪い側で p95 1 秒以内 | 未測定。台本 `userAdminSuspendWorst` は読み込みを確かめた | test-results.md 5節 | performance-validation | Unverified |
| U1-NFR5.3 | 同上 | まとめての無効化は索引で引く1回の更新、未無効の行だけを書き換え、0 件でも成功 | 通過（件数だけで確かめる） | verify・RefreshTokenRepositoryIT・RefreshTokenRevocationServiceIT | build-and-test | Met |
| U1-NFR5.4 | 同上 | 想定の規模（利用者 50 名・同時 10 件、未無効の行 約 80 件）を1台で処理 | 未測定 | performance-test-instructions.md 3節 | performance-validation | Unverified |
| U1-NFR5.5 | 同上 | 新しい指標と警報を足さない | `docker/monitoring/` に Intent の間の差なし | `git diff --stat ded2653~1 7689ade -- docker/monitoring`（差なし） | build-and-test | Met |
| U1-NFR6.1a | 同上 | 停止中のログインの監査はパスワードの誤りと同じ経路（確定の後の2本目）で残る | 通過 | verify・AuditAuthenticationEventsIT | build-and-test | Met |
| U1-NFR6.1b | 同上 | 接続プールの使い方（上限 30）が変わらないことを既存のログインの場面の負荷で確かめる | 未測定 | performance-test-instructions.md 2節 | performance-validation | Unverified |
| U1-NFR6.2 | 同上 | 監査の書き込みに失敗しても停止中のログインの応答と判定を変えない | 通過 | verify・AuditWriteFailureIT | build-and-test | Met |
| U1-NFR9.1 | 同上 | team.md の利用停止の必須テストと認証の失敗のテストを書く | 通過 | verify・security-test-instructions.md 2節 | build-and-test | Met |
| U1-NFR9.2 | 同上 | 停止中の利用者への招待は登録済みの拒否（AC3.2.6）、書いた後の読み取りが書いた値を返す | 通過 | verify・InvitationAdminApiIT#suspendedUserEmailIsRegistered・UserSuspensionIT | build-and-test | Met |
| U1-NFR9.3 | 同上 | 時刻の判定は注入した時計、sleep と実時刻に頼らない | 通過。テストは注入した時計で動かす | verify・RefreshTokenRevocationService.java・SuspendedUserAuthenticationIT | build-and-test | Met |
| U1-NFR9.4 | 同上 | 静的解析・秘密情報・脆弱性の関門を除外なしで通し、問い合わせは名前つきの引数 | 通過 | verify・UserRepository.java・SEC-1 | build-and-test | Met |
| U1-NFR9.5 | construction/u1-user-suspension/nfr-requirements/tech-stack-decisions.md | 手を入れた一覧のパッケージを外し、パッケージごとの下限を満たす | `auth.domain` 98.2%・100.0%、`auth.repository` 100.0%・100.0%、`access.domain` 100.0%・100.0% | verify・backend/build.gradle.kts | build-and-test | Met |
| U1-NFR9.6 | 同上 | 回数の確かめは既存の部品、説明文は英語、XxxTest・XxxIT、予約のドメイン | 通過 | verify・LoginApiIT | build-and-test | Met |
| U1-NFR10.1 | construction/u1-user-suspension/nfr-requirements/security-requirements.md | V9 だけを前進のみで足し、既存の利用者は有効になる | 起動時の validate-on-migrate・ddl-auto: validate とエンティティの読み書きは通過。既存の利用者のデータでの起動と利用は未確かめ（依頼者の決定 Q1 B・Q4 A） | verify・UserSchemaIT、U1 の code-summary.md 7節 | deployment-execution | Unverified |
| U1-NFR10.2 | 同上 | 1つ前の版のアプリが V9 の後の内部DB で動く | 自動のテストと戻しの練習は置かない決定（Q4 A）。未確かめ | U1 の code-summary.md 7節・11節 | deployment-pipeline・deployment-execution | Unverified |
| U1-NFR10.3 | 同上 | 戻している間は停止が効かない制約を受け入れ、戻す前に停止中の利用者を確かめる手順を戻しの手順に書く | README の戻しの節に V9 の注意を書いた。手順そのものは未作成 | README.md、infrastructure-design/gate-decisions.md | deployment-pipeline | Unverified |
| U1-NFR11.1 | 同上 | `user` は `auth` を知らない、既存の境界テストを緩めない | 通過（既存の境界テストは変えていない） | verify・ArchitectureTest | build-and-test | Met |
| U1-NFR11.2 | 同上 | C1 の口は MANDATORY、巻き戻しで両方が戻る、存在しない ID は想定外の誤り | 通過 | verify・UserSuspensionIT | build-and-test | Met |
| U2-NFR3.1 | construction/u2-shared-paging/nfr-requirements/security-requirements.md | Paging・UiPaging の口は整数と列挙だけ（parsePage の文字列は受け入れた危険） | コードのレビューで確かめた | Paging.java、U2 の code-summary.md 4節 | build-and-test | Met |
| U2-NFR5.1a | 同上 | 新しい性能の目標を足さず、Paging が問い合わせを増やさない | 確かめた | InvitationService.java、U2 の code-summary.md 4節 | build-and-test | Met |
| U2-NFR5.1b | 同上 | 招待の一覧の既存の目標（同時 10 件で p95 1 秒）を保つ | 未測定（既存の k6 の `invitationList`） | performance-test-instructions.md 2節 | performance-validation | Unverified |
| U2-NFR5.2 | 同上 | 新しい指標・警報・ログを足さない | `docker/monitoring/` に差なし | U1-NFR5.5 と同じ | build-and-test | Met |
| U2-NFR9.1 | 同上 | page はサーバーの parsePage だけで検証し、誤りは 400 | 通過（jqwik の性質を含む） | verify・PagingTest | build-and-test | Met |
| U2-NFR9.2 | 同上 | offsetOf は long で桁あふれしない | 通過 | verify・PagingTest | build-and-test | Met |
| U2-NFR9.3 | 同上 | page の誤りは既存の `VALIDATION_FAILED`、例外の文を載せない | 通過 | verify・InvitationAdminApiIT | build-and-test | Met |
| U2-NFR9.4 | 同上 | 静的解析・画面のセキュリティ系のルール・秘密情報・脆弱性の関門を除外なしで通す | 通過 | verify・SEC-1 | build-and-test | Met |
| U2-NFR9.5 | 同上 | 招待の振る舞いを変えず、既存のテストが参照先の変更だけで通る | 通過 | verify・InvitationAdminApiIT | build-and-test | Met |
| U2-NFR9.6 | construction/u2-shared-paging/nfr-requirements/tech-stack-decisions.md | jqwik は tries 500、fast-check は既定の 100 回 | 通過 | verify・PagingTest・paging.test.ts | build-and-test | Met |
| U2-NFR9.7 | 同上 | 失敗時の乱数の種を記録して再現できる | 既存の仕組みのまま（失敗の報告に seed が出る設定） | PagingTest、backend/build.gradle.kts | build-and-test | Met |
| U2-NFR9.8 | 同上 | `invitation.domain` が `InvitationPaging` が抜けた後も下限を満たす | 98.3%（233/237）・96.4%（107/111） | B2 の関門の verify | build-and-test | Met |
| U2-NFR9.9 | 同上 | `common.paging` がパッケージごとの下限を満たす、一覧を変えない | 100.0%・100.0% | B2 の関門の verify | build-and-test | Met |
| U2-NFR9.10 | 同上 | 画面のカバレッジは全体の合計で判定し、移したファイルを外さない | 画面の全体 97.42%・92.85% | verify・frontend/vitest.config.ts | build-and-test | Met |
| U2-NFR9.11 | 同上 | 新しい依存を足さない | 依存の定義と lockfile に差なし | gradle/libs.versions.toml・frontend/package.json（`git diff --stat`） | build-and-test | Met |
| U2-NFR11.1 | construction/u2-shared-paging/nfr-requirements/security-requirements.md | `common.paging` はどの機能にも依存しない、既存の境界テストを変えない | 通過 | verify・InvitationBoundaryArchitectureTest・ArchitectureTest | build-and-test | Met |
| U3-NFR1.1 | construction/u3-user-admin-api/nfr-requirements/security-requirements.md | 7つの API の 401・403・200（204）をサーバー側で判定 | 通過 | verify・UserAdminListApiIT#authorization・UserAdminOperationsApiIT#authorization・UserAdminProfileApiIT | build-and-test | Met |
| U3-NFR1.2 | 同上 | 印の付け外しの直後の次の要求で 403／200 が切り替わる、自分の印を外すのは 409 | 通過 | verify・UserAdminOperationsApiIT#flagChangeTakesEffectOnTheNextRequest | build-and-test | Met |
| U3-NFR1.3 | 同上 | 停止中の管理者は7つの API のどれも 401、監査が残らない | 通過 | verify・UserAdminOperationsApiIT#authorization・UserAdminListApiIT#authorization | build-and-test | Met |
| U3-NFR1.4 | 同上 | 書き換えの前に操作した人を確かめ直し、外れていれば 403 と NOT_ADMIN の監査 | 通過 | verify・UserAdminConcurrencyIT・UserAdminAuditIT | build-and-test | Met |
| U3-NFR1.5 | 同上 | 本文の値で印・停止・失敗回数を変えられない | 通過 | verify・UserAdminMassAssignmentIT | build-and-test | Met |
| U3-NFR3.1 | 同上 | 検索の文字・メールアドレス・氏名は controller から repository の口まで伏せ字の型 | 通過（TRACE でも値が出ない） | verify・UserAdminSecretLeakIT | build-and-test | Met |
| U3-NFR3.2 | 同上 | 応答にハッシュ値・トークン・失敗回数・ロックの判定の内部の値を含めない | 通過（行は11項目だけ） | verify・UserAdminListApiIT | build-and-test | Met |
| U3-NFR3.3 | 同上 | エラー応答は項目の名前・理由・code だけ | 通過 | verify・UserAdminBusyApiIT・UserAdminListApiIT | build-and-test | Met |
| U3-NFR3.4 | construction/u3-user-admin-api/nfr-requirements/observability-requirements.md | アプリのログは既存の決まり（構造化・トレース ID・4xx は WARN 以下）で値を出さない | 通過 | verify・UserAdminSecretLeakIT | build-and-test | Met |
| U3-NFR4.1 | construction/u3-user-admin-api/nfr-requirements/reliability-requirements.md | 最後の有効な管理者を無くす操作は 409 で状態を変えず、同時に互いに外しても 0 人にならない | 通過（待ち合わせで重なりを作る） | verify・UserAdminConcurrencyIT | build-and-test | Met |
| U3-NFR4.2 | 同上 | 失敗回数を戻す操作とログインの重なりは順に行ったどちらかの結果、5xx にならない | 通過 | verify・ResetLoginConcurrencyIT | build-and-test | Met |
| U3-NFR4.3 | 同上 | 行き詰まりを作らない、上限切れは 409 `USER_ADMIN_BUSY` で巻き戻る | 通過 | verify・UserRowLockRepositoryIT・UserAdminBusyApiIT | build-and-test | Met |
| U3-NFR4.4 | 同上 | 排他の待ちの上限 3000 ミリ秒のまま、待ち合わせの止める時間はそれより短い | 3000 ミリ秒の定数、待ち合わせは 2000 ミリ秒 | U3 の generation-notes.md の Step 40・UserAdminBusyApiIT | build-and-test | Met |
| U3-NFR4.5 | 同上 | BR3.1 の根拠が H2 と Hibernate の上で成り立つ | NFR 設計の試しのコードと結合テストで確かめた | verify・UserAdminConcurrencyIT、U3 の nfr-design/reliability-design.md | build-and-test | Met |
| U3-NFR5.1 | construction/u3-user-admin-api/nfr-requirements/performance-requirements.md | 一覧（利用者 1,000 名、a〜d）が同時 10 件で p95 1 秒以内 | 未測定。台本 `userAdminList` は読み込みを確かめた | test-results.md 5節 | performance-validation | Unverified |
| U3-NFR5.2 | 同上 | 一覧の問い合わせは3回までで行の数で増えない | 通過 | verify・UserAdminListQueryCountIT | build-and-test | Met |
| U3-NFR5.3 | 同上 | 氏名と言語の変更が同時 10 件で p95 1 秒以内 | 未測定（`userAdminProfile`） | test-results.md 5節 | performance-validation | Unverified |
| U3-NFR5.4 | 同上 | 5つの操作が操作ごとに p95 1 秒以内、204 の率 1 | 未測定（`userAdminOps`） | test-results.md 5節 | performance-validation | Unverified |
| U3-NFR5.5 | 同上 | 止める操作の悪い側（未無効 100・無効 1,000）で p95 1 秒以内 | 未測定（`userAdminSuspendWorst`） | test-results.md 5節 | performance-validation | Unverified |
| U3-NFR5.6 | 同上 | 目標の負荷で BUSY を例外にせず p95 に混ぜない（上限切れが 409 で返ることは結合テスト） | 409 で返ることは通過。負荷の下の件数は未測定 | verify・UserAdminBusyApiIT | performance-validation | Unverified |
| U3-NFR5.7 | 同上 | 台本と手順書を足し、`k6 inspect` で読み込みと場面の名前を確かめる | 5つの場面が読み込めた（B4 の Step 39、`perf/` はその後変わっていない） | perf/k6/scenarios.js・perf/README.md、`git log 4bd600d..7689ade -- perf/` | build-and-test | Met |
| U3-NFR5.8 | construction/u3-user-admin-api/nfr-requirements/scalability-requirements.md | 想定の規模を1台で処理し、1,000 名で目標を満たす | 未測定 | performance-test-instructions.md 3節 | performance-validation | Unverified |
| U3-NFR5.9 | construction/u3-user-admin-api/nfr-requirements/observability-requirements.md | 7つの API の時間と状態コードが既存の HTTP の指標で取れる（uri・le の値） | 未確かめ | performance-test-instructions.md 3節 | observability-setup | Unverified |
| U3-NFR5.10 | 同上 | 新しい指標・警報を足さず、既存の警報が拾う範囲を確かめる | 足していない（差なし）。拾うことは未確かめ | `git diff --stat`（docker/monitoring 差なし） | observability-setup | Unverified |
| U3-NFR5.11 | 同上 | SLO は決めず、基準の値を記録する | 未記録 | performance-test-instructions.md 3節 | observability-setup・performance-validation | Unverified |
| U3-NFR6.1 | construction/u3-user-admin-api/nfr-requirements/scalability-requirements.md | 接続の数の見積もり（5つの操作は1件に2本）を記録する | README の既知の制約に記録。見積もりの確かめは U3-NFR6.3 | README.md | build-and-test | Met |
| U3-NFR6.2 | construction/u3-user-admin-api/nfr-requirements/reliability-requirements.md | 5つの操作と一覧を同時 10 件で流してもプールが尽きず監査が欠けない | 未測定（`userAdminPool`） | test-results.md 5節 | performance-validation | Unverified |
| U3-NFR6.3 | 同上 | 上限 10 の (A) 同時 5・(B) 同時 10 で「1件に2本」の見積もりを確かめる | 未測定 | performance-test-instructions.md 2節 | performance-validation | Unverified |
| U3-NFR8.1 | construction/u3-user-admin-api/nfr-requirements/security-requirements.md | 足す code に ja・en の説明文、Accept-Language で選ぶ | 通過 | verify・UserAdminProblemTypesTest・UserAdminOperationsApiIT#unknownAndMalformedUsers | build-and-test | Met |
| U3-NFR9.1 | 同上 | q は 254 コードポイントまで、`%`・`_`・`\` は文字どおり、page・userId の検証 | 通過 | verify・UserAdminListApiIT | build-and-test | Met |
| U3-NFR9.2 | 同上 | 静的解析・秘密情報・脆弱性の関門を除外なしで通す | 通過 | verify・SEC-1 | build-and-test | Met |
| U3-NFR9.3 | construction/u3-user-admin-api/nfr-requirements/observability-requirements.md | 5つの操作は判定に届いた要求ごとに監査の行を1件、必須の項目つき | 通過 | verify・UserAdminAuditIT | build-and-test | Met |
| U3-NFR9.4 | construction/u3-user-admin-api/nfr-requirements/reliability-requirements.md | 監査の書き込みの失敗で応答と判定を変えない、ERROR に値を含めない | 通過（`b126bdc` で組み立ての失敗のログの値もそろえた） | verify・UserAdminAuditWriteFailureIT・AuditEventListenerTest | build-and-test | Met |
| U3-NFR9.5 | 同上 | 1つのトランザクションで確定するか何も変えない（止める操作は停止とトークンを一緒に） | 通過 | verify・UserAdminOperationsIT | build-and-test | Met |
| U3-NFR9.6 | construction/u3-user-admin-api/nfr-requirements/tech-stack-decisions.md | 新しいパッケージと手を入れた一覧のパッケージがパッケージごとの下限を満たす | すべて満たす（test-results.md 2.1） | verify | build-and-test | Met |
| U3-NFR9.7 | 同上 | team.md の必須テスト（印の変更・ロックの解除・最後の管理者・認可・改ざん・監査・漏えい） | 通過 | verify・security-test-instructions.md 2節 | build-and-test | Met |
| U3-NFR9.8 | 同上 | 拒否の判定とロックの判定に jqwik、種を記録 | 通過 | verify・RejectionPolicyTest・LockViewTest | build-and-test | Met |
| U3-NFR10.1 | construction/u3-user-admin-api/nfr-requirements/performance-requirements.md | 表と列を変えず、監査の種類と理由は既存の列に 32 文字以内で入れる | 通過 | verify・AuditEventTest | build-and-test | Met |
| U3-NFR10.2 | 同上 | 索引か列を足すと決めたときは V10 以降・前進のみ・後方互換 | 足していない（`backend/src/main/resources/db` の差は U1 の V9 だけ）。条件に当たらない | `git diff --stat ded2653~1 7689ade -- backend/src/main/resources/db` | build-and-test | Met |
| U3-NFR11.1 | construction/u3-user-admin-api/nfr-requirements/tech-stack-decisions.md | `useradmin` は `user`・`auth` の口だけ、`access.domain` への依存は1本 | 通過 | verify・UserAdminBoundaryArchitectureTest | build-and-test | Met |
| U3-NFR11.2 | 同上 | 書き換えの口を呼ぶのは `useradmin.service` だけ | 通過 | verify・UserAdminBoundaryArchitectureTest | build-and-test | Met |
| U4-NFR1.1 | construction/u4-admin-forbidden-ui/nfr-requirements/security-requirements.md | S6 とメニューを隠すことは表示だけでサーバーの判定の代わりにしない | 画面のテスト通過。サーバー側は U3 | B5 の関門の画面のテスト・AppRouter.test.tsx・U3-NFR1.1 | build-and-test | Met |
| U4-NFR1.2 | 同上 | 「権限が無い」は `/api/admin/`・403・`ACCESS_DENIED` の3つがそろうときだけ | 通過（fast-check） | adminForbidden.test.ts | build-and-test | Met |
| U4-NFR1.3 | 同上 | 画面の印が管理者でないときはサーバーに問わず S6、管理の API を呼ばない | 通過 | AppRouter.test.tsx | build-and-test | Met |
| U4-NFR1.4 | 同上 | 401 の扱いとログアウトは変えない | 通過 | ShellLayout.test.tsx | build-and-test | Met |
| U4-NFR1.5 | 同上 | 画面の印はトークンの応答からだけ入れる | 通過 | DisplaySettingsProvider.test.tsx | build-and-test | Met |
| U4-NFR3.1 | 同上 | S6 に原因・detail・利用者の値を出さない、ブラウザの保存に値を出さない | 通過 | AdminForbiddenProvider.test.tsx | build-and-test | Met |
| U4-NFR3.2 | 同上 | 130 の報告に資格情報と2語の氏名を入れない | 報告の部品の見つかった件数 0 | e2e・playwright-secret-check-reporter.ts | build-and-test | Met |
| U4-NFR5.1a | construction/u4-admin-forbidden-ui/nfr-requirements/performance-requirements.md | 読み直しは既存の更新の API を1回呼ぶだけで新しい経路を作らない | 通過 | apiClient.test.ts | build-and-test | Met |
| U4-NFR5.1b | 同上 | 更新の API の既存の目標（p95 1 秒）を当てる | 未測定（U1 の NFR5 の場面に含む） | performance-test-instructions.md 2節 | performance-validation | Unverified |
| U4-NFR7.1 | construction/u4-admin-forbidden-ui/nfr-requirements/tech-stack-decisions.md | WCAG 2.1 AA（S6 の role="status"、見出しへのフォーカス、作り直さない） | 通過。130 の axe 違反 0 | AdminForbiddenView.test.tsx・AppRouter.test.tsx・e2e の 130 | build-and-test | Met |
| U4-NFR7.2 | 同上 | `AdminForbiddenView` の vitest-axe 違反 0 | 通過 | AdminForbiddenView.test.tsx | build-and-test | Met |
| U4-NFR7.3 | 同上 | 130 の 20 組で axe の違反 0・はみ出し無し | 20 件 expected（B2 の関門の内訳: 違反 0・はみ出し無し・CSP の違反 0） | e2e の 130、U4 の generation-notes.md | build-and-test | Met |
| U4-NFR8.1 | 同上 | S6 の文言の ja・en、見出しはサイドバーの項目の名前 | 通過 | messages.test.ts・forbiddenHeading.test.ts | build-and-test | Met |
| U4-NFR8.2 | 同上 | 自分の言語の反映は ja・en だけ | 通過 | DisplaySettingsProvider.test.tsx | build-and-test | Met |
| U4-NFR9.1 | construction/u4-admin-forbidden-ui/nfr-requirements/performance-requirements.md | 403 を受けたら同じ描画で S6 に置き換え、決まった間隔の処理を持たない | 通過 | AdminForbiddenProvider.test.tsx | build-and-test | Met |
| U4-NFR9.2 | 同上 | 読み直しは同じ URL の 403 を重ねても1回 | 通過 | AdminForbiddenProvider.test.tsx・apiClient.test.ts | build-and-test | Met |
| U4-NFR9.3 | 同上 | S6 の画面の時間に数値の目標を置かない | 置いていない（決定の記録） | 130 のソース | build-and-test | Met |
| U4-NFR9.4 | 同上 | 初回の JavaScript は gzip で 500KB 以内を目安 | 125.5 KB | B5 の関門の verify の frontendBundleSize | build-and-test | Met |
| U4-NFR9.5 | construction/u4-admin-forbidden-ui/nfr-requirements/security-requirements.md | セキュリティ系のリンタ・型検査を通し HTML を直接埋め込まない、CSP の違反 0 | 通過 | verify の verifyLint・e2e の 130 | build-and-test | Met |
| U4-NFR9.6 | construction/u4-admin-forbidden-ui/nfr-requirements/tech-stack-decisions.md | 新しい依存を足さない | package.json・package-lock.json に差なし | `git diff --stat` | build-and-test | Met |
| U4-NFR9.7 | 同上 | make-you-chic-ui の中身を変えず、U4 では固定先を動かさない | 通過（固定先の更新は U5 の専用のコミット） | vendorUnchanged・git submodule status | build-and-test | Met |
| U4-NFR9.8 | 同上 | 画面のカバレッジの下限を保ち、除外を増やさない | 97.42%・92.85% | verify・frontend/vitest.config.ts | build-and-test | Met |
| U4-NFR9.9 | 同上 | `isAdminForbidden`・`forbiddenHeadingKey` に fast-check、種を記録 | 通過 | adminForbidden.test.ts・forbiddenHeading.test.ts | build-and-test | Met |
| U4-NFR9.10 | 同上 | 流れの E2E を足さず、既存の E2E が通る | 通過（090 の期待は U4 の決まりに合わせて直した、N-16） | e2e | build-and-test | Met |
| U4-NFR9.11 | 同上 | 画面のテストの書き方（英語、waitFor、上限を延ばさない） | 通過 | AdminAreaPage.test.tsx ほか | build-and-test | Met |
| U4-NFR9.12 | 同上 | 画面の側の境界（機能どうしで読み合わない、骨組みは auth を読まない） | 確かめた | adminForbidden.ts、U4 の code-summary.md 5節 | build-and-test | Met |
| U5-NFR1.1 | construction/u5-user-admin-ui/nfr-requirements/security-requirements.md | 押せない形とメニューの出し分けは表示だけ | 画面のテスト通過。サーバー側は U3 | rowActions.test.ts・U3-NFR1.1 | build-and-test | Met |
| U5-NFR1.2 | 同上 | 403 は U4 の共通の扱いに渡して画面は何も出さない、401 は ApiClient に任せる | 通過 | UserAdminPage.test.tsx | build-and-test | Met |
| U5-NFR3.1 | 同上 | 応答の値と検索の文字をコンソール・保存・URL・履歴に出さない | 通過 | UserAdminPage.test.tsx | build-and-test | Met |
| U5-NFR3.2 | 同上 | 失敗の文言は code から選び detail・title を出さない、氏名はタグとして描かない | 通過 | failureMessage.test.ts・UserTable.test.tsx | build-and-test | Met |
| U5-NFR3.3 | 同上 | 決めた項目だけを読み、知らない項目を捨てる | 通過 | userAdminApi.test.ts | build-and-test | Met |
| U5-NFR3.4 | 同上 | E2E の報告に資格情報と 110 の U の値を残さない | 報告の部品の見つかった件数 0（値の種類 7、添付 299） | e2e・playwright-secret-check-reporter.ts | build-and-test | Met |
| U5-NFR3.5 | 同上 | 120 は `/api/admin/` の下の GET 以外を本物へ通さない | どの組も blocked 0・routeSeen 9。わざと失敗させる3つの形も期待どおり | e2e の 120・adminApiRoute.ts、U5 の generation-notes.md Step 18 | build-and-test | Met |
| U5-NFR5.1 | construction/u5-user-admin-ui/nfr-requirements/performance-requirements.md | 一覧の1ページ目まで 2 秒以内（5回すべて） | E2E の WAR で最大 103 ms（記録のみ、口の上乗せを含む）。本番での判定は未確かめ | e2e の 120 の user-admin-screen-ms | performance-validation・observability-setup・feedback-optimization | Unverified |
| U5-NFR5.2 | 同上 | 2ページ目まで 1.5 秒以内（5回すべて） | 最大 80 ms（記録のみ、API の時間を含まない）。本番での判定は未確かめ | 同上 | performance-validation・observability-setup・feedback-optimization | Unverified |
| U5-NFR5.3 | 同上 | API の時間は U3 の目標のまま U5 では測り直さず、送る要求が C3 のとおり | 要求の形は通過。API の時間は U3-NFR5.x | userAdminApi.test.ts | build-and-test | Met |
| U5-NFR5.4 | 同上 | サーバーの順で 20 件まで、自動の読み直しをしない、最後の読み直しだけを使う | 通過（偽の時計） | UserAdminPage.test.tsx | build-and-test | Met |
| U5-NFR5.5 | 同上 | 独自の時間切れを置かず 5 秒で「時間がかかっています」、二重の送信を防ぐ | 通過（偽の時計） | UserAdminPage.test.tsx ほか | build-and-test | Met |
| U5-NFR5.6 | 同上 | 初回の JavaScript は 500KB 以内を目安、前後を記録 | (1) 122.7 KB・(2) 122.8 KB・(3) 125.5 KB | B5 の関門の verify の frontendBundleSize | build-and-test | Met |
| U5-NFR7.1 | construction/u5-user-admin-ui/nfr-requirements/tech-stack-decisions.md | WCAG 2.1 AA（Badge の文字、aria-disabled と理由、確かめの表示のはじめのフォーカス、フォーカスの戻し先は W12） | 画面のテスト（jsdom）は通過。実際のブラウザでは S3・S4 を閉じた後のフォーカスが行の「操作」に戻らず body に移る（N-19。make-you-chic-ui の `useFocusTrap` の問題） | UserRowActions.test.tsx・ConfirmActionDialog.test.tsx、U5 の generation-notes.md「レビューの後」、gate-decisions.md 4節 | 後の Intent（N-19 の持ち越し、承認済み） | Not Met |
| U5-NFR7.2 | 同上 | 画面部品ごとに vitest-axe の違反 0 | 通過 | UserAdminPage.test.tsx ほか6つ | build-and-test | Met |
| U5-NFR7.3 | 同上 | 120 の 20 組で axe の違反 0・はみ出し無し | 240 回の検査で違反 0・はみ出し 0 | e2e の 120 | build-and-test | Met |
| U5-NFR8.1 | 同上 | 文言の ja・en、鍵がそろう | 通過 | registration.test.tsx・UserAdminPage.test.tsx | build-and-test | Met |
| U5-NFR8.2 | 同上 | Table・Modal の文言を画面の言語で渡す | 通過 | UserTable.test.tsx・ConfirmActionDialog.test.tsx | build-and-test | Met |
| U5-NFR8.3 | 同上 | 自分の言語を en にした直後の Toast は en、日時は時間帯と言語で | 通過 | UserAdminPage.test.tsx・lockedUntil.test.ts | build-and-test | Met |
| U5-NFR9.1 | construction/u5-user-admin-ui/nfr-requirements/security-requirements.md | セキュリティ系のリンタと型検査、CSP の違反 0 | 通過 | verify の verifyLint・e2e の 120 | build-and-test | Met |
| U5-NFR9.2 | construction/u5-user-admin-ui/nfr-requirements/tech-stack-decisions.md | 新しい npm の依存を足さない | package.json・package-lock.json に差なし | `git diff --stat` | build-and-test | Met |
| U5-NFR9.3 | 同上 | 固定先を `3481488` 以降へ専用のコミットで上げ、既存の画面と E2E が通る | `3d9521a`（C2 `364e9d6`、`3481488` を祖先に含む）、E2E 152 件 expected | git submodule status・e2e | build-and-test | Met |
| U5-NFR9.4 | 同上 | `frontend/.npmrc` に `ignore-scripts=true` | 入れた（C1 `17af97d`） | frontend/.npmrc | build-and-test | Met |
| U5-NFR9.5 | 同上 | 画面のカバレッジの下限を守り、除外を増やさない | 97.42%・92.85% | verify・frontend/vitest.config.ts | build-and-test | Met |
| U5-NFR9.6 | 同上 | `rowActions.ts`・`searchInput.ts` に fast-check | 通過 | rowActions.test.ts・searchInput.test.ts | build-and-test | Met |
| U5-NFR9.7 | 同上 | waitFor、上限を延ばさない、実時刻に頼らない | 通過 | verify の画面のテスト・fieldErrors.test.ts | build-and-test | Met |
| U5-NFR9.8 | 同上 | 代表の流れ 110 を1本だけ足し、飛ばした注記が無い、既存の E2E が通る | 110 expected、`skip-reason` なし、152 件 expected | e2e の 110 | build-and-test | Met |
| U5-NFR9.9 | 同上 | 差し替えの見本は1つで型を付け、本物の応答と一致 | 110 で一覧・409・400 の本物の応答と見本が一致 | e2e の 110・userAdminFixtures.ts | build-and-test | Met |

### 集計

| 区分 | 件数 | Met | Not Met | Unverified |
|---|---|---|---|---|
| 共通（TC・CI・SEC・E2E・計画・基盤の決定） | 12 | 12 | 0 | 0 |
| U1 | 30 | 23 | 0 | 7 |
| U2 | 16 | 15 | 0 | 1 |
| U3 | 41 | 30 | 0 | 11 |
| U4 | 26 | 25 | 0 | 1 |
| U5 | 28 | 25 | 1 | 2 |
| 合計 | 153 | 130 | 1 | 22 |

- Unverified の持ち主の内訳: performance-validation 16 件（うち U5-NFR5.1・NFR5.2 の2件は observability-setup・feedback-optimization と共同、U3-NFR5.11 は observability-setup と共同で下に数える）、observability-setup 3 件、deployment-pipeline・deployment-execution 3 件。
- Not Met の 1 件（U5-NFR7.1）は、コード生成の承認の場で後の Intent への持ち越しとして承認済みです（`construction/code-generation/gate-decisions.md` の 2節・4節）。

### 判定に迷ったもの

- **U5-NFR7.1（Not Met）**: 目標は WCAG 2.1 AA で、フォーカスの戻し先を W12（閉じた後は開いた行の「操作」）と決めている。画面のテスト（jsdom）は通るが、実際のブラウザでは make-you-chic-ui の `useFocusTrap` が背景に `inert` が付いたまま前の要素へ戻そうとし、フォーカスが body に移る（N-19）。E2E は `inert` が外れるのを待つ形のため N-19 を見ていない（U5 のレビューの R-02）。目標を緩めず Not Met とした。持ち越しの中身と直した後に流す手順は `gate-decisions.md` の4節と U5 の `generation-notes.md` の「C2′ の後に流し直すもの」。
- **U5-NFR7.3（Met）**: 120 は axe の違反 0・はみ出し 0 を確かめる検査で、閉じた後のフォーカスは目標に含まないため Met とした。
- **U1-NFR5.1・NFR6.1、U2-NFR5.1、U4-NFR5.1（a・b に分けた）**: 要件の中に、この段で確かめた部分（問い合わせの回数・経路・新しい経路を作らないこと）と、負荷の試験が持つ部分（既存の p95・接続プール）が混ざるため分けた。
- **U3-NFR10.2（Met）**: 条件つきの要件（索引か列を足すと決めたとき）で、足していないことを差分で確かめた。code-generation の `traceability.json` は N/A としているが、この表では N/A を使わず、条件に当たらないことを確かめた結果として Met とした。
- **U3-NFR6.1（Met）**: 見積もりを記録する要件として、README の既知の制約の記録で Met とした。見積もりそのものの確かめは U3-NFR6.3（Unverified）で扱う。
- **U3-NFR5.7（Met）**: この段では `k6 inspect` を流し直していない。`perf/` が B4 の後に変わっていないため、B4 の Step 39 の結果を使った。
- **U1-NFR10.3（Unverified）**: README の戻しの節に V9 の注意は書いたが、要件が求める「戻す前に停止中の利用者を確かめる手順」はまだ無い（deployment-pipeline で決める）。
- **U4-NFR7.3（Met）**: この段の E2E（B5 の関門）は 130 の 20 件が expected であることだけを写した。組ごとの違反の件数と規則の名前は B2 の関門の内訳（違反 0）を出典とした。
- **TC-3・U1-NFR9.5・U3-NFR9.6 のパッケージの値**: `b126bdc` で変わった `audit.*` のほかは、バックエンドのソースが同じ B4 の流し直した関門の値を写した（`test-results.md` の 2.1）。

## 4. 準備の判断

| 観点 | 判断 |
|---|---|
| build-ready | はい（`b126bdc` の verify が成功。`develop` の `7689ade` とアプリのソースが同じ） |
| test-ready | はい（手元のテスト・E2E がすべて通過。k6 の場面を用意し、読み込みを確かめた） |
| deployment-ready | **条件つき**。理由は次の2つです（承認の後の push の CI、run 37106195579 は success） |

- Not Met の目標が1件あります（U5-NFR7.1、N-19）。後の Intent への持ち越しとして承認済みですが、段の失敗の条件（Not Met か Unverified が残る）には当たります。
- Unverified の 22 件は、この Intent の流れにある後の段（performance-validation・observability-setup・deployment-pipeline・deployment-execution・feedback-optimization）が持ち主です。目標は緩めていません。

失敗の条件に当たるため、Build and Test の失敗の手順（`.claude/aidlc-common/stages/construction/build-and-test.md` の Step 9）の段 2 の分類を書いておきます。

- U5-NFR7.1: 原因は依存の部品（make-you-chic-ui の `useFocusTrap`）で、このリポジトリから直接変えられません（`project.md` の Forbidden）。直し方は make-you-chic-ui の側の直し（`make-you-chic-ui-request-3.md`）を取り込む固定先の更新（C2′）と、E2E 110・120 の確かめの形の書き換えです。見積もり: 部品の直しが公開の側に入るのを待つ必要があり（2026-10-03 の時点では未公開）、取り込んだ後の確かめは verify の画面の段・osvScan・E2E の全体で約 20 分、変更は gitlink と E2E の2ファイル。依頼者は B5 の統合の前に「N-19 の直しを待たずに統合する」と決め、承認の場で後の Intent への持ち越しとしました。
- Unverified の 22 件: この段の環境では測れない（負荷の環境・配備した環境が要る）目標で、持ち主の段がこの Intent の流れに EXECUTE としてあります。

**依頼者の決定（2026-10-03、検査の後の確かめ）**: 推奨どおり、失敗の手順（ループバック・halt-and-ask）には入らず、U5-NFR7.1 の Not Met は後の Intent への持ち越し（N-19、コード生成の承認の場で承認済み）、Unverified の 22 件は持ち主の段への引き継ぎとして、この段の承認へ進める。目標は緩めていない。

## 5. 既知の制約と、後の段へ持ち越すもの

- **後の Intent への持ち越し**（`gate-decisions.md` の4節）: N-19（AC2.1.8・AC3.1.7・AC4.1.9・AC5.1.7 は条件つき）、Q-H（一意の制約の違反の例外の文）、出力を捕まえるテストの範囲の弱さ、V9 の後の戻し、8KB を超える要求の HTML の 400、U3 R-02 の確かめ直しの隙、`ms-pool-pending` の式の見直し。
- **この段で足した持ち越し**（Q3: A）: U5 の2回目のレビューの R-03（送信中も言語の選択を変えられ、送った値と画面の値がずれうる）。N-19 と同じ画面の直しとして扱います。
- **段全体のレビューの R-02**: AC3.2.9・AC3.2.10・AC2.2.6 は、`cross-unit-traceability.md` でテストのソースを根拠に判定しました。承認済みの `traceability.json` は書き換えていません。
- **この段で足した持ち越し（依頼者の決定、検査の後の確かめ）**: AC2.2.6 は2つのテストの組み合わせ（印を外した直後の 403 と、管理者でない利用者の 403 の監査）で網羅と判定した。印を外した直後の要求の監査の行を1つのテストで続けて確かめるテストを、後の Intent で足す。
- **段全体のレビューの R-03**: 最新の実測を `test-results.md` の1か所にまとめました。
- **後の段へ**:
  - performance-validation: k6 の5つの場面と既存の `login`・`refresh`・`invitationList`。最初に `k6 inspect` を流し直す。
  - observability-setup: 7つの API の `uri`・`le` の値、既存の警報が拾う範囲、監査の種類5つと理由の名前、SLO の基準の値。
  - deployment-pipeline: 戻す前に停止中の利用者を確かめる手順（U1 の NFR10.3、基盤の設計の U1・U3 の申し送り）、スモークテスト（5つの操作は監査に残り利用者の状態を変えるため、入れるときは先に依頼者に伝える）。
  - deployment-execution: V9 の事後の裏付け（既存の初期管理者でログインでき `/api/me` が通る）。
- **`team.md` の記述**: Testing Posture の「2026-09-29 の時点で 12 パッケージ」は、日付つきの記述のため書き換えない（U1 のレビューの R-04 の決定）。今の一覧は 7 パッケージ。

## Sources

- 各単位の `code-generation-plan.md`（Testing Contract・「Build and Test に引き継ぐこと」）・`unit-test-instructions.md`・`code-summary.md`・`generation-notes.md`・`traceability.json`、`nfr-requirements/`・`nfr-design/` の文書（`aidlc/spaces/default/intents/260930-user-admin/construction/u1-user-suspension/` 〜 `u5-user-admin-ui/`）
- `aidlc/spaces/default/intents/260930-user-admin/construction/code-generation/gate-decisions.md`・`construction/infrastructure-design/gate-decisions.md`
- `aidlc/spaces/default/intents/260930-user-admin/construction/build-and-test/test-results.md`・`cross-unit-traceability.md`・`build-and-test-questions.md`
- `aidlc/spaces/default/memory/team.md`・`aidlc/spaces/default/memory/project.md`

## Assumptions & Open Questions

- U5-NFR7.1 の Not Met と Unverified の 22 件の扱いは、依頼者の決定（4節）で、持ち越しの承認のもとでこの段の承認へ進めることにした。承認の場で、この扱いを含めて承認を確かめます。
