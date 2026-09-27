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
 * メールの値の型（送信の依頼・結果・失敗の種類・想定外の例外）と、依頼の確かめ（BR3.1〜BR3.4）の純粋な関数。
 *
 * <p>どれも Bean ではなく、宛先・差し込む値を文字列にするときは伏せる（NFR1.2）。
 */
package cherry.mastersmith.mail.domain;
