<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  kitApi, lineApi, stationApi,
  type FirstAidKit, type KitStocks, type RailwayLine, type Station
} from '../api'

const kits = ref<FirstAidKit[]>([])
const lines = ref<RailwayLine[]>([])
const stations = ref<Station[]>([])
const stocks = ref<KitStocks>({ stations: [], lines: [] })
const loading = ref(false)

const filterLineId = ref<number | null>(null)
const filterStationId = ref<number | null>(null)

const displayedKits = computed(() =>
  kits.value.filter(k =>
    (!filterLineId.value || k.railwayLine?.id === filterLineId.value) &&
    (!filterStationId.value || k.station?.id === filterStationId.value)
  )
)

const todayStr = () => {
  const d = new Date()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
}

// 到期日到了（今天或更早）的箱子，日期标红提醒
const isDue = (expiryDate: string | null) => !!expiryDate && expiryDate <= todayStr()

const loadLines = async () => {
  try {
    lines.value = (await lineApi.getAll()).data
  } catch {
    ElMessage.error('加载线路失败')
  }
}

const loadStations = async () => {
  try {
    stations.value = (await stationApi.getAll(true)).data
  } catch {
    ElMessage.error('加载站点失败')
  }
}

const loadKits = async () => {
  loading.value = true
  try {
    kits.value = (await kitApi.getAll()).data
  } catch {
    ElMessage.error('加载急救箱名单失败')
  } finally {
    loading.value = false
  }
}

const loadStocks = async () => {
  try {
    stocks.value = (await kitApi.getStocks()).data
  } catch {
    ElMessage.error('加载可用箱数台账失败')
  }
}

const refreshAll = () => Promise.all([loadKits(), loadStocks()])

// —— 急救箱建档 ——
const registerDialogVisible = ref(false)
const registerForm = ref({
  kitCode: '',
  stationId: null as number | null,
  batchNo: '',
  expiryDate: null as string | null
})

const openRegisterDialog = () => {
  registerForm.value = { kitCode: '', stationId: null, batchNo: '', expiryDate: null }
  registerDialogVisible.value = true
}

const submitRegister = async () => {
  const form = registerForm.value
  if (!form.kitCode.trim() || !form.stationId) {
    ElMessage.warning('请填写箱编号并选择所属站')
    return
  }
  if (!!form.batchNo.trim() !== !!form.expiryDate) {
    ElMessage.warning('批次和到期日要填一起填')
    return
  }
  try {
    await kitApi.register({
      kitCode: form.kitCode.trim(),
      stationId: form.stationId,
      ...(form.expiryDate ? { batchNo: form.batchNo.trim(), expiryDate: form.expiryDate } : {})
    })
    ElMessage.success('急救箱已建档')
    registerDialogVisible.value = false
    await refreshAll()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '建档失败')
  }
}

// —— 写批次和到期日 ——
const expiryDialogVisible = ref(false)
const expiryForm = ref({ kitId: 0, kitCode: '', batchNo: '', expiryDate: null as string | null })

const openExpiryDialog = (row: FirstAidKit) => {
  expiryForm.value = { kitId: row.id, kitCode: row.kitCode, batchNo: '', expiryDate: null }
  expiryDialogVisible.value = true
}

const submitExpiry = async () => {
  const form = expiryForm.value
  if (!form.batchNo.trim() || !form.expiryDate) {
    ElMessage.warning('批次和到期日一样都不能少')
    return
  }
  const due = form.expiryDate <= todayStr()
  try {
    await ElMessageBox.confirm(
      due
        ? `到期日 ${form.expiryDate} 已经到了：落笔后箱子「${form.kitCode}」立即撤下，本站与线路两处可用箱数同时减一。确认落笔？`
        : `到期日 ${form.expiryDate} 还没到：只落批次和到期日，箱子照算可用，两处计数不动。确认落笔？`,
      '写批次和到期日',
      { type: due ? 'warning' : 'info', confirmButtonText: '确认落笔' }
    )
  } catch {
    return
  }
  try {
    await kitApi.writeExpiry(form.kitId, {
      batchNo: form.batchNo.trim(),
      expiryDate: form.expiryDate
    })
    ElMessage.success(due ? '已落笔：箱子撤下，两处可用箱数已减一' : '已落笔：批次和到期日已登记，箱子仍在可用里')
    expiryDialogVisible.value = false
    await refreshAll()
  } catch (error: any) {
    // 两人抢写同一箱/台账缺失中断等：后端整体回滚，两处计数仍是动手前的数
    ElMessage.error(error.response?.data?.message || '落笔失败，未做任何改动')
    await refreshAll()
  }
}

const kitStatusTag = (row: FirstAidKit) => {
  if (row.status === 0) return { type: 'info' as const, text: '已到期撤下' }
  if (!row.expiryDate) return { type: 'warning' as const, text: '可用（待登记）' }
  return { type: 'success' as const, text: '可用' }
}

onMounted(async () => {
  await Promise.all([loadLines(), loadStations()])
  await refreshAll()
})
</script>

<template>
  <div class="manage-container">
    <div class="header-bar">
      <h2>急救箱药品台账</h2>
      <el-button type="primary" @click="openRegisterDialog">急救箱建档</el-button>
    </div>

    <el-alert
      type="info"
      :closable="false"
      title="每箱按箱记下批次和到期日，一箱只落一次。写上的到期日到了，箱子当场撤下，本站可用箱数和按线路加起来的可用箱数同一事务各减一；还没到期的箱不从可用里拿掉。两人同时写同一箱只成一次；写到一半中断，两处计数仍是动手前的数。"
      style="margin-bottom: 16px"
    />

    <el-row :gutter="16" class="stock-row">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>本站可用箱数（逐站）</template>
          <el-table :data="stocks.stations" border stripe size="small">
            <el-table-column prop="stationName" label="站点" />
            <el-table-column prop="lineName" label="所属线路" />
            <el-table-column label="可用箱数" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="row.availableCount > 0 ? 'success' : 'info'">{{ row.availableCount }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>按线路加起来的可用箱数（逐线）</template>
          <el-table :data="stocks.lines" border stripe size="small">
            <el-table-column prop="lineName" label="线路" />
            <el-table-column label="可用箱数" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="row.availableCount > 0 ? 'success' : 'info'">{{ row.availableCount }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <div class="filter-bar">
      <el-select v-model="filterLineId" placeholder="按线路筛选" clearable style="width: 200px; margin-right: 12px">
        <el-option v-for="line in lines" :key="line.id" :label="line.lineName" :value="line.id" />
      </el-select>
      <el-select v-model="filterStationId" placeholder="按站点筛选" clearable style="width: 220px">
        <el-option v-for="station in stations" :key="station.id" :label="station.stationName" :value="station.id" />
      </el-select>
    </div>

    <el-table :data="displayedKits" v-loading="loading" border stripe row-key="id">
      <el-table-column prop="kitCode" label="箱编号" width="130" />
      <el-table-column label="所属线路" width="120">
        <template #default="{ row }">{{ row.railwayLine?.lineName || '-' }}</template>
      </el-table-column>
      <el-table-column label="所属站点" width="140">
        <template #default="{ row }">{{ row.station?.stationName || '-' }}</template>
      </el-table-column>
      <el-table-column label="批次" width="130">
        <template #default="{ row }">{{ row.batchNo || '-' }}</template>
      </el-table-column>
      <el-table-column label="到期日" width="130">
        <template #default="{ row }">
          <el-tag v-if="row.expiryDate" :type="isDue(row.expiryDate) ? 'danger' : 'success'" effect="plain">
            {{ row.expiryDate }}
          </el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="kitStatusTag(row).type">{{ kitStatusTag(row).text }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="登记时刻" width="170">
        <template #default="{ row }">{{ row.expiryRecordedAt || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="!row.expiryDate"
            size="small"
            type="primary"
            @click="openExpiryDialog(row)"
          >写批次和到期日</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog title="急救箱建档" v-model="registerDialogVisible" width="440px">
      <el-form :model="registerForm" label-width="90px">
        <el-form-item label="箱编号" required>
          <el-input v-model="registerForm.kitCode" placeholder="如：KIT-001" />
        </el-form-item>
        <el-form-item label="所属站点" required>
          <el-select v-model="registerForm.stationId" placeholder="选择所属站" style="width: 100%">
            <el-option v-for="station in stations" :key="station.id" :label="station.stationName" :value="station.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="批次">
          <el-input v-model="registerForm.batchNo" placeholder="选填，与到期日一起填" />
        </el-form-item>
        <el-form-item label="到期日">
          <el-date-picker
            v-model="registerForm.expiryDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选填，与批次一起填"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item>
          <span class="form-tip">批次和到期日要填一起填；到期日已到的建档即撤下，不计入可用箱数。</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="registerDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitRegister">建档</el-button>
      </template>
    </el-dialog>

    <el-dialog :title="`写批次和到期日：${expiryForm.kitCode}`" v-model="expiryDialogVisible" width="440px">
      <el-form :model="expiryForm" label-width="90px">
        <el-form-item label="批次" required>
          <el-input v-model="expiryForm.batchNo" placeholder="药品批次" />
        </el-form-item>
        <el-form-item label="到期日" required>
          <el-date-picker
            v-model="expiryForm.expiryDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="药品到期日"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item>
          <span class="form-tip">每箱只落一次。到期日到了的，落笔即撤下并核减两处可用箱数；还没到期的只落档，不动计数。</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="expiryDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitExpiry">落笔</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.manage-container {
  padding: 20px;
}

.header-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.header-bar h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.stock-row {
  margin-bottom: 16px;
}

.filter-bar {
  margin-bottom: 16px;
}

.form-tip {
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
}
</style>
