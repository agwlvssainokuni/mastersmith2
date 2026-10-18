# セキュリティの設計 — U3 u3-businessdata-screens

U3 は種類 `ui` の単位で、段の定義により performance・security・logical-components・traceability だけを作る。そのため、この文書にセキュリティ（NFR4）・アクセシビリティ（NFR5）・言語（NFR6）・テスト（NFR7）・運用（NFR8）の設計を、節に分けて書く。U3 は SQL も対象DB の接続も持たないが、画面で主キー・行の値・検索の値を扱い、E2E では対象DB の資格情報を使う。

ID は単位の接頭辞を付けて指す（例 `U3-NFR4.22`）。

## 捨ての試しの結果（要点）

試しの全体は `performance-design.md` の冒頭にある。セキュリティとアクセシビリティに関わるのは次の2つ。

- **T1・T3（Tooltip）**
  - make-you-chic-ui の固定先 `a9309c4` の dist で試した。
  - セルにポインターを重ねると、Tooltip に値の全体（100 字）が文字として出た。
  - Tooltip は `aria-describedby` を常に付け、中身は表示のときだけ portal で作る（`vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/Tooltip/Tooltip.tsx` 92行）。
  - 設計への意味: 値は Tooltip の中でも文字として出る。読み上げは、DOM の文字（切らない値の全体）で読む。
- **T4（詳細）**: 10,000 字の値を文字のまま描き、見出しにフォーカスを移せた（15 ms）。開いたときのフォーカスの移動（D18）に、性能の妨げは無い。

## 冒頭の対応表

| U3 の NFR 要件の ID | この文書の節 | 扱い |
|---|---|---|
| NFR4.1 SQL の組み立て | — | N/A（U3 は SQL を作らない） |
| NFR4.2 値の漏えいの防止 | 1節 | 部分（画面の側）。ページの題・例外の文・知らせの経路を足した |
| NFR4.3 書き込みの接続の分離 | — | N/A（U3 は対象DB の接続を持たない） |
| NFR4.4 問い合わせの時間の上限 | — | N/A（サーバーの側。画面は `performance-design.md` の 2節） |
| NFR4.20 文字として描く・リンクの先 | 2節 | そのまま |
| NFR4.21 行の操作の `data-testid` | 7節 | 包みの span に置く |
| NFR4.22 記憶の寿命と上限 | 3節 | 消す時の知らせの受け方（Q2: A） |
| NFR4.23 本文の大きさと値の長さ | 4節 | 実際に送るバイト数で確かめる |
| NFR4.24 応答の形の確かめ | 5節 | 書き込みの 2xx の扱い（Q3: A） |
| NFR4.25 E2E の資格情報 | 8節 | 値の目印の種類を足す |
| NFR4.26 画面の出し分け | 2節 | そのまま |
| NFR5.6〜5.10 アクセシビリティ | 7節 | 185 の組み立て（Q4: A）、4.14 の試しの記録 |
| NFR6.3・6.4 言語 | 6節 | 文の表を具体にした |
| NFR7.9〜7.13 テスト | 9節 | 旗のテスト（R-12）を含む |
| NFR8.7 403 の後に送り直さない | 6節 | 部分（しきい値は Observability Setup） |

## 1. 値の漏えいの経路（NFR4.2、部分）

| 経路 | 手当て |
|---|---|
| 道（URL） | 置くのは `schema`・`table`・`item` だけ |
| 履歴の状態 | 置くのは `{ rowToken }` だけ。印は `crypto.getRandomValues` の 16 バイト（16 進）で作り、主キーから作らない |
| ブラウザの保存 | localStorage・sessionStorage・IndexedDB を使わない |
| DOM の `id`・`data-testid` | 置くのはページの中の番号（`r<n>`）だけ |
| `console` | 呼ばない。コードの検索で確かめる |
| ページの題 | `document.title` に行のラベルを入れない（ブラウザの履歴のデータに残るため） |
| 画面に出る例外の文 | 行の値を含む `Error` を作らない（react-router の既定の誤りの画面は、例外の文を出すため）。写しの関数は、形が違えば `undefined` を返し、例外を投げない |
| Toast・`aria-live` | 件数と決まった文だけを入れ、値を入れない |
| E2E の記録 | 8節 |

サーバーの側の漏えい（ログ・トレース・エラー応答）は、U1・U2 の `*SecretLeakIT` が受け持つ（部分）。

## 2. 描き方と出し分け（NFR4.20・NFR4.26）

- 値と表示名は文字として描き、`dangerouslySetInnerHTML` を使わない（oxlint の `react/no-danger`）。Tooltip の中身も文字だけにする。
- リンクの先（`href`）は、道を作る1か所の関数（`frontend/src/app/navigation/tablePlaceholderPath.ts` を広げたもの）の結果だけにする。値から `href` を作らない。
- ボタンと行の操作は、定義の `operations` に従って出すだけにする。判定はサーバーが行う（`aidlc/spaces/default/memory/project.md` の Mandated）。
- 権限の無いテーブルへ直接移ったときは、403 の表示にする（機能設計の 6.1）。

## 3. 記憶（NFR4.22、Q2: A）

**作り**
- **印の表**: `Map<印, 主キー>`。作った順に最大 100 件を持ち、101 件目を置くときに最も古いものを捨てる。読んでも消さず、順も変えない（ブラウザの戻る操作で詳細へ戻れるようにするため）。
- **テーブルごとの表**: `Map<テーブルの鍵, { definition, listState }>`。最大 100 テーブルを持ち、使った順に古いものから捨てる。定義もこの表で上限を持つ。
  - テーブルの鍵は、スキーマ名とテーブル名の組から作る。
- 記憶は画面の外のモジュールの変数だけに持ち、ブラウザの保存に写さない。

**全部を消す時**
- **ログイン状態の切り替わり**: ログイン⇄未ログインの切り替わりで、すぐ全部消す。
  - 骨組みの `frontend/src/app/login-state/` に「ログインの範囲が変わった」の知らせを足し、LoginStateGate が切り替わりのたびに知らせる。
  - 記憶のモジュールは、読み込まれた時に購読する（読み込まれる前の記憶は空のため、取りこぼしは無い）。
  - この知らせで、ログアウト・トークンの更新の失敗（`onUnauthenticated` → 未ログイン）・新しいログインを覆う。
- **作業ロールの切り替え**: 作業ロールの roleId が変わったら、記憶を読む・書く口の入口で全部消す（R-03）。詳細・更新の画面は、印が無くなるため一覧へ移る。
- **既知の制約**: 別のタブで別の利用者がログインし、このタブのトークンの更新がその利用者に替わる場合は、ログイン状態が切り替わらないため消えない。
  - `LoginState` は利用者を見分ける値を持たず（`frontend/src/app/registry/types.ts` 104〜116行）、`authSession.refresh()` は未ログインを通らずに利用者を置き換える（`frontend/src/features/auth/authSession.ts` 93〜99行）。
  - サーバーが要求のたびに判定するため、残るのは、前の利用者が入れた検索の条件と主キーの印だけである。

記憶の口の形（説明用の断片。名前はコード生成で確定）:

```ts
/** 主キーは印で引く。読んでも消さない。作った順に最大 100 件 */
export function putRowKey(key: Record<string, string>): string
export function peekRowKey(token: string): Record<string, string> | undefined
/** テーブルごとの定義と一覧の状態。使った順に最大 100 テーブル */
export function tableEntry(ref: TableRef, roleId: number | null): TableEntry
/** 全部消す（ログインの範囲の知らせ、作業ロールの変化） */
export function clearBusinessDataStore(): void
```

## 4. 本文の大きさと値の長さ（NFR4.23）

- **値の長さ**: コードポイントで数える。
  - 上限: 検索の値と主キーは 1,000 字（U1 の L6）、書き込みの値は 10,000 字（DSL・DB の長さが短ければそちら。U2 の security-design 6節）。
  - 数えるのは `frontend/src/shared/validation/codePoints.ts` の `countCodePoints`。組になっていないサロゲートも1つと数え、Java の `String.codePointCount` と同じ数え方になる（R-04）。
  - 確かめる時: 入力から離れたときと、送るとき。
- **本文の大きさ**: `JSON.stringify` を1回だけ行い、その文字列のバイト数を `utf8ByteLength` で測ってから、同じ文字列を送る。
  - そのため、制御の文字のエスケープ（`\u0001` の 6 バイト。U2 の W10）と、組になっていないサロゲートのエスケープも、実際に送るバイト数で数える。
  - 上限: 読み取り・削除は 64 KB、作成・更新は 4 MiB（4,194,304 バイト）。超えたら送らずに、413 と同じ文を出す。
- 上限の値は機能の中の1か所の定数に置き、U1・U2 の定数の名前を説明文に書く。判定はサーバーを正とする。
- **確かめるテスト**: 上限ちょうどは送ること、+1 字・+1 バイトは送らないこと。サロゲートの組と、組になっていないサロゲートも含める。

## 5. 応答の形の確かめと、書き込みの結果（NFR4.24、Q3: A）

**形の確かめ**
- 応答の本文は `unknown` で受け、型ごとの写しの関数で確かめてから写す。
- 列挙の値は、すべて既知の一覧と照らす。対象は `ValueType`・`Operation`・`listFormat`・`formPart`（`lookup` を除く）・`restrictionReason`・`operator`。
- 次の場合は形の崩れにする（機能設計の承認の場の決定）。
  - 入力にするカラムで、`formPart`・`nullable`・`required` のどれかが欠けている
  - `lookup` か、知らない列挙の値が来た
- 写しの関数の性質ベースのテストでは、任意の JSON の入力で、例外を投げずに `undefined` か正しい型を返すことを確かめる。

**書き込みの結果の出し分け**

| 結果 | 表示 | 次の行き先 |
|---|---|---|
| 応答が届かない（通信の失敗）・500・503 `BUSINESS_TARGET_DB_TIMEOUT` | 「保存されたか分かりません」（削除は「削除されたか分かりません」） | 確かめる入口: 作成は一覧で探す、更新は開き直す、削除は一覧を読み直す |
| 2xx だが本文の形が崩れている | 保存できたものとして「保存しました」の Toast（`key` は使わない） | 一覧へ戻る |
| 503 `BUSINESS_TARGET_DB_UNAVAILABLE`・409・400・413 | 機能設計の 6.1 のとおり、「保存されていません」の側 | 6.1 のとおり |

- どの場合も、自動では送り直さない。
- 今の `apiRequest` は、送った後に応答が届かなかった場合も `networkError()` にする（`frontend/src/shared/api-client/apiClient.ts` 161行）。そのため、書き込みの呼び出しだけは、通信の失敗を「分からない」の側に分ける。

## 6. 文の選び方（NFR6.3・NFR6.4・NFR8.7）

- 文は、code と reason だけで選ぶ。日本語と英語を対で置く。
- **503**
  - 読み取り: 2つの code に同じ文を出す（「業務データのデータベースとつながりません。時間をおいてやり直してください。」）。
  - 書き込み: `UNAVAILABLE` は「保存されていません。時間をおいてやり直してください。」（削除は「削除されていません」）、`TIMEOUT` は5節の「分かりません」。
- **409 `BUSINESS_SAVE_REJECTED`**
  - fieldErrors が無いとき: 「保存できませんでした（保存はされていません）。入力を確かめてやり直し、続くときは管理者に確かめてください。」 DB が拒んだ場合と、U2 のレビューの R-04 の形（確かめの段の時間切れが 409 に入る場合）の両方に当てはまる文にした。
  - 削除: 承認済みの「ほかのデータから使われているため削除できません。」のまま（U2 で、削除の SAVE_REJECTED は外部キーの違反だけのため）。
  - `NOT_EDITABLE_NOT_NULL`: 6.1 のとおり、利用者には直せない旨を出す。
- **413**: 「入力が大きすぎるため送れませんでした。値を短くしてください。」 入力は消さない。画面で先に止めたとき（4節）も同じ文にする。
- **400 で画面に結べる fieldErrors が無いとき**（U1 の page の上限、U1 のレビューの R-01 の読む側の誤り）: 一覧の上に「条件か表示の範囲を読めませんでした。条件を見直してやり直してください。」を出し、前の表を残す。
- **知らない code**: 状態コードの区分（4xx・5xx）ごとの決まった文に写す。書き込みの 5xx は5節に従う。
- **保存の 403 の別の文**（`tables.problem.writeForbidden`。NFR6.4）: コード生成の計画（B6）で足すかを決める。
- **403 の後**: 業務データの API を送り直さない（NFR8.7、部分）。しきい値の見直しは Observability Setup が受け持つ。

## 7. アクセシビリティ（NFR5.6〜5.10、NFR4.21）

**行の操作と行のリンクの印**
- 行の操作の `data-testid` は、Dropdown を包む span に `tables-row-actions-r<n>` で置く。Dropdown はトリガーの `data-testid` を `dropdown-trigger` に上書きするため、トリガーには置けない（`vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/Dropdown/Dropdown.tsx` 142行。今の `frontend/src/features/useradmin/UserRowActions.tsx` 66〜82行と同じ形）。
- 行のリンクの `data-testid` は `tables-row-link-r<n>` にする。
- どちらも、U4 の鍵の owner の正規化（末尾が数字か `r<n>` なら `-*`。U4 のレビューの R-03 の直し）で、`tables-row-actions-*`・`tables-row-link-*` になる。
- 表の右端の行の操作の Dropdown は `placement="bottom-end"` にする（NFR5.7）。

**E2E 185 の組み立て（Q4: A）**
- 1組を1件にして 20 件にする。各件は、1回のログインで4つの画面と状態を順に回り、状態ごとに axe を流す。
- 状態の内訳:
  - 一覧: 100 カラム × 20 行。NULL の行・長い表示名を含む
  - 開いた検索の項目と、その誤りの状態
  - 開いた行の操作のメニューと、その収まり（`frontend/e2e/support/overflow.ts`）
  - 詳細: 10,000 字の値を含む
  - 作成の誤りのまとめ
  - 更新
  - 削除の確かめの Modal と、離れる確かめの Modal
- 200% の拡大は、既定の幅 1280×720 の半分の CSS ピクセル（640×360）で代える。既定の組と dark・lg の2件を足し、4つの画面を回る。
- 規則・要素は外さない（`aidlc/spaces/default/memory/project.md` の Forbidden）。記録には選択子を出さず、U4 の鍵の形（L3・L6）に合わせる。
- 185 は、U4 のレビューの R-01 が直った形（menuOwner の選択子は、`aria-haspopup` と `aria-expanded` の両方を持つ要素だけ）を前提にする。U3 の検索の開閉のボタンは `aria-expanded` だけを持つため、当たらない。

**4.14 の試し（NFR5.8、R-06）**
- B5・B6 の終わりに、作業フォルダの中だけで axe-core 4.14 にして流し、統合しない。
- 記録は、U4 の L6 の形（ファイル × 組 × 規則 × 鍵 × 区分）に、要素の持ち主の列を足す。marks に `mycui-` で始まるクラスがあれば make-you-chic-ui、無ければ U3 とする。
- make-you-chic-ui の部品の違反は、U4 の依頼（5）と B7 に渡す。
- 行の操作のトリガーと行のリンクが、規則 `label-content-name-mismatch` の passes・incomplete・violations のどれに入ったかを記録し、U4 の passes の対象の候補に足す。

**読み上げとキーボード（NFR5.9・NFR5.10）**
- 機能設計の D9・D12・D18・D22 のとおり。
- 一覧のセルは DOM の文字で値の全体を読み、`aria-label` を付けない（`performance-design.md` の 1節）。

## 8. E2E の資格情報と値（NFR4.25）

- 対象DB の資格情報（管理者・読み取り・書き込みのアカウント）と接続先は、Playwright の `webServer.env` に置かず、プロセスの環境変数で渡す。
  - `SECRET_ENV_NAMES`（`frontend/playwright.config.ts` 58行）に足す。管理者の資格情報も足す（NFR 要件の承認の場の決定 U3 R-01）。
  - 渡す仕組みは Infrastructure Design で決める。
- `frontend/e2e/support/secretValues.ts` の `SecretKind`（36行）に、業務データの値の種類（例 `businessValue`）を足す。
  - 180 が実行ごとに作る値は、目印を付けた形で値のファイルに書く。
  - 185 の見本の目印の値は、`SECRET_FIXED_VALUES` に足す。
  - `<script>` のような一般的な文字列は、探す値にしない。
- 書き込みのアカウントに DDL を与えない決まり（`aidlc/spaces/default/memory/project.md` の Mandated）は、E2E のためにも緩めない。
- 見本の行の値は架空のものだけにする。

## 9. テスト（NFR7.9〜NFR7.13）

**画面の部品のテスト**（NFR7.9）
- 機能設計の 3.9 と `frontend-components.md` の 7節のとおり。
- 描画の後に反映される値は `waitFor` で待って確かめる。テストの時間の上限は、原因を確かめずに延ばさない。

**性質ベースのテスト**（NFR7.10）
- 機能設計の D29 の性質に、5節の写しの関数の性質を足す。

**「1回だけ飛ばす」の旗**（NFR7.11、R-12）
- 今の判定は、道（pathname）が変わる移動だけを止める（`frontend/src/features/roleadmin/useUnsavedGuard.ts` 53行）。道の変わらない移動では、旗が使われずに残りうる。
- 旗を下ろす条件を、次の3つにする。
  - 道が変わる移動の判定で、1回使ったとき
  - 道か問い合わせが変わったとき
  - `markClean()` の後に入力が変わったとき
- 3つ目を見分けるため、フォームは入力の版の数（変えるたびに 1 進む数）を guard に渡す（任意の引数を足す）。版の数が `markClean()` の時と違えば、旗は無効にする。roleadmin は版の数を渡さないため、振る舞いは変わらない。
- 確かめるテスト:
  - `markClean()` の後に道の変わらない移動をしても、次に変更を作れば次の移動は止まること
  - 機能設計の D25 の3つ（`markClean()` の後の移動が止まらない、その後に変更を作ると止まる、呼ばなければ今どおり止まる）

**カバレッジ**（NFR7.12）
- frontend の下限（全体で行 80%・分岐 70%）を保ち、除外を増やさない。

**E2E**（NFR7.13）
- 180 と 185 は `logical-components.md` の L10 のとおりにする。
- 見本は型を付けた1つの見本にし、その形が本物の応答と合うことを、180 の中で `frontend/e2e/support/shapeCompare.ts` を使って毎回確かめる。

## 上流との差

承認済みの文書は書き換えず、差をここに書く（`aidlc/spaces/default/memory/project.md` の決まり）。

1. **書き込みの 2xx の形の崩れ（Q3: A と D5 の差）**: 機能設計の D5 は「形の崩れは通信の失敗」とする。書き込みの 2xx で本文の形が崩れていても、保存できたものとして扱う（5節）。通信の失敗の文を出すと、利用者が送り直して二重に作るおそれがあるため。あわせて、書き込みの通信の失敗・500 は「分からない」の側に分ける（機能設計の 6.1 は TIMEOUT だけを「分からない」としていた）。
2. **作業ロールの切り替えで記憶を消す（Q2: A）**: `U3-NFR4.22` の消す時（ログアウト・更新の失敗・別の利用者のログイン）に、作業ロールの roleId の変化を足した（3節。U3 の NFR 要件のレビューの R-03）。別のタブで利用者が替わる場合は、既知の制約にした。
3. **代わりの案の順から T3 を外した（F2: A）**: `performance-design.md` の 1節。
4. **「塗りまで」を参考として並べる（F1: A）**: `performance-design.md` の 4節。物差しは変えない。
5. **U4 の R-01・R-03 を前提にした**: 7節。U4 の承認の場で別の形になったら、U3 をそろえ直す。

## 後の段に回すこと

- **Infrastructure Design**:
  - 180 の対象DB と 040 の前提: 180 のために E2E のアプリ（全ファイルで1つ）へ対象DB の接続先を渡すと、`frontend/e2e/040-dsl-admin.e2e.ts` 21行の前提（「E2E の環境には対象DB の接続先が無い」）が崩れる。040 の書き換え（本数に数えない）か、別の形にするかを決める。今の `compose.yaml` の `targetdb-postgres` は、PC にポートを開けていない（137行）。
  - 専用のスキーマ: 実行ごとに一意の名前で作り、終わったら消す。
  - 付与: 読み取り・書き込みのアカウントへ、その専用のスキーマの権限を与える。
  - 管理者の資格情報の渡し方（承認の場の決定 U3 R-01）。DDL を新しい npm の依存で行うなら、ライセンスの確認が要る（`docker compose exec` の psql なら、新しい依存は要らない）。
- **コード生成の計画**:
  - U1・U2 の検証が `codePointCount` で数えていることを、読み取りで確かめる（R-04）。
  - 64 KB のバイト数の定数（65,536 の見込み）を、U1 の定数で確かめる。
  - `src/features/tables/` に oxlint の `no-console` を当てるかを決める。
  - R-04（作業ロールの確かめの後の画面）と R-06（保存の 403 の文）。
- **B5**: E2E 185 で、本物の画面の axe と測りで確かめ直す。あわせて、4.14 の試しの記録を作る。

## U1・U2・U4 とそろえる点

- **U1**
  - 値の長さ 1,000 コードポイント、本文 64 KB、413 が 400 より先に出ること、page の上限 1,000,000。
  - 503 の2つの code と、409 の `BUSINESS_TARGET_DB_MISMATCH`。
  - BINARY と同じ扱いの型は、「（バイナリ）」で出す。
  - U1 のレビューの R-01 の答えが「400 以外」になっても、U3 は code で出し分けるため追従できる。
- **U2**
  - 値の長さ 10,000 コードポイント、本文 4 MiB、日時は秒まで。
  - `required`・`nullable` は必ず値を持ち、`formPart` に `lookup` は来ない。
  - TIMEOUT は「分からない」、UNAVAILABLE は「保存されていない」、SAVE_REJECTED はカラムを示さない。
  - U2 のレビューの R-01・R-03・R-04 の答えで code の割り当てが変わっても、U3 は code だけで文を選ぶため、文の表を直すだけで済む。削除の SAVE_REJECTED に外部キー以外の理由が入る形になったら、削除の文を見直す。
- **U4**
  - 名前の関数 `rowActionsName`（L1）・鍵の関数（L3）・passes の確かめ（L5）を使う。
  - 鍵の関数の置き場（U4 のレビューの R-02）は U4 が決め、U3 は使うだけにする。
  - 表示の組の 20 組と、記録の形（選択子を出さない）を U4 と同じにする。

## Sources

- この段の答え
  - `aidlc/spaces/default/intents/261016-business-data-views/construction/u3-businessdata-screens/nfr-design/nfr-design-questions.md`（Q1〜Q4: A、まとめの確認: Looks correct）
  - `aidlc/spaces/default/intents/261016-business-data-views/construction/u3-businessdata-screens/nfr-design/nfr-design-followup-questions.md`（F1・F2: A、まとめの確認: Looks correct）
- NFR 要件: `aidlc/spaces/default/intents/261016-business-data-views/construction/u3-businessdata-screens/nfr-requirements/security-requirements.md`（NFR4〜NFR8）
- 承認の場の決定
  - NFR 要件の段: `aidlc/spaces/default/intents/261016-business-data-views/construction/nfr-requirements/memory.md`（U3 R-01・ID の扱い）
  - 機能設計の段: `aidlc/spaces/default/intents/261016-business-data-views/construction/functional-design/memory.md`（nullable・formPart）
- レビュー
  - `aidlc/spaces/default/intents/261016-business-data-views/.aidlc-reviews/nfr-requirements/units/u3-businessdata-screens/00d44a7a2452e6b7/1.json`（R-01〜R-06）
  - 同じ段の U1・U2・U4 のレビュー: `aidlc/spaces/default/intents/261016-business-data-views/.aidlc-reviews/nfr-design/units/<単位>/df13e747868fc8fa/1.json`（U1・U2 の R-01・R-04、U4 の R-01〜R-03）
- 機能設計: `aidlc/spaces/default/intents/261016-business-data-views/construction/u3-businessdata-screens/functional-design/functional-spec.md`（D2・D5・D9・D16〜D18・D25・D29・6.1）・`frontend-components.md`（3節・4節・6節）
- 同じ段の U1・U2・U4 の NFR 設計: 各単位の `nfr-design/` の `security-design.md`・`reliability-design.md`（U1・U2）・`logical-components.md`（U4）
- コード
  - `frontend/src/app/registry/types.ts`（104〜116行）、`frontend/src/features/auth/authSession.ts`（93〜99行・127行）、`frontend/src/app/login-state/LoginStateGate.tsx`
  - `frontend/src/shared/api-client/apiClient.ts`（161行）、`frontend/src/shared/validation/codePoints.ts`
  - `frontend/src/features/roleadmin/useUnsavedGuard.ts`（53行）、`frontend/src/features/useradmin/UserRowActions.tsx`（66〜82行）
  - `vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/Dropdown/Dropdown.tsx`（142行）・`Tooltip/Tooltip.tsx`（92行）
  - `frontend/playwright.config.ts`（58行）、`frontend/e2e/support/secretValues.ts`（36行）・`shapeCompare.ts`・`overflow.ts`、`frontend/e2e/040-dsl-admin.e2e.ts`（21行）、`compose.yaml`（137行）
- 捨ての試し: リポジトリの外の試しの置き場の結果（T1・T3・T4）
- 決まり: `aidlc/spaces/default/memory/team.md`（Testing Posture・Code Style）、`aidlc/spaces/default/memory/project.md`（Forbidden・Mandated・学び）

## Assumptions & Open Questions

- 前提: 同じタブで別の利用者がログインするときは、必ずログアウト（未ログイン）を通る。別のタブで利用者が替わる場合は、既知の制約とした（3節）。
- 前提: U1・U2 の検証は Java の `codePointCount` で数える。コード生成の計画で確かめる。
- 前提: 64 KB は 65,536 バイト。U1 の定数で確かめる。
