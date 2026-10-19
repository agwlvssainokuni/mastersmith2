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
package cherry.mastersmith.businessdata.testsupport;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 手元の番号で受けて本物の対象DB へ流し、合図の後に「つないだ後に止まる」相手を作る中継（Step 19、reliability-design.md の F2、
 * unit-test-instructions.md の 6節）。外の端末には接続しない。
 *
 * <p>{@link #arm(String)} の後、送り手（アプリ）から目印の文字（ASCII）を含むまとまりが届くと、その接続の両向きの流れを止める（届いた
 * まとまりも渡さない）。平文の通信なら、目印に {@code COUNT(} を使えば、接続の確かめ（ping・空の問い合わせ）は止めずに、件数の問い合わせで
 * 止まる。通信が暗号化されているとき（MySQL・MariaDB の既定の TLS）は {@link #armAfterLargeChunks(int)} を使う。
 * {@link #release()} で止めた接続を切り、合図を外す（プールは壊れた接続を捨て、次の要求は新しい接続で読む）。
 */
public final class StallingRelay implements AutoCloseable {

    private final ServerSocket server;

    private final String targetHost;

    private final int targetPort;

    private final AtomicReference<byte[]> marker = new AtomicReference<>();

    private final List<Socket> sockets = new CopyOnWriteArrayList<>();

    private final List<Pair> stalled = new CopyOnWriteArrayList<>();

    private StallingRelay(ServerSocket server, String targetHost, int targetPort) {
        this.server = server;
        this.targetHost = targetHost;
        this.targetPort = targetPort;
        Thread.ofVirtual().name("stalling-relay").start(this::acceptForever);
    }

    /**
     * 手元（ループバック）の空いた番号で待ち受けを始める。
     *
     * @param targetHost 本物の対象DB のホスト
     * @param targetPort 本物の対象DB の番号
     * @return 中継
     */
    public static StallingRelay start(String targetHost, int targetPort) {
        try {
            return new StallingRelay(new ServerSocket(0, 50, InetAddress.getLoopbackAddress()), targetHost, targetPort);
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

    /** 大きなまとまりとみなす大きさの下限（バイト。接続の確かめの ping より大きく、問い合わせより小さい）。 */
    static final int LARGE_CHUNK = 40;

    /** 大きなまとまりで止めるときに、先に通す大きなまとまりの数（負なら使わない）。 */
    private final AtomicInteger largeChunksToPass = new AtomicInteger(-1);

    /**
     * 大きなまとまり（{@value #LARGE_CHUNK} バイト以上）を {@code pass} 個通した次の大きなまとまりで、流れを止める合図を置く。通信が
     * 暗号化されていて目印の文字が見えない DB（MySQL・MariaDB の既定の TLS）で使う。
     *
     * @param pass 先に通す大きなまとまりの数（貸し出しの始めの {@code SET time_zone} などの分。数は DB とドライバーの版で決まる）
     */
    public void armAfterLargeChunks(int pass) {
        largeChunksToPass.set(pass);
    }

    /**
     * 次に目印を含むまとまりが届いたら流れを止める合図を置く（平文の通信の DB で使う）。
     *
     * @param text 目印（ASCII）
     */
    public void arm(String text) {
        marker.set(text.getBytes(StandardCharsets.US_ASCII));
    }

    /**
     * 止めた接続の数を返す。
     *
     * @return 数
     */
    public int stalledCount() {
        return stalled.size();
    }

    /** 合図を外し、止めた接続を切る。 */
    public void release() {
        marker.set(null);
        largeChunksToPass.set(-1);
        for (Pair pair : stalled) {
            pair.close();
        }
        stalled.clear();
    }

    private void acceptForever() {
        while (!server.isClosed()) {
            try {
                Socket client = server.accept();
                Socket upstream = new Socket(targetHost, targetPort);
                sockets.add(client);
                sockets.add(upstream);
                Pair pair = new Pair(client, upstream);
                Thread.ofVirtual().start(() -> pump(pair, client, upstream, true));
                Thread.ofVirtual().start(() -> pump(pair, upstream, client, false));
            } catch (IOException e) {
                // 閉じたときに受け付けが終わる。
                return;
            }
        }
    }

    private void pump(Pair pair, Socket from, Socket to, boolean fromClient) {
        byte[] buffer = new byte[64 * 1024];
        try (InputStream in = from.getInputStream()) {
            OutputStream out = to.getOutputStream();
            int read;
            while ((read = in.read(buffer)) >= 0) {
                if (fromClient && shouldStall(buffer, read)) {
                    pair.stalled.set(true);
                    stalled.add(pair);
                }
                if (pair.stalled.get()) {
                    continue;
                }
                out.write(buffer, 0, read);
                out.flush();
            }
        } catch (IOException e) {
            // 相手が切れたら流れを終える。
        } finally {
            if (!pair.stalled.get()) {
                pair.close();
            }
        }
    }

    private boolean shouldStall(byte[] buffer, int read) {
        byte[] armed = marker.get();
        if (armed != null && contains(buffer, read, armed) && marker.compareAndSet(armed, null)) {
            return true;
        }
        if (read < LARGE_CHUNK) {
            return false;
        }
        int left = largeChunksToPass.get();
        if (left < 0) {
            return false;
        }
        if (left == 0) {
            return largeChunksToPass.compareAndSet(0, -1);
        }
        largeChunksToPass.compareAndSet(left, left - 1);
        return false;
    }

    private static boolean contains(byte[] buffer, int length, byte[] target) {
        outer:
        for (int i = 0; i + target.length <= length; i++) {
            for (int j = 0; j < target.length; j++) {
                if (buffer[i + j] != target[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public void close() {
        try {
            server.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        for (Socket socket : sockets) {
            try {
                socket.close();
            } catch (IOException e) {
                // 後片付け。閉じられなくても続ける。
            }
        }
    }

    /** 中継の1つの接続の組。 */
    private static final class Pair {

        private final Socket client;

        private final Socket upstream;

        private final AtomicBoolean stalled = new AtomicBoolean();

        private Pair(Socket client, Socket upstream) {
            this.client = client;
            this.upstream = upstream;
        }

        private void close() {
            for (Socket socket : List.of(client, upstream)) {
                try {
                    socket.close();
                } catch (IOException e) {
                    // 後片付け。閉じられなくても続ける。
                }
            }
        }
    }
}
