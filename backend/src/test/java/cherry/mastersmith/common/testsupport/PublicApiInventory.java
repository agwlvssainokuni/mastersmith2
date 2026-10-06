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
package cherry.mastersmith.common.testsupport;

import java.util.Collections;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

/**
 * PUBLIC の口の一覧と、その比べ方（要件 NFR1.6・2.2、security-design 4.4・4.4.1、ADR-008 の補足）。
 *
 * <p>分類の正は口の隣の印 {@code ApiAccess} で、テストの側に分類の一覧は持たない（ADR-008）。ただし PUBLIC の口は、意図しない
 * 増減を止めるための追加の守りとして、この一覧で持つ。PUBLIC を足す・外すときは、一覧の行と理由のコメントを同じ変更で直す。
 *
 * <p>比べる単位は口（ハンドラーの方法）1つで、宣言された方法の集合と道の型の集合の組で表す。暗黙の HEAD・OPTIONS は足さない。
 * 宣言が無い口は空の方法の集合（すべての方法）で表し、方法を列挙した口とは別のものとして扱う。道の型はアプリが持つ口の一覧の道の型を
 * そのまま使い（設定値の置き換えは解決した値）、パス変数は変数名を含む宣言の形のまま比べる。
 */
public final class PublicApiInventory {

    /**
     * 口1つ（宣言された方法の集合と道の型の集合の組）。
     *
     * @param methods 宣言された方法の集合（空ならすべての方法）
     * @param patterns 道の型の集合
     */
    public record PublicApiEntry(Set<String> methods, Set<String> patterns) {

        /**
         * 作る（並びを決めた変えられない集合にする）。
         *
         * @param methods 宣言された方法の集合
         * @param patterns 道の型の集合
         */
        public PublicApiEntry {
            methods = sorted(methods);
            patterns = sorted(patterns);
        }

        /**
         * 道の型が1つの口を作る。
         *
         * @param pattern 道の型
         * @param methods 宣言された方法
         * @return 口
         */
        public static PublicApiEntry of(String pattern, String... methods) {
            return new PublicApiEntry(Set.of(methods), Set.of(pattern));
        }

        private static SortedSet<String> sorted(Set<String> values) {
            return Collections.unmodifiableSortedSet(new TreeSet<>(values));
        }
    }

    /**
     * 比べた結果。
     *
     * @param added 一覧に無いのに PUBLIC の口（増えた行）
     * @param removed 一覧にあるのに PUBLIC の口に無い（減った行）
     */
    public record Diff(Set<PublicApiEntry> added, Set<PublicApiEntry> removed) {

        /**
         * 一致したか。
         *
         * @return 増えた行も減った行も無ければ true
         */
        public boolean isEmpty() {
            return added.isEmpty() && removed.isEmpty();
        }
    }

    /** {@code server.error.path} の解決した値（今の設定では既定の値）。 */
    private static final String ERROR_PATH = "/error";

    /** PUBLIC の口の一覧（口の単位で 9 行。方法と道の組では 14）。 */
    public static final Set<PublicApiEntry> EXPECTED = Set.of(
            // 1. AuthController.login: ログインの入口
            PublicApiEntry.of("/api/auth/login", "POST"),
            // 2. AuthController.refresh: アクセストークンの更新（リフレッシュの Cookie で確かめる）
            PublicApiEntry.of("/api/auth/session/refresh", "POST"),
            // 3. AuthController.logout: ログアウト（アクセストークンが切れていても行える）
            PublicApiEntry.of("/api/auth/session/logout", "POST"),
            // 4. AppearanceController.get: ログイン画面の前に見た目の設定を読む
            PublicApiEntry.of("/api/appearance", "GET"),
            // 5. ProblemTypeController.describe: エラー応答の type の説明
            PublicApiEntry.of("/api/problems/{slug}", "GET"),
            // 6. RegistrationController.verify: 招待のリンクの確かめ（ログインの前）
            PublicApiEntry.of("/api/registration/verify", "POST"),
            // 7. RegistrationController.complete: 登録の完了（ログインの前）
            PublicApiEntry.of("/api/registration/complete", "POST"),
            // 8. ErrorPathController.error: サーブレットの誤りの転送の応答（読み取りの方法。HEAD・OPTIONS も宣言されている）
            PublicApiEntry.of(ERROR_PATH, "GET", "HEAD", "OPTIONS"),
            // 9. ErrorPathController.errorForUpdate: 同上（更新の方法）
            PublicApiEntry.of(ERROR_PATH, "POST", "PUT", "PATCH", "DELETE"));

    private PublicApiInventory() {}

    /**
     * 口の対応づけから、宣言された方法の集合と道の型の集合の組を作る（暗黙の HEAD・OPTIONS は足さない）。
     *
     * @param info 口の対応づけ
     * @return 口
     */
    public static PublicApiEntry entryOf(RequestMappingInfo info) {
        Set<String> methods = info.getMethodsCondition().getMethods().stream()
                .map(RequestMethod::name)
                .collect(Collectors.toSet());
        return new PublicApiEntry(methods, info.getPatternValues());
    }

    /**
     * 一覧と、実際の PUBLIC の口の集まりを比べる。
     *
     * @param expected 一覧
     * @param actual 実際の PUBLIC の口
     * @return 増えた行と減った行
     */
    public static Diff diff(Set<PublicApiEntry> expected, Set<PublicApiEntry> actual) {
        Set<PublicApiEntry> added =
                actual.stream().filter(entry -> !expected.contains(entry)).collect(Collectors.toUnmodifiableSet());
        Set<PublicApiEntry> removed =
                expected.stream().filter(entry -> !actual.contains(entry)).collect(Collectors.toUnmodifiableSet());
        return new Diff(added, removed);
    }
}
