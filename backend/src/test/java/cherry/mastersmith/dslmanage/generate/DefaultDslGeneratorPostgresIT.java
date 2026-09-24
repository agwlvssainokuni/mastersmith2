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
package cherry.mastersmith.dslmanage.generate;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.dsl.domain.DslColumn;
import cherry.mastersmith.dsl.domain.DslTable;
import cherry.mastersmith.dsl.domain.FormPart;
import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import java.util.List;
import java.util.function.UnaryOperator;

/** PostgreSQL での既定の DSL の生成の結合テスト（共通の確かめは {@link AbstractDefaultDslGeneratorIT}）。 */
class DefaultDslGeneratorPostgresIT extends AbstractDefaultDslGeneratorIT {

    @Override
    DatabaseProduct product() {
        return DatabaseProduct.POSTGRESQL;
    }

    @Override
    List<String> fixtureSql(UnaryOperator<String> q) {
        String dept = "%s." + q.apply("Dept_Mst");
        return List.of(
                "CREATE TABLE " + dept + " (code VARCHAR(10) NOT NULL PRIMARY KEY, name VARCHAR(300) NOT NULL,"
                        + " note TEXT NULL, amount NUMERIC(10,2) NOT NULL DEFAULT 0, born DATE NULL,"
                        + " updated_at TIMESTAMPTZ NULL, hm TIME NULL, flag BOOLEAN NOT NULL DEFAULT false,"
                        + " bits BIT(1) NULL)",
                "COMMENT ON TABLE " + dept + " IS '部署'",
                "COMMENT ON COLUMN " + dept + ".code IS '部署コード'",
                "CREATE TABLE %s.emp (id INTEGER NOT NULL PRIMARY KEY, dept_code VARCHAR(10) NULL,"
                        + " CONSTRAINT fk_emp_dept FOREIGN KEY (dept_code) REFERENCES " + dept + " (code))",
                "COMMENT ON COLUMN %s.emp.dept_code IS '" + SYMBOL_COMMENT.replace("'", "''") + "'",
                "CREATE TABLE %s." + q.apply(SYMBOL_TABLE) + " (" + q.apply(SYMBOL_COLUMN)
                        + " INTEGER NOT NULL PRIMARY KEY)",
                "CREATE TABLE %s.a_first (id INTEGER NOT NULL PRIMARY KEY, created TIMESTAMP NULL)",
                "CREATE VIEW %s.v_emp AS SELECT id, dept_code FROM %s.emp");
    }

    @Override
    void assertProductSpecificColumns(DslTable dept) {
        assertThat(dept.columns().get("flag").dbType().name()).isEqualTo("bool");
        assertThat(dept.columns().get("updated_at").dbType().name()).isEqualTo("timestamptz");
        DslColumn bits = dept.columns().get("bits");
        assertThat(bits.formPart()).as("PostgreSQL の bit は分類しない").isEqualTo(FormPart.TEXT);
        assertThat(bits.search().enabled()).isFalse();
        assertThat(bits.detail().visible()).isFalse();
    }
}
