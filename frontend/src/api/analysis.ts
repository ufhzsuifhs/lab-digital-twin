import http from './request'

// ============ 三、设备利用率 ============
export const deviceOccupation = () => http.get<any, any[]>('/analysis/device/occupation')
export const deviceUtilization = () => http.get<any, Record<string, any>>('/analysis/device/utilization')
export const utilizationTrend = (granularity = 'daily') =>
  http.get<any, any[]>('/analysis/device/utilization-trend', { params: { granularity } })
export const loadRanking = () => http.get<any, any[]>('/analysis/device/load-ranking')

// ============ 四、事业部 ============
export const businessUnitOccupation = () => http.get<any, any[]>('/analysis/business-unit/occupation')
export const businessUnitTrend = () => http.get<any, any[]>('/analysis/business-unit/trend')

// ============ 五、机种 ============
export const machineTypeRatio = () => http.get<any, any[]>('/analysis/machine-type/ratio')

// ============ 六、实验申请 ============
export const applicationTrend = (granularity = 'monthly') =>
  http.get<any, any[]>('/analysis/application/trend', { params: { granularity } })
export const deptApplicationCount = () => http.get<any, any[]>('/analysis/application/dept')
export const requestTypeDistribution = () => http.get<any, any[]>('/analysis/application/request-type')
export const categoryDistribution = () => http.get<any, any[]>('/analysis/application/category')
export const supplierRatio = () => http.get<any, any[]>('/analysis/application/supplier')

// ============ 七、完成率 ============
export const completionRateOverall = () => http.get<any, Record<string, any>>('/analysis/completion/overall')
export const completionTrend = () => http.get<any, any[]>('/analysis/completion/trend')

// ============ 八、异常 NG ============
export const ngSummary = () => http.get<any, Record<string, any>>('/analysis/abnormal/summary')
export const ngTrend = () => http.get<any, any[]>('/analysis/abnormal/trend')
export const ngTopItem = () => http.get<any, any[]>('/analysis/abnormal/top-item')
export const ngTopMachineType = () => http.get<any, any[]>('/analysis/abnormal/top-machine-type')
export const ngTopDevice = () => http.get<any, any[]>('/analysis/abnormal/top-device')
export const ngInspector = () => http.get<any, any[]>('/analysis/abnormal/inspector')
export const ngReason = () => http.get<any, any[]>('/analysis/abnormal/reason')

// ============ 九、实验结果 ============
export const resultOkNg = (source = 'reliability') =>
  http.get<any, any[]>('/analysis/result/okng', { params: { source } })
export const resultDistribution = () => http.get<any, any[]>('/analysis/result/distribution')

// ============ 十、DQA ============
export const dqaProjectRatio = () => http.get<any, any[]>('/analysis/dqa/project-ratio')
export const dqaPurpose = () => http.get<any, any[]>('/analysis/dqa/purpose')
export const dqaStage = () => http.get<any, any[]>('/analysis/dqa/stage')
export const dqaResult = () => http.get<any, any[]>('/analysis/dqa/result')
export const dqaMonthTrend = () => http.get<any, any[]>('/analysis/dqa/month-trend')

// ============ 十一、报价 ============
export const quoteDepartment = () => http.get<any, any[]>('/analysis/quote/department')
export const quoteBusinessUnit = () => http.get<any, any[]>('/analysis/quote/business-unit')
export const quoteItemRanking = () => http.get<any, any[]>('/analysis/quote/item-ranking')
export const quoteInstrumentRanking = () => http.get<any, any[]>('/analysis/quote/instrument-ranking')
export const quoteCostTrend = () => http.get<any, any[]>('/analysis/quote/cost-trend')
