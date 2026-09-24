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
package cherry.mastersmith.targetdb.testsupport;

/**
 * 標準のテストのスキーマの名前（3種類の DB で同じ）。
 *
 * <ul>
 *   <li>{@link #CUSTOMER}: 大文字・小文字の混じった名前、コメント（表・カラム）、空白だけのコメント、既定値、NULL を許すかの両方、
 *       長さ・精度・桁数のある型、1行のデータ。
 *   <li>{@link #ORDER_ITEMS}: カラムの順と違う順の複合の主キー、同じスキーマへの外部キー（{@link #FK_CUSTOMER}）、別のスキーマ
 *       （{@link #OTHER_TABLE}）への外部キー（写しに含めない）。コメント無し。
 *   <li>{@link #SYMBOL_TABLE}: 引用符・空白・セミコロンなど SQL の記号を含む名前の表とカラム（AC1.1.9）。
 *   <li>{@link #CUSTOMER_VIEW}: ビュー。
 * </ul>
 */
public final class TargetDbFixture {

    /** 顧客の表（大文字・小文字の混じった名前）。 */
    public static final String CUSTOMER = "Customer";

    /** 顧客の表の主キーのカラム。 */
    public static final String CUSTOMER_ID = "Customer_Id";

    /** 顧客の表のコメント。 */
    public static final String CUSTOMER_COMMENT = "顧客";

    /** 名前のカラムのコメント。 */
    public static final String NAME_COMMENT = "顧客の名前";

    /** 明細の表。 */
    public static final String ORDER_ITEMS = "order_items";

    /** 明細から顧客への外部キーの名前。 */
    public static final String FK_CUSTOMER = "fk_order_customer";

    /** 明細から別のスキーマへの外部キーの名前（写しに含めない）。 */
    public static final String FK_OTHER = "fk_order_other";

    /** 記号を含む名前の表（引用符・二重引用符・逆引用符・空白・セミコロン）。 */
    public static final String SYMBOL_TABLE = "sym 'q' \"dq\" `bq` ; DROP TABLE Customer; --x";

    /** 記号を含む名前のカラム。 */
    public static final String SYMBOL_COLUMN = "col \"x\"; 'y' `z`";

    /** ビュー。 */
    public static final String CUSTOMER_VIEW = "customer_view";

    /** 別のスキーマの表。 */
    public static final String OTHER_TABLE = "other_table";

    private TargetDbFixture() {}
}
