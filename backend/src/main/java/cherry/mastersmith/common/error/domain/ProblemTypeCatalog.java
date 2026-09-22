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
package cherry.mastersmith.common.error.domain;

import java.util.List;

/**
 * 問題の種類の定義の一覧。各機能は、この型の Bean を自分のパッケージに置いて自分の問題の種類を加える（BR5.16）。
 *
 * <p>U1 は起動時にすべての一覧を集め、code・slug が重複していれば起動を失敗させる。U1 のファイルは書き換えない。
 */
public interface ProblemTypeCatalog {

    /**
     * この機能が定義する問題の種類を返す。
     *
     * @return 問題の種類の一覧
     */
    List<ProblemType> problemTypes();
}
