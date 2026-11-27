# Vape 脚本 API（Rhino / ES6）

> 适用版本：4.21.55 及以后（脚本系统内嵌 Mozilla Rhino 1.7.15，解释执行）；4.21.60 起 `inv` 覆盖全部 41 格并新增 `world` / `attack` / `util` 与控制台。
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
`.vapeclient/scripts` 不会被启动时的遗留文件清理删掉（清理只针对 `cache` 之外的临时产物）。

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
player.entityId    // 实体 id（可与 entities() 的 id 比对）
player.fallDistance// 下落距离
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

实体快照字段：`id`、`name`、`type`、`x`、`y`、`z`、`distance`（到自己）、`isSelf`、`isPlayer`、`isFriend`、`health`（仅生物）。

`id` 可以直接传给 `attack()`（见 §4.6）。

### 4.5 背包与装备

`inv` 覆盖完整玩家物品栏（41 格）：0–8 热栏、9–35 主背包、36–39 装备（36 靴、37 腿、38 胸、39 头）、40 副手。

```js
inv.size();            // 41
inv.slot(3);           // 槽位快照 { slot, area, name, count, displayName }，空槽为 null
inv.hotbar(3);         // 热栏 0–8 的便捷写法，等价 inv.slot(3)
inv.armor();           // { boots, leggings, chestplate, helmet }，空槽为 null
inv.offhand();         // 40 号槽（副手）
inv.find("chestplate"); // 匹配到的所有槽位数组，如 [38, 12]
inv.count("arrow");    // 同 id 物品的总数量
inv.selected();        // 当前选中的热栏槽位（0–8）
inv.select(3);         // 只切换选中槽位，不发使用包
inv.use(3);            // 切到该槽并让服务端「使用」它——装备类物品即穿上
inv.wearingElytra();   // 胸甲槽是否穿着鞘翅（热栏里的鞘翅不算）
```

`name` 是语言无关的物品 id（如 `minecraft:elytra`、`minecraft:diamond_chestplate`），
跨语言、跨版本匹配都用它，不要用 `displayName`。`area` 是 `hotbar` / `main` /
`armor` / `offhand` 之一。`inv.use()` 只对热栏 0–8 生效（它模拟的是「手持并右键」），
`find` / `count` / `slot` 则覆盖全部 41 格。

> 装备槽 36–39 在个别版本上映射不稳，`inv.armor()` 会自动回退到实体护甲列表；
> 换甲时优先用 `inv.use()` 把物品从热栏穿上，而不是直接改装备槽。

### 4.6 世界、动作与工具

```js
world.time();          // 世界游戏刻（WorldInfo.getWorldTime）
world.blockAt(x, y, z); // { x, y, z, id }；未加载区块返回 null

attack(entity);        // entity 为 entities() 句柄或数字实体 id，走控制器 attackEntity
attack(entity.id);     // 实体句柄现在带 id 字段
swing();               // 挥手（不攻击）
useItem();             // 使用当前选中槽位（等价 inv.use(inv.selected())）

util.time();           // 现实时间毫秒
util.random();         // [0, 1) 随机数
util.dist(x1, y1, z1, x2, y2, z2);
util.clamp(value, min, max);
```

`attack()` 会拒绝攻击自己；命中前请自行用 `isFriend` / `isPlayer` 过滤。

### 4.7 绘制（仅在 onRender2D 内）

```js
function onRender2D(event) {
    draw.text(x, y, "text", 0xFFFFFFFF);      // 带阴影文字，颜色 ARGB
    draw.rect(x, y, width, height, 0x40000000);
    draw.hline(x, y, length, 0xFFFFFFFF);
    draw.textWidth("text");                   // 文字宽度
    draw.fontHeight();                        // 行高
    draw.displayWidth(); draw.displayHeight();// 屏幕逻辑尺寸
}
```

在渲染回调之外调用 `draw.*` 会记录一条错误日志，不做任何绘制。

### 4.8 持久化

```js
storage.set("key", value);          // 支持 string / number / boolean / 对象 / 数组
storage.get("key", 默认值);
storage.has("key");
storage.remove("key");
storage.clear();
```

对象/数组会以 JSON 文本存进 `.vapeclient/scripts/data/<id>.json`。同一脚本的多个 js 文件共享同一份 storage。

### 4.9 定时器与多文件

```js
after(1000, function () { log("1 秒后执行"); });   // 毫秒；只执行一次
load("lib/helper.js");                             // 同一作用域执行另一个脚本文件
```

`load()` 只能加载 `.vapeclient/scripts/` 目录内的文件（会校验规范路径），重复加载同一文件只执行一次。

### 4.10 设置项声明（加载期调用）

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

function onEnable() {
    ticks = storage.get("ticks", 0);
    notify("示例脚本", "已开启");
});

function onTick() {
    ticks++;
    after(1000, function () { log("运行了 1 秒"); });
});

function onRender2D(event) {
    if (!showHud.get()) return;
    draw.text(4, 4, "ticks=" + ticks, tint.get());
    draw.hline(4, 16, 60, tint.get());
});

function onKeyPress(event) {
    if (event.down && event.key === 82) log("按了 R");
});

function onDisable() {
    storage.set("ticks", ticks);
});
```

---

## 5.1 内置脚本控制台

ClickGUI 设置页左侧的 **扩展** 分类下面有一个独立的 **控制台 / Console** 入口（带图标），
点开后会弹出一个窗口：

- 上面是输出区（最近 12 行，按 `!` 开头的行是错误，`>` 开头的是输入回显）；
- 下面是输入框，输入一条 JS 表达式后回车执行，结果会追加到输出区；
- `↑` / `↓` 翻历史命令（最多保留 50 条）；
- `print(...)` / `console.log(...)` 都可以往输出区打印，`console.clear()` 清屏；
- 侧边栏的「控制台」同时就是开关：点一下打开（按钮变亮），再点一下收起；窗口本身没有额外的关闭按钮。

控制台和脚本模块用的是同一套全局 API，但作用域互相独立：在控制台里用 `var` 定义的变量
不会影响已加载的脚本，反过来也一样。适合临时查询状态（`player.name`、`inv.size()`）、
试 API、或者算一个数看看。

控制台的作用域额外开放了 Java 互操作：`Packages`、`java`、`javax` 都可用，可以直接
`new java.io.File(".").getAbsolutePath()` 或 `java.awt.Desktop.getDesktop()`。这意味着
控制台能执行任意 Java 代码，只在这个客户端窗口里手敲的输入才会被执行。

控制台运行在解释模式，并且有指令预算（约 2000 万条指令）；`while (true) {}` 这类死循环
会被中止并显示错误，不会卡死客户端。
## 6. 重载与调试

- **内置「重新加载脚本」（Reload Scripts）模块**：重新扫描目录——已有脚本重新编译、新增 json 立即注册、删除的 json 对应模块被禁用（重启后从列表消失）。
- 脚本里删掉/改名设置项后，重载会自动摘掉旧设置项，不会留下僵尸项。
- 加载失败、运行期异常、声明期错误都会：写 `extension.log`、写客户端日志、弹一条 `Script error` 通知。
- 删了 json 的模块本次进程仍会在 GUI 里（无反向注销 API），但不会运行。

---

## 7. 限制与安全

- **没有沙箱**：脚本可以访问主机 JVM（`Packages.java.*`），能读写文件、执行命令。**只运行自己写的或可信来源的脚本。**
- 脚本模块没有指令看门狗：`while(true){}` 会卡住客户端线程；控制台有约 2000 万条指令的预算，会中止并报错。
- 每 tick / 每帧的回调里不要做重活（大范围 `entities()` 遍历、字符串拼接、I/O），会掉帧。
- 发包/收包回调跑在网络线程，异常会被吞掉并停用模块。
- 已知限制：`onMove` 目前只读；不支持自定义设置项类型（如列表、条件显示）。
- 物品栏/世界只读快照：`inv.slot()` / `world.blockAt()` 每次调用现取，不做缓存；
  `inv.use()` 只能从热栏 0–8 触发（模拟手持右键），不能在背包里直接拖拽物品。

---

更新日期：2026-11-27
