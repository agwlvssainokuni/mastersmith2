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
package cherry.mastersmith.common.security;

import cherry.mastersmith.common.error.domain.ProblemType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * フィルターの段階のエラー応答の組み立て（BR5.1）。U1 が提供し、U2・U3 が認証の入口・アクセス拒否の処理（401・403）で使う。
 *
 * <p>問題の種類から type・code・title・status を、要求から traceId・instance・表示言語を埋め、コントローラーの中の例外の変換と
 * 同じ形の ErrorResponse（{@code application/problem+json}）を書く。
 */
public interface ErrorResponseWriter {

    /**
     * エラー応答を書く。
     *
     * @param request 要求
     * @param response 応答
     * @param problemType 問題の種類
     * @throws IOException 応答の書き込みに失敗したとき
     */
    void write(HttpServletRequest request, HttpServletResponse response, ProblemType problemType) throws IOException;
}
