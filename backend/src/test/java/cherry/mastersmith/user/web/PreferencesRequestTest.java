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
package cherry.mastersmith.user.web;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.user.domain.DisplayTimeZoneInput;
import cherry.mastersmith.user.domain.FontSize;
import cherry.mastersmith.user.domain.Language;
import cherry.mastersmith.user.domain.Preferences;
import cherry.mastersmith.user.domain.Theme;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;

/** プリファレンスの要求・応答の単体テスト（表示のタイムゾーンの 3 状態の変換、文字列化。BR2.3・BR2.4、SD2）。 */
class PreferencesRequestTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static PreferencesRequest read(String json) {
        return MAPPER.readValue(json, PreferencesRequest.class);
    }

    @Test
    @DisplayName("an absent item is Keep")
    void absentIsKeep() {
        assertThat(read("{\"displayName\":\"a\"}").displayTimeZoneInput()).isEqualTo(new DisplayTimeZoneInput.Keep());
    }

    @Test
    @DisplayName("an explicit null is Clear")
    void nullIsClear() {
        assertThat(read("{\"displayTimeZone\":null}").displayTimeZoneInput())
                .isEqualTo(new DisplayTimeZoneInput.Clear());
    }

    @Test
    @DisplayName("strings, including the empty string, are Text and kept as sent")
    void stringIsText() {
        assertThat(read("{\"displayTimeZone\":\"Asia/Tokyo\"}").displayTimeZoneInput())
                .isEqualTo(new DisplayTimeZoneInput.Text("Asia/Tokyo"));
        assertThat(read("{\"displayTimeZone\":\"\"}").displayTimeZoneInput())
                .isEqualTo(new DisplayTimeZoneInput.Text(""));
        assertThat(read("{\"displayTimeZone\":\" Asia/Tokyo \"}").displayTimeZoneInput())
                .isEqualTo(new DisplayTimeZoneInput.Text(" Asia/Tokyo "));
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {"42|NUMBER", "true|BOOLEAN", "{\"a\":1}|OBJECT", "[\"Asia/Tokyo\"]|ARRAY", "1.5|NUMBER"})
    @DisplayName("numbers, booleans, objects and arrays are NotText with only the kind")
    void otherTypesAreNotText(String json, String kind) {
        assertThat(read("{\"displayTimeZone\":" + json + "}").displayTimeZoneInput())
                .isEqualTo(new DisplayTimeZoneInput.NotText(kind));
    }

    @Test
    @DisplayName("toString hides the name and the zone value")
    void toStringHidesValues() {
        PreferencesRequest request = read("{\"displayName\":\"SECRET-NAME\",\"displayTimeZone\":\"SECRET-ZONE\"}");

        assertThat(request.toString()).doesNotContain("SECRET-NAME").doesNotContain("SECRET-ZONE");
        assertThat(read("{}").toString()).contains("absent");
    }

    @Test
    @DisplayName("the response always carries the fifth item, null when unset, and hides the name in toString")
    void responseCarriesTheFifthItem() {
        Preferences preferences = new Preferences("SECRET-NAME", Language.JA, Theme.SYSTEM, FontSize.MD);

        PreferencesResponse unset = PreferencesResponse.from(preferences, null);
        PreferencesResponse set = PreferencesResponse.from(preferences, "Asia/Tokyo");

        assertThat(MAPPER.writeValueAsString(unset)).contains("\"displayTimeZone\":null");
        assertThat(MAPPER.writeValueAsString(set)).contains("\"displayTimeZone\":\"Asia/Tokyo\"");
        assertThat(set.toString()).doesNotContain("SECRET-NAME").contains("Asia/Tokyo");
    }
}
