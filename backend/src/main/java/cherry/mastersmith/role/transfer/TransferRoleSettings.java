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
package cherry.mastersmith.role.transfer;

import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.domain.PermissionValues;
import java.util.Map;
import java.util.Objects;

/**
 * 書き出す1つのロールの名前と設定（BR9.14）。
 *
 * <p>{@link #toString()} は設定の数だけを出し、名前と値を出さない（NFR1.9）。
 *
 * @param name ロールの名前
 * @param settings 対象ごとの値（設定のある対象だけ。どの順でもよい）
 */
public record TransferRoleSettings(String name, Map<PermissionTarget, PermissionValues> settings) {

    /** 写して持つ。 */
    public TransferRoleSettings {
        Objects.requireNonNull(name, "name");
        settings = Map.copyOf(settings);
    }

    /** 設定の数だけを出す。 */
    @Override
    public String toString() {
        return "TransferRoleSettings[settings=" + settings.size() + "]";
    }
}
