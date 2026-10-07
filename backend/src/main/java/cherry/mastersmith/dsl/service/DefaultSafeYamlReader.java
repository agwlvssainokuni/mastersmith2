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
package cherry.mastersmith.dsl.service;

import cherry.mastersmith.dsl.domain.DslError;
import cherry.mastersmith.dsl.parse.SafeYamlParser;
import cherry.mastersmith.dsl.parse.YamlLimits;
import cherry.mastersmith.dsl.parse.YamlParseResult;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * {@link SafeYamlReader} の実装（U2 の機能設計の 3.8節・BR6.1〜BR6.5、NFR 設計の 4.1）。
 *
 * <p>DSL の読み込みと同じ部品（{@link SafeYamlParser}）を、呼ぶ側の上限で使う。部品の拒否（DSL の誤りの種類）は区分に写し、
 * 文言の鍵と埋める値は捨てる。
 */
@Service
public class DefaultSafeYamlReader implements SafeYamlReader {

    private final SafeYamlParser parser;

    /**
     * 作る。
     *
     * @param parser YAML の安全な読み込み
     */
    public DefaultSafeYamlReader(SafeYamlParser parser) {
        this.parser = parser;
    }

    @Override
    public SafeYamlResult read(byte[] yamlBytes, SafeYamlLimits limits) {
        Objects.requireNonNull(yamlBytes, "yamlBytes は必須です");
        Objects.requireNonNull(limits, "limits は必須です");
        if (yamlBytes.length > limits.maxBytes()) {
            return new SafeYamlResult.Rejected(SafeYamlRejectionKind.TOO_LARGE, null, null, null);
        }
        YamlLimits yamlLimits =
                new YamlLimits(limits.maxBytes(), limits.maxDepth(), limits.maxAliases(), limits.maxExpandedNodes());
        return switch (parser.parse(yamlBytes, yamlLimits)) {
            case YamlParseResult.Parsed parsed ->
                new SafeYamlResult.Parsed(
                        parsed.document().json(),
                        new YamlPositions(parsed.document().positions()));
            case YamlParseResult.Rejected rejected -> reject(rejected.error());
        };
    }

    private static SafeYamlResult.Rejected reject(DslError error) {
        SafeYamlRejectionKind kind =
                switch (error.kind()) {
                    case SIZE_LIMIT -> SafeYamlRejectionKind.TOO_LARGE;
                    case DEPTH_LIMIT -> SafeYamlRejectionKind.TOO_DEEP;
                    case ALIAS_LIMIT -> SafeYamlRejectionKind.TOO_MANY_ALIASES;
                    case FORBIDDEN_TAG -> SafeYamlRejectionKind.TAG_NOT_ALLOWED;
                    case DUPLICATE_KEY -> SafeYamlRejectionKind.DUPLICATE_KEY;
                    case SYNTAX -> SafeYamlRejectionKind.SYNTAX;
                    case UNSUPPORTED_VERSION, SEMANTIC ->
                        throw new IllegalStateException("読み込みの段に無い誤りの種類です: " + error.kind());
                };
        return new SafeYamlResult.Rejected(kind, error.line(), error.column(), error.path());
    }
}
