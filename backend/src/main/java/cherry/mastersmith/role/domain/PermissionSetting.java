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

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * ロールの1つの対象への明示の設定（表 {@code permission_settings}。{@code entities.md} の PermissionSetting）。少なくとも1つの値が
 * 設定されている対象だけ行を持つ（BR4.2）。対象は名前で持ち、DSL への参照は持たない（ADR-004）。
 *
 * <p>主キーはロールの ID と対象の名前の組で、下位の名前の無い階層は空の文字列（Q2: A）。作成・値の書き換え・削除は
 * {@code role.store} の {@code RoleStore} だけが行う。値の組は {@link PermissionValues} で受け渡し、すべて設定なしの値では作らない・
 * 書き換えない（行を消すのは store の役目）。
 */
@Entity(name = "PermissionSetting")
@Table(name = "permission_settings")
@IdClass(PermissionSettingId.class)
public class PermissionSetting {

    @Id
    @Column(name = "role_id")
    private Long roleId;

    @Id
    @Column(name = "schema_name", length = 256)
    private String schemaName;

    @Id
    @Column(name = "table_name", length = 256)
    private String tableName;

    @Id
    @Column(name = "column_name", length = 256)
    private String columnName;

    @Enumerated(EnumType.STRING)
    @Column(name = "main_permission", length = 8)
    private MainPermission mainPermission;

    @Column(name = "create_permission")
    private Boolean createPermission;

    @Column(name = "delete_permission")
    private Boolean deletePermission;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** JPA が使う。 */
    protected PermissionSetting() {}

    /**
     * 設定の行を作る。
     *
     * @param roleId ロールの ID
     * @param target 対象
     * @param values 値の組（少なくとも1つが設定されていること。カラムは補助権限を持たないこと）
     * @param now 今の日時（注入した時計の値）
     */
    public PermissionSetting(long roleId, PermissionTarget target, PermissionValues values, Instant now) {
        Objects.requireNonNull(target, "target");
        this.roleId = roleId;
        this.schemaName = target.schemaName();
        this.tableName = target.storedTableName();
        this.columnName = target.storedColumnName();
        assign(values, now);
    }

    /**
     * 値の組を書き換える。
     *
     * @param values 値の組（少なくとも1つが設定されていること。カラムは補助権限を持たないこと）
     * @param now 今の日時（注入した時計の値）
     */
    public void update(PermissionValues values, Instant now) {
        assign(values, now);
    }

    private void assign(PermissionValues values, Instant now) {
        Objects.requireNonNull(values, "values");
        if (values.isEmpty()) {
            throw new IllegalArgumentException("すべて設定なしの値では行を持ちません");
        }
        if (!columnName.isEmpty() && values.hasAuxiliary()) {
            throw new IllegalArgumentException("カラムは補助権限を持ちません");
        }
        this.mainPermission = values.main();
        this.createPermission = values.create();
        this.deletePermission = values.delete();
        this.updatedAt = Objects.requireNonNull(now, "now");
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

    /**
     * 値の組を返す。
     *
     * @return 値の組
     */
    public PermissionValues values() {
        return new PermissionValues(mainPermission, createPermission, deletePermission);
    }

    /**
     * 更新の日時を返す。
     *
     * @return 日時
     */
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** ID と階層だけを出す（計画の 7.2）。 */
    @Override
    public String toString() {
        return "PermissionSetting[roleId=" + roleId + ", level=" + target().level() + "]";
    }
}
