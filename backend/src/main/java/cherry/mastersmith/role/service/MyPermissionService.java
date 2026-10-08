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

import cherry.mastersmith.role.domain.EffectiveNode;
import cherry.mastersmith.role.domain.PermissionSnapshot;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.springframework.stereotype.Service;

/**
 * 自分の権限の木の業務処理（契約 C8 の {@code /api/me/permissions/...}、FS の 2.11、BR2.2・BR4.12・BR5.3〜BR5.5。Should）。
 *
 * <p>主体の写し（{@link EffectivePermissionResolver#snapshotFor(long)} を1回）から、今の DSL の節の実効の値と {@code inMenu} だけを返す。
 * 他人を指す値は受け取らない。DSL が無いときは節が空、作業ロールが無いときは作業ロールが空ですべて NONE・不可。監査に残さない。
 */
@Service
public class MyPermissionService {

    private final EffectivePermissionResolver resolver;

    /**
     * 作る。
     *
     * @param resolver 解決の口
     */
    public MyPermissionService(EffectivePermissionResolver resolver) {
        this.resolver = resolver;
    }

    /**
     * 木の1段目（スキーマ）を読む。
     *
     * @param userId 主体の利用者 ID（要求の文脈から読んだ値）
     * @return 1階層
     */
    public MyPermissionTree schemas(long userId) {
        return tree(userId, PermissionSnapshot::schemaNodes);
    }

    /**
     * スキーマの下のテーブルを読む（名前は問い合わせの引数のまま。今の DSL に無ければ空）。
     *
     * @param userId 主体の利用者 ID
     * @param schemaName スキーマの名前
     * @return 1階層
     */
    public MyPermissionTree tables(long userId, String schemaName) {
        Objects.requireNonNull(schemaName, "schemaName");
        return tree(userId, snapshot -> snapshot.tableNodes(schemaName));
    }

    /**
     * テーブルの下のカラムを読む（名前は問い合わせの引数のまま。今の DSL に無ければ空）。
     *
     * @param userId 主体の利用者 ID
     * @param schemaName スキーマの名前
     * @param tableName テーブルの名前
     * @return 1階層
     */
    public MyPermissionTree columns(long userId, String schemaName, String tableName) {
        Objects.requireNonNull(schemaName, "schemaName");
        Objects.requireNonNull(tableName, "tableName");
        return tree(userId, snapshot -> snapshot.columnNodes(schemaName, tableName));
    }

    private MyPermissionTree tree(long userId, Function<PermissionSnapshot, List<EffectiveNode>> level) {
        PermissionSnapshot snapshot = resolver.snapshotFor(userId);
        return new MyPermissionTree(snapshot.workRole(), level.apply(snapshot));
    }
}
