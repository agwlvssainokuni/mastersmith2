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

import cherry.mastersmith.common.error.domain.BusinessException;
import cherry.mastersmith.common.error.domain.LocalizedText;
import cherry.mastersmith.common.error.domain.ProblemType;
import cherry.mastersmith.common.error.domain.ProblemTypeCatalog;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * エラー応答の結合テストのための窓口（テストのソースの中だけに置く）。
 *
 * <p>{@code mastersmith.test-fixture.error-endpoints=true} を設定したテストだけで有効になり、ほかのテストの起動には入らない。
 */
@RestController
@ConditionalOnBooleanProperty("mastersmith.test-fixture.error-endpoints")
public class TestFixtureEndpoints {

    /** テスト用の業務エラーの問題の種類。 */
    public static final ProblemType TEST_CONFLICT = new ProblemType(
            "TEST_CONFLICT",
            409,
            new LocalizedText("テスト用の競合", "Test conflict"),
            new LocalizedText("テスト用の業務エラーです。", "A business error used by tests."),
            null);

    /** 本文の受け取りの確かめに使う要求。 */
    public record EchoRequest(@NotBlank String name) {}

    /** 本文の受け取りの確かめに使う応答。 */
    public record EchoResponse(String name) {}

    /**
     * JSON の本文を受け取り、そのまま返す。
     *
     * @param request 要求
     * @return 応答
     */
    @PostMapping(
            path = "/api/test-fixture/echo",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public EchoResponse echo(@Valid @RequestBody EchoRequest request) {
        return new EchoResponse(request.name());
    }

    /**
     * 数値の問い合わせの値を受け取る（JSON だけで応答する）。
     *
     * @param value 数値
     * @return 数値
     */
    @GetMapping(path = "/api/test-fixture/number", produces = MediaType.APPLICATION_JSON_VALUE)
    public int number(@RequestParam int value) {
        return value;
    }

    /**
     * 業務エラーを起こす。
     *
     * @return 返らない
     */
    @GetMapping("/api/test-fixture/business")
    public String business() {
        throw new BusinessException(TEST_CONFLICT, "表示してよい説明");
    }

    /**
     * 想定外の例外を起こす（メッセージに秘密の役の値を含める）。
     *
     * @param secret 例外のメッセージに入れる値
     * @return 返らない
     */
    @GetMapping("/api/test-fixture/boom")
    public String boom(@RequestParam String secret) {
        throw new IllegalStateException("内部の情報 " + secret);
    }

    /** テスト用の問題の種類を登録する。 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBooleanProperty("mastersmith.test-fixture.error-endpoints")
    public static class Catalog {

        /**
         * テスト用の問題の種類の一覧。
         *
         * @return 一覧
         */
        @Bean
        public ProblemTypeCatalog testFixtureCatalog() {
            return () -> List.of(TEST_CONFLICT);
        }
    }
}
