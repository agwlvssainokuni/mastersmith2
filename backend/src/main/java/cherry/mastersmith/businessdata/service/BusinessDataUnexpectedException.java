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
package cherry.mastersmith.businessdata.service;

import java.io.Serial;

/**
 * 業務データの読み取りの想定外の失敗（500。logical-components.md の L3）。行の値・JDBC の例外の文を含みうるため、メッセージも原因も
 * 持たない（BR10.2）。手がかり（SQLState・業者の番号・例外のクラスの名前）は、投げる前に業務処理の層が WARN で1行出す。
 */
public final class BusinessDataUnexpectedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 作る。 */
    public BusinessDataUnexpectedException() {
        super(null, null, false, true);
    }
}
