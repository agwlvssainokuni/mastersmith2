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
package cherry.mastersmith.dsl.parse;

import java.util.ArrayList;
import java.util.List;

/**
 * 位置の対応表のキー（JSON Pointer の形。例 {@code /tables/dept_mst/columns/code}、networknt の誤りの場所と同じ形）と、利用者に
 * 返す場所（点でつないだ形。例 {@code tables.dept_mst.columns.code}）の組み立て。文書の根は {@code ""}。
 */
public final class JsonPointers {

    /** 文書の根。 */
    public static final String ROOT = "";

    private JsonPointers() {}

    /**
     * 対応表の子の場所を作る。
     *
     * @param parent 親の場所
     * @param key キー
     * @return 子の場所
     */
    public static String child(String parent, String key) {
        return parent + "/" + key.replace("~", "~0").replace("/", "~1");
    }

    /**
     * 並びの要素の場所を作る。
     *
     * @param parent 親の場所
     * @param index 位置（0 から）
     * @return 要素の場所
     */
    public static String child(String parent, int index) {
        return parent + "/" + index;
    }

    /**
     * 利用者に返す、点でつないだ場所にする。
     *
     * @param pointer 場所（JSON Pointer の形）
     * @return 点でつないだ場所（文書の根は null）
     */
    public static String toPath(String pointer) {
        if (pointer == null || pointer.isEmpty()) {
            return null;
        }
        List<String> names = new ArrayList<>();
        for (String token : pointer.substring(1).split("/", -1)) {
            names.add(token.replace("~1", "/").replace("~0", "~"));
        }
        return String.join(".", names);
    }
}
