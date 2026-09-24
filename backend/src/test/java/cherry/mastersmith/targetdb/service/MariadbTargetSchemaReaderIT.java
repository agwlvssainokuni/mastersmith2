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
package cherry.mastersmith.targetdb.service;

import cherry.mastersmith.targetdb.domain.DatabaseProduct;

/** MariaDB を対象DB にした読み取りの口の結合テスト（共通の確かめは {@link AbstractTargetSchemaReaderIT}）。 */
class MariadbTargetSchemaReaderIT extends AbstractTargetSchemaReaderIT {

    @Override
    DatabaseProduct product() {
        return DatabaseProduct.MARIADB;
    }

    @Override
    String authenticationFailureText() {
        return "Access denied";
    }
}
