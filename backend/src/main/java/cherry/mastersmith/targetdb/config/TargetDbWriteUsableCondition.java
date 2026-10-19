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
package cherry.mastersmith.targetdb.config;

import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 対象DB の書き込みの設定が「使える」ときだけ一致する条件（Intent 261016-business-data-views の U2、BR9.1・BR9.2）。書き込みの接続
 * （{@code targetWriteDataSource}）とその管理者（{@code targetWriteTransactionManager}）は、このときだけ作る。
 *
 * <p>点検は {@link TargetDbWriteSettings#inspect} と同じで、ログは出さない（WARN は {@link TargetDataSourceConfig} が1回だけ出す）。
 */
class TargetDbWriteUsableCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        TargetDbProperties properties = Binder.get(context.getEnvironment())
                .bind(TargetDbProperties.PREFIX, TargetDbProperties.class)
                .orElse(null);
        return properties != null
                && TargetDbWriteSettings.inspect(TargetDbSettings.inspect(properties), properties)
                        instanceof TargetDbWriteSettings.Usable;
    }
}
