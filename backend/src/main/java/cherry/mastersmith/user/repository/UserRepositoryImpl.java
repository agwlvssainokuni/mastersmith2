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

import cherry.mastersmith.user.domain.Preferences;
import jakarta.persistence.EntityManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.Optional;
import org.hibernate.Session;

/**
 * {@link UserRepositoryCustom} の実装（Spring Data が {@link UserRepository} に組み込む）。
 *
 * <p>呼び出し元のトランザクションの中の同じ接続で JDBC の文を流す（{@link Session#doReturningWork}）。氏名・言語・テーマ・文字の
 * 大きさは、束縛の直前に引数の組から取り出し、変数・ログ・例外に残さない（氏名は個人に関する値。NFR2.3）。例外はここで受けず、
 * Hibernate の変換に任せて外へ出す。
 */
public class UserRepositoryImpl implements UserRepositoryCustom {

    /** 更新と、更新した行の 2 列の取り出しを 1 文で行う（生成キーとして受ける）。 */
    private static final String SAVE_PREFERENCES = "UPDATE users SET display_name = ?, language = ?, theme = ?,"
            + " font_size = ?, display_time_zone = CASE WHEN ? = TRUE THEN CAST(? AS VARCHAR(64))"
            + " ELSE display_time_zone END WHERE user_id = ?";

    /** 生成キーとして受ける列の名前。 */
    private static final String[] RETURNED_COLUMNS = {"user_id", "display_time_zone"};

    private final EntityManager entityManager;

    /**
     * 作る。
     *
     * @param entityManager JPA のエンティティの管理
     */
    public UserRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Optional<UserRepository.SavedPreferences> savePreferences(
            long userId, Preferences preferences, boolean change, String zone) {
        entityManager.flush();
        return entityManager.unwrap(Session.class).doReturningWork(connection -> {
            // try-with-resources の途中で return せず、最後に 1 回だけ返す（資源を閉じる経路を 1 つにそろえる）。
            Optional<UserRepository.SavedPreferences> saved = Optional.empty();
            try (PreparedStatement statement = connection.prepareStatement(SAVE_PREFERENCES, RETURNED_COLUMNS)) {
                statement.setString(1, preferences.displayName());
                statement.setString(2, preferences.language().value());
                statement.setString(3, preferences.theme().value());
                statement.setString(4, preferences.fontSize().value());
                statement.setBoolean(5, change);
                if (zone == null) {
                    statement.setNull(6, Types.VARCHAR);
                } else {
                    statement.setString(6, zone);
                }
                statement.setLong(7, userId);
                if (statement.executeUpdate() > 0) {
                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        if (keys.next()) {
                            saved = Optional.of(
                                    new Saved(keys.getLong("user_id"), keys.getString("display_time_zone")));
                        }
                    }
                }
            }
            return saved;
        });
    }

    /** 保存した後の 2 列。 */
    private record Saved(Long userId, String displayTimeZone) implements UserRepository.SavedPreferences {

        @Override
        public Long getUserId() {
            return userId;
        }

        @Override
        public String getDisplayTimeZone() {
            return displayTimeZone;
        }
    }
}
