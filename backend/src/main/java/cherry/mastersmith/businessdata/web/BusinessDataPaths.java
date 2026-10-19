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

/** 業務データの読み取りと書き込みの API の道（BR10.4・U2 の BR12.3。名前も行の値も道に載せない）。 */
public final class BusinessDataPaths {

    /** 道の頭。 */
    public static final String BASE = "/api/tables";

    /** 定義。 */
    public static final String DEFINITION = BASE + "/definition";

    /** 一覧と検索。 */
    public static final String SEARCH = BASE + "/rows/search";

    /** 詳細。 */
    public static final String GET = BASE + "/rows/get";

    /** 作成（POST）と更新（PATCH）。Intent 261016-business-data-views の U2。 */
    public static final String ROWS = BASE + "/rows";

    /** 削除（POST）。U2。 */
    public static final String DELETE = BASE + "/rows/delete";

    private BusinessDataPaths() {}
}
