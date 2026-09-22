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
package cherry.mastersmith.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.auth.domain.ClientInfo;
import cherry.mastersmith.auth.domain.RefreshTokenValue;
import cherry.mastersmith.common.error.domain.BusinessException;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.domain.Password;
import cherry.mastersmith.user.service.UserAccountService;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** 同じリフレッシュトークンの同時の更新で、成功が1つだけになることの結合テスト（NFR9.2、BR5.6）。 */
@SpringBootTest(properties = "mastersmith.auth.password.bcrypt-cost=4")
class RefreshConcurrencyIT {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @Autowired
    LoginService loginService;

    @Autowired
    TokenRefreshService refreshService;

    @Autowired
    UserAccountService userAccountService;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @AfterEach
    void shutdown() {
        executor.shutdownNow();
    }

    @RepeatedTest(3)
    @DisplayName("of two simultaneous refreshes with the same token exactly one succeeds")
    void onlyOneSucceeds() throws Exception {
        String email = "refresh-" + UUID.randomUUID() + "@example.com";
        userAccountService.createUser(email, new Password("正しいパスワード-1234"), false);
        RefreshTokenValue token = loginService
                .login(new LoginCommand(email, new Password("正しいパスワード-1234")), new ClientInfo("127.0.0.1", "IT", null))
                .refreshToken();
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            results.add(executor.submit(() -> {
                start.await(30, TimeUnit.SECONDS);
                try {
                    refreshService.refresh(token);
                    return true;
                } catch (BusinessException e) {
                    return false;
                }
            }));
        }
        start.countDown();

        int succeeded = 0;
        for (Future<Boolean> result : results) {
            succeeded += result.get(30, TimeUnit.SECONDS) ? 1 : 0;
        }
        assertThat(succeeded).isEqualTo(1);
    }
}
