# Reliability Design — U8 インスタンスの見た目の設定（u8-instance-appearance）

U8 の障害のときのふるまいと、内部DB に依存しないことの設計です。答えは `nfr-design-questions.md`（Q2: A、Consolidated Summary Confirmation: Looks correct）。

出典の略号: NFR はこの単位の NFR 要件 `construction/u8-instance-appearance/nfr-requirements/` の枝番（枝番はこの単位の中で振る）、BR は `construction/u8-instance-appearance/functional-design/rules.md`、「要点 n」は `nfr-design-questions.md` の「NFR 設計の要点（案）」の番号、U4 W2・W3 は `construction/u4-display-foundation/functional-design/functional-spec.md`。

## 1. 考え方: 失敗の道を作らない

U4 W2 のとおり、画面はこの API の答えが出るまで最初の画面を描かず、待ちに上限を置かない。U8 には「速く・必ず答える」ことが要る。そのため、失敗に備える仕組みを足すのではなく、失敗しうる依存を持たない作りにする。

| 仕組み | 当てるか | 理由 |
|---|---|---|
| サーキットブレーカー・再試行・時間切れ・隔壁 | 当てない | 外部の呼び出し・内部DB・ほかの単位への依存が無く、当てる相手が無い（要点 5） |
| 代わりの値（フォールバック） | 起動時の判定で当てる | 設定が無い・許されない値のときは既定（blue・sans）を採る（BR1.3・BR1.4）。要求の処理には代わりの値が要る失敗が無い |
| 健全性の判定（HealthIndicator） | 足さない | 起動の後に失敗しうる依存を持たない。健全性は既存の `/actuator/health` のまま（NFR9.6） |

## 2. 起動を止めない・必ず答える

- 設定の型は2項目を文字列で受け、列挙の型や検証の注釈（`@Validated`）に結び付けない。値が何でも Spring の結び付けで起動が止まらない（NFR9.1、BR1.7）。
- 判定の関数は、どの入力（`null`・空・空白だけ・任意の文字列）でも許される値と「警告の有無」を返す全域の関数とし、例外を投げない（NFR9.2）。
- 要求の処理は保持した値を写すだけで、想定内の業務のエラーを返さない。エラーの `code` の一覧（`XxxProblemTypeCatalog`）は作らない。想定外の失敗は既存の共通のエラー応答（500 / `INTERNAL_ERROR`、内部の例外のメッセージを載せない）に任せ、画面は前に当てた値か既定のまま描く（NFR9.2、U4 W3）。

確かめ方:

| 要件 | 確かめ方 |
|---|---|
| NFR9.1 | 結合テスト: ブランドカラーに `red`、フォントファミリーに `mono` を設定して起動し、GET が 200 で `blue`・`sans` を返す。設定なしでも起動して同じ値を返す |
| NFR9.2 | 単体テスト（判定のどの結果でも、返す2つの値が許される値のどれか）と、`logical-components.md` の5節の jqwik の性質ベースのテスト（NFR9.8） |

## 3. 内部DB に依存しないこと

### 3.1 構造の検査（NFR5.1）

機能ごとの境界テストの前例（`backend/src/test/java/cherry/mastersmith/auth/AuthBoundaryArchitectureTest.java` など）どおり、`backend/src/test/java/cherry/mastersmith/appearance/AppearanceBoundaryArchitectureTest.java` を足す。既存の `ArchitectureTest`（全体の層の決まり）は変えず、緩めない。

| 決まり | 対象 |
|---|---|
| `appearance..` は DB アクセスに依存しない | `..repository..`・`javax.sql..`・`jakarta.persistence..`・`org.springframework.jdbc..`・`org.springframework.data..`・`org.springframework.transaction..`・`com.zaxxer.hikari..` |
| `appearance..` はほかの機能に依存しない | `cherry.mastersmith` の下で `common..` と `appearance..` の外（`auth`・`access`・`user`・`audit`・`dsl`・`dslmanage`・`targetdb`・`invitation` などの機能） |
| ほかの機能は `appearance..` に依存しない | `appearance..` の外の `cherry.mastersmith` のクラス（`config` を含む） |

### 3.2 接続を借りないことの結合テスト（NFR5.2、Q2: A）

- トークンを付けない `GET /api/appearance` の前と後で、内部DB の接続を借りた回数を比べ、増えていないことを確かめる。回数は HikariCP の指標 `hikaricp.connections.acquire`（プールの名前のタグつきのタイマー）の件数を `MeterRegistry` から読む。既存の結合テストが `MeterRegistry` を読む前例（`common/observability/ExternalExportIT.java`）がある。
- テストの文脈で指標が取れないときは、テストの中だけで `DataSource` を包んで `getConnection` の回数を数える仕組みに替える（本番のコードは変えない）。どちらにしたかはコード生成で記録する。
- 既存の結合テストと同じ Spring の文脈で動かし、プールの一時停止のための専用の文脈（MBean の登録の有効化）は作らない。借りた回数が増えなければ、プールが尽きたときや詰め直しの一時停止のあいだも内部DB の待ちで遅れることは無い。
- 要求の全体（フィルターの連鎖の認証・認可の段階を含む）を1回の要求として数えるため、MockMvc か実際の HTTP で要求を送る。比べる前に、ほかの要求や起動の直後の処理が終わっていることを待ち合わせ、同じ文脈で並んで動くテストの要求が混ざらない形にする（具体はコード生成）。

## 4. 失敗のときのふるまい

| 場合 | ふるまい | 要件 |
|---|---|---|
| 設定が無い・空・空白だけ | 既定で起動し、警告は出さない | NFR9.1、BR1.3 |
| 設定が許されない値 | 既定で起動し、項目ごとに警告のログを1件出す | NFR9.1、BR1.4、NFR9.4 |
| 内部DB の接続プールが尽きた・詰め直しの一時停止中 | 影響を受けずに答える（トークンを付けない要求） | NFR5.1・NFR5.2 |
| 使えないトークンを付けた要求 | 既存の扱いで 401（画面はトークンを付けない） | NFR4.4 |
| 想定外の失敗 | 既存の共通のエラー応答で 500。画面は前に当てた値か既定のまま描く | NFR9.2、U4 W3 |

## 5. SLO と可用性

可用性の目標はアプリ全体と同じとし、U8 だけの SLO は置かない。手元の監視を常に動かしていない間は、SLO の判定を `Unverified` とし、持ち主の段（observability-setup・feedback-optimization）に引き継ぐ。目標を緩めて満たしたことにはしない（NFR9.3）。SLI は `observability-design.md` の4節。

## 6. 戻しとバックアップ

- U8 は内部DB の表・移行・保存するデータを持たず、バックアップの対象は無い。戻しは既存の決まり（直前の版の成果物での再配備）のまま。直前の版には `/api/appearance` が無く、画面は失敗として既定のまま描く（U4 W3）。
- 設定の変更の戻しは、`.env` の2項目を元に戻してアプリを起動し直す。

## 7. 上流との差

承認済みの文書と食い違う設計は無い。NFR5.2 の「測り方の具体はコード生成で決める」を、この段の依頼者の決定（Q2: A）で「借りた回数の比べ、既存の結合テストと同じ文脈」まで決めた（追加）。指標と数える包みのどちらを使うかはコード生成に残す。
