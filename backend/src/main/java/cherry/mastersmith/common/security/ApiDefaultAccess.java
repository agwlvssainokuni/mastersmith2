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
package cherry.mastersmith.common.security;

/**
 * 差し込み口「API の既定の扱い」。U1 の公開の決まりと {@link SecurityRuleContributor} に当たらない {@code /api/**} の扱いを決める。
 *
 * <p>U3 が置く（0個か1個）。2個以上あれば起動を失敗させる。1つも無ければ、U1 は {@code /api/**} を許可する。
 */
public interface ApiDefaultAccess {

    /**
     * {@code /api/**} にログインを求めるか。
     *
     * @return ログイン必須なら true
     */
    boolean requireAuthentication();
}
