# NFR Requirements の質問（U3 u3-target-db-time）

U3 は、対象DB の日時の値の読み書き・接続のタイムゾーン・既定の DSL の生成の直しの単位です（`unit-of-work.md` の種類は library。アプリに入る）。サーバー側の変更ですが、新しい API・表・画面は足しません。この単位に当たる NFR は、性能（日時の型変換の費用・問い合わせの数）、セキュリティ（値と接続情報の漏えい・読めない値の入力）、信頼性（接続の初期化の失敗・読めない値）、使う技術（PostgreSQL の接続の時差の設定のやり方・桁の取り出しの手段）です。拡張（負荷の増え方）と観測（新しい指標・警報）は、新しい経路を作らないため当たりません。

## 決まっていること（質問にしない）

| 項目 | 決まっている内容 | 出どころ |
|---|---|---|
| 問い合わせの数 | 日時の扱いを直しても、業務データの API が使う対象DB・内部DB への問い合わせの数を増やさず、読み取りの目標（p95 など）を変えない。桁は読む文の結果から取り、新しい問い合わせを足さない。接続の初期化は接続を作るときに1回だけ。数は `*ConnectionUsageIT` と同じ形の結合テストで決定的に確かめる | 要件 NFR2.1・BR3.16・AC2.1.7・AC5.3.3 |
| 漏えいの防止 | 日時・時刻の値（読めない文字・端数を超えた値を含む）・対象DB の接続情報・JDBC の例外のメッセージを、ログ・監査・トレースの属性・エラー応答に出さない。項目の名前・誤りの種類・SQLState・例外のクラスの名前だけを出す。日時を持つ型は `toString` で値を伏せる。TRACE を有効にした `XxxSecretLeakIT` で確かめる | BR3.15・`team.md` Code Style・`project.md` Forbidden |
| 読めない値の入力 | 読めないセルだけ DB が送った文字をそのまま返し、要求全体は失敗させない（BR3.10）。書き込み・検索では、その形（ゼロの日時・24 時間を超える time・infinity）を 400 `VALIDATION_FAILED` で拒否する（BR3.11）。主キーが読めない行の詳細・更新・削除は、既存の「行を1件に決められない操作」の拒否にする（BR3.12）。新しい code・状態コードは足さない | BR3.10〜BR3.12・`team.md` の code の決まり |
| JVM の既定に頼らない | 型の対応・読み書きの部品は JVM の既定のタイムゾーンを暗に使わない。`user.timezone` を変えても値が変わらないことをテストで確かめる。実行時のタイムゾーンの設定（Dockerfile の `-Duser.timezone`・compose の `TZ`・テストの JVM の `user.timezone`・E2E の起動）は変えない | BR3.17・BR3.19・`team.md` Deployment・Testing Posture |
| 必須のテスト | MySQL・MariaDB の `datetime`・`timestamp`・`date`・`time`、PostgreSQL の `timestamp`・`timestamptz`・`date`・`time`・`timetz` を、3種類の対象DB で `./gradlew verify` の中で毎回、一覧・詳細・作成・更新（と検索・主キー・参照の値の往復）の結合テストで確かめる。日付の変わり目、秒の端数 0・3・6 桁、範囲の端（MySQL の `timestamp` の 2038 年の上限・`time` の 24 時間超・負）、往復、jqwik の性質ベースのテストを含める。時差（`ZoneId`）と時計（`Clock`）は注入する。テスト用の対象DB のスキーマ・データベースは、実行ごとに一意の名前で作って終わったら消す | `team.md` Testing Posture（業務データの日時のタイムゾーンの変換・対象DB の書き込み・3種類の対象DB） |
| 試しの扱い | K-95（PostgreSQL の接続の時差）・K-98（桁・桁つきの時差つきの型・生成が日時の精度を読むか）は、NFR 設計の捨ての試しで先に確かめ、結果の要点を成果物に写す。不具合でなければ直さず、確かめた結果を Build and Test に記録する。この段（NFR 要件）では質問と目標を決め、試しは行わない | BR3.18・ADR-009 |
| 新しい依存 | 足さない（JDBC ドライバー・Hikari の既存の機能と標準の `java.time` だけで足りる見込み）。足すことになったときは、採用の前にライセンスを確かめ、Apache License 2.0 と異なるものは採用の理由を設計の記録に残す | `team.md` Code Style |
| 境界テスト・静的解析 | 既存の ArchUnit の境界テストと SpotBugs の除外は緩めず、足す形にする。DB の種類ごとの差は repository の層の `RowDialect` の実装に閉じ、service の層は DB の種類で分岐しない。`PREDICTABLE_RANDOM`・`SQL_` の関門は今のまま | `team.md` Code Style |
| この単位に当たらない NFR | NFR1.1（自作の SVG）は u6、NFR1.2（本人だけが自分の設定を変える）は u2、NFR1.3（メニューの応答の印）・NFR2.2（ホームの API）は u5、NFR3.x（アクセシビリティ）・NFR6.1（言語）は画面を持つ単位、NFR4.1・NFR4.2（互換と戻し）は u2・u5（U3 は列・表を足さず、応答の項目名・形も変えないため、戻しは前の版のイメージだけでよい）。NFR5.3（E2E は1本まで）は画面を通す単位が受け持ち、U3 は E2E を足さない | 要件 NFR1〜NFR6・`unit-of-work.md` |
| 機能設計のレビュー（`reviews/review-01.md`）の Minor | R-02（桁が得られないときの実現手段）は Q2 で決める。R-03（`functional-spec.md` 5 節の差に、読めないセルの既存の扱い（要求全体の失敗からセルだけ元の文字へ）と `timetz` の書き込みの時差の保存（今は UTC に正規化）を足す）は、承認済みの文書を書き換えず、この段の成果物の「上流との差」に書く（`project.md` の決まり） | `project.md` の学び |

## Q1. [開発] PostgreSQL の接続のタイムゾーンを、どう UTC にするか

文脈: 読み取りのプールと書き込みのプールの両方で、接続を作るときに1回だけ UTC にします。MySQL・MariaDB は、接続の初期化の文（`SET time_zone = '+00:00'`）を接続の設定（`setConnectionInitSql`）に置いています。PostgreSQL のドライバーは、接続の開始時に JVM の既定のタイムゾーンを送るため（K-95、未検証）、その後に UTC へ上書きする形か、開始時の指定そのものを UTC にする形かで、JVM の設定に頼る範囲が変わります。

**推奨: A。** 理由: MySQL・MariaDB と同じ置き場・同じ形（接続を作るときに1回の初期化の文）になり、読み取りと書き込みの両方を同じ定数で書けて、初期化が失敗すれば既存の「借りる失敗」になります（新しい code 不要）。JVM の既定のタイムゾーンを最初に送る点は、初期化の文が必ず上書きするため、結果は JVM の設定に預けません。試し（K-95）で、`user.timezone` を変えても `SHOW TimeZone` と時差を持たない型の値が変わらないことを確かめ、変わるなら B へ切り替えます。

- A. 接続の初期化の文（`SET TIME ZONE 'UTC'`、定数）を、読み取りと書き込みのプールの設定に置く。MySQL・MariaDB と同じ形。
- B. ドライバーの接続の設定（開始時の指定。`options=-c TimeZone=UTC` など）で UTC にする。開始時から UTC だが、MySQL・MariaDB と置き場が分かれ、設定の文字の組み立てが増える。
- C. A と B の両方に置く。最も確実だが、2か所を同じ値に保つ手間が増え、片方の変更の取り残しの恐れがある。
- X. Other (please specify)

[Answer]: A

## Q2. [開発] 秒の端数の桁を取り出す手段と、取り出せないときの動き（レビュー R-02）

文脈: 機能設計は、応答の端数を「結果の列情報（DB の実際の桁）」で決め、取れないときは「DB が送った桁のまま」出すとしました。ところが、今の読み取りは日時を型（`LocalDateTime` など）にしてから整形するため、DB が送った末尾の 0 は取り出せず、この逃げ道は文字として読み直さないと実現できません（レビューの指摘）。MySQL・MariaDB の日時は今も文字として読んでおり（`MysqlFamilyRowDialect`）、PostgreSQL と型によって読み方が違います。結果の列情報が桁を返すかは3種類の対象DB で未確認です（試しで確かめる）。

**推奨: A。** 理由: 取り出す手段を1つ（結果の列情報）に絞ると、型の変換の費用が増えず（NFR2.1）、決まりも1段で済みます。桁が得られないことが試しと結合テストで分かった型だけ、その時点で依頼者に諮って設計に戻すので、確かめずに2段の逃げ道を先に作る手間を避けられます。試しは NFR 設計で最初に行います（BR3.18）。

- A. 結果の列情報だけで決める。BR3.4 の「DB が送った桁のまま」は取り下げ、桁が得られない DB・型が試しか結合テストで見つかったら、そこで設計に戻す（その型だけ別の質問にする）。
- B. 結果の列情報を使い、得られない型だけ、文字として読み直して DB が送った桁を使う（BR3.4 の逃げ道を、文字の読み直しとして実現する）。取りこぼしは無いが、型ごとに読み方が2通りになり、テストの組み合わせが増える。
- C. 結果の列情報を使い、得られない型だけ DSL の桁を使う。機能設計の Q1: B（DSL の桁に頼らない）を一部取り下げる形になり、手書きの DSL が桁を書かないと端数が落ちる。
- X. Other (please specify)

[Answer]: A

## Q3. [開発] DSL の桁と DB の実際の桁が食い違うとき（レビュー R-01）

文脈: 応答の桁は結果の列情報（DB の実際の桁）、書き込みの上限は DSL に明示した桁で、出どころが違います。手書きの DSL の桁が DB より小さい場合や、DSL を適用した後に DB の桁が広がった場合、読んだ値の端数は DSL の桁を超え、そのまま送り返すと書き込みの端数の拒否（BR3.9）に当たります。機能設計の BR3.13（読んだ値のまま更新しても変わらない）の「桁を固定するため読んだ値の桁は超えない」は、DSL と DB の桁が一致するときだけ成り立ちます。書き込みの上限を DB の実際の桁に合わせるには、書き込みの前に桁を知る問い合わせが要り、問い合わせの数を増やさない決まり（NFR2.1）に反します。なお、画面は変えていない欄を送らない（BR3.11・U4）ため、食い違いが当たるのは、利用者が食い違いのある列を新しい値に変えたときと、API を直接呼ぶ呼び方に限られます。

**推奨: B。** 理由: 問い合わせを足さず、丸め・切りで黙って食い違う保存も起こさない安全側（400 で止め、どの項目かを示す）に倒せます。BR3.13 は「DSL と DB の桁が一致するとき」を前提と明記して直し、食い違うときの動作（変えていない欄は送らないので影響せず、変えた欄だけ 400）を結合テストの対象にします。DSL の桁を DB の桁へ直す手段は、既定の DSL の生成（BR3.8）と DSL の照合の側にあり、この単位の範囲を超えません。

- A. 書き込みの上限を、読んだときに分かった DB の桁に合わせる（読む文の結果から取った桁を、書き込みの確かめに引き継ぐ）。往復は食い違いでも保てるが、読む要求と書く要求が別のため、桁を持ち回る仕組み（保存・要求への添付）が要り、設計が大きくなる。
- B. 食い違いを BR3.13 の前提として明記し、食い違うときは BR3.9 の拒否（400）のままにする。「DSL の桁が DB の桁以上」であることを、往復の保証の前提にする。
- C. BR3.9 の端数の拒否を取り下げ、端数の丸め・切りは DB に任せる（機能設計の Q3: A に戻す）。食い違いの問題は無くなるが、入れた値と保存した値が黙って食い違う。
- X. Other (please specify)

[Answer]: B

## Q4. [品質] 日時の型の変換の費用の目標

文脈: 一覧は最大 100 行 × 100 カラム（1万の値）を1ページに出せます（読む側の上限）。今回、日時・時刻のセルに、結果の列情報から桁を取る処理と、読めないセルの取り扱い（例外を値に変える）が加わります。読み取りの p95 の目標（`project.md` の学び・前の Intent の目標）は変えません（NFR2.1）。性能の目標の物差しを変えるのは要件定義で依頼者が決めたときだけ（`team.md`）なので、ここで置くのは、変換の費用を測る新しい目標です。この単位は library のため、性能の独立した文書は作らず、`tech-stack-decisions.md` に置きます（U1 と同じ）。

**推奨: B。** 理由: 変換の費用は、サーバーを立てずに単体テストで決定的に測れ、`./gradlew verify` と CI で毎回確かめられます。U1（画面の側の変換）と同じ物差し（1万の値で合計 500 ミリ秒以内・1つの値の平均 0.05 ミリ秒以内）にそろえると、サーバー側と画面側を同じ基準で見られます。既存の読み取りの p95 は、Build and Test で既存の業務データの読み取りの場面を直前直後で並べて記録し、物差しは変えません。新しい k6 の場面と perf の種の変更は、この単位の大きさ（M）に見合わないため足しません。

- A. 数の目標は新しく置かない。問い合わせの数（結合テスト）と接続の初期化が1回だけであることを確かめ、読み取りの p95 は既存の目標のまま Build and Test で既存の場面を流して確かめる。変換そのものの費用は測らない。
- B. A に加えて、1万の日時・時刻のセル（時点・時差なし・timetz・端数 0・3・6 桁・読めないセルを混ぜる）の読み取りの変換を、単体テストで測り、合計 500 ミリ秒以内・1つの値の平均 0.05 ミリ秒以内とする。測った環境（JDK の版・CPU）を成果物に書く。
- C. B に加えて、日時の多い表を読む新しい k6 の場面と perf の種を足し、読み取りの p95 を直前直後で比べる合否（悪化の上限）を置く。最も確かだが、perf の台本・種・使い捨ての環境の手間と、PC の資源（colima の VM のメモリ）の負担が増える。
- X. Other (please specify)

[Answer]: B

## Sources

- Initial description: N6（業務データの日時のタイムゾーンの変換）と画面の仕上げの Intent [desc]
- Workflow-selected scope: classic [scope]
- 機能設計: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u3-target-db-time/functional-design/functional-spec.md`・`rules.md`（BR3.1〜BR3.19）・`functional-design-questions.md`（Q1: B・Q2: B・Q3: B・Q4: B）・`reviews/review-01.md`（R-01・R-02・R-03）
- 要件・契約・決定: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/requirements-analysis/requirements.md`（FR5・FR6・NFR1〜NFR6）、`inception/contract-design/contract-summary.md`（C4）、`inception/domain-design/decisions.md`（ADR-002・003・004・009）、`inception/units-generation/unit-of-work.md`（U3）
- 前の単位の書き方: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u1-time-zone-conversion/nfr-requirements/`（`security-requirements.md`・`tech-stack-decisions.md`・`nfr-requirements-questions.md`）
- 既存のコード（読み取りだけ）: `backend/src/main/java/cherry/mastersmith/targetdb/config/TargetDataSourceConfig.java`・`TargetWriteDataSourceConfig.java`・`TargetDbProperties.java`、`businessdata/repository/ColumnValueCodec.java`・`MysqlFamilyRowDialect.java`・`PostgresRowDialect.java`・`RowSqlExecutor.java`
- 進め方: `aidlc/spaces/default/memory/team.md`（Testing Posture・Code Style・Deployment）・`project.md`

## Assumptions & Open Questions

- [assumption] PostgreSQL のドライバーが接続の開始時に JVM の既定のタイムゾーンを送ることは、機能設計のレビュー（R-01 の確認）の読みで、未実行。Q1 の推奨 A は、試し（K-95）で確かめる。
- [assumption] 結果の列情報が桁を返すかは、3種類の対象DB・型で未確認（Q2。試しで確かめる）。
- [assumption] Q4 の物差し（1万の値で合計 500 ミリ秒・平均 0.05 ミリ秒）は U1 にそろえた見立てで、サーバー側（JDBC の読み取りを含まない変換だけ）の実測は未確認。NFR 設計の試しで実際の値を測り、外れたときは原因を確かめてから依頼者に諮る（目標は緩めて満たしたことにしない）。
- [assumption] 単位の種類は `unit-of-work.md` の library とし、性能・拡張・信頼性・観測の独立した文書は作らない（`produces_kinds` は service・ui だけ）。依頼文の「service」との差は、承認の前に確かめる。
- 未決: Q1〜Q4（上）。
