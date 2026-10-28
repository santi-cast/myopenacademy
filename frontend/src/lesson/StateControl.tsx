import { useId } from 'react'
import { STATE_HELP, savedText, type SaveState, type SaveStatus } from './saveStatus'

function Status({ status }: { status: SaveStatus }) {
  return (
    <p className="save-status" role="status">
      {status.kind === 'saving' && <span className="save-progress">Guardando…</span>}
      {status.kind === 'saved' && (
        <span className="save-ok"><span aria-hidden="true">✓ </span><span>{savedText(status.updatedAt)}</span></span>
      )}
      {status.kind === 'failed' && <span className="save-error">{status.message}</span>}
    </p>
  )
}

export function StateControl<S extends string>({ legend, options, saved, save, onChange }: {
  legend: string
  options: { value: S; label: string }[]
  saved: S
  save: SaveState<S>
  onChange: (value: S) => void
}) {
  const id = useId()
  const value = save.pending ?? saved
  const saving = save.status.kind === 'saving'
  return (
    <fieldset className="state" aria-describedby={`${id}-help`}>
      <legend>{legend}</legend>
      <p className="state-help" id={`${id}-help`}>{STATE_HELP}</p>
      <div className="seg">
        {options.map((option) => (
          <span key={option.value} className="seg-option">
            <input
              type="radio"
              id={`${id}-${option.value}`}
              name={id}
              value={option.value}
              checked={value === option.value}
              disabled={saving}
              onChange={() => onChange(option.value)}
            />
            <label htmlFor={`${id}-${option.value}`}>{option.label}</label>
          </span>
        ))}
      </div>
      <Status status={save.status} />
    </fieldset>
  )
}
