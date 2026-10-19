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
package cherry.mastersmith.businessdata.service;

import cherry.mastersmith.businessdata.domain.BusinessDataProblemTypes;
import cherry.mastersmith.common.error.domain.ProblemType;
import cherry.mastersmith.common.error.domain.ProblemTypeCatalog;
import java.util.List;
import org.springframework.stereotype.Component;

/** 業務データの問題の種類を、起動時の収集の対象にする（BR1.9。code の重複は起動時の検査で起動を止める）。 */
@Component
public class BusinessDataProblemTypeCatalog implements ProblemTypeCatalog {

    @Override
    public List<ProblemType> problemTypes() {
        return BusinessDataProblemTypes.all();
    }
}
