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
package cherry.mastersmith.useradmin.service;

import cherry.mastersmith.auth.domain.LockView;
import cherry.mastersmith.auth.service.LockAdministrationService;
import cherry.mastersmith.common.paging.Paging;
import cherry.mastersmith.user.domain.FieldError;
import cherry.mastersmith.user.domain.FieldErrorReason;
import cherry.mastersmith.user.domain.SearchText;
import cherry.mastersmith.user.service.ProfileCommand;
import cherry.mastersmith.user.service.ProfileUpdateResult;
import cherry.mastersmith.user.service.UserAccountService;
import cherry.mastersmith.user.service.UserAdminSlice;
import cherry.mastersmith.user.service.UserAdminSummary;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 利用者の管理の業務処理（Intent 260930-user-admin の U3、契約 C3）。B3 では一覧と、氏名・言語の変更を持つ。
 *
 * <p>このクラスには {@code @Transactional} を付けず、トランザクションを {@link TransactionTemplate} で作る。一覧は読み取りだけの
 * 1つのトランザクションで、全体の件数・行・ロックの判定を読む（BR1.6）。業務のログと監査の出来事は出さない（BR1.9・BR5.3）。
 */
@Service
public class UserAdminService {

    /** 検索の文字の項目の名前（契約 C3）。 */
    static final String Q = "q";

    private final UserAccountService userAccounts;

    private final LockAdministrationService locks;

    private final TransactionTemplate transaction;

    private final TransactionTemplate readOnly;

    /**
     * 作る。
     *
     * @param userAccounts UserAccount の口
     * @param locks Authentication の利用者の管理に向けた口
     * @param transactionManager トランザクションの管理
     */
    public UserAdminService(
            UserAccountService userAccounts,
            LockAdministrationService locks,
            PlatformTransactionManager transactionManager) {
        this.userAccounts = userAccounts;
        this.locks = locks;
        this.transaction = new TransactionTemplate(transactionManager);
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
    }

    /**
     * 利用者の一覧の1ページを読む（BR1.1〜BR1.9）。判定の順は page → q（どちらも誤りなら page の誤り。BR1.4）。
     *
     * @param actorId 操作している管理者の利用者 ID（self の判定に使う）
     * @param rawPage 要求の page（無ければ null）
     * @param search 検索の文字（無ければ null）
     * @return 結果
     */
    public UserAdminListResult list(long actorId, String rawPage, SearchText search) {
        OptionalInt parsed = Paging.parsePage(rawPage);
        if (parsed.isEmpty()) {
            return new UserAdminListResult.InvalidPage();
        }
        if (search != null && search.isTooLong()) {
            return new UserAdminListResult.Invalid(List.of(new FieldError(Q, FieldErrorReason.TOO_LONG)));
        }
        int page = parsed.getAsInt();
        UserAdminPage result = readOnly.execute(status -> readPage(actorId, page, search));
        return new UserAdminListResult.Listed(Objects.requireNonNull(result, "result"));
    }

    /**
     * 管理者による氏名と言語の変更（BR5.1〜BR5.4）。1つのトランザクションで UserAccount の口を呼ぶ。行の排他・操作した人の
     * 確かめ直し・監査はしない。
     *
     * @param userId 対象の利用者 ID
     * @param command 氏名と言語（検証の前の値）
     * @return 結果
     */
    public ProfileUpdateResult updateProfile(long userId, ProfileCommand command) {
        Objects.requireNonNull(command, "command");
        return Objects.requireNonNull(
                transaction.execute(status -> userAccounts.updateProfile(userId, command)), "result");
    }

    private UserAdminPage readPage(long actorId, int page, SearchText search) {
        UserAdminSlice slice = userAccounts.findAdminPage(search, Paging.offsetOf(page), Paging.PAGE_SIZE);
        List<Long> ids = slice.items().stream().map(UserAdminSummary::userId).toList();
        Map<Long, LockView> views = locks.lockViewsOf(ids);
        List<UserAdminEntry> entries = slice.items().stream()
                .map(summary -> new UserAdminEntry(
                        summary, views.getOrDefault(summary.userId(), LockView.NONE), summary.userId() == actorId))
                .toList();
        return new UserAdminPage(entries, page, Paging.PAGE_SIZE, slice.total());
    }
}
