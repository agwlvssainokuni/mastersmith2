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

import cherry.mastersmith.businessdata.domain.ReadRejection;
import cherry.mastersmith.businessdata.domain.TableAccessView;
import cherry.mastersmith.role.domain.PermissionSnapshot;
import java.util.Objects;

/** 権限の写しと名前の照合の結果（判定の順の ②〜④。BR1.2〜BR1.4）。 */
public sealed interface TableAccessResolution {

    /**
     * 判定の材料を作れた（テーブルの主権限が READ 以上）。
     *
     * <p>取った権限の写しも一緒に返し、参照先の判定の材料を同じ写しで作る（取り直さない。Intent 261029-reference-lookup-cleanup の
     * U1、BR2.2）。写しは DSL と権限の設定だけを持ち、行の値を持たない。
     *
     * @param view 判定の材料
     * @param snapshot この要求で1回だけ取った権限の写し
     */
    record Resolved(TableAccessView view, PermissionSnapshot snapshot) implements TableAccessResolution {

        /** 判定の材料と写しが必須であることを確かめる。 */
        public Resolved {
            Objects.requireNonNull(view, "view は必須です");
            Objects.requireNonNull(snapshot, "snapshot は必須です");
        }

        /** 判定の材料だけを出す（写しの中身を出さない）。 */
        @Override
        public String toString() {
            return "Resolved[view=" + view + "]";
        }
    }

    /**
     * 拒否した（DSL が無い・DSL に無い名前・テーブルが NONE）。
     *
     * @param rejection 拒否
     */
    record Rejected(ReadRejection rejection) implements TableAccessResolution {

        /** 拒否が必須であることを確かめる。 */
        public Rejected {
            Objects.requireNonNull(rejection, "rejection は必須です");
        }
    }
}
