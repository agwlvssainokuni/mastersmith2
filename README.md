# MasterSmith

MasterSmith（マスタ管理アプリ）のリポジトリです。バックエンドは Java 25・Spring Boot 4（`backend/`）、画面は React＋TypeScript とデザインシステム make-you-chic-ui（`frontend/`、`vendor/make-you-chic-ui`）で作り、画面を同梱した実行可能 WAR を1つのコンテナで動かします。

現在の内容はアプリの骨格（U1）です。アプリの起動、内部DB（組み込みの H2）、ヘルスチェック、1行1件の JSON のログ、分散トレースと外部エクスポート（既定は無効）、共通のエラー応答、画面の骨組み（AppShell とログイン用レイアウト）、後の単位（U2 認証・U3 アクセス制御・U4 監査ログ）が使う差し込み口を持ちます。

## 前提の道具

| 道具 | 版 | 入れ方の例（macOS） | 使う場面 |
|---|---|---|---|
| JDK | 25（Temurin） | `brew install --cask temurin@25`、または SDKMAN の `sdk install java 25.0.4-tem` | ビルド・テスト |
| Node.js・npm | 24 | `brew install node@24`、または `nvm install 24` | 画面のビルド・テスト |
| Gitleaks | 8.30.1 | `brew install gitleaks` | 秘密情報の検出（`./gradlew verify`） |
| OSV-Scanner | 2.6.0 | `brew install osv-scanner` | 依存関係の脆弱性の検査（`./gradlew verify`） |
| Python と pre-commit | pre-commit 4.x | `brew install pre-commit` | コミットの前の検査 |
| Docker（Docker Desktop または colima） | Compose v2 | `brew install colima docker docker-compose` | コンテナで動かすとき、対象DB の結合テスト（`./gradlew verify`） |
| Playwright の Chromium | `@playwright/test` と同じ版 | `cd frontend && npx playwright install chromium` | ビルドした WAR での画面の確認（`./gradlew e2eTest`） |

- Gradle は Wrapper（`./gradlew`）を使うため、別に入れる必要はありません。
- `./gradlew verify` は、Node.js の版が 24 でない、または Gitleaks・OSV-Scanner が見つからないときは、入れ方を示して失敗します（検査を黙って飛ばしません）。
- コンテナの CPU の上限は既定で 4 です。colima を使う場合は、VM を CPU 4・メモリ 6GiB にしてください（`colima start --cpu 4 --memory 6`。確かめ方と、`.env` での上限の合わせ方は「コンテナの資源の上限」）。VM の CPU を増やせないときは、`.env` の `MASTERSMITH_CONTAINER_CPUS` で上限を下げて起動できます（ログインの照合の時間の目標は 4 が前提です）。
- `./gradlew verify` はコンテナの実行環境（colima）が動いていることを前提にします。対象DB（MySQL・MariaDB・PostgreSQL）の結合テストを、版を固定したイメージのコンテナ（Testcontainers）で毎回実行するためです。内部DB を使うテストは、これまでどおり組み込みの H2 で動きます。設定と、届かないときの扱いは「対象DB（利用者の業務データの DB）」を参照してください。

## 取得と準備

```bash
git clone --recurse-submodules <このリポジトリの URL>
cd mastersmith2
# すでに取得済みのとき
git submodule update --init

# コミットの前の検査（Gitleaks・フォーマットの確認）を有効にする（各自1回）
pre-commit install
```

サブモジュールは2つです。`vendor/make-you-chic-ui`（画面のデザインシステム。npm の `file:` の依存）と、`vendor/java-mustache-processor`（メールのテンプレートを描く自前の Mustache のエンジン。Gradle の composite build で組む。`settings.gradle.kts` の `includeBuild`）です。サブモジュールを取得していないと、Gradle の構成の段階で `vendor/java-mustache-processor` のビルドが見つからず失敗します（`git clone --recurse-submodules`、または取得済みなら `git submodule update --init`）。

make-you-chic-ui の固定先は `735ef04`（Intent 260925-user-management の B4 で `edb1f94` から更新。Modal・RadioGroup・Table・Dropdown・Button の追加と直し）です。

どちらのサブモジュールも、中身はこのリポジトリから変更しません（変更はそれぞれのリポジトリ側で行う）。`./gradlew verify` の 0 の段で、どちらも追跡されるファイルが変わっていないことを確かめます。サブモジュールの固定先の更新は、承認を得た専用のコミットで行い、更新の前後のコミットのハッシュを記録します。

## 1コマンドの検査（統合の前の関門）

```bash
./gradlew verify
```

次の順に実行し、1つでも失敗したら後ろの段は実行しません。CI（GitHub Actions）も同じタスクを呼びます。

| 順 | 段（タスク） | 内容 |
|---|---|---|
| 0 | 準備（`verifyPrepare`） | 道具の確認、make-you-chic-ui の `npm ci`・ビルドと、2つのサブモジュール（make-you-chic-ui・java-mustache-processor）の追跡されるファイルが変わっていないことの確認、画面の `npm ci` |
| 1 | フォーマット（`verifyFormat`） | Spotless（palantir-java-format、ライセンスヘッダーを含む。バックエンドのメールのテンプレートの Mustache のコメント `{{! ... }}` の形のライセンスヘッダーも確かめる）、Prettier |
| 2 | リンタ（`verifyLint`） | oxlint、ESLint、Stylelint |
| 3 | ライセンスヘッダー（`verifyLicense`） | 画面のファイルのヘッダー（Java・Gradle の Kotlin DSL・メールのテンプレートは 1 の段の Spotless が確かめる） |
| 4 | ビルド（`verifyBuild`） | Java のコンパイル、`tsc --noEmit`、Vite のビルド |
| 5 | 単体テスト（`verifyUnitTest`） | JUnit（`*Test`）、Vitest |
| 6 | 結合テスト（`verifyIntegrationTest`） | Spring と組み込みの H2 を起動するテスト、対象DB（MySQL・MariaDB・PostgreSQL）のコンテナを使うテスト、JVM の中で起動するテスト用の SMTP の受け手（SubEtha SMTP）でメールを実際に受けるテスト（`*IT`） |
| 7 | カバレッジの下限（`verifyCoverage`） | JaCoCo・`@vitest/coverage-v8`。行 80%・分岐 70% を下回ったら失敗。バックエンドは全体の合計に加えて、新しく作るパッケージ（`cherry.mastersmith.targetdb` 以後）ごとにも同じ下限を当てる（既存のパッケージは全体の合計で判定する。`backend/build.gradle.kts` の `packagesJudgedByTotal`） |
| 8 | 安全の検査（`verifySecurity`） | SpotBugs＋FindSecBugs（重大度 High と、SQL インジェクション系（パターン名が `SQL_` で始まる）・予測できる乱数（`PREDICTABLE_RANDOM`）・メールのヘッダーへの差し込み（`SMTP_HEADER_INJECTION`）の指摘は priority によらず失敗。誤検知は `backend/config/spotbugs-exclude.xml` に理由を書いて外す）、OSV-Scanner（下の判定）、Gitleaks（リポジトリの履歴全体） |
| 9 | 成果物と量の確認（`verifyArtifact`） | 画面を同梱した実行可能 WAR（`backend/build/libs/mastersmith.war`）、初回の読み込みの JavaScript の量（500KB を超えたら警告だけ） |

メールのテスト（U1）はコンテナを使いません。テスト用の SMTP の受け手を JVM の中で起動し、TLS の証明書はテストの実行のたびに JDK の `keytool` で一時のディレクトリに作ります（リポジトリに置かない）。そのため、コンテナの実行環境が無くても飛ばされません（飛ばされうるのは対象DB のテストだけです）。

各段だけを実行することもできます（例: `./gradlew verifyFormat`）。報告は `backend/build/reports/`（テスト・JaCoCo・SpotBugs）、`frontend/coverage/`、`build/reports/osv-scanner/osv.json` に出ます。

### 依存関係の脆弱性の判定（OSV-Scanner）

検査の対象は `backend/gradle.lockfile`、`frontend/package-lock.json`、`vendor/make-you-chic-ui/package-lock.json` です。

| 対象 | 統合を止める（失敗） | 警告だけ |
|---|---|---|
| バックエンド（Gradle） | 重大度 High 以上（CVSS 7.0 以上） | それ未満 |
| 画面の実行時の依存関係（`frontend/package.json` の `dependencies` とその依存。make-you-chic-ui の実行時の依存関係を含む） | 重大度 High 以上 | それ未満 |
| 画面の開発用の依存関係（`devDependencies` とその依存。make-you-chic-ui の開発用の依存関係を含む） | 成果物を作る道具（`config/npm-build-tools.txt` に書いたもの）の重大度 High 以上 | それ以外のすべて |
| すべての npm の依存関係 | 悪意のあるパッケージ（OSV の ID が `MAL-` で始まるもの）は重大度によらず失敗 | — |

- 実行時か開発用かは、lockfile の各項目の印（`dev`）で判定します。lockfile で見つからないものは実行時として扱います。
- 重大度の分からないものは警告として表示します。警告も毎回すべて表示されるので、定期的に見直してください。
- 画面を作る道具（Vite など）を足したり入れ替えたりしたときは、`config/npm-build-tools.txt` も見直してください。

## ビルドした WAR での画面の確認（E2E）

`./gradlew verify` と CI には入れていません。統合の前と、リリースの前に手で実行します。

```bash
cd frontend && npx playwright install chromium && cd ..   # 初回と Playwright の更新のとき
docker compose --profile mail up -d mailpit                # メールの受け手（Mailpit）を起動しておく
./gradlew e2eTest
```

E2E の WAR はメールを手元の受け手 Mailpit（`127.0.0.1:1025`）へ送ります（「メール（U1）」）。`./gradlew e2eTest` は始める前に Mailpit の API（`http://127.0.0.1:8025/api/v1/info`）に届くかを確かめ、届かなければ起動の手順を示して失敗します（Mailpit の起動・停止はしません）。

WAR をビルドし、一時ディレクトリの内部DBで起動して、`frontend/e2e/` のすべての確認を CSP 違反やスクリプトのエラーなしに通ることを確かめます（番号は 18081。`E2E_PORT` で変えられます）。1つの WAR と内部DBを共有するため、ファイル名の番号の順に1本ずつ実行します。

| ファイル | 確かめる流れ |
| --- | --- |
| `010-skeleton.e2e.ts` | 画面の骨格（ログイン画面の枠と CSP の応答ヘッダー） |
| `020-auth.e2e.ts` | 初期管理者のログインとログアウト、誤ったパスワードの表示 |
| `030-admin-access.e2e.ts` | 代表の流れ「ログイン → 管理画面に入れるか → ログアウト」 |
| `040-dsl-admin.e2e.ts` | DSL の管理「ログイン → DSL の管理 → 貼り付けで投入 → プレビュー → 適用 → 今の状態が適用中 → ログアウト」（対象DB は設定しないため、照合は「接続先が設定されていません」の警告になる） |
| `050-display-accessibility.e2e.ts` | ログインの画面の表示の設定の 20 組（テーマ × 文字の大きさ、ブランドカラー × テーマ、幅 375px のテーマ × 文字の大きさ）ごとのアクセシビリティの検査（axe-core、WCAG 2.0・2.1 の A・AA）と横のはみ出し、最初の画面が出るまでの時間の測定（5 回）。ログインはしない |
| `060-invitation-accessibility.e2e.ts` | 招待の管理の画面の表示の設定の 20 組ごとのアクセシビリティの検査（一覧・警告・招待の入力の Modal・取り消しの確かめの Modal。axe-core、WCAG 2.0・2.1 の A・AA）と横のはみ出し、一覧と次のページが出るまでの時間の測定（5 回）。初期管理者でログインする |
| `070-registration-accessibility.e2e.ts` | 登録の完了の画面の表示の設定の 20 組 × 2 つの状態（フォーム・使えないリンク）ごとのアクセシビリティの検査（axe-core、WCAG 2.0・2.1 の A・AA）と横のはみ出し。ログインしない。確かめの API だけを見本に差し替え、完了の要求は送らない |
| `080-preferences-accessibility.e2e.ts` | プリファレンスとパスワードの変更の画面の表示の設定の 20 組 × 2 画面 × 2 つの状態（最初・画面の確かめの誤り）ごとのアクセシビリティの検査（axe-core、WCAG 2.0・2.1 の A・AA）と横のはみ出し、画面を開く・保存・パスワードの変更の時間の測定（5 回ずつ）。初期管理者でログインし、ユーザーメニューから移る |
| `090-invitation-registration-flow.e2e.ts` | この Intent の代表の流れ「管理者でログイン → 招待 → Mailpit からリンクを取り出す → 登録の完了 → 新しい利用者でログイン → 管理画面に入れない → ログアウト」と、リンクを開いてからフォームが出るまでの時間の測定（5 回） |

050 について（Intent 260925-user-management の U4）:

- 流れの確かめではないため、「機能の Intent ごとに代表の流れを1本まで」の本数に数えません。
- 最初の画面の時間（目標は手元の PC でキャッシュが空の状態から 2 秒以内）は記録だけで、失敗にはしません。CSP の違反と、見た目の設定が `sans` のときの Noto Serif JP のフォントの読み込みは失敗にします。
- green・orange の組の primary のボタンのコントラスト不足は、既知の制約（「画面の表示の設定（U4）」）として `frontend/e2e/support/axe.ts` の `KNOWN_VIOLATIONS` に名前と対象を指定して扱います。ほかの違反は失敗にします。既知の違反が消えたときも失敗にするため、make-you-chic-ui が直ったら一覧とこの README を見直します。
- 050 だけを流すときも Mailpit の起動が要ります（`(cd frontend && npx playwright test e2e/050-display-accessibility.e2e.ts)` の前に `./gradlew :backend:bootWar` と Mailpit の起動）。

060 について（Intent 260925-user-management の U5）:

- 流れの確かめではないため、「機能の Intent ごとに代表の流れを1本まで」の本数に数えません。
- 組ごとに、ログインの画面から初期管理者でログインし、サイドバーの「利用者の招待」から開きます。一覧の API（`GET /api/admin/invitations?page=…`）の答えだけを見本（`frontend/e2e/support/invitationFixtures.ts`）に差し替えます。招待の入力と取り消しの確かめの Modal は開くだけで、要求は送りません。
- ログインの後は利用者の設定が当たるため、組のテーマと文字の大きさは、ログイン（`POST /api/auth/login`）と復元（`POST /api/auth/session/refresh`）の本物の応答の `user.theme`・`user.fontSize` の2項目だけを書き換えて当てます（`frontend/e2e/support/loginPreferences.ts`）。サーバーの状態（利用者の設定）は変えません。U6 の 070 はログインの前の画面のため U4 の鍵で当て、U7 の 080 はプリファレンスの答え（`GET /api/me/preferences`）の差し替えで当てます（どちらも `loginPreferences.ts` は使いません）。
- 一覧と次のページの時間（目標は一覧 2 秒・次のページ 1.5 秒）は記録だけで、失敗にはしません。CSP の違反、画面の問題、本物の一覧の応答と見本の形の違いは失敗にします。測定は招待の API で招待を 21 件置くため、1 回の実行で Mailpit に 21 通のメールが届きます（Mailpit は消しません）。招待を使える設定が無い WAR では、測定を飛ばして理由を注記に残します（E2E の WAR には SMTP とベース URL が渡るため、念のための備え）。
- green・orange の組の primary のボタンのコントラスト不足は、既知の制約（「画面の表示の設定（U4）」）として `frontend/e2e/support/axe.ts` の `INVITATION_KNOWN_VIOLATIONS` に画面の状態ごとの名前で扱います（一覧の「招待する」、招待の入力の「招待する」）。ほかの違反と、一覧と一致しない既知の違反は失敗にします。
- 060 だけを流すときも Mailpit の起動が要ります（`(cd frontend && npx playwright test e2e/060-invitation-accessibility.e2e.ts)` の前に `./gradlew :backend:bootWar` と Mailpit の起動）。

070 について（Intent 260925-user-management の U6）:

- 流れの確かめではないため、「機能の Intent ごとに代表の流れを1本まで」の本数に数えません。
- 登録の完了の画面はログインの前の画面のため、組のテーマと文字の大きさは 050 と同じく、読み込みの前に U4 の鍵（`mastersmith.display-settings`）へ置いて当てます（`loginPreferences.ts` は使いません）。
- フォームの状態は、確かめの API（`POST /api/registration/verify`）の答えだけを見本（`frontend/e2e/support/registrationFixtures.ts` の `VERIFY_SAMPLE`、どの招待にも当たらないトークン `a11y-sample-token`）に差し替えて出します。使えないリンクの状態は、差し替えずにフラグメントの無い `/register` を開いて出します。完了の要求は送らず、サーバーの状態（内部DB・監査・招待）を変えません。
- green・orange の組の primary のボタンのコントラスト不足は、既知の制約（「画面の表示の設定（U4）」）として `frontend/e2e/support/axe.ts` の `REGISTRATION_KNOWN_VIOLATIONS` に画面の状態ごとの名前で扱います（フォームの「登録を完了する」。使えないリンクの状態は無し）。ほかの違反と、一覧と一致しない既知の違反は失敗にします。CSP の違反も失敗にします。
- 070 だけを流すときも Mailpit の起動が要ります（`(cd frontend && npx playwright test e2e/070-registration-accessibility.e2e.ts)` の前に `./gradlew :backend:bootWar` と Mailpit の起動。070 はメールを送りませんが、E2E の WAR はメールを Mailpit へ送る設定で起動します）。

080 について（Intent 260925-user-management の U7）:

- 流れの確かめではないため、「機能の Intent ごとに代表の流れを1本まで」の本数に数えません。
- 組ごとに、ログインの画面から初期管理者でログインし、ユーザーメニューの「プリファレンス」「パスワードの変更」から開きます。プリファレンスの画面は開いた時点に内部DB の値へ画面をそろえるため、組のテーマと文字の大きさは、`GET /api/me/preferences` の答えだけを見本（`frontend/e2e/support/preferencesFixtures.ts`、氏名は `検査 太郎`）に差し替え、そのそろえ（本物の `applyUserPreferences`）で当てます。ブランドカラーは 050 と同じく `/api/appearance` の差し替えです。`loginPreferences.ts` は使いません。
- 画面の確かめの誤りの状態は、氏名を空にして「保存する」、3つを空のまま「変更する」で出します。検査では `PUT`・`POST /api/me/…` を送りません（送られていれば失敗）。
- 既知の違反は、状態ごとの名前の一覧（`frontend/e2e/support/axe.ts` の `PREFERENCES_KNOWN_VIOLATIONS`、green・orange の組の「保存する」「変更する」）と、トップバーのアバター（`AVATAR_KNOWN_COMBOS`）・dark の組の誤りの文字（`withFormFieldErrorKnownViolation`）です（「画面の表示の設定（U4）」の既知の制約）。ほかの違反と、一覧と一致しない既知の違反は失敗にします。CSP の違反も失敗にします。
- 測定は、招待の API で招待を1件置き、Mailpit のメールからリンクを取り出し、登録の完了の API で利用者を1人作って行います（`frontend/e2e/support/registeredUser.ts`、宛先は実行ごとに重ならない `u7-perf-…@example.com`）。1 回の実行で Mailpit に1通届き、新しい利用者と、招待・パスワードの変更の監査が一時の内部DB に残ります。Mailpit の API は読むだけです。
- 時間（目標は開く 2 秒・保存 1.5 秒・パスワードの変更 2.5 秒）は記録だけで、失敗にはしません。本物の `GET /api/me/preferences` の応答と見本の形の違い、CSP の違反、画面の問題は失敗にします。招待を使える設定が無い・Mailpit に届かない WAR では、測定を飛ばして理由を注記に残します（念のための備え）。
- 080 だけを流すときも Mailpit の起動が要ります（`(cd frontend && npx playwright test e2e/080-preferences-accessibility.e2e.ts)` の前に `./gradlew :backend:bootWar` と Mailpit の起動）。

090 について（Intent 260925-user-management の U6、E2E-1）:

- この Intent の代表の流れの E2E で、流れの本数に数えるのは 090 だけです。前のファイルが作った状態に頼らず、管理者のログインと招待を自分で行い、宛先は実行ごとに重ならない値（`e2e-invitee-<実行時刻>-<乱数>@example.com`）にします。
- 1 回の実行で招待を1件置き、Mailpit に1通届き、新しい利用者と監査の記録が一時の内部DB に残ります（E2E の内部DB は実行ごとの一時のディレクトリ）。
- 招待メールのリンクは Mailpit の API（`GET /api/v1/search`・`GET /api/v1/message/{ID}`）を読むだけで取り出し、書き込まず、メールを消しません（`frontend/e2e/support/mailpit.ts`。API の場所は `build.gradle.kts` の `mailpitInfoUrl` と同じ `http://127.0.0.1:8025`）。
- リンクを開いてからフォームが出るまでの時間（目標は手元の PC でキャッシュが空の状態から 2 秒以内）を5回測り、値と目標以内の回数を注記と添付に残します。時間では失敗させません。同じ5回で、アドレス欄に `#token=` が残らないこと・CSP の違反が無いこと・ブラウザの保存にトークンが無いことは失敗の条件です。
- 本物の確かめの応答の項目の名前と型が見本（`VERIFY_SAMPLE`）と同じことを毎回確かめます（値は比べません）。
- 090 だけを流すときも Mailpit の起動が要ります（`(cd frontend && npx playwright test e2e/090-invitation-registration-flow.e2e.ts)` の前に `./gradlew :backend:bootWar` と Mailpit の起動）。
- HTML の報告（`frontend/playwright-report/`）と失敗のときのトレースには、090 で開いた使い捨てのトークンを含むリンクが載ります（E2E の一時の内部DB の中の値）。共有しません。json の結果・注記・添付・`test.step` の題には、リンク・トークン・宛先・パスワードを入れません。

E2E は Mailpit に届いたメールを消しません。`./gradlew e2eTest` の後に片付けるときは、「手元でメールを見る」の2行（`docker compose stop mailpit` と `docker compose rm -f mailpit`）で止めて消します（後の単位の E2E もこの書き方に従います）。

結果は `frontend/test-results/e2e-results.json`（json の報告）と `frontend/playwright-report/` に出ます。どちらもコミット・共有しません。実行ごとに作る仮の署名鍵・初期管理者のメールアドレス・仮のパスワードは、`webServer.env` ではなく Playwright のプロセスの環境変数で WAR に渡し、json の結果に含まれないことを `frontend/playwright-secret-check-reporter.ts` が確かめます（含まれていれば実行を失敗にし、値は表示しません）。失敗したときのトレース（`trace: 'retain-on-failure'`）には仮の資格情報が含まれうるため、共有しません。

## 開発時の起動

```bash
# バックエンド（http://localhost:8080、内部DBは ./backend/data/）
./gradlew :backend:bootRun

# 画面の開発サーバー（http://localhost:5173。/api と /actuator と /dsl はバックエンドへ転送する）
cd frontend && npm run dev
```

開発サーバーは元の Host を保ったまま転送するため、エラー応答の `type` の URL は開発サーバーの Host から組み立てられます。

## コンテナでの起動と確認

配備は手で行い、コンテナに入れる WAR は手元で `./gradlew verify` を通して作ったものを使います。

```bash
git status --porcelain           # 何も表示されないこと（未コミットの変更があれば配備しない）
git rev-parse --short HEAD       # 配備する版のコミットのハッシュを控える（戻すときに使う）
./gradlew verify                 # 検査を通して WAR を作る（backend/build/libs/mastersmith.war）
cp .env.example .env             # 初回だけ。値を入れる（.env はコミットしない）
(umask 077 && cp .env.targetdb.example .env.targetdb)   # 初回だけ。見本の対象DB の値を入れる（コミットしない）
# 2回目以降は、ここで内部DBのデータを複写し（「内部DBのバックアップと戻し方」）、
# いま動いている版のイメージに戻し用のタグを付ける（「戻し方」。例: docker tag mastersmith:local mastersmith:pre-dsl）
docker compose --profile targetdb-postgres up -d --build   # アプリと見本の対象DB（PostgreSQL）を起動する
docker compose --profile targetdb-postgres ps              # app が healthy、targetdb-postgres が Up になれば起動の完了（最長で約 2 分）
```

- 配備したアプリには、見本の対象DB（PostgreSQL、compose の profile `targetdb-postgres`、DB `business`・スキーマ `sales`、読み取りだけのアカウント `mastersmith_reader`）をつなぎ、アプリと一緒に起動・停止します。そのため、起動・状態の確認・停止の `docker compose` のコマンドには `--profile targetdb-postgres` を付けます。
- 初めて起動する前に、次の9項目を入れておきます。値は各自で決め、コミットしません。
  - `.env.targetdb` の2項目 `MASTERSMITH_SAMPLE_TARGETDB_ADMIN_PASSWORD`・`MASTERSMITH_SAMPLE_TARGETDB_READER_PASSWORD`: 見本の DB の管理者と `mastersmith_reader` のパスワード。乱数で作ることを勧めます（例: `openssl rand -hex 24`。英数字だけになり、`$` の展開などに左右されません）。見本の対象DB のコンテナだけが読み、アプリのコンテナには渡りません（アプリのコンテナは `.env` だけを読むため、見本の DB の管理者のパスワードを `.env` に置かない）。
  - `.env` の `MASTERSMITH_TARGET_DB_*` の7項目: 「対象DB」の「手元で試す対象DB（compose の profile）」の手順 3 の PostgreSQL の値（ホストは `targetdb-postgres`、番号は `5432`）。`MASTERSMITH_TARGET_DB_PASSWORD` は `.env.targetdb` の `MASTERSMITH_SAMPLE_TARGETDB_READER_PASSWORD` と同じ値にします。
- 見本のスキーマと読み取りのアカウントは、見本の DB のボリュームが無い状態で初めて起動したときだけ作られます。起動の後に `docker compose logs targetdb-postgres` で初期化の誤りが無いことを確かめます。後から `.env.targetdb` のパスワードを変えても見本の DB には効かないため、変えるときは見本の DB を作り直します（「手元で試す対象DB（compose の profile）」の手順 5 で消してから起動し直す）。
- これまで `.env` に `MASTERSMITH_SAMPLE_TARGETDB_*` の2項目を入れていたときは、次の手順で `.env.targetdb` に移します。どのコマンドも値を画面に出しません。値を変えずに移すだけなら、見本の DB のボリュームはそのまま使え、作り直しは要りません。

  ```bash
  test -e .env.targetdb && echo ".env.targetdb が既にあります。上書きせず、中身を確かめてから進めてください"
  (umask 077 && cp .env "$HOME/.mastersmith-env-before-targetdb")          # 戻すときのために .env をリポジトリの外へ複写する
  (umask 077 && grep '^MASTERSMITH_SAMPLE_TARGETDB_' .env > .env.targetdb)  # 2行を書き写す
  grep -c '^MASTERSMITH_SAMPLE_TARGETDB_' .env.targetdb                      # 2 と出ること
  (umask 077 && grep -v '^MASTERSMITH_SAMPLE_TARGETDB_' .env > .env.new && mv .env.new .env)   # .env から2行を消す
  docker compose --profile targetdb-postgres up -d --force-recreate --wait   # アプリと見本の対象DB のコンテナを作り直す
  docker inspect mastersmith-app-1 --format '{{range .Config.Env}}{{println .}}{{end}}' \
    | cut -d= -f1 | grep -c '^MASTERSMITH_SAMPLE_TARGETDB_'                  # 名前だけを数えて 0 と出ること
  ```

  問題が無ければ、複写した `$HOME/.mastersmith-env-before-targetdb` を消します。戻すときは、その複写を `.env` に戻して `.env.targetdb` を消し、この版より前の `compose.yaml` で起動し直します。
- アプリは起動のときに対象DB に接続しません。見本の対象DB が止まっていてもアプリは起動を続け、スキーマの読み込み（既定の DSL の生成）と照合が「接続できない」になります。対象DB を使わないときは、`--profile targetdb-postgres` を付けずに起動し、`MASTERSMITH_TARGET_DB_*` を書きません（7項目がすべて空なら対象DB を使いません）。
- 動いている版は、控えたコミットのハッシュで見分けます。イメージのタグは `local` のままです。
- ブラウザで `http://localhost:8080/` を開き、ログイン画面が表示されることを確かめます。`.env` に `MASTERSMITH_WEB_BASE_URL`（招待を使うときに入れる。例 `http://localhost:8080`）を入れた環境では、その値と同じ URL で開きます。`http://127.0.0.1:8080/` で開くと、ログイン・更新・ログアウトの Origin の確かめが合わず 403 / `ORIGIN_NOT_ALLOWED` になり、警報 `ms-origin` の拒否の数が増えます。招待メールを見るときは、受け手（Mailpit）を `docker compose --profile mail up -d mailpit` で起動します（「手元でメールを見る」）。ヘルスチェックの応答が UP で、下のスモークテストが通るまで、配備の完了とはみなしません。
- 初めての起動では、`.env` に `MASTERSMITH_AUTH_SIGNING_KEY` と初期管理者（`MASTERSMITH_AUTH_INITIAL_ADMIN_EMAIL`・`MASTERSMITH_AUTH_INITIAL_ADMIN_PASSWORD`）を入れておきます。起動のログに「初期管理者を作成しました」の INFO が出ることを確かめます（2回目以降の起動では作られません）。
- 配備の確認（スモークテスト、手で行う）: ログイン画面から初期管理者でログインし、ホームが表示されること、メニューの「管理」で管理者向け領域が開けること、ユーザーメニューのログアウトでログイン画面に戻ることを確かめます。あわせて、そのログインとログアウトの監査イベント2件（`LOGIN_SUCCEEDED`・`LOGGED_OUT`）が記録されていることを「監査ログの確かめ方」の手順で確かめ、`docker compose logs app` に ERROR が出ていないことを見ます。
- 見本の対象DB をつないだ配備では、スモークテストに次を足します: サイドバーの「DSL」で DSL の管理画面を開き、今の状態が表示されること。「スキーマを読み込む」で見本の DB から既定の DSL が作られ、プレビューに `sales` のテーブルとビューが並ぶこと（「未設定」「接続できない」にならないこと）。その操作の監査イベント `DSL_GENERATED` が記録されていること。スモークテストの操作は監査ログに残り、消せません。
- ログは `docker compose logs -f app`（1行1件の JSON）で見ます。1つの要求のログは `traceId` で絞り込めます。メッセージ（`message`）の中の改行は「 ⏎ 」（前後に空白を置いた U+23CE）に置き換えて出すため、Hibernate の起動の案内（ロガー `org.hibernate.orm.connections.pooling`）のような複数行のメッセージも1件が1行に収まります。スタックトレース（`exception`）の改行はそのままです。
- 止めるときは `docker compose --profile targetdb-postgres down`（アプリと見本の対象DB を一緒に止めます。内部DB と見本の対象DB のデータはボリュームに残ります）。

### 戻し方

配備の前に、いま動いているイメージに戻し用のタグ（例: `pre-dsl`）を付けて残しておき、問題が出たらそのイメージで起動し直します（イメージを作り直さない）。

```bash
# 配備の前（新しい版を作る前）に、いま動いている版のイメージに戻し用のタグを付ける
docker tag mastersmith:local mastersmith:pre-dsl
```

戻すときは、次の順で行います。

```bash
docker image inspect mastersmith:pre-dsl --format '{{.Id}}'   # 戻し先のイメージがあること
docker compose stop app                                       # 止めるときに H2 のファイルが詰め直される（約 1 秒）
# 今の内部DBのデータを複写する（次の節。戻した後に調べるため）
MASTERSMITH_IMAGE_TAG=pre-dsl docker compose up -d --no-build app   # 戻し先のイメージで起動する（作り直さない）
MASTERSMITH_IMAGE_TAG=pre-dsl docker compose ps app                 # healthy になったら、上のスモークテストを行う
```

- `MASTERSMITH_IMAGE_TAG` は `compose.yaml` の `image: mastersmith:${MASTERSMITH_IMAGE_TAG:-local}` の口です。**戻している間は、アプリを起動・作り直す `docker compose` のコマンドに毎回 `MASTERSMITH_IMAGE_TAG=<戻し用のタグ>` を付けます。** 付けずに `docker compose up -d` を行うと、`mastersmith:local`（新しい版）で作り直されます。付け忘れを避けたいときは、`.env` に `MASTERSMITH_IMAGE_TAG=<戻し用のタグ>` の1行を足し、新しい版に戻すときに消します。
- **`--no-build` を必ず付けます。** `--build` を付けると、今の WAR から作ったイメージに戻し用のタグが付き、戻し先のイメージが上書きされます。
- 戻し用のタグのイメージが無いとき（消してしまったとき）は、直前の版のコミット（前回の配備で控えたハッシュ）を `git worktree add ../mastersmith-rollback <ハッシュ>` で取り出し、`(cd ../mastersmith-rollback && git submodule update --init && ./gradlew :backend:bootWar)` で WAR を作り、その WAR で `docker build -t mastersmith:<戻し用のタグ> .` を行ってから、上の手順で起動します。終わったら `git worktree remove ../mastersmith-rollback` で消します。
- 見本の対象DB（`targetdb-postgres`）は戻しの対象外です。動かしたままでよく、古い版が `MASTERSMITH_TARGET_DB_*` を読まないときは使われません。

スキーマの変更は前進のみ・後方互換のため、1つ前の版のアプリが今のスキーマで動きます。スキーマは戻しません。データが壊れたとき、移行（Flyway）が途中で失敗したときだけ、配備の前に取ったバックアップを展開してデータも戻します（次の節。バックアップの後の記録は失われます）。

V8（招待の表、下の「招待と登録の完了（U3）」）を当てた後に1つ前の版へ戻すときは、次の点に気を付けます。

- 1つ前の版は招待の表を読み書きしません。戻した後に今の版へ戻し直すと、招待の表は戻す前の状態のまま使われ、戻している間に有効期限を過ぎた招待は期限切れになります（管理者が一覧から送り直す）。
- 1つ前の版も `MASTERSMITH_WEB_BASE_URL` を読むため、Origin の確かめは同じ値に固定されたままです（同じ `http://localhost:8080` で開けば動きます）。戻しの練習で別の番号（例 `http://localhost:18080`）で開くときは、配備の `.env` の複写の末尾に `MASTERSMITH_WEB_BASE_URL=http://localhost:18080` を足してから起動します（同じ項目が2行あると後の行が効きます）。

V7（利用者のプリファレンスとパスワードの変更、下の「利用者のプリファレンスとパスワードの変更（U2）」）を当てた後に1つ前の版へ戻すときは、次の2点に気を付けます。戻しの練習の手順は配備の段（deployment-pipeline）で書き起こします。

- **`.env` の `MASTERSMITH_AUTH_INITIAL_ADMIN_EMAIL` を、配備のときのまま変えないでください。** 変えると、1つ前の版が初期管理者を作ろうとして、氏名（`display_name`、V7 で足した必須の列）を渡さない利用者の追記を試み、失敗しえます（起動は続く見込みですが、未確認です）。
- **空の内部DB で1つ前の版を起動するときは、V7 を当てていない内部DB を使います。** 1つ前の版が利用者を作るのは、利用者が1人もいないときの初期管理者の作成だけで、V7 を当てた内部DB には必ず利用者がいるため、ふだんの戻しでは起きません。

### 内部DBのバックアップと戻し方

アプリを止めてから、ボリュームのファイルをリポジトリの外（ホームの下の `~/.mastersmith-backup/`、権限 700）に複写します。置き場を `mktemp -d` の一時ディレクトリにしません（colima の VM から見えず、複写が空振りします）。

```bash
docker compose stop app
mkdir -p ~/.mastersmith-backup && chmod 700 ~/.mastersmith-backup
docker run --rm -v mastersmith_mastersmith-data:/data -v "$HOME/.mastersmith-backup":/backup eclipse-temurin:25.0.4_7-jre-noble \
  tar czf /backup/mastersmith-data-$(date +%Y%m%d%H%M).tgz -C /data .
ls -l ~/.mastersmith-backup/     # ファイルができたこと（中身は開かない）
docker compose start app         # 配備の途中なら起動せず、次の手順で新しい版を起動する
```

戻すときは、アプリを止め、ボリュームの中身を消してからバックアップを展開します。

```bash
docker compose stop app
docker run --rm -v mastersmith_mastersmith-data:/data -v "$HOME/.mastersmith-backup":/backup eclipse-temurin:25.0.4_7-jre-noble \
  sh -c 'rm -rf /data/* && tar xzf /backup/<バックアップのファイル> -C /data && chown -R 10001:10001 /data'
docker compose start app         # 止めたコンテナをそのまま起動する（作り直すときは戻し方の節の MASTERSMITH_IMAGE_TAG を付ける）
```

コンテナの外で動かしている場合は、アプリを止めて `./data/`（`bootRun` なら `backend/data/`）を複写します。

### 内部DBのファイルの詰め直し（アプリを止めずに）

DSL の投入と適用を重ねると、アプリが動いている間は内部DB（組み込みの H2）のファイルが伸び続けます（「DSL の管理の API」の節の既知の制約）。H2 は接続が1本でも開いている間は詰め直さないため、運用の道具 `docker/hikari-pool.sh` で、HikariCP の接続プールの標準の JMX の操作（一時停止 → 接続の破棄 → 0 本を待つ → 再開）を呼びます。最後の接続が閉じたときに、接続先の `;DEFRAG_ALWAYS=TRUE` に従って H2 がファイルを詰め直します。アプリのプロセスは止めません。

詰め直しのスレッドは 1 本に固定しています（イメージの既定の JVM の引数 `-Dh2.compactThreads=1`。テストの JVM と E2E の WAR の起動も同じ）。H2 2.4.240 は閉じるときの全体の詰め直しを CPU の数 / 4 本のスレッドで並べて行いますが、2本以上では H2 の中の競合に当たり、詰め直しが中断されてファイルが縮まないことがあるためです（Intent 260925-user-management の U3 で、CPU 8 の PC の結合テスト `H2CompactionByPoolSuspensionIT` で見つけた）。CPU の数によらず同じ動きになり、今の配備（CPU 4）では元から 1 本のため速さは変わりません。`MASTERSMITH_JAVA_OPTIONS` で上書きしないでください。H2 を上げるときは、この指定が要るかを見直します。この引数より前の版のイメージに戻したときは、CPU 4 の配備では元から 1 本のため動きは変わりません。

```bash
./docker/hikari-pool.sh status     # 接続の本数と内部DB のファイルの大きさ（読み取りだけ）
./docker/hikari-pool.sh compact    # 詰め直す（前と後の大きさ・かかった時間・結果を出す）
./docker/hikari-pool.sh resume     # 再開だけ行う（下の「再開し忘れ・道具が途中で終わったとき」）
```

- **いつ行うか**: 目安として、ファイルが 300MB を超えたとき（`status` で見られます）に、利用の少ない時間に行います。自動では行いません。
- **詰め直しの間の要求**: 一時停止の間、内部DB を使う要求（ログイン・トークンの更新・監査の記録・DSL の操作・健全性の確認）は、失敗せずに再開まで待たされます（接続を借りる待ちの上限 5 秒は効きません）。道具は一時停止から再開までを既定で 45 秒以内に抑えます。
- **かかる時間の目安**: 借りている接続が無ければ、接続の破棄から詰め直しの終わりまで1秒未満〜数秒です（圧縮の効く 10MB の DSL を 21 件適用した状態で、229MiB → 15MiB が 1 秒未満。圧縮の効かない本文の約 210MB の詰め直しは、PC の上の試しで約 2 秒）。道具は詰め直しの終わりを「ファイルの大きさが 3 秒変わらない」ことで判断するため、一時停止は数秒〜十数秒になります（使い捨ての環境で 10MB の DSL を 21 回適用しプレビューを1件置いた状態から、270.4MiB → 15.2MiB、一時停止から再開まで 3.6 秒）。10MB の本文 21 件の悪い側の条件での時間は Build and Test で測ります。
- **時間の上限と打ち切り**: 重い DSL の操作やログインの最中で接続が借りられていると、返されるまで待ちます。10 秒（`--zero-wait`）の内に 0 本にならなければ、詰め直さずに再開し、「詰め直しを確かめられませんでした」と出して 1 で終わります（何も壊しません。時間を置いてやり直します）。0 本になった後、ファイルが落ち着くまでの待ちは 30 秒（`--settle-wait`）、一時停止から再開までの全体は 45 秒（`--total-limit`）が上限で、超えそうなら再開を優先します。
- **健全性**: 一時停止が約 30 秒を超えると、`/actuator/health` が 503 を返し続け、コンテナが unhealthy になります（再開で戻ります）。その間、アプリのログに `TimeBoundedDbHealthIndicator` の WARN が出ます。
- **同時に1つ**: `compact` は同じコンテナに対して同時に1つだけ動きます（この PC の上のロック）。途中で失敗・中断（Ctrl-C）しても、再開を必ず試みます。
- **再開し忘れ・道具が途中で終わったとき**: 一時停止のままだと、内部DB を使う要求がすべて待ち続け、処理のスレッドがたまります。`./docker/hikari-pool.sh resume` で再開し、だめなら `docker compose --profile targetdb-postgres restart app` で起動し直します（止めるときにも H2 が閉じて詰め直されます）。`compact` が強制終了されてロックが残ったときは、`resume` の後に表示されたロックのディレクトリを消します。JMX の操作を手で打たず、この道具を使ってください。
- **詰め直しに失敗したとき**: 再開の後に内部DB を開けないと、要求が 500 になり、コンテナが unhealthy になります。`docker compose --profile targetdb-postgres restart app` で起動し直し、それでも開けないときは「内部DBのバックアップと戻し方」で直前のバックアップを展開します（バックアップの後の記録は失われます）。大きな詰め直しの前には、アプリを止めてよい時にバックアップを取ることを勧めます（バックアップはアプリを止めます）。
- **ディスクの空き**: 詰め直しは、残す分の大きさの新しいファイルを書きます。空き（`colima ssh -- df -h /`）が残す分（最大約 210MB）より十分にあることを確かめます。
- **接続先の指定**: `MASTERSMITH_DB_URL` で接続先を上書きするときも `;DEFRAG_ALWAYS=TRUE` を付けます。付けないと、`compact` は接続を閉じて再開するだけで縮まず、道具が「大きさが減っていません」と出します。
- **記録**: 詰め直しの操作は、監査ログにもアプリのログにも残りません（HikariCP の標準の機能だけで行うため）。前と後の大きさと時間は、道具の出力で見ます。
- **しくみと公開の範囲**: 設定は `application.yaml` の `spring.datasource.hikari.register-mbeans`・`allow-pool-suspension`（どちらも true）です。道具は、JDK のイメージ（`eclipse-temurin:25.0.4_7-jdk-noble`）の一時のコンテナをアプリのコンテナと PID・ネットワークの名前空間を共有して同じ利用者の番号（10001）で動かし、アプリの JVM に attach して、JVM の中だけの JMX の接続（コンテナの中のループバックだけで待ち受ける）で操作します。JMX を PC やネットワークに公開しません（遠隔の接続の設定は有効にしません）。操作できるのは、この PC で Docker を使える人だけです。
- **使い捨ての環境で試す**: `--container mastersmith-perf-app-1` を付けます。`perf/dsl-timing.sh --storage --compact` は、投入と適用を重ねた後にこの道具を流し、前と後の大きさと時間を記録します（`perf/README.md`）。

### コンテナの資源の上限（colima の VM・メモリ・JVM）

colima の VM の大きさはリポジトリの外の設定のため、コミットでは固定できません。各自の PC で次のとおりにします。

```bash
# VM を CPU 4・メモリ 6GiB にする（VM を止めると、動いているコンテナも止まる）
docker compose stop app
colima stop
colima start --cpu 4 --memory 6
colima list                                              # CPUS が 4、MEMORY が 6GiB
docker info --format '{{.NCPU}} {{.MemTotal}}'           # 4 と約 6GB（VM の OS の分だけ 6GiB より少し小さい）
docker compose up -d --wait                              # app が healthy になるまで待つ
```

- VM の大きさの見積もり: 配備したアプリ（2GB）・見本の対象DB（512MB）・手元の監視 `lgtm`（1.5GB）を合わせて約 4GB で、6GiB の内側に収まります。負荷の試験の環境（2GB、`perf/README.md`）も同時に動かすと約 6GB で VM の上限に近づくため、負荷の試験の間は手元の監視を止めます。
- コンテナの上限の既定は CPU 4・メモリ 2g で、この VM の大きさが前提です。VM を広げた後は `docker compose up -d --wait` でコンテナを作り直し、上限を `docker inspect mastersmith-app-1 --format '{{.HostConfig.NanoCpus}} {{.HostConfig.Memory}}'`（`4000000000 2147483648`）で確かめます。VM がこれより小さい PC では、`.env` の `MASTERSMITH_CONTAINER_CPUS`・`MASTERSMITH_CONTAINER_MEMORY` で下げます（下の「既知の制約」）。
- 照合に使える CPU は、コンテナの `cpus` と VM の CPU の数の小さい方で頭打ちになります。VM だけを広げても、`MASTERSMITH_CONTAINER_CPUS` が小さいままではログインは速くなりません。
- JVM の設定は `.env` の `MASTERSMITH_JAVA_OPTIONS` で足します（イメージの作り直しは要らず、コンテナの作り直しで効きます）。最大ヒープは既定でメモリの上限の 50% です（以前は 75%。10MB の DSL の投入とログインを重ねたときにメモリの上限に迫ったため下げました。下の「既知の制約」）。ヒープ以外（メタ領域・スレッドのスタック・直接バッファなど）はこの外側で使うため、割合を変える（例: `-XX:MaxRAMPercentage=40.0`）か、ヒープ以外の上限（例: `-XX:MaxMetaspaceSize=256m`）を足して調整します。値は空白で区切り、空白を含む値は扱いません。JVM 標準の `JAVA_TOOL_OPTIONS` は使いません（コマンド行の 50% に上書きされ、起動の時に JSON でない行をログに出すため）。

#### 既知の制約（メモリの上限を下げるときと高い負荷）

- メモリの上限の既定は 2g です（最大ヒープはその 50%、1,024MiB）。2g では、`refresh` を毎秒約 11,000 件で流しても止まりませんでした（`perf/README.md`。最大ヒープが 75% だったときの結果）。
- 最大ヒープが 75%（1,536MiB）だったときは、10MB の DSL の投入とログインを重ねる負荷（k6 の `dslMixed`）で、ヒープが最大まで広がり、プロセスのメモリが上限の約 93% に達して、コンテナのメモリの上限での回収（`memory.events` の `max`）が 1,500 回を超えました。50% では同じ条件で約 69%・0 回でした（Intent 260925-storage-memory-fixes の計画の前の測定。直した後の確かめは Build and Test）。割合を 75% に戻す（`MASTERSMITH_JAVA_OPTIONS=-XX:MaxRAMPercentage=75.0`）と、この状態に戻ります。
- VM が CPU 4・メモリ 6GiB に満たない PC では、`.env` の `MASTERSMITH_CONTAINER_MEMORY` で `1g` などに下げます。ただし 1g では、高い負荷でメモリの上限で止まりえます。最大ヒープが 75%（768MB、ヒープ以外に使えるのは約 256MB）だったときに、CPU の上限 2・メモリの上限 1g で、トークンの更新を同時 10 件・考える時間なし（毎秒約 3,000 件）で流したところ、約 35 秒でコンテナがメモリの上限で止まりました（OOMKilled、2026-09-23 の負荷の試験）。`restart: "no"` のため、止まったままになります。
- 上限を変えると、最大ヒープも 50% の割合で変わり、1g では JVM の GC が G1 ではなく Serial になります。負荷の試験の結果とメモリの内訳（ヒープとヒープ以外）の測り方は `perf/README.md` にあります。
- 止まったかどうかは `docker inspect mastersmith-app-1 --format '{{.State.OOMKilled}} {{.State.ExitCode}}'`（`true 137` なら上限で止まった）で確かめます。

#### 設定の効き方の確かめ

`Dockerfile`・compose を変えたときは、次のスクリプトで、メモリの上限の変数と JVM の設定の口が効くことと、環境変数の分け方（アプリは `.env`、見本の対象DB は `.env.targetdb`）を確かめます。JVM の `-version` だけを小さな上限（512MB）で動かすため、アプリは起動せず、秘密情報も要りません。配備したアプリを止めずに実行できます。

```bash
./gradlew :backend:bootWar && docker compose build app   # イメージ mastersmith:local を作る（配備したコンテナは作り直さない）
./docker/check-container-limits.sh                        # 期待と違う点があれば、期待の値と実際の値を出して失敗する
```

- 確かめること: 両方の compose の `mem_limit`（変数なしで 2g、`MASTERSMITH_CONTAINER_MEMORY=768m` で 768m）、最大ヒープの割合（変数なし・空の値で 50%、`MASTERSMITH_JAVA_OPTIONS` で 60%）、ヒープ以外の上限（`-XX:MaxMetaspaceSize=128m`）、java がコンテナの PID 1 であること（停止の合図を直接受け取る）、タイムゾーンの引数が残ること、`compose.yaml` の `app` が `.env` だけを読み `MASTERSMITH_SAMPLE_TARGETDB_*` を持たないことと、見本の対象DB の3つのサービスが `.env.targetdb` を読み `environment` にパスワードを持たないこと（名前だけを見て、値は展開も表示もしない）。
- 値が入った `.env` を読んだ状態でアプリのコンテナに `MASTERSMITH_SAMPLE_TARGETDB_*` が無いことは、「コンテナでの起動と確認」の移し替えの手順の最後のコマンドで確かめます。
- 別のタグのイメージは `MASTERSMITH_IMAGE_TAG=<タグ> ./docker/check-container-limits.sh` で確かめます。前提は Docker（Compose v2）と、そのイメージがあることです。`.env` は読みません。

## 環境変数

秘密情報は `.env`（Git 管理外）から環境変数で渡します。見本は `.env.example` です。手元で試す対象DB（compose の profile `targetdb-*`）の2項目だけは `.env.targetdb`（Git 管理外。見本は `.env.targetdb.example`）に入れます。値を空にした変数は「空の値」として渡るため、既定値を使う設定は `.env` に書かないでください。

| 環境変数 | 既定値 | 内容 |
|---|---|---|
| `MASTERSMITH_CONTAINER_CPUS` | `4` | アプリのコンテナの CPU の上限（`docker compose` だけが使う）。Docker の VM の CPU が 4 に満たない PC では下げる。照合の時間の目標は 4 が前提 |
| `MASTERSMITH_CONTAINER_MEMORY` | `2g` | アプリのコンテナのメモリの上限（`docker compose` だけが使う。`1g`・`1536m` の形）。colima の VM が CPU 4・メモリ 6GiB に満たない PC では下げる。1g では高い負荷で止まりうる（「コンテナの資源の上限」の「既知の制約」） |
| `MASTERSMITH_JAVA_OPTIONS` | なし | JVM に足す引数（空白で区切る。例: `-XX:MaxRAMPercentage=40.0 -XX:MaxMetaspaceSize=256m`）。既定の引数（最大ヒープはメモリの上限の 50%、タイムゾーン Asia/Tokyo、H2 の詰め直しのスレッド 1 本）の後ろに置くため、同じ指定は上書きになる。空白を含む値は扱わない。イメージの作り直しは要らず、コンテナの作り直し（`docker compose up -d`）で効く |
| `MASTERSMITH_DB_URL` | `jdbc:h2:file:./data/mastersmith;DEFRAG_ALWAYS=TRUE` | 内部DBの接続先（コンテナでは `/app/data/mastersmith`）。`;DEFRAG_ALWAYS=TRUE` は、アプリの停止時（DB を閉じるとき）にファイルを詰め直す指定。付けないと、DSL の履歴の古い行を消しても H2 のファイルが縮まず、投入と適用を重ねるたびに大きくなる（起動し直しても縮まない）。上書きするときも `;DEFRAG_ALWAYS=TRUE` を付ける（アプリを止めずに詰め直す `docker/hikari-pool.sh compact` にも要る） |
| `MASTERSMITH_DB_USERNAME` | `sa` | 内部DBの利用者 |
| `MASTERSMITH_DB_PASSWORD` | 空 | 内部DBのパスワード（秘密情報） |
| `MASTERSMITH_DB_MAXIMUM_POOL_SIZE` | `30` | 内部DBの接続プールの接続の数の上限。同時の要求がこの数に達すると監査の記録が欠けうる（「監査ログ（U4）」の「既知の制約」を参照） |
| `MASTERSMITH_HEALTH_DB_TIMEOUT` | `2s` | ヘルスチェックの内部DBの確認の制限時間 |
| `MASTERSMITH_WEB_BASE_URL` | なし | ベースURL（例 `http://localhost:8080`）。3つに使う: 招待のリンクの元（U3。無い・形が正しくないと招待と送り直しだけが 503）、ログイン・更新・ログアウトの Origin の確かめ（入れると Origin がこの値に固定される）、エラー応答の `type` の URL（無ければ要求から組み立てる）。http か https の絶対 URL で、問い合わせ・`#`・利用者情報を含めない |
| `MASTERSMITH_INVITATION_VALIDITY` | `24h` | 招待の有効期限の長さ。1 時間以上で 1 時間で割り切れる長さ（`48h` など。`90m` は起動を止める）。招待メールの「このリンクは N 時間有効です」に同じ時間の数が入る。**長くしすぎない**（上限は置いていない。リンクが漏れたときに使える期間が延びる） |
| `MASTERSMITH_INVITATION_RETENTION` | `90d` | 終わった招待（完了・取り消し・置き換え）は終わった日時から、期限切れの招待中は有効期限から、この長さを過ぎたら定期の削除で消す。1 日以上で 1 日で割り切れる長さ。監査の記録は消さない |
| `MASTERSMITH_INVITATION_CLEANUP_CRON` | `0 45 3 * * *` | 招待の定期の削除の時刻（Spring の cron。リフレッシュトークンの削除の 3 時 30 分と重ねない） |
| `MASTERSMITH_WEB_TRUST_FORWARDED_HEADERS` | `false` | 転送元のヘッダー（`X-Forwarded-*`・`Forwarded`）を信頼するか |
| `MASTERSMITH_WEB_MAX_REQUEST_BODY_SIZE` | `1MB` | 要求の本文の大きさの上限（DSL の投入の API を除く。ログインと認可の確かめの後に確かめる。「DSL の管理の API（U4）」を参照） |
| `MASTERSMITH_DSL_MAX_SUBMIT_SIZE` | `10MB` | DSL の投入の API（`POST /api/admin/dsl/preview`）だけの要求の本文の上限（10,485,760 バイト） |
| `MASTERSMITH_DSL_HISTORY_LIMIT` | `20` | DSL の適用の履歴の件数の上限（1 以上）。超えた分は古いものから消す |
| `MASTERSMITH_OBSERVABILITY_EXPORT_ENABLED` | `false` | 外部エクスポート（トレース・ログ・指標の OTLP の送信）を有効にするか |
| `MASTERSMITH_OBSERVABILITY_EXPORT_ENDPOINT` | `http://localhost:4318` | OTLP の受け手（HTTP）のベースURL |
| `MASTERSMITH_TRACING_SAMPLING_PROBABILITY` | `1.0` | 外部へ送るトレースの割合（トレースIDは割合によらずすべての要求に付く） |
| `MASTERSMITH_TRACE_USE_DYNAMIC_LOGGER` | `true` | メソッドの呼び出しの追跡で、対象のクラスの名前のロガーを使うか |
| `MASTERSMITH_TRACE_HIDE_PROXY_CLASS_NAMES` | `true` | 追跡で、代理のクラス（プロキシ）の名前を隠すか |
| `MASTERSMITH_TRACE_LOG_EXCEPTION_STACK_TRACE` | `true` | 追跡で、例外のときにスタックトレースを出すか |
| `MASTERSMITH_TRACE_ENTER_MESSAGE` | `ENTER $[targetClassShortName]#$[methodName]($[arguments])` | 追跡の入るときの文言 |
| `MASTERSMITH_TRACE_EXIT_MESSAGE` | `EXIT  $[targetClassShortName]#$[methodName](): $[returnValue]` | 追跡の出るときの文言 |
| `MASTERSMITH_TRACE_EXCEPTION_MESSAGE` | `EXCEPTION $[targetClassShortName]#$[methodName](): $[exception]` | 追跡の例外のときの文言 |
| `MASTERSMITH_TARGET_DB_TYPE` | 空 | 対象DB の種類（`mysql`・`mariadb`・`postgresql`）。7つの項目（種類・ホスト・番号・DB の名前・スキーマ・ユーザー名・パスワード）がすべて空なら対象DB を使わない |
| `MASTERSMITH_TARGET_DB_HOST` | 空 | 対象DB のホスト名（英数字と `.`・`-`・`_`、または `[...]` の IPv6） |
| `MASTERSMITH_TARGET_DB_PORT` | 空 | 対象DB の番号（1〜65535） |
| `MASTERSMITH_TARGET_DB_DATABASE` | 空 | 接続する DB の名前（MySQL・MariaDB はスキーマと同じ） |
| `MASTERSMITH_TARGET_DB_SCHEMA` | 空 | 読み取るスキーマの名前 |
| `MASTERSMITH_TARGET_DB_USERNAME` | 空 | 対象DB のユーザー名（読み取りの権限だけのアカウントを勧める） |
| `MASTERSMITH_TARGET_DB_PASSWORD` | 空 | 対象DB のパスワード（秘密情報） |
| `MASTERSMITH_TARGET_DB_CONNECT_TIMEOUT` | `3s` | 対象DB への接続の待ちの上限 |
| `MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_GENERATE` | `20s` | 既定の DSL の生成で、問い合わせ1回の待ちの上限（1 秒以上） |
| `MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_COMPARE` | `5s` | 照合で、問い合わせ1回の待ちの上限（1 秒以上） |
| `MASTERSMITH_TARGET_DB_POOL_MAXIMUM_SIZE` | `5` | 対象DB の接続の数の上限（内部DB のプールとは別） |
| `MASTERSMITH_TARGET_DB_POOL_IDLE_TIMEOUT` | `60s` | 使っていない対象DB の接続を閉じるまでの時間（10 秒以上） |
| `SPRING_MAIL_HOST` | なし | メールの SMTP の接続先（「メール（U1）」）。無ければメールを送らない。手元では `mailpit` |
| `SPRING_MAIL_PORT` | 方式の標準 | SMTP の番号（1〜65535）。STARTTLS では 587 を書く。数でない値は起動が止まる |
| `SPRING_MAIL_PROTOCOL` | `smtp` | `smtps` にすると接続の始めから暗号化する（SMTPS） |
| `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE`・`SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_REQUIRED` | なし | 両方を `true` にすると STARTTLS（必須）。片方だけでは暗号化なしの扱い |
| `SPRING_MAIL_USERNAME`・`SPRING_MAIL_PASSWORD` | なし | SMTP の資格情報（パスワードは秘密情報）。暗号化なしの設定と一緒に使うと送らない |
| `SPRING_MAIL_PROPERTIES_MAIL_SMTP_CONNECTIONTIMEOUT`・`..._TIMEOUT`・`..._WRITETIMEOUT` | `3000` | 接続・応答の待ち・書き込みの時間切れ（ミリ秒、1 以上の整数）。SMTPS では `MAIL_SMTPS_` の名前 |
| `MASTERSMITH_MAIL_FROM` | なし | 差出人のメールアドレス（小文字・前後に空白の無い形）。無ければメールを送らない |
| `MASTERSMITH_MAIL_FROM_NAME` | `MasterSmith` | 差出人の表示名 |
| `MASTERSMITH_APPEARANCE_BRAND_COLOR` | `blue` | インスタンスのブランドカラー（`blue`・`green`・`purple`・`orange`）。許されない値は既定を使い、起動時に警告を出す（「インスタンスの見た目の設定（U8）」） |
| `MASTERSMITH_APPEARANCE_FONT_FAMILY` | `sans` | インスタンスのフォントファミリー（`sans`・`serif`）。許されない値は既定を使い、起動時に警告を出す |
| `MASTERSMITH_SAMPLE_TARGETDB_ADMIN_PASSWORD` | 空 | 手元で試す対象DB（compose の profile `targetdb-*`）の管理者のパスワード（秘密情報。`.env` ではなく `.env.targetdb` に入れる。アプリには渡らない） |
| `MASTERSMITH_SAMPLE_TARGETDB_READER_PASSWORD` | 空 | 手元で試す対象DB の読み取りだけのアカウント `mastersmith_reader` のパスワード（秘密情報。`.env.targetdb` に入れる） |
| `MASTERSMITH_AUTH_SIGNING_KEY` | 空（必須） | アクセストークンの署名鍵（Base64、復元して 32 バイト以上。秘密情報。無い・短いと起動しない） |
| `MASTERSMITH_AUTH_INITIAL_ADMIN_EMAIL` | 空 | 初期管理者のメールアドレス（無い・不正なら作らずに警告） |
| `MASTERSMITH_AUTH_INITIAL_ADMIN_PASSWORD` | 空 | 初期管理者のパスワード（秘密情報。12 文字以上、UTF-8 で 72 バイト以内） |
| `MASTERSMITH_AUTH_ACCESS_TOKEN_TTL` | `5m` | アクセストークンの有効期限 |
| `MASTERSMITH_AUTH_REFRESH_TOKEN_TTL` | `24h` | リフレッシュトークンの有効期限（Cookie の寿命も同じ） |
| `MASTERSMITH_AUTH_LOCK_THRESHOLD` | `5` | ロックするまでの連続失敗回数（1 以上） |
| `MASTERSMITH_AUTH_LOCK_DURATION` | `30m` | ロックの時間 |
| `MASTERSMITH_AUTH_PASSWORD_BCRYPT_COST` | `12` | パスワードのハッシュ（bcrypt）の cost（4〜31） |
| `MASTERSMITH_AUTH_REFRESH_TOKEN_CLEANUP_RETENTION` | `7d` | 無効・期限切れのリフレッシュトークンを、期限からどれだけ残してから消すか |
| `MASTERSMITH_AUTH_REFRESH_TOKEN_CLEANUP_CRON` | `0 30 3 * * *` | 使い終わったリフレッシュトークンの削除を行う時刻 |

- メソッドの呼び出しの追跡は、対象のクラスのロガーを TRACE にしたときだけ出ます（例: `LOGGING_LEVEL_CHERRY_MASTERSMITH_COMMON_ERROR=TRACE`）。
- 信号ごとの送り先の上書きは Spring Boot の設定で行えます（例: `MANAGEMENT_OPENTELEMETRY_TRACING_EXPORT_OTLP_ENDPOINT`）。
- ログのレベルは `LOGGING_LEVEL_<パッケージ>` で機能ごとに変えられます（既定は INFO）。
- 認証（U2）の署名鍵は必須です。`openssl rand -base64 32` で作った値を `.env` の `MASTERSMITH_AUTH_SIGNING_KEY` に入れます。値が無い・短いとアプリは起動しません。
- 署名鍵を替えるとき（鍵の交換）は、`.env` の値を替えてコンテナを作り直します。発行済みのアクセストークンは 401 になりますが、画面はトークンの更新で取り直すため、ログインし直しは要りません。
- 開発と E2E は `http://localhost` で行います。リフレッシュトークンの Cookie は `Secure` のため、localhost 以外のホスト名や IP への `http` ではログインが働きません。

## メール（U1）

アプリは、テンプレートを描いた HTML のメールを SMTP で1回だけ送ります（招待のメールは後の単位 U3 が使う）。送信の部品は Spring Boot のメールの自動設定で作り、設定は環境変数（`.env`）だけから受け取ります（「環境変数」の表の `SPRING_MAIL_*`・`MASTERSMITH_MAIL_*`）。配備先が決まるまで、実在の宛先・外部の SMTP へは送りません。手元でメールを見るときは、下の Mailpit へ送ります。

- 接続先（`SPRING_MAIL_HOST`）と差出人（`MASTERSMITH_MAIL_FROM`）の両方がそろい、設定に不正が無いときだけ送ります。何も設定しなければ送らず、警告も出しません（起動は続きます）。
- 設定の一部が欠けている・不正なとき（接続先が空白だけ、差出人の欠け・形の誤り・改行、表示名の改行、資格情報の片方だけ、範囲の外のポート、知らない方式、時間切れが 1 以上の整数でない）は、問題のある項目の名前だけ（例: `spring.mail.username`）を WARN で1件出し、送りません（値はログに出しません）。直したら再起動します。数でないポートだけは、設定の結び付けで起動が止まります。
- 起動のときに、設定の状態（`NOT_CONFIGURED`・`INVALID`・`CONFIGURED`）と暗号化の方式、準備したテンプレートの件数を INFO で1行ずつ出します（接続先・差出人・資格情報の値は出しません）。起動のときに SMTP へは接続せず、SMTP の状態はヘルスチェックに含めません。
- 送信は1回だけで、自動でやり直しません。送信ごとに、テンプレートの識別・言語・失敗の種類（`CONNECTION_FAILED`・`TIMEOUT`・`REJECTED` など）・例外の型の名前だけをログに1件出します。宛先・差し込んだ値・件名・本文・SMTP の応答はログに出しません。メールの部品（`org.eclipse.angus`・`jakarta.mail`）のログは OFF です。原因を調べるときは失敗の種類と例外の型の名前で絞り、受け手の側のログを見ます。

### 暗号化の方式の書き方

| 方式 | 書き方 | ポート |
|---|---|---|
| なし（NONE） | 何も書かない。手元の受け手（Mailpit）向け。資格情報と一緒には使えない（送らない） | 既定 25（Mailpit は 1025） |
| STARTTLS（必須） | `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true` と `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_REQUIRED=true` の両方 | **587 を書く**（書かないと 25 になる） |
| SMTPS | `SPRING_MAIL_PROTOCOL=smtps`（または `SPRING_MAIL_SSL_ENABLED=true`） | 既定 465 |

- **`starttls.enable` だけの設定（`required` を付けない）に注意**: 受け手が STARTTLS を受け付けないと、メールが暗号化されずに平文で届き、本文の招待の URL（トークン）が守られません。アプリはこの組を暗号化なし（NONE）と扱い、資格情報があれば送りませんが、資格情報の無い設定では平文で送ります。配備先が決まったら、STARTTLS（必須）か SMTPS を使ってください。
- TLS の相手の名前の確かめ（`checkserveridentity`）と TLS 1.2 以上は `application.yaml` で指定しています。証明書は JVM の既定の信頼できる一覧で確かめます。

### 運用で設定しない値

次の値は、秘密の漏れや暗号化の弱まりにつながるため、環境変数などで設定しないでください（アプリは既定の値を置いていますが、`spring.mail.properties.*` の口から上書きできてしまいます）。

- `SPRING_MAIL_PROPERTIES_MAIL_DEBUG`（`mail.debug`。宛先・本文・資格情報が標準出力に出る）
- `mail.smtp.ssl.trust`・`mail.smtps.ssl.trust`（証明書を無条件に信じる）
- `mail.smtp.ssl.checkserveridentity`・`mail.smtps.ssl.checkserveridentity` の `false`（相手の名前を確かめない）
- `mail.smtp.ssl.protocols`・`mail.smtps.ssl.protocols` に TLS 1.2 より古い版
- STARTTLS の `mail.smtp.starttls.required` の `false`
- `SPRING_MAIL_TEST_CONNECTION=true`（起動のときに SMTP へ接続し、つながらないと起動が止まる）
- `MANAGEMENT_HEALTH_MAIL_ENABLED=true`（SMTP の状態がヘルスチェックを左右する）
- メールの部品のロガーの水準（`LOGGING_LEVEL_ORG_ECLIPSE_ANGUS`・`LOGGING_LEVEL_JAKARTA_MAIL` を OFF から上げない）

### 手元でメールを見る（Mailpit、compose の profile `mail`）

```bash
docker compose --profile mail up -d mailpit    # 起動（画面は http://127.0.0.1:8025）
docker compose stop mailpit                    # 止める
docker compose rm -f mailpit                   # 消す（受けたメールも消える）
```

- アプリのコンテナから送るときは、`.env` に `SPRING_MAIL_HOST=mailpit`・`SPRING_MAIL_PORT=1025`・`MASTERSMITH_MAIL_FROM`（暗号化なし・資格情報なし）を書いて、`docker compose up -d` で作り直します。PC の上で動かす WAR（E2E など）からは `localhost:1025` へ送ります。
- 画面と API（8025）と SMTP（1025）は PC の localhost だけに開きます。Mailpit は受けたメールを外へ中継しません。
- ボリュームを置かないため、コンテナを消すと受けたメールも消えます。Mailpit が持つメールは既定で最大 500 通で、超えると古いものから消えます（イメージ `axllent/mailpit:v1.31.2` の起動の引数 `--max` の既定の値。有効期間 `--max-age` は既定で無し）。
- E2E（`./gradlew e2eTest`）の前に起動しておきます（「ビルドした WAR での画面の確認（E2E）」）。

## 対象DB（利用者の業務データの DB）

アプリは、利用者の業務データの DB（対象DB。MySQL・MariaDB・PostgreSQL）を、読み取り専用の接続で読みます（今は、設定したスキーマのテーブル・ビュー・カラム・キー・コメントのメタデータだけ）。接続先は環境変数 `MASTERSMITH_TARGET_DB_*`（「環境変数」の表）だけから受け取り、画面・API から受け取りません。

- 7つの項目がすべて空なら対象DB を使わず、起動は続きます。一部だけ空・不正なら、問題のある項目の名前（例: `mastersmith.target-db.schema`）だけを WARN で1件出し、対象DB を使いません（値はログに出しません）。直したら再起動します。
- 起動時には対象DB に接続しません。ヘルスチェックは内部DB だけで判断し、対象DB の状態は応答に含めません。
- ログイン・監査ログ・Flyway は内部DB を使い続け、対象DB には表を作りません。
- 待ちの上限は、接続の待ち（既定 3 秒）と、問い合わせ1回ごとの待ち（既定の DSL の生成 20 秒・照合 5 秒）だけです。読み取りの全体の上限はありません。読み取りは問い合わせ4回のため、応答しない対象DB では、照合でも最悪 接続 3 秒＋5 秒×4 回（約 23 秒）かかることがあります（照合の 10 秒の目標を超えることを許す決定です）。
- 読めなかったときは、原因の種類（`TIMEOUT`・`CONNECTION_FAILED`）と SQLState だけを WARN で出します。接続先・ユーザー名・パスワード・ドライバーの例外の文言は出しません。
- 3つのドライバー（MariaDB・PostgreSQL・MySQL）自身のログは、`backend/src/main/resources/application.yaml` の `logging.level` で止めています（`org.mariadb.jdbc`・`org.postgresql`・`com.mysql.cj` を `OFF`）。ドライバーのログは接続先・ユーザー名・例外の文言を含むことがあり、MariaDB のドライバーは認証の失敗をユーザー名つきで WARN に出すことを結合テストで確かめました。MySQL・PostgreSQL のドライバーも同じ扱いにそろえています。
- 読めない原因を調べるときは、まず読み取りの口（`JdbcTargetSchemaReader`）の WARN の `reason` と `sqlState` で絞ります（`08` で始まる: 接続できない、`28` で始まる: 認証の失敗、`57014`: 問い合わせの打ち切り、`TIMEOUT` で SQLState が無い: 接続の待ちの間に応答が無い）。さらに調べるときは、同じネットワークから DB の付属のクライアント（`psql`・`mysql`・`mariadb`）で接続を試すか、対象DB の側のログを見てください。配備した環境でドライバーのログを有効にしないでください（接続情報がログに残ります）。
- 対象DB のアカウントは、読み取りの権限だけにしてください（MySQL・MariaDB は `GRANT SELECT, SHOW VIEW ON <DB>.*`、PostgreSQL は `GRANT USAGE ON SCHEMA` と `GRANT SELECT ON ALL TABLES IN SCHEMA`）。アプリは接続を読み取り専用にし、書き込み・DDL を発行するコードを持ちませんが、アカウントの権限は調べません。

### 対象DB の結合テストとコンテナの実行環境

対象DB の結合テストは、版とダイジェストを固定したイメージ（`backend/src/test/java/cherry/mastersmith/targetdb/testsupport/TargetDbImages.java`）の MySQL 8.4・MariaDB 11.8・PostgreSQL 18 を Testcontainers で起動して行います。`./gradlew verify` の中で3種類とも毎回実行します（CI も同じ）。

Testcontainers は Docker の context を読まないため、colima を使う PC では、Docker の接続先を環境変数で渡してください（シェルの設定ファイルに書いておくと便利です）。

```bash
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
# 後片付けのコンテナ（Ryuk）が VM の中の Docker の接続口を使うため
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

- Docker に届かないとき、開発中（環境変数 `CI` が無い）は、対象DB のテストだけを中断（`SKIPPED`）にし、「コンテナの実行環境が無いため対象DB のテストを飛ばした。この状態では統合しない」という警告を出します。**この状態では統合しません。** `colima start` で起動し、上の環境変数を確かめてから `./gradlew verify` をやり直してください。
- CI（GitHub Actions は `CI=true` を設定する）では、Docker に届かなければ飛ばさずに失敗します。
- 単位のテストだけを実行するとき: `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.targetdb.*'`（1種類だけなら `'cherry.mastersmith.targetdb.*Postgres*'` など）。

### 手元で試す対象DB（compose の profile）

画面からスキーマの読み込みを試すときや、読み取りの時間を測るときは、見本の対象DB を compose の profile で1つずつ起動します。ポートは PC に開けず、アプリのコンテナから compose の中の名前で接続します。

配備したアプリには、見本のうち PostgreSQL（`targetdb-postgres`）をつなぎ、アプリと一緒に起動・停止します（「コンテナでの起動と確認」の `docker compose --profile targetdb-postgres up -d --build` と `docker compose --profile targetdb-postgres down`）。下の手順 1 の `.env.targetdb` と手順 3 の `.env` の設定は、その配備でも同じです。手順 2・5 の見本の DB だけの起動・停止は、ほかの種類を試すときや、見本の DB だけを止める・作り直すときに使います。ほかの種類に切り替えるときは、手順 3 の値を替えてアプリのコンテナを作り直し、使わなくなった見本の DB は止めます。見本の DB が持つのは見本のスキーマと読み取りのアカウントだけで、手順 5 で消しても業務のデータや内部DB は失われません。

1. `.env.targetdb` に `MASTERSMITH_SAMPLE_TARGETDB_ADMIN_PASSWORD` と `MASTERSMITH_SAMPLE_TARGETDB_READER_PASSWORD` を入れる（`(umask 077 && cp .env.targetdb.example .env.targetdb)` で作る。空だと DB を作れません）。見本の対象DB のコンテナは起動の入口で管理者のパスワードを各イメージの変数（`POSTGRES_PASSWORD`・`MYSQL_ROOT_PASSWORD`・`MARIADB_ROOT_PASSWORD`）に写すため、`docker compose exec` の中ではそれらの変数ではなく `MASTERSMITH_SAMPLE_TARGETDB_ADMIN_PASSWORD` を使います。
2. 起動する（初めての起動で、見本のスキーマと読み取りだけのアカウント `mastersmith_reader` を `docker/targetdb/<種類>/` の SQL と台本で作ります）。

   ```bash
   docker compose --profile targetdb-postgres up -d targetdb-postgres   # PostgreSQL（DB business・スキーマ sales）
   docker compose --profile targetdb-mysql up -d targetdb-mysql         # MySQL（DB・スキーマ business）
   docker compose --profile targetdb-mariadb up -d targetdb-mariadb     # MariaDB（DB・スキーマ business）
   ```

3. アプリに設定する（例: PostgreSQL）。`.env` に次を書き、`docker compose up -d app` でアプリのコンテナを作り直します。

   ```text
   MASTERSMITH_TARGET_DB_TYPE=postgresql
   MASTERSMITH_TARGET_DB_HOST=targetdb-postgres
   MASTERSMITH_TARGET_DB_PORT=5432
   MASTERSMITH_TARGET_DB_DATABASE=business
   MASTERSMITH_TARGET_DB_SCHEMA=sales
   MASTERSMITH_TARGET_DB_USERNAME=mastersmith_reader
   MASTERSMITH_TARGET_DB_PASSWORD=<MASTERSMITH_SAMPLE_TARGETDB_READER_PASSWORD と同じ値>
   ```

   MySQL・MariaDB は `TYPE` を `mysql`・`mariadb`、`HOST` を `targetdb-mysql`・`targetdb-mariadb`、`PORT` を `3306`、`DATABASE` と `SCHEMA` を `business` にします。

4. 読み取りの時間を測るための、テーブル 100 個 × カラム 100 個のスキーマ `large` を作るとき（アプリの `SCHEMA`（MySQL・MariaDB は `DATABASE` も）を `large` にする）:

   ```bash
   ./docker/targetdb/generate-large-schema.sh postgres \
     | docker compose exec -T targetdb-postgres psql -v ON_ERROR_STOP=1 -U target_admin -d business
   ./docker/targetdb/generate-large-schema.sh mysql \
     | docker compose exec -T targetdb-mysql sh -c 'MYSQL_PWD="$MASTERSMITH_SAMPLE_TARGETDB_ADMIN_PASSWORD" mysql --user=root'
   ./docker/targetdb/generate-large-schema.sh mariadb \
     | docker compose exec -T targetdb-mariadb sh -c 'MYSQL_PWD="$MASTERSMITH_SAMPLE_TARGETDB_ADMIN_PASSWORD" mariadb --user=root'
   ```

5. 止める・消す: `docker compose --profile targetdb-postgres stop targetdb-postgres`。データごと消すときは `docker compose --profile targetdb-postgres rm -sf targetdb-postgres` の後に `docker volume rm mastersmith_mastersmith-targetdb-postgres`（mysql・mariadb も同じ形）。

メモリの上限は PostgreSQL・MariaDB が 512MB、MySQL が 768MB です。3つを同時に起動すると、colima の VM（6GiB）のうち約 2GB を使います。配備したアプリ（上限 2GB）と PostgreSQL の見本（512MB）を一緒に動かすと約 2.5GB です。

## DSL の書式（JSON Schema）と読み込みの上限

DSL（YAML）の書式は JSON Schema（2020-12）で定めています。正本は `backend/src/main/resources/dsl/dsl-schema-v1.json` の1つで、アプリの検証もこれを読みます。ビルドのときに画面の静的なファイルの置き場へ複写し、ログインなしで次の URL から取れます（秘密は含みません）。

- 起動したアプリ: `http://<ホスト>:8080/dsl/dsl-schema-v1.json`（画面の開発サーバーでは `http://localhost:5173/dsl/dsl-schema-v1.json`）
- WAR の中の複写が正本と同じことは、`./gradlew verify` の 9 の段（`:backend:verifyDslSchemaInWar`）で確かめます。

エディタで DSL を書くときは、YAML の言語サーバー（VS Code の YAML の拡張など）に JSON Schema を指定すると、項目の補完と誤りの表示が使えます。DSL の先頭に次の1行を書くか、エディタの設定で `*.yaml` に JSON Schema を結び付けてください。

```yaml
# yaml-language-server: $schema=http://localhost:8080/dsl/dsl-schema-v1.json
version: 1
```

エディタの検証は補助です。正はサーバー側の検証で、画面や API で投入した DSL はすべてサーバー側で検証します。サーバー側では、エディタでは確かめられない次の決まりも確かめます。

| 段 | 決まり |
|---|---|
| 大きさ | 本文が 10MB（10,485,760 バイト）を超えたら読みません |
| 読み込み | 入れ子の深さは 50 まで。対応表・並びを指す別名（`*name`）は 100 個まで。別名を展開した後の節の数は 1,000,000 まで（別名の展開の爆発を止めます）。タグ（`!!str`・`!!java...`・`!custom` など）はすべて拒否します。1つの対応表の中の同じキーは誤りにします（後の値で上書きしません） |
| 書式の版 | `version` は 1 だけ |
| 構文 | JSON Schema に合うこと（書式に無い項目は誤り。接続先・ユーザー名・パスワードなどの項目も書けません） |
| 意味 | メニュー・主キー・外部キー・選択肢の参照の先のテーブルとカラムがあること、一覧の並び順が重ならないこと、フォーム部品と選択肢の出どころが合うこと、`min` ≦ `max`・`minLength` ≦ `maxLength`、`pattern` が正しい正規表現で 1,000 文字まで（確かめは 1 件 100 ミリ秒まで） |

- YAML は 1.1 の暗黙の型で読みます。`yes`・`no`・`on`・`off` は真偽値になるため、文字として書くときは `"yes"` のように引用符で囲んでください。
- 誤りは、YAML の行・列と DSL の中の場所（例 `tables.dept_mst.columns.dept_code.list.order`）と、文言の鍵（`dsl.` で始まる。一覧は `cherry.mastersmith.dsl.domain.DslMessageKeys`）で返します。誤りに埋める値は、項目の名前と、書いた値の先頭 100 文字だけです。

## 既定の DSL の生成の決まり

対象DB のスキーマ（テーブル・ビュー・カラム・主キー・外部キー・コメント）から、既定の DSL を作ります（部品は `cherry.mastersmith.dslmanage.generate`）。作った DSL は、投入した DSL と同じくサーバー側の検証（前の節）を通したものだけを返します。接続先・ユーザー名・パスワード・スキーマ名は DSL に書きません。

- **表示名**: `ja` はテーブル・カラムのコメント、コメントが無ければ物理名。`en` はいつも物理名です。コメントの制御文字（改行・タブを除く）は取り除き、長さは切り詰めません。
- **メニューと並び**: メニューは1階層で、テーブル（ビューを含む）ごとに1項目です。メニューと `tables` は、物理名を大文字・小文字を区別せずに比べた順（同じなら元の名前の順）に並べます。
- **ビュー**: `view: true` で、主キー・外部キーを持ちません。
- **DB 上の型**: `dbType` には DB が返した型の名前・長さ・精度・桁・NULL を許すかをそのまま書きます（型の名前は MySQL・MariaDB が `DATA_TYPE`、PostgreSQL が `udt_name`）。`longtext` のように長さが 2147483647 を超える型は、長さを `null` にします。
- **バリデーション**: NOT NULL で既定値の無いカラムに `required`、長さのある文字列に `maxLength`（どちらも `origin: DB`）。数値の範囲・一意は作りません。
- **外部キー**: 1つのカラムだけの外部キーは、フォーム部品を `select`、選択肢を参照先のテーブルとカラム（`REFERENCE`）にします。複数のカラムの外部キーは `foreignKeys` に写すだけです。参照先が読めない（写しに無い）外部キーは写しません。
- **主キー**: 主キーのカラムの検索は `EQUALS`。一覧の既定の並べ替えは、主キーの最初のカラムの昇順です（そのカラムを一覧に出さないときは無し）。

型の分類とフォーム部品の初期値は次のとおりです（型の名前は大文字・小文字を区別しません）。

| 分類 | 型 | フォーム部品 | 検索 | 一覧 | 詳細 | 書式 |
|---|---|---|---|---|---|---|
| 短い文字列 | `char`・`varchar`（PostgreSQL の `bpchar` を含む）で長さ 255 以下 | text | 部分一致 | 表示・並べ替え可 | 表示 | 無し |
| 長い文字列 | 長さ 256 以上・長さの無い `varchar`、`tinytext`・`text`・`mediumtext`・`longtext`・`clob` | textarea | 部分一致 | 表示しない | 表示 | 無し |
| 数値 | `tinyint`・`smallint`・`mediumint`・`int`・`integer`・`bigint`・`decimal`・`numeric`・`float`・`double`・`real`、PostgreSQL の `int2`・`int4`・`int8`・`float4`・`float8` | number | 範囲 | 表示・並べ替え可 | 表示 | 桁区切り |
| 真偽値 | `boolean`・`bool`、MySQL・MariaDB の `tinyint(1)`・`bit(1)` | checkbox | 選択肢 | 表示・並べ替え可 | 表示 | はい・いいえ |
| 日付 | `date` | date | 範囲 | 表示・並べ替え可 | 表示 | 日付 |
| 日時 | `datetime`・`timestamp`・`timestamptz` | datetime | 範囲 | 表示・並べ替え可 | 表示 | 日時 |
| 時刻 | `time`・`timetz` | text | 完全一致 | 表示・並べ替え可 | 表示 | 時刻 |
| 対応外 | 上のどれでもない型（`json`・`enum`・`year`・`uuid`・`bytea`、PostgreSQL の `bit` など） | text | 検索しない | 表示しない | 表示しない | 無し |

- **`tinyint(1)` と `bit(1)`**: MySQL・MariaDB の `tinyint(1)` は、情報スキーマの `COLUMN_TYPE`（型の全体の表記）で見分けます。MySQL 8.4 は `tinyint(1) unsigned` の幅を落として `tinyint unsigned` と返すため、MySQL では符号なしの `tinyint(1)` は数値になります（MariaDB では真偽値）。`bit(1)` は精度 1 で見分けます。
- **大きさ**: 作った DSL も上限の 10MB の内に収めます。超えるほど大きなスキーマでは生成を失敗にします（目安: 100 テーブル × 100 カラムでコメントが無いとき約 6.7MB。コメントの分だけ増えます）。その場合は、対象のスキーマを分けるなどの運用で対応してください。

## DSL の管理の API（U4）

管理者だけが使う DSL の管理の API です（`/api/admin/dsl/` の下。ログインしていなければ 401、管理者でなければ 403 と `ACCESS_DENIED` の監査）。画面（U5）から使います。エラーは Problem Details（`code` つき）で返ります。

| メソッドとパス | 内容 | 成功 | 主な失敗（`code`） |
|---|---|---|---|
| `GET /api/admin/dsl/status` | 今の状態（適用中の版とプレビュー。無ければ `null`） | 200 | — |
| `GET /api/admin/dsl/preview` | プレビューの中身（要約・適用中との違い・対象DB との照合の警告）。重い処理 | 200 | 404 `DSL_PREVIEW_NOT_FOUND`、503 `DSL_BUSY` |
| `POST /api/admin/dsl/preview?source=UPLOAD\|PASTE` | 投入（本文は `application/yaml`、10MB まで）。検証を通れば今のプレビューを置き換える。重い処理 | 201 | 413 `DSL_TOO_LARGE`、415 `UNSUPPORTED_MEDIA_TYPE`、422 `DSL_INVALID`（誤りの先頭 100 件 `errors` と総数 `total`）、503 `DSL_BUSY` |
| `DELETE /api/admin/dsl/preview` | プレビューの破棄 | 204 | 404 `DSL_PREVIEW_NOT_FOUND` |
| `POST /api/admin/dsl/preview/generate` | スキーマの読み込み（既定の DSL を作り、今のプレビューを置き換える）。重い処理 | 201 | 503 `TARGET_DB_UNCONFIGURED`・`TARGET_DB_UNAVAILABLE`・`DSL_BUSY` |
| `GET /api/admin/dsl/preview/download` | プレビュー中の DSL を、保存した本文のまま添付（`dsl-preview-<識別の先頭12文字>.yaml`）で返す | 200 | 404 `DSL_PREVIEW_NOT_FOUND` |
| `POST /api/admin/dsl/apply` | 適用（本文 `{"previewId": "..."}`。見たプレビューを指定する）。対象DB には接続しない | 200 | 409 `DSL_PREVIEW_CHANGED`（プレビューが置き換わった・破棄された・同時の適用に負けた） |
| `GET /api/admin/dsl/history` | 適用の履歴（新しい順、最大 `MASTERSMITH_DSL_HISTORY_LIMIT` 件、今適用中の版に `current: true`） | 200 | — |
| `POST /api/admin/dsl/history/{revisionId}/restore` | 履歴の版をプレビューに戻す（今の検証にかけ直し、通れば出どころ `RESTORE` で今のプレビューを置き換え、照合の警告つきの中身を返す）。重い処理 | 201 | 404 `DSL_REVISION_NOT_FOUND`（件数の上限で消えた版を含む）、422 `DSL_INVALID`（今の検証を通らない。プレビューは変わらない）、400 `VALIDATION_FAILED`（識別が UUID の形でない）、503 `DSL_BUSY` |
| `GET /api/admin/dsl/applied/download` | 適用中の DSL（今の状態の `applied` と同じ版）を、保存した本文のまま添付（`dsl-applied-<識別の先頭12文字>.yaml`）で返す | 200 | 404 `DSL_APPLIED_NOT_FOUND` |

- **問題の種類（`code`）**: `DSL_INVALID`（422）・`DSL_TOO_LARGE`（413）・`DSL_PREVIEW_NOT_FOUND`（404）・`DSL_PREVIEW_CHANGED`（409）・`DSL_APPLIED_NOT_FOUND`（404）・`DSL_REVISION_NOT_FOUND`（404）・`TARGET_DB_UNCONFIGURED`（503）・`TARGET_DB_UNAVAILABLE`（503）・`DSL_BUSY`（503）。説明は `/api/problems/<code を小文字とハイフンにしたもの>` で見られます。
- **誤りの文言**: 422 の `errors[].message` と照合の警告の `message` は、要求の `Accept-Language` の言語（日本語・英語、既定は日本語）です。YAML・JSON Schema の部品の例外の文言は入りません。
- **本文の大きさの上限の置き場**: 本文の大きさは、ログイン（アクセストークンの確かめ）と認可の後に確かめます。本文を読むのはログインしていてその API を使える人の要求だけです。そのため、**ログインしていない大きな要求は 413 ではなく 401** になります。投入の API だけ上限が `MASTERSMITH_DSL_MAX_SUBMIT_SIZE`（既定 10MB、`DSL_TOO_LARGE`）で、ほかの API は今までどおり `MASTERSMITH_WEB_MAX_REQUEST_BODY_SIZE`（既定 1MB、`PAYLOAD_TOO_LARGE`）です。`Content-Length` がある送り方では本文を読まずに断ります。
- **重い処理は同時に1つ**: 生成・投入・プレビューの表示・履歴からの戻しは、アプリ全体で同時に1つだけ処理します。重なった要求は待たずに 503 `DSL_BUSY` で断り、状態を変えず、監査の出来事も出しません。少し待ってからやり直してください。照合は対象DB が応答しないと最悪 23〜28 秒かかり（「対象DB」の節）、その間ほかの重い処理は `DSL_BUSY` になります。
- **適用**: 見たプレビューの識別（`previewId`）を指定します。1つのトランザクションで履歴への追加・プレビューの削除・上限を超えた古い履歴の削除を行い、確定の後にだけ適用中の DSL を切り替えて監査に記録します。適用中と同じ内容でも履歴に1件足します。
- **履歴からの戻し**: 戻した版は投入の一種として扱い、監査に `DSL_SUBMITTED`（出どころ `RESTORE`）を記録します。今の検証を通らない版（書式の版が変わった など）は投入と同じ誤りの一覧の 422 になり、プレビューは変わらず、受け付けなかった投入の出来事（`DSL_SUBMISSION_REJECTED`）も記録しません。戻したプレビューを適用すると、履歴には出どころ `RESTORE` の新しい版として足されます。
- **起動時**: 履歴の最新（適用した日時が最も新しい版）を適用中の DSL として読みます。今の検証を通らない（書式の版が変わった など）ときは、ERROR（`適用中の DSL を読めないため、適用中の DSL が無い状態で起動します`、識別の先頭 12 文字と誤りの種類だけ）を1件出し、適用中の DSL が無い状態で起動を続けます。
- **保存**: プレビュー（最大1件）と適用の履歴は内部DB の `dsl_previews`・`dsl_applied_revisions`（Flyway の V5）に、受け取ったバイト列のまま入れます。すべて 10MB なら最大約 210MB です。
- **既知の制約（動いている間の内部DB のファイルの大きさ）**: 上限を超えた古い履歴を消しても、アプリが動いている間は H2 がその場所を再利用せず、内部DB のファイルは投入と適用のたびに本文の大きさの分（10MB の DSL なら約 10.8MB）ずつ大きくなります（2026-09-25 の測定で、10MB の DSL の投入と適用を 21 回で約 278MB、40 回で約 483MB。頭打ちになりません）。この伸びは受け入れ、**アプリを止めずに `./docker/hikari-pool.sh compact` で詰め直します**（「内部DBのファイルの詰め直し」の節。詰め直しの間の要求は再開まで待たされます）。アプリを止めたとき（`docker compose restart app` など）にも、接続先の `;DEFRAG_ALWAYS=TRUE`（「環境変数」の表の `MASTERSMITH_DB_URL`）でファイルが詰め直されます。
  - 内部DB のファイルの大きさは、アプリを止めずに読み取りだけで見られます: `./docker/hikari-pool.sh status`（または `docker run --rm -v mastersmith_mastersmith-data:/data:ro eclipse-temurin:25.0.4_7-jre-noble du -sh /data`）。目安として、DSL の投入と適用を重ねて 300MB を超えたら、ディスクの空き（`colima ssh -- df -h /`）を確かめ、利用の少ない時間に `./docker/hikari-pool.sh compact` で詰め直します。

### DSL の操作の監査

生成・投入（履歴からの戻しを含む）・受け付けなかった投入・適用・破棄を、監査の表 `audit_events` に1件ずつ記録します（種類 `DSL_GENERATED`・`DSL_SUBMITTED`・`DSL_SUBMISSION_REJECTED`・`DSL_APPLIED`・`DSL_PREVIEW_DISCARDED`）。

- 列（Flyway の V6 で足した、NULL を許す列）: 操作した管理者の利用者 ID `actor_user_id`、DSL の識別 `dsl_hash`、出どころ `dsl_source`（`GENERATED`・`UPLOAD`・`PASTE`・`RESTORE`）、受け付けなかった投入の理由の種類 `rejection_kind`（最初の誤りの種類、大きさで断ったときは `SIZE_LIMIT`。そのときは `dsl_hash` は空）。ほかに既存の日時・種類・結果・接続元IP・User-Agent・トレースIDを記録します。
- DSL の本文と対象DB の接続先は記録しません。`DSL_BUSY` で断った要求、巻き戻った適用、検証を通らなかった履歴からの戻しは記録しません。
- 記録に失敗しても DSL の操作は成功し、アプリのログに ERROR（`監査イベントの記録に失敗しました`）が1回出ます（本文は載りません）。

### DSL の操作の指標とログ

- 指標 `mastersmith.dsl.operation`（Timer。Prometheus では `mastersmith_dsl_operation_milliseconds_*`）。タグは `operation`（`generate`・`submit`・`restore`・`apply`・`discard`・`compare`）と `outcome`（`success`・`rejected`・`failed`・`busy`）だけです。`compare` はプレビューの表示の中の照合だけの時間です。
- 操作ごとに INFO（`DSL の操作を終えました`）を1件、キー `dsl.operation`・`dsl.outcome`・`dsl.durationMs`・`dsl.hash`（先頭 12 文字）・`dsl.source` で出します。想定内の失敗（413・409・422・503）は WARN でスタックトレースなし、想定外（500）は ERROR です。
- 手元の監視のダッシュボード「MasterSmith の概要」の行「DSL の操作」に、操作ごとの件数・結果ごとの件数・操作ごとの時間の 95 パーセンタイル（目標の線 1 秒・10 秒・30 秒）があります。警報はありません。

## DSL の管理画面（U5）

管理者は、サイドバーの「DSL」（「管理」の次。管理者にだけ表示）から DSL の管理画面 `/admin/dsl` を開きます。画面は上の「DSL の管理の API（U4）」だけを使います。サイドバーの項目を隠すのは表示の切り替えで、使えるかどうかはサーバー側（401・403）で決まります。管理者でない利用者が URL を直接開くと「ページが見つかりません」になります。

- **今の状態**（画面の上部、どのタブでも表示）: 適用中の版とプレビューの識別（先頭 12 文字。全体はツールチップと読み上げで読めます）・出どころ・人・日時（端末の時差で、時差の略号つき）と、「スキーマを読み込む」。
- **プレビュー**のタブ: 検証を通ったこと、対象DB との食い違いの件数（照合できなかったときはその旨を先頭に）、要約（表示名が埋まっていない場所は先頭 100 件と総数）、適用中との違い（既定は違いのあるテーブルだけ。「すべて表示」で変わらないテーブルも表示。行を開くとカラムの違い）、照合の警告、メニューの木。「ダウンロード」「破棄する」「適用する」。
- **投入**のタブ: 「ファイルを選ぶ」と「貼り付ける」を切り替えて入力し、選んでいる方だけを送ります。10MB を超えるものは送る前に案内し、ファイルは読み込みません（上限の確かめは案内で、正はサーバー側の 413）。検証を通らなかったときは、誤りの件数と先頭 100 件（行・列・場所・種類・内容）を示し、入力は残ります。このタブに **DSL の書式（JSON Schema）** のリンク（`/dsl/dsl-schema-v1.json`、ログインなしで取れる静的なファイル）があります。
- **履歴**のタブ: 適用の履歴（新しい順）。適用中の版は「適用中」の文字で示し、その行から適用中の DSL をダウンロードできます。ほかの版は「プレビューに戻す」で今のプレビューに置けます（今の検証を通らないときは誤りの一覧をこのタブに示します）。
- **確かめる表示**: スキーマの読み込み・投入・戻しでプレビューを置き換えるとき（今のプレビューの出どころ・置いた人・日時を表示）、適用するとき（違いの件数と照合の警告の件数を表示）、破棄するときに出ます。はじめのフォーカスは「やめる」で、背景のクリックでは閉じません。
- **失敗の知らせ**: サーバーの `code` から文言を選びます（`DSL_BUSY` は「ほかの処理中です。少し待ってからやり直してください」で、状態を読み直さず入力は残ります）。知らない `code` や通信の失敗は一般の文言にし、`detail` や内部の文言は表示しません。適用が 409 のときは今の状態を読み直し、別の管理者による置き換えか破棄かを分けて示します（自動では適用しません）。
- **読み直し**: 操作の後と、プレビュー・版・適用中が無い（404）応答の後に、今の状態と開いているタブの中身を読み直します。定期的な自動の読み直しはしません。
- **表示言語**: ブラウザの言語が英語なら英語、それ以外は日本語です。テーブル名・カラム名・DSL の中の場所・識別は訳しません。

## 外部エクスポートの確かめ方

受け取ったものを標準出力に出すだけの OTLP の受け手（OpenTelemetry Collector）を、profile `observability` で一緒に起動します。

```bash
# .env に次の2行を書く
#   MASTERSMITH_OBSERVABILITY_EXPORT_ENABLED=true
#   MASTERSMITH_OBSERVABILITY_EXPORT_ENDPOINT=http://otel-collector:4318
docker compose --profile observability up -d --build
curl -s http://localhost:8080/api/no-such-api > /dev/null
docker compose logs -f otel-collector      # トレース・ログはすぐ、指標は 60 秒ごとに届く
```

外部へ送るトレースからは、例外のメッセージとスタックトレース、要求の URL の問い合わせの部分を取り除いています。

外部へ送るログには、ログのキーと値（例: `dsl.operation`・`userId`・`code`）が、キーの名前そのものの属性として付きます。手元の監視（Loki）ではこの属性で絞り込めます。ただし、個人に関する値を持つ4つのキー `email`・`enteredEmail`・`sourceIp`・`userAgent` は、キーを残して値を `[REDACTED]` に置き換えてから送ります（送り出す直前に置き換えるため、標準出力のログ（`docker compose logs app`）では元の値のままです）。ログの本文（メッセージ）と例外の属性は置き換えません。

## 手元の監視（Grafana）

指標・ログ・トレースを手元で見るときだけ、`grafana/otel-lgtm`（OTLP の受け手と Prometheus・Loki・Tempo・Grafana を1つにしたコンテナ）を profile `monitoring` で起動します。既定の起動（`docker compose up`）には含まれません。

```bash
# .env に次の2行を書く（見終わったら消す。送り先が無い間は送信の失敗の警告がログに出るため）
#   MASTERSMITH_OBSERVABILITY_EXPORT_ENABLED=true
#   MASTERSMITH_OBSERVABILITY_EXPORT_ENDPOINT=http://lgtm:4318
docker compose --profile monitoring up -d --wait
# ブラウザで http://localhost:3000/ を開き、ダッシュボードの「MasterSmith」→「MasterSmith の概要」を見る
docker compose --profile monitoring stop lgtm   # 見終わったら止め、.env の2行を消して docker compose up -d で起動し直す
```

- 画面はログインなしの閲覧だけです（`127.0.0.1` にだけ結び付けています）。ダッシュボードと警報の決まりは `docker/monitoring/` のファイルで入れているため、画面からは変えられません。変えるときはファイルを直して `docker compose --profile monitoring up -d --force-recreate lgtm` で読み込み直します。
- 警報は外へは知らせません。Grafana の「Alerting」→「Alert rules」（フォルダー MasterSmith）で状態を見ます。
- DSL の操作（U4）の行の見方は「DSL の管理の API（U4）」の「DSL の操作の指標とログ」を参照してください。
- 指標は 60 秒ごとに届きます。起動の直後は空のパネルがあります。起動より前のログ（Spring の起動のログ）は送られません。
- 監視のコンテナのメモリの上限は 1.5GB（`1536m`）です。900MB では、DSL の行を含むダッシュボードを開いたときに Grafana がメモリの上限で止められました（コンテナは動き続け、画面だけが応答しなくなります）。colima の VM は CPU 4・メモリ 6GiB を前提にしています（アプリ 2GB・見本の対象DB 512MB と合わせて約 4GB。負荷の試験の間は監視を止めます）。VM が小さいときは「コンテナの資源の上限」の手順で広げてください（`colima start --cpu 4 --memory 6`）。
- 集めたデータはボリューム `mastersmith_mastersmith-monitoring` に残ります。消すときは `docker volume rm mastersmith_mastersmith-monitoring`（アプリの内部DBのボリュームとは別です）。
- 外部エクスポートを有効にすると、JVM が `sun.misc.Unsafe` の警告を標準エラーに数行出します（送信に使う protobuf の部品による。1行1件の JSON ではありません）。

## プロキシを置く配備

- エラー応答の `type` の URL を固定するときは、`MASTERSMITH_WEB_BASE_URL` にベースURLを設定します。
- プロキシの内側からだけ受け付ける配備では、`MASTERSMITH_WEB_TRUST_FORWARDED_HEADERS=true` にすると、転送元のヘッダーのスキーム・Host・接続元を使います。外から直接届く配備では有効にしないでください（ヘッダーを偽装できるため）。
- HTTPS（`Strict-Transport-Security` を含む）は、配備先が決まったときに扱います。

## スキーマの変更（Flyway）

- 置き場所: `backend/src/main/resources/db/migration`
- ファイルの名前: `V<番号>__<単位>_<内容>.sql`（例: `V2__u2_user_account.sql`）。単位ごとにファイルを分けます。
- 前進のみとし、適用済みのファイルは書き換えません（書き換えると起動時の検証で起動が止まります）。1つ前の版のアプリが動く後方互換を保ちます。
- Hibernate はスキーマを作らず、検証だけ行います。
- V7（`V7__u2_user_preferences.sql`、Intent 260925-user-management の U2）: `users` に `display_name`（必須、既定の値なし。既存の利用者にはメールアドレスを入れる）・`language`（既定 `ja`）・`theme`（既定 `system`）・`font_size`（既定 `md`）を、`audit_events` に `target_user_id`・`target_invitation_id`（空を許す）を足します。前進のみで、1つ前の版のアプリが動く後方互換を保ちます。確かめは2段です。(1) 自動の結合テスト（`V7MigrationIT`・`V7BackwardCompatibilityIT`）で、V6 までしか知らない Flyway が V7 の後の内部DB で失敗しないこと、既存の利用者の初期値、1つ前の版の追記の形の既知の限界を確かめます。(2) 1つ前の版を V7 の後の内部DB の複写で起動する確かめ（Hibernate の検証が足した列を許すことを含む）は、配備の段の戻しの練習で行います。
- V8（`V8__u3_invitation.sql`、Intent 260925-user-management の U3）: 招待の表 `invitations` を新しく足します（既存の表は変えない）。同じメールアドレスの招待中を1件に限るため、状態が `PENDING` のときだけメールアドレスになる生成列 `pending_email` に一意の制約を付けます。トークンはハッシュ（SHA-256）だけを保存します。確かめは2段です。(1) 自動の結合テスト（`V8MigrationIT`・`V8BackwardCompatibilityIT`）で、生成列と一意の制約のふるまい、V7 までしか知らない Flyway が V8 の後の内部DB で失敗しないことを確かめます。(2) 1つ前の版を V7・V8 の後の内部DB の複写で起動する確かめは、配備の段の戻しの練習で行います。

## API のアクセス制御（U3）

`/api/` の下は**既定でログインが必要**です。ログインなしで呼べるのは、次の明示した一覧だけです。

| パス | 置く単位 | 理由 |
|---|---|---|
| `/actuator/health` | U1 | 起動確認 |
| `/api/problems/**` | U1 | 誰でも読める説明文書 |
| `/api/auth/login` | U2 | ログインする前に呼ぶ |
| `/api/auth/session/**` | U2 | 更新は Cookie で認証し、ログアウトは期限切れでも呼べる必要がある |
| `GET /api/appearance`（GET だけ） | U8（Intent 260925-user-management） | ログインの前の画面も見た目の設定を読む |
| `POST /api/registration/verify`・`POST /api/registration/complete`（POST だけ） | U3（Intent 260925-user-management） | 招待された人がログインの前にリンクを確かめ、登録を完了する |

- **管理者のみの範囲**: `/api/admin` そのものと `/api/admin/**` は管理者だけが使えます。未ログインは 401 / `AUTHENTICATION_REQUIRED`、管理者でない利用者は 403 / `ACCESS_DENIED` になります。存在しない管理 API も、管理者でない利用者には 403 になります（有無を明かさないため）。管理者かどうかは、要求ごとに内部DBから読んだ値で判断します。
- 後の単位が管理者のみの API を足すときは、**`/api/admin/` の下に置いてください**（個々の API での宣言には頼りません）。それ以外の `/api/` の下は、置くだけでログインが必要になります。
- **確認用 API**: `GET /api/admin/check` は、管理者なら 204（内容なし）を返します。画面の管理者向け領域が、表示のたびにこれを呼びます。
- **正規化されていないパスの拒否**: エンコードされた区切り・`;`・`..`・連続した `//` などを含む要求は、判定の前に 400 / `REQUEST_REJECTED` で拒否します。応答はほかのエラーと同じ形（`type`・`code`・`traceId`）で、安全のためのヘッダーも付きます。拒否したパスはログに出しません。
- **画面**: サイドバーの「管理」は管理者にだけ表示されますが、これは表示の切り替えにすぎません。判定は必ずサーバー側で行われます。
- 環境変数は増えません。
- **登録の完了の公開の API（U3）**: 差し込み口（order 310）で上の2つの POST だけを公開します。`/api/registration/` のほかの道・ほかのメソッドはログインが必要です。公開の道でも、壊れた・期限切れのアクセストークンを付けると 401 になります（画面はトークンを付けずに呼ぶ）。**回数の制限は置いていません**（受け入れた危険 R1）。誤ったトークンで完了を呼ぶたびに監査に `REGISTRATION_FAILED` が1行増えます。急な増えに自動で気づく仕組みは無いため、「監査ログの確かめ方」の数える問い合わせで見つけます。

## 監査ログ（U4）

認証（ログインの成功・失敗・ログアウト）と管理者のみの API へのアクセスの拒否を、内部DBの `audit_events` の表に1件ずつ**追記**します。

- **追記だけ**です。アプリは監査イベントを変える・消す処理を持ちません。保存の期間は無期限で、古い記録を消す仕組みもありません（保存の期間と古い記録の扱いは後続 Intent で決めます）。
- **監査ログを見る画面・API は、この Intent では作りません。** 当面の確認は、開発者が内部DBを読み取りで開いて行います。
- 記録する項目は、発生の日時・種類（`LOGIN_SUCCEEDED`・`LOGIN_FAILED`・`LOGGED_OUT`・`ACCESS_DENIED`）・結果・入力されたメールアドレス・失敗の理由・接続元IP・User-Agent・要求のパス（アクセスの拒否のときだけ）・トレースIDです。パスワード・トークン・ハッシュ値は記録しません。
- パスワードの変更（Intent 260925-user-management の U2）も記録します。種類は `PASSWORD_CHANGED` で、成功（結果 `SUCCESS`）と今のパスワードの誤り（結果 `FAILURE`、失敗の理由 `CURRENT_PASSWORD_MISMATCH`）を1件ずつ記録します。操作した人（`actor_user_id`）と対象の利用者（`target_user_id`）に本人の利用者 ID が入ります。入力の誤りと、氏名・表示の設定の保存は記録しません。
- 招待と登録の完了（Intent 260925-user-management の U3）も記録します。種類は `INVITATION_ISSUED`・`INVITATION_RESENT`・`INVITATION_CANCELLED`（操作した管理者 `actor_user_id` と対象の招待 `target_invitation_id`、結果 `SUCCESS`）、`REGISTRATION_COMPLETED`（操作した人は空、対象の招待と作った利用者 `target_user_id`）、`REGISTRATION_FAILED`（結果 `FAILURE`、失敗の理由 `INVITATION_EXPIRED`・`INVITATION_ALREADY_USED`・`INVITATION_CANCELLED`・`INVITATION_NOT_FOUND`・`EMAIL_ALREADY_REGISTERED`、対象の招待は見つかったときだけ）です。拒否（400・404・409・503）・送信の失敗・リンクの確かめ・入力の誤り・期限切れの置き換えは記録しません。トークン・招待の URL・メールアドレスは記録しません。
- 対象の列（V7）: `target_user_id`（対象の利用者）・`target_invitation_id`（対象の招待。招待と登録の完了の出来事で使います）。どちらも空を許し、それ以前の種類の記録では空のままです。
- 監査イベントの `trace_id` は、同じ要求のアプリのログの `traceId` と一致します。1つの要求を追うときは、この値でログを絞り込みます。
- 記録に失敗しても、ログイン・ログアウト・401／403 の応答は変わりません。失敗したときは、アプリのログに ERROR（`監査イベントの記録に失敗しました`）が1回出ます。**この ERROR には、記録しようとした項目（メールアドレスを含む）がキーと値で載ります**。後から手で記録を補えるようにするためで、U4 に限った扱いです。パスワード・トークンは載りません。外部エクスポートで外へ送るときは、メールアドレス・接続元IP・User-Agent の値を `[REDACTED]` に置き換えます（「外部エクスポートの確かめ方」）。元の値は標準出力のログで見ます。
- パスワードの変更の記録に失敗したときの ERROR には、メールアドレスは載らず（出来事がメールアドレスを持たないため）、操作した人と対象の利用者（`actorUserId`・`targetUserId`・`targetInvitationId`）が載ります。記録に失敗しても、パスワードの変更の応答（204・400）は変わりません。
- 1件の書き込みに 200 ミリ秒を超えてかかった場合は、WARN（`監査イベントの記録に時間がかかりました`）が1回出ます。1日に数件以上出るときは、記録の量と内部DBの待ちを確かめてください。
- 環境変数は増えません。

### 既知の制約（同時の要求と接続プール）

- ログイン（成功・失敗）とログアウトでは、確定の後に同じスレッドで監査を記録します。このとき、業務で使った内部DBの接続（1本目）を持ったまま、記録のために2本目の接続を借ります。
- そのため、接続プールの上限（`MASTERSMITH_DB_MAXIMUM_POOL_SIZE`、既定 30）以上の同時の要求が来ると、2本目の接続の待ちが時間切れ（5 秒）になり、監査の記録が欠けることがあります。ログイン自体は成功し、アプリのログに ERROR（`監査イベントの記録に失敗しました`）が出ます。上限は以前は 10 で、同時 10 件のログインでこの欠けが起きていました。
- Tomcat の同時処理のスレッドの上限は設定しておらず、既定の 200 です。プールの上限より多い要求が同時に来ることがあります。
- 同時の数を増やす場合は、`MASTERSMITH_DB_MAXIMUM_POOL_SIZE` で上限を上げます。その際は、コンテナのメモリの上限の内側で動くこととヘルスチェックへの影響を、起動と負荷の試験で確かめてください。

### 監査ログの確かめ方

アプリを止めてボリュームを複写し、**複写したファイル**を読み取りで開きます（H2 のコンソールは使いません）。

```bash
docker compose stop app
mkdir -p ~/.mastersmith-backup && chmod 700 ~/.mastersmith-backup
docker run --rm -v mastersmith_mastersmith-data:/data -v "$HOME/.mastersmith-backup":/backup eclipse-temurin:25.0.4_7-jre-noble \
  tar czf /backup/mastersmith-data-$(date +%Y%m%d%H%M).tgz -C /data .
docker compose start app
# 複写を展開し、H2 の道具で読み取り（ACCESS_MODE_DATA=r）で開いて audit_events を読む
```

誤ったトークンの登録の完了の増え（U3 の残る危険 R1）は、開いた複写で次の形で数えます（時間の幅は見たい期間に合わせる）。とくに `INVITATION_NOT_FOUND` が1つの送り元から多いときは総当たりの疑いとします。

```sql
SELECT failure_reason, source_ip, COUNT(*) AS failures
  FROM audit_events
 WHERE event_type = 'REGISTRATION_FAILED'
   AND occurred_at >= TIMESTAMP WITH TIME ZONE '2026-01-01 00:00:00+00:00'
 GROUP BY failure_reason, source_ip
 ORDER BY failures DESC;
```

複写したファイルにもメールアドレスと接続元IPが残ります。実行の利用者と管理する者だけが読める場所に置いてください。

### 消してはいけない操作

- **ボリュームを消す操作（`docker compose down -v` など）を、バックアップの前に行わないでください。** 監査ログも一緒に消えます。
- 版を替える前は、上の「内部DBのバックアップと戻し方」の手順でボリュームを複写します。この手順で監査ログも一緒に守られます。
- OS の権限を持つ者によるファイルの直接の書き換えは、アプリでは検知できません（受け入れている危険です）。

## 利用者のプリファレンスとパスワードの変更（U2）

Intent 260925-user-management の U2 で、ログインした利用者が自分の氏名と表示の設定を読み書きし、パスワードを変える API を足しました（契約 C4）。

| API | 応答 | 監査 |
|---|---|---|
| `GET /api/me/preferences` | 200 と `displayName`・`language`（`ja`・`en`）・`theme`（`light`・`dark`・`system`）・`fontSize`（`sm`・`md`・`lg`）。メールアドレスと管理者かは返しません。`theme` の `system` はそのまま返します | 記録しない |
| `PUT /api/me/preferences` | 4つをまとめて置き換え、200 と保存した値。1つでも誤りがあれば何も変えずに 400 | 記録しない |
| `POST /api/me/password` | 本文は `currentPassword`・`newPassword`・`newPasswordConfirmation`。変えたら 204（本文なし） | 成功と今のパスワードの誤りを `PASSWORD_CHANGED` で記録 |

- **使える人**: ログインした利用者なら誰でも、自分の分だけを使えます（管理者でなくても使え、403 の場面はありません）。対象はアクセストークンの本人だけで決め、URL・本文で利用者 ID やメールアドレスを受け取りません（本文に入れても読み捨てます）。アクセストークンが無い・無効なとき、認証の後に本人の行が消えていたときは 401 / `AUTHENTICATION_REQUIRED` です。`/api/` の既定のログイン必須に乗るため、アクセスの決まりの設定は増えていません。
- **氏名の決まり**: 前後の空白（全角の空白・タブ・改行なども含む）を除いてから判定し、除いた値を保存します。除いた後が空なら誤り、254 文字（コードポイントで数えます。絵文字も1文字）まで、制御文字と見えない書式の文字（ゼロ幅の空白など）は誤りです。
- **パスワードの決まり**: 新しいパスワードは作成のときと同じ規則（12 文字以上、UTF-8 で 72 バイト以内）で、確かめの値と完全に一致する必要があります。今と同じパスワードへの変更も受け付けます。変えても、発行済みのアクセストークンは有効期限まで使え、ほかの端末を含むリフレッシュトークンもそのまま使えます（ログアウトはしません）。
- **入力の誤り**: 400 / `VALIDATION_FAILED` に、項目ごとの誤り `fieldErrors`（`[{ "field": "displayName", "reason": "TOO_LONG" }, ...]`）が付きます。`field` は要求の項目名、`reason` は次のどれかで、要求の項目の順に、1つの項目に1つだけ載ります。入れた値は載りません。本文が JSON として読めないときは、今までどおり 400 / `MALFORMED_REQUEST`（`fieldErrors` なし）です。

  | `reason` | 意味 |
  |---|---|
  | `REQUIRED` | 値が無い（氏名は前後の空白を除いた後の空、今のパスワードの空を含む） |
  | `TOO_SHORT` | 新しいパスワードが 12 文字未満 |
  | `TOO_LONG` | 氏名が 254 文字を超える、新しいパスワードが UTF-8 で 72 バイトを超える |
  | `INVALID_CHARACTER` | 氏名に制御文字・見えない書式の文字がある |
  | `INVALID_VALUE` | 言語・テーマ・文字の大きさが決めた値に一致しない（大文字や前後の空白は一致しません） |
  | `MISMATCH` | 確かめの値が新しいパスワードと一致しない |

- **今のパスワードの誤り**: 400 / `PASSWORD_CURRENT_MISMATCH`（ログインの状態は変わらないため 401 にしません）。説明文は要求の `Accept-Language`（`ja`・`en`）で選ばれます。2つの端末からほぼ同時に変えたときは、後の方もこの誤りになります（先に確定した新しいパスワードを上書きしません）。
- **今のパスワードの誤りの回数は制限しません**（受け入れている危険です。ログインの失敗回数・ロックにも数えません）。総当たりの疑いを調べるときは、上の「監査ログの確かめ方」の手順で複写を読み取りで開き、次の問い合わせで利用者ごとの誤りの件数を見ます（定期の実行や警報にはしていません）。

  ```sql
  SELECT actor_user_id, COUNT(*) AS failures, MIN(occurred_at), MAX(occurred_at)
    FROM audit_events
   WHERE event_type = 'PASSWORD_CHANGED' AND result = 'FAILURE'
     AND failure_reason = 'CURRENT_PASSWORD_MISMATCH'
     AND occurred_at >= TIMESTAMP WITH TIME ZONE '2026-09-01 00:00:00+00'
   GROUP BY actor_user_id ORDER BY failures DESC
  ```

- **秘密と個人情報**: パスワード（今・新しい・確かめ）・ハッシュ・トークン・メールアドレスは、アプリのログ・監査の記録・エラー応答に出しません（メソッドの呼び出しの追跡を TRACE にしても出ません）。
- **ログインと更新の応答**: `POST /api/auth/login` と `POST /api/auth/session/refresh` の応答の `user` に、`email`・`admin` に加えて `displayName`・`language`・`theme`・`fontSize` が載ります（契約 C3）。内部DB への問い合わせは増えていません。
- **既存の利用者と初期管理者の初期値**: 氏名はメールアドレス、言語 `ja`、テーマ `system`、文字の大きさ `md` です。
- 環境変数は増えません（`.env.example` は変わりません）。

### 契約との差

契約の文書（`aidlc/spaces/default/intents/260925-user-management/inception/contract-design/contract-summary.md`）は書き換えず、実装との差をここに記録します（依頼者の決定）。後の単位（U3 の登録の完了、U7 の画面）は、この形を正として読みます。

- **C4**: 400 / `VALIDATION_FAILED` に、項目ごとの誤り `fieldErrors` と上の `reason` の一覧を足しました（安全な項目の追加）。
- **C8**: `PASSWORD_CHANGED` に、対象の利用者 `target_user_id`（本人と同じ値）を足しました。

## 招待と登録の完了（U3）

Intent 260925-user-management の U3 で、管理者が利用者をメールで招待し、招待された人がリンクから登録を完了する API を足しました（契約 C5・C6・C10）。画面は後の単位（U5・U6）の受け持ちです。

| API | 認可 | 成功 | 主な失敗 |
|---|---|---|---|
| `POST /api/admin/invitations`（`email`・`language`） | 管理者だけ（401・403） | 201 と招待 | 400 `VALIDATION_FAILED`・409 `INVITATION_EMAIL_REGISTERED`・409 `INVITATION_ALREADY_PENDING`（`invitationId`・`page`）・503 `INVITATION_NOT_CONFIGURED`（`unavailableReasons`） |
| `GET /api/admin/invitations?page=` | 管理者だけ | 200 と1ページ（20 件、招待した日時の新しい順、`invitationEnabled`・`unavailableReasons`） | 400 `VALIDATION_FAILED`（page が 1 以上の整数でない） |
| `POST /api/admin/invitations/{invitationId}/resend` | 管理者だけ | 200 と招待（新しいトークンと有効期限） | 404 `INVITATION_NOT_FOUND`・503 `INVITATION_NOT_CONFIGURED` |
| `POST /api/admin/invitations/{invitationId}/cancel` | 管理者だけ | 204（設定によらず取り消せる） | 404 `INVITATION_NOT_FOUND` |
| `POST /api/registration/verify`（`token`） | ログインなし | 200 と `email`・`language` | 404 `REGISTRATION_LINK_INVALID` |
| `POST /api/registration/complete`（`token`・`displayName`・`password`・`passwordConfirmation`・`language`・`theme`・`fontSize`） | ログインなし | 204（自動ではログインしない） | 400 `VALIDATION_FAILED`・404 `REGISTRATION_LINK_INVALID` |

- **招待を使える設定**: `MASTERSMITH_WEB_BASE_URL` とメールの送信の設定（`SPRING_MAIL_HOST`・`MASTERSMITH_MAIL_FROM`）の両方がそろうときだけ使えます。足りないと招待と送り直しだけが 503 で、理由 `BASE_URL_NOT_CONFIGURED`・`SMTP_NOT_CONFIGURED` の順に並びます。起動時に INFO（`招待を使える設定かを点検しました`）が1件出ます。ベースURL の形が正しくないときは、項目の名前だけの WARN が1件出ます（値は出しません）。
- **招待の URL**: `MASTERSMITH_WEB_BASE_URL` ＋ `/register#token=` ＋ トークン。要求の Host ヘッダーからは組み立てません。トークンは URL のフラグメントにだけ載り、API は要求の本文でだけ受け取ります。
- **項目ごとの誤り**: 400 には `fieldErrors: [{field, reason}]` が載ります（`reason` は `REQUIRED`・`TOO_SHORT`・`TOO_LONG`・`INVALID_CHARACTER`・`INVALID_VALUE`・`MISMATCH`）。入れた値は載せません。一覧の page の誤りには載りません。
- **sendResult**: 送信の結果は `SENT`・`FAILED` の2つで返します。内部では確定の時点で `PENDING`（送信中・結果不明）とし、送信の途中でアプリが止まると `PENDING` のまま残りますが、API では `FAILED` として返します（一覧から送り直せる）。送信に失敗しても招待は作られ、招待は 201・送り直しは 200 で `FAILED` を返します。失敗のときはアプリのログに U1 の WARN と U3 の INFO（`invitationId`・`operation`・`failureKind`）が1件ずつ出ます。
- **リンクの拒否**: 期限切れ・使用済み・取り消し済み・置き換え済み・存在しない・改ざん・同じメールアドレスの利用者がいる、のどれでも同じ 404 の本文です。理由は監査の `failure_reason` にだけ残ります。
- **同じメールアドレス**: 期限内の招待中があれば 409（その招待の ID と一覧のページ）、期限切れなら新しい招待で置き換えます（古いリンクは使えなくなる）。
- **回数の制限**: 置いていません（R1。「API のアクセス制御（U3）」）。
- **秘密と個人情報**: トークン・招待の URL・メールアドレスは、アプリのログ・監査ログ・トレースの属性・エラー応答に出しません（管理者だけが見る正常の応答の `email`・`invitedBy` は除く）。内部DB にはトークンのハッシュだけを保存します。
- **定期の削除**: 毎日 `MASTERSMITH_INVITATION_CLEANUP_CRON` の時刻に、保存の長さを過ぎた招待を消し、消した件数を INFO で出します。失敗は ERROR で、次の回に任せます。

### 契約との差

契約の文書（`aidlc/spaces/default/intents/260925-user-management/inception/contract-design/contract-summary.md`）は書き換えず、実装との差をここに記録します（依頼者の決定）。後の単位（U5・U6）は、この形を正として読みます。

- **C2**: U3 が通る経路の口を、文字列にすると値を伏せる型 `RedactedText` で受け渡す形にしました（`existsByEmail(RedactedText)` を足し、`findDisplayName` の戻り値を `Optional<RedactedText>` にした）。既存の `existsByEmail(String)`（初期管理者）とログインの経路は据え置きです。
- **C5**: `invitedBy` は招待した管理者の氏名だけで、利用者の行が無ければ空の文字列です（メールアドレスへは切り替えません）。招待の 400 に `fieldErrors` を足しました。送り直しの有効期限は「24 時間」ではなく `MASTERSMITH_INVITATION_VALIDITY` の長さです。
- **C6**: 登録の完了の 400 に `fieldErrors` を足しました。
- **C8**: `REGISTRATION_FAILED` の失敗の理由に `EMAIL_ALREADY_REGISTERED` を足しました。
- **C10**: 差し込みに `validityHours` を足し、本文の有効な期間の文を「このリンクは {{validityHours}} 時間有効です」（英語は「This link is valid for {{validityHours}} hours.」）にしました。

## 招待の管理の画面（U5）

Intent 260925-user-management の U5 で、管理者が招待中の人を一覧で確かめ、招待・送り直し・取り消しを行う画面を `frontend/src/features/invitation/` に足しました（API は「招待と登録の完了（U3）」）。

- **開き方**: サイドバーの「利用者の招待」、または `/admin/invitations`。サイドバーの項目と画面は管理者にだけ出しますが、判定はサーバー側（401・403）で行います。
- **一覧**: 20 件ごとで、サーバーの順（招待した日時の新しい順）のまま出します。今のページは画面の中だけに持ち、URL には載せません（開くたびに1ページ目）。自動の読み直しはしません。開いたまま有効期限を過ぎた行は、次に一覧を読むまで「期限内」のまま出ます（送り直し・取り消しは期限切れでも行えます）。送信の結果と状態は文字（「送信済み」「送信に失敗」「期限内」「期限切れ」）で示します。
- **招待・送り直し・取り消し**: 送信の間はボタンが「送信しています」になり、招待の入力の Modal は閉じません。画面の側に時間切れを置いていないため、メールの受け手が応答の手前で遅れ続けると、待ちはサーバーの SMTP の時間切れまで続きます（既知の限界）。成功は Toast で、失敗は一覧の上の知らせ（1つだけ）で示します。招待を使える設定（SMTP・ベース URL）が無いときは警告を出し、「招待する」と「送り直す」を押せなくします（「取り消す」は押せます）。
- **狭い幅の表**: 表は make-you-chic-ui の Table の包む要素の中で横に動きます。Tab で行のボタンに届くと、その要素を見える位置まで動かします。矢印のキーでの手動の横の移動はありません。
- **日時の書式**: `formatDateTime` を `frontend/src/features/dsl/format.ts` から `frontend/src/shared/format/formatDateTime.ts` へ移し、DSL と招待の2つの画面で使います。
- **契約との差**: 招待した管理者は、U3 の「契約との差」の C5 の `invitedBy` のとおり氏名だけを出し、空なら「（不明）」と出します。
- **既知の制約**: ブランドカラーが `green`・`orange` のとき、この画面の primary のボタンもコントラストが足りません（「画面の表示の設定（U4）」の既知の制約）。

## 登録の完了の画面（U6）

Intent 260925-user-management の U6 で、招待された人が招待メールのリンクから登録を完了する画面を `frontend/src/features/registration/` に足しました（API は「招待と登録の完了（U3）」）。

- **開き方**: 招待メールのリンク（`<MASTERSMITH_WEB_BASE_URL>/register#token=…`）。ログインなしで開け、アプリの枠（サイドバー・トップバー）の外の単独の画面です。サイドバー・ユーザーメニューの項目はありません。
- **トークンの扱い**: 開いた直後に、アドレス欄からフラグメント（`#token=…`）を履歴の項目の置き換えで消し、トークンは画面のメモリだけに持ちます（ブラウザの保存・URL・コンソールに出しません。API へは要求の本文だけで送ります）。そのため、読み込み直すと「このリンクは使えません」になります。メールのリンクを開き直せば続けられます。
- **閲覧の履歴**: フラグメントを消す前のアドレス（`/register#token=…`）は、ブラウザの閲覧の履歴に残ります。画面のある Chromium を使い捨てのプロファイルで1回確かめたところ、トークンを含むアドレスが履歴に1行残りました（アドレス欄からは消えています）。Playwright の headless の Chromium は閲覧の履歴を作りませんでした。これは依頼者が受け入れた残る危険です（U6 の NFR 設計の承認の場の A9、コード生成の承認の場）。使い終えたトークンと有効期限が過ぎたトークンは使えません（「このリンクは使えません」になる）。登録を終える前の使える間は、同じ端末の閲覧の履歴を見られる人がリンクを開き直せます。
- **ログインしたまま開いたとき**: リンクを確かめずに「ログインしたままです。登録を続けるには、ログアウトしてください。」と「ログアウトして続ける」「ホームへ戻る」を出します。「ログアウトして続ける」でログアウト（リフレッシュトークンのサーバー側の無効化を含む）の後にリンクを確かめます。
- **確かめの失敗**: 期限切れ・使用済み・取り消し・改ざん・存在しないリンク（404 `REGISTRATION_LINK_INVALID`）は、理由によらず同じ「このリンクは使えません。いちばん新しい招待メールのリンクを使うか、招待した管理者に招待の送り直しを依頼してください。」と「ログインの画面へ」を出します。通信の失敗やそのほかの応答は「読み込めませんでした。…」と「もう一度読み込む」（同じリンクで確かめ直す）を出します。サーバーの説明文（`detail`）は画面に出しません。
- **表示の設定**: 開いた時点は、言語が招待の言語、テーマと文字の大きさがそのブラウザに最後に保存された値です。フォームの言語・テーマ・文字の大きさは、選んだ軸だけを選んだ時点で画面に当てます（保存はしない）。完了せずに離れると、ブラウザの保存の値に戻ります。完了すると選んだ3つをブラウザに保存します。
- **完了の後**: 自動ではログインしません。ログインの画面へ移り、「登録が完了しました。設定したパスワードでログインしてください。」と、メールアドレスの欄に招待のメールアドレスを出します（メールアドレスは画面の中のメモリだけで渡し、URL に載せません）。成功の Toast は出しません。
- **入力の確かめ**: 送る前に、サーバーと同じ決まり（氏名は前後の空白を除いて 1〜254 文字で改行・タブ・見えない文字を含まない、パスワードは 12 文字以上で UTF-8 の 72 バイトまで、確かめが一致）を画面でも確かめます。判定はサーバーが正です。確かめの関数は `frontend/src/shared/validation/` に置き、U7（パスワードの変更・プリファレンス）と共用します（`frontend/src/features/README.md`）。
- **契約との差**: 登録の完了の 400 の `fieldErrors`（U3 の「契約との差」の C6）は読まず、「入力を確かめてください。…」の知らせだけを出します。
- **既知の制約**: ブランドカラーが `green`・`orange` のとき、この画面の「登録を完了する」のボタンもコントラストが足りません（「画面の表示の設定（U4）」の既知の制約）。

## プリファレンスとパスワードの変更の画面（U7）

Intent 260925-user-management の U7 で、ログインした利用者が自分の氏名・言語・テーマ・文字の大きさを変える画面と、自分のパスワードを変える画面を `frontend/src/features/preferences/` に足しました（API は「利用者のプリファレンスとパスワードの変更（U2）」）。

- **開き方**: トップバーのユーザーメニューの「プリファレンス」「パスワードの変更」（ログアウトより上）、または `/me/preferences`・`/me/password`。ログインが要り、未ログインで開くとログインの画面へ移ります。サイドバーには出しません。ユーザーメニューから読み込み直しなしで移ります（「後の単位（U2・U3・U4）が使う差し込み口」の画面の差し込み口）。
- **開いた時点の値**: 開くたびに内部DB の値を読みます。読んだ値が画面に当たっている値と違えば（ほかの PC で保存した後など）、内部DB の値に画面をそろえるため、開いた時点で言語やテーマが変わることがあります（知らせは出しません）。テーマ `system` の利用者には「OS に合わせる」が選ばれた状態で出ます。読めないときは「プリファレンスを読み込めませんでした。」と「もう一度読み込む」を出します。
- **選んだ時点の見せ方**: テーマと文字の大きさは選んだ時点で画面に当たります（保存はまだ）。言語は保存すると切り替わります。「元に戻す」か、保存せずに画面を離れると、元の見た目に戻ります（保存していない変更の確かめは出しません）。
- **保存の後**: ユーザーメニューの名前・画面の言語・見た目がすぐに保存した値になり、Toast「保存しました」（新しい言語の文言）を出します。氏名の前後の空白はサーバーが除いた値で表示します。
- **パスワードの変更**: 今のパスワード・新しいパスワード・確かめの3つで変えます。変えてもログインしたままで、ほかの端末もログインしたままです。今のパスワードが違うときは、その項目に「今のパスワードが正しくありません」を出します（ログアウトはしません）。成功すると3つの項目を空にして Toast「パスワードを変更しました」を出します。
- **入力の確かめ**: 送る前に、共用の関数（`frontend/src/shared/validation/`）でサーバーと同じ決まりを確かめます（今のパスワードは空だけを見ます）。判定はサーバーが正で、サーバーの 400 の項目ごとの誤り（`fieldErrors`）は同じ項目の下に同じ文言で出します。対応づけられない誤りは「入力を確かめてください。」、ほかの失敗は「保存できませんでした。…」などの知らせです。サーバーの説明文（`detail`）は画面に出しません。
- **見せ方の最中のトークンの更新**: テーマか文字の大きさを選んで見せている間にトークンの更新（ログイン状態の更新）が起きると、画面は当たっている値に戻り、フォームの選択は選んだ値のまま残ります（食い違い）。次の選択・「元に戻す」・保存で解けます（U4 の決まりのまま、依頼者の決定で追いません。コード生成の単体テストで確かめました）。
- **秘密と個人情報**: 氏名とパスワードは画面のメモリだけに持ち、ブラウザの保存・URL・コンソールに出しません（ブラウザに保存するのは U4 の3つの表示の設定だけ）。
- **既知の制約**: ブランドカラーが `green`・`orange` のときの「保存する」「変更する」、2語の氏名のときのトップバーのアバター、ダークのテーマの項目の誤りの文字は、コントラストが足りません（「画面の表示の設定（U4）」の既知の制約）。

## インスタンスの見た目の設定（U8）

Intent 260925-user-management の U8 で、インスタンス全体のブランドカラーとフォントファミリーを設定から読み、ログインなしで読める API で画面へ渡すようにしました（契約 C7）。画面に当てるのは U4 の受け持ちです。

| 環境変数 | 許される値 | 既定 |
|---|---|---|
| `MASTERSMITH_APPEARANCE_BRAND_COLOR` | `blue`・`green`・`purple`・`orange` | `blue` |
| `MASTERSMITH_APPEARANCE_FONT_FAMILY` | `sans`・`serif` | `sans` |

- **値の読み方**: 大文字・小文字と前後の空白は問いません（例: ` Green ` は `green`）。無い・空・空白だけなら既定を使い、警告は出しません。
- **許されない値**: 既定を使い、アプリの起動時に項目ごとに WARN（`見た目の設定に許されない値が指定されたため、既定の値を使います`）を1件出します。起動は止めません。ログのキーは `property`（項目の名前。例 `mastersmith.appearance.brand-color`）・`defaultValue`（使った既定の値）・`allowedValues`（許される値の一覧）で、**設定された値そのものは出しません**。警告は起動時の1回だけで、要求のたびには出ません。
- **変更の当て方**: 値は起動時に1回だけ読みます。変えたら `.env` を直してアプリを起動し直します（`docker compose up -d`）。
- **API**: `GET /api/appearance` は、ログインなしで 200 と `{ "brandColor": "<名前>", "fontFamily": "<名前>" }` を返します。値は常に上の表の小文字の名前で、2項目の外は載せません。内部DB を使わないため、接続プールが尽きたときも答えます。
- **GET の外**: `GET` 以外のメソッドは公開していません。未ログイン・使えないトークンなら 401 / `AUTHENTICATION_REQUIRED`、使えるトークン付きなら 405 / `METHOD_NOT_ALLOWED`（`Allow` の見出しつき）です。`HEAD` も未ログインでは 401 です。使えないトークン（期限切れ・改ざん）を付けた `GET` も 401 になるため、画面はこの API にトークンを付けずに呼びます。
- **監査**: 読み取りは監査ログに残しません。
- **回数の制限**: 置いていません（返すのは秘密を含まない2つの名前だけ）。配備先が決まったときに前段で扱います。
- **戻すとき**: 前の版のイメージに戻しても、`.env` の2項目を消す必要はありません（前の版は読みません）。
- **コントラストの既知の制約**: `green`・`orange` を選ぶと、primary のボタンの文字のコントラストが WCAG AA に届きません（「画面の表示の設定（U4）」の「既知の制約」）。AA を満たしたいときは `blue`・`purple` を選びます。

## 画面の表示の設定（U4）

Intent 260925-user-management の U4 で、すべての画面に表示の設定（言語・テーマ・文字の大きさ）とインスタンスの見た目の設定（U8）を当てる土台を `frontend/src/app/display-settings/` に置きました。

- **3つの軸と当て方**: 言語（`ja`・`en`）・テーマ（`light`・`dark`・`system`）・文字の大きさ（`sm`・`md`・`lg`）。ログインの前は、そのブラウザに最後に保存された値（無い軸は、言語がブラウザの言語設定、テーマが `system`、文字の大きさが `md`）。ログインの後は利用者の設定（ログイン・セッションの復元・トークンの更新の応答の値）で、同じブラウザに残った前の利用者の値は使いません。テーマ `system` は OS の配色に追従します（開いたままの切り替えにも）。
- **ブラウザに残す値**: localStorage の `mastersmith.display-settings`（`language`・`theme`・`fontSize` の3つだけ）と、make-you-chic-ui の鍵（`design-system-*`。U4 の鍵の写しとして、画面を描く前に書き直す）だけです。トークン・メールアドレス・氏名は残しません。保存するのは、ログインで利用者の設定を受けたとき、プリファレンスの保存、登録の完了、ログインの画面の言語の切り替え（言語だけ）のときで、ログアウトでは消しません。
- **見た目の設定の読み方**: 画面を開いたときに1回だけ `GET /api/appearance` をセッションの復元と並べて読み、両方の答えが出るまで画面を描きません。読み取りに失敗したら前に当てた値（無ければ `blue`・`sans`）で描き、読み直しません。待ちに上限を置いていないため、この API の応答が返らない（ハング）と、ログインの画面を含むすべての画面の最初の描画が止まります（依頼者が受け入れた決定。今のセッションの復元と同じ待ち方）。
- **ログインの画面の言語の切り替え**: ログインの画面の右上に「日本語」「English」のボタンの組を置きます。選ぶと画面の言語が切り替わり、ブラウザの保存の値の言語だけを書き換えます。
- **登録の完了からの受け渡し**: 登録の完了の画面から、メールアドレスを画面の中のメモリだけで1回だけログインの画面へ渡し、案内とメールアドレスの初期値を出します（URL・ブラウザの保存に載せない）。
- **要求の言語**: すべての要求（認証の API・公開の API を含む）に、画面の言語を `Accept-Language`（`ja`・`en` だけ）として付けます。呼び出し側が指定したときは上書きしません。
- **トークンを付けないパス**: `frontend/src/shared/api-client/apiClient.ts` の `TOKENLESS_API_PATHS` の6つ（認証の API の `/api/auth/login`・`/api/auth/session/refresh`・`/api/auth/session/logout`、公開の API の `/api/appearance`・`/api/registration/verify`・`/api/registration/complete`）。問い合わせの部分を除いた完全な一致で判定し、401 でも更新と送り直しをしません。
- **機能の画面からの使い方**: `useDisplaySettings()`（`language`・`theme`・`fontSize`・`resolvedTheme`・`displayName`・`setPreview`・`clearPreview`・`applyUserPreferences`・`setLanguage`）、`saveBrowserDisplaySettings`・`saveBrowserLanguage`、`LANGUAGE_NAMES`、文言の鍵 `display.theme.*`・`display.fontSize.*`（`frontend/src/features/README.md`）。

### 既知の制約（ブランドカラーのコントラスト）

make-you-chic-ui の primary のボタンは、ブランドカラーの 500 の色（`--color-primary`）の背景に白い文字で描かれます。ブランドカラーが `green`・`orange` のとき、この組み合わせは WCAG AA の 4.5:1 に届きません（テーマの light・dark とも）。

| ブランドカラー | 背景の色 | 白い文字とのコントラスト比 | WCAG AA（4.5:1） |
|---|---|---|---|
| `blue` | `#2563eb` | 5.17:1 | 満たす |
| `purple` | `#9333ea` | 5.38:1 | 満たす |
| `green` | `#16a34a` | 3.30:1 | 満たさない |
| `orange` | `#ea580c` | 3.56:1 | 満たさない |

- 原因は make-you-chic-ui の primary の色で、このリポジトリからは直しません（`vendor/` は変更しない）。依頼者の判断で既知の制約として受け入れました。
- ログインの画面では、選択中の言語のボタンとログインのボタンが当たります（ほかの画面の primary のボタンも同じ）。
- 招待の管理の画面（U5）では、一覧の「招待する」と、招待の入力の Modal の「招待する」が当たります。060 の検査は、画面の状態ごとの名前の一覧（`INVITATION_KNOWN_VIOLATIONS`）でこれを既知の違反として扱います。
- 登録の完了の画面（U6）では、フォームの「登録を完了する」が当たります（使えないリンクの表示には primary のボタンがありません）。070 の検査は、画面の状態ごとの名前の一覧（`REGISTRATION_KNOWN_VIOLATIONS`）でこれを既知の違反として扱います。
- AA を満たしたいときは、`MASTERSMITH_APPEARANCE_BRAND_COLOR` に `blue` か `purple` を選びます。
- プリファレンスとパスワードの変更の画面（U7）では、「保存する」「変更する」が当たります。080 の検査は、画面の状態ごとの名前の一覧（`PREFERENCES_KNOWN_VIOLATIONS`）でこれを既知の違反として扱います。
- 050〜080 の検査はこの違反を既知の違反として扱います（「ビルドした WAR での画面の確認（E2E）」）。080 は、下の2つ（アバターと誤りの文字）も既知の違反として扱います。

make-you-chic-ui のほかの部品にも、コントラストが AA に届かないものがあります（Intent 260925-user-management の U7 のコード生成の 080 で見つかり、依頼者の判断で既知の制約として受け入れました。make-you-chic-ui が直ったら、080 の一覧から外し、この README を見直します）。

- **トップバーのアバター**: make-you-chic-ui の Avatar は、頭文字の文字を `--color-primary`、背景を `--color-primary-subtle` で描きます。例えば `blue` のライトで 4.36:1（ダークでは 2.55:1）です。氏名が2語で頭文字が2文字になると当たり（頭文字が1文字のときは axe が判定できない扱いにします）、アプリのどの画面のトップバーでも起きます。`purple` のライトでは当たりません。080 は、当たる組の一覧（`frontend/e2e/support/axe.ts` の `AVATAR_KNOWN_COMBOS`）で扱います。
- **ダークのテーマの項目の誤りの文字**: make-you-chic-ui の FormField の誤りの文字は、ダークのテーマで `--color-danger`（`#dc2626`）を背景 `#0b0f19` に描き、3.96:1 です（ライトでは当たりません）。ログインの画面・登録の完了の画面（U6）・U7 の2つの画面など、項目の誤りを出すすべての画面で起きます。U7 の選択のまとまりの誤り（`.preferences-choice-error`、サーバーが返したときだけ出ます）も同じ色です。080 は、ダークの組の誤りの状態の誤りの文字だけを既知の違反として扱います（`withFormFieldErrorKnownViolation`）。050〜070 は誤りを出した状態を検査していません。

### 契約との差

契約 C9（表示の設定の口）の形は `useDisplaySettings` の7つと `saveBrowserDisplaySettings` で、置き場は「`frontend/src/app/` の側で U4 が決める」でした。機能設計のとおり、`resolvedTheme`・`displayName`・`LANGUAGE_NAMES`・`saveBrowserLanguage`（ログインの画面の言語の切り替えのための U4 の中の口）を足し、置き場を `frontend/src/app/display-settings/` にしました（項目の追加は契約の決まりで安全な変更）。契約の文書は書き換えていません。

## 後の単位（U2・U3・U4）が使う差し込み口

U1 のファイルは書き換えずに、次の型を使います。

| 差し込み口 | 形 | 使う単位 |
|---|---|---|
| 追加のアクセスの決まり | `cherry.mastersmith.common.security.SecurityRuleContributor`（`Ordered`）の Bean。order は単位の番号ではなく機能の名前で 100 台ずつ割り当てる（`auth` は 100 台で本番 110、`access` は 200 台で本番 210、`invitation` は 300 台で本番 310、`appearance` は 400 台で本番 410。本番の決まりは x10、x00・x50 はテストの決まりが使う）。同じ order が2つあると起動が失敗する。足してよいのはアクセスの決まり、トークンの検証、認証の入口と拒否の処理、要求の検査の拒否の処理で、ヘッダー・セッション・CSRF の設定は変えない | U2、U3 |
| API の既定の扱い | `cherry.mastersmith.common.security.ApiDefaultAccess` の Bean（0個か1個。2個以上は起動の失敗）。無ければ `/api/**` は許可、`requireAuthentication()` が true ならログイン必須 | U3 |
| フィルターの段階のエラー応答 | `cherry.mastersmith.common.security.ErrorResponseWriter`。401・403 などを共通の ErrorResponse の形で書く | U2、U3 |
| 想定内のエラー | `cherry.mastersmith.common.error.domain.BusinessException` を起こす。問題の種類（`ProblemType`、日英の説明つき）は自分のパッケージの `ProblemTypeCatalog` の Bean に置く（code・slug の重複は起動の失敗） | U2、U3、U4 |
| 要求中のトレースID | `cherry.mastersmith.common.observability.TraceIdProvider`（無ければ空） | U4 |
| ログに秘密情報が出ないことのテストの補助 | `backend/src/test/java` の `cherry.mastersmith.common.testsupport`（`JsonLogRecords` など） | U2 以降 |
| 表示の設定の口（U4 が提供。契約 C9） | `frontend/src/app/display-settings/` の `useDisplaySettings`・`saveBrowserDisplaySettings`・`LANGUAGE_NAMES`、`frontend/src/app/login-handoff/` の `handOffToLogin`。機能の画面は make-you-chic-ui の `useTheme` と localStorage を直接触らない | U5・U6・U7（画面） |
| 画面の差し込み口 | `frontend/src/features/<featureId>/registration.ts` に `FeatureRegistration`（`frontend/src/app/registry/types.ts`）を `registration` という名前でエクスポートする。画面・サイドバーの項目・ユーザーメニューの項目・ログイン状態の提供元・文言（鍵は `<featureId>.` で始める）を登録できる。ユーザーメニューの項目は `action`（操作）か `path`（登録済みの画面の URL、読み込み直しなしで移る）のどちらか一方を持つ（`frontend/src/features/README.md`）。重複は画面の起動の失敗 | U2、U3、U7 |
| ログイン用レイアウト | `frontend/src/app/layout/LoginLayout.tsx`（role=LOGIN の画面が、入力欄とボタンを子として置く） | U2 |
| スキーマの変更 | 上の「スキーマの変更（Flyway）」の決まり | U2、U4 |
| 検証済みの利用者（U2 が提供） | `cherry.mastersmith.auth.domain.AuthenticatedUser`（`userId`・`email`・`admin`）。要求ごとに DB から読んだ値で、Spring Security の認証の結果の主体に置く | U3 |
| トークンの認証の失敗（U2 が提供） | `cherry.mastersmith.auth.domain.TokenAuthenticationException`（`AuthenticationException` の子）と `TokenFailureReason`（`TOKEN_MALFORMED`・`TOKEN_INVALID`・`TOKEN_EXPIRED`・`USER_NOT_FOUND`）。トークンが無い要求は U2 の検証を通らず、Spring Security の「認証が足りない」の例外が届く | U3 |
| 認証の出来事（U2 が提供） | `cherry.mastersmith.auth.domain.AuthenticationEvent` を U2 のトランザクションの中で知らせる。受け取りは `@TransactionalEventListener(phase = AFTER_COMMIT)` で確定の後に同じスレッドで行う | U4 |
| 時計（U2 が提供） | `java.time.Clock` の Bean（UTC、`cherry.mastersmith.auth.service.AuthClockConfig`）。ほかの単位は別に定義せずこれを使う | U3、U4 |
| API 呼び出しの共通部分（U2 が提供） | `frontend/src/shared/api-client/` の `apiFetch`・`apiRequest`（アクセストークンの付与、401 での更新と送り直し）と `{ status, code }` の形のエラー | U3（画面） |
| アクセス拒否の出来事（U3 が提供） | `cherry.mastersmith.access.domain.AdminAccessDeniedEvent`（`eventType`＝`ACCESS_DENIED`・`occurredAt`・`result`＝`FAILURE`・`failureReason`・`enteredEmail`（分かるときだけ）・`sourceIp`・`userAgent`・`requestPath`・`traceId`）を `ApplicationEventPublisher` で知らせる。受け取りは `@EventListener` で**要求と同じスレッド・応答を書く前**に行う（内部DBの更新を伴わないため、U2 の認証の出来事と異なり確定の後ではない）。受け取り側の失敗で 401／403 の応答は変わらない | U4 |
| 役割・権限の判定の置き場所（U3 が提供） | `cherry.mastersmith.access.web.AdminAuthorizationManager`。後続 Intent で役割・権限の判定を足すときはここに足す | 後続 Intent |

- 秘密情報を持つ型は、文字列化（`toString`）でその項目を伏せ字にしてください（メソッドの呼び出しの追跡が引数と戻り値を文字列にするため）。
- 画面での表示の制御はサーバー側の権限の確認の代わりになりません。データは API の側で守ります。

## ライセンス

Apache License 2.0（`LICENSE`）。

実行可能 WAR には、対象DB の JDBC ドライバーを、変更せずに独立した jar のまま同梱します（採用の理由は Intent 260923-dsl-schema-loader の U1 の技術選定の記録）。

| 部品 | ライセンス | ライセンスの文書 |
|---|---|---|
| MySQL Connector/J（`com.mysql:mysql-connector-j`） | GPL v2 ＋ Universal FOSS Exception 1.0（Apache License 2.0 で公開するプロジェクトへの同梱を認める） | jar の中の `LICENSE` |
| MariaDB Connector/J（`org.mariadb.jdbc:mariadb-java-client`） | LGPL 2.1 以降 | jar に含まれないため `backend/src/main/resources/META-INF/third-party-licenses/` に置き、WAR の `WEB-INF/classes/META-INF/third-party-licenses/` に入る |
| PostgreSQL JDBC（`org.postgresql:postgresql`） | BSD 2-Clause | jar の中の `META-INF/LICENSE` |
| Checker Framework qualifiers（`org.checkerframework:checker-qual`。PostgreSQL JDBC の依存） | MIT | jar の中の `META-INF/LICENSE.txt` |

DSL の読み込み（U2）で使う次の部品は Apache License 2.0 で、このプロジェクトと同じライセンスです。

| 部品 | ライセンス |
|---|---|
| SnakeYAML（`org.yaml:snakeyaml`） | Apache License 2.0 |
| networknt JSON Schema Validator（`com.networknt:json-schema-validator`） | Apache License 2.0 |
| ITU（`com.ethlo.time:itu`。networknt の依存） | Apache License 2.0 |

メールの描画と送信（Intent 260925-user-management の U1）で使う部品:

| 部品 | ライセンス | 範囲 |
|---|---|---|
| Spring Boot のメールの部品（`spring-boot-starter-mail`・`spring-boot-mail`・`spring-context-support`） | Apache License 2.0 | WAR に同梱 |
| Jakarta Mail API（`jakarta.mail:jakarta.mail-api`）・Angus Mail（`org.eclipse.angus:angus-mail`） | EPL 2.0・GPL2 w/ CPE・EDL 1.0 から選べる。このプロジェクトは **EDL 1.0**（BSD-3-Clause と同じ形の寛容なライセンス）を選ぶ | WAR に同梱（jar の中の `META-INF/LICENSE.md`） |
| java-mustache-processor（`cherry.mustache:cherry-mustache-core`。サブモジュール `vendor/java-mustache-processor`） | Apache License 2.0 | WAR に同梱 |
| SubEtha SMTP（`com.github.davidmoten:subethasmtp`）と依存の guava-mini・jsr305 | Apache License 2.0 | テストだけ（配布物に含めない） |
| Mailpit（イメージ `axllent/mailpit`） | MIT | 手元の確かめだけに compose の profile で起動する別のコンテナ（アプリに組み込まない） |

Jakarta Mail・Angus Mail は、Java で SMTP を送る標準の API とその実装で、Spring Boot のメールの自動設定が前提とし、実用になる代わりが無いため採用しました（部品は変えずにライブラリとして使う。既存の `jakarta.activation-api`・`angus-activation` も同じ Eclipse の部品）。

テストだけで使う Testcontainers（MIT）は配布物に含めません。

画面のフォントと検査の道具（Intent 260925-user-management の U4 ほか）:

| 部品 | ライセンス | 範囲 |
|---|---|---|
| Noto Sans JP（`@fontsource/noto-sans-jp`） | SIL Open Font License 1.1 | 画面の既定のフォント。`dist`・WAR に同梱（改変しない） |
| Noto Serif JP（`@fontsource/noto-serif-jp`） | SIL Open Font License 1.1 | 見た目の設定が `serif` のときの明朝体。`dist`・WAR に同梱（改変しない。`sans` のときは読まれない） |
| axe-core（`axe-core`） | Mozilla Public License 2.0 | 開発時の検査だけ（vitest-axe と `frontend/e2e/050-display-accessibility.e2e.ts`）。改変せず、画面の成果物に入らない |

フォントの採用の理由は Intent 260925-user-management の U4 の機能設計（9節）、axe-core は同じ U4 の NFR 要件の技術選定の記録にあります。
