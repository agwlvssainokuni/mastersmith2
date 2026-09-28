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
 * Invitation（招待と登録の完了。Intent 260925-user-management の U3）。管理者が利用者を招待し、招待された人がリンクから登録を
 * 完了する。
 *
 * <p>招待は利用者の表とは別の表に持ち、登録の完了まで利用者を作らない（ADR-001）。招待のトークンは内部DB にハッシュだけを保存し、
 * 招待メールにだけ載せる。依存するのは UserAccount（{@code user.service}・{@code user.domain} の値の型）と Mail
 * （{@code mail.service}・{@code mail.domain}）と {@code common} だけで、{@code auth}・{@code audit} を参照しない。監査は
 * {@code invitation.domain} の出来事で知らせる（ADR-008）。
 */
package cherry.mastersmith.invitation;
