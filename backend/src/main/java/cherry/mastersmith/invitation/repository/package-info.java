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
 * Invitation の DB アクセス（招待の表 {@code invitations}）。行の排他の読み取り（待ちの上限 3 秒）、条件つきの更新、一覧、定期の削除を
 * 持つ。問い合わせは名前つきの引数だけで組み立て、文字列の連結を使わない。
 */
package cherry.mastersmith.invitation.repository;
