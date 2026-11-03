# 作る単位（Unit of Work）— timezone-ui-polish

出典: 部品の一覧（`inception/domain-design/components.md`、16部品）、設計の決定（`decisions.md`、ADR-001〜009）、要件（`inception/requirements-analysis/requirements.md`）、ストーリー（`inception/user-stories/stories.md`、17件）、質問と答え（`inception/units-generation/units-generation-questions.md` の Q1: A・Q2: A と、計画の承認）。

## 方針

- **分け方**: 目的（タイムゾーン・メニューとホーム・アイコン素材）ごとにまとめ、純粋な変換（性質ベースのテスト）と対象DB の型の直し（3種類の結合テスト）は確かめ方が違うため別の単位にした（Q1: A）。
- **配備**: すべての単位は同じ実行可能 WAR に入る（埋め込み）。別々には配備しない（`team.md`）。
- **作る前提**: 依存の順に1つずつ作る。独立した単位は、依存の図に「並行できる」とだけ記録する（Q2: A）。作る順は Delivery Planning が決める。
- **コード知識ベースとの対応**: 部品の「備考」の列のとおり。

## 単位の一覧

| Unit ID | Directory | 名前 | 種類 | 複雑さ | 配備の形 |
|---|---|---|---|---|---|
| U1 | u1-time-zone-conversion | 時点・ローカル時刻の変換（画面の側） | library | M | 埋め込み（フロントエンドのビルドに入る） |
| U2 | u2-display-time-zone | 表示のタイムゾーンの設定（API・画面・管理の画面の時刻） | service | L | 埋め込み（アプリと画面の両方） |
| U3 | u3-target-db-time | 対象DB の日時（接続の時差・端数・型の名前） | library | M | 埋め込み（アプリに入る） |
| U4 | u4-business-data-time | 業務データの画面の時刻の表示・入力・検索 | ui | L | 埋め込み（フロントエンドのビルドに入る） |
| U5 | u5-menu-icons-home | メニューの印・アイコンの決め方・ホーム・管理の仮の画面の廃止 | ui | L | 埋め込み（アプリと画面の両方） |
| U6 | u6-app-icon-assets | アプリのアイコンと favicon の素材・ログインのアイコン | packaging | S | 埋め込み（`frontend/public/` のビルドの成果物） |

## U1 u1-time-zone-conversion

- **持つ部品**: TimeZoneConversion
- **責務**: 時点を表示のタイムゾーンの時刻と時差の文字に直す（秒の端数は桁のまま）、表示のタイムゾーンのローカル時刻を時点に直す（夏時間で存在しない・重なる・ひとつに決まるを判定して返す）、タイムゾーンの名前を今のブラウザが解釈できるかを返す、元の時点を保って往復させる。ブラウザの既定のタイムゾーンを暗に使わず、タイムゾーンを明示に受ける。置き場は `frontend/src/shared/format/`。
- **ストーリー**: US2.5 の往復、US2.1・US2.3・US2.4 の変換の部分、US1.2 の名前の解釈の部分、AC1.2.6。
- **実装の注意・制約**: 純粋な関数だけ（状態を持たない）。`fast-check` の性質ベースのテスト（変換して戻すと元の時点に戻る、時差の無い型は時差を変えても値が変わらない、など）と、夏時間・端数の時差の単体テストを持つ。テストはタイムゾーンを明示に指定し、機械の既定に預けない。
- **依存**: なし。

## U2 u2-display-time-zone

- **持つ部品**: UserPreferences・TimeZoneNamePolicy・DisplayTimeZoneContext・OtherScreenTimes
- **責務**: 利用者の設定に表示のタイムゾーンの列を足して保存・読み出し（本人だけ、一括代入の防止）、保存の要求の名前の照合、プリファレンスの画面のタイムゾーンの選択、表示のタイムゾーンの決定と配布（読み込み中・解釈できない名前の状態）、利用者の一覧・招待の一覧・DSL の状態と履歴の時刻を表示のタイムゾーンで見せる。
- **ストーリー**: US1.1、US1.2、US5.3 の一部（列を足すだけの互換）。
- **実装の注意・制約**: 内部DB の変更は利用者の設定の行に NULL 可の列を1つ足すだけ（前進のみ・前の版が読まずに動く）。サーバーの名前の照合は純粋な関数にして性質ベースのテストを付ける（`jqwik`）。認証・認可・監査の必須のテスト（`team.md`）のうち、本人だけが変えられること・未認証 401・要求の本文の改ざんを確かめる。プリファレンスの `Select` は make-you-chic-ui の部品を使う。
- **依存**: U1（変換の部品）。

## U3 u3-target-db-time

- **持つ部品**: TargetDbSession・BusinessDataValueCodec・DslDefaultGenerator
- **責務**: PostgreSQL の対象DB の接続（読み取り・書き込み）のセッションのタイムゾーンを UTC に明示する、日時の秒の端数の桁（既定の DSL の生成と読み書き）、桁つきの時差つきの型の扱い、読んだ値をそのまま送り返しても値が変わらない往復。確かめた結果が不具合でなければ直さず、結果を記録する（FR6.3）。
- **ストーリー**: US5.1、US2.2 の接続の時差（AC2.2.4）、US2.1 の読めない値（AC2.1.6）、US2.5 のサーバー側の往復、US5.3 の一部。
- **実装の注意・制約**: K-95・K-98 は未検証のため、NFR Design の捨ての試し（または Code Generation の前の確かめ）で先に確かめる。結合テストは MySQL・MariaDB・PostgreSQL の3種類で毎回流す（`team.md`）。型・端数・範囲の端・往復・性質ベースのテストの必須の項目に従う。既存の境界テスト（ArchUnit）と SpotBugs の除外は緩めない。
- **依存**: なし。

## U4 u4-business-data-time

- **持つ部品**: BusinessDataTimeView
- **責務**: 業務データの一覧・詳細・参照の選択肢で、時点を表す型の値を表示のタイムゾーンと時差で見せる（見出しの近くに表示のタイムゾーンの案内の1行）、作成・更新・検索の入力欄（`TextInput`、書式の案内）、夏時間の誤り（存在しない時刻）と重なる時刻の2択、読んだ値を変えて扱わない往復（主キー・同時更新の判定・参照の値は元の時点を保って送る）。
- **ストーリー**: US2.1、US2.2、US2.3、US2.4、US2.5、US5.2 の一部（業務データの画面）。
- **実装の注意・制約**: サーバーへ送るのは時点（時差つき）だけ（ADR-002）。画面の側の夏時間の拒否が、この Intent の拒否の唯一の場所になる。設定の読み込みの前は時刻の欄を出さない。axe は、誤りの状態・長い名前・ブランドカラーとテーマのすべての組で行う。E2E は、N6 の日時の代表の流れを1本まで足す（`team.md`）。
- **依存**: U1（変換）、U2（表示のタイムゾーンの配布）、U3（サーバー側の日時の値の読み書き）。

## U5 u5-menu-icons-home

- **持つ部品**: NavigationMenu・AppIconPolicy・WorkRoleSelection（読むだけ・変えない）・HomeEntrance・ShellFrame
- **責務**: メニューの応答の項目にテーブルかビューかの印を足す（足すだけ、見られる項目にだけ）、許すアイコンの名前の一覧（サーバーと画面の2か所）のそろえ、アイコンの決め方（DSL の `icon` → 種類の既定 → 取り込むまでの扱い）、管理の区画のアイコン（意味が近い3項目）、ホームの作業ロールと入口のカード（業務・管理）、サイドバーから管理の仮の項目と仮の画面を外す（廃止した道は「見つからない」）、上のバーのロゴの口の利用（取り込み後。Should）。
- **ストーリー**: US3.1、US3.2、US3.3、US4.2（ShellFrame の側）、US4.3、US4.4、US5.3 の一部（メニューの応答は足すだけ）、US5.2 の一部（ホーム）。
- **実装の注意・制約**: ホームは新しい API を呼ばない。管理の仮の画面を外すと、既存のテスト（E2E 030・090・130 と単体テスト）の書き換え・移しが要る（件数を Build and Test で記録。本数に数えない）。サーバー側の管理の権限の確認 API と 403 の判定は残す。上流（make-you-chic-ui）のアイコン（`mail`・`users`・`shield`・`table`・`view`・`folder`）の取り込みを待つ部分は、取り込むまで付けない・今の既定のまま（`make-you-chic-ui-request-8.md`）。`ShellFrame` のロゴは Should。
- **依存**: U6（アプリのアイコンの素材。上のバーのロゴ用）。

## U6 u6-app-icon-assets

- **持つ部品**: AppIconAssets・LoginFrame
- **責務**: 鍛冶の金床とハンマーの輪郭の1色のアイコンを自作の SVG で作る（アプリの中は `currentColor`、favicon は固定のブランドの色）、SVG の favicon と `.ico`・`.png` を `frontend/public/` に置き、`index.html` に favicon の `<link>` を足す、`/favicon.ico` に画像を返す、SVG にスクリプト・イベントの属性・外部の参照を入れない、先頭に Apache License 2.0 のヘッダーを置き検査の対象に `.svg` を足す、ログイン画面のアプリ名の上にアイコンを置く（飾り）。
- **ストーリー**: US4.1、US5.2 の一部（ログイン）。
- **実装の注意・制約**: `frontend/index.html`・`frontend/public/` は「frontend の本体」にあたり、統合の前に E2E を流す（`team.md`）。`.ico`・`.png` は元の SVG から作る手順を README か記録に書く。
- **依存**: なし。

## 横断の決まり

| 決まり | 受け持つ単位 |
|---|---|
| アクセシビリティ（US5.2）: 実際のブラウザの axe を、ブランドカラーとテーマのすべての組で、誤りの状態・長い名前・展開した状態で流す | U2（プリファレンス）・U4（業務データの画面）・U5（ホーム・サイドバー）・U6（ログイン） |
| 前の版との互換と戻し（US5.3） | U2（列を足すだけ）・U3（接続のタイムゾーンの変更の前後で値が変わらない）・U5（メニューの応答は足すだけ） |
| 要件との差（ADR-002） | U4（画面の側だけで夏時間の存在しない時刻を拒否する）。承認済みの要件・ストーリーは書き換えず、各単位の成果物に差を記録する |

## Sources

- Initial description: N6（業務データの日時のタイムゾーンの変換）と画面の仕上げの Intent [desc]
- Workflow-selected scope: classic [scope]
- 部品の一覧と決定: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/domain-design/components.md`・`decisions.md`
- 質問と答え: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/units-generation/units-generation-questions.md`（Q1: A・Q2: A）
- 進め方: `aidlc/spaces/default/memory/team.md`・`project.md`

## Assumptions & Open Questions

- [assumption] 各単位の複雑さ（S/M/L）は、今のコードの量の見立て。Delivery Planning で見直す。
- 上流（make-you-chic-ui）の取り込みの時期は、Delivery Planning で決める。
