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
 * 文字列にすると値を伏せる文字列（Intent 260925-user-management の U3 の計画の決定 3、NFR2.1）。
 *
 * <p>メソッドの呼び出しの追跡（TraceAspect）が業務処理と DB アクセスの層の引数と戻り値を文字列にするため、メールアドレス・氏名のように
 * アプリのログに出さない値を {@code String} のまま受け渡さない口で使う（例: 登録済みかの確かめ、招待した管理者の氏名）。
 * 問い合わせでは SpEL（{@code :#{#email.value()}}）で取り出す。
 *
 * @param value 値
 */
public record RedactedText(String value) {

    /** 値が null でないことを確かめる。 */
    public RedactedText {
        Objects.requireNonNull(value, "value");
    }

    /** 値を伏せて文字列にする。 */
    @Override
    public String toString() {
        return "***";
    }
}
