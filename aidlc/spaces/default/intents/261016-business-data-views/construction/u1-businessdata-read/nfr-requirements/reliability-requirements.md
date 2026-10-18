# 信頼性の要件 — U1 u1-businessdata-read

## 出典

- 要件 `aidlc/spaces/default/intents/261016-business-data-views/inception/requirements-analysis/requirements.md`（NFR3・NFR4.4、FR11.3・FR13.3）
- ストーリー `inception/user-stories/stories.md`（AC1.2.4・AC1.3.9・AC1.5.7・AC3.3.4・AC3.6.3・AC3.6.5）
- この単位の機能設計 `construction/u1-businessdata-read/functional-design/`（`rules.md` の BR1.8・BR4.7・BR5.5・BR7.3・BR7.7〜BR7.9・BR8.1・BR8.5・BR8.6）
- この単位の機能設計のレビュー `.aidlc-reviews/functional-design/units/u1-businessdata-read/7dacbaa3e842d0af/1.json`（R-07・R-08）
- ADR-007（`inception/domain-design/decisions.md`。NULL の並び・部分一致のエスケープ・時差つきの型）
- この段の答え `construction/u1-businessdata-read/nfr-requirements/nfr-requirements-questions.md`（Q3: A・Q7: A、まとめの確認 Looks correct）
- コード: `Dockerfile`（`-Duser.timezone=Asia/Tokyo`）、`backend/src/main/java/cherry/mastersmith/targetdb/service/JdbcTargetSchemaReader.java`（対象DB の失敗を理由に分けて返す今の形）
- `project.md` の学び（応答しない対象DB は、テストの中で開いた ServerSocket で作る）

## ID の対応表

全体の表は `security-requirements.md` の「ID の振り方」にある。

| 上流の ID | この単位での扱い | この単位の ID |
|---|---|---|
| NFR3 対象DB | 当たる（振る舞い。性能は `performance-requirements.md` の NFR3.2） | NFR3.1・NFR3.3 |
| NFR4.4 時間の上限 | 当たる（値は `security-requirements.md` の NFR4.4。この文書は時間切れと失敗の返し方） | NFR4.8 |

## 要件

| ID | 要件 | 確かめ方 | 持ち主の段 |
|---|---|---|---|
| NFR3.1 | MySQL・MariaDB・PostgreSQL の3種類で、定義・一覧・検索・詳細が同じ振る舞いになる（部分一致の大文字と小文字の扱いを除く。FR3.4）。含めるもの:<br>・型の違い・識別子の大文字と小文字<br>・NULL の並び（昇順で最後・降順で最初。BR5.5）<br>・部分一致の `%`・`_`・`\`・`!` は文字として扱う（AC1.2.4）<br>・境界の値（`BIGINT` の上限と下限、`DECIMAL(38,10)` の桁の多い値、NULL）を1桁も変えずに文字で返す（AC3.6.5） | 版を固定したイメージの3種類の DB の結合テストを、`./gradlew verify` の中で毎回流す（CI も同じ。H2 やモックで代用しない） | Code Generation（B2） |
| NFR3.3 | 時差つきの型（PostgreSQL の `timestamptz`・`timetz`、MySQL・MariaDB の `timestamp`）の検索の値と主キーの値（R-08、Q7: A）:<br>・オフセットが無い値は UTC とみなす<br>・`Z` と `+hh:mm`・`-hh:mm` の付いた値も受け付け、UTC にそろえてバインドする<br>・返す値は UTC にして `+00:00` を付ける（BR7.3）<br>・アプリのタイムゾーン（`-Duser.timezone=Asia/Tokyo`）と接続のタイムゾーンに左右されない<br>時差の無い型（`DATE`・`DATETIME`・`TIME`）は、タイムゾーンを足さず DB の値のまま扱う | 3種類の DB の結合テスト: オフセットの無い値・`Z`・`+09:00` の値で同じ行に当たること、返す値が `+00:00` であること、JVM の既定のタイムゾーンを変えても結果が同じであること | NFR 設計の試しでやり方を決め、Code Generation（B2）で確かめる |
| NFR4.8 | 対象DB に接続できない・答えないときは、目的ごとの時間の上限（NFR4.4）の中で業務データの機能の誤りとして返す。<br>・接続できない・読み取りの接続の設定が無い → 503 `BUSINESS_TARGET_DB_UNAVAILABLE`<br>・時間切れ（件数の問い合わせを含む）→ 503 `BUSINESS_TARGET_DB_TIMEOUT`<br>・応答しない対象DB では、借りる待ち 3 秒と一覧の問い合わせ 2 回分（5 秒 × 2）の和、およそ 13 秒以内に返す<br>例外の通り道（R-07）: 処理の中で出た `SQLException` と想定外の失敗は、メッセージを持ったまま貸す口の外へ出さない。受けた所で理由（UNAVAILABLE・TIMEOUT・MISMATCH・型の誤り）に変えて返す（BR8.5）。プールの借りる待ちの時間切れも UNAVAILABLE に写す | 応答しない対象DB（テストの中で開いて何も返さない ServerSocket）と接続できない対象DB の結合テストで、状態コード・code・応答までの時間を確かめる（AC3.3.4）。応答とアプリのログに JDBC の例外のメッセージ・接続先・行の値が無いこと（NFR4.2） | NFR 設計で口の置き場を決め、Code Generation（B2）で確かめる |

## 補足

- 可用性の目標（SLO）は置かない。配備先が手元のコンテナに限られるため、`project.md` の学びのとおり、運用の指標は Observability Setup で基準の値を並べる。
- 対象DB の失敗は、業務データの API を失敗させるだけで、アプリ全体の健全性（`/actuator/health`）には含めない（今の DSL の照合と同じ扱い）。

## 上流との差

- 承認の場の決定（`construction/functional-design/memory.md` の Open questions）: `nullable` は U2 が足す検証の材料とし、`formPart` の値域から `lookup` を外し、U3 は `nullable`・`required` が欠けたら形の崩れとする。U1（B2）は `formPart`・`nullable` を返さないため、この単位の信頼性の要件に差は無い。
- R-07: レビューの推奨は「コード生成の計画で決める」だったが、この段では「例外のメッセージを口の外へ出さず、理由に変えて返す」という要件だけを NFR4.8 に置いた。口の置き場は NFR 設計に回す。
- R-08: レビューの推奨は「NFR 設計の試しで決める」だったが、利用者に見える振る舞いのため、この段の Q7 で依頼者が決めた（オフセットが無い値は UTC）。NFR3.3 に置き、やり方だけを NFR 設計の試しに回す。`rules.md` の BR4.7 に、受け付ける書式と省略時の扱いを足す必要がある（差としてここに書く）。

## 後の段に回すこと

- **NFR 設計の試し**:
  - 時差の扱い: 接続のタイムゾーンを UTC にするか、JDBC の型（`OffsetDateTime`）で受け渡すか。MySQL・MariaDB の `timestamp` の渡し方を含む（ADR-007）。
  - OTHER の値の渡し方が3種類の DB で通るか（BR7.9 の切り替え先）。
  - 値の型の誤りの SQLState の表を、3種類の DB で実際に起こして確かめる（BR8.6）。
- **NFR 設計（論理の部品）**: 口の置き場（R-07）。例外を口が包んで返すか処理の側で捕えるかと、`TargetReadSession` を `targetdb.service` と `targetdb.domain` のどちらに置くかを決め、BR8.5・BR11.2 に1行足す。
- **Performance Validation**: NFR4.8 は負荷の試験の対象にしない（結合テストで確かめる）。
- **Observability Setup**: 503 の code ごとの区画（`observability-requirements.md` の NFR8.3）。

## U2・U3 とそろえる点

- U2 は `BUSINESS_TARGET_DB_UNAVAILABLE`・`BUSINESS_TARGET_DB_TIMEOUT`・`BUSINESS_TARGET_DB_MISMATCH` を使い回し、書き込みの口も同じ貸す形と同じ例外の通り道（NFR4.8）にする。
- U2 の時差つきの型の入力の値も、NFR3.3 と同じ読み方（オフセットが無い値は UTC）にする。U3 の画面の入力と表示も UTC の値をそのまま扱う。
- U3 は 503 の2つの code と 409 の `BUSINESS_TARGET_DB_MISMATCH` の文を出し分ける（`functional-spec.md` 8節）。
- 枝番: U2・U3 の信頼性の要件で NFR3 に足すものは NFR3.4 以降に振り、U1 と重ねない。
