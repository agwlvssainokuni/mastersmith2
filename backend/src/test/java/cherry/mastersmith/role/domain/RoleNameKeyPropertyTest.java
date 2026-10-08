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
package cherry.mastersmith.role.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * ロールの名前の正規化と検証の性質ベースのテスト（jqwik。BR1.1〜BR1.4、NFR6.2、AC1.1.9・AC1.1.16）。
 *
 * <p>名前は {@code Properties} で終わらせない（{@code *Test}・{@code *IT} のどちらにも当たらず {@code verify} で動かないため。計画の
 * D-12）。失敗のときの乱数の種は、既存のテストの出力の設定（{@code exceptionFormat = FULL}）で残る。
 */
class RoleNameKeyPropertyTest {

    /** 名前の材料（ASCII・全角・日本語・サロゲートペア・小文字化で文字が増える文字・空白・制御文字・行の区切り）。 */
    private static final List<String> TOKENS = List.of(
            "a", "Z", "s", "S", "営", "業", "Ｓ", "ｓ", "😀", "𐐀", "İ", "ß", "Σ", " ", "　", " ", "\t", "\n", "\u0000",
            "\u007F", " ");

    /** 大文字と小文字の行き来で元に戻る材料（大文字と小文字だけの違いの確かめに使う）。 */
    private static final List<String> CASE_TOKENS = List.of("a", "Z", "s", "S", "営", "業", "Ｓ", "ｓ", "𐐀", "𐐨", "部");

    /** 空白・制御文字を含まない、長さを最も膨らませる材料（列の長さに収まることの確かめに使う）。 */
    private static final List<String> WIDE_TOKENS = List.of("😀", "𐐀", "İ", "a", "営");

    @Provide
    Arbitrary<String> anyNames() {
        return Arbitraries.of(TOKENS).list().ofMaxSize(80).map(tokens -> String.join("", tokens));
    }

    @Provide
    Arbitrary<String> caseNames() {
        return Arbitraries.of(CASE_TOKENS).list().ofMinSize(1).ofMaxSize(64).map(tokens -> String.join("", tokens));
    }

    @Provide
    Arbitrary<String> wideNames() {
        return Arbitraries.of(WIDE_TOKENS).list().ofMinSize(1).ofMaxSize(64).map(tokens -> String.join("", tokens));
    }

    @Property
    @Label("an accepted name has no white space at either end, 1 to 64 code points and no control character")
    void acceptedNamesAreNormalized(@ForAll("anyNames") String raw) {
        if (RoleName.parse(raw) instanceof RoleNameValidation.Valid(RoleName name)) {
            String value = name.value();
            assertThat(value).isNotEmpty();
            assertThat(RoleName.isWhiteSpace(value.codePointAt(0))).isFalse();
            assertThat(RoleName.isWhiteSpace(value.codePointBefore(value.length())))
                    .isFalse();
            assertThat(value.codePointCount(0, value.length())).isBetween(1, RoleName.MAX_CODE_POINTS);
            assertThat(value.codePoints().noneMatch(RoleName::isControl)).isTrue();
        }
    }

    @Property
    @Label("a name is accepted exactly when the trimmed value is non-empty, at most 64 code points and has no control")
    void acceptanceMatchesTheRules(@ForAll("anyNames") String raw) {
        String trimmed = RoleName.strip(raw);
        boolean expected = !trimmed.isEmpty()
                && trimmed.codePointCount(0, trimmed.length()) <= RoleName.MAX_CODE_POINTS
                && trimmed.codePoints().noneMatch(RoleName::isControl);

        assertThat(RoleName.parse(raw) instanceof RoleNameValidation.Valid).isEqualTo(expected);
    }

    @Property
    @Label("parsing an accepted name again gives the same value and key")
    void parsingIsIdempotent(@ForAll("anyNames") String raw) {
        if (RoleName.parse(raw) instanceof RoleNameValidation.Valid(RoleName name)) {
            RoleNameValidation again = RoleName.parse(name.value());
            assertThat(again).isEqualTo(new RoleNameValidation.Valid(name));
            assertThat(((RoleNameValidation.Valid) again).name().key()).isEqualTo(name.key());
        }
    }

    @Property
    @Label("names that differ only in upper and lower case have the same key")
    void caseOnlyDifferenceSharesTheKey(@ForAll("caseNames") String raw) {
        RoleNameValidation upper = RoleName.parse(raw.toUpperCase(Locale.ROOT));
        RoleNameValidation lower = RoleName.parse(raw.toLowerCase(Locale.ROOT));

        assertThat(upper).isInstanceOf(RoleNameValidation.Valid.class);
        assertThat(lower).isInstanceOf(RoleNameValidation.Valid.class);
        assertThat(((RoleNameValidation.Valid) upper).name().key())
                .isEqualTo(((RoleNameValidation.Valid) lower).name().key());
    }

    @Property
    @Label("an accepted name fits into 128 UTF-16 units and its key into 256")
    void valuesFitIntoTheColumns(@ForAll("wideNames") String raw) {
        RoleNameValidation result = RoleName.parse(raw);

        assertThat(result).isInstanceOf(RoleNameValidation.Valid.class);
        RoleName name = ((RoleNameValidation.Valid) result).name();
        assertThat(name.value().length()).isLessThanOrEqualTo(128);
        assertThat(name.key().length()).isLessThanOrEqualTo(256);
    }

    @Provide
    Arbitrary<String> asciiLetters() {
        return Arbitraries.strings()
                .withCharRange('A', 'Z')
                .withCharRange('a', 'z')
                .ofMinLength(1)
                .ofMaxLength(64);
    }

    @Property
    @Label("an ASCII name and its full-width form have different keys")
    void fullWidthIsAnotherKey(@ForAll("asciiLetters") String raw) {
        StringBuilder fullWidth = new StringBuilder();
        raw.chars().forEach(c -> fullWidth.append((char) (c - 'A' + 'Ａ')));
        RoleNameValidation half = RoleName.parse(raw);
        RoleNameValidation full = RoleName.parse(fullWidth.toString());

        assertThat(half).isInstanceOf(RoleNameValidation.Valid.class);
        assertThat(full).isInstanceOf(RoleNameValidation.Valid.class);
        assertThat(((RoleNameValidation.Valid) full).name().key())
                .isNotEqualTo(((RoleNameValidation.Valid) half).name().key());
    }
}
