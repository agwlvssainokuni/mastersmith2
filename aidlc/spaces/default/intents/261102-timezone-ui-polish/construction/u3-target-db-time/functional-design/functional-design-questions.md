# Functional Design の質問（U3 u3-target-db-time）

対象の単位: U3 `u3-target-db-time`（種類 library。対象DB の日時の値の読み書き・接続のタイムゾーン・既定の DSL の生成の直し）。出典は `unit-of-work.md`・`requirements.md`（FR5・FR6）・`stories.md`（US5.1・AC2.1.6・AC2.2.4・US2.5）・`contract-summary.md` の C4・`decisions.md` の ADR-002・ADR-004・ADR-009、既存のコード（`ColumnValueCodec`・`RowDialect`・`TargetDataSourceConfig`・`MysqlSchemaQueries`、読み取りだけ）。

## 決まっていること（質問にしない）

| 項目 | 決まっている内容 | 出どころ |
|---|---|---|
| 接続のタイムゾーン | PostgreSQL の読み取り・書き込みの両方の接続に UTC を明示する（MySQL・MariaDB は今も接続を作るときに `SET time_zone = '+00:00'` を流している）。設定のやり方（初期化の文・接続の属性）は NFR 設計の捨ての試しで確かめて決める | FR5.1・ADR-004・ADR-009 |
| 時差を持たない型の値 | 接続の変更の前後で読み書きの値が変わらないことを、3種類の対象DB の結合テストで確かめる | FR5.2・AC2.2.4 |
| サーバーは変換しない | 表示のタイムゾーンへの変換は画面の側（U1）。サーバーは UTC の時点の形を保ち、新しい変換や拒否を足さない（夏時間の存在しない時刻の拒否は画面の側だけ） | ADR-002・ADR-003・C4 の rules |
| 時差の無い値の受け方 | 時点を表す型で、時差の無い値は UTC とみなして受ける（今のまま）。日時として読めない値は 400（`VALIDATION_FAILED`） | C4（Q2: A） |
| `timetz` | 変換せず、DB の時差のまま `HH:mm:ss±hh:mm` で返し、時差を落とさず保存する（今は UTC にそろえて `+00:00` で返しているため、この単位で直す） | FR2.4・A1・C4 の `changed_values_by_u3` |
| 秒の端数の出し方の方針 | 応答の端数は DB の定義の桁に固定し、末尾の 0 を落とさない（今は 0〜9 桁の可変で末尾の 0 が落ちる。応答の項目名・形は変えない） | FR2.5・C4 の `changed_values_by_u3` |
| 桁つきの時差つきの型 | `timestamp(n) with time zone` を時点を表す型として扱う。コードを読むと、型の名前から括弧以降をすべて落とすため（`RowDialect.baseName`）、`timestamp(3) with time zone` は `timestamp` として時差を持たない型になる見込み（未実行。確かめる持ち主は NFR 設計の捨ての試し）。不具合でなければ直さず、結果を Build and Test に記録する | FR6.2・AC5.1.3・AC5.1.4・ADR-009 |
| 範囲外の書き込み | DB が日時の範囲外・形の誤りで保存を拒んだときは、今の分類のとおり 409 `BUSINESS_SAVE_REJECTED`（形の誤りは 400）。この単位で新しい code・状態コードは足さない | 既存の `RowSqlFailureClassifier`・team.md の code の決まり |
| 往復 | 読んだ値をそのまま更新に送っても対象DB の値は変わらない。主キー・同時更新の判定の値・参照の値は、読んだままの文字を送り返す | AC2.5.2・AC2.5.3・C4 の rules |
| 型の対応の置き場 | DB の型ごとの対応は repository の層の1か所（今の `ColumnValueCodec`）に閉じる。DB の種類ごとの差は `RowDialect` の実装に閉じ、service の層は DB の種類で分岐しない。既存の境界テスト・SpotBugs の除外は緩めない | team.md の Code Style |
| 日時の型のテスト | MySQL・MariaDB の `datetime`・`timestamp`・`date`・`time`、PostgreSQL の `timestamp`・`timestamptz`・`date`・`time`・`timetz` を、3種類の対象DB の結合テスト・日付の変わり目・秒の端数 0・3・6 桁・範囲の端・往復・jqwik の性質ベースのテストで確かめる。時差（`ZoneId`）と時計は注入し、機械の既定のタイムゾーンに預けない | team.md の Testing Posture |
| 既定の DSL の端数の上限が 0 になる不具合 | 確かめる持ち主は NFR 設計の捨ての試し。コードを読むと、生成は型の名前に `DATA_TYPE` だけを写し、日時の精度の列（`DATETIME_PRECISION`）を読んでいない見込み（未実行）。不具合なら直す | FR6.1・AC5.1.2・ADR-009 |

以下の質問は、上の決まりだけでは決まらない点に絞った（4問）。

## Q1. [開発] 応答の秒の端数の桁を、どこから決めるか

文脈: 応答の端数を DB の定義の桁に固定する方針は決まっているが、その桁の出どころが決まっていない。日時・時刻の4つの型（時差なし・時差つきの日時、時差なし・時差つきの時刻）に共通で効き、手書きの DSL（桁を書かないことがある）でも桁が落ちないかが分かれる。

**推奨: B。** DB の定義が正しい桁の持ち主で、手書きの DSL が桁を書き忘れても端数が落ちず、DSL の桁と食い違っても読み書きが崩れないため。読む文には新しい問い合わせを足さない（結果の列情報を使う）ので、問い合わせの数は増えない（NFR2.1）。生成の直し（Q2）は、どちらを選んでも画面の案内と DSL の照合のために別に行う。

- A. DSL の桁だけで決める（型の名前の `(n)` → DSL の精度 → DB の種類の既定 PostgreSQL 6・MySQL/MariaDB 0、今の `fractionDigits` の順のまま）。仕組みは今のまま。手書きの DSL が桁を書かないと MySQL 側で 0 桁と扱われ、端数が落ちる。
- B. 読むときの結果の列情報（JDBC の結果の桁）で決める。DSL の桁は応答に使わない。DB が正。DSL が古くても崩れない。
- C. B を優先し、列情報が桁を返さない DB のときだけ DSL の桁を使う。取りこぼしは最も少ないが、決まりが2段になり、テストの組み合わせが増える。
- X. Other (please specify)

[Answer]: B

## Q2. [開発] 既定の DSL の生成が、日時の桁を書くか

文脈: FR6.1 のとおり、生成が端数の桁を DSL に書くよう直すかを確かめて直す。書き方で、既に適用済みの DSL との差（プレビューの差分の件数）が変わる。差分の項目には `dbType.precision` が含まれ（`DslDiffCalculator`）、書く桁が既存の DSL の値と違うと、再生成のたびに差分が出る。

**推奨: B。** 既存の DSL は桁を書いていないため、DB の製品の既定の桁と同じ列には何も書かなければ、再生成の差分は端数のある列だけに出る。DB の製品の既定（PostgreSQL 6・MySQL/MariaDB 0）は Q1 の A・C の順の最後と同じ値で、書かない列は今までの意味のまま。

- A. 日時・時刻の列はすべて桁を書く（0 も書く）。DSL を見るだけで桁が分かるが、既存の DSL との差分が日時の列すべてに出る。
- B. DB の製品の既定と違う桁のときだけ書く（MySQL/MariaDB は 1 以上、PostgreSQL は 6 以外）。差分は端数のある列にだけ出る。
- C. 生成は変えず、不具合でなければ記録だけにする（Q1 の B で応答は直る前提）。DSL の桁の欄は空のまま。FR6.1 は「確かめて、不具合なら直す」なので、不具合と確かめられた場合はこの選択肢を取れない。
- X. Other (please specify)

[Answer]: B

## Q3. [開発] 書き込みの端数が、列の桁を超えているとき

文脈: 読んだ値を送り返す往復では桁は超えない。超えるのは、利用者が列の桁より細かい端数を入れたときだけ（例: 3 桁の列に 6 桁、画面の入力は 1〜6 桁を許す）。DB は黙って丸め（MySQL・PostgreSQL）または切り、保存した値は入れた値と変わる。

**推奨: B。** 入れた値と保存した値が黙って食い違うのを避けられ、拒否は既存の入力の誤り（`VALIDATION_FAILED`、どの項目か示す）だけで足りる。桁が分からないとき（手書きの DSL で桁を書かず Q1 が B の場合）は確かめずに DB に任せる。確かめるための桁は、書き込みの文の前に DSL の精度から決める（問い合わせは増やさない）。

- A. DB に任せる（丸め・切りを許す）。実装は最も小さいが、往復と性質ベースのテストの「保存した値は入れた値に戻る」が、桁を超える入力で成り立たない。
- B. DSL の桁が分かる列で、桁を超える端数を 400 `VALIDATION_FAILED` で拒否する。桁が分からない列は確かめない。
- C. DSL の桁が分かる列で、サーバーが列の桁に丸めて保存する（拒否しない）。利用者は誤りに気づかないが、保存はできる。
- X. Other (please specify)

[Answer]: B

## Q4. [開発] DB の値を日時として読めないとき（K-99・AC2.1.6）

文脈: 今は、結果の日時・時刻を型として読めない値（MySQL のゼロの日時 `0000-00-00 00:00:00`、`time` の 24 時間を超える値・負の値、PostgreSQL の `infinity`）が1つでも含まれると、その要求全体が読み取りの変換の失敗（`READ_CONVERSION`）になり、一覧が1行でも読めない行を含むだけで表示できない（コードを読んだ内容。未実行）。MySQL の `timestamp` の 2038 年の上限は、DB が保存の側で拒むため（Q の外、上の「範囲外の書き込み」）、読む側の問題にはならない。

**推奨: B。** 読めない値のある行を含む一覧でも、ほかの行と値は見られる。応答の形は変えない（契約 C4 の変更なし）。U1 は、読めない文字を元のまま出す規則（`unreadable`）を既に持つため、画面は新しい扱いを足さずに済む。主キーが読めない行は、読んだ値を送り返せないため、詳細・更新・削除は拒否される（行を1件に決められない操作と同じ扱いになる）。読めなかった値そのものはログ・エラー応答・トレースの属性に出さない。

- A. 今のまま。読めない値が1つでもあれば、その要求全体が失敗する。変更は無いが、レガシーな MySQL の表の一覧が開けなくなることがある。決めた側の動作を明示したテストを足す。
- B. 読めなかったセルだけ、DB が送った文字をそのまま返す（応答の項目は増やさない）。書き込みでは、その形の文字は日時として読めない値として 400 で拒否する。
- C. B に加えて、応答に「読めない値」の印を足す（項目を足す。契約 C4 の変更なしを破り、前の版の画面は印を読まずに動く）。画面が文字の形から推測せずに済むが、承認済みの契約を変える。
- X. Other (please specify)

[Answer]: B

## Sources

- Initial description: N6（業務データの日時のタイムゾーンの変換）と画面の仕上げの Intent [desc]
- Workflow-selected scope: classic [scope]
- 単位の定義: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/units-generation/unit-of-work.md`（U3）・`unit-of-work-story-map.md`
- 要件・ストーリー: `inception/requirements-analysis/requirements.md`（FR2.4・FR2.5・FR5・FR6）、`inception/user-stories/stories.md`（AC2.1.6・AC2.2.4・AC2.5.x・US5.1）
- 契約・決定: `inception/contract-design/contract-summary.md`（C4）、`inception/domain-design/decisions.md`（ADR-002・ADR-003・ADR-004・ADR-009）
- 既存のコード（読み取りだけ）: `backend/src/main/java/cherry/mastersmith/businessdata/repository/ColumnValueCodec.java`・`RowDialect.java`・`MysqlFamilyRowDialect.java`・`RowSqlExecutor.java`・`targetdb/config/TargetDataSourceConfig.java`
- 進め方: `aidlc/spaces/default/memory/team.md`（Testing Posture の日時の項目・Code Style）・`project.md`

## Assumptions & Open Questions

- [assumption] 「決まっていること」の表のうち、コードを読んで書いた点（`baseName` が括弧以降を落とす、生成が `DATETIME_PRECISION` を読まない、読めない値で要求全体が失敗する）は未実行の見立て。NFR 設計の捨ての試しで確かめ、結果で設計を直す。
- [assumption] PostgreSQL の接続のタイムゾーンの設定のやり方（接続の初期化の文か接続の属性か）は、Functional Design では決めず NFR 設計で決める。
- 未決: Q1〜Q4（上）。
