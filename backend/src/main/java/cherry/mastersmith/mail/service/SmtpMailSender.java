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
package cherry.mastersmith.mail.service;

import cherry.mastersmith.mail.config.MailSettings;
import cherry.mastersmith.mail.domain.MailFailureKind;
import cherry.mastersmith.mail.domain.MailRequest;
import cherry.mastersmith.mail.domain.MailRequestValidation;
import cherry.mastersmith.mail.domain.MailSendResult;
import cherry.mastersmith.mail.domain.MailUnexpectedException;
import cherry.mastersmith.mail.template.MailTemplateRegistry;
import cherry.mastersmith.mail.template.RenderedMail;
import cherry.mastersmith.mail.template.RenderedMailInspector;
import cherry.mastersmith.mail.transport.SendAttempt;
import cherry.mastersmith.mail.transport.SmtpMailTransport;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * メールの送信の入口（契約 C1、functional-spec.md の 4節、logical-components.md の 2節・6節）。
 *
 * <p>設定の状態 → 依頼の確かめ → 描画 → 件名と lang の確かめ → 送信の順に呼び、送信ごとにログを1件（成功は INFO、失敗は WARN）
 * 出し、Observation {@code mastersmith.mail.send} を1つ作る。ログとタグには templateId・language（未知は {@code unknown}）・結果の
 * 種類・例外の型の名前だけを出し、宛先・差し込んだ値・件名・本文・SMTP の応答は出さない（BR6.2・BR6.3）。監査ログには書かず
 * （BR6.4）、トランザクションを持たない（BR5.4）。
 */
@Service
public class SmtpMailSender implements MailSender {

    /** Observation の名前。 */
    public static final String OBSERVATION_NAME = "mastersmith.mail.send";

    /** 未知の templateId・language をログとタグに出すときの値。 */
    static final String UNKNOWN = "unknown";

    /** 想定外の失敗のタグの値（失敗の種類に当たらないため）。 */
    static final String UNEXPECTED = "unexpected";

    private static final Logger LOGGER = LoggerFactory.getLogger(SmtpMailSender.class);

    private final MailSettings settings;

    private final MailTemplateRegistry registry;

    private final SmtpMailTransport transport;

    private final ObservationRegistry observationRegistry;

    /**
     * 入口を作る。
     *
     * @param settings 起動時に点検した設定
     * @param registry 準備したテンプレート
     * @param transport 組み立てと送信の部品
     * @param observationRegistry 観測の登録先
     */
    public SmtpMailSender(
            MailSettings settings,
            MailTemplateRegistry registry,
            SmtpMailTransport transport,
            ObservationRegistry observationRegistry) {
        this.settings = settings;
        this.registry = registry;
        this.transport = transport;
        this.observationRegistry = observationRegistry;
    }

    @Override
    public boolean isConfigured() {
        return settings instanceof MailSettings.Usable;
    }

    @Override
    public MailSendResult send(MailRequest request) {
        String template = registry.contains(request.templateId()) ? request.templateId() : UNKNOWN;
        String language =
                MailRequestValidation.SUPPORTED_LANGUAGES.contains(request.language()) ? request.language() : UNKNOWN;
        Observation observation = Observation.createNotStarted(OBSERVATION_NAME, observationRegistry)
                .lowCardinalityKeyValue("mail.template", template)
                .lowCardinalityKeyValue("mail.language", language)
                .start();
        try (Observation.Scope scope = observation.openScope()) {
            SendAttempt attempt = attempt(request);
            MailSendResult result = attempt.result();
            observation
                    .lowCardinalityKeyValue("mail.outcome", result.isSent() ? "sent" : "failed")
                    .lowCardinalityKeyValue("mail.failure.kind", result.isSent() ? "none" : tag(result.failureKind()));
            log(template, language, attempt);
            return result;
        } catch (MailUnexpectedException e) {
            // 想定外の失敗のログは呼び出し元の変換の境界で出す（BR6.2）。観測には包んだ例外だけを渡す（原因の文言を載せない）。
            observation
                    .lowCardinalityKeyValue("mail.outcome", "failed")
                    .lowCardinalityKeyValue("mail.failure.kind", UNEXPECTED);
            observation.error(e);
            throw e;
        } finally {
            observation.stop();
        }
    }

    /** 状態・確かめ・描画・件名と lang・送信を順に行う。 */
    private SendAttempt attempt(MailRequest request) {
        if (!(settings instanceof MailSettings.Usable usable)) {
            return failed(MailFailureKind.NOT_CONFIGURED);
        }
        Optional<RenderedMail> rendered;
        try {
            Optional<Set<String>> names = registry.variableNames(request.templateId());
            Optional<MailFailureKind> invalid =
                    MailRequestValidation.validate(request, names.isPresent(), names.orElse(Set.of()));
            if (invalid.isPresent()) {
                return failed(invalid.get());
            }
            rendered = registry.render(request.templateId(), request.language(), request.variables())
                    .flatMap(html -> RenderedMailInspector.inspect(request.templateId(), request.language(), html));
        } catch (RuntimeException e) {
            // 最後の守り: 確かめ・描画・件名の取り出しの中で包まれていない実行時の例外（U1 の不具合）は、文言と原因を出さないよう包む。
            throw MailUnexpectedException.of(e);
        }
        if (rendered.isEmpty()) {
            return failed(MailFailureKind.TEMPLATE_ERROR);
        }
        return transport.send(usable, rendered.get(), request.to());
    }

    private static SendAttempt failed(MailFailureKind kind) {
        return new SendAttempt(MailSendResult.failed(kind), null);
    }

    private static String tag(MailFailureKind kind) {
        return kind.name().toLowerCase(Locale.ROOT);
    }

    /** 送信ごとに1件だけログを出す（キーと値。例外の物は渡さない）。 */
    private static void log(String template, String language, SendAttempt attempt) {
        MailSendResult result = attempt.result();
        if (result.isSent()) {
            LOGGER.atInfo()
                    .addKeyValue("templateId", template)
                    .addKeyValue("language", language)
                    .log("メールを送信しました");
            return;
        }
        var event = LOGGER.atWarn()
                .addKeyValue("templateId", template)
                .addKeyValue("language", language)
                .addKeyValue("failureKind", result.failureKind().name());
        if (attempt.exceptionType() != null) {
            event = event.addKeyValue("exceptionType", attempt.exceptionType());
        }
        event.log("メールを送信できませんでした");
    }
}
