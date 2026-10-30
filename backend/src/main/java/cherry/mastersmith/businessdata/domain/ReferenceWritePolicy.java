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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 保存前の参照の段（判定の順の ⑩）の判定の純粋な関数（Intent 261029-reference-lookup-cleanup の U2、logical-components.md 1節、
 * security-design.md 6節）。対象DB を読まず、値をログに出さない。
 *
 * <ul>
 *   <li>更新前と本文の値が同じか（BR2.5。U1 の {@link ReferencePolicy#sameReferenceValue} を使い回す）
 *   <li>作成の必須の参照項目の状態と本文から、どうするかを決める（BR3.2〜BR3.5、functional-spec.md 4.3節）
 *   <li>確かめ・状態の結果の写しと、⑩ の結果のまとめ（失敗 → 行が無い → 400 → 409 → 進む。BR1.3）と、既存の拒否への写し
 *       （BR1.4・BR4.4）
 * </ul>
 */
public final class ReferenceWritePolicy {

    private ReferenceWritePolicy() {}

    /** 作成の必須の参照項目について決めたこと（functional-spec.md 4.3節の表の升目）。 */
    public enum RequiredDecision {
        /** 通常の確かめに回す（使えて値あり）。 */
        CHECK,
        /** 409 {@code REFERENCE_NOT_FOUND}（読めない・多すぎて値あり。問い合わせない）。 */
        REFERENCE_NOT_FOUND,
        /** 409 {@code NOT_EDITABLE_NOT_NULL}（読めない・多すぎて空。field はカラムの名前）。 */
        NOT_EDITABLE_NOT_NULL,
        /** 保留した既存の検証の誤りを戻す（使えて空で、保留した誤りがある。400）。 */
        RETURN_HELD,
        /** 確かめずに既存の流れのまま進む（使えて空で、保留した誤りが無い）。 */
        PROCEED
    }

    /**
     * 更新前の値と本文の値が同じか（BR2.5）。更新前の値が読めない・切られた（{@code null}）とき、どちらかが NULL のときは「違う」
     * （安全側）。
     *
     * @param previous 更新前の値（読めなければ null）
     * @param body 本文の型つきの値
     * @return 同じなら true（確かめない）
     */
    public static boolean unchanged(BoundValue previous, BoundValue body) {
        return ReferencePolicy.sameReferenceValue(previous, body);
    }

    /**
     * 作成の必須の参照項目について、状態と本文から決める（BR3.3〜BR3.5）。
     *
     * @param state 状態と本文の状態
     * @param held ⑨ で保留した誤りがあるなら true
     * @return 決めたこと
     */
    public static RequiredDecision decide(RequiredReferenceState state, boolean held) {
        Objects.requireNonNull(state, "state は必須です");
        boolean value = state.bodyState() == RequiredReferenceState.BodyState.VALUE;
        return switch (state.status()) {
            case NO_PERMISSION, TOO_MANY ->
                value ? RequiredDecision.REFERENCE_NOT_FOUND : RequiredDecision.NOT_EDITABLE_NOT_NULL;
            case AVAILABLE -> {
                if (value) {
                    yield RequiredDecision.CHECK;
                }
                yield held ? RequiredDecision.RETURN_HELD : RequiredDecision.PROCEED;
            }
        };
    }

    /**
     * 確かめの結果を ⑩ の部分の結果に写す（BR4.4）。
     *
     * @param column 参照元のカラムの名前
     * @param result {@code check} の結果
     * @return 進む・拒否（{@code REFERENCE_NOT_FOUND}）・失敗
     */
    public static ReferenceStepOutcome ofCheck(String column, ReferenceCheckResult result) {
        Objects.requireNonNull(column, "column は必須です");
        Objects.requireNonNull(result, "result は必須です");
        return switch (result) {
            case ReferenceCheckResult.Found found -> new ReferenceStepOutcome.Proceed();
            case ReferenceCheckResult.NotFoundOrDenied denied ->
                rejected(column, BusinessDataFieldErrorReason.REFERENCE_NOT_FOUND);
            case ReferenceCheckResult.Failed failed ->
                new ReferenceStepOutcome.Failed(failed.failure(), ReferenceStepOutcome.Stage.CHECK);
        };
    }

    /**
     * 決めたことを ⑩ の部分の結果に写す（{@link RequiredDecision#CHECK} は確かめの後で決まるため写さない）。
     *
     * @param column 参照元のカラムの名前
     * @param decision 決めたこと（{@code CHECK} 以外）
     * @param heldErrors そのカラムの保留した誤り（{@code RETURN_HELD} のとき1つ以上）
     * @return 部分の結果
     * @throws IllegalArgumentException {@code CHECK} を渡したとき
     */
    public static ReferenceStepOutcome ofDecision(
            String column, RequiredDecision decision, List<BusinessDataFieldError> heldErrors) {
        Objects.requireNonNull(column, "column は必須です");
        Objects.requireNonNull(decision, "decision は必須です");
        return switch (decision) {
            case REFERENCE_NOT_FOUND -> rejected(column, BusinessDataFieldErrorReason.REFERENCE_NOT_FOUND);
            case NOT_EDITABLE_NOT_NULL -> rejected(column, BusinessDataFieldErrorReason.NOT_EDITABLE_NOT_NULL);
            case RETURN_HELD -> new ReferenceStepOutcome.Invalid(heldErrors);
            case PROCEED -> new ReferenceStepOutcome.Proceed();
            case CHECK -> throw new IllegalArgumentException("CHECK は確かめの結果で写します");
        };
    }

    /**
     * 必須の参照項目の件数の状態を参照先の状態に写す（BR3.2。{@code Failed} は呼ぶ側が先に扱う）。
     *
     * @param state {@code countState} の結果（{@code Failed} 以外）
     * @return 状態
     * @throws IllegalArgumentException {@code Failed} を渡したとき
     */
    public static ReferenceStatus statusOf(ReferenceCountState state) {
        Objects.requireNonNull(state, "state は必須です");
        return switch (state) {
            case ReferenceCountState.Available available -> ReferenceStatus.AVAILABLE;
            case ReferenceCountState.TooMany tooMany -> ReferenceStatus.TOO_MANY;
            case ReferenceCountState.NoPermission noPermission -> ReferenceStatus.NO_PERMISSION;
            case ReferenceCountState.Failed failed -> throw new IllegalArgumentException("失敗は状態に写しません");
        };
    }

    /**
     * ⑩ の部分の結果をまとめる（BR1.3）。失敗 → 行が無い → 400（保留した誤りを並べた順に）→ 409（拒否をすべて並べた順に）→ 進む。
     * 失敗・行が無いが2つ以上あれば最初のもの。
     *
     * @param partials 部分の結果（並びは DSL の並び）
     * @return まとめた結果
     */
    public static ReferenceStepOutcome combine(List<ReferenceStepOutcome> partials) {
        Objects.requireNonNull(partials, "partials は必須です");
        ReferenceStepOutcome.Failed failed = null;
        ReferenceStepOutcome.RowMissing missing = null;
        List<BusinessDataFieldError> invalid = new ArrayList<>();
        List<BusinessDataFieldError> rejected = new ArrayList<>();
        for (ReferenceStepOutcome partial : partials) {
            switch (partial) {
                case ReferenceStepOutcome.Failed f -> failed = failed == null ? f : failed;
                case ReferenceStepOutcome.RowMissing m -> missing = missing == null ? m : missing;
                case ReferenceStepOutcome.Invalid i -> invalid.addAll(i.fieldErrors());
                case ReferenceStepOutcome.Rejected r -> rejected.addAll(r.fieldErrors());
                case ReferenceStepOutcome.Proceed p -> {}
            }
        }
        if (failed != null) {
            return failed;
        }
        if (missing != null) {
            return missing;
        }
        if (!invalid.isEmpty()) {
            return new ReferenceStepOutcome.Invalid(invalid);
        }
        if (!rejected.isEmpty()) {
            return new ReferenceStepOutcome.Rejected(rejected);
        }
        return new ReferenceStepOutcome.Proceed();
    }

    /**
     * ⑩ の結果を既存の拒否に写す（BR1.4・BR4.4。新しい code を作らない）。理由の写しは既定の枝を置かずに尽くす。
     *
     * @param outcome ⑩ の結果（{@code Proceed} 以外）
     * @return 拒否
     * @throws IllegalArgumentException {@code Proceed} を渡したとき
     */
    public static WriteRejection rejectionOf(ReferenceStepOutcome outcome) {
        Objects.requireNonNull(outcome, "outcome は必須です");
        return switch (outcome) {
            case ReferenceStepOutcome.Invalid invalid -> WriteRejection.VALIDATION_FAILED;
            case ReferenceStepOutcome.Rejected rejected -> WriteRejection.REFERENCE_NOT_FOUND;
            case ReferenceStepOutcome.RowMissing missing -> missing.rejection();
            case ReferenceStepOutcome.Failed failed ->
                rejectionOf(failed.failure().reason());
            case ReferenceStepOutcome.Proceed proceed -> throw new IllegalArgumentException("進む結果は拒否に写しません");
        };
    }

    /**
     * 失敗の理由を既存の拒否に写す（BR1.4。TIMEOUT → 503、UNAVAILABLE → 503、MISMATCH → 409）。
     *
     * @param reason 理由
     * @return 拒否
     */
    public static WriteRejection rejectionOf(ReferenceFailureReason reason) {
        Objects.requireNonNull(reason, "reason は必須です");
        return switch (reason) {
            case TIMEOUT -> WriteRejection.TARGET_DB_TIMEOUT;
            case UNAVAILABLE -> WriteRejection.TARGET_DB_UNAVAILABLE;
            case MISMATCH -> WriteRejection.TARGET_DB_MISMATCH;
        };
    }

    /**
     * ⑩ の結果の項目の誤り（400・409 のときだけ。ほかは空）。
     *
     * @param outcome ⑩ の結果
     * @return 項目の誤り
     */
    public static List<BusinessDataFieldError> fieldErrorsOf(ReferenceStepOutcome outcome) {
        Objects.requireNonNull(outcome, "outcome は必須です");
        return switch (outcome) {
            case ReferenceStepOutcome.Invalid invalid -> invalid.fieldErrors();
            case ReferenceStepOutcome.Rejected rejected -> rejected.fieldErrors();
            case ReferenceStepOutcome.RowMissing missing -> List.of();
            case ReferenceStepOutcome.Failed failed -> List.of();
            case ReferenceStepOutcome.Proceed proceed -> List.of();
        };
    }

    private static ReferenceStepOutcome rejected(String column, BusinessDataFieldErrorReason reason) {
        return new ReferenceStepOutcome.Rejected(List.of(new BusinessDataFieldError(column, reason)));
    }
}
