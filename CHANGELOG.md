# Changelog

## 1.5.1

### ✨ Improvements

- `rty` 的 `maxCount` 传入 `Int.MAX_VALUE`（含默认值）时表示无限重试
- `RtyScope.rtyCount` 达到 `Int.MAX_VALUE` 后保持不变

## 1.5.0

### ⚠️ Breaking Changes

- `rty` 的 `maxCount` 默认值由 `3` 改为 `Int.MAX_VALUE`
- `rty` 的 `getDelay` 默认延迟由固定 `5` 秒改为阶梯延迟（依次 `3` 秒、`5` 秒，之后固定 `10` 秒）

### Migration

- 如需保持旧版默认行为，显式传入 `rty(maxCount = 3, getDelay = rtyDelay(5_000))`

### ✨ Improvements

- 新增 `rtyDelayDefault()` 与 `rtyDelay(vararg delays: Long)` 用于构建 `getDelay` 延迟策略
- 完善公开 API 的 KDoc 注释，补充参数说明、`maxCount` 约束与 `rtySkip()` 行为说明
- 校验 `rtySkip()` 调用时机，禁止在当轮执行块外调用
- 修正 Maven POM 中的开源协议为 MIT，与仓库 `LICENSE` 保持一致

### 🐛 Bug Fixes

- 嵌套调用 `rty` 时，内层调用外层的 `rtySkip()` 会被内层拦截并变成内层重试
- `block` 内 `catch (Exception)` 会误捕获 `rtySkip()`，导致跳过失效
- `block` 抛出 JVM `Error` 时会被捕获并重试，现改为直接向外抛出
- 协程已取消或非正数延迟期间被取消时仍会执行一次 `block`

## 1.1.1

### ⚠️ Breaking Changes

- `RtyDoingScope.skip()` 重命名为 `RtyDoingScope.rtySkip()`

### Migration

- `block` 内调用的 `skip()` 改为 `rtySkip()`

## 1.1.0

### ✨ Improvements

- 新增 `RtyDoingScope` 作为 `block` 的接收者，支持调用 `skip()` 跳过本次执行，不触发 `onFailure` 且不计入 `rtyCount`

## 1.0.0

- 提供协程重试函数 `rty` 与 `RtyScope`，支持配置最大执行次数 `maxCount`、延迟时间 `getDelay` 与失败回调 `onFailure`，返回 `Result<T>`
