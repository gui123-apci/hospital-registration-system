import { http } from '@/api/http'
import type { TriageResult } from '@/types/models'

export const triageApi = {
  ask(symptoms: string) {
    return http.post<TriageResult>('/triage/ask', { symptoms })
  }
}
