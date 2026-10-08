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
package cherry.mastersmith.group.repository;

import java.util.Objects;

/**
 * グループの ID と名前の投影（一覧の行・詳細・要約の読み取り）。{@code group.service} が業務処理の型に写す（web の層が DB アクセスの
 * 層の型に触れないため）。
 *
 * @param groupId グループの ID
 * @param name 名前
 */
public record GroupRowView(long groupId, String name) {

    /** 名前が null でないことを確かめる。 */
    public GroupRowView {
        Objects.requireNonNull(name, "name");
    }
}
