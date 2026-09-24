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
package cherry.mastersmith.dslmanage.service;

import cherry.mastersmith.common.error.domain.ProblemType;
import cherry.mastersmith.common.error.domain.ProblemTypeCatalog;
import cherry.mastersmith.dslmanage.domain.DslProblemTypes;
import java.util.List;
import org.springframework.stereotype.Component;

/** U4 の問題の種類を、起動時の収集の対象にする（BR8.2。重複は起動の失敗）。 */
@Component
public class DslProblemTypeCatalog implements ProblemTypeCatalog {

    @Override
    public List<ProblemType> problemTypes() {
        return DslProblemTypes.all();
    }
}
