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
import cherry.mastersmith.role.domain.RoleName;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 検証を通った権限の YAML の中身（BR9.1、{@code entities.md} の RoleTransferDocument）。ロールはファイルの順、設定は対象ごとの値（設定の
 * ある対象だけ。すべて設定なしの対象は持たない）。
 *
 * <p>{@link #toString()} はロールの数と設定の数だけを出す（NFR1.9）。
 *
 * @param roles ファイルの中のロール（名前の鍵は重ならない）
 */
public record RoleTransferDocument(List<Role> roles) {

    /** 写して持つ。 */
    public RoleTransferDocument {
        roles = List.copyOf(roles);
    }

    /**
     * ファイルの中の1つのロール。
     *
     * @param name ロールの名前（名前の規則で正規化した値）
     * @param settings 対象ごとの値（設定のある対象だけ。読み込みの順）
     */
    public record Role(RoleName name, Map<PermissionTarget, PermissionValues> settings) {

        /** 写して持ち、設定なしの値が無いことを確かめる。 */
        public Role {
            Objects.requireNonNull(name, "name");
            settings = Collections.unmodifiableMap(new LinkedHashMap<>(settings));
            if (settings.values().stream().anyMatch(PermissionValues::isEmpty)) {
                throw new IllegalArgumentException("設定なしの値は持ちません");
            }
        }

        /** 設定の数だけを出す。 */
        @Override
        public String toString() {
            return "Role[settings=" + settings.size() + "]";
        }
    }

    /**
     * 設定の数の合計を返す。
     *
     * @return 設定の数
     */
    public int settingCount() {
        return roles.stream().mapToInt(role -> role.settings().size()).sum();
    }

    /** ロールの数と設定の数だけを出す。 */
    @Override
    public String toString() {
        return "RoleTransferDocument[roles=" + roles.size() + ", settings=" + settingCount() + "]";
    }
}
