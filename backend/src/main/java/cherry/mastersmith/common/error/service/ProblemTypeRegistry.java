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
package cherry.mastersmith.common.error.service;

import cherry.mastersmith.common.error.domain.ProblemType;
import cherry.mastersmith.common.error.domain.ProblemTypeCatalog;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import org.springframework.stereotype.Component;

/**
 * 問題の種類の一覧。起動時にすべての {@link ProblemTypeCatalog} を集め、code・slug で引けるようにする（BR5.16）。
 *
 * <p>code または slug が重複していれば、重複した値を示して起動を失敗させる。
 */
@Component
public class ProblemTypeRegistry {

    private final Map<String, ProblemType> byCode;

    private final Map<String, ProblemType> bySlug;

    /**
     * すべての定義を集めて重複を確かめる。
     *
     * @param catalogs 各機能の問題の種類の定義の一覧
     * @throws IllegalStateException code または slug が重複しているとき
     */
    public ProblemTypeRegistry(List<ProblemTypeCatalog> catalogs) {
        Map<String, ProblemType> codes = new LinkedHashMap<>();
        Map<String, ProblemType> slugs = new LinkedHashMap<>();
        TreeSet<String> duplicateCodes = new TreeSet<>();
        TreeSet<String> duplicateSlugs = new TreeSet<>();
        for (ProblemTypeCatalog catalog : catalogs) {
            for (ProblemType type : catalog.problemTypes()) {
                if (codes.putIfAbsent(type.code(), type) != null) {
                    duplicateCodes.add(type.code());
                }
                if (slugs.putIfAbsent(type.slug(), type) != null) {
                    duplicateSlugs.add(type.slug());
                }
            }
        }
        if (!duplicateCodes.isEmpty() || !duplicateSlugs.isEmpty()) {
            List<String> parts = new ArrayList<>();
            if (!duplicateCodes.isEmpty()) {
                parts.add("code=" + duplicateCodes);
            }
            if (!duplicateSlugs.isEmpty()) {
                parts.add("slug=" + duplicateSlugs);
            }
            throw new IllegalStateException("問題の種類の定義が重複しています: " + String.join(", ", parts));
        }
        this.byCode = Collections.unmodifiableMap(codes);
        this.bySlug = Collections.unmodifiableMap(slugs);
    }

    /**
     * code で問題の種類を引く。
     *
     * @param code code
     * @return 問題の種類（無ければ空）
     */
    public Optional<ProblemType> findByCode(String code) {
        return Optional.ofNullable(byCode.get(code));
    }

    /**
     * slug で問題の種類を引く。
     *
     * @param slug slug
     * @return 問題の種類（無ければ空）
     */
    public Optional<ProblemType> findBySlug(String slug) {
        return Optional.ofNullable(bySlug.get(slug));
    }

    /**
     * 集めたすべての問題の種類を返す。
     *
     * @return 問題の種類の一覧（登録の順）
     */
    public List<ProblemType> all() {
        return List.copyOf(byCode.values());
    }
}
