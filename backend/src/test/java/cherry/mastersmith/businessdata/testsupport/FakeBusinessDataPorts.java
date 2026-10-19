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
package cherry.mastersmith.businessdata.testsupport;

import cherry.mastersmith.businessdata.domain.PageRequest;
import cherry.mastersmith.businessdata.domain.RowKey;
import cherry.mastersmith.businessdata.domain.SearchCondition;
import cherry.mastersmith.businessdata.domain.TableAccessView;
import cherry.mastersmith.businessdata.repository.RowDetailResult;
import cherry.mastersmith.businessdata.repository.RowSearchResult;
import cherry.mastersmith.businessdata.repository.RowSqlExecutor;
import cherry.mastersmith.role.domain.EffectivePermission;
import cherry.mastersmith.role.domain.PermissionSnapshot;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.domain.WorkRoleRef;
import cherry.mastersmith.role.service.EffectivePermissionResolver;
import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import cherry.mastersmith.targetdb.domain.ReadPurpose;
import cherry.mastersmith.targetdb.domain.TargetReadOutcome;
import cherry.mastersmith.targetdb.domain.TargetReadSession;
import cherry.mastersmith.targetdb.service.TargetReadAccess;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.mockito.Mockito;

/**
 * businessdata の業務処理の層のテストで使う、手で書いた小さな偽物（unit-test-instructions.md の 6節）。呼んだ順と回数を1つの記録に
 * 残す。
 */
public final class FakeBusinessDataPorts {

    /** 呼んだ順の記録。 */
    public final List<String> calls = new ArrayList<>();

    /** 解決の口の偽物。 */
    public final Resolver resolver = new Resolver();

    /** 読み取りの口の偽物。 */
    public final ReadAccess readAccess = new ReadAccess();

    /** 行の SQL の部品の偽物。 */
    public final Executor executor = new Executor();

    /** 読み取りの口の答え方。 */
    public enum Mode {
        /** 処理を呼んで Done を返す。 */
        DONE,
        /** 設定が無い。 */
        NOT_CONFIGURED,
        /** 借りられない。 */
        UNAVAILABLE
    }

    /** 解決の口の偽物（{@link #snapshotFor} だけを使う）。 */
    public final class Resolver implements EffectivePermissionResolver {

        /** 返す写し。 */
        public PermissionSnapshot snapshot;

        @Override
        public Optional<WorkRoleRef> effectiveWorkRole(long userId) {
            throw new AssertionError("使わない");
        }

        @Override
        public PermissionSnapshot snapshotFor(long userId) {
            calls.add("snapshotFor:" + userId);
            return snapshot;
        }

        @Override
        public EffectivePermission resolve(long userId, PermissionTarget target) {
            throw new AssertionError("使わない");
        }
    }

    /** 読み取りの口の偽物。 */
    public final class ReadAccess implements TargetReadAccess {

        /** 答え方。 */
        public Mode mode = Mode.DONE;

        /** DB の種類。 */
        public Optional<DatabaseProduct> product = Optional.of(DatabaseProduct.POSTGRESQL);

        @Override
        public <T> TargetReadOutcome<T> withRead(ReadPurpose purpose, Function<TargetReadSession, T> work) {
            calls.add("withRead:" + purpose);
            return switch (mode) {
                case NOT_CONFIGURED -> new TargetReadOutcome.NotConfigured<>();
                case UNAVAILABLE -> new TargetReadOutcome.Unavailable<>("08001", 0, "java.sql.SQLException");
                case DONE ->
                    new TargetReadOutcome.Done<>(work.apply(new TargetReadSession(
                            product.orElse(DatabaseProduct.POSTGRESQL), Mockito.mock(Connection.class), 5)));
            };
        }

        @Override
        public Optional<DatabaseProduct> product() {
            calls.add("product");
            return product;
        }
    }

    /** 行の SQL の部品の偽物。 */
    public final class Executor extends RowSqlExecutor {

        /** 一覧の答え。 */
        public RowSearchResult searchResult = new RowSearchResult.Page(0, List.of());

        /** 詳細の答え。 */
        public RowDetailResult detailResult = new RowDetailResult.NotFound();

        /** 受け取った条件。 */
        public List<SearchCondition> conditions;

        /** 受け取ったページ。 */
        public PageRequest page;

        @Override
        public RowSearchResult search(
                TargetReadSession session, TableAccessView view, List<SearchCondition> conditions, PageRequest page) {
            calls.add("search");
            this.conditions = conditions;
            this.page = page;
            return searchResult;
        }

        @Override
        public RowDetailResult detail(TargetReadSession session, TableAccessView view, RowKey key) {
            calls.add("detail");
            return detailResult;
        }
    }
}
