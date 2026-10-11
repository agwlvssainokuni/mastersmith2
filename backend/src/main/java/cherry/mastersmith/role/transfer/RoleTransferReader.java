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

import cherry.mastersmith.dsl.service.SafeYamlResult;
import cherry.mastersmith.dsl.service.YamlPositions;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.domain.PermissionTargetName;
import cherry.mastersmith.role.domain.PermissionValues;
import cherry.mastersmith.role.domain.RoleName;
import cherry.mastersmith.role.domain.RoleNameValidation;
import cherry.mastersmith.role.domain.TransferError;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * 上限つきの読み込みで読めた権限の YAML の木を検証し、中身（{@link RoleTransferDocument}）か誤りの一覧を返す純粋な関数（BR9.1・BR9.3・
 * BR9.5、{@code security-design.md} 5節）。
 *
 * <ul>
 *   <li>ルートは {@code version}（整数の 1 だけ）と {@code roles}（ロールの名前をキーにした対応表。空を許す）だけ。版が誤りなら、ほかを
 *       確かめずに {@code UNSUPPORTED_VERSION} だけを返す
 *   <li>ロールは {@code schemas}、スキーマは {@code main}・{@code create}・{@code delete}・{@code tables}、テーブルは
 *       {@code main}・{@code create}・{@code delete}・{@code columns}、カラムは主権限の値だけ。書かない項目は設定なし。入れ子は対応表
 *       （空の対応表を許す）
 *   <li>主権限は文字列の {@code NONE}・{@code READ}・{@code FULL} だけ、{@code create}・{@code delete} は真偽の値だけを受ける（文字列の
 *       {@code 'true'}・null・数は {@code INVALID_VALUE}）。{@code yes}・{@code on}・{@code no} は読み込みの部品が真偽として読むため受ける
 *   <li>ロールの名前は名前の規則（BR1）で正規化し、鍵が重なるロールは {@code DUPLICATE_ROLE}。対象の名前は BR4.1 の規則。名前の誤りが
 *       あったロール・対象の下は確かめない（同じ名前の誤りを重ねて出さない）
 *   <li>誤りは見つけた順に最大 {@value TransferError#MAX_ERRORS} 件まで集め、それ以上は探さない
 * </ul>
 *
 * <p>誤りは理由・位置・場所（キーを点でつないだ形。読み込みの部品の拒否の場所と同じ形）だけを持ち、入れた値を持たない。キーは YAML の
 * 型によらず書いたとおりの文字列として読める（試し U1）。
 */
public final class RoleTransferReader {

    /** 受け付ける版。 */
    public static final int SUPPORTED_VERSION = 1;

    private static final String ROOT = "";

    private static final Set<String> ROOT_KEYS = Set.of("version", "roles");

    private static final Set<String> ROLE_KEYS = Set.of("schemas");

    private static final Set<String> SCHEMA_KEYS = Set.of("main", "create", "delete", "tables");

    private static final Set<String> TABLE_KEYS = Set.of("main", "create", "delete", "columns");

    private RoleTransferReader() {}

    /** 検証の結果。 */
    public sealed interface Result permits Valid, Invalid {}

    /**
     * 検証を通った。
     *
     * @param document 中身
     */
    public record Valid(RoleTransferDocument document) implements Result {

        /** 中身が必須であることを確かめる。 */
        public Valid {
            Objects.requireNonNull(document, "document");
        }
    }

    /**
     * 中身に誤りがある。
     *
     * @param errors 誤りの一覧
     */
    public record Invalid(TransferErrors errors) implements Result {

        /** 一覧が必須であることを確かめる。 */
        public Invalid {
            Objects.requireNonNull(errors, "errors");
        }
    }

    /**
     * 読み込みの部品の拒否を、誤りの一覧（1件）に写す（BR9.3）。大きさの上限の超過は 413 で返すため、ここでは受けない。
     *
     * @param rejected 読み込みの拒否
     * @return 誤りの一覧
     * @throws IllegalArgumentException 大きさの上限の超過を渡したとき
     */
    public static TransferErrors rejected(SafeYamlResult.Rejected rejected) {
        Objects.requireNonNull(rejected, "rejected");
        TransferError.Reason reason =
                switch (rejected.kind()) {
                    case TOO_DEEP -> TransferError.Reason.TOO_DEEP;
                    case TOO_MANY_ALIASES -> TransferError.Reason.TOO_MANY_ALIASES;
                    case TAG_NOT_ALLOWED -> TransferError.Reason.TAG_NOT_ALLOWED;
                    case DUPLICATE_KEY -> TransferError.Reason.DUPLICATE_KEY;
                    case SYNTAX -> TransferError.Reason.SYNTAX;
                    case TOO_LARGE -> throw new IllegalArgumentException("大きさの上限の超過は 413 で返します");
                };
        return TransferErrors.of(new TransferError(reason, rejected.line(), rejected.column(), rejected.path()));
    }

    /**
     * 木を検証する。
     *
     * @param tree 読めた木と位置を引く口
     * @return 中身か誤りの一覧
     */
    public static Result read(RoleTransferTree tree) {
        Objects.requireNonNull(tree, "tree");
        Walk walk = new Walk(tree.positions());
        RoleTransferDocument document;
        try {
            document = walk.root(tree.tree());
        } catch (ErrorsFull full) {
            document = null;
        }
        if (!walk.errors.isEmpty()) {
            return new Invalid(new TransferErrors(walk.errors));
        }
        return new Valid(Objects.requireNonNull(document, "document"));
    }

    /** 誤りが上限に達したことの合図（このクラスの中だけで使い、外へ出さない）。 */
    private static final class ErrorsFull extends RuntimeException {

        private static final long serialVersionUID = 1L;

        ErrorsFull() {
            super(null, null, false, false);
        }
    }

    /** 1回の検証の状態。 */
    private static final class Walk {

        private final YamlPositions positions;

        private final List<TransferError> errors = new ArrayList<>();

        Walk(YamlPositions positions) {
            this.positions = positions;
        }

        RoleTransferDocument root(JsonNode root) {
            if (!root.isObject()) {
                error(TransferError.Reason.INVALID_VALUE, ROOT);
                return null;
            }
            unknownKeys(root, ROOT, ROOT_KEYS);
            JsonNode version = root.get("version");
            if (version == null) {
                missing(ROOT, "version");
            } else if (!isSupportedVersion(version)) {
                errors.clear();
                error(TransferError.Reason.UNSUPPORTED_VERSION, child(ROOT, "version"));
                return null;
            }
            JsonNode roles = root.get("roles");
            if (roles == null) {
                missing(ROOT, "roles");
                return null;
            }
            String rolesPointer = child(ROOT, "roles");
            if (!roles.isObject()) {
                error(TransferError.Reason.INVALID_VALUE, rolesPointer);
                return null;
            }
            List<RoleTransferDocument.Role> result = new ArrayList<>();
            Set<String> keys = new HashSet<>();
            for (Map.Entry<String, JsonNode> entry : roles.properties()) {
                String pointer = child(rolesPointer, entry.getKey());
                if (!(RoleName.parse(entry.getKey()) instanceof RoleNameValidation.Valid(RoleName name))) {
                    error(TransferError.Reason.INVALID_ROLE_NAME, pointer);
                    continue;
                }
                if (!keys.add(name.key())) {
                    error(TransferError.Reason.DUPLICATE_ROLE, pointer);
                    continue;
                }
                Map<PermissionTarget, PermissionValues> settings = new LinkedHashMap<>();
                role(entry.getValue(), pointer, settings);
                result.add(new RoleTransferDocument.Role(name, settings));
            }
            return new RoleTransferDocument(result);
        }

        private void role(JsonNode role, String pointer, Map<PermissionTarget, PermissionValues> settings) {
            if (!role.isObject()) {
                error(TransferError.Reason.INVALID_VALUE, pointer);
                return;
            }
            unknownKeys(role, pointer, ROLE_KEYS);
            JsonNode schemas = role.get("schemas");
            if (schemas == null) {
                return;
            }
            String schemasPointer = child(pointer, "schemas");
            if (!schemas.isObject()) {
                error(TransferError.Reason.INVALID_VALUE, schemasPointer);
                return;
            }
            for (Map.Entry<String, JsonNode> entry : schemas.properties()) {
                String schemaPointer = child(schemasPointer, entry.getKey());
                if (badName(entry.getKey(), schemaPointer)) {
                    continue;
                }
                schema(entry.getKey(), entry.getValue(), schemaPointer, settings);
            }
        }

        private void schema(
                String schemaName, JsonNode schema, String pointer, Map<PermissionTarget, PermissionValues> settings) {
            if (!schema.isObject()) {
                error(TransferError.Reason.INVALID_VALUE, pointer);
                return;
            }
            unknownKeys(schema, pointer, SCHEMA_KEYS);
            put(settings, PermissionTarget.schema(schemaName), values(schema, pointer));
            JsonNode tables = schema.get("tables");
            if (tables == null) {
                return;
            }
            String tablesPointer = child(pointer, "tables");
            if (!tables.isObject()) {
                error(TransferError.Reason.INVALID_VALUE, tablesPointer);
                return;
            }
            for (Map.Entry<String, JsonNode> entry : tables.properties()) {
                String tablePointer = child(tablesPointer, entry.getKey());
                if (badName(entry.getKey(), tablePointer)) {
                    continue;
                }
                table(schemaName, entry.getKey(), entry.getValue(), tablePointer, settings);
            }
        }

        private void table(
                String schemaName,
                String tableName,
                JsonNode table,
                String pointer,
                Map<PermissionTarget, PermissionValues> settings) {
            if (!table.isObject()) {
                error(TransferError.Reason.INVALID_VALUE, pointer);
                return;
            }
            unknownKeys(table, pointer, TABLE_KEYS);
            put(settings, PermissionTarget.table(schemaName, tableName), values(table, pointer));
            JsonNode columns = table.get("columns");
            if (columns == null) {
                return;
            }
            String columnsPointer = child(pointer, "columns");
            if (!columns.isObject()) {
                error(TransferError.Reason.INVALID_VALUE, columnsPointer);
                return;
            }
            for (Map.Entry<String, JsonNode> entry : columns.properties()) {
                String columnPointer = child(columnsPointer, entry.getKey());
                if (badName(entry.getKey(), columnPointer)) {
                    continue;
                }
                JsonNode value = entry.getValue();
                if (value.isObject()) {
                    error(
                            value.has("create") || value.has("delete")
                                    ? TransferError.Reason.COLUMN_AUX_NOT_ALLOWED
                                    : TransferError.Reason.INVALID_VALUE,
                            columnPointer);
                    continue;
                }
                Optional<MainPermission> main = mainOf(value);
                if (main.isEmpty()) {
                    error(TransferError.Reason.INVALID_VALUE, columnPointer);
                    continue;
                }
                put(
                        settings,
                        PermissionTarget.column(schemaName, tableName, entry.getKey()),
                        new PermissionValues(main.get(), null, null));
            }
        }

        /** スキーマ・テーブルの {@code main}・{@code create}・{@code delete} を読む（誤りは記録して設定なしとして扱う）。 */
        private PermissionValues values(JsonNode node, String pointer) {
            MainPermission main = null;
            JsonNode mainNode = node.get("main");
            if (mainNode != null) {
                Optional<MainPermission> parsed = mainOf(mainNode);
                if (parsed.isEmpty()) {
                    error(TransferError.Reason.INVALID_VALUE, child(pointer, "main"));
                } else {
                    main = parsed.get();
                }
            }
            return new PermissionValues(main, booleanOf(node, "create", pointer), booleanOf(node, "delete", pointer));
        }

        private Boolean booleanOf(JsonNode node, String key, String pointer) {
            JsonNode value = node.get(key);
            if (value == null) {
                return null;
            }
            if (!value.isBoolean()) {
                error(TransferError.Reason.INVALID_VALUE, child(pointer, key));
                return null;
            }
            return value.booleanValue();
        }

        private static Optional<MainPermission> mainOf(JsonNode value) {
            return value.isString() ? MainPermission.parse(value.stringValue()) : Optional.empty();
        }

        private static boolean isSupportedVersion(JsonNode version) {
            return version.isIntegralNumber() && version.canConvertToInt() && version.intValue() == SUPPORTED_VERSION;
        }

        private static void put(
                Map<PermissionTarget, PermissionValues> settings, PermissionTarget target, PermissionValues values) {
            if (!values.isEmpty()) {
                settings.put(target, values);
            }
        }

        private boolean badName(String name, String pointer) {
            if (PermissionTargetName.problemOf(name).isPresent()) {
                error(TransferError.Reason.INVALID_TARGET_NAME, pointer);
                return true;
            }
            return false;
        }

        private void unknownKeys(JsonNode node, String pointer, Set<String> known) {
            for (Map.Entry<String, JsonNode> entry : node.properties()) {
                if (!known.contains(entry.getKey())) {
                    error(TransferError.Reason.UNKNOWN_KEY, child(pointer, entry.getKey()));
                }
            }
        }

        /** 無い項目の誤り（位置は親の対応表、場所は無い項目の場所）。 */
        private void missing(String parentPointer, String key) {
            add(new TransferError(
                    TransferError.Reason.MISSING_KEY,
                    lineOf(parentPointer),
                    columnOf(parentPointer),
                    toPath(child(parentPointer, key))));
        }

        private void error(TransferError.Reason reason, String pointer) {
            add(new TransferError(reason, lineOf(pointer), columnOf(pointer), toPath(pointer)));
        }

        private void add(TransferError error) {
            errors.add(error);
            if (errors.size() >= TransferError.MAX_ERRORS) {
                throw new ErrorsFull();
            }
        }

        private Integer lineOf(String pointer) {
            return positions.find(pointer).map(YamlPositions.Location::line).orElse(null);
        }

        private Integer columnOf(String pointer) {
            return positions.find(pointer).map(YamlPositions.Location::column).orElse(null);
        }
    }

    /** 対応表の子の場所（JSON Pointer の形。読み込みの部品の位置の対応表のキーと同じ書き方）。 */
    static String child(String parent, String key) {
        return parent + "/" + key.replace("~", "~0").replace("/", "~1");
    }

    /** 利用者に返す場所（キーを点でつないだ形。文書の根は null）。 */
    static String toPath(String pointer) {
        if (pointer == null || pointer.isEmpty()) {
            return null;
        }
        List<String> names = new ArrayList<>();
        for (String token : pointer.substring(1).split("/", -1)) {
            names.add(token.replace("~1", "/").replace("~0", "~"));
        }
        String path = String.join(".", names);
        return path.isEmpty() ? null : path;
    }
}
