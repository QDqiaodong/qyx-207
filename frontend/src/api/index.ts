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
  // 本条是冲正单时指向被冲正的原记录；普通变更为 null
  reversalOfId: number | null
  // 本条已被冲正时指向冲掉它的冲正单；未被冲正为 null
  reversedById: number | null
}

// 清扫占台单：1=清扫中（生效占台），2=已结束
export interface CleaningOccupancy {
  id: number
  bench: RestBench
  benchCode: string
  stationId: number
  stationName: string
  lineId: number
  lineName: string
  status: number
  reason: string
  operator: string
  startedAt: string
  finishedAt: string | null
}

// 当前在座记录
export interface BenchSitting {
  id: number
  bench: RestBench
  benchCode: string
  passengerName: string | null
  passengerKey: string
  satAt: string
}

export interface CleaningStartPayload {
  benchIds: number[]
  reason?: string
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
  },
  // 冲正：对写错的变更记录补一条反向记录，原记录保留并标记已被冲正
  reverseRecord: (recordId: number, data: { reason?: string }) =>
    instance.post<ChangeRecord>(`/benches/records/${recordId}/reverse`, data)
}

export const cacheApi = {
  getBenchMaterials: () => instance.get('/cache/bench-materials'),
  getBenchCount: () => instance.get('/cache/bench-count')
}

// 清扫占台：只按点名的具体休息台开清扫，没有整线/整站入口
export const cleaningApi = {
  start: (data: CleaningStartPayload) =>
    instance.post<CleaningOccupancy[]>('/benches/cleaning/start', data),
  finish: (occupancyId: number) =>
    instance.put<CleaningOccupancy>(`/benches/cleaning/${occupancyId}/finish`),
  getActive: () =>
    instance.get<CleaningOccupancy[]>('/benches/cleaning/active'),
  getByBench: (benchId: number) =>
    instance.get<CleaningOccupancy[]>(`/benches/${benchId}/cleaning`)
}

// 就座/离开：清扫占台中的台落座会被后端拒绝
export const sittingApi = {
  sit: (benchId: number, data: { passengerName?: string; passengerKey: string }) =>
    instance.post<BenchSitting>(`/benches/${benchId}/sit`, data),
  leave: (sittingId: number) =>
    instance.delete(`/benches/sittings/${sittingId}`),
  getAll: () =>
    instance.get<BenchSitting[]>('/benches/sittings')
}
