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
package cherry.mastersmith.role.store;

import java.util.Objects;

/**
 * 排他と書き込みの結果（{@code reliability-design.md} 2.3）。例外の文を持たない。
 *
 * <p>{@link Done} 以外を受けたら、呼び出し元は1つ目のトランザクションに必ず巻き戻しの印を付ける（{@code RoleStoreTransactions}）。
 * 文字列にしたときは種類（と排他の種類・相手）だけを出し、値を出さない（呼び出し元の {@code role.service} はメソッドの呼び出しの追跡の
 * 対象のため。NFR 設計の読み直しの R-13、計画の D-5）。
 *
 * @param <T> 成功のときの値の型
 */
public sealed interface RoleStoreOutcome<T>
        permits RoleStoreOutcome.Done,
                RoleStoreOutcome.RoleMissing,
                RoleStoreOutcome.NameTaken,
                RoleStoreOutcome.AlreadyAssigned,
                RoleStoreOutcome.Referenced,
                RoleStoreOutcome.Busy {

    /**
     * 成功した。
     *
     * @param <T> 値の型
     * @param value 値（値の無い操作では null）
     */
    record Done<T>(T value) implements RoleStoreOutcome<T> {

        /** 種類だけを出す（値を出さない）。 */
        @Override
        public String toString() {
            return "Done";
        }
    }

    /**
     * 排他の読み取りで、ロールの行が無かった。
     *
     * @param <T> 値の型
     */
    record RoleMissing<T>() implements RoleStoreOutcome<T> {

        @Override
        public String toString() {
            return "RoleMissing";
        }
    }

    /**
     * 名前の鍵の一意の制約（{@code uk_roles_name_key}）の違反（SQLState 23505）。
     *
     * @param <T> 値の型
     */
    record NameTaken<T>() implements RoleStoreOutcome<T> {

        @Override
        public String toString() {
            return "NameTaken";
        }
    }

    /**
     * 割り当ての主キーの違反（SQLState 23505。割り当ての表を足す B5 で区分を足す）。
     *
     * @param <T> 値の型
     */
    record AlreadyAssigned<T>() implements RoleStoreOutcome<T> {

        @Override
        public String toString() {
            return "AlreadyAssigned";
        }
    }

    /**
     * 外部キーの違反（削除の 23503、追加の 23506）。読み替え先は操作ごとに決める（計画の 4.3）。
     *
     * @param <T> 値の型
     * @param who 違反の相手
     */
    record Referenced<T>(Referent who) implements RoleStoreOutcome<T> {

        /** 相手が null でないことを確かめる。 */
        public Referenced {
            Objects.requireNonNull(who, "who");
        }

        @Override
        public String toString() {
            return "Referenced[" + who + "]";
        }
    }

    /**
     * 行の排他・一意の鍵の待ちの上限切れ（BR8.3・BR8.4）。WARN は store が1回出し済み。
     *
     * @param <T> 値の型
     * @param lockKind 排他の種類（{@code ROLE_ROW}・{@code ROLE_NAME_KEY}）
     */
    record Busy<T>(String lockKind) implements RoleStoreOutcome<T> {

        /** 排他の種類が null でないことを確かめる。 */
        public Busy {
            Objects.requireNonNull(lockKind, "lockKind");
        }

        @Override
        public String toString() {
            return "Busy[" + lockKind + "]";
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
