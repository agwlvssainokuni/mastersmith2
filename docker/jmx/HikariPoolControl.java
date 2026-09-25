/*
 * Copyright 2026 agwlvssainokuni
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.sun.tools.attach.VirtualMachine;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

/*
 * アプリの内部DB（組み込みの H2）のファイルを、アプリを止めずに詰め直す運用の道具（Intent 260925-storage-memory-fixes の FR1）。
 * 入口は docker/hikari-pool.sh で、このファイルはそこから JDK のイメージの一時のコンテナの中で動く（依存なしの1ファイル）。
 *
 * アプリのコンテナと PID・ネットワークの名前空間を共有し、同じ利用者の番号で、アプリの JVM（PID 1）に attach する。
 * JVM の中だけの JMX の接続（startLocalManagementAgent。ループバックだけで待ち受け、外には公開しない）で、HikariCP の
 * プールの MBean（com.zaxxer.hikari:type=Pool (<プールの名前>)）の標準の操作と属性だけを使う。アプリのコードは使わない。
 *
 * 副コマンド:
 *   status   接続の本数と内部DB のファイルの大きさを出す
 *   compact  一時停止 → 接続の破棄 → 0 本を待つ → ファイルが落ち着くのを待つ → 再開。最後の接続が閉じたときに、接続先の
 *            DEFRAG_ALWAYS=TRUE に従って H2 が詰め直す。どこで失敗・中断しても、再開を必ず試みる
 *   resume   再開だけ行う（再開し忘れ・道具の途中の終わりからの戻し）
 *
 * 終わりの値: 0 成功、1 詰め直していない・終わりを確かめられない（再開は済み）、2 使い方・前提の誤り、3 再開に失敗した。
 * 出力は接続の本数・ファイルの大きさ・時間だけで、秘密情報は含まない。
 */
public final class HikariPoolControl {

    private static final String DEFAULT_POOL = "mastersmith-db";

    private static final String DEFAULT_DB_FILE = "/proc/1/root/app/data/mastersmith.mv.db";

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private static final long POLL_MILLIS = 100;

    private static final int EXIT_OK = 0;

    private static final int EXIT_NOT_COMPACTED = 1;

    private static final int EXIT_USAGE = 2;

    private static final int EXIT_RESUME_FAILED = 3;

    private HikariPoolControl() {}

    /** 引数から読む設定。 */
    private record Options(
            String command,
            String pool,
            Path dbFile,
            long zeroWaitMillis,
            long settleWaitMillis,
            long totalLimitMillis,
            long stableMillis) {

        static Options parse(String[] args) {
            if (args.length == 0) {
                throw new IllegalArgumentException("副コマンド（status・compact・resume）を指定してください");
            }
            String command = args[0];
            String pool = DEFAULT_POOL;
            Path dbFile = Path.of(DEFAULT_DB_FILE);
            long zeroWait = 10_000;
            long settleWait = 30_000;
            long totalLimit = 45_000;
            long stable = 3_000;
            for (int i = 1; i < args.length; i++) {
                String name = args[i];
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException(name + " の値がありません");
                }
                String value = args[++i];
                switch (name) {
                    case "--pool" -> pool = value;
                    case "--db-file" -> dbFile = Path.of(value);
                    case "--zero-wait" -> zeroWait = seconds(name, value);
                    case "--settle-wait" -> settleWait = seconds(name, value);
                    case "--total-limit" -> totalLimit = seconds(name, value);
                    case "--stable" -> stable = seconds(name, value);
                    default -> throw new IllegalArgumentException("知らない指定です: " + name);
                }
            }
            if (!pool.matches("[A-Za-z0-9._-]+")) {
                throw new IllegalArgumentException("--pool は英数字と . _ - だけで指定してください");
            }
            if (totalLimit < zeroWait) {
                throw new IllegalArgumentException("--total-limit は --zero-wait 以上にしてください");
            }
            return new Options(command, pool, dbFile, zeroWait, settleWait, totalLimit, stable);
        }

        private static long seconds(String name, String value) {
            try {
                long seconds = Long.parseLong(value);
                if (seconds < 1 || seconds > 600) {
                    throw new IllegalArgumentException(name + " は 1〜600 の秒数で指定してください");
                }
                return seconds * 1000;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(name + " は 1〜600 の秒数で指定してください", e);
            }
        }
    }

    public static void main(String[] args) {
        Options options;
        try {
            options = Options.parse(args);
        } catch (IllegalArgumentException e) {
            log("使い方の誤り: " + e.getMessage());
            System.exit(EXIT_USAGE);
            return;
        }
        System.exit(run(options));
    }

    private static int run(Options options) {
        MBeanServerConnection connection;
        JMXConnector connector;
        ObjectName poolName;
        try {
            connector = connect();
            connection = connector.getMBeanServerConnection();
            poolName = new ObjectName("com.zaxxer.hikari:type=Pool (" + options.pool() + ")");
            if (!connection.isRegistered(poolName)) {
                log("プールの MBean（" + poolName + "）がありません。application.yaml の register-mbeans と pool-name を確かめてください");
                close(connector);
                return EXIT_USAGE;
            }
        } catch (Exception e) {
            log("アプリの JVM に接続できません（" + e.getClass().getSimpleName() + "）。対象のコンテナが動いているかを確かめてください");
            return EXIT_USAGE;
        }
        Pool pool = new Pool(connection, poolName, options.dbFile());
        try {
            return switch (options.command()) {
                case "status" -> {
                    log("状態: " + pool.describe());
                    yield EXIT_OK;
                }
                case "resume" -> resumeOnly(pool);
                case "compact" -> compact(pool, options);
                default -> {
                    log("知らない副コマンドです: " + options.command());
                    yield EXIT_USAGE;
                }
            };
        } finally {
            close(connector);
        }
    }

    private static int resumeOnly(Pool pool) {
        try {
            pool.invoke("resumePool");
            log("再開しました: " + pool.describe());
            return EXIT_OK;
        } catch (Exception e) {
            log("再開に失敗しました（" + e.getClass().getSimpleName() + "）。docker compose restart app で起動し直してください");
            return EXIT_RESUME_FAILED;
        }
    }

    private static int compact(Pool pool, Options options) {
        long before = pool.fileSize();
        log("始める前: " + pool.describe());
        AtomicBoolean suspended = new AtomicBoolean(false);
        // 中断（Ctrl-C・docker stop の合図）で終わるときも、一時停止のままにしない。
        Thread hook = new Thread(() -> {
            if (suspended.get()) {
                log("中断されたため再開します");
                resumeQuietly(pool, suspended);
            }
        });
        Runtime.getRuntime().addShutdownHook(hook);
        long start = System.nanoTime();
        int result;
        try {
            suspended.set(true);
            pool.invoke("suspendPool");
            log("一時停止しました（この間、内部DB を使う要求は再開まで待ちます）");
            pool.invoke("softEvictConnections");
            log("接続の破棄を求めました: " + pool.describe());
            result = waitAndSettle(pool, options, start, before);
        } catch (Exception e) {
            log("途中で失敗しました（" + e.getClass().getSimpleName() + "）。再開します");
            result = EXIT_NOT_COMPACTED;
        }
        if (!resumeQuietly(pool, suspended)) {
            return EXIT_RESUME_FAILED;
        }
        try {
            Runtime.getRuntime().removeShutdownHook(hook);
        } catch (IllegalStateException e) {
            // 中断で JVM が終わりかけている（フックはすでに動いている）。再開は済んでいるため、そのまま結果を出す。
            log("終わりの途中です（中断の合図を受けました）");
        }
        long after = pool.fileSize();
        log(String.format(
                Locale.ROOT,
                "結果: %s、大きさ %s → %s、一時停止から再開まで %d ms",
                result == EXIT_OK ? "詰め直しました" : "詰め直しを確かめられませんでした",
                mib(before),
                mib(after),
                elapsedMillis(start)));
        if (result == EXIT_OK && after >= before) {
            log("大きさが減っていません。接続先（MASTERSMITH_DB_URL）に ;DEFRAG_ALWAYS=TRUE があるかを確かめてください");
        }
        return result;
    }

    /** 0 本を待ち、ファイルが落ち着くのを待つ。上限を超えたら打ち切る（再開は呼び出し元が行う）。 */
    private static int waitAndSettle(Pool pool, Options options, long start, long before) throws Exception {
        long zeroDeadline = start + options.zeroWaitMillis() * 1_000_000;
        long totalDeadline = start + options.totalLimitMillis() * 1_000_000;
        while (pool.intAttribute("TotalConnections") > 0) {
            if (System.nanoTime() >= zeroDeadline) {
                log(String.format(
                        Locale.ROOT,
                        "借りている接続が %d ms の内に返りませんでした（%s）。詰め直さずに再開します",
                        options.zeroWaitMillis(),
                        pool.describe()));
                return EXIT_NOT_COMPACTED;
            }
            Thread.sleep(POLL_MILLIS);
        }
        log("接続が 0 本になりました（" + elapsedMillis(start) + " ms）。H2 が閉じて詰め直すのを待ちます");
        long settleDeadline = Math.min(System.nanoTime() + options.settleWaitMillis() * 1_000_000, totalDeadline);
        long lastSize = -1;
        long stableSince = System.nanoTime();
        while (true) {
            long size = pool.fileSize();
            boolean working = pool.hasTemporaryFiles();
            long now = System.nanoTime();
            if (size != lastSize || working) {
                lastSize = size;
                stableSince = now;
            } else if (now - stableSince >= options.stableMillis() * 1_000_000) {
                log("ファイルが落ち着きました: " + mib(size) + "（" + elapsedMillis(start) + " ms）");
                return EXIT_OK;
            }
            if (now >= settleDeadline) {
                log(String.format(
                        Locale.ROOT,
                        "ファイルが落ち着く前に待ちの上限に達しました（%s → %s）。再開を優先します",
                        mib(before),
                        mib(size)));
                return EXIT_NOT_COMPACTED;
            }
            Thread.sleep(POLL_MILLIS);
        }
    }

    private static boolean resumeQuietly(Pool pool, AtomicBoolean suspended) {
        if (!suspended.get()) {
            return true;
        }
        try {
            pool.invoke("resumePool");
            suspended.set(false);
            log("再開しました: " + pool.describe());
            return true;
        } catch (Exception e) {
            log("再開に失敗しました（" + e.getClass().getSimpleName() + "）。まず ./docker/hikari-pool.sh resume を試し、"
                    + "だめなら docker compose restart app で起動し直してください");
            return false;
        }
    }

    private static JMXConnector connect() throws Exception {
        VirtualMachine vm = VirtualMachine.attach("1");
        String address;
        try {
            address = vm.startLocalManagementAgent();
        } finally {
            vm.detach();
        }
        return JMXConnectorFactory.connect(new JMXServiceURL(address));
    }

    private static void close(JMXConnector connector) {
        try {
            connector.close();
        } catch (IOException e) {
            log("JMX の接続を閉じられませんでした（" + e.getClass().getSimpleName() + "）");
        }
    }

    private static long elapsedMillis(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }

    private static String mib(long bytes) {
        return bytes < 0 ? "（読めない）" : String.format(Locale.ROOT, "%.1fMiB", bytes / 1048576.0);
    }

    private static void log(String message) {
        System.out.println("[" + LocalTime.now().format(TIME) + "] " + message);
        System.out.flush();
    }

    /** プールの MBean とファイルの読み取り。 */
    private record Pool(MBeanServerConnection connection, ObjectName name, Path dbFile) {

        void invoke(String operation) throws Exception {
            connection.invoke(name, operation, null, null);
        }

        int intAttribute(String attribute) throws Exception {
            return (Integer) connection.getAttribute(name, attribute);
        }

        long fileSize() {
            try {
                return Files.size(dbFile);
            } catch (IOException e) {
                return -1;
            }
        }

        /** H2 が詰め直しの途中に作る一時のファイル（*.tempFile）があるか。 */
        boolean hasTemporaryFiles() {
            Path dir = dbFile.getParent();
            if (dir == null) {
                return false;
            }
            try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.tempFile")) {
                return files.iterator().hasNext();
            } catch (IOException e) {
                return false;
            }
        }

        String describe() {
            try {
                return String.format(
                        Locale.ROOT,
                        "使用中 %d・待機 %d・合計 %d・借りる待ち %d、ファイル %s",
                        intAttribute("ActiveConnections"),
                        intAttribute("IdleConnections"),
                        intAttribute("TotalConnections"),
                        intAttribute("ThreadsAwaitingConnection"),
                        mib(fileSize()));
            } catch (Exception e) {
                return "（状態を読めない: " + e.getClass().getSimpleName() + "）、ファイル " + mib(fileSize());
            }
        }
    }
}
