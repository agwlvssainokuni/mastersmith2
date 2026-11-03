# 技術の選択と、数の目標 — u3-target-db-time

出典: 機能設計（`construction/u3-target-db-time/functional-design/`）、要件（`inception/requirements-analysis/requirements.md` の FR5・FR6・NFR2.1）、質問と答え（`nfr-requirements-questions.md` の Q1: A・Q2: A・Q3: B・Q4: B）、コード知識ベース（`aidlc/spaces/default/codekb/mastersmith2/technology-stack.md`）、`team.md`（Code Style・Testing Posture）、既存のコード（`TargetDataSourceConfig`・`TargetWriteDataSourceConfig`・`ColumnValueCodec`・`RowDialect` の実装、読み取りだけ）。

U3 は、対象DB の日時の読み書き・接続のタイムゾーン・既定の DSL の生成の直し（種類 library。アプリに入る）。種類の決まりにより、性能・拡張・信頼性・観測の独立した文書は作らない（新しい経路・状態・負荷の持ち方が無い）。性能の数の目標は、この文書の2節に置く。セキュリティと信頼性の要件は `security-requirements.md`。

## 1. 技術の選択

| 項目 | 選択 | 理由 | 検討した別の案 |
|---|---|---|---|
| PostgreSQL の接続のタイムゾーン（Q1: A） | 接続の初期化の文（`SET TIME ZONE 'UTC'`、定数）を、読み取りのプールと書き込みのプールの設定（`setConnectionInitSql`）の PostgreSQL の側に置く。MySQL・MariaDB の `SET time_zone = '+00:00'` と同じ置き場・同じ形。接続を作るときに1回だけ（貸すたびには流さない） | 置き場が3種類の DB でそろい、定数1つで読み取りと書き込みに同じ値を使える。初期化が失敗すれば既存の借りる失敗になる（新しい code 不要）。ドライバーが開始時に JVM の既定を送っても、初期化の文が上書きするため、結果は JVM の設定に預けない | ドライバーの接続の設定（開始時の指定。`options=-c TimeZone=UTC` など）: 開始時から UTC だが置き場が分かれる。A と B の両方: 2か所を同じ値に保つ手間。試し（K-95）で `user.timezone` を変えて値が変わるなら、B へ切り替える |
| 秒の端数の桁の取り出し（Q2: A） | 結果の列情報（JDBC の結果の桁。`ResultSetMetaData`）だけで決める。新しい問い合わせは足さない。桁が得られない DB・型が試しか結合テストで見つかったら、その型だけ設計に戻して依頼者に諮る（DSL の桁・文字の読み直しへの切り替えは、その時に決める） | 手段を1つに絞ると、型の変換の費用が増えず、決まりが1段で済む。確かめずに2段の逃げ道を先に作る手間を避ける | 得られない型だけ文字として読み直す: 型ごとに読み方が2通りになりテストが増える。得られない型だけ DSL の桁: 手書きの DSL が桁を書かないと端数が落ちる |
| 書き込みの端数の上限（Q3: B） | DSL に明示した桁（型の名前の `(n)` または `dbType.precision`）だけで確かめ、超えたら 400 `VALIDATION_FAILED`。DSL に桁が無い列は確かめない。DB の実際の桁との食い違いは、BR3.13 の前提（DSL の桁が DB の桁以上）として明記し、食い違うときは 400 のまま | 問い合わせを足さず、丸め・切りで黙って食い違う保存も起こさない安全側。画面は変えていない欄を送らないので、当たるのは変えた欄と API の直接の呼び出しだけ | 読んだときの DB の桁を書き込みへ引き継ぐ: 桁を持ち回る仕組みが要り設計が大きい。拒否の取り下げ: 入れた値と保存した値が黙って食い違う |
| 日時の型の対応・時点の扱い | 標準の `java.time`（`OffsetDateTime`・`LocalDateTime`・`LocalDate`・`LocalTime`・`OffsetTime`）。タイムゾーンは UTC を明示に使い、JVM の既定を暗に使わない。型の対応は repository の層の1か所（既存の `ColumnValueCodec`）に閉じ、DB の種類ごとの差は `RowDialect` の実装に閉じる | 既存の形を足すだけで、新しい依存が要らない。service の層は DB の種類で分岐しない | 日時のライブラリを足す: 依存・ライセンス・脆弱性検査の対象が増える |
| 型の名前の分類 | 型の名前から括弧の部分 `(n)` だけを取り除き、括弧の後ろの `with time zone` を残して分類する。確かめて不具合でなければ直さず、結果を記録する（BR3.18） | 桁つきの時差つきの型を時点を表す型として扱うため | なし |
| 既定の DSL の生成の桁 | DB の定義から端数の桁を読み、その製品の既定（PostgreSQL 6・MySQL/MariaDB 0）と違うときだけ `dbType.precision` に書く。読めなければ書かず、生成は失敗させない | 既存の DSL との差分を、端数のある列だけにする | 日時の列にすべて桁を書く: 差分が日時の列すべてに出る |
| テスト | JUnit・Testcontainers（MySQL・MariaDB・PostgreSQL の実物。版を固定したイメージ）・jqwik（性質ベース）・ArchUnit・SpotBugs（FindSecBugs） | 既存と同じ（`team.md` Testing Posture）。新しい依存なし | H2・モックでの代用は禁止（`project.md` Mandated） |
| ビルド・配備 | 既存のバックエンドのビルドに入る。単体では配備しない（埋め込み）。実行時のタイムゾーンの設定は変えない | `unit-of-work.md` の配備の形・BR3.19 | なし |

## 2. 数の目標（性能。NFR2.1 に当たる細目）

| ID | 目標 | 測り方 | 根拠 |
|---|---|---|---|
| NFR2.1.1 | 日時の扱いを直しても、業務データの API が使う対象DB・内部DB への問い合わせの数を増やさない。桁は読む文の結果から取る | `*ConnectionUsageIT`（`NavigationConnectionUsageIT` など）と同じ形の結合テストで、1要求が使う接続の数・問い合わせの数が変更の前後で同じことを決定的に確かめる | 要件 NFR2.1（Q4: B の前提） |
| NFR2.1.2 | 接続の初期化は、接続を作るときに1回だけ（貸すたびには流さない）。PostgreSQL の読み取りと書き込みの両方 | 結合テスト: 同じ接続を続けて借りても、初期化の文が1回だけであること。初期化の後のセッションのタイムゾーンが UTC であること | BR3.1・BR3.16 |
| NFR2.1.3 | 1万の日時・時刻のセル（時点・時差を持たない日時・日付を除く時刻・`timetz`・端数 0・3・6 桁・読めないセルを混ぜる）の読み取りの変換が、合計 **500 ミリ秒以内** | 単体テストで、結果の1つのセルから値を作る部品（コンテナ・ネットワークを含めない）を、固定の値1万個に通し、時間を測る（JIT の暖機の後。時計は注入するか単調な時計を使う）。測った環境（JDK の版・CPU）を成果物に書く | 一覧の1ページ最大（100 行 × 100 カラム = 1万の値）で体感の遅れが出ない水準。画面の側の変換（U1）と同じ物差し（Q4: B） |
| NFR2.1.4 | 1つのセルの変換が、平均 **0.05 ミリ秒以内**（上の測りの合計 ÷ 値の数） | 同上 | 同上 |
| NFR2.1.5 | 業務データの読み取りの p95 などの既存の目標は変えない。Build and Test で、既存の業務データの読み取りの場面（perf の既存の台本）を直前直後で並べて記録する。新しい k6 の場面・perf の種は足さない | 既存の場面の値を、この単位の統合の前後の版で並べて記録する | 要件 NFR2.1・`team.md` Testing Posture（物差しを変えるのは要件定義で依頼者が決めたときだけ） |

上の数の目標（NFR2.1.3・NFR2.1.4）は、この Intent で初めて置く（前の物差しは無い）。目標を緩めて満たしたことにはしない（`team.md`）。外れたときは、原因を確かめてから、依頼者に諮る。NFR2.1.5 の前の値は、Build and Test で記録する（この段では測らない）。

## 3. 新しい依存

なし（Q1: A・Q2: A）。足す場合は、採用の前にライセンスを確かめ、Apache License 2.0 と異なるものは採用の理由を設計の記録に残し、lockfile と OSV-Scanner の対象に入れる（`team.md`）。

## 4. NFR 設計の捨ての試しで確かめること（BR3.18・ADR-009 の持ち主）

- K-95: PostgreSQL の接続で、`user.timezone` を変えても `SHOW TimeZone` と時差を持たない型の値が変わらないこと。変わるなら、開始時の指定への切り替えか、既存の値を壊さない移行の段取りを依頼者に諮る。
- K-98: 結果の列情報が、3種類の対象DB の日時・時刻の型（桁 0・3・6）で桁を返すこと。得られない型があれば設計に戻す（Q2: A）。桁つきの時差つきの型の分類と、既定の DSL の生成が桁を読むか（不具合なら直し、不具合でなければ直さず記録する）。
- MySQL・MariaDB の `time` の負の値・24 時間を超える値、MariaDB の `timestamp` の上限、ゼロの日時、PostgreSQL の `infinity` の読み方（決めた側の動作を明示する）。
- 変換の費用（NFR2.1.3・NFR2.1.4）の実際の値の見立て。

## Sources

- Initial description: N6（業務データの日時のタイムゾーンの変換）と画面の仕上げの Intent [desc]
- Workflow-selected scope: classic [scope]
- 機能設計: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u3-target-db-time/functional-design/functional-spec.md`・`rules.md`
- 要件: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/requirements-analysis/requirements.md`
- 質問と答え: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u3-target-db-time/nfr-requirements/nfr-requirements-questions.md`（Q1: A・Q2: A・Q3: B・Q4: B）
- コード知識ベース: `aidlc/spaces/default/codekb/mastersmith2/technology-stack.md`

## Assumptions & Open Questions

- [assumption] 1万のセルの変換が 500 ミリ秒以内・平均 0.05 ミリ秒以内は、画面の側（U1）の物差しにそろえた見立て。サーバー側の変換（JDBC の読み取りを含まない部分）の実測は未確認。NFR 設計の捨ての試しで実際の値を測って確かめる。
- [assumption] 初期化の文 `SET TIME ZONE 'UTC'` が、ドライバーの開始時の指定を上書きして接続のセッションを UTC にする（未実行。K-95 の試しで確かめる）。
- 未解決の点: 結果の列情報が桁を返すか（3種類の対象DB・型）。NFR 設計の捨ての試しで確かめる。
