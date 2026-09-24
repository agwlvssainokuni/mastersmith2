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
package cherry.mastersmith.targetdb.repository;

import cherry.mastersmith.targetdb.domain.DatabaseProduct;

/** MySQL の情報スキーマの読み手の結合テスト（共通の確かめは {@link AbstractSchemaQueriesIT}）。 */
class MysqlSchemaQueriesIT extends AbstractSchemaQueriesIT {

    @Override
    DatabaseProduct product() {
        return DatabaseProduct.MYSQL;
    }

    @Override
    String expectedIntegerType() {
        return "int";
    }

    @Override
    String expectedDecimalType() {
        return "decimal";
    }

    @Override
    String expectedAmountDefault() {
        return "0.00";
    }

    @Override
    String expectedStatusDefault() {
        return "new";
    }
}
