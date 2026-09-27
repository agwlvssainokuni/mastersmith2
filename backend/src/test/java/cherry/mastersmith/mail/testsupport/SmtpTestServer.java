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
package cherry.mastersmith.mail.testsupport;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLContext;
import org.subethamail.smtp.auth.EasyAuthenticationHandlerFactory;
import org.subethamail.smtp.auth.LoginFailedException;
import org.subethamail.smtp.server.SMTPServer;
import org.subethamail.smtp.server.Session;
import org.subethamail.smtp.server.SessionHandler;
import org.subethamail.wiser.Wiser;
import org.subethamail.wiser.WiserMessage;

/**
 * JVM の中で起動するテスト用の SMTP の受け手（SubEtha SMTP 7.2.2 の Wiser、NFR9.1）。コンテナは使わない。
 *
 * <p>空いている番号で {@code 127.0.0.1} に起動する。STARTTLS（受け付ける・必須にする・受け付けない）、SMTPS、認証、宛先の拒否を
 * 作れ、受けた接続の数を数える。
 */
public final class SmtpTestServer implements AutoCloseable {

    /** 暗号化の受け付け方。 */
    public enum Tls {
        /** STARTTLS を受け付けない（広告しない）。 */
        NONE,
        /** STARTTLS を必須にする。 */
        STARTTLS_REQUIRED,
        /** 接続の始めから TLS。 */
        SMTPS
    }

    private final Wiser wiser;

    private final AtomicInteger connections = new AtomicInteger();

    private SmtpTestServer(Builder builder) {
        SMTPServer.Builder server = SMTPServer.port(0)
                .bindAddress(loopback())
                .hostName("localhost")
                .sessionHandler(new CountingSessionHandler(connections));
        switch (builder.tls) {
            case NONE -> {}
            case STARTTLS_REQUIRED -> server.requireTLS().startTlsSocketFactory(builder.sslContext);
            case SMTPS -> server.serverSocketFactory(builder.sslContext);
        }
        if (builder.username != null) {
            String username = builder.username;
            String password = builder.password;
            server.requireAuth().authenticationHandlerFactory(new EasyAuthenticationHandlerFactory((u, p, context) -> {
                if (!username.equals(u) || !password.equals(p)) {
                    throw new LoginFailedException();
                }
            }));
        }
        boolean rejectRecipients = builder.rejectRecipients;
        this.wiser = Wiser.accepter((from, recipient) -> !rejectRecipients).server(server);
        wiser.start();
    }

    private static InetAddress loopback() {
        try {
            return InetAddress.getByName("127.0.0.1");
        } catch (UnknownHostException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 受け手の作り方を始める。
     *
     * @return 作り方
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 待ち受けている番号を返す。
     *
     * @return 番号
     */
    public int port() {
        return wiser.getServer().getPortAllocated();
    }

    /**
     * 受けたメールを返す。
     *
     * @return 受けたメール
     */
    public List<WiserMessage> messages() {
        return List.copyOf(wiser.getMessages());
    }

    /**
     * 受けた接続の数を返す。
     *
     * @return 接続の数
     */
    public int connections() {
        return connections.get();
    }

    @Override
    public void close() {
        wiser.stop();
    }

    /** 受けた接続を数える（受け付けはすべて許す）。 */
    private record CountingSessionHandler(AtomicInteger counter) implements SessionHandler {

        @Override
        public SessionAcceptance accept(Session session) {
            counter.incrementAndGet();
            return SessionAcceptance.success();
        }

        @Override
        public void onSessionEnd(Session session) {
            // 数えるだけのため何もしない。
        }
    }

    /** 受け手の作り方。 */
    public static final class Builder {

        private Tls tls = Tls.NONE;

        private SSLContext sslContext;

        private String username;

        private String password;

        private boolean rejectRecipients;

        private Builder() {}

        /**
         * 暗号化の受け付け方を決める。
         *
         * @param mode 受け付け方
         * @param context 受け手の TLS の文脈（NONE のときは使わない）
         * @return この作り方
         */
        public Builder tls(Tls mode, SSLContext context) {
            this.tls = mode;
            this.sslContext = context;
            return this;
        }

        /**
         * 認証を必須にする。
         *
         * @param user 受け付けるユーザー名
         * @param pass 受け付けるパスワード
         * @return この作り方
         */
        public Builder requireAuth(String user, String pass) {
            this.username = user;
            this.password = pass;
            return this;
        }

        /**
         * すべての宛先を拒否する。
         *
         * @return この作り方
         */
        public Builder rejectRecipients() {
            this.rejectRecipients = true;
            return this;
        }

        /**
         * 起動する。
         *
         * @return 起動した受け手
         */
        public SmtpTestServer start() {
            return new SmtpTestServer(this);
        }
    }
}
