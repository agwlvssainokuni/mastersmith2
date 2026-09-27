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
 * メールのテンプレートの一覧・起動時の準備・描画と、描いた本文からの件名と言語の取り出し（BR2.1〜BR2.3、BR4.1〜BR4.4）。
 *
 * <p>差し込む値と描いた本文を扱うため、メソッドの呼び出しの追跡（TraceAspect）の対象の層の外に置く用途名の下位パッケージとする
 * （NFR 設計の Q1 A）。外からは使わせない。
 */
package cherry.mastersmith.mail.template;
