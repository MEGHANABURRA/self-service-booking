import { useEffect, useId, useRef, useState } from 'react'
import './SearchableDropdown.css'

export interface SearchableDropdownOption {
  value: string
  label: string
  detail?: string
  searchText?: string
}

interface SearchableDropdownProps {
  ariaLabel: string
  describedBy?: string
  disabled?: boolean
  error?: string | null
  id?: string
  invalid?: boolean
  loading?: boolean
  onChange: (value: string) => void
  options: SearchableDropdownOption[]
  placeholder: string
  searchPlaceholder: string
  value: string
  variant?: 'full' | 'compact'
}

function SearchableDropdown({
  ariaLabel,
  describedBy,
  disabled = false,
  error = null,
  id,
  invalid = false,
  loading = false,
  onChange,
  options,
  placeholder,
  searchPlaceholder,
  value,
  variant = 'full',
}: SearchableDropdownProps) {
  const [isOpen, setIsOpen] = useState(false)
  const [query, setQuery] = useState('')
  const rootRef = useRef<HTMLDivElement>(null)
  const triggerRef = useRef<HTMLButtonElement>(null)
  const listId = useId()
  const selectedOption = options.find((option) => option.value === value)
  const normalizedQuery = query.trim().toLocaleLowerCase()
  const filteredOptions = options.filter((option) =>
    `${option.label} ${option.detail ?? ''} ${option.searchText ?? ''}`
      .toLocaleLowerCase()
      .includes(normalizedQuery),
  )

  useEffect(() => {
    if (!isOpen) {
      return
    }

    const trigger = triggerRef.current
    const triggerRect = trigger?.getBoundingClientRect()
    const panelHeight = Math.min(320, window.innerHeight * 0.42)
    if (
      trigger &&
      triggerRect &&
      window.innerHeight - triggerRect.bottom < panelHeight + 12
    ) {
      trigger.scrollIntoView({ block: 'center' })
    }

    function dismiss(event: PointerEvent | KeyboardEvent) {
      if (event instanceof KeyboardEvent && event.key === 'Escape') {
        setIsOpen(false)
        triggerRef.current?.focus()
      }

      if (
        event instanceof PointerEvent &&
        event.target instanceof Node &&
        !rootRef.current?.contains(event.target)
      ) {
        setIsOpen(false)
      }
    }

    document.addEventListener('pointerdown', dismiss)
    document.addEventListener('keydown', dismiss)
    return () => {
      document.removeEventListener('pointerdown', dismiss)
      document.removeEventListener('keydown', dismiss)
    }
  }, [isOpen])

  function chooseOption(option: SearchableDropdownOption) {
    onChange(option.value)
    setQuery('')
    setIsOpen(false)
  }

  return (
    <div
      className={`searchable-dropdown searchable-dropdown--${variant}`}
      ref={rootRef}
    >
      <button
        aria-controls={listId}
        aria-expanded={isOpen}
        aria-haspopup="listbox"
        aria-invalid={invalid}
        aria-label={ariaLabel}
        aria-describedby={describedBy}
        className="searchable-dropdown-trigger"
        disabled={disabled}
        id={id}
        ref={triggerRef}
        type="button"
        onClick={() => setIsOpen((open) => !open)}
      >
        <span className="searchable-dropdown-value">
          {selectedOption?.label ?? placeholder}
        </span>
        <span aria-hidden="true" className="searchable-dropdown-chevron" />
      </button>
      {isOpen && (
        <div className="searchable-dropdown-panel">
          <input
            autoFocus
            className="searchable-dropdown-search"
            placeholder={searchPlaceholder}
            type="search"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
          />
          <div
            aria-label={ariaLabel}
            aria-live="polite"
            className="searchable-dropdown-options"
            id={listId}
            role="listbox"
          >
            {loading && <p className="searchable-dropdown-status">Loading…</p>}
            {!loading && error && (
              <p className="searchable-dropdown-status">{error}</p>
            )}
            {!loading && !error && filteredOptions.length === 0 && (
              <p className="searchable-dropdown-status">No matching options.</p>
            )}
            {!loading && !error && filteredOptions.map((option) => (
              <button
                aria-selected={option.value === value}
                className="searchable-dropdown-option"
                key={option.value}
                role="option"
                type="button"
                onClick={() => chooseOption(option)}
              >
                <span>{option.detail ?? option.label}</span>
                {option.detail && <span>{option.label}</span>}
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}

export default SearchableDropdown