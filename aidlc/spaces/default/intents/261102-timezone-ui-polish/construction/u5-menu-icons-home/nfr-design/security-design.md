# セキュリティの設計 — u5-menu-icons-home

出典: NFR 要件（`construction/u5-menu-icons-home/nfr-requirements/security-requirements.md` の NFR1.3.1〜NFR1.3.5・NFR3.1.1・NFR3.1.2・NFR3.2.1・NFR4.2.1・NFR5.3.1・NFR6.1.1、`performance-requirements.md`、`tech-stack-decisions.md`）、機能設計（`construction/u5-menu-icons-home/functional-design/functional-spec.md` の D1〜D32、`frontend-components.md`）、契約（`inception/contract-design/contract-summary.md` の C5・C7）、NFR 要件のレビューの指摘（`nfr-requirements/reviews/review-01.md` の R-01・R-04）、`team.md`（Code Style・Testing Posture の N 階層のメニュー）、`project.md`（Forbidden・Mandated）、今のコード（読み取りだけ）。

U5 が足す攻撃面は小さい。サーバーは応答の項目を 1 つ足すだけで、画面は応答の値を描くだけ。入力を受ける欄も、新しい API もない。この設計は、足す項目が情報を漏らさない作り、メニューの値を信頼できない入力として扱う作り、画面で権限を足さない作り、廃止で権限の確かめを弱めない進め方を決める。

## 1. 防御の層（どこで何を守るか）

| 層 | 守るもの | 作り |
|---|---|---|
| サーバー: 絞り（`MenuFilter`） | 見られない項目の存在を明かさない（NFR1.3.1） | 種類は**項目を作る瞬間**に決める。落とした項目は作らないので、種類も存在しない。種類を後から付ける・別の経路で足すことをしない |
| サーバー: 型（`NavNode`） | 形の決まり | 種類を持てるのは、テーブルを持つ（移れる）項目だけ（構成子で守る）。まとまり（フォルダ）は常に種類なし。種類は列挙型（`TABLE`・`VIEW`）で、ほかの値を作れない |
| サーバー: 応答（`NavNodeResponse`） | 載せる値 | 種類の名前の文字列か `null` だけ。DSL の識別・ロールの ID・権限の値・テーブルの組以外の情報を載せない。`toString` は位置の道・移れるか・子の数のまま |
| サーバー: 認証・認可 | 誰が読めるか | 今のまま（認証あり。未認証は 401）。新しい道・コントローラーを足さない |
| 画面: 読み込み（`parseNode`） | 応答の値の型 | `tableKind` は `TABLE`・`VIEW` だけ通し、ほかは `null`。足りない項目・知らない値で応答を拒否しない |
| 画面: 描画 | 実行・誘導 | 表示名は文字として描く。`icon` は許す一覧と照らす。道は 1 か所で作る。HTML を埋め込む口を使わない |
| 画面: 出し分け | 権限 | 画面は権限を足さない。判定は応答（メニュー・`admin`）に従う。管理の画面・管理の API はサーバーが守る |

## 2. 要件ごとの設計

### 2.1 NFR1.3.1: 印は見られる項目にだけ付く

- `MenuFilter.filterItem` は、移れる項目を作る分岐（`visible` が真）の中でだけ種類の操作を呼び、結果を `NavNode` に渡す。落とす分岐では呼ばない。
- 種類が `null` になるのは、フォルダ、または操作が `null` を返したとき。後者はサービスの経路では起こらない（`performance-design.md` 1.2。メニューの木と種類の操作は同じモデルから作るため）。
- **確かめの置き場（NFR 要件のレビューの R-01 を直した形）**:
  - `MenuFilterTest`・`MenuFilterPropertyTest`（単体・性質ベース）: 種類は移れる項目にだけ付く／移れない項目は常に `null`／権限を足しても見えている項目の種類は変わらない／操作が `null` を返しても項目は落ちず種類だけ `null`。
  - `NavigationApiIT`（結合）: テーブル → `TABLE`、ビュー → `VIEW`、フォルダ → `null`、見られない項目が応答に出ない。
  - `NavigationDslSwapIT`（既存の `menuWithRemovedTable` に確かめを足す）: DSL の差し替えの途中でも、残った項目の種類が差し替え前のモデルのテーブルの種類と同じ（木と種類がずれない）。
  - `NavigationAuthorizationApiIT`（既存・変更なし）: 未認証は 401。
  - `NavigationMenuAccessConsistencyIT`（既存）: 見られない項目が応答にも種類にも出ない。

### 2.2 NFR1.3.2: ログ・トレースに値を出さない

- `NavNode`・`NavNodeResponse` の `toString` は変えない（`tableKind` を足しても、文字列には出さない。種類は値の名前を含まないため出しても漏えいではないが、形を変えないほうが、既存の `NavigationRecordsToStringTest` の保証を保てる）。
- 応答を作る処理にログの出力を足さない。DSL の置き換え（許す一覧に無い `icon` → `list`）も今までどおりログを出さない。
- 確かめ: `NavigationRecordsToStringTest`（形が変わらない確かめを足す）、`NavigationSecretLeakIT`（TRACE を有効にしても、表示名・テーブルの名前が出ない）。

### 2.3 NFR1.3.3: メニューの値は信頼できない入力

| 値 | 扱い | 設計 |
|---|---|---|
| 表示名（`label`） | 文字として描く | React の子（文字）として渡す。`dangerouslySetInnerHTML`・文字列からの HTML の組み立てを使わない（`react/no-danger` が止める）。ホームのカードの名前・サイドバーの項目の名前・作業ロールの 1 行はすべて文字。`i18next` の補間は `escapeValue: false`（React が描くため）だが、補間の結果を `Trans` で HTML として解釈する形・`dangerouslySetInnerHTML` を使わないことを、画面の部品のテストで確かめる |
| `icon` | 許す一覧と照らす | `resolveItemIcon` が `isAllowedIcon`（24 個の一覧）で判定する。一覧に無い・`list`・空・型の違う値は種類の既定に落とす。`icon` の値から道・属性・クラス名を作らない。`Icon` の `name` に渡すのは一覧にある `IconName` だけ |
| `tableKind` | `TABLE`・`VIEW` 以外は `null` | `parseNode` が決め、その後は列挙の値だけを持つ |
| カードの道 | アプリの中の道だけ | 業務のカード: `toTablePlaceholderPath`（先頭は常に `/tables?`）の 1 か所。管理のカード: 登録済みの道（`registration.ts` の定数）。`buildHomeCards` は、作った道が `/` で始まり `//` で始まらないことを確かめ、外れたカードは出さない（`Link` の `to` に外部の URL・`javascript:` を渡す口を作らない）。業務の項目の `table` の組（スキーマ名・テーブル名）は `URLSearchParams` でエンコードされる |

確かめ: `HomePage`・`HomeCard`・`ShellLayout` の部品のテスト（表示名に `<script>`・`<img onerror>`・`"`・`'`・`&` を含む項目が文字として出る／`icon` に `javascript:`・`https://x`・大文字違い・空・知らない名前を入れても既定に落ち、道が作られない）、`parseNode` の単体テスト（知らない `tableKind` → `null`）、`fast-check` の性質（任意の文字列の表示名・`icon` で、描画した結果の要素の数が決まった形から増えない／カードの道が許す形から外れない）。サーバー側は既存の `NavigationUntrustedInputIT` が通る。

### 2.4 NFR1.3.4: 画面で権限を足さない

- 業務のカードの元は、サーバーが絞った応答の最上位の項目だけ。応答に無い項目のカードを作る入力が存在しない。
- 管理のカードの元は、`selectAdminSidebarItems(registrations, loginState)`。`loginState.admin` はサーバーの応答から来た値で、画面で `true` にする経路を足さない。管理者でなければ空配列で、見出しごと出さない。
- 管理の道・管理の API の権限の判定は、サーバーの `AdminPaths`・認可の仕組みのまま。画面でカードを隠すこと・出すことは見せ方で、押した先で権限が無ければ今のとおり 403 の表示（登録された管理の道）になる。
- 確かめ: `buildHomeCards` の性質ベースのテスト（応答に無い項目のカードが出ない／管理者でなければ管理のカードが空）。サーバーの既存のテスト（`AdminPathBoundaryIT`・`AdminAuthorizationManagerTest`・`AdminAccessDeniedHandlerTest`・`AdminAuthenticationEntryPointTest`・`AdminAccessIT`）が変更なしで通る。

### 2.5 NFR1.3.5: 仮の画面の廃止で確かめを弱めない

進め方（順序が大事）:

1. 先に、仮の画面に頼っていた検査の移し先を作る（E2E 130 は DSL の管理の画面へ、030 はホームの管理のカード経由へ。単体テストはホーム・サイドバー・`decideRoute` へ）。
2. 次に、`features/admin/` と登録の読み込みを外す。
3. 最後に、`/admin` が「見つからない」になることを `decideRoute`・`AppRouter` のテストで確かめる。
4. サーバー側の `GET /api/admin/check` と 403 の判定は触れない。k6 の場面と E2E の API の直接の確かめがこの API を使い続ける。

`/admin` の振る舞い（レビュー役の機能設計の指摘 D24 を反映）:

| 利用者 | `/admin` を開いたとき |
|---|---|
| 未ログイン | ログインの画面へ送られる（`decideRoute` は未ログインを先に振り分ける。今のまま） |
| ログイン済み（管理者・そうでない人とも） | 「見つからない」画面（シェルの中）。管理者でない人にも、存在や権限の有無を明かさない |

登録された管理の道（`/admin/users` など）を管理者でない人が開いたときの「権限が無い」の表示と、管理の API の 403 を拾う `AdminForbiddenProvider` の仕組みは変えない。`/admin` の確かめを書き換えるテスト（E2E 090 の管理者でない利用者の確かめ）は、実在する管理の道 1 つで 403 の表示を確かめ、`/admin` は「見つからない」を確かめる形にする。

移す前と後の件数は Build and Test で記録する。「画面の確認 API の呼び出し」の単体テスト（`adminApi.test.ts`・`AdminAreaPage.test.tsx` の一部）は、呼ぶ画面が無くなるため移さず外す。これは外す件数として別に数える。

### 2.6 NFR3.1.1・NFR3.1.2・NFR3.2.1: アクセシビリティ

- カードは `Link`（`<a>`）の中に `Card` を置く。`Card` は `<div>` で、`<a>` の中に置いてよい（フローコンテンツ）。`Link` の中にボタン・入力などの操作部品を置かない（入れ子の操作の違反を避ける）。
- アイコンは `aria-hidden`、名前は文字。リンクの名前は表示名の文字だけで足りる（アイコンが名前に混ざらない）。
- 見出しは h1 → h2。カードの並びは `ul`・`li` で、`section` の `aria-labelledby` を見出しに結ぶ。
- フォーカスの輪は `:focus-visible` で、make-you-chic-ui のトークンの色。色の対比は実際のブラウザの axe の色のコントラストで確かめる（ブランドカラーとテーマのすべての組）。
- 長い名前は `overflow-wrap: anywhere`、格子は折り返し、画面全体の横のスクロールを出さない（375 px を含む）。ロゴは固定の大きさで上のバーに収める。
- 確かめ: `vitest-axe` を部品ごとに 1 件、E2E の axe（E2E 160 の拡張）と、`support/overflow.ts` の測り。

### 2.7 NFR4.2.1・NFR6.1.1・NFR5.3.1

- 互換: 応答は足すだけ。画面は `tableKind` 無しの応答を読んでも壊れない（`null`）。古い画面が新しい応答を読んでも壊れない（知らない項目を無視）。確かめ: `NavigationApiIT` で既存の項目の値が変わらない／`parseNode` の 5 通り（項目なし・`null`・`TABLE`・`VIEW`・知らない値）。
- 文言: ホームの新しい文言は `ja.ts`・`en.ts` の両方に足し、`messages.test.ts` が鍵の対応を確かめる。作業ロールの名前は補間で文字のまま渡す。
- E2E: 新しい流れは足さない。書き換え・移し（030・090・130・160）は本数に数えない。`frontend/src` の本体と `navigation` の `src/main` に触れるため、統合の前に `./gradlew e2eTest` を流す。

## 3. 脅威と対応

| 脅威 | 対応する設計 |
|---|---|
| 種類の有無で見られない項目の存在を推測される | 2.1（項目を作る瞬間にだけ付ける。落とした項目は存在しない） |
| 悪意のある表示名・`icon` が実行される・外へ誘導する | 2.3（文字として描く・一覧と照らす・道を 1 か所で作る・アプリの中の形かを確かめる） |
| ホームの出し分けの誤りで権限の無い人が管理を使える | 2.4（画面は権限を足さない。押した先はサーバーが 403） |
| 廃止で権限の確かめの検査が失われる | 2.5（移し先を先に作る。サーバー側は触れない） |
| ログに項目の値が出る | 2.2（`toString` の形を変えない・ログを足さない） |
| 古い画面と新しい応答の食い違い | 2.7（足すだけ・`null` として読む） |

## Sources

- Initial description: N6（業務データの日時のタイムゾーンの変換）と画面の仕上げの Intent [desc]
- Workflow-selected scope: classic [scope]
- NFR 要件: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u5-menu-icons-home/nfr-requirements/security-requirements.md`・`performance-requirements.md`・`tech-stack-decisions.md`・`reviews/review-01.md`
- 機能設計: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u5-menu-icons-home/functional-design/functional-spec.md`・`frontend-components.md`・`reviews/review-01.md`
- 契約: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/contract-design/contract-summary.md`（C5・C7）
- 今のコード（読み取りだけ）: `backend/src/main/java/cherry/mastersmith/navigation/`、`frontend/src/app/navigation/tablePlaceholderPath.ts`・`frontend/src/app/i18n/i18n.ts`・`frontend/src/app/routing/decideRoute.ts`
- 進め方: `aidlc/spaces/default/memory/team.md`・`project.md`

## Assumptions & Open Questions

- [assumption] `buildHomeCards` が道の形（`/` で始まり `//` で始まらない）を確かめる小さな関数は、`toTablePlaceholderPath` と登録の道が作る形をすでに満たす防御の二重化で、置き場（`homeCards.ts`）はコード生成の計画で確定する。
- [assumption] E2E 090 の管理者でない利用者の確かめは、実在する管理の道（`/admin/invitations` など）が残るため、書き換えで足りる。
- 未解決の点: 無い。
