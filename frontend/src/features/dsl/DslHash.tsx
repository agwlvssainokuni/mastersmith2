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
//
// DSL の識別の表示（BR5.10、accessibility-checklist.md の P7）。先頭 12 文字を示し、全体をツールチップと、
// 読み上げ用の視覚的に隠した文字で読めるようにする。
import { Tooltip } from 'make-you-chic-ui'
import { shortHash } from './format'
import './DslCommon.css'

export interface DslHashProps {
  /** 識別の全体 */
  hash: string
  'data-testid'?: string
}

/** DSL の識別 */
export function DslHash({ hash, 'data-testid': testId }: DslHashProps) {
  return (
    <Tooltip content={hash}>
      <span className="dsl-hash" data-testid={testId}>
        <span aria-hidden="true">{`${shortHash(hash)}…`}</span>
        <span className="dsl-visually-hidden">{hash}</span>
      </span>
    </Tooltip>
  )
}
