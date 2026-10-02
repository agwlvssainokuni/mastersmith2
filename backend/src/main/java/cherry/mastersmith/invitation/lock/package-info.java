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
 * 招待の行の排他の読み取りの実装（Spring Data の独自の断片 {@code InvitationLockQueries} の実装）。
 *
 * <p>メソッドの呼び出しの追跡（{@code TraceAspect}）の対象の層の外の用途名の下位パッケージ。引数の招待のトークンのハッシュ値
 * （{@code byte[]}）が追跡のログに出ないようにするため、ここに置く（Intent 260930-user-admin の U3）。
 */
package cherry.mastersmith.invitation.lock;
