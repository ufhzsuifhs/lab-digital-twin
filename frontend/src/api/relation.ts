import http from './request'

export const fetchRelationGraph = () =>
  http.get<any, { nodes: any[]; edges: any[] }>('/relation/graph')
