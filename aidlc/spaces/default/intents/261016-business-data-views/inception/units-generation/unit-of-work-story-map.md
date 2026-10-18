# ストーリーと単位の対応（Unit of Work Story Map）— business-data-views

出典: ストーリー `aidlc/spaces/default/intents/261016-business-data-views/inception/user-stories/stories.md`（US1.1〜US4.1、17 件）、画面と受け入れ基準の対応 `aidlc/spaces/default/intents/261016-business-data-views/inception/refined-mockups/mockups.md` の 0 節、`unit-of-work.md`。

## 対応表

| ストーリー | 主の単位 | 関わる単位 | 主の単位の Directory | 備考 |
|---|---|---|---|---|
| US1.1 メニューからテーブルの一覧を開く | U3 | U1 | u3-businessdata-screens | 画面と道の登録は U3、一覧の API と 403・404 は U1 |
| US1.2 条件で行を絞り込む | U1 | U3 | u1-businessdata-read | 検索の演算子・エスケープ・型の誤りは U1、検索の領域は U3 |
| US1.3 ページを送り、並べ替える | U1 | U3 | u1-businessdata-read | 件数・並べ替え・NULL の並び・読む行の上限は U1、表とページ送りと件数の選択は U3 |
| US1.4 行の詳細を見る | U1 | U3 | u1-businessdata-read | 行の指し方・2行以上の拒否は U1、詳細の画面と一覧へ戻るは U3 |
| US1.5 値を読みやすい形で見る | U3 | U1 | u3-businessdata-screens | 書式・NULL・長い値・password の伏せ字の表示は U3、時差つきの型の UTC と password を応答に含めないことは U1 |
| US2.1 行を作成する | U2 | U3 | u2-businessdata-write | 作成の API・既定値・自動の採番・参照先に無い値は U2、作成の画面は U3 |
| US2.2 行を更新する | U2 | U3 | u2-businessdata-write | 変えたカラムだけの更新・空の入力・password は U2、更新の画面と離れる確かめは U3 |
| US2.3 行を削除する | U2 | U3 | u2-businessdata-write | 削除の API・外部キー・CASCADE は U2、削除の確かめは U3 |
| US2.4 入力の誤りをその場で直す | U2 | U3 | u2-businessdata-write | サーバー側の検証と pattern の時間の上限は U2、画面の検証と誤りの出し方は U3 |
| US2.5 同じ値の重なりを防ぐ | U2 | — | u2-businessdata-write | 一意の事前の問い合わせと制約の違反の読み替え |
| US3.1 作業ロールの権限どおりにカラムが見え、直せる | U1 | U2・U3 | u1-businessdata-read | 出し分けの関数は U1、書き込みの本文の拒否は U2、画面の入口は U3 |
| US3.2 主キーとビューで、行の操作が決まる | U1 | U2・U3 | u1-businessdata-read | 許す操作の関数と詳細の 409 は U1、更新・削除の 409 と監査は U2、理由の表示は U3 |
| US3.3 見られない・無いときに理由が分かる | U1 | U2・U3 | u1-businessdata-read | 判定の順・404・403・つながらない は U1、書き込みの設定が無い・時間切れは U2、文の出し分けは U3 |
| US3.4 業務データの操作を監査で追える | U2 | — | u2-businessdata-write | 監査の出来事と種類 |
| US3.5 API が作業ロールの権限で守られる | U1 | U2 | u1-businessdata-read | 認可の表のテストと API の分類は U1（読み取り）と U2（書き込み）の両方に当たる。境界テストの変更は U1 |
| US3.6 業務データの SQL と接続が入力や DB の違いで乱れない | U1 | U2・U3 | u1-businessdata-read | SQL の部品・識別子の照合・方言・境界の値の読み取り・接続の数は U1、書き込みの側と書き込みのアカウントは U2、桁の多い数の受け渡しは U3 |
| US4.1 読み上げで行の操作と画面の部品が分かる | U4 | U3 | u4-a11y-carryover | 固定先・名前の関数・既存の一覧・トップバー・axe-core は U4、業務データの一覧の行の操作と axe は U3 |

## 単位をまたぐもの（横断）

- **認可（US3.5）**: 足す API はすべて表のパラメーターのテストで 401・403・200 を確かめる。U1 は一覧・詳細、U2 は作成・更新・削除。
- **出し分けの関数（US3.1・US3.2）**: U1 が持ち、U2 と U3 は結果を使う（U3 は応答の可否で入口を出す）。
- **漏えい（US3.6）**: 行の値・検索の値・接続情報の漏えいの確かめは U1（読み取り）と U2（書き込み・監査の失敗のログ）。
- **3種類の対象DB**: U1・U2 の結合テストはどちらも3種類で流す。
- **axe（US4.1）**: 既存の画面の axe と axe-core の引き上げは U4、業務データの画面の axe は U3。

## 単位の中のストーリーの順（単位の中で作る順の目安）

- U1: US3.1（出し分けの関数）→ US3.6（SQL の部品と方言）→ US1.2・US1.3（一覧と検索）→ US1.4（詳細）→ US3.2・US3.3・US3.5（判定の順・拒否・認可）
- U2: US2.1（作成）→ US2.4・US2.5（検証・一意）→ US2.2（更新）→ US2.3（削除）→ US3.4（監査）
- U3: US1.1（一覧の画面と道）→ US1.5（値の表示）→ 一覧の検索・ページ（US1.2・US1.3 の画面）→ 詳細（US1.4）→ 作成・更新・削除の画面（US2.1〜US2.4）
- U4: US4.1（固定先 → 名前の関数と既存の一覧 → トップバー → axe-core の引き上げ）
- U5: ストーリー無し（FR15・FR16）

## 網羅の確かめ

- 17 件のストーリーはすべて主の単位を持つ。
- U1〜U4 はどれも主のストーリーを持つ。U5 はストーリーを持たず、要件の FR15・FR16（User Stories で Code Generation・Build and Test へ Deferred にしたもの）を受け持つ。
