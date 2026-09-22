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

import java.util.Objects;

/**
 * 入力されたパスワード（平文）を包む型。文字列化では値を伏せる（{@code ***}）。
 *
 * <p>メソッドの呼び出しの追跡（TraceAspect）が引数を文字列にするため、パスワードを {@code String} のまま業務処理の引数に
 * 使わない（NFR3.1、NFR3.6）。
 *
 * @param value 平文のパスワード
 */
public record Password(String value) {

    /** 値が null でないことを確かめる。 */
    public Password {
        Objects.requireNonNull(value, "value");
    }

    /** 値を伏せて文字列にする。 */
    @Override
    public String toString() {
        return "***";
    }
}
