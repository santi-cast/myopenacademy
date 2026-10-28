import type { Counts } from '../api/types'
import { progressText, progressWidth } from '../progress'

export function ProgressSummary({ counts }: { counts: Counts }) {
  return (
    <>
      <p className="summary">{progressText(counts)}</p>
      <div className="bar" aria-hidden="true">
        <span style={{ width: progressWidth(counts) }} />
      </div>
    </>
  )
}
