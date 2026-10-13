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

import cherry.mastersmith.dsl.parse.SafeYamlParser;
import cherry.mastersmith.dsl.service.DefaultSafeYamlReader;
import cherry.mastersmith.dsl.service.SafeYamlLimits;
import cherry.mastersmith.dsl.service.SafeYamlReader;
import cherry.mastersmith.dsl.service.SafeYamlResult;
import cherry.mastersmith.role.transfer.RoleTransferTree;
import java.nio.charset.StandardCharsets;

/**
 * 権限の YAML の部品の単体テストで、本番と同じ上限つきの読み込み（{@code SafeYamlReader}）で木を作る手伝い（BR9.3、計画の 9.3）。
 * 上限は本番の確かめ・適用と同じ値（大きさ 5 MiB・深さ 10・コレクションの別名 0・展開後の節 500,000）。
 */
public final class TransferYamlTrees {

    /** 本番と同じ上限。 */
    public static final SafeYamlLimits LIMITS = new SafeYamlLimits(5_242_880, 10, 0, 500_000);

    private static final SafeYamlReader READER = new DefaultSafeYamlReader(new SafeYamlParser());

    private TransferYamlTrees() {}

    /**
     * 本文を読む。
     *
     * @param yaml 本文
     * @return 読めた木か拒否
     */
    public static SafeYamlResult read(String yaml) {
        return READER.read(yaml.getBytes(StandardCharsets.UTF_8), LIMITS);
    }

    /**
     * 本文を読み、読めた木を返す（拒否なら失敗にする）。
     *
     * @param yaml 本文
     * @return 木
     */
    public static RoleTransferTree tree(String yaml) {
        return switch (read(yaml)) {
            case SafeYamlResult.Parsed(var tree, var positions) -> new RoleTransferTree(tree, positions);
            case SafeYamlResult.Rejected rejected -> throw new AssertionError("読み込みで拒否されました: " + rejected);
        };
    }

    /**
     * 本文を読み、拒否を返す（読めたら失敗にする）。
     *
     * @param yaml 本文
     * @return 拒否
     */
    public static SafeYamlResult.Rejected rejected(String yaml) {
        return switch (read(yaml)) {
            case SafeYamlResult.Rejected rejected -> rejected;
            case SafeYamlResult.Parsed parsed -> throw new AssertionError("読めてしまいました: " + parsed);
        };
    }
}
