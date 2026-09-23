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

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理者向け領域の表示可否を確かめる確認用 API（BR4.1。計画の C2）。
 *
 * <p>判定はアクセスの決まり（{@link AdminSecurityContributor}）に任せ、ここに届いた要求は管理者のものだけである。本体は成功
 * （204、内容なし）を返すだけで、DB を使わない。
 */
@RestController
public class AdminCheckController {

    /** 確認用 API のパス。 */
    public static final String PATH = "/api/admin/check";

    /** 管理者の要求に、内容なしの成功を返す。 */
    @GetMapping(PATH)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void check() {
        // 判定はアクセスの決まりが済ませているため、本体は成功を返すだけでよい。
    }
}
