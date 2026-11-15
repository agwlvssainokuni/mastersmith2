# Contract Design の質問（Intent 261114-internal-db-postgresql）

4つの単位（U1 u1-internal-db-platform・U2 u2-portable-sql・U3 u3-postgres-behaviour-tests・U4 u4-local-runtime-docs）の間の約束を決める。単位の間に実行時の通信は無く、約束は「起動時の設定」「移行の表の形」「テストの土台」「repository の口」「イメージの固定先」の5つになる。

## 決まっていること（質問にしない）

- **外部の API**: 新しい外部の API は作らない。今の REST API は、内部DB が H2 でも PostgreSQL でも同じ状態コード・`code`・応答の項目を返す（FR3.1）。外部の約束は今のままで、この段では変えない。
- **起動時の設定（U1 が持ち、U3・U4 が使う）**: 環境変数 `MASTERSMITH_DB_URL`・`MASTERSMITH_DB_USERNAME`・`MASTERSMITH_DB_PASSWORD`・`MASTERSMITH_DB_MAXIMUM_POOL_SIZE` の今の名前と形を保つ（FR1.4、`backend/src/main/resources/application.yaml`）。種類は URL の先頭（`jdbc:h2:`・`jdbc:postgresql:`）で判断し、それ以外は起動を止める（FR1.3）。接続情報はログ・エラー応答に出さない（`project.md` の Forbidden）。
- **移行の表の形（U1 が持ち、U2・U3 が使う）**: H2 と PostgreSQL の移行は同じ版の番号・同じ表・同じ名前の制約と索引にし、普段の `verify` の単体テストで突き合わせる（ADR-002）。新しい移行は、両方の一式に同じ版で同じコミットで足す。
- **大きなバイナリの列の型と写し方の組**: U1 の機能設計が決める（Units Generation のレビュー R-03）。この段では「組を U1 が決め、U2 が従う」ことだけを約束にし、型そのものは未決の点として残す。
- **repository の口（U2 が持ち、U3 が使う）**: U2 は書き直す repository のメソッドの名前・引数・戻り値の型を変えない（業務処理の層は変えない、ADR-001・ADR-003）。
- **起動の失敗**: 対応しない種類・届かない接続先・移行の失敗では起動を止める（FR1.3・FR2.4・FR3.10）。単位の間での再試行や時間の上限の約束は持たない（実行時の通信が無いため）。
- **変更の決め方**: すべて同じリポジトリと1つの WAR の中にあるため、約束の変更は、変える単位の設計の段で依頼者の承認を得て行う。版の番号は付けない。
- **コンテナの実行環境が無いとき**: 対象DB のテストの今の部品（`targetdb/testsupport` の `ContainerRuntimeCheck`）と同じ扱いで、警告を出して飛ばす。明示して CI で流したときは飛ばさない（FR5.3）。

## Q1: PostgreSQL の結合テストを流す入口（U1 が作り、U3 が使う）

要件は「Gradle の明示の指定を付けたときだけ流れ、普段の `verify`・`verifyQuick`・CI のプッシュでは流れない」（FR5.2）。名前は ADR-005 で後の設計に回したが、U3 の結合テストを U1 と並べて書けるよう、形と名前をこの段で決めたい。

A. 専用のタスク `./gradlew :backend:internalDbPostgresTest` を足す。PostgreSQL の結合テストは JUnit のタグ `internal-db-postgres` を付けた `*IT` とし、今の `integrationTest` はこのタグを外す。CI は手動の実行の入力（`internal_db_postgres: true`）のときだけこのタスクを流す（推奨）
B. 今の `integrationTest` に `-PinternalDb=postgresql` を付けたときだけ、タグの付いたテストに切り替えて流す（タスクを増やさない）
C. `-PinternalDb=postgresql` を付けたときだけ、今の `integrationTest` にタグの付いたテストを足して一緒に流す
X. Other (please specify)

[Answer]: A

## Q2: PostgreSQL の結合テストの土台の形（U1 が作り、U3 が使う）

A. コンテナは JVM に1つだけ起動して使い回し、Spring の文脈を共有する。テストのクラスには共通の注釈（`@InternalDbPostgresIT` の形。タグと接続先の設定をまとめる）を付ける。各テストは自分で作ったデータだけを使って後始末する。空の内部DB が要るテスト（移行・初期管理者・移行の失敗）だけは、同じコンテナに自分用のデータベースを作って使い、終わったら消す（推奨）
B. テストのクラスごとにコンテナを起動する（分離は強いが、明示して流すときの時間が長い）
C. コンテナは1つで、テストのクラスごとにデータベースを作り直す（分離と速さの中間。すべてのクラスで移行を流し直す）
X. Other (please specify)

[Answer]: A

## Q3: 内部DB の PostgreSQL のイメージの固定先（テストの側）

FR4.2 は「対象DB の PostgreSQL と同じ版とダイジェスト」、ADR-005 は「`TargetDbImages` の値を使う」と決めた。テストの定数をどう持つかを決めたい。

A. 内部DB の結合テストも `targetdb/testsupport` の `TargetDbImages.postgres()` をそのまま使う。固定先がテストでは1か所のままで、上げるときにずれない。`.github/dependabot.yml` の一覧に足すのは compose の内部DB 用のサービスだけになる（推奨）
B. 内部DB 用の定数（`InternalDbImages` の形）を別に置き、`TargetDbImages` と同じ値であることを単体テストで突き合わせる。固定先の一覧にテストの定数も足す
X. Other (please specify)

[Answer]: A
