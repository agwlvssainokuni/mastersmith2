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
package cherry.mastersmith.mail.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 送信の依頼（契約 C1 の MailRequest）。呼び出し元（U3）が作って渡す。
 *
 * <p>値の確かめは {@link MailRequestValidation} が送信のときに行う（BR3.1〜BR3.4）。ここでは確かめず、渡された値をそのまま持つ
 * （null の差し込む値も、拒否するために持つ）。差し込む値の対応表は外から変えられない写しにする。文字列にするときは templateId と
 * language だけを出し、宛先と差し込む値は出さない（NFR1.2。メソッドの呼び出しの追跡の TRACE に出るため）。
 *
 * @param templateId テンプレートの識別（例 {@code invitation}）
 * @param language 言語（{@code ja}・{@code en}）
 * @param to 宛先（正規化済みのメールアドレス、1人だけ）
 * @param variables 差し込む値（名前と値）
 */
public record MailRequest(String templateId, String language, String to, Map<String, String> variables) {

    /** 差し込む値を変えられない写しにする（null の値を含められるよう {@code Map.copyOf} は使わない）。 */
    public MailRequest {
        variables = variables == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(variables));
    }

    /** 宛先と差し込む値を伏せて文字列にする（NFR1.2）。 */
    @Override
    public String toString() {
        return "MailRequest[templateId=" + templateId + ", language=" + language + "]";
    }
}
