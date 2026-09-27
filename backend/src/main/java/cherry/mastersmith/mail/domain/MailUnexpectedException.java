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
package cherry.mastersmith.mail.domain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * メールの送信の想定外の失敗（BR5.3、NFR 設計の security-design.md の 4.3）。U1 の不具合や、分類できない送信の部品の失敗を表す。
 *
 * <p>固定の文言と、原因の連なりの型の名前の一覧だけを持ち、原因の例外そのものは付けない（{@link #getCause()} は null）。部品の例外の
 * 文言には宛先のメールアドレスや SMTP の応答が入りうるため、既存の 5xx の ERROR のログ・トレースの例外のイベントに残さないため。
 * 調べるときは、型の名前・時刻・トレースIDで絞る。
 *
 * <p>作る口は {@link #of(Throwable)} の1つだけ（NFR 設計の承認の場の U1 R-02）。
 */
public final class MailUnexpectedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 例外の固定の文言（宛先・部品の文言を含めない）。 */
    public static final String MESSAGE = "メールの送信で想定外の失敗が起きました";

    /** 原因の連なりをたどる数の上限（連なりが輪になっても止まるようにする）。 */
    static final int MAX_CAUSES = 16;

    /** 原因の連なりの型の名前（変更できない一覧）。 */
    private final transient List<String> causeTypeNames;

    private MailUnexpectedException(List<String> causeTypeNames) {
        super(MESSAGE + "（原因の型: " + causeTypeNames + "）", null, false, true);
        this.causeTypeNames = List.copyOf(causeTypeNames);
    }

    /**
     * 想定外の失敗を包む。原因の連なり（{@link Throwable#getCause()}、抑制された例外）の型の名前だけを写す。
     *
     * <p>Jakarta Mail の {@code MessagingException} は {@code getCause()} で次の例外（{@code getNextException()}）を返すため、
     * この連なりに含まれる。
     *
     * @param cause 包む失敗
     * @return 包んだ例外（原因を付けない）
     */
    public static MailUnexpectedException of(Throwable cause) {
        return new MailUnexpectedException(causeTypeNames(cause));
    }

    /**
     * 原因の連なりの型の名前を返す。
     *
     * @return 型の名前（外側から順）
     */
    public List<String> causeTypeNames() {
        return causeTypeNames == null ? List.of() : causeTypeNames;
    }

    /** 原因の連なりを幅優先でたどり、型の名前を集める（同じ例外は1回だけ、数に上限）。 */
    static List<String> causeTypeNames(Throwable cause) {
        List<String> names = new ArrayList<>();
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<Throwable> queue = new ArrayDeque<>();
        if (cause != null) {
            queue.add(cause);
        }
        while (!queue.isEmpty() && names.size() < MAX_CAUSES) {
            Throwable current = queue.removeFirst();
            if (!seen.add(current)) {
                continue;
            }
            names.add(current.getClass().getName());
            if (current.getCause() != null) {
                queue.add(current.getCause());
            }
            Collections.addAll(queue, current.getSuppressed());
        }
        return names;
    }
}
