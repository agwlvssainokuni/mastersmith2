# NFR Requirements の質問 — u1-user-suspension

単位 U1（`aidlc/spaces/default/intents/260930-user-admin/inception/units-generation/unit-of-work.md`、種類 library）の非機能要件のための質問です。library の単位のため、成果物は `security-requirements.md`・`tech-stack-decisions.md`・`traceability.json` の3つです（`performance-requirements.md` などは作らず、性能と信頼性の前提は `security-requirements.md` の別の節に置きます）。確かめた資料は、この単位の承認済みの機能設計 `construction/u1-user-suspension/functional-design/`（`functional-spec.md` の 2.1・2.6・2.7・6節・7節「後の段へ渡すこと」・承認の場の決定、`rules.md`、レビューの記録の新しい Minor R-06）、要件 `inception/requirements-analysis/requirements.md` の NFR1〜NFR11、契約 `inception/contract-design/contract-summary.md` の C1・C7、ADR `inception/domain-design/decisions.md` の ADR-007・ADR-008、Bolt の計画 `inception/delivery-planning/bolt-plan.md` の B1、前の Intent の NFR（`aidlc/spaces/default/intents/260922-auth-audit-base/construction/u2-authentication/nfr-requirements/security-requirements.md` の NFR4.2「照合と SQL の回数を確かめ、応答時間そのものは比べない」、`aidlc/spaces/default/intents/260925-user-management/construction/u2-user-preferences/` と `u3-invitation/` の NFR10 の扱い）、既存のコード（`backend/src/main/resources/application.yaml` のアクセストークン 5 分・リフレッシュトークン 24 時間・削除の残す期間 7 日、`auth/repository/RefreshTokenRepository.java` の `deleteExpiredBefore`、`db/migration/V3__u2_authentication.sql` の `ix_refresh_tokens_user_id`、`backend/build.gradle.kts` の `packagesJudgedByTotal`、`auth/testsupport` の `CountingPasswordEncoder`・`SqlStatementCounter`、`docker/monitoring/provisioning/alerting/mastersmith.yaml`）、決まり `aidlc/spaces/default/memory/team.md`・`project.md` です。

## 設計の要点（案）

上流とコードの確認から導ける、この単位の非機能要件の見通しです。質問の答えで決まる点は（Qn）と書きます。ID は成果物での枝番の見込みで、単位の中で .1 から振ります。

### 性能

1. **既存の3つの入口の目標を保つ（NFR5）**: ログインと更新の既存の目標（同時 10 件で p95 1 秒）を変えない。停止の判定は、3つの入口がすでに読んでいる利用者の要約の suspended を見るだけで、内部DB の問い合わせを増やさない（BR1.3）。アクセストークンの認証は今も要求ごとに利用者の要約を読んでおり、読み取りの回数は変わらない。
2. **停止中のログインの応答時間（NFR2）**: 停止中とパスワードの誤りは、照合1回・本人の行の排他つきの読み取り1回・更新1回・出来事1件で、通る道が同じ（違いは書き戻す値と出来事の理由だけ）。本人の行とダミーの行の違いは、存在しないメールアドレスの既存の経路の話で、今回足すものではない。確かめ方は（Q1）。
3. **まとめての無効化の性能（ADR-007 の項目2）**: 利用者 ID の索引（`ix_refresh_tokens_user_id`）で引く1回の更新。行は有効期限（24 時間）の後 7 日で定期の削除に消えるため、1人の行の数には上限がある（見込みは 4 で書く）。目標と測る条件は（Q2）。
4. **1人の利用者の行の数の見込み**: 未無効の行は、ログアウトせずに終えた会話ごとに1件残り、最長で約 8 日残る。1日 10 回ログインしてログアウトしない利用者でも約 80 件。更新のたびに古い行は無効になるため、無効の行は使い方によって数百件になりうるが、更新の問い合わせは未無効の行だけを書き換える。

### セキュリティ

5. **3つの入口のサーバー側の判定（NFR1、PM の Mandated）**: ログインの照合・トークンの更新・アクセストークンの認証のすべてで、サーバー側で停止を判定する。入口ごとに、停止中は拒否、停止を解いた直後は受け付けることをサーバー側のテストで確かめる（`team.md` の利用停止の必須テスト）。
6. **列挙の防止（NFR2）**: 停止中の拒否は、ログインは 401 AUTHENTICATION_FAILED、更新は 401 REFRESH_FAILED、アクセストークンの認証は 401 AUTHENTICATION_REQUIRED で、入口ごとのほかの失敗と同じ応答にする。応答に停止を示す値を載せない（BR6.1）。読み書きの回数がパスワードの誤りとそろうことを、既存の `LoginServiceTest` の形（照合と読み書きの回数を数える）で確かめる。時間の扱いは（Q1）。
7. **止める前に出したトークン（`team.md` の必須テスト）**: リフレッシュトークンは止めるときにまとめて無効にし、停止を解いても戻さない（BR3.3・BR5.4）。アクセストークンは停止中は拒否し、解いた後は有効期限（5 分）まで使える（BR4.3）。どちらも決めた側の動作を明示したテストにする。時刻は注入した時計で動かし、`sleep` と実時刻に頼らない。
8. **受け入れた隙（BR5.5、R-03）**: 止める確定の前に同時に走った更新やログインの照合が作った新しいトークンは残りうる。残っても、次のアクセストークンの認証と更新で拒否される。判定の読み直しは足さない（承認の場の決定）。
9. **個人に関する値と秘密（NFR3、PM の Forbidden）**: 停止中のログインの出来事とアプリのログにメールアドレス・パスワード・トークンを出さない（BR2.6）。まとめての無効化のログは DEBUG で利用者 ID と件数だけ（BR5.3）。C1 の口の引数と戻り値は利用者 ID・真偽・件数だけで、TRACE のログに出ても個人に関する値は出ない。既存の `*SecretLeakIT` に V9 の列を足すかはコード生成で確かめる（機能設計の7節）。
10. **管理の API の監査の扱い（C7、D2）**: アクセストークンの認証の失敗の区分 `USER_SUSPENDED` はアプリの中だけの値で、応答とアクセスの拒否の監査には出さない（有効期限切れと同じ扱い）。停止中のログインは監査に LOGIN_FAILED・ACCOUNT_SUSPENDED が残る。
11. **構造の決まり（NFR11）**: `user` は `auth` を知らない。まとめての無効化は `auth.service` の新しい `RefreshTokenRevocationService` に置き、既存の ArchUnit の境界テストを緩めない。
12. **静的解析**: 既存の関門（SpotBugs の priority 1、`SQL_`・`PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION`）をそのまま通す。足す問い合わせは名前つきの引数の JPQL・ネイティブの問い合わせにし、文字列をつなげない。

### 規模

13. **想定の規模**: 前の Intent と同じ利用者 最大 50 名・同時 10 名を1台で処理する。利用者の行に列を1つ足すだけで行の数は増えない。監査の行は停止中のログイン1回に1行で、今のログインの失敗と同じ量。

### 信頼性

14. **監査の書き込みの失敗で応答を変えない**（BR2.5、既存の AuditLog の決まり）。停止中のログインの監査は、既存のログインの失敗と同じく確定の後に2本目の接続で記録する。経路はパスワードの誤りと同じで、接続の使い方（NFR6）は変わらない。新しい2本目の経路は無い（更新とアクセストークンの認証は監査を出さない）。
15. **C1 の口のトランザクション**: `setSuspended` と `revokeAllRefreshTokens` は MANDATORY で、呼び出し元のトランザクションの外では想定外の誤りになる。止める操作が巻き戻れば停止もトークンの無効化も戻る（BR1.5・BR5.2）。
16. **V9 の後方互換（NFR10）**: `users.suspended` を必須・既定 false で足す前進のみの変更。1つ前の版のアプリが V9 の後の内部DB で起動し、ログイン・更新・監査・登録の完了による利用者の作成（列を知らずに書き込み、既定の値が入る）が今までどおり動くことを目標にする。確かめ方は前の Intent の V7・V8 と同じ2段（自動の結合テストと、deployment-execution の戻しの練習）を見込み、作りと手順は NFR 設計・基盤の設計で決める（機能設計の7節）。
17. **戻した間は停止が効かない（R-02、受け入れた制約）**: 1つ前の版のイメージで戻すと、戻している間は停止中の利用者もログインできる（止めたときに無効にしたリフレッシュトークンは無効のまま）。deployment-pipeline の戻しの手順に、戻す前に停止中の利用者を確かめる手順を書く（申し送り）。

### 観測

18. **新しい指標と警報は足さない**: 停止中のログインは既存のログインの HTTP の指標と p95 の警報（`ms-login-p95`）に含まれ、理由は監査ログで追える。失敗の理由ごとの警報は今も無い。ログは既存の決まり（4xx は WARN 以下でスタックトレースなし、キーと値の構造化ログ、トレースID を含む）。

### 技術

19. **新しい依存は足さない**: 既存の Spring Boot・Spring Security・Spring Data JPA・Flyway・H2・ArchUnit で作る。性質ベースのテスト（jqwik）を当てる純粋な関数は、この単位には見当たらない（区分の変換は列挙を尽くす単体テストで足りる）。

### テストとカバレッジ（NFR9）

20. **パッケージごとの下限に戻す範囲**: 手を入れる `auth.domain`・`auth.repository`・`access.domain` を `packagesJudgedByTotal` から外し、行 80%・分岐 70% を満たす。2026-10-01 の実測は `auth.domain` が行 97.9%・分岐 100.0%、`auth.repository` が行 93.9%・分岐 50.0%（足りない分岐は `lockDummyForUpdate` の「空いたダミーの行が無い」側で、ダミーの行8つをすべて排他した状態を作るテストが要る）、`access.domain` は行・分岐とも 100%。ほかに実際に手を入れた一覧のパッケージがあれば同じく外す。値は `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で実測して記録する。

## 決まっていること（質問にしない）

| 決まっていること | 出典 |
|---|---|
| 性能の目標は同時 10 件の要求で p95 を測る。想定の規模は利用者 最大 50 名・同時 10 名 | 前の Intent の NFR、要件 NFR5 |
| 停止中のログインは照合・排他つきの読み取り・更新・出来事の回数をパスワードの誤りとそろえ、テストで回数を確かめる | ADR-008、BR2.2・BR2.3、機能設計 2.1、`bolt-plan.md` の B1 |
| ログインの時間をそろえる仕組みは、前の Intent から「照合と SQL の回数を確かめ、応答時間そのものは比べない」形 | 前の Intent の U2 の NFR4.2 |
| 3つの入口の拒否はほかの失敗と同じ応答、停止を示す値を載せない | 要件 NFR2、BR6.1 |
| 止める前のリフレッシュトークンは解いても戻さない、アクセストークンは解いた後は期限まで使える | BR3.3・BR4.3・BR5.4 |
| 止める確定と同時の更新・ログインの照合が作ったトークンが残る隙は受け入れる | BR5.5、承認の場の決定 R-03 |
| C1 の口の置き場・型・トランザクションの属性（UserAccountService・RefreshTokenRevocationService、MANDATORY） | 機能設計 2.5・2.6、承認の場の決定 R-01 |
| setSuspended の後に呼び出し元が先に読み込んだエンティティを使わないことの確かめは U3 のコード生成の計画で扱う | レビューの R-06（承認の場で申し送り） |
| スキーマの変更は V9 の前進のみ、1つ前の版のアプリが動く後方互換。確かめ方は NFR 設計・基盤の設計 | 要件 NFR10、`team.md` の Deployment、機能設計の7節 |
| 1つ前の版に戻した間は停止が効かない制約を受け入れ、戻す前に停止中の利用者を確かめる手順を deployment-pipeline に申し送る | 承認の場の決定 R-02 |
| 手を入れた `packagesJudgedByTotal` のパッケージは一覧から外し、パッケージごとの下限の対象に戻す。一覧は増やさない、除外も増やさない | `team.md` の Testing Posture、機能設計の6節・差 D1 |
| パスワード・トークンを出さない、メールアドレスをアプリのログとエラー応答に出さない | `project.md` の Forbidden、要件 NFR3 |
| 停止中の利用者のメールアドレスへの招待が登録済みの拒否になる回帰テスト1件と、書いた後の読み取りの結合テストはコード生成で入れる | 機能設計の7節（R-04・R-05） |
| この Intent の流れには performance-validation の段がある（性能の目標の測定の持ち主） | `aidlc-state.md` |
| 新しい依存は足さない | 機能設計（既存の部品だけで作る） |
| 画面のアクセシビリティ（NFR7）・多言語（NFR8）はこの単位に当てはまらない（画面を持たず、新しいエラーの説明文も無い）。同時の操作（NFR4）は最後の管理者の保護と失敗回数の取り消しが U3 の持ち物で、U1 は受け入れた隙（BR5.5）だけ | 単位の分割 `unit-of-work.md`、BR6.1 |

---

## Q1. 停止中のログインの応答時間がほかの失敗とそろうことを、どう確かめますか？

理由: 機能設計の7節がこの段に渡した論点です（ADR-007 の項目4）。機能設計で、停止中とパスワードの誤りの読み書きの回数をそろえられることは確かめられました（ADR-007 の項目4 の「成り立たなければ時間の差を測る」には当たりません）。停止中とパスワードの誤りは同じ本人の行を読み書きし、違いは書き戻す値と出来事の理由だけです。応答時間の大半は bcrypt の照合（cost 12 で1回 約 278 ms）で、手元の PC の k6 では、同時 10 件のログインの p95 が成功 940 ms・失敗 926 ms でした（`aidlc/spaces/default/intents/260923-colima-spec-up/construction/build-and-test/test-results.md`）。残る違い（書き戻す値と理由）は、この測定の揺れより小さい見込みです。

A. 回数だけで確かめ、時間は比べない（前の Intent の NFR4.2 と同じ）。`CountingPasswordEncoder`・`SqlStatementCounter` で、停止中（パスワードの正誤・ロック中を含む）とパスワードの誤りの照合と読み書きの回数が同じことをテストで確かめる（推奨。道が同じため回数で十分で、時間を比べると揺れで不安定になる）
B. A に加え、performance-validation の k6 で、停止中・パスワードの誤り・存在しないメールアドレスのログインを同じ条件で流し、p95 と中央値の差を記録する（合否にしない。差が目立てば報告する）
C. B の差を合否にする（例: 停止中とパスワードの誤りの p95 の差が 50 ms 以内）
X. Other (please specify)

[Answer]: A

## Q2. リフレッシュトークンのまとめての無効化の性能を、どの目標と条件で確かめますか？

理由: 機能設計の7節がこの段に渡した論点です（ADR-007 の項目2「1人のトークンの数に比例し、問題にならない見込み」）。まとめての無効化は U3 の止める操作の中で呼ばれるため、時間は止める操作の応答時間（要件 NFR5 の p95 1 秒、U3 の持ち物）に含まれます。1人の未無効の行は、1日 10 回ログインしてログアウトしない利用者でも約 80 件（定期の削除で約 8 日で消える）の見込みです。

A. U1 単独の数の目標は置かず、U3 の止める操作の p95 1 秒（同時 10 件）に含める。performance-validation の k6 で止める操作を測るとき、対象の利用者に未無効の行 100 件と無効の行 1,000 件を持たせた悪い側の条件で測る。U1 の結合テストでは、件数（100 件すべてが無効になり、ほかの利用者の行は変わらない）だけを確かめ、時間は比べない（推奨。見込みの上限より悪い条件で、目標を持つ U3 の操作として1回で測れる）
B. 見込み（索引で引く1回の更新）で足りるとし、件数を増やした条件では測らない。U3 の止める操作は通常の条件（トークン数件）で測る
C. A に加え、U1 の結合テストで 1,000 件の無効化を時間の上限（例 1 秒）つきで確かめる（`./gradlew verify` の中で時間を比べるため、負荷の高い CI で不安定になりうる）
X. Other (please specify)

[Answer]: A

---

## Consolidated Summary Confirmation

答えのまとめと、U1 の NFR 要件の計画:

- 設計の要点（案）の 1〜20 のとおりに作る。成果物は library の3つ（security-requirements・tech-stack-decisions・traceability）。
- Q1 A: 停止中のログインがほかの失敗とそろうことは、照合と読み書きの回数だけをテストで確かめ、時間は比べない（前の Intent と同じ形）。
- Q2 A: リフレッシュトークンのまとめての無効化には U1 だけの目標を置かず、U3 の止める操作の p95 1 秒に含める。Performance Validation では、未無効の行 100 件・無効の行 1,000 件の悪い側の条件で測る。U1 の結合テストは件数だけを確かめる。
- NFR10（V9 の後方互換）は、前の Intent の V7・V8 と同じ2段（自動の結合テストと戻しの練習）で確かめる見込み。細部は NFR 設計・基盤の設計で決める。
- `auth.domain`・`auth.repository`・`access.domain` を `packagesJudgedByTotal` から外す。
- 新しい依存は足さない。
- 申し送り:
  - 戻した間は停止が効かない制約（機能設計の R-02）は deployment-pipeline へ。
  - 文脈を空にした後の扱い（機能設計の R-06）は U3 のコード生成の計画へ。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
