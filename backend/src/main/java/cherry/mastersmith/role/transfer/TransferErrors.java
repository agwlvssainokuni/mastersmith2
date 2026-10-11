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
package cherry.mastersmith.role.transfer;

import cherry.mastersmith.role.domain.TransferError;
import java.util.List;

/**
 * 権限の YAML の確かめの拒否（{@code ROLE_TRANSFER_INVALID}）の誤りの一覧（BR9.5、最大 {@value TransferError#MAX_ERRORS} 件）。
 *
 * <p>{@link #toString()} は件数だけを出し、場所（ロールと対象の名前を含みうる）を出さない（NFR1.9）。
 *
 * @param errors 誤り（見つけた順。1 件以上 {@value TransferError#MAX_ERRORS} 件以下）
 */
public record TransferErrors(List<TransferError> errors) {

    /** 写して持ち、件数を確かめる。 */
    public TransferErrors {
        errors = List.copyOf(errors);
        if (errors.isEmpty() || errors.size() > TransferError.MAX_ERRORS) {
            throw new IllegalArgumentException("誤りは 1 件以上 " + TransferError.MAX_ERRORS + " 件以下です");
        }
    }

    /**
     * 1件だけの一覧を作る（読み込みの拒否）。
     *
     * @param error 誤り
     * @return 一覧
     */
    public static TransferErrors of(TransferError error) {
        return new TransferErrors(List.of(error));
    }

    /** 件数だけを出す。 */
    @Override
    public String toString() {
        return "TransferErrors[count=" + errors.size() + "]";
    }
}
