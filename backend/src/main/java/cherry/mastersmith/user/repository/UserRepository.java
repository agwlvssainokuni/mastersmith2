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
import cherry.mastersmith.user.domain.ProfileUpdate;
import cherry.mastersmith.user.domain.RedactedText;
import cherry.mastersmith.user.domain.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
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
     * メールアドレスの利用者がいるかを返す（Intent 260925-user-management の U3 の計画の決定 3）。
     *
     * <p>メールアドレスは文字列にすると伏せる型で受ける（メソッドの呼び出しの追跡が引数を文字列にするため）。招待・登録の完了・初期管理者の
     * 作成の経路はこの口を使う（初期管理者は Intent 260930-user-admin の B1 で移した）。ログインの照合は {@link #findByEmail(String)} を使う。
     *
     * @param email 前後の空白を除き小文字にそろえたメールアドレス
     * @return いれば true
     */
    @Query("select count(u) > 0 from User u where u.email = :#{#email.value()}")
    boolean existsByRedactedEmail(@Param("email") RedactedText email);

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

    /**
     * 停止の状態の列だけを書き換える（Intent 260930-user-admin の U1、BR1.4・BR1.5）。
     *
     * <p>ほかの列（管理者の印・氏名と表示の設定・パスワードのハッシュ）は書かない。書く前に持続化の文脈を書き出し、書いた後に文脈を
     * 空にする（同じトランザクションで先に読み込んだエンティティから古い値を読まないため。NFR9.4）。拒否の判定はしない。
     *
     * @param userId 利用者 ID
     * @param suspended 停止するなら true、解くなら false
     * @return 書き換えた行の数（利用者がいなければ 0）
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE User u SET u.suspended = :suspended WHERE u.userId = :userId")
    int updateSuspended(@Param("userId") long userId, @Param("suspended") boolean suspended);

    /**
     * 利用者の行を、登録した日時の古い順・利用者 ID の順に読む（検索なし。Intent 260930-user-admin の U3、BR1.1・BR1.2・BR1.6）。
     *
     * <p>投影で読み、パスワードのハッシュの列を読まない。状態（管理者・停止中）で絞らない。全体の件数は {@link #count()} で数える。
     *
     * @param pageable 読み始めの位置と件数（並びは持たせない）
     * @return 行（0〜件数）
     */
    @Query("select new cherry.mastersmith.user.repository.UserAdminRow(u.userId, u.email, u.displayName, u.language,"
            + " u.adminFlag, u.suspended, u.createdAt) from User u order by u.createdAt, u.userId")
    List<UserAdminRow> findAdminRows(Pageable pageable);

    /**
     * 管理者による氏名と言語の変更で、氏名と言語の2列だけを書き換える（Intent 260930-user-admin の U3、BR5.2）。
     *
     * <p>値は検証を通った組（文字列化で氏名を伏せる型）で受け、SpEL の中でだけ取り出す（BR7.4）。テーマ・文字の大きさ・メールアドレス・
     * パスワードのハッシュ・管理者の印・停止の状態は書かない。行の排他はしない（後に確定したものが勝つ）。
     *
     * @param userId 対象の利用者 ID
     * @param profile 検証を通った氏名と言語
     * @return 書き換えた行の数（利用者がいなければ 0。同じ値でも 1）
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE User u SET u.displayName = :#{#profile.displayName()}, u.language = :#{#profile.language()}"
            + " WHERE u.userId = :userId")
    int updateProfile(@Param("userId") long userId, @Param("profile") ProfileUpdate profile);

    /**
     * 検索の条件に当たる利用者の数を返す（Intent 260930-user-admin の U3、BR1.5・BR1.6）。
     *
     * <p>メールアドレスか氏名に、検索のパターンが大文字と小文字を区別せずに当たる行を数える（{@code ilike}、エスケープの文字は
     * {@code \}）。パターンは文字列にすると伏せる型で受け、SpEL の中でだけ取り出す（BR7.4）。
     *
     * @param pattern 小文字にそろえ、{@code \}・{@code %}・{@code _} をエスケープし、前後に {@code %} を付けたパターン
     * @return 当たる行の数
     */
    @Query("select count(u) from User u where u.email ilike :#{#pattern.value()} escape '\\'"
            + " or u.displayName ilike :#{#pattern.value()} escape '\\'")
    long countBySearch(@Param("pattern") RedactedText pattern);

    /**
     * 検索の条件に当たる利用者の行を、登録した日時の古い順・利用者 ID の順に読む（Intent 260930-user-admin の U3、BR1.1・BR1.5）。
     *
     * <p>投影で読み、パスワードのハッシュの列を読まない（BR1.6）。読み始めの位置と件数は {@code pageable} で渡す（並びは問い合わせの
     * 中で決めるため、{@code pageable} に並びを持たせない）。
     *
     * @param pattern 検索のパターン（{@link #countBySearch(RedactedText)} と同じ）
     * @param pageable 読み始めの位置と件数
     * @return 行（0〜件数）
     */
    @Query("select new cherry.mastersmith.user.repository.UserAdminRow(u.userId, u.email, u.displayName, u.language,"
            + " u.adminFlag, u.suspended, u.createdAt) from User u"
            + " where u.email ilike :#{#pattern.value()} escape '\\'"
            + " or u.displayName ilike :#{#pattern.value()} escape '\\'"
            + " order by u.createdAt, u.userId")
    List<UserAdminRow> findAdminRowsBySearch(@Param("pattern") RedactedText pattern, Pageable pageable);

    /**
     * 1人の利用者の要約の投影を読む（Intent 260930-user-admin の U3、契約 C8 の lockAdminRowsInIdOrder・lockUserRow・findAdminSummary、
     * BR3.1・BR2.5）。排他しない。パスワードのハッシュの列を読まない。
     *
     * @param userId 利用者 ID
     * @return 要約の投影（いなければ空）
     */
    @Query("select new cherry.mastersmith.user.repository.UserAdminRow(u.userId, u.email, u.displayName, u.language,"
            + " u.adminFlag, u.suspended, u.createdAt) from User u where u.userId = :userId")
    Optional<UserAdminRow> findAdminRow(@Param("userId") long userId);

    /**
     * 有効な管理者（印を持ち停止していない利用者。ロック中も含む）の利用者 ID を、昇順に読む（Intent 260930-user-admin の U3、BR3.1・
     * BR3.2）。排他しない。管理者の行の排他の後に、別の問い合わせとして呼ぶ（待つ間に確定した変更を含めて数えるため）。
     *
     * @return 有効な管理者の利用者 ID（昇順）
     */
    @Query("select u.userId from User u where u.adminFlag = true and u.suspended = false order by u.userId")
    List<Long> findActiveAdminIds();

    /**
     * 管理者の印の列だけを書き換える（Intent 260930-user-admin の U3、契約 C8 の setAdmin、BR4.1・BR4.2）。
     *
     * <p>ほかの列（停止の状態・氏名と表示の設定・パスワードのハッシュ）は書かない。書く前に持続化の文脈を書き出し、書いた後に文脈を空に
     * する（停止の列の更新と同じ形）。拒否の判定はしない。
     *
     * @param userId 利用者 ID
     * @param admin 印を付けるなら true、外すなら false
     * @return 書き換えた行の数（利用者がいなければ 0）
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE User u SET u.adminFlag = :admin WHERE u.userId = :userId")
    int updateAdminFlag(@Param("userId") long userId, @Param("admin") boolean admin);
}
