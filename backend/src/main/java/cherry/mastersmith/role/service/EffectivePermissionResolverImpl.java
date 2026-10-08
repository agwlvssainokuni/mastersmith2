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
package cherry.mastersmith.role.service;

import cherry.mastersmith.dsl.domain.ActiveDsl;
import cherry.mastersmith.dsl.domain.DslModel;
import cherry.mastersmith.dsl.service.ActiveDslModelProvider;
import cherry.mastersmith.role.domain.EffectivePermission;
import cherry.mastersmith.role.domain.PermissionInheritance;
import cherry.mastersmith.role.domain.PermissionSnapshot;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.domain.PermissionTreeBuilder;
import cherry.mastersmith.role.domain.PermissionValues;
import cherry.mastersmith.role.domain.WorkRoleRef;
import cherry.mastersmith.role.repository.PermissionSettingRepository;
import cherry.mastersmith.role.repository.PermissionSettingRow;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@link EffectivePermissionResolver} の実装（{@code logical-components.md} の L4、{@code performance-design.md} 1節、NFR2.3、計画の
 * D-15・D-16）。
 *
 * <p>どの口も読み取りだけの1つのトランザクションで読む（呼び出し元のトランザクションが有ればそれに入る）。写しと {@code resolve} は
 * 行の値だけの射影で読み、エンティティにしない。継承の計算は {@link PermissionInheritance}（純粋な関数）に任せる。
 */
@Service
public class EffectivePermissionResolverImpl implements EffectivePermissionResolver {

    private final UserRoleReader reader;

    private final PermissionSettingRepository settings;

    private final ActiveDslModelProvider activeDsl;

    private final TransactionTemplate readOnly;

    /**
     * 作る。
     *
     * @param reader 利用者のロールを決める読み取り
     * @param settings 権限の設定の表の読み取り
     * @param activeDsl 適用中の DSL の提供口
     * @param transactionManager トランザクションの管理
     */
    public EffectivePermissionResolverImpl(
            UserRoleReader reader,
            PermissionSettingRepository settings,
            ActiveDslModelProvider activeDsl,
            PlatformTransactionManager transactionManager) {
        this.reader = reader;
        this.settings = settings;
        this.activeDsl = activeDsl;
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
    }

    @Override
    public Optional<WorkRoleRef> effectiveWorkRole(long userId) {
        return Objects.requireNonNull(
                readOnly.execute(status -> reader.read(userId).effective()), "result");
    }

    @Override
    public PermissionSnapshot snapshotFor(long userId) {
        return Objects.requireNonNull(
                readOnly.execute(status -> {
                    Optional<WorkRoleRef> workRole = reader.read(userId).effective();
                    ActiveDsl dsl = activeDsl.current();
                    if (!(dsl instanceof ActiveDsl.Present(DslModel model, String hash))) {
                        return PermissionSnapshot.of(workRole.orElse(null), null, null, Map.of());
                    }
                    if (workRole.isEmpty()) {
                        return PermissionSnapshot.of(null, model, hash, Map.of());
                    }
                    Map<PermissionTarget, PermissionValues> values = new HashMap<>();
                    for (PermissionSettingRow row :
                            settings.findAllOfRole(workRole.get().roleId())) {
                        values.put(row.target(), row.values());
                    }
                    return PermissionSnapshot.of(workRole.get(), model, hash, values);
                }),
                "result");
    }

    @Override
    public EffectivePermission resolve(long userId, PermissionTarget target) {
        Objects.requireNonNull(target, "target");
        return Objects.requireNonNull(
                readOnly.execute(status -> {
                    Optional<WorkRoleRef> workRole = reader.read(userId).effective();
                    if (workRole.isEmpty()
                            || !(activeDsl.current() instanceof ActiveDsl.Present(DslModel model, String _))
                            || !PermissionTreeBuilder.inCurrentDsl(model, target)) {
                        return EffectivePermission.NONE;
                    }
                    PermissionValues schema = null;
                    PermissionValues table = null;
                    PermissionValues column = null;
                    for (PermissionSettingRow row : settings.findAncestors(
                            workRole.get().roleId(),
                            target.schemaName(),
                            target.storedTableName(),
                            target.storedColumnName())) {
                        if (row.tableName().isEmpty()) {
                            schema = row.values();
                        } else if (row.columnName().isEmpty()) {
                            table = row.values();
                        } else {
                            column = row.values();
                        }
                    }
                    return PermissionInheritance.resolve(target.level(), schema, table, column)
                            .effective();
                }),
                "result");
    }
}
