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
package cherry.mastersmith.businessdata.web;

import cherry.mastersmith.targetdb.domain.DatabaseProduct;

/** 定義の取得の参照の項目を MARIADB で確かめる（Intent 261029-reference-lookup-cleanup の U1）。 */
class MariadbReferenceReadIT extends AbstractReferenceReadIT {

    @Override
    DatabaseProduct product() {
        return DatabaseProduct.MARIADB;
    }
}
