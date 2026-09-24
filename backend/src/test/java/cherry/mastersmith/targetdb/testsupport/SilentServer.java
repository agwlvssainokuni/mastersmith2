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
package cherry.mastersmith.targetdb.testsupport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 接続を受け付けて何も返さない、手元の待ち受け（応答しない対象DB の役。AC1.1.8）。外の端末には接続しない。
 *
 * <p>{@code try (SilentServer server = SilentServer.start()) { ... server.port() ... }} の形で使う。
 */
public final class SilentServer implements AutoCloseable {

    private final ServerSocket server;

    private final List<Socket> accepted = new CopyOnWriteArrayList<>();

    private SilentServer(ServerSocket server) {
        this.server = server;
        Thread.ofVirtual().name("silent-server").start(this::acceptForever);
    }

    /**
     * 手元（ループバック）の空いた番号で待ち受けを始める。
     *
     * @return 待ち受け
     */
    public static SilentServer start() {
        try {
            return new SilentServer(new ServerSocket(0, 50, InetAddress.getLoopbackAddress()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 待ち受けの番号を返す。
     *
     * @return 番号
     */
    public int port() {
        return server.getLocalPort();
    }

    private void acceptForever() {
        while (!server.isClosed()) {
            try {
                accepted.add(server.accept());
            } catch (IOException e) {
                // 閉じたときに受け付けが終わる。
                return;
            }
        }
    }

    @Override
    public void close() {
        try {
            server.close();
            for (Socket socket : accepted) {
                socket.close();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
