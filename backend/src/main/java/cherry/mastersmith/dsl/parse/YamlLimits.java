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
package cherry.mastersmith.dsl.parse;

/**
 * 読み込みの上限（{@link SafeYamlParser} に渡す。U2 の機能設計の BR6.1・BR6.6、NFR 設計の 4.1）。
 *
 * <p>上限の値は部品の中に持たず、呼ぶ側が渡す。DSL の読み込みは {@code DslFormat} の値で、上限つきの安全な読み込みの口
 * （{@code SafeYamlReader}）は呼ぶ側の値で作る。深さと別名の上限は SnakeYAML の {@code LoaderOptions} と
 * {@link LimitingParser} の両方に、大きさは {@code LoaderOptions} の文字の数の上限に同じ値を渡す（NFR 設計の試し T1'。片方だけ
 * を小さくすると、部品の例外が先に出て区分と位置を失うため）。
 *
 * @param maxBytes 本文の大きさの上限（バイト。1 以上）
 * @param maxDepth 入れ子の深さの上限（文書の根から数えた対応表と並びの段の数。1 以上）
 * @param maxCollectionAliases コレクション（対応表・並び）を指す別名の数の上限（0 以上）
 * @param maxExpandedNodes 別名を展開した後の節の数の上限（1 以上）
 */
public record YamlLimits(int maxBytes, int maxDepth, int maxCollectionAliases, int maxExpandedNodes) {

    /** 上限の値の範囲を確かめる。 */
    public YamlLimits {
        if (maxBytes < 1 || maxDepth < 1 || maxExpandedNodes < 1) {
            throw new IllegalArgumentException("大きさ・深さ・展開後の節の数の上限は 1 以上です");
        }
        if (maxCollectionAliases < 0) {
            throw new IllegalArgumentException("別名の数の上限は 0 以上です");
        }
    }
}
