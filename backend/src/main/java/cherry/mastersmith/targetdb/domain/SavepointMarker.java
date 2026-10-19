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
package cherry.mastersmith.targetdb.domain;

import java.sql.SQLException;

/**
 * 書き込みの文の直前にセーブポイントを置く口（Intent 261016-business-data-views の U2、reliability-design.md の 3節）。実装は書き込みの口
 * （{@code TargetWriteAccess}）が持ち、トランザクションの管理者の {@code createSavepoint} を呼ぶ。文の文字列を組み立てない。
 */
@FunctionalInterface
public interface SavepointMarker {

    /**
     * セーブポイントを置く（置き直すと、後のものが「書き込みの文の直前」になる）。
     *
     * @throws SQLException セーブポイントを置けなかったとき（メッセージは読まない）
     */
    void mark() throws SQLException;
}
