import { ImagePlus, X } from 'lucide-react'
import { useEffect, useId, useRef, useState, type DragEvent } from 'react'
import { cn, validateImage } from '../lib/utils'

interface Props {
  file: File | null
  onChange: (file: File | null) => void
  error?: string
}

export function ImageDropzone({ file, onChange, error }: Props) {
  const id = useId()
  const inputRef = useRef<HTMLInputElement>(null)
  const [dragging, setDragging] = useState(false)
  const [localError, setLocalError] = useState<string | null>(null)

  const [preview, setPreview] = useState<string | null>(null)
  useEffect(() => {
    if (!file) {
      setPreview(null)
      return
    }
    const url = URL.createObjectURL(file)
    setPreview(url)
    return () => URL.revokeObjectURL(url)
  }, [file])

  const accept = (f: File | undefined | null) => {
    if (!f) return
    const err = validateImage(f)
    if (err) {
      setLocalError(err)
      return
    }
    setLocalError(null)
    onChange(f)
  }

  const onDrop = (e: DragEvent) => {
    e.preventDefault()
    setDragging(false)
    accept(e.dataTransfer.files?.[0])
  }

  const shownError = localError ?? error

  if (file && preview) {
    return (
      <div className="relative overflow-hidden rounded-2xl border border-line bg-surface-2">
        <img src={preview} alt="Selected upload preview" className="max-h-[480px] w-full object-cover" />
        <div className="absolute inset-x-0 bottom-0 flex items-center justify-between gap-2 bg-gradient-to-t from-black/80 to-transparent p-3 text-xs">
          <span className="truncate text-ink/90">
            {file.name} · {(file.size / 1024 / 1024).toFixed(2)} MB
          </span>
        </div>
        <button
          type="button"
          onClick={() => {
            onChange(null)
            if (inputRef.current) inputRef.current.value = ''
          }}
          className="absolute right-3 top-3 rounded-full bg-black/70 p-2 text-ink backdrop-blur hover:bg-danger"
          aria-label="Remove image"
        >
          <X className="h-4 w-4" aria-hidden />
        </button>
      </div>
    )
  }

  return (
    <div>
      <label
        htmlFor={id}
        onDragOver={(e) => {
          e.preventDefault()
          setDragging(true)
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={onDrop}
        className={cn(
          'flex cursor-pointer flex-col items-center justify-center gap-2 rounded-2xl border-2 border-dashed px-6 py-10 text-center transition-colors',
          'focus-within:ring-2 focus-within:ring-amber',
          dragging ? 'border-amber bg-amber-muted/40' : 'border-line bg-surface-2 hover:border-amber/60',
          shownError && 'border-danger',
        )}
      >
        <span className="flex h-12 w-12 items-center justify-center rounded-2xl bg-amber-muted text-amber">
          <ImagePlus className="h-6 w-6" aria-hidden />
        </span>
        <span className="font-medium">
          Drop an image here or <span className="text-amber underline-offset-2 hover:underline">browse</span>
        </span>
        <span className="text-xs text-muted">JPEG, PNG or WebP · up to 5 MB</span>
        <input
          ref={inputRef}
          id={id}
          type="file"
          accept="image/jpeg,image/png,image/webp"
          className="sr-only"
          onChange={(e) => accept(e.target.files?.[0])}
        />
      </label>
      {shownError && (
        <p className="field-error" role="alert">
          {shownError}
        </p>
      )}
    </div>
  )
}
