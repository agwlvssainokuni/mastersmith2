# NFR Requirements の質問 — u2-user-preferences

単位 U2（`aidlc/spaces/default/intents/260925-user-management/inception/units-generation/unit-of-work.md`、種類 service）の非機能要件のための質問です。service の単位のため、成果物は `performance-requirements.md`・`security-requirements.md`・`scalability-requirements.md`・`reliability-requirements.md`・`observability-requirements.md`・`tech-stack-decisions.md`・`traceability.json` のすべてです。

読んだ上流:

- この単位の承認済みの機能設計 `construction/u2-user-preferences/functional-design/`（`rules.md`・`functional-spec.md`・`entities.md`・`traceability.json`）。6節で、今のパスワードの誤りが続いたときの制限と、応答時間の目標（要件の [assumption] の 1 秒）がこの段へ渡されている
- 要件 `inception/requirements-analysis/requirements.md` の NFR1〜NFR11 と 7節の未解決の点、契約 `inception/contract-design/contract-summary.md` の C2・C3・C4・C8、ADR `inception/domain-design/decisions.md`（ADR-003・ADR-004・ADR-008）、Bolt の計画 `inception/delivery-planning/bolt-plan.md` の B2 の完了の条件
- 前の Intent の NFR `aidlc/spaces/default/intents/260922-auth-audit-base/construction/u2-authentication/nfr-requirements/`（ログイン・更新の p95 1 秒、bcrypt、ロック、IP ごとの回数の制限はしない）と、実測の記録 `aidlc/spaces/default/intents/260923-colima-spec-up/construction/build-and-test/test-results.md`（cost 12 の照合1回 約 278 ms、同時 10 件のログインの p95 は CPU 4 で成功 940 ms・失敗 926 ms、余裕 60〜75 ms）
- 既存のコード `backend/src/main/resources/application.yaml`（bcrypt の cost 12、ロック 5 回・30 分、接続プールの上限 30・借りる待ち 5 秒）、`backend/build.gradle.kts` の `packagesJudgedByTotal`、`audit/service/AuditEventListener.java`（確定の後に `REQUIRES_NEW` で2本目の接続を借りる）、`user/service/UserAccountService.java`・`UserAccountConfig.java`
- 決まり `aidlc/spaces/default/memory/team.md`（Testing Posture）・`project.md`（Forbidden・Mandated・Corrections）

## 設計の要点（案）

上流とコードの確認から導ける、この単位の非機能要件の見通しです。質問の答えで決まる点は（Qn）と書きます。ID は成果物での枝番の見込みです。

### 性能

1. **プリファレンスの取得と保存（NFR6）**: 同時 10 件の要求で、GET・PUT `/api/me/preferences` の p95 が 1 秒以内（要件の [assumption] をそのまま目標にする）。bcrypt を使わず、内部DB の利用者の1行の読み書きだけのため、余裕は大きい見込み。
2. **パスワードの変更（NFR6）**: 目標と測る負荷は（Q1）。
3. **ログインと更新の応答の広げ（C3、BR6.1）**: 前の Intent の目標（同時 10 件で p95 1 秒）を保つ。4つの値は応答を作るときの既存の利用者の読み取りから取り、問い合わせを増やさない。
4. **bcrypt の cost は既定の 12 のまま変えない**（前の Intent の NFR の決定）。
5. **接続の持ち方（NFR5 の考え方）**: パスワードの照合と新しいパスワードのハッシュ（1回 約 278 ms）はトランザクションの外で計算し、内部DB の接続を持つのはハッシュの読み取りと書き込みの短い間だけにする。細部は NFR 設計で決める。
6. **測り方と持ち主**: 性能の目標は、この Intent の流れにある performance-validation の段が、使い捨ての環境の k6 で測る（`project.md` の Testing Posture）。k6 の場面にプリファレンスの取得・保存とパスワードの変更（成功・今のパスワードの誤り）を足す。

### セキュリティ

7. **認可（NFR4、BR8.1）**: 3本の API は、未認証は 401、管理者でないログインした利用者は 200（パスワードの変更は 204）をサーバー側のテストで確かめる。対象の利用者はアクセストークンの本人だけで決め、URL・本文で利用者を受け取らない。
8. **CSRF**: 3本はクッキーではなく `Authorization` のベアラーで認証するため、トークンの更新・ログアウトのような Origin の確かめは足さない（既存の `/api/` の扱いのまま）。
9. **秘密（NFR2、BR7.4・BR8.4）**: パスワード（今・新しい・確かめ）・ハッシュ・トークンを、ログ・監査ログ・トレースの属性・エラー応答に含めない。メールアドレスをアプリのログとエラー応答に含めない。既存の `*SecretLeakIT` の形で PASSWORD_CHANGED の成功と失敗を確かめ、監査の列の一覧に V7 の2列を足す。
10. **入力の上限**: 要求の本文は既存の上限 1MB のまま。氏名は 254 コードポイント、パスワードは UTF-8 で 72 バイト（既存の PasswordPolicy）。72 バイトを超える今のパスワードは照合に渡さず不一致とする（BR4.2）。
11. **今のパスワードの誤りが続いたときの制限**: （Q2）。
12. **静的解析**: 既存の関門（SpotBugs の priority 1、`SQL_`・`PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION`）をそのまま通す。

### 拡張性

13. **想定の規模**: 前の Intent と同じ利用者 最大 50 名・同時 10 名を1台で処理する。利用者の行に4列を足すだけで、行の数は増えない。監査の行はパスワードの変更1回に1行（今のパスワードの誤りを含む）で、頻度は低い。監査の保存期間は既存のまま。

### 信頼性

14. **監査の書き込みの失敗で操作を失敗させない**（BR7.3、既存の AuditLog の決まり）。
15. **2本目の接続（NFR5、`project.md` の Corrections）**: パスワードの変更の成功は、確定の後の監査の記録で2本目の接続を借りる経路になる（`AuditEventListener` の AFTER_COMMIT と `REQUIRES_NEW`）。同時 10 件で最大 20 本で、上限 30 に収まる見込みだが、見積もりだけで済ませず、6. の k6 で、接続を借りる待ちの時間切れの累計が 0 であることを確かめる（`project.md` の Testing Posture の測り方）。
16. **V7 の後方互換（NFR10、BR9.1）**: 前進のみ。1つ前の版のアプリが V7 の後の内部DB で起動できることを目標にする（Flyway の既定は知らない新しい移行を無視し、Hibernate の validate は余分な列を許す見込み。1つ前の版が利用者を作るのは、利用者が1人もいないときの初期管理者の作成だけ）。確かめ方は NFR 設計・基盤の設計、V7 を当てる前の内部DB のバックアップは配備の段で扱う（機能設計の6節）。
17. **想定外の誤り**: 内部DB の障害などは既存の 500 の扱い（例外のメッセージを載せない）。

### 観測

18. **新しい指標と警報は足さない**: 3本の API の応答時間と状態コードは既存の HTTP の指標で取れ、パスワードの変更の成功と誤りは監査ログで追える。ログは既存の決まり（4xx は WARN 以下でスタックトレースなし、キーと値の構造化ログ、トレースID を含む）。（Q2 で制限を入れるときは、拒否もアプリのログの WARN と監査に残す）

### 技術

19. **新しい依存は足さない**: 既存の Spring Boot・Spring Security（`BCryptPasswordEncoder`）・Spring Data JPA・Flyway・H2・jqwik（DisplayName の性質ベースのテスト）・ArchUnit で作る。

### テストとカバレッジ（NFR9）

20. **パッケージごとの下限に戻す範囲**: 手を入れる `packagesJudgedByTotal` のパッケージ（見込みは `user.domain`・`user.repository`・`user.service`・`audit.domain`・`audit.repository`・`audit.service`、応答を広げる `auth.service`・`auth.web`。ほかに実際に手を入れたものも含める）を一覧から外し、行 80%・分岐 70% を満たす。実測の値は `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で取り、記録する。前の記録で単独では下回っていた `audit.service`（行 77.2%）はテストを足して上げる。
21. **必須のテスト**: `team.md` のパスワードの変更の必須テスト（今のパスワードの確かめ、規則の境界、変更の後のリフレッシュトークンが使えること）、認可（401・200）、監査（成功と失敗の必須の項目）、秘密の漏えい。

## 決まっていること（質問にしない）

| 決まっていること | 出典 |
|---|---|
| 性能の目標は同時 10 件の要求で p95 を測る。想定の規模は利用者 最大 50 名・同時 10 名 | 前の Intent の U2 の NFR1.1・NFR1.6 |
| プリファレンス・パスワードの変更の API は p95 1 秒以内が [assumption] で、この段で確かめる | 要件 NFR6、機能設計の6節 |
| パスワードのハッシュは bcrypt（cost 12）、パスワードは 12 コードポイント以上・UTF-8 で 72 バイト以内 | 前の Intent の NFR（Q1・Q5）、`application.yaml` |
| IP ごとのログインの回数の制限はしない（利用者 50 名の社内向け） | 前の Intent の U2 の NFR の Q3 B |
| パスワードを変えても、リフレッシュトークンとアクセストークンは無効にしない | 要件 FR6.3、`project.md` の DECIDED、BR4.5 |
| 今のパスワードの誤りは 400 PASSWORD_CURRENT_MISMATCH（401 にしない）、失敗として監査に残す | BR4.2・BR7.2、ADR-004 |
| 要求1件で接続を2本使う経路は、負荷の試験で接続プールが尽きないことを確かめる | 要件 NFR5、`project.md` の Corrections |
| 負荷の試験は使い捨ての環境で行い、接続プールは待ちの時間切れの累計と待ちの最大で判断する | `project.md` の Testing Posture |
| この Intent の流れには performance-validation の段がある（性能の目標の測定の持ち主） | `aidlc-state.md` |
| スキーマの変更は V7 の前進のみ、1つ前の版のアプリが動く後方互換 | 要件 NFR10、`team.md` の Deployment |
| 手を入れた `packagesJudgedByTotal` のパッケージは一覧から外し、パッケージごとの下限の対象に戻す。一覧は増やさない、除外も増やさない | `team.md` の Testing Posture、B2 の完了の条件 |
| パスワード・ハッシュ・トークンを出さない、メールアドレスをアプリのログとエラー応答に出さない | `project.md` の Forbidden、要件 NFR2 |
| エラーの説明文は ja・en で、要求の Accept-Language で選ぶ | 要件 NFR8、BR8.3、ADR-005 |
| 招待のトークン（NFR1）・列挙の防止（NFR3）・画面のアクセシビリティ（NFR7）・手元のメールの受け手（NFR11）はこの単位に当てはまらない（`traceability.json` で N/A とする） | 単位の分割 `unit-of-work.md` |

---

## Q1. パスワードの変更の API の応答時間の目標と、測る負荷をどうしますか？

理由: 要件 NFR6 は「既存の API と同じく p95 1 秒以内」を [assumption] としています。ただし、変更の成功は今のパスワードの照合と新しいパスワードのハッシュで bcrypt を2回計算します（cost 12 で1回 約 278 ms）。照合1回のログインでも、同時 10 件の p95 は CPU 4 で 940 ms（余裕 60 ms）でした。変更の成功を同時 10 件で流すと、p95 は約 1.9 秒になる見込みです（まだ測っていません）。今のパスワードの誤りは照合1回だけで、ログインと同じ重さです。

A. 同時 10 件で、変更の成功は p95 2 秒以内、今のパスワードの誤りと入力の誤りは p95 1 秒以内とする（bcrypt の回数に合わせる。プリファレンスの取得・保存は 1 秒のまま）
B. 目標は 1 秒のまま、パスワードの変更だけ測る負荷を同時 2 件にする（変更は頻度が低く、同時 10 件は起きにくいため）。同時 10 件の値は記録だけにする
C. 同時 10 件で 1 秒を守るため、bcrypt の cost を 11 に下げる（新しく作るハッシュの計算は約半分になるが、総当たりへの強さも半分になる。保存済みのハッシュは変更するまで cost 12 のまま）
X. Other (please specify)

[Answer]: A

## Q2. パスワードの変更で、今のパスワードの誤りが続いたときに制限を設けますか？

理由: 要件の未解決の点で、この段が持ち主です（要件 7節、機能設計の BR4.5・6節、ADR-004 の悪い点）。今の設計では、今のパスワードの誤りは回数に数えず、ログインのロックにも関係しません。そのため、ログインしたままの画面や漏れたアクセストークン・リフレッシュトークン（リフレッシュトークンは 24 時間有効）を使えば、ログインのロック（5 回・30 分）を通らずに、今のパスワードを何度でも試せます（照合1回 約 278 ms）。

A. 制限を設けない（前の Intent で IP ごとの回数の制限をしなかったのと同じく、利用者 50 名の社内向けとして受け入れる。誤りはすべて監査に残り、後から追える）
B. 利用者ごとに今のパスワードの連続の誤りを数え、ログインのロックと同じ値（設定の `mastersmith.auth.lock` の threshold・duration、既定 5 回・30 分）に達したら、その時間の間はパスワードの変更だけを照合せずに拒否する（新しい code、状態コード 429）。回数は内部DB に持ち（起動し直しても消えない。列の追加は V7 に含める）、変更の成功で 0 に戻す。拒否した試みも PASSWORD_CHANGED の FAILURE（新しい失敗の理由）として監査に残す。ログイン・トークンには影響しない。ADR-004 のとおり UserAccount の中に持ち、Authentication のロックとは別にする
C. 今のパスワードの誤りをログインの失敗回数に数える（しきい値でアカウントをロックし、ロックの間はパスワードの変更も拒否する。発行済みのトークンは FR6.3 のとおり無効にしないため、ロックの間もほかの API は使える。本人が次にログインできなくなる妨害が起きうる。UserAccount から Authentication のロックを使うため、ADR-004 の部品の関係を決め直す）
X. Other (please specify)

[Answer]: A

---

## Consolidated Summary Confirmation

答えのまとめ:

- 設計の要点（案）は冒頭の 21 件のとおり（プリファレンスの取得・保存は同時 10 件で p95 1 秒、bcrypt の cost 12 は変えず計算はトランザクションの外、2本目の接続の経路は performance-validation の k6 で待ちの時間切れが 0 であることを確かめる、V7 は1つ前の版が起動できることを目標に確かめ方は NFR 設計・基盤の設計、新しい指標・警報・依存は足さない、手を入れた既存のパッケージはパッケージごとの下限に戻す など）
- Q1 A: パスワードの変更の成功は同時 10 件で p95 2 秒、今のパスワードの誤り・入力の誤りは p95 1 秒。プリファレンスの取得・保存は p95 1 秒のまま。要件 NFR6 の [assumption] 1 秒との差を成果物に記録する
- Q2 A: 今のパスワードの誤りが続いても制限しない（承認済みの BR4.5 のとおり）。ログインのロックを通らずに試せることは残る危険として記録し、アクセストークンの有効期限が短いこと（漏れたトークンで試せる時間の上限）と監査の PASSWORD_CHANGE_FAILED で見つけられることを根拠に書く

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
