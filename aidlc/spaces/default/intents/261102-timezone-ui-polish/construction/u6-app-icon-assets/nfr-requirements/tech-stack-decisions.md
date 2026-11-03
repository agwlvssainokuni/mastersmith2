# 技術の選択と、数の目標 — u6-app-icon-assets

出典: 要件（`inception/requirements-analysis/requirements.md` の FR9・NFR1.1・NFR2.1・NFR3.2）、設計の決定（`inception/domain-design/decisions.md` の ADR-008）、単位の定義（`inception/units-generation/unit-of-work.md` の U6）、`team.md`（Code Style・Deployment）、既存のコード（`frontend/scripts/check-license-header.mjs`・`frontend/index.html`、読み取りだけ。`frontend/public/` は今は無い）。セキュリティの要件は `security-requirements.md`。

U6 は素材の単位（種類 packaging）。性能・拡張・信頼性・観測の独立した文書は作らない（新しい経路・状態・負荷が無い）。数の目標は素材の大きさの上限だけで、2節に置く。

## 1. 技術の選択

| 項目 | 選択 | 理由 | 検討した別の案 |
|---|---|---|---|
| 素材の置き場と返し方 | `frontend/public/` に自作の SVG・`.ico`・`.png` を置く。Vite がビルドで `dist` のルートへそのまま複写し、実行可能 WAR に入る。`index.html` に SVG の favicon と `.ico`（または `.png`）の `<link>` を足す。`/favicon.ico` はサーバーの新しい道を作らず、静的なファイルで返す | ADR-008 のとおり。サーバーの変更が増えず、ブラウザが既定で読む `/favicon.ico` にも効く | サーバーに `/favicon.ico` の道を作る（変更が増える）。データ URI で `index.html` に埋める（既定の要求に効かない） |
| ライセンスヘッダーの検査 | 既存の `check-license-header.mjs` の `HTML_EXTENSIONS`（HTML コメント形）に `.svg` を足す。期待するヘッダーは HTML と同じ形（`<!--` で始まる）。`buildExpectedHeaders`・`checkContent` の純粋な関数に足し、単体テストで確かめる | 既に `.html` で同じ形を検査している。`team.md` が「`.svg` を足す」と書いている | 別のスクリプトでヘッダーも見る（二重の検査の手間） |
| SVG の中身の検査 | 新しい純粋な関数の検査スクリプト `frontend/scripts/check-svg-safety.mjs`（`checkSvgContent(content)` を export）を足す。`package.json` に `svg:check` を足し、`license:check` と同じ Gradle のタスクの中から呼ぶ（呼び方の具体はコード生成の計画で確かめる）。依存を足さず、文字の走査と正規表現で、許可リストに載らない要素・属性・外部の参照を数える。XML の完全な解釈（パーサー）は使わず、`<` から `>` までの開きタグを字句として切り出す | 新しい依存（SVG・XML の解析器）を足さずに、許可リストで「入れない」を保証できる。SVG は自作の小さな1色の図で、字句の走査で足りる。純粋な関数にすると、良い例・悪い例の単体テストと `fast-check` の性質（許可リストの外の要素を1つ足すと必ず落ちる）で確かめられる | XML パーサーを足す（依存・ライセンス・脆弱性検査の対象が増える）。禁止リスト方式（見落としが出る） |
| 許可リストの一覧 | 要素: `svg`・`g`・`path`・`circle`・`ellipse`・`rect`・`line`・`polyline`・`polygon`・`title`・`desc`・`defs`・`clipPath`（`defs` と `clipPath` は `#` の参照だけ）。属性: `xmlns`（値は `http://www.w3.org/2000/svg` のみ）・`viewBox`・`width`・`height`・`fill`・`stroke`・`stroke-width`・`stroke-linecap`・`stroke-linejoin`・`d`・`points`・`cx`・`cy`・`r`・`rx`・`ry`・`x`・`y`・`x1`・`y1`・`x2`・`y2`・`transform`・`opacity`・`fill-rule`・`clip-rule`・`clip-path`・`id`・`role`・`aria-hidden`・`focusable`。`href`・`xlink:href` は値が `#` で始まるときだけ。`fill`・`stroke` などの値に `url(` を使うときは `url(#...)` だけ。`style` 属性と `class` は使わない。図柄が金床とハンマーの1色の輪郭で足りるため、最小に絞る | 必要なものだけを許し、広げるときは検査と単体テストと一緒に変える | なし |
| 色 | 自作の SVG のアプリの中の使い方（ログイン画面）は `currentColor`。favicon の SVG は固定のブランドの色（1色）。favicon の SVG は、ブランドカラーが利用者ごとに変わる設定でも固定の色のまま（`index.html` は利用者の設定を知らないため）。ダークの favicon の対比は、1色が明暗どちらの背景にも見える色を選ぶか、`prefers-color-scheme` の `<style>` を使わず（`style` は許可リストの外）、`.png`・`.ico` と同じ1色にする | `style` 要素を許さないため、SVG の中で配色の切り替えをしない。切り替えが要る場合は図柄の確定で依頼者に諮る | favicon に `<style>` と `prefers-color-scheme` を使う（`style` を許可リストに入れる必要があり、検査が広がる） |
| ログイン画面のアイコン | React の部品（`frontend/src/features/auth/` のログイン画面の中、または複数画面で使うなら `frontend/src/shared/` の部品）。JSX の `<svg>` と固定の `path` を書く。`aria-hidden="true"`・`focusable="false"`・`fill="currentColor"`（または `stroke="currentColor"`）。`dangerouslySetInnerHTML` を使わない。図柄の線（`d`）は `public/` の元の SVG と同じ値とし、テストで一致を確かめる（NFR1.1.5 の（d）） | `currentColor` はインラインの SVG でしか効かない。`<img src>` は色を継げない。ログイン画面は新しい HTTP 要求を足さずに済む。上のバーのロゴ（U5 が使う口）も同じ部品を渡せる | `<img src="/app-icon.svg">`（`currentColor` が効かず、テーマに合わない）。SVG を文字列として読み込んで差し込む（`react/no-danger` に当たる） |
| `.ico`・`.png` の作り方 | 元の SVG から、この PC に入っている変換の道具（例: `rsvg-convert` で PNG、ImageMagick で ICO）で作り、結果のバイナリをコミットする。新しい依存（npm・Gradle）は足さない。道具の名前・版・コマンド・出力の大きさ（`.ico` は 16・32・48 px を含む、`.png` は 180 px の Apple 用と 192・512 px のうち採るもの）を README か記録に書く。再生成は手動で、ビルドに組み込まない | npm の画像の道具（`sharp` など）は `hasInstallScript` を持ち、`frontend/.npmrc` の `ignore-scripts=true` と食い違う。生成物は小さく変わらないため、手順の記録で足りる | ビルドの度に生成する（依存・スクリプトの実行が要る）。手で描き直す（元の SVG と図柄がずれる） |
| テスト | Vitest（検査スクリプトの単体テスト・素材のファイルのテスト・ログイン画面のアイコンのテスト）、`fast-check`（許可リストの性質）、Testing Library と vitest-axe（ログイン画面のアイコン、1件）、Spring の結合テスト（`/favicon.ico` と SVG の favicon の返り方）、Playwright（既存のログインの E2E の書き換えと axe。新しい流れは足さない）。テストの説明文は英語 | 既存と同じ（`team.md` Testing Posture）。新しい依存なし | なし |
| 配備 | 同じ実行可能 WAR に入る（埋め込み）。別には配備しない。実行時の設定（タイムゾーンなど）は変えない。ブラウザは favicon を強くキャッシュするため、図柄を変えたときの見え方は、配備の後のスモークテストで確かめる（キャッシュの手当ては、既存の `CacheControlFilter` の決まりのままで足す必要があるかは、コード生成の計画で確かめる） | `unit-of-work.md` の配備の形 | なし |

## 2. 数の目標（素材の大きさ。NFR2.1.1 に当たる細目）

| ID | 目標 | 測り方 | 根拠 |
|---|---|---|---|
| NFR2.1.1 | 自作の SVG は1ファイル 4 KiB 以内、`.ico` は 32 KiB 以内、`.png` は1ファイル 16 KiB 以内。ログイン画面はアイコンのための追加の HTTP 要求を足さない（部品として描く） | 素材のテスト（security-requirements.md の NFR1.1.5 の（b））でファイルの大きさを測る。ログイン画面の要求の数は、既存の E2E か画面のテストでアイコンを足した前後で増えないことを確かめる | 小さな1色の図で足りる大きさ。肥大の防止（性能の物差しではなく、素材の上限） |

この上限は、この Intent で初めて置く（前の物差しは無い）。外れたときは、図柄を簡単にするか、原因を確かめてから依頼者に諮る。

## 3. 新しい依存

なし。SVG の検査は標準の Node の機能と正規表現で書き、画像の変換は手元の道具の手順の記録だけにする。足す場合は、採用の前にライセンスを確かめ、Apache License 2.0 と異なるものは採用の理由を設計の記録に残し、`frontend/package-lock.json` と OSV-Scanner の対象に入れる。npm の道具はパッケージのスクリプトを動かさない（`ignore-scripts=true`）ため、スクリプトを要するものは採らない（`team.md` Code Style）。

## 4. 検査の置き場と範囲の境目

| 対象 | 検査 | 置き場 | 範囲の外 |
|---|---|---|---|
| `.svg` のライセンスヘッダー | 先頭の `<!-- ... -->` の形と中身がひな形（`config/license-header.txt`）と一致する。XML 宣言が先頭にあれば不合格 | `frontend/scripts/check-license-header.mjs`（`.svg` を `HTML_EXTENSIONS` に足す）。`frontend/` の下のすべての `.svg`（`public/`・`src/`）。`node_modules`・`dist`・`vendor/` は対象外（既存の `SKIP_DIRS`） | `.ico`・`.png` などのバイナリ |
| `.svg` の中身 | 許可リストの要素・属性、外部の参照、実行できるものの有無 | `frontend/scripts/check-svg-safety.mjs`（`public/`・`src/` の下の `.svg`） | バイナリ。`vendor/make-you-chic-ui` の SVG（このリポジトリが直接変更しない） |
| バイナリの形式と大きさ | PNG の署名・ICO のヘッダー・大きさの上限・存在 | Vitest の素材のテスト（`frontend/src/` の対象と同じ場所、または `frontend/scripts/` の隣） | 中身の図柄が元の SVG と一致するか（手順の記録で追う） |
| `index.html` の `<link>` | favicon の SVG と `.ico`（または `.png`）の指定があり、外部の URL を指さない | 同上のテスト | なし |
| `/favicon.ico` の返り方 | 状態 200・`image/*`・HTML でない・未認証で読める | Spring の結合テスト（`backend/src/test/java` の既存の静的なファイルまたは SPA のフォールバックのテストの近く） | ブラウザの実際のキャッシュの挙動 |
| ログイン画面のアイコン | 飾り（`aria-hidden`）・`script` 等が無い・図柄が元の SVG と一致・axe 違反 0 件 | 画面のテスト（Vitest）と既存の E2E の書き換え | 上のバーのロゴ（U5。取り込み後） |

## Sources

- Initial description: N6（業務データの日時のタイムゾーンの変換）と画面の仕上げの Intent [desc]
- Workflow-selected scope: classic [scope]
- 要件: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/requirements-analysis/requirements.md`
- 設計の決定: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/domain-design/decisions.md`（ADR-008）
- 単位の定義: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/units-generation/unit-of-work.md`
- 既存の検査: `frontend/scripts/check-license-header.mjs`・`frontend/index.html`
- 進め方: `aidlc/spaces/default/memory/team.md`・`project.md`

## Assumptions & Open Questions

- [assumption] 変換の道具（`rsvg-convert`・ImageMagick など）がこの PC にあるかは未確認。無い場合の代わり（別の道具、または別の PC での作成）はコード生成の計画で決め、新しい依存は足さない。
- [assumption] `check-svg-safety.mjs` を `license:check` と同じ Gradle のタスクの中から呼べる（呼び方は `license:check` の現状に倣う）。コード生成の計画で、`./gradlew verify` と CI の両方で動くことを確かめる。
- [assumption] favicon の SVG は固定の1色。ダークテーマでの見え方の切り替えが要るかは、図柄の確定（画面イメージの案）で決める。要るときは許可リストを広げる前に依頼者に諮る。
- [assumption] ブラウザの favicon のキャッシュは強い。配備の後に図柄が更新されて見えない場合の手当て（ファイル名の変更など）は、スモークテストで見つかったときに決める。
- 未解決の点は無い。
