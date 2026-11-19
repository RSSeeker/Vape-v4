# Vape 脚本 API（Rhino / ES6）

> 适用版本：4.21.55 及以后（脚本系统内嵌 Mozilla Rhino 1.7.15，解释执行）。
> 脚本是**解释执行**的，不需要编译；改完用「重新加载脚本」模块热重载即可。

---

## 1. 目录与文件

```
.vapeclient/
  scripts/
    example.json          ← 模块描述（必需）
    example.js            ← 脚本入口（默认与 json 同名；可用 main 指定）
    mylib.js              ← 其他脚本文件，用 load("mylib.js") 引入
    data/
      example.json        ← storage 自动生成的每个脚本的持久化数据
  extension.log           ← 脚本加载/运行日志
```

`.vapeclient` 位于游戏/客户端的工作目录（`LocalConfigStore.directory()`）。
首次启动会自动生成 `example.json` + `example.js` 示例，之后的示例模板升级会自动重写占位示例。

---

## 2. JSON 描述文件

```json
{
  "name": { "en": "My HUD", "zh": "我的 HUD" },
  "id": "my-hud",
  "description": { "en": "Shows info on screen.", "zh": "在屏幕上显示信息。" },
  "main": "my-hud.js",
  "defaultHidden": false,
  "defaultEnabled": false,
  "versions": ["1.20.1", ">=1.21"]
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `name` | string 或 `{en, zh}` | 模块显示名；也支持 `nameZh` 兄弟字段。**内部名固定取英文**，中文只做渲染期翻译 |
| `id` | string | 脚本 id，用于 `storage` 数据文件名；缺省用 json 文件名 |
| `description` | string 或 `{en, zh}` | 模块说明（也支持 `descriptionZh`） |
| `main` | string | 入口 js；缺省 = `<json 名>.js`；自动补 `.js` 后缀 |
| `defaultHidden` | bool | 默认是否隐藏（隐藏模块不会出现在分类列表里） |
| `defaultEnabled` | bool | 进游戏后是否默认开启 |
| `versions` | string[] | 版本白名单，见下 |

`versions` 支持：`"1.20.1"`、`">=1.20.1"`、`">1.20"`、`"<=1.21"`、`"<1.21"`、`"!=1.21"`、`"=1.20.1"`、`"*"`（不限版本），也接受协议号（如 `"47"`）。空数组或 `*` 表示不限；无法识别的条目会让整个脚本被跳过。

> ⚠️ 模块的内部名（英文 `name`）是配置 key 的一部分。**改英文名会丢已保存的配置**；只改中文译名不影响。

---

## 3. 生命周期

脚本**顶层代码在加载期执行**（此时应声明设置项），之后按事件回调：

| 回调 | 时机 | 说明 |
| --- | --- | --- |
| `onEnable()` | 模块开启 | 进世界前 `player` 为 `null` |
| `onDisable()` | 模块关闭 | 适合保存 `storage` |
| `onTick()` | 每客户端 tick（20/s） | 主逻辑放这里 |
| `onRender2D(event)` | 每帧 2D 阶段 | `event.width/height`；只有这里能调用 `draw.*` |
| `onKeyPress(event)` | 按键 | `event.key`（GLFW 键码）、`event.down` |
| `onChat(event)` | 收到聊天 | `event.message`（纯文本） |
| `onAttack(event)` | 攻击实体前 | `event.target`（实体快照或 `null`） |
| `onMove(event)` | 移动计算 | `event.x/y/z`，当前只读 |
| `onWorldChange()` | 切换世界 | 无参数 |
| `onPacketSend(event)` / `onPacketReceive(event)` | 收发包 | `event.name`、`event.className`、`event.cancel()` |

回调里的异常会被捕获、写日志并**自动停用该模块**，不会崩客户端。

---

## 4. 全局 API

### 4.1 输出

```js
log("text");                       // 客户端调试日志
chat("text");                      // 发到本地聊天
notify("标题", "内容");            // 屏幕右上角通知
```

### 4.2 玩家

```js
player.name        // 玩家名
player.x, y, z     // 坐标
player.yaw, pitch  // 朝向
player.health      // 血量
player.food        // 饥饿值
player.onGround    // 是否着地
```

`player` 每次回调前刷新一次快照；不在世界里时为 `null`，所以顶层代码里请判空。

### 4.3 模块

```js
var m = module("Fly");     // 按名字取句柄，不存在返回 null
m.name; m.category; m.enabled; m.visible;
m.setEnabled(true);
m.toggle();
var s = m.setting("Speed");// 单个设置项句柄
s.name; s.kind; s.value; s.get(); s.set(2.5);
m.settings();              // 全部设置项句柄数组
modules();                 // 所有可见模块句柄数组
```

`kind` 取值：`toggle` / `number` / `text` / `mode` / `color` / `keybind` / `value`。
`set()` 对 `number` 会自动夹到范围内；`color` 用 ARGB int 或 `"#RRGGBB"` 字符串。

### 4.4 实体

```js
entities();   // 世界内全部实体快照
players();    // 只含玩家
isFriend("Notch");   // 好友判断（字符串名）
```

实体快照字段：`name`、`type`、`x`、`y`、`z`、`distance`（到自己）、`isSelf`、`isPlayer`、`isFriend`、`health`（仅生物）。

### 4.5 绘制（仅在 onRender2D 内）

```js
onRender2D(function (event) {
    draw.text(x, y, "text", 0xFFFFFFFF);      // 带阴影文字，颜色 ARGB
    draw.rect(x, y, width, height, 0x40000000);
    draw.hline(x, y, length, 0xFFFFFFFF);
    draw.textWidth("text");                   // 文字宽度
    draw.fontHeight();                        // 行高
    draw.displayWidth(); draw.displayHeight();// 屏幕逻辑尺寸
});
```

在渲染回调之外调用 `draw.*` 会记录一条错误日志，不做任何绘制。

### 4.6 持久化

```js
storage.set("key", value);          // 支持 string / number / boolean / 对象 / 数组
storage.get("key", 默认值);
storage.has("key");
storage.remove("key");
storage.clear();
```

对象/数组会以 JSON 文本存进 `.vapeclient/scripts/data/<id>.json`。同一脚本的多个 js 文件共享同一份 storage。

### 4.7 定时器与多文件

```js
after(1000, function () { log("1 秒后执行"); });   // 毫秒；只执行一次
load("lib/helper.js");                             // 同一作用域执行另一个脚本文件
```

`load()` 只能加载 `.vapeclient/scripts/` 目录内的文件（会校验规范路径），重复加载同一文件只执行一次。

### 4.8 设置项声明（加载期调用）

```js
setting.toggle({en: "Enabled", zh: "启用"}, true);
setting.number({en: "Speed", zh: "速度"}, 1.0, 10.0, 20.0);   // (名称, 默认, 最小, 最大)
setting.text({en: "Label", zh: "文字"}, "hi");
setting.mode({en: "Mode", zh: "模式"}, "A", ["A", "B", "C"]);  // (名称, 默认, 选项)
setting.color({en: "Tint", zh: "颜色"}, 0xFFFFFFFF);            // ARGB
setting.keybind({en: "Hotkey", zh: "快捷键"}, 0);               // GLFW 键码，0 = 未绑定
```

设置项必须写在**顶层**（或 `prepareSettings` 阶段），回调里声明无效。
返回句柄：`handle.get()` / `handle.set(v)` / `handle.name` / `handle.value`。

---

## 5. 完整示例

```js
var showHud = setting.toggle({en: "Show HUD", zh: "显示 HUD"}, true);
var tint    = setting.color({en: "Tint", zh: "颜色"}, 0xFFFFFFFF);
var ticks   = 0;

onEnable(function () {
    ticks = storage.get("ticks", 0);
    notify("示例脚本", "已开启");
});

onTick(function () {
    ticks++;
    after(1000, function () { log("运行了 1 秒"); });
});

onRender2D(function (event) {
    if (!showHud.get()) return;
    draw.text(4, 4, "ticks=" + ticks, tint.get());
    draw.hline(4, 16, 60, tint.get());
});

onKeyPress(function (event) {
    if (event.down && event.key === 82) log("按了 R");
});

onDisable(function () {
    storage.set("ticks", ticks);
});
```

---

## 6. 重载与调试

- **内置「重新加载脚本」（Reload Scripts）模块**：重新扫描目录——已有脚本重新编译、新增 json 立即注册、删除的 json 对应模块被禁用（重启后从列表消失）。
- 脚本里删掉/改名设置项后，重载会自动摘掉旧设置项，不会留下僵尸项。
- 加载失败、运行期异常、声明期错误都会：写 `extension.log`、写客户端日志、弹一条 `Script error` 通知。
- 删了 json 的模块本次进程仍会在 GUI 里（无反向注销 API），但不会运行。

---

## 7. 限制与安全

- **没有沙箱**：脚本可以访问主机 JVM（`Packages.java.*`），能读写文件、执行命令。**只运行自己写的或可信来源的脚本。**
- 没有指令看门狗：`while(true){}` 会卡住客户端线程。
- 每 tick / 每帧的回调里不要做重活（大范围 `entities()` 遍历、字符串拼接、I/O），会掉帧。
- 发包/收包回调跑在网络线程，异常会被吞掉并停用模块。
- 已知限制：`onMove` 目前只读；不支持自定义设置项类型（如列表、条件显示）。

---

更新日期：2026-11-19
