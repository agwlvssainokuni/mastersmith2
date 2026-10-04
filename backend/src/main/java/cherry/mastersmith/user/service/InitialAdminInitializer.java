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
package cherry.mastersmith.user.service;

import cherry.mastersmith.user.domain.EmailAddress;
import cherry.mastersmith.user.domain.FontSize;
import cherry.mastersmith.user.domain.InitialAdminCreatedEvent;
import cherry.mastersmith.user.domain.InitialAdminRescueCondition;
import cherry.mastersmith.user.domain.Language;
import cherry.mastersmith.user.domain.Password;
import cherry.mastersmith.user.domain.PasswordPolicy;
import cherry.mastersmith.user.domain.RedactedText;
import cherry.mastersmith.user.domain.Theme;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 初期管理者の自動作成と救済（BR1.1〜BR1.5、NFR6.1、NFR10.3。救済は Intent 261004-safety-carryover の FR1）。
 *
 * <p>すべての部品を作った後（Flyway の適用の後）、Web サーバーが要求の受け付けを始める前に動く。設定が無い・正しくないときは
 * 作らず救わずに WARN（足りない・正しくない項目と直し方）を出し、起動は続ける。パスワードの値はどのログにも出さない。
 *
 * <p>設定が正しいときは、まず救済の操作（{@link UserAccountService#rescueInitialAdmin(RedactedText, Password)}）を呼び、結果で
 * 分ける。
 *
 * <ul>
 *   <li>救済した（設定のメールアドレスの利用者が停止中・印なし・パスワード不一致のどれかに当たった）: WARN を1行出す（FR1.7）。キーは
 *       {@code maskedEmail} と {@code conditions}（当たった条件を決まった順につないだ値）だけ
 *   <li>救済が要らない: 既にいるため作成しなかった INFO を出す（FR1.3）
 *   <li>いない: 作成する。作ったら INFO の後に {@link InitialAdminCreatedEvent} を知らせ、監査に残す（FR1.4・FR1.5）
 *   <li>救済が例外を投げた: 救済は巻き戻っている。ERROR を1行出して起動を続ける（FR1.2a）。キーは {@code maskedEmail} と
 *       {@code exceptionClass}（例外のクラスの名前）だけで、例外そのもの（メッセージ・スタックトレース・原因の連なり）は渡さない。
 *       H2 の例外の連なりの文には行の全部の列の値（メールアドレスを含む）が入りうるため。{@code team.md} の「想定外は ERROR で
 *       スタックトレース付き」との差であり、コード生成の記録に書く
 * </ul>
 *
 * <p>作るときは利用者の作成の操作（契約 C2）に、管理者・氏名＝そろえたメールアドレス・言語 ja・テーマ system・文字の大きさ md を渡し、
 * 結果の型で判定する（BR5.4）。
 *
 * <p>ログには、キー {@code maskedEmail} に伏せ字（先頭の1文字＋{@code ***}＋{@code @}＋ドメイン、
 * {@link EmailAddress#mask(String)}）だけを載せ、メールアドレスそのものは載せない。{@code maskedEmail} は外部エクスポートの
 * 伏せる対象のキーではないため、外部エクスポートでは伏せ字がそのまま送られる（Intent 260929-log-deps-cleanup の FR1、
 * {@code project.md} の Forbidden）。
 */
@Component
public class InitialAdminInitializer implements SmartInitializingSingleton {

    /** 直し方の文。 */
    static final String RESOLUTION = "MASTERSMITH_AUTH_INITIAL_ADMIN_EMAIL と MASTERSMITH_AUTH_INITIAL_ADMIN_PASSWORD"
            + "（12 文字以上、UTF-8 で 72 バイト以内）を正しく設定して再起動すると、初期管理者が作成されます";

    /** 救済した WARN のメッセージ（FR1.7）。 */
    static final String RESCUED_MESSAGE = "初期管理者を救済しました";

    /** 救済に失敗した ERROR のメッセージ（FR1.2a）。 */
    static final String RESCUE_FAILED_MESSAGE = "初期管理者の救済に失敗しました";

    private static final Logger LOGGER = LoggerFactory.getLogger(InitialAdminInitializer.class);

    private final InitialAdminProperties properties;

    private final UserAccountService userAccountService;

    private final ApplicationEventPublisher eventPublisher;

    private final Clock clock;

    /**
     * 初期管理者の作成と救済の処理を作る。
     *
     * @param properties 初期管理者の設定
     * @param userAccountService UserAccount の業務処理
     * @param eventPublisher 出来事の知らせ（作成の監査のため）
     * @param clock 時計
     */
    public InitialAdminInitializer(
            InitialAdminProperties properties,
            UserAccountService userAccountService,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.properties = properties;
        this.userAccountService = userAccountService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    public void afterSingletonsInstantiated() {
        createIfNeeded();
    }

    /**
     * 設定を検査し、必要なら初期管理者を救済するか作る。
     *
     * @return 作ったら true（救済したとき・何もしなかったときは false）
     */
    boolean createIfNeeded() {
        String email = EmailAddress.normalize(properties.email());
        String password = properties.password();
        List<String> problems = problems(email, password);
        if (!problems.isEmpty()) {
            LOGGER.atWarn()
                    .addKeyValue("reason", String.join("、", problems))
                    .addKeyValue("resolution", RESOLUTION)
                    .log("初期管理者を作成しませんでした");
            return false;
        }
        InitialAdminRescueResult rescue;
        try {
            rescue = userAccountService.rescueInitialAdmin(new RedactedText(email), new Password(password));
        } catch (RuntimeException e) {
            // 例外そのものは渡さない（メッセージと原因の連なりに行の値が入りうるため）。クラスの名前だけを載せる。
            LOGGER.atError()
                    .addKeyValue("maskedEmail", EmailAddress.mask(email))
                    .addKeyValue("exceptionClass", e.getClass().getName())
                    .log(RESCUE_FAILED_MESSAGE);
            return false;
        }
        return switch (rescue) {
            case InitialAdminRescueResult.Rescued rescued -> {
                LOGGER.atWarn()
                        .addKeyValue("maskedEmail", EmailAddress.mask(email))
                        .addKeyValue("conditions", InitialAdminRescueCondition.code(rescued.conditions()))
                        .log(RESCUED_MESSAGE);
                yield false;
            }
            case InitialAdminRescueResult.NotNeeded notNeeded -> {
                LOGGER.atInfo()
                        .addKeyValue("maskedEmail", EmailAddress.mask(email))
                        .log("初期管理者は既にいるため、作成しませんでした");
                yield false;
            }
            case InitialAdminRescueResult.NotFound notFound -> create(email, password);
        };
    }

    /** 初期管理者を作り、作ったら作成の出来事を知らせる。 */
    private boolean create(String email, String password) {
        // 初期値は氏名＝そろえたメールアドレス・ja・system・md（BR2.2・BR5.4）。
        CreateUserResult result = userAccountService.createUser(
                new NewUser(email, email, new Password(password), Language.JA, Theme.SYSTEM, FontSize.MD, true));
        return switch (result) {
            case CreateUserResult.EmailAlreadyUsed alreadyUsed -> {
                // 同時の起動などで、確かめの後に別の作成が先に確定した場合。
                LOGGER.atInfo()
                        .addKeyValue("maskedEmail", EmailAddress.mask(email))
                        .log("初期管理者は既にいるため、作成しませんでした");
                yield false;
            }
            case CreateUserResult.Created created -> {
                LOGGER.atInfo()
                        .addKeyValue("maskedEmail", EmailAddress.mask(email))
                        .log("初期管理者を作成しました");
                // 作成の確定の後（トランザクションの外）に知らせる。監査の受け手はその場で記録する。
                eventPublisher.publishEvent(new InitialAdminCreatedEvent(created.userId(), clock.instant()));
                yield true;
            }
        };
    }

    /** 設定の問題を返す（パスワードの値は載せない）。 */
    private static List<String> problems(String email, String password) {
        List<String> problems = new ArrayList<>();
        if (email == null || email.isEmpty()) {
            problems.add("メールアドレス（email）が設定されていません");
        } else if (!EmailAddress.isValid(email)) {
            problems.add("メールアドレス（email）の形式が正しくありません");
        }
        if (password == null || password.isEmpty()) {
            problems.add("パスワード（password）が設定されていません");
        } else {
            if (!PasswordPolicy.hasMinimumLength(password)) {
                problems.add("パスワード（password）が 12 文字未満です");
            }
            if (!PasswordPolicy.fitsMaxBytes(password)) {
                problems.add("パスワード（password）が UTF-8 で 72 バイトを超えています");
            }
        }
        return problems;
    }
}
