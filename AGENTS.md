# AGENTS.md

## 项目概览

- `rty` 是基于 Kotlin 协程的 Android 重试库，Maven 坐标为 `io.github.zj565061763.android:rty`。
- 仓库包含核心库 `:lib`（`com.sd.lib.rty`）与示例应用 `:app`（`com.sd.demo.rty`）两个模块。
- 编译环境为 `compileSdk = 35`、`minSdk = 21`、JVM Target `1.8`、Kotlin `1.9.25`。

| 模块 / 配置 | 关键路径 | 职责 |
|---|---|---|
| `:lib` | `lib/src/main/java/com/sd/lib/rty/Rty.kt` | 核心重试函数 `rty`、作用域接口与内部实现 |
| `:lib` 单元测试 | `lib/src/test/java/com/sd/lib/rty/RtyTest.kt` | `:lib` 核心行为的协程单元测试 |
| `:lib` 发布配置 | `lib/build.gradle.kts`、`lib/gradle.properties` | 单变体 `release` 发布配置与 Maven Central 元数据 |
| `:app` | `app/src/main/java/com/sd/demo/rty/` | Jetpack Compose 示例界面（`MainActivity`、`SampleActivity`） |
| 版本目录 | `gradle/libs.versions.toml` | 统一管理插件与依赖版本 |

## 常用命令

| 命令 | 用途 |
|---|---|
| `./gradlew :lib:testDebugUnitTest` | 运行 `:lib` 单元测试 |
| `./gradlew :lib:testDebugUnitTest --tests "com.sd.lib.rty.RtyTest"` | 仅运行 `RtyTest` |
| `./gradlew :lib:assembleRelease` | 构建 `:lib` 的 Release AAR |
| `./gradlew :app:assembleDebug` | 构建示例应用 Debug APK |
| `./gradlew check` | 执行全量检查与单元测试 |

## 核心实现与约束

- `:lib` 对 `kotlinx-coroutines-android` 使用 `compileOnly` 依赖以避免传递协程版本，修改 `:lib` 时不得改为 `implementation` 或 `api`，也不引入额外运行时依赖。
- 对外仅暴露 `rty`、`RtyScope`、`RtyDoingScope`，内部类 `RtyScopeImpl` 与 `SkipRtyException` 保持 `private`。
- 修改公开 API 时保持现有函数签名与默认参数的源码及二进制兼容性，内部调用 `delay` 时通过 `getDelay().milliseconds` 转为 `Duration`。
- `maxCount` 通过 `require(maxCount > 0)` 校验，表示含首次执行在内的最大总执行次数，而非额外重试次数。
- `rtyCount` 从 `1` 开始计数，仅在普通异常需要重试且完成延迟后递增。
- `RtyDoingScope.rtySkip()` 抛出绑定当前作用域 `owner`、直接继承 `Throwable` 且重写 `fillInStackTrace()` 的 `SkipRtyException`，避免采集栈帧与被业务 `catch (Exception)` 误捕。
- 捕获 `SkipRtyException` 时先校验 `exception.owner === this`，非当前作用域直接重抛给外层 `rty`；当前作用域不触发 `onFailure`、不递增 `rtyCount`，仅延迟后继续下一轮。
- `block` 抛出 `CancellationException` 时直接重新抛出，且在 `block` 与 `onFailure` 返回后均调用 `currentCoroutineContext().ensureActive()` 以响应协程取消。
- 普通异常先回调 `onFailure(exception)` 再检查 `rtyCount >= maxCount`，因此达到最大次数时的最后一次失败也会触发 `onFailure`。
- `onFailure` 返回 `false` 或 `rtyCount >= maxCount` 时立即返回 `Result.failure`，不再执行延迟。

## 测试与代码规范

- Kotlin 源码与 Gradle 脚本统一使用 2 空格缩进。
- 修改 `:lib` 逻辑后在 `RtyTest` 中补充或更新对应测试，使用 `kotlinx.coroutines.test.runTest` 的虚拟时间验证延迟与重试行为。

| 测试类 | 用例 | 覆盖范围 |
|---|---|---|
| `RtyTest` | `test success` | 首次执行成功时返回 `Result.success` |
| `RtyTest` | `test error` | 连续失败达到 `maxCount` 时返回最后一次异常 |
| `RtyTest` | `test cancel` | `CancellationException` 正常向外传播并取消协程 |
| `RtyTest` | `test count` | `rtyCount` 从 `1` 依次递增至 `maxCount` |
| `RtyTest` | `test skip` | `rtySkip()` 跳过当次执行，不触发 `onFailure` 且不消耗 `rtyCount` |
| `RtyTest` | `test skip with catch Exception` | `block` 内 `catch (Exception)` 不会误捕 `rtySkip()` |
| `RtyTest` | `test nested skip` | 嵌套 `rty` 中调用外层 `rtySkip()` 时正常透传到外层作用域 |
| `RtyTest` | `test delay` | 重试延迟按 `getDelay()` 推进且达到上限后不再额外延迟 |
| `RtyTest` | `test invalid maxCount` | `maxCount <= 0` 时抛出 `IllegalArgumentException` |
| `RtyTest` | `test onFailure` | 每次失败（含达到 `maxCount` 的最后一次）均触发 `onFailure` |
| `RtyTest` | `test onFailure false` | `onFailure` 返回 `false` 时提前终止重试且不再延迟 |

## 发布配置

- 发布元数据定义在 `lib/gradle.properties`（`GROUP`、`POM_ARTIFACT_ID`、`VERSION_NAME`），通过 `com.vanniktech.maven.publish` 发布单变体 `release` 及源码、Javadoc JAR。
