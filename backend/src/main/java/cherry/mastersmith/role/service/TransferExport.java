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
package cherry.mastersmith.role.service;

import cherry.mastersmith.role.transfer.RoleTransferPayload;
import java.util.Objects;

/**
 * 権限の YAML の書き出しの結果（FS の 2.13、BR9.14・NFR2.9）。
 *
 * <p>{@link #toString()} は大きさと上限を超えたかだけを出す（NFR1.9）。
 *
 * @param yaml 書き出した本文（UTF-8）
 * @param exceedsImportLimit 本文の大きさが読み込みの上限を超えたか（応答のヘッダーで知らせる）
 */
public record TransferExport(RoleTransferPayload yaml, boolean exceedsImportLimit) {

    /** 本文が必須であることを確かめる。 */
    public TransferExport {
        Objects.requireNonNull(yaml, "yaml");
    }

    /** 大きさと上限を超えたかだけを出す。 */
    @Override
    public String toString() {
        return "TransferExport[bytes=" + yaml.size() + ", exceedsImportLimit=" + exceedsImportLimit + "]";
    }
}
