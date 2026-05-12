<!-- 使用 script setup：单文件组件脚本，无需显式 export default -->
<script setup>
// 从 vue 引入 ref，用于响应式列表数据
import { ref } from "vue";

// JSDoc：单行号码结构为 reds 数组 + blue 数字
/** @typedef {{ reds: number[]; blue: number }} Row */

// 多组号码，初始为空数组；类型标注为 Row[]
const rows = ref(/** @type {Row[]} */ ([]));

// 数字格式化为两位字符串
function pad2(n) {
  // 小于 10 前补零
  return n < 10 ? `0${n}` : String(n);
}

// 随机 6 个不重复红球（1～33），返回升序数组
function pickReds() {
  // 构建 1～33 号码池
  const pool = Array.from({ length: 33 }, (_, i) => i + 1);
  // Fisher–Yates 原地洗牌
  for (let i = pool.length - 1; i > 0; i--) {
    // 随机交换位置 j
    const j = Math.floor(Math.random() * (i + 1));
    // 解构交换
    [pool[i], pool[j]] = [pool[j], pool[i]];
  }
  // 取前 6 个并排序
  return pool.slice(0, 6).sort((a, b) => a - b);
}

// 随机蓝球 1～16
function pickBlue() {
  // 均匀随机整数 1～16
  return Math.floor(Math.random() * 16) + 1;
}

// 生成 5 组号码写入 rows
function generate() {
  // 新结果数组
  const next = [];
  // 生成 5 组
  for (let i = 0; i < 5; i++) {
    // 每组一组红球 + 蓝球
    next.push({ reds: pickReds(), blue: pickBlue() });
  }
  // 赋值触发更新
  rows.value = next;
}
</script>

<!-- 模板：页面结构 -->
<template>
  <!-- 主内容容器 -->
  <div class="page">
    <!-- 页头：标题、说明、按钮 -->
    <header class="header">
      <!-- 标题 -->
      <h1 class="title">双色球号码生成</h1>
      <!-- 规则提示 -->
      <p class="hint">红球 6 个（01–33 不重复），蓝球 1 个（01–16）</p>
      <!-- 触发 generate -->
      <button type="button" class="btn" @click="generate">一键生成 5 组</button>
    </header>

    <!-- 有号码时才显示列表 -->
    <ul class="list" v-if="rows.length">
      <!-- 遍历每一组 -->
      <li v-for="(row, idx) in rows" :key="idx" class="row">
        <!-- 组序号 -->
        <span class="idx">{{ idx + 1 }}</span>
        <!-- 球号区域 -->
        <span class="balls">
          <!-- 红球循环 -->
          <span v-for="r in row.reds" :key="r" class="ball red">{{ pad2(r) }}</span>
          <!-- 加号 -->
          <span class="sep">+</span>
          <!-- 蓝球 -->
          <span class="ball blue">{{ pad2(row.blue) }}</span>
        </span>
      </li>
    </ul>
  </div>
</template>

<!-- 组件局部样式 -->
<style>
/* 全局根样式：字体与背景 */
:root {
  /* 字体 */
  font-family: "Segoe UI", system-ui, -apple-system, sans-serif;
  /* 主色 */
  color: #1a1a1a;
  /* 渐变背景 */
  background: linear-gradient(160deg, #fff8f0 0%, #ffe8dc 45%, #f0f4ff 100%);
  /* 最小高度 */
  min-height: 100%;
}

/* body 重置与高度 */
body {
  /* 去 margin */
  margin: 0;
  /* 全屏高 */
  min-height: 100vh;
}

/* #app 居中布局 */
#app {
  /* 最小高度 */
  min-height: 100vh;
  /* flex */
  display: flex;
  /* 垂直居中 */
  align-items: center;
  /* 水平居中 */
  justify-content: center;
  /* 内边距 */
  padding: 24px 16px;
}

/* 内容最大宽 */
.page {
  /* 宽 100% */
  width: 100%;
  /* 最大宽 */
  max-width: 520px;
}

/* 页头 */
.header {
  /* 居中 */
  text-align: center;
  /* 下边距 */
  margin-bottom: 28px;
}

/* 标题 */
.title {
  /* margin */
  margin: 0 0 8px;
  /* 字号 */
  font-size: 1.5rem;
  /* 粗体 */
  font-weight: 700;
  /* 字距 */
  letter-spacing: 0.02em;
}

/* 提示文字 */
.hint {
  /* margin */
  margin: 0 0 20px;
  /* 小字 */
  font-size: 0.875rem;
  /* 灰色 */
  color: #5c5c5c;
}

/* 按钮 */
.btn {
  /* 手型 */
  cursor: pointer;
  /* 无边框 */
  border: none;
  /* 圆角 */
  border-radius: 999px;
  /* 内边距 */
  padding: 12px 28px;
  /* 字号 */
  font-size: 1rem;
  /* 粗体 */
  font-weight: 600;
  /* 白字 */
  color: #fff;
  /* 红渐变 */
  background: linear-gradient(135deg, #e63946 0%, #c1121f 100%);
  /* 阴影 */
  box-shadow: 0 8px 24px rgba(193, 18, 31, 0.35);
  /* 过渡 */
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

/* 按钮悬停 */
.btn:hover {
  /* 上移 */
  transform: translateY(-1px);
  /* 阴影加深 */
  box-shadow: 0 10px 28px rgba(193, 18, 31, 0.42);
}

/* 按钮 active */
.btn:active {
  /* 复位位移 */
  transform: translateY(0);
}

/* 列表 */
.list {
  /* 无列表符号 */
  list-style: none;
  /* 去 margin */
  margin: 0;
  /* 去 padding */
  padding: 0;
  /* flex 列 */
  display: flex;
  /* 纵向 */
  flex-direction: column;
  /* 行距 */
  gap: 14px;
}

/* 单行卡片 */
.row {
  /* flex */
  display: flex;
  /* 垂直居中 */
  align-items: center;
  /* gap */
  gap: 12px;
  /* 内边距 */
  padding: 14px 16px;
  /* 背景 */
  background: rgba(255, 255, 255, 0.85);
  /* 圆角 */
  border-radius: 14px;
  /* 阴影 */
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.06);
  /* 毛玻璃 */
  backdrop-filter: blur(8px);
}

/* 序号块 */
.idx {
  /* 不收缩 */
  flex-shrink: 0;
  /* 宽 */
  width: 28px;
  /* 高 */
  height: 28px;
  /* flex 居中 */
  display: flex;
  /* 纵中 */
  align-items: center;
  /* 横中 */
  justify-content: center;
  /* 字号 */
  font-size: 0.8rem;
  /* 粗 */
  font-weight: 700;
  /* 灰字 */
  color: #888;
  /* 灰底 */
  background: #f3f3f3;
  /* 圆角 */
  border-radius: 8px;
}

/* 球容器 */
.balls {
  /* flex */
  display: flex;
  /* 换行 */
  flex-wrap: wrap;
  /* 对齐 */
  align-items: center;
  /* 间距 */
  gap: 8px;
}

/* 球基础 */
.ball {
  /* 行内 flex */
  display: inline-flex;
  /* 纵中 */
  align-items: center;
  /* 横中 */
  justify-content: center;
  /* 宽 */
  width: 36px;
  /* 高 */
  height: 36px;
  /* 圆 */
  border-radius: 50%;
  /* 字号 */
  font-size: 0.85rem;
  /* 粗 */
  font-weight: 700;
  /* 白字 */
  color: #fff;
}

/* 红球 */
.ball.red {
  /* 渐变 */
  background: radial-gradient(circle at 30% 30%, #ff6b6b, #c1121f);
  /* 内阴影 */
  box-shadow: inset 0 -2px 6px rgba(0, 0, 0, 0.2);
}

/* 蓝球 */
.ball.blue {
  /* 渐变 */
  background: radial-gradient(circle at 30% 30%, #4cc9f0, #0077b6);
  /* 内阴影 */
  box-shadow: inset 0 -2px 6px rgba(0, 0, 0, 0.2);
}

/* 分隔加号 */
.sep {
  /* 粗 */
  font-weight: 700;
  /* 灰 */
  color: #999;
  /* 左右 padding */
  padding: 0 2px;
}
</style>
