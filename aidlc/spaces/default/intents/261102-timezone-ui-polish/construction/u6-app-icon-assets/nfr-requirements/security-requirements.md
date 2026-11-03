# セキュリティの要件 — u6-app-icon-assets

出典: 要件（`inception/requirements-analysis/requirements.md` の FR9・NFR1.1・NFR3.1・NFR3.2・NFR5.3）、ストーリー（`inception/user-stories/stories.md` の US4.1）、単位の定義（`inception/units-generation/unit-of-work.md` の U6）、設計の決定（`inception/domain-design/decisions.md` の ADR-008）、`team.md`（Code Style の自作の SVG・Way of Working の E2E の行・Testing Posture）、`project.md`（Forbidden の自作の SVG）、既存の検査（`frontend/scripts/check-license-header.mjs`）。

U6 は、アプリのアイコンと favicon の素材（自作の SVG、そこから作る `.ico`・`.png`）を `frontend/public/` に置き、`index.html` に favicon の `<link>` を足し、ログイン画面のアプリ名の上に飾りのアイコンを置く単位（種類 packaging。アプリには `dist` ごと入る）。新しい API・表・列・依存は足さない。そのため、この単位のセキュリティの要件は「自作の SVG に実行できるもの・外への参照を入れない」「ライセンスヘッダーを検査する」「バイナリの出どころを残す」「`/favicon.ico` を画像として返す」「飾りとして読み上げから外す」に絞る。技術の選択は `tech-stack-decisions.md`。

## 番号の付け方

inception の NFR と同じ番号は、同じ意味でだけ使う。NFR1.1（自作の SVG の安全とヘッダー）の細目は NFR1.1.1〜NFR1.1.6、NFR3.1（axe）の細目は NFR3.1.1、NFR3.2（アイコンは飾りとして外す）の細目は NFR3.2.1、NFR5.3（E2E は1本まで）の細目は NFR5.3.1、NFR2.1 は性能の目標が当たらないため、素材の大きさの上限を NFR2.1.1 に置く（`tech-stack-decisions.md`）。上流の NFR に当たる行が無い追加の要件は無い。

## 要件の一覧

| ID | 要件 | 根拠・決まり | 確かめ方 |
|---|---|---|---|
| NFR1.1.1 | 自作の SVG は、許す要素と属性の一覧（許可リスト）に載るものだけを含む。`script`・`foreignObject`・`iframe`・`object`・`embed`・`style` の要素、`on` で始まる属性（`onload` など）、`javascript:` を含む値、外部の参照を含まない。外部の参照とは、`href`・`xlink:href` の値が `#` で始まらないもの、`url(...)` が `#` で始まらないもの、`@import`、`image`・`use` による外部の読み込み、`DOCTYPE`・`ENTITY` の宣言を指す。許可リストの具体の一覧は `tech-stack-decisions.md` | `project.md` Forbidden・`team.md` Code Style・AC4.1.4 | 検査スクリプトの単体テスト（NFR1.1.5）と `./gradlew verify` の中の検査。`frontend/public/` と `frontend/src/` の下の `.svg` のすべてが対象 |
| NFR1.1.2 | 自作の SVG の先頭に、HTML と同じ `<!-- ... -->` の形の Apache License 2.0 のヘッダー（年 `2026`、著作権者名 `agwlvssainokuni`、ひな形は `config/license-header.txt`）を置く。XML 宣言（`<?xml ...?>`）は置かず、ヘッダーのコメントがファイルの最初に来る。既存のライセンスヘッダーの検査（`frontend/scripts/check-license-header.mjs`）の対象の拡張子に `.svg` を足し、ヘッダーが無い・形が違う `.svg` を失敗させる | `team.md` Code Style・`project.md` Mandated・AC4.1.4 | `check-license-header.mjs` の単体テスト（`.svg` の合格・ヘッダー無し・`/* */` の形・XML 宣言が先頭の不合格）と `./gradlew verify` |
| NFR1.1.3 | `.ico`・`.png` などのバイナリはヘッダーを置けないため、ライセンスヘッダーと SVG の内容の検査の対象の外とする。代わりに、元の SVG と作り方（使った道具の名前と版・コマンド・出力の大きさ）を README か記録に書き、元の SVG と同じ図柄から作ったことを追えるようにする。バイナリを手で編集しない。`.gitattributes` で `*.ico`・`*.png` を `binary` として扱う（`*.ico` は既にある） | `team.md` Code Style・AC4.1.6 | README か記録に手順があること（コード生成のレビューで確かめる）。バイナリの形式の確かめは NFR1.1.5 |
| NFR1.1.4 | `/favicon.ico` の要求に、HTML ではなく画像（`image/*`）を、ログインなしで返す。見つからない画面の道が返す `index.html` に落ちない。SVG の favicon（`/favicon.svg` など）も、正しい種類（`image/svg+xml`）で返す。レスポンスの種類を推測させない（既存のセキュリティの決まりの `X-Content-Type-Options: nosniff` を外さない）。サーバーに新しい道・コントローラーを足さない（静的なファイルで返す） | ADR-008・FR9.5・AC4.1.3 | 結合テスト（Spring を起動し、`TestRestTemplate` 相当で `/favicon.ico`・SVG の favicon を未認証で要求）: 状態 200・`Content-Type` が `image/` で始まる・本文が `<!doctype html` で始まらない・`nosniff` が付くこと。ビルドした `dist` に素材が入ること（NFR1.1.5） |
| NFR1.1.5 | 素材のテストを `frontend/` に置き、`./gradlew verify` で流す。（a）`public/` の SVG のすべてが NFR1.1.1・NFR1.1.2 の検査を通る（実際のファイルを読む）、（b）`.ico`・`.png` が存在し、形式の先頭の印（PNG の署名・ICO のヘッダー）と大きさの上限（NFR2.1.1）を満たす、（c）`index.html` に SVG の favicon と `.ico` か `.png` の `<link>` があり、外部の URL を指さない、（d）ログイン画面のアイコンの図柄の線（`path` の `d`）が `public/` の元の SVG と一致する。検査スクリプトの許可リストの境界（許す要素は通り、1つ足すと落ちる）を、良い例と悪い例の双方で確かめる。悪い例には `<script>`・`onload`・`href="https://..."`・`url(http://...)`・`@import`・`foreignObject`・`DOCTYPE` を含める | `team.md` Testing Posture（ALWAYS の失敗の場合のテスト・テストは英語の説明文）・`project.md` Mandated | `./gradlew verify`（Vitest）。テストの説明文は英語 |
| NFR1.1.6 | ログイン画面のアイコンは、React の部品として描き、`dangerouslySetInnerHTML` を使わない（`react/no-danger` が止める）。SVG の文字列を実行時に読み込んで差し込む形にしない。アイコンの図柄はソースに書いた固定の値だけで、利用者の入力・サーバーの応答の値を含まない。アイコンを使うコンポーネントに `href`・外部の読み込みを入れない | `team.md` Code Style（フロントエンド・セキュリティ系のルール） | oxlint（`react/no-danger`）と画面のテスト（描かれた SVG に `script`・`on*` 属性・`href` が無いこと） |
| NFR3.1.1 | ログイン画面のアクセシビリティを、アイコンを足した状態で、実際のブラウザの axe（WCAG 2.1 AA）で違反 0 件とする。ブランドカラーとテーマのすべての組で行う。誤りの状態（ロック・認証失敗の表示）と長い名前の確かめは、ログイン画面に当たる範囲で行う。axe の規則を外す・要素を `exclude` する形で通さない。favicon に表示上の確かめはなく、画面の axe の範囲外 | NFR3.1・`project.md` Forbidden（axe の規則を外さない）・Testing Posture（axe）・US5.2 | Playwright の既存のログイン画面の axe の検査の書き換え（本数に数えない）。違反 0 件を件数で Build and Test に記録 |
| NFR3.2.1 | ログイン画面のアイコンは飾りとして扱い、`aria-hidden="true"` を付け、`focusable="false"` とする。名前（`title`・`aria-label`）は付けない。アイコンだけで情報を伝えない（アプリ名は文字で伝わる）。アプリの中のアイコンの色は `currentColor` で、ブランドカラー・テーマ・高コントラストでも文字との対比（WCAG 2.1 AA の非テキストのコントラスト）を保つ | NFR3.2・AC4.1.5 | 画面のテスト: アイコンが読み上げの対象にならない（アクセシビリティツリーに出ない）こと、アプリ名が読めること。vitest-axe を1件。色の対比は実際のブラウザの axe の色のコントラストの検査で確かめる |
| NFR5.3.1 | この単位は E2E の流れを新しく足さない。ログイン画面の既存の E2E の検査に、アイコンの有無と axe の確かめを書き足す形で行う（本数に数えない）。`frontend/index.html`・`frontend/public/` は frontend の本体にあたるため、統合の前に `./gradlew e2eTest` を流して通す | NFR5.3・`team.md` Way of Working（E2E を流す条件）・Testing Posture | 統合の前の `./gradlew e2eTest` の全件の通過 |
| NFR2.1.1 | 素材の大きさの上限: 自作の SVG は1ファイル 4 KiB 以内、`.ico` は 32 KiB 以内、`.png` は 1ファイル 16 KiB 以内。ログイン画面は、アイコンのために追加の HTTP 要求を足さない（アイコンは画面の部品として描く）。favicon は `index.html` の `<link>` から1回読むだけで、ほかの画面の読み込みを遅らせない | 性能の目標（NFR2.1）は日時の変換の単位のもの。この単位は素材の肥大を防ぐ上限だけを置く | NFR1.1.5 の（b）のテスト |

## 脅威の見立て（U6 の範囲）

| 脅威 | 見立て | 対応 |
|---|---|---|
| 自作の SVG に実行できるもの（`script`・イベントの属性・`foreignObject`）が混ざる | SVG を直接開く・`<object>` や `<iframe>` で開くとスクリプトが動きうる。サーバーが自分のオリジンで返すため、混ざると同じオリジンのスクリプトとして動く | NFR1.1.1（許可リスト）・NFR1.1.5 |
| SVG が外の URL を読みに行く（追跡・外部の資源の読み込み・情報の漏えい） | `href`・`url()`・`@import`・`image`・`use`・外部の実体の宣言 | NFR1.1.1 |
| 検査を通らない形のファイルが入る（ヘッダー無し、先頭が XML 宣言） | 検査の対象の拡張子に `.svg` が無いと、見過ごされる | NFR1.1.2 |
| 素材の出どころが追えないバイナリが入る | `.ico`・`.png` は中身を検査しにくい | NFR1.1.3（元の SVG と手順）・NFR1.1.5 の（b） |
| `/favicon.ico` に HTML が返り、画像として扱われず、種類の推測に頼る | 見つからない道は `index.html` を返す見込み（K-102） | NFR1.1.4 |
| アイコンが読み上げの邪魔・意味のない案内になる | 飾りに名前を付けると、読み上げが冗長になる | NFR3.2.1 |

## この単位に当たらない inception の NFR

NFR1.2（本人だけが自分の設定を変える）は u2-display-time-zone、NFR1.3（メニューの応答の印）・NFR2.2（ホームの API）は u5-menu-icons-home、NFR3.3（タイムゾーンの選択）は u2-display-time-zone、NFR4.1（内部DB の列を足すだけ）は u2-display-time-zone、NFR4.2（メニューの応答は足すだけ）は u5-menu-icons-home、NFR5.1（日時の必須のテスト）は u3-target-db-time と u4-business-data-time、NFR5.2（テストのタイムゾーンの固定）は日時を扱う単位、NFR6.1（足す画面の文言）は u2・u4・u5 が受け持つ。U6 は文言を足さない（アイコンは飾りで、名前を持たない）。

## 上流との差（承認済みの要件・設計は書き換えず、ここに記録する）

- **検査の置き場（ADR-008 の補足）**: ADR-008 は「ライセンスヘッダーの検査の対象に `.svg` を足して SVG の安全とヘッダーを確かめる」と書く。ライセンスヘッダーの検査は先頭のコメントの有無だけを見るため、SVG の中身（スクリプト・イベントの属性・外部の参照）は見られない。そこで、ヘッダーは既存の `check-license-header.mjs` に `.svg` を足して確かめ、中身は別の検査スクリプトで確かめる（置き場と範囲は `tech-stack-decisions.md`）。検査を2つに分ける差で、ADR は書き換えない。
- **サーバーの変更なし（ADR-008 の確認）**: `/favicon.ico` を静的なファイルで返す前提は、既存の静的なファイルの配信とセキュリティの決まりで成り立つ見立て（未検証）。成り立たないとき（ログインを求める・`index.html` が返る）は、サーバーの設定の変更が要るため、NFR 設計かコード生成の計画で設計に戻して依頼者に諮る。

## Sources

- Initial description: N6（業務データの日時のタイムゾーンの変換）と画面の仕上げの Intent [desc]
- Workflow-selected scope: classic [scope]
- 要件: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/requirements-analysis/requirements.md`（FR9・NFR1.1・NFR3.1・NFR3.2・NFR5.3）
- ストーリー: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/user-stories/stories.md`（US4.1）
- 単位の定義: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/units-generation/unit-of-work.md`（U6）
- 機能設計（functional-spec）と決まり（rules）: U6 は素材と検査だけの単位で、機能設計の段を持たないため、この単位の functional-spec・rules は無い。代わりに要件（requirements）と単位の定義、ADR-008 を上流とする
- 設計の決定: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/domain-design/decisions.md`（ADR-008）
- 既存の検査: `frontend/scripts/check-license-header.mjs`
- 進め方: `aidlc/spaces/default/memory/team.md`・`project.md`

## Assumptions & Open Questions

- [assumption] 静的なファイル（`dist` のルート）は、既存のセキュリティの決まりの下でログインなしで返り、見つからない道だけが `index.html` に落ちる（K-102 は未検証）。結合テスト（NFR1.1.4）で確かめ、成り立たなければ設計に戻す。
- [assumption] `.ico`・`.png` を作る道具（SVG からの変換）は、この PC に入っている道具（例: `rsvg-convert`・ImageMagick）を使う前提。入っていない場合の代わりの道具は、コード生成の計画で決める（新しい依存は足さない）。
- [assumption] 許可リストの要素・属性は、金床とハンマーの1色の輪郭に足りる最小の一覧にする。図柄の確定で足りない要素が出たら、一覧を広げる前に依頼者に諮る。
- 未解決の点は無い（検査の置き場と許可リストは `tech-stack-decisions.md`）。
