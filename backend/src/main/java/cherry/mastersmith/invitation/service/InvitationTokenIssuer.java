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
package cherry.mastersmith.invitation.service;

import cherry.mastersmith.invitation.domain.InvitationToken;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/** 招待のトークンを作る（BR3.1、NFR1.1）。暗号学的に安全な乱数を1つ持ち、作成と送り直しのたびに新しく作る。 */
@Component
public class InvitationTokenIssuer {

    private final SecureRandom random = new SecureRandom();

    /**
     * 新しいトークンを作る。
     *
     * @return 新しいトークン（文字列にすると伏せる）
     */
    public InvitationToken issue() {
        return InvitationToken.generate(random);
    }
}
