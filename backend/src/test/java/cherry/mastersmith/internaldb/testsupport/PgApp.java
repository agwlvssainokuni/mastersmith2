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
package cherry.mastersmith.internaldb.testsupport;

import cherry.mastersmith.MastersmithApplication;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.TestDatabase;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 内部DB が PostgreSQL のアプリを 1 つ起動して持つテストの部品（U3。H2 の部品に依存しない）。専用の空のデータベースと、実行ごとの乱数の初期管理者を持つ。
 * 共有のアプリは {@link PgSharedApp}、クラスごとのアプリは {@link #start(String...)} で作って {@link #close()} で閉じる。
 *
 * <p>接続先・利用者・パスワード・初期管理者の資格情報は、出力にも例外の文にも出さない（起動の失敗は例外の型の名前だけを伝える）。
 */
public final class PgApp implements AutoCloseable {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final InternalDbPostgresDatabase database;

    private final ConfigurableApplicationContext context;

    private final HttpTestClient client;

    private final int port;

    private final String adminEmail;

    private final String adminPassword;

    private final long startupMillis;

    private PgApp(
            InternalDbPostgresDatabase database,
            ConfigurableApplicationContext context,
            String adminEmail,
            String adminPassword,
            long startupMillis) {
        this.database = database;
        this.context = context;
        this.port = Integer.parseInt(context.getEnvironment().getProperty("local.server.port"));
        this.client = new HttpTestClient(port);
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.startupMillis = startupMillis;
    }

    /**
     * 専用のデータベースを作ってアプリを起動する。
     *
     * @param extraArgs 起動の引数に足す {@code --name=value}（待ちの設定・ログの水準など）
     * @return 起動したアプリ
     */
    public static PgApp start(String... extraArgs) {
        return startWithSources(new Class<?>[0], extraArgs);
    }

    /**
     * 追加の設定のクラス（文の数を数える {@code StatementCounter.Config} など）を足して、専用のデータベースにアプリを起動する。
     *
     * @param extraSources アプリの設定に足すクラス
     * @param extraArgs 起動の引数に足す {@code --name=value}
     * @return 起動したアプリ
     */
    public static PgApp startWithSources(Class<?>[] extraSources, String... extraArgs) {
        InternalDbPostgresDatabase database = InternalDbPostgresDatabase.create();
        byte[] bytes = new byte[6];
        RANDOM.nextBytes(bytes);
        String adminEmail = "pg-admin-" + HexFormat.of().formatHex(bytes) + "@example.com";
        String adminPassword = TestDatabase.randomSecret();
        List<String> args = new ArrayList<>(List.of(database.applicationArgs()));
        args.add("--mastersmith.auth.initial-admin.email=" + adminEmail);
        args.add("--mastersmith.auth.initial-admin.password=" + adminPassword);
        args.addAll(List.of(extraArgs));
        long start = System.nanoTime();
        ConfigurableApplicationContext started;
        try {
            Class<?>[] sources = new Class<?>[extraSources.length + 1];
            sources[0] = MastersmithApplication.class;
            System.arraycopy(extraSources, 0, sources, 1, extraSources.length);
            started = new SpringApplicationBuilder(sources).run(args.toArray(String[]::new));
        } catch (RuntimeException e) {
            database.close();
            throw new IllegalStateException("the application did not start: " + chainOf(e));
        }
        long millis = (System.nanoTime() - start) / 1_000_000L;
        return new PgApp(database, started, adminEmail, adminPassword, millis);
    }

    /**
     * メールの送信先を、指定の番号で待ち受ける SMTP の受け手にしてアプリを起動する（招待の API が要る。実在の宛先へは送らない）。
     *
     * @param smtpPort 受け手の待ち受けの番号
     * @return 起動したアプリ
     */
    public static PgApp startWithMail(int smtpPort) {
        return start(
                "--spring.mail.host=localhost",
                "--spring.mail.port=" + smtpPort,
                "--mastersmith.mail.from=noreply@example.com",
                "--spring.mail.properties.mail.smtp.connectiontimeout=1000",
                "--spring.mail.properties.mail.smtp.timeout=1000",
                "--spring.mail.properties.mail.smtp.writetimeout=1000",
                "--mastersmith.web.base-url=http://localhost:8080",
                "--mastersmith.invitation.cleanup.cron=-");
    }

    private static String chainOf(Throwable failure) {
        StringBuilder names = new StringBuilder();
        int depth = 0;
        for (Throwable current = failure; current != null && depth < 8; current = current.getCause(), depth++) {
            if (!names.isEmpty()) {
                names.append(" <- ");
            }
            names.append(current.getClass().getSimpleName());
        }
        return names.toString();
    }

    /**
     * 専用のデータベースの接続の入口（保持側の接続・観測の接続を作る）。
     *
     * @return データベース
     */
    public InternalDbPostgresDatabase database() {
        return database;
    }

    /**
     * 起動したアプリの Spring の文脈。
     *
     * @return 文脈
     */
    public ConfigurableApplicationContext context() {
        return context;
    }

    /**
     * アプリの待ち受けの番号。
     *
     * @return 番号
     */
    public int port() {
        return port;
    }

    /**
     * アプリへ HTTP の要求を送る部品。
     *
     * @return クライアント
     */
    public HttpTestClient client() {
        return client;
    }

    /**
     * 初期管理者のメールアドレス（実行ごとの乱数。予約のドメイン）。
     *
     * @return メールアドレス
     */
    public String adminEmail() {
        return adminEmail;
    }

    /**
     * 初期管理者のパスワード（実行ごとの乱数。出力に出さない）。
     *
     * @return パスワード
     */
    public String adminPassword() {
        return adminPassword;
    }

    /**
     * 起動に掛かった時間。
     *
     * @return ミリ秒
     */
    public long startupMillis() {
        return startupMillis;
    }

    @Override
    public void close() {
        try {
            context.close();
        } finally {
            database.close();
        }
    }
}
