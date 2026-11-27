import { http } from '@/api/http'
import type { TriageResult } from '@/types/models'

export const triageApi = {
  ask(symptoms: string) {
    return http.post<TriageResult>('/triage/ask', { symptoms })
  },

  /**
   * SSE 流式导诊：fetch + ReadableStream 接收，onChunk 回调每段文本。
   * 返回 Promise，流结束 resolve，出错 reject。
   */
  async askStream(symptoms: string, onChunk: (text: string) => void): Promise<void> {
    const token = localStorage.getItem('token')
    const response = await fetch('/api/triage/ask/stream', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      },
      body: JSON.stringify({ symptoms })
    })
    if (!response.ok) {
      throw new Error(`导诊请求失败: ${response.status}`)
    }
    const reader = response.body?.getReader()
    if (!reader) {
      throw new Error('浏览器不支持流式读取')
    }
    const decoder = new TextDecoder()
    let buffer = ''
    try {
      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        // SSE 格式：data:xxx\n\n，可能有多条
        const lines = buffer.split('\n')
        buffer = lines.pop() ?? ''
        for (const line of lines) {
          if (line.startsWith('data:')) {
            const text = line.slice(5)
            if (text) onChunk(text)
          }
        }
      }
    } finally {
      reader.releaseLock()
    }
  }
}
