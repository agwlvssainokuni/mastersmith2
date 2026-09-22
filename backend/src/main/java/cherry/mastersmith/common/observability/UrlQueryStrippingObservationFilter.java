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
package cherry.mastersmith.common.observability;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 観測の記録（トレースの属性・指標のタグ）から、要求の URL の問い合わせの部分（{@code ?} 以降）を取り除く
 * （BR4.3、security-design 5章）。問い合わせの部分だけを表す属性は丸ごと取り除く。
 */
@Component
public class UrlQueryStrippingObservationFilter implements ObservationFilter {

    /** URL を値に持つ属性の名前。 */
    static final Set<String> URL_KEYS = Set.of("http.url", "url.full", "url.path", "http.target", "uri");

    /** 問い合わせの部分だけを表す属性の名前。 */
    static final Set<String> QUERY_KEYS = Set.of("url.query", "http.query");

    @Override
    public Observation.Context map(Observation.Context context) {
        for (KeyValue keyValue : context.getHighCardinalityKeyValues()) {
            String stripped = strip(keyValue);
            if (stripped != null) {
                context.addHighCardinalityKeyValue(KeyValue.of(keyValue.getKey(), stripped));
            }
        }
        for (KeyValue keyValue : context.getLowCardinalityKeyValues()) {
            String stripped = strip(keyValue);
            if (stripped != null) {
                context.addLowCardinalityKeyValue(KeyValue.of(keyValue.getKey(), stripped));
            }
        }
        for (String key : QUERY_KEYS) {
            context.removeHighCardinalityKeyValue(key);
            context.removeLowCardinalityKeyValue(key);
        }
        return context;
    }

    /** URL の属性で問い合わせの部分があれば、それを除いた値を返す。変えないときは null。 */
    private static String strip(KeyValue keyValue) {
        if (!URL_KEYS.contains(keyValue.getKey())) {
            return null;
        }
        String value = keyValue.getValue();
        int index = value.indexOf('?');
        return index < 0 ? null : value.substring(0, index);
    }
}
