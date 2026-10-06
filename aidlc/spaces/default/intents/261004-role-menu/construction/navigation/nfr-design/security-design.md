# セキュリティの設計 — U5 navigation

## 出典

- この単位の承認済みの NFR 要件 `construction/navigation/nfr-requirements/security-requirements.md`（NFR1.1〜NFR1.4・NFR1.6・NFR1.7・NFR1.9・NFR1.12）と、その読み直しの記録（R-01・R-08・R-09）
- この単位の承認済みの機能設計 `construction/navigation/functional-design/functional-spec.md`（2.1・2.2）・`rules.md`（BR1.1・BR5.1〜BR5.4・BR6.1）
- 契約 `inception/contract-design/contract-summary.md`（C1・C5・C9）
- この段の答え `nfr-design-questions.md`（Q1 A・Q3 A と、まとめの確認）
- 統合の点: role の NFR 設計 `construction/role/nfr-design/logical-components.md`（5節・6節の U5 への引き継ぎ）・`reliability-design.md`（3節 `EffectivePermissionConsistencyIT`）
- 捨ての試しの結果: `reliability-design.md` の 1節（N1・N2）

## 1. 入口と分類（NFR1.2・NFR1.3）

- 2本の API（`GET /api/me/navigation`・`GET /api/me/table-access`）に `ApiAccess(AUTHENTICATED)` を付ける。U1 の構造の検査と実行時の検査が網羅を見る。
- 認証は既存の仕組み（アクセストークンの認証。停止中は既存の拒否）に任せ、navigation の中で重ねない。
- `NavigationAuthorizationApiIT`（パラメーターを使う表）:

| API | 利用者 | 期待 |
|---|---|---|
| メニュー | 未認証 | 401 `AUTHENTICATION_REQUIRED` |
| メニュー | 停止中（作業ロールで READ のテーブルあり） | 既存の停止の拒否 |
| メニュー | 通常 | 200 |
| 置き場 | 未認証 | 401 |
| 置き場 | 停止中（そのテーブルが READ） | 既存の停止の拒否 |
| 置き場 | そのテーブルが READ・FULL | 200 |
| 置き場 | そのテーブルだけ NONE（ほかのテーブルは READ） | 403 `ACCESS_DENIED` |

## 2. 判定の置き場と読み方（NFR1.1・NFR1.9、navigation の R-08 の決着）

- 判定はすべてサーバー側。メニューは `snapshotFor` を1回呼んで写しで絞り、置き場は `resolve(userId, {schema, table, null})` を1回呼ぶ。
- この使い分けは、role の NFR 設計で改められた引き継ぎ「`resolve` は1回の要求で対象が1つのとき（テーブルの置き場など）に使ってよい。2つ以上の対象は `snapshotFor` を1回」（`construction/role/nfr-design/logical-components.md` の 5節・6節）に合う。承認済みの role の NFR 要件にあった「`snapshotFor` を1回（必須）」との食い違いは、role の側の文言の直しで解けた。
- 置き場は、DSL が無い・組が無い・NONE を区別しない結果の型 `NotVisible` にし、同じ 403 `ACCESS_DENIED`・同じ本文の項目で返す。表示名を返さない。どの場合も `resolve` を1回呼ぶ。
- 応答の時間がそろうことは保証しない（受け入れた制約、7節）。

## 3. 利用者を指す値（NFR1.4）

- 2本の API の画面入出力の層は、引数に `schema`・`table` だけを宣言し、主体は要求の文脈の `AuthenticatedUser` から `userId` を読む。`userId` などほかの引数は結び付けないため、送られても読まれない。
- `NavigationApiIT` に、引数 `userId` にほかの利用者の ID を足した要求で、主体のメニューと置き場の結果だけが返る場合を入れる。

## 4. 信頼できない入力（NFR1.7、捨ての試し N1・N2）

- 引数の値の文字は問わない。試し N1 で、印字できる文字（記号・日本語・絵文字・`javascript:`・`https://`）も制御文字も、要求の検査で拒否されず、送った値のまま受け取れた。base64url で包む形への切り替えは要らない（Q3 A の条件に当たらない）。
- 空の確かめ: Spring の結び付けは空の文字列（`table=`）を拒否しない（試し N2-f）。画面入出力の層で、`schema`・`table` が空なら `VALIDATION_FAILED`（400）にする。引数が無いときは既存の変換が 400 にする（試し N2-e）。長さでは拒否しない（BR5.1、機能設計の直し R-01）。
- `label` はサーバーで変えずに返す。`icon` は `NavIconPolicy` で照らす（大文字と小文字を区別し、前後の空白を許さない）。
- `NavigationUntrustedInputIT`: 試し N1 の 23 種の名前を持つテーブルを使い捨ての DSL に置き、置き場の問い合わせがすべて 200（権限あり）・403（権限なし）になり、400 にならないこと。`label` に `<script>`・`<`・`>`・`&`・`"`・`'` を含めて応答の JSON の値が変わらないこと。空の引数は 400。

## 5. メニューと置き場の一致のテスト（NFR1.12、Q1 A）

- 期待はメニューの応答から作らず、テストの用意（DSL・明示の設定・割り当て）からテストの中に書いた期待の表（DSL の全テーブルの期待の主権限）で持つ。navigation の R-01 の誤り（DSL にあってメニューが指さない READ のテーブルを 403 と誤る）を避ける。
- 確かめることは2つ。
  - 置き場: DSL の全テーブルについて、期待が READ・FULL なら 200（`main` も期待どおり）、NONE なら 403。
  - メニュー: 移れる項目のテーブルの集まりが、「期待が READ・FULL で、メニューの項目が指すテーブル」の集まりと等しい。
- 組は7つ。どの組の DSL にも、どのメニューの項目も指さない READ のテーブルを1つ以上置く。

| 組 | 中身 |
|---|---|
| 1 | 悪い側（1万カラム明示・テーブルの半分が NONE・menus 1,000 項目・深さ 5） |
| 2 | スキーマだけに明示の値（テーブルとカラムは継承） |
| 3 | テーブルだけに明示の値（スキーマは設定なし） |
| 4 | 作業ロールなし（メニューは空、置き場はすべて 403） |
| 5 | 上書きの向き（スキーマ NONE・テーブル READ と、スキーマ FULL・テーブル NONE） |
| 6 | カラムだけに明示の READ（テーブルは NONE のまま。メニューに出ず、置き場も 403） |
| 7 | DSL なし（メニューは空、置き場はすべて 403） |

- 口の値の網羅（カラム・補助権限・DSL に無い名前・直接とグループ経由にまたがる割り当て・読み替え中）は、role の `EffectivePermissionConsistencyIT`（8つの組、期待の表）が受け持つ。navigation の `NavigationMenuAccessConsistencyIT` は、HTTP を通した側で、絞り込みと置き場の判定が同じ期待に合うことを確かめる。「継承の階層の場合を網羅する」とは書かない（読み直しの R-09）。
- 悪い側の組（1）は、テストの用意の時間を抑えるため、DSL と明示の設定を SQL で内部DB に直接入れてよい（テストの中で `TestDatabase` の一時の場所を使う）。期待の表は、明示の設定の作り方（テーブルの番号が偶数なら READ など）から同じ規則で求める。

## 6. 個人に関する値と TRACE（NFR1.6）

- 受け渡す値は `userId`・名前・表示名・権限の値だけ。伏せ字の型は使わない。
- `NavigationBoundaryArchitectureTest` に、`navigation` の service・domain の公開のメソッドの引数と戻り値に、`AuthenticatedUser` と `user` の伏せ字の型が現れないことの規則を置く（`AuthenticatedUser` は web の層だけで読む）。

## 7. 受け入れた制約

- **拒否の理由ごとの応答の時間**: そろうことを保証しない（承認済み）。
- **エンコードしない文字・大きすぎる問い合わせ**: エンコードしていない空白・`#`・`"`・`<`・`|`・`{`、8 KiB を超える問い合わせは、Tomcat が Spring に渡す前に既定の HTML の 400 で拒否する（試し N2-a・N2-c）。アプリの共通の誤りの本文にならない。画面は必ずエンコードし、実際の名前は 8 KiB に届かないため、細工した要求だけが受ける。
- **TRACE のログの名前**: 承認済み。
- **正しくない `%` の並び**: 試し N2-d で、`a%zz` などを送ると 500 になり、ERROR のログの例外の文に引数の名前と値が入った。依頼者の決定で受け入れた制約とした。漏れるのは攻撃者が送った崩れた値で秘密ではなく、アプリ全体の今の振る舞いである。アプリ全体の直しは後の Intent に回す。認証の前か後かは未確認（`observability-design.md` の 3節）。

## 8. 依頼者に諮る点

- 無い（正しくない `%` の並びの件は、承認の場の決定で 7節の受け入れた制約にした）。

## 承認の場の決定と直し

- 決定: 依頼者は承認の場で Request Changes を選び、決定の文は「推奨の案のとおり直す」。navigation で直すのは R-01・R-02 の2件。
- R-02（Major）: 8節の「依頼者に諮る点」を、依頼者の決定（案 1）に従って 7節の受け入れた制約に移した（中身は `observability-design.md` の 3節と末尾の節）。
