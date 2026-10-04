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
package cherry.mastersmith.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 初期管理者の設定の文字列化（Intent 261004-safety-carryover の NFR1、K-25）の単体テスト。 */
class InitialAdminPropertiesTest {

    @Test
    @DisplayName("the string form masks the email address and hides the password")
    void stringFormMasksEmailAndPassword() {
        InitialAdminProperties properties = new InitialAdminProperties("admin@example.com", "初期管理者のパスワード-0001");

        assertThat(properties.toString())
                .doesNotContain("admin@example.com")
                .doesNotContain("初期管理者のパスワード-0001")
                .contains("email=a***@example.com")
                .contains("password=***");
    }

    @Test
    @DisplayName("missing values are shown as null without failing")
    void missingValues() {
        assertThat(new InitialAdminProperties(null, null).toString())
                .isEqualTo("InitialAdminProperties[email=null, password=***]");
    }

    @Test
    @DisplayName("a value without an at sign is fully masked")
    void valueWithoutAtSign() {
        assertThat(new InitialAdminProperties("not-an-address", "x").toString())
                .doesNotContain("not-an-address")
                .contains("email=***");
    }
}
