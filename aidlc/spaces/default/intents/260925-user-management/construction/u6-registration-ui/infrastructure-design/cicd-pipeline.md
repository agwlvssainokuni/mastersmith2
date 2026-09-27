# CI/CD Pipeline — U6 登録の完了の画面（u6-registration-ui）

U6 の検査の流れ（CI と1コマンドの検査）、代表の流れの E2E（E2E-1）と Mailpit からの招待のリンクの取り出し、統合・配備・戻しを示します。既存の仕組み（`.github/workflows/ci.yml`・`build.gradle.kts` の `verify`・`frontendBundleSize`・`e2eTest`、`frontend/playwright.config.ts`、`frontend/e2e/010`〜`040`、`.gitignore`、README の E2E の節）を正とし、この単位で足す点だけを書きます。配備先は開発者の PC 上のコンテナだけで、クラウドの基盤は作りません（`aidlc/spaces/default/memory/project.md` の Deployment）。

- 答え: `infrastructure-design-questions.md`（要点 24 件、Q1: C、Q2: A、まとめの確認は Looks correct）
- この段で先に承認された決定: U1（Mailpit の SMTP・API は `127.0.0.1` だけに公開、`webServer.env` で SMTP の設定、`e2eTest` の前提の確かめ）、U3（`MASTERSMITH_WEB_BASE_URL: http://localhost:${port}`、本文に `ベース URL ＋ /register#token= ＋ トークン`、取り出しの部品と流れは U6 と B5）、U4（`frontend/e2e/support/`、`json` の reporter を `frontend/test-results/` に書く）、U8（E2E の WAR は見た目の設定なし）

## 1. CI（GitHub Actions）

| 項目 | 内容 | 変更 |
|---|---|---|
| きっかけ | `develop` への統合の後のプッシュ（既存の `.github/workflows/ci.yml`） | 無し |
| 実行 | `./gradlew verify`（ローカルの1コマンドの検査と同じタスク）。サブモジュールは固定先で取得し、依存は lockfile どおり（`npm ci`） | 無し |
| E2E | CI の外。`./gradlew e2eTest` は手元で実行する（4節） | 無し（`project.md` の Deployment の学び: 意図して CI の外に置く検査は代わりの実行の場を明記する） |
| 失敗のとき | 次の Bolt に進む前に原因を直す（`team.md` の Way of Working） | 無し |

## 2. 1コマンドの検査（`./gradlew verify`）の段と関門

既存の段の並びのまま、U6 の分は次の段に入ります。

| 段 | U6 の分 | 関門の基準 |
|---|---|---|
| 1 フォーマット | Prettier・Stylelint（`frontend/src/features/registration/`・`frontend/src/shared/validation/`・`frontend/e2e/` の新しいファイル） | 1件でも差分があれば失敗 |
| 2 リンタ | oxlint・ESLint（`react/no-danger` などのセキュリティ系を含む） | error で失敗 |
| 3 ライセンスヘッダー | 新しい `.ts`・`.tsx`・`.css`（`frontend/e2e/` を含む）の先頭の Apache License 2.0（`/* ... */`、2026、agwlvssainokuni） | 欠ければ失敗 |
| 4 ビルド・型検査 | `tsc`（`frontend/tsconfig.json` の `include` が `e2e` を含むため、確かめの見本と画面の側の応答の型のずれもここで落ちる）・`vite build` | 失敗で止める |
| 5 単体テスト | 画面部品のテスト（Vitest ＋ Testing Library ＋ user-event ＋ vitest-axe、部品ごとにアクセシビリティ検査1件）、`registrationToken`・`failureKind`・`formProblems`・`registrationApi` の単体テスト、`shared/validation` の性質ベースのテスト（fast-check、失敗時の `seed`・`path` を出力に残す） | 1件でも失敗で止める |
| 6 結合テスト | U6 の分は無い（サーバーの側の登録の API の結合テストは U3） | 既存の基準 |
| 7 カバレッジ | フロントエンドの下限（行 80%・分岐 70%、`@vitest/coverage-v8` の `thresholds`）。計測の除外を増やさない。`frontend/e2e/` は既存どおり対象外 | 下回れば失敗（NFR9.8） |
| 8 安全の検査 | `osvScan`（`frontend/package-lock.json`）・Gitleaks。U6 は依存を足さないため lockfile の差分は無い見込み（B5 で確かめる） | 既存の基準（実行時の依存は High 以上で止める） |
| 9 成果物 | `frontendBundleSize`（初回の JavaScript が gzip で 500KB を超えたら警告だけ） | 警告だけ（NFR6.3） |

- 依存: U6 は新しい依存を足しません（`@playwright/test`・`fast-check` は既存、`axe-core` は U4 が B4 で足す）。Mailpit の API は Node 24 の組み込みの `fetch` で呼びます（NFR9.6）。
- サブモジュール: B5 に固定先の更新は無い見込みです（make-you-chic-ui の更新は B4）。U6 は B4 の更新の後の版に頼ります（NFR9.7）。

## 3. 実際のブラウザの検査（アクセシビリティ、`./gradlew e2eTest` の中）

| 項目 | 内容 |
|---|---|
| ファイル | `frontend/e2e/` の E2E-1 と別のファイル。U4 の `050-` に足すか画面の単位ごとのファイルにするか、番号、共用の手伝いの重なりは B5 のコード生成の計画で U5・U7 とそろえて決める（承認の場の U6 R-02・U7 R-02） |
| 中身 | 登録の完了の画面の2状態（`ready`・`unavailable`）× 20 組（(a) テーマ2×文字の大きさ3、(b) ブランドカラー4×テーマ2、(c) 幅 375px でテーマ2×文字の大きさ3）。U4 の手伝いで axe（WCAG 2.0・2.1 の A・AA のタグ）と横のはみ出しを見る（NFR7.3） |
| 状態の出し方 | `ready` は組ごとの新しいコンテキストの中で `POST /api/registration/verify` だけを `page.route` で見本の 200 に差し替える。`unavailable` はフラグメントの無い `/register` を本物のまま開く（NFR7.5） |
| 非干渉 | ログインせず、サーバーの状態を変える要求を送らず、Mailpit を使わない。E2E の本数に数えない（`project.md` の学び） |
| 時間 | 見込みで約 1〜2 分（40 回）。B5 と Build and Test で実測し、検査の部分と流れの部分を分けて記録する |

## 4. 代表の流れ E2E-1（`./gradlew e2eTest`、verify と CI の外）

### 4.1 置き場と流れ

| 項目 | 内容 | 出典 |
|---|---|---|
| ファイル | `frontend/e2e/` に1本（番号は B5 の計画で U5・U7 とそろえる）。この Intent の代表の流れの1本 | W13、`team.md` の Testing Posture、要点 12 |
| 流れ | 管理者でログイン → U5 の画面で招待（言語 ja） → Mailpit で受けてリンクを取り出す（4.3） → 5回の計測（4.4） → リンクを開いて登録を完了 → ログインの画面の案内とメールアドレスの持ち越し → 新しい利用者でログイン → 管理メニューが無く管理画面の URL も使えない → ログアウト | W13 |
| 前提の作り方 | 前のテストが作った状態に頼らず、管理者のログインと招待を自分で行う。管理者は既存の初期管理者（`adminEmail`・`E2E_ADMIN_PASSWORD`）を読むだけで変えない | W13 の3、NFR1.5 |
| 宛先 | `e2e-invitee-<実行時刻>-<乱数の16進>@example.com` の形で実行ごとに作る。実在の宛先・外部の SMTP へは送らない | 要点 15、`project.md` の Forbidden |
| 応答の形の照合 | 本物の確かめの応答を `page.waitForResponse` で受け、状態コード 200、項目の名前の集まりと各項目の型が見本と同じことを確かめる。値は比べず、失敗の知らせにも項目の名前と型だけを出す | NFR 設計の Q1 A、`security-design.md` の5.2、要点 17 |
| 流れ全体の時間 | 目標を置かない（Playwright の既定の時間切れのまま） | NFR 要件の要点 16 |

### 4.2 WAR への設定と前提

| 項目 | 内容 |
|---|---|
| WAR の設定 | U6 は `frontend/playwright.config.ts` の `webServer.env` に何も足さない。SMTP の接続先と差出人は U1、`MASTERSMITH_WEB_BASE_URL: http://localhost:${port}` は U3 が足す（要点 13） |
| 前提の確かめ | U1 のとおり、`e2eTest` は始める前に `127.0.0.1:8025` の Mailpit の API に届くかを確かめ、届かなければ `docker compose --profile mail up -d mailpit` を示して失敗させる。E2E-1 は Mailpit を起動・停止しない（要点 14） |
| API の場所 | Mailpit の API の場所（`http://127.0.0.1:8025`）は U1 の前提の確かめと同じ1か所の定数を使う |

### 4.3 Mailpit からの取り出しの部品

`frontend/e2e/support/` の下（U4 が B4 で作る置き場）に取り出しのモジュールを1つ置きます（名前はコード生成で決める。例 `mailpit.ts`）。状態を持たず、Mailpit の API は読み取り（`GET`）だけを使い、書き込み・消す API は使いません（Q1: C）。

| 手順 | 内容 |
|---|---|
| (a) 探す | 検索の API（`GET /api/v1/search`、問い合わせは宛先の `to:`）でそのアドレスのメッセージを探し、宛先がそのアドレスと完全に一致するものだけを残す。1通でないとき（0通・2通以上）は失敗にする（送り直しを含まない流れのため1通のはず） |
| (b) 取り出す | 取得の API（`GET /api/v1/message/{ID}`）で HTML と本文の文字を読み、`/register#token=` を含む URL を取り出す。HTML の `href` と本文の文字の URL が同じで、Playwright の `baseURL` ＋ `/register#token=` で始まることを確かめる（U3 がベース URL だけから組み立てることの裏付け） |
| (c) 待つ | 上限 10 秒・間隔 500 ミリ秒（`expect.poll`）。招待の送信は要求の中で済む（ADR-009）ため、招待の応答の後すぐに届く。上限を超えたら、Mailpit の起動と U1 の SMTP の設定を確かめる旨だけの失敗にする |
| (d) 出さない | 失敗の知らせ・`console`・注記・添付・標準出力にアドレス・URL・トークン・本文を載せない（件数と「見つからない」などの種類だけ）。Mailpit への要求は Playwright の `request` ではなく Node の `fetch` で送り、Mailpit の応答の本文がトレースと報告に記録されないようにする |

```ts
// 説明用の断片（形だけ）
const link = await findInvitationLink(address)   // GET の search → message、値は返り値だけ
expect(link.startsWith(`${baseURL}/register#token=`), 'invitation link base').toBe(true)
```

### 4.4 フォームの時間の測り方（NFR6.1）

| 項目 | 内容 |
|---|---|
| 時点 | リンクを取り出した後、登録を完了する前 |
| 手順 | 5回とも `browser.newContext()`（キャッシュが空）で同じリンクを開き、`page.goto` の直前からメールアドレスの欄が見えるまでをテストの側の時計で測り、コンテキストを閉じる。確かめは招待を消費せず監査しない（U3 の BR7.1） |
| 同じ5回の確かめ | (a) アドレス欄に `#token=` が無い、(b) CSP の違反が無い、(c) localStorage・sessionStorage にトークンが無い。この3つは失敗の条件にする（`performance-design.md` の2.1） |
| 記録 | 5回の値（ミリ秒だけ）を注記と添付で残し、`json` の reporter のファイル（U4）から Build and Test が写す。時間では失敗させない |
| 実行 | WAR のヘルスチェックが通った後に測る。長い実行は `caffeinate -i` を付けて流す（`project.md` の Testing Posture） |

### 4.5 E2E-1 が残すものと片付け（Q1: C）

| 残るもの | 置き場 | 扱い |
|---|---|---|
| 招待・利用者・監査の行 | `e2eTest` の一時ディレクトリの内部DB | 配備したアプリの内部DB と監査ログを汚さない。`workers: 1` で後のファイルに利用者が残るが、後のファイルは頼らない |
| 招待メール（有効だったリンクと `example.com` のアドレス） | Mailpit（ボリュームなし、コンテナの中だけ） | E2E-1 は消さず、Mailpit の API に書き込まない（U5 の測定のメールも同じ）。開発者が `e2eTest` の後に `docker compose rm -sf mailpit` で止めて消す。手順を README の E2E の節に書く |
| HTML の報告・失敗のときのトレース | `frontend/playwright-report/`・`frontend/test-results/`（管理外） | 開いたリンクが載ることを受け入れ、手元だけに置いて共有しない（Q2: A）。README に「E2E の報告とトレースは共有しない」と書く |
| `json` の reporter のファイル | `frontend/test-results/` の下（U4） | 時間・成否・照合した項目の名前と型だけ。操作の題（開いた URL）が入るかは Playwright の版で違いうるため、B5 のコード生成で確かめ、入るときの扱いを計画に書く |

### 4.6 実行の時点

- 画面・認証に関わる変更を統合する前（B5）と、リリースの前に手元で `./gradlew e2eTest` を実行します（`team.md` の Testing Posture）。
- 実行の前に Mailpit を起動します（`docker compose --profile mail up -d mailpit`）。

## 5. 統合と配備

| 順 | 内容 | 関門 | 持ち主の段 |
|---|---|---|---|
| 1 | B5（U5〜U7 の画面）の作業ブランチを `develop` から作る | — | code-generation |
| 2 | 統合の前にローカルの `./gradlew verify` と `./gradlew e2eTest`（Mailpit を起動して）を通す。コンテナの実行環境が無い警告が出たら起動してやり直す | 全検査の合格 | code-generation・build-and-test |
| 3 | `develop` へ squash で統合する（1 Bolt が1コミット、日本語の件名）。`git push` は依頼者が行う | 依頼者の承認 | code-generation |
| 4 | CI（`./gradlew verify`）が統合の後に再確認する | CI の合格 | — |
| 5 | 配備したアプリをイメージの作り直しで入れ替え、ヘルスチェックとスモークテストを行う。U6 の分のスモークテストは、フラグメントの無い `/register` を `http://localhost:8080` で開いて「リンクが使えない」の表示が出ること（API を呼ばず、内部DB・監査を変えない） | healthy とスモークテスト | deployment-pipeline・deployment-execution |
| 6 | 招待から登録の完了までの配備の後の確かめ（配備した内部DB に利用者と監査の行が残る）は U3 の決定のとおり deployment-pipeline で決め、送る前に依頼者に伝える | 依頼者の了承 | deployment-pipeline |

- README の E2E の節の表に、E2E-1 とアクセシビリティの検査の行を足します（Mailpit の起動が要ること、時間は記録だけで失敗させないこと、取り出したリンクを報告に写さないこと、報告とトレースを共有しないこと、`e2eTest` の後に Mailpit を止めて消すこと）。

## 6. 戻し

| 項目 | 内容 |
|---|---|
| 手段 | 直前の版のイメージだけで戻す。スキーマの変更は無く、`.env` も変えない |
| 戻している間の影響 | B5 の前の版には `/register` の画面が無く、戻している間に届いた招待のリンクは開けない。トークンは有効期限まで使えるため、戻しを直せば同じリンクで続けられる。この注意を戻しの手順に書く（手順は deployment-pipeline） |
| E2E の環境 | 一時ディレクトリの内部DB で、戻しは要らない。Mailpit は止めて消すだけ |

## 7. 上流との差

承認済みの文書は書き換えません（`aidlc/spaces/default/memory/project.md` の Way of Working）。

| ID | 文書 | 承認済みの記述 | この段での扱い | 理由 |
|---|---|---|---|---|
| C-D1 | U3 の基盤の設計 `cicd-pipeline.md` の5節・`infrastructure-specification.md` の7節 | 取り出した URL はログ・報告・Playwright の報告の添付に出さない | 注記・添付・失敗の知らせ・標準出力・`json` のファイルには出さない。`page.goto(リンク)` の操作の題が HTML の報告に、URL と画面の写しが失敗のときのトレースに載ることは受け入れ、手元だけで共有しない（4.5） | 依頼者の決定（Q2: A）。トークンは一時の内部DB の招待にしか効かず、流れの最後に使い終わる（NFR1.5、`security-design.md` の1.4） |
| C-D2 | U3 の基盤の設計 `infrastructure-specification.md` の7節 | Mailpit が受けたメールは見終えたら止めて消す | E2E-1 は消さず Mailpit の API に書き込まない。開発者が `e2eTest` の後に止めて消す手順を README に書く（4.5） | 依頼者の決定（Q1: C）。U3 の決まりを手順にしたもの |
| C-D3 | 質問の文書の要点 16 | 取り出しの部品 | 消す API（`DELETE /api/v1/messages`）を使う記述は置かず、読み取りの API だけにした（4.3） | Q1: C の答えに合わせた |
| C-D4 | 前例で埋めた細部 | 上流に値が無い | Mailpit の API（`GET /api/v1/search` の `to:`・`GET /api/v1/message/{ID}` の HTML と本文の項目）は v1.31.2 の仕様として書いた。実物で確かめていないため、B5 のコード生成で最初に確かめ、違えば計画で直す。待つ上限 10 秒・間隔 500 ミリ秒と宛先の形は、U1 の時間切れ（3 秒）と ADR-009（要求の中で送る）から決めた | 上流は「取り出し方は infrastructure-design の持ち主」とだけ決めていたため |
| C-D5 | 前例で埋めた細部 | 上流に記述が無い | Mailpit に消さずに残るメールが増え続けたときの扱い（Mailpit の保持の上限の既定の値と、上限を超えたときに古いものから消えるか）は確かめていない。宛先が実行ごとに違うため取り出しには影響しない見込みで、B5 で確かめて README の手順に書く | Q1: C で消さないことにしたため |
| C-D6 | U4 の基盤の設計の4節 | `json` のファイルに秘密が入らないことをコード生成で確かめる | E2E-1 の分は、操作の題（開いた URL）が入るかも B5 で確かめる（4.5） | Q2: A の受け入れの範囲を HTML の報告とトレースに限るため |
