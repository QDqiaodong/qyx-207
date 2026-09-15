import axios from 'axios'

const instance = axios.create({
  baseURL: '/api',
  timeout: 10000
})

export interface RailwayLine {
  id: number
  lineCode: string
  lineName: string
  lineType: string
  description: string
  status: number
}

export interface Station {
  id: number
  stationCode: string
  stationName: string
  lineOrder: number
  railwayLine: RailwayLine
  lineId: number
  status: number
}

export interface RestBench {
  id: number
  benchCode: string
  material: string
  specification: string
  positionDesc: string
  railwayLine: RailwayLine
  station: Station
  lineId: number
  stationId: number
  status: number
}

export interface ChangeRecord {
  id: number
  benchId: number
  benchCode: string
  changeType: string
  oldLineId: number
  oldLineName: string
  newLineId: number
  newLineName: string
  oldStationId: number
  oldStationName: string
  newStationId: number
  newStationName: string
  changeReason: string
  operator: string
  createdAt: string
}

export const lineApi = {
  getAll: () => instance.get<RailwayLine[]>('/lines'),
  getById: (id: number) => instance.get<RailwayLine>(`/lines/${id}`),
  create: (data: Partial<RailwayLine>) => instance.post<RailwayLine>('/lines', data),
  update: (id: number, data: Partial<RailwayLine>) => instance.put<RailwayLine>(`/lines/${id}`, data),
  delete: (id: number) => instance.delete(`/lines/${id}`)
}

export const stationApi = {
  getAll: (includeAll: boolean = false) =>
    instance.get<Station[]>('/stations', { params: includeAll ? { includeAll: true } : {} }),
  getByLineId: (lineId: number, includeAll: boolean = false) =>
    instance.get<Station[]>(`/stations/line/${lineId}`, { params: includeAll ? { includeAll: true } : {} }),
  getById: (id: number) => instance.get<Station>(`/stations/${id}`),
  create: (data: Partial<Station>) => instance.post<Station>('/stations', data),
  update: (id: number, data: Partial<Station>) => instance.put<Station>(`/stations/${id}`, data),
  suspend: (id: number) => instance.put<Station>(`/stations/${id}/suspend`),
  delete: (id: number) => instance.delete(`/stations/${id}`)
}

export interface BenchTransferPayload {
  lineId: number
  stationId: number
  reason?: string
}

export const benchApi = {
  getAll: () => instance.get<RestBench[]>('/benches'),
  getByLineId: (lineId: number) => instance.get<RestBench[]>(`/benches/line/${lineId}`),
  getByStationId: (stationId: number) => instance.get<RestBench[]>(`/benches/station/${stationId}`),
  getById: (id: number) => instance.get<RestBench>(`/benches/${id}`),
  create: (data: Partial<RestBench>) => instance.post<RestBench>('/benches', data),
  update: (id: number, data: Partial<RestBench>) => instance.put<RestBench>(`/benches/${id}`, data),
  transfer: (id: number, data: BenchTransferPayload) =>
    instance.put<RestBench>(`/benches/${id}/transfer`, data),
  delete: (id: number) => instance.delete(`/benches/${id}`),
  getRecords: (benchId?: number) => {
    const params = benchId ? { benchId } : {}
    return instance.get<ChangeRecord[]>('/benches/records', { params })
  }
}

export const cacheApi = {
  getBenchMaterials: () => instance.get('/cache/bench-materials'),
  getBenchCount: () => instance.get('/cache/bench-count')
}
