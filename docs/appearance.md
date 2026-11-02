# 画面の見た目

インスタンスの見た目の設定（ブランドカラー・フォント）と、画面の表示の設定（言語・テーマ・文字の大きさ）、ブランドカラーのコントラストをまとめた文書です。

## インスタンスの見た目の設定

インスタンス全体のブランドカラーとフォントファミリーを設定から読み、ログインなしで読める API で画面へ渡します。画面に当てるのは「画面の表示の設定」です。

| 環境変数 | 許される値 | 既定 |
|---|---|---|
| `MASTERSMITH_APPEARANCE_BRAND_COLOR` | `blue`・`green`・`purple`・`orange` | `blue` |
| `MASTERSMITH_APPEARANCE_FONT_FAMILY` | `sans`・`serif` | `sans` |

- **値の読み方**: 大文字・小文字と前後の空白は問いません（例: ` Green ` は `green`）。無い・空・空白だけなら既定を使い、警告は出しません。
- **許されない値**: 既定を使い、アプリの起動時に項目ごとに WARN（`見た目の設定に許されない値が指定されたため、既定の値を使います`）を1件出します。起動は止めません。ログのキーは `property`（項目の名前。例 `mastersmith.appearance.brand-color`）・`defaultValue`（使った既定の値）・`allowedValues`（許される値の一覧）で、**設定された値そのものは出しません**。警告は起動時の1回だけで、要求のたびには出ません。
- **変更の当て方**: 値は起動時に1回だけ読みます。変えたら `.env` を直してアプリを起動し直します（`docker compose up -d`）。
- **API**: `GET /api/appearance` は、ログインなしで 200 と `{ "brandColor": "<名前>", "fontFamily": "<名前>" }` を返します。値は常に上の表の小文字の名前で、2項目の外は載せません。内部DB を使わないため、接続プールが尽きたときも答えます。
- **GET の外**: `GET` 以外のメソッドは公開していません。未ログイン・使えないトークンなら 401 / `AUTHENTICATION_REQUIRED`、使えるトークン付きなら 405 / `METHOD_NOT_ALLOWED`（`Allow` の見出しつき）です。`HEAD` も未ログインでは 401 です。使えないトークン（期限切れ・改ざん）を付けた `GET` も 401 になるため、画面はこの API にトークンを付けずに呼びます。
- **監査**: 読み取りは監査ログに残しません。
- **回数の制限**: 置いていません（返すのは秘密を含まない2つの名前だけ）。配備先が決まったときに前段で扱います。
- **戻すとき**: 前の版のイメージに戻しても、`.env` の2項目を消す必要はありません（前の版は読みません）。
- **コントラスト**: primary のボタンの文字は、4つのどのブランドカラーでも WCAG AA を満たします（`green`・`orange` は濃い文字。「画面の表示の設定」の「ブランドカラーのコントラスト」）。

## 画面の表示の設定

すべての画面に表示の設定（言語・テーマ・文字の大きさ）とインスタンスの見た目の設定を当てる土台は `frontend/src/app/display-settings/` にあります。

- **3つの軸と当て方**: 言語（`ja`・`en`）・テーマ（`light`・`dark`・`system`）・文字の大きさ（`sm`・`md`・`lg`）。ログインの前は、そのブラウザに最後に保存された値（無い軸は、言語がブラウザの言語設定、テーマが `system`、文字の大きさが `md`）。ログインの後は利用者の設定（ログイン・セッションの復元・トークンの更新の応答の値）で、同じブラウザに残った前の利用者の値は使いません。テーマ `system` は OS の配色に追従します（開いたままの切り替えにも）。
- **ブラウザに残す値**: localStorage の `mastersmith.display-settings`（`language`・`theme`・`fontSize` の3つだけ）と、make-you-chic-ui の鍵（`design-system-*`。`mastersmith.display-settings` の写しとして、画面を描く前に書き直す）だけです。トークン・メールアドレス・氏名は残しません。保存するのは、ログインで利用者の設定を受けたとき、プリファレンスの保存、登録の完了、ログインの画面の言語の切り替え（言語だけ）のときで、ログアウトでは消しません。
- **見た目の設定の読み方**: 画面を開いたときに1回だけ `GET /api/appearance` をセッションの復元と並べて読み、両方の答えが出るまで画面を描きません。読み取りに失敗したら前に当てた値（無ければ `blue`・`sans`）で描き、読み直しません。待ちに上限を置いていないため、この API の応答が返らない（ハング）と、ログインの画面を含むすべての画面の最初の描画が止まります（依頼者が受け入れた決定。今のセッションの復元と同じ待ち方）。
- **ログインの画面の言語の切り替え**: ログインの画面の右上に「日本語」「English」のボタンの組を置きます。選ぶと画面の言語が切り替わり、ブラウザの保存の値の言語だけを書き換えます。
- **登録の完了からの受け渡し**: 登録の完了の画面から、メールアドレスを画面の中のメモリだけで1回だけログインの画面へ渡し、案内とメールアドレスの初期値を出します（URL・ブラウザの保存に載せない）。
- **要求の言語**: すべての要求（認証の API・公開の API を含む）に、画面の言語を `Accept-Language`（`ja`・`en` だけ）として付けます。呼び出し側が指定したときは上書きしません。
- **トークンを付けないパス**: `frontend/src/shared/api-client/apiClient.ts` の `TOKENLESS_API_PATHS` の6つ（認証の API の `/api/auth/login`・`/api/auth/session/refresh`・`/api/auth/session/logout`、公開の API の `/api/appearance`・`/api/registration/verify`・`/api/registration/complete`）。問い合わせの部分を除いた完全な一致で判定し、401 でも更新と送り直しをしません。
- **機能の画面からの使い方**: `useDisplaySettings()`（`language`・`theme`・`fontSize`・`resolvedTheme`・`displayName`・`setPreview`・`clearPreview`・`applyUserPreferences`・`setLanguage`）、`saveBrowserDisplaySettings`・`saveBrowserLanguage`、`LANGUAGE_NAMES`、文言の鍵 `display.theme.*`・`display.fontSize.*`（`frontend/src/features/README.md`）。

### ブランドカラーのコントラスト

make-you-chic-ui は、文字の色を背景に合わせて出し分けます（primary のボタンの通常・hover・active の文字、選ばれたタブの文字を含む）。既知の制約（受け入れている違反）はありません。primary のボタンの文字（hover でない状態）は次のとおりです（計算の値）。

| ブランドカラー | 背景の色 | 文字の色 | コントラスト比 | WCAG AA（4.5:1） |
|---|---|---|---|---|
| `blue` | `#2563eb` | 白 `#fff` | 5.17:1 | 満たす |
| `purple` | `#9333ea` | 白 `#fff` | 5.38:1 | 満たす |
| `green` | `#16a34a` | 濃い灰 `#111827` | 5.38:1 | 満たす |
| `orange` | `#ea580c` | 濃い灰 `#111827` | 4.98:1 | 満たす |

- アバターの頭文字は `--color-primary-subtle-text`（ライトは brand-700、ダークは brand-400）、項目の誤りの文字は `--color-danger-text`（ライトは `#dc2626`、ダークは `#f87171`。ダークの背景 `#0b0f19` に対して 6.92:1）です。
- アプリ独自の CSS の文字も、同じ文字用の色を使います（100 の検査で 20 組を確かめます）。
  - プリファレンスの画面の選択のまとまりの誤り（`.preferences-choice-error`、サーバーが返したときだけ出ます）: `--color-danger-text`。
  - DSL の管理の画面の JSON Schema のリンク（`.dsl-link`）と、見つからない画面の「ホームへ」のリンク（`.page-link`）: `--color-primary-subtle-text`（計算で、ライトは 6.83:1 以上、ダークは 5.56:1 以上）。
- 選ばれたタブの文字は `--color-primary-emphasis-text`（ライトは brand-700、ダークは brand-400）で描きます。下線は `--color-primary` のままです（文字ではないため、文字のコントラストの対象外）。
- primary のボタンの hover・active の文字は `--color-primary-hover-text`（白 `#fff`。テーマとブランドカラーに依らない）で描きます。100 は、プリファレンスの画面の「保存する」にマウスを重ねた状態を検査します。050〜090 は hover でない状態を検査します。

選ばれたタブと hover の値は次のとおりです（計算の値。WCAG 2.x の相対輝度の式で、`vendor/make-you-chic-ui` の `tokens.css`・`semantic.css` の色から計算）。

| ブランドカラー | 選ばれたタブの文字（ライト、背景 `#fafafa`） | 選ばれたタブの文字（ダーク、背景 `#0b0f19`） | primary のボタンの hover（背景 brand-600 に白の文字） |
|---|---|---|---|
| `blue` | `#1e40af` 8.36:1 | `#60a5fa` 7.53:1 | `#1d4ed8` 6.70:1 |
| `purple` | `#6b21a8` 8.35:1 | `#c084fc` 7.25:1 | `#7e22ce` 6.98:1 |
| `green` | `#166534` 6.83:1 | `#4ade80` 10.99:1 | `#15803d` 5.02:1 |
| `orange` | `#9a3412` 7.00:1 | `#fb923c` 8.46:1 | `#c2410c` 5.18:1 |

- すべての組で WCAG AA（4.5:1）を満たします。primary のボタンの active（背景 brand-700 に白の文字）は、blue 8.72:1・purple 8.72:1・green 7.13:1・orange 7.31:1 です。
