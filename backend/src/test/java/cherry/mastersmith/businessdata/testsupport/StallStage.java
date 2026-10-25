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
package cherry.mastersmith.businessdata.testsupport;

import java.util.List;

/**
 * 暗号化された通信で {@link StallingRelay} が止めた段が、想定の段かを、止める前に対象DB に届いた文で確かめる純粋な関数（Intent
 * 261025-deps-perf-carryover の FR3.7 R-05）。
 *
 * <p>止めた段が想定と違う（ドライバーの版でまとまりの分け方が変わった）ときは、テストが「時間切れにならない」「次が 200 にならない」
 * のような遠い失敗ではなく、届いた文の一覧と原因の見立てを持つ失敗になる。
 */
public final class StallStage {

    private StallStage() {}

    /**
     * 止める前に対象DB に届いた文が、想定と同じかを確かめる。
     *
     * @param expected 想定の文（止める前に届くはずの文を古い順に。何も届かないはずなら空）
     * @param actual 実際に届いた文（古い順）
     * @param report 中継の診断（{@link StallingRelay#stallReport()}）
     * @throws AssertionError 違うとき（届いた文と診断を文に入れる）
     */
    public static void require(List<String> expected, List<String> actual, String report) {
        if (!expected.equals(actual)) {
            throw new AssertionError("止まった段が想定と違います。止める前に対象DB に届くはずの文 " + expected + " に対し、実際は " + actual
                    + " でした（中継の診断 " + report + "）。ドライバーの版でまとまりの分け方が変わった可能性があります。"
                    + "StallingRelay#armAfterLargeChunks に渡す数を、届いた文に合わせて見直してください。");
        }
    }
}
