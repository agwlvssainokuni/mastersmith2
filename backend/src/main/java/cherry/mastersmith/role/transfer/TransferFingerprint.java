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

import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.domain.PermissionValues;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 変わる点の一覧の指紋（BR9.9）と、本文の SHA-256（BR11.7）。
 *
 * <p>指紋は、ロールの鍵の順に並べたロールごとに「名前・作るか置き換えるか・置き換えるロールの ID」と、対象の順に並べた変わる対象ごとに
 * 「スキーマ・テーブル・カラムの名前・前の値・後の値・変わり方・今の DSL にあるか」を並べ、最後に適用中の DSL の識別（無ければ無し）を
 * 足した文字列の SHA-256 の 16 進数の小文字。項目はどれも「文字数:値;」（無い値は「~;」）の形で区切り、名前に区切りの文字が入っても
 * 別の並びと同じ文字列にならないようにする。並びはこの部品の外で決まる（{@link TransferPlanner} が並べてから渡す）ため、YAML の
 * 対応表の順に依らない。
 */
public final class TransferFingerprint {

    private static final Pattern WELL_FORMED = Pattern.compile("[0-9a-f]{64}");

    private TransferFingerprint() {}

    /**
     * 指紋を作る。
     *
     * @param roles ロールごとの変わる点（並べた後のもの）
     * @param dslHash 適用中の DSL の識別（無ければ null）
     * @return 指紋（16 進数の小文字 64 文字）
     */
    public static String of(List<TransferRolePlan> roles, String dslHash) {
        StringBuilder text = new StringBuilder();
        for (TransferRolePlan role : roles) {
            field(text, "ROLE");
            field(text, role.name());
            field(text, role.action().name());
            field(text, role.roleId() == null ? null : role.roleId().toString());
            for (TransferChange change : role.changes()) {
                PermissionTarget target = change.target();
                field(text, "CHANGE");
                field(text, target.schemaName());
                field(text, target.tableName());
                field(text, target.columnName());
                values(text, change.before());
                values(text, change.after());
                field(text, change.reason().name());
                field(text, Boolean.toString(change.inCurrentDsl()));
            }
        }
        field(text, "DSL");
        field(text, dslHash);
        return sha256Hex(text.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 指紋の形（16 進数の小文字 64 文字）かを確かめる。
     *
     * @param value 値（null は偽）
     * @return 指紋の形なら真
     */
    public static boolean isWellFormed(String value) {
        return value != null && WELL_FORMED.matcher(value).matches();
    }

    /**
     * バイト列の SHA-256 の 16 進数の小文字を返す。
     *
     * @param bytes バイト列
     * @return 16 進数の小文字 64 文字
     */
    public static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 を使えません", e);
        }
    }

    private static void values(StringBuilder text, PermissionValues values) {
        MainPermission main = values.main();
        field(text, main == null ? null : main.name());
        field(text, values.create() == null ? null : values.create().toString());
        field(text, values.delete() == null ? null : values.delete().toString());
    }

    private static void field(StringBuilder text, String value) {
        if (value == null) {
            text.append("~;");
        } else {
            text.append(value.length()).append(':').append(value).append(';');
        }
    }
}
