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

import java.time.Instant;
import java.util.Objects;

/**
 * グループの詳細のメンバー1人分（{@code entities.md} の GroupMember、BR7.4・BR9.1）。
 *
 * <p>氏名とメールアドレスは個人に関する値のため、文字列にすると伏せる（メソッドの呼び出しの追跡が業務処理の戻り値を TRACE のログに
 * 文字列で出すため。{@code security-design.md} 4.2）。値を取り出すのは応答の DTO を作る web の層だけ。パスワードのハッシュ値・
 * ロックの判定の内部の値を持たない（BR9.2）。
 *
 * @param userId 利用者 ID
 * @param displayName 氏名
 * @param email メールアドレス
 * @param suspended 利用停止中か
 * @param addedAt メンバーに足した日時
 */
public record GroupMember(long userId, String displayName, String email, boolean suspended, Instant addedAt) {

    /** 値が null でないことを確かめる。 */
    public GroupMember {
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(addedAt, "addedAt");
    }

    /** 氏名とメールアドレスを伏せて文字列にする。 */
    @Override
    public String toString() {
        return "GroupMember[userId=" + userId + ", displayName=***, email=***, suspended=" + suspended + ", addedAt="
                + addedAt + "]";
    }
}
