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
package cherry.mastersmith.role.service;

import cherry.mastersmith.role.domain.EffectivePermission;
import cherry.mastersmith.role.domain.PermissionSnapshot;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.domain.WorkRoleRef;
import java.util.Optional;

/**
 * 実効の権限の解決の口（契約 C5、{@code logical-components.md} の L4、BR5.3〜BR5.5・BR7.2、NFR2.3）。U5 navigation と後の Intent が使う。
 *
 * <p>どの口も要求ごとに内部DB の割り当て・作業ロールの保存・設定から求め、トークン・画面から送られた値・要求をまたぐキャッシュを使わない
 * （BR5.4）。実効の権限は有効な作業ロールの設定だけで決め、ほかのロールの設定を合算せず、管理者の印も使わない（BR5.5）。作業ロールが
 * 無い・適用済みの DSL が無い・対象が今の DSL に無いときは NONE・不可を返す（BR5.3）。読み取りだけで、保存を書き換えない（BR7.3）。DB の
 * 誤りは例外のまま投げる（想定外の失敗）。
 *
 * <p><b>使い分け</b>（{@code performance-design.md} 1節、{@code logical-components.md} 4節）:
 *
 * <ul>
 *   <li>{@link #resolve(long, PermissionTarget)} は、1回の要求で対象が1つのとき（テーブルの置き場など）に使ってよい。
 *   <li>2つ以上の対象を判定する呼ぶ側は {@link #snapshotFor(long)} を1回呼び、その写しで判定する。
 *   <li>理由は、問い合わせの数（{@code resolve} は呼ぶたびに有効な作業ロールを決める読み取り4回と祖先の行1回を行う）と、1回の要求の中で
 *       同じ作業ロールで答える一貫性（{@code resolve} を重ねて呼ぶと、その間に作業ロールが切り替わりうる）。
 * </ul>
 */
public interface EffectivePermissionResolver {

    /**
     * 有効な作業ロールを返す（BR7.1・BR7.2。保存が利用者のロールにあればそれ、無ければ最初のロール、ロールが無ければ無し）。有効な
     * 作業ロールを決める唯一の持ち主（契約 C5）。
     *
     * @param userId 利用者 ID
     * @return 有効な作業ロール（無ければ空）
     */
    Optional<WorkRoleRef> effectiveWorkRole(long userId);

    /**
     * 有効な作業ロールの設定の写しを返す（有効な作業ロールを決める読み取り4回と、設定の行をまとめて1回。BR5.4、NFR2.3）。写しは呼んだ
     * 要求の中だけで使い、要求をまたいで持たない。
     *
     * @param userId 利用者 ID
     * @return 写し
     */
    PermissionSnapshot snapshotFor(long userId);

    /**
     * 1つの対象の実効の権限を返す（写しを使わず、有効な作業ロールを決める読み取り4回と、対象の祖先の行を1回。計画の D-16）。2つ以上の対象は
     * {@link #snapshotFor(long)} を使う。
     *
     * @param userId 利用者 ID
     * @param target 対象
     * @return 実効の権限
     */
    EffectivePermission resolve(long userId, PermissionTarget target);
}
