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
package cherry.mastersmith.common.web;

import cherry.mastersmith.common.error.domain.ProblemType;
import java.util.Objects;

/**
 * 要求の本文の大きさの上限で断ったことの知らせ（{@link RequestSizeRejectionListener} に渡す）。本文の中身は持たない。
 *
 * @param method HTTP メソッド
 * @param path アプリの中のパス
 * @param maxBytes 当てた上限（バイト）
 * @param contentLength 送られた大きさ（{@code Content-Length}。分割の送信で分からないときは -1）
 * @param problemType 応答の問題の種類
 * @param route 当たった道ごとの決まり（既定の上限のときは null）
 */
public record RequestSizeRejection(
        String method,
        String path,
        long maxBytes,
        long contentLength,
        ProblemType problemType,
        RequestBodyLimitRoute route) {

    /** 必須の値を確かめる。 */
    public RequestSizeRejection {
        Objects.requireNonNull(method, "method は必須です");
        Objects.requireNonNull(problemType, "problemType は必須です");
    }
}
