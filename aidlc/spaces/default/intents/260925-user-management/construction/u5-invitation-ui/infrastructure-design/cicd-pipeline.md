# CI/CD Pipeline — U5 招待の管理の画面（u5-invitation-ui）

U5 の検査の流れ（1コマンドの検査と CI）、依存の扱い、実際のブラウザの検査と画面の時間の測定（E2E）、配備と戻しを示します。配備先は開発者の PC 上のコンテナだけで、クラウドの基盤は作りません（`aidlc/spaces/default/memory/project.md` の Deployment）。

- 答え: `infrastructure-design-questions.md`（要点 11〜19、Q1: A）
- 上流: `construction/u5-invitation-ui/nfr-design/`（`performance-design.md` の4節・5節、`security-design.md` の6節・7節、`logical-components.md` の5節・7節）、同じ段の `construction/u1-mail/infrastructure-design/cicd-pipeline.md`（6節・7節）・`construction/u3-invitation/infrastructure-design/cicd-pipeline.md`（5節・6節）・`construction/u4-display-foundation/infrastructure-design/cicd-pipeline.md`（4節・5節）（どれも `aidlc/spaces/default/intents/260925-user-management/` の下）
- 既にある仕組み（正とする）: `build.gradle.kts`（`verify`・`frontendBundleSize`・`osvScan`・`e2eTest`）・`.github/workflows/ci.yml`・`frontend/playwright.config.ts`・`frontend/tsconfig.json`・`frontend/vitest.config.ts`・`frontend/package-lock.json`・`.gitignore`・README の E2E の節

## 1. CI（GitHub Actions）

`.github/workflows/ci.yml` は変えません。

| 項目 | 今の形 | U5 での扱い |
|---|---|---|
| きっかけ | `develop` へのプッシュ・`v*` のタグ・手動 | 変えない |
| 実行 | `./gradlew verify` | 変えない。E2E（`./gradlew e2eTest`）は CI に入れない（`team.md` の Testing Posture） |
| 秘密 | CI は秘密を使わない | U5 は秘密を足さない |

## 2. 1コマンドの検査（`./gradlew verify`）の段と関門

段は増やさず、既存の段で U5 の変更を確かめます。どれか1つでも失敗したら全体を失敗とします（既存のとおり）。ビルドの設定の変更はありません。

| 段 | U5 で動くもの | 関門（失敗の条件） |
|---|---|---|
| 0 準備 | `frontendInstall`（`npm ci`）。make-you-chic-ui は B4 の更新の後の固定先 | 入れられない |
| 1〜3 フォーマット・リンタ・ライセンスヘッダー | U5 の TypeScript・CSS と `frontend/e2e/` の新しい検査のファイル・手伝い | 既存の基準（Prettier・oxlint・ESLint・Stylelint・ライセンスヘッダー。`react/no-danger` を含む） |
| 4 ビルド | 型検査（`npm run typecheck`、`frontend/tsconfig.json` の `include` に `e2e` がある）と画面のビルド | 型の誤り。一覧の見本と `frontend/src/features/invitation/api/types.ts` の型の食い違い（契約 C5 の項目）もここで止まる（要点 11） |
| 5 単体テスト | 画面部品のテスト（Vitest ＋ Testing Library ＋ user-event ＋ vitest-axe）、性質ベースのテスト（fast-check、`paging.ts`・`inviteInput.ts`、失敗時の乱数の種を残す）、`invitationApi.ts` のテスト、移した `formatDateTime` のテストと DSL の画面の既存のテスト | 1件でも失敗 |
| 7 カバレッジ | フロントエンドの `@vitest/coverage-v8` の `thresholds`（行 80%・分岐 70%） | 下回る。U5 のために計測の除外を増やさない。`frontend/e2e/` は既存どおり計測の対象外（NFR9.6） |
| 8 安全の検査 | `osvScan`・Gitleaks | 既存の基準。U5 は依存を足さない（3節） |
| 9 成果物 | `frontendBundleSize`（初回の JavaScript、警告だけ）と `bootWar` | WAR が作れない（大きさは警告だけ、NFR6.6） |

## 3. 依存と make-you-chic-ui

| 項目 | 扱い |
|---|---|
| 新しい依存 | 実行時・開発時とも足さない。コード生成で `frontend/package.json`・`frontend/package-lock.json` に U5 の変更による差分が無いことを確かめて記録する（NFR9.4） |
| axe-core | U4 が B4 で devDependencies に明示で足したものを使う |
| make-you-chic-ui | B4 の固定先の更新（`edb1f94` → `735ef04`）の後の版を使う。B5 のコード生成の計画で、更新が済んでいることを前提として確かめる。中身は変えない（NFR9.5、`project.md` の Forbidden） |
| 統合の形 | B5 は固定先の更新を含まないため、`develop` へ squash で統合する。統合の前に `./gradlew verify` を通す（`team.md` の Way of Working） |

## 4. 実際のブラウザの検査と測定（`./gradlew e2eTest`、verify と CI の外）

### 4.1 置き場と中身

| 項目 | 作り |
|---|---|
| ファイル | `frontend/e2e/` の `050-` の後の番号の新しいファイル（番号と並びは B5 のコード生成の計画で U6・U7 とそろえる。U6 R-02・U7 R-02）。既存の 010〜040 と U4 の 050 は変えない |
| アクセシビリティの検査 | 20 組（(a) テーマ × 文字の大きさ 6・(b) ブランドカラー × テーマ 8・(c) 幅 375px の6組）。一覧の API の答えだけを `page.route` の見本で差し替え、一覧（行あり）・警告（(a)・(b) だけ）・招待の入力の Modal・取り消しの確かめの Modal を出す。Modal は開くだけで招待・取り消しの要求は送らない。axe は U4 の手伝いで `page.evaluate`、WCAG 2.0・2.1 の A・AA のタグ。横のはみ出しと CSP の違反も記録する（NFR7.3・NFR7.4、`logical-components.md` の5節） |
| ログイン | 組ごとに新しいコンテキストを作り、ログインの画面のフォームから管理者（`frontend/playwright.config.ts` の `adminEmail`・`adminPassword`）でログインしてから、サイドバーの「利用者の招待」で開く。1回の実行で約 25 回 |
| 画面の時間の測定 | 同じファイルに1件。5回、一覧の1ページ目と次のページの時間を測り、注記と添付で残す。一覧の API と `/api/appearance` は差し替えない。時間で失敗させない（NFR6.1・NFR6.2、`performance-design.md` の4節） |
| 本数 | 流れの E2E ではないため、`team.md` の「機能の Intent ごとに代表の流れを1本まで」に数えない。U5 は流れの E2E を足さず、代表の流れ（招待から登録の完了まで）は U6 が B5 で書く（NFR9.9） |

### 4.2 測定の招待の用意

1. 測定のテストの要求の口（Playwright の `request`）で、管理者としてログインの API（`POST /api/auth/login`）を呼び、アクセストークンを得る。ログインの API には Origin の確かめが当たらない（`AuthController`・`OriginVerifier` の確かめはトークンの更新とログアウトだけ）。
2. 一覧の API を読み、`invitationEnabled` が偽なら測定を行わずに `test.skip` とし、理由を注記に残す。E2E の WAR には U1 の SMTP の設定と U3 のベース URL が渡るため真になる見込みで、この判定は念のための備え。意図の記述（U5 R-01）と前提の確かめ（U5 R-02）は B5 のコード生成の計画で拾う。飛ばしたときは Build and Test で `Unverified` とし、持ち主を明記して引き継ぐ。
3. 招待の API（`POST /api/admin/invitations`）で 21 件を置く。メールアドレスは実行ごとに重ならない `example.com` の下の値、言語は `ja`。各応答が 201 であることを確かめる（`sendResult` は SENT・FAILED のどちらでもよい）。
4. 招待のたびに本物の送信の道を通り、E2E の WAR に渡した Mailpit（`127.0.0.1:1025`）だけに送られる。実在の宛先・外部の SMTP へは送らない（`project.md` の Forbidden）。

### 4.3 Mailpit に届く 21 通（Q1: A）

| 項目 | 扱い |
|---|---|
| 消すか | 消さない。U5 の検査は Mailpit の API に書き込まない |
| 片付け | 見終えたら既存の手順（`docker compose stop mailpit`、消すときは `docker compose rm -f mailpit`）。Mailpit はボリュームを持たないため、コンテナを消すと受けたメールも消える |
| 取り違え | U6 の E2E-1 などは宛先で探すため、測定のメールが残っていても取り違えない |
| 届いたリンク | その実行の E2E の WAR（一時の内部DB、番号 18081）にしか効かず、WAR が止まると使えない |
| README | E2E の節に「測定のテストが1回の実行で 21 通を送ること」を書く |

### 4.4 実行の時点と前提

- 画面・認証に関わる変更を統合する前（B5）とリリースの前に、手元で `./gradlew e2eTest` を実行する（`team.md` の Testing Posture）。U5 の変更の後に、既存の 010〜040・U4 の 050・U6 の E2E-1 が通ることを記録する（NFR9.9）。
- `e2eTest` は始める前に Mailpit の API（`127.0.0.1:8025`）に届くかを確かめ、届かなければ失敗させる（U1 の決定）。実行の前に `docker compose --profile mail up -d mailpit` が要る。
- 測定で置いた 21 件の招待とログインの監査の記録は、同じ WAR と一時の内部DB を使う後のファイルに残る。後のファイルは一覧の件数・順・監査の件数に頼らない作りにする（要点 15）。
- 長い実行は `caffeinate -i` を付ける（`project.md` の Testing Posture）。
- README の E2E の節の表に、U5 の検査のファイルの行（検査の中身、時間は記録だけで失敗させないこと、招待を 21 件置き Mailpit に 21 通届くこと）を足す。

## 5. 検査の結果のファイルと秘密

| 項目 | 扱い |
|---|---|
| 置き場 | `frontend/test-results/`（`json` の reporter の結果を含む）・`frontend/playwright-report/` は既存どおり管理外。`.gitignore` は変えない |
| 含まれうる秘密 | 失敗のときのトレースに、実行ごとの仮の初期管理者のパスワード、要求の口で得たアクセストークン、見本のメールアドレスが含まれうる。コミット・共有しない。新しい秘密の置き場は作らない |
| 注記と添付 | アクセストークン・パスワードを出さない。組ごとの成否・違反の件数と規則の名前・`incomplete`・時間の値だけ |
| Build and Test への写し | 上の値だけを写す（U4 の決定と同じ） |

## 6. 配備

既存の流れのままです。

1. `./gradlew verify` と手元の `./gradlew e2eTest` を通して `develop` へ squash で統合する。
2. WAR を作り、イメージを作り直して `docker compose up -d`。
3. ヘルスチェックとスモークテストで確かめる。U5 のスモークテストは、`http://localhost:8080/` に管理者でログインし、サイドバーの「利用者の招待」から一覧が開けること。招待の操作と Mailpit での確かめは U3 のスモークテストで行い、利用者と監査が残ることを先に依頼者に伝える（`project.md` の Corrections）。

画面の時間は配備したアプリでは測りません（`monitoring-design.md` の3節）。環境の昇格（検証環境・本番環境）は、配備先が決まるまで作りません。

## 7. 戻し

| 項目 | 扱い |
|---|---|
| 前の版への戻し | イメージだけを直前の版に戻す既存の決まりのまま |
| サーバーの状態 | U5 は変えないため、戻しで消すものは無い。招待の表（V8）の後方互換は U3 の持ち主 |
| ブラウザに残った値 | U5 はブラウザの保存を使わないため、残る値は無い |

## 8. 上流との差

承認済みの文書は書き換えず、差をここに記録します（`project.md` の Way of Working）。

| 対象 | 承認済みの形 | この段の設計 | 理由 |
|---|---|---|---|
| `e2eTest` の前提 | U5 の NFR 設計に Mailpit の起動の記述は無い | 実行の前に Mailpit の起動が要る | U1 の同じ段の決定の影響。既存の決まりで埋めた追加 |
| Mailpit の 21 通 | 上流に記述が無い | 消さず、README に書く（Q1: A） | 依頼者の答え |
| 結果のファイルの扱い | NFR 設計は「トレースと報告は手元だけ」 | 含まれうる秘密（アクセストークンを含む）を明記し、コミット・共有しない | `project.md` の Forbidden で埋めた追加 |
| スモークテスト | 上流に記述が無い | 一覧が開けることを U5 の分とし、招待の操作は U3 の分に任せる | `team.md` の Deployment（配備のたびのスモークテスト）で埋めた追加 |
| 承認済みの設計の文書 | — | 食い違う点は無い。U5 R-01・R-02 はコード生成の計画で拾う（4.2） | NFR Design の承認の場の決定のとおり |
