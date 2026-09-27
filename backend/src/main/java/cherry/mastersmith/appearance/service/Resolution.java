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
package cherry.mastersmith.appearance.service;

/**
 * 1つの項目の判定の結果。
 *
 * @param value 採った値（許される値のどれか。{@code null} にならない）
 * @param warned 許されない値を既定に置き換えたか（警告のログを出すべきか）
 * @param <T> 値の型
 */
public record Resolution<T>(T value, boolean warned) {}
