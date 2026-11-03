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
// 時刻の欄の部品（Intent 261102-timezone-ui-polish の U2、LC8、BR2.19・BR2.20・BR2.21、PD7・RD5）。管理の画面の時刻は
// この部品だけで描く。提供元の状態で出し分ける: loading は淡い短い文（読み上げにも「読み込み中」）、error は短い文、
// ready は <time dateTime={iso}> に分までの文字（YYYY-MM-DD HH:mm ±HH:mm）。loading・error では整形しない。
// 文字は React の文字のノードとしてだけ描く（HTML として埋め込まない）。
// - DisplayTime: 時点を 1 つ描く（整形は 1 つの時刻につき 1 回）。
// - useDisplayTimeText: 時点を文字にする関数（aria-label など文字が要る場所。loading・error では短い文を返し、整形しない）と、
//   その文字を使って描く TimeValue の組。同じ時刻を文字と表示の両方に使うときに整形を 1 回で済ませる。
import { formatInstantToMinutes } from '../../shared/format/formatInstantToMinutes'
import { useMessages } from '../i18n/I18nProvider'
import { useDisplayTimeZone } from './DisplayTimeZoneProvider'
import './DisplayTime.css'

/** 時点を文字にする関数を返す（loading・error では短い文。ready のときだけ整形する）。 */
export function useDisplayTimeText(): (iso: string) => string {
  const state = useDisplayTimeZone()
  const t = useMessages()
  return (iso: string): string => {
    if (state.status === 'loading') {
      return t('displayTime.loading')
    }
    if (state.status === 'error') {
      return t('displayTime.error')
    }
    return formatInstantToMinutes(iso, state.timeZone)
  }
}

export interface TimeValueProps {
  /** 時点の文字（ISO 8601） */
  iso: string
  /** useDisplayTimeText が返した文字 */
  text: string
}

/** 整形済みの文字を、提供元の状態に合わせて描く（ready は <time>、それ以外は淡い短い文）。 */
export function TimeValue({ iso, text }: TimeValueProps) {
  const state = useDisplayTimeZone()
  if (state.status !== 'ready') {
    return <span className="display-time-note">{text}</span>
  }
  return <time dateTime={iso}>{text}</time>
}

export interface DisplayTimeProps {
  /** 時点の文字（ISO 8601） */
  iso: string
}

/** 表示のタイムゾーンで時刻を描く。 */
export function DisplayTime({ iso }: DisplayTimeProps) {
  const text = useDisplayTimeText()
  return <TimeValue iso={iso} text={text(iso)} />
}
