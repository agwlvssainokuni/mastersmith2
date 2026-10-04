# コード生成の計画（261004-safety-carryover）

- 範囲: bugfix（深さ Minimal、Test Strategy は Minimal）。brownfield。単位の分割は無い（zero-Unit）。成果物の置き場は `aidlc/spaces/default/intents/261004-safety-carryover/construction/code-generation/`。
- 入力: 要件 `inception/requirements-analysis/requirements.md`（FR1〜FR8・NFR1〜NFR7、承認済み。承認の場で R-01〜R-07 を反映、R-08・R-09 はリスクとして受け入れ）、質問と答え `requirements-analysis-questions.md`（Q1: B・Q2: A・Q3: C・Q4: A・Q5: A・Q6: D・Q7: C・Q8: A、F1: C・F2: C・F3: A・F4: B・F5: A）、コード知識ベース `aidlc/spaces/default/codekb/mastersmith2/`（K-25〜K-31）、開発担当のスキャン `inception/reverse-engineering/developer-scan.md`。
- 計画の基準のコード: `develop` の先頭 `25ec1b9`（Requirements Analysis の承認の記録）。
- 計画の段で行った確かめ: ソースの読み取りだけ（Read・Grep）。カバレッジの実測は行っていない（1.1 の根拠のとおり、`packagesJudgedByTotal` のパッケージの `src/main` を変えないため）。テストもビルドも流していない。

## 1. 変える範囲（影響の範囲）

| 区分 | ファイル | 変更 | 要件 |
|---|---|---|---|
| ドメイン（user） | `backend/src/main/java/cherry/mastersmith/user/domain/InitialAdminRescueCondition.java`（新規） | 救済の条件の列挙 `SUSPENDED`・`NO_ADMIN`・`PASSWORD`（この順）と、条件の集まりを決まった順に `+` でつなぐ純粋な関数 | FR1.1・FR1.6a |
| ドメイン（user） | `backend/src/main/java/cherry/mastersmith/user/domain/InitialAdminRescuedEvent.java`（新規） | 救済の出来事（利用者 ID・条件の集まり・日時だけ。メールアドレス・パスワード・ハッシュを持たない） | FR1.2・FR1.5 |
| ドメイン（user） | `backend/src/main/java/cherry/mastersmith/user/domain/InitialAdminCreatedEvent.java`（新規） | 初期管理者の作成の出来事（利用者 ID・日時だけ） | FR1.4・FR1.5 |
| 業務処理（user） | `backend/src/main/java/cherry/mastersmith/user/service/InitialAdminRescueResult.java`（新規） | 救済の結果の型（sealed interface の record: `NotFound`・`NotNeeded`・`Rescued(userId, conditions)`） | FR1.1・FR1.3 |
| 業務処理（user） | `backend/src/main/java/cherry/mastersmith/user/service/UserAccountService.java` | 救済の操作 `rescueInitialAdmin(RedactedText email, Password password)` を足す（1つのトランザクションで判定・書き換え・出来事の知らせ） | FR1.1〜FR1.3・FR1.2a・NFR3 |
| 業務処理（user） | `backend/src/main/java/cherry/mastersmith/user/service/InitialAdminInitializer.java` | 「既にいれば何もしない」を「救済の判定 → 救済・何もしない・作成」に変える。作成の出来事・救済の WARN・救済の失敗の ERROR を足す | FR1.1〜FR1.5・FR1.7・FR1.2a |
| 業務処理（user） | `backend/src/main/java/cherry/mastersmith/user/service/InitialAdminProperties.java` | `toString` でメールアドレスも伏せる（`EmailAddress.mask`） | NFR1 |
| 業務処理（auth） | `backend/src/main/java/cherry/mastersmith/auth/service/InitialAdminRescueListener.java`（新規） | 救済の出来事を同じトランザクションで受け（`@EventListener`・`MANDATORY`）、失敗回数を 0・ロックを解き、リフレッシュトークンをすべて無効にする | FR1.2(c)(e)・FR1.2a |
| ドメイン（audit） | `backend/src/main/java/cherry/mastersmith/audit/domain/AuditEventType.java` | `INITIAL_ADMIN_CREATED`・`INITIAL_ADMIN_RESCUED` を足す | FR1.5・FR1.6a |
| ドメイン（audit） | `backend/src/main/java/cherry/mastersmith/audit/domain/AuditEvent.java` | 対象の利用者と条件の列（`rejection_kind`）を持つ起動時の行を作る静的な組み立てを1つ足す（列は足さない） | FR1.6・FR1.6a |
| ドメイン（audit） | `backend/src/main/java/cherry/mastersmith/audit/domain/AuditEventFactory.java` | 2つの出来事からの組み立て `from(...)` と、接続元の決まった値 `system` の定数 | FR1.5・FR1.6・FR1.6a |
| 業務処理（audit） | `backend/src/main/java/cherry/mastersmith/audit/service/AuditEventListener.java` | 2つの出来事の受け取り（既存と同じ `AFTER_COMMIT`・`fallbackExecution = true`・最優先） | FR1.5・FR1.8 |
| 設定（FR4.2。D3: A） | `backend/src/main/resources/application.yaml`（`logging.level`。二重の ERROR と確かめられたときだけ） | Tomcat の `dispatcherServlet` のロガーを `OFF` にして、フィルターの中の例外の ERROR を1行にする | FR4.2 |
| 環境 | `Dockerfile`・`compose.yaml`・`perf/README.md`・`README.md`・`docker/hikari-pool.sh`（D4: A。同じイメージのすべての出現） | イメージに複数のアーキテクチャをまとめた index のダイジェストを付ける（版のタグは残す） | FR7.2・FR7.4 |
| 環境 | `.github/dependabot.yml` | ダイジェストを付けたイメージと、Dependabot が見ない固定先を手で揃える決まりをコメントに書く | FR7.3 |
| 文書 | `README.md` | 初期管理者の救済の手順（RB-22 に当たるもの）、新しい監査の種類、利用者の管理の画面の既知の点、Tomcat のロガーを止めたことで失うログ（D3） | FR1.9・FR5.1・FR4.2・R-08 |
| テスト（新規） | `backend/src/test/java/cherry/mastersmith/user/domain/InitialAdminRescueConditionTest.java` | 条件の値のつなぎ方（例の値と、jqwik の性質ベースのテスト） | FR1.6a |
| テスト（新規） | `backend/src/test/java/cherry/mastersmith/user/service/InitialAdminPropertiesTest.java` | `toString` にメールアドレスとパスワードが出ない | NFR1 |
| テスト（変更） | `backend/src/test/java/cherry/mastersmith/user/service/InitialAdminInitializerTest.java`・`UserAccountServiceTest.java` | 救済・何もしない・作成・救済の失敗・照合1回の分岐 | FR1.1〜FR1.4・FR1.7・FR1.2a・NFR3 |
| テスト（新規） | `backend/src/test/java/cherry/mastersmith/auth/service/InitialAdminRescueListenerTest.java` | 失敗回数を 0・ロックの解除・トークンの無効化の呼び出し、ロックの状態の行が無い利用者 | FR1.2(c)(e) |
| テスト（変更） | `backend/src/test/java/cherry/mastersmith/audit/domain/AuditEventFactoryTest.java`・`audit/service/AuditEventListenerTest.java` | 2つの出来事の行の形（種類・結果・対象・操作した人・接続元・条件・空の列） | FR1.5・FR1.6・FR1.6a・FR1.8 |
| テスト（新規） | `backend/src/test/java/cherry/mastersmith/user/service/InitialAdminRescueIT.java` | 起動を2回行う結合テスト（救済の4通り・何もしない・作成・途中の失敗・監査の失敗・設定のパスワードが規則外） | FR1.1〜FR1.8・NFR2・R-08 |
| テスト（新規） | `backend/src/test/java/cherry/mastersmith/user/service/InitialAdminSecretLeakIT.java` | 作成・救済で、ログ（INFO と TRACE）と監査の行にメールアドレス・パスワード・トークンが出ない | FR1.5・FR1.7・NFR1 |
| テスト（新規） | `backend/src/test/java/cherry/mastersmith/user/testsupport/FailingInitialAdminRescueConfig.java` | 救済の出来事を受けて例外を投げるテストだけの受け手（auth の受け手の後に呼ばれる順） | FR1.2a |
| テスト（変更） | `backend/src/test/java/cherry/mastersmith/user/service/InitialAdminIT.java` | 作成の監査の行がちょうど1行・2回目の起動で行が増えないことを足す | FR1.4・FR1.5 |
| テスト（新規） | `backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminBusyLogTraceIT.java` | 409 `USER_ADMIN_BUSY` の L3 の各行に、同じ `traceId` の L4 がちょうど1行 | FR3.1 |
| テスト（新規） | `backend/src/test/java/cherry/mastersmith/common/error/web/FilterExceptionErrorLogIT.java` | 認証の途中で接続を借りられないときの ERROR の行の数（直す前の再現と、直した後の1行） | FR4.1・FR4.1a・FR4.2・NFR6 |

- 移行（`db/migration`）と表の変更は無い。`audit_events` の列は増やさない（FR1.6a・制約）。
- 画面（`frontend/`）の変更は無い（FR5 は直さない。Q6: D）。
- `team.md` は変えない（FR6 は Build and Test。6節）。`project.md` も直接は変えない（FR7.3 で足す学びは、承認の場の学びの手順で行う）。
- `vendor/` の中身は変えない（`project.md` の Forbidden）。サブモジュールの固定先の更新も無い。

### 1.1 カバレッジとパッケージの一覧（NFR5）

- `backend/build.gradle.kts` の `packagesJudgedByTotal` は今 7 個（`access.service`・`audit.repository`・`common.error.domain`・`common.error.service`・`common.health`・`common.i18n.domain`・`common.web`。`backend/build.gradle.kts` 225〜233 行で確かめた）。
- この計画が `src/main` を変えるパッケージは `user.domain`・`user.service`・`auth.service`・`audit.domain`・`audit.service` で、**一覧に当たるものは無い**。どれもすでにパッケージごとの下限（行 80%・分岐 70%）の対象である。
  - `audit.repository`（一覧の中）は変えない。監査の書き込みは既存の `AuditEventRecorder` と `AuditEventRepository` をそのまま使う。
  - FR4.2 の直し（D3: A）は `application.yaml`（リソース）だけで、Java のパッケージに当たらない。`common.error.*`・`common.web` の `src/main` は変えない。
- そのため、計画の段のカバレッジの実測（`:backend:cleanTest :backend:cleanIntegrationTest :backend:test :backend:integrationTest :backend:jacocoTestReport`）は省いた（依頼の決まり「当たるパッケージが無いと確かめられたなら省いて根拠を書く」）。
- 生成の途中で、一覧の中のパッケージの `src/main` に手を入れる必要が出たときは、手を入れる前に止めて、オーケストレーターに知らせる（team.md の「テストを足して一覧から外す」作業が付くため、計画の変更として依頼者に諮る）。
- 足す分岐は、同じ Step の単体テストで覆う（パッケージごとの下限を下回らせない）。カバレッジの数値は Step 16 の `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` の実測だけを記録する（project.md の Testing Posture の学び）。下限・除外の設定は変えない。

### 1.2 要件の「未解決の点」を計画で決めたこと

#### P1 当たった条件を入れる列（FR1.6a）: `rejection_kind`

- `failure_reason` は `AuditFailureReason` の列挙を `@Enumerated(EnumType.STRING)` で持つ列で、構築子の説明も「失敗の理由（成功なら null）」である（`AuditEvent.java` 70〜71 行・115 行）。救済の行は結果が `SUCCESS` のため、この列に値を入れると「成功なのに失敗の理由がある」行になり、既存の読み方と食い違う。組み合わせ（最大 7 通り）を列挙の値として足す形も、失敗の理由の一覧を汚す。
- `rejection_kind` は自由な文字列（`VARCHAR(32)`、`AuditEvent.java` 96〜97 行）で、今は DSL の投入の拒否の種類だけが使う。結果に依らない「種類の短い値」の列として使える。
- よって `rejection_kind` に入れる。値は `SUSPENDED`・`NO_ADMIN`・`PASSWORD` を決まった順に `+` でつなぐ（最大 `SUSPENDED+NO_ADMIN+PASSWORD` の 27 文字）。作成の行では空（NULL）。
- 列の名前（拒否の種類）と使い方（救済の条件）がずれるため、`AuditEvent` の説明と README の「監査ログ（U4）」に、出来事ごとの列の使い方として書く。依頼者の決定（D1: A）。

#### P2 救済の置き場（制約の節）: `user` が書き換えて出来事で知らせ、`auth` が同じトランザクションで受ける

- 依存の向きの決まり（コードで確かめた）:
  - `AuthBoundaryArchitectureTest`: `user` は `auth` に依存しない。`auth` は `user.domain` のエンティティと `user.repository` に依存しない。`auth` と `user` は `audit` に依存しない。
  - `UserAdminBoundaryArchitectureTest`: `useradmin` の外で `useradmin` に依存してよいのは `audit` だけ。書き換えの口（`UserAccountService.setAdmin`・`setSuspended`、`RefreshTokenRevocationService.revokeAllRefreshTokens`、`LockAdministrationService.prepareFailureReset`・`completeFailureReset`）を呼んでよいのは、持ち主のクラスの中と `useradmin.service` だけ。
- 既存の形の前例: 利用者の作成は `UserAccountService.createUser` が同じトランザクションで `UserCreatedEvent` を知らせ、`auth.service.LoginAttemptStateInitializer` が `@EventListener`・`@Transactional(propagation = MANDATORY)` で受けてロックの状態の行を作る。`team.md` の「逆向きの知らせは Spring の出来事で行う」に当たる。
- 決めた形:
  1. `InitialAdminInitializer`（`user.service`）が設定を検査し、`UserAccountService.rescueInitialAdmin(...)` を呼ぶ。
  2. `rescueInitialAdmin` は `@Transactional` の1つのトランザクションで、利用者を読み、条件を判定し（パスワードの照合は bcrypt 1回）、当たれば停止を解く・印を付ける・パスワードのハッシュを置き換える（`UserRepository` の既存の `updateSuspended`・`updateAdminFlag`・`updatePasswordHashIfUnchanged` を直接使う。書き換えの口は呼ばない）。最後に `InitialAdminRescuedEvent` を知らせる。
  3. `auth.service.InitialAdminRescueListener`（新規）が同じトランザクションの中で受け、`LoginAttemptStateRepository` の `createIfAbsent`・`update(userId, 0, null)` で失敗回数を 0 にしてロックを解き、`RefreshTokenRepository.revokeAllActiveByUserId(userId, 今の時刻)` でリフレッシュトークンをすべて無効にする（書き換えの口は呼ばない）。
  4. 確定の後に `audit.service.AuditEventListener` が受けて監査に1行残す。途中のどこかが例外を投げれば全部が巻き戻り、確定しないため監査の行も残らない（FR1.2a）。
- これで依存の向き（`auth` → `user`、`audit` → `user`）は変わらず、既存の境界テストは緩めない・消さない。
- ただし、`UserAdminBoundaryArchitectureTest` の「書き換えの口は `useradmin.service` だけが呼ぶ」（Intent 260930-user-admin の BR7.3・NFR11.2）の趣旨から見ると、起動時の救済が `useradmin` を通らずに印・停止・失敗回数・トークンを書き換える2つ目の経路になる。テストは口の呼び出しだけを見るため通る。依頼者はこの2つ目の経路を受け入れた（D2: A。境界テストは変えない・緩めない）。趣旨との差を `InitialAdminRescueListener` と `rescueInitialAdmin` の説明に書き、code-summary.md にも記録する。
- `UserCreatedEvent` の前例（`InitialAdminIT` の「ロックの状態の行が作られる」）から、起動の途中（`SmartInitializingSingleton`）に知らせた出来事を受け手が受け取れること（Spring の出来事の受け手の登録が先に済んでいること）は確かめ済みとみなす。

#### P3 接続を借りられない状況の作り方（FR4.1a）

- 今の設定は `spring.datasource.hikari.allow-pool-suspension: true` だが、一時停止の間は接続の待ちの上限（5 秒）が効かず再開まで待たされる（`application.yaml` のコメント）。時間切れの例外を作れないため、一時停止は使わない。
- 決めた形: テストのクラスの設定だけで `spring.datasource.hikari.maximum-pool-size=1`・`spring.datasource.hikari.connection-timeout=250`（HikariCP の下限）にする。本番の設定とコードは変えない。
  1. 接続を使う前に、アクセストークンを用意する（`auth.testsupport.AuthTestTokens` でテストの署名鍵から作る。利用者は JDBC で1人入れる）。
  2. テストのスレッドで `DataSource.getConnection()` を呼び、ただ1本の接続を持ったままにする（try-with-resources）。持つ操作が終わってから要求を送るため、`sleep` も待ち合わせの部品も要らない（順序で確実に作る）。
  3. 認証の要る API（`GET /api/me/preferences`）へ Bearer つきの要求を1本送る。`AccessTokenAuthenticationProvider` の `findById` が 250 ms で借りられずに例外になる見込み。
  4. 応答（500 の Problem Details と `traceId`）と、要求の前の出力の位置から後のログの JSON を読み、`logger` の項目で Tomcat のロガー（`org.apache.catalina.core.ContainerBase.` で始まる）の ERROR と `ErrorPathController` の ERROR を数える。
- Tomcat の行は、フィルターの連なりを抜けた後に出るため、MDC の `traceId` を持たない見込み（未検証）。そのときは、要求を1本だけ送った区間の中の行として数え、`traceId` を持たないことも記録する（「同じ `traceId` で2行」の判定は、区間と例外のクラスの名前で結び付けて記録する）。
- 確かめた結果は3つに分ける。
  - 2行出た（見立てどおり）→ Step 12 の直しへ進む。
  - 再現できなかった（500 にならない、ERROR が1行以下など）→ FR4.1b。直しは行わず、試みた範囲と結果を code-summary.md に記録して止め、オーケストレーターに知らせる。
  - 見立てと違う原因だった → FR4.3。直さずに止め、確かめた原因を記録してオーケストレーターに知らせる。

#### P4 FR4.2 の直し方（依頼者の決定 D3: A）

- Step 11 で二重の ERROR と確かめられたときだけ、`application.yaml` の `logging.level` で、Tomcat の `dispatcherServlet` のロガー（`org.apache.catalina.core.ContainerBase.[Tomcat].[localhost].[/].[dispatcherServlet]`）を `OFF` にする。理由と障害の調べ方（`ErrorPathController` の ERROR が原因の例外と `traceId` を持つこと）をコメントに書く。
- 残る1行は `ErrorPathController` の ERROR「想定外のエラーが起きました」（原因の例外・`traceId` つき）。500 の Problem Details（内部のメッセージを含めない）は変えない。
- 代わりに失うもの: 応答を書き始めた後（確定した後）に例外がサーブレットの外へ出たときは `/error` へ回らないため、その例外のログが無くなる。このことを `application.yaml` のコメントと `README.md`（Step 15）に明記する。今のアプリに応答を流しながら書く API は見当たらない（`ResponseEntity` で返す形）が、確かめは読み取りの範囲に留まる。
- 再現できなかった（FR4.1b）・見立てと違う原因だった（FR4.3）ときは、この直しを入れず、Step 11 で止めて依頼者に諮る（推測で直さない）。
- 採らなかった案: `common.error.web` に例外を受けるフィルターを置く形（応答の確定の後の例外も1行残せるが、`sendError` と `ERROR_EXCEPTION` の扱い・フィルターの順序の確かめが要り、手数とリスクが大きい）。

#### P5 FR5.1 の既知の点の記録の置き場: `README.md`

- 利用者の管理の画面の節は `README.md` に無く（「利用者の管理の API（Intent 260930-user-admin の U3）」の節だけ）、前の Intent の手順書（`runbooks.md`）は承認済みの記録で書き換えない（project.md の学び）。
- 「利用者の管理の API」の節の後に小見出し「利用者の管理の画面の既知の点」を足し、言語の欄で Enter を押して送信すると、送信中に押せなくなった選択肢からフォーカスが外れて `body` に落ちること、直さない理由（make-you-chic-ui の `RadioGroup` に読み取り専用の口が無い。Q6: D）、直すときの候補（K-29 の (a)〜(c)）を書く。

#### P6 FR7.3 の記録の置き場: `.github/dependabot.yml` のコメント

- 固定の決まりを読む人は、イメージを上げる人（Dependabot の知らせを受けた人）であるため、`.github/dependabot.yml` の `docker`・`docker-compose` の項のコメントに書く。
  - ダイジェストを付けたイメージ（どのファイルのどのイメージか）と、付けるダイジェストは複数のアーキテクチャをまとめた index のもの（`linux/arm64`・`linux/amd64` を含む）であること。
  - Dependabot が見ない固定先（`docker/perf/compose.yaml`・`backend/src/test/java/cherry/mastersmith/targetdb/testsupport/TargetDbImages.java`・`perf/README.md`・`README.md`・`docker/hikari-pool.sh`）は、同じ版とダイジェストに手で揃えること。
- `project.md` の Tech Stack の学び（3か所を一緒に上げる）を補う1行は、この段の承認の場の学びの手順で、依頼者の承認を得て足す（直接は書かない）。

#### P7 FR1.9 の手順書の置き場: `README.md`

- 前の Intent の `runbooks.md`（RB-22）は承認済みの記録のため書き換えず、生きた手順の置き場である `README.md` の「コンテナでの起動と確認」の節に小見出し「使える管理者がいなくなったとき（初期管理者の救済）」を足す。
  - 救済の働き方（条件の (a)〜(c)、行うこと (a)〜(e)、監査の `INITIAL_ADMIN_RESCUED` と条件の列、WARN の1行）。
  - 「初期管理者を止めたい・印を外したいときは、先に `.env` の初期管理者の設定を替える（外す）。そうしないと次の再起動で戻る」（FR1.9・F5）。
  - 設定のパスワードが規則（12 文字以上・72 バイト以内）を満たさないと、作成も救済もされず WARN「初期管理者を作成しませんでした」が出ること。その WARN が出たら `.env` のパスワードを直して再起動すること（R-08 の手当て）。
  - 確かめ方は、値を出さずに件数とキーの名前だけで見ること（project.md の学び）。
- あわせて「監査ログ（U4）」の節に、2つの新しい種類と列の使い方（接続元 `system`、操作した人は空、条件は `rejection_kind`）を書く。

#### P8 レビューの指摘 R-08・R-09 の手当て

- R-08（設定のパスワードが規則外だと、停止中・印なしの初期管理者も救われない）: Step 9 の結合テストに「停止中で印の無い初期管理者がいて、設定のパスワードが 12 文字未満」の判定を1件足す（救済されない・状態が変わらない・WARN が出る・監査の行が増えない）。手順は P7 のとおり README に書く。
- R-09（p95 が 1 秒に近く、3回の判定はぶれで Not Met になりうる）: 6節の Build and Test への引き継ぎに「1回でも 1 秒以上なら、条件をそろえたまま回数を増やして切り分ける（目標は緩めない）」を書く。

## 2. 手順

Testing Contract（4節の前の `## Testing Contract`）の `plan_profile.steps` を順の基準にする（test-after。層ごとに実装を書き、同じ作業の中でその層のテストを書いて流し、通ってから次の層へ進む）。今回の変更に当たらない層は次のとおり。

- データモデル・DB の振る舞い: 移行（`db/migration`）・表の変更は無い（FR1.6a・制約）。該当なし。
- リポジトリ・データアクセス: 既存の問い合わせ（`UserRepository`・`LoginAttemptStateRepository`・`RefreshTokenRepository`・`AuditEventRepository`）をそのまま使い、足さない・変えない。該当なし（使い方の確かめは業務処理の層の結合テストで行う）。
- 画面の振る舞い: 変更は無い（FR5 は直さない）。該当なし。

各 Step の後ろの括弧は、実装する要件の ID。

### 2.1 準備（構成と依存）

- [x] **Step 1**（準備。NFR4）: 段を始める前の片付けと作業ブランチ。
  - git の対象外の E2E の生成物（`frontend/playwright-report`・`frontend/test-results`）が残っていれば消す（project.md の学び 2026-10-03。段の途中では消さない）。
  - この計画の記録（`aidlc/` の下）のコミットは、オーケストレーターが依頼者に提案して承認を得てから行う。
  - `develop` から短命のブランチ `fix/261004-safety-carryover` を作る（team.md の Way of Working。worktree は使わない）。
- [x] **Step 2**（テストの実行の準備。Testing Contract の `runner_step`）: `unit-test-instructions.md` の範囲を絞ったコマンドが今のまま動くことを、変更の前に1回流して確かめる。
  - 単体: `unit-test-instructions.md` の 3.1 の「変更の前」のコマンド（既存の `InitialAdminInitializerTest`・`UserAccountServiceTest`・`AuditEventFactoryTest`・`AuditEventTest`・`AuditEventListenerTest` と境界テスト）。
  - 結合: 3.2 の「変更の前」のコマンド（既存の `InitialAdminIT`・`UserAdminBusyApiIT`）。
  - colima が動いていることを確かめ、README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡す（この2つは対象DB を使わないが、後の verify と同じ環境で流すため）。
  - 結果（件数・成否）を code-summary.md に記録する。

### 2.2 ドメインの層（user・audit）

- [x] **Step 3**（FR1.1・FR1.4・FR1.5・FR1.6・FR1.6a）: ドメインの型を足す。
  - `user.domain.InitialAdminRescueCondition`: 列挙 `SUSPENDED`・`NO_ADMIN`・`PASSWORD`（宣言の順が並びの順）と、静的な関数 `code(Set<InitialAdminRescueCondition>)`（空の集まりは想定外の誤り。決まった順に `+` でつなぐ）。
  - `user.domain.InitialAdminRescuedEvent(long userId, Set<InitialAdminRescueCondition> conditions, Instant occurredAt)`: 構築子で必須の値と空でない条件を確かめ、条件は変えられない写し（`EnumSet` の順）で持つ。メールアドレス・パスワード・ハッシュを持たない。
  - `user.domain.InitialAdminCreatedEvent(long userId, Instant occurredAt)`。
  - `audit.domain.AuditEventType` に `INITIAL_ADMIN_CREATED`・`INITIAL_ADMIN_RESCUED`（どちらも 32 文字以内）を足し、説明に Intent の名前を書く。
  - `audit.domain.AuditEvent` に、起動時の出来事の行を作る静的な組み立て（名前は `set` で始めない。既存の `AuditEventTest` の「値を変える手段を持たない」）を1つ足す。種類・結果 `SUCCESS`・接続元・対象の利用者・条件（`rejection_kind`）だけを入れ、`entered_email`・`user_agent`・`request_path`・`trace_id`・`actor_user_id`・DSL の項目・対象の招待は空。説明に、出来事ごとの列の使い方（P1）を書く。
  - `audit.domain.AuditEventFactory` に、接続元の決まった値の定数 `SYSTEM_SOURCE_IP = "system"`（`source_ip` の `VARCHAR(45)` 以内、IP の形でない）と、`from(InitialAdminCreatedEvent)`・`from(InitialAdminRescuedEvent)` を足す（作成は条件が空、救済は `InitialAdminRescueCondition.code(...)`）。
- [x] **Step 4**（Step 3 のテスト。FR1.6a・FR1.6・FR1.5）: ドメインのテストを書いて流す（`unit-test-instructions.md` 3.1）。
  - `InitialAdminRescueConditionTest`（新規）: 3つすべてで `SUSPENDED+NO_ADMIN+PASSWORD`、1つずつで各値、渡す順に依らず決まった順になること、空の集まりは誤り。jqwik の性質ベースのテストで、どの空でない部分集合でも「32 文字以内」「`+` で分けると決まった順の部分列」「分けて戻すと元の集まり」になること（失敗時の種を記録する。team.md）。
  - `AuditEventFactoryTest`（変更）: 作成の行（種類・`SUCCESS`・対象の利用者・操作した人が空・接続元 `system`・条件が空・`entered_email` などが空）と、救済の行（条件の列が決まった順の値）。
  - `AuditEventTest`（変えない見込み）: 既存の「すべての種類が 32 文字以内」が足した2つも覆う。
  - `InitialAdminRescuedEvent` の構築子の確かめ（空の条件・null）は `InitialAdminRescueConditionTest` の中か、同じパッケージの小さなテストで覆う（パッケージごとの分岐の下限のため）。

### 2.3 業務処理の層（user・auth・audit）

- [x] **Step 5**（FR1.1〜FR1.3・FR1.2a・NFR3）: `UserAccountService` に救済の操作を足す。
  - `InitialAdminRescueResult`（`user.service`、新規）: sealed interface と record の `NotFound`・`NotNeeded`・`Rescued(long userId, Set<InitialAdminRescueCondition> conditions)`。メールアドレスを持たない（`TraceAspect` が戻り値を TRACE に出すため）。
  - `rescueInitialAdmin(RedactedText email, Password password)`（`@Transactional`）:
    1. メールアドレスをそろえて `findByEmail` で1回読む。いなければ `NotFound`。
    2. 条件を判定する: 停止中なら `SUSPENDED`、印が無ければ `NO_ADMIN`、`passwordEncoder.matches` が偽なら `PASSWORD`。照合はこの1回だけ（NFR3。72 バイトを超える値は呼び出し元の検査で来ない前提だが、`PasswordPolicy.fitsMaxBytes` が偽なら照合せず不一致とする既存の決まりにそろえる）。
    3. 条件が無ければ書かずに `NotNeeded`。
    4. 条件があれば、停止を解く（`updateSuspended(id, false)`）・印を付ける（`updateAdminFlag(id, true)`）・パスワードのハッシュを設定の値で作り直して置き換える（`updatePasswordHashIfUnchanged(id, 読んだハッシュ, 新しいハッシュ)`。1行でなければ想定外の誤り）を行う。パスワードは条件 `PASSWORD` に当たらないときも書き直す（FR1.2(d) の文言どおり。依頼者の決定 D6: A）。
    5. `InitialAdminRescuedEvent` を知らせ、`Rescued` を返す。
  - 書き換えの口（`setAdmin`・`setSuspended`）は呼ばず、リポジトリを直接使う（P2）。説明に、起動時の救済だけが使う口であること、`useradmin` を通らない経路であることを書く。
  - 例外のメッセージにメールアドレス・ハッシュを載せない（利用者 ID だけ）。
- [x] **Step 6**（FR1.1〜FR1.5・FR1.7・FR1.2a・NFR1）: `InitialAdminInitializer` と `InitialAdminProperties` を変える。
  - 構築子に `ApplicationEventPublisher` と `Clock` を足す。
  - 流れ: 設定の検査（今どおり。問題があれば WARN「初期管理者を作成しませんでした」で終わる）→ `rescueInitialAdmin` →
    - `NotNeeded`: 今の INFO「初期管理者は既にいるため、作成しませんでした」（キー `maskedEmail`）をそのまま出す（FR1.3）。
    - `Rescued`: WARN「初期管理者を救済しました」を1行出す。キーは `maskedEmail`（`EmailAddress.mask`）と `conditions`（`InitialAdminRescueCondition.code(...)`）だけ（FR1.7）。
    - `NotFound`: 今どおり `createUser` で作る。`Created` なら INFO「初期管理者を作成しました」の後に `InitialAdminCreatedEvent` を知らせる（トランザクションの外。監査の受け手の `fallbackExecution = true` でその場で記録される）。`EmailAlreadyUsed` は今どおり INFO。
  - `rescueInitialAdmin` が例外を投げたとき（FR1.2a）: `RuntimeException` を受け、ERROR「初期管理者の救済に失敗しました」を1行出して、起動を続ける（`afterSingletonsInstantiated` から例外を出さない）。キーは `maskedEmail` と `exceptionClass`（例外のクラスの名前）だけで、例外そのもの（スタックトレース・メッセージ）は渡さない。H2 の例外の連なりの文には行の全部の列の値（メールアドレスを含む）が入りうるため（project.md の学び 2026-10-01）。team.md の「想定外は ERROR でスタックトレース付き」との差として、説明と code-summary.md に書く。
  - `InitialAdminProperties.toString()`: メールアドレスを `EmailAddress.mask` の形にし、パスワードは今どおり `***`（NFR1、K-25）。
- [x] **Step 7**（FR1.2(c)(e)・FR1.2a）: `auth.service.InitialAdminRescueListener`（新規）。
  - `@EventListener`・`@Transactional(propagation = Propagation.MANDATORY)` で `InitialAdminRescuedEvent` を受ける（`LoginAttemptStateInitializer` と同じ形）。順は `@Order(0)` とし、テストの失敗の受け手（`@Order(Ordered.LOWEST_PRECEDENCE)`）より先に呼ばれるようにする。
  - `LoginAttemptStateRepository.createIfAbsent(userId)` の後に `update(userId, 0, null)`（失敗回数 0・ロックの解除。ロックの状態の行が無い利用者でも行を作って 0 にする）。
  - `RefreshTokenRepository.revokeAllActiveByUserId(userId, clock.instant())`。
  - ログは DEBUG で利用者 ID と無効にした件数だけ（`RefreshTokenRevocationService` と同じ形）。
  - 書き換えの口（`RefreshTokenRevocationService`・`LockAdministrationService`）は呼ばない（P2）。説明に理由を書く。
- [x] **Step 8**（FR1.5・FR1.8）: `AuditEventListener` に2つの受け取りを足す。
  - `onInitialAdminCreated(InitialAdminCreatedEvent)`・`onInitialAdminRescued(InitialAdminRescuedEvent)`。既存と同じく `@Order(Ordered.HIGHEST_PRECEDENCE)`・`@TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = true)`、組み立て・書き込みの失敗は受け止めて ERROR を1回出す（今の `record` の仕組み。FR1.8）。
  - 失敗の ERROR に載せる項目（`fields`）は、種類と対象の利用者 ID だけ（メールアドレスを持たないため）。
- [x] **Step 9**（Step 5〜8 のテスト。FR1.1〜FR1.8・FR1.2a・NFR1〜NFR3・R-08）: 業務処理の層のテストを書いて流す（`unit-test-instructions.md` 3.1・3.2）。
  - 単体（モックを使う。`unit-test-instructions.md` 3.4）:
    - `UserAccountServiceTest`（変更）: 条件の判定（3つ・1つずつ・無し）、照合が1回だけ（NFR3）、条件があるときの3つの書き込みと出来事、条件が無いときに書かない・知らせない、利用者がいないときの `NotFound`。
    - `InitialAdminInitializerTest`（変更）: `Rescued` で WARN が1行（キーは `maskedEmail`・`conditions` だけ）、`NotNeeded` で今の INFO、`NotFound` で作成と `InitialAdminCreatedEvent`、救済の例外で ERROR が1行（キーは `maskedEmail`・`exceptionClass` だけ）で例外を外へ出さない、設定の問題で救済も作成も呼ばない。
    - `InitialAdminPropertiesTest`（新規）: `toString` にメールアドレスそのものとパスワードが出ず、伏せ字が出る。
    - `InitialAdminRescueListenerTest`（新規）: 行の作成と 0 への更新・トークンの無効化が呼ばれる。
    - `AuditEventListenerTest`（変更）: 2つの受け取りが記録を頼むこと、組み立ての失敗で ERROR を1回出して外へ出さないこと。
  - 結合（起動を2回行う。`InitialAdminIT` と同じ `SpringApplicationBuilder` の形。`unit-test-instructions.md` 3.2）:
    - `InitialAdminRescueIT`（新規）:
      - 3つの条件すべて（停止中・印なし・パスワード不一致）＋ロック中＋起動の前のリフレッシュトークン → 停止が解け、印が付き、失敗回数 0・解除の予定なし、パスワードが設定の値、起動の前のリフレッシュトークンがすべて無効（`revoked_at` が入る、トークンの更新の API が 401）、設定のパスワードでログインできる（200）、監査に `INITIAL_ADMIN_RESCUED`・条件 `SUSPENDED+NO_ADMIN+PASSWORD`・対象の利用者・操作した人が空・接続元 `system`・`entered_email` などが空の行がちょうど1行、救済の WARN がちょうど1行（FR1.1・FR1.2・FR1.5〜FR1.7・NFR2）。
      - 1つだけに当たる3通り（パラメーター化）→ それぞれ救済され、条件の列が `SUSPENDED`・`NO_ADMIN`・`PASSWORD`（FR1.1・FR1.6b）。
      - 有効な管理者でパスワードも一致 → 何も変わらず（ハッシュの値も同じ）、監査の行が増えず、INFO が出る（FR1.3・NFR2）。
      - 救済の途中で失敗（`user.testsupport.FailingInitialAdminRescueConfig` を起動の元に足す。auth の受け手の後に呼ばれ、投げる前に同じトランザクションで失敗回数とトークンが書き換わっていることを確かめてから例外を投げる）→ 停止・印・失敗回数・パスワードのハッシュ・リフレッシュトークンが起動の前のまま、監査に救済の行が無い、ERROR がちょうど1行、起動が続く（`context.isRunning()`。FR1.2a）。
      - 監査の書き込みの失敗（既存の `audit.testsupport.FailingAuditEventRepositoryConfig`）→ 救済は確定し、監査の失敗の ERROR が1回出て、起動が続く（FR1.8）。
      - 停止中で印の無い初期管理者がいて、設定のパスワードが 12 文字未満 → 救済されず、状態が変わらず、WARN「初期管理者を作成しませんでした」が出て、監査の行が増えない（R-08・NFR2）。
      - 設定が無い → 既にいる利用者が停止中でも何もしない（NFR2）。
    - `InitialAdminIT`（変更）: 1回目の起動で `INITIAL_ADMIN_CREATED` の行がちょうど1行（対象は作った利用者・操作した人が空・接続元 `system`・条件が空）、2回目の起動で監査の行が増えない（FR1.4・FR1.5）。既存の確かめは変えない。
    - `InitialAdminSecretLeakIT`（新規）: 既定の INFO と、`cherry.mastersmith` を TRACE にした起動の両方で、作成と救済（3つの条件）の起動のログ・監査の行のすべての列に、メールアドレス（そろえた値・設定の値）・パスワード（設定の値・起動の前の値）・リフレッシュトークンの値・`$2a$` が含まれないこと（`JsonLogRecords.assertContainsNoSecret`）。TRACE の場合は `TraceAspect` の行が出ていること（確かめになっていること）も確かめる（NFR1）。
  - 境界テスト（変えずに流す）: `ArchitectureTest`・`AuthBoundaryArchitectureTest`・`AuditBoundaryArchitectureTest`・`UserAdminBoundaryArchitectureTest` が通ること。

### 2.4 API の層（テストだけ。FR3・FR4）

- [x] **Step 10**（FR3.1）: `UserAdminBusyLogTraceIT`（新規。本番のコードは変えない）。
  - `UserAdminBusyApiIT` と同じ作り（`RowLockHolder` で対象の行を `FOR UPDATE` で持ち続ける、`UserAdminFixtures`・`UserAdminApi`）に、`OutputCaptureExtension` で要求の前の位置から後のログの JSON を読む形を足す。
  - 2つの経路で 409 `USER_ADMIN_BUSY` を起こす: 印を付ける（利用者の行を持つ。`lockKind` が `ADMIN_ROWS`）と、止める（リフレッシュトークンの行を持つ。`REFRESH_TOKEN_ROWS`）。
  - 確かめ: L3（`GlobalExceptionHandler` の WARN、`code` が `USER_ADMIN_BUSY`）の各行について、同じ `traceId` の L4（`RowLockFailures` の WARN、`lockKind`・`exceptionClass` を持つ）がちょうど1行。`traceId` は空でなく、応答の `traceId` と一致する。L3 がちょうど2行（送った 409 の数）。
  - 既存の `UserAdminSecretLeakIT` も同じ結び付きの一部（同じ `traceId` のアプリの WARN が2行）を確かめていることは code-summary.md に書く。FR3.1 の「各 L3 にちょうど1行の L4」の形で確かめるのはこのテストとする。
  - FR3.2（負荷の試験での確かめ）は行わない。
- [x] **Step 11**（FR4.1・FR4.1a・FR4.1b・FR4.3）: `FilterExceptionErrorLogIT`（新規）で、直す前の状態を確かめる。
  - P3 の作り方で要求を1本送り、応答が 500 の Problem Details（`code` が `INTERNAL_ERROR`、内部のメッセージを含まない、`traceId` を持つ）であること、`ErrorPathController` の ERROR（「想定外のエラーが起きました」、原因の例外を持つ、応答と同じ `traceId`）と Tomcat のロガーの ERROR（`Servlet.service()`）の行の数を記録する。
  - この時点のテストは「ERROR が1行だけ」を確かめる最終の形で書き、流して落ちること（2行出ること）を出力ごと code-summary.md に記録する（直す前の再現。NFR6）。
  - P3 の3つの分け方で、再現できなかった（FR4.1b）・違う原因だった（FR4.3）ときは、ここで止めてオーケストレーターに知らせる（直しは行わず、依頼者に諮る。D3 の決定でもこの止まり方は残す）。
- [x] **Step 12**（FR4.2・NFR6）: D3: A の直しを入れ、Step 11 のテストを通す（Step 11 で二重の ERROR と確かめられたときだけ）。
  - `application.yaml` の `logging.level` に Tomcat の `dispatcherServlet` のロガーを `OFF` で足す（Spring Boot の角かっこの書き方）。コメントに、理由（例外の ERROR を変換の境界の `ErrorPathController` の1回だけにする。team.md の Code Style）、障害の調べ方、失うもの（応答を書き始めた後にサーブレットの外へ出た例外のログが無くなる）を書く。
  - 確かめ: その要求の ERROR がちょうど1行（`ErrorPathController`、原因の例外と `traceId` を持つ）、Tomcat のロガーの ERROR が0行、応答（500・`INTERNAL_ERROR`・内部のメッセージなし）が変わらないこと。
  - 直しを一時的に外して Step 11 のテストが落ちることを1回確かめ、戻す（`git diff` で戻ったことを確かめる）。再現のテストと直しは同じコミットに入れる（project.md の Mandated）。
  - 既存のエラー応答のテスト（`common.error.web` の既存の結合テスト）も流して通ることを確かめる（`unit-test-instructions.md` 3.2）。

### 2.5 環境と構成（FR7）

- [x] **Step 13**（FR7.2・FR7.4）: イメージにダイジェストを付ける。
  - 付ける時点のタグが指す index（manifest list）のダイジェストを `docker buildx imagetools inspect <タグ>` で読み、その index に `linux/arm64` と `linux/amd64` の両方が含まれることを確かめる。イメージごとに、タグ・index のダイジェスト・2つのアーキテクチャの有無と、読んだ日時を code-summary.md に記録する。1つのアーキテクチャのイメージのダイジェストは使わない。
  - 対象（版のタグは残し、`タグ@sha256:…` の形）:
    - `Dockerfile` の `FROM eclipse-temurin:25.0.4_7-jre-noble`
    - `compose.yaml` の `otel/opentelemetry-collector:0.162.0`・`grafana/otel-lgtm:0.34.0`
    - `perf/README.md` の `grafana/k6:2.3.0`（6か所）・`eclipse-temurin:25.0.4_7-jdk-noble`（1か所）
    - 同じイメージのほかの出現にも同じダイジェストを付ける（D4: A）: `perf/README.md` の `eclipse-temurin:25.0.4_7-jre-noble`（3か所）と `grafana/otel-lgtm:0.34.0`（1か所）、`README.md` の `eclipse-temurin:25.0.4_7-jre-noble`（4か所）と `eclipse-temurin:25.0.4_7-jdk-noble`（説明の1か所）、`docker/hikari-pool.sh` の `JDK_IMAGE`。
  - 既存のダイジェスト付きのイメージ（対象DB・Mailpit）は変えない（FR7.4）。対象DB の3か所の手での揃えの仕組みも変えない（FR7.1）。
  - 確かめ: `docker compose config -q`（`compose.yaml`）と `docker compose -f docker/perf/compose.yaml config -q` が通ること。`docker buildx imagetools inspect <タグ@ダイジェスト>` が同じ index を返すこと。`perf/README.md` の k6 の台本の読み込みの確かめ（`unit-test-instructions.md` 5節の `inspect`）が、ダイジェスト付きのイメージで通ること。`docker/hikari-pool.sh` は `bash -n` で文法を確かめる（配備したアプリには触れない）。
  - `Dockerfile` からアプリのイメージを作って起動を確かめる判定（FR7 の判定）は、Build and Test の k6 のためのイメージ作りで行う（6節）。
- [x] **Step 14**（FR7.3）: `.github/dependabot.yml` の `docker`・`docker-compose` の項のコメントに、P6 の決まりを書く。既存の `ignore`（`eclipse-temurin` の大きな版）とその理由・外す時期は変えない。

### 2.6 文書と記録

- [x] **Step 15**（FR1.9・FR5.1・R-08）: `README.md` を直す。
  - P7 の「使える管理者がいなくなったとき（初期管理者の救済）」の小見出しと、「コンテナでの起動と確認」の初回の起動の箇条（266 行）の「2回目以降の起動では作られません」に、救済の場合の WARN が出ることを足す。
  - 「監査ログ（U4）」の節に、2つの新しい種類と列の使い方。
  - P5 の「利用者の管理の画面の既知の点」。
  - FR4.2 の直しを入れたとき（D3: A）: 例外のログの扱いを書く節（「監査ログ（U4）」の近くか、既存のログの説明の節）に、Tomcat の `dispatcherServlet` のロガーを止めていること、フィルターの中の例外は `ErrorPathController` の ERROR の1行だけになること、応答を書き始めた後にサーブレットの外へ出た例外のログは無くなることを書く。
  - メールアドレスの実値・パスワード・`.env` の値は書かない。例のメールアドレスは `example.com` だけ。
- [x] **Step 16**（NFR4・NFR5・NFR6）: 統合の前の1コマンドの検査。
  - colima が動いていることを確かめ、README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡して `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を流す（対象DB のテストが SKIPPED にならないこと）。
  - 変えたパッケージ（`user.domain`・`user.service`・`auth.service`・`audit.domain`・`audit.service`）の行と分岐のカバレッジと、全体の合計を `backend/build/reports/jacoco/test/jacocoTestReport.xml` から読んで code-summary.md に記録する。テストの件数も実測だけを書く。
  - 失敗したときは team.md の「不安定なテストと CI の失敗」の決まりで扱い、直さずに流し直す前にオーケストレーターに知らせる。
- [x] **Step 17**（NFR4。依頼者の決定 D5: B・D7: B）: 統合の前の E2E は流さない。
  - 読み方: この変更は画面に触れず、救済は E2E の初期管理者では働かない（E2E は実行ごとに新しい内部DB で初期管理者を作るため、有効な管理者でパスワードも一致する）。そのため、team.md の「E2E は、画面・認証に関わる変更を統合する前に手元で実行する」の「認証に関わる変更」に当たらないと読む。
  - この読み方と team.md の文言との差を code-summary.md に書く（8節にも記録した）。
  - E2E はリリース（配備）の前に流す（6.3 の引き継ぎ）。
  - E2E の生成物はこの段の中では作らない・消さない（project.md の学び 2026-10-03）。
- [x] **Step 18**（記録）: `code-summary.md`・`traceability.json`・`source-manifest.json` を書く。
  - `code-summary.md` には、計画との差、Step 2 の基準の結果、Step 11 の直す前の再現の出力、Step 13 のダイジェストと2つのアーキテクチャの記録、Step 16 のカバレッジの実測、「依頼者に確かめたいこと」の節（無ければ「無し」）を置く（project.md の学び 2026-09-28）。
  - `traceability.json` は 7節の予定のとおり。`source-manifest.json` には、この段で作った・変えた・消したアプリのファイルをすべて並べる（7節の見込み）。
- [ ] **Step 19**（コミットと統合。生成の担当はコミットしない）: 生成の後に、オーケストレーターが 5節のコミットを依頼者に提案し、承認を得てから行う（project.md の Change Control）。
  - 段の承認の場は、最後のコードのコミットの後、`aidlc/` の下だけの記録のコミットを積む前に開く（project.md の学び 2026-10-03）。
  - squash の統合の前に、監査ログを含む `aidlc/` の未コミットの変更をコミットしておく（project.md の学び 2026-10-03。`git restore` で `aidlc/` を戻さない）。
  - `fix/261004-safety-carryover` を `develop` へ **squash** で統合し（team.md。サブモジュールの更新が無いため fast-forward の例外に当たらない）、ブランチを消す。`origin` への push は依頼者が行う。

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

## 4. 依頼者の決定（計画の承認の前）

計画の承認の前に依頼者に確かめ、答えを本文に反映した（記録: `code-generation-questions.md` の D1〜D7）。未決の問いは残っていない。

| 問い | 答え | 反映した所 |
|---|---|---|
| D1 当たった条件を入れる列（FR1.6a） | A: `rejection_kind` | 1.2 の P1、Step 3 |
| D2 救済の置き場と、書き換えの口の決まり | A: `UserAccountService` と auth の受け手がリポジトリを直接使う。境界テストは変えず、`useradmin` を通らない2つ目の経路を受け入れて説明に書く | 1.2 の P2、Step 5・7 |
| D3 FR4.2 の直し方 | A: 二重の ERROR と確かめられたら `application.yaml` で Tomcat の `dispatcherServlet` のロガーを `OFF`。応答を書き始めた後に外へ出た例外のログが無くなることを計画と README に明記。FR4.1b の「再現できなければ止めて諮る」は残す | 1.2 の P4、Step 11・12・15 |
| D4 ダイジェストを付ける範囲（FR7.2） | A: 要件の5か所＋同じイメージのほかの出現すべて（`perf/README.md` の jre 3・otel-lgtm 1、`README.md` の jre 4・jdk 1、`docker/hikari-pool.sh` の `JDK_IMAGE`） | 1節の表、P6、Step 13、8節 |
| D5 統合の前の E2E | B: 流さない | Step 17、6.3、8節 |
| D6 救済でのパスワードの書き直し | A: 条件 `PASSWORD` に当たらない救済でも設定の値で書き直す | Step 5 |
| D7 D5: B と team.md の食い違い | B: 流さない。画面に触れず、E2E の初期管理者では救済が働かないため「認証に関わる変更」に当たらないと読み、読み方と team.md との差を計画と code-summary.md に書く。E2E はリリース（配備）の前に流す | Step 17、6.3、8節 |

## 5. コミットの区切りと統合（案）

生成の担当はコミットしない。生成の後に、オーケストレーターが次の区切りを依頼者に提案し、承認を得てから作業ブランチ `fix/261004-safety-carryover` に積む（project.md の Change Control）。メッセージは日本語。

- C1: 初期管理者の救済と、作成・救済の監査（Step 3〜9 と、Step 15 の README の救済の手順・監査の種類）
- C2: BUSY の L3・L4 のログの traceId の結び付きを確かめるテスト（Step 10）
- C3: フィルターの中の例外の ERROR を1行にする（Step 11・12。再現のテストと直しを同じコミットに入れる。project.md の Mandated）
- C4: イメージにダイジェストを付け、固定の決まりを Dependabot の設定に書く（Step 13・14）
- C5: 利用者の管理の画面の既知の点を README に書く（Step 15 の FR5.1 の部分）

統合は `develop` へ **squash** で1コミットにする（team.md。サブモジュールの固定先の更新が無いため、fast-forward の例外に当たらない）。件名の案: 「初期管理者の救済と監査、フィルターの中の例外の ERROR の二重の出力の直し、イメージのダイジェスト」。細かいコミットは作業ブランチと監査ログに残る。統合の前に Step 16 の verify が通っていること（統合の前の E2E は流さない。D5・D7: B）。

## 6. Build and Test 以降に引き継ぐこと

### 6.1 FR2 ログインの p95（Build and Test が持ち主。FR2.4）

- 今の版（統合した `develop`）のイメージを作り、使い捨ての環境（`docker/perf/compose.yaml`、`perf/README.md` の手順）で、前の Intent と同じ条件（場面 `loginSuccess`・同じ `VUS`・`DURATION`・同じ上限）で **3回以上** 流す。台本全体を `caffeinate -i` で包む（project.md の学び）。配備したアプリを止めるときは先に依頼者に伝える。
- 回ごとに p95 と、FR2.2a の条件（日時、イメージのタグとダイジェスト、colima の VM の CPU・メモリ、コンテナの上限、`VUS`・`DURATION`、同時に動いていたコンテナ、電源と `caffeinate -i`）を記録する。条件がそろわない回は判定から外し、理由を書く。
- 合否は (b) すべての回で p95 が 1 秒を下回ること。参考に (a) 幅と 939.6 ms が入るか。目標は緩めない（FR2.2・NFR7）。
- R-09 の手当て: 1回でも p95 が 1 秒以上なら、条件をそろえたまま回数を増やして（例: 合わせて 6 回）、ぶれか違いかを切り分ける。それでも 1 秒以上の回が残れば Not Met とし、依頼者に扱いを諮る。
- 停止の判定の前の版は流さない（FR2.3）。前の Intent の完了の目安から変えたことを Build and Test の記録に書く。
- このイメージ作りで、`Dockerfile` のダイジェスト付きの `FROM` からアプリのイメージを作れて、使い捨ての環境で起動の確かめ（健全性）が通ることも記録する（FR7 の判定）。

### 6.2 FR6 `team.md` の「12 パッケージ」（Build and Test。FR6.2）

- この計画は一覧のパッケージを外さないため、直した後の数は 7 個のまま（`access.service`・`audit.repository`・`common.error.domain`・`common.error.service`・`common.health`・`common.i18n.domain`・`common.web`）。
- 直す文言の案（依頼者の確認を得てから書く）: 「2026-09-29 の時点で 12 パッケージ」を「2026-10-04 の時点で 7 パッケージ（`access.service`・`audit.repository`・`common.error.domain`・`common.error.service`・`common.health`・`common.i18n.domain`・`common.web`）」に替える。生成の途中で一覧が変わったときは、その後の数にする。

### 6.3 そのほか

- Step 16 の verify の後に `develop` へ統合した版の CI の結果を確かめる（team.md）。
- FR8（配備）は Deployment Pipeline・Deployment Execution の段で行う。FR8.2 の「初期管理者が救済の条件に当たりうるか」の確かめ（依頼者に尋ねる・内部DB の複写で停止と印の有無だけを読む）と、救済が働く見込みのときの了承は、配備の前に行う。
- E2E（`./gradlew e2eTest`）は統合の前には流さず、リリース（配備）の前に流す（D5・D7: B）。Build and Test か Deployment Pipeline の段で、配備の前に1回流す手順に入れる。E2E の起動のログで、救済の WARN「初期管理者を救済しました」が0件であること（件数だけを見る）も確かめる（NFR4）。
- NFR3（起動の時間）の目安として、Build and Test で、救済の判定が入った版の起動のログの起動の時間を、前の版の配備の記録の値と並べて記録する（照合1回の確かめは Step 9 の単体テストで行う）。

## 7. traceability と source-manifest の予定

### 7.1 traceability.json（Step 18）

zero-Unit で設計の段が無いため、要件の ID を直接並べる（段の定義の「incremental scope は FR・NFR を直接」）。`OK` の target は、実装かテストの1つのファイル。

| ID | 状態の予定 | target の予定 |
|---|---|---|
| FR1.1・FR1.2・FR1.3 | OK | `backend/src/main/java/cherry/mastersmith/user/service/UserAccountService.java` |
| FR1.2a | OK | `backend/src/test/java/cherry/mastersmith/user/service/InitialAdminRescueIT.java` |
| FR1.4・FR1.7 | OK | `backend/src/main/java/cherry/mastersmith/user/service/InitialAdminInitializer.java` |
| FR1.5・FR1.6・FR1.6a | OK | `backend/src/main/java/cherry/mastersmith/audit/domain/AuditEventFactory.java` |
| FR1.6b | OK | `backend/src/test/java/cherry/mastersmith/user/service/InitialAdminRescueIT.java` |
| FR1.8 | OK | `backend/src/main/java/cherry/mastersmith/audit/service/AuditEventListener.java` |
| FR1.9 | OK | `README.md` |
| FR2.1〜FR2.4 | Deferred | Build and Test（6.1） |
| FR3.1 | OK | `backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminBusyLogTraceIT.java` |
| FR3.2 | N/A | 負荷の試験では確かめない（Q4） |
| FR4.1・FR4.1a | OK | `backend/src/test/java/cherry/mastersmith/common/error/web/FilterExceptionErrorLogIT.java` |
| FR4.1b・FR4.3 | OK または N/A | 再現できた・見立てどおりなら N/A（理由を書く）。当たったときは code-summary.md の記録の節を理由に Deferred |
| FR4.2 | OK | `backend/src/main/resources/application.yaml`（二重の ERROR と確かめられなかったときは Deferred とし、理由を書く） |
| FR5.1 | OK | `README.md` |
| FR6.1・FR6.2 | Deferred | Build and Test（6.2） |
| FR7.1 | N/A | 仕組みを変えない（Q7: C） |
| FR7.2・FR7.4 | OK | `Dockerfile` |
| FR7.3 | OK | `.github/dependabot.yml` |
| FR8.1〜FR8.3 | Deferred | Deployment Pipeline・Deployment Execution |
| NFR1 | OK | `backend/src/test/java/cherry/mastersmith/user/service/InitialAdminSecretLeakIT.java` |
| NFR2 | OK | `backend/src/test/java/cherry/mastersmith/user/service/InitialAdminRescueIT.java` |
| NFR3 | OK | `backend/src/test/java/cherry/mastersmith/user/service/UserAccountServiceTest.java` |
| NFR4・NFR5 | Deferred | Build and Test（Step 16 の実測を Build and Test が判定する） |
| NFR6 | OK | `backend/src/test/java/cherry/mastersmith/common/error/web/FilterExceptionErrorLogIT.java` |
| NFR7 | Deferred | Build and Test（6.1） |

### 7.2 source-manifest.json に載せる見込みのパス

- 新規: `backend/src/main/java/cherry/mastersmith/user/domain/InitialAdminRescueCondition.java`・`InitialAdminRescuedEvent.java`・`InitialAdminCreatedEvent.java`、`backend/src/main/java/cherry/mastersmith/user/service/InitialAdminRescueResult.java`、`backend/src/main/java/cherry/mastersmith/auth/service/InitialAdminRescueListener.java`
- 変更: `backend/src/main/java/cherry/mastersmith/user/service/UserAccountService.java`・`InitialAdminInitializer.java`・`InitialAdminProperties.java`、`backend/src/main/java/cherry/mastersmith/audit/domain/AuditEventType.java`・`AuditEvent.java`・`AuditEventFactory.java`、`backend/src/main/java/cherry/mastersmith/audit/service/AuditEventListener.java`
- FR4.2: `backend/src/main/resources/application.yaml`（二重の ERROR と確かめられたときだけ）
- テスト（新規）: `backend/src/test/java/cherry/mastersmith/user/domain/InitialAdminRescueConditionTest.java`、`backend/src/test/java/cherry/mastersmith/user/service/InitialAdminPropertiesTest.java`・`InitialAdminRescueIT.java`・`InitialAdminSecretLeakIT.java`、`backend/src/test/java/cherry/mastersmith/user/testsupport/FailingInitialAdminRescueConfig.java`、`backend/src/test/java/cherry/mastersmith/auth/service/InitialAdminRescueListenerTest.java`、`backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminBusyLogTraceIT.java`、`backend/src/test/java/cherry/mastersmith/common/error/web/FilterExceptionErrorLogIT.java`
- テスト（変更）: `backend/src/test/java/cherry/mastersmith/user/service/InitialAdminInitializerTest.java`・`UserAccountServiceTest.java`・`InitialAdminIT.java`、`backend/src/test/java/cherry/mastersmith/audit/domain/AuditEventFactoryTest.java`、`backend/src/test/java/cherry/mastersmith/audit/service/AuditEventListenerTest.java`
- 環境と文書: `Dockerfile`・`compose.yaml`・`perf/README.md`・`README.md`・`.github/dependabot.yml`・`docker/hikari-pool.sh`

## 8. 要件との差／計画で決めたこと

- FR1.2a の ERROR は、例外のクラスの名前だけを載せ、スタックトレースを付けない（team.md の「想定外は ERROR でスタックトレース付き」との差。H2 の例外の文に行の値が入りうるため。Step 6）。
- FR1.6a の条件の列は `rejection_kind`（P1・D1: A）。列の名前（拒否の種類）と使い方がずれる。
- 救済は `useradmin` を通らずに印・停止・失敗回数・トークンを書き換える2つ目の経路になる（P2・D2: A）。境界テストは変えない。
- FR4.2 の直し（D3: A）で、応答を書き始めた後にサーブレットの外へ出た例外のログは無くなる（P4。README と `application.yaml` のコメントに明記）。
- FR3.1 の一部は、既存の `UserAdminSecretLeakIT` がすでに確かめている（同じ `traceId` のアプリの WARN が2行）。この計画は FR3.1 の形（L3 ごとに L4 がちょうど1行）で確かめる新しいテストを足す（Step 10）。
- FR4.1 の「同じ `traceId` で2行」は、Tomcat の行が `traceId` を持たない見込みのため、要求を1本だけ送った区間で結び付けて数える形になりうる（P3）。結果は Step 11 で記録する。
- ダイジェストを付ける範囲は、要件 FR7.2 の5か所より広い（D4: A）。同じイメージのほかの出現（`perf/README.md` の `eclipse-temurin:25.0.4_7-jre-noble` 3か所と `grafana/otel-lgtm:0.34.0` 1か所、`README.md` の `eclipse-temurin:25.0.4_7-jre-noble` 4か所と `eclipse-temurin:25.0.4_7-jdk-noble` 1か所、`docker/hikari-pool.sh` の `JDK_IMAGE`）にも同じダイジェストを付け、Dependabot が見ない固定先の一覧（P6）に入れる。
- 統合の前の E2E は流さない（D5・D7: B）。team.md の「E2E は、画面・認証に関わる変更を統合する前に手元で実行する」に対し、この変更は画面に触れず、救済は E2E の初期管理者では働かない（有効な管理者でパスワードも一致する）ため「認証に関わる変更」に当たらないと読んだ。この読み方と team.md の文言との差を code-summary.md にも書く。E2E はリリース（配備）の前に流す（6.3）。
- R-08・R-09 はリスクとして受け入れられた指摘で、P8 のとおり手当てする（R-08 は Step 9 の判定と Step 15 の README、R-09 は 6.1）。

## Sources

- 要件（承認済み）: `aidlc/spaces/default/intents/261004-safety-carryover/inception/requirements-analysis/requirements.md`、質問と答え `requirements-analysis-questions.md`（同じディレクトリ）、承認の場の記録（監査ログ `aidlc/spaces/default/intents/261004-safety-carryover/audit/sakura-local-4e42a93f87ce.md` の Requirements Analysis の Gate Approved。R-08・R-09 は Accepted risk）
- コード知識ベース: `aidlc/spaces/default/codekb/mastersmith2/`（K-25〜K-31）、開発担当のスキャン `aidlc/spaces/default/intents/261004-safety-carryover/inception/reverse-engineering/developer-scan.md`
- 確かめたコード: `backend/src/main/java/cherry/mastersmith/user/service/InitialAdminInitializer.java`・`InitialAdminProperties.java`・`UserAccountService.java`・`UserPreferencesService.java`、`user/repository/UserRepository.java`、`auth/service/LoginAttemptStateInitializer.java`・`RefreshTokenRevocationService.java`・`LockAdministrationService.java`、`auth/web/AccessTokenAuthenticationProvider.java`、`audit/domain/AuditEvent.java`・`AuditEventType.java`・`AuditFailureReason.java`・`AuditEventFactory.java`、`audit/service/AuditEventListener.java`、`useradmin/service/UserAdminService.java`、`common/error/web/ErrorPathController.java`、`common/observability/TraceAspect.java`、`backend/src/main/resources/application.yaml`・`db/migration/V4__u4_audit_event.sql`・`V6__u4_dsl_audit_columns.sql`・`V7__u2_user_preferences.sql`、`backend/build.gradle.kts`、テストの `ArchitectureTest.java`・`auth/AuthBoundaryArchitectureTest.java`・`audit/AuditBoundaryArchitectureTest.java`・`useradmin/UserAdminBoundaryArchitectureTest.java`・`user/service/InitialAdminIT.java`・`useradmin/web/UserAdminBusyApiIT.java`・`useradmin/web/UserAdminSecretLeakIT.java`・`audit/domain/AuditEventTest.java`、`Dockerfile`・`compose.yaml`・`docker/perf/compose.yaml`・`docker/hikari-pool.sh`・`perf/README.md`・`README.md`・`.github/dependabot.yml`
- 書式の参考: `aidlc/spaces/default/intents/261003-user-admin-followup/construction/code-generation/code-generation-plan.md`・`unit-test-instructions.md`
- 決まり: `aidlc/spaces/default/memory/org.md`・`team.md`・`project.md`・`phases/construction.md`
