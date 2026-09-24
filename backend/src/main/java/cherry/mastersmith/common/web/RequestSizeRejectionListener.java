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

import jakarta.servlet.http.HttpServletRequest;

/**
 * 要求の本文の大きさの上限で断ったことを受け取る口（{@link RequestSizeLimitFilter} の「断ったことを知らせる口」）。
 *
 * <p>各機能が Bean として置く。知らせは要求と同じスレッドで、応答を書く前に届く（本文の上限の確かめは認証・認可の後に置くため、
 * 操作した人が分かる）。受け取り側の例外は、本文の上限の仕組みが受け止めて WARN を1件出し、応答は変えない。
 */
@FunctionalInterface
public interface RequestSizeRejectionListener {

    /**
     * 断ったことを受け取る。
     *
     * @param request 断った要求（本文は読まないこと）
     * @param rejection 断ったことの知らせ
     */
    void onRejected(HttpServletRequest request, RequestSizeRejection rejection);
}
