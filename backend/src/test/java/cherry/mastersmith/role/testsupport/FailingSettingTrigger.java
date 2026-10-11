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
package cherry.mastersmith.role.testsupport;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * テストだけで内部DB に置く H2 のトリガー（計画の D-25、NFR3.2）。権限の設定の表に、カラムの名前が目印（{@value #MARK}）の行を書くと
 * 失敗させる。適用の途中（JDBC のバッチの中）の失敗を、本番のコードに失敗の口を足さずに起こすために使う。
 *
 * <p>H2 はテストのコンパイルの依存に無い（実行時だけの依存）ため、トリガーは H2 のソースのトリガー（{@code CREATE TRIGGER … AS $$…$$}。
 * H2 が実行時に JDK のコンパイラでコンパイルする）で置き、ビルドの設定は変えない。
 */
public final class FailingSettingTrigger {

    /** 書くと失敗させるカラムの名前の目印。 */
    public static final String MARK = "FAIL_AT_THIS_COLUMN";

    private static final String NAME = "TEST_FAILING_SETTING";

    private static final String SOURCE = "org.h2.api.Trigger create() { return new org.h2.api.Trigger() {"
            + " public void fire(java.sql.Connection connection, Object[] oldRow, Object[] newRow)"
            + " throws java.sql.SQLException {"
            + " if (newRow != null) { for (Object value : newRow) { if (\"" + MARK + "\".equals(value)) {"
            + " throw new java.sql.SQLException(\"テストの目印の行で失敗させました\", \"45000\"); } } } } }; }";

    private FailingSettingTrigger() {}

    /**
     * トリガーを置く。
     *
     * @param jdbc 内部DB
     */
    public static void install(JdbcTemplate jdbc) {
        jdbc.execute("CREATE TRIGGER IF NOT EXISTS " + NAME + " BEFORE INSERT ON permission_settings FOR EACH ROW AS $$"
                + SOURCE + "$$");
    }

    /**
     * トリガーを外す。
     *
     * @param jdbc 内部DB
     */
    public static void remove(JdbcTemplate jdbc) {
        jdbc.execute("DROP TRIGGER IF EXISTS " + NAME);
    }
}
