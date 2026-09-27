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
package cherry.mastersmith.user.domain;

/**
 * 入力の誤りの理由（{@code fieldErrors} の {@code reason}。{@code nfr-design/security-design.md} 3節）。
 *
 * <p>値を足すのは安全な変更（受け側は知らない値を一般の文言で出す）で、名前の変更・削除は壊れる変更とする。後の単位（登録の完了）も
 * 同じ型を使う。
 */
public enum FieldErrorReason {
    /** 値が無い（項目が無い・null・空の文字列）。氏名は前後の空白を除いた後の空。 */
    REQUIRED,
    /** 新しいパスワードが 12 コードポイント未満。 */
    TOO_SHORT,
    /** 氏名が 254 コードポイントを超える、または新しいパスワードが UTF-8 で 72 バイトを超える。 */
    TOO_LONG,
    /** 氏名に制御文字（Cc）か見えない書式の文字（Cf）がある。 */
    INVALID_CHARACTER,
    /** 言語・テーマ・文字の大きさが決めた値に完全に一致しない。 */
    INVALID_VALUE,
    /** 確かめの値が新しいパスワードと一致しない。 */
    MISMATCH
}
