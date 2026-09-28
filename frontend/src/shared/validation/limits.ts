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
//
// 氏名・パスワードの入力の確かめの上限と下限（U6 の functional-spec.md の 6節）。サーバーの決まり（U2 の BR1.3・BR4.1、
// U3 の BR7.2）と同じ値。判定はサーバーが正で、画面の確かめはその代わりにしない。

/** 氏名の長さの上限（前後の空白を除いた後のコードポイントの数。U2 の BR1.3） */
export const DISPLAY_NAME_MAX_CODE_POINTS = 254

/** パスワードの長さの下限（コードポイントの数。U2 の BR4.1） */
export const PASSWORD_MIN_CODE_POINTS = 12

/** パスワードの長さの上限（UTF-8 のバイト数。U2 の BR4.1） */
export const PASSWORD_MAX_UTF8_BYTES = 72
