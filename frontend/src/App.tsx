import { useRef, useState } from 'react'
import { ApiError, toApiError, validateInvoice, viewInvoice } from './api/client'
import type { InvoiceView } from './api/types'
import { ErrorMessage } from './components/ErrorMessage'
import { Header } from './components/Header'
import { UploadPanel } from './components/UploadPanel'
import { Viewer } from './components/Viewer'
import type { Validation } from './validation'
import './App.css'

const MAX_FILE_SIZE = 10 * 1024 * 1024 // must match spring.servlet.multipart.max-file-size

// The screen is always in exactly one of these states. The "kind" field tells
// TypeScript which one, so e.g. `invoice` can only be used when kind is 'shown'.
type State =
  | { kind: 'idle' }
  | { kind: 'loading'; fileName: string }
  | { kind: 'shown'; id: number; fileName: string; invoice: InvoiceView; validation: Validation }
  | { kind: 'failed'; fileName: string; error: ApiError }

export default function App() {
  const [state, setState] = useState<State>({ kind: 'idle' })
  // Lets us cancel uploads that are still running when a new file is chosen,
  // so a slow old answer can't replace the newer one.
  const currentRequest = useRef<AbortController | null>(null)
  const requestCounter = useRef(0)

  async function handleFile(file: File) {
    currentRequest.current?.abort()

    if (file.size > MAX_FILE_SIZE) {
      setState({
        kind: 'failed',
        fileName: file.name,
        error: new ApiError('Datei zu groß', 'Die Datei ist zu groß. Erlaubt sind höchstens 10 MB.', 413),
      })
      return
    }

    const controller = new AbortController()
    currentRequest.current = controller
    const id = ++requestCounter.current
    setState({ kind: 'loading', fileName: file.name })

    // Start both requests at once. The check (especially PDF/A) takes longer,
    // so the invoice is shown first and the report fills in when it arrives.
    // .then(ok, fail) turns the result into a Validation, so this promise never rejects.
    const validation: Promise<Validation> = validateInvoice(file, controller.signal).then(
      (report) => ({ status: 'done', report, checkedAt: new Date() }),
      (error) => ({ status: 'failed', error: toApiError(error) }),
    )

    let invoice: InvoiceView
    try {
      invoice = await viewInvoice(file, controller.signal)
    } catch (error) {
      if (controller.signal.aborted) return
      controller.abort() // the file is unreadable, so the check is not needed
      setState({ kind: 'failed', fileName: file.name, error: toApiError(error) })
      return
    }
    setState({ kind: 'shown', id, fileName: file.name, invoice, validation: { status: 'checking' } })

    const result = await validation
    if (controller.signal.aborted) return
    // Update only if the screen still shows this file.
    setState((current) => (current.kind === 'shown' && current.id === id
      ? { ...current, validation: result }
      : current))
  }

  function reset() {
    currentRequest.current?.abort()
    setState({ kind: 'idle' })
  }

  switch (state.kind) {
    case 'idle':
    case 'loading':
      return (
        <>
          <Header />
          <UploadPanel onFile={handleFile} busy={state.kind === 'loading'} />
        </>
      )
    case 'failed':
      return (
        <>
          <Header />
          <ErrorMessage error={state.error} fileName={state.fileName} onReset={reset} />
        </>
      )
    case 'shown':
      return (
        <>
          <Header fileName={state.fileName} onReset={reset} />
          {/* key: a new file gets a fresh Viewer, so the old selection is cleared. */}
          <Viewer key={state.id} invoice={state.invoice} fileName={state.fileName} validation={state.validation} />
        </>
      )
  }
}
