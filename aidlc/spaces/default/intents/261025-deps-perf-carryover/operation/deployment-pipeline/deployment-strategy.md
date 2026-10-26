# 配備の仕方（deployment-strategy）

Intent 261025-deps-perf-carryover の配備の仕方です。前の手順（`aidlc/spaces/default/intents/261016-business-data-views/operation/deployment-pipeline/deployment-strategy.md`）からの差を中心に書きます。流れの全体と順の番号は `cd-config.md` の 3節です。

## 1. 方式

- 入れ替え（recreate）。開発者の PC 上の1つのコンテナで、アプリを止めて新しいイメージで起動し直す。無停止の入れ替え・段階的な切り替え（blue/green・canary）はしない（配備先が決まるまでの扱い。切り替えの割合と中止の条件の代わりに、4節の中止の条件を置く）。
- 内部DB の移行は無い（V13 のまま）。`.env` の変更も無い。
- 同じ停止の中で、見本の PG の番号（E2E の上乗せ）を閉じる（Q1: A）。アプリの作り直しは入れ替えの1回だけ。
- アプリが止まるのは、停止から healthy まで（約 1〜2 分の見込み）と、配備の後の複写（約 10 秒）。

## 2. 入れ替えの手順（`cd-config.md` の 3節の順 1〜8）

停止の間の順は「止める → 見本の PG の番号を閉じる → イメージ → 入れ替え → health」です。WAR は停止の前に作り直します（停止を短くするため）。

```bash
# 順 1: 関門
git status --short -- . ':!aidlc'                          # 空であること
git diff --stat 9bf6b70 HEAD -- . ':!aidlc'                # 空であること

# 順 2: WAR の作り直しと確かめ（停止の前）
./gradlew :backend:bootWar --rerun-tasks
shasum -a 256 backend/build/libs/mastersmith.war           # 記録する
unzip -p backend/build/libs/mastersmith.war WEB-INF/classes/application.yaml | grep -n 'MASTERSMITH_TARGET_DB_POOL_MAXIMUM_SIZE'   # :10} であること
unzip -l backend/build/libs/mastersmith.war | grep -c 'static/assets/'   # 画面のビルドがあること（件数だけ）
unzip -l backend/build/libs/mastersmith.war | grep -c 'TableAccessResponse'   # 0 であること（削除した道の応答の型）
unzip -l backend/build/libs/mastersmith.war | grep -E 'WEB-INF/lib/spring-boot-4\.[0-9.]+\.jar'   # spring-boot-4.1.2.jar であること

# 順 3: 戻し先のタグ（今動いている版）
docker tag mastersmith:local mastersmith:pre-deps-perf-carryover
docker image inspect mastersmith:pre-deps-perf-carryover --format '{{.Id}}'   # sha256:4cec01e79026… であること

# 順 4: 救済の見込みを依頼者に尋ねる（前の配備の後に初期管理者のパスワードを画面で変えた・止めた・印を外したか）

# 順 5: 依頼者の承認の後、T0（UTC）を控え、アプリを止める（内部DB の複写は取らない。cd-config.md の 4節）
date -u +%Y-%m-%dT%H:%M:%SZ
docker compose stop app

# 順 6: 見本の PG の番号を閉じる（cd-config.md の 3.2 のコマンド）

# 順 7: 順 2 の WAR からイメージを作り直し、app だけを入れ替える
docker compose --profile targetdb-postgres up -d --build app

# 順 8: healthy を待ち、健全性を確かめる
docker compose --profile targetdb-postgres ps app          # healthy であること
docker compose --profile targetdb-postgres ps              # targetdb-postgres が Up、PORTS が 5432/tcp だけ、mailpit が healthy
```

- 順 7 の `--build` は、順 2 の WAR を写してイメージ `mastersmith:local` を作り直す（WAR は作り直さない。`project.md` の学び）。順 3 のタグを付ける前に順 7 を行わない。
- 順 8 の後に、開いているタブを読み込み直す。

## 3. 配備の後の確かめ（スモークテスト、Q3: A、小さな範囲）

健全性と、下のすべてが通るまで、配備の完了としません（`team.md` の Deployment）。ロールは作らず、初期管理者の今の割り当てを使います。

- パスワードの要る操作（ログイン）と画面の操作は依頼者が行い、AI はアプリのログ・監査の記録・件数で裏付けます（`project.md` の Corrections）。
- 起動のログは、値を表示せず、レベル・ロガー・メッセージ・キーの名前と件数だけを見ます。
- 監査に残る操作（ログイン、ログアウト）は、行う前に依頼者に伝え、下の 3.2 の期待の件数をその場で合わせます。依頼者が予定に無い操作をしたときは、その場で何をしたかを伺い、期待の件数を直してから数えます（`project.md` の学び）。
- パスワードの変更は、スモークテストで行いません。初期管理者のパスワードを変えると、次の起動の救済で `.env` の値に戻るため（前の手順）。パスワード変更の成功の知らせ（C5）は、画面のテストと E2E で確かめ済みです。
- 業務データの一覧・詳細は読み取りだけ（監査に残らない）。作成・更新・削除は今回の範囲に入れません（書き込みの経路は変えていません。Spring Boot の更新は E2E 256 件の中の書き込みの流れで確かめ済み）。

### 3.1 確かめの一覧

| # | 確かめ | 行う人 | 通る条件 |
|---|---|---|---|
| S1 | 健全性と起動のログ | AI | `app` が healthy、`/actuator/health` が 200・UP。ERROR 0 件。WARN はロガーと件数だけを見て、前の版から出ているもの（Spring の Bean の案内、Flyway の H2 の版の案内）と、救済の見込みどおりの救済の WARN のほかに無い。書き込みの設定の WARN（項目の名前 `MASTERSMITH_TARGET_DB_WRITE_` を含む行）が 0 件。ログの中の `.env` の初期管理者のメールアドレスの値の件数が 0（値は表示しない） |
| S2 | 移行と JVM の設定 | AI | Flyway の `Migrating schema` が 0 件で、現在の版が 13 のまま（検証の失敗なし）。`ExitOnOutOfMemoryError` と `MaxRAMPercentage=50.0` が PID 1 のコマンド行に1件ずつ。初期管理者の INFO・WARN にキー `maskedEmail` があり、キー `email` が無い。WAR の中の `spring-boot-4.1.2.jar`（順 2 で確かめる） |
| S3 | 読み取りのプールの既定 10 | AI | 順 2 の WAR の `application.yaml` の既定が `:10}`、`.env` に `MASTERSMITH_TARGET_DB_POOL_MAXIMUM_SIZE` の項目が無い（名前の件数 0）。配備したアプリの指標の公開の範囲は変えないため、`hikaricp.connections.max` は読まない（`project.md` の決まり。負荷の試験で 10 を確認済み） |
| S4 | 見本の PG | AI | `cd-config.md` の 3.2 の確かめが `1\|t\|t\|t\|f\|f\|f`、PORTS が `5432/tcp` だけ。`sales.customers` の行の数を控える（値は表示しない） |
| S5 | ★ログイン | 依頼者 | 初期管理者でログインし（ログイン中ならタブを読み込み直し）、ホームが開ける |
| S6 | ★業務データの一覧と詳細 | 依頼者 | 業務のメニューの項目から `customers` の一覧が開き、見出しは表示名、検索・並べ替え・1ページの件数の選択が働く。行の「詳細」で詳細が開き、戻ると一覧の状態が保たれる（読み取りのプール 10 で動く） |
| S7 | ★表示の設定の保存の知らせ | 依頼者 | 「表示の設定」で値を変えて保存すると、成功の知らせが出る。保存した値を元に戻して保存し、元に戻す（監査には残らない） |
| S8 | 削除した道 | AI | 順 2 の WAR に `TableAccessResponse` が無い（件数 0）。S5〜S7 の後のアプリのログに ERROR が増えていない（件数） |
| S9 | 監査の記録 | AI | README の「監査ログの確かめ方」のとおり、アプリを止めて内部DB を `~/.mastersmith-backup/mastersmith-data-<日時>-after-deps-perf-carryover.tgz`（権限 600）へ複写し、すぐ起動し直す（約 10 秒。依頼者に伝えてから）。複写を読み取りで開き、T0 以降の `audit_events` を種類と結果ごとに数え、3.2 の表と比べる。値は表示しない。この複写は配備の後のバックアップを兼ねる |

S1・S2 のコマンド（値を表示しない形）:

```bash
# S1: レベルごとの件数と、WARN・ERROR のロガーとメッセージと件数だけ
docker compose logs --no-log-prefix app | jq -R -r 'fromjson? | .level' | sort | uniq -c
docker compose logs --no-log-prefix app | jq -R -r 'fromjson? | select(.level=="WARN" or .level=="ERROR") | .level + " " + .logger + " " + .message' | sort | uniq -c
docker compose logs --no-log-prefix app | grep -c 'MASTERSMITH_TARGET_DB_WRITE_'        # 0 であること

# S2: Flyway の移行の件数、初期管理者の行のキーの有無（値は表示しない）
docker compose logs --no-log-prefix app | jq -R -r 'fromjson? | select(.logger | startswith("org.flywaydb")) | .message' | grep -c 'Migrating schema'   # 0 であること
docker compose logs --no-log-prefix app | jq -R -r 'fromjson? | select(.logger | test("InitialAdmin")) | .level + " " + .message + " " + (keys_unsorted | join(","))' | sort | uniq -c
docker exec mastersmith-app-1 cat /proc/1/cmdline | tr '\0' '\n' | grep -cE 'ExitOnOutOfMemoryError|MaxRAMPercentage=50.0'   # 2 であること
```

### 3.2 監査の期待の件数（T0 以降、`AuditEventType` で確かめた種類）

`backend/src/main/java/cherry/mastersmith/audit/domain/AuditEventType.java` の種類で数えます。数えるのは種類（`event_type`）と結果（`result`）だけで、利用者・対象・主キーの値は表示しません。期待の件数は操作の前に依頼者と合わせ、依頼者が予定と違う操作をしたときはその場で直します。業務データの読み取り（一覧・詳細・定義）、403 の拒否、表示の設定の保存は監査に残りません。

| 種類 | 期待の件数（結果は `SUCCESS`） | 出どころ |
|---|---|---|
| `LOGIN_SUCCEEDED` | 依頼者がログインした回数（ログイン中のまま読み込み直したときは数えない） | S5 |
| `LOGGED_OUT` | 依頼者がログアウトした回数（予定では 0） | 予定に無い操作 |
| `INITIAL_ADMIN_RESCUED` | 救済の見込みどおり（働かない見込みなら 0）。数えるのは、T0 以降（入れ替えの起動）の分 | 順 7 |
| `INITIAL_ADMIN_CREATED` | 0 | 順 7 |
| `BUSINESS_ROW_*`・`ROLE_*`・`DSL_*`・`GROUP_*`・`USER_*`・`INVITATION_*`・`ACCESS_DENIED`・`LOGIN_FAILED`・`WORK_ROLE_SWITCHED` | 0 | 予定に無い操作 |
| 上のどの種類でも、結果が `FAILURE` か `UNKNOWN` の行 | 0 | — |

## 4. 合否の基準と中止の条件

| 条件 | 扱い |
|---|---|
| 順 6 の見本の PG の確かめが期待と違う | 入れ替え（順 7）に進まず依頼者に諮る。前の版のまま `docker compose --profile targetdb-postgres start app` で起動し直せる |
| healthy にならない | `rollback-runbook.md`。H2 の版も移行も変わらないため、内部DB の異常は見込んでいない |
| S1 で ERROR が出る・出続ける、ログに秘密・メールアドレスそのもの・業務データの値が出る、書き込みの設定の WARN が出る | 原因が設定だけと分かれば直して入れ替え直す。分からなければ `rollback-runbook.md` の 2節で戻すかを依頼者に諮る |
| S5〜S7 のどれかが通らず、その場で原因が操作の誤りや設定だけと分からない | 同上 |
| S9 で期待と違う件数が出て、依頼者の操作で説明できない | 合否を決める前に依頼者に諮る |

## 5. 監視

- 手元の監視（otel-lgtm）は常に動かしていない。配備の確かめは S1〜S9 のログ・監査・件数で行い、負荷の下の性能は Build and Test の結果（`test-results.md` 9節）を基準とする。

## Sources

- 前の手順: `aidlc/spaces/default/intents/261016-business-data-views/operation/deployment-pipeline/deployment-strategy.md`、`aidlc/spaces/default/intents/261015-deps-ui-carryover/operation/deployment-pipeline/deployment-strategy.md`
- `cd-config.md`・`deployment-pipeline-questions.md`（Q1・Q3）
- この Intent の記録: `construction/build-and-test/test-results.md`・`cross-unit-traceability.md`
- リポジトリ: `backend/src/main/java/cherry/mastersmith/audit/domain/AuditEventType.java`、`backend/src/main/resources/application.yaml`、README（「業務データの画面」「監査ログの確かめ方」）
- 決まり: `aidlc/spaces/default/memory/team.md`（Deployment）、`project.md`（Deployment・Corrections の学び）

## Assumptions & Open Questions

- [assumption] 適用中の DSL に `sales.customers` が含まれ、初期管理者の今の割り当てで一覧・詳細が開ける（前の Intent の片付けの後も、グループ経由のロールの割り当てと作業ロールの保存が残っている）。開けないときは S6 を保留にして依頼者に諮る。
- [assumption] 表示の設定の保存は監査に残らない（`project.md` の学び。`AuditEventType` に種類が無い）。S9 の複写で `LOGIN_SUCCEEDED` 以外の種類が 0 であることを数えて確かめる。
- 見本の `customers` は2行のため、S6 では複数ページの送りを確かめられない（E2E 180 の 25 件で確かめ済み）。
