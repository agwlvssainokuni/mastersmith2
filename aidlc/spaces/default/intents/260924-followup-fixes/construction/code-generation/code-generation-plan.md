# Code Generation の計画 — 前の Intent で後に回した小さな修正7件

## 対象と前提

- 対象: 単位の分割の無い不具合の修正（scope: bugfix、Depth: Minimal、Test Strategy: Minimal、Brownfield）。単位なしの1回の実装とし、成果物はこのディレクトリ（`aidlc/spaces/default/intents/260924-followup-fixes/construction/code-generation/`）に置く。
- 入力: 要件 `aidlc/spaces/default/intents/260924-followup-fixes/inception/requirements-analysis/requirements.md`（FR1〜FR8、NFR1〜NFR6）、質問と回答 `requirements-analysis-questions.md`（Q1: B、Q2: B、Q3: A、Q4: A、Q5: A）、コードの知識ベース `aidlc/spaces/default/codekb/mastersmith2/`（`code-quality-assessment.md` の TD-1〜TD-7・C-1〜C-6）。
- 設計の段（Functional Design〜Infrastructure Design）は無い。直し方は要件と、この計画を書く前に読んだ実際のコードから決めた。
- 承認の場の決定:
  - Reverse Engineering: 承認。変更なし。
  - Requirements Analysis: 承認。レビューの指摘 R-01（FR3.1 の「流したときの記録」の判定の仕方）・R-02（FR1.2 の伏せ方）・R-03（FR6.3 の合否の基準）は Minor のまま、要件は直していない。この計画で、R-01・R-03 は機械的に確かめられる基準を、R-02 は伏せた値の書き方を決める（下の各節）。要件の文書は書き換えない。
- 段の分担（この段で行わないこと）:
  - k6 を流す確かめ（FR8.2）と、FR3.1 の「流したときの記録」の確かめは Build and Test の段で、使い捨ての環境で行う（project.md の Testing Posture の学び）。
  - Hibernate の案内が起動のログで1行1件になったことの最終の確かめ（FR4.1 の前半）は、Build and Test の段で使い捨ての環境を起動するときに行い、ロガー名とともに記録する。この段では、配備済みのアプリの今のログからロガー名を読み取る（Step 2）。
  - この PC の `.env` から `.env.targetdb` への移し替え（FR6.3 の手順の実施）は Deployment Execution の段で、依頼者の確認を得て行う。`.env` は秘密情報を含むため、この段では開かない。
- 作業の場所: `develop` から短命の作業ブランチ（例: `fix/260924-followup-fixes`）を作って作業する。サブモジュールの固定先の更新を専用のコミットとして残す必要がある（project.md の Mandated）ため、統合は squash ではなく fast-forward で行う（「コミットの分け方」と「懸念と承認の場で確かめたいこと」を参照）。
- `vendor/make-you-chic-ui` の中身は直接変えない（project.md の Forbidden）。取り込むのはサブモジュールの固定先の更新だけ。
- DB のスキーマは変えない（要件の制約）。Flyway の新しい版は作らない。

## 直し方の要点

### FR1 手元の監視（Loki）でログのキーと値を絞り込めるようにする

- `backend/src/main/java/cherry/mastersmith/config/ObservabilityConfig.java` の `OtlpLogAppenderInstaller.afterPropertiesSet()` で、`appender.start()` の前に `appender.setCaptureKeyValuePairAttributes(true)` を呼ぶ（FR1.1）。属性の名前はキーの名前そのもの（例: `dsl.operation`）になる。
- 伏せる処理は、送り出しの直前に置く（FR1.2、FR1.3）。既存の `SanitizingSpanExporter`（トレース）と同じ形で、ログの送信の仕組み（`LogRecordExporter`）を包む `SanitizingLogRecordExporter` を `backend/src/main/java/cherry/mastersmith/common/observability/` に新しく置き、`ObservabilityConfig` の Bean の後処理（`BeanPostProcessor`）で `LogRecordExporter` の Bean を包む。
  - 包む形を選んだ理由: 処理の順番（`LogRecordProcessor` の並び）に頼らず、送り出す直前の1か所で必ず効く。標準出力（`logback-spring.xml` の `JSON_STDOUT`）は別の経路のため変わらない（FR1.3）。
  - SDK 1.62 には、ログの記録を部分だけ差し替える既製の部品が無い見込みのため、`LogRecordData` のすべての取り出し口（既定の実装を持つ `getBodyValue`・`getEventName` などを含む）を元の記録に渡し、`getAttributes` だけを差し替える。実装の途中で、差し替えが SDK の版で成り立たないと分かったときは、同じ Bean の後処理の中で `LogRecordProcessor` を最も先に並べる形に切り替え、差として記録する。
- 伏せるキー: `email`・`enteredEmail`・`sourceIp`・`userAgent`。main のコードのキーと値をすべて洗い出した結果（`addKeyValue` の全キーと `AuditEventListener` の項目の一覧）、個人に関する値はこの4つだけだった。`userId`・`actorUserId`（内部の番号）、`requestPath`（問い合わせの部分を除いた道）、`traceId`・`dslHash` は伏せない。PART 2 の実装の直前にもう一度洗い出し、増えていれば足す。
- 伏せた値の書き方（R-02 の案）: 固定の文字列 `[REDACTED]` に置き換える。キーは残す（FR1.2）。値が文字列以外の型でも、元の型の属性を消して文字列の `[REDACTED]` にする。ハッシュ値にしない理由は、メールアドレスのハッシュは候補の一覧から逆に引けるうえ、同じ人の記録を結び付けられるため。
- 監視の警報とダッシュボード（`docker/monitoring/`）は変えない（FR1.5）。
- README の「外部エクスポートの確かめ方」に、ログのキーと値が属性として送られることと、伏せるキーと値の書き方を書く。541 行の「監査の書き込みの失敗の ERROR」の説明に、外部へ送るときは伏せることを足す。

### FR2 ロックの状態の行が無い利用者の同時の初めてのログイン

- 起きている道: `LoginService.decide()` は1つのトランザクションの中で `lockUserRow()` を呼び、行が無ければ `createIfAbsent()`（H2 の `MERGE`）で作る。同じ利用者の初めてのログインが2つ同時に来ると、後の `MERGE` が主キーの重複で失敗し、500 になる（TD-2）。JPA の問い合わせで例外が出るとトランザクションは巻き戻し専用になるため、同じトランザクションの中で例外を受け止めて続けることはできない。
- 直し方（採用）: 行の作成を、判定のトランザクションとは別の短いトランザクションに移し、重複は「既にある」として扱う。
  1. 判定のトランザクションで行を排他つきで読み、無ければ何も書かず、出来事も知らせずに「行が無い」を返して終える。
  2. 判定のトランザクションが終わってから、別の短いトランザクションで `createIfAbsent()` を行う。同時の別のログインが先に作っていて重複の例外（`DataIntegrityViolationException`。実際の例外の型は PART 2 で確かめる）になったときは、「既にある」として DEBUG のログを1回出して進む（例外を捕まえて何もしないコードにはしない）。
  3. 判定のトランザクションをもう一度行う。2回目も行が無ければ `IllegalStateException` で失敗させる（起きないはずの状態のため、黙って続けない）。
- 守ること:
  - 判定・失敗の回数の数え方・ロックのしきい値は変えない（FR2.1）。判定は行の排他の中で行う今の形のまま。
  - 出来事の知らせ（監査）は、判定を終えたトランザクションの中で1回だけ行う。1回目の「行が無い」のときは知らせないため、同じログインが二重に記録されない。監査の受け取り側は今のまま確定の後に記録する（`AuditEventListener` の `AFTER_COMMIT`）。
  - 接続は同時に1本しか使わない（トランザクションを順に行うため、接続プールの使い方は今と同じ）。
  - 行がある利用者（ふだんの道）は、SQL の種類と回数が今と同じ（`LoginApiIT` 178〜179 行の確かめが変わらない）。
  - トランザクションの境界は業務処理の層（`LoginService`）にだけ置く（team.md の Code Style）。`LoginAttemptStateRepository` は変えない。
- 比べて採らなかった案:
  - 毎回のログインの前に行を作るトランザクションを置く: ふだんの道の SQL が増え、`LoginApiIT` の確かめと性能が変わる。
  - 同じトランザクションの中で重複の例外を受け止める: JPA ではトランザクションが巻き戻し専用になり、続けられない。
  - 行の作成を `REQUIRES_NEW` で入れ子にする: 判定のトランザクションが接続を持ったまま2本目の接続を借りるため、前の Intent の接続プールの問題（1要求で2本）を増やす。
- 組み込みの H2 を内部DB に使う。テストもコンテナではなく本番と同じ組み込みの H2 で行う（team.md の Testing Posture の学び）。

### FR3 負荷の試験の台本で、利用者が VU の間で重ならないようにする

- `perf/k6/scenarios.js` の `loginLoop`（`dslMixed` のログインの側）で、利用者を `userEmail(exec.vu.idInTest)` で選ぶ。`exec.vu.idInTest` は試験全体で重ならない番号のため、`logins` の VU どうしで利用者が重ならない。今の `((idInTest - 1) % 10) + 1` は、`dslHeavy` の VU が途中の番号を取ると、11 番の VU が 1 番の利用者と重なる。
- VU の番号は `VUS + 1` まで取りうるため、`dslMixed` には試験用の利用者が `VUS + 1` 名要る。環境変数 `PERF_USER_COUNT`（既定 11）を足し、`setup()` で `dslMixed` のときに `VUS + 1 > PERF_USER_COUNT` なら理由を示して止める。
- `perf/README.md` の手順 2 で入れる試験用の利用者を 11 名（`seq -w 1 11`）にし、`dslMixed` の説明（155 行）を 11 名に直す。単独の場面（`loginSuccess`・`refresh` など）の選び方は変えない（FR3.2）。
- R-01 の判定の仕方（機械的に確かめられる基準）:
  - 台本を読んで: `loginLoop` の利用者の選び方が `exec.vu.idInTest` だけで決まり、剰余などで番号を畳まないこと。
  - 流したときの記録で: `loginLoop` は VU ごとの最初の回に `loginLoop-user vu=<番号> user=<試験用の利用者のメールアドレス>` の1行を k6 のログに出す（試験用の利用者だけで、秘密の値は含まない）。Build and Test で k6 の出力からこの行を集め、行の数が `VUS` と同じで、利用者の重複が0件（`sort | uniq -d` が空）であることを合格とする。

### FR4 起動のときの Hibernate の案内を1行1件のログにする

- 標準出力の JSON は、もともと改行を `\n` と書いて1行に出しているが、メッセージの値の中に改行が残るため、読むときに1件が複数行に見える（TD-4）。そのため、合格の確かめは「出力が物理的に1行」だけでは直す前も通ってしまう。メッセージの項目の値に改行（LF・CR）が無いことで確かめる。
- `logback-spring.xml` の `<message>` を、改行を置き換えるメッセージの出力の部品 `SingleLineMessageJsonProvider`（`backend/src/main/java/cherry/mastersmith/common/observability/`、logstash-logback-encoder の `MessageJsonProvider` を継いだもの）に替える。項目の名前（`message`）と並びは変えない。
- 置き換える記号: 改行の並び（`\r\n`・`\r`・`\n`）1つを ` ⏎ `（前後に空白を置いた U+23CE）1つにする。行の頭のタブなどはそのまま残す。
- すべてのロガーに効き、ロガーの水準は変えない（FR4.2。`application.yaml` の `logging.level` は触らない）。スタックトレースの項目（`exception`）の書き方は変えない（FR4.3）。外部へ送るログの本文は対象の外（要件は標準出力の JSON だけ）。
- Hibernate の案内のロガー名: 読み取りのスキャンでは未確認（C-3）。Step 2 で配備済みのアプリのログから読み取って記録する（見込みは Hibernate 7 の `org.hibernate.orm.connections.pooling`。確かめるまでは見込みとして扱う）。

### FR5 アプリのコンテナのメモリの上限の既定を 2g にする

- 既定を `1g` から `2g` にし、合わせて直す場所は次の5か所（FR5.1、FR5.2）。
  - `compose.yaml` 63 行（`${MASTERSMITH_CONTAINER_MEMORY:-2g}`）と、説明のコメント 57〜61 行
  - `docker/perf/compose.yaml` 57 行
  - `.env.example` 24〜26 行
  - `README.md` 204〜208 行（「既知の制約（メモリの上限 1g と高い負荷）」を、既定 2g と、VM が小さい PC で下げるときの注意に書き直す）・220 行（確かめのスクリプトの説明）・230 行（環境変数の表）
  - `docker/check-container-limits.sh` 20 行（説明）・93 行（変数なしで 2147483648 を期待する）
- `MASTERSMITH_CONTAINER_MEMORY` で変える口は残す（FR5.3）。VM が 6GiB に満たない PC では `.env` で `1g` などに下げる旨を README に書く。
- 既定ではなく試験の条件として 1g と書いた箇所（`perf/README.md` 50・77・109・142 行、`perf/k6/scenarios.js` 30 行）は、既定の記述ではないため変えない。ただし 142 行と `scenarios.js` 30 行は、前の Intent の決定（NFR1.12 の条件を配備の既定 2g とする）と食い違っているため、差として記録し、承認の場で直すかを確かめる。
- FR5.2 の合格の基準: `grep -n` で、上の5か所に既定を 1g と書いた箇所が残っていない。`./docker/check-container-limits.sh` を変数なしで流して、両方の compose が 2147483648 を期待して通る。

### FR6 アプリのコンテナに見本の対象DB の管理者のパスワードを渡さない

- 見本の対象DB の2つの値（`MASTERSMITH_SAMPLE_TARGETDB_ADMIN_PASSWORD`・`MASTERSMITH_SAMPLE_TARGETDB_READER_PASSWORD`）を `.env.targetdb` に分け、見本として値を空にした `.env.targetdb.example`（Apache License 2.0 のヘッダーつき）を置く（FR6.1）。`.env.example` の 69〜73 行からは2つの値を外し、`.env.targetdb.example` を指す説明に替える。
- `compose.yaml` の `app` は今のまま `.env` だけを読む（FR6.2）。見本の対象DB の3つのサービスは、`environment:` の `${MASTERSMITH_SAMPLE_TARGETDB_*}` の展開をやめ、`env_file: .env.targetdb`（`required: false`）で読む。
  - 気をつける点: compose の `${...}` の展開はプロジェクトの `.env` から値を読むため、`.env.targetdb` に移すと、今の `POSTGRES_PASSWORD: ${MASTERSMITH_SAMPLE_TARGETDB_ADMIN_PASSWORD:-}` の形は値を受け取れない。
  - 採る形（案 A）: 3つのサービスの起動の入口（`entrypoint`）を `sh -c` で包み、`MASTERSMITH_SAMPLE_TARGETDB_ADMIN_PASSWORD` を各イメージの変数（`POSTGRES_PASSWORD`・`MYSQL_ROOT_PASSWORD`・`MARIADB_ROOT_PASSWORD`）に写してから、イメージ本来の入口（`docker-entrypoint.sh`）を `exec` で呼ぶ。compose で `entrypoint` を上書きするとイメージの `CMD` も消えるため、`command` に本来の値（`postgres`・`mysqld`・`mariadbd`）を書く。本来の値は PART 2 で `docker image inspect` で確かめてから書く。compose の中では `$` を `$$` と書き、展開させない。
  - 変数の名前が要件（FR6.1）のとおりになり、今 `.env` にある2行をそのまま移せば済む。値が空のときに DB の起動が失敗する今の動作も変わらない。
  - 案 A に伴って直す場所: `docker compose exec` の中で `$MYSQL_ROOT_PASSWORD`・`$MARIADB_ROOT_PASSWORD` を使う README の手順（347・349 行）と `docker/targetdb/generate-large-schema.sh` の使い方の説明（24・26 行）を `$MASTERSMITH_SAMPLE_TARGETDB_ADMIN_PASSWORD` に替える（`exec` の環境には入口で写した値が入らないため）。`docker/targetdb/<種類>/02-reader-account.sh` の説明とエラーの文言の「`.env`」を「`.env.targetdb`」に替える（負荷の試験の環境 `docker/perf/compose.yaml` は、これまでどおり一時の環境ファイルに各イメージの変数を直接書くため、変えない）。
  - 採らなかった案: 案 B（`.env.targetdb` に各イメージの変数の名前を直接書く。負荷の試験の環境と同じ形で compose は簡単だが、要件の変数の名前と違い、移すときに書き直しが要る）。案 D（`docker compose --env-file .env --env-file .env.targetdb` を毎回付ける。すべての手順のコマンドが長くなり、付け忘れると空のパスワードになる）。
- `.gitignore`: `.env.*` の決まりで `.env.targetdb` は既に外れているが、`.env.targetdb.example` も外れてしまう（`!.env.example` だけが例外のため）。`!.env.targetdb.example` を足す。
- README: 「コンテナでの起動と確認」の「`.env` に次の9項目」（114〜124 行）を `.env` の7項目と `.env.targetdb` の2項目に分け、環境変数の表（263〜264 行）と「手元で試す対象DB」の手順 1（318 行）を直す。今 `.env` にこの値を入れている人のための移す手順を書く（値を表示しない形。`umask 077` で `.env.targetdb` を作り、`grep '^MASTERSMITH_SAMPLE_TARGETDB_' .env` の結果を書き写してから `.env` の2行を消し、アプリのコンテナを作り直して、アプリの環境変数の名前だけを見て `MASTERSMITH_SAMPLE_TARGETDB_` が無いことを確かめる）。見本の DB のボリュームが既にあるときは、値を変えなければ DB を作り直す必要は無いことも書く。
- `MASTERSMITH_TARGET_DB_*` は今のまま `.env` で渡す（FR6.4）。
- `perf/` の環境ファイルの形への影響: `docker/perf/compose.yaml` と `perf/dsl-timing.sh` は、リポジトリの外の一時の環境ファイル（`MASTERSMITH_PERF_TARGETDB_ENV_FILE`）に `POSTGRES_PASSWORD` などを直接書く形で、`compose.yaml` の `.env`・`.env.targetdb` を読まない。そのため変えない。ただし `02-reader-account.sh` は両方の環境で共有するため、文言の変更が両方で正しいことを確かめる。
- R-03 の合否の基準（FR6.3、機械的に確かめられるもの）:
  1. `git check-ignore -q .env.targetdb` が 0 で終わる（Git 管理外）。
  2. `git check-ignore -q .env.targetdb.example` が 1 で終わる（コミットできる）。
  3. コミットの後、`git ls-files .env.targetdb` が空で、`git ls-files .env.targetdb.example` がそのファイルを示す。
  4. `.env.targetdb.example` のコメントと空行を除いた行が、すべて `名前=`（値が空）の形である（`grep -vE '^[[:space:]]*(#|$)' .env.targetdb.example | grep -vE '^[A-Z0-9_]+=$'` が何も出さない）。
  5. `./gradlew verify` の Gitleaks（履歴全体）と pre-commit の Gitleaks が通る。
  6. 参考の確かめ: リポジトリの外の一時の場所に、乱数（本物ではない値）を入れた `.env.targetdb` の形のファイルを作り、`gitleaks dir` で既定の規則が検出するかを記録する。守りの本体は `.gitignore`（2・3）であり、検出されなくても規則は足さない。検出されないときは結果を記録し、規則を足すかを依頼者に諮る。
- FR6.2 の確かめ: `docker/check-container-limits.sh` に「5. 環境変数の分け方」の節を足す。`docker compose --profile targetdb-postgres --profile targetdb-mysql --profile targetdb-mariadb config --no-env-resolution --format json` を展開し、`app` の `env_file` が `.env` だけであること、`app` の `environment` に `MASTERSMITH_SAMPLE_TARGETDB_` で始まる名前が無いこと、3つの見本の対象DB が `.env.targetdb` を読むこと、を確かめる（値は展開も表示もしない。既存のスクリプトと同じく `grep` で取り出し、`jq`・`python` に頼らない）。今の値が入った `.env` を読んだ状態での確かめ（`docker compose config` の `app` の環境変数の名前だけを見る）は、移し替えと一緒に Deployment Execution で行う。

### FR7 make-you-chic-ui の Modal・Alert の直しを取り込む

- サブモジュールの固定先を `5258c8b`（`5258c8bb987b0fa6ffd0ad7c4eadc7d4006da52d`）から `edb1f94`（`edb1f943c0e66293494fa974605f34fcd7e258d7`）に更新する（FR7.1）。それだけの専用のコミットにし、コミットのメッセージに前後の完全なハッシュを記録する（project.md の Mandated）。
  - 更新の前に、親のリポジトリの gitlink が本当に `5258c8b` であること（`git ls-tree HEAD vendor/make-you-chic-ui`。C-4 で未照合）と、`5258c8b..edb1f94` の間のコミットが `edb1f94` の1つだけであることを確かめる。`vendor/make-you-chic-ui/package-lock.json` が変わるかも見る（OSV-Scanner の対象のため）。
  - 隣のリポジトリ（`../make-you-chic-ui`）の `edb1f94` で、`Modal` に `closeLabel`（既定 `閉じる`）と、本文を `aria-describedby` で結ぶ作りが、`Alert` に `dismissLabel`（既定 `閉じる`）が入っていることを読んで確かめた。既定の値が今と同じため、固定先の更新だけのコミットでも既存のテストは通る見込み（Step 10 で確かめる）。
- 画面の変更（FR7.2）:
  - `frontend/src/features/dsl/messages.ts` に `dsl.action.close`（日本語 `閉じる`・英語 `Close`）を足す。
  - `frontend/src/features/dsl/DslConfirmDialog.tsx` の `Modal` に `closeLabel={t('dsl.action.close')}` を渡す。
  - `frontend/src/features/dsl/DslAdminPage.tsx` の `Alert`（`onDismiss` を持つ唯一の `Alert`）に `dismissLabel={t('dsl.action.close')}` を渡す。ほかの `Alert`（`LoginForm`・`DslStatusPanel` など）は閉じるボタンを持たないため変えない。
- 本文の `aria-describedby`（FR7.3）は Modal が自動で行うため、画面の側のコードは要らない。本文にはボタンの文言も含まれるが、Modal の作りのとおりとする。
- 既存のテスト `DslAdminPage.test.tsx` 535 行（名前「閉じる」で探す）は、日本語の表示では名前が変わらないため、そのまま通ることを確かめる（FR7.4）。

### FR8 負荷の試験で2件目の直しを確かめる

- 要件の前提との差: `perf/README.md` には「先に1人ずつログインしてロックの状態の行を作る」の手順は書かれていない。この手順は前の Intent の記録（`aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/performance-validation/test-results.md` 83 行）と project.md の学びにだけある。確定済みの前の記録は書き換えない。
- そのため FR8.1 は、`perf/README.md` の `dslMixed` の説明に「試験用の利用者はロックの状態の行が無いまま流す（先に1人ずつログインしない。FR2 の直しの確かめを兼ねる）」と明記する形で行う。差は code-summary.md に記録する。
- project.md の Testing Posture の学び（「内部DB に SQL で直接入れた試験用の利用者は……1人ずつログインさせて行を作る」）は、この直しで要らなくなる。memory のファイルは直接書き換えず、差として code-summary.md に記録し、学びの仕組みで扱うかを承認の場で確かめる。
- k6 を流す確かめ（FR8.2）は Build and Test の段で行う。この段は台本と手順の変更と、台本を読んで確かめる範囲（`k6 inspect` での読み込みの確かめを含む）にとどめる。

## 変更するファイル

影響の大きさは、低（文書・説明だけ、または使う場所が1つ）、中（振る舞いが変わるが使う場所が限られる）、高（認証・全体のログなど多くの経路に効く）とした。

| ファイル | 変更 | 利用者・テスト・設定の参照 | 影響 | 要件 |
|---|---|---|---|---|
| `backend/src/main/java/cherry/mastersmith/auth/service/LoginService.java` | 行の作成を別の短いトランザクションに移し、重複を「既にある」として扱い、判定をやり直す | `AuthController`・`TokenRefreshService`（`issueTokens` だけ）。テスト `LoginServiceTest`・`LoginConcurrencyIT`・`LoginApiIT`・`AuthSettingsIT`・`AuthSecretLeakIT`・`RefreshConcurrencyIT`・`TokenRefreshServiceTest`・`Audit*IT` | 高 | FR2.1、FR2.3 |
| `backend/src/test/java/cherry/mastersmith/auth/service/LoginServiceTest.java` | `createsMissingRow` を新しい流れに合わせ、重複・失敗・行が無いままの場合を足す | 単体テスト | 低 | FR2.1〜FR2.3 |
| `backend/src/test/java/cherry/mastersmith/auth/service/LoginConcurrencyIT.java` | 行が無い利用者の同時の初めてのログイン（成功・失敗・ロック・待ち合わせでの再現）を足す。スレッドの数を増やす | 結合テスト（組み込みの H2） | 低 | FR2.1、FR2.2 |
| `backend/src/main/java/cherry/mastersmith/config/ObservabilityConfig.java` | キーと値を属性として送る設定を足し、`LogRecordExporter` を包む後処理を足す | `ExternalExportIT`。送り出しの有効時だけ効く（`mastersmith.observability.export.enabled`） | 中 | FR1.1、FR1.2 |
| `backend/src/main/java/cherry/mastersmith/common/observability/SanitizingLogRecordExporter.java`（新規） | 送り出す直前に4つのキーの値を `[REDACTED]` にする | `ObservabilityConfig` だけ | 中 | FR1.2、FR1.3 |
| `backend/src/test/java/cherry/mastersmith/common/observability/SanitizingLogRecordExporterTest.java`（新規） | 伏せる・残す・委ねるの単体テスト | 単体テスト | 低 | FR1.2 |
| `backend/src/test/java/cherry/mastersmith/common/observability/OtlpLogExportIT.java`（新規） | 送り出しを有効にし、受け手が受け取った `/v1/logs` の中身を確かめる（監査の書き込みの失敗と初期管理者のログを実際に起こす） | 結合テスト。`FailingAuditEventRepositoryConfig` を使う。`ExternalExportIT` は変えない | 低 | FR1.1、FR1.2、FR1.4 |
| `backend/src/main/java/cherry/mastersmith/common/observability/SingleLineMessageJsonProvider.java`（新規） | メッセージの改行を ` ⏎ ` に置き換えて出す | `logback-spring.xml` だけ | 高（すべてのログ） | FR4.1、FR4.3 |
| `backend/src/main/resources/logback-spring.xml` | `<message>` を新しい部品に替える | すべてのロガー。`JsonLogFormatTest`・`TracingAndLoggingIT`・`LogEvents` を使う `*SecretLeakIT`・`AuditNotInAppLogIT` | 高 | FR4.1 |
| `backend/src/test/java/cherry/mastersmith/common/observability/JsonLogFormatTest.java` | 改行を含むメッセージと、スタックトレースの項目のテストを足す | 単体テスト | 低 | FR4.1、FR4.3 |
| `vendor/make-you-chic-ui`（gitlink だけ） | 固定先を `5258c8b` → `edb1f94` | `frontend/package.json` の `file:` の参照、`./gradlew verify` の `vendorBuild`・`vendorUnchanged`、CI のサブモジュールの取得、OSV-Scanner | 中 | FR7.1 |
| `frontend/src/features/dsl/messages.ts` | `dsl.action.close` を日本語と英語で足す | DSL の画面の部品すべて（鍵が増えるだけ） | 低 | FR7.2 |
| `frontend/src/features/dsl/DslConfirmDialog.tsx` | `closeLabel` を渡す | `DslAdminPage` | 低 | FR7.2、FR7.3 |
| `frontend/src/features/dsl/DslAdminPage.tsx` | `Alert` に `dismissLabel` を渡す | 画面の入口（`registration.ts`） | 低 | FR7.2 |
| `frontend/src/features/dsl/DslConfirmDialog.test.tsx` | 閉じるボタンの名前（日本語・英語）と説明の結び付きのテストを足す | 画面部品のテスト | 低 | FR7.2、FR7.3 |
| `frontend/src/features/dsl/DslAdminPage.test.tsx` | 英語の表示で `Alert` の閉じるボタンが `Close` のテストを足す（535 行は変えない） | 画面部品のテスト | 低 | FR7.2、FR7.4 |
| `compose.yaml` | メモリの既定を 2g に、説明を直す。見本の対象DB の3サービスを `.env.targetdb` を読む形（入口を包む）にし、説明を直す | 配備（`docker compose`）、`docker/check-container-limits.sh`、README の手順 | 中 | FR5.1、FR6.2 |
| `docker/perf/compose.yaml` | メモリの既定を 2g に | `perf/README.md`・`perf/dsl-timing.sh`（2g を明示して渡すため動作は同じ） | 低 | FR5.2 |
| `.env.example` | メモリの既定の説明を 2g に。見本の対象DB の2項目を外し、`.env.targetdb.example` を指す | README の手順（`cp .env.example .env`） | 低 | FR5.2、FR6.1 |
| `.env.targetdb.example`（新規） | 見本の対象DB の2項目（値は空）とヘッダー・説明 | README の手順 | 低 | FR6.1 |
| `.gitignore` | `!.env.targetdb.example` を足す | Git、Gitleaks（履歴） | 低 | FR6.3 |
| `docker/check-container-limits.sh` | 既定の期待を 2g に。「5. 環境変数の分け方」の節を足し、先頭の説明を直す | README の「設定の効き方の確かめ」 | 低 | FR5.2、FR6.2 |
| `docker/targetdb/postgres/02-reader-account.sh`・`docker/targetdb/mysql/02-reader-account.sh`・`docker/targetdb/mariadb/02-reader-account.sh` | 説明とエラーの文言の「`.env`」を「`.env.targetdb`」に | 見本の対象DB と負荷の試験の対象DB の初期化（両方で共有） | 低 | FR6.1 |
| `docker/targetdb/generate-large-schema.sh` | 使い方の説明（24・26 行）の変数を `$MASTERSMITH_SAMPLE_TARGETDB_ADMIN_PASSWORD` に | README の手順 4。負荷の試験の台本（`perf/dsl-timing.sh`）は自分のコマンドを使うため影響なし | 低 | FR6.2 |
| `README.md` | FR1（送るログの属性と伏せ方）、FR4（メッセージの改行の置き換え）、FR5（既定 2g・既知の制約・表・確かめの説明）、FR6（`.env` と `.env.targetdb` の分け方、移す手順、表、手順 1・4） | 開発者と依頼者が読む手順 | 低 | FR1.2、FR4.1、FR5.2、FR6.3 |
| `perf/k6/scenarios.js` | `loginLoop` の利用者の選び方、`PERF_USER_COUNT` の確かめ、VU ごとの最初の回の1行、説明 | 負荷の試験（Build and Test で流す） | 低 | FR3.1、FR3.2 |
| `perf/README.md` | 試験用の利用者を 11 名に。`dslMixed` の説明に、行が無いまま流すことと確かめの仕方を書く | 負荷の試験の手順 | 低 | FR3.1、FR8.1 |

変えないもの: `LoginAttemptStateRepository.java`、Flyway の版、`application.yaml` の `logging.level`、`docker/monitoring/` の警報とダッシュボード、`ExternalExportIT.java`、`vendor/make-you-chic-ui` の中身、`.github/workflows/ci.yml`、`Dockerfile`。

## 実行の手順

Testing Contract の方針は test-after で、テスト可能な層ごとに実装を書き、その層のテストを書いて実行し、すべて通ってから次の層へ進む。今回の層は、業務処理（FR2）→ 共通部品のログの出力（FR1・FR4。業務処理の層と同じ扱いの単体テストと結合テスト）→ 画面部品（FR7）→ 環境とビルドの設定（FR5・FR6・FR3）→ 文書と対応づけ、の順とする。データのモデル・DB アクセス・API の層には変更が無い（API の振る舞いは既存の結合テストで守る）。

- [ ] Step 1: 変更の前の基準を取る（実行は承認の後の PART 2）。コンテナの実行環境（colima）が動いていることを確かめ、`./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を実行して、単体・結合・画面のテストの件数、失敗の数、飛ばした数、カバレッジ（行・分岐、バックエンドと画面）を記録する（project.md の Testing Posture: 実測の数字だけを報告する）。あわせて、`unit-test-instructions.md` の、この Intent に絞ったコマンド（既にあるテストのクラスとファイルの分）が今のコードで動くことを確かめる（テストの実行の準備の確認）。
- [ ] Step 2: 直す前の状態を記録する（読み取りだけ）。
  - 配備済みのアプリのログから、メッセージに改行を含む行のロガー名だけを取り出して記録する（`docker compose logs app --no-log-prefix` の出力から `"logger"` の項目だけを数える。メッセージの本文とキーと値は表示しない）（FR4、C-3）。
  - サブモジュールの gitlink（`git ls-tree HEAD vendor/make-you-chic-ui`）と、`vendor/make-you-chic-ui` で `5258c8b..edb1f94` の間のコミットと変わるファイルの一覧（`git -C vendor/make-you-chic-ui log --oneline` と `diff --stat`。取れなければ `fetch` してから）を記録する（FR7.1、C-4）。
  - 見本の対象DB の3つのイメージの本来の入口と `CMD`（`docker image inspect --format '{{json .Config.Entrypoint}} {{json .Config.Cmd}}'`）を記録する（FR6.2）。
- [ ] Step 3: 業務処理の層を実装する（FR2.1、FR2.3）。`LoginService` を「直し方の要点」の FR2 のとおりに変える。重複の例外の実際の型を確かめ、受け止める型を決める。
- [ ] Step 4: 業務処理の層のテストを書いて実行する（FR2.1、FR2.2）。
  - `LoginServiceTest`: 行が無いときに別のトランザクションで作ってから判定し直す、作成が重複の例外でも「既にある」として判定する、行が無い利用者のパスワードの誤りが1回目の失敗として数えられる、作った後も行が無ければ失敗する。
  - `LoginConcurrencyIT`: 行を消した利用者に、正しいパスワードの同時のログイン（例: 10 本）を送り、内部の失敗が0件・行が1つ・監査の記録がログインの数と同じ（二重に記録しない）。同時の失敗 4 回は数えられてロックされない、5 回はロックされ、ロック中は正しいパスワードでも拒否される（ロックと失敗の場合。project.md の Mandated）。別のトランザクションが行を作りかけて確定していない間にログインを始め、ログインが待ちに入ったこと（スレッドの状態を期限つきで見張る。固定の時間の `sleep` に頼らない）を確かめてから確定させ、ログインが普段どおり成功する（待ち合わせでの再現）。
  - 不具合の再現の確かめ: テストが通った後、`LoginService.java` の変更だけを一時的に外して（例: `git stash push -- backend/src/main/java/cherry/mastersmith/auth/service/LoginService.java`）、同時の初めてのログインのテストが失敗する（内部の失敗が出る）ことを確かめて記録し、変更を戻す。待ち合わせの形で再現しないとき（H2 が行の排他の読み取りで待つなど）は、同時の本数と回数を増やした形で再現させ、直す前の失敗の率を記録する。どちらでも再現しないときは、依頼者に報告して進め方を確かめる。
  - 既存の結合テストが通ることを確かめる: `LoginAttemptStateRepositoryIT`・`LoginApiIT`・`AuthSettingsIT`・`AuthSecretLeakIT`・`RefreshConcurrencyIT`・`AuditAuthenticationEventsIT`・`AuditTraceIdIT`・`AuditRollbackIT`・`AuditWriteFailureIT`・`AuditWriteTimingIT`（NFR6）。
  - コミットの提案 C1（「コミットの分け方」）。直しと再現のテストを同じコミットにする（project.md の Mandated）。
- [ ] Step 5: 共通部品（ログの送り出し）を実装する（FR1.1〜FR1.3）。`ObservabilityConfig` の変更と `SanitizingLogRecordExporter` を書く。伏せるキーを main のコードでもう一度洗い出す。
- [ ] Step 6: 共通部品（ログの送り出し）のテストを書いて実行する（FR1.1〜FR1.4）。
  - `SanitizingLogRecordExporterTest`: 4つのキーの値が `[REDACTED]` になりキーは残る、ほかの属性と本文・時刻・トレースの情報は変わらない、文字列以外の値も伏せる、`flush`・`shutdown` を元に渡す。
  - `OtlpLogExportIT`（送り出しを有効にし、テストの中で起動する受け手に送る。外部には接続しない）: キーと値（例: `dsl.operation`）が属性として届く。初期管理者の作成の INFO と、監査の書き込みの失敗の ERROR（`FailingAuditEventRepositoryConfig` で追記を失敗させ、実際に HTTP でログインする）で、メールアドレス・送り元の IP・User-Agent の元の値が届いたログに無く、キーと `[REDACTED]` はある。ログイン・トークンの更新・ログアウトを行い、使ったパスワード・アクセストークン・リフレッシュトークン・署名鍵の値が `/v1/logs` と `/v1/traces` のどちらにも無い。届いた本文は protobuf のため、文字列を UTF-8 のバイト列として探す（新しい依存は足さない）。
  - 標準出力は変わらないこと（FR1.3）: `JsonLogFormatTest`（この段の FR4 の追加の前の状態で）・`TracingAndLoggingIT`・`ExternalExportIT`・`*SecretLeakIT`・`AuditNotInAppLogIT` を変更なしで流して通す。
- [ ] Step 7: 共通部品（標準出力のログの形）を実装する（FR4.1〜FR4.3）。`SingleLineMessageJsonProvider` を書き、`logback-spring.xml` の `<message>` を替える。
- [ ] Step 8: 共通部品（標準出力のログの形）のテストを書いて実行する（FR4.1、FR4.3、NFR4）。
  - `JsonLogFormatTest`: LF・CRLF・CR を含むメッセージが1行に出て、`message` の値に改行が無く ` ⏎ ` で区切られる。例外つきのログで、`exception` の項目には改行が残る。既存の `single()` の確かめ（物理的に1行）も通る。
  - ログを読むほかのテスト（`TracingAndLoggingIT`・`*SecretLeakIT`・`AuditNotInAppLogIT`・`AuditWriteFailureIT`）を流して通す。
  - コミットの提案 C2（FR1・FR4）。
- [ ] Step 9: サブモジュールの固定先を更新する（FR7.1）。`vendor/make-you-chic-ui` を `edb1f94` にし、`./gradlew vendorBuild` で作り直す。`vendor/make-you-chic-ui` の中のファイルは変えない（`./gradlew vendorUnchanged` で確かめる）。
- [ ] Step 10: 固定先の更新だけの状態で、画面のテストを流す（`frontend` の全体の `npm run test:coverage` と、DSL の2つのテストのファイル）。既定の名前が `閉じる` のままのため、既存のテスト（`DslAdminPage.test.tsx` 535 行を含む）が変更なしで通ることを確かめる（FR7.4）。通ったら、コミットの提案 C3（固定先の更新だけの専用のコミット。メッセージに `5258c8bb987b0fa6ffd0ad7c4eadc7d4006da52d` → `edb1f943c0e66293494fa974605f34fcd7e258d7` を記録する）。
- [ ] Step 11: 画面部品を実装する（FR7.2、FR7.3）。`messages.ts`・`DslConfirmDialog.tsx`・`DslAdminPage.tsx` を変える。
- [ ] Step 12: 画面部品のテストを書いて実行する（FR7.2〜FR7.4、NFR5）。
  - `DslConfirmDialog.test.tsx`: 日本語の表示で閉じるボタンの名前が `閉じる`、英語の表示で `Close`。ダイアログの説明（accessible description）に本文の文言が含まれる。既存の vitest-axe の検査が通る。
  - `DslAdminPage.test.tsx`: 英語の表示で、結果の知らせの閉じるボタンの名前が `Close` で、押すと知らせが消える。535 行の既存のテストはそのまま通る。既存の vitest-axe の検査が通る。
  - コミットの提案 C4（FR7 の画面）。
- [ ] Step 13: 環境とビルドの設定を変える（FR5.1〜FR5.3、FR6.1〜FR6.4）。`compose.yaml`・`docker/perf/compose.yaml`・`.env.example`・`.env.targetdb.example`・`.gitignore`・`docker/check-container-limits.sh`・`docker/targetdb/*/02-reader-account.sh`・`docker/targetdb/generate-large-schema.sh` を「直し方の要点」のとおりに変える。
- [ ] Step 14: 環境とビルドの設定を確かめる（FR5.2、FR6.1〜FR6.3）。
  - `./docker/check-container-limits.sh` を流し、すべて通る（変数なしで両方の compose が 2g、768m で 768m、JVM の確かめ、5 の環境変数の分け方）。直す前の `compose.yaml` で 5 の節が失敗することも確かめる（`git stash` で一時的に戻す）。
  - R-03 の基準 1・2・4 を実行する（3 はコミットの後、5 は Step 18、6 は参考の記録）。
  - 案 A の入口の包み方が動くことを確かめる: プロジェクトのルートに `.env.targetdb` が無いことを確かめたうえで、乱数（表示しない）を入れた一時の `.env.targetdb` を権限 600 で作り、別のプロジェクト名（例: `-p mastersmith-fr6check`）で見本の対象DB を1種類ずつ起動し、初期化のログに誤りが無く読み取りのアカウントが作られたことを確かめ、`down -v` で消す。3種類が終わったら一時の `.env.targetdb` を消す。`.env.targetdb` が既にあるときは触らずに依頼者に確かめる。配備したアプリと見本の対象DB（プロジェクト `mastersmith`）には触れない。
  - コミットの提案 C5（FR5・FR6）。
- [ ] Step 15: 負荷の試験の台本と手順を変える（FR3.1、FR3.2、FR8.1）。`perf/k6/scenarios.js`・`perf/README.md` を変える。
- [ ] Step 16: 台本を確かめる（FR3.1、FR3.2）。台本を読んで、R-01 の「台本を読んで」の基準を満たすことを確かめる。`docker run --rm -e SCENARIO=dslMixed -v "$PWD/perf/k6:/scripts:ro" grafana/k6:2.3.0 inspect /scripts/scenarios.js` で読み込めることと、`options.scenarios` が今と同じ（`dslHeavy` 1・`logins` `VUS`）ことを確かめる。ほかの場面（`loginSuccess`・`refresh` など）も `inspect` で読み込めることを確かめる。流す確かめ（R-01 の「流したときの記録」と FR8.2）は Build and Test の段で行う。
- [ ] Step 17: 文書を直す（FR1.2、FR4.1、FR5.2、FR6.3、FR8.1）。README の該当の節と `perf/README.md` を直す（Step 13・15 で直した分を除く）。README の移す手順は、値を表示しないコマンドだけで書く。
  - コミットの提案 C6（FR3・FR8 と文書）。
- [ ] Step 18: 統合の前の関門を通す（NFR2、NFR3、NFR6）。colima が動いていることを確かめ、`./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を実行し、すべて通ることを確かめる。対象DB のテストが `SKIPPED` になっていないこと（コンテナの実行環境が無い警告が出ていないこと）、カバレッジの下限（行 80%・分岐 70%、全体の合計と新しいパッケージごと。この Intent では新しいパッケージは作らない）、ライセンスヘッダー（新しいファイルを含む）、Gitleaks、OSV-Scanner（サブモジュールの lockfile を含む）、SpotBugs が通ることを確かめる。テストの件数とカバレッジは実測の数字を記録する。あわせて、`git status` で `.env`・`.env.targetdb` がコミットの対象に入っていないことと、`vendor/make-you-chic-ui` の変更が gitlink だけであることを確かめる。R-03 の基準 3 をコミットの後に確かめる。
- [ ] Step 19: 成果物を書く（段の定義の Step 5。PART 2 の後）。
  - `code-summary.md`: 変更したファイル、Step 1・2・4（再現）・14・16・18 の実測の結果、判断（FR2 の直し方、FR1 の伏せ方、FR4 の記号、FR6 の案 A）、計画との差、要件の前提との差（FR8.1 の手順が perf/README に無かったこと、project.md の学びが要らなくなったこと、`perf/README.md` 142 行と `scenarios.js` 30 行の 1g）。
  - `source-manifest.json`: この段で作った・変えたアプリのパスの一覧（`vendor/make-you-chic-ui` の gitlink を含む）。
  - `traceability.json`: 設計の段が無いため、FR・NFR の ID を直接持ち、`OK` の対象は実在するファイル1つにする。FR8.2 と FR3.1 の「流したときの記録」は `Deferred`（Build and Test）とする。
- [ ] Step 20: 統合する。依頼者の承認を得て、作業ブランチを `develop` へ fast-forward で取り込む（squash しない）。AI は `git push` をしない（team.md の Way of Working）。

## コミットの分け方

コミットは、ファイルの変更のまとまりごとに提案し、実行の前に必ず依頼者の承認を得る（project.md の Change Control）。メッセージは日本語。各コミットの前に、そのまとまりのテスト（`unit-test-instructions.md` のコマンド）と pre-commit のフック（Gitleaks・フォーマット）を通す。統合の前の関門（`./gradlew verify`）は Step 18 で作業ブランチの全体に対して1回通す。

| 提案 | 中身 | 理由 |
|---|---|---|
| C1 | FR2 の直し（`LoginService.java`）と、再現のテスト・失敗の場合のテスト（`LoginServiceTest.java`・`LoginConcurrencyIT.java`） | 不具合を再現するテストを直しと同じコミットに含める（project.md の Mandated） |
| C2 | FR1（`ObservabilityConfig.java`・`SanitizingLogRecordExporter.java` とテスト2つ）と FR4（`SingleLineMessageJsonProvider.java`・`logback-spring.xml`・`JsonLogFormatTest.java`） | どちらもログの出力の変更。分けたいときは FR1 と FR4 の2つにする |
| C3 | サブモジュールの固定先の更新だけ（`vendor/make-you-chic-ui` の gitlink） | 承認を得た専用のコミットで、前後のハッシュを記録する（project.md の Mandated） |
| C4 | FR7 の画面（`messages.ts`・`DslConfirmDialog.tsx`・`DslAdminPage.tsx` とテスト2つ） | C3 の後に置く |
| C5 | FR5・FR6（compose の2つ、`.env.example`・`.env.targetdb.example`・`.gitignore`・確かめのスクリプト・`docker/targetdb/` の台本と、README のその部分） | コンテナの設定のまとまり |
| C6 | FR3・FR8（`perf/k6/scenarios.js`・`perf/README.md`）と、README の FR1・FR4 の説明 | 負荷の試験と文書 |

- 統合は fast-forward とし、squash しない。squash すると C3 が他の変更と1つのコミットにまとまり、「サブモジュールの固定先の更新は専用のコミットで行う」を守れないため。
- ワークフローの記録（`aidlc/` の下）のコミットは、これまでどおり段の承認のときに別に提案する。

## 要件との対応

| 要件 | 計画の Step | 確かめ方 |
|---|---|---|
| FR1.1 キーと値を属性として送る | 5、6 | `OtlpLogExportIT` |
| FR1.2 個人に関する値を伏せる | 5、6 | `SanitizingLogRecordExporterTest`・`OtlpLogExportIT`（実際の監査の失敗の ERROR と初期管理者の INFO） |
| FR1.3 標準出力は変えない | 6 | 既存の `JsonLogFormatTest`・`TracingAndLoggingIT`・`*SecretLeakIT` が変更なしで通る |
| FR1.4 秘密の値が送られない | 6 | `OtlpLogExportIT`（パスワード・アクセストークン・リフレッシュトークン・署名鍵） |
| FR1.5 警報とダッシュボードは変えない | — | `docker/monitoring/` に差分が無いこと（Step 18 の `git status`） |
| FR2.1 同時の初めてのログインで 500 にしない | 3、4 | `LoginConcurrencyIT`（内部の失敗 0 件・行 1 つ・失敗の数）、`LoginServiceTest` |
| FR2.2 再現のテストを同じコミットに、失敗の場合のテスト | 4 | 直す前に失敗することの記録、ロックと失敗のテスト、コミット C1 |
| FR2.3 直し方と既存の決まりの維持 | 3、4 | 既存の `Audit*IT`・`LoginApiIT`・`LoginAttemptStateRepositoryIT` が通る、監査の記録が二重にならない |
| FR3.1 VU の間で利用者が重ならない | 15、16 | 台本を読んだ確かめ（R-01）と `k6 inspect`。流したときの記録は Build and Test |
| FR3.2 単独の場面は変えない | 15、16 | 台本の差分が `loginLoop` と `setup` と説明だけ、`k6 inspect` |
| FR4.1 1行1件（改行の置き換え） | 2、7、8 | `JsonLogFormatTest`。起動のログでの確かめは Build and Test |
| FR4.2 ロガーの水準は変えない | 7 | `application.yaml` に差分が無い |
| FR4.3 スタックトレースは変えない | 8 | `JsonLogFormatTest` |
| FR5.1 既定 2g | 13、14 | `docker/check-container-limits.sh` |
| FR5.2 5か所を合わせる | 13、14、17 | `grep` と `docker/check-container-limits.sh` |
| FR5.3 変える口を残す | 14 | `docker/check-container-limits.sh`（768m） |
| FR6.1 `.env.targetdb` に分ける | 13、14 | `.env.targetdb.example`、R-03 の基準 4、一時の `.env.targetdb` での DB の初期化 |
| FR6.2 `app` は `.env` だけ | 13、14 | `docker/check-container-limits.sh` の 5。値が入った状態は Deployment Execution |
| FR6.3 移す手順と Git・Gitleaks | 13、14、17、18 | R-03 の基準 1〜6、README の差分 |
| FR6.4 `MASTERSMITH_TARGET_DB_*` は `.env` のまま | 13 | `compose.yaml`・`.env.example` の差分 |
| FR7.1 固定先の更新 | 2、9、10 | 専用のコミット C3、前後のハッシュ、既存のテストが通る |
| FR7.2 閉じるボタンの名前 | 11、12 | `DslConfirmDialog.test.tsx`・`DslAdminPage.test.tsx` |
| FR7.3 本文を説明として結ぶ | 12 | `DslConfirmDialog.test.tsx`（accessible description）と vitest-axe |
| FR7.4 既存のテストが通る | 10、12 | `DslAdminPage.test.tsx` 535 行 |
| FR8.1 手順から外す | 15、17 | `perf/README.md` の差分（要件の前提との差を記録） |
| FR8.2 負荷の試験で確かめる | — | Build and Test（使い捨ての環境で k6） |
| NFR1 秘密情報と個人に関する値 | 6、14、17、18 | `OtlpLogExportIT`、`.env`・`.env.targetdb` を開かない・表示しない、Gitleaks |
| NFR2 品質の下限の維持 | 1、18 | JaCoCo と Vitest の下限、除外を増やさない |
| NFR3 統合前の関門 | 18 | `./gradlew verify`（対象DB のテストを飛ばさない） |
| NFR4 1行1件のログ | 8 | `JsonLogFormatTest` |
| NFR5 アクセシビリティ | 12 | vitest-axe |
| NFR6 既存の動作の維持 | 4、6、8、10、12、18 | 既存の結合テスト・画面のテスト・`./gradlew verify`。E2E（`./gradlew e2eTest`）は `verify` の外のため、Step 18 の後に1回流して記録する |

## 懸念と承認の場で確かめたいこと

1. FR6 の案 A（見本の対象DB の起動の入口を包む）: 公式のイメージの入口を上書きするため、イメージの版を上げるときに入口と `CMD` の見直しが要る。要件の変数の名前を保つための選択で、代わりに案 B（`.env.targetdb` に各イメージの変数を直接書く）もある。
2. 統合のしかた: team.md の決まりは squash だが、サブモジュールの専用のコミットを残すため fast-forward にする。前の Intent と同じく `develop` の上で直接コミットする形でもよい（その場合は各コミットの前に `./gradlew verify` を通すことになり、時間がかかる）。
3. FR4 の置き換えの記号 ` ⏎ `: ASCII に限りたいときは ` | ` などに替える。
4. FR1 の伏せる範囲: 4つのキーの値だけで、ログの本文（メッセージ）と、例外の属性（`exception.message`・`exception.stacktrace`）は変えない。メソッドの呼び出しの追跡（`TraceAspect`、既定は出ない）を TRACE にすると、引数の文字列がメッセージとして送られうる。トレースと同じく例外のメッセージを外すかは、この Intent の範囲の外として記録する。
5. `perf/README.md` 142 行と `perf/k6/scenarios.js` 30 行の「コンテナの上限 1g で流す」は、前の Intent の決定（条件を 2g）と食い違う。FR5 の範囲の外のため変えない予定。直すなら C6 に含める。
6. FR8.1 の前提の差と、project.md の学びが要らなくなった点は、code-summary.md に記録する。memory のファイルは直接変えない。

## Testing Contract

```json
{
  "version": 1,
  "methodology": "test-after",
  "source": "team",
  "ordering": "テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。",
  "scope": "bugfix",
  "test_strategy": "minimal",
  "project_type": "brownfield",
  "applicable_notes": [
    {
      "layer": "org",
      "text": "We treat tests as a first-class deliverable in every Bolt. The specific\nmethodology (TDD, BDD, ATDD, or classic test-after) is affirmed at\npractices-discovery and recorded in `team.md` under this heading with explicit\n`Methodology` and `Ordering` fields; Code Generation resolves those fields\nindependently from coverage, tooling, and scope notes.\n\nWhen no posture has been affirmed, our default per scope is:\n- **Methodology**: test-after\n- **Ordering**: implement each applicable testable layer, then write and run\n  that layer's tests.\n- `mvp`, `enterprise`, `feature`, `infra`, `classic` add an 80% line-coverage\n  floor and CI execution before merge.\n- `bugfix`, `security-patch` add a targeted regression for the specific\n  bug/vulnerability and require the existing suite to remain green.\n- `express` uses the Minimal strategy: requirement-driven unit tests (one per\n  requirement, with a happy-path floor per component); existing tests remain\n  green.\n- `poc`, `refactor`, `workshop` add no extra new-test floor and require the\n  existing suite to remain green.\n\nThe active `Test Strategy` still applies in every scope and determines test\nvolume/types. Scope floors are additive; they never reduce or replace the\nselected strategy.\n\nBuild and Test verifies defined coverage floors and affirmed quality targets;\nthey may not be weakened to make a step pass.\n\nAffirm a stricter posture in `team.md` if the team commits to one."
    },
    {
      "layer": "team",
      "text": "- **Methodology**: test-after\n- **Ordering**: テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。\n- テストは各 Bolt の成果物の一部であり、テストのない機能は完成とみなさない。テスト量はワークフローの Test Strategy に従い、以下の下限はそれに追加される。\n- カバレッジの下限は、すべての Intent に共通で **行カバレッジ 80% 以上、分岐カバレッジ 70% 以上** とする。バックエンド・フロントエンドの両方に適用し、下回ったらビルドを失敗させる。道具はバックエンドが JaCoCo、フロントエンドが `@vitest/coverage-v8`（`thresholds` 設定）。\n- バックエンドでは、全体の合計に加えて、すべてのパッケージごとにも同じ下限（行 80%・分岐 70%）を当てる。ただし、既存のパッケージに単独で下限を下回るものがあれば、パッケージごとの下限は新しく作るパッケージだけに当てる（既存のパッケージは全体の合計で判定する）。どちらになるかは、パッケージごとの下限を入れる Bolt で既存のパッケージのカバレッジを実測して決め、結果を記録する。\n- カバレッジの計測から外すのは、アプリの起動クラス、設定値だけのクラス、自動生成コード、`vendor/` 配下に限る。除外を後から増やして実質的に下限を下げることはしない。パッケージごとの下限を満たすために除外を増やすこともしない。\n- DB を使うテストは、本番と同じ種類の DB をコンテナで起動して使う（Testcontainers）。そのため、ローカルと CI にコンテナの実行環境があることを前提条件として文書化する。テストごとにデータを用意して巻き戻し、実行順に依存させない。表を作る・消す操作（DDL）がその場で確定して巻き戻せない DB（MySQL・MariaDB など）で表を作るテストは、巻き戻す代わりに、テスト（またはテストのクラス）ごとに名前の重ならないスキーマ（MySQL・MariaDB ではデータベース）を作り、終わったら消す。\n- 対象DB（MySQL・MariaDB・PostgreSQL）の結合テストをどこで実行するかは、Build and Test で `./gradlew verify` の時間と colima の VM のメモリを実測してから決める。決まるまでは、3種類すべてを `./gradlew verify` の中で毎回実行する（CI も同じ）。コンテナの実行環境が無いときの扱いは Way of Working のとおり。\n- 画面からの一連の操作を確かめるテスト（E2E）は、代表的な流れ1〜2本に絞って入れる（まずは「ログイン → 管理画面に入れるか → ログアウト」）。道具は設計のステージで決める。\n- 入力と出力の性質をランダムな入力で確かめるテスト（性質ベースのテスト）を、純粋な関数（ロック判定の回数計算、有効期限の判定、入力の検証など）に一部適用する。道具は Java が jqwik、フロントエンドが fast-check。失敗時の乱数の種を記録して再現できるようにする。\n- テストの説明文（テスト名、`describe` / `it`、`@DisplayName`）は英語で書く。テストデータは日本語でよい。\n- フロントエンドのテストは対象と同じ場所に `*.test.ts` / `*.test.tsx` として置き、Vitest ＋ Testing Library（jsdom）＋ user-event ＋ vitest-axe を使う。画面部品ごとにアクセシビリティ検査を1件入れる。\n- Java のテストは `src/test/java` に、対象と同じパッケージ構成で置く。単体テストは `XxxTest`、Spring や DB を起動する結合テストは `XxxIT` とし、分けて実行できるようにする。\n- 時刻に依存する処理（有効期限、ロックの解除など）は注入可能な時計（`Clock` 等）から現在時刻を取得し、テストで `sleep` や実時刻に依存しない。不安定なテストは放置せず、原因を直すまで統合しない。\n- 認証・認可・監査に関わる機能では、次のテストを必ず書く。★印の項目は要件（しきい値や動作）が未確定のため、要件定義で決めてからテストを書く。\n  - アカウントロック: 失敗回数のしきい値の境界（しきい値−1回ではロックされない／しきい値ちょうどでロックされる）、ロック中は正しいパスワードでも拒否、ログイン成功時の失敗回数の扱い。★しきい値、回数を数える期間、ロックの解除方法\n  - ログイン: 存在しないユーザーとパスワード誤りで、応答からユーザーIDの存在を推測できないこと\n  - トークン: 有効期限の境界（直前は有効／直後は無効）、署名の改ざん、署名方式の指定を悪用した改ざん（`alg: none` 等）、ログアウト後のリフレッシュトークンの拒否、ログアウト後もアクセストークンが有効期限まで使えること（決定済みの仕様として明示する）。★各トークンの有効期限の値、リフレッシュトークンを使うたびに作り直すか\n  - 初期管理者の自動作成: 2回目以降の起動で重複作成しない、設定が無い／不正なときの動作、パスワードがログに出ない。★設定が無いときに起動を止めるか\n  - 認可: 未認証（401）、管理者フラグなし（403）、管理者（200）をサーバー側のテストで確かめる（画面で管理メニューを隠すことはサーバー側の検査の代わりにしない）\n  - 監査ログ: 対象イベントごとに必須項目が記録されること、存在しないユーザーIDでのログイン失敗も記録されること。★監査ログの書き込みに失敗したときに操作を失敗させるか\n  - 秘密情報の漏えい: ログ・監査ログの出力にパスワード・トークンの値が含まれないこと\n  - 構造化ログ・分散トレース: 決めた形式で出る、トレースIDがログに含まれる、外部エクスポートが既定で無効であること\n  - 画面: ログイン画面のアクセシビリティ検査、ロック時のメッセージ表示、ログアウトでトークンが破棄されること\n- 利用者が投入する DSL（YAML）を読み込む機能では、Code Style の「信頼できない入力」の決まりを確かめる次のテストを必ず書く（認証・認可・監査と同じ扱い）。上限の具体的な数値は設計の段で決め、決めた値の境界で確かめる。\n  - 大きさ: 上限ちょうどは受け付け、上限を超えると拒否される\n  - 入れ子の深さ: 上限ちょうどは受け付け、上限を超えると拒否される\n  - 別名（アンカー）: 展開の数が上限を超えると拒否され、別名の展開の爆発で処理が止まらない\n  - タグ: 任意の型を作るタグ（`!!` など）を含む DSL は拒否され、型が作られない\n  - 重複キー: 同じキーが重なる DSL はエラーになり、後の値で黙って上書きされない\n  - JSON Schema の `$ref`: 外部の URL を取りに行かない\n  - 拒否の応答: Problem Details の形で返り、YAML・JSON Schema の部品の例外のメッセージを含まない\n\n- 内部DB（組み込みの H2）を使うテストは、コンテナではなく本番と同じ組み込みの H2 で行う。Testcontainers は、コンテナで動かす対象DB（後続 Intent D・E で扱う業務DB）のテストに使う。Walking Skeleton の「DB を使うテスト1件以上（本番と同じ種類の DB をコンテナで起動する）」も、内部DBについてはこの読み方とする。 (learned 2026-09-22)"
    },
    {
      "layer": "project",
      "text": "- テストの件数やカバレッジを報告するときは、`./gradlew verify` がテストのタスクを UP-TO-DATE で飛ばすことがあるため、`:backend:cleanTest :backend:cleanIntegrationTest` を付けて実行し直し、実測の数字だけを報告する。 (learned 2026-09-23) \n- 負荷の環境や配備先が決まらないと測れない目標（応答時間のパーセンタイル、運用の指標、ファイルの権限など）は、Build and Test で `Unverified` とし、持ち主の段（performance-validation・observability-setup・deployment-execution）を明記して引き継ぐ。目標を緩めて「満たした」ことにはしない。 (learned 2026-09-23) \n- 負荷の試験は、配備した環境とは別の使い捨ての環境（仮の署名鍵・仮の利用者、終わったら消す）で行い、本物のデータと監査ログを汚さない。手順は perf/README.md。 (learned 2026-09-23) \n- 負荷の試験で、アプリが止まる・極端に遅いなどの結果が出たときは、環境を起動し直して再現させ、原因をログと状態（OOMKilled など）で確かめてから記録する。 (learned 2026-09-23) \n- 同時の重なりを確実に作るため、本番のコードを変えずに、監査の書き込みの時間を測る LongSupplier（AuditEventListener で2本目を借りる直前に呼ばれる）をテストで差し替えて待ち合わせる方式にした。既存の LoginConcurrencyIT は 8 スレッドでプールの 10 に届かず、前回の失敗のログインで尽きなかった理由の1つと見られる。 (learned 2026-09-23) \n- Intent の流れに Performance Validation の段が無く、負荷の環境（使い捨ての環境）を手元で用意できるときは、k6 の試験と NMT の測定の持ち主を Build and Test とし、Unverified で引き継がずにその段で実行する。 (learned 2026-09-23) \n- 修正の前の設定（例: 上限 1g）も修正の後の環境（例: CPU 4 の VM）で流し（pre1g）、要件の前提（VM を上げても F3 が起きる）を実測で裏付ける。 (learned 2026-09-23) \n- パッケージごとのカバレッジの下限と SpotBugs の SQL_ の関門を U1 の計画に入れた。team.md の決まりだが今のビルドに無く、この Intent で最初に作る単位のため。U1 の設計の文書には無い作業。 (learned 2026-09-24) \n- U4 で既存の AuditSecretLeakIT の列の一覧に V6 の4列を足し、テストの JVM のヒープを 1g にした。前者は承認済みの V6 と必ず食い違うため、後者は構造の検査がクラスを持ち続け U3 の 10MB 超えのテストでヒープが尽きたため。どちらも計画に無い変更で、依頼者に確かめる。 (learned 2026-09-24) \n- 応答しない対象DB の TIMEOUT の確かめに、テストの中で開いた ServerSocket（受け付けて何も返さない）を使う。外の端末に頼らず確実に再現できる代わりに、本物の DB の遅延ではない。 (learned 2026-09-24) \n- パッケージごとのカバレッジの下限は新しいパッケージだけに当てた。実測で audit.service（行 77.2%）・common.health（行 79.2%）・auth.repository（分岐 50.0%）が単独で下回ったため（team.md の決まりどおり）。既存の 22 パッケージを一覧で外し、新しいパッケージは自動で対象になる。 (learned 2026-09-24) \n- 負荷の試験で接続プールが尽きたかを確かめるときは、使い捨てのアプリにだけ MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics を渡して /actuator/metrics の hikaricp の値を読み、数秒ごとの使用中の数ではなく、待ちの時間切れの累計と借りるまでの待ちの最大で判断する（配備したアプリの公開の範囲は変えない）。 (learned 2026-09-24) \n- k6 などの長い試験は caffeinate -i を付けて流し、PC の自動のスリープで要求が止まって結果が崩れるのを防ぐ（バッテリー駆動のまま 414 秒スリープし、要求が 6分52秒止まった）。内部DB に SQL で直接入れた試験用の利用者はロックの状態の行が無いため、同時のログインを流す前に1人ずつログインさせて行を作る。 (learned 2026-09-24) \n- 軽い API の性能は、投入を重ねて内部DB のファイルが膨らんだ状態（悪い側の条件）のまま測る。メモリの最大（memory.peak）を比べる試験の前は、アプリのコンテナを作り直して前の最大の値を消す。 (learned 2026-09-24) \n- Performance Validation の段が無いため、2件目の直しの負荷の試験での確かめ（Q5: A）の持ち主を Build and Test とした（project.md の学びどおり）。 (learned 2026-09-24)"
    }
  ],
  "obligations": {
    "strategy": "minimal",
    "strategy_volume": [
      "One verifiable test per requirement at the narrowest effective level.",
      "At least one happy-path unit test per component.",
      "Unit tests are the default; a bugfix/security scope floor may require an integration or E2E regression when that is the narrowest level that reproduces the defect."
    ],
    "scope_floor": [
      "Include a targeted regression for the bug or vulnerability.",
      "Keep the existing test suite green."
    ],
    "combination_rule": "Apply every selected-strategy obligation and every scope-floor obligation; neither replaces the other, and a targeted scope regression may add the narrowest necessary test type beyond the strategy default."
  },
  "plan_profile": {
    "methodology": "test-after",
    "runner_step": "Verify the existing test runner/configuration and record the exact unit-scoped command.",
    "runner_ready_before_first_test": true,
    "testable_layers": [
      "Data model / database behavior",
      "Repository / data access",
      "Business logic",
      "API / endpoint",
      "Frontend behavior"
    ],
    "steps": [
      "Project structure and production configuration skeleton.",
      "Verify the existing test runner/configuration and record the exact unit-scoped command.",
      "Data model / database behavior - implement.",
      "Data model / database behavior - write and run its tests after implementation.",
      "Repository / data access - implement.",
      "Repository / data access - write and run its tests after implementation.",
      "Business logic - implement.",
      "Business logic - write and run its tests after implementation.",
      "API / endpoint - implement.",
      "API / endpoint - write and run its tests after implementation.",
      "Frontend behavior - implement.",
      "Frontend behavior - write and run its tests after implementation.",
      "Environment/build configuration.",
      "Documentation and traceability."
    ]
  },
  "input_sha256": "sha256:17461f5a68937fd2726dc9ba291e0ef114c997ced40b44152c5978dd98d2a6dc",
  "contract_sha256": "sha256:950fe5aec051c2cf1b25469e09d1ce8697a8edcef7b53fb9648497a3c7d639b2"
}
```
