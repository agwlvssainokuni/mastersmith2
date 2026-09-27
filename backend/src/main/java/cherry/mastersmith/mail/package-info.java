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
 * メールの描画と送信（U1、契約 C1）。テンプレートを描いて HTML のメールを作り、SMTP で1回だけ送る共通の部品。
 *
 * <p>外から使えるのは {@code mail.service}（{@code MailSender}）と {@code mail.domain}（値の型）だけ。設定の点検は
 * {@code mail.config}、テンプレートの一覧・準備・描画は {@code mail.template}、メールの組み立てと送信は {@code mail.transport} に
 * 置く（MailBoundaryArchitectureTest で守る）。内部DB に触れず、トランザクションを持たない（BR5.4）。
 */
package cherry.mastersmith.mail;
