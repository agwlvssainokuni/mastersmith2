# Performance Requirements — U6 登録の完了の画面（u6-registration-ui）

U6 の性能の要件です。U6 は画面の単位のため、自分の API を持ちません。ここでは、招待のリンクを開いてから登録のフォームが出るまでの時間、確かめ中と送信中の表示、画面の塊の読み込みを扱います。答えは `nfr-requirements-questions.md`（Q1: B、Q2: B、Consolidated Summary Confirmation: Looks correct）です。

出典の略号:
- NFR・FR は `aidlc/spaces/default/intents/260925-user-management/inception/requirements-analysis/requirements.md`
- D・W と「n節」は、この単位の `construction/u6-registration-ui/functional-design/functional-spec.md`
- Q1・Q2 と「要点 n」は、この段の `nfr-requirements-questions.md`（Q と「NFR の要点（案）」の番号）
- 「共通の決定」は、U5 の NFR 要件の質問で出た、画面の3つ（U5〜U7）に共通する依頼者の決定（画面の時間の目標を置き、実際のブラウザで5回ずつ測って記録し、統合の関門にはしない。狭い幅は幅 375px の6組を足す）

枝番はこの単位の中で振ります（ほかの単位と同じ番号でも別の要件です）。この単位の成果物と置き場は次のとおりです。

| 成果物 | 置く要件 |
|---|---|
| この文書 | 性能（NFR6.1〜NFR6.3） |
| `security-requirements.md` | 招待のトークン（NFR1.1〜NFR1.5）、招待のメールアドレス（NFR2.1）、列挙の防止の画面の側（NFR3.1）、公開の API・回数の制限・パスワードの入力・二重の送信・CSP（NFR9.1〜NFR9.5） |
| `tech-stack-decisions.md` | 依存と make-you-chic-ui（NFR9.6・NFR9.7）、アクセシビリティ（NFR7.1〜NFR7.5）、多言語（NFR8.1・NFR8.2）、テスト（NFR9.8〜NFR9.11） |

## 前提

- 画面 `/register` は機能の登録の遅延読み込み（`frontend/src/app/registry/registrationModules.ts`）で、開いたときに画面の塊を読みます。フラグメントからトークンを取り出してアドレス欄から消し、リンクの確かめ（`POST /api/registration/verify`）の答えを待ってフォームを出します（W2・W4・W5）。
- 画面が使う2つの API の目標は、U3 で決まっています。
  - リンクの確かめ: 同時 10 件で p95 1 秒（`construction/u3-invitation/nfr-requirements/performance-requirements.md` の NFR6.3）
  - 登録の完了の成功: 同時 10 件で p95 1 秒（同じ文書の NFR6.4）
  - どちらも Performance Validation の段の k6 で測ります。
- リンクの確かめは内部DB を変えず、成功も失敗も監査しません（`construction/u3-invitation/functional-design/rules.md` の BR7.1）。そのため、完了の前なら同じリンクを何度開いても状態は変わらず、監査の行も増えません。
- 画面の時間は、手元の PC のブラウザ（Playwright の Chromium、`Desktop Chrome`）で、`./gradlew :backend:bootWar` で作った WAR を相手に測ります（既存の `frontend/playwright.config.ts`）。

## 1. 目標

| ID | 要件 | 確かめ方と持ち主の段 | 出典 |
|---|---|---|---|
| NFR6.1 | 手元の PC のブラウザで、キャッシュが空の新しいブラウザのコンテキストで招待のリンクを開いてから、登録のフォーム（メールアドレスの欄）が出るまで 2 秒以内 | Build and Test で、E2E-1（`./gradlew e2eTest`、NFR9.10）の中で測って記録する。登録を完了する前に、E2E-1 で取り出した同じリンクを、測るたびに新しいブラウザのコンテキスト（キャッシュが空）で開き、移動の開始からメールアドレスの欄が見えるまでを 5 回測る。5 回すべてが 2 秒以内であることを目標にする。時間は統合の関門にしない（テストの成否にしない）。測った値は Build and Test の結果に記録する | Q1 B、共通の決定、要点 1、W4・W5、U3 の BR7.1 |
| NFR6.2 | 待ちの間は、画面が止まって見えないように状態を示す。確かめの答えが返るまでは「確かめています」（`role="status"`）を出し、送信の間は「登録を完了する」のボタンを「登録しています」（`aria-busy`）にする。どちらの待ちにも画面の側で上限を置かない（応答か通信の失敗まで待つ。既存の ApiClient のまま） | 画面部品のテスト（Vitest）で、確かめ・完了の要求に答えを返さない間、それぞれの表示が出ていることを見る（`frontend-components.md` の 8節）。API の時間そのものは U3 の NFR6.3・NFR6.4 を Performance Validation の k6 で測る | 要点 1・2、W4・W8 |
| NFR6.3 | 登録の完了の画面は遅延読み込みの塊に入れ、入口の JavaScript を増やさない。初回の読み込みの JavaScript（入口のファイルと、そこから静的に読み込まれるファイル）は gzip で 500KB 以内を目安とする（超えたら警告を出すだけで、統合は止めない。U4 の NFR6.3 のまま） | 既存の `frontend/scripts/check-bundle-size.mjs`（`./gradlew verify` の中）。B5 のコード生成で、登録の完了の画面の塊が入口のファイルと別のファイルに出ることと、その塊の大きさ（圧縮前と gzip）を記録する | 要点 3、U4 の NFR6.3 |

## 2. 測り方の注意

- NFR6.1 はブラウザと PC の状態に左右されます。そのため統合の関門にはせず、測った値を記録します（U4 の NFR6.1 と共通の決定と同じ扱い、`team.md` の「不安定なテストは統合しない」）。
- 2 秒を超えたときは、目標を緩めて「満たした」ことにはしません（`project.md` の Testing Posture）。次の点を確かめ、結果とともに依頼者に相談します。
  - リンクの確かめの API の時間
  - 画面の塊と入口の JavaScript の読み込み
  - 描画（見た目の設定とセッションの復元の待ち、U4 の W2）
- 5 回の計測はどれも同じリンクを開くだけで、登録を完了しません。完了は計測の後に1回だけ行います（E2E-1 の流れのまま）。
- E2E の WAR は内部DB が空の状態から起動します。そのため NFR6.1 は、最初の起動の直後の値ではなく、ヘルスチェックが通った後に測ります（`playwright.config.ts` の `webServer.url` のとおり）。
- 長い計測は `caffeinate -i` を付けて流し、PC のスリープで値が崩れるのを防ぎます（`project.md` の Testing Posture）。

## 3. 上流との差

承認済みの文書は書き換えません（`aidlc/spaces/default/memory/project.md` の Way of Working）。

| 決定 | 文書 | 承認済みの記述 | この段での扱い |
|---|---|---|---|
| リンクを開いてからフォームが出るまで 2 秒以内（Q1: B） | 要件の NFR6（`inception/requirements-analysis/requirements.md`） | API の応答時間だけを決めており、画面の時間の目標は無い | 追加（NFR6.1）で、食い違いではない。統合の関門にせず、測って記録する（共通の決定と同じ） |
| E2E-1 の中で同じリンクを 5 回開いて測る | 機能設計の W13（E2E-1 の流れ） | 流れの中でリンクを開くのは1回 | 流れの確かめは W13 のまま。計測のために、完了の前に同じリンクを新しいコンテキストで 5 回開く手順を足す（U3 の BR7.1 で状態は変わらない）。追加 |
| 初回の JavaScript の目安 500KB | U4 の NFR6.3 | 目安（警告だけ） | そのまま当てる（NFR6.3）。変更は無い |
