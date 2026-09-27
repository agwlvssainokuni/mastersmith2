# NFR Design — Questions（U4 表示の設定の土台 / u4-display-foundation）

U4 は、すべての画面に表示の設定（言語・テーマの選択・文字の大きさ・インスタンスの見た目）を当てる土台、ApiClient の広げ（要求の言語・トークンを付けない公開の API のパス）、ログインの画面の言語の切り替えと登録の完了からの受け渡しを受け持つ画面の単位です。種類は ui のため、作る成果物は performance-design・security-design・logical-components・traceability の4つです（拡張性・信頼性・観測性は service の単位だけ）。

非機能の設計のほとんどは、承認済みの NFR 要件・機能設計・契約・既存のコードで決まっています。そのため、まず NFR 設計の要点（案）を示します。上流から作りが1つに決まらない3点だけを質問にします。

1. ログインの画面を、幅 375px の検査に B4 で入れるか
2. 実際のブラウザの axe-core で、どの規則を合否に使うか
3. ApiClient の「トークンを付けないパス」の一覧の形

読んだ上流:

- 承認済みの NFR 要件 `aidlc/spaces/default/intents/260925-user-management/construction/u4-display-foundation/nfr-requirements/`
  - NFR2.1・NFR2.2、NFR6.1〜NFR6.5、NFR7.1〜NFR7.5、NFR8.1・NFR8.2、NFR9.1〜NFR9.11
  - `nfr-requirements-questions.md` の Q1: B・Q2: B と要点 14 件
- NFR 要件の承認の場の決定（Minor 2 件は Accepted risk で、コード生成で拾う）
  - R-01: 実際のブラウザの検査のファイルの実行順（番号）と、既存の E2E（010〜040 は番号の順に1本ずつ、同じ WAR と内部DB を共有する）への非干渉を、コード生成の計画で決める
  - R-02: ログインの画面の狭い幅の確かめ方を、B4 の計画に書く
- U5〜U7 の NFR 要件で決まった共通の決定
  - U4 の検査（NFR7.3）に、表示の幅 375px（高さ 812px）で、テーマ2×文字の大きさ3の6組を足す。B5 で U5・U6・U7 のすべての画面に当てる（`construction/u5-invitation-ui/nfr-requirements/tech-stack-decisions.md` の NFR7.4、U6 の NFR7.3 の (c)、U7 の NFR7.4）
  - 画面の時間の目標を置き、実際のブラウザで5回ずつ測って記録し、統合の関門にしない
  - U6 の検査は、検査のブラウザの中だけで確かめの API の答えを差し替える（`page.route`。U6 の NFR7.5）
- 承認済みの機能設計 `construction/u4-display-foundation/functional-design/`
  - `functional-spec.md`: D1〜D14、W1〜W12、7節（失敗の場合。W2 の5のハングの影響範囲を含む）、9節（Noto Serif JP・Noto Sans JP の採用の記録）
  - `frontend-components.md`: 3.3 のゲート、4節、6.1 の ApiClient。一覧の名前（`AUTH_API_PATHS`・`isAuthApiPath`）はコード生成で決めるとしている
- 依存する単位の NFR 設計（READY）
  - `construction/u8-instance-appearance/nfr-design/`: `GET /api/appearance` は I/O を持たず、保持した値を写すだけ。キャッシュの見出しは `no-store`。失敗は 500 で、画面は前に当てた値か既定のまま描く
  - `construction/u2-user-preferences/nfr-design/security-design.md` の3節: 400 `VALIDATION_FAILED` の追加の項目 `fieldErrors`
- 契約 `aidlc/spaces/default/intents/260925-user-management/inception/contract-design/contract-summary.md`（C3・C7・C9、未解決の点の C7・C9）、画面 `inception/refined-mockups/interaction-spec.md`（WCAG AA、狭い幅（768px 未満）でも崩れない）
- 決まり `aidlc/spaces/default/memory/team.md`・`project.md`
  - E2E の本数と置き場
  - Playwright の検査は流れではないため本数に数えない（NFR 要件の段の学び）
  - ライセンス、フロントエンドの依存の脆弱性
- 既存のコード
  - `frontend/src/shared/api-client/apiClient.ts`: `AUTH_API_PATHS`・`isAuthApiPath` は、このファイルの中だけで使われている
  - `frontend/src/shared/api-client/apiError.ts`: `ApiError.problem` は Problem Details の本文を追加の項目ごと持つ
  - `frontend/src/main.tsx`: Noto Sans JP の8つの CSS を読み込む
  - `frontend/vite.config.ts`: `modulePreload.polyfill: false`・`assetsInlineLimit: 0`・`manifest: true`
  - `frontend/scripts/check-bundle-size.mjs`: 入口から静的にたどった `.js` だけを gzip で数える
  - `frontend/playwright.config.ts`: `workers: 1`・`fullyParallel: false`、`Desktop Chrome`、ロケールは `ja-JP`、WAR は実行ごとの一時の内部DB
  - `frontend/e2e/010〜040`: 各テストは Playwright の既定の新しいブラウザのコンテキストで動く
  - `backend/src/main/resources/application.yaml`: CSP の `script-src 'self'`
  - `frontend/package.json`: `@playwright/test` ^1.63.0。axe-core は vitest-axe の推移依存
  - `vendor/make-you-chic-ui`: 読み取りだけ

## NFR 設計の要点（案）

1. **部品の構成（logical-components）**
   - 置き場は、機能設計の2節のとおりとする。
     - `frontend/src/app/display-settings/`（新しい）
     - `frontend/src/app/login-handoff/`（新しい）
     - 既存の `frontend/src/app/`・`frontend/src/shared/api-client/`・`frontend/src/features/auth/` を広げる
   - 実際のブラウザの検査は `frontend/e2e/` に置く。検査の手伝い（組の切り替え・axe-core の読み込み・はみ出しの判定）は、同じフォルダの下の手伝いのモジュールに置く。このモジュールは画面の成果物（`dist`）に入らない。
   - 失敗の範囲は画面の中に閉じ、壊れたときの影響は次のとおりとする。
     - ブラウザの保存・見た目の設定の読み取りの失敗: 既定の値で描く（D7・D11）
     - 見た目の設定の応答が返らない: 全画面の最初の描画が止まる（W2 の5、Q2 A で受け入れ済み）
2. **性能（最初の描画のゲート、NFR6.2）**
   - 見た目の設定の読み取り（`GET /api/appearance`）は、React の描画の外で1回だけ始める。
     - 例: 画面の入口か、`App` のモジュールの中の、1回だけ作る約束
     - 開発時の StrictMode の二重の描画でも要求は1回になる
     - `ThemeProvider` が保存の値を読むより前に、写しの鍵を書き直す（W1 の2）
   - セッションの復元（トークンの更新）は、既存の `LoginStateGate` が最初の描画の副作用で始める。
   - `DisplaySettingsProvider` は、2つの答えが出るまで子を描かない。待ちは2つの API の遅いほうにほぼ等しい。
   - 見た目の設定の答えは成功・失敗のどちらも「答えが出た」として扱う。失敗は例外にせず、前に当てた値のまま描く。
   - 待ちに上限・再試行・中断（AbortController）を置かない（W2 の4、Q2 A）。この決定は変えず、代わりに次の3つで支える。
     - (a) U8 の NFR 設計の「I/O を持たない」予算
     - (b) NFR6.1 の実際のブラウザでの測定
     - (c) 2つの要求がともに始まることの部品のテスト（どちらにもまだ答えない状態で、2つの要求を見る）
3. **性能（最初の画面が出るまでの時間の測り方、NFR6.1）**
   - 実際のブラウザの検査のファイル（要点 6）の中に、測定のテストを1件置く。
   - 次の手順を5回行う。
     1. `browser.newContext()` で、キャッシュが空の新しいコンテキストを作る
     2. `page.goto('/')` の直前から、ログインの画面の見出しが見えるまでの時間を、テストの側の時計で測る
     3. コンテキストを閉じる
   - テストの側の時計は Playwright の待ちの間隔を含むため、実際より長めに出る。長めに出る側で判定する。
   - 5回の値は、テストの注記（`test.info().annotations`）と添付（JSON）で残し、Build and Test の結果に写す。
   - 時間で失敗させない（統合の関門にしない）。2 秒を超えたときの切り分けは、NFR 要件の2節のとおり。
   - 同じテストで次の2つも確かめ、記録する。
     - 見た目の設定が `sans` のとき、Noto Serif JP のフォントのファイルへの要求が無いこと（要求の一覧で見る。NFR6.4）
     - CSP の違反がコンソールに出ないこと（NFR9.4）
4. **性能（フォントと配信物、NFR6.3〜NFR6.5）**
   - Noto Serif JP は、機能設計の 9.1 のとおり画面の入口（`frontend/src/main.tsx`）で読み込む。Noto Sans JP と同じく、japanese・latin の 400〜700 の8つの CSS とする。
   - `@font-face` の `font-display: swap` と `unicode-range` で、明朝体の文字を描くときにだけフォントのファイルが読まれる。
   - フォントのファイルは `dist/assets/` の名前にハッシュが付くファイルになり、既存の `CacheControlFilter` の `immutable` で保存される。
   - 初回の JavaScript の大きさは、既存の `check-bundle-size.mjs` で変更の前後を記録する。この道具は `.js` だけを数えるため、フォントと CSS は入らない。
   - 配信物の大きさは、コード生成で変更の前後を記録する。
     - `dist/assets/` のフォントのファイル（`.woff2`・`.woff`）の合計を、フォントの種類ごとに分ける
     - 入口の CSS の大きさ（`@font-face` の宣言が増えるため）
     - `backend/build/libs/mastersmith.war` の大きさ
   - 上限は置かない。
5. **セキュリティ（ブラウザの保存と受け渡し、NFR2.1・NFR2.2）**
   - ブラウザの保存の読み書きは、`display-settings` の中の1つのモジュールだけで行う。
     - `try`/`catch` で例外を外へ出さない
     - 項目ごとに許される値だけを受け入れる
   - 書く値は3つの軸だけの形から作る。ログイン状態や応答の本文をそのまま書かない。
   - 受け渡し（`login-handoff`）は、モジュールの中の変数だけに1回だけ持ち、描画の確定の後に消す。URL・ブラウザの保存・`console` に出さない。
   - 確かめは、NFR 要件の表のとおり画面部品のテストで行う。
     - localStorage・sessionStorage のすべての鍵の値に、テストのトークン・メールアドレス・氏名が無いこと
     - 受け渡しの後の URL にメールアドレスが無いこと
6. **セキュリティと非干渉（実際のブラウザの検査の置き場、NFR7.3・NFR9.11、R-01）**
   - 検査は新しいファイルに置く。ファイルの名前は既存の 010〜040 の後の番号とする（例: `050-display-accessibility.e2e.ts`）。
     - 既存の4つのファイルの実行順と前提を変えない
     - 既存のファイルは変えない
   - 非干渉は次の3つで作る。
     - (a) 組ごとに新しいブラウザのコンテキストを使い、表示の設定の値（U4 の鍵と make-you-chic-ui の鍵）をそのコンテキストの中だけに置く。後の既存のテストに持ち越さない。既定へ戻す処理は要らない。
     - (b) 見た目の設定の切り替えは、そのコンテキストの中だけで `GET /api/appearance` の答えを差し替えて行う（`page.route`、U6 の NFR7.5 と同じ形）。サーバーの設定と内部DB は変えない。
     - (c) U4 の B4 の検査（ログインの画面）はログインせず、サーバーの状態（内部DB・監査・ロックの回数）を変える要求を送らない。
   - B5 でログインの後の画面を足すときは、前提（利用者・招待）をその検査の中で自分で作る（NFR9.11）。
   - 組の切り替えは次のとおりとする。
     - テーマ・文字の大きさ: 読み込みの前に U4 の鍵（`mastersmith.display-settings`）をコンテキストの初めのスクリプト（`addInitScript`）で置く。本物の読み込みの道（D5・W1）を通す。
     - ブランドカラー: `/api/appearance` の答えの差し替えで切り替える。本物の当て方の道（W3）を通す。フォントファミリーは `sans` のまま。
   - `<html>` の属性を直接書き換える方法は使わない（本物の道を通らないため）。
   - 崩れは、各組で `document.documentElement.scrollWidth` が表示の幅以下であることで確かめる。
7. **セキュリティ（axe-core を検査のブラウザの中だけで読み込む、NFR9.4・NFR9.6）**
   - axe-core の本体のファイルは、明示の devDependencies（4.13.0）から Node の側で読む。
   - 読んだ本体は、検査のページで `page.evaluate` で評価する（`@axe-core/playwright` と同じ読み込み方）。この評価はブラウザの操作の口から行うため、ページの CSP（`script-src 'self'`）に止められない。
   - アプリの CSP・`index.html`・画面のコードは変えない。コンテキストで CSP を外す設定（`bypassCSP`）も使わない。
     - 使うと、そのファイルで CSP の違反を見る確かめ（要点 3）ができなくなる
     - アプリの実際の条件とずれる
   - 次の読み込み方は使わない。
     - `addScriptTag` の中身の埋め込み: CSP で止まる
     - 同じオリジンの道への差し替えで配信する方法: 検査のためだけの道が増える
   - 合否に使う規則は Q2 で決める。
8. **セキュリティ（公開の API・要求の言語・見た目の値、NFR9.1〜NFR9.3）**
   - トークンを付けないパスの判定は、1つの関数で、問い合わせの部分を除いた完全な一致で行う。
   - ほかのパスの付け方と、401 での更新の流れは変えない。
   - 一覧の形は Q3 で決める。
   - `Accept-Language` の値は、`ja`・`en` を返す登録済みの関数からだけ取る。呼び出し側の指定は上書きしない。
   - 見た目の設定の応答は、項目ごとに C7 の値だけを `setBrand`・`setFontFamily` に渡す。
   - `ApiError.problem` は、Problem Details の本文を追加の項目ごと持つ（既存）。そのため、U2 の `fieldErrors` のために ApiClient を変えない（読むのは U7 の `fieldErrors.ts`）。
9. **拡張性・信頼性・観測性（ui のため成果物は作らない）**
   - 画面の単位で、状態はブラウザの1つのタブの中だけにある。
   - 失敗の扱いは機能設計の7節のとおり（既定の値で描く・読み直さない）。
   - 画面の側に独自の指標・ログの送り先を足さない。`console` にトークン・メールアドレス・氏名を出さない。
   - 3つの設計の分類は成果物を作らない。要点は logical-components の失敗の範囲に書く。
10. **テストとカバレッジ（NFR7.2・NFR8・NFR9.8〜NFR9.10）**
    - 画面部品ごとの vitest-axe の検査を入れる。
    - 性質ベースのテスト（fast-check、失敗時の種を記録）を4つの関数に当てる。
    - 文言の鍵が ja・en でそろうことを確かめる。
    - フロントエンドのカバレッジの下限（行 80%・分岐 70%）を守り、除外を増やさない。
    - 既存の画面のテストと、既存の E2E 010〜040 が通り続けることを確かめる。
    - 実際のブラウザの検査の手伝いのモジュールは `frontend/e2e/` の下に置く。Vitest のカバレッジの計測の対象外（既存の E2E と同じ扱い）で、除外の設定は増やさない。
11. **依存（NFR9.5〜NFR9.7）**
    - 足す依存は次の2つ。版は lockfile で固定し、推移依存が無いことをコード生成で記録する。
      - `@fontsource/noto-serif-jp` 5.3.0（dependencies、OFL-1.1）
      - `axe-core` 4.13.0（devDependencies、MPL-2.0）
    - make-you-chic-ui の固定先の更新（edb1f94 → 735ef04）は、B4 の専用のコミットで行い、前後のハッシュを記録する。統合は fast-forward でよい。

## 決まっていること（質問にしない）

| 決まっていること | 出典 |
|---|---|
| 見た目の設定とセッションの復元を並べて読み、両方の答えが出るまで描かない。待ちに上限・再試行を置かない | 機能設計 W2・D11・7節、U4 の機能設計の Q2 A |
| 最初の画面が出るまで 2 秒以内。新しいコンテキストで5回測って記録し、統合の関門にしない | NFR6.1（Q1: B） |
| 初回の JavaScript は gzip で 500KB を目安にする（警告だけ）。配信物の大きさに上限を置かず、測って記録する | NFR6.3・NFR6.5 |
| Noto Serif JP は画面の入口で、Noto Sans JP と同じ形（400〜700、japanese・latin）で読み込み、自前で配信する | 機能設計 9.1・9.2、NFR6.4・NFR9.5 |
| ブラウザに保存するのは3つの表示の設定だけ。受け渡しはメールアドレスだけを、メモリで1回だけ | NFR2.1・NFR2.2、D6・D7・D13 |
| 公開の API の3つのパスにトークンを付けず、401 で更新しない。判定は完全な一致 | NFR9.1、D10 |
| アプリの CSP を変えない。検査の都合は検査のブラウザのコンテキストだけで扱う | NFR9.4 |
| 実際のブラウザの検査は Playwright＋axe-core で、`./gradlew e2eTest` の中、`verify` と CI の外。組は次の3つ。(a) テーマ2×文字の大きさ3の6組。(b) ブランドカラー4×テーマ2の8組（`md`）。(c) 幅 375px で、テーマ2×文字の大きさ3の6組。流れの E2E の本数に数えない | NFR7.3・NFR9.11、U5〜U7 の共通の決定 |
| 検査の要求の差し替え（`page.route`）は、検査のブラウザのコンテキストの中だけで行う | U6 の NFR7.5（Q2: B） |
| ApiClient の一覧の名前は、機能設計ではコード生成で決めるとしていた | `frontend-components.md` の 6.1（この段で Q3 として前倒しで尋ねる） |
| U2 の `fieldErrors` は Problem Details の追加の項目で、ApiClient は本文をそのまま `ApiError.problem` に持つ | U2 の NFR 設計の security-design.md の3節、既存の `apiError.ts` |
| make-you-chic-ui の中身は変えない。固定先の更新は B4 の専用のコミット | `project.md` の Forbidden・Mandated、NFR9.7 |

次の2点は、候補の論点から質問にせず、要点に書いて要約で確かめます。

- 既存の E2E への非干渉とファイルの番号（要点 6）: Playwright の既定（テストごとに新しいコンテキスト）と、ログインしない検査で、作りが1つに決まるため。
- axe-core の読み込み方（要点 7）: NFR9.4 の「アプリの CSP を緩めない」と、そのファイルでの CSP の違反の確かめを両立できるのは `page.evaluate` だけのため。

## Q1. ログインの画面を、幅 375px の検査（テーマ2×文字の大きさ3の6組）に B4 で入れますか？

理由:

- U5〜U7 の共通の決定は、幅 375px の6組を「B5 で U5・U6・U7 の画面に当てる」としています。ログインの画面には触れていません。
- NFR 要件の承認の場の R-02 は、B4 の対象のログインの画面の狭い幅の確かめ方を、B4 の計画に書くことを求めています。
- U4 はログインの画面の右上に言語の切り替え（`LoginLanguageSwitch`）を足します。文字の大きさ `lg` と幅 375px の組では、見出しやフォームと重なる・はみ出すおそれがあります。
- 検査の仕組み（組の切り替え・はみ出しの判定）は B4 で作ります。そのため、幅 375px の6組を足しても、増えるのは組の一覧だけです（新しい依存は無い）。

- A. B4 で、ログインの画面を幅 375px の6組にも入れる。表示の幅 375px（高さ 812px）で、テーマ2×文字の大きさ3の6組を切り替え、横のはみ出しが無いことと axe の違反 0 件を確かめる（ブランドカラーは既定の `blue`）。登録の完了の案内（受け渡しの値がある状態）は、受け渡しに登録の完了の画面の操作が要るため、B4 では検査しない（画面部品のテストで見る）。R-02 はこれで閉じる（推奨）
- B. B4 のログインの画面は、既定の幅の組（(a)・(b)）だけにする。幅 375px の組は、B5 で U5〜U7 の画面と一緒に、ログインの画面にも足す。R-02 は B4 の計画に「B5 で足す」と書いて閉じる
- C. ログインの画面には幅 375px の自動の確かめを入れない。Build and Test で、幅 375px で目で見て記録する
- X. Other (please specify)

[Answer]: A

## Q2. 実際のブラウザの axe-core で、どの規則を合否（違反 0 件）に使いますか？

理由:

- 目標は WCAG 2.1 AA です（NFR7.1）。
- axe-core の既定の実行は、WCAG の規則に加えて「ベストプラクティス」の規則も流します。例: ランドマークの中に置く `region`、見出しの1つ目 `page-has-heading-one`。これらは WCAG の達成基準ではありません。
- U5 は、表が横に動く領域にキーボードで届くこと（`scrollable-region-focusable`）の確かめを求めています（U5 の NFR7.4）。
- make-you-chic-ui の部品は変えられません。部品の側のベストプラクティスの指摘で落ちると、このリポジトリでは直せないことがあります。
- ここで決めた規則は、B5 の U5〜U7 の画面にも同じく当てます。

- A. WCAG 2.0・2.1 の A と AA のタグ（`wcag2a`・`wcag2aa`・`wcag21a`・`wcag21aa`）の規則だけを合否に使う。コントラスト（`color-contrast`）はここに入る。`scrollable-region-focusable` がこのタグの版に入っていなければ、規則の名前で足す。ベストプラクティスは流さない（推奨）
- B. A の規則で合否を決め、ベストプラクティスの規則も流して、違反は失敗にせず件数と規則の名前をテストの注記に記録する（Build and Test の結果に写し、直すかは画面の段の持ち主が判断する）
- C. axe-core の既定の規則（ベストプラクティスを含む）すべてで、違反 0 件を合否にする
- X. Other (please specify)

[Answer]: A

## Q3. ApiClient の「トークンを付けず、401 で更新しないパス」の一覧を、どの形にしますか？

理由:

- 機能設計（`frontend-components.md` の 6.1）は、今の一覧（`AUTH_API_PATHS`・`isAuthApiPath`）が認証の API に限った名前のため、次のどちらにするかをコード生成で決めるとしていました。
  - 公開の API を含む名前に改める
  - 公開の API の一覧を別に置いて、判定でまとめる
- どちらもふるまいは同じです（NFR9.1）。
- 違いは、セキュリティの点検のしやすさです。トークンを付けないかを決める一覧と判定の場所が、1か所か2か所かが変わります。
- 今の名前は `apiClient.ts` の中だけで使われており、ほかのファイルとテストは参照していません。改めても影響はこのファイルに閉じます。

- A. 1つの一覧と1つの判定にまとめ、名前を改める（例: `TOKENLESS_API_PATHS`・`isTokenlessApiPath`）。認証の API の3つと公開の API の3つを、コメントで分けて同じ一覧に並べる。トークンを付けないパスを点検するときに見る所が1か所になる（推奨）
- B. 認証の API の一覧（`AUTH_API_PATHS`）はそのまま残し、公開の API の一覧（例: `PUBLIC_API_PATHS`）を別に置く。判定の関数を1つ（例: `isTokenlessApiPath`）にし、2つの一覧を見る。既存の名前を変えずに済む
- C. 機能設計のとおり、コード生成で決める（この段では決めない）
- X. Other (please specify)

[Answer]: A

---

## Consolidated Summary Confirmation

答えのまとめ（Q1〜Q3 の回答の後に埋めます）:

- NFR 設計の要点（案）は冒頭の 11 件のとおり。主な点は次のとおり。
  - 見た目の設定の読み取りは描画の外で1回だけ始め、2つの答えが出るまで描かない。上限・再試行・中断は置かない
  - 最初の画面の時間は、新しいコンテキストで5回、テストの側の時計で測って注記と添付で残す
  - フォントは入口で8つの CSS を読み込み、配信物の大きさを種類ごとに記録する
  - 実際のブラウザの検査は `050-` の新しいファイルに置き、組ごとに新しいコンテキストを使う。組の切り替えは U4 の鍵の初めのスクリプトと、`/api/appearance` の答えの差し替えで行い、ログインしない
  - axe-core は Node の側で読み、`page.evaluate` で評価する。アプリの CSP と `bypassCSP` は変えない
  - `fieldErrors` のために ApiClient を変えない
  - 拡張性・信頼性・観測性の成果物は作らない
- Q1 A: ログインの画面を B4 で幅 375px の検査（テーマ2×文字の大きさ3の6組）に入れる（登録の完了の案内の状態は検査しない）。NFR 要件の R-02 を閉じる。U5〜U7 の共通の決定をログインの画面にも広げることを上流との差として記録する
- Q2 A: 実際のブラウザの axe-core の合否は WCAG 2.0・2.1 の A・AA のタグの規則だけで決める。`scrollable-region-focusable` がタグに入っていなければ名前で足す
- Q3 A: ApiClient のトークンを付けず 401 で更新しないパスは、1つの一覧と1つの判定にまとめ、名前を改める（例: `TOKENLESS_API_PATHS`・`isTokenlessApiPath`）。機能設計の「コード生成で決める」を前倒しで決めたことを上流との差として記録する

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
