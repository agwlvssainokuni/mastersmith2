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
package cherry.mastersmith.role.web;

/** 権限の YAML の受け渡しの道（C7、FS の 2.13〜2.15。B6）。 */
public final class RoleTransferPaths {

    /** 受け渡しの道の根。 */
    public static final String ROOT = "/api/admin/role-transfer";

    /** 書き出し（GET）。 */
    public static final String EXPORT = ROOT + "/export";

    /** 確かめ（POST）。 */
    public static final String CHECK = ROOT + "/check";

    /** 適用（POST）。 */
    public static final String APPLY = ROOT + "/apply";

    private RoleTransferPaths() {}
}
