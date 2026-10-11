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
package cherry.mastersmith.role.testsupport;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * 生のソケットで権限の YAML の確かめ・適用の POST を送る手伝い（計画の D-24、基盤の読み直しの R-01。{@code invitation/testsupport/RawHttp}
 * と同じ考え方）。要求の行・ヘッダー・本文をすべて書き切ってから応答を読む。送り手の作りで決まる形にし、HTTP の部品の癖（本文を送り
 * 終える前に応答を読むか）に合否を預けない。
 */
public final class RawTransferPost {

    private RawTransferPost() {}

    /**
     * 応答。
     *
     * @param status 状態コード
     * @param raw 応答の全体（ヘッダーと本文）
     */
    public record Response(int status, String raw) {

        /**
         * 応答の本文を返す。
         *
         * @return 本文（ヘッダーの後。分割の転送の符号は外す）
         */
        public String body() {
            int separator = raw.indexOf("\r\n\r\n");
            if (separator < 0) {
                return "";
            }
            String body = raw.substring(separator + 4);
            if (!raw.substring(0, separator)
                    .toLowerCase(java.util.Locale.ROOT)
                    .contains("transfer-encoding: chunked")) {
                return body;
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            java.io.ByteArrayOutputStream decoded = new java.io.ByteArrayOutputStream();
            int position = 0;
            while (position < bytes.length) {
                int lineEnd = position;
                while (bytes[lineEnd] != '\r') {
                    lineEnd++;
                }
                int size = Integer.parseInt(
                        new String(bytes, position, lineEnd - position, StandardCharsets.US_ASCII).trim(), 16);
                if (size == 0) {
                    break;
                }
                decoded.write(bytes, lineEnd + 2, size);
                position = lineEnd + 2 + size + 2;
            }
            return decoded.toString(StandardCharsets.UTF_8);
        }
    }

    /**
     * POST を送る（{@code Connection: close}、本文は {@code application/yaml}、長さは {@code Content-Length}）。
     *
     * @param port アプリの待ち受けの番号
     * @param path 道
     * @param accessToken アクセストークン
     * @param body 本文
     * @return 応答
     */
    public static Response post(int port, String path, String accessToken, byte[] body) {
        String head = "POST " + path + " HTTP/1.1\r\n"
                + "Host: localhost:" + port + "\r\n"
                + "Authorization: Bearer " + accessToken + "\r\n"
                + "Content-Type: application/yaml\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Connection: close\r\n\r\n";
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), port)) {
            socket.setSoTimeout(60_000);
            OutputStream out = socket.getOutputStream();
            out.write(head.getBytes(StandardCharsets.US_ASCII));
            out.write(body);
            out.flush();
            InputStream in = socket.getInputStream();
            String raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return new Response(Integer.parseInt(raw.substring(9, 12)), raw);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
