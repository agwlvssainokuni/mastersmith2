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

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 接続を受け付けて何も返さない受け手（応答しない SMTP の受け手の代わり。NFR9.2）。受けた接続の数を数える。
 *
 * <p>外の端末に頼らずに時間切れを確実に作れる代わりに、本物の SMTP の遅延ではない。
 */
public final class SilentSmtpServer implements AutoCloseable {

    private final ServerSocket serverSocket;

    private final AtomicInteger connections = new AtomicInteger();

    private final List<Socket> accepted = new CopyOnWriteArrayList<>();

    private final Thread thread;

    /**
     * 空いている番号で待ち受けを始める。
     *
     * @throws IOException 待ち受けを始められないとき
     */
    public SilentSmtpServer() throws IOException {
        this.serverSocket = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
        this.thread = Thread.ofVirtual().start(() -> {
            while (!serverSocket.isClosed()) {
                try {
                    accepted.add(serverSocket.accept());
                    connections.incrementAndGet();
                } catch (IOException e) {
                    // 閉じたときに受け付けが終わる。
                    return;
                }
            }
        });
    }

    /**
     * 待ち受けている番号を返す。
     *
     * @return 番号
     */
    public int port() {
        return serverSocket.getLocalPort();
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
    public void close() throws IOException {
        serverSocket.close();
        for (Socket socket : accepted) {
            socket.close();
        }
        thread.interrupt();
    }
}
