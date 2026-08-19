import http from './request'

/** 首页六大指标聚合 */
export function fetchOverview() {
  return http.get<any, Record<string, any>>('/dashboard/overview')
}

/** 数字孪生设备列表 */
export function fetchDevices() {
  return http.get<any, any[]>('/dashboard/devices')
}

/** 单个设备详情 */
export function fetchDeviceDetail(id: string) {
  return http.get<any, Record<string, any>>(`/dashboard/device/${id}`)
}
