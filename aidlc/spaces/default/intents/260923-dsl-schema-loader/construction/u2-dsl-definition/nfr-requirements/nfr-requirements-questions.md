# NFR Requirements — U2 DSL の定義（u2-dsl-definition）— 質問

この段では、U2 の非機能の要件（数値の目標と守る決まり）と、YAML・JSON Schema の部品の選定を決めます。U2 の受け持ちは、要件の NFR2（読み込みの大きさの上限）、NFR3（投入された DSL は信頼できない入力）、NFR5（部品の例外の文言を応答に含めない）と、NFR1 の一部（10 秒の内の読み込みと検証）です（`inception/units-generation/unit-of-work.md`）。

すでに決まっていること:
- 5MB を超えたら読む前に止める。入れ子の深さ・別名の数に上限を置き、任意の型を作るタグを拒否し、重複キーを誤りにし、JSON Schema は同梱のものだけで外部を取りに行かない（`construction/u2-dsl-definition/functional-design/rules.md` の BR1.1〜BR1.6、`aidlc/spaces/default/memory/team.md` の Code Style）。上限の数値はこの段で決め、決めた値の境界でテストする（team.md の Testing Posture）。
- 誤りの行・列は、YAML の位置の対応表から求める（ADR-008）。部品の選定では、この対応表が作れることを条件にし、この段で小さく試して確かめる（`inception/delivery-planning/risk-and-sequencing-rationale.md`）。確かめられなければ、行・列を諦めてパスだけを示す案を依頼者に諮る。
- 新しい依存を足すときはライセンスを確かめ、Apache License 2.0 と異なるものは採用の理由を記録する。

部品の候補（Maven Central で 2026-09-23 に確かめた最新の版）:
- **networknt json-schema-validator 3.0.7**（Apache License 2.0）: Jackson 3（`tools.jackson`、既存の構成と同じ）で動く。YAML を位置つきで読む仕組み（値ごとの行・列を記録する読み込み）を持つ。YAML の読み込みは Jackson 3 の YAML 形式（`jackson-dataformat-yaml` 3.x、Apache License 2.0）を使う
- **json-sKema 0.31.0**（MIT）: YAML を SnakeYAML（既存の推移依存と同じ 2.6）で読み、誤りに元の行・列を付ける仕組みを持つ。Kotlin の標準ライブラリに依存する
- **SnakeYAML 2.6**（Apache License 2.0、既存の推移依存）: 深さ・別名の数・重複キー・タグの制限の設定を持つ。行・列の位置を値ごとに持つ

---

## Q1. 読み込みの上限の数値

想定の規模（100 テーブル × 100 カラム）の DSL の YAML の入れ子の深さは 10 前後（メニューの階層を含む）です。別名（アンカー）は、共通のカラムの定義を使い回すときに使えます。

A. 入れ子の深さ 50・別名の数 100（想定より十分に大きく、爆発は防げる）
B. 入れ子の深さ 30・別名は使わせない（別名を1つでも使えば ALIAS_LIMIT。いちばん安全だが、作者が定義を使い回せない）
C. 入れ子の深さ 100・別名の数 1000（ゆるめ）
X. Other (please specify)

[Answer]: A

## Q2. U2 の読み込みと検証の時間の目標

プレビューへの読み込みと検証は、対象DB との照合を含めて 10 秒以内が目標です（NFR1）。照合（U4）は対象DB の問い合わせを含みます。

A. U2 の読み込み・検証・モデル作りは、想定の規模の DSL で 3 秒以内（照合と保存に残りを使う）。Build and Test で測る
B. 5 秒以内（照合の時間が短くなる）
X. Other (please specify)

[Answer]: A

## Q3. 部品の選び方（試す順）

A. networknt json-schema-validator 3 ＋ Jackson 3 の YAML 形式を先に試す（いずれも Apache License 2.0、既存の Jackson 3 とそろう）。読み込みの上限・タグ・重複キーを Jackson の YAML の設定で守れない分は、先に SnakeYAML の制限つきの読み込みで確かめる。位置の対応表が作れなければ json-sKema を試す
B. json-sKema を先に試す（行・列を付ける仕組みがそのまま使えるが、MIT と Kotlin の標準ライブラリが増える）。だめなら networknt を試す
X. Other (please specify)

[Answer]: A

## Q4. JSON Schema の版

A. 2020-12（最新の版。候補の部品はどちらも対応）
B. draft-07（エディタの対応が広い古い版）
X. Other (please specify)

[Answer]: A

## Q5. JSON Schema を DSL の作者に配る方法（要件の OQ6）

A. 同梱の正本（`backend/src/main/resources/` の下）をそのまま公開リポジトリで参照してもらい、README に置き場とエディタでの使い方を書く（新しい API は作らない）
B. 管理画面からダウンロードできるようにする（API と画面の働きが増える）
C. この Intent では配らない（投入したときの誤りの一覧だけで応える）
X. Other (please specify)

[Answer]: B. static コンテンツとして同梱。

## Q6. 位置の対応表の試し方（ADR-008）

A. U1 と同じく、使い捨ての作業ブランチで試しのテストを動かし、結果を tech-stack-decisions.md に記録して試しのコードは捨てる（本物は B1 で作る）
B. 試しのテストを残して B1 で本物に取り込む
X. Other (please specify)

[Answer]: A

---

## 追加の質問

## F1. JSON Schema の静的なファイルの公開の範囲と入り口（Q5 の答え「static コンテンツとして同梱」の細部）

検証に使う同梱の正本と同じファイルを、ビルドのときにアプリの静的なファイル（WAR の中）としても置き、URL で取れるようにします（正本は1つで、ビルドで複写する。API は作らない）。決まっていないのは、誰が取れるかと、どこから入るかです。JSON Schema に秘密は含まれません。

A. ログインしていなくても取れる静的なファイル（例: `/dsl/dsl-schema-v1.json`）にし、DSL の管理画面にダウンロードのリンクを置く。README にも URL を書く
B. A と同じ公開の範囲で、画面のリンクは置かず README に URL だけを書く（画面の変更が無い）
C. 管理者だけが取れるようにし、DSL の管理画面にリンクを置く（静的なファイルにアクセス制御を足す）
X. Other (please specify)

[Answer]: A

---

## 試しの結果（Q6: A、ADR-008）

使い捨ての作業ブランチで、networknt json-schema-validator と SnakeYAML を足した試しのテスト（12 件、すべて通過）を動かし、次を確かめた。試しのコードとブランチは捨てた。

- **networknt の版**: 最新の 3.0.7 は Jackson 3.2.1 を求め、アプリ全体の Jackson（Spring Boot の 3.1.5）を引き上げてしまう。3.0.6 は Jackson 3.1 系で動き、Spring Boot の版のままになる → **3.0.6 を使う**（Spring Boot を上げるときに見直す）
- **Jackson の YAML の読み込みは使えない**: 別名（`*name`）を展開せずに文字列にしてしまい（`use: *b` が `"b"` になる）、重複キーを黙って後の値で上書きする
- **SnakeYAML の制限つきの読み込み**（深さ 50・別名 100・重複キーの禁止・タグの禁止）: 深さ 51 は拒否、別名 101 は拒否、重複キーは2回目の位置つきで拒否、`!!` のタグと独自のタグ（`!custom`）はどちらも拒否された
- **位置の対応表**: SnakeYAML の読み込みの結果（節の木）から、検証に使う JSON の形と「パス → 行・列」の対応表を自分で作る方式で、JSON Schema の誤りのパスから行・列が正しく引けた（必須の欠け・知らない項目・型の違い）。別名で参照した値の誤りは、参照を書いた場所の行・列になった（BR4.2 のとおり）
- **別名の展開の爆発**: 展開した後の節の数に上限（例 200,000）を置くと、10 段の入れ子の別名（展開すると 10 の 10 乗）も約 45 ミリ秒で打ち切れた
- **速さ**: 100 テーブル × 100 カラム（約 0.68MB、節 50,503）の読み込み・変換・検証が 92〜210 ミリ秒（3 秒の目標に十分収まる）
- **外部の `$ref`**: 試しの HTTP の受け手を立てて確かめたところ、networknt 3.0.6 は既定でも外部を取りに行かなかった。念のため、取りに行かない設定（`fetchRemoteResources(false)`）を明示する

→ ADR-008 の条件は満たされた（行・列を示せる）。切り替え先（パスだけを示す）は使わない。json-sKema は試さなくてよい。

## Consolidated Summary Confirmation

- **読み込みの上限**（Q1: A）: 入れ子の深さ 50・別名 100・本文 5MB。加えて、別名を展開した後の節の数に上限を置く（500,000。想定の規模の約 10 倍）。タグ（`!!` と独自のタグ）はすべて拒否、重複キーは誤り
- **時間**（Q2: A）: 想定の規模で U2 の読み込み・検証・モデル作りは 3 秒以内（試しでは 0.2 秒）。Build and Test で測る
- **部品**（Q3: A と試しの結果）: YAML の読み込みは SnakeYAML 2.6（既存の推移依存を直接の依存にする）、JSON Schema の検証は networknt json-schema-validator 3.0.6（Apache License 2.0、推移依存の itu も Apache License 2.0）。位置の対応表と検証用の JSON の形は、SnakeYAML の節の木から自分で作る。Jackson の YAML の読み込みは使わない
- **JSON Schema の版**（Q4: A）: 2020-12。同梱の1つを正とし、外部を取りに行かない設定を明示する
- **配り方**（Q5: B「static コンテンツとして同梱」、F1: A）: 同梱の正本をビルドで静的なファイルに複写し、ログインなしで取れる URL（例 `/dsl/dsl-schema-v1.json`）で公開する。DSL の管理画面にダウンロードのリンクを置き、README にも URL を書く。※ 承認済みの U5 の機能設計にこのリンクは無い。設計の文書は書き換えず、差を U5 の NFR 要件とコード生成で扱う（project.md の Way of Working）
- **識別**: 本文のバイト列の SHA-256 を 16 進数（64 文字）で表す（BR5.1 の「アルゴリズムは NFR 設計」を、ここで決める）
- **成果物**: U2 は library のため、security-requirements.md（NFR2・NFR3・NFR5 の枝番）・tech-stack-decisions.md（上の選定と試しの結果、NFR1 の枝番）・traceability.json を作る

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
