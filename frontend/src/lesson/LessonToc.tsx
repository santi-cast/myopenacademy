export type TocEntry = { id: string; title: string; level: 2 | 3 }

function TocList({ entries, activeId }: { entries: TocEntry[]; activeId: string | null }) {
  return (
    <ol className="toc">
      {entries.map((entry) => (
        <li key={entry.id} className={`lvl${entry.level}`}>
          <a href={`#${entry.id}`} aria-current={entry.id === activeId ? 'true' : undefined}>{entry.title}</a>
        </li>
      ))}
    </ol>
  )
}

/** «En esta lección» as a dropdown above the text; the wide-screen column is TocRail. */
export function TocDropdown({ entries, activeId }: { entries: TocEntry[]; activeId: string | null }) {
  if (entries.length === 0) return null
  return (
    <details className="toc-drop">
      <summary>En esta lección <span className="toc-drop-hint">{entries.length} apartados</span></summary>
      <nav aria-label="En esta lección"><TocList entries={entries} activeId={activeId} /></nav>
    </details>
  )
}

/** Right column from 1400px (04). */
export function TocRail({ entries, activeId }: { entries: TocEntry[]; activeId: string | null }) {
  return (
    <aside className="mix-rail">
      {entries.length > 0 && (
        <nav aria-labelledby="lesson-rail-title">
          <h2 id="lesson-rail-title">En esta lección</h2>
          <TocList entries={entries} activeId={activeId} />
        </nav>
      )}
    </aside>
  )
}
