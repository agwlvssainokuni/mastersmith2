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

import cherry.mastersmith.dsl.domain.DbType;
import cherry.mastersmith.dsl.domain.DetailSetting;
import cherry.mastersmith.dsl.domain.DisplayName;
import cherry.mastersmith.dsl.domain.DslColumn;
import cherry.mastersmith.dsl.domain.DslForeignKey;
import cherry.mastersmith.dsl.domain.DslFormat;
import cherry.mastersmith.dsl.domain.DslMenuItem;
import cherry.mastersmith.dsl.domain.DslModel;
import cherry.mastersmith.dsl.domain.DslTable;
import cherry.mastersmith.dsl.domain.FormPart;
import cherry.mastersmith.dsl.domain.ListFormat;
import cherry.mastersmith.dsl.domain.ListSetting;
import cherry.mastersmith.dsl.domain.LookupListItem;
import cherry.mastersmith.dsl.domain.LookupSearchItem;
import cherry.mastersmith.dsl.domain.OptionItem;
import cherry.mastersmith.dsl.domain.OptionSource;
import cherry.mastersmith.dsl.domain.OptionSourceKind;
import cherry.mastersmith.dsl.domain.SearchOperator;
import cherry.mastersmith.dsl.domain.SearchSetting;
import cherry.mastersmith.dsl.domain.SortDirection;
import cherry.mastersmith.dsl.domain.Validation;
import cherry.mastersmith.dsl.domain.ValidationOrigin;
import cherry.mastersmith.dsl.domain.ValidationType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import tools.jackson.databind.JsonNode;

/**
 * 検証を通った JSON の形から、変更できないモデルを作る（BR5.2）。構文と意味の検証を通った形だけを受け取る前提で、形が合わない
 * ときはプログラムの誤りとして例外にする。
 */
final class DslModelMapper {

    private DslModelMapper() {}

    /**
     * モデルを作る。
     *
     * @param json 検証を通った JSON の形
     * @param dslHash 本文のバイト列の識別
     * @return モデル
     */
    static DslModel toModel(JsonNode json, String dslHash) {
        Map<String, DslTable> tables = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> table : json.path("tables").properties()) {
            tables.put(table.getKey(), table(table.getKey(), table.getValue()));
        }
        return new DslModel(dslHash, DslFormat.CURRENT_VERSION, list(json.path("menus"), DslModelMapper::menu), tables);
    }

    private static DslMenuItem menu(JsonNode node) {
        return new DslMenuItem(
                displayName(node.path("label")),
                text(node.path("icon")),
                text(node.path("table")),
                list(node.path("items"), DslModelMapper::menu));
    }

    private static DslTable table(String name, JsonNode node) {
        Map<String, DslColumn> columns = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> column : node.path("columns").properties()) {
            columns.put(column.getKey(), column(column.getKey(), column.getValue()));
        }
        return new DslTable(
                name,
                displayName(node.path("label")),
                node.path("view").booleanValue(),
                list(node.path("primaryKey"), JsonNode::stringValue),
                list(node.path("foreignKeys"), DslModelMapper::foreignKey),
                columns);
    }

    private static DslForeignKey foreignKey(JsonNode node) {
        return new DslForeignKey(
                list(node.path("columns"), JsonNode::stringValue),
                node.path("referencedTable").stringValue(),
                list(node.path("referencedColumns"), JsonNode::stringValue));
    }

    private static DslColumn column(String name, JsonNode node) {
        JsonNode search = node.path("search");
        JsonNode listSetting = node.path("list");
        JsonNode dbType = node.path("dbType");
        return new DslColumn(
                name,
                displayName(node.path("label")),
                new DbType(
                        dbType.path("name").stringValue(),
                        integer(dbType.path("length")),
                        integer(dbType.path("precision")),
                        integer(dbType.path("scale")),
                        dbType.path("nullable").booleanValue()),
                FormPart.fromDslName(node.path("formPart").stringValue()),
                new SearchSetting(
                        search.path("enabled").booleanValue(),
                        enumOf(search.path("operator"), SearchOperator::valueOf),
                        text(search.path("default")),
                        search.path("collapsed").booleanValue()),
                new ListSetting(
                        listSetting.path("visible").booleanValue(),
                        integer(listSetting.path("order")),
                        integer(listSetting.path("width")),
                        listSetting.path("sortable").booleanValue(),
                        enumOf(listSetting.path("defaultSort"), SortDirection::valueOf),
                        enumOf(listSetting.path("format"), ListFormat::valueOf)),
                new DetailSetting(node.path("detail").path("visible").booleanValue()),
                list(node.path("validations"), DslModelMapper::validation),
                options(node.path("options")));
    }

    private static Validation validation(JsonNode node) {
        ValidationType type = ValidationType.fromDslName(node.path("type").stringValue());
        JsonNode value = node.path("value");
        JsonNode message = node.path("message");
        return new Validation(
                type,
                type.hasValue() && type != ValidationType.PATTERN ? value.decimalValue() : null,
                type == ValidationType.PATTERN ? value.stringValue() : null,
                ValidationOrigin.valueOf(node.path("origin").stringValue()),
                message.isObject() ? displayName(message) : null);
    }

    private static OptionSource options(JsonNode node) {
        if (!node.isObject()) {
            return null;
        }
        return new OptionSource(
                OptionSourceKind.valueOf(node.path("source").stringValue()),
                list(
                        node.path("items"),
                        item -> new OptionItem(item.path("value").stringValue(), displayName(item.path("label")))),
                text(node.path("table")),
                text(node.path("valueColumn")),
                text(node.path("labelColumn")),
                list(
                        node.path("lookupSearch"),
                        item -> new LookupSearchItem(
                                item.path("column").stringValue(),
                                SearchOperator.valueOf(item.path("operator").stringValue()))),
                list(
                        node.path("lookupList"),
                        item -> new LookupListItem(
                                item.path("column").stringValue(),
                                item.path("order").intValue())));
    }

    private static DisplayName displayName(JsonNode node) {
        return new DisplayName(node.path("ja").stringValue(), node.path("en").stringValue());
    }

    private static <T> List<T> list(JsonNode array, Function<JsonNode, T> mapper) {
        List<T> values = new ArrayList<>();
        if (array.isArray()) {
            for (JsonNode element : array) {
                values.add(mapper.apply(element));
            }
        }
        return values;
    }

    private static String text(JsonNode node) {
        return node.isString() ? node.stringValue() : null;
    }

    private static Integer integer(JsonNode node) {
        return node.isNumber() ? node.intValue() : null;
    }

    private static <E> E enumOf(JsonNode node, Function<String, E> valueOf) {
        return node.isString() ? valueOf.apply(node.stringValue()) : null;
    }
}
