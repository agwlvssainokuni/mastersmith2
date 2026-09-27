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
package cherry.mastersmith.user.domain;

/** 表示の設定の値（言語・テーマ・文字の大きさ）の共通の形。API と内部DB では小文字の文字列で表す（BR2.1）。 */
public interface PreferenceValue {

    /**
     * API と内部DB で使う小文字の文字列を返す。
     *
     * @return 値の文字列（例: {@code ja}）
     */
    String value();
}
