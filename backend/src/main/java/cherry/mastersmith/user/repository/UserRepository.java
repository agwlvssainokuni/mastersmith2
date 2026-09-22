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
package cherry.mastersmith.user.repository;

import cherry.mastersmith.user.domain.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 利用者の表の DB アクセス。ID で探す・保存は {@link JpaRepository} の操作を使う。 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * メールアドレスで利用者を探す。
     *
     * @param email 前後の空白を除き小文字にそろえたメールアドレス
     * @return 利用者（いなければ空）
     */
    Optional<User> findByEmail(String email);
}
