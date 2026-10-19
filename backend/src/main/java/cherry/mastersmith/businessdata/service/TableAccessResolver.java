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
package cherry.mastersmith.businessdata.service;

import cherry.mastersmith.businessdata.domain.ColumnKind;
import cherry.mastersmith.businessdata.domain.DefinitionId;
import cherry.mastersmith.businessdata.domain.KnownIdentifiers;
import cherry.mastersmith.businessdata.domain.ReadRejection;
import cherry.mastersmith.businessdata.domain.TableAccessInput;
import cherry.mastersmith.businessdata.domain.TableAccessPolicy;
import cherry.mastersmith.businessdata.domain.TableQuery;
import cherry.mastersmith.businessdata.repository.ColumnValueCodec;
import cherry.mastersmith.dsl.domain.DslColumn;
import cherry.mastersmith.dsl.domain.DslModel;
import cherry.mastersmith.dsl.domain.DslSchema;
import cherry.mastersmith.dsl.domain.DslTable;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.domain.PermissionSnapshot;
import cherry.mastersmith.role.domain.WorkRoleRef;
import cherry.mastersmith.role.service.EffectivePermissionResolver;
import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import cherry.mastersmith.targetdb.service.TargetReadAccess;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 権限の写しを1回だけ取り、その写しの DSL で名前を照らして判定の材料を作る（契約 C1 の accessFor、BR1.2〜BR1.4・BR3.1〜BR3.3、
 * logical-components.md の L2）。
 *
 * <p>内部DB のトランザクションを開かない（写しは解決の口の中の短い読み取りで取り終える。BR8.3）。対象DB には接続しない（DB の
 * 種類は設定から読む。BR8.4）。管理者の印は使わない。
 */
@Service
public class TableAccessResolver {

    private final EffectivePermissionResolver permissions;

    private final TargetReadAccess readAccess;

    /**
     * 作る。
     *
     * @param permissions 実効の権限の解決の口
     * @param readAccess 業務データの読み取りの口（DB の種類だけを使う）
     */
    public TableAccessResolver(EffectivePermissionResolver permissions, TargetReadAccess readAccess) {
        this.permissions = permissions;
        this.readAccess = readAccess;
    }

    /**
     * 要求が指すテーブルの判定の材料を作る（判定の順の ②〜④）。
     *
     * @param userId 利用者 ID（要求の文脈から読んだもの）
     * @param query 要求が指すテーブル
     * @return 判定の材料、または拒否
     */
    public TableAccessResolution resolve(long userId, TableQuery query) {
        PermissionSnapshot snapshot = permissions.snapshotFor(userId);
        Optional<DslModel> dsl = snapshot.dsl();
        Optional<String> dslHash = snapshot.dslHash();
        if (dsl.isEmpty() || dslHash.isEmpty()) {
            return new TableAccessResolution.Rejected(ReadRejection.DSL_NOT_APPLIED);
        }
        Optional<DslSchema> schema = dsl.get().schemas().stream()
                .filter(s -> s.name().equals(query.schema()))
                .findFirst();
        DslTable table = schema.isEmpty() || query.table() == null
                ? null
                : schema.get().tables().get(query.table());
        if (table == null) {
            return new TableAccessResolution.Rejected(ReadRejection.TABLE_NOT_FOUND);
        }
        String schemaName = schema.get().name();
        MainPermission tableMain = snapshot.main(schemaName, table.name(), null);
        if (tableMain == MainPermission.NONE) {
            return new TableAccessResolution.Rejected(ReadRejection.ACCESS_DENIED);
        }
        Optional<DatabaseProduct> product = readAccess.product();
        Map<String, MainPermission> columnMain = new HashMap<>();
        Map<String, ColumnKind> kinds = new HashMap<>();
        for (DslColumn column : table.columns().values()) {
            columnMain.put(column.name(), snapshot.main(schemaName, table.name(), column.name()));
            kinds.put(column.name(), ColumnValueCodec.kind(product, column));
        }
        Set<String> schemaNames = new LinkedHashSet<>();
        dsl.get().schemas().forEach(s -> schemaNames.add(s.name()));
        KnownIdentifiers names = new KnownIdentifiers(
                schemaNames, schema.get().tables().keySet(), table.columns().keySet());
        DefinitionId definitionId = new DefinitionId(
                dslHash.get(), snapshot.workRole().map(WorkRoleRef::roleId).orElse(null));
        TableAccessInput input = new TableAccessInput(
                schemaName,
                table,
                definitionId,
                tableMain,
                snapshot.create(schemaName, table.name()),
                snapshot.delete(schemaName, table.name()),
                columnMain,
                kinds,
                // 書き込みの接続の設定は U2 が足す。U1 では常に false。
                false,
                names);
        return new TableAccessResolution.Resolved(TableAccessPolicy.evaluate(input));
    }
}
