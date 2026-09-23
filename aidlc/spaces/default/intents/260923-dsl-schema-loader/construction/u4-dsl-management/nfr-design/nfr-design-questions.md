# NFR Design — U4 DSL の管理（u4-dsl-management）— 質問

この段では、U4 の非機能の要件（`construction/u4-dsl-management/nfr-requirements/`）を満たす作りの方針を決めます。作りの選び方が分かれる3点を尋ねます。ほかの点は、答えの後の要点の確認で示します。

すでに決まっていること:
- 投入の API（`POST /api/admin/dsl/preview`）だけ本文の上限 10MB、ほかの API は 1MB（NFR2.6）。今は全要求に1つの上限（`mastersmith.web.max-request-body-size`、既定 1MB）を当てる仕組みがある
- 重い処理（生成・投入・戻し・プレビューの表示）はアプリ全体で同時に1つ。重なった要求は待たずに 503 `DSL_BUSY`（NFR1.13・NFR1.14）
- DSL の識別は本文のバイト列の SHA-256（U2）。ダウンロードは保存した本文をそのまま返す（ADR-003）

---

## Q1. 投入の API だけ本文の上限を 10MB にする作り

A. 既存の本文の上限の仕組みに「道ごとの上限」の表を足し、`POST /api/admin/dsl/preview` だけ 10MB にする（上限の値は `mastersmith.dsl.max-submit-size`、既定 10MB）。超えたときの応答は、この道だけ `DSL_TOO_LARGE`、ほかは今までどおり
B. 既存の仕組みからこの道を外し、DSL の投入の道だけの上限の仕組みを別に置く（仕組みが2つになる）
X. Other (please specify)

[Answer]: A

## Q2. 重なった要求を断る場所

10MB の本文は、読み込むだけで 10MB 以上のメモリを使います。断る要求の本文は読まずに断りたいところです。

A. 画面入出力の層の手前（要求を受けて本文を読む前）で、同時に1つの許可を取る。取れなければ本文を読まずに 503 `DSL_BUSY`。許可は応答を返し終えたときに返す
B. 業務処理の層で許可を取る（本文は読んだ後になる。作りは単純）
X. Other (please specify)

[Answer]: A

## Q3. DSL の本文を内部DB に入れる型

A. バイト列（H2 の `BINARY LARGE OBJECT`）として、受け取ったバイト列のまま入れる（識別とダウンロードがバイト単位で必ず一致する）
B. 文字列（H2 の `CHARACTER LARGE OBJECT`）として入れる（DB の中で読みやすいが、改行や文字の表し方の違いでバイト列が変わるおそれがある）
X. Other (please specify)

[Answer]: A

---

## Consolidated Summary Confirmation

- **本文の上限**（Q1: A）: 既存の本文の上限の仕組みに道ごとの上限を足し、`POST /api/admin/dsl/preview` だけ 10MB（`mastersmith.dsl.max-submit-size`、既定 10MB）。超えたらこの道だけ 413 `DSL_TOO_LARGE`（受け付けなかった投入の監査 SIZE_LIMIT も出す）、ほかは既存の 413
- **重なりを断る場所**（Q2: A）: DSL の API の重い道（生成・投入・戻し・プレビューの表示）に、画面入出力の層の手前で同時に1つの許可（`Semaphore`、待たない）を取る仕組みを置く。取れなければ本文を読まずに 503 `DSL_BUSY`、状態を変えず監査も出さない。許可は応答を返し終えたときに必ず返す（失敗・例外のときも）
- **本文の型**（Q3: A）: プレビューと履歴の表の本文は `BINARY LARGE OBJECT`（受け取ったバイト列のまま）。承認済みの U4 の機能設計の entities.md（`yamlText` は text）は書き換えず、差を記録する
- **性能**: プレビューの表示のたびに、プレビューの本文を U2 で読み（最大 10MB）、適用中は保持しているモデル（U2 の ActiveDslModelHolder）を使って違いを求める（適用中を読み直さない）。照合は U1 の `readSchema(COMPARE)`。プレビューの読み込みの結果は、同じ `previewId` の間だけアプリの中に1つ持ち、表示のたびに読み直さない（プレビューを置き換え・破棄・適用したら捨てる）
- **照合の時間**: 照合の全体の上限は置かない。対象DB が応答しないときは最悪 23〜28 秒かかり、10 秒の目標（NFR1.8）を超えることを許す（U1 の NFR 設計のレビュー R-01 への依頼者の決定 B。承認の場で記録する）
- **信頼性**: 適用は1つのトランザクション（`@Transactional` は業務処理の層だけ）で、確定の後に適用中のモデルの差し替えと監査の出来事（既存の確定の後に記録する仕組み）。プレビューを置くのは固定の鍵の1行への MERGE。起動時は適用中を読み、読めなければ ERROR 1件で「無い」
- **観測**: `mastersmith.dsl.operation` の Timer（タグ `operation`・`outcome` の決まった語だけ）。操作ごとに INFO のログ1件（キーと値、識別は先頭 12 文字）。ダッシュボード `docker/monitoring/dashboards/` に DSL の操作の1枚（件数・結果・95 パーセンタイル）を足す。式は起動して確かめてから書く
- **成果物**: U4 は service のため、performance・security・scalability・reliability・observability の各 design.md、logical-components.md、traceability.json を作る

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct

---

## 確認の後の直し（2026-09-24、NFR Design の Request Changes）

上の要点の「本文の上限」にある「超えたらこの道だけ 413 `DSL_TOO_LARGE`（受け付けなかった投入の監査 SIZE_LIMIT も出す）」と「重なりを断る場所」の本文の扱いは、レビューの指摘（既存の大きさの確かめはログインの前で動き、操作した人が分からない）を受け、依頼者の Request Changes（A）で次のとおり変えた。正は security-design.md 1節・4節・5節。上の確認の記録は書き換えずに残す。

- 本文の上限は、既存の `RequestSizeLimitFilter` に道ごとの上限と code を足して確かめる（投入の道は 10MB・`DSL_TOO_LARGE`）
- 413 で断った投入は監査に記録しない（WARN のログだけ）。承認済みの機能設計 BR7.4・ストーリー AC6.3.1 との差として記録した

## 確認の後の直し その2（2026-09-24、2回目の Request Changes）

依頼者の決定で、`RequestSizeLimitFilter` を認証（JWT の検証）と認可の後に置くことにした。そのため、上の「その1」の「413 で断った投入は監査に記録しない」は取り消し、413 も操作した人つきで監査に記録する（承認済みの機能設計 BR7.4・ストーリー AC6.3.1 のとおり）。ログインしていない大きな要求は 401 になる。正は security-design.md 1節・4節・5節。
