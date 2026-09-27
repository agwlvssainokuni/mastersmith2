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
 * メールの組み立てと SMTP での1回だけの送信、送信の失敗の分類（BR5.1〜BR5.3）。
 *
 * <p>宛先と描いた本文を扱うため、メソッドの呼び出しの追跡（TraceAspect）の対象の層の外に置く用途名の下位パッケージとする
 * （NFR 設計の Q1 A）。外からは使わせない。
 */
package cherry.mastersmith.mail.transport;
