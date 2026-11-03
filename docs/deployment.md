# 配備と戻し

コンテナで配備したアプリの初期管理者の救済、戻し方、コンテナの資源の上限、プロキシを置く配備、スキーマの変更（Flyway）をまとめた文書です（最初の起動の手順は `README.md` の「コンテナでの起動と確認」、内部DB のバックアップは `docs/internal-db.md`）。

## 使える管理者がいなくなったとき（初期管理者の救済）

管理者の印を外す・止めるなどで、使える管理者がいなくなったとき（ログインできない・管理の画面が使えない）は、アプリを起動し直すと、`.env` の初期管理者の設定（`MASTERSMITH_AUTH_INITIAL_ADMIN_EMAIL`・`MASTERSMITH_AUTH_INITIAL_ADMIN_PASSWORD`）の利用者が救済されます。救済専用の設定はありません。画面・API からは操作できず、起動のときだけ働きます。

- **救済する条件**: 設定のメールアドレスの利用者が既にいて、次のどれか1つでも当たるとき。
  - (a) 利用が止められている（停止中）
  - (b) 管理者の印が無い
  - (c) 今のパスワードが、設定のパスワードと一致しない
- **救済で行うこと**（1つのトランザクションで、全部が反映されるか、全部が取り消されるかのどちらか）: (a) 停止を解く、(b) 管理者の印を付ける、(c) ログインの失敗回数を 0 に戻しロックを解く、(d) パスワードを設定の値に置き換える（(c) の条件に当たらないときも置き換える）、(e) その利用者のリフレッシュトークンをすべて無効にする（画面でログインし直す）。
- **ログ**: 救済したときは WARN「初期管理者を救済しました」が1行出ます。キーは `maskedEmail`（伏せ字のメールアドレス）と `conditions`（当たった条件。`SUSPENDED`・`NO_ADMIN`・`PASSWORD` を決まった順に `+` でつないだ値、例 `SUSPENDED+NO_ADMIN`）だけです。どれにも当たらないときは、INFO「初期管理者は既にいるため、作成しませんでした」が出て、何も変わりません。救済の途中で失敗したときは、すべてを取り消して ERROR「初期管理者の救済に失敗しました」が1行出ます（キーは `maskedEmail` と `exceptionClass` だけ）。起動は続くため、`docker compose logs app` で原因の例外のクラスを見て、直してから起動し直します。
- **監査**: 救済は `audit_events` に種類 `INITIAL_ADMIN_RESCUED` の行を1行残します（`docs/operations.md` の「監査ログ」）。途中で失敗したときは残りません。
- **初期管理者を止めたい・印を外したいとき**: 先に `.env` の初期管理者の設定を替える（別の利用者のメールアドレスにする、または2項目を外す）か、初期管理者のパスワードを画面で変えたときは `.env` のパスワードもそろえます。そうしないと、次の再起動で初期管理者がまた救済され、止めた状態・外した印・画面で変えたパスワードが元に戻ります。
- **設定のパスワードが規則を満たさないとき**: 設定のパスワードが規則（12 文字以上・72 バイト以内）を満たさないときは、作成も救済もされず、WARN「初期管理者を作成しませんでした」（キー `reason`・`resolution`）が出ます。停止中・印なしの初期管理者がいても救われません。この WARN が出たら、`.env` のパスワードを規則に合う値に直して起動し直します。設定が無いときも同じ WARN が出て、何もしません。
- **確かめ方**: 値を表示せずに、件数とキーの名前だけで見ます（例: `docker compose logs app | grep -c '初期管理者を救済しました'`）。監査の行は `docs/operations.md` の「監査ログの確かめ方」の手順で、種類 `INITIAL_ADMIN_RESCUED` の行の数と `rejection_kind` の値を数えます。メールアドレス・パスワードの値は画面やログに出さないでください。

## 戻し方

配備の前に、いま動いているイメージに戻し用のタグ（例: `pre-dsl`）を付けて残しておき、問題が出たらそのイメージで起動し直します（イメージを作り直さない）。

```bash
# 配備の前（新しい版を作る前）に、いま動いている版のイメージに戻し用のタグを付ける
docker tag mastersmith:local mastersmith:pre-dsl
```

戻すときは、次の順で行います。

```bash
docker image inspect mastersmith:pre-dsl --format '{{.Id}}'   # 戻し先のイメージがあること
docker compose stop app                                       # 止めるときに H2 のファイルが詰め直される（約 1 秒）
# 今の内部DBのデータを複写する（docs/internal-db.md の「内部DBのバックアップと戻し方」。戻した後に調べるため）
MASTERSMITH_IMAGE_TAG=pre-dsl docker compose up -d --no-build app   # 戻し先のイメージで起動する（作り直さない）
MASTERSMITH_IMAGE_TAG=pre-dsl docker compose ps app                 # healthy になったら、README.md のスモークテストを行う
```

- `MASTERSMITH_IMAGE_TAG` は `compose.yaml` の `image: mastersmith:${MASTERSMITH_IMAGE_TAG:-local}` の口です。**戻している間は、アプリを起動・作り直す `docker compose` のコマンドに毎回 `MASTERSMITH_IMAGE_TAG=<戻し用のタグ>` を付けます。** 付けずに `docker compose up -d` を行うと、`mastersmith:local`（新しい版）で作り直されます。付け忘れを避けたいときは、`.env` に `MASTERSMITH_IMAGE_TAG=<戻し用のタグ>` の1行を足し、新しい版に戻すときに消します。
- **`--no-build` を必ず付けます。** `--build` を付けると、今の WAR から作ったイメージに戻し用のタグが付き、戻し先のイメージが上書きされます。
- 戻し用のタグのイメージが無いとき（消してしまったとき）は、直前の版のコミット（前回の配備で控えたハッシュ）を `git worktree add ../mastersmith-rollback <ハッシュ>` で取り出し、`(cd ../mastersmith-rollback && git submodule update --init && ./gradlew :backend:bootWar)` で WAR を作り、その WAR で `docker build -t mastersmith:<戻し用のタグ> .` を行ってから、上の手順で起動します。終わったら `git worktree remove ../mastersmith-rollback` で消します。
- 見本の対象DB（`targetdb-postgres`）は戻しの対象外です。動かしたままでよく、古い版が `MASTERSMITH_TARGET_DB_*` を読まないときは使われません。

スキーマの変更は前進のみ・後方互換のため、1つ前の版のアプリが今のスキーマで動きます。スキーマは戻しません。データが壊れたとき、移行（Flyway）が途中で失敗したときだけ、配備の前に取ったバックアップを展開してデータも戻します（`docs/internal-db.md` の「内部DBのバックアップと戻し方」。バックアップの後の記録は失われます）。

V9（利用停止の列 `users.suspended`、下の「スキーマの変更（Flyway）」）を当てた後に1つ前の版へ戻すときは、**戻している間は利用停止が効かなくなる**（1つ前の版は停止の列を読まないため、停止中の利用者もログイン・トークンの更新・アクセストークンの認証の3つの入口で受け付けられる）ことに気を付けます。戻す前に停止中の利用者がいるかを確かめ、いたときの扱い（戻さない・戻した後に止め直す など）を決めてから戻します。

V8（招待の表、`docs/users.md` の「招待と登録の完了」）を当てた後に1つ前の版へ戻すときは、次の点に気を付けます。

- 1つ前の版は招待の表を読み書きしません。戻した後に今の版へ戻し直すと、招待の表は戻す前の状態のまま使われ、戻している間に有効期限を過ぎた招待は期限切れになります（管理者が一覧から送り直す）。
- 1つ前の版も `MASTERSMITH_WEB_BASE_URL` を読むため、Origin の確かめは同じ値に固定されたままです（同じ `http://localhost:8080` で開けば動きます）。戻しの練習で別の番号（例 `http://localhost:18080`）で開くときは、配備の `.env` の複写の末尾に `MASTERSMITH_WEB_BASE_URL=http://localhost:18080` を足してから起動します（同じ項目が2行あると後の行が効きます）。

V7（利用者のプリファレンスとパスワードの変更、`docs/users.md` の「利用者のプリファレンスとパスワードの変更」）を当てた後に1つ前の版へ戻すときは、次の2点に気を付けます。

- **`.env` の `MASTERSMITH_AUTH_INITIAL_ADMIN_EMAIL` を、配備のときのまま変えないでください。** 変えると、1つ前の版が初期管理者を作ろうとして、氏名（`display_name`、V7 で足した必須の列）を渡さない利用者の追記を試み、失敗しえます（起動は続く見込みですが、未確認です）。
- **空の内部DB で1つ前の版を起動するときは、V7 を当てていない内部DB を使います。** 1つ前の版が利用者を作るのは、利用者が1人もいないときの初期管理者の作成だけで、V7 を当てた内部DB には必ず利用者がいるため、ふだんの戻しでは起きません。

## コンテナの資源の上限（colima の VM・メモリ・JVM）

colima の VM の大きさはリポジトリの外の設定のため、コミットでは固定できません。各自の PC で次のとおりにします。

```bash
# VM を CPU 4・メモリ 6GiB にする（VM を止めると、動いているコンテナも止まる）
docker compose stop app
colima stop
colima start --cpu 4 --memory 6
colima list                                              # CPUS が 4、MEMORY が 6GiB
docker info --format '{{.NCPU}} {{.MemTotal}}'           # 4 と約 6GB（VM の OS の分だけ 6GiB より少し小さい）
docker compose up -d --wait                              # app が healthy になるまで待つ
```

- VM の大きさの見積もり: 配備したアプリ（2GB）・見本の対象DB（512MB）・手元の監視 `lgtm`（1.5GB）を合わせて約 4GB で、6GiB の内側に収まります。負荷の試験の環境（2GB、`perf/README.md`）も同時に動かすと約 6GB で VM の上限に近づくため、負荷の試験の間は手元の監視を止めます。
- コンテナの上限の既定は CPU 4・メモリ 2g で、この VM の大きさが前提です。VM を広げた後は `docker compose up -d --wait` でコンテナを作り直し、上限を `docker inspect mastersmith-app-1 --format '{{.HostConfig.NanoCpus}} {{.HostConfig.Memory}}'`（`4000000000 2147483648`）で確かめます。VM がこれより小さい PC では、`.env` の `MASTERSMITH_CONTAINER_CPUS`・`MASTERSMITH_CONTAINER_MEMORY` で下げます（下の「既知の制約」）。
- 照合に使える CPU は、コンテナの `cpus` と VM の CPU の数の小さい方で頭打ちになります。VM だけを広げても、`MASTERSMITH_CONTAINER_CPUS` が小さいままではログインは速くなりません。
- JVM の設定は `.env` の `MASTERSMITH_JAVA_OPTIONS` で足します（イメージの作り直しは要らず、コンテナの作り直しで効きます）。最大ヒープは既定でメモリの上限の 50% です（75% では、10MB の DSL の投入とログインを重ねたときにメモリの上限に迫るため。下の「既知の制約」）。ヒープ以外（メタ領域・スレッドのスタック・直接バッファなど）はこの外側で使うため、割合を変える（例: `-XX:MaxRAMPercentage=40.0`）か、ヒープ以外の上限（例: `-XX:MaxMetaspaceSize=256m`）を足して調整します。値は空白で区切り、空白を含む値は扱いません。JVM 標準の `JAVA_TOOL_OPTIONS` は使いません（コマンド行の 50% に上書きされ、起動の時に JSON でない行をログに出すため）。
- ヒープが尽きた（`OutOfMemoryError`）ときは、イメージの既定の JVM の引数 `-XX:+ExitOnOutOfMemoryError` で JVM が止まります（中途半端な状態で動き続けないため）。compose のアプリは `restart: "no"` のため起き直さないので、`docker compose logs app` と `docker inspect mastersmith-app-1 --format '{{.State.OOMKilled}} {{.State.ExitCode}}'` で原因を見てから `docker compose up -d --wait` で起動し直します。テストの JVM には付けていません。

### 既知の制約（メモリの上限を下げるときと高い負荷）

- メモリの上限の既定は 2g です（最大ヒープはその 50%、1,024MiB）。2g では、`refresh` を毎秒約 11,000 件で流しても止まりませんでした（`perf/README.md`。最大ヒープが 75% だったときの結果）。
- 最大ヒープが 75%（1,536MiB）だったときは、10MB の DSL の投入とログインを重ねる負荷（k6 の `dslMixed`）で、ヒープが最大まで広がり、プロセスのメモリが上限の約 93% に達して、コンテナのメモリの上限での回収（`memory.events` の `max`）が 1,500 回を超えました。50% では同じ条件で約 69%・0 回でした。割合を 75% に戻す（`MASTERSMITH_JAVA_OPTIONS=-XX:MaxRAMPercentage=75.0`）と、この状態に戻ります。
- VM が CPU 4・メモリ 6GiB に満たない PC では、`.env` の `MASTERSMITH_CONTAINER_MEMORY` で `1g` などに下げます。ただし 1g では、高い負荷でメモリの上限で止まりえます。最大ヒープが 75%（768MB、ヒープ以外に使えるのは約 256MB）だったときに、CPU の上限 2・メモリの上限 1g で、トークンの更新を同時 10 件・考える時間なし（毎秒約 3,000 件）で流したところ、約 35 秒でコンテナがメモリの上限で止まりました（OOMKilled、2026-09-23 の負荷の試験）。`restart: "no"` のため、止まったままになります。
- 上限を変えると、最大ヒープも 50% の割合で変わり、1g では JVM の GC が G1 ではなく Serial になります。負荷の試験の結果とメモリの内訳（ヒープとヒープ以外）の測り方は `perf/README.md` にあります。
- 止まったかどうかは `docker inspect mastersmith-app-1 --format '{{.State.OOMKilled}} {{.State.ExitCode}}'`（`true 137` なら上限で止まった）で確かめます。

### 設定の効き方の確かめ

`Dockerfile`・compose を変えたときは、次のスクリプトで、メモリの上限の変数と JVM の設定の口が効くことと、環境変数の分け方（アプリは `.env`、見本の対象DB は `.env.targetdb`）を確かめます。JVM の `-version` だけを小さな上限（512MB）で動かすため、アプリは起動せず、秘密情報も要りません。配備したアプリを止めずに実行できます。

```bash
./gradlew :backend:bootWar && docker compose build app   # イメージ mastersmith:local を作る（配備したコンテナは作り直さない）
./docker/check-container-limits.sh                        # 期待と違う点があれば、期待の値と実際の値を出して失敗する
```

- 確かめること: 両方の compose の `mem_limit`（変数なしで 2g、`MASTERSMITH_CONTAINER_MEMORY=768m` で 768m）、最大ヒープの割合（変数なし・空の値で 50%、`MASTERSMITH_JAVA_OPTIONS` で 60%）、ヒープ以外の上限（`-XX:MaxMetaspaceSize=128m`）、java がコンテナの PID 1 であること（停止の合図を直接受け取る）、タイムゾーンの引数が残ること、`compose.yaml` の `app` が `.env` だけを読み `MASTERSMITH_SAMPLE_TARGETDB_*` を持たないことと、見本の対象DB の3つのサービスが `.env.targetdb` を読み `environment` にパスワードを持たないこと（名前だけを見て、値は展開も表示もしない）。
- 値が入った `.env` を読んだ状態でアプリのコンテナに `MASTERSMITH_SAMPLE_TARGETDB_*` が無いことは、`README.md` の「コンテナでの起動と確認」の移し替えの手順の最後のコマンドで確かめます。
- 別のタグのイメージは `MASTERSMITH_IMAGE_TAG=<タグ> ./docker/check-container-limits.sh` で確かめます。前提は Docker（Compose v2）と、そのイメージがあることです。`.env` は読みません。

## プロキシを置く配備

- エラー応答の `type` の URL を固定するときは、`MASTERSMITH_WEB_BASE_URL` にベースURLを設定します。
- プロキシの内側からだけ受け付ける配備では、`MASTERSMITH_WEB_TRUST_FORWARDED_HEADERS=true` にすると、転送元のヘッダーのスキーム・Host・接続元を使います。外から直接届く配備では有効にしないでください（ヘッダーを偽装できるため）。
- HTTPS（`Strict-Transport-Security` を含む）は、配備先が決まったときに扱います。

## スキーマの変更（Flyway）

- 置き場所: `backend/src/main/resources/db/migration`
- ファイルの名前: `V<番号>__<単位>_<内容>.sql`（例: `V2__u2_user_account.sql`）。単位ごとにファイルを分けます。
- 前進のみとし、適用済みのファイルは書き換えません（書き換えると起動時の検証で起動が止まります）。1つ前の版のアプリが動く後方互換を保ちます。
- Hibernate はスキーマを作らず、検証だけ行います。
- V7（`V7__u2_user_preferences.sql`）: `users` に `display_name`（必須、既定の値なし。既存の利用者にはメールアドレスを入れる）・`language`（既定 `ja`）・`theme`（既定 `system`）・`font_size`（既定 `md`）を、`audit_events` に `target_user_id`・`target_invitation_id`（空を許す）を足します。前進のみで、1つ前の版のアプリが動く後方互換を保ちます。
- V8（`V8__u3_invitation.sql`）: 招待の表 `invitations` を新しく足します（既存の表は変えない）。同じメールアドレスの招待中を1件に限るため、状態が `PENDING` のときだけメールアドレスになる生成列 `pending_email` に一意の制約を付けます。トークンはハッシュ（SHA-256）だけを保存します。生成列と一意の制約のふるまいは `InvitationSchemaIT`、同時の招待は `InvitationConcurrencyIT` が確かめます。
- V9（`V9__u1_user_suspension.sql`）: `users` に `suspended`（既定 `FALSE`・必須。既存の利用者は有効のまま）を足す前進のみの変更です。確かめは、起動時の Flyway と Hibernate の検証と、利用停止の状態の読み書きの結合テストです。**`suspended` を読まない版に戻している間は、利用停止が効きません**（停止中の利用者もログイン・トークンの更新・アクセストークンの認証の3つの入口を通れます。止めたときに無効にしたリフレッシュトークンは無効のままです）。
- V10（`V10__u3_group.sql`）: グループの表 `groups`（名前の鍵 `name_key` に一意の制約 `uk_groups_name_key`）とメンバーの表 `group_members`（主キーはグループと利用者の組。グループへの外部キーは削除の制限つき、利用者への外部キー、利用者の ID の索引）を新しく足し、`audit_events` に `target_role_id`・`target_group_id`（空を許す）と `detail`（16,384 文字まで、空を許す）を足します。前進のみで、既存の列と表は変えません。確かめは `AuditMigrationCompatibilityIT`（V1〜V9 だけを知る Flyway が V10 の後の内部DB で止まらないこと、V10 の前に書いた監査の行が V10 の後も読めて足した3列が空であること、V10 の後も前の版の列だけで行を追記できること、表と制約の名前・索引・列の長さ）と、起動時の Flyway と Hibernate の検証です。1つ前の版のアプリを V10 の後の内部DB で起動する確かめ（戻しの練習）は、配備の段で扱います。
- V11（`V11__u4_role.sql`）: ロールの表 `roles`（名前の鍵 `name_key` に一意の制約 `uk_roles_name_key`）と権限の設定の表 `permission_settings`（主キー `pk_permission_settings` はロール・スキーマ・テーブル・カラムの組。上の階層の行はテーブル・カラムを空の文字列で持つ。ロールへの外部キー `fk_permission_settings_role` は削除の制限つき。値と階層の検査の制約）を新しく足します。前進のみで、既存の列と表は変えません。確かめは `RoleSchemaIT`（表と制約の名前・列の長さ・制約のふるまい）、`RoleMigrationCompatibilityIT`（V1〜V10 だけを知る Flyway が V11 の後の内部DB で止まらないこと、V11 の前に書いた利用者と監査の行が V11 の後も変わらず読めて `admin_flag` もそのままであること、V11 の後も前の版の列だけで利用者と監査の行を追記できること）、`AuditMigrationCompatibilityIT`（履歴の最後の版）と、起動時の Flyway と Hibernate の検証です。1つ前の版のアプリを V11 の後の内部DB で起動する確かめ（戻しの練習）は、配備の段で扱います。
- V12（`V12__u4_role_assignment.sql`）: 利用者への割り当ての表 `user_role_assignments`（主キー `pk_user_role_assignments` はロールと利用者の組）、グループへの割り当ての表 `group_role_assignments`（主キー `pk_group_role_assignments` はロールとグループの組）、作業ロールの保存の表 `work_role_selections`（主キー `pk_work_role_selections` は利用者。ロールへの外部キーは置かない）を新しく足します。ロール・利用者・グループへの外部キーは削除の制限つきで、利用者・グループの ID の索引を置きます。`users.admin_flag` は残し、行を移しません（管理の可否は印で決める）。前進のみで、既存の列と表は変えず、監査の表にも列を足しません。確かめは `RoleSchemaIT`（制約と索引の名前・制約のふるまい・エンティティの読み書き）、`RoleMigrationCompatibilityIT`（V1〜V10 と V1〜V11 だけを知る Flyway が V12 の後の内部DB で止まらないこと、V12 の前の行が変わらず読めること、V12 の後も前の版の列だけで利用者と監査の行を追記できること）、`AuditMigrationCompatibilityIT`（履歴の最後の版 12）と、起動時の Flyway と Hibernate の検証です。1つ前の版のアプリを V12 の後の内部DB で起動する確かめ（戻しの練習）は、配備の段で扱います。
- V14（`V14__u2_display_time_zone.sql`）: 利用者の表 `users` に列 `display_time_zone`（`VARCHAR(64)`、空を許す、既定値・制約・索引なし）を 1 つ足します。空は「ブラウザに合わせる」です。前進のみで、既存の列と行は変えません。名前の検証はアプリが 1 か所（JDK の地域の名前の集合への完全一致）で行います。確かめは `UserSchemaIT`（型・長さ・空を許すこと・既存の行が空・移行のファイルが列の追加 1 文だけ）、`DisplayTimeZoneMigrationCompatibilityIT`（V1〜V13 だけを知る Flyway が V14 の後の内部DB で止まらないこと、V14 の前に書いた利用者の行が読めて足した列が空なこと、V14 の後も前の版の列だけで追加・更新できること）、`AuditMigrationCompatibilityIT`（履歴の最後の版 14）です。**戻したとき**（1つ前の版のイメージ）: 前の版の画面にはタイムゾーンの選択が無く、管理の画面の時刻は前の版の書式（`JST` などの略称）に戻ります。列と保存した名前は消えず、前の版の保存（4 項目）は列を書き換えません。進み直すと再び効きます。1つ前の版のアプリを V14 の後の内部DB で起動する確かめ（戻しの練習）は、配備の段で扱います。
