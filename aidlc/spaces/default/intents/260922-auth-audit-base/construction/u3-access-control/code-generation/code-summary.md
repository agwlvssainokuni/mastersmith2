# Code Summary — U3 管理画面のアクセス制御（u3-access-control）

承認済みの `code-generation-plan.md`（Step 1〜13）と `unit-test-instructions.md` に従って生成した結果をまとめる。方法は Testing Contract の test-after（層ごとに実装してから、その層のテストを書いて実行する）である。

## 作った・変えたファイル

新規 52 件、変更 9 件（合計 61 件）。一覧は `source-manifest.json` にある。

### 変更したファイル（9 件）

D1（8 件）と README の節の追加だけである。本番の設定・品質の目標は変えていない。

| ファイル | 変更 |
|---|---|
| `build.gradle.kts` | `e2eTest` の実行の対象を `e2e/u1-skeleton.e2e.ts` から `e2e`（すべて）にした（D1） |
| `README.md` | 「API のアクセス制御（U3）」の節を足し、E2E の節の説明を更新し、差し込み口の表に U3 が提供する2件を足した |
| `backend/src/test/java/cherry/mastersmith/config/SecurityExtensionIT.java` | 「U1 だけなら `/api/**` はすべて通る」の期待を、U3 の既定（ログインなしは 401）に改めた。役の `ApiDefaultAccess` を使う入れ子の確認を取り除いた（D1） |
| `backend/src/test/java/cherry/mastersmith/common/testsupport/TestSecurityExtensions.java` | 使われなくなる役の `ApiDefaultAccess`（`DefaultAccess`）を取り除いた（D1） |
| `backend/src/test/java/cherry/mastersmith/common/error/web/ErrorResponseIT.java` | クラスの設定に `mastersmith.test-fixture.public-api=true` を足した（D1） |
| `backend/src/test/java/cherry/mastersmith/common/observability/TracingAndLoggingIT.java` | 同上（D1） |
| `backend/src/test/java/cherry/mastersmith/common/observability/ExternalExportIT.java` | 同上（D1） |
| `backend/src/test/java/cherry/mastersmith/config/ExposureIT.java` | 同上（D1） |
| `backend/src/test/java/cherry/mastersmith/config/SecurityHeadersIT.java` | 同上（D1） |

U2 のファイルは1件も変えていない（D2 の A のとおり）。`vendor/make-you-chic-ui` とサブモジュールの固定先も変えていない。

### 新規のファイル（52 件）

| 区分 | 件数 | 主なもの |
|---|---|---|
| バックエンドの本体（`cherry.mastersmith.access`） | 20 | `domain`（`AdminPaths`・`AccessDeniedReason`・`AdminAccessDeniedEvent`・`AccessEventType`・`AccessResult`・`AccessProblemTypes`）、`service`（`AccessDeniedEventPublisher`・`AccessProblemTypeCatalog`）、`web`（`AdminSecurityContributor`・`AdminApiDefaultAccess`・`AdminAuthorizationManager`・`AdminAuthenticationEntryPoint`・`AdminAccessDeniedHandler`・`AccessRequestRejectedHandler`・`AccessWebSecurityConfig`・`AdminCheckController`）、`package-info` 4 件 |
| バックエンドのテスト | 20 | 単体 9 クラス、結合 6 クラス、テストの補助 4 件（`PublicApiTestRules`・`AdminAccessTestConfig`・`AdminTestUsers`・`CapturedAccessDeniedEvents`）、`AccessProblemTypeCatalogTest` |
| フロントエンド | 11 | `src/features/admin/`（`adminApi`・`adminAreaStatus`・`AdminAreaPage`・`AdminPlaceholder`＋CSS・`registration` と、それぞれのテスト） |
| E2E | 1 | `frontend/e2e/u3-admin-access.e2e.ts` |

依存関係は1つも足していない（`gradle/libs.versions.toml`・`backend/build.gradle.kts`・`backend/gradle.lockfile`・`frontend/package.json` は変えていない）。環境変数・秘密情報・設定の型も増えていない（`application.yaml`・`.env.example` は変えていない）。内部DBの表もスキーマの変更も無い。

## 主な実装の判断

- **アクセスの決まり**: `AdminSecurityContributor`（order 210）が `/api/admin` と `/api/admin/**` に `AdminAuthorizationManager` を当て、401 の入口の処理と 403 の拒否の処理を置き換える。`AdminApiDefaultAccess` が `/api/**` の既定をログイン必須にする。U1 の `SecurityConfig` は変えていない。
- **401 の応答（D2 の A）**: `AdminAuthenticationEntryPoint` は、管理者のみのパスかの判断・理由の判定・出来事の通知だけを行い、応答の書き出しと区分の DEBUG は U2 の `TokenAuthenticationEntryPoint` に委ねる（注入して呼ぶ）。U2 のソースもテストも変えずに済んだ。
- **判定の根拠**: `AdminAuthorizationManager` は、U2 が要求ごとに内部DBから読んだ `AuthenticatedUser` の `admin` だけを見る。U2 の `AuthenticatedUserToken` は権限の一覧を持たないため、役割の文字列では判定しない。DB は読まない（`AdminAccessIT` で、確認用 API の1要求の SQL が `select users` 1回だけであることを確かめている）。
- **出来事の通知の受け止め**: 例外の受け止めは `AccessDeniedEventPublisher`（`service`）に1か所だけ置き、401・403 の処理はそれを呼ぶ。WARN には例外の型だけを出し、メッセージとスタックトレースは出さない。
- **メールアドレスを漏らさない工夫**: `AdminAccessDeniedEvent.toString()` でメールアドレスを `***` に伏せた。U1 のメソッドの呼び出しの追跡（`TraceAspect`）が `service` の引数を文字列にするため、伏せないと `cherry.mastersmith.access` を TRACE にしたときにアプリのログへメールアドレスが出てしまう（NFR10.3）。`AccessSecretLeakIT` がこれを確かめる。
- **`AdminAuthorizationManager` は Bean にしない**: `AdminSecurityContributor` の中で `new` する。Bean にすると `TraceAspect` の代理に包まれ、TRACE のときに `AuthenticatedUserToken`（＝メールアドレス）が文字列化されてログに出てしまうため。
- **403 の出来事は管理者のみのパスに限る**: `AdminAccessDeniedHandler` は、403 の応答は常に共通の形で書くが、出来事は `AdminPaths.isAdminOnly` が真のときだけ知らせる（BR3.1 の範囲）。
- **要求の検査の拒否（C4）**: 第一案の `WebSecurityCustomizer` で `RequestRejectedHandler` を差し込む形が効いた（`AccessWebSecurityConfig`）。代わりの形（連鎖より前のフィルター）は要らなかった。
- **拒否の WARN のトレースID**: 計画では「`code` とトレースIDを出す」としていたが、U1 のログの仕組みが同じ行に `traceId` を付けるため、`addKeyValue("traceId", ...)` を重ねると JSON に同じ鍵が2回出る。重複を避けて `code` だけをキーと値で渡し、トレースIDは U1 の仕組みに任せた（`observability-design.md` 2章の表と一致する）。同じ行にトレースIDが載り、応答の `traceId` と一致することは `AdminPathBoundaryIT` が確かめている。

## テストとカバレッジ

| 対象 | 件数 | 結果 |
|---|---|---|
| バックエンドの単体テスト（全体） | 339 | すべて成功 |
| バックエンドの結合テスト（全体） | 194 | すべて成功 |
| うち U3（`cherry.mastersmith.access`）の単体 | 69 | すべて成功 |
| うち U3 の結合 | 43 | すべて成功 |
| フロントエンド（全体） | 167 | すべて成功 |
| うち U3（`src/features/admin`） | 30 | すべて成功 |
| E2E（`frontend/e2e` 全体、ビルドした WAR） | 5 | すべて成功（U1 2件・U2 2件・U3 1件） |

| カバレッジ | 行 | 分岐 | 下限 |
|---|---|---|---|
| バックエンド全体（JaCoCo） | 97.09% | 91.65% | 行 80%・分岐 70% |
| バックエンドの U3（`cherry.mastersmith.access`） | 100.00%（136/136） | 97.73%（43/44） | 同上 |
| フロントエンド全体（`@vitest/coverage-v8`） | 98.73% | 94.02% | 同上 |
| フロントエンドの U3（`src/features/admin`） | 96.87% | 85.71% | 同上 |

カバレッジの除外は増やしていない（U1 が決めた範囲のまま）。U3 は設定値だけの型を持たない。

チームの必須の認可のテストは、`AdminAccessIT`（未ログイン 401 / 管理者でない利用者 403 / 管理者 204）と `AdminPathBoundaryIT`（パスの境界）がサーバー側で確かめている。画面で「管理」を隠すことを判定の代わりにしていないことは、`AdminAccessIT`（画面を介さない直接の呼び出し）と `registration.test.ts`（表示の切り替えだけであること）で確かめている。

## 1コマンドの検査（`./gradlew verify`）の結果

すべての段（フォーマット → リンタ → ライセンスヘッダー → ビルド → 単体テスト → 結合テスト → カバレッジ → 安全の検査 → 成果物）が成功した。SpotBugs（FindSecBugs 込み）の High は 0 件、Gitleaks と OSV-Scanner も成功した。`pre-commit run --all-files`（秘密情報の検出・Spotless・Prettier）も成功した。

**ただし、既存の U2 のテストに実行のたびに結果が変わる不安定さ（flaky）があり、`./gradlew verify` が約 17% の確率で失敗する**（次の「開いている問題」を参照）。U3 の変更とは関係が無く、U3 のテストは毎回すべて成功している。

## コンテナでの起動と確認

`docker compose build app` でイメージを作り、秘密情報を環境変数だけで渡して起動し（`.env` は作らない方針のため、`docker compose up` ではなく `docker run -e ...` で確かめた）、次を確認した。

| 確認 | 結果 |
|---|---|
| ヘルスチェック `/actuator/health` | 200 |
| 初期管理者でログイン（`admin` が真） | 成功 |
| 管理者で `GET /api/admin/check` | 204 |
| 未ログインで `GET /api/admin/check` | 401 |
| `/api/admin/..;/secret` | 400 / `REQUEST_REJECTED` |
| 画面の配信 `/` | 200 |
| ログアウト | 204 |
| ログに初期管理者のパスワードが出ないこと | 出ていない |
| 拒否の WARN が JSON 1行で、トレースIDが付き、拒否したパスを含まないこと | 確認した |

画面の操作（ログイン → サイドバーの「管理」→ 管理者向け領域 → ログアウト）は、ビルドした WAR に対する `u3-admin-access.e2e.ts` で確かめている。

## 承認済みの設計との違い（D3）

依頼者の決定 D3-A のとおり、Functional Design の文書は書き換えず、ここに違いを記録する。

| 違い | Functional Design の記述 | 実装 | 根拠 |
|---|---|---|---|
| 1. 問題の種類 | `rules.md` BR6.1: U3 の問題の種類は `ACCESS_DENIED` だけ | `ACCESS_DENIED`（403）と `REQUEST_REJECTED`（400）の2つ | `nfr-design/security-design.md` 2章（確定回答 Q1） |
| 2. 拒否の応答 | `functional-spec.md` WF1 の手順1は「判定の前に拒否する」だけで応答の形を決めていない | 共通の形の 400 と U1 のヘッダー（CSP・`X-Content-Type-Options`・`X-Frame-Options`・`Referrer-Policy`） | `nfr-design/security-design.md` 2章 |
| 3. 出来事の要求のパス | `functional-design/entities.md` の `AdminAccessDeniedEvent` に要求のパスの項目は無い | `requestPath`（正規化済み・問い合わせの部分なし・512 文字で切り詰め）を載せる | `nfr-design/security-design.md` 4章と、U4 の `nfr-design/security-design.md` 6章（NFR Design の承認の場での依頼者の決定） |

## 計画からの逸脱と、生成中に決まったこと

1. **`/API/admin/check` の期待を改めた**（`AdminPathBoundaryIT`）。計画と `nfr-design/security-design.md` 1章は「ログイン中 404・未ログイン 401」としていたが、`/API/...` は大文字のため `/api/**` に当たらず、U1 の「`/api/` の外は画面の配信」の決まり（BR1.5）により誰に対しても画面（`index.html`）の 200 になる。U1 の承認済みの振る舞い（`ExposureIT` の「画面の URL を直接開くと index.html」）と一致しており、設計文書の記述の側が U1 の決まりと噛み合っていない。BR1.6 が求める安全の性質（大文字のパスを管理者のみの範囲として扱わない＝管理 API には決して届かない）は満たしているため、テストはその性質（管理者・管理者でない利用者・未ログインのいずれも 200 の画面で、`ACCESS_DENIED` を含まない）を確かめる形にした。U1 のファイルは変えていない。
2. **エンコードされた区切り（`%2F`）は Tomcat が拒否する**。`/api/admin%2Fcheck` は Spring Security の要求の検査より手前で Tomcat が 400 を返すため、U3 の `REQUEST_REJECTED` の形にはならない（本文は Tomcat の既定）。判定より手前で拒否され管理 API に届かないという安全の性質は満たしているため、`AdminPathBoundaryIT` では「400 で、`ACCESS_DENIED` や内部の情報を含まない」ことを確かめる別のテストにした。U3 の共通の形の 400 は `..;`・`./`・`//`・`;` を含むパスで確かめている。
3. **拒否の WARN のトレースID**を、キーと値で重ねて出すのをやめた（上の「主な実装の判断」を参照）。
4. **`AdminAuthorizationManager` を Bean にしない**ことにした（上の「主な実装の判断」を参照）。計画はどちらとも書いていない。

## 確かめられなかったこと（Deferred）

| 項目 | 理由 |
|---|---|
| NFR1.1・NFR1.3（性能）応答時間（同時 10 件で 95% が 300 ms 以内） | Performance Validation の段で測る |
| NFR10.5（観測性）拒否の件数の運用での確認 | 監査ログ（U4）と、配備先が決まったときの Operation の段で定める |
| `docker compose up -d --build` そのもの | `.env` を作らない方針のため、`docker compose build` ＋ `docker run -e ...` で同じ確認を行った |

## 後の単位への申し送り

- **U4（アクセス拒否の出来事）**: `cherry.mastersmith.access.domain.AdminAccessDeniedEvent` を `ApplicationEventPublisher` で知らせる。項目は `eventType`（`AccessEventType.ACCESS_DENIED`）・`occurredAt`（U2 の `Clock` の Bean の時点）・`result`（`AccessResult.FAILURE`）・`failureReason`（`AccessDeniedReason`: `NOT_ADMIN`・`TOKEN_MISSING`・`TOKEN_MALFORMED`・`TOKEN_INVALID`・`USER_NOT_FOUND`）・`enteredEmail`（403 のときだけ。401 では常に null）・`sourceIp`・`userAgent`（512 文字で切り詰め）・`requestPath`（正規化済み・問い合わせの部分なし・512 文字で切り詰め）・`traceId`。
- **U4（受け取り方）**: **`@EventListener` で、要求と同じスレッド・応答を書く前**に受け取る（U2 の認証の出来事が `@TransactionalEventListener(AFTER_COMMIT)` であるのと異なる。U3 は内部DBの更新を伴わないため）。受け取り側で例外を投げても 401／403 の応答（状態コード・`code`・本文）は変わらない（U3 側で受け止めて WARN を出す）。テストの例は `CapturedAccessDeniedEvents`。
- **U4（`toString`）**: この出来事の `toString()` はメールアドレスを伏せる。監査ログに書くときは `enteredEmail()` で値を取る。
- **U4（秘密情報）**: この出来事はアクセストークン・Authorization ヘッダー・パスワードの項目を持たない。
- **後続 Intent（API の置き場所）**: 管理者のみにする API は **`/api/admin/` の下に置く**（個々の API での宣言には頼らない）。それ以外の `/api/` の下は、置くだけでログインが必要になる。公開にしたい API は、U1 か U2 の公開の一覧に明示して足す（レビューで理由を確かめる）。
- **後続 Intent F（役割・権限）**: 役割・権限の判定は `cherry.mastersmith.access.web.AdminAuthorizationManager` に足す（ADR-003）。この部品は Bean ではなく `AdminSecurityContributor` が作るため、依存が要るときはそこで渡す。
- **後続 Intent（問題の種類）**: U3 は `ACCESS_DENIED`（403）と `REQUEST_REJECTED`（400）を使っている。新しい code を足すときは重ならないようにする（起動時に検査される）。
- **テストの補助**: `cherry.mastersmith.access.testsupport` の `PublicApiTestRules`（`mastersmith.test-fixture.public-api=true` のときだけ `/api/**` を公開にする。**管理者のみの決まりは緩まない**）、`AdminAccessTestConfig`・`AdminTestUsers`（管理者・管理者でない利用者の作成とログイン）、`CapturedAccessDeniedEvents`（出来事の記録と、受け取りの失敗の再現）。U1・U2 の結合テストで `/api/**` の既定の拒否が邪魔になる場合は、`mastersmith.test-fixture.public-api=true` を1行足す。本番の設定にはこの仕組みが無い。

## 開いている問題

（解決済み）**既存の U2 のテストの補助に、実行のたびに結果が変わる不具合があった（U3 の変更とは無関係）。**生成の後、依頼者の承認を得て `backend/src/test/java/cherry/mastersmith/auth/testsupport/AuthTestTokens.java` の `tamper(String)` を「署名の最初の1文字を置き換える」形に直し、トークンのテストを5回続けて実行してすべて成功することを確かめた（レビューでも修正の内容を確認済み）。以下は、そのときの不具合の内容である。

- 場所: `backend/src/test/java/cherry/mastersmith/auth/testsupport/AuthTestTokens.java` の `tamper(String)`。
- 内容: 署名（Base64URL）の最後の1文字を `A`（すでに `A` なら `B`）に置き換えて改ざんを作るが、HS256 の署名は 32 バイト＝ 43 文字で、**最後の1文字は下位2ビットが詰め物**である。`A`（0）と `B`（1）はこの詰め物のビットだけが違うため、復号すると**同じ 32 バイト**になり、改ざんしたはずのトークンが有効なまま通ってしまう。署名の最後の文字が `A` になる確率は 1/16 なので、1回のトークンにつき約 6% の確率で起きる。
- 影響を受けるテスト: `auth/service/AccessTokenServiceTest`（`otherAlgorithms` の1件と `messageHasNoToken`）と `auth/web/AccessTokenApiIT`（`broken, tampered, alg none and other algorithms are 401 with their reasons`）の計3か所。1回の `./gradlew verify` で少なくとも1件が失敗する確率は約 17%。実際、今回の生成中に3回の失敗を確認した（いずれも U3 のテストは全件成功）。
- 直し方の案（依頼者の承認が要る。U2 のファイルを変えるため）: `tamper` を「最後の1文字ではなく、署名の**最初**の1文字を別の文字に置き換える」か、「署名を復号して1バイト反転してから符号化し直す」形にする。どちらも詰め物のビットに当たらないため、必ず署名が壊れる。U3 の `AccessDeniedEventsIT` では、この落とし穴を避けるために `tamperedSignature` ではなく `hs256WithOtherKey`（別の鍵で署名）と `algNone` を使っている。

このほかに未解決の問題は無い。

なお、上の修正は U3 の生成の後に別途 依頼者の承認を得て行ったものであり、D1 で承認された 8 ファイルとは別である。
