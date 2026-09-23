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
 * AuditLog（監査ログ）。U2 の認証の出来事と U3 のアクセス拒否の出来事を受け取り、内部DBの監査イベントの表に1件ずつ追記する。
 * 監査ログを見る画面・API は本Intentでは作らない（FR9.1〜FR9.5、NFR3.5）。
 */
package cherry.mastersmith.audit;
