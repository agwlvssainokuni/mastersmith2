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
package cherry.mastersmith.dslmanage.web;

import cherry.mastersmith.common.error.domain.BusinessException;
import cherry.mastersmith.dslmanage.domain.DslProblemTypes;
import cherry.mastersmith.dslmanage.service.DslLifecycle;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.concurrent.Semaphore;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 重い道の同時の数をアプリ全体で1つにする（NFR1.13・NFR1.14、NFR 設計の scalability-design.md 1節）。
 *
 * <p>{@link HeavyDslOperation} の印のある窓口だけ、ハンドラーの引数を作る前（本文を読む前）に許可を待たずに取る（{@code Semaphore(1)}
 * の {@code tryAcquire}）。取れなければ 503 {@code DSL_BUSY} で断り、状態を変えず、監査の出来事も出さない（指標 {@code busy} だけ）。
 * 許可は、取れたときだけ要求の属性に印を付け、応答を返し終えたとき（例外のときも）に必ず返す。
 */
@Component
public class DslHeavyOperationGate implements HandlerInterceptor {

    /** 許可を取れたことの要求の属性の名前。 */
    static final String PERMIT_ATTRIBUTE = DslHeavyOperationGate.class.getName() + ".PERMIT";

    private final Semaphore permit = new Semaphore(1);

    private final DslLifecycle lifecycle;

    /**
     * 作る。
     *
     * @param lifecycle DSL の管理の業務処理（断ったことの指標）
     */
    public DslHeavyOperationGate(DslLifecycle lifecycle) {
        this.lifecycle = lifecycle;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method) || !method.hasMethodAnnotation(HeavyDslOperation.class)) {
            return true;
        }
        if (!permit.tryAcquire()) {
            lifecycle.recordBusy(
                    method.getMethodAnnotation(HeavyDslOperation.class).value());
            throw new BusinessException(DslProblemTypes.DSL_BUSY);
        }
        request.setAttribute(PERMIT_ATTRIBUTE, Boolean.TRUE);
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (Boolean.TRUE.equals(request.getAttribute(PERMIT_ATTRIBUTE))) {
            request.removeAttribute(PERMIT_ATTRIBUTE);
            permit.release();
        }
    }
}
