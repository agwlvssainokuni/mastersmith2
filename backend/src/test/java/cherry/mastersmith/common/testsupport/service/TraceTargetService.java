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
package cherry.mastersmith.common.testsupport.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Service;

/**
 * メソッドの呼び出しの追跡の結合テストのための業務処理の層の役（テストのソースの中だけに置く）。
 *
 * <p>{@code mastersmith.test-fixture.trace-target=true} を設定したテストだけで有効になる。
 */
@Service
@ConditionalOnBooleanProperty("mastersmith.test-fixture.trace-target")
public class TraceTargetService {

    /**
     * あいさつの文を返す。
     *
     * @param name 名前
     * @return あいさつの文
     */
    public String greet(String name) {
        return "こんにちは、" + name;
    }

    /**
     * 例外を起こす。
     *
     * @param reason 例外のメッセージ
     * @return 返らない
     */
    public String fail(String reason) {
        throw new IllegalStateException(reason);
    }
}
