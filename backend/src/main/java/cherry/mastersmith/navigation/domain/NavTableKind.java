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
package cherry.mastersmith.navigation.domain;

/**
 * 移れるメニューの項目が指すテーブルの種類（{@code functional-spec.md} の D1・D2）。画面が業務の項目の既定のアイコンを決めるのに使う。
 *
 * <p>応答には名前（{@code TABLE}・{@code VIEW}）の文字列として載る。種類が分からない項目（まとまり・DSL に見つからないテーブル）は列挙の値を
 * 持たず {@code null} で表す。
 */
public enum NavTableKind {
    /** 実体のあるテーブル。 */
    TABLE,
    /** ビュー。 */
    VIEW
}
