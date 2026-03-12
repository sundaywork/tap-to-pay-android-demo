# Stripe Tap to Pay 迁移要点 → TickTapPro

将本示例的 Stripe Terminal（Tap to Pay + S710）集成到 TickTapPro 主项目的操作要点。

---

## 一、依赖配置 (app/build.gradle.kts)

```kotlin
// 在 dependencies 块中添加
implementation("com.stripe:stripeterminal:5.3.0")
implementation("com.stripe:stripeterminal-taptopay:5.3.0")

// 在 android 块之前添加 configurations（解决 recyclerview 版本冲突）
configurations.all {
    resolutionStrategy {
        force("androidx.recyclerview:recyclerview:1.3.2")
    }
}
```

若使用 Groovy 的 `build.gradle`，语法对应调整。

---

## 二、minSdk 升级

```kotlin
// app/build.gradle.kts
defaultConfig {
    minSdk = 26  // 从 24 升级，Stripe 要求
    // ...
}
```

---

## 三、buildConfig 与 gradle.properties

**gradle.properties** 添加：

```properties
EXAMPLE_BACKEND_URL=http://coredev3.aiic.nz:4567/
```

**app/build.gradle.kts** 的 defaultConfig 中添加：

```kotlin
buildConfigField(
    "String",
    "EXAMPLE_BACKEND_URL",
    "\"${project.findProperty("EXAMPLE_BACKEND_URL")?.toString()?.trim()?.trim('\"') ?: ""}\""
)
```

若项目已有 `findProperty` 用法，可参考现有写法。

---

## 四、权限 (AndroidManifest.xml)

**仅添加以下权限**（Stripe 需要）：

```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
```

**关于蓝牙权限**：本示例当前配置为 **Tap to Pay + S710**，**不需要蓝牙**。  
- Tap to Pay：使用手机 NFC  
- S710：使用 Internet  

若后续接入 **Bluetooth 读卡器**（如 Stripe Reader M2），再在 manifest 中声明蓝牙相关权限。

---

## 五、网络配置

若后端使用 HTTP，需允许明文流量。TickTapPro 已有 `android:usesCleartextTraffic="true"`，一般无需额外配置。

若使用 `network_security_config`，可参考：

```xml
<!-- res/xml/network_security_config.xml -->
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">coredev3.aiic.nz</domain>
    </domain-config>
</network-security-config>
```

---

## 六、需要迁移的代码文件

| 文件 | 说明 |
|------|------|
| `ApiClient.kt` | 后端 API：connection token、创建 PaymentIntent、capture |
| `TokenProvider.kt` | 实现 `ConnectionTokenProvider`，从后端获取 token |
| `TerminalEventListener.kt` | 实现 `TerminalEventListener` | 
| `NavigationListener.kt` | 接口定义 |
| `MainActivity.kt` | Terminal 初始化、连接、收款逻辑 |
| `ConnectReaderFragment.kt` | 连接读卡器 UI |
| `PaymentDetails.kt` | 金额输入与收款 UI |
| `fragment_connect_reader.xml` | 布局 |
| `fragment_payment_details.xml` | 布局 |

包名需改为 `nz.aiic.ticktappro` 或新建子包如 `nz.aiic.ticktappro.stripe`。

---

## 七、后端 API 约定

后端需提供：

1. **Connection Token**：`POST /connection_token` → 返回 `{ "secret": "pst_xxx" }`
2. **创建 PaymentIntent**：`POST /create_payment_intent`，参数：amount, currency, extended_auth, incremental_auth
3. **Capture**：`POST /capture_payment_intent`，参数：payment_intent_id
4. **List Locations**：由 Stripe SDK 的 `listLocations` 通过 connection token 调用

---

## 八、集成入口

- 在 WebView 中通过 JS Bridge 调用原生 Stripe 支付入口，或  
- 在 MainActivity 增加入口跳转到 Stripe 支付 Activity/Fragment  

建议将 Stripe 相关逻辑封装在独立 Activity 或 Fragment 中，便于维护。

---

## 九、Tap to Pay 特殊要求（Release 测试）

- 使用 **release** 构建安装  
- 关闭 **开发者选项**  
- 测试环境需使用 Stripe 物理测试卡  

---

## 十、可选：移除无用蓝牙逻辑

若确认只使用 Tap to Pay + S710，可在迁移后删除：

- Manifest 中的 `BLUETOOTH`、`BLUETOOTH_ADMIN`、`BLUETOOTH_CONNECT`、`BLUETOOTH_SCAN`
- MainActivity 中与 `BluetoothAdapter` 相关的逻辑

---

## 参考

- 示例项目路径：`d:\development\stripe\tap-to-pay-android-demo`
- Stripe Terminal Android 文档：https://docs.stripe.com/terminal/payments/setup-reader/tap-to-pay?platform=android
