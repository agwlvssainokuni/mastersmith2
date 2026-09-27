# Performance Design — U6 登録の完了の画面（u6-registration-ui）

U6 の性能の設計です。U6 は画面の単位で、自分の API を持ちません。この文書では次の4つの作りを決めます。

- 招待のリンクを開いてから登録のフォームが出るまでの時間の測り方（E2E-1 の中の5回）
- 確かめ中・送信中の待ちの表示
- 画面の塊の読み込み
- 実際のブラウザのアクセシビリティの検査の回数と時間の見積もり

答えは `nfr-design-questions.md` にあります（Q1: A、Q2: A、Consolidated Summary Confirmation: Looks correct）。

出典の略号:

- NFR: この単位の NFR 要件 `construction/u6-registration-ui/nfr-requirements/` の枝番（この単位の中で振った番号）
- D・W・n節: この単位の機能設計 `construction/u6-registration-ui/functional-design/functional-spec.md`
- 部品 n節: 同じフォルダの `frontend-components.md`
- 要点 n: この段の `nfr-design-questions.md` の「NFR 設計の要点（案）」の番号
- U4 の設計: `construction/u4-display-foundation/nfr-design/` の各文書

この単位（種類 ui）の成果物と置き場は、次の表のとおりです。拡張性・信頼性・観測性の設計は service の単位だけが作ります。U6 での扱いは `logical-components.md` の4節に書きます。

| 成果物 | 置く設計 |
|---|---|
| この文書 | 性能（NFR6.1〜NFR6.3）、アクセシビリティの検査の回数と時間の見積もり（承認の場の R-01） |
| `security-design.md` | 招待のトークン（NFR1.1〜NFR1.5）、メールアドレス（NFR2.1）、列挙の防止（NFR3.1）、公開の API・想定外の応答・パスワード・二重の送信・CSP（NFR9.1〜NFR9.5）、依存（NFR9.6・NFR9.7）、差し替えの答えと本物の応答の形の照合（Q1 A、承認の場の R-02）、400 の出し方（Q2 A） |
| `logical-components.md` | 部品の一覧と依存の向き・失敗の範囲、アクセシビリティ（NFR7.1〜NFR7.5）、多言語（NFR8.1・NFR8.2）、テストの作り（NFR9.8〜NFR9.11）、実際のブラウザで動くファイルの置き場と非干渉 |
| `traceability.json` | NFR の枝番と設計の対応 |

## 1. 予算の前提

| 項目 | 値 | 持ち主 |
|---|---|---|
| リンクの確かめ（`POST /api/registration/verify`） | 同時 10 件で p95 1 秒 | U3 の NFR6.3（Performance Validation の k6） |
| 登録の完了の成功（`POST /api/registration/complete`） | 同時 10 件で p95 1 秒 | U3 の NFR6.4（同上） |
| 見た目の設定（`GET /api/appearance`） | I/O を持たず、保持した値を写すだけ | U8 の NFR 設計 |
| 最初の描画のゲート（見た目の設定とセッションの復元の遅いほうを待つ） | 上限・再試行・中断を置かない | U4 の `performance-design.md` の2節 |
| リンクを開いてからフォームが出るまで | 2 秒以内（記録だけ、関門にしない） | この単位の NFR6.1 |

リンクを開いてからフォームが出るまでの時間は、次の和になります。U6 が足す待ちは画面の塊の読み込みと確かめの API の2つです。

1. 入口の HTML・JavaScript・CSS の読み込み（キャッシュが空）
2. U4 の最初の描画のゲート
3. 登録の完了の画面の塊の読み込み（遅延読み込み、3節）
4. リンクの確かめの API の往復
5. フォームの描画

## 2. リンクを開いてからフォームが出るまでの測り方（NFR6.1、要点 2）

測り方は U4 の `performance-design.md` の3節と同じ形にし、E2E-1 のファイルの中に置きます。

- 置く場所: E2E-1 の流れの中で、招待メールからリンクを取り出した後、登録を完了する前。
- 手順を5回くり返す。
  1. `browser.newContext()` で、キャッシュが空の新しいコンテキストを作る
  2. `page.goto(リンク)` の直前から、メールアドレスの欄（読み取り専用の TextInput）が見えるまでを、テストの側の時計で測る
  3. 次の確かめ（2.1）を行う
  4. コンテキストを閉じる
- テストの側の時計は Playwright の待ちの間隔を含むため、実際より長めに出ます。長めに出る側で判定し、目標を緩めません。
- 5回の値（ミリ秒）は、テストの注記（`test.info().annotations`）と添付（JSON）で残し、Build and Test の結果に写します。
- 時間では失敗させません（統合の関門にしない）。2 秒を超えたときは NFR 要件の2節のとおり、確かめの API の時間・塊と入口の JavaScript の読み込み・U4 のゲートの待ちを切り分け、依頼者に相談します。
- 5回の開きはどれもリンクの確かめだけで、招待の状態を変えず、監査もしません（U3 の BR7.1）。完了は計測の後に、流れの本体で1回だけ行います。
- WAR はヘルスチェックが通った後に測ります（`playwright.config.ts` の `webServer.url`）。長い計測は `caffeinate -i` を付けて流します（`project.md` の Testing Posture）。

```ts
// 説明用の断片（形だけ）
for (let i = 0; i < 5; i++) {
  const ctx = await browser.newContext()
  const p = await ctx.newPage()
  const t0 = Date.now()
  await p.goto(link)
  await expect(p.getByLabel(emailLabel)).toBeVisible()
  times.push(Date.now() - t0)
  expect(p.url()).not.toContain('#token=')   // 2.1 の (a)
  await ctx.close()
}
```

### 2.1 同じ5回で行う確かめ（失敗の条件にする）

時間と違って PC の状態に左右されない確かめなので、失敗の条件にします（U4 が NFR6.4・NFR9.4 の確かめを失敗の条件にした前例と同じ扱い）。

| 確かめ | 見方 | 要件 |
|---|---|---|
| (a) フォームが出た後のアドレス欄に `#token=` が無い | `page.url()` | NFR1.3 |
| (b) ブラウザのコンソールに CSP の違反が出ない | コンテキストの `console` の出来事と `pageerror` を集め、CSP の違反の知らせが無いこと | NFR9.5 |
| (c) ブラウザの保存にトークンが無い | `page.evaluate` で localStorage・sessionStorage のすべての鍵の値を読み、リンクのトークンが含まれないこと | NFR1.2 |

(c) は画面部品のテスト（NFR1.2 の (a)）の実際のブラウザでの裏付けで、読み取りだけのため費用は小さいので足します。

## 3. 待ちの表示と画面の塊（NFR6.2・NFR6.3、要点 3）

- 確かめ中（`verifying`）は `RegistrationStatus` が `role="status"` で「リンクを確かめています」を出します。送信中（`submitting`）は「登録を完了する」の Button を `loading`（`aria-busy`・`aria-disabled`）にし、「登録しています」を出します。
- どちらの待ちにも画面の側で上限・再試行・中断（`AbortController`）を置きません。既存の ApiClient のまま、応答か通信の失敗まで待ちます。サーバーの側の時間は U3 の NFR6.3・NFR6.4 で決まっています。
- 確かめは `phase` が `verifying` になったときの副作用で送ります（部品 4.3）。開発時の `StrictMode` で2回送られても害は無く（招待を消費しない）、古い要求の答えは捨てて最後に送った要求の答えだけで状態を移します（`functional-spec.md` の W4 の4）。この段で作りを変えません。成果物のビルドは `StrictMode` の二重の実行をしないため、実際のブラウザの時間（2節）には影響しません。
- 画面は機能の登録の遅延読み込み（`features/registration/registration.ts`、`frontend/src/app/registry/registrationModules.ts` の既存の仕組み）で入口と別の塊にします。`shared/validation` は U6 と U7 の塊から読まれ、入口には入りません。
- 初回の JavaScript（入口と、そこから静的に読まれるファイル）の目安は gzip で 500KB（超えたら警告だけ）。既存の `frontend/scripts/check-bundle-size.mjs`（`./gradlew verify` の中）のままです。
- B5 のコード生成で次を記録します。
  - 登録の完了の画面の塊が入口のファイルと別のファイルに出ること（`dist/.vite/manifest.json` で、入口から静的にたどれないこと）
  - その塊の大きさ（圧縮前と gzip）と、`check-bundle-size.mjs` の値の変更の前後

## 4. アクセシビリティの検査の回数と時間の見積もり（承認の場の R-01、要点 4）

| 項目 | 値 |
|---|---|
| U6 の状態 | 2（`ready`・`unavailable`） |
| 組 | (a) テーマ2×文字の大きさ3の6組、(b) ブランドカラー4×テーマ2の8組、(c) 幅 375px でテーマ2×文字の大きさ3の6組 = 20 組 |
| U6 の実行 | 2×20 = 40 回（組ごとに新しいコンテキストで画面を開き、axe を1回流し、横のはみ出しを見る） |
| 1組あたりの見込み | 1〜3 秒（コンテキストの作成、画面の読み込み、U4 のゲートの待ち、`ready` では差し替えの確かめ、axe の実行） |
| U6 の分の見込み | 約 1〜2 分 |
| `e2eTest` の検査の部分の見込み | U4 のログインの画面 20 組、U5・U7 の画面の分を合わせて約 160 組、約 3〜8 分 |

- どれも実測していない見込みの値です。B5 のコード生成で検査を足した後の `./gradlew e2eTest` の時間を実測し、Build and Test の結果に、検査の部分と流れの部分（010〜040 と E2E-1）の時間を分けて記録します。
- 時間の上限は置きません。検査が長くなることは NFR 要件の承認の場で依頼者が受け入れています。
- 組を減らす、1つのコンテキストを組の間で使い回す変更はしません。U4 の非干渉の作り（組ごとに新しいコンテキストで、表示の設定の値をそのコンテキストに閉じる）を崩すためです。
- `e2eTest` は `./gradlew verify` と CI の外にあり、画面・認証に関わる変更を統合する前とリリースの前に手元で実行します（`team.md` の Testing Posture）。そのため、この時間は統合前の関門の時間を増やしません。

## 5. 上流との差

承認済みの文書は書き換えません（`aidlc/spaces/default/memory/project.md` の Way of Working）。

| ID | 文書 | 承認済みの記述 | この段での扱い | 理由 |
|---|---|---|---|---|
| PD-D1 | NFR 要件の NFR1.3（`security-requirements.md`） | E2E-1 で、リンクを開いてフォームが出た後の `page.url()` に `#token=` が無いことを見る（1回） | 計測の5回のすべてでも見て、失敗の条件にする（2.1 の (a)） | 同じ5回で費用なく見られ、不安定でないため。要約（要点 2）で依頼者が確かめた。追加 |
| PD-D2 | NFR 要件の NFR9.5（`security-requirements.md`） | Build and Test の E2E で、コンソールに CSP の違反が出ないことを記録する | 記録に加えて、計測の5回で違反があれば失敗にする（2.1 の (b)） | U4 の NFR 設計が NFR9.4 の確かめを失敗の条件にした前例と同じ。要約（要点 2）で依頼者が確かめた。追加 |
| PD-D3 | NFR 要件の NFR1.2（`security-requirements.md`） | ブラウザの保存にトークンが無いことを画面部品のテストで見る | 計測の5回で、実際のブラウザでも見る（2.1 の (c)） | 画面部品のテストの jsdom の保存と実際のブラウザの保存の差を埋める裏付け。読み取りだけで費用が小さい。追加で、要約に無かった点のため承認の場で伝える |
| PD-D4 | NFR 要件の承認の場の R-01 | 検査の回数と `e2eTest` の時間の見積もりを添える | 4節に見込みの値を書き、実測は B5 のコード生成と Build and Test で行う | R-01 の求めに応じた。見込みで、実測ではない |
