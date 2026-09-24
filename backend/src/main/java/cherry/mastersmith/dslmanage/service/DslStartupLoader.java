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
package cherry.mastersmith.dslmanage.service;

import cherry.mastersmith.dsl.domain.DslReadResult;
import cherry.mastersmith.dsl.service.ActiveDslModelHolder;
import cherry.mastersmith.dsl.service.DslReader;
import cherry.mastersmith.dslmanage.domain.DslAppliedRef;
import cherry.mastersmith.dslmanage.domain.DslContent;
import cherry.mastersmith.dslmanage.domain.DslDownload;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/**
 * 起動時（要求を受ける前）に、適用中の DSL を読んで適用中のモデルに入れる（BR5.1・BR5.2、NFR8.4）。
 *
 * <p>すべての部品を作り終えた後、Web の待ち受けを始める前に呼ばれる（{@link SmartInitializingSingleton}）。適用中の DSL が今の検証を
 * 通らないとき（書式の版が変わった など）は、ERROR を1件（識別の先頭12文字と誤りの種類だけ。本文は出さない）出し、「無い」で
 * 起動を続ける。内部DB を読めないとき（想定外）は起動を止める。
 */
@Component
public class DslStartupLoader implements SmartInitializingSingleton {

    /** 読めなかったときの ERROR の文言。 */
    static final String INVALID_MESSAGE = "適用中の DSL を読めないため、適用中の DSL が無い状態で起動します";

    private static final Logger LOGGER = LoggerFactory.getLogger(DslStartupLoader.class);

    private final DslRecordStore store;

    private final DslReader dslReader;

    private final ActiveDslModelHolder activeDslModelHolder;

    /**
     * 作る。
     *
     * @param store 保存
     * @param dslReader DSL の読み込み（U2）
     * @param activeDslModelHolder 適用中のモデルの差し替え（U2）
     */
    public DslStartupLoader(DslRecordStore store, DslReader dslReader, ActiveDslModelHolder activeDslModelHolder) {
        this.store = store;
        this.dslReader = dslReader;
        this.activeDslModelHolder = activeDslModelHolder;
    }

    @Override
    public void afterSingletonsInstantiated() {
        load();
    }

    /** 適用中の DSL を読み、適用中のモデルに入れる（無い・読めないときは「無い」）。 */
    void load() {
        Optional<DslContent<DslAppliedRef>> current = store.findCurrentRevisionContent();
        if (current.isEmpty()) {
            activeDslModelHolder.replace(null);
            return;
        }
        DslContent<DslAppliedRef> content = current.get();
        switch (dslReader.read(content.yamlBytes())) {
            case DslReadResult.Valid valid -> activeDslModelHolder.replace(valid.model());
            case DslReadResult.Invalid invalid -> {
                LOGGER.atError()
                        .addKeyValue(
                                "dsl.hash", DslDownload.prefix(content.ref().dslHash()))
                        .addKeyValue("dsl.revisionId", content.ref().revisionId())
                        .addKeyValue(
                                "dsl.errorKinds",
                                invalid.errors().stream()
                                        .map(error -> error.kind().name())
                                        .distinct()
                                        .toList())
                        .log(INVALID_MESSAGE);
                activeDslModelHolder.replace(null);
            }
        }
    }
}
