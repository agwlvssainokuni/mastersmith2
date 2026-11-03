# 性能の設計 — u5-menu-icons-home

出典: NFR 要件（`construction/u5-menu-icons-home/nfr-requirements/performance-requirements.md` の NFR2.1.1・NFR2.1.2・NFR2.2.1〜NFR2.2.3、`tech-stack-decisions.md`）、機能設計（`construction/u5-menu-icons-home/functional-design/functional-spec.md` の D3・D5・D6・D10・D16・D17・D20・D27、`frontend-components.md`）、契約（`inception/contract-design/contract-summary.md` の C5）、NFR 要件のレビューの指摘（`nfr-requirements/reviews/review-01.md` の R-02・R-03）、今のコード（読み取りだけ: `NavigationService`・`MenuFilter`・`DslModel.findTable`、`buildNavSections.ts`、`HomePage.tsx`）。

U5 は新しい API・問い合わせ・依存を足さない。この設計は「メニューの応答に種類を足しても読み出しが増えない作り」と「画面の組み立てを1回の走査に収める作り」を決める。キャッシュ・プール・非同期・分割は足さない（要らない）。

## 1. サーバー: 種類を返す操作の作り（NFR2.1.1・NFR2.1.2）

### 1.1 作り

```text
NavigationService.menu(userId):
  present = dslProvider.current()                 ← 今のまま 1 回
  snapshot = resolver.snapshotFor(userId)         ← 今のまま 1 回
  model = present.model()
  kindOf = (schema, table) ->                     ← 新しい操作（新しい読み出しは無い）
             model.findTable(TableRef(schema, table))
                  .map(t -> t.view() ? VIEW : TABLE)
                  .orElse(null)
  MenuFilter.filter(model.menus(), MenuFilterInput(tableMain, iconOf, kindOf))
```

- 種類を決める操作は、**メニューの木を取り出したのと同じ `model`** を閉じ込める。DSL を別に読まない（`current()` は 1 回のまま）。権限の解決も呼び足さない（`snapshotFor` は 1 回のまま）。
- 操作は `MenuFilter` が移れる項目（主権限が `READ`・`FULL`）を作るときだけ、項目 1 つにつき 1 回呼ぶ。移れない項目・落とす項目では呼ばない。
- `findTable` は、スキーマ名の一致をスキーマの一覧（検証を通ったモデルはちょうど 1 つ。BR1.3）で探し、見つけたスキーマのテーブルを名前の表で引く。1 項目あたりの費用は定数で、木を走査し直さない（項目の数に対して合計 O(N)）。
- 対象DB・内部DB への新しい問い合わせは無く、接続の数も増えない。

### 1.2 `null` が起こる条件の確かめ（NFR 要件の指摘 R-01 への答え）

メニューの木と種類を決める操作は**同じ `model`** から作る。メニューの項目が指すテーブルは、投入と適用の検証（メニューのテーブルはスキーマに存在すること）を通ったモデルの中にあるため、`NavigationService` を通る限り、移れる項目の種類が `null` になる状況は起こらない。DSL の差し替えの途中（`NavigationDslSwapIT` の `menuWithRemovedTable` のように、DSL の取り出しの後に差し替わるとき）に食い違うのは権限の写し（`snapshotFor`）の側で、それは今までどおり項目を落とす（主権限が `NONE`）だけで、種類は元のモデルのまま決まる。

したがって、機能設計 D5 の「DSL にテーブルが見つからないときは `null`」は、`MenuFilter` が受ける操作が `null` を返してよい、という**防御の決まり**であり、サービスの経路では起こらない。これは D5 の補足で、機能設計は書き換えず、`logical-components.md` の差の一覧に記録する。確かめの置き場は次のとおり。

| 確かめ | 置き場 | 内容 |
|---|---|---|
| `null` を返す操作でも壊れない | `MenuFilterTest`（単体） | 操作が `null` を返すと移れる項目の `tableKind` が `null`。項目は落ちない・例外を投げない |
| 種類の操作の呼ばれる回数 | `MenuFilterTest`（単体、回数を数える替え物） | 移れる項目 1 つにつきちょうど 1 回。移れない項目・落とす項目では 0 回（NFR 要件のレビューの R-02 の直し） |
| 差し替えの途中でも木と種類が同じモデル | `NavigationDslSwapIT`（既存の `menuWithRemovedTable` に確かめを足す） | 差し替えの途中の応答で、残った項目の `tableKind` が差し替え前のモデルのテーブルの種類と同じ（木と種類がずれない） |
| 読み出しの回数が変わらない | `NavigationCallCountTest`（既存、変更なし） | menus 1 項目と 1,000 項目で `current()` 1 回・`snapshotFor` 1 回 |

### 1.3 応答の大きさ（NFR 要件のレビューの R-03 を直した値）

`tableKind` の追加分（項目名と値と区切りのカンマ。圧縮前）を数えた値:

| 項目 | 文字列 | バイト数 |
|---|---|---|
| テーブル | `"tableKind":"TABLE",` | 20 |
| ビュー | `"tableKind":"VIEW",` | 19 |
| フォルダ（値なし） | `"tableKind":null,` | 17 |

1,000 項目でも、最大でも約 20 KB の増加（圧縮前）にとどまる。NFR 要件の文面の約 22・18 バイトは「最大でも 22 バイト以下」と読み、この表を正とする（NFR 要件の文書は書き換えず、差を `logical-components.md` に記録する）。応答時間の目標（95 パーセンタイル 1 秒以内）は変えない。

## 2. 画面: 組み立てを 1 回の走査にする（NFR2.2.2・NFR2.2.3）

### 2.1 走査の作り

| 処理 | 作り | 費用 |
|---|---|---|
| サイドバーの業務の項目 | `buildNavSections` が応答の木を 1 回たどり、項目ごとに `resolveItemIcon`（定数の分岐）を呼ぶ。今の走査に 1 回の呼び出しを足すだけ | 項目の数 N に O(N)（今のまま） |
| ホームの業務のカード | `buildHomeCards` が最上位の項目だけを 1 回たどる。移れる項目は自分の道、フォルダは `firstNavigablePath` が子を深さ優先でたどり、最初の移れる項目で**打ち切る** | 最悪でも木の全項目を 1 回ずつ（O(N)）。打ち切るため、ふつうは深さ分 |
| ホームの管理のカード | `selectAdminSidebarItems` の戻り（管理区画の登録、最大で数個）から作る | 登録の数に O(M) |

- どの走査も、項目を高々 1 回だけ訪れる。再帰の深さは DSL の深さの上限（5 段）に収まる（`MenuFilter` と同じ前提）。
- `resolveItemIcon` は許す一覧への `Set` の所属の判定（`ALLOWED_ICON_SET`）で、定数時間。一覧は 24 個で増えても定数。

### 2.2 再計算の抑え方

- `HomePage` は、カードのモデル（`buildHomeCards` の戻り）を `useMemo` で作り、依存は `business.response`・`language`・`loginState`（`loggedIn`・`admin`）・`registrations`・文言を引く関数。業務のメニューの読み直し（作業ロールの切り替えの成功・拒否）や言語の切り替えのときだけ作り直す。ほかの再描画（開閉の状態、トースト）では作り直さない。
- 作業ロールの 1 行は `current?.name` を読むだけで、計算を足さない。
- サイドバー側は今の作りのまま（`ShellLayout` が描画ごとに `buildNavSections` を呼ぶ）。走査に足すのは定数時間の分岐 1 つだけで、メモ化の作りは変えない（変えるとサイドバーの開閉の状態の規則に触れるため）。

### 2.3 ネットワークと大きさ（NFR2.2.1・NFR2.2.3）

- ホームは、すでにシェルが読む業務のメニューの状態・作業ロールの状態・ログイン状態・登録を読むだけで、HTTP 要求を足さない。ホームを開く操作で増える要求は 0。
- ロゴは `AppIcon`（`<svg>` と 2 つの `<path>` を描く部品）で、画像・SVG の取得をしない。大きさは 24 px 固定（`width`・`height` を指定）で、描画の後に上のバーの配置がずれない。
- 新しい依存を足さない。足すコードは、純粋な関数 3 つ（`resolveItemIcon`・`firstNavigablePath`・`buildHomeCards`）と部品 3 つ（`HomeCardList`・`HomeCard`・ホームの変更）、素の CSS 1 つだけで、入口の JavaScript の増加は小さい。ホームは遅延読み込みにしない（最初の画面のため）。

## 3. 性能の予算と確かめ（まとめ）

| 予算 | 値 | 確かめ方 | 確かめる段 |
|---|---|---|---|
| メニューの API の読み出し（`current()`・`snapshotFor`） | それぞれ 1 回（変更なし） | `NavigationCallCountTest`（変更なし） | Code Generation |
| 種類の操作の呼び出し | 移れる項目 1 つにつき 1 回 | `MenuFilterTest`（回数を数える替え物） | Code Generation |
| 1 要求の接続の数 | 今のまま | `NavigationConnectionUsageIT`・`NavigationQueryExposureIT`（変更なし） | Code Generation |
| メニューの API の応答時間 | 95 パーセンタイル 1 秒以内・`checks` の率 1（変更なし） | k6 の既存の場面 `navMenu`・`navMenuBaseline`（台本は変えない） | Build and Test（Performance Validation の段が無いため。`project.md` の学び）。測れなければ `Unverified` |
| 応答の大きさの増加 | 1 項目 20・19・17 バイト以下、1,000 項目で約 20 KB（圧縮前） | 単体テストで `NavNodeResponse` の JSON の長さの差を確かめる（1 項目の追加分が 22 バイト以下） | Code Generation |
| `firstNavigablePath` の訪問数 | 木の項目の数以下 | `fast-check` の性質ベースのテスト（訪問を数える替え物で決定的に） | Code Generation |
| ホームを開くときの HTTP 要求の増加 | 0 | `HomePage` の部品のテスト（API の呼び出しの数が増えない）と E2E 030 の書き換え | Code Generation・統合の前の E2E |
| `buildHomeCards` の再計算 | 依存が変わらない再描画では呼ばない | 部品のテスト（同じ入力で再描画しても呼び直さない） | Code Generation |

## Sources

- Initial description: N6（業務データの日時のタイムゾーンの変換）と画面の仕上げの Intent [desc]
- Workflow-selected scope: classic [scope]
- NFR 要件: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u5-menu-icons-home/nfr-requirements/performance-requirements.md`・`security-requirements.md`・`tech-stack-decisions.md`・`reviews/review-01.md`
- 機能設計: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u5-menu-icons-home/functional-design/functional-spec.md`・`frontend-components.md`
- 契約: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/contract-design/contract-summary.md`（C5）
- 今のコード（読み取りだけ）: `backend/src/main/java/cherry/mastersmith/navigation/service/NavigationService.java`・`navigation/domain/MenuFilter.java`・`dsl/domain/DslModel.java`、`backend/src/test/java/cherry/mastersmith/navigation/web/NavigationDslSwapIT.java`、`frontend/src/app/navigation/buildNavSections.ts`・`frontend/src/app/pages/HomePage.tsx`
- 進め方: `aidlc/spaces/default/memory/team.md`・`project.md`

## Assumptions & Open Questions

- [assumption] 投入と適用の検証で「メニューのテーブルはスキーマに存在する」ことが守られている（`MenuFilter` の説明にある深さの前提と同じ位置の前提）。成り立たないとき、種類は `null` になり、画面は既定のアイコンに落ちる（壊れない）。
- [assumption] ホームのメモ化の依存（`t` を引く関数の同一性）は、コード生成で実際の `useMessages` の戻りを確かめて決める。
- 未解決の点: 無い。
