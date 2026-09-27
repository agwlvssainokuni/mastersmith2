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
 * 見た目の設定の判定と保持（U8）。起動時に1回だけ判定し、許されない値は既定に置き換えて項目ごとに警告のログを1件出す
 * （BR1.1〜BR1.6・BR2.1）。
 */
package cherry.mastersmith.appearance.service;
