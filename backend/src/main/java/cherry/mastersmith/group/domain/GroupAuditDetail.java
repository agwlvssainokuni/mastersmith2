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
package cherry.mastersmith.group.domain;

import java.util.Objects;

/**
 * 監査の detail に入れる中身（BR8.5・BR8.7、契約 C10。{@code entities.md} の GroupAuditDetail）。
 *
 * <p>4つの形ごとにキーを決めた型で、{@code audit} がこの型から決めたキー（{@code name}・{@code before}・{@code after}・
 * {@code groupName}・{@code members}・{@code assignedRoles}）だけの JSON を作る。グループの名前と数だけを持ち、メールアドレス・氏名・
 * パスワード・トークン・ハッシュ値は型に無い。自由な対応表や任意の文字列の連結は受けない。
 */
public sealed interface GroupAuditDetail
        permits GroupAuditDetail.Name, GroupAuditDetail.Rename, GroupAuditDetail.Membership, GroupAuditDetail.InUse {

    /**
     * 作成・削除・名前の重なり・同じ名前への変更の detail。
     *
     * @param name グループの名前
     */
    record Name(String name) implements GroupAuditDetail {

        /** 値が null でないことを確かめる。 */
        public Name {
            Objects.requireNonNull(name, "name");
        }
    }

    /**
     * 名前の変更の detail（重なりで断った変更では、after に要求の名前を入れる）。
     *
     * @param before 変える前の名前
     * @param after 変えた後（要求）の名前
     */
    record Rename(String before, String after) implements GroupAuditDetail {

        /** 値が null でないことを確かめる。 */
        public Rename {
            Objects.requireNonNull(before, "before");
            Objects.requireNonNull(after, "after");
        }
    }

    /**
     * メンバーの追加・外しの detail（対象の利用者は監査の行の {@code target_user_id} だけで示す）。
     *
     * @param groupName グループの名前
     */
    record Membership(String groupName) implements GroupAuditDetail {

        /** 値が null でないことを確かめる。 */
        public Membership {
            Objects.requireNonNull(groupName, "groupName");
        }
    }

    /**
     * 使用中の削除の拒否の detail（BR4.2 の残りの数）。
     *
     * @param name グループの名前
     * @param members 残りのメンバーの数
     * @param assignedRoles 残りのロールの割り当ての数
     */
    record InUse(String name, int members, int assignedRoles) implements GroupAuditDetail {

        /** 値が null でなく、数が負でないことを確かめる。 */
        public InUse {
            Objects.requireNonNull(name, "name");
            if (members < 0 || assignedRoles < 0) {
                throw new IllegalArgumentException("数は 0 以上です");
            }
        }
    }
}
