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
package cherry.mastersmith.access.web;

import cherry.mastersmith.common.security.ApiDefaultAccess;
import org.springframework.stereotype.Component;

/**
 * {@code /api/**} の既定をログイン必須にする（BR1.3、NFR3.2）。U1 の差し込み口「API の既定の扱い」にアプリで1つだけ置く。
 *
 * <p>これにより、ログインなしで呼べる API は U1（{@code /actuator/health}・{@code /api/problems/**}）と
 * U2（{@code /api/auth/login}・{@code /api/auth/session/**}）が明示した一覧だけになる。既定の拒否は設定で切り替えられない
 * （テストの中だけの仕組みは {@code PublicApiTestRules}）。
 */
@Component
public class AdminApiDefaultAccess implements ApiDefaultAccess {

    @Override
    public boolean requireAuthentication() {
        return true;
    }
}
