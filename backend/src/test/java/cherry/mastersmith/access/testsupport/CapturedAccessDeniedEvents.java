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
package cherry.mastersmith.access.testsupport;

import cherry.mastersmith.access.domain.AdminAccessDeniedEvent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.context.event.EventListener;

/**
 * アクセス拒否の出来事を集めるテスト用の受け取り（U4 の受け取り方と同じ {@code @EventListener}。要求と同じスレッドで、応答を
 * 書く前に受け取る）。
 *
 * <p>受け取るのは U3 の出来事だけにする（枠組みの出来事まで受けると、テストの下ごしらえの出来事にも当たってしまうため）。
 *
 * <p>{@link #failOnNextEvents()} を呼ぶと、以降の受け取りで例外を投げる。これで「受け取り側の失敗で 401／403 の応答が変わら
 * ない」ことを確かめる（BR3.5、NFR10.1）。
 */
public class CapturedAccessDeniedEvents {

    private final List<AdminAccessDeniedEvent> events = new CopyOnWriteArrayList<>();

    private final AtomicBoolean failing = new AtomicBoolean(false);

    /**
     * 出来事を記録する。失敗の役にしているときは、記録した後に例外を投げる。
     *
     * @param event 出来事
     */
    @EventListener
    public void onAccessDenied(AdminAccessDeniedEvent event) {
        events.add(event);
        if (failing.get()) {
            throw new IllegalStateException("テスト用の受け取りの失敗");
        }
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

    /** 以降の受け取りで例外を投げるようにする。 */
    public void failOnNextEvents() {
        failing.set(true);
    }

    /** 記録と失敗の役を元に戻す。 */
    public void clear() {
        events.clear();
        failing.set(false);
    }
}
