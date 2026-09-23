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
package cherry.mastersmith.access.domain;

/**
 * 管理者のみの API のパスの照合（BR1.1、BR1.6）。DB を使わない純粋な関数。
 *
 * <p>アクセスの決まり（フィルターの連鎖の設定）と、401・403 の処理での出来事の要否の判断（BR3.3、BR3.6）が、同じこの関数を
 * 使う。判定と記録がずれないようにするためである。
 *
 * <p>大文字・小文字は区別する（{@code /API/admin/...} は管理者のみの対象にしない）。{@code /api/admin} そのものと
 * {@code /api/admin/} の下の両方を対象とし、末尾のスラッシュの有無で判定を変えない。
 */
public final class AdminPaths {

    /** 管理者のみの範囲の入口のパス。 */
    public static final String ADMIN_ROOT = "/api/admin";

    /** 管理者のみの範囲の下のパスの接頭辞。 */
    public static final String ADMIN_PREFIX = ADMIN_ROOT + "/";

    /** アクセスの決まりに書く、管理者のみのパスの型。 */
    public static final String[] ADMIN_PATTERNS = {ADMIN_ROOT, ADMIN_PREFIX + "**"};

    private AdminPaths() {}

    /**
     * 管理者のみの対象のパスかを返す。
     *
     * @param path 要求のパス（コンテキストパスを除いたもの。null なら false）
     * @return 管理者のみの対象なら true
     */
    public static boolean isAdminOnly(String path) {
        if (path == null) {
            return false;
        }
        return path.equals(ADMIN_ROOT) || path.startsWith(ADMIN_PREFIX);
    }

    /**
     * 要求の URI からコンテキストパスを除いた、アプリの中でのパスを返す。
     *
     * @param requestUri 要求の URI（問い合わせの部分を含まない）
     * @param contextPath コンテキストパス（無ければ空文字）
     * @return アプリの中でのパス
     */
    public static String relativePath(String requestUri, String contextPath) {
        if (requestUri == null) {
            return null;
        }
        if (contextPath != null && !contextPath.isEmpty() && requestUri.startsWith(contextPath)) {
            return requestUri.substring(contextPath.length());
        }
        return requestUri;
    }

    /**
     * アクセスの決まりに書く、管理者のみのパスの型を返す。
     *
     * @return パスの型
     */
    public static String[] adminPatterns() {
        return ADMIN_PATTERNS.clone();
    }
}
