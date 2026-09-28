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
package cherry.mastersmith.invitation.service;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring の定期実行を有効にする（招待の定期の削除。NFR9.10、RD-D3）。既存の {@code auth} の設定にも同じ指定があるが、招待の定期の削除が
 * {@code auth} の設定に黙って頼らないよう、ここにも置く（重ねて置いても害は無い）。
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class InvitationSchedulingConfig {}
