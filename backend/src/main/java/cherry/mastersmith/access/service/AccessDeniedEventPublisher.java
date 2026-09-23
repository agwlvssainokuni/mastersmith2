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
package cherry.mastersmith.access.service;

import cherry.mastersmith.access.domain.AdminAccessDeniedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * アクセス拒否の出来事をアプリの中に知らせる（BR3.5、ADR-004）。受け取り側（U4）を知らない。
 *
 * <p>受け取りは要求と同じスレッドで、応答を書く前に行われる。受け取り側の失敗は U4 の中で受け止める決まりだが、念のためここでも
 * 例外を捕まえて WARN を1回出し、呼び出し元に伝えない（受け止めの二重の備え。{@code reliability-design.md} 1章）。例外を
 * 黙って捨てることはしない。WARN には例外の型だけを出し、例外のメッセージとスタックトレースは出さない
 * （{@code observability-design.md} 2章）。
 */
@Service
public class AccessDeniedEventPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccessDeniedEventPublisher.class);

    private final ApplicationEventPublisher eventPublisher;

    /**
     * 作る。
     *
     * @param eventPublisher アプリ内の出来事の通知
     */
    public AccessDeniedEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * 出来事を知らせる。受け取り側で例外が起きても、呼び出し元には伝えない。
     *
     * @param event アクセス拒否の出来事
     */
    public void publish(AdminAccessDeniedEvent event) {
        try {
            eventPublisher.publishEvent(event);
        } catch (RuntimeException e) {
            LOGGER.atWarn().addKeyValue("exceptionType", e.getClass().getName()).log("アクセス拒否の出来事の受け取りで例外が戻りました");
        }
    }
}
