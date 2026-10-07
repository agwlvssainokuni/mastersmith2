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

import cherry.mastersmith.dsl.domain.DslReadResult;
import cherry.mastersmith.dsl.domain.DslStartupReadResult;

/**
 * DSL の読み込みの口（契約 C2・C4 の {@code DslReader}。U3 の生成した DSL の検証と、U4 の投入・起動時の読み込みが使う）。
 *
 * <p>想定内の失敗（検証を通らない DSL）は結果の型（{@link DslReadResult.Invalid}）で返し、例外にしない。想定外の失敗（プログラムの
 * 誤り）だけを例外にする。
 */
public interface DslReader {

    /**
     * DSL を読み、検証する。段の順は 大きさ → 読み込み（深さ・別名・タグ・重複キー）→ 書式の版 → 構文 → 意味 で、前の段に誤りが
     * あれば後の段は行わない（BR2.3）。
     *
     * @param yamlBytes UTF-8 の YAML の本文
     * @return 検証を通った（モデルと識別）か、通らなかった（誤りの一覧）か
     */
    DslReadResult read(byte[] yamlBytes);

    /**
     * 起動時の読み方で DSL を読む（U2 dsl-v2 の BR2.3、functional-spec.md の 2節）。段の順は {@link #read(byte[])} と同じで、意味の
     * 検証からメニューの深さだけを外し、モデルを作る前に深すぎる枝を落とす（{@code MenuDepth.prune}）。深さ以外の誤り（書式の版 1 を
     * 含む）は通常の読み方と同じく通らない。
     *
     * @param yamlBytes UTF-8 の YAML の本文
     * @return 検証を通った（深すぎる枝を落としたモデルと、落とした項目の数）か、通らなかった（誤りの一覧）か
     */
    DslStartupReadResult readAtStartup(byte[] yamlBytes);

    /**
     * 本文のバイト列から DSL の識別を求める（検証はしない。BR5.1）。
     *
     * @param yamlBytes 本文のバイト列
     * @return SHA-256 を 16 進数の小文字 64 文字にしたもの
     */
    String hash(byte[] yamlBytes);
}
