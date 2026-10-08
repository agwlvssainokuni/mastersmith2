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
package cherry.mastersmith.role.domain;

import java.util.Objects;

/** ロールの名前の検証の結果（{@code entities.md} の RoleName、BR1.1〜BR1.3）。 */
public sealed interface RoleNameValidation permits RoleNameValidation.Valid, RoleNameValidation.Invalid {

    /** 名前が正しくない理由。 */
    enum Reason {
        /** 前後の空白を取り除いた後が空（空・空白だけを含む）。 */
        INVALID_BLANK,
        /** 64 コードポイントを超える。 */
        INVALID_TOO_LONG,
        /** 制御文字（改行・タブを含む）がある。 */
        INVALID_CONTROL_CHARACTER
    }

    /**
     * 正しい名前。
     *
     * @param name 名前
     */
    record Valid(RoleName name) implements RoleNameValidation {

        /** 名前が null でないことを確かめる。 */
        public Valid {
            Objects.requireNonNull(name, "name");
        }
    }

    /**
     * 正しくない名前。入力の値を持たない（入力の誤りは監査にもログにも残さない。BR1.1〜BR1.3）。
     *
     * @param reason 理由
     */
    record Invalid(Reason reason) implements RoleNameValidation {

        /** 理由が null でないことを確かめる。 */
        public Invalid {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
