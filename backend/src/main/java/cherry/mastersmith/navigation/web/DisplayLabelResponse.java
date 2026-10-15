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
package cherry.mastersmith.navigation.web;

import cherry.mastersmith.dsl.domain.DisplayName;
import cherry.mastersmith.navigation.domain.DisplayLabel;

/**
 * 表示名の組の応答（{@code entities.md} の ENT-004）。DSL の値をそのまま返す（BR3.2）。
 *
 * @param ja 日本語の表示名
 * @param en 英語の表示名
 */
public record DisplayLabelResponse(String ja, String en) {

    /**
     * 表示名の組から作る。
     *
     * @param label 表示名の組
     * @return 応答
     */
    public static DisplayLabelResponse from(DisplayLabel label) {
        return new DisplayLabelResponse(label.ja(), label.en());
    }

    /**
     * DSL の表示名から作る。
     *
     * @param name DSL の表示名
     * @return 応答
     */
    public static DisplayLabelResponse from(DisplayName name) {
        return from(DisplayLabel.of(name));
    }

    /**
     * 値を伏せた文字列を返す（Intent 261015-deps-ui-carryover の FR4.1）。{@code TraceAspect} が引数と戻り値を TRACE のログに文字列で出す
     * ため、DSL の表示名を出さない。
     *
     * @return 値を伏せた文字列
     */
    @Override
    public String toString() {
        return "DisplayLabelResponse[ja=***, en=***]";
    }
}
