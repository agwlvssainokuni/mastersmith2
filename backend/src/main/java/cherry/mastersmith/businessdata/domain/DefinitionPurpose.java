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

/**
 * 定義の取得の用途（Intent 261029-reference-lookup-cleanup の U1、契約 C2 の {@code purpose}、entities.md の DefinitionPurpose）。
 * 作成・更新のときだけ参照先を読む（BR4.1・BR12.1）。本文に無いときは {@link #VIEW}。
 */
public enum DefinitionPurpose {
    /** 一覧・詳細の画面（参照先を読まない。既定）。 */
    VIEW,
    /** 作成の画面（作成で入力できる参照の項目だけ参照先を読む）。 */
    CREATE,
    /** 更新の画面（更新で入力できる参照の項目だけ参照先を読む）。 */
    UPDATE;

    /**
     * 参照先を読む用途かを返す（BR4.1）。
     *
     * @return 作成・更新なら true
     */
    public boolean readsReferences() {
        return this != VIEW;
    }

    /**
     * 参照元のカラムがこの用途で入力できるかを返す（BR3.3）。{@link #VIEW} では常に偽。
     *
     * @param column 参照元のカラム
     * @return 入力できるなら true
     */
    public boolean editable(ColumnAccess column) {
        return switch (this) {
            case VIEW -> false;
            case CREATE -> column.editableOnCreate();
            case UPDATE -> column.editableOnUpdate();
        };
    }
}
