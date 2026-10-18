# 基盤の設計 — U3 u3-businessdata-screens

U3 は種類 `ui` の単位で、業務データの一覧・詳細・作成・更新の画面を受け持つ。配備先は開発者の PC の上のコンテナだけで、クラウドの基盤は作らない（`aidlc/spaces/default/memory/project.md`）。画面は WAR に同梱した `dist` のままで、新しいコンテナと内部DB の移行は無い。この単位の基盤の中心は、代表の流れ 180 のための E2E の環境（見本の PG に届く道・専用のスキーマ・一時のロール）である。

答え: `infrastructure-design-questions.md` の Q1〜Q7（すべて A）と、まとめの確認（Looks correct）。

## 1. Deployment

| Facet | Choice | Rationale |
|---|---|---|
| Compute | 画面は WAR に同梱した `dist`（SPA）。新しいコンテナは無い | team.md の Deployment |
| 画面の配信 | 配信元と同じオリジンの API を呼ぶ。CORS の設定は置かない | team.md の Code Style |
| 初回の JavaScript | 一覧・詳細・フォームの3つの画面を別々の `lazy` にする。`frontend/scripts/check-bundle-size.mjs` の入口の値と画面ごとの chunk の gzip を記録する | U3-NFR1.10、NFR 設計 `performance-design.md` 5節 |
| E2E の環境 | PC の上の WAR（18081、一時の H2）と Playwright。compose の見本の PG（`targetdb-postgres`）と Mailpit を使う | NFR 要件 Q2: A |
| ネットワーク | E2E のときだけ、上乗せの compose ファイルで見本の PG を `127.0.0.1:15432` に開ける。上乗せ無しで作り直せば閉じる（Q1: A） | `compose.yaml` 137行の「開けない」を既定のまま残す |
| E2E の接続先 | WAR の `MASTERSMITH_TARGET_DB_HOST` は名前ではなく `127.0.0.1` | colima の IPv4 の転送の学び（project.md） |
| Storage | 実行ごとの専用のスキーマ（使い捨て）。E2E の内部DB は今のまま一時の H2 | team.md の Testing Posture |
| IaC | 上乗せの compose ファイル1つ、Gradle の準備・片付けのタスク、表の定義の SQL のファイル1つ | project.md の Deployment |
| Sizing | 見本の PG の `mem_limit` 512m は変えない。180 の負荷は小さい | 2.3 |
| 戻し | イメージだけ（U3 は内部DB の移行を足さない） | team.md の Deployment |

### 1.1 置き場（名前はコード生成で確定する）

| もの | 置き場の案 | 中身 |
|---|---|---|
| 上乗せの compose | `docker/e2e/compose.targetdb-port.yaml` | `targetdb-postgres` に `127.0.0.1:${E2E_TARGETDB_PORT:-15432}:5432` を足すだけ |
| 表の定義と架空の行 | `frontend/e2e/support/businessDataSchema.sql`（ライセンスヘッダー付き） | 表1つと架空の行 21 件以上。スキーマ名は psql の `:"schema"` で入れる |
| 準備・片付け | `build.gradle.kts` の `e2eTest` の `doFirst` と、`finalizedBy` の片付けのタスク | `cicd-pipeline.md` 3節 |

上乗せの起動の形（README に書く）:

```bash
docker compose -f compose.yaml -f docker/e2e/compose.targetdb-port.yaml \
  --profile targetdb-postgres up -d targetdb-postgres
```

コンテナは作り直しになるが、ボリュームは残る。配備したアプリは compose の中の名前（`targetdb-postgres:5432`）で届くため、影響しない。

## 2. Infrastructure Services

| Service | Role | Configuration | Notes |
|---|---|---|---|
| 見本の PG `targetdb-postgres` | database | PostgreSQL 18.6、`mem_limit` 512m。E2E のときだけ `127.0.0.1:15432` に公開する。管理者はコンテナの中のローカルのソケットから入る（Q3: A） | 配備したアプリと共有。E2E が触るのは `e2e_biz_*` と `mastersmith_e2e_*` だけ |
| 一時の読み取りのロール | 資格情報 | `mastersmith_e2e_r_<印>`。LOGIN、乱数のパスワード、`VALID UNTIL` は作った時刻から 2 時間。実行のスキーマの USAGE・SELECT だけ | WAR の `MASTERSMITH_TARGET_DB_USERNAME`・`_PASSWORD` |
| 一時の書き込みのロール | 資格情報 | `mastersmith_e2e_w_<印>`。上と同じ形。実行のスキーマの USAGE と表の SELECT・INSERT・UPDATE・DELETE（sequence を使う表なら USAGE も）。DDL・TEMPORARY は与えない | WAR の `MASTERSMITH_TARGET_DB_WRITE_USERNAME`・`_PASSWORD`（U2 の Q1: A の名前） |
| 実行のスキーマ | storage | `e2e_biz_<UTC の年月日時分秒>_<16進 8 文字>`。持ち主は `target_admin`。表1つと架空の行（Q5: A） | WAR の `MASTERSMITH_TARGET_DB_SCHEMA`。180 には `E2E_BIZ_SCHEMA`（名前は仮）で渡す |
| Mailpit | mail | 変えない | — |
| E2E の WAR | app | 対象DB の値は Gradle の `Exec` の environment から引き継ぐ。`webServer.env` には置かない | 今の署名鍵と初期管理者の渡し方と同じ形 |

### 2.1 WAR に渡す値

| 環境変数 | 値 | 秘密か |
|---|---|---|
| `MASTERSMITH_TARGET_DB_TYPE` | `postgresql` | いいえ |
| `MASTERSMITH_TARGET_DB_HOST`・`_PORT` | `127.0.0.1`・`E2E_TARGETDB_PORT`（既定 15432） | 接続先（json の検索に入れるかはコード生成で決める） |
| `MASTERSMITH_TARGET_DB_DATABASE` | `business` | いいえ |
| `MASTERSMITH_TARGET_DB_SCHEMA` | 実行のスキーマ | いいえ |
| `MASTERSMITH_TARGET_DB_USERNAME`・`_PASSWORD` | 一時の読み取りのロール | はい（`SECRET_ENV_NAMES`） |
| `MASTERSMITH_TARGET_DB_WRITE_USERNAME`・`_PASSWORD` | 一時の書き込みのロール | はい（`SECRET_ENV_NAMES`） |

管理者のパスワード（`.env.targetdb` の `MASTERSMITH_SAMPLE_TARGETDB_ADMIN_PASSWORD`）は、Gradle にも Playwright にも入れない（Q3: A）。

### 2.2 見本の PG の今の状態（2026-10-19、読み取りで確かめた値）

| 項目 | 値 |
|---|---|
| 版 | PostgreSQL 18.6（Debian 18.6-1.pgdg13+2）aarch64 |
| スキーマ | `public`・`sales` |
| ロール | `target_admin`・`mastersmith_reader`（`mastersmith_writer` はまだ無い） |
| `has_database_privilege('public','business','TEMP')` | `t`（U2 の REVOKE はまだ行っていない → Q6: A） |
| `pg_hba.conf`（`/var/lib/postgresql/18/docker`） | `local all all trust`、`host all all 127.0.0.1/32 trust`、`host all all ::1/128 trust`、replication の同じ3行、最後に `host all all all scram-sha-256` |
| PC への公開 | なし（`5432/tcp` だけ） |
| PC の待ち受け | 5432・15432・18081 は空き。127.0.0.1 の 1025・8025・8080 は colima の ssh が転送している |

コンテナの中のソケットとコンテナの中のループバックは `trust` のため、Q3: A の管理者の取り口は成り立つ。公開の番号から来る接続は bridge の gateway から来るため `scram-sha-256` に当たる見込みだが、確かめていない（「未確認の点」）。

### 2.3 VM に収まるか

U3 の E2E が足すのは、見本の PG の中の小さなスキーマだけである。E2E の WAR と Playwright は PC の上で動き、colima の VM（6GiB）の中の量は変わらない。見本の PG の 512m は今のまま足りる見込みで、E2E の後に `docker stats --no-stream` の値を記録する。

## 3. Shared Infrastructure

| Shared Resource | Owner Unit | Consumer Units | Access Boundary |
|---|---|---|---|
| 見本の PG `targetdb-postgres` | U1（読み取りのアカウント・見本のスキーマ）・U2（書き込みのアカウント・TEMPORARY の取り上げ） | U3 の E2E（180） | 実行のスキーマと一時のロールだけ。`sales`・`public` と既存のロール（`target_admin`・`mastersmith_reader`・`mastersmith_writer`）には触らない。前の残りを消すときも、接頭辞（`e2e\_biz\_%`・`mastersmith\_e2e\_%`）に当たるものだけ |
| E2E の WAR・内部DB・初期管理者 | 既存（`frontend/playwright.config.ts`） | U3（170 の書き換え・180・185）・U4（130 など） | `workers: 1`。初期管理者の状態は変えない |
| Mailpit | 既存 | E2E の全件 | 変えない |

## 4. 040 との両立（Q2: A）

| 項目 | 扱い |
|---|---|
| WAR の数 | 1つのまま。対象DB の設定を渡し、`MASTERSMITH_TARGET_DB_SCHEMA` を実行のスキーマにする |
| 040 の照合 | DSL のスキーマ `e2e` が設定と違うため、照合は `SCHEMA_MISMATCH` の1件に決まる（`backend/src/main/java/cherry/mastersmith/dslmanage/service/DslReconciler.java` 112〜117行）。PG の中身によらない |
| 040 の書き換え | 期待を `dsl-warning-group-SCHEMA_MISMATCH` に替え、21行の説明と README の表を直す。既存の E2E の書き換えで、本数に数えない |
| 未設定の経路 | 実際のブラウザでは見なくなる。サーバー側の結合テスト（dslmanage）と 135 の差し替えが受け持つ |
| 150・170 | API の適用は `SCHEMA_MISMATCH` の警告になるが、適用は警告で止まらない（`dslmanage/service` を読んだ限り）。170 は NFR 要件で差し替えへの書き換えが決まっている |

## 上流との差

承認済みの文書は書き換えず、差をここに書く（project.md の決まり）。

1. **Q3: A と承認の場の決定 U3 R-01 の文面との差**: NFR 要件の承認の場の決定 U3 R-01（`construction/nfr-requirements/memory.md`）は「見本 DB の管理の資格情報をプロセスの環境変数で渡し、`SECRET_ENV_NAMES` に足す」としていた。この単位では、管理者は `docker compose exec` でコンテナの中のローカルのソケット（`trust`）から入り、管理者のパスワードをプロセスに入れない（Q3: A）。決定の目的（`webServer.env` に置かない、json を検索する）は満たし、そもそも値を持たない点が文面との差である。`SECRET_ENV_NAMES` には一時の2つのロールの値を足す。
2. **040 の書き換え**: `frontend/e2e/040-dsl-admin.e2e.ts` 21行の前提（「E2E の環境には対象DB の接続先が無い」）を、Q2: A で `SCHEMA_MISMATCH` の期待に替える（4節）。U3 の NFR 設計は「040 の書き換えか別の形」を後の段に回しており、その答えである。
3. **U1 の「見本の対象DB は変えない」を上乗せで満たす**: U1 の `infrastructure-specification.md` 58行は見本の対象DB を「変えない」とする。`compose.yaml` は変えず、ポートは E2E のときだけ上乗せのファイルで開ける（Q1: A）。
4. **U2 の R-01 にこの単位で答える**: U2 の基盤の設計のレビュー R-01（Major）の (a) 届く道は Q1: A、(b) 管理者の資格情報の置き場は Q3: A（プロセスに入れない）、(c) 040 との両立は Q2: A で答える。

## 後の段に回すこと

- **B5 の計画**: 185 と 170 の書き換えは差し替えで流す。見本の PG の起動は、B5 の統合の前の E2E の全件からすでに前提になる。準備・片付けのタスクと上乗せのファイルを B5 で入れるか B6 で入れるかは、B5 の計画で決める（040 の書き換えと同じ Bolt に置く）。
- **B6 の計画**: 180、`businessDataSchema.sql`、`SECRET_ENV_NAMES` と `SecretKind` の追加、`-Pe2e.files=…`、README の E2E の節。
- **B6 の前に手で1回**: 見本の PG の `REVOKE TEMPORARY ON DATABASE business FROM PUBLIC`（Q6: A。U2 の README の手順と同じ）。
- **B6 で確かめること**
  - 公開の番号から来る接続が scram になり、誤ったパスワードで拒まれること
  - 片付けの後に `e2e_biz_*` と `mastersmith_e2e_*` が 0 件になること
  - 040・150・170・180 が通ること
  - E2E の全体・180・185 の時間と、見本の PG の `docker stats` の値

## U1・U2・U4 とそろえる点

- **U1**: 読み取りの環境変数の名前（`MASTERSMITH_TARGET_DB_*`）をそのまま使う。見本の対象DB の `compose.yaml` は変えない（上流との差の3）。perf の値は E2E に響かない。
- **U2**: Q5: A の一時のロールの形をそのまま受ける。書き込みの環境変数の名前は U2 の Q1: A。DDL は与えない。TEMPORARY の取り上げは U2 の台本と同じ SQL で、B6 の前に手で行う。
- **U4**: B7 と Build and Test の E2E の全件の手順に、見本の PG を上乗せで起動することを足す。時間は U4 の NFR7.2 と同じ回で測る。

## 未確認の点

- **外からの接続が scram になること**: コンテナの中の `pg_hba.conf` は `127.0.0.1/32` を `trust` にしている。公開の番号から来る接続は bridge の gateway から来るため `scram-sha-256` に当たる見込みだが、確かめていない。B6 で誤ったパスワードの接続が拒まれることを確かめる。当たらなければ、上乗せをやめて Q1 を諮り直す。
- **`DROP ROLE` が止まるか**: 一時のロールに接続が残っているときに `DROP ROLE` が止まるかは確かめていない。止まる場合に備えて、片付けの順の先頭に `pg_terminate_backend` を置く。
- **185 の時間**: 前の Intent の記録にファイルごとの時間が無く、185 の 22 件の時間は見積もれていない。B5・B6 の実測で、全体が基準の 1.5 倍を超えるかを判断する。

## Sources

- この段の答え: `aidlc/spaces/default/intents/261016-business-data-views/construction/u3-businessdata-screens/infrastructure-design/infrastructure-design-questions.md`（Q1〜Q7: A、まとめの確認: Looks correct）
- 上流（どれも `aidlc/spaces/default/intents/261016-business-data-views/` の下）
  - `construction/u3-businessdata-screens/nfr-requirements/nfr-requirements-questions.md`（Q2: A）・`security-requirements.md`（NFR4.25・NFR7.13）・`performance-requirements.md`（NFR3.7）
  - `construction/u3-businessdata-screens/nfr-design/security-design.md`（8節・後の段に回すこと）・`performance-design.md`（5節）・`logical-components.md`（L10）
  - `construction/nfr-requirements/memory.md`（決定 U3 R-01）
  - `construction/u2-businessdata-write/infrastructure-design/infrastructure-design-questions.md`（Q1・Q5: A）・`cicd-pipeline.md`（5.1）・`infrastructure-specification.md`（4節・8節）
  - `.aidlc-reviews/infrastructure-design/units/u2-businessdata-write/4bcccff4724084fb/1.json`（R-01）
  - `construction/u1-businessdata-read/infrastructure-design/infrastructure-specification.md`（58行・6節）
- 既にあるもの（読むだけ）: `compose.yaml`（120〜165行）、`.env.example`（41〜73行）、`.env.targetdb.example`、`frontend/playwright.config.ts`（41〜62行・98〜122行）、`frontend/e2e/040-dsl-admin.e2e.ts`（21行・141〜147行）、`backend/src/main/java/cherry/mastersmith/dslmanage/service/DslReconciler.java`（92〜120行）
- この PC の実行環境: `colima list`・`docker ps`・`docker network ls`・`docker volume ls`・`lsof -iTCP -sTCP:LISTEN`、見本の PG の中の `pg_hba.conf` の表示と SELECT（2026-10-19、読み取りだけ）

## Assumptions & Open Questions

- 前提: 開発者は1人で、E2E は 18081 の固定の番号で動くため、2つの E2E が同時に流れることは無い。前の残りを接頭辞で消してよいのはこのため。
- 前提: `docker compose exec` は、リポジトリのルートの `compose.yaml` の見本の PG に当たる（プロジェクトの名前は `mastersmith`）。
- 未確認: 「未確認の点」の3つ。
- 諮る点は無い。E2E の全体が基準の 1.5 倍を超えたときと、外からの接続が `trust` に当たったときだけ、その時点で諮る。
