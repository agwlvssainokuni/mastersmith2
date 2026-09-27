# CI/CD Pipeline — U7 プリファレンスとパスワードの変更の画面（u7-preferences-ui）

U7 の検査の流れを示します。示す項目は次のとおりです。配備先は開発者の PC 上のコンテナだけで、クラウドの基盤は作りません（`aidlc/spaces/default/memory/project.md` の Deployment）。

- 1コマンドの検査と CI
- 実際のブラウザの検査と画面の時間の測り（`./gradlew e2eTest`）
- 検査と測りの資格情報
- 配備と戻し

参照するもの:

- 答え: `infrastructure-design-questions.md`（要点 10〜21、質問なし、Looks correct）
- 上流: `construction/u7-preferences-ui/nfr-design/`
  - `performance-design.md` の4節・5節
  - `security-design.md` の6節・7節
  - `logical-components.md` の5節・7節
- 同じ段の先行する成果物（どれも `aidlc/spaces/default/intents/260925-user-management/` の下）
  - `construction/u1-mail/infrastructure-design/cicd-pipeline.md`（6節・7節）
  - `construction/u3-invitation/infrastructure-design/cicd-pipeline.md`（5節）
  - `construction/u4-display-foundation/infrastructure-design/cicd-pipeline.md`（4節・5節）
  - `construction/u8-instance-appearance/infrastructure-design/`
- 既にある仕組み（正とする）
  - `build.gradle.kts`（`verify`・`frontendBundleSize`・`osvScan`・`e2eTest`）
  - `.github/workflows/ci.yml`
  - `frontend/playwright.config.ts`
  - `frontend/vitest.config.ts`
  - `.gitignore`
  - README の E2E の節

## 1. CI（GitHub Actions）

`.github/workflows/ci.yml` は変えません。

| 項目 | 今の形 | U7 での扱い |
|---|---|---|
| きっかけ | `develop` へのプッシュ・`v*` のタグ・手動 | 変えない |
| サブモジュール | `submodules: true` で固定先のコミットを取得 | U7 は固定先を変えない（make-you-chic-ui の新しい版は B4 の更新の後に使う） |
| 実行 | `./gradlew verify` | 変えない。`./gradlew e2eTest`（U7 の検査と測りを含む）は CI に入れない（`team.md` の Testing Posture） |
| 秘密 | CI は秘密を使わない | U7 は秘密を足さない |

## 2. 1コマンドの検査（`./gradlew verify`）の段と関門

段は増やさず、ビルドの設定も変えません。既存の段で U7 の変更を確かめ、どれか1つでも失敗したら全体を失敗とします（既存のとおり）。

| 段 | U7 で動くもの | 関門（失敗の条件） |
|---|---|---|
| 0 準備 | `frontendInstall`（`npm ci`）。依存の追加は無い | 入れられない |
| 1〜3 フォーマット・リンタ・ライセンスヘッダー | `frontend/src/features/preferences/`、骨組みの変更、`frontend/e2e/` の U7 の新しいファイル | 既存の基準（Prettier・oxlint・ESLint・Stylelint・ライセンスヘッダー、`react/no-danger` を含む） |
| 4 ビルド | 型検査と画面のビルド（`React.lazy` の塊を含む） | 型の誤り・ビルドの失敗 |
| 5 単体テスト | 以下の3種（NFR7.2・NFR9.6〜NFR9.8）。1. 画面部品のテスト（Vitest ＋ Testing Library ＋ user-event ＋ vitest-axe。画面ごとに1件の axe）。2. `fieldErrors.ts` の性質ベースのテスト（fast-check、失敗時の `seed`・`path` を出力に残す）。3. 骨組みのテスト（`validateRegistrations.test.ts`・`ShellLayout.test.tsx`・`navigationItems.test.ts`）に足す確かめ | 1件でも失敗。既存の画面のテストも変えずに通す |
| 7 カバレッジ | `@vitest/coverage-v8` の `thresholds`（行 80%・分岐 70%） | 下回る。U7 のために計測の除外を増やさない。`frontend/e2e/` は既存どおり計測の対象外（NFR9.5） |
| 8 安全の検査 | `osvScan`・Gitleaks（既存のまま） | 既存の基準。U7 は依存を足さないため新しい対象は無い（NFR9.10） |
| 9 成果物 | `frontendBundleSize`（初回の JavaScript、警告だけ）と `bootWar` | WAR が作れない。大きさは警告だけで、U7 の前後の値をコード生成で記録する（NFR6.6） |

## 3. 実際のブラウザの検査と画面の時間の測り（`./gradlew e2eTest`、verify と CI の外）

### 3.1 置き場と中身

| 項目 | 設計 | 出典 |
|---|---|---|
| ファイル | `frontend/e2e/` の新しいファイル1本。番号は U4 の `050-display-accessibility.e2e.ts` の後（例 `05x-preferences-accessibility.e2e.ts`）。最終の番号と、U5・U6 の検査・E2E-1 との並びは、B5 のコード生成の計画でそろえる | `logical-components.md` の 5.1、承認の場の U6 R-02・U7 R-02 |
| 検査 | 2画面 × 2状態（最初の状態・画面の確かめの誤りの状態）× 20 組。組は (a) 6組・(b) 8組・(c) 幅 375px の6組。合否は2つ: axe の違反 0 件（WCAG 2.0・2.1 の A・AA のタグ、`page.evaluate` で評価）と、横のはみ出しが無いこと。CSP の違反 0 件も確かめる | NFR7.1・NFR7.3・NFR7.4、`logical-components.md` の 5.2〜5.4 |
| 組の当て方 | 組ごとに新しいコンテキストで初期管理者としてログインする。`GET /api/me/preferences` と `/api/appearance` は、そのコンテキストの中だけで差し替える（`page.route`）。組の値は画面の D2 の本物の道で当たる。PUT・POST は送らず、送られていないことを要求の記録で確かめる | `logical-components.md` の 5.3・5.4、`security-design.md` の6節 |
| 測り | 3つの場面（開く・保存・パスワードの変更）を、それぞれ5回ずつ測る。本物の応答で測り、差し替えない。時間で失敗させない | NFR6.1〜NFR6.3、`performance-design.md` の4節 |
| 実行 | `./gradlew e2eTest` の中。`workers: 1` のまま番号の順に動く。既存の 010〜040 と U4 の 050 は変えない | NFR9.8 |
| 本数 | 流れの E2E ではないため、`team.md` の「代表の流れを1本まで」に数えない | NFR9.9 |
| 共用の手伝い | U4 が B4 で作る `frontend/e2e/support/` を使い、U7 のための別の手伝いを作らない。B5 で足す関数（ログイン・差し替えの答えの組み立て・招待から利用者を作る関数）は、U5・U6 とそろえて B5 のコード生成の計画で決める | 要点 5・11 |

### 3.2 E2E の WAR の設定と結果の読み取り

| 項目 | 設計 | 出典 |
|---|---|---|
| `frontend/playwright.config.ts` | U7 は項目を足さない。`webServer.env` は先の単位が足したものをそのまま使う。1. U1: SMTP の接続先 `localhost:1025` と差出人。2. U3: `MASTERSMITH_WEB_BASE_URL: http://localhost:${port}`。見た目の設定は入れない（U8、既定の blue・sans） | 要点 12 |
| 結果の読み取り | U4 が足す reporter の `json`（`frontend/test-results/` の下の1つのファイル）に、U7 の注記と添付も入る。Build and Test はそこから写す。写す値は、画面・状態・組ごとの成否、違反の件数と規則の名前、`incomplete`、場面ごとの5回の時間 | U4 の Q2: A、`logical-components.md` の 5.5 |

### 3.3 測りに使う利用者と Mailpit

測りのテストが、自分で作った利用者で測ります（`performance-design.md` の 4.2）。初期管理者の設定とパスワードは変えません。

1. 初期管理者として、要求の口（Playwright の `request`）からログインの API を呼ぶ。
2. 招待の API で1件招待する。宛先は実行ごとに重ならない `example.com` の値（例 `u7-perf-<実行の印>@example.com`）にする。
3. U6 の助けの部品で、Mailpit の API（`127.0.0.1:8025`）からその宛先のメールを探す。本文の `/register#token=` の値を取り出す（取り出し方は U3 の基盤の設計の5節）。
4. 登録の完了の API で利用者を作る。パスワードは実行ごとに作る値にする（3節）。

- **U7 は招待のリンクをブラウザで開きません**。そのため、閲覧の履歴にトークンを含むアドレスが残る危険（NFR 設計の承認の場の A9、U6 の SD-D2）には触れません。A9 の B5 での確かめは、U6 の E2E-1 の持ち物です（要点 13）。
- 前提の確かめは U1 の決定のとおりです。`e2eTest` は始める前に Mailpit の API に届くかを確かめ、届かなければ起動の手順を示して失敗します。`webServer.env` に SMTP の設定が入るため、招待を使える設定（`invitationEnabled`）も真になります。
- 上のため、NFR 設計の 4.2 の「招待を使えない・受け手の手段が無いときは `test.skip` にして `Unverified`」の道は、通常の実行では通りません。念のための道として残し、B5 で飛ばす判定の前提を確かめます（承認の場の U5 R-02 と同じ扱い、要点 14）。
- 作った利用者と招待は、同じ WAR と内部DB を使う後のファイルに残ります。後のファイルは、利用者・招待の件数と順に頼りません。

### 3.4 実行の時点と前提

| 項目 | 設計 |
|---|---|
| 実行の時点 | 画面・認証に関わる変更を統合する前（B5）と、リリースの前に、手元で実行する（`team.md` の Testing Posture）。骨組みの変更の影響は、既存の 010〜040 を変えずに通すことで確かめる（NFR9.8） |
| 前提 | 実行の前に `docker compose --profile mail up -d mailpit` を行い、`npx playwright install chromium` を済ませておく。長い実行は `caffeinate -i` を付ける |
| Mailpit の中身 | E2E はメールを消さず、Mailpit の API に書き込まない。測りの招待のメールのトークンは、実行ごとの一時の内部DB の WAR にだけ効き、`e2eTest` が終わって WAR が止まると使えなくなる。開発と同じ受け手に入るため、開発者が `e2eTest` の後に Mailpit を止めて消す（`docker compose --profile mail stop mailpit` と `docker compose --profile mail rm -f mailpit`。ボリュームなし）。この手順を README に書く（U6 の段の決定、U5 も同じ） |
| README | E2E の節の表に U7 のファイルの行を足す。書く内容は3つ: 検査の中身、時間は記録だけで失敗させないこと、Mailpit の起動が要ること |

## 4. 検査と測りの資格情報と結果のファイル

| 項目 | 扱い | 出典 |
|---|---|---|
| 初期管理者 | 既存どおり、パスワードと署名鍵を実行ごとに `randomBytes` で作って環境変数で渡す。U7 はログインするだけで、設定もパスワードも変えない | `frontend/playwright.config.ts`、要点 15 |
| 作った利用者のパスワード | テストの中で `randomBytes` から2つ作る。12 コードポイント以上・72 バイト以下で、変更の前と後で交互に使う。リポジトリ・`.env` に置かない | `performance-design.md` の 4.2 |
| 秘密の値の置き場 | 次の値は、そのテストの変数だけに持つ。パスワード・アクセストークン・招待のトークンと URL・作った宛先。注記・添付・`console`・テストの名前・結果の JSON に出さない。結果の JSON に秘密が入らないことはコード生成で確かめる | `security-design.md` の6節、要点 15 |
| 差し替えの答え | 氏名は固定のテストの値（例 `検査 太郎`）にし、実在の個人に関する値を使わない | 要点 16 |
| 結果のファイル | `frontend/test-results/`・`frontend/playwright-report/` は既存どおり管理外。失敗のときのトレースには、入れたパスワード・トークン・作った宛先が含まれうるため、コミット・共有しない。新しい秘密の置き場は作らない | 要点 17 |
| メールの送り先 | E2E の WAR に渡した手元の受け手（Mailpit）だけ。実在の宛先・外部の SMTP へ送らない | `project.md` の Forbidden |

## 5. 統合と配備

1. B5 は、サブモジュールの固定先の更新を含まない。短命のブランチで作業し、`develop` へ squash で統合する。
2. 統合の前に `./gradlew verify` を通し、手元で `./gradlew e2eTest` を流す（`team.md` の Way of Working・Testing Posture）。`git push` は依頼者が行う。
3. WAR を作り、イメージを作り直して `docker compose up -d` を行う。
4. ヘルスチェックと既存のスモークテストで確かめる。

配備の後の確かめで保存やパスワードの変更を行うかは、配備の段で決めます。行うときに先に依頼者に伝えることも、配備の段で決めます。これらの操作は配備した内部DB の設定と監査ログに残り、パスワードが要る操作は依頼者が行う決まりのためです（`project.md` の Corrections）。

画面の時間は配備したアプリでは測りません。環境の昇格（検証環境・本番環境）は、配備先が決まるまで作りません。

## 6. 戻し

| 項目 | 扱い |
|---|---|
| 前の版への戻し | イメージだけを直前の版に戻す既存の決まりのまま |
| サーバーの状態 | U7 は変えないため、戻しで消すものは無い。利用者の設定の表（V7）の後方互換は U2 の持ち物 |
| ブラウザに残った値 | U7 は保存を持たない。U4 の表示の設定の鍵の扱いは U4 の基盤の設計のとおり |

## 7. 上流との差

承認済みの文書は書き換えません（`aidlc/spaces/default/memory/project.md` の Way of Working）。

| ID | 対象 | 承認済みの形 | この段の設計 | 理由 |
|---|---|---|---|---|
| C-D1 | 測りを飛ばす道（`performance-design.md` の 4.2・4.4） | 招待を使えない・受け手の手段が無いときは `test.skip` にして `Unverified` | 道は残すが、U1 の前提の確かめと `webServer.env` の SMTP の設定により通常の実行では通らないことを明記した（3.3） | 先に承認された U1・U3 の決定の影響を書いた追加。食い違いではない |
| C-D2 | A9（閲覧の履歴の残る危険） | 承認の場で B5 で実際の動きを確かめて記録する | U7 はリンクをブラウザで開かないため関わらず、確かめは U6 の E2E-1 の持ち物とした（3.3） | 持ち主を明記した追加 |
| C-D3 | Mailpit の中身の扱い | U1・U3 の決まり（見終えたら止めて消す） | E2E はメールを消さず Mailpit の API に書き込まない。開発者が `e2eTest` の後に止めて消す手順を README に書く（3.4） | U6 の段の決定（U5 も同じ）にそろえた |
| C-D4 | 配備の後の確かめ | 上流に記述なし | 保存・パスワードの変更を行うかは配備の段で決める（5節） | 既存の決まり（`project.md` の Corrections）で埋めた追加 |
| C-D5 | 検査のファイルの番号 | `05x-` の例だけ | 最終の番号は B5 のコード生成の計画で U5・U6 とそろえる（3.1） | 承認の場の U6 R-02・U7 R-02 のとおり |
