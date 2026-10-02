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

import cherry.mastersmith.common.persistence.RowLockFailures;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.springframework.aop.interceptor.CustomizableTraceInterceptor;

/**
 * 行の排他の失敗（待ちの上限切れ・行き詰まり）の例外の連なりを追跡のログに出さない {@link CustomizableTraceInterceptor}（Intent
 * 260930-user-admin の U3、{@code infrastructure-specification.md} 6.2・10節の I-D1）。
 *
 * <p>排他の失敗の連なりの最後（H2 の {@code MVStoreException}）の文には、排他されていた行の全部の列の値が入りうる。書き込みの問い合わせ
 * （{@code @Modifying} など）の上限切れは repository の中で個別に受けないため、ここでまとめて手当てする。例外が
 * {@link RowLockFailures#isLockFailure(Throwable)} に当たるときは、
 *
 * <ul>
 *   <li>例外の文言の置き換え（{@code $[exception]}）を、例外の文字列ではなくクラスの名前だけにする
 *   <li>書き出しで元の例外を渡さず、スタックトレース（原因の連なり）を出さない
 * </ul>
 *
 * <p>ほかの例外、入る・出るときの文言は今までどおり。設定の項目と既定値（{@code mastersmith.trace.*}）は変えない。
 * {@code log-exception-stack-trace} が true のままでも、排他の失敗の連なりの文は出ない。
 */
public class LockFailureSafeTraceInterceptor extends CustomizableTraceInterceptor {

    private static final long serialVersionUID = 1L;

    @Override
    protected String replacePlaceholders(
            String message,
            MethodInvocation methodInvocation,
            Object returnValue,
            Throwable throwable,
            long invocationTime) {
        if (throwable != null && RowLockFailures.isLockFailure(throwable)) {
            // 例外の文字列（文と原因）を使わず、クラスの名前だけを置く。クラスの名前に置き換えの印（$[...]）は現れない。
            String withClassName =
                    message.replace(PLACEHOLDER_EXCEPTION, throwable.getClass().getName());
            return super.replacePlaceholders(withClassName, methodInvocation, returnValue, null, invocationTime);
        }
        return super.replacePlaceholders(message, methodInvocation, returnValue, throwable, invocationTime);
    }

    @Override
    protected void writeToLog(Log logger, String message, Throwable ex) {
        if (ex != null && RowLockFailures.isLockFailure(ex)) {
            super.writeToLog(logger, message, null);
            return;
        }
        super.writeToLog(logger, message, ex);
    }
}
