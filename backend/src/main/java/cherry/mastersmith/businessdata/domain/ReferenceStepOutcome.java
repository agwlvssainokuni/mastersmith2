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

import java.util.List;
import java.util.Objects;

/**
 * 保存前の参照の段（判定の順の ⑩）の結果（Intent 261029-reference-lookup-cleanup の U2、entities.md の ReferenceStepOutcome、
 * BR1.3・BR1.4・BR4.4・BR4.5）。業務処理の中だけで使う。どの場合も参照先・参照元の行の値を持たない。
 */
public sealed interface ReferenceStepOutcome {

    /** 失敗が起きた読み取り（診断の WARN の {@code stage} の値。observability-design.md 2.1）。 */
    enum Stage {
        /** 読み取りの接続を借りる段。 */
        BORROW,
        /** 更新前の行の読み取り。 */
        PREVIOUS_ROW,
        /** 必須の参照項目の状態の読み取り（{@code countState}）。 */
        COUNT_STATE,
        /** 参照の確かめ（{@code check}）。 */
        CHECK
    }

    /** 拒否が無く、書き込みへ進む。 */
    record Proceed() implements ReferenceStepOutcome {}

    /**
     * 保留した既存の検証の誤りを戻した（400 {@code VALIDATION_FAILED}。BR3.5）。
     *
     * @param fieldErrors カラムごとの誤り（1つ以上。値を持たない）
     */
    record Invalid(List<BusinessDataFieldError> fieldErrors) implements ReferenceStepOutcome {

        /** 誤りが1つ以上あり、一覧を変更できないものにする。 */
        public Invalid {
            fieldErrors = nonEmpty(fieldErrors);
        }

        /** 誤りの数だけを出す。 */
        @Override
        public String toString() {
            return "Invalid[fieldErrors=" + fieldErrors.size() + "]";
        }
    }

    /**
     * 参照の確かめで拒否した（409 {@code BUSINESS_REFERENCE_NOT_FOUND}。BR2.7・BR3.3・BR3.4・BR4.4）。
     *
     * @param fieldErrors カラムごとの誤り（{@code REFERENCE_NOT_FOUND}・{@code NOT_EDITABLE_NOT_NULL}。1つ以上）
     */
    record Rejected(List<BusinessDataFieldError> fieldErrors) implements ReferenceStepOutcome {

        /** 誤りが1つ以上あり、一覧を変更できないものにする。 */
        public Rejected {
            fieldErrors = nonEmpty(fieldErrors);
        }

        /** 誤りの数だけを出す。 */
        @Override
        public String toString() {
            return "Rejected[fieldErrors=" + fieldErrors.size() + "]";
        }
    }

    /**
     * 更新前の行が無い・2行以上（BR2.6）。
     *
     * @param rejection {@link WriteRejection#ROW_NOT_FOUND} か {@link WriteRejection#ROW_NOT_UNIQUE}
     */
    record RowMissing(WriteRejection rejection) implements ReferenceStepOutcome {

        /** 行が無い・2行以上の拒否だけを受け付ける。 */
        public RowMissing {
            Objects.requireNonNull(rejection, "rejection は必須です");
            if (rejection != WriteRejection.ROW_NOT_FOUND && rejection != WriteRejection.ROW_NOT_UNIQUE) {
                throw new IllegalArgumentException("行が無い・2行以上の拒否だけです");
            }
        }
    }

    /**
     * 対象DB の失敗（BR4.5）。
     *
     * @param failure メッセージを捨てた失敗の値
     * @param stage 失敗が起きた読み取り
     */
    record Failed(ReferenceReadFailure failure, Stage stage) implements ReferenceStepOutcome {

        /** 必須の値を確かめる。 */
        public Failed {
            Objects.requireNonNull(failure, "failure は必須です");
            Objects.requireNonNull(stage, "stage は必須です");
        }
    }

    private static List<BusinessDataFieldError> nonEmpty(List<BusinessDataFieldError> errors) {
        Objects.requireNonNull(errors, "fieldErrors は必須です");
        List<BusinessDataFieldError> copy = List.copyOf(errors);
        if (copy.isEmpty()) {
            throw new IllegalArgumentException("誤りは1つ以上です");
        }
        return copy;
    }
}
