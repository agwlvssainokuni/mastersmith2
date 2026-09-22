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
package cherry.mastersmith.common.observability;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Role;

/**
 * メソッドの呼び出しの追跡（{@link TraceAspect}）の設定（{@code mastersmith.trace.*}）。値は Spring の
 * {@code CustomizableTraceInterceptor} の同じ名前の設定に渡す。既定値は application.yaml に置く。
 *
 * @param useDynamicLogger 対象のクラスの名前のロガーを使うか
 * @param hideProxyClassNames 代理のクラス（プロキシ）の名前を隠すか
 * @param logExceptionStackTrace 例外のときにスタックトレースを出すか
 * @param enterMessage 入るときの文言
 * @param exitMessage 出るときの文言
 * @param exceptionMessage 例外のときの文言
 */
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
@ConfigurationProperties("mastersmith.trace")
public record TraceProperties(
        boolean useDynamicLogger,
        boolean hideProxyClassNames,
        boolean logExceptionStackTrace,
        String enterMessage,
        String exitMessage,
        String exceptionMessage) {}
