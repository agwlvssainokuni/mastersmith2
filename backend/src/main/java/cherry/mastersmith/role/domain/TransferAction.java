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
package cherry.mastersmith.role.domain;

/**
 * 権限の YAML の読み込みで、1つのロールをどう扱うか（BR9.6、{@code entities.md} の TransferRolePlan の action）。ロールの名前の鍵で今の
 * ロールと照らし、当たれば置き換え、当たらなければ作る。
 */
public enum TransferAction {
    /** 今のロールに当たらないため、ロールを作る。 */
    CREATE,
    /** 今のロールに当たるため、設定をファイルの状態に置き換える（名前は変えない）。 */
    REPLACE
}
