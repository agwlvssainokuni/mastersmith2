# 内部DB（H2 と PostgreSQL）のバックアップと詰め直し

内部DB は既定で組み込みの H2 です。compose の profile で、内部DB 用の PostgreSQL に切り替えることもできます。この文書は、PostgreSQL にする手順・既知の制約・バックアップと戻し（PostgreSQL）と、H2 のバックアップと戻し方、複写を読み取りだけで数える道具、アプリを止めずにファイルを詰め直す手順（H2 だけ）をまとめた文書です。

どちらの内部DB でも、アプリは接続先（`MASTERSMITH_DB_URL`）から種類を判断します（`jdbc:postgresql:` なら PostgreSQL、それ以外は H2）。表は、PostgreSQL では PostgreSQL 用の移行（Flyway の V1〜V14）で作ります。

## 内部DB を PostgreSQL にする

既定の配備は H2 のままで、何もしなければ PostgreSQL は起動しません。使いたいときだけ、compose の profile `internal-db-postgres` で内部DB 用の PostgreSQL のコンテナ（サービス `internal-db-postgres`）を起動し、アプリの接続先をそちらに向けます。見本の対象DB（`targetdb-postgres`）とは別のサービス・別のボリューム・別の資格情報です。

- **対応する版**: PostgreSQL 18 系だけです。イメージの版とダイジェストは `compose.yaml` の `internal-db-postgres` を見てください（見本の対象DB の PostgreSQL と、結合テストのイメージ（`TargetDbImages`）と同じ値で、上げるときは3つを一緒に上げます。`.github/dependabot.yml` のコメントも参照）。MySQL・MariaDB は内部DB として対応しません（対象DB としては使えます）。
- **ポート**: PC に開けません。アプリのコンテナから `internal-db-postgres:5432` で接続します。
- **メモリ**: 上限 512m です。アプリ（既定 2g）・手元の監視・見本の対象DB と同時に動かすと colima の VM（6GiB）の近くになるため、使うときは使わない対象DB・手元の監視・負荷の試験を止めます（`docs/operations.md`）。

### 手順

1. `.env` に、次の6つを設定します。値は各自で決め、ファイルにも文書にも書き残さず、コミットしません（見本は `.env.example` の「内部DB を PostgreSQL にする」）。パスワードは乱数で作ることを勧めます（例: `openssl rand -hex 24`。英数字だけになり、`$` の展開などに左右されません）。

   | 変数 | 値 | 渡す先 |
   |---|---|---|
   | `MASTERSMITH_INTERNAL_DB_POSTGRES_USER` | 利用者の名前 | PostgreSQL のコンテナ |
   | `MASTERSMITH_INTERNAL_DB_POSTGRES_PASSWORD` | パスワード（秘密情報） | PostgreSQL のコンテナ |
   | `MASTERSMITH_INTERNAL_DB_POSTGRES_DATABASE` | データベースの名前 | PostgreSQL のコンテナ |
   | `MASTERSMITH_DB_URL` | `jdbc:postgresql://internal-db-postgres:5432/<データベース名>` | アプリ |
   | `MASTERSMITH_DB_USERNAME` | 上の利用者の名前と同じ | アプリ |
   | `MASTERSMITH_DB_PASSWORD` | 上のパスワードと同じ | アプリ |

   3つとも値を入れてください。パスワードが空だと PostgreSQL が起動を拒みます。利用者・データベース名が空だと `postgres` になり、アプリの設定とずれます。`MASTERSMITH_DB_URL` に `;DEFRAG_ALWAYS=TRUE` は付けません（H2 だけの指定です）。
2. 起動します。PostgreSQL が健全（`pg_isready`）になってからアプリが起動します。

   ```bash
   docker compose --profile internal-db-postgres up -d --build
   docker compose --profile internal-db-postgres ps    # internal-db-postgres・app が healthy になれば起動の完了
   ```

   見本の対象DB も使うときは、`--profile targetdb-postgres` も付けます（`docker compose --profile internal-db-postgres --profile targetdb-postgres up -d --build`）。以降の `docker compose` のコマンドにも同じ `--profile` を付けます。
3. 空の内部DB に、アプリが最初の起動で表を作ります。初期管理者は、`.env` の初期管理者の設定（`README.md` の「コンテナでの起動と確認」）で H2 のときと同じように作られます。

### H2 に戻す

1. アプリを止めます（`docker compose stop app`）。
2. `.env` の `MASTERSMITH_DB_URL`・`MASTERSMITH_DB_USERNAME` の行を消し（またはコメントにし）、`MASTERSMITH_DB_PASSWORD` を、H2 を作ったときの値（既定は空）に戻します。H2 を空でないパスワードで作っていた場合は、その値に戻します。
3. `--profile internal-db-postgres` を付けずにアプリを作り直します（`docker compose up -d --build`）。

H2 のデータは別のボリューム（`mastersmith-data`）に残っていて、PostgreSQL のデータとは行き来しません（PostgreSQL にしていた間の利用者・監査・DSL は H2 には現れません）。PostgreSQL のコンテナとデータは、止めただけでは残ります（消し方は次の節）。

### PostgreSQL のコンテナとデータを止める・消す

```bash
docker compose --profile internal-db-postgres stop internal-db-postgres      # 止める（データは残る）
docker compose --profile internal-db-postgres rm -sf internal-db-postgres    # コンテナを消す（データは残る）
docker volume rm mastersmith_mastersmith-internal-db-postgres                # データを消す（元に戻せない。先にバックアップを取る）
```

PostgreSQL は、データを作る最初の起動のときだけ、`.env` の利用者・パスワード・データベース名を使います。作った後に `.env` の値を変えても効きません（アプリの接続先と食い違うと、アプリの接続が認証で失敗します）。値を変えるときは、アプリと PostgreSQL のコンテナを消し、ボリュームを消して作り直します（中のデータは消えるため、必要なら先にバックアップを取り、作り直した後に戻します）。

### 既知の制約

- **起動のログに接続先の URL が出る**: アプリの起動のとき、Flyway の INFO に、資格情報を含まない接続先の URL（ホスト・番号・データベース名）が出ます。利用者の名前とパスワードは出ません。
- **アプリの利用者がデータベースの所有者の 1 アカウント**: アプリは、PostgreSQL のコンテナの `MASTERSMITH_INTERNAL_DB_POSTGRES_USER`（所有者でスーパーユーザー）と同じ 1 つのアカウントで接続します。管理用と接続用に分けていません。手元の単一インスタンスで、利用者の入力が直接届かない内部DB のための前提です。権限を分けた形は、配備先が決まるまで決めません。
- **利用者の検索の Unicode の端の差**: 利用者の検索（大文字・小文字を区別しない部分一致）は、日常の入力（日本語・全角と半角・カナ・記号・`%`・`_`・`\`）では H2 と PostgreSQL で同じ結果です。次の 3 つの端だけが違います。
  - `İstanbul`（点付きの大文字の I）は、検索語 `i` に PostgreSQL では当たり、H2 では当たりません。
  - 語末のシグマ `ς` と `σ` は、H2 では同じ文字として扱い、PostgreSQL では別の文字です。
  - `ǅ` は、検索語 `ǆ` に PostgreSQL では当たります。
- **内部DB として使えるのは H2 と PostgreSQL だけ**: MySQL・MariaDB は内部DB として対応しません。
- **H2 のときだけの運用は PostgreSQL に当たりません**（下の「H2 のときだけの運用」）。

### 困ったとき

- **PostgreSQL のコンテナが起動しない・すぐ止まる**: `docker compose --profile internal-db-postgres logs internal-db-postgres` で理由を見ます。パスワードが空のときは、パスワードが未設定である旨が出ます（`.env` の `MASTERSMITH_INTERNAL_DB_POSTGRES_PASSWORD` を入れる）。アプリは PostgreSQL が健全になるまで起動しません。
- **値を変えたのに効かない**: 上のとおり、値が効くのはデータを作る最初の起動だけです。ボリュームを作り直します。PostgreSQL のコンテナの健全の判定（`pg_isready`）は、利用者・パスワードを確かめません。接続の失敗は、アプリのログと `docker compose logs internal-db-postgres` に出ます。
- **アプリが接続できない**: `.env` の `MASTERSMITH_DB_URL` のホスト名が `internal-db-postgres` で、利用者・パスワード・データベース名がコンテナ側の3つと同じかを確かめます。

## PostgreSQL の内部DB のバックアップと戻し

PostgreSQL のコンテナの中の `pg_dump`・`pg_restore`・`psql` を、`docker compose exec -T … sh -c '…'` で呼びます。`sh -c` の単一引用符で、利用者とデータベースの名前をコンテナの中の環境変数（`POSTGRES_USER`・`POSTGRES_DB`）から展開させ、手元のシェルでは展開させません。パスワードはコマンドにも手元の環境にも書きません（コンテナの中の接続は、パスワードを要さない同じコンテナ内の接続です）。`-T` は TTY を付けない指定で、`-Fc` の出力が壊れないために付けます。

複写は、リポジトリの外のホームの下（`~/.mastersmith-backup/`、権限 700）に置き、コミットしません。複写には利用者・監査などの個人に関する値が入ります。置き場を `mktemp -d` の一時ディレクトリにしません（colima の VM から見えません）。

```bash
docker compose stop app
mkdir -p ~/.mastersmith-backup && chmod 700 ~/.mastersmith-backup
(umask 077 && docker compose --profile internal-db-postgres exec -T internal-db-postgres \
  sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > ~/.mastersmith-backup/mastersmith-pg-$(date +%Y%m%d%H%M).dump)
ls -l ~/.mastersmith-backup/     # ファイルができたこと（中身は開かない）
# 次の 1 行は、止めたアプリをそのまま再開するときだけ実行する。配備の途中（新しい版へ入れ替える）なら実行せず、
# 次の手順で新しい版を起動する（止めたままの古い版を起動してしまうため）。
docker compose start app
```

戻すときは、アプリを止め、複写を流し込みます。

```bash
docker compose stop app
docker compose --profile internal-db-postgres exec -T internal-db-postgres \
  sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists' < ~/.mastersmith-backup/<複写のファイル>
docker compose start app
```

- **失うもの**: 戻すと、複写を取った後の記録（利用者・ロール・DSL・監査の行など）は失われます。
- **残るもの**: `--clean --if-exists` が消して作り直すのは、複写にある物だけです。複写を取った後に足された表（アプリの版を上げたときの移行の表など）は残ります。複写を取った時点とまったく同じ状態にしたいときは、PostgreSQL のコンテナとボリュームを消して作り直し（上の「PostgreSQL のコンテナとデータを止める・消す」）、空の PostgreSQL に `--clean --if-exists` を付けずに `pg_restore` します（アプリは止めたままにします）。
- **戻した後の確かめ**: 次の件数を、複写を取る前（または取った直後）の値と並べて、一致を確かめます。個人に関する値は出しません。

  ```bash
  docker compose --profile internal-db-postgres exec -T internal-db-postgres sh -c \
    'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At -c "select count(*) from users" -c "select count(*) from roles" -c "select event_type, result, count(*) from audit_events group by 1, 2 order by 1, 2"'
  ```

  H2 の `scripts/count-db-copy.mjs`（下の「内部DBの複写を数える」）は H2 の複写だけを数え、PostgreSQL には使えません。
- **配備の戻し**: 配備の前に複写を取っておき、データが壊れたとき・移行が途中で失敗したときだけ、上の戻しを行います（`docs/deployment.md` の「戻し方」。スキーマは前進のみで戻しません）。

## H2 のときだけの運用

次の 5 つは、内部DB が組み込みの H2 のときだけの運用です。PostgreSQL の内部DB には当たりません（PostgreSQL は上の節の手順で、バックアップは `pg_dump`、ファイルの詰め直しは要りません）。

| H2 だけの運用 | この文書の節 |
|---|---|
| 停止時にファイルを詰め直す `;DEFRAG_ALWAYS=TRUE`（接続先の指定） | 下の「内部DBのファイルの詰め直し（アプリを止めずに）」 |
| アプリを止めずに詰め直す `docker/hikari-pool.sh`（`status`・`compact`・`resume`） | 下の「内部DBのファイルの詰め直し（アプリを止めずに）」 |
| 使い捨ての環境の H2 を読む `perf/h2-shell.sh` | 下の「内部DBのファイルの詰め直し（アプリを止めずに）」の最後の項と `perf/README.md` |
| ボリュームのファイルの複写・`tar` での戻し（バックアップと戻し） | 下の「内部DBのバックアップと戻し方」 |
| 詰め直しのスレッドを 1 本にする JVM の引数 `-Dh2.compactThreads=1` | 下の「内部DBのファイルの詰め直し（アプリを止めずに）」 |

## 内部DBのバックアップと戻し方

**H2 のときだけ**の手順です（PostgreSQL は上の「PostgreSQL の内部DB のバックアップと戻し」）。アプリを止めてから、ボリュームのファイルをリポジトリの外（ホームの下の `~/.mastersmith-backup/`、権限 700）に複写します。置き場を `mktemp -d` の一時ディレクトリにしません（colima の VM から見えず、複写が空振りします）。

```bash
docker compose stop app
mkdir -p ~/.mastersmith-backup && chmod 700 ~/.mastersmith-backup
docker run --rm -v mastersmith_mastersmith-data:/data -v "$HOME/.mastersmith-backup":/backup eclipse-temurin:25.0.5_8-jre-noble@sha256:a98128a57de944a72bd31fbc79b898edd5a5864d3652071d5bbd2160ab7d7dce \
  tar czf /backup/mastersmith-data-$(date +%Y%m%d%H%M).tgz -C /data .
ls -l ~/.mastersmith-backup/     # ファイルができたこと（中身は開かない）
docker compose start app         # 配備の途中なら起動せず、次の手順で新しい版を起動する
```

戻すときは、アプリを止め、ボリュームの中身を消してからバックアップを展開します。

```bash
docker compose stop app
docker run --rm -v mastersmith_mastersmith-data:/data -v "$HOME/.mastersmith-backup":/backup eclipse-temurin:25.0.5_8-jre-noble@sha256:a98128a57de944a72bd31fbc79b898edd5a5864d3652071d5bbd2160ab7d7dce \
  sh -c 'rm -rf /data/* && tar xzf /backup/<バックアップのファイル> -C /data && chown -R 10001:10001 /data'
docker compose start app         # 止めたコンテナをそのまま起動する（作り直すときは戻し方の節の MASTERSMITH_IMAGE_TAG を付ける）
```

コンテナの外で動かしている場合は、アプリを止めて `./data/`（`bootRun` なら `backend/data/`）を複写します。

## 内部DBの複写を数える（読み取りだけ・個人に関する値を出さない）

**H2 のときだけ**の手順です（PostgreSQL の件数の数え方は、上の「PostgreSQL の内部DB のバックアップと戻し」の「戻した後の確かめ」）。

### 台本で決まった組を数える（`scripts/count-db-copy.mjs`）

配備の前後に毎回数える組（利用者・管理者・停止・DSL の版・プレビュー・ロール・割り当て・監査の種類ごとの件数・スキーマの版など）は、台本 `scripts/count-db-copy.mjs` で数えます（Intent 261108-ci-checks-release。前にホームの下に置いていた `count-db.sh`・`count-db2.sh`・`count-db3.sh` と Java の補助を1つにまとめた）。前提は JDK（11 以上の `java`）と、Gradle のキャッシュの H2 の jar（`./gradlew` を一度でも流していればある）です。docker は使いません。

```bash
node scripts/count-db-copy.mjs --copy ~/.mastersmith-backup/<バックアップのファイル> --since <起点の時刻>
node scripts/count-db-copy.mjs --copy <…>.tgz --since 2026-11-01T05:00:00Z --set basic,roles,reflookup,tz,audit
```

| 組（`--set`） | 数えるもの | 前の道具 |
|---|---|---|
| `basic`（既定） | 初期管理者の行・停止・管理者の印、利用者の数・管理者・有効な管理者・停止、DSL の適用の版とプレビューの数、起点からの監査の種類と結果ごとの件数、スキーマの版 | `count-db.sh` |
| `roles` | `basic` の初期管理者とスキーマの版・DSL の数に加えて、最新の DSL の語（`customers`・`customer_orders`）の回数、ロールの数、初期管理者のロール（直接・グループ経由）と作業ロールの選択の数 | `count-db2.sh` |
| `reflookup` | スキーマの版、DSL の版の数と最新の DSL の語の回数と他テーブル参照の識別子（スキーマ.表.カラム → 参照先の表）、プレビュー・ロール・権限の設定・利用者・有効な管理者の数、初期管理者の行・停止・印・ロール・作業ロール（割り当て済みのものか）、監査の合計と、起点からの合計・種類と結果と理由ごとの件数・業務データの監査の列の空の数 | `count-db3.sh`（`CountRefLookup.java`） |
| `tz` | スキーマの版の最大と V14 の行、`users.display_time_zone` の列の有無と、値のある行・無い行の数、最新の DSL の語（`customers`・`created_at`）の回数 | `CountTz.java` |
| `audit` | 起点からの監査の種類と結果ごとの件数と合計、監査の全体の数 | `SinceCount.java` |

- 引数: `--copy`（複写の `.tgz`、必須）、`--since`（ISO-8601 の起点の時刻。無いときは起点からの件数を数えない）、`--set`（カンマで並べる）、`--h2-jar`（H2 の jar。無ければ環境変数 `H2_JAR`、それも無ければ `backend/gradle.lockfile` の版の jar を Gradle のキャッシュから探す）、`--env`（初期管理者のメールアドレスを読むファイル。既定 `.env`）。
- 複写はリポジトリの `build/count-db-copy/` の下の実行ごとの置き場（権限 700）に展開し、終わったら（失敗・中断でも）消します。強制終了・端末の切断などで消せずに残った置き場は、次に流したときの最初に消します。中断したときは `build/count-db-copy/` に `work-*` が残っていないかを確かめてください（複写には個人に関する値が入りうるため）。H2 は読み取り（`ACCESS_MODE_DATA=r`）で開きます。
- 出すのは件数・区分の名前・スキーマの版・DSL の識別子だけです。初期管理者のメールアドレスは `.env` から読み、子の `java` へ標準入力で渡してバインド変数で使います（出力には「`.env` にあるか」の 0・1 だけ）。SQL の誤りは例外のクラスの名前と SQLState だけを出します。
- 終了コード: 0（数えた）、1（`java` が誤りで終わった）、3（引数の誤り・複写や H2 の jar が無いなど）。
- `.env` を読むため、AI が流すときは流す前に依頼者に伝えて許しを得ます。

### 表と列を指定して数える（`CountRows`）

上の台本の組に無い表や列を数えるときは、複写を **読み取りだけで開き**、件数だけを出す次の小さな道具を使います。道具はリポジトリの外（ホームの下）に置き、コミットしません。複写は上の手順で取った `.tgz` を、ホームの下の新しいディレクトリ（権限 700）に展開したものです（macOS の一時ディレクトリは colima の VM から見えません）。

- 表と列の名前は、先に `INFORMATION_SCHEMA.COLUMNS` で確かめます。無いときは **誤りの文を出さず**、`列なし` と書くだけです（H2 の誤りの文には、問い合わせた行の値（メールアドレスなど個人に関する値）が入りうるためです）。
- 開けない・読めないときも、例外の文は表示しません（例外のクラスの名前と SQLState だけ）。
- 値を並べる `group` は、個人に関する値を持たない列（`event_type`・`result`・`failure_reason`）だけに限ります。それ以外の列は `表示しない列` と出します。
- 値はバインド変数で渡し、表と列の名前は `INFORMATION_SCHEMA` で見つけた名前を引用して使います（利用者の入力を SQL に組み込みません）。

```bash
mkdir -p ~/.mastersmith-tools && chmod 700 ~/.mastersmith-tools
cat > ~/.mastersmith-tools/CountRows.java <<'EOS'
import java.sql.*;
import java.util.Set;

/** 内部DB の複写を、読み取りだけで数える。使い方: count <表> [<列> <値>] / group <表> <列> */
public class CountRows {
    private static final Set<String> GROUPABLE = Set.of("EVENT_TYPE", "RESULT", "FAILURE_REASON");

    public static void main(String[] args) {
        try (Connection c = DriverManager.getConnection(
                "jdbc:h2:file:/data/mastersmith;ACCESS_MODE_DATA=r", "sa", "")) {
            run(c, args);
        } catch (SQLException e) {
            System.out.println("開けない・読めない（" + e.getClass().getSimpleName() + " " + e.getSQLState() + "）");
        }
    }

    private static void run(Connection c, String[] a) throws SQLException {
        if (a.length < 2 || !(a[0].equals("count") || a[0].equals("group"))) {
            System.out.println("使い方: count <表> [<列> <値>] / group <表> <列>");
            return;
        }
        String table = name(c, "TABLE_NAME", a[1], null);
        if (table == null) {
            System.out.println(a[1] + " 表なし");
            return;
        }
        if (a[0].equals("count") && a.length == 2) {
            System.out.println(a[1] + " " + one(c, "SELECT COUNT(*) FROM \"" + table + "\"", null));
            return;
        }
        if (a.length < 3) {
            System.out.println("使い方: count <表> [<列> <値>] / group <表> <列>");
            return;
        }
        String column = name(c, "COLUMN_NAME", a[2], table);
        if (column == null) {
            System.out.println(a[1] + "." + a[2] + " 列なし");
            return;
        }
        if (a[0].equals("count")) {
            String value = a.length > 3 ? a[3] : "";
            System.out.println(a[1] + "." + a[2] + " " + one(c,
                    "SELECT COUNT(*) FROM \"" + table + "\" WHERE \"" + column + "\" = ?", value));
            return;
        }
        if (!GROUPABLE.contains(column)) {
            System.out.println(a[1] + "." + a[2] + " 表示しない列");
            return;
        }
        try (PreparedStatement s = c.prepareStatement("SELECT \"" + column + "\", COUNT(*) FROM \"" + table
                + "\" GROUP BY \"" + column + "\" ORDER BY 1");
                ResultSet r = s.executeQuery()) {
            while (r.next()) {
                System.out.println(r.getString(1) + " " + r.getLong(2));
            }
        }
    }

    /** 表または列の名前を INFORMATION_SCHEMA で探す（見つからなければ null）。 */
    private static String name(Connection c, String field, String wanted, String table) throws SQLException {
        String sql = "SELECT " + field + " FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = 'PUBLIC' AND UPPER("
                + field + ") = UPPER(?)" + (table == null ? "" : " AND TABLE_NAME = ?") + " FETCH FIRST 1 ROWS ONLY";
        try (PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, wanted);
            if (table != null) {
                s.setString(2, table);
            }
            try (ResultSet r = s.executeQuery()) {
                return r.next() ? r.getString(1) : null;
            }
        }
    }

    private static long one(Connection c, String sql, String value) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(sql)) {
            if (value != null) {
                s.setString(1, value);
            }
            try (ResultSet r = s.executeQuery()) {
                r.next();
                return r.getLong(1);
            }
        }
    }
}
EOS

# 複写（.tgz）を、ホームの下の新しいディレクトリに展開する（読み取りだけで開くため、所有者は問わない。消すときは docker で消す）
COPY=$(mktemp -d "$HOME/.mastersmith-copy-XXXXXX") && chmod 700 "$COPY"
docker run --rm -v "$HOME/.mastersmith-backup":/backup:ro -v "$COPY":/out eclipse-temurin:25.0.5_8-jre-noble@sha256:a98128a57de944a72bd31fbc79b898edd5a5864d3652071d5bbd2160ab7d7dce \
  tar xzf /backup/<バックアップのファイル> -C /out
# 数える（H2 の jar は perf/h2-shell.sh と同じ Gradle のキャッシュのもの。コンテナの中の複写は /data、道具は /tool。
# 道具は Java のソースを直接実行するため、JRE でなく JDK のイメージで動かす。複写は :ro で付ける）
H2_JAR=$(ls ~/.gradle/caches/modules-2/files-2.1/com.h2database/h2/2.4.240/*/h2-2.4.240.jar | head -n 1)
count() { docker run --rm -v "$COPY":/data:ro -v "$H2_JAR":/h2.jar:ro -v "$HOME/.mastersmith-tools":/tool:ro \
  eclipse-temurin:25.0.5_8-jdk-noble@sha256:95e350ad1eb738f943e141640f7e3d797e483359a3b33c3864255bfd1ad71f8a java -cp /h2.jar /tool/CountRows.java "$@" 2>&1 | grep -v '^$'; }
count count users                              # 利用者の数
count group audit_events event_type            # 監査の種類ごとの件数
count count audit_events event_type USER_SUSPENDED
count count users no_such_column x             # 存在しない列でも、誤りの文は出ず「列なし」と出る
```

- 使い終わったら複写のディレクトリを消します（`rm -rf "$COPY"`）。
- 配備の時刻以降の件数だけを数えたいときは、`count <表> <列> <値>` の列を日時でなく種類（`event_type`）にし、複写を配備の前後で2つ取って差を見ます。

## 内部DBのファイルの詰め直し（アプリを止めずに）

**H2 のときだけ**の運用です（`;DEFRAG_ALWAYS=TRUE`・`docker/hikari-pool.sh`・`perf/h2-shell.sh`・`-Dh2.compactThreads=1` のすべて。PostgreSQL の内部DB では使いません）。

DSL の投入と適用を重ねると、アプリが動いている間は内部DB（組み込みの H2）のファイルが伸び続けます（`docs/dsl.md` の「DSL の管理の API」の節の既知の制約）。H2 は接続が1本でも開いている間は詰め直さないため、運用の道具 `docker/hikari-pool.sh` で、HikariCP の接続プールの標準の JMX の操作（補充が済むのを待つ → 一時停止 → 接続の破棄 → 0 本を待つ → 再開）を呼びます。最後の接続が閉じたときに、接続先の `;DEFRAG_ALWAYS=TRUE` に従って H2 がファイルを詰め直します。アプリのプロセスは止めません。

詰め直しのスレッドは 1 本に固定しています（イメージの既定の JVM の引数 `-Dh2.compactThreads=1`。テストの JVM と E2E の WAR の起動も同じ）。H2 2.4.240 は閉じるときの全体の詰め直しを CPU の数 / 4 本のスレッドで並べて行いますが、2本以上では H2 の中の競合に当たり、詰め直しが中断されてファイルが縮まないことがあるためです（CPU 8 の PC の結合テスト `H2CompactionByPoolSuspensionIT` で確かめた）。CPU の数によらず同じ動きになり、今の配備（CPU 4）では元から 1 本のため速さは変わりません。`MASTERSMITH_JAVA_OPTIONS` で上書きしないでください。H2 を上げるときは、この指定が要るかを見直します。

```bash
./docker/hikari-pool.sh status     # 接続の本数と内部DB のファイルの大きさ（読み取りだけ）
./docker/hikari-pool.sh compact    # 詰め直す（前と後の大きさ・かかった時間・結果を出す）
./docker/hikari-pool.sh resume     # 再開だけ行う（下の「再開し忘れ・道具が途中で終わったとき」）
```

- **いつ行うか**: 目安として、ファイルが 300MB を超えたとき（`status` で見られます）に、利用の少ない時間に行います。自動では行いません。
- **詰め直しの間の要求**: 一時停止の間、内部DB を使う要求（ログイン・トークンの更新・監査の記録・DSL の操作・健全性の確認）は、失敗せずに再開まで待たされます（接続を借りる待ちの上限 5 秒は効きません）。道具は一時停止から再開までを既定で 45 秒以内に抑えます。
- **かかる時間の目安**: 借りている接続が無ければ、接続の破棄から詰め直しの終わりまで1秒未満〜数秒です（圧縮の効く 10MB の DSL を 21 件適用した状態で、229MiB → 15MiB が 1 秒未満。圧縮の効かない本文の約 210MB の詰め直しは、PC の上の試しで約 2 秒）。道具は詰め直しの終わりを「ファイルの大きさが 3 秒変わらない」ことで判断するため、一時停止は数秒〜十数秒になります（使い捨ての環境で 10MB の DSL を 21 回適用しプレビューを1件置いた状態から、270.4MiB → 15.2MiB、一時停止から再開まで 3.6 秒）。
- **補充の途中を避ける**: プールが接続を補充している途中（起動の直後・接続を作り直した直後）に一時停止すると、破棄の後に補充の途中の接続が1本だけ足され、0 本にならずに取りこぼすことがあります。そのため道具は、一時停止の前に、補充が済むのを待ちます。済む条件は、全体の接続の数がプールの上限（`MaximumPoolSize`）に届くか、待機の接続の数が最小の空きの数（`MinimumIdle`、上限を超えない）に届くことです（結合テスト `H2CompactionByPoolSuspensionIT` の `awaitFillSettled` と同じ）。待ちの上限は 10 秒（`--fill-wait`）で、超えたら一時停止せず、詰め直さずに 1 で終わります（まだ何も止めていないため、再開は要りません）。
- **時間の上限と打ち切り**: 重い DSL の操作やログインの最中で接続が借りられていると、返されるまで待ちます。10 秒（`--zero-wait`）の内に 0 本にならなければ、詰め直さずに再開し、「詰め直しを確かめられませんでした」と出して 1 で終わります（何も壊しません。時間を置いてやり直します）。0 本になった後、ファイルが落ち着くまでの待ちは 30 秒（`--settle-wait`）、一時停止から再開までの全体は 45 秒（`--total-limit`）が上限で、超えそうなら再開を優先します。
- **健全性**: 一時停止が約 30 秒を超えると、`/actuator/health` が 503 を返し続け、コンテナが unhealthy になります（再開で戻ります）。その間、アプリのログに `TimeBoundedDbHealthIndicator` の WARN が出ます。
- **同時に1つ**: `compact` は同じコンテナに対して同時に1つだけ動きます（この PC の上のロック）。途中で失敗・中断（Ctrl-C）しても、再開を必ず試みます。
- **再開し忘れ・道具が途中で終わったとき**: 一時停止のままだと、内部DB を使う要求がすべて待ち続け、処理のスレッドがたまります。`./docker/hikari-pool.sh resume` で再開し、だめなら `docker compose --profile targetdb-postgres restart app` で起動し直します（止めるときにも H2 が閉じて詰め直されます）。`compact` が強制終了されてロックが残ったときは、`resume` の後に表示されたロックのディレクトリを消します。JMX の操作を手で打たず、この道具を使ってください。
- **詰め直しに失敗したとき**: 再開の後に内部DB を開けないと、要求が 500 になり、コンテナが unhealthy になります。`docker compose --profile targetdb-postgres restart app` で起動し直し、それでも開けないときは「内部DBのバックアップと戻し方」で直前のバックアップを展開します（バックアップの後の記録は失われます）。大きな詰め直しの前には、アプリを止めてよい時にバックアップを取ることを勧めます（バックアップはアプリを止めます）。
- **ディスクの空き**: 詰め直しは、残す分の大きさの新しいファイルを書きます。空き（`colima ssh -- df -h /`）が残す分（最大約 210MB）より十分にあることを確かめます。
- **接続先の指定**: `MASTERSMITH_DB_URL` で H2 の接続先を上書きするときも `;DEFRAG_ALWAYS=TRUE` を付けます。付けないと、`compact` は接続を閉じて再開するだけで縮まず、道具が「大きさが減っていません」と出します。
- **記録**: 詰め直しの操作は、監査ログにもアプリのログにも残りません（HikariCP の標準の機能だけで行うため）。前と後の大きさと時間は、道具の出力で見ます。
- **しくみと公開の範囲**: 設定は `application.yaml` の `spring.datasource.hikari.register-mbeans`・`allow-pool-suspension`（どちらも true）です。道具は、JDK のイメージ（`eclipse-temurin:25.0.5_8-jdk-noble@sha256:95e350ad1eb738f943e141640f7e3d797e483359a3b33c3864255bfd1ad71f8a`）の一時のコンテナをアプリのコンテナと PID・ネットワークの名前空間を共有して同じ利用者の番号（10001）で動かし、アプリの JVM に attach して、JVM の中だけの JMX の接続（コンテナの中のループバックだけで待ち受ける）で操作します。JMX を PC やネットワークに公開しません（遠隔の接続の設定は有効にしません）。操作できるのは、この PC で Docker を使える人だけです。
- **使い捨ての環境で試す**: `--container mastersmith-perf-app-1` を付けます。`perf/dsl-timing.sh --storage --compact` は、投入と適用を重ねた後にこの道具を流し、前と後の大きさと時間を記録します（`perf/README.md`）。使い捨ての環境の H2 を読む道具 `perf/h2-shell.sh`（`perf/README.md`）も、H2 のときだけの道具です。
