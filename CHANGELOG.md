# Changelog

## Unreleased

### ✨ Improvements

- 完善公开 API 的 KDoc 注释，补充参数说明、`maxCount` 约束与 `rtySkip()` 行为说明
- 修正 Maven POM 中的开源协议为 MIT，与仓库 `LICENSE` 保持一致

### 🐛 Bug Fixes

- 嵌套调用 `rty` 时，内层调用外层的 `rtySkip()` 会被内层拦截并变成内层重试
- `block` 内 `catch (Exception)` 会误捕获 `rtySkip()`，导致跳过失效

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
