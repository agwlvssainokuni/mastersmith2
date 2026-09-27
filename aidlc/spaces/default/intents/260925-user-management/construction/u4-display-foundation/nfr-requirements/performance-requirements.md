# Performance Requirements — U4 表示の設定の土台（u4-display-foundation）

U4 の性能の要件です。U4 は画面の単位のため、自分の API を持ちません。ここでは、画面を開いてから最初の画面が出るまでの時間、初回に読み込む JavaScript の大きさ、フォントの読み込み、配信物の大きさを扱います。答えは `nfr-requirements-questions.md`（Q1: B、Consolidated Summary Confirmation: Looks correct）です。

出典の略号:
- NFR・FR は `aidlc/spaces/default/intents/260925-user-management/inception/requirements-analysis/requirements.md`
- D・W・9節は、この単位の `construction/u4-display-foundation/functional-design/functional-spec.md`
- Q1・Q2 と「要点 n」は、この段の `nfr-requirements-questions.md`（Q と「NFR の要点（案）」の番号）

枝番はこの単位の中で振ります（ほかの単位と同じ番号でも別の要件です）。この単位の成果物と置き場は次のとおりです。

| 成果物 | 置く要件 |
|---|---|
| この文書 | 性能（NFR6.1〜NFR6.5） |
| `security-requirements.md` | 個人に関する値とブラウザの保存（NFR2.1・NFR2.2）、公開の API・要求のヘッダー・応答の値・CSP（NFR9.1〜NFR9.4） |
| `tech-stack-decisions.md` | 依存と make-you-chic-ui（NFR9.5〜NFR9.7）、アクセシビリティ（NFR7.1〜NFR7.5）、多言語（NFR8.1・NFR8.2）、テスト（NFR9.8〜NFR9.11） |

## 前提

- 画面を開くと、見た目の設定の読み取り（`GET /api/appearance`）とセッションの復元（トークンの更新）を同時に始めます。両方の答えが出るまで最初の画面は描きません。待ちに上限はありません（W2・D11、機能設計の Q2 A）。
- 2つの API の目標は、ほかの単位で決まっています。
  - `GET /api/appearance`: 同時 10 件で p95 300 ミリ秒（`construction/u8-instance-appearance/nfr-requirements/performance-requirements.md` の NFR6.1）
  - トークンの更新: 同時 10 件で p95 1 秒のまま（`construction/u2-user-preferences/nfr-requirements/performance-requirements.md` の NFR6.5）
  - どちらも Performance Validation の段の k6 で測ります。
- 画面の時間は、手元の PC のブラウザ（Playwright の Chromium）で、`./gradlew :backend:bootWar` で作った WAR を相手に測ります（既存の `frontend/playwright.config.ts`）。

## 1. 目標

| ID | 要件 | 確かめ方と持ち主の段 | 出典 |
|---|---|---|---|
| NFR6.1 | 手元の PC のブラウザで、キャッシュが空の状態で画面を開いてから、最初の画面（ログインの画面）が出るまで 2 秒以内 | Build and Test で、既存の E2E（`./gradlew e2eTest`）の中で測って記録する。測るたびに新しいブラウザのコンテキスト（キャッシュが空）を使い、移動の開始からログインの画面の見出しが見えるまでを 5 回測る。5 回すべてが 2 秒以内であることを目標にする。時間は統合の関門にしない（テストの成否にしない）。測った値は Build and Test の結果に記録する | Q1 B、要点 1、W2 |
| NFR6.2 | 見た目の設定の読み取りとセッションの復元は、どちらかの答えを待たずに始める。最初の描画の待ちは、2つの API の遅いほうにほぼ等しく、足し算にならない | 画面部品のテスト（Vitest）で確かめる。どちらの要求にもまだ答えを返さない状態で、2つの要求がともに始まっていることを見る。片方の答えだけでは描かないことは、機能設計の `frontend-components.md` の 7節の `DisplaySettingsProvider` のテストで見る（コード生成） | 要点 1、W2・D11 |
| NFR6.3 | 初回の読み込みの JavaScript（入口のファイルと、そこから静的に読み込まれるファイル）は、gzip で 500KB 以内を目安とする。超えたら警告を出すだけで、統合は止めない（既存の目安のまま） | 既存の `frontend/scripts/check-bundle-size.mjs`（`./gradlew verify` の中）。U4 の変更の前後の値（今の入口の JavaScript は圧縮前 約 370KB）をコード生成で測って記録する | 要点 2、前の Intent の U1 の NFR1.5 |
| NFR6.4 | フォントは描画を止めない。Noto Serif JP は、Noto Sans JP と同じく太さごとの `@font-face` の宣言（`font-display: swap`）だけを CSS に持ち、フォントのファイルは明朝体の文字を描くときにだけ読む。フォントのファイルは名前にハッシュが付く `/assets/` の下に置き、`public, max-age=31536000, immutable` で保存させる（既存の `CacheControlFilter` のまま） | コード生成で、ビルドした CSS の `font-display: swap` と、フォントのファイルが `dist/assets/` の下に出ることを確かめる。保存の指定は既存の `CacheControlFilter` のテストのまま。見た目の設定が `sans` の画面でフォントのファイル（Noto Serif JP）が読まれないことは、Build and Test の E2E の中で要求の一覧を見て記録する | 要点 3、9.1 |
| NFR6.5 | 配信物（`dist` と WAR）の大きさの上限は置かない。Noto Serif JP を足して増えた量を測って記録する。今の値は、Noto Sans JP の分が 約 9.8MB（japanese のサブセットは太さごとに woff2 約 1.0MB、ほかに woff）、WAR が 約 87MB | コード生成で、変更の前後の `dist/assets/` のフォントのファイルの合計と WAR の大きさを記録する | 要点 4、9.2 の Consequences（機能設計の Q4 A） |

## 2. 測り方の注意

- NFR6.1 はブラウザと PC の状態に左右されます。そのため統合の関門にはせず、測った値を記録します（前の Intent の DSL の管理画面の NFR1.18 と同じ扱い、`team.md` の「不安定なテストは統合しない」）。
- 2 秒を超えたときは、目標を緩めて「満たした」ことにはしません（`project.md` の Testing Posture）。次の点を確かめ、結果とともに依頼者に相談します。
  - 2つの API のどちらが遅いか
  - JavaScript の読み込みか
  - 描画か
- E2E の WAR は内部DB が空の状態から起動します。そのため NFR6.1 は、最初の起動の直後の値ではなく、ヘルスチェックが通った後に測ります（`playwright.config.ts` の `webServer.url` のとおり）。
- 長い計測は `caffeinate -i` を付けて流し、PC のスリープで値が崩れるのを防ぎます（`project.md` の Testing Posture）。

## 3. 上流との差

承認済みの文書は書き換えません（`aidlc/spaces/default/memory/project.md` の Way of Working）。

| 決定 | 文書 | 承認済みの記述 | この段での扱い |
|---|---|---|---|
| 最初の画面が出るまで 2 秒以内（Q1: B） | 要件の NFR6（`inception/requirements-analysis/requirements.md`） | API の応答時間だけを決めており、画面の時間の目標は無い | 追加（NFR6.1）で、食い違いではない。統合の関門にせず、測って記録する |
| 初回の JavaScript の目安 500KB | 前の Intent の U1 の NFR1.5 | 目安（警告だけ） | そのまま当てる（NFR6.3）。変更は無い |
| 配信物の大きさの上限を置かない | 機能設計の 9.2 の Consequences | 「`dist` と WAR が大きくなる」を悪い点として受け入れた | 上限は置かず、測って記録する（NFR6.5）。追加 |
