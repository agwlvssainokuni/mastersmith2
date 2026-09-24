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
package cherry.mastersmith.dslmanage.generate;

import cherry.mastersmith.dsl.domain.DslTable;
import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import java.util.List;
import java.util.function.UnaryOperator;

/** MySQL での既定の DSL の生成の結合テスト（共通の確かめは {@link AbstractDefaultDslGeneratorIT}）。 */
class DefaultDslGeneratorMysqlIT extends AbstractDefaultDslGeneratorIT {

    @Override
    DatabaseProduct product() {
        return DatabaseProduct.MYSQL;
    }

    @Override
    List<String> fixtureSql(UnaryOperator<String> q) {
        return mysqlFamilySql(q);
    }

    @Override
    void assertProductSpecificColumns(DslTable dept) {
        assertMysqlFamilyColumns(dept);
    }
}
