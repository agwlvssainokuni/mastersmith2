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
package cherry.mastersmith.audit.service;

import cherry.mastersmith.audit.domain.AuditEvent;
import cherry.mastersmith.audit.repository.AuditEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 監査イベントを新しいトランザクションで1件追記する（{@code nfr-design/reliability-design.md} 1章）。
 *
 * <p>確定の後の経路では、元のトランザクションは既に確定しているため、そこに加わると書き込みが確定されない。そのため、どちらの
 * 経路でも {@link Propagation#REQUIRES_NEW} で元のトランザクションに加わらずに始める。
 *
 * <p>ここでは例外を受け止めない。受け止めと ERROR のログは受け取り側（{@link AuditEventListener}）の役目である（BR3.1）。
 */
@Service
public class AuditEventRecorder {

    private final AuditEventRepository repository;

    /**
     * 作る。
     *
     * @param repository 監査イベントの保存の部品
     */
    public AuditEventRecorder(AuditEventRepository repository) {
        this.repository = repository;
    }

    /**
     * 監査イベントを1件追記する。
     *
     * @param auditEvent 監査イベント
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditEvent auditEvent) {
        repository.save(auditEvent);
    }
}
