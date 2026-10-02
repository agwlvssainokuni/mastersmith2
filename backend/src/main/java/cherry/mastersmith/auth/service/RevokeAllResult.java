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
package cherry.mastersmith.auth.service;

/**
 * リフレッシュトークンのまとめての無効化の結果（契約 C1、Intent 260930-user-admin の U1）。
 *
 * @param revoked 無効にした行の数（0 以上。未無効の行が無ければ 0）
 */
public record RevokeAllResult(int revoked) {

    /** 無効にした行の数が 0 以上であることを確かめる。 */
    public RevokeAllResult {
        if (revoked < 0) {
            throw new IllegalArgumentException("無効にした行の数は 0 以上です: " + revoked);
        }
    }
}
