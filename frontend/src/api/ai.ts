import http from './request'

export const aiDevicePressure = (days = 7) =>
  http.get<any, Record<string, any>>('/ai/device-pressure', { params: { days } })
export const aiDelayRisk = () => http.get<any, Record<string, any>>('/ai/delay-risk')
export const aiBottleneck = () => http.get<any, Record<string, any>>('/ai/bottleneck')
export const aiEfficiency = () => http.get<any, Record<string, any>>('/ai/efficiency')
export const aiDeptEfficiency = () => http.get<any, any[]>('/ai/dept-efficiency')
export const aiResourceRecommend = (experimentItemId: string) =>
  http.get<any, any[]>('/ai/resource-recommend', { params: { experimentItemId } })
