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

import org.aopalliance.aop.Advice;
import org.springframework.aop.Pointcut;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.aop.interceptor.CustomizableTraceInterceptor;
import org.springframework.aop.support.AbstractPointcutAdvisor;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Role;
import org.springframework.stereotype.Component;

/**
 * メソッドの呼び出しの追跡（NFR10.12〜NFR10.14）。処理は Spring の {@link CustomizableTraceInterceptor} に任せる。
 *
 * <p>対象は {@code cherry.mastersmith} の下の {@code web}・{@code service}・{@code domain}・{@code repository} の層の Bean の
 * メソッド。Spring Data の repository は、インターフェースに宣言されたメソッドを対象にする（実体のクラスが別のパッケージにあるため）。
 * 対象から外すもの: フィルター全般（Spring Security のフィルターを含む）、設定クラス（{@code @Configuration}）、設定値の
 * record（{@code @ConfigurationProperties}。代理で包めないため）、起動クラス、追跡の仕組み自身（このパッケージ）。
 *
 * <p>対象のクラスの名前のロガーを使い、そのロガーが TRACE のときだけ文字列を組み立てて出す（既定の INFO では出さない）。
 * 追跡のログは文字列の組み立てで出す（キーと値の決まりの例外、NFR10.14）。引数と戻り値は文字列にされるため、秘密情報を持つ型は
 * 文字列化でその項目を伏せ字にする（U2 以降も同じ）。
 *
 * <p>自動の代理の仕組みより先に作られる部品のため、基盤の役割（{@code ROLE_INFRASTRUCTURE}）として宣言する。
 */
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
@Component
public class TraceAspect extends AbstractPointcutAdvisor {

    private static final long serialVersionUID = 1L;

    /** 追跡の対象の指定（AspectJ の書き方）。 */
    static final String EXPRESSION = "(within(cherry.mastersmith..web..*)"
            + " || within(cherry.mastersmith..service..*)"
            + " || within(cherry.mastersmith..domain..*)"
            + " || within(cherry.mastersmith..repository..*)"
            + " || execution(* cherry.mastersmith..repository..*.*(..)))"
            + " && !within(jakarta.servlet.Filter+)"
            + " && !within(java.lang.Record+)"
            + " && !@within(org.springframework.boot.context.properties.ConfigurationProperties)"
            + " && !@within(org.springframework.context.annotation.Configuration)"
            + " && !within(cherry.mastersmith.MastersmithApplication)"
            + " && !within(cherry.mastersmith.common.observability..*)";

    private final transient AspectJExpressionPointcut pointcut;

    private final transient CustomizableTraceInterceptor interceptor;

    /**
     * 追跡の仕組みを作る。
     *
     * @param properties 追跡の設定
     */
    public TraceAspect(TraceProperties properties) {
        this.pointcut = new AspectJExpressionPointcut();
        this.pointcut.setExpression(EXPRESSION);
        this.interceptor = createInterceptor(properties);
    }

    /**
     * 設定を当てた追跡の処理を作る。
     *
     * @param properties 追跡の設定
     * @return 追跡の処理
     */
    static CustomizableTraceInterceptor createInterceptor(TraceProperties properties) {
        CustomizableTraceInterceptor traceInterceptor = new CustomizableTraceInterceptor();
        traceInterceptor.setUseDynamicLogger(properties.useDynamicLogger());
        traceInterceptor.setHideProxyClassNames(properties.hideProxyClassNames());
        traceInterceptor.setLogExceptionStackTrace(properties.logExceptionStackTrace());
        traceInterceptor.setEnterMessage(properties.enterMessage());
        traceInterceptor.setExitMessage(properties.exitMessage());
        traceInterceptor.setExceptionMessage(properties.exceptionMessage());
        return traceInterceptor;
    }

    @Override
    public Pointcut getPointcut() {
        return pointcut;
    }

    @Override
    public Advice getAdvice() {
        return interceptor;
    }
}
