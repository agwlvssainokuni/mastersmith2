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

import java.io.InputStream;
import java.util.Objects;

/**
 * 確かめ・適用の要求の本文の流れ（{@code reliability-design.md} 2.6、NFR1.9）。画面入出力の層は本文を読まずにこの型で業務処理に渡し、
 * 業務処理が入口（{@link RoleTransferSlot}）を取った後に上限まで読む。
 *
 * <p>{@link #toString()} は中身を出さない。
 *
 * @param stream 本文の流れ（閉じるのは渡した側）
 */
public record RoleTransferBody(InputStream stream) {

    /** 流れが必須であることを確かめる。 */
    public RoleTransferBody {
        Objects.requireNonNull(stream, "stream");
    }

    /** 中身を出さない。 */
    @Override
    public String toString() {
        return "RoleTransferBody[stream]";
    }
}
