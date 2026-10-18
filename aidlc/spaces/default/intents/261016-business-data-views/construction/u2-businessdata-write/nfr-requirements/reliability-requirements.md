# 信頼性の要件 — U2 u2-businessdata-write

## 出典

- 要件 `aidlc/spaces/default/intents/261016-business-data-views/inception/requirements-analysis/requirements.md`
  - NFR3、NFR4.4、FR7.2、FR10.3、FR13.3
- ストーリー `inception/user-stories/stories.md`
  - AC2.2.3、AC3.2.6、AC3.3.4、AC3.3.5、AC3.4.3
- この単位の機能設計 `construction/u2-businessdata-write/functional-design/`
  - `rules.md`: BR6.1〜BR6.4、BR8.1・BR8.2、BR9.3、BR10.1、BR11.3
  - `functional-spec.md`: 2節の ⑩、11.2 の R-05
- 機能設計のレビュー `.aidlc-reviews/functional-design/units/u2-businessdata-write/7dacbaa3e842d0af/1.json`
  - R-05、R-08
- ADR-007 `inception/domain-design/decisions.md`
  - SQLState の見分け、時差つきの型
- U1 の NFR 要件 `construction/u1-businessdata-read/nfr-requirements/reliability-requirements.md`
  - NFR3.3（時差）、NFR4.8（例外の通り道）
- この段の答え `construction/u2-businessdata-write/nfr-requirements/nfr-requirements-questions.md`
  - Q1: A、まとめの確認は Looks correct
- `project.md` の学び
  - 応答しない対象DB は、テストの中で開いた ServerSocket で作る

## ID の対応表

全体の表は `security-requirements.md` の「ID の振り方」にある。

| 上流の ID | この単位での扱い | この単位の ID |
|---|---|---|
| NFR3 対象DB | 当たる（振る舞い。性能は `performance-requirements.md` の NFR3.4） | NFR3.5・NFR3.6 |
| NFR4.4 時間の上限 | 当たる（値は `security-requirements.md` の NFR4.4。この文書は時間切れと失敗の返し方） | NFR4.12・NFR4.15 |

## 要件

### NFR3.5 3種類の DB で同じ振る舞い

**要件**: MySQL・MariaDB・PostgreSQL の3種類で、作成・更新・削除を同じ振る舞いにする。

- **件数**: 一致した行で数え、今と同じ値の更新も1行と数える。MySQL・MariaDB の書き込みの接続では `useAffectedRows` を真にしない（BR8.2）。
- **2行以上に当たったとき**: 更新・削除が2行以上に当たったら、巻き戻して 409 `BUSINESS_ROW_NOT_UNIQUE` にする。0 行のときは 404 にする（BR8.1、AC3.2.6）。
- **制約の違反**: 誤りの番号（SQLState・業者の番号）で種類を分け、例外の文は読まない（BR6.1）。
- **丸めない**: 境界の値は丸めずに書き、読み直して1桁も変わらないことを確かめる。対象は `BIGINT` の上限と下限、`DECIMAL(38,10)` の桁の多い値、NULL。
- **後勝ち**: カラムごとに、後から保存した人の値になる（AC2.2.3）。

**確かめ方**

- 版を固定したイメージの3種類の DB で結合テストをする。
- 書いた後の値は、アプリとは別の接続で読み直す。
- テストのクラスごとのスキーマを作り、終わったら消す。
- `./gradlew verify` の中で毎回流す。

**持ち主の段**: NFR 設計の試し（件数の設定、SQLState の表）、Code Generation（B4）

### NFR3.6 時差つきの型の入力

**要件**

- 時差つきの型の入力の値は、U1 の NFR3.3 と同じに扱う。
  - オフセットの無い値は UTC とみなす。
  - `Z` と `±hh:mm` の付いた値も受け付ける。
  - UTC にそろえ、型つきでバインドする。
- アプリと接続のタイムゾーンに左右されないようにする。
- 時差の無い型は、DB の値のまま扱う。

**確かめ方**

- 3種類の DB で、オフセットの無い値・`Z`・`+09:00` の値を書き、別の接続で読み直して同じ時点であることを確かめる。
- JVM の既定のタイムゾーンを変えても、結果が同じであることを確かめる。

**持ち主の段**: NFR 設計の試し（U1 と合わせる）、Code Generation（B4）

### NFR4.12 失敗の分け方と例外の通り道（R-08）

**要件**: 対象DB の失敗は、保存されたかが分かるかどうかで分け、書き込みの時間の上限（文ごとに 5 秒）の中で返す（BR10.1・BR6.4）。

| 起きたこと | 応答 |
|---|---|
| 接続を借りられない、書き込みの文を送る前の失敗、書き込みの設定はあるが接続できない | 503 `BUSINESS_TARGET_DB_UNAVAILABLE`（保存されていない） |
| 書き込みの文を送った後の時間切れ・通信の断絶・確定の失敗 | 503 `BUSINESS_TARGET_DB_TIMEOUT`（確定したか分からない） |
| 未定義の表・カラム | 409 `BUSINESS_TARGET_DB_MISMATCH` |
| 違反の後の確かめの失敗・時間切れ | 409 `BUSINESS_SAVE_REJECTED`（カラムを示さない。書き込みは巻き戻し済み） |

- **例外の通り道**: `SQLException` と想定外の失敗は、メッセージを持ったまま書き込みの口の外へ出さない。受けた所で理由に変えて返す（U1 の NFR4.8 と同じ通り道）。
- **口が貸す型**: 書き込みの口は、DB の種類を持つ型を処理に貸す（U1 の `TargetReadSession` と同じ形）。BR6.1 の誤りの番号の分類と BR6.2 の確かめの SQL は、その種類を使う（R-08）。
- **最悪の時間**: 応答しない対象DB では、借りる待ち 3 秒で UNAVAILABLE を返す。文が答えないときも、文ごとの 5 秒で返す。1要求の最悪はおよそ 18 秒とする。

**確かめ方**

- 応答しない対象DB（何も返さない ServerSocket）と接続できない対象DB の結合テストで、状態コード・code・応答までの時間を確かめる（AC3.3.4）。
- 書き込みの文を送った後に通信を断つ結合テストで TIMEOUT になることを確かめる（形は NFR 設計で決める）。
- 応答とログに、JDBC の例外のメッセージ・接続先・行の値が無いことを確かめる。

**持ち主の段**: NFR 設計（型の名前と置き場、通信を断つ確かめの形）、Code Generation（B4）

### NFR4.15 時間切れの監査の読み方（R-05）

**要件**

- 書き込みの文を送った後の時間切れ（理由の値 `BUSINESS_TARGET_DB_TIMEOUT`）の監査の行は、「確定したかは分からない」と読めるようにする。
- 結果が FAILURE のまま「保存されていない」と誤って読まれないようにする。
- 次のどちらにするかは、NFR 設計で決める。
  - BR11.3 か `detail` に注記を書く
  - 結果の値に区別を足す
- 結果の値に区別を足すときは、次の2つを確かめる。
  - 監査の表の列の長さ
  - 既存の `AuditResult` の値と、それを読む箇所

**確かめ方**: 時間切れの監査の行を読む結合テストで、決めた形（注記か値）が残ることを確かめる。

**持ち主の段**: NFR 設計、Code Generation（B4）

## 補足

- 可用性の目標（SLO）は置かない。配備先が手元のコンテナに限られるため（`project.md`）。
- 対象DB の失敗は、業務データの API を失敗させるだけで、`/actuator/health` には含めない（U1 と同じ）。
- 同時の重なりについて:
  - 一意の制約が無いカラムでの同時の重なり（FR10.3）は、既知の制約とする。
  - 同じ行の同時の更新の後勝ち（FR7.2）も、既知の制約とする。
  - どちらも、防ぐ仕組みを足さない。

## 上流との差

- **承認の場の決定**（`construction/functional-design/memory.md` の Open questions）
  - 内容: `nullable` は U2 が足す。`formPart` から `lookup` を外す。`required`・`nullable` はすべてのカラムで返す。
  - この文書の要件に差は無い。
- **R-08**
  - レビューの推奨: 型を 4節か BR9.3 に一言書く。
  - この段の扱い: NFR4.12 に要件として置いた。型の名前と置き場は NFR 設計に回す。
- **R-05**
  - 承認の場の記録: 「NFR 設計で扱う」。
  - この段の扱い: 監査を読む人に見える振る舞いのため、要件（NFR4.15）だけを置き、形は NFR 設計に回した。
- **Q1: A**
  - 違反の後の確かめを1文にまとめる。そのため、BR6.2 の「外部キーごとに確かめる」を「1文で確かめる」に読み替える。
- **U1 の文書との差**
  - U1 の文書は「U3 は NFR1.4 以降」としている。この差は `performance-requirements.md` に書いた。
  - NFR3 の枝番は、U1 の「NFR3.4 以降」に従う。

## 後の段に回すこと

- **NFR 設計の試し**
  - MySQL・MariaDB の件数の設定と、同じ値の更新の数え方（BR8.2）。
  - 3種類の DB の SQLState・業者の番号の表（BR6.1）。
  - 巻き戻した後に、同じ接続で2つ目のトランザクションを専用の管理者で続けられるか。PostgreSQL の失敗したトランザクションの扱いも合わせて確かめる。続けられなければ、セーブポイントに切り替える（BR6.2）。
  - PostgreSQL の書き込みのアカウントで、`serial` と `identity` の作成に要る権限。
  - 時差つきの型を UTC として型つきで渡す形（U1 と合わせる）。
  - 書き込みの文を送った後に通信を断つ確かめの形。
- **NFR 設計（論理の部品）**
  - DB の種類つきの書き込みの型の置き場（R-08）。
  - 時間切れの監査の読み方（R-05）。
- **Performance Validation**
  - NFR4.12 は負荷の試験の対象にしない。結合テストで確かめる。
- **Observability Setup**
  - 503 の code ごとの区画（`observability-requirements.md` の NFR8.5）。
- **基盤の設計**
  - 見本の書き込みのアカウントの初期化（`security-requirements.md` の NFR4.16）。

## U3 とそろえる点

- **番号**: U3 は NFR1.7・2.10・3.7・4.20・7.7・8.7 から振る。
- **503 の出し分け**: 画面は次のとおり出し分ける。
  - `BUSINESS_TARGET_DB_TIMEOUT`: 「保存されたか分からない」と出し、確かめる入口を出す（AC3.3.5）。
  - `UNAVAILABLE`: 「保存されていない」と出す。
  - `BUSINESS_SAVE_REJECTED`: カラムを示さない誤りとして出す。
- **時差つきの型**: 画面の入力と表示は、UTC の値をそのまま扱う。
- **定義の項目**: 平らに置く。`required`・`nullable` は必ず値を持つ。`formPart` に `lookup` は来ない。
- **値の長さ**: 10,000 字の上限を、画面の `maxLength` にも当てる。U3 の D19 は画面だけの守りになる。
- **pattern と 413**: pattern は画面で当てない。`PATTERN_TIMEOUT` と 413 の文は画面が用意する。
- **E2E**: 代表の流れ1本まで。U2 は E2E を足さない。
