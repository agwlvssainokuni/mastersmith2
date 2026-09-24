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
package cherry.mastersmith.dsl.testsupport;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

/** 要求の数を数えるだけの HTTP の受け口（{@code 127.0.0.1} の空いた番号）。外部の {@code $ref} を取りに行かないことを確かめる。 */
public final class CountingHttpServer implements AutoCloseable {

    private final HttpServer server;

    private final AtomicInteger requests = new AtomicInteger();

    /** 受け口を立てる。 */
    public CountingHttpServer() {
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            byte[] body = "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    /**
     * 受け口の URL を返す。
     *
     * @param path パス（先頭の {@code /} を含む）
     * @return URL
     */
    public String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    /**
     * 受けた要求の数を返す。
     *
     * @return 要求の数
     */
    public int requests() {
        return requests.get();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
