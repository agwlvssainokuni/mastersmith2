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
/**
 * インスタンスの見た目の設定（Intent 260925-user-management の U8、契約 C7、要件 FR8・CR2）。
 *
 * <p>インスタンス全体のブランドカラーとフォントファミリーを設定から起動時に1回だけ読み、無い・許されない値のときは既定
 * （blue・sans）に置き換え、ログインなしで読める {@code GET /api/appearance} で返す。画面に当てるのは U4 の受け持ち。
 *
 * <p>層の構成: この機能の設定の型は {@code appearance.config}、判定と保持は {@code appearance.service}、API と公開の決まりは
 * {@code appearance.web} に置く。内部DB に触れないため {@code domain}・{@code repository} は持たない。ほかの機能と依存し合わない
 * （AppearanceBoundaryArchitectureTest で守る）。
 */
package cherry.mastersmith.appearance;
