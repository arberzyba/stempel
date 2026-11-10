import { useRef, useState } from 'react'
import { ApiError, viewInvoice } from './api/client'
import type { InvoiceView } from './api/types'
import { CheckPanel } from './components/CheckPanel'
import { ErrorMessage } from './components/ErrorMessage'
import { Header } from './components/Header'
import { InvoiceDocument } from './components/InvoiceDocument'
import { UploadPanel } from './components/UploadPanel'
import './App.css'

const MAX_FILE_SIZE = 10 * 1024 * 1024 // must match spring.servlet.multipart.max-file-size

// The screen is always in exactly one of these states. The "kind" field tells
// TypeScript which one, so e.g. `invoice` can only be used when kind is 'shown'.
type State =
  | { kind: 'idle' }
  | { kind: 'loading'; fileName: string }
  | { kind: 'shown'; fileName: string; invoice: InvoiceView }
  | { kind: 'failed'; fileName: string; error: ApiError }

export default function App() {
  const [state, setState] = useState<State>({ kind: 'idle' })
  // Lets us cancel an upload that is still running when a new file is chosen,
  // so a slow old answer can't replace the newer one.
  const currentRequest = useRef<AbortController | null>(null)

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
    setState({ kind: 'loading', fileName: file.name })

    try {
      const invoice = await viewInvoice(file, controller.signal)
      setState({ kind: 'shown', fileName: file.name, invoice })
    } catch (error) {
      if (controller.signal.aborted) return
      const apiError = error instanceof ApiError
        ? error
        : new ApiError('Fehler', 'Etwas ist schiefgelaufen. Bitte versuchen Sie es erneut.', 0)
      setState({ kind: 'failed', fileName: file.name, error: apiError })
    }
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
          <main className="page viewer">
            <InvoiceDocument invoice={state.invoice} />
            <CheckPanel invoice={state.invoice} fileName={state.fileName} />
          </main>
        </>
      )
  }
}
