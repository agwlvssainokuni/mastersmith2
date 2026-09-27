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
package cherry.mastersmith.user.service;

/**
 * パスワードの変更の、照合の後・書き込みの前に呼ぶ待ち合わせの口（{@code reliability-design.md} 2節）。
 *
 * <p>本番は何もしない既定の部品（{@link NoOpPasswordChangeBarrier}）を使い、本番のコードの流れは変えない。同時の変更の結合テストが
 * 差し替えて、照合の後に別の変更を確定させる重なりを確実に作る。
 */
public interface PasswordChangeBarrier {

    /**
     * 新しいハッシュを書き込む直前に呼ばれる（トランザクションの外、接続を持たない間）。
     *
     * @param userId 本人の利用者 ID
     */
    void beforeWrite(long userId);
}
