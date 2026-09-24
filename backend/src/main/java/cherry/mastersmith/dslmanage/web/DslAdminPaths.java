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
package cherry.mastersmith.dslmanage.web;

/** DSL の管理の API のパス（契約 C6、BR8.3。すべて {@code /api/admin/dsl/} の下）。 */
public final class DslAdminPaths {

    /** API の根。 */
    public static final String ROOT = "/api/admin/dsl";

    /** 今の状態。 */
    public static final String STATUS = ROOT + "/status";

    /** プレビュー（表示・投入・破棄）。 */
    public static final String PREVIEW = ROOT + "/preview";

    /** スキーマの読み込み。 */
    public static final String GENERATE = PREVIEW + "/generate";

    /** プレビュー中のダウンロード。 */
    public static final String PREVIEW_DOWNLOAD = PREVIEW + "/download";

    /** 適用。 */
    public static final String APPLY = ROOT + "/apply";

    /** 適用の履歴。 */
    public static final String HISTORY = ROOT + "/history";

    /** 履歴の版をプレビューに戻す（パスの変数 {@code revisionId}）。 */
    public static final String HISTORY_RESTORE = HISTORY + "/{revisionId}/restore";

    /** 適用中のダウンロード。 */
    public static final String APPLIED_DOWNLOAD = ROOT + "/applied/download";

    private DslAdminPaths() {}
}
