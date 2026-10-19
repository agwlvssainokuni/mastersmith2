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
package cherry.mastersmith.businessdata.domain;

/** 行の SQL のどの段階で失敗したか（NFR 設計のレビュー R-01・R-03）。 */
public enum RowQueryStage {
    /** 貸し出しの始めの文（MySQL・MariaDB の {@code SET time_zone}）。 */
    SESSION_INIT,
    /** 文を作って値をバインドする段階。 */
    BIND,
    /** 文を実行する段階。 */
    EXECUTE,
    /** 結果を読んで値を文字にする段階。 */
    READ
}
