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
package cherry.mastersmith.auth.testsupport;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * トランザクションの確定の後に知らされた出来事を集めるテスト用の受け取り（U4 の受け取り方と同じ
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)}）。
 *
 * <p>テストの設定で Bean として置き、{@link #ofType(Class)} で認証の出来事だけを取り出す。
 */
public class CapturedAuthenticationEvents {

    private final List<Object> events = new CopyOnWriteArrayList<>();

    /**
     * 確定の後の出来事を記録する。
     *
     * @param event 出来事
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEvent(Object event) {
        events.add(event);
    }

    /**
     * 指定した型の出来事を、知らされた順に返す。
     *
     * @param <T> 出来事の型
     * @param type 出来事の型
     * @return 出来事の一覧
     */
    public <T> List<T> ofType(Class<T> type) {
        return events.stream().filter(type::isInstance).map(type::cast).toList();
    }

    /** 記録を消す。 */
    public void clear() {
        events.clear();
    }
}
