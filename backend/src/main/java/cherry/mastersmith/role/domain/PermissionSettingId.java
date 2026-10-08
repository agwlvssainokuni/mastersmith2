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
package cherry.mastersmith.role.domain;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/** 権限の設定の主キー（ロールの ID と対象の名前の組。表に保存する空の文字列のまま持つ）。 */
public class PermissionSettingId implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long roleId;

    private String schemaName;

    private String tableName;

    private String columnName;

    /** JPA が使う。 */
    protected PermissionSettingId() {}

    /**
     * 主キーを作る。
     *
     * @param roleId ロールの ID
     * @param target 対象
     */
    public PermissionSettingId(long roleId, PermissionTarget target) {
        Objects.requireNonNull(target, "target");
        this.roleId = roleId;
        this.schemaName = target.schemaName();
        this.tableName = target.storedTableName();
        this.columnName = target.storedColumnName();
    }

    /**
     * ロールの ID を返す。
     *
     * @return ロールの ID
     */
    public Long getRoleId() {
        return roleId;
    }

    /**
     * 対象を返す。
     *
     * @return 対象
     */
    public PermissionTarget target() {
        return PermissionTarget.fromStored(schemaName, tableName, columnName);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PermissionSettingId id
                && Objects.equals(roleId, id.roleId)
                && Objects.equals(schemaName, id.schemaName)
                && Objects.equals(tableName, id.tableName)
                && Objects.equals(columnName, id.columnName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(roleId, schemaName, tableName, columnName);
    }

    /** ID と階層だけを出す。 */
    @Override
    public String toString() {
        return "PermissionSettingId[roleId=" + roleId + "]";
    }
}
