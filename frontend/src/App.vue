<script setup lang="ts">
import { ref } from 'vue'
import RailwayLineManage from './components/RailwayLineManage.vue'
import StationManage from './components/StationManage.vue'
import RestBenchManage from './components/RestBenchManage.vue'
import CleaningManage from './components/CleaningManage.vue'
import FirstAidKeyManage from './components/FirstAidKeyManage.vue'
import FirstAidKitManage from './components/FirstAidKitManage.vue'
import ChangeRecords from './components/ChangeRecords.vue'

const activeTab = ref('bench')
</script>

<template>
  <div class="app-container">
    <header class="app-header">
      <h1>轨道交通站点候车休息台管理系统</h1>
      <p>线路 + 站点双重关联绑定</p>
    </header>
    
    <nav class="app-nav">
      <el-tabs v-model="activeTab" type="card">
        <el-tab-pane label="休息台管理" name="bench">
          <RestBenchManage />
        </el-tab-pane>
        <el-tab-pane label="清扫占台" name="cleaning">
          <CleaningManage />
        </el-tab-pane>
        <el-tab-pane label="急救箱钥匙" name="key">
          <FirstAidKeyManage />
        </el-tab-pane>
        <el-tab-pane label="急救箱药品" name="kit">
          <FirstAidKitManage />
        </el-tab-pane>
        <el-tab-pane label="线路管理" name="line">
          <RailwayLineManage />
        </el-tab-pane>
        <el-tab-pane label="站点管理" name="station">
          <StationManage />
        </el-tab-pane>
        <el-tab-pane label="变更台账" name="record">
          <ChangeRecords />
        </el-tab-pane>
      </el-tabs>
    </nav>
  </div>
</template>

<style scoped>
.app-container {
  min-height: 100vh;
  background-color: #f5f7fa;
}

.app-header {
  background: linear-gradient(135deg, #1e88e5 0%, #1565c0 100%);
  color: white;
  padding: 30px 40px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.app-header h1 {
  margin: 0 0 8px 0;
  font-size: 28px;
  font-weight: 600;
}

.app-header p {
  margin: 0;
  opacity: 0.9;
  font-size: 14px;
}

.app-nav {
  padding: 20px;
}

.el-tabs {
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}
</style>
