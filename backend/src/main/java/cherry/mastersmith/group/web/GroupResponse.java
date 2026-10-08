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
package cherry.mastersmith.group.web;

import cherry.mastersmith.group.service.GroupView;
import java.time.Instant;

/**
 * 作ったグループの応答（契約 C6 の Group）。
 *
 * @param groupId グループの ID
 * @param name 名前
 * @param createdAt 作成の日時
 * @param updatedAt 更新の日時
 */
public record GroupResponse(long groupId, String name, Instant createdAt, Instant updatedAt) {

    /**
     * 業務処理の値から作る。
     *
     * @param view 作ったグループ
     * @return 応答
     */
    static GroupResponse from(GroupView view) {
        return new GroupResponse(view.groupId(), view.name(), view.createdAt(), view.updatedAt());
    }
}
