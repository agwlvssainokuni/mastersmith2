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
package cherry.mastersmith.mail.template;

/**
 * 起動のときのテンプレートの準備の失敗（BR2.3）。起動を止める。
 *
 * <p>templateId・language・原因の種類だけを持ち、部品（Mustache のエンジン）の例外の文言とテンプレートの中身は持たない。
 */
public final class TemplatePreparationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 準備の失敗の原因の種類。 */
    public enum Reason {
        /** 一覧の templateId に ja か en のファイルが無い（読めない）。 */
        MISSING,
        /** Mustache として壊れていて準備できない。 */
        PARSE_ERROR,
        /** 置き場のファイルの名前が {@code <templateId>_<language>.html} の形に合わない。 */
        INVALID_NAME,
        /** 置き場に一覧に無い templateId のファイルがある。 */
        UNKNOWN_TEMPLATE
    }

    private final String templateId;

    private final String language;

    private final Reason reason;

    /**
     * 準備の失敗を作る。
     *
     * @param templateId テンプレートの識別（名前の形が違うときは null）
     * @param language 言語（名前の形が違うときは null）
     * @param reason 原因の種類
     */
    public TemplatePreparationException(String templateId, String language, Reason reason) {
        super("メールのテンプレートを準備できません（templateId=" + templateId + ", language=" + language + ", reason=" + reason + "）");
        this.templateId = templateId;
        this.language = language;
        this.reason = reason;
    }

    /**
     * templateId を返す。
     *
     * @return templateId（不明なら null）
     */
    public String templateId() {
        return templateId;
    }

    /**
     * 言語を返す。
     *
     * @return 言語（不明なら null）
     */
    public String language() {
        return language;
    }

    /**
     * 原因の種類を返す。
     *
     * @return 原因の種類
     */
    public Reason reason() {
        return reason;
    }
}
