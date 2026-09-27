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
package cherry.mastersmith.appearance.service;

import cherry.mastersmith.appearance.config.AppearanceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 見た目の設定を起動時に1回だけ解決して持つ（BR1.6、NFR6.2・NFR6.3）。
 *
 * <p>コンストラクターの中で2つの項目を {@link AppearanceResolver} で判定し、許されない値の項目ごとに WARN を1件出す（BR1.4・
 * BR2.1）。警告は項目の名前・使った既定の値・許される値の一覧だけをキーと値で出し、設定された値そのものとスタックトレースは出さない
 * （NFR9.4）。解決した値は変わらないため、ロック・同期・{@code volatile} を置かない。{@link #current()} は持った値を返すだけで、
 * 設定を読み直さず判定もしない。
 */
@Service
public class AppearanceService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AppearanceService.class);

    private static final String WARNING_MESSAGE = "見た目の設定に許されない値が指定されたため、既定の値を使います";

    private final ResolvedAppearance current;

    /**
     * 設定を判定して作る。
     *
     * @param properties 見た目の設定
     */
    public AppearanceService(AppearanceProperties properties) {
        Resolution<BrandColor> brandColor = AppearanceResolver.resolveBrandColor(properties.brandColor());
        Resolution<FontFamily> fontFamily = AppearanceResolver.resolveFontFamily(properties.fontFamily());
        if (brandColor.warned()) {
            warn(AppearanceProperties.BRAND_COLOR, brandColor.value().value(), BrandColor.allowedValues());
        }
        if (fontFamily.warned()) {
            warn(AppearanceProperties.FONT_FAMILY, fontFamily.value().value(), FontFamily.allowedValues());
        }
        this.current = new ResolvedAppearance(brandColor.value(), fontFamily.value());
    }

    /**
     * 起動時に解決した値を返す。
     *
     * @return 解決した値（呼ぶたびに同じインスタンス）
     */
    public ResolvedAppearance current() {
        return current;
    }

    private static void warn(String property, String defaultValue, String allowedValues) {
        LOGGER.atWarn()
                .setMessage(WARNING_MESSAGE)
                .addKeyValue("property", property)
                .addKeyValue("defaultValue", defaultValue)
                .addKeyValue("allowedValues", allowedValues)
                .log();
    }
}
