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
 * 描いた結果（entities.md の RenderedMail）。送信のために U1 の中だけで作り、呼び出し元には返さない。
 *
 * <p>文字列にするときは templateId と language だけを出し、件名と本文は伏せる（NFR1.2、security-design.md の 4.2 の二重の守り）。
 *
 * @param templateId テンプレートの識別
 * @param language 言語（本文の html 要素の lang 属性とも一致する）
 * @param subject 件名（本文の title 要素の文面から作る。空でなく改行を含まない）
 * @param htmlBody 描いた HTML の本文
 */
public record RenderedMail(String templateId, String language, String subject, String htmlBody) {

    /** 件名と本文を伏せて文字列にする。 */
    @Override
    public String toString() {
        return "RenderedMail[templateId=" + templateId + ", language=" + language + "]";
    }
}
