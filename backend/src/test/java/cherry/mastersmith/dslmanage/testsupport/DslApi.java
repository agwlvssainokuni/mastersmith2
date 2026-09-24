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
package cherry.mastersmith.dslmanage.testsupport;

import cherry.mastersmith.common.testsupport.HttpTestClient;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.Socket;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** DSL の管理の API を、アクセストークンつきで呼ぶテストの補助（実際の番号で待ち受けるアプリへ送る）。 */
public final class DslApi {

    /** API の根。 */
    public static final String ROOT = "/api/admin/dsl";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final HttpTestClient client;

    private final String accessToken;

    private final String language;

    private final int port;

    /**
     * 作る。
     *
     * @param port アプリの待ち受けの番号
     * @param accessToken アクセストークン（無ければ null。未認証の要求）
     */
    public DslApi(int port, String accessToken) {
        this(port, accessToken, null);
    }

    private DslApi(int port, String accessToken, String language) {
        this.client = new HttpTestClient(port);
        this.accessToken = accessToken;
        this.language = language;
        this.port = port;
    }

    /**
     * 表示言語（Accept-Language）を変えた補助を返す。
     *
     * @param acceptLanguage Accept-Language の値
     * @return 補助
     */
    public DslApi withLanguage(String acceptLanguage) {
        return new DslApi(port, accessToken, acceptLanguage);
    }

    private HttpRequest.Builder request(String path) {
        HttpRequest.Builder builder = client.request(ROOT + path);
        if (accessToken != null) {
            builder.header("Authorization", "Bearer " + accessToken);
        }
        if (language != null) {
            builder.header("Accept-Language", language);
        }
        return builder;
    }

    /**
     * GET を送る。
     *
     * @param path {@code /api/admin/dsl} からのパス
     * @return 応答
     */
    public HttpResponse<String> get(String path) {
        return client.send(request(path).GET().build());
    }

    /**
     * GET を送り、本文をバイト列で受け取る。
     *
     * @param path {@code /api/admin/dsl} からのパス
     * @return 応答
     */
    public HttpResponse<byte[]> getBytes(String path) {
        try {
            return HttpClient.newHttpClient()
                    .send(request(path).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /**
     * DSL を投入する（{@code application/yaml}）。
     *
     * @param body 本文
     * @param source 出どころ（UPLOAD・PASTE）
     * @return 応答
     */
    public HttpResponse<String> submit(byte[] body, String source) {
        return submit(body, source, "application/yaml");
    }

    /**
     * 本文の形を指定して DSL を投入する。
     *
     * @param body 本文
     * @param source 出どころ
     * @param contentType 本文の形
     * @return 応答
     */
    public HttpResponse<String> submit(byte[] body, String source, String contentType) {
        return client.send(request("/preview?source=" + source)
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build());
    }

    /**
     * 本文を送らずに、{@code Content-Length} のヘッダーだけで投入の要求を送り、応答を受け取る。本文を読まずに断ること（413・401）を
     * 確かめるために使う（アプリが本文を読もうとすれば、送られないため応答が返らず、読み取りの待ちの上限で失敗する）。
     *
     * @param contentLength 名乗る本文の大きさ
     * @param source 出どころ
     * @return 状態コードと本文
     */
    public RawResponse submitHeadersOnly(long contentLength, String source) {
        try (Socket socket = new Socket("localhost", port)) {
            socket.setSoTimeout(15_000);
            StringBuilder head = new StringBuilder()
                    .append("POST ")
                    .append(ROOT)
                    .append("/preview?source=")
                    .append(source)
                    .append(" HTTP/1.1\r\n")
                    .append("Host: localhost:")
                    .append(port)
                    .append("\r\n")
                    .append("Content-Type: application/yaml\r\n")
                    .append("Content-Length: ")
                    .append(contentLength)
                    .append("\r\n")
                    .append("Connection: close\r\n");
            if (accessToken != null) {
                head.append("Authorization: Bearer ").append(accessToken).append("\r\n");
            }
            head.append("\r\n");
            OutputStream out = socket.getOutputStream();
            out.write(head.toString().getBytes(StandardCharsets.US_ASCII));
            out.flush();
            InputStream in = socket.getInputStream();
            int status = Integer.parseInt(readLine(in).split(" ")[1]);
            int length = -1;
            boolean chunked = false;
            String line;
            while (!(line = readLine(in)).isEmpty()) {
                String lower = line.toLowerCase(Locale.ROOT);
                if (lower.startsWith("content-length:")) {
                    length = Integer.parseInt(
                            line.substring(line.indexOf(':') + 1).strip());
                } else if (lower.startsWith("transfer-encoding:") && lower.contains("chunked")) {
                    chunked = true;
                }
            }
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            if (chunked) {
                int size;
                while ((size = Integer.parseInt(readLine(in).split(";")[0].strip(), 16)) > 0) {
                    body.write(in.readNBytes(size));
                    readLine(in);
                }
            } else if (length > 0) {
                body.write(in.readNBytes(length));
            }
            return new RawResponse(status, body.toString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** CRLF で終わる1行を読む（行の終わりの記号を除く）。 */
    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1 && b != '\n') {
            if (b != '\r') {
                line.write(b);
            }
        }
        return line.toString(StandardCharsets.UTF_8);
    }

    /**
     * 本文を送らない要求の応答。
     *
     * @param status 状態コード
     * @param body 本文
     */
    public record RawResponse(int status, String body) {}

    /**
     * スキーマを読み込む。
     *
     * @return 応答
     */
    public HttpResponse<String> generate() {
        return client.send(request("/preview/generate")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build());
    }

    /**
     * プレビューを破棄する。
     *
     * @return 応答
     */
    public HttpResponse<String> discard() {
        return client.send(request("/preview").DELETE().build());
    }

    /**
     * 適用する。
     *
     * @param previewId 見たプレビューの識別
     * @return 応答
     */
    public HttpResponse<String> apply(String previewId) {
        return post("/apply", MAPPER.writeValueAsString(Map.of("previewId", previewId)));
    }

    /**
     * 履歴の版をプレビューに戻す（本文なし）。
     *
     * @param revisionId 戻す版の識別
     * @return 応答
     */
    public HttpResponse<String> restore(String revisionId) {
        return client.send(request("/history/" + revisionId + "/restore")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build());
    }

    /**
     * JSON の本文で POST を送る。
     *
     * @param path {@code /api/admin/dsl} からのパス
     * @param json 本文
     * @return 応答
     */
    public HttpResponse<String> post(String path, String json) {
        return client.send(request(path)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build());
    }

    /**
     * 投入して、置かれたプレビューの識別を返す（201 でなければ失敗させる）。
     *
     * @param body 本文
     * @return previewId
     */
    public String submitOk(byte[] body) {
        HttpResponse<String> response = submit(body, "PASTE");
        if (response.statusCode() != 201) {
            throw new IllegalStateException("投入に失敗しました: " + response.statusCode() + " " + response.body());
        }
        return (String) json(response).get("previewId");
    }

    /**
     * JSON の対応表にする。
     *
     * @param response 応答
     * @return 対応表
     */
    public static Map<String, Object> json(HttpResponse<String> response) {
        return HttpTestClient.json(response);
    }

    /**
     * JSON の並びにする。
     *
     * @param response 応答
     * @return 並び
     */
    public static List<Map<String, Object>> jsonList(HttpResponse<String> response) {
        return MAPPER.readValue(response.body(), new TypeReference<List<Map<String, Object>>>() {});
    }
}
