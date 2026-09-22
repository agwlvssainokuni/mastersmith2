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

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** パスワードのハッシュの仕組み（bcrypt、cost は設定。NFR2.1）。 */
@Configuration(proxyBeanMethods = false)
public class UserAccountConfig {

    /**
     * bcrypt のパスワードのハッシュの仕組みを作る。
     *
     * @param properties パスワードのハッシュの設定
     * @return パスワードのハッシュの仕組み
     */
    @Bean
    public PasswordEncoder passwordEncoder(PasswordProperties properties) {
        return new BCryptPasswordEncoder(properties.bcryptCost());
    }
}
