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
package cherry.mastersmith.invitation.testsupport;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * 要求の Host ヘッダーを自由に決めて HTTP/1.1 の POST を送るテストの補助（Java の HttpClient は Host を変えられないため、ソケットで
 * 直接送る）。招待の URL が要求の Host から組み立てられないことの確かめに使う（BR3.4、NFR1.4）。
 */
public final class RawHttp {

    private RawHttp() {}

    /**
     * 応答。
     *
     * @param status 状態コード
     * @param raw 応答の全文（ヘッダーと本文）
     */
    public record Response(int status, String raw) {}

    /**
     * POST を送る。
     *
     * @param port アプリの待ち受けの番号
     * @param path 道
     * @param host Host ヘッダーの値
     * @param accessToken アクセストークン
     * @param json 本文
     * @return 応答
     */
    public static Response post(int port, String path, String host, String accessToken, String json) {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        String head = "POST " + path + " HTTP/1.1\r\n"
                + "Host: " + host + "\r\n"
                + "Authorization: Bearer " + accessToken + "\r\n"
                + "Content-Type: application/json\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Connection: close\r\n\r\n";
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), port)) {
            socket.setSoTimeout(30_000);
            OutputStream out = socket.getOutputStream();
            out.write(head.getBytes(StandardCharsets.US_ASCII));
            out.write(body);
            out.flush();
            InputStream in = socket.getInputStream();
            String raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            int status = Integer.parseInt(raw.substring(9, 12));
            return new Response(status, raw);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
