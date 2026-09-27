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
 * 保存されたパスワードのハッシュ（bcrypt の形式）を包む型。文字列化では値を伏せる（{@code ***}）。
 *
 * <p>メソッドの呼び出しの追跡（TraceAspect）が DB アクセスの層の引数も文字列にするため、ハッシュを {@code String} のまま問い合わせの
 * 引数に使わない（NFR2.2、BR7.4）。{@code user} パッケージの外へ出さない（ADR-001）。
 *
 * @param value ハッシュの文字列
 */
public record PasswordHash(String value) {

    /** 値が null でないことを確かめる。 */
    public PasswordHash {
        Objects.requireNonNull(value, "value");
    }

    /** 値を伏せて文字列にする。 */
    @Override
    public String toString() {
        return "***";
    }
}
