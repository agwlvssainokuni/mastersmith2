# Infrastructure Specification — U5 利用者の管理の画面（u5-user-admin-ui）

U5 の基盤の設計です。U5 は画面（ui）の単位で、管理者が利用者の一覧・検索・5つの操作（管理者の印を付ける・外す、利用を止める・停止を解く、失敗回数を戻す）・氏名と言語の変更を行う画面（`frontend/src/features/useradmin/`）と、その E2E（110 の代表の流れ、120 の実際のブラウザの検査と画面の時間の測り）を持ちます。自分の API・内部DB の表・サーバーの状態・設定を持たず、`backend/` に手を入れません。画面のビルド結果（`dist`）として実行可能 WAR に同梱され、既存のコンテナで配信されます。配備先は開発者の PC 上のコンテナだけで、クラウドの基盤（IaC・検証環境・警報の通知の先）は作りません（`aidlc/spaces/default/memory/project.md` の Deployment）。

この文書は新しい設計ではなく、承認済みの NFR 要件・NFR 設計と既にある仕組みの記録です（`project.md` の Deployment「既に実装されている段は既にあるものの記録として書く」）。U5 で新しく決めたのは、質問 Q1 の答え（B5 までの E2E の報告の扱い）だけです。

- 答え: `infrastructure-design-questions.md`（決まっていること、要点 1〜8、Q1: A、Consolidated Summary Confirmation: Looks correct）
- 上流: `construction/u5-user-admin-ui/nfr-design/`（`performance-design.md` 4節〜6節、`security-design.md` 3節・7節〜9節・12節と「承認の場の決定」、`logical-components.md` 3節・5節・9節）、`construction/u5-user-admin-ui/nfr-requirements/`（`security-requirements.md`・`performance-requirements.md`・`tech-stack-decisions.md`）、`construction/u5-user-admin-ui/functional-design/functional-spec.md`（9節と「承認の場の決定」）・`frontend-components.md`（2.4・2.5）、`inception/domain-design/components.md`（UserAdminUi）、`inception/contract-design/contract-summary.md`（C3・C4）、`inception/delivery-planning/bolt-plan.md`（B5）、同じ段の `construction/u3-user-admin-api/infrastructure-design/`・`construction/u4-admin-forbidden-ui/infrastructure-design/`（どれも `aidlc/spaces/default/intents/260930-user-admin/` の下）
- 既にある仕組み（正とする、読むだけ）: `Dockerfile`・`compose.yaml`・`backend/src/main/resources/application.yaml`（CSP）・`backend/src/main/java/cherry/mastersmith/common/web/CacheControlFilter.java`・`frontend/playwright.config.ts`・`frontend/playwright-secret-check-reporter.ts`・`frontend/.npmrc`・`frontend/scripts/check-bundle-size.mjs`・`.gitignore`・`build.gradle.kts`（`verifyPrepare`・`e2eTest`）・`.github/workflows/ci.yml`

## 1. Deployment

| Facet | Choice | Rationale |
|---|---|---|
| 実行の形 | 画面のビルド結果（`dist`）を同梱した既存の実行可能 WAR を、既存の `Dockerfile`・`compose.yaml` の `app` で動かす。U5 のためのコンテナ・ポート・ボリューム・資源の上限・ヘルスチェックの変更は無い | U5 はブラウザの中で動く画面だけを足す（要点 1、`security-design.md` 9節）。成果物の形は `team.md` の Deployment（`dist` を同梱した WAR） |
| 設定 | 環境変数・`.env` の項目・`application.yaml` を足さない・変えない。新しい秘密を足さない | U5 は設定を読まない（`security-design.md` 9節） |
| 配信とキャッシュ | 既存の `CacheControlFilter` を変えない。U5 の画面は `registration.ts` の `lazy` で遅れて読む塊になり、`dist/assets/` のハッシュ付きの名前で `public, max-age=31536000, immutable`。`index.html` と画面の URL は `no-cache`、管理の API（`/api/**`）は `no-store` | 一覧の応答（メールアドレス・氏名）をブラウザのキャッシュに残さない（NFR3.1）。初回の読み込みに画面を含めない（NFR5.6、`performance-design.md` 4節） |
| CSP | `application.yaml` の `content-security-policy` に差分を作らない。120 のためにも緩めない。外部への通信・外部の資源・埋め込みのスクリプトとスタイルを足さず、要素の `style` 属性で差し込まない | NFR9.1、`security-design.md` 7節 |
| ブラウザの保存と URL | U5 のコードは localStorage・sessionStorage を読み書きしない。今のページと検索の文字は画面の状態だけに持ち、URL・履歴に載せない。トークンと管理者の印は既存どおりメモリだけ | NFR3.1、`security-design.md` 6.1 |
| 内部DB と移行 | 変更なし。Flyway の新しい版は足さず、バックアップの対象も無い | U5 はサーバーの状態を持たない。利用者の表と移行は U1・U3 の持ち物 |
| 環境 | 開発者の PC 上のコンテナだけ（`http://localhost:8080`）。E2E は `./gradlew e2eTest` が PC の上で `java -jar` で WAR を一時の内部DB（番号 18081）で起動する（既存の `frontend/playwright.config.ts`） | `project.md` の Deployment。E2E の WAR の起動の形は変えない（変えるのは reporter と trace だけ、3節） |
| IaC | 作らない | 配備先が決まるまでクラウドの基盤は作らない（`project.md` の Deployment） |
| 資源の大きさ | 資源の上限（CPU・メモリの既定）・JVM の設定・接続プールは変えない。初回の JavaScript は既存の目安（gzip で 500KB、超えたら警告だけ）のまま見張り、2つの時点の値を記録する（4節） | サーバーの負荷は一覧と操作の API の分で、U3 の NFR5.1・NFR5.3〜NFR5.6 が押さえる（NFR5.3）。U5 は決まった間隔の読み直しをしない（NFR5.4） |

## 2. Infrastructure Services

| Service | Role | Configuration | Notes |
|---|---|---|---|
| 静的なファイルの配信（Spring Boot の既存の配信と `CacheControlFilter`） | 画面の塊（JavaScript・CSS）の配信 | 変更なし。U5 の塊は `/assets/` のハッシュ付きの名前で `immutable` | 入口のファイルは増やさない（NFR5.6） |
| 利用者の管理の API（U3、契約 C3） | 一覧（`GET /api/admin/users`、`page`・`q`）・5つの操作（POST）・氏名と言語（PUT）。管理者だけ | U5 は画面を開いたとき・ページを変えたとき・検索・操作の後・「もう一度読み込む」のときだけ一覧を読む。決まった間隔の読み直し・画面の側の時間切れ・再試行・中断は置かない | p95 1 秒（同時 10 件）、排他の待ちの上限切れは約 3 秒で 409 `USER_ADMIN_BUSY`（U3 の NFR5.1・NFR5.3〜NFR5.6、U5 の NFR5.3〜NFR5.5）。サーバー側の認可と監査は U3 |
| ApiClient（`frontend/src/shared/api-client/`） | 要求の共通の口 | 変更なし。アクセストークンの付与・401 での更新・`Accept-Language` は既存のまま。`fieldErrors.ts` を `features/preferences/` から移すだけ（ふるまいは変えない） | 独自の時間切れを置かない（`performance-design.md` 3.1） |
| 403 の共通の扱いと自分の氏名・言語の反映（U4、契約 C4） | `useAdminForbidden`・`useApplyOwnProfile` | 呼ぶだけ | 403 で AppFrame が S6 に置き換える（NFR1.2） |
| ページ送りの共通の部品（U2、`src/shared/paging/paging.ts`） | `PAGE_SIZE`・`correctedPage` など | 読むだけ | U2 は B2 で移す |
| make-you-chic-ui（`vendor/make-you-chic-ui`） | `Table`・`Dropdown`・`Modal`・`Button`・`Badge`・`Alert`・`Toast` | 固定先を `077f5b4` から `3481488` 以降へ上げる。具体のコミットは B5 のコード生成の計画で決め、計画の承認の前に `Dropdown` の押せない項目と理由の文などの口（`frontend-components.md` 2.4・2.5）があることを確かめる。中身は変えない | NFR9.3、`project.md` の Forbidden・Mandated（5節・`cicd-pipeline.md` 3節） |

## 3. E2E の実行の基盤（手元だけ、`./gradlew verify` と CI の外）

U5 の E2E は手元の PC でだけ動き、サーバーの資源を足しません。設定の変更は `frontend/playwright.config.ts` の reporter と trace の2点だけです（要点 5）。

| 項目 | 今の形 | B5 の後 | 出典 |
|---|---|---|---|
| reporter | list・html（`playwright-report/`）・json（`test-results/e2e-results.json`）・報告の部品 | list・json・報告の部品（html を外す） | `security-design.md` 3.2（Q1 A） |
| trace の既定 | `retain-on-failure` | `off`。`E2E_TRACE` で `on`・`retain-on-failure` に切り替える。決まった3つの外の値は設定の読み込みで止める | `security-design.md` 3.2（Q2 A） |
| 110・120 の trace | — | `test.use({ trace: e2eTraceMode() })`（既定の実行では `off`） | `security-design.md` 3.2・12節の 3、承認の場の受け入れ 2 |
| json の報告 | 作る | 変えない。Build and Test が写す唯一の報告 | `security-design.md` 3.1 |
| 報告の部品 | json の報告の中の環境変数の3つの値を探す | 探す値に値のファイル（`test-results/` の下、権限 600、探し終えたら消す）と値の形を足し、探す先を json の報告（`attachments[].body` と `stdout`・`stderr` の `buffer` の base64 を復号）と `test-results/` の下のすべてのファイル（zip は `node:zlib` で展開）と前の `playwright-report/` に広げる。値は表示せず、種類と件数だけを出す | `security-design.md` 3.4・3.5、承認の場の R-01・R-06 |
| 効く範囲 | — | すべての E2E（010〜100・U4 の 130・110・120）の報告。E2E のファイルは変えない | `logical-components.md` 5節・9節 |
| ブラウザ | Chromium（`npx playwright install chromium`） | 変えない。`ignore-scripts=true` の影響を受けない（インストールのスクリプトではない） | NFR9.4 |
| 前提 | Mailpit（compose の profile `mail`）を起動しておく。`e2eTest` が始める前に届くかを確かめる | 変えない。110 は招待と Mailpit を使う | `build.gradle.kts` の `e2eTest` |

### 3.1 B5 までの E2E の報告の扱い（Q1: A、承認の場の決定 R-02）

`playwright.config.ts` の直しは承認済みの設計のとおり B5 で行い、先取りしません。それまでの `./gradlew e2eTest` では、html の報告と失敗のときの trace に、ログインの手伝いが入れる仮の資格情報（初期管理者のメールアドレスと仮のパスワード）が残りえます。いまの作業フォルダにも、前の実行の `frontend/playwright-report/` と `frontend/test-results/` が残っています。そこで依頼者の決定（承認の場の決定 R-02）により、次のとおり扱います。

対象の実行は、B1・B2・B4 の統合の前の `./gradlew e2eTest` と、B5 の中で `playwright.config.ts` を直す前の `./gradlew e2eTest`（固定先の更新と `.npmrc` のコミットの後など）のすべてです。

| 順 | すること |
|---|---|
| 0 | B1 の始め（最初の `e2eTest` より前）に、いま手元に残っている `frontend/playwright-report/` と `frontend/test-results/` を消し、消したことを B1 のコード生成の記録に書く |
| 1 | `./gradlew e2eTest` の結果（通ったファイル・130 の組ごとの結果など）を、json の報告（`frontend/test-results/e2e-results.json`）から、その Bolt のコード生成の記録に写す |
| 2 | 写した後に `frontend/playwright-report/` と `frontend/test-results/` を消す（json の報告も `test-results/` の下にあるため、必ず写した後に消す） |
| 3 | 消したことと、報告を共有していないことを、その Bolt のコード生成の記録に書く |

- これで、B5 の最初の実行の時点で前の html の報告は残っていない前提になります。前の報告で失敗することは、`security-design.md` 3.8 の (6)（わざと残した `playwright-report/`）で確かめます。
- 前回の書き手が確かめたいとした2点（B1 の実行の後の扱い、B5 の中で設定の直しより前に流す実行の扱い）は、この決定で決まりました。B5 の中で設定の直しをどの順に置くかは、コード生成の計画で決めます。
- B5 が B4 の後すぐに始まらないときも、この扱いのまま進めます。
- 理由: 承認済みの NFR 設計（B5 に置く）と U4 の確認済みの要約（先取りしない）を変えずに、報告が PC に残る間を Bolt ごとの統合の前までに縮められるためです。値は実行ごとの仮のもので、E2E の一時の内部DB の中でだけ使えます。

## 4. 配信物の大きさ

| 項目 | 扱い |
|---|---|
| 上限 | 新しく置かない。既存の目安（gzip で 500KB、超えたら警告だけで統合は止めない）のまま（NFR5.6） |
| 見張り | 既存の `frontendBundleSize`（`frontend/scripts/check-bundle-size.mjs`、`./gradlew verify` の段 9） |
| 記録 | コード生成（B5）で2つの時点の値を記録する。(1) make-you-chic-ui の固定先を上げる前、(2) U5 の変更の後。固定先の更新で増えた分と U5 で増えた分を分けて見る（`performance-design.md` 4節） |
| 依存 | U5 は実行時・開発時とも新しい依存を足さない。`dist` と WAR の増えは make-you-chic-ui の版の差と U5 の画面のコードの分だけ（NFR9.2） |

## 5. Shared Infrastructure

複数の単位・既存の画面と共有するものです（`logical-components.md` 9節）。新しいサーバーの資源はありません。

| Shared Resource | Owner Unit | Consumer Units | Access Boundary |
|---|---|---|---|
| 利用者の管理の API（契約 C3） | U3 | U5 | 管理者だけ。サーバー側の認可（401・403・成功）・自分自身の操作の拒否・最後の管理者の保護は U3 のサーバー側のテストで確かめ、画面で押せない形にすることを代わりにしない（NFR1.1、`project.md` の Mandated） |
| 403 の共通の扱いと自分の氏名・言語の反映（契約 C4） | U4 | U5 と既存の管理の画面 | 画面の中の関数・部品の呼び出し。U5 は書き換える口を作らない |
| E2E の WAR・一時の内部DB・初期管理者（`frontend/playwright.config.ts`） | 既存（E2E の設定） | 010〜100・U4 の 130・U5 の 110・120 | 全ファイルで共有し、`workers: 1` で番号の順に1本ずつ動く。110 は自分で作った利用者 U だけを変え、初期管理者の印・停止・ロックを変えない。120 は `/api/admin/` の下の GET 以外を本物へ通さない（NFR3.5、NFR9.8） |
| `frontend/playwright.config.ts` と報告の部品 `frontend/playwright-secret-check-reporter.ts` | 既存。B5 で U5 が手を入れる | すべての E2E | html を外し、trace の既定を `off` にする。json の報告は変えない（3節） |
| Mailpit（compose の profile `mail`、`127.0.0.1:1025`・`127.0.0.1:8025`） | 既存（前の Intent の U1） | 090・080・110 など招待を使う E2E と、`e2eTest` の始めの確かめ | 110 は既存の `createRegisteredUser` を変えずに使い、U への招待のメールが Mailpit に残る。宛先は実行ごとに重ならない（`u7-perf-<runTag>@example.com`） |
| make-you-chic-ui の固定先 | サブモジュール（B5 で U5 が上げる） | 部品を使うすべての画面 | 固定先の更新を承認を得た専用のコミットにし、既存の画面の部品のテストと E2E（010〜100・130）を通す（NFR9.3） |
| `frontend/.npmrc` | 既存 | `frontend/` の中のインストール | `ignore-scripts=true` を足す（`engine-strict=true` は残す）。`vendor/make-you-chic-ui` の中のインストール（`verifyPrepare` の `vendorInstall`）には効かない（NFR9.4） |

## 6. セキュリティと秘密情報（DevSecOps・Compliance の視点）

| 項目 | 扱い |
|---|---|
| 新しい秘密 | 足さない。秘密情報・鍵ファイル・設定・`.env` の項目を持たない |
| サーバーの判定 | 行の項目を押せない形にすることとメニューの出し分けは表示だけ。判定と監査はサーバー（U3・U1）が要求ごとに行う。U5 は `backend/` を変えない（NFR1.1、`project.md` の Mandated） |
| 画面に出す値 | 失敗の文言は `code` と状態コードから選び、サーバーの `detail`・`title` を出さない。応答の値は React の文字として描く。C3 の決めた項目だけを型で読む（NFR3.2・NFR3.3） |
| ブラウザのコンソールと保存 | メールアドレス・氏名・検索の文字をコンソール・保存・URL・履歴に出さない（NFR3.1）。管理の API の応答は `no-store` |
| E2E の資格情報 | 初期管理者のメールアドレスとパスワード・署名鍵は既存どおりプロセスの環境変数で渡し、`webServer.env` に置かない。110 が作る U の値は値のファイル（権限 600）に書き、報告の部品が探し終えたら消す（NFR3.4） |
| E2E の報告 | B5 の後は html を作らず、trace の既定は `off`。B5 で設定を直すまでは 3.1 のとおり、B1 の始めに今ある報告を消し、各実行の後に結果を写してから消す。`frontend/test-results/`・`frontend/playwright-report/` は `.gitignore` の対象のままで、コミット・共有しない。E2E は CI の外のため、CI の成果物に報告は載らない |
| 120 の書き換え | `/api/admin/` の下の GET 以外を本物へ通さず、打ち切って数える。口が受けた件数が 1 以上であることを毎回確かめる（NFR3.5、`security-design.md` 4節） |
| テストのデータ | 見本のメールアドレスは `example.com` の下の値だけ、氏名は架空の値（2語を含む）。実在しそうな氏名・宛先を置かない（公開のリポジトリのため、`team.md` の Testing Posture） |
| 依存とサプライチェーン | 新しい依存を足さない。make-you-chic-ui の lockfile は OSV-Scanner の対象のまま。`frontend/` のインストールでパッケージのスクリプトを動かさない（NFR9.2〜NFR9.4、`team.md` の Code Style） |
| 監査 | U5 は監査の記録を書かない。5つの操作と氏名・言語の変更の監査は U3 の持ち物（契約 C6、`project.md` の Mandated） |

## 7. 上流との差

承認済みの文書は書き換えず、差をここに記録します（`project.md` の Way of Working）。

| 対象 | 承認済みの形 | この段の設計 | 理由 |
|---|---|---|---|
| B5 までの E2E の報告 | NFR 設計は直しを B5 に置く。U4 の基盤の設計は「先取りしない」 | 先取りしない。B2・B4 では json の報告から結果を写した後に `frontend/playwright-report/` と `frontend/test-results/` を消し、消したことと共有していないことを記録する（3.1） | Q1: A。同じ段の U4 のレビューの R-03 への答え。食い違いではなく、B2・B4 の手順の追加 |
| B1 と B5 の中の E2E の報告 | 上流に記述が無い（Q1 A は B2・B4 だけを挙げた） | B1 の始めに今ある報告を消し、B1 と、B5 の中で設定を直す前の実行にも 3.1 の扱いを当てる | 承認の場の決定 R-02（`cicd-pipeline.md` の「承認の場の決定」の節）。手順の追加で、食い違いではない |
| 配信のキャッシュの扱い | U5 の NFR 設計に記述は無い | 既存の `CacheControlFilter` の `/assets/**`・`/api/**` の扱いがそのまま当たることを明記した | 既存の仕組みで埋めた追加。食い違いではない |
| 承認済みの設計の文書 | — | 食い違う点は無い。NFR 設計の承認の場の申し送りはコード生成の計画で扱う（`cicd-pipeline.md` 6節） | NFR Design の承認の場の決定のとおり |
