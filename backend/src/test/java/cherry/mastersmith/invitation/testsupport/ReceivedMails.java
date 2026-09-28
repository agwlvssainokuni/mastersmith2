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
package cherry.mastersmith.invitation.testsupport;

import cherry.mastersmith.mail.testsupport.SmtpTestServer;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.subethamail.wiser.WiserMessage;

/**
 * 受け手で受けた招待メールから、本文・件名・リンク・トークンを取り出すテストの補助。取り出した値はテストの中の比較にだけ使い、出力や
 * 記録に写さない（{@code unit-test-instructions.md} 6節）。
 */
public final class ReceivedMails {

    private static final Pattern HREF = Pattern.compile("href=\"([^\"]+)\"");

    private static final Pattern TOKEN = Pattern.compile("#token=([A-Za-z0-9_-]{43})");

    private ReceivedMails() {}

    /**
     * 最後に受けたメールを返す。
     *
     * @param receiver 受け手
     * @return メール
     */
    public static MimeMessage last(SmtpTestServer receiver) {
        try {
            WiserMessage message = receiver.messages().getLast();
            return message.getMimeMessage();
        } catch (jakarta.mail.MessagingException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * メールの本文（HTML）を返す。
     *
     * @param message メール
     * @return 本文
     */
    public static String body(MimeMessage message) {
        try {
            Object content = message.getContent();
            if (content instanceof String text) {
                return text;
            }
            if (content instanceof jakarta.mail.Multipart multipart) {
                StringBuilder text = new StringBuilder();
                for (int i = 0; i < multipart.getCount(); i++) {
                    Object part = multipart.getBodyPart(i).getContent();
                    if (part instanceof String value) {
                        text.append(value);
                    } else if (part instanceof jakarta.mail.Multipart nested) {
                        for (int j = 0; j < nested.getCount(); j++) {
                            text.append(nested.getBodyPart(j).getContent());
                        }
                    }
                }
                return text.toString();
            }
            throw new IllegalStateException("本文の形が想定と違う: " + content.getClass());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (jakarta.mail.MessagingException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 本文のリンク（href の値）を返す。
     *
     * @param body 本文
     * @return リンク
     */
    public static String link(String body) {
        Matcher matcher = HREF.matcher(body);
        if (!matcher.find()) {
            throw new IllegalStateException("本文にリンクが無い");
        }
        return matcher.group(1);
    }

    /**
     * 本文のリンクからトークンを取り出す。
     *
     * @param body 本文
     * @return トークン
     */
    public static String token(String body) {
        Matcher matcher = TOKEN.matcher(link(body));
        if (!matcher.find()) {
            throw new IllegalStateException("リンクにトークンが無い");
        }
        return matcher.group(1);
    }

    /**
     * 最後に受けたメールのトークンを返す。
     *
     * @param receiver 受け手
     * @return トークン
     */
    public static String lastToken(SmtpTestServer receiver) {
        return token(body(last(receiver)));
    }
}
