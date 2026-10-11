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
package cherry.mastersmith.role.transfer;

import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.domain.PermissionTreeBuilder;
import java.util.Comparator;

/**
 * 権限の YAML の受け渡しで使う並び（BR9.9・BR9.14）。名前はどれもコードポイントの順（UTF-16 の単位の順ではない）。対象は
 * スキーマ → テーブル → カラムの順で、上の階層の対象（下位の名前が無いもの）を先に置く。
 */
public final class TransferOrder {

    /** 名前のコードポイントの順。 */
    public static final Comparator<String> NAME = PermissionTreeBuilder.CODE_POINT_ORDER;

    /** 対象の順（スキーマ → テーブル → カラム。下位の名前が無いものを先に置く）。 */
    public static final Comparator<PermissionTarget> TARGET = Comparator.comparing(PermissionTarget::schemaName, NAME)
            .thenComparing(PermissionTarget::tableName, Comparator.nullsFirst(NAME))
            .thenComparing(PermissionTarget::columnName, Comparator.nullsFirst(NAME));

    private TransferOrder() {}
}
