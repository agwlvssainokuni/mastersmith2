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

import cherry.mastersmith.user.domain.EmailAddress;
import cherry.mastersmith.user.domain.Password;
import cherry.mastersmith.user.domain.PasswordPolicy;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * 初期管理者の自動作成（BR1.1〜BR1.5、NFR6.1、NFR10.3）。
 *
 * <p>すべての部品を作った後（Flyway の適用の後）、Web サーバーが要求の受け付けを始める前に動く。設定が無い・正しくないときは
 * 作らずに WARN（足りない・正しくない項目と直し方）を出し、起動は続ける。既にいれば何もしない。パスワードの値はどのログにも
 * 出さない。
 */
@Component
public class InitialAdminInitializer implements SmartInitializingSingleton {

    /** 直し方の文。 */
    static final String RESOLUTION = "MASTERSMITH_AUTH_INITIAL_ADMIN_EMAIL と MASTERSMITH_AUTH_INITIAL_ADMIN_PASSWORD"
            + "（12 文字以上、UTF-8 で 72 バイト以内）を正しく設定して再起動すると、初期管理者が作成されます";

    private static final Logger LOGGER = LoggerFactory.getLogger(InitialAdminInitializer.class);

    private final InitialAdminProperties properties;

    private final UserAccountService userAccountService;

    /**
     * 初期管理者の作成の処理を作る。
     *
     * @param properties 初期管理者の設定
     * @param userAccountService UserAccount の業務処理
     */
    public InitialAdminInitializer(InitialAdminProperties properties, UserAccountService userAccountService) {
        this.properties = properties;
        this.userAccountService = userAccountService;
    }

    @Override
    public void afterSingletonsInstantiated() {
        createIfNeeded();
    }

    /**
     * 設定を検査し、必要なら初期管理者を作る。
     *
     * @return 作ったら true
     */
    boolean createIfNeeded() {
        String email = EmailAddress.normalize(properties.email());
        String password = properties.password();
        List<String> problems = problems(email, password);
        if (!problems.isEmpty()) {
            LOGGER.atWarn()
                    .addKeyValue("reason", String.join("、", problems))
                    .addKeyValue("resolution", RESOLUTION)
                    .log("初期管理者を作成しませんでした");
            return false;
        }
        if (userAccountService.existsByEmail(email)) {
            LOGGER.atInfo().addKeyValue("email", email).log("初期管理者は既にいるため、作成しませんでした");
            return false;
        }
        try {
            userAccountService.createUser(email, new Password(password), true);
        } catch (DataIntegrityViolationException e) {
            LOGGER.atInfo().addKeyValue("email", email).log("初期管理者は既にいるため、作成しませんでした");
            return false;
        }
        LOGGER.atInfo().addKeyValue("email", email).log("初期管理者を作成しました");
        return true;
    }

    /** 設定の問題を返す（パスワードの値は載せない）。 */
    private static List<String> problems(String email, String password) {
        List<String> problems = new ArrayList<>();
        if (email == null || email.isEmpty()) {
            problems.add("メールアドレス（email）が設定されていません");
        } else if (!EmailAddress.isValid(email)) {
            problems.add("メールアドレス（email）の形式が正しくありません");
        }
        if (password == null || password.isEmpty()) {
            problems.add("パスワード（password）が設定されていません");
        } else {
            if (!PasswordPolicy.hasMinimumLength(password)) {
                problems.add("パスワード（password）が 12 文字未満です");
            }
            if (!PasswordPolicy.fitsMaxBytes(password)) {
                problems.add("パスワード（password）が UTF-8 で 72 バイトを超えています");
            }
        }
        return problems;
    }
}
