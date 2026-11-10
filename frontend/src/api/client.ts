import type { InvoiceView, ProblemDetail, ValidationReport } from './types'

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
export function viewInvoice(file: File, signal?: AbortSignal): Promise<InvoiceView> {
  return postFile<InvoiceView>('/api/invoices/view', file, signal)
}

/** Uploads a file and returns the check report. */
export function validateInvoice(file: File, signal?: AbortSignal): Promise<ValidationReport> {
  return postFile<ValidationReport>('/api/invoices/validate', file, signal)
}

/** Turns any error into an ApiError, so the UI only has to handle one kind. */
export function toApiError(error: unknown): ApiError {
  return error instanceof ApiError
    ? error
    : new ApiError('Fehler', 'Etwas ist schiefgelaufen. Bitte versuchen Sie es erneut.', 0)
}

async function postFile<T>(path: string, file: File, signal?: AbortSignal): Promise<T> {
  const body = new FormData()
  body.append('file', file)

  let response: Response
  try {
    response = await fetch(path, { method: 'POST', body, signal })
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
  return (await response.json()) as T
}

async function readProblem(response: Response): Promise<ProblemDetail> {
  try {
    return (await response.json()) as ProblemDetail
  } catch {
    return {} // the body was not JSON, e.g. a proxy error page
  }
}
