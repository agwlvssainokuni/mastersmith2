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
package cherry.mastersmith.invitation.repository;

import cherry.mastersmith.invitation.domain.Invitation;
import cherry.mastersmith.invitation.domain.InvitationEmail;
import cherry.mastersmith.invitation.domain.InvitationState;
import cherry.mastersmith.invitation.domain.SendResult;
import cherry.mastersmith.invitation.lock.InvitationLockQueries;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 招待の表の DB アクセス（BR2.2・BR2.3・BR4.3・BR5.1・BR5.2・BR6.4・BR11.1、{@code performance-design.md} 5節・6節）。保存は
 * {@link JpaRepository} の操作を使い、業務処理のトランザクションの中で呼ぶ。
 *
 * <ul>
 *   <li>行の排他の読み取りは独自の断片 {@link InvitationLockQueries} に置く（{@code PESSIMISTIC_WRITE}、待ちの上限 3 秒。Intent
 *       260930-user-admin の U3 で Spring Data の {@code @Lock} から移した）。待ちの時間切れは想定外の誤り（既存の 500 の扱い）で、
 *       例外の連なりを外へ出さず値を含まない例外に置き換える
 *   <li>メールアドレスは文字列にすると伏せる値の型で受け、SpEL で取り出す（メソッドの呼び出しの追跡が引数を文字列にするため）
 *   <li>問い合わせは名前つきの引数だけで組み立て、文字列の連結を使わない（NFR9.3）
 * </ul>
 */
public interface InvitationRepository extends JpaRepository<Invitation, Long>, InvitationLockQueries {

    /** 1回の削除の件数の上限。 */
    int DELETE_BATCH_SIZE = 1000;

    /**
     * 同じメールアドレスの招待中を行の排他つきで読む。
     *
     * @param email 正規化したメールアドレス
     * @return 招待中の招待（無ければ空）
     */
    default Optional<Invitation> findPendingByEmailForUpdate(InvitationEmail email) {
        return findByEmailAndStateForUpdate(email, InvitationState.PENDING);
    }

    /**
     * 同じメールアドレスの招待中を読む（排他なし。同時の招待で負けた側が勝った側を引くときに使う）。
     *
     * @param email 正規化したメールアドレス
     * @param state 招待中（{@link InvitationState#PENDING}）
     * @return 招待中の招待（無ければ空）
     */
    @Query("select i from Invitation i where i.email = :#{#email.value()} and i.state = :state")
    Optional<Invitation> findByEmailAndState(
            @Param("email") InvitationEmail email, @Param("state") InvitationState state);

    /**
     * 招待をトークンのハッシュで読む（リンクの確かめ。BR7.1）。
     *
     * @param tokenHash トークンのハッシュ
     * @return 招待（無ければ空）
     */
    @Query("select i from Invitation i where i.tokenHash = :tokenHash")
    Optional<Invitation> findByTokenHash(@Param("tokenHash") byte[] tokenHash);

    /**
     * 指定した状態の招待を、招待した日時の新しい順・同じなら ID の大きい順に読む（一覧。BR5.1・BR5.2）。
     *
     * @param state 状態（一覧は招待中）
     * @param pageable ページ（1ページ 20 件）
     * @return 招待の一覧
     */
    @Query("select i from Invitation i where i.state = :state order by i.invitedAt desc, i.invitationId desc")
    List<Invitation> findPage(@Param("state") InvitationState state, Pageable pageable);

    /**
     * 指定した状態の招待の件数を返す。
     *
     * @param state 状態
     * @return 件数
     */
    @Query("select count(i) from Invitation i where i.state = :state")
    long countByState(@Param("state") InvitationState state);

    /**
     * 一覧の並びで、指定した招待より前に並ぶ同じ状態の招待の件数を返す（BR2.3 の位置の計算）。
     *
     * @param state 状態（一覧は招待中）
     * @param invitedAt 指定した招待の招待した日時
     * @param invitationId 指定した招待の ID
     * @return 前に並ぶ件数
     */
    @Query("select count(i) from Invitation i where i.state = :state and (i.invitedAt > :invitedAt"
            + " or (i.invitedAt = :invitedAt and i.invitationId > :invitationId))")
    long countBefore(
            @Param("state") InvitationState state,
            @Param("invitedAt") Instant invitedAt,
            @Param("invitationId") long invitationId);

    /**
     * 送信の結果を、送ったトークンのハッシュを今も持ち送信中の行にだけ書く（BR4.3）。
     *
     * @param invitationId 招待の ID
     * @param tokenHash 送ったトークンのハッシュ
     * @param result 送信の結果（{@link SendResult#SENT}・{@link SendResult#FAILED}）
     * @param pending 送信中（{@link SendResult#PENDING}）
     * @return 書き換えた行の数（同時に送り直された・行が無ければ 0）
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Invitation i set i.sendResult = :result where i.invitationId = :invitationId"
            + " and i.tokenHash = :tokenHash and i.sendResult = :pending")
    int updateSendResultIfCurrent(
            @Param("invitationId") long invitationId,
            @Param("tokenHash") byte[] tokenHash,
            @Param("result") SendResult result,
            @Param("pending") SendResult pending);

    /**
     * 送信の結果を記録する（送信中の行だけ）。
     *
     * @param invitationId 招待の ID
     * @param tokenHash 送ったトークンのハッシュ
     * @param result 送信の結果
     * @return 書き換えた行の数
     */
    default int recordSendResult(long invitationId, byte[] tokenHash, SendResult result) {
        return updateSendResultIfCurrent(invitationId, tokenHash, result, SendResult.PENDING);
    }

    /**
     * 終わった招待（完了・取り消し・置き換え）のうち、終わった日時が境目以前の行を、件数の上限までまとめて消す（BR11.1）。対象を
     * 選ぶ副問い合わせと外側の削除の両方に条件を置く。
     *
     * @param cutoff 境目
     * @param limit 1回の件数の上限
     * @return 消した行の数
     */
    @Modifying(clearAutomatically = true)
    @Query(
            value = "DELETE FROM invitations WHERE state IN ('COMPLETED', 'CANCELLED', 'REPLACED')"
                    + " AND ended_at <= :cutoff AND invitation_id IN (SELECT invitation_id FROM invitations"
                    + " WHERE state IN ('COMPLETED', 'CANCELLED', 'REPLACED') AND ended_at <= :cutoff"
                    + " ORDER BY invitation_id FETCH FIRST :limit ROWS ONLY)",
            nativeQuery = true)
    int deleteEndedBefore(@Param("cutoff") Instant cutoff, @Param("limit") int limit);

    /**
     * 期限切れの招待中のうち、有効期限が境目以前の行を、件数の上限までまとめて消す（BR11.1）。対象を選ぶ副問い合わせと外側の削除の
     * 両方に条件を置き、選んだ後に送り直された行（有効期限が新しくなった行）は消さない。
     *
     * @param cutoff 境目
     * @param limit 1回の件数の上限
     * @return 消した行の数
     */
    @Modifying(clearAutomatically = true)
    @Query(
            value = "DELETE FROM invitations WHERE state = 'PENDING' AND expires_at <= :cutoff"
                    + " AND invitation_id IN (SELECT invitation_id FROM invitations WHERE state = 'PENDING'"
                    + " AND expires_at <= :cutoff ORDER BY invitation_id FETCH FIRST :limit ROWS ONLY)",
            nativeQuery = true)
    int deleteExpiredPendingBefore(@Param("cutoff") Instant cutoff, @Param("limit") int limit);
}
