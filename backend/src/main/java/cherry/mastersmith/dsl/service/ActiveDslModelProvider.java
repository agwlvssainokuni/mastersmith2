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
package cherry.mastersmith.dsl.service;

import cherry.mastersmith.dsl.domain.ActiveDsl;

/**
 * 適用中のモデルの提供口（契約 C8 の {@code ActiveDslModelProvider}。後続の Intent I・J・K が使う。読み取りだけ）。
 *
 * <p>呼ぶたびに、その時点の適用中のモデルを返す（途中の状態を返さない）。返したモデルは変更できない値で、取り出した側が持ち
 * 続けても差し替えの影響を受けない。
 *
 * <p>利用者への注意: モデルのバリデーションの {@code pattern} は、正しい正規表現かを確かめただけで、値に当てはめたことは無い。
 * 当てはめるときは、重い正規表現で止まらないよう時間の上限つきで当てはめること（U2 の NFR 要件の 4節）。
 */
public interface ActiveDslModelProvider {

    /**
     * 今の適用中のモデルを返す。
     *
     * @return ある（モデルと識別）か、無いか（BR5.4）
     */
    ActiveDsl current();
}
