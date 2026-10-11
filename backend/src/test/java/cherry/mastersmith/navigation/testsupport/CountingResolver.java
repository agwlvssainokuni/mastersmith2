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
package cherry.mastersmith.navigation.testsupport;

import cherry.mastersmith.dsl.domain.DslModel;
import cherry.mastersmith.role.domain.EffectivePermission;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.domain.PermissionSnapshot;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.domain.PermissionValues;
import cherry.mastersmith.role.domain.WorkRoleRef;
import cherry.mastersmith.role.service.EffectivePermissionResolver;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * 呼ばれた回数を数える解決の口の替え物（{@code logical-components.md} の T3、NFR2.3・NFR1.11）。Spring を起動しない単体テストで
 * {@code NavigationService} に直に渡す。
 *
 * <p>テーブルの階層の明示の主権限を対応表で持ち、写しは本物の {@link PermissionSnapshot} で作る（DSL に無い対象は NONE）。
 * {@link #failWith} で、写し・解決の呼び出しで例外を投げる場合を作る。
 */
public final class CountingResolver implements EffectivePermissionResolver {

    private final AtomicInteger snapshotCalls = new AtomicInteger();

    private final AtomicInteger resolveCalls = new AtomicInteger();

    private final Supplier<DslModel> dsl;

    private final Map<PermissionTarget, PermissionValues> settings = new HashMap<>();

    private WorkRoleRef workRole = new WorkRoleRef(1L, "作業ロール");

    private RuntimeException failure;

    /**
     * 作る。
     *
     * @param dsl 写しを作るときの適用中のモデル（null は「無い」）を返す操作
     */
    public CountingResolver(Supplier<DslModel> dsl) {
        this.dsl = dsl;
    }

    /**
     * テーブルの明示の主権限を置く。
     *
     * @param schemaName スキーマの名前
     * @param tableName テーブルの名前
     * @param main 主権限
     * @return この替え物
     */
    public CountingResolver table(String schemaName, String tableName, MainPermission main) {
        settings.put(PermissionTarget.table(schemaName, tableName), new PermissionValues(main, null, null));
        return this;
    }

    /**
     * 作業ロールを無しにする（写しはすべて NONE）。
     *
     * @return この替え物
     */
    public CountingResolver withoutWorkRole() {
        workRole = null;
        settings.clear();
        return this;
    }

    /**
     * 写し・解決の呼び出しで投げる例外を置く。
     *
     * @param failure 例外
     * @return この替え物
     */
    public CountingResolver failWith(RuntimeException failure) {
        this.failure = failure;
        return this;
    }

    @Override
    public Optional<WorkRoleRef> effectiveWorkRole(long userId) {
        return Optional.ofNullable(workRole);
    }

    @Override
    public PermissionSnapshot snapshotFor(long userId) {
        snapshotCalls.incrementAndGet();
        if (failure != null) {
            throw failure;
        }
        return snapshot();
    }

    @Override
    public EffectivePermission resolve(long userId, PermissionTarget target) {
        resolveCalls.incrementAndGet();
        if (failure != null) {
            throw failure;
        }
        return snapshot().effective(target);
    }

    private PermissionSnapshot snapshot() {
        DslModel model = dsl.get();
        return PermissionSnapshot.of(
                workRole, model, model == null ? null : model.dslHash(), workRole == null ? Map.of() : settings);
    }

    /**
     * 写しの呼び出しの回数を返す。
     *
     * @return 回数
     */
    public int snapshotCalls() {
        return snapshotCalls.get();
    }

    /**
     * 解決の呼び出しの回数を返す。
     *
     * @return 回数
     */
    public int resolveCalls() {
        return resolveCalls.get();
    }
}
