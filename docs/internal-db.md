# 内部DB のバックアップと詰め直し

内部DB（組み込みの H2）のバックアップと戻し方、複写を読み取りだけで数える道具、アプリを止めずにファイルを詰め直す手順をまとめた文書です。

## 内部DBのバックアップと戻し方

アプリを止めてから、ボリュームのファイルをリポジトリの外（ホームの下の `~/.mastersmith-backup/`、権限 700）に複写します。置き場を `mktemp -d` の一時ディレクトリにしません（colima の VM から見えず、複写が空振りします）。

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

配備の前後の複写の中身（監査の種類ごとの件数・利用者の数など）を確かめるときは、複写を **読み取りだけで開き**、件数だけを出す小さな道具を使います。道具はリポジトリの外（ホームの下）に置き、コミットしません。複写は上の手順で取った `.tgz` を、ホームの下の新しいディレクトリ（権限 700）に展開したものです（macOS の一時ディレクトリは colima の VM から見えません）。

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
- **接続先の指定**: `MASTERSMITH_DB_URL` で接続先を上書きするときも `;DEFRAG_ALWAYS=TRUE` を付けます。付けないと、`compact` は接続を閉じて再開するだけで縮まず、道具が「大きさが減っていません」と出します。
- **記録**: 詰め直しの操作は、監査ログにもアプリのログにも残りません（HikariCP の標準の機能だけで行うため）。前と後の大きさと時間は、道具の出力で見ます。
- **しくみと公開の範囲**: 設定は `application.yaml` の `spring.datasource.hikari.register-mbeans`・`allow-pool-suspension`（どちらも true）です。道具は、JDK のイメージ（`eclipse-temurin:25.0.5_8-jdk-noble@sha256:95e350ad1eb738f943e141640f7e3d797e483359a3b33c3864255bfd1ad71f8a`）の一時のコンテナをアプリのコンテナと PID・ネットワークの名前空間を共有して同じ利用者の番号（10001）で動かし、アプリの JVM に attach して、JVM の中だけの JMX の接続（コンテナの中のループバックだけで待ち受ける）で操作します。JMX を PC やネットワークに公開しません（遠隔の接続の設定は有効にしません）。操作できるのは、この PC で Docker を使える人だけです。
- **使い捨ての環境で試す**: `--container mastersmith-perf-app-1` を付けます。`perf/dsl-timing.sh --storage --compact` は、投入と適用を重ねた後にこの道具を流し、前と後の大きさと時間を記録します（`perf/README.md`）。
