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

import cherry.mastersmith.user.domain.PasswordHash;
import cherry.mastersmith.user.domain.Preferences;
import cherry.mastersmith.user.domain.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 利用者の表の DB アクセス。ID で探す・保存は {@link JpaRepository} の操作を使う。
 *
 * <p>氏名と表示の設定の保存とパスワードの変更は、エンティティの変更の検出（全列を書く）では書き換えず、書き換える列だけを持つ更新の
 * 問い合わせで行う（同時に起きても相手の列を古い値で上書きしないため。BR3.4、{@code reliability-design.md} 2節・3節）。問い合わせは
 * 名前つきの引数で組み立て、文字列の連結を使わない（NFR9.3）。更新の後は持続化の文脈を消し、同じトランザクションで古い値を読まない。
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * メールアドレスで利用者を探す。
     *
     * @param email 前後の空白を除き小文字にそろえたメールアドレス
     * @return 利用者（いなければ空）
     */
    Optional<User> findByEmail(String email);

    /**
     * 本人の氏名と表示の設定の4列だけを書き換える（BR3.3・BR3.4）。
     *
     * <p>値は4つの組（文字列化で氏名を伏せる型）で受け取る。メソッドの呼び出しの追跡が引数を文字列にするため、氏名（既存の利用者では
     * メールアドレスと同じ値）を {@code String} のまま渡さない。
     *
     * @param userId 本人の利用者 ID
     * @param preferences 決まりに合う4つの組
     * @return 書き換えた行の数（本人がいなければ 0）
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE User u SET u.displayName = :#{#preferences.displayName()}, u.language = :#{#preferences.language()},"
            + " u.theme = :#{#preferences.theme()}, u.fontSize = :#{#preferences.fontSize()} WHERE u.userId = :userId")
    int updatePreferences(@Param("userId") long userId, @Param("preferences") Preferences preferences);

    /**
     * 読んだときのハッシュのままなら、パスワードのハッシュだけを書き換える（BR4.3、{@code reliability-design.md} 2節の条件つきの更新）。
     *
     * <p>ハッシュは文字列化で伏せる型で受け取る（メソッドの呼び出しの追跡が引数を文字列にするため）。
     *
     * @param userId 本人の利用者 ID
     * @param readHash 照合に使った（読んだときの）ハッシュ
     * @param newHash 新しいパスワードのハッシュ
     * @return 書き換えた行の数（ハッシュが変わっていた・本人がいなければ 0）
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE User u SET u.passwordHash = :#{#newHash.value()}"
            + " WHERE u.userId = :userId AND u.passwordHash = :#{#readHash.value()}")
    int updatePasswordHashIfUnchanged(
            @Param("userId") long userId,
            @Param("readHash") PasswordHash readHash,
            @Param("newHash") PasswordHash newHash);
}
