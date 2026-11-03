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
package cherry.mastersmith.user.domain;

import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;

/**
 * 表示のタイムゾーンの名前の門（Intent 261102-timezone-ui-polish の U2、BR2.2・BR2.4、SD1・PD5）。DB・時計・ログを使わない純粋な関数。
 *
 * <ul>
 *   <li>長さが 1〜64 文字でなければ {@link FieldErrorReason#INVALID_VALUE}
 *   <li>JDK の地域の名前の集合に、大文字小文字を区別した完全一致で当たらなければ {@link FieldErrorReason#INVALID_VALUE}
 *   <li>値は整形も前後の空白の削り直しもしない（そのまま照らし、そのまま保存する）。{@code +09:00} のような固定の時差と {@code Z} は
 *       集合に無いため通らない
 * </ul>
 *
 * <p>名前の集合はクラスの読み込み時に 1 回だけ作る不変の集合で、呼び出しごとに作り直さない（{@link ZoneId#getAvailableZoneIds()} は
 * 呼び出しのたびに別の可変の写しを返すため）。文字列でない値・{@code null} は集合に引かない。
 */
public final class TimeZoneName {

    /** 名前の長さの上限（列の長さと同じ）。 */
    public static final int MAX_LENGTH = 64;

    /** 作り置きの不変の名前の集合。 */
    private static final Set<String> NAMES = Set.copyOf(ZoneId.getAvailableZoneIds());

    private TimeZoneName() {}

    /**
     * 名前が決まりに合うかを判定する。
     *
     * @param raw 入力された名前（null でもよい）
     * @return 合わなければ理由（合えば空）
     */
    public static Optional<FieldErrorReason> check(String raw) {
        if (raw == null || raw.isEmpty() || raw.length() > MAX_LENGTH) {
            return Optional.of(FieldErrorReason.INVALID_VALUE);
        }
        return NAMES.contains(raw) ? Optional.empty() : Optional.of(FieldErrorReason.INVALID_VALUE);
    }

    /**
     * 作り置きの不変の名前の集合を返す（毎回同じ参照。変更できない）。
     *
     * @return 名前の集合
     */
    public static Set<String> names() {
        return NAMES;
    }
}
