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
 * 参照先の読み取りの失敗の理由（Intent 261029-reference-lookup-cleanup の U1、NFR 設計 reliability-design.md 5.2、entities.md の
 * {@code failureReason}）。U2 はこの3つで既存の code への写しを尽くす（既定の枝を置かない）。
 */
public enum ReferenceFailureReason {
    /** 時間切れ（文の上限・全体の上限。503 {@code BUSINESS_TARGET_DB_TIMEOUT}）。 */
    TIMEOUT,
    /** 接続の失敗・借りられない・読み取りの設定が無い（503 {@code BUSINESS_TARGET_DB_UNAVAILABLE}）。 */
    UNAVAILABLE,
    /** DSL と対象DB の型の食い違い（409 {@code BUSINESS_TARGET_DB_MISMATCH}）。 */
    MISMATCH
}
