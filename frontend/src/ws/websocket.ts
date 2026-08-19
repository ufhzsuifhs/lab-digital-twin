/**
 * 轻量 STOMP over WebSocket 客户端（无需额外依赖）。
 * 后端 Spring STOMP 端点 /ws，频道 /topic/device-status、/topic/experiment-status。
 */
export interface StompMessage {
  destination: string
  body: string
}

type Handler = (msg: StompMessage) => void

export class StompClient {
  private ws: WebSocket | null = null
  private handlers = new Map<string, Set<Handler>>()
  private pendingSubs: string[] = []
  private connected = false

  connect(url: string): Promise<void> {
    return new Promise((resolve, reject) => {
      const ws = new WebSocket(url)
      this.ws = ws
      ws.onopen = () => {
        this.connected = true
        ws.send('CONNECT\naccept-version:1.1,1.0\nheart-beat:10000,10000\n\n\0')
        this.pendingSubs.forEach((d) => this.sendSubscribe(d))
        this.pendingSubs = []
        resolve()
      }
      ws.onerror = (e) => reject(e)
      ws.onmessage = (ev) => this.handleFrame(String(ev.data))
      ws.onclose = () => {
        this.connected = false
      }
    })
  }

  subscribe(destination: string, handler: Handler): void {
    if (!this.handlers.has(destination)) this.handlers.set(destination, new Set())
    this.handlers.get(destination)!.add(handler)
    if (this.connected) this.sendSubscribe(destination)
    else this.pendingSubs.push(destination)
  }

  private sendSubscribe(destination: string): void {
    this.ws?.send(`SUBSCRIBE\nid:sub-${encodeURIComponent(destination)}\ndestination:${destination}\n\n\0`)
  }

  private handleFrame(frame: string): void {
    // STOMP MESSAGE 帧：headers 与 body 以 \n\n 分隔，帧以 \0 结尾
    const sep = frame.indexOf('\n\n')
    if (sep < 0) return
    const headers = frame.slice(0, sep)
    let body = frame.slice(sep + 2)
    if (body.endsWith('\0')) body = body.slice(0, -1)
    const m = /destination:([^\n]+)/.exec(headers)
    if (!m) return
    const destination = m[1].trim()
    const set = this.handlers.get(destination)
    if (set) {
      const msg: StompMessage = { destination, body }
      set.forEach((h) => {
        try {
          h(msg)
        } catch (e) {
          console.error('stomp handler error', e)
        }
      })
    }
  }

  disconnect(): void {
    this.connected = false
    this.ws?.close()
    this.ws = null
  }
}

/** 应用级单例 */
export const stomp = new StompClient()

/** 订阅设备状态（数字孪生模型颜色实时变化） */
export function subscribeDeviceStatus(handler: (data: any) => void): void {
  stomp.subscribe('/topic/device-status', (msg) => {
    try {
      handler(JSON.parse(msg.body))
    } catch {
      /* ignore */
    }
  })
}

/** 订阅实验状态（首页指标卡实时刷新） */
export function subscribeExperimentStatus(handler: (data: any) => void): void {
  stomp.subscribe('/topic/experiment-status', (msg) => {
    try {
      handler(JSON.parse(msg.body))
    } catch {
      /* ignore */
    }
  })
}
