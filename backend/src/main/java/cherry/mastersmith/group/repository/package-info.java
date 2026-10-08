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
 * グループの管理の DB アクセス（読み取りだけ）。一覧・件数・詳細・所属・数を読む。
 *
 * <p>書き込みの方法（{@code save}・{@code delete}・{@code @Modifying} の問い合わせ）と行の排他は置かない。違反と上限切れを起こしうる
 * 書き込みと排他は、メソッドの呼び出しの追跡の対象の外の {@code group.store} がまとめて行う（{@code security-design.md} 4.1、計画の
 * D-4）。そのため Spring Data の {@code Repository} だけを継ぎ、{@code CrudRepository}・{@code JpaRepository} を継がない。
 */
package cherry.mastersmith.group.repository;
