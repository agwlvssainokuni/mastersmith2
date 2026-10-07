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
 * 上限つきの安全な YAML の読み込みの口（U2 の機能設計の 3.8節・BR6.1〜BR6.5、ADR-007、契約 C3。U4 role の権限の YAML が使う）。
 *
 * <p>使い方: 呼ぶ側が本文のバイト列と上限（{@link SafeYamlLimits}）を渡し、結果（{@link SafeYamlResult}）を受け取る。読めたら
 * JSON の木と位置を引く口を返し、拒否したら区分と位置だけを返す。想定内の失敗（上限・タグ・重複キー・YAML として読めない）は
 * 結果の型で返し、例外にしない。
 *
 * <p>守りの決まり:
 *
 * <ul>
 *   <li>大きさは読む前にバイト数で確かめる（上限ちょうどは受け付ける）。
 *   <li>深さ・別名・展開後の節の数・タグ・重複キー・YAML として読めない本文は、最初の1件で区分に写して拒否する。任意の型を
 *       作らず、外部の参照を取りに行かない（SnakeYAML の節の木を作る部品だけを使う）。
 *   <li>部品の例外の文は捨て、区分と位置だけを返す。結果を文字列にしても木の中身を出さない。口はログを出さない。
 *   <li>DSL の JSON Schema の検証はしない（呼ぶ側が自分の検証を行う）。
 * </ul>
 */
public interface SafeYamlReader {

    /**
     * YAML を上限つきで安全に読む。
     *
     * @param yamlBytes UTF-8 の YAML の本文（文字列で受けない。{@code TraceAspect} が引数を文字列にしても中身が出ないため）
     * @param limits 上限
     * @return 読めた（木と位置を引く口）か、拒否した（区分と位置）か
     */
    SafeYamlResult read(byte[] yamlBytes, SafeYamlLimits limits);
}
