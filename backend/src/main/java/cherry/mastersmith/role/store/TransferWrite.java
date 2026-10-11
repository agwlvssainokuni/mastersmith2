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

import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.domain.PermissionValues;
import cherry.mastersmith.role.domain.Role;
import cherry.mastersmith.role.domain.RoleName;
import java.util.Map;
import java.util.Objects;

/**
 * 権限の YAML の適用で書く1つのロール（{@code reliability-design.md} 2.5、BR9.6・BR9.7）。置き換えるロールは排他したロールを、作るロールは
 * 名前を持ち、どちらも適用の後の設定をすべて持つ（置き換えは今の行をすべて消してから書く）。
 *
 * <p>{@link #toString()} は作るか置き換えるかと設定の数だけを出す（NFR1.9）。
 *
 * @param replaced 置き換えるロール（排他したもの。作るときは null）
 * @param created 作るロールの名前（置き換えるときは null）
 * @param settings 適用の後の対象ごとの値（設定のある対象だけ）
 */
public record TransferWrite(Role replaced, RoleName created, Map<PermissionTarget, PermissionValues> settings) {

    /** どちらか一方だけを持つことを確かめ、写して持つ。 */
    public TransferWrite {
        if ((replaced == null) == (created == null)) {
            throw new IllegalArgumentException("置き換えるロールか作るロールの名前のどちらか一方を持ちます");
        }
        settings = Map.copyOf(Objects.requireNonNull(settings, "settings"));
        if (settings.values().stream().anyMatch(PermissionValues::isEmpty)) {
            throw new IllegalArgumentException("設定なしの値は持ちません");
        }
    }

    /**
     * 置き換えるロールを書く。
     *
     * @param role 排他したロール
     * @param settings 適用の後の値
     * @return 書く内容
     */
    public static TransferWrite replace(Role role, Map<PermissionTarget, PermissionValues> settings) {
        return new TransferWrite(Objects.requireNonNull(role, "role"), null, settings);
    }

    /**
     * 作るロールを書く。
     *
     * @param name 名前
     * @param settings 適用の後の値
     * @return 書く内容
     */
    public static TransferWrite create(RoleName name, Map<PermissionTarget, PermissionValues> settings) {
        return new TransferWrite(null, Objects.requireNonNull(name, "name"), settings);
    }

    /** 作るか置き換えるかと設定の数だけを出す。 */
    @Override
    public String toString() {
        return "TransferWrite[" + (replaced != null ? "REPLACE" : "CREATE") + ", settings=" + settings.size() + "]";
    }
}
