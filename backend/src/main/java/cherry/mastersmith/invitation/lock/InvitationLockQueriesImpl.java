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
package cherry.mastersmith.invitation.lock;

import cherry.mastersmith.common.persistence.RowLockFailures;
import cherry.mastersmith.common.persistence.RowLockUnavailableException;
import cherry.mastersmith.invitation.domain.Invitation;
import cherry.mastersmith.invitation.domain.InvitationEmail;
import cherry.mastersmith.invitation.domain.InvitationState;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.TypedQuery;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link InvitationLockQueries} の実装（Spring Data の独自の断片。名前は {@code Impl} の後置の既定）。
 *
 * <p>メソッドの呼び出しの追跡（{@code TraceAspect}）の対象の層（{@code web}・{@code service}・{@code domain}・{@code repository}）の
 * 外の用途名の下位パッケージに置く。追跡は引数を文字列にし、{@code byte[]} の招待のトークンのハッシュ値は中身が出るため（Intent
 * 260930-user-admin の U3、依頼者の決定 案 1）。インターフェースは {@code invitation.repository} に置き、口は変えない。問い合わせは名前つきの引数だけで
 * 組み立て、文字列の連結を使わない（NFR9.3）。メールアドレスは伏せる値の型で受け、文字列はこの本体の中でだけ取り出す。
 */
public class InvitationLockQueriesImpl implements InvitationLockQueries {

    /** 行の排他の待ちの上限（ミリ秒）。 */
    static final int LOCK_TIMEOUT_MILLIS = 3000;

    /** 排他の種類（ログの {@code lockKind}）。 */
    static final String LOCK_KIND = "INVITATION_ROW";

    private static final Logger LOGGER = LoggerFactory.getLogger(InvitationLockQueriesImpl.class);

    private static final String LOCK_TIMEOUT_HINT = "jakarta.persistence.lock.timeout";

    private final EntityManager entityManager;

    /**
     * 断片を作る。
     *
     * @param entityManager JPA のエンティティの管理
     */
    public InvitationLockQueriesImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Optional<Invitation> findByEmailAndStateForUpdate(InvitationEmail email, InvitationState state) {
        return lockOne(
                "select i from Invitation i where i.email = :email and i.state = :state",
                query -> query.setParameter("email", email.value()).setParameter("state", state));
    }

    @Override
    public Optional<Invitation> findByIdForUpdate(long invitationId) {
        return lockOne(
                "select i from Invitation i where i.invitationId = :invitationId",
                query -> query.setParameter("invitationId", invitationId));
    }

    @Override
    public Optional<Invitation> findByTokenHashForUpdate(byte[] tokenHash) {
        return lockOne(
                "select i from Invitation i where i.tokenHash = :tokenHash",
                query -> query.setParameter("tokenHash", tokenHash));
    }

    /**
     * 行の排他つきで多くて1件を読む。排他を取れなかったときは WARN（排他の種類とクラスの名前）を出してから、値を含まない例外を投げる。
     */
    private Optional<Invitation> lockOne(String jpql, UnaryOperator<TypedQuery<Invitation>> parameters) {
        try {
            TypedQuery<Invitation> query = entityManager
                    .createQuery(jpql, Invitation.class)
                    .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                    .setHint(LOCK_TIMEOUT_HINT, LOCK_TIMEOUT_MILLIS);
            List<Invitation> rows = parameters.apply(query).getResultList();
            return rows.stream().findFirst();
        } catch (PersistenceException e) {
            if (!RowLockFailures.isLockFailure(e)) {
                throw e;
            }
            RowLockFailures.warn(LOGGER, LOCK_KIND, e);
            throw new RowLockUnavailableException(LOCK_KIND);
        }
    }
}
