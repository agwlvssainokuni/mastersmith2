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

import cherry.mastersmith.auth.domain.LockView;
import cherry.mastersmith.auth.domain.LoginAttemptState;
import cherry.mastersmith.auth.repository.LoginAttemptStateRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authentication の、利用者の管理に向けた口（Intent 260930-user-admin の U3、契約 C8 の Authentication の口）。
 *
 * <p>B3 では一覧のロックの判定（{@link #lockViewsOf(Collection)}）だけを持つ。失敗回数そのものとダミーの行は外へ渡さない（BR1.7）。
 */
@Service
public class LockAdministrationService {

    private final LoginAttemptStateRepository repository;

    private final Clock clock;

    /**
     * 作る。
     *
     * @param repository ロックの状態の DB アクセス
     * @param clock 時計
     */
    public LockAdministrationService(LoginAttemptStateRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * 利用者ごとのロックの判定の結果を返す（BR1.7）。排他なしの1回の問い合わせで読み、今の時刻は注入した時計から取る。
     *
     * <p>呼び出し元の読み取りだけのトランザクションに入る（無ければ新しく始める）。行の無い利用者は {@link LockView#NONE}。空の集まりでは
     * 問い合わせない。
     *
     * @param userIds 利用者 ID（正の値）
     * @return 利用者 ID ごとの判定（渡した ID をすべて含む。順は渡した順）
     * @throws IllegalArgumentException 正でない ID があるとき（ダミーの行を読まないため）
     */
    @Transactional(readOnly = true)
    public Map<Long, LockView> lockViewsOf(Collection<Long> userIds) {
        Objects.requireNonNull(userIds, "userIds");
        if (userIds.isEmpty()) {
            return Map.of();
        }
        if (userIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("利用者 ID は正の値です");
        }
        List<Long> ids = userIds.stream().distinct().toList();
        Map<Long, LoginAttemptState> rows = repository.findBySubjectIds(ids).stream()
                .collect(Collectors.toMap(LoginAttemptState::getSubjectId, Function.identity()));
        Instant now = clock.instant();
        Map<Long, LockView> views = new LinkedHashMap<>();
        for (Long id : ids) {
            views.put(id, LockView.of(rows.get(id), now));
        }
        return views;
    }
}
