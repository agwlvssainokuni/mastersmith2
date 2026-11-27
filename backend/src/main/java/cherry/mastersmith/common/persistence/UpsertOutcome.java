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
package cherry.mastersmith.common.persistence;

/** 「あれば更新・無ければ挿入」の手順が、どの経路で行を書いたかの区分。 */
public enum UpsertOutcome {

    /** 最初の更新が 1 行以上に当たった。 */
    UPDATED,

    /** 更新が 0 行で、挿入が通った。 */
    INSERTED,

    /** 挿入が鍵の重なりになり、更新のやり直しが 1 行以上に当たった。 */
    UPDATED_AFTER_CONFLICT
}
