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
package cherry.mastersmith.dsl.domain;

import java.util.Objects;

/**
 * 表示名（日本語と英語）。空の文字列は許す（未設定として扱い、プレビューで「未設定」と示す）。
 *
 * @param ja 日本語
 * @param en 英語
 */
public record DisplayName(String ja, String en) {

    /** 両方が必須であることを確かめる。 */
    public DisplayName {
        Objects.requireNonNull(ja, "ja は必須です");
        Objects.requireNonNull(en, "en は必須です");
    }
}
