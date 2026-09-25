# Code Generation の結果 — 前の Intent で後に回した小さな修正7件

承認済みの計画（`code-generation-plan.md`）の Step 1〜19 を実行した。Step 20（統合）は行っていない。コミットはしていない（依頼者の承認の後に C1〜C6 のまとまりで行う）。

## 変更したファイル

コミットの提案のまとまりごとに並べる。まとまりどうしでファイルは重ならない。ただし `README.md` だけは C5 と C6 にまたがる（下の「README の分け方」）。

| まとまり | ファイル | 変更 | 要件 |
|---|---|---|---|
| C1 | `backend/src/main/java/cherry/mastersmith/auth/service/LoginService.java` | 行が無いときは判定のトランザクションで何も書かずに終え、別の短いトランザクションで行を作り（重複の `DataIntegrityViolationException` は「既にある」として DEBUG を1回出す）、判定をやり直す。2回目も行が無ければ `IllegalStateException` | FR2.1、FR2.3 |
| C1 | `backend/src/test/java/cherry/mastersmith/auth/service/LoginServiceTest.java` | T1〜T4（`createsMissingRow` を置き換え、3件を追加） | FR2.1〜FR2.3 |
| C1 | `backend/src/test/java/cherry/mastersmith/auth/service/LoginConcurrencyIT.java` | T5〜T8 を追加。スレッドの数を 8 から 12 に | FR2.1、FR2.2 |
| C2 | `backend/src/main/java/cherry/mastersmith/config/ObservabilityConfig.java` | `setCaptureKeyValuePairAttributes(true)` と、`LogRecordExporter` を包む Bean の後処理 | FR1.1、FR1.2 |
| C2 | `backend/src/main/java/cherry/mastersmith/common/observability/SanitizingLogRecordExporter.java`（新規） | 4つのキーの値を `[REDACTED]` にして送る | FR1.2、FR1.3 |
| C2 | `backend/src/test/java/cherry/mastersmith/common/observability/SanitizingLogRecordExporterTest.java`（新規） | T9・T10 ほか（5件） | FR1.2 |
| C2 | `backend/src/test/java/cherry/mastersmith/common/observability/OtlpLogExportIT.java`（新規） | T11〜T13 | FR1.1、FR1.2、FR1.4 |
| C2 | `backend/src/main/java/cherry/mastersmith/common/observability/SingleLineMessageJsonProvider.java`（新規） | メッセージの改行の並びを ` ⏎ ` に置き換える | FR4.1、FR4.3 |
| C2 | `backend/src/main/resources/logback-spring.xml` | `<message>` を新しい部品に替える | FR4.1 |
| C2 | `backend/src/test/java/cherry/mastersmith/common/observability/JsonLogFormatTest.java` | T14・T15 を追加。既存の `lineBreaksEscaped` の期待の値を FR4 に合わせた（「計画との差」の 2） | FR4.1、FR4.3 |
| C3 | `vendor/make-you-chic-ui`（gitlink だけ） | `5258c8bb987b0fa6ffd0ad7c4eadc7d4006da52d` → `edb1f943c0e66293494fa974605f34fcd7e258d7` | FR7.1 |
| C4 | `frontend/src/features/dsl/messages.ts` | `dsl.action.close`（閉じる・Close） | FR7.2 |
| C4 | `frontend/src/features/dsl/DslConfirmDialog.tsx` | Modal に `closeLabel` | FR7.2、FR7.3 |
| C4 | `frontend/src/features/dsl/DslAdminPage.tsx` | Alert に `dismissLabel` | FR7.2 |
| C4 | `frontend/src/features/dsl/DslConfirmDialog.test.tsx` | T16・T17 | FR7.2、FR7.3 |
| C4 | `frontend/src/features/dsl/DslAdminPage.test.tsx` | T18（535 行の既存のテストは変えていない） | FR7.2、FR7.4 |
| C5 | `compose.yaml` | メモリの既定を 2g に。見本の対象DB の3サービスを `.env.targetdb` を読み、起動の入口を包む形に | FR5.1、FR6.2 |
| C5 | `docker/perf/compose.yaml` | メモリの既定を 2g に | FR5.2 |
| C5 | `.env.example` | メモリの既定の説明を 2g に。見本の対象DB の2項目を外し `.env.targetdb.example` を指す | FR5.2、FR6.1 |
| C5 | `.env.targetdb.example`（新規） | 2項目（値は空）とヘッダー・説明 | FR6.1 |
| C5 | `.gitignore` | `!.env.targetdb.example` | FR6.3 |
| C5 | `docker/check-container-limits.sh` | 既定の期待を 2g に。「5. 環境変数の分け方」を追加 | FR5.2、FR6.2 |
| C5 | `docker/targetdb/postgres/02-reader-account.sh`・`docker/targetdb/mysql/02-reader-account.sh`・`docker/targetdb/mariadb/02-reader-account.sh` | 説明とエラーの文言を `.env.targetdb`（負荷の試験の環境では一時の環境ファイル）に | FR6.1 |
| C5 | `docker/targetdb/generate-large-schema.sh` | 使い方の説明の変数を `$MASTERSMITH_SAMPLE_TARGETDB_ADMIN_PASSWORD` に | FR6.2 |
| C5・C6 | `README.md` | 下の「README の分け方」 | FR1.2、FR4.1、FR5.2、FR6.3 |
| C6 | `perf/k6/scenarios.js` | `loginLoop` の利用者を `exec.vu.idInTest` だけで選ぶ。`PERF_USER_COUNT`（既定 11）の確かめ、VU ごとの最初の回の `loginLoop-user` の1行 | FR3.1、FR3.2 |
| C6 | `perf/README.md` | 試験用の利用者を 11 名に。`dslMixed` を行が無いまま流すことと、重なりの確かめ方 | FR3.1、FR8.1 |

### README の分け方

- C5（FR5・FR6）: 「コンテナでの起動と確認」の初回の `cp .env.targetdb.example` の行・9項目の説明・パスワードを変えるときの注意・`.env` からの移し替えの手順、「コンテナの資源の上限」の VM を広げた後の段落・「既知の制約」・「設定の効き方の確かめ」、「環境変数」の前書き・`MASTERSMITH_CONTAINER_MEMORY` の行・`MASTERSMITH_SAMPLE_TARGETDB_*` の2行、「手元で試す対象DB」の前書きの1文・手順 1・手順 4。
- C6（FR1・FR4）: 「コンテナでの起動と確認」のログの行（改行の ` ⏎ `）、「外部エクスポートの確かめ方」の末尾の段落（ログの属性と伏せ方）、「監査ログ（U4）」の記録の失敗の行（外へ送るときは伏せる）。

## 実測の結果

### Step 1 変更の前の基準（`./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`、HEAD 5fd0891）

| 項目 | 値 |
|---|---|
| バックエンドの単体テスト | 716 件、失敗 0、飛ばし 0 |
| バックエンドの結合テスト | 375 件、失敗 0、飛ばし 0 |
| 画面のテスト | 47 ファイル・313 件、すべて成功 |
| バックエンドのカバレッジ（JaCoCo の全体） | 行 98.08%（3884/3960）、分岐 94.14%（1349/1433） |
| 画面のカバレッジ | 行 97.86%（961/982）、分岐 93.6%（585/625） |
| 時間 | 4分34秒 |

- 1回目は、このシェルに README の「対象DB の結合テストとコンテナの実行環境」の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` が無く、対象DB のテストが `SKIPPED` になり、パッケージごとの下限（`targetdb.repository` 行 34%）で失敗した。README の2つの環境変数を付けて流し直した上の値を基準とした。以降の実行も同じ環境変数を付けた。
- この Intent に絞ったコマンドのうち既存のテストのクラスの分は、Step 4・6・8 で指定どおりに動いた。

### Step 2 直す前の状態

- 配備済みのアプリのログ（78 件、すべて JSON）のうち、メッセージに改行を含むのは2件で、ロガー名はどちらも `org.hibernate.orm.connections.pooling`（見込みどおり。C-3 を確かめた）。
- サブモジュールの gitlink は `5258c8bb987b0fa6ffd0ad7c4eadc7d4006da52d`（C-4 を照合した）。`fetch` の後、`5258c8b..edb1f94` の間のコミットは `edb1f94`（Modal/Alert にアクセシビリティ関連の拡張インタフェースを追加）の1つだけ。変わるファイルは Alert・Modal の本体とテストの4つで、`package-lock.json` は変わらない。
- 見本の対象DB のイメージの本来の入口と `CMD`: PostgreSQL `["docker-entrypoint.sh"] ["postgres"]`、MySQL `["docker-entrypoint.sh"] ["mysqld"]`、MariaDB `["docker-entrypoint.sh"] ["mariadbd"]`。

### Step 4 業務処理の層のテストと不具合の再現

- `LoginServiceTest` 12 件、`LoginConcurrencyIT` 7 件、守るべき既存の結合テスト（`LoginAttemptStateRepositoryIT` 7・`LoginApiIT` 5・`AuthSettingsIT` 6・`AuthSecretLeakIT` 1・`RefreshConcurrencyIT` 3・`AuditAuthenticationEventsIT` 6・`AuditTraceIdIT` 3・`AuditRollbackIT` 3・`AuditWriteFailureIT` 6・`AuditWriteTimingIT` 3）がすべて成功。
- 再現: `git stash push -- backend/src/main/java/cherry/mastersmith/auth/service/LoginService.java` で直しを外して `LoginConcurrencyIT` を流すと、新しい4件（T5〜T8）がすべて失敗し、既存の3件は成功した。T5 では同時の 10 本のうち 9 本が内部の失敗だった。例外はどれも `DataIntegrityViolationException`（H2 の主キー違反 `JdbcSQLIntegrityConstraintViolationException` を Hibernate の `ConstraintViolationException` 経由で変換したもの）で、受け止める型が計画の見込みどおりと確かめた。T6 は待ち合わせの形（行を作りかけて確定していない別のトランザクションの間に、ログインが H2 の中で待ちに入ったことをスレッドの状態で見張る）で再現した。
- 直しを戻した後、`LoginConcurrencyIT` を3回続けて流し、3回とも 7 件すべて成功した。

### Step 6・8 ログの出力のテスト

- 単体: `SanitizingLogRecordExporterTest` 5 件、`JsonLogFormatTest` 9 件（FR4 の前の状態で 7 件がそのまま通ることも Step 6 で確かめた）。
- 結合: `OtlpLogExportIT` 3・`ExternalExportIT`（入れ子を含め 3）・`TracingAndLoggingIT`（入れ子を含め 11）・`AuditNotInAppLogIT` 3・`AuditSecretLeakIT` 3・`AuthSecretLeakIT` 1・`AccessSecretLeakIT` 1・`TargetDbSecretLeakIT` 1・`AuditWriteFailureIT` 6 がすべて成功。

### Step 10・12 画面のテスト

- 固定先の更新だけの状態: 47 ファイル・313 件すべて成功（カバレッジは基準と同じ）。DSL の2つのファイルは 34 件すべて成功。
- 画面の変更の後: DSL の2つのファイルが 37 件（34 ＋ T16〜T18）すべて成功。

### Step 14 環境とビルドの設定

- `./docker/check-container-limits.sh`: 20 件すべて期待どおり（両方の compose で変数なし 2147483648・768m で 805306368、JVM の確かめ 8 件、節5 の 8 件）。
- 直す前の `compose.yaml`（`git stash push -- compose.yaml` で一時的に戻した状態）: 7 件が失敗（既定の 1g が1件、見本の対象DB の3サービスの `env_file` と `environment` のパスワードが6件）。`app` の2件は直す前でも通る（「残る点」の 3）。
- R-03 の基準 1: `git check-ignore -q .env.targetdb` は 0。基準 2: `.env.targetdb.example` は 1。基準 4: 値の入った行なし。
- 案 A の入口の包み方: 乱数（表示しない）の一時の `.env.targetdb`（権限 600）で、プロジェクト名 `mastersmith-fr6check`・空の環境ファイル（`.env` を読まない）で3種類を1つずつ起動した。3種類とも、`02-reader-account.sh` が誤りなく動き、読み取りのアカウントと管理者が `.env.targetdb` の値でログインでき、誤ったパスワードは拒否された（PostgreSQL はコンテナの IP 経由のパスワード認証で確かめた）。`docker compose exec` の環境には `POSTGRES_PASSWORD`・`MYSQL_ROOT_PASSWORD`・`MARIADB_ROOT_PASSWORD` が無い（README の手順 4 の変数の置き換えが正しい）。確かめの後、`down -v` で消し、一時の `.env.targetdb` も消した。配備したアプリと見本の対象DB（プロジェクト `mastersmith`）には触れていない。
- R-03 の基準 6（参考）: リポジトリの外に同じ形のファイル（16 進の乱数と Base64 の乱数）を作り、`gitleaks dir` を既定の規則とリポジトリの `.gitleaks.toml` の両方で流したところ、どちらも検出されなかった（`no leaks found`、Gitleaks 8.30.1）。計画どおり規則は足していない。

### Step 16 台本の確かめ

- 台本を読んで: `loginLoop` の利用者は `userEmail(exec.vu.idInTest)` だけで決まり、剰余で番号を畳まない（R-01 の「台本を読んで」の基準）。単独の場面の選び方は変えていない。
- `k6 inspect`（`--include-system-env-vars` を付けた形。「計画との差」の 5）: 直す前と後で、9つの場面すべての `options.scenarios` と閾値が同じ（`dslMixed` は `dslHeavy` 1・`logins` 10）。
- 通信を切った状態（`--network none`）で `dslMixed` を流し、`VUS=11` では `setup()` が「VUS + 1 = 12 名要ります（PERF_USER_COUNT=11）」で止まることと、`VUS=10` では `loginLoop-user` の行が 10 行（VU 8 は `dslHeavy`）で利用者 01〜07・09〜11 に重なりが無いことを見た。要求はどこにも届いていない。流したときの記録の合否（R-01）は Build and Test で行う。

### Step 18 統合の前の関門（`./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`）

| 項目 | 値 |
|---|---|
| 結果 | 成功（4分52秒） |
| バックエンドの単体テスト | 726 件（基準 +10）、失敗 0、飛ばし 0 |
| バックエンドの結合テスト | 382 件（基準 +7）、失敗 0、飛ばし 0。対象DB のテストも実行（ログに `SKIPPED` 0 件） |
| 画面のテスト | 47 ファイル・316 件（基準 +3）、すべて成功 |
| バックエンドのカバレッジ | 行 98.08%（3941/4018）、分岐 93.83%（1369/1459）。パッケージごとの下限も通過（新しいパッケージは作っていない） |
| 画面のカバレッジ | 行 97.86%（961/982）、分岐 93.6%（585/625） |
| ライセンスヘッダー・フォーマット・リンタ・型検査 | 通過 |
| Gitleaks（履歴 156 コミット） | 検出なし |
| OSV-Scanner | `UP-TO-DATE`（入力の lockfile が変わっていないため再実行されず。サブモジュールの `package-lock.json` も固定先の間で変わっていない） |
| SpotBugs | 止める指摘なし。新しい警告は `SanitizingLogRecordExporter` の `CT_CONSTRUCTOR_THROW`（priority 2）1件で、既存の `SanitizingSpanExporter` と同じ形 |
| E2E（`./gradlew e2eTest`、verify の外） | 6 件すべて成功 |

- 新しいクラスのカバレッジ: `SanitizingLogRecordExporter` 行 19/19・分岐 4/4、`SingleLineMessageJsonProvider` 行 7/7・分岐 4/6、`LoginService` 行 66/66・分岐 21/24。
- `git status`: `.env`・`.env.targetdb` は対象に入っていない。`vendor/make-you-chic-ui` の変更は gitlink だけ（サブモジュールの中に変更なし）。`docker/monitoring/`・`application.yaml`・`Dockerfile`・`.github/workflows/ci.yml`・`LoginAttemptStateRepository.java`・Flyway の版に差分なし。
- R-03 の基準 3（コミットの後の `git ls-files`）は、コミットをしていないため未確認。C5 のコミットの後に確かめる。

## 判断

- FR2: 行の作成を判定とは別の短いトランザクションにし、重複を「既にある」として判定をやり直す（計画の採用案）。受け止める型は、再現で実際に出た `DataIntegrityViolationException` にした。トランザクションは順に行うため、同時に使う接続は1本のまま。行がある利用者の道は SQL の種類と回数が変わらない（`LoginApiIT` が変更なしで通る）。
- FR1: `LogRecordExporter` を包む形にした。SDK 1.62 の `LogRecordData` のすべての取り出し口を元の記録に任せ、`getAttributes` だけを差し替えた。`ExtendedLogRecordData`（SDK の内部の型）は実装しないため、OTLP の送信の部品は差し替えた属性を使う（`OtlpLogExportIT` で確かめた）。処理の部品の並びに切り替える予備の形は要らなかった。伏せるキーは実装の直前に main のコードを洗い直し、計画の4つから増えていないことを確かめた。
- FR4: 改行の並び（CRLF・CR・LF）1つを ` ⏎ ` 1つにする。スタックトレースの項目は別の部品のため改行を残す。
- FR6: 案 A（起動の入口を `sh -c` で包み、`export` してから `exec docker-entrypoint.sh "$@"`、`command` に本来の `CMD`）。代入を `exec` の前に置く形ではなく `export` にしたのは、特殊な組み込みの `exec` の前の代入が環境に渡るかが sh によって揺れるため。

## 計画との差

1. Step 1 の1回目は `DOCKER_HOST` などが無く対象DB のテストが飛ばされたため、README の環境変数を付けて流し直した（上の Step 1）。
2. `JsonLogFormatTest` の既存のテスト `lineBreaksEscaped` の期待の値を、`"改行を含む\n値"` から `"改行を含む ⏎ 値"` に直した。FR4 はメッセージの値の改行をなくすことそのもので、既存のテストの期待と必ず食い違うため。キーと値の改行が1行に収まり別の記録を偽れないことの確かめは変えていない。
3. T11（`OtlpLogExportIT` のキーと値の属性）は、監査の書き込みの失敗の ERROR の `auditEventType`（値 `LOGIN_SUCCEEDED`）と `exceptionType` で確かめた。計画の例の `dsl.operation` は DSL の操作を要するため、点を含むキーの名前がそのまま属性になることは単体テスト（`SanitizingLogRecordExporterTest` の T10 の `dsl.operation`）で確かめた。
4. 単体テストは計画の T1〜T4・T9・T10・T14・T15 に加えて、`SanitizingLogRecordExporterTest` に3件（文字列以外の値、伏せるキーが無いときは同じ記録を渡す、`close` を含む委ね）を足した。T2 では DEBUG のログを捕まえるため、テストの中でそのロガーの水準を一時的に DEBUG にして戻す。
5. `unit-test-instructions.md` の `k6 inspect` のコマンド（`docker run -e SCENARIO=...`）では、`inspect` が環境変数を `__ENV` に入れず、場面の名前が `undefined` になって `options.scenarios` を確かめられなかった。実際には `inspect --include-system-env-vars /scripts/scenarios.js` を使った（文書は承認の対象のため変えていない）。
6. `docker/check-container-limits.sh` の確かめに使うイメージ `mastersmith:local` は作り直していない。配備したアプリが同じタグを使っているため。`Dockerfile` は変えておらず、節1・節5 はイメージを使わない。
7. `docker/check-container-limits.sh` の節5 を書いた直後、直す前の設定で `grep` が何も見つけないと `set -euo pipefail` でスクリプトが途中で抜ける不具合があった。取り出しの関数を失敗にしない形に直してから、上の結果を取った。
8. README は計画に挙げた行のほかに、既定が 2g になったことに合わせて「コンテナの資源の上限」の VM を広げた後の段落、`compose.yaml` の先頭の説明の1行、「設定の効き方の確かめ」の説明を直した。移し替えの手順には、戻すときのために `.env` をリポジトリの外へ表示せずに複写する手順を入れた（project.md の Deployment の学び）。
9. `02-reader-account.sh` の文言は、負荷の試験の環境と共有するため「`.env.targetdb`（負荷の試験の環境では一時の環境ファイル）」とした。

## 要件の前提との差

- FR8.1: `perf/README.md` には「先に1人ずつログインしてロックの状態の行を作る」の手順がもともと書かれていない（前の Intent の記録と project.md の学びにだけある）。そのため、`dslMixed` の説明に「行が無いまま流す」を明記する形で行った。確定済みの前の記録は書き換えていない。
- project.md の Testing Posture の学び（「内部DB に SQL で直接入れた試験用の利用者は……1人ずつログインさせて行を作る」）は、FR2 の直しで要らなくなる。memory のファイルは変えていない。
- `perf/README.md` の `dslMixed` の行（「コンテナの上限 1g で流し」）と `perf/k6/scenarios.js` 30 行（「コンテナの上限を 1g にして流し」）は、前の Intent の決定（NFR1.12 の条件を配備の既定 2g とする）と食い違っていた。生成の後に依頼者の判断（直す）で、この2か所と `perf/README.md` 109 行の NFR1.12 の条件の説明を 2g に直し、C6 に含めた（生成の担当の Step 18 の `verify` の後の変更で、文言だけ）。

## 残る点（後の段へ・依頼者に確かめたい点）

1. Gitleaks は `.env.targetdb` の形の乱数の値を既定の規則でもリポジトリの設定でも検出しなかった。守りの本体は `.gitignore`（基準 1・2）。依頼者の判断（足さない）で規則は足さない。
2. R-03 の基準 3 は C5 のコミットの後に確かめ、満たした（「コミット」を参照）。
3. `docker/check-container-limits.sh` の節5 の `app` の2件は、直す前の `compose.yaml` でも通る。漏れの原因は `compose.yaml` の形ではなく `.env` の中身のため。値が入った `.env` を読んだ状態でのアプリの環境変数の確かめ（名前だけ）は、README の移し替えの手順の最後のコマンドで、Deployment Execution の段で行う。
4. Build and Test に回すもの: FR8.2 と FR3.1 の流したときの記録（k6 の `dslMixed` を行が無い利用者のまま流し、ログインの `checks` の率 1、`loginLoop-user` の行の数が `VUS` と同じで重なり 0 件）、FR4.1 の起動のログでの確かめ（Hibernate の案内が1行1件になること。ロガー名は Step 2 で `org.hibernate.orm.connections.pooling` と確かめた）。
5. Deployment Execution に回すもの: この PC の `.env` から `.env.targetdb` への移し替えと、アプリのコンテナの環境変数の確かめ。見本の対象DB のコンテナも新しい入口で作り直すことになる（ボリュームはそのまま）。
6. `perf/README.md` と `perf/k6/scenarios.js` の 1g の記述は依頼者の判断で直した（C6）。project.md の学び（先に1人ずつログインして行を作る）が要らなくなった点を学びの仕組みで扱うかは、この段の承認の前の学びの確認で扱う。

## コミット

依頼者の承認を得て、作業ブランチ `fix/260924-followup-fixes` に次の順でコミットした（`5fd0891` の計画の承認の記録の後）。R-03 の基準 3 はコミットの後に確かめた（`git ls-files` が `.env.targetdb.example` だけを示し、`git check-ignore -q .env.targetdb` が 0）。

- C1 `37e60be`: FR2
- C2 `1e1195b`: FR1・FR4
- C3 `b244d3c`: サブモジュールの固定先 `5258c8bb987b0fa6ffd0ad7c4eadc7d4006da52d` → `edb1f943c0e66293494fa974605f34fcd7e258d7`（専用のコミット）
- C4 `e5a63e5`: FR7 の画面
- C5 `3c3f746`: FR5・FR6（README のその部分を含む）
- C6 `295407f`: FR3・FR8.1 と 1g の記述の直し、README のログの説明
