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
package cherry.mastersmith.common.testsupport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 起動したアプリ（実際の番号で待ち受ける Tomcat）へ HTTP の要求を送るテストの補助。フィルターの連鎖・ヘッダー・トレースを
 * 本番と同じ経路で確かめるために使う。
 */
public final class HttpTestClient {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private final String baseUrl;

    /**
     * 送り先の番号を指定して作る。
     *
     * @param port アプリの待ち受けの番号
     */
    public HttpTestClient(int port) {
        this.baseUrl = "http://localhost:" + port;
    }

    /**
     * 要求の組み立てを始める。
     *
     * @param path パス（問い合わせを含んでよい）
     * @return 要求の組み立て
     */
    public HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(30));
    }

    /**
     * GET の要求を送る。
     *
     * @param path パス
     * @param headers ヘッダーの名前と値の組（名前, 値, 名前, 値, ...）
     * @return 応答
     */
    public HttpResponse<String> get(String path, String... headers) {
        HttpRequest.Builder builder = request(path).GET();
        if (headers.length > 0) {
            builder.headers(headers);
        }
        return send(builder.build());
    }

    /**
     * 要求を送る。
     *
     * @param request 要求
     * @return 応答（本文は文字列）
     */
    public HttpResponse<String> send(HttpRequest request) {
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /**
     * JSON の本文を項目の対応表にする。
     *
     * @param response 応答
     * @return 項目の対応表
     */
    public static Map<String, Object> json(HttpResponse<String> response) {
        return MAPPER.readValue(response.body(), new TypeReference<Map<String, Object>>() {});
    }
}
