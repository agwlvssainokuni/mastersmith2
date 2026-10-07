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
package cherry.mastersmith.dsl.service;

/**
 * 上限つきの安全な YAML の読み込みの口（{@link SafeYamlReader}）に呼ぶ側が渡す上限（U2 の機能設計の BR6.1、entities.md の
 * SafeYamlLimits）。作るときに範囲を確かめ、外れたら呼ぶ側の誤りとして例外にする。
 *
 * @param maxBytes 本文の大きさの上限（バイト。1 以上。ちょうどは受け付ける）
 * @param maxDepth 入れ子の深さの上限（文書の根から数えた対応表と並びの段の数。1 以上）
 * @param maxAliases コレクション（対応表・並び）を指す別名の数の上限（0 以上。0 ならコレクションを指す別名を1つも許さない。
 *     文字などの単独の値を指す別名は数えない）
 * @param maxExpandedNodes 別名を展開した後の節の数の上限（キーと値の節を数える。1 以上）
 */
public record SafeYamlLimits(int maxBytes, int maxDepth, int maxAliases, int maxExpandedNodes) {

    /** 上限の値の範囲を確かめる。 */
    public SafeYamlLimits {
        if (maxBytes < 1) {
            throw new IllegalArgumentException("maxBytes は 1 以上です");
        }
        if (maxDepth < 1) {
            throw new IllegalArgumentException("maxDepth は 1 以上です");
        }
        if (maxAliases < 0) {
            throw new IllegalArgumentException("maxAliases は 0 以上です");
        }
        if (maxExpandedNodes < 1) {
            throw new IllegalArgumentException("maxExpandedNodes は 1 以上です");
        }
    }
}
