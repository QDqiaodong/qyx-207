<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  keyApi, lineApi, stationApi,
  type FirstAidKey, type KeyCheckout, type RailwayLine, type Station
} from '../api'

const keys = ref<FirstAidKey[]>([])
const lines = ref<RailwayLine[]>([])
const stations = ref<Station[]>([])
const activeCheckouts = ref<KeyCheckout[]>([])
const loading = ref(false)

const filterLineId = ref<number | null>(null)
const filterStationId = ref<number | null>(null)

const activeCheckoutByKey = (keyId: number) =>
  activeCheckouts.value.find(c => c.key?.id === keyId)

const displayedKeys = computed(() =>
  keys.value.filter(k =>
    (!filterLineId.value || k.railwayLine?.id === filterLineId.value) &&
    (!filterStationId.value || k.station?.id === filterStationId.value)
  )
)

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

const loadKeys = async () => {
  loading.value = true
  try {
    keys.value = (await keyApi.getAll()).data
  } catch {
    ElMessage.error('加载钥匙名单失败')
  } finally {
    loading.value = false
  }
}

const loadActive = async () => {
  try {
    activeCheckouts.value = (await keyApi.getActive()).data
  } catch {
    ElMessage.error('加载未交还登记失败')
  }
}

const refreshAll = () => Promise.all([loadKeys(), loadActive()])

// —— 钥匙建档 ——
const registerDialogVisible = ref(false)
const registerForm = ref({ keyCode: '', stationId: null as number | null })

const openRegisterDialog = () => {
  registerForm.value = { keyCode: '', stationId: null }
  registerDialogVisible.value = true
}

const submitRegister = async () => {
  if (!registerForm.value.keyCode.trim() || !registerForm.value.stationId) {
    ElMessage.warning('请填写钥匙编号并选择所属站')
    return
  }
  try {
    await keyApi.register({
      keyCode: registerForm.value.keyCode.trim(),
      stationId: registerForm.value.stationId
    })
    ElMessage.success('钥匙已建档')
    registerDialogVisible.value = false
    await loadKeys()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '建档失败')
  }
}

// —— 领用登记 ——
const checkoutDialogVisible = ref(false)
const checkoutForm = ref({
  role: 'STATION_STAFF' as 'SUPERVISOR' | 'STATION_STAFF',
  borrower: '',
  stationId: null as number | null,
  keyIds: [] as number[]
})

// 值班长可一次领空全线（所有在库钥匙）；站务只能领本站名下那把在库钥匙
const selectableKeys = computed(() =>
  keys.value.filter(k => {
    if (k.status !== 1) return false
    if (checkoutForm.value.role === 'STATION_STAFF') {
      return checkoutForm.value.stationId != null && k.station?.id === checkoutForm.value.stationId
    }
    return true
  })
)

const openCheckoutDialog = (row?: FirstAidKey) => {
  checkoutForm.value = {
    role: 'STATION_STAFF',
    borrower: '',
    stationId: row?.station?.id ?? null,
    keyIds: row && row.status === 1 ? [row.id] : []
  }
  checkoutDialogVisible.value = true
}

const onCheckoutRoleChange = () => {
  checkoutForm.value.keyIds = []
  if (checkoutForm.value.role === 'SUPERVISOR') {
    checkoutForm.value.stationId = null
  }
}

const takeWholeLine = () => {
  checkoutForm.value.keyIds = selectableKeys.value.map(k => k.id)
}

const submitCheckout = async () => {
  const form = checkoutForm.value
  if (!form.borrower.trim()) {
    ElMessage.warning('请填写领用人')
    return
  }
  if (form.role === 'STATION_STAFF' && !form.stationId) {
    ElMessage.warning('站务领用必须选择本人所在站')
    return
  }
  if (form.keyIds.length === 0) {
    ElMessage.warning('请点名至少一把具体钥匙')
    return
  }
  try {
    await keyApi.checkout({
      keyIds: form.keyIds,
      borrower: form.borrower.trim(),
      role: form.role,
      stationId: form.role === 'STATION_STAFF' ? form.stationId! : undefined
    })
    ElMessage.success('领用登记已落单')
    checkoutDialogVisible.value = false
    await refreshAll()
  } catch (error: any) {
    // 未交还重复领用/越站领用/并发撞单等：后端整体回滚，没有半截登记单
    ElMessage.error(error.response?.data?.message || '领用失败，登记未落单')
    await refreshAll()
  }
}

// —— 交还 ——
const returnKey = async (row: FirstAidKey) => {
  const checkout = activeCheckoutByKey(row.id)
  if (!checkout) return
  try {
    await ElMessageBox.confirm(
      `确认收回钥匙「${row.keyCode}」？上一份是「${checkout.borrower}」于 ${checkout.checkedOutAt} 领走的。`,
      '交还钥匙',
      { type: 'warning', confirmButtonText: '确认交还' }
    )
  } catch {
    return
  }
  try {
    await keyApi.returnKey(checkout.id)
    ElMessage.success('钥匙已交还，状态翻回在库')
    await refreshAll()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '交还失败')
    await refreshAll()
  }
}

// —— 领用历史 ——
const historyDrawerVisible = ref(false)
const historyKey = ref<FirstAidKey | null>(null)
const historyRecords = ref<KeyCheckout[]>([])

const openHistory = async (row: FirstAidKey) => {
  historyKey.value = row
  historyDrawerVisible.value = true
  try {
    historyRecords.value = (await keyApi.getByKey(row.id)).data
  } catch {
    ElMessage.error('加载领用历史失败')
  }
}

// —— 注销 ——
const retireKey = async (row: FirstAidKey) => {
  try {
    await ElMessageBox.confirm(`确认注销钥匙「${row.keyCode}」？`, '注销钥匙', { type: 'warning' })
  } catch {
    return
  }
  try {
    await keyApi.retire(row.id)
    ElMessage.success('钥匙已注销')
    await refreshAll()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '注销失败')
    await refreshAll()
  }
}

const keyStatusTag = (row: FirstAidKey) => {
  if (row.status === 0) return { type: 'info' as const, text: '已注销' }
  if (row.status === 2) {
    const holder = activeCheckoutByKey(row.id)
    return { type: 'danger' as const, text: holder ? `在外：${holder.borrower}` : '在外' }
  }
  return { type: 'success' as const, text: '在库' }
}

onMounted(async () => {
  await Promise.all([loadLines(), loadStations()])
  await refreshAll()
})
</script>

<template>
  <div class="manage-container">
    <div class="header-bar">
      <h2>急救箱钥匙领用</h2>
      <el-button type="primary" @click="openRegisterDialog">钥匙建档</el-button>
    </div>

    <el-alert
      type="info"
      :closable="false"
      title="每领走一把钥匙必须落领用登记（钥匙编号、所属站、领用人、交出时刻）。未交还前同一把开不出第二份登记；值班长可一次领空全线，站务只能领本站名下那把。登记单与在库状态同一事务落库，只改状态不落单不算数。"
      style="margin-bottom: 16px"
    />

    <div class="filter-bar">
      <el-select v-model="filterLineId" placeholder="按线路筛选" clearable style="width: 200px; margin-right: 12px">
        <el-option v-for="line in lines" :key="line.id" :label="line.lineName" :value="line.id" />
      </el-select>
      <el-select v-model="filterStationId" placeholder="按站点筛选" clearable style="width: 220px; margin-right: 12px">
        <el-option v-for="station in stations" :key="station.id" :label="station.stationName" :value="station.id" />
      </el-select>
      <el-button type="primary" @click="openCheckoutDialog()">领用登记</el-button>
    </div>

    <el-table :data="displayedKeys" v-loading="loading" border stripe row-key="id">
      <el-table-column prop="keyCode" label="钥匙编号" width="140" />
      <el-table-column label="所属线路" width="130">
        <template #default="{ row }">{{ row.railwayLine?.lineName || '-' }}</template>
      </el-table-column>
      <el-table-column label="所属站点" width="150">
        <template #default="{ row }">{{ row.station?.stationName || '-' }}</template>
      </el-table-column>
      <el-table-column label="钥匙状态" width="140">
        <template #default="{ row }">
          <el-tag :type="keyStatusTag(row).type">{{ keyStatusTag(row).text }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="交出时刻" width="180">
        <template #default="{ row }">{{ activeCheckoutByKey(row.id)?.checkedOutAt || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 1"
            size="small"
            type="primary"
            @click="openCheckoutDialog(row)"
          >领用</el-button>
          <el-button
            v-if="row.status === 2"
            size="small"
            type="warning"
            @click="returnKey(row)"
          >交还</el-button>
          <el-button size="small" @click="openHistory(row)">领用历史</el-button>
          <el-button
            v-if="row.status === 1"
            size="small"
            type="danger"
            @click="retireKey(row)"
          >注销</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog title="钥匙建档" v-model="registerDialogVisible" width="420px">
      <el-form :model="registerForm" label-width="90px">
        <el-form-item label="钥匙编号" required>
          <el-input v-model="registerForm.keyCode" placeholder="如：KEY-001" />
        </el-form-item>
        <el-form-item label="所属站点" required>
          <el-select v-model="registerForm.stationId" placeholder="选择所属站" style="width: 100%">
            <el-option v-for="station in stations" :key="station.id" :label="station.stationName" :value="station.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="registerDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitRegister">建档</el-button>
      </template>
    </el-dialog>

    <el-dialog title="领用登记" v-model="checkoutDialogVisible" width="520px">
      <el-form :model="checkoutForm" label-width="90px">
        <el-form-item label="领用人" required>
          <el-input v-model="checkoutForm.borrower" placeholder="谁把钥匙领走的" />
        </el-form-item>
        <el-form-item label="角色" required>
          <el-radio-group v-model="checkoutForm.role" @change="onCheckoutRoleChange">
            <el-radio value="STATION_STAFF">站务（只领本站）</el-radio>
            <el-radio value="SUPERVISOR">值班长（可领全线）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="checkoutForm.role === 'STATION_STAFF'" label="所在站" required>
          <el-select v-model="checkoutForm.stationId" placeholder="本人所在站" style="width: 100%"
                     @change="checkoutForm.keyIds = []">
            <el-option v-for="station in stations" :key="station.id" :label="station.stationName" :value="station.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="点名钥匙" required>
          <el-select v-model="checkoutForm.keyIds" multiple placeholder="只列在库钥匙" style="width: 100%">
            <el-option
              v-for="key in selectableKeys"
              :key="key.id"
              :label="`${key.keyCode}（${key.station?.stationName}）`"
              :value="key.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="checkoutForm.role === 'SUPERVISOR'">
          <el-button size="small" @click="takeWholeLine">一次领空全线（{{ selectableKeys.length }} 把在库）</el-button>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="checkoutDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCheckout">落登记单</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="historyDrawerVisible" :title="`领用历史：${historyKey?.keyCode || ''}`" size="480px">
      <el-table :data="historyRecords" border stripe>
        <el-table-column prop="borrower" label="领用人" width="90" />
        <el-table-column prop="stationName" label="所属站" width="110" />
        <el-table-column prop="checkedOutAt" label="交出时刻" width="160" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'danger' : 'success'" size="small">
              {{ row.status === 1 ? '未交还' : '已交还' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>
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

.filter-bar {
  margin-bottom: 16px;
}
</style>
