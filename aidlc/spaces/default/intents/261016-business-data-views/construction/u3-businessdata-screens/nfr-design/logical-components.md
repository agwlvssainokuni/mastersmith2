# 論理の部品 — U3 u3-businessdata-screens

この文書は、U3 の NFR の設計がどの部品に当たるかをまとめる。部品は L1〜L10 の 10 個とする。名前は仮で、コード生成で変えるときは差を記録する。文章で ID を指すときは、単位の接頭辞を付ける（例 `U3-NFR4.22`）。

## 捨ての試しの結果（要点）

試しの全体は `performance-design.md` の冒頭にある。

- **条件**: make-you-chic-ui の固定先 `a9309c4` の dist・React 19・Playwright の Chromium で、100 カラム × 100 行の `Table` を描いた。
- **結果**（中央値）
  - 件数を 20→100 に変える（100×100）: T1（セルごとの Tooltip）46 ms、T3（はみ出したセルだけ Tooltip）452 ms
  - 詳細とフォーム（T4）: 15 ms
- **部品への意味**
  - L7 の表は T1 のまま、仮想化の部品を足さない。
  - T3 の「はみ出しを測る部品」は作らない（F2: A）。
  - L10 の測りの部品に、参考の「塗りまで」の記録を足す（F1: A）。

## 冒頭の対応表

| 部品 | 置き場 | 受け持つ NFR 要件 | 成果物（`dist`）に入るか |
|---|---|---|---|
| L1 API の呼び出し | `frontend/src/features/tables/businessDataApi.ts` | U3-NFR1.9・4.23・4.24 | 入る |
| L2 写しの関数 | `frontend/src/features/tables/businessDataParse.ts` | U3-NFR4.24 | 入る |
| L3 記憶 | `frontend/src/features/tables/businessDataStore.ts` | U3-NFR4.2・4.22・2.10 | 入る |
| L4 ログインの範囲の知らせ | `frontend/src/app/login-state/`（足す） | U3-NFR4.22 | 入る |
| L5 文と読み直しの判定 | `frontend/src/features/tables/problemMessage.ts` | U3-NFR6.3・6.4・8.7、4.24 | 入る |
| L6 純粋な関数 | `valueFormat.ts`・`rowLabel.ts`・`formDiff.ts`・`formValidation.ts`・上限の定数 | U3-NFR2.11・3.7・4.20・4.23・5.9・7.10 | 入る |
| L7 フック・画面・部品 | `frontend/src/features/tables/`（機能設計の `frontend-components.md` の 2節） | U3-NFR1.7・1.9・1.10・2.10・4.20・4.21・4.26・5.6〜5.10 | 入る |
| L8 共通の部品 | `frontend/src/shared/scroll-region/`・`page-size/`・`null-value/`・`unsaved-guard/` | U3-NFR5.6・5.10・7.11 | 入る |
| L9 骨組みの変更 | `frontend/src/app/navigation/`（4つの道・今の項目）・`frontend/src/app/work-role/`（`registerSwitchGuard`） | U3-NFR4.20・5.10 | 入る |
| L10 E2E の補助と E2E | `frontend/e2e/support/businessDataFixtures.ts`・`businessDataApiRoute.ts`・`businessDataMeasure.ts`、`secretValues.ts` の追記、`180-business-data-flow.e2e.ts`・`185-business-data-accessibility.e2e.ts` | U3-NFR1.8・4.2・4.25・5.6〜5.8・7.8・7.13 | 入らない |

## 1. 部品ごとの役割と境界

- **L1 API の呼び出し**
  - 本文の直列化を1回だけ行い、大きさを確かめてから送る。上限を超えたら送らない。
  - 応答を L2 で写す。
  - 書き込みの結果を「分かる」と「分からない」に分ける（Q3: A）。通信の失敗・500・503 TIMEOUT は「分からない」にする。
  - 失敗は今の ApiError で投げる。自動では送り直さない。
- **L2 写しの関数**
  - `unknown` から型へ写す。例外を投げず、形が違えば `undefined` を返す。
  - 列挙の値は、既知の一覧と照らす（`lookup` を除く）。
- **L3 記憶**
  - 印の表（作った順に最大 100 件。読んでも消さない）と、テーブルごとの表（使った順に最大 100 テーブル。定義と一覧の状態を持つ）を持つ。
  - L4 の知らせと、作業ロールの roleId の変化で、全部消す（Q2: A）。
  - ブラウザの保存に写さない。
- **L4 ログインの範囲の知らせ**
  - LoginStateGate が、ログイン⇄未ログインの切り替わりのたびに知らせる。
  - L3 は、読み込まれた時に購読する。
  - 骨組みから機能を import しない向きを保つ（機能が骨組みの知らせを購読する）。
- **L5 文と読み直しの判定**
  - code と reason から、文言の鍵を選ぶ。知らない code は、状態コードの区分で写す。
  - `needsDefinitionReload(problem, definition)` を持つ（D4）。
- **L6 純粋な関数**
  - 書式・行のラベル・変更の比べ・画面の検証・上限の定数（コードポイント・バイト数）。
  - fast-check の性質ベースのテストの対象。
- **L7 フック・画面・部品**
  - 3つの画面は、それぞれ別の `lazy` にする。
  - 表の部品は `React.memo` にし、セルは Tooltip で包む（T1）。
  - 行の操作は、包みの span の `data-testid` を `tables-row-actions-r<n>` にする。行のリンクは `tables-row-link-r<n>` にする。
- **L8 共通の部品**
  - `unsaved-guard` は roleadmin から移し、`markClean()` と入力の版の数（任意の引数）を足す。
  - 旗を下ろす条件は3つ（`security-design.md` の 9節）。
- **L9 骨組みの変更**
  - 道を作る1か所の関数で、4つの道を作る。
  - サイドバーの今の項目の判定を、4つの道に広げる。
  - 作業ロールの切り替えの前の確かめの口を足す。
- **L10 E2E の補助と E2E**
  - 型を付けた1つの見本を置く。型は `src` から `import type` で読む。
  - 応答を差し替える口は、POST・PATCH の `/api/tables/*` を受ける。記録には、メソッドと道だけを出す。
  - 測りの部品は、click と change で測り始め、参考の「塗りまで」も記録する。
  - 180 は代表の流れで、見本の形を本物の応答と `shapeCompare` で比べる。
  - 185 は、1組を1件にした 20 件と、200% の2件にする（Q4: A）。
  - U4 の L1（名前の関数）・L3（鍵の関数）・L5（passes の確かめ）を使う。

## 2. 失敗の及ぶ範囲

| 失敗 | 及ぶ範囲 | 及ばない範囲 | 手当て |
|---|---|---|---|
| L1・L2 の写しの誤り | 業務データの画面（形の崩れの表示） | ほかの機能 | 写しの関数の単体テスト・性質ベースのテスト |
| L3 の消し忘れ | 同じタブの中の検索の条件と主キーの印 | サーバーの判定（要求ごとに判定する） | 3つの消す時の単体テスト |
| L4 の知らせの誤り | 購読する機能（今は L3 だけ） | ログイン状態そのもの | LoginStateGate のテスト |
| L5 の文の取り違え | 利用者の受け取り方（とくに「分からない」と「されていない」） | データ | code ごとの文の単体テスト |
| L8 の `unsaved-guard` の誤り | 業務データのフォームと roleadmin | ほかの画面 | 移したテストと、旗のテスト |
| L9 の道・今の項目の誤り | サイドバーの今の項目、作業ロールの切り替え | — | 既存のテストの書き換え（`frontend-components.md` の 7節） |
| L10 の誤り | E2E の取りこぼし・失敗 | 成果物（`dist` に入らない） | passes の「0 件は失敗」（U4 の L5）、180 の見本の形の比べ |

## 3. 共有するもの

- **U4 から使うもの**: L1 `rowActionsName`、L3 の鍵の関数、L5 の passes の確かめ、L6 の記録の形、表示の組の 20 組。
- **roleadmin と共有するもの**: L8 の `unsaved-guard`（移した後も roleadmin の振る舞いは変えない）。
- **骨組みに足すもの**: L4 の知らせ、L9 の作業ロールの確かめの口。
- **passes の候補（U4 に渡す）**: `tables-row-actions-*`（行の操作のトリガー）と `tables-row-link-*`（行のリンク）。B5・B6 の 4.14 の試しの結果で足す。

## 上流との差

承認済みの文書は書き換えず、差をここに書く（`aidlc/spaces/default/memory/project.md` の決まり）。

1. **書き込みの結果の分け方（Q3: A と D5 の差）**: L1 は、書き込みの 2xx で本文の形が崩れていても、保存できたものとして扱う。機能設計の D5（形の崩れは通信の失敗）との差（`security-design.md` の 5節）。
2. **作業ロールの切り替えで記憶を消す（Q2: A）**: L3 は、作業ロールの roleId が変わったら全部消す。機能設計の `businessDataStore` の口（`clearBusinessDataStore` はログアウトのとき）に、消す時を足した。あわせて、承認済みの口の名前 `takeRowKey` は、読んでも消さない口として `peekRowKey`（仮）にする（R-07 の答え）。
3. **T3 を代わりの案の順から外した（F2: A）**: はみ出しを測る部品は作らない。
4. **「塗りまで」を参考として並べる（F1: A）**: L10 の測りの部品が記録する。
5. **U4 の R-01・R-03 を前提にした**: L10 の 185 は、menuOwner の選択子が Dropdown のトリガーに絞られた形（R-01）と、owner の末尾の正規表現が数字か `r<n>` だけの形（R-03）を前提にする。
6. **骨組みの変更が増える**: L4（ログインの範囲の知らせ）は、機能設計の `frontend-components.md` の 6節の `app/` の変更の一覧に無い。

## 後の段に回すこと

- **Infrastructure Design**: 180 の対象DB と 040 の前提、専用のスキーマ、付与、管理者の資格情報の渡し方（`security-design.md` の「後の段に回すこと」）。
- **コード生成の計画**
  - `codePointCount` の確かめ（U1・U2）。
  - 64 KB の定数の確かめ。
  - `src/features/tables/` に `no-console` を当てるか。
  - R-04・R-06。
  - L4・L8・L9 の変更を使っている既存のテストと E2E の洗い出し（`aidlc/spaces/default/memory/project.md` の学び）。
- **B5**: 185 で、本物の画面の axe と測りで確かめ直す。

## U1・U2・U4 とそろえる点

- **U1**: L5 は 503 の2つの code・409 の MISMATCH・413・400（fieldErrors なしを含む）を出し分ける。L6 の上限は U1 の 1,000 コードポイント・64 KB。
- **U2**: L1・L5 は、TIMEOUT を「分からない」、UNAVAILABLE を「保存されていない」、SAVE_REJECTED をカラムを示さない誤りとして扱う。L6 の上限は U2 の 10,000 コードポイント・4 MiB。
- **U4**: L7 の行の操作の名前は U4 の L1 で作り、L10 は U4 の L3・L5・L6 を使う。鍵の関数の置き場（U4 のレビューの R-02）は U4 が決める。

## Sources

- この段の答え
  - `aidlc/spaces/default/intents/261016-business-data-views/construction/u3-businessdata-screens/nfr-design/nfr-design-questions.md`（Q1〜Q4: A）
  - `aidlc/spaces/default/intents/261016-business-data-views/construction/u3-businessdata-screens/nfr-design/nfr-design-followup-questions.md`（F1・F2: A）
- NFR 要件: `aidlc/spaces/default/intents/261016-business-data-views/construction/u3-businessdata-screens/nfr-requirements/`（`performance-requirements.md`・`security-requirements.md`・`tech-stack-decisions.md`）
- 機能設計: `aidlc/spaces/default/intents/261016-business-data-views/construction/u3-businessdata-screens/functional-design/frontend-components.md`（1節・2節・4節・6節・7節）・`functional-spec.md`
- 同じ段の U4 の NFR 設計: `aidlc/spaces/default/intents/261016-business-data-views/construction/u4-a11y-carryover/nfr-design/logical-components.md`（L1〜L6）、レビュー `aidlc/spaces/default/intents/261016-business-data-views/.aidlc-reviews/nfr-design/units/u4-a11y-carryover/df13e747868fc8fa/1.json`（R-01〜R-03）
- 同じ段の U1・U2 の NFR 設計: 各単位の `nfr-design/logical-components.md` の「U3 とそろえる点」
- コード: `frontend/src/app/login-state/LoginStateGate.tsx`、`frontend/src/app/work-role/WorkRoleProvider.tsx`、`frontend/src/features/roleadmin/useUnsavedGuard.ts`、`frontend/e2e/support/u7Measure.ts`・`shapeCompare.ts`・`frameApiRoute.ts`
- 捨ての試し: リポジトリの外の試しの置き場の結果

## Assumptions & Open Questions

- 前提: 機能（tables）が骨組み（`app/login-state`）の知らせを購読する向きは、今の決まり（機能どうしは import しない。機能から骨組みへは import してよい）に合う。
- 未決: L4 の口の名前と形は、コード生成の計画で確定する。
