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
package cherry.mastersmith.user.repository;

import cherry.mastersmith.user.domain.Preferences;
import java.util.Optional;

/** {@link UserRepository} のうち、問い合わせの注釈では書けない口（実装は {@link UserRepositoryImpl}）。 */
public interface UserRepositoryCustom {

    /**
     * 本人の氏名と表示の設定の4列を書き換え、同じ文で表示のタイムゾーンを書き換えるか変えないかを決め、保存した後の2列を返す
     * （BR3.3・BR3.4、Intent 261102-timezone-ui-polish の U2 の BR2.3・BR2.6・BR2.7）。
     *
     * <p>更新と結果の取り出しが 1 つの文で、5 項目が 1 つの文で確定する（内部DB の文の数は変えない。NFR2.1.2）。結果は JDBC の生成キー
     * （更新した行の 2 列）で受けるため、H2 でも PostgreSQL でも同じ書き方で動く。表示のタイムゾーンは {@code change} が真のときだけ
     * {@code zone}（null なら空）に置き、偽のときは列を書き換えず今の値を返す。値は位置の引数だけで渡し、文字列を連結しない。
     *
     * <p>書く前に持続化の文脈を書き出し（flush）、書いた後も文脈は書き換えない。同じトランザクションで先に読み込んだエンティティは古い値の
     * ままになる（呼び出し側はこの文の後に {@code findById} で読み直さない）。
     *
     * @param userId 本人の利用者 ID
     * @param preferences 決まりに合う4つの組
     * @param change 表示のタイムゾーンを書き換えるか（項目が無いときは偽）
     * @param zone 書き換えるときの名前（空にするときは null）
     * @return 保存した後の2列（本人がいなければ空）
     */
    Optional<UserRepository.SavedPreferences> savePreferences(
            long userId, Preferences preferences, boolean change, String zone);
}
