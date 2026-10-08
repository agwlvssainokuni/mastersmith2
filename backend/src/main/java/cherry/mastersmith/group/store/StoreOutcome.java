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
package cherry.mastersmith.group.store;

import java.util.Objects;

/**
 * 排他と書き込みの結果（{@code reliability-design.md} 2.2）。例外の文を持たない。
 *
 * <p>{@link Done} 以外を受けたら、呼び出し元は1つ目のトランザクションに必ず巻き戻しの印を付ける（Hibernate が違反の時点で印を付ける
 * ため、付け忘れると確定のときに {@code UnexpectedRollbackException} になる。捨ての試しの T5）。
 *
 * @param <T> 成功のときの値の型
 */
public sealed interface StoreOutcome<T>
        permits StoreOutcome.Done,
                StoreOutcome.GroupMissing,
                StoreOutcome.NameTaken,
                StoreOutcome.AlreadyMember,
                StoreOutcome.Referenced,
                StoreOutcome.Busy {

    /**
     * 成功した。
     *
     * @param <T> 値の型
     * @param value 値（値の無い操作では null）
     */
    record Done<T>(T value) implements StoreOutcome<T> {}

    /**
     * 排他の読み取りで、グループの行が無かった。
     *
     * @param <T> 値の型
     */
    record GroupMissing<T>() implements StoreOutcome<T> {}

    /**
     * 名前の鍵の一意の制約（{@code uk_groups_name_key}）の違反（SQLState 23505）。
     *
     * @param <T> 値の型
     */
    record NameTaken<T>() implements StoreOutcome<T> {}

    /**
     * メンバーの主キー（{@code pk_group_members}）の違反（SQLState 23505）。
     *
     * @param <T> 値の型
     */
    record AlreadyMember<T>() implements StoreOutcome<T> {}

    /**
     * グループへの外部キー（{@code fk_group_members_group}）の違反（削除の 23503、追加の 23506）。読み替え先は操作ごとに決める（削除なら
     * {@code GROUP_IN_USE}、メンバーの追加なら {@code GROUP_NOT_FOUND}。BR5.4、計画の D-6）。
     *
     * @param <T> 値の型
     */
    record Referenced<T>() implements StoreOutcome<T> {}

    /**
     * 行の排他・名前の鍵・メンバーの主キーの待ちの上限切れ（BR5.2）。WARN は store が1回出し済み。
     *
     * @param <T> 値の型
     * @param lockKind 排他の種類（{@code GROUP_ROW}・{@code GROUP_NAME_KEY}・{@code GROUP_MEMBER_KEY}）
     */
    record Busy<T>(String lockKind) implements StoreOutcome<T> {

        /** 排他の種類が null でないことを確かめる。 */
        public Busy {
            Objects.requireNonNull(lockKind, "lockKind");
        }
    }

    /**
     * 成功したかを返す。
     *
     * @return {@link Done} なら true
     */
    default boolean isDone() {
        return this instanceof Done<T>;
    }
}
