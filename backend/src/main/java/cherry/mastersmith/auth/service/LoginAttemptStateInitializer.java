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
package cherry.mastersmith.auth.service;

import cherry.mastersmith.auth.repository.LoginAttemptStateRepository;
import cherry.mastersmith.user.service.UserCreatedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 利用者の作成の知らせを、作成と同じトランザクションで受けて、ロックの状態の行（失敗回数 0）を作る（計画の C8、
 * scalability-design 1章）。{@code user} は {@code auth} を知らない。
 */
@Component
public class LoginAttemptStateInitializer {

    private final LoginAttemptStateRepository repository;

    /**
     * 処理を作る。
     *
     * @param repository ロックの状態の DB アクセス
     */
    public LoginAttemptStateInitializer(LoginAttemptStateRepository repository) {
        this.repository = repository;
    }

    /**
     * 作った利用者のロックの状態の行を作る。
     *
     * @param event 利用者の作成の知らせ
     */
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onUserCreated(UserCreatedEvent event) {
        repository.createIfAbsent(event.userId());
    }
}
