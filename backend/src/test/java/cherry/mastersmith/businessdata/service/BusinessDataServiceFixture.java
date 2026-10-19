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

import static cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture.column;
import static cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture.table;

import cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture;
import cherry.mastersmith.dsl.domain.DslModel;
import cherry.mastersmith.dsl.domain.DslTable;
import cherry.mastersmith.dsl.domain.SearchOperator;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.domain.PermissionSnapshot;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.domain.PermissionValues;
import cherry.mastersmith.role.domain.WorkRoleRef;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 業務処理の層のテストの DSL と権限の写し（値はすべて架空）。 */
final class BusinessDataServiceFixture {

    /** 主キーのあるテーブル。 */
    static final DslTable ITEM = table(
            "item",
            false,
            List.of("code"),
            column("code").dbType("int4").search(SearchOperator.EQUALS).build(),
            column("name").search(SearchOperator.CONTAINS).build(),
            column("cost").dbType("numeric").build());

    /** 主キーの無いテーブル。 */
    static final DslTable LOG = table("log", false, List.of(), column("text").build());

    /** 見られないテーブル。 */
    static final DslTable SECRET =
            table("secret", false, List.of("id"), column("id").build());

    /** DSL。 */
    static final DslModel DSL = BusinessDataDslFixture.model("sample", ITEM, LOG, SECRET);

    /** 作業ロール。 */
    static final WorkRoleRef WORK_ROLE = new WorkRoleRef(7L, "営業");

    private BusinessDataServiceFixture() {}

    /**
     * スキーマを READ、{@code item.cost} を NONE、{@code secret} を NONE にした写しを作る。
     *
     * @return 写し
     */
    static PermissionSnapshot snapshot() {
        Map<PermissionTarget, PermissionValues> settings = new LinkedHashMap<>();
        settings.put(PermissionTarget.schema("sample"), new PermissionValues(MainPermission.READ, false, false));
        settings.put(
                PermissionTarget.column("sample", "item", "cost"),
                new PermissionValues(MainPermission.NONE, null, null));
        settings.put(PermissionTarget.table("sample", "secret"), new PermissionValues(MainPermission.NONE, null, null));
        return PermissionSnapshot.of(WORK_ROLE, DSL, BusinessDataDslFixture.HASH, settings);
    }
}
