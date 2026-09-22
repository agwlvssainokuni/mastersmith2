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
package cherry.mastersmith.config;

import java.io.IOException;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * 画面（ビルドした SPA）の配信（security-design 6章）。
 *
 * <p>静的なファイル（{@code classpath:/static/}）を配り、見つからない要求は、{@code /api/}・{@code /actuator/} の下でなければ
 * {@code index.html} を返す（画面の URL の直接の表示と再読み込みのため）。{@code /api/} の下で見つからないものは 404 /
 * {@code NOT_FOUND} のエラー応答になる。
 */
@Configuration(proxyBeanMethods = false)
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new SpaFallbackResourceResolver());
    }

    /** 見つからない画面の URL に {@code index.html} を返す解決の仕組み。 */
    static final class SpaFallbackResourceResolver extends PathResourceResolver {

        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            Resource resource = super.getResource(resourcePath, location);
            if (resource != null) {
                return resource;
            }
            if (isServerPath(resourcePath)) {
                return null;
            }
            Resource index = location.createRelative("index.html");
            return index.exists() && index.isReadable() ? index : null;
        }

        /**
         * 画面の URL ではなくサーバーの窓口のパスかを判定する。
         *
         * @param resourcePath 先頭の {@code /} を除いたパス
         * @return サーバーの窓口のパスなら true
         */
        static boolean isServerPath(String resourcePath) {
            return resourcePath.equals("api")
                    || resourcePath.startsWith("api/")
                    || resourcePath.equals("actuator")
                    || resourcePath.startsWith("actuator/");
        }
    }
}
