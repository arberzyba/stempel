import type { InvoiceView, ProblemDetail } from './types'

/** An error we can show to the user: a short title and a German explanation. */
export class ApiError extends Error {
  readonly title: string
  readonly status: number

  constructor(title: string, message: string, status: number) {
    super(message)
    this.name = 'ApiError'
    this.title = title
    this.status = status
  }
}

/** Uploads a file and returns the invoice as readable data. */
export async function viewInvoice(file: File, signal?: AbortSignal): Promise<InvoiceView> {
  const body = new FormData()
  body.append('file', file)

  let response: Response
  try {
    response = await fetch('/api/invoices/view', { method: 'POST', body, signal })
  } catch (error) {
    if (signal?.aborted) throw error // the user picked another file; not a real error
    throw new ApiError(
      'Keine Verbindung',
      'Der Stempel-Server ist nicht erreichbar. Bitte prüfen Sie, ob er läuft.',
      0,
    )
  }

  if (!response.ok) {
    const problem = await readProblem(response)
    throw new ApiError(
      problem.title ?? 'Fehler',
      problem.detail ?? `Unerwarteter Fehler (HTTP ${response.status}).`,
      response.status,
    )
  }
  return (await response.json()) as InvoiceView
}

async function readProblem(response: Response): Promise<ProblemDetail> {
  try {
    return (await response.json()) as ProblemDetail
  } catch {
    return {} // the body was not JSON, e.g. a proxy error page
  }
}
