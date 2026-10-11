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
package cherry.mastersmith.navigation.service;

import cherry.mastersmith.navigation.domain.NavIconPolicy;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * アイコンの許した名前の一覧のファイルの読み込み（{@code rules.md} の BR4.1・BR4.3、NFR6.8、{@code reliability-design.md} の 4節）。
 *
 * <p>起動時にコンストラクターで {@value #LOCATION} を1回だけ読み、1行ずつ、{@code #} で始まる行と空行を飛ばし、前後の空白を除いた名前の
 * 集まりにする。ファイルが無い・読めない・名前が0個・{@code list} が無い・名前が重なるときは、ERROR に理由の種類だけを出して起動を
 * 止める（設定の誤りを早く止める）。
 */
@Component
public class AllowedNavIconList {

    /** 一覧のファイルの場所（クラスパス）。 */
    public static final String LOCATION = "navigation/allowed-icons.txt";

    private static final Logger LOG = LoggerFactory.getLogger(AllowedNavIconList.class);

    private final NavIconPolicy policy;

    /**
     * 一覧のファイルを読む。
     *
     * @throws InvalidIconListException 一覧のファイルの誤りのとき（起動を止める）
     */
    public AllowedNavIconList() {
        this.policy = new NavIconPolicy(load(new ClassPathResource(LOCATION)));
    }

    /**
     * 照らし合わせの決まりを返す。
     *
     * @return 照らし合わせ
     */
    public NavIconPolicy policy() {
        return policy;
    }

    /**
     * 一覧のファイルを読み、名前の集まりにする。誤りのときは ERROR に理由の種類だけを出す。
     *
     * @param resource 一覧のファイル
     * @return 名前の集まり（ファイルの順）
     * @throws InvalidIconListException 一覧のファイルの誤りのとき
     */
    public static Set<String> load(Resource resource) {
        try {
            if (!resource.exists()) {
                throw new InvalidIconListException(Failure.MISSING);
            }
            String content;
            try (InputStream in = resource.getInputStream()) {
                content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new InvalidIconListException(Failure.UNREADABLE);
            }
            return parse(content);
        } catch (InvalidIconListException e) {
            LOG.atError().addKeyValue("reason", e.failure()).log("業務のメニューのアイコンの一覧のファイルに誤りがあるため、起動を止めます");
            throw e;
        }
    }

    /**
     * 一覧のファイルの中身を名前の集まりにする（ログは出さない）。
     *
     * @param content ファイルの中身
     * @return 名前の集まり（ファイルの順）
     * @throws InvalidIconListException 名前が0個・{@code list} が無い・名前が重なるとき
     */
    public static Set<String> parse(String content) {
        Set<String> names = new LinkedHashSet<>();
        for (String line : content.split("\\R", -1)) {
            String name = line.strip();
            if (name.isEmpty() || name.startsWith("#")) {
                continue;
            }
            if (!names.add(name)) {
                throw new InvalidIconListException(Failure.DUPLICATE);
            }
        }
        if (names.isEmpty()) {
            throw new InvalidIconListException(Failure.EMPTY);
        }
        if (!names.contains(NavIconPolicy.DEFAULT_ICON)) {
            throw new InvalidIconListException(Failure.NO_DEFAULT);
        }
        return names;
    }

    /** 一覧のファイルの誤りの種類。 */
    public enum Failure {
        /** ファイルが無い。 */
        MISSING,
        /** ファイルを読めない。 */
        UNREADABLE,
        /** 名前が1つも無い。 */
        EMPTY,
        /** 既定のアイコン {@code list} が無い。 */
        NO_DEFAULT,
        /** 名前が重なる。 */
        DUPLICATE
    }

    /** 一覧のファイルの誤り（起動を止める）。文には誤りの種類だけを入れる。 */
    public static final class InvalidIconListException extends IllegalStateException {

        private static final long serialVersionUID = 1L;

        private final Failure failure;

        /**
         * 作る。
         *
         * @param failure 誤りの種類
         */
        public InvalidIconListException(Failure failure) {
            super("業務のメニューのアイコンの一覧のファイルに誤りがあります: " + failure);
            this.failure = failure;
        }

        /**
         * 誤りの種類を返す。
         *
         * @return 誤りの種類
         */
        public Failure failure() {
            return failure;
        }
    }
}
