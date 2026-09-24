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
package cherry.mastersmith.dslmanage.generate;

/**
 * 既定の DSL の生成の口（契約 C5。U4 が使う）。
 *
 * <p>対象DB のスキーマを U1 から読み（{@code readSchema(GENERATE)}）、U2 の書式の YAML の本文を作り、U2 で検証して返す。想定内の
 * 失敗（対象DB の設定が無い・接続できない）は結果の型で返す。生成した本文が大きさの上限（10MB）を超えたときと、U2 の検証を
 * 通らないとき（作りの誤り）は想定外の失敗として例外にし、部分的な DSL を返さない（BR5.2、NFR2.5）。保存と監査はしない（U4）。
 */
public interface DefaultDslGenerator {

    /**
     * 既定の DSL を生成する。
     *
     * @return 生成できた・設定が無い・接続できない のどれか
     * @throws IllegalStateException 生成した本文が上限を超えた、または U2 の検証を通らなかった（想定外の失敗）
     */
    DefaultDslResult generate();
}
