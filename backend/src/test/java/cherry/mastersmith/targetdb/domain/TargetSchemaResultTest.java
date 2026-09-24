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
package cherry.mastersmith.targetdb.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 読み取りの結果の型の単体テスト（契約 C1・C3、BR1.9）。 */
class TargetSchemaResultTest {

    @Test
    @DisplayName("the result is exactly one of success, unconfigured or unavailable")
    void threeKinds() {
        assertThat(TargetSchemaResult.class.getPermittedSubclasses())
                .containsExactlyInAnyOrder(
                        TargetSchemaResult.Success.class,
                        TargetSchemaResult.Unconfigured.class,
                        TargetSchemaResult.Unavailable.class);
        TargetSchema schema = new TargetSchema(DatabaseProduct.MYSQL, "s", List.of());
        assertThat(TargetSchemaResult.success(schema))
                .isInstanceOfSatisfying(
                        TargetSchemaResult.Success.class,
                        success -> assertThat(success.schema()).isSameAs(schema));
        assertThat(TargetSchemaResult.unconfigured()).isInstanceOf(TargetSchemaResult.Unconfigured.class);
        assertThat(TargetSchemaResult.unavailable(UnavailableReason.TIMEOUT))
                .isEqualTo(new TargetSchemaResult.Unavailable(UnavailableReason.TIMEOUT));
    }

    @Test
    @DisplayName("the only reasons for being unavailable are timeout and connection failure")
    void twoReasons() {
        assertThat(UnavailableReason.values())
                .containsExactly(UnavailableReason.TIMEOUT, UnavailableReason.CONNECTION_FAILED);
        assertThatThrownBy(() -> TargetSchemaResult.unavailable(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> TargetSchemaResult.success(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("no result type has a place for a host, user name, password or exception message")
    void noPlaceForSecrets() {
        List<Class<?>> types = List.of(
                TargetSchemaResult.Success.class,
                TargetSchemaResult.Unconfigured.class,
                TargetSchemaResult.Unavailable.class,
                TargetSchema.class,
                TargetTable.class,
                TargetColumn.class,
                TargetDbType.class,
                TargetForeignKey.class);
        List<String> components = types.stream()
                .flatMap(type -> Arrays.stream(type.getRecordComponents()))
                .map(RecordComponent::getName)
                .toList();

        assertThat(components)
                .noneMatch(name -> name.matches("(?i).*(host|user|password|url|message|exception|cause).*"));
        assertThat(TargetSchemaResult.unavailable(UnavailableReason.CONNECTION_FAILED))
                .hasToString("Unavailable[reason=CONNECTION_FAILED]");
        assertThat(TargetSchemaResult.unconfigured()).hasToString("Unconfigured[]");
    }

    @Test
    @DisplayName("the database product is read from the setting regardless of case and unknown values are empty")
    void productFromSetting() {
        assertThat(DatabaseProduct.fromSetting("MySQL")).contains(DatabaseProduct.MYSQL);
        assertThat(DatabaseProduct.fromSetting(" mariadb ")).contains(DatabaseProduct.MARIADB);
        assertThat(DatabaseProduct.fromSetting("POSTGRESQL")).contains(DatabaseProduct.POSTGRESQL);
        assertThat(DatabaseProduct.fromSetting("postgres")).isEmpty();
        assertThat(DatabaseProduct.fromSetting("oracle")).isEmpty();
        assertThat(DatabaseProduct.fromSetting("")).isEmpty();
        assertThat(DatabaseProduct.fromSetting(null)).isEmpty();
        assertThat(ReadPurpose.values()).containsExactly(ReadPurpose.GENERATE, ReadPurpose.COMPARE);
    }
}
