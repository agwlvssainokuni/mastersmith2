# 配備の仕方（deployment-strategy）

Intent 261015-deps-ui-carryover の配備の仕方です。前の手順（`aidlc/spaces/default/intents/261004-role-menu/operation/deployment-pipeline/deployment-strategy.md`）からの差だけを書きます。

## 1. 方式

- 入れ替え（recreate）。開発者の PC 上の1つのコンテナで、アプリを止めて新しいイメージで起動し直す。無停止の入れ替えはしない（配備先が決まるまでの扱い）。
- アプリが止まるのは、入れ替えの前の複写から healthy まで（約 2 分の見込み）と、配備の後の複写（約 10 秒）。
- Mailpit も入れ替える（Q1: A）。受け手の中身は消える。

## 2. 入れ替えの手順（`cd-config.md` の 3節の順 1〜6）

```bash
# 順 1: 関門
git status --short -- . ':!aidlc'                       # 空であること
git diff --stat 776738e HEAD -- . ':!aidlc'             # 空であること

# 順 2: WAR の作り直しと確かめ
./gradlew :backend:bootWar
shasum -a 256 backend/build/libs/mastersmith.war        # 記録する
unzip -l backend/build/libs/mastersmith.war | grep -E 'SafeYamlParser|DslModel.class|noto-sans-jp-japanese-400' | head

# 順 3: 戻し先のタグ（今動いている版）
docker tag mastersmith:local mastersmith:pre-deps-ui-carryover
docker image inspect mastersmith:pre-deps-ui-carryover --format '{{.Id}}'   # sha256:e0a5825d257c… であること

# 順 5: 依頼者の承認の後、T0（UTC）を控え、アプリを止めて内部DB を複写する（配備の前のバックアップ）
date -u +%Y-%m-%dT%H:%M:%SZ
docker compose stop app
#   README の「内部DBのバックアップと戻し方」の形で ~/.mastersmith-backup/mastersmith-data-<日時>-pre-deps-ui-carryover.tgz を作る。
#   複写を ACCESS_MODE_DATA=r で開き、cd-config.md の 4節の数だけを読む（値は表示しない）

# 順 6: 順 2 の WAR からイメージを作り直し、app だけを入れ替える。続けて Mailpit を入れ替える
docker compose --profile targetdb-postgres up -d --build app
docker compose --profile targetdb-postgres ps app                       # healthy を待つ
docker compose --profile mail up -d mailpit
docker compose ps mailpit                                               # healthy・v1.31.5 であること
```

## 3. 配備の後の確かめ（スモークテスト）

健全性と、下のすべてが通るまで、配備の完了としません（`team.md` の Deployment）。パスワードの要る操作と画面の操作は依頼者が行い、AI はアプリのログと監査の記録で裏付けます。起動のログは値を表示せず、レベル・ロガー・メッセージ・キーの名前と件数だけを見ます。監査に残る操作は行う前に伝え、依頼者が予定に無い操作をしたときはその場で伺って期待の件数を直します（`project.md` の学び）。

### 3.1 確かめの一覧

| # | 確かめ | 行う人 | 通る条件 |
|---|---|---|---|
| S1 | 健全性と起動のログ | AI | `app` が healthy、`/actuator/health` が 200・UP。ERROR 0 件。WARN は前の版から出ているもの（Spring の Bean の案内、Flyway の H2 の版の案内）と、救済の見込みどおりの救済の WARN のほかに無い |
| S2 | JVM と移行 | AI | コンテナの Java が 25.0.4.1。`ExitOnOutOfMemoryError` と `MaxRAMPercentage=50.0` が PID 1 のコマンド行に1件ずつ。Flyway の `Migrating schema` が 0 件で、現在の版が 12。初期管理者の INFO・WARN にキー `maskedEmail` があり、キー `email` が無い |
| S3 | `hikari-pool.sh` | AI | 新しい jdk のイメージで `docker/hikari-pool.sh status` が新しい JRE のアプリに対して動く（Build and Test の T-14）。値は表示しない |
| S4 | 書体の配信 | AI | アプリが配る `index.html` から読み込む CSS に `noto-sans-jp` の書体があり、書体のファイルが 200 で返る |
| S5 | ★ログインと書体の表示 | 依頼者 | 初期管理者でログインし（ログイン中ならタブを読み込み直し）、ホームが開ける。日本語の文字が Noto Sans JP で表示される（崩れ・文字化け・代わりの書体の目立つ差が無い。Build and Test の T-15） |
| S6 | ★Dropdown の収まり | 依頼者 | 利用者の管理の一覧で、表の右端の行の「操作」のメニューを開くと、メニューが画面の右へはみ出さずに画面の中に収まる（ブラウザの幅を狭めても収まる） |
| S7 | ★畳んだサイドバーの印 | 依頼者 | サイドバーを畳むと、今いる画面の項目（またはそれを含む区画）に印が出る。開き直すと元に戻る |
| S8 | ★英語の表示のトップバー | 依頼者 | 表示の設定で英語に切り替えると、トップバーの文言（畳むボタンとユーザーメニューのトリガーの名前・ユーザーメニュー）と画面の文言が英語になる。確かめた後に日本語に戻す（表示の設定の保存は監査に残らない） |
| S9 | ★DSL の管理の画面 | 依頼者 | DSL の画面の状態の表示に、落とした項目の警告が出ない（適用中の DSL は深さの上限の内のため）。プレビューがあるときは、違いの表の上に「メニューの違いは比べない」の説明が出る（プレビューを作る操作は監査に残るため、無ければ作らない） |
| S10 | 監査の記録 | AI | README の「監査ログの確かめ方」のとおり、アプリを止めて内部DB を `~/.mastersmith-backup/mastersmith-data-<日時>-after-deps-ui-carryover.tgz` へ複写し、すぐ起動し直す（約 10 秒。依頼者に伝えてから）。複写を読み取りで開き、T0 以降の `audit_events` を種類と結果ごとに数え、3.2 の表と比べる。この複写は配備の後のバックアップを兼ねる |

- 1行の長さの上限の拒否は確かめない（Q2: B。`cd-config.md` の 5節）。

S1・S2 のコマンド（値を表示しない形）:

```bash
docker compose logs --no-log-prefix app | jq -R -r 'fromjson? | .level' | sort | uniq -c
docker compose logs --no-log-prefix app | jq -R -r 'fromjson? | select(.level=="WARN" or .level=="ERROR") | .level + " " + .logger + " " + .message' | sort | uniq -c
docker compose exec app java -version 2>&1 | head -1
docker compose exec app sh -c 'tr "\0" " " < /proc/1/cmdline' | grep -o -E 'ExitOnOutOfMemoryError|MaxRAMPercentage=[0-9.]+' | sort | uniq -c
docker compose logs --no-log-prefix app | jq -R -r 'fromjson? | select(.logger | startswith("org.flywaydb")) | .message' | grep -c 'Migrating schema'   # 0 であること
docker compose logs --no-log-prefix app | jq -R -r 'fromjson? | select(.logger | test("InitialAdmin")) | .level + " " + .message + " " + (keys_unsorted | join(","))' | sort | uniq -c
```

### 3.2 監査の期待の件数（T0 以降、`AuditEventType` で確かめた種類）

数えるのは種類（`event_type`）と結果（`result`）だけで、利用者・対象の値は表示しません。

| 種類 | 期待の件数（結果は成功） | 出どころ |
|---|---|---|
| `LOGIN_SUCCEEDED` | S5 で依頼者がログインした回数（ログイン中のまま読み込み直したときは数えない） | S5 |
| `INITIAL_ADMIN_RESCUED` | 救済の見込みどおり（働かない見込みなら 0） | 順 6 |
| `INITIAL_ADMIN_CREATED` | 0 | 順 6 |
| `DSL_GENERATED`・`DSL_SUBMITTED`・`DSL_SUBMISSION_REJECTED`・`DSL_APPLIED`・`DSL_PREVIEW_DISCARDED`・`ROLE_*`・`GROUP_*`・`WORK_ROLE_SWITCHED`・`ACCESS_DENIED`・`LOGIN_FAILED` | 0 | 予定に無い操作 |
| 上のどの種類でも、結果が失敗の行 | 0 | 業務の拒否が無いこと |

- 確かめの読み取り（一覧・詳細・状態・表示の設定の保存）は監査に残りません（README・前の手順の S8 の誤りの訂正）。

## 4. 合否の基準と中止の条件

- **合格**: S1〜S10 がすべて通ったとき。合格の後に `main` への取り込み（`cd-config.md` の順 8）へ進む。
- **中止**: 次のどれかが起きたら配備を中止し、依頼者に諮ってから `rollback-runbook.md` に従って戻します。

| 条件 | 戻し方 |
|---|---|
| 入れ替えの前の測りで、DSL の最も長い行が 65,536 を超える | 入れ替えない（アプリを `docker compose start app` で起動し直して依頼者に諮る） |
| 起動が healthy にならない | イメージだけを戻す（`rollback-runbook.md` の 2節） |
| S1 で ERROR が出る・出続ける、ログに秘密・メールアドレスそのものが出る | 同上 |
| S5〜S9 のどれかが通らず、その場で原因が操作の誤りや設定だけと分からない | 同上 |
| S10 で期待と違う件数が出て、依頼者の操作で説明できない | 合否を決める前に依頼者に諮る |
| アプリが止まった（`ExitOnOutOfMemoryError`） | `rollback-runbook.md` の 4節 |

## 5. 監視

- 手元の監視（lgtm、profile `monitoring`）は、この段では起動しない。
- 配備の直後は、`docker compose logs app`（値を表示しない形）と監査の記録（S10）で確かめる。

## Sources

- 前の手順: `aidlc/spaces/default/intents/261004-role-menu/operation/deployment-pipeline/deployment-strategy.md`
- `cd-config.md`（この段）、`construction/build-and-test/build-and-test-summary.md`（T-14・T-15）
- `backend/src/main/java/cherry/mastersmith/audit/domain/AuditEventType.java`（監査の種類）、README（監査ログの確かめ方）

## Assumptions & Open Questions

- S8 のトップバーの名前（畳むボタン・トリガー）は読み上げの名前のため、画面に文字として出ないときは、ブラウザのツールチップか開発者ツールのアクセシビリティの表示で確かめる。E2E の 160 で英語の名前を確かめ済み。
