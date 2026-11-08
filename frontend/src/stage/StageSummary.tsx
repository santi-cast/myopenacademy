import type { StageCounts } from '../api/types'
import { progressText, progressWidth } from '../progress'

export function StageSummary({ counts }: { counts: StageCounts }) {
  // Reuse the lesson formatter's rounding, server percentage and zero-available rules.
  const display = {
    completedLessons: counts.completedStages,
    availableLessons: counts.availableStages,
    plannedLessons: counts.plannedStages,
    percentage: counts.percentage,
  }
  return <>
    <p className="summary">{progressText(display).replace('lecciones', 'etapas')}</p>
    <div className="bar" aria-hidden="true"><span style={{ width: progressWidth(display) }} /></div>
  </>
}
