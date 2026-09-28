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
 * Invitation のドメイン（招待のエンティティ、状態と送信の結果、トークンと招待の URL の値の型、有効の判定・ページの計算・入力の検証の
 * 純粋な関数、監査の出来事、問題の種類）。
 *
 * <p>秘密を持つ値（トークン・招待の URL・メールアドレス）は、文字列にすると伏せる値の型で受け渡す（メソッドの呼び出しの追跡が引数と
 * 戻り値を文字列にするため）。
 */
package cherry.mastersmith.invitation.domain;
