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
package cherry.mastersmith.navigation.domain;

import java.util.Objects;
import java.util.Set;

/**
 * アイコンの照らし合わせ（{@code rules.md} の BR4.2、{@code logical-components.md} の L4）。DSL の {@code icon} を許した名前の集まりと
 * 文字どおり（大文字と小文字を区別し、前後の空白を許さない）照らし、無い・空・一致しないときは {@link #DEFAULT_ICON} にする。置き換えの
 * ログは出さない。
 */
public final class NavIconPolicy {

    /** 既定のアイコンの名前（一覧に必ず含まれる。BR4.3）。 */
    public static final String DEFAULT_ICON = "list";

    private final Set<String> allowed;

    /**
     * 許した名前の集まりから作る。
     *
     * @param allowed 許した名前（{@link #DEFAULT_ICON} を含む）
     * @throws IllegalArgumentException 既定のアイコンを含まないとき
     */
    public NavIconPolicy(Set<String> allowed) {
        this.allowed = Set.copyOf(Objects.requireNonNull(allowed, "allowed は必須です"));
        if (!this.allowed.contains(DEFAULT_ICON)) {
            throw new IllegalArgumentException("許した名前に既定のアイコンがありません");
        }
    }

    /**
     * DSL の {@code icon} を照らす。
     *
     * @param icon DSL の値（null でもよい）
     * @return 一覧の名前と文字どおり一致すればそのまま、そうでなければ {@link #DEFAULT_ICON}
     */
    public String iconOf(String icon) {
        return icon != null && allowed.contains(icon) ? icon : DEFAULT_ICON;
    }

    /**
     * 許した名前の集まりを返す。
     *
     * @return 許した名前（変更できない）
     */
    public Set<String> allowed() {
        return allowed;
    }
}
