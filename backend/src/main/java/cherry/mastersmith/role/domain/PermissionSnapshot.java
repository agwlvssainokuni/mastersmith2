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

import cherry.mastersmith.dsl.domain.DisplayName;
import cherry.mastersmith.dsl.domain.DslModel;
import cherry.mastersmith.dsl.domain.DslSchema;
import cherry.mastersmith.dsl.domain.DslTable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 1回の要求の中だけで使う、有効な作業ロールの設定の写し（契約 C5、{@code entities.md} の PermissionSnapshot、BR5.1〜BR5.5）。
 *
 * <p>作業ロールの明示の設定を対象の名前の組をキーにした対応表で持ち、継承の関数（{@link PermissionInheritance}）で実効の値を求める。
 * 作業ロールが無い・適用済みの DSL が無い・対象が今の DSL に無いときは NONE・不可を返す（BR5.3。DSL に無い対象の設定は使わない）。
 * ほかのロールの設定を合算せず、管理者の印も使わない（BR5.5）。要求をまたいで持ち越さない（BR5.4）。
 *
 * <p>文字列にしたときは作業ロールの ID と設定の行の数だけを出す（メソッドの呼び出しの追跡の対象の {@code role.service} の戻り値に
 * なるため。計画の D-5）。
 */
public final class PermissionSnapshot {

    private final WorkRoleRef workRole;

    private final DslModel dsl;

    private final String dslHash;

    private final Map<PermissionTarget, PermissionValues> settings;

    private PermissionSnapshot(
            WorkRoleRef workRole, DslModel dsl, String dslHash, Map<PermissionTarget, PermissionValues> settings) {
        this.workRole = workRole;
        this.dsl = dsl;
        this.dslHash = dslHash;
        this.settings = Map.copyOf(settings);
    }

    /**
     * 写しを作る。
     *
     * @param workRole 有効な作業ロール（無ければ null）
     * @param dsl 適用中の DSL のモデル（無ければ null）
     * @param dslHash 適用中の DSL の識別（無ければ null。DSL があるときは必須）
     * @param settings 作業ロールの明示の設定（作業ロールが無ければ空）
     * @return 写し
     */
    public static PermissionSnapshot of(
            WorkRoleRef workRole, DslModel dsl, String dslHash, Map<PermissionTarget, PermissionValues> settings) {
        Objects.requireNonNull(settings, "settings");
        if ((dsl == null) != (dslHash == null)) {
            throw new IllegalArgumentException("DSL のモデルと識別はそろえて持ちます");
        }
        if (workRole == null && !settings.isEmpty()) {
            throw new IllegalArgumentException("作業ロールが無い写しは設定を持ちません");
        }
        return new PermissionSnapshot(workRole, dsl, dslHash, settings);
    }

    /**
     * 有効な作業ロールを返す。
     *
     * @return 作業ロール（無ければ空）
     */
    public Optional<WorkRoleRef> workRole() {
        return Optional.ofNullable(workRole);
    }

    /**
     * 読んだ時点の適用中の DSL の識別を返す。
     *
     * @return DSL の識別（無ければ空）
     */
    public Optional<String> dslHash() {
        return Optional.ofNullable(dslHash);
    }

    /**
     * 対象の実効の権限を返す（BR5.1〜BR5.3）。カラムの補助権限は、そのカラムのテーブルの実効の値。
     *
     * @param target 対象
     * @return 実効の権限（作業ロールが無い・DSL が無い・対象が今の DSL に無いときは NONE・不可）
     */
    public EffectivePermission effective(PermissionTarget target) {
        Objects.requireNonNull(target, "target");
        if (workRole == null || dsl == null || !PermissionTreeBuilder.inCurrentDsl(dsl, target)) {
            return EffectivePermission.NONE;
        }
        PermissionValues schema = settings.get(PermissionTarget.schema(target.schemaName()));
        PermissionValues table = target.tableName() == null
                ? null
                : settings.get(PermissionTarget.table(target.schemaName(), target.tableName()));
        PermissionValues column = target.columnName() == null ? null : settings.get(target);
        return PermissionInheritance.resolve(target.level(), schema, table, column)
                .effective();
    }

    /**
     * 対象の実効の主権限を返す（契約 C5 の main）。
     *
     * @param schemaName スキーマの名前
     * @param tableName テーブルの名前（スキーマの対象は null）
     * @param columnName カラムの名前（スキーマ・テーブルの対象は null）
     * @return 実効の主権限
     */
    public MainPermission main(String schemaName, String tableName, String columnName) {
        return effective(new PermissionTarget(schemaName, tableName, columnName))
                .main();
    }

    /**
     * 対象の実効の CREATE を返す（契約 C5 の create）。
     *
     * @param schemaName スキーマの名前
     * @param tableName テーブルの名前（スキーマの対象は null）
     * @return 実効の CREATE
     */
    public boolean create(String schemaName, String tableName) {
        return effective(new PermissionTarget(schemaName, tableName, null)).create();
    }

    /**
     * 対象の実効の DELETE を返す（契約 C5 の delete）。
     *
     * @param schemaName スキーマの名前
     * @param tableName テーブルの名前（スキーマの対象は null）
     * @return 実効の DELETE
     */
    public boolean delete(String schemaName, String tableName) {
        return effective(new PermissionTarget(schemaName, tableName, null)).delete();
    }

    /**
     * 自分の権限の木の1段目（今の DSL のスキーマ）を返す（FS の 2.11）。DSL が無ければ空。
     *
     * @return 節の一覧（DSL の順）
     */
    public List<EffectiveNode> schemaNodes() {
        if (dsl == null) {
            return List.of();
        }
        return dsl.schemas().stream()
                .map(schema -> node(
                        PermissionTarget.schema(schema.name()),
                        schema.label(),
                        !schema.tables().isEmpty()))
                .toList();
    }

    /**
     * スキーマの下のテーブルの節を返す（FS の 2.11）。DSL が無い・スキーマが今の DSL に無ければ空。
     *
     * @param schemaName スキーマの名前
     * @return 節の一覧（DSL の順）
     */
    public List<EffectiveNode> tableNodes(String schemaName) {
        Objects.requireNonNull(schemaName, "schemaName");
        return schema(schemaName)
                .map(schema -> schema.tables().values().stream()
                        .map(table -> node(
                                PermissionTarget.table(schemaName, table.name()),
                                table.label(),
                                !table.columns().isEmpty()))
                        .toList())
                .orElse(List.of());
    }

    /**
     * テーブルの下のカラムの節を返す（FS の 2.11）。DSL が無い・テーブルが今の DSL に無ければ空。
     *
     * @param schemaName スキーマの名前
     * @param tableName テーブルの名前
     * @return 節の一覧（DSL の順）
     */
    public List<EffectiveNode> columnNodes(String schemaName, String tableName) {
        Objects.requireNonNull(schemaName, "schemaName");
        Objects.requireNonNull(tableName, "tableName");
        return schema(schemaName)
                .map(schema -> schema.tables().get(tableName))
                .map(DslTable::columns)
                .map(columns -> columns.values().stream()
                        .map(column -> node(
                                PermissionTarget.column(schemaName, tableName, column.name()), column.label(), false))
                        .toList())
                .orElse(List.of());
    }

    private Optional<DslSchema> schema(String schemaName) {
        if (dsl == null) {
            return Optional.empty();
        }
        return dsl.schemas().stream()
                .filter(schema -> schema.name().equals(schemaName))
                .findFirst();
    }

    private EffectiveNode node(PermissionTarget target, DisplayName label, boolean children) {
        EffectivePermission permission = effective(target);
        boolean inMenu = target.level() == PermissionLevel.TABLE && permission.main() != MainPermission.NONE;
        return new EffectiveNode(target, label, permission, inMenu, children);
    }

    /**
     * 写しが持つ明示の設定の行の数を返す。
     *
     * @return 行の数
     */
    public int settingCount() {
        return settings.size();
    }

    /** 作業ロールの ID と設定の行の数だけを出す（名前・対象の名前を出さない）。 */
    @Override
    public String toString() {
        return "PermissionSnapshot[workRoleId=" + (workRole == null ? null : workRole.roleId()) + ", dsl="
                + (dsl != null) + ", rows=" + settings.size() + "]";
    }
}
