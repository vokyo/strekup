# 学习打卡应用 — 阶段开发路线图

> **项目定位**:奥克兰软件工程实习简历主项目 | 独立开发 | 4–6 周工期
> **总工期**:6 周(42 天)
> **节奏**:每周投入 30–40 小时(全职学生强度)
> **产出**:8/8 简历项目 + live demo + 完整 GitHub 仓库
> **目标投递**:2026 年 6 月 2 日前完成,赶上奥克兰暑期实习第一波窗口

---

## 总览

| 阶段 | 时长 | 核心目标 | 里程碑产出 |
|---|---|---|---|
| **阶段 0** | 3 天 | 需求设计 + 架构规划 | `/docs` 文档齐全,动手前一切想清楚 |
| **阶段 1** | 1 周 | 后端核心骨架 | Postman 能跑通主流程 |
| **阶段 2** | 1 周 | 后端进阶能力 | Refresh Token + 权限 + 动态查询 + 单测 |
| **阶段 3** | 1 周 | 前端核心骨架 | 浏览器能跑全流程 CRUD |
| **阶段 4** | 1 周 | 打卡业务 + 差异化功能 | 热力图 + Redis 排行榜 + S3 + 定时提醒 |
| **阶段 5** | 4 天 | 工程化 + 部署 | Live demo 上线 |
| **阶段 6** | 3 天 | 文档 + 简历打磨 | 投第一波简历 |

---

## 技术栈速览

### Backend
- Java 21 + Spring Boot 3.4
- Spring Data JPA + Hibernate + MySQL 8 + Flyway
- Spring Security + JWT (Access + Refresh,Hash 存储 + 轮换)
- Redis (Lettuce) + ShedLock
- JUnit 5 + Mockito + Testcontainers
- SpringDoc OpenAPI 3

### Frontend
- React 18 + TypeScript + Vite
- Zustand (客户端状态) + TanStack Query v5 (服务端状态)
- React Router v6 + Axios
- Tailwind CSS + Recharts
- React Hook Form + Zod
- Vitest + React Testing Library

### DevOps
- Docker + docker-compose
- GitHub Actions
- AWS (EC2 + RDS + S3 + ElastiCache)
- Vercel (前端)

---

## 阶段 0:需求设计与架构规划(3 天)

**宗旨**:这是最容易被跳过、但决定项目质量上限的阶段。**动手写代码之前,所有决策想清楚**。

### Day 1:业务设计

- [x] 写产品一句话定义(英文)
- [x] 列出 6–8 条核心用户故事(`As a user, I want to...`)
- [x] 划定 MVP 范围 vs 未来扩展
- [x] 画线框图(Excalidraw / Figma):登录、主页、打卡详情、统计页、排行榜

**产出**:`/docs/requirements.md` + `/docs/wireframes.html`

### Day 2:技术设计

- [x] 画 ER 图(dbdiagram.io)
  - 核心实体:`User`、`Habit`、`CheckIn`、`Tag`、`RefreshToken`、`Attachment`
  - 标清关系、外键、索引
- [x] 设计 API 契约(端点列表 + 请求响应示例)
- [x] 画系统架构图(前端 ↔ 后端 ↔ MySQL/Redis/S3 的交互)
- [x] 画 **JWT + Refresh Token 认证流程图**(这个单独画,面试高频考点)

**产出**:
- `/docs/er-diagram.md`
- `/docs/api-spec.md`
- `/docs/architecture.md`
- `/docs/auth-flow.md`

### Day 3:项目初始化 + 规范定义

- [x] 新建 GitHub 仓库,初始化 README 骨架
- [x] 写 `CLAUDE.md`(仓库根目录,给 Claude Code 读)
- [x] 配置 `.gitignore`(提前把 `.env` / `target` / `node_modules` 等加上)
- [x] 在 Claude Projects 上传所有文档,建立"项目大脑"
- [x] 写 `coding-standards.md`(分层规范、命名规范、commit 规范)
- [x] 在 `/docs/decisions/` 建 ADR 目录,写第一条 ADR(技术栈选型理由)

**产出**:仓库骨架 + 项目宪章完备

### ✅ 阶段 0 结束的自检

- ✅ 我能对着白板 10 分钟讲清楚这个项目做什么、为谁做、技术架构怎样
- ✅ 所有核心技术决策都有书面理由(ADR)

**预计 commit 数**:5–8 个(每份文档一个)

---

## 阶段 1:后端核心骨架(1 周)

**目标**:Postman 能跑通注册 → 登录 → 习惯 CRUD → 打卡 CRUD 全流程。

### Day 4:项目初始化

- [ ] `start.spring.io` 创建项目(选依赖:Web / Security / Data JPA / Validation / MySQL / Flyway / Actuator)
- [ ] 本地装 MySQL 8(用 Docker 最省事),建库
- [ ] 配置 `application.yml`(**从第一天就用环境变量,不留 fallback**)
- [ ] 建立**垂直切片**目录(per [architecture.md](architecture.md) + [coding-standards.md](../coding-standards.md)):根包 `com.streakup`,每个业务特性一个子包(`auth / user / habit / checkin / tag / attachment / stats`),每个子包内含 `entity / repository / service / controller / dto`。横切基础设施放 `common/`(error / persistence / redis / time)和 `security/`。Day 4 先只建 `auth`、`user`、`common`、`security` 四个包,其余在后续天随特性进入时再建。
- [ ] 第一个 Flyway 迁移 `V1__init_schema.sql`(建 User 表)
- [ ] 项目能 `./mvnw spring-boot:run` 启动

### Day 5:基础设施

- [ ] `BaseEntity`(id / createdAt / updatedAt,`@MappedSuperclass`)
- [ ] 统一响应格式:`ApiErrorResponse` + 自定义异常(`NotFoundException`、`ForbiddenException`、`ConflictException`、`BadRequestException`)
- [ ] `GlobalExceptionHandler`(`@RestControllerAdvice`)
- [ ] 日志配置:`logback-spring.xml`(dev 文本 / prod JSON)

### Day 6–7:认证系统(Access Token only,先不做 Refresh)

- [ ] `User` entity + `UserRepository`
- [ ] `PasswordEncoder` Bean (BCrypt)
- [ ] `JwtService`(生成 + 解析 Access Token)
- [ ] `JwtAuthenticationFilter`(拦截 `Authorization: Bearer`)
- [ ] `SecurityConfig`(路径权限 + CORS)
- [ ] `AuthController`:`POST /api/v1/auth/register` + `POST /api/v1/auth/login`
- [ ] `GET /api/v1/users/me`(验证鉴权链路)
- [ ] Spring Security 的 401/403 处理:自定义 `AuthenticationEntryPoint` + `AccessDeniedHandler`
- [ ] **用 Postman 跑通注册 + 登录 + 带 token 访问 `/me`**

### Day 8–9:核心业务 CRUD(Habit + CheckIn)

- [ ] Flyway 迁移:`V2__add_habit_checkin.sql`
- [ ] `Habit` entity + Repository + Service + DTO + Controller(CRUD 五个接口)
- [ ] `CheckIn` entity + Repository + Service + DTO + Controller
- [ ] Bean Validation 加满(`@NotBlank` / `@Size` / `@Pattern` / `@Positive`)
- [ ] 打卡业务逻辑:同一习惯同一天不能重复打卡(409 Conflict)
- [ ] **2–3 个集成测试**(Testcontainers + MockMvc),覆盖成功路径

### Day 10:收尾

- [ ] 把整个流程用 Postman 再跑一遍,发现问题就修
- [ ] 整理 API 文档接入:加 SpringDoc OpenAPI,访问 `/swagger-ui.html`
- [ ] 写阶段 journal:`/docs/journal/phase-1.md`

### ✅ 阶段 1 结束的自检

- ✅ Postman 能跑通:注册 → 登录 → 建习惯 → 打卡 → 查打卡列表
- ✅ 无硬编码敏感信息
- ✅ 至少 3 个集成测试通过
- ✅ Swagger UI 能打开,API 文档自动生成

**预计 commit 数**:30–40 个

**面试故事储备**:
- "我为什么先做 Access Token,第二阶段才加 Refresh Token"(迭代式开发)
- "GlobalExceptionHandler 的分层设计"(业务异常 / Security 异常 / 兜底)
- "Spring Security 的 `AuthenticationEntryPoint` 和 `@ControllerAdvice` 的执行时机差别"

---

## 阶段 2:后端进阶能力(1 周)

**目标**:把"会做 CRUD"升级到"懂工程"。这一阶段是简历项目的**深度护城河**。

### Day 11:Refresh Token 机制

- [ ] Flyway 迁移:`V3__add_refresh_token.sql`(存 SHA-256 hash、过期时间、用户、设备指纹)
- [ ] `RefreshToken` entity + Repository
- [ ] `AuthService` 新增:`issueRefreshToken` / `rotateRefreshToken` / `revokeRefreshToken`
- [ ] `POST /api/v1/auth/refresh`(读 Cookie → 校验 hash → 颁新 token → 作废旧 token)
- [ ] `POST /api/v1/auth/logout`(清 Cookie + 作废 token)
- [ ] Cookie 配置:`HttpOnly` + `SameSite=Lax` + `Secure`(prod)+ Path 限定 `/api/v1/auth`
- [ ] 更新 `/docs/auth-flow.md`,把 Refresh 流程画清楚

### Day 12:权限模型

- [ ] 资源所有者校验(用户只能改自己的 habit / checkIn)
- [ ] 抽 `AccessService`,集中化权限判断
- [ ] Service 层调用 AccessService,Controller 不写权限逻辑
- [ ] 补对应的集成测试(资源越权返回 404; 非资源型角色不足才返回 403)

### Day 13:动态查询(JPA Specification)

- [ ] `CheckInSpecification` 工具类,支持:
  - 按日期范围
  - 按习惯 ID
  - 按标签
  - 按是否有附件
- [ ] `GET /api/v1/check-ins?habitId=xx&from=xx&to=xx&tagId=xx`
- [ ] 支持分页(`Pageable`)+ 排序
- [ ] 这是面试加分点。注释遵循 [coding-standards.md](../coding-standards.md) §Core Principles:默认不写注释,只在*为什么*非显而易见时(如 `Specification` 组合顺序、游标分页边界条件)写一行说明。面试时讲清思路比写长注释更有分量。

### Day 14:测试加强

- [ ] Service 层 Mockito 单测(覆盖失败路径):
  - 过期 refresh token
  - 错误密码
  - 重复注册
  - 非作者删除
  - 同一天重复打卡
- [ ] 目标:核心 Service 覆盖率 70%+
- [ ] CI 里加跑测试 + 覆盖率报告(Jacoco)

### Day 15:ShedLock + Redis 预备

- [ ] 引入 Spring Data Redis(Lettuce)
- [ ] 配置 Redis 连接,建 `RedisTemplate` Bean
- [ ] 引入 ShedLock(jdbc provider)
- [ ] Flyway 迁移:`V4__create_shedlock_table.sql`
- [ ] `@EnableScheduling` + `@EnableSchedulerLock` 配置
- [ ] **写一个 dummy `@Scheduled` + `@SchedulerLock`** 验证可用

### Day 16:写阶段 journal + 代码 review

- [ ] 自己从头把仓库过一遍,该重构的重构
- [ ] `/docs/journal/phase-2.md`
- [ ] 新增 ADR:"Why SHA-256 hash refresh tokens" + "Why JPA Specification over @Query"

### ✅ 阶段 2 结束的自检

- ✅ Refresh Token 完整实现,Cookie 配置正确
- ✅ 权限系统覆盖所有资源
- ✅ 至少 10 个集成测试 + 5 个 Service 单测
- ✅ Redis 和 ShedLock 已接入,待业务使用

**预计 commit 数**:25–35 个

**面试故事储备**:
- "Refresh Token 为什么要 Hash 存储 + 轮换"
- "HttpOnly Cookie + SameSite=Lax 如何防御 XSS + CSRF"
- "JPA Specification 相比原生 SQL 的类型安全优势"
- "Jacoco 覆盖率报告和测试策略"

---

## 阶段 3:前端核心骨架(1 周)

**目标**:浏览器能走通"注册 → 登录 → 建习惯 → 打卡 → 查看"完整流程。

### Day 17:项目初始化 + 基础设施

- [ ] `npm create vite@latest`(React + TypeScript)
- [ ] 配置 Tailwind CSS
- [ ] 配置 ESLint + Prettier + Husky + lint-staged(**从第一天配,不留债**)
- [ ] 目录结构:`src/{views,components,stores,api,lib,types,hooks}`
- [ ] 配置路径别名(`@/` → `src/`)
- [ ] 基础布局组件:`Layout` / `Navbar` / `Footer`

### Day 18:HTTP 层

- [ ] Axios 实例封装(`src/api/client.ts`)
- [ ] 请求拦截器:自动加 `Authorization: Bearer {accessToken}`
- [ ] 响应拦截器:
  - 401 → 自动调 `/auth/refresh` → 用新 token 重放原请求
  - 失败多次则跳登录页
  - 并发请求的 refresh 去重(防止同时触发多个 refresh)
- [ ] `register/login/logout/refresh` 请求统一开启 `withCredentials`,确保分域部署下 refresh cookie 能被浏览器写入/清除
- [ ] 类型化 API 层:`src/api/auth.ts` / `habits.ts` / `checkins.ts`
- [ ] (可选)用 `openapi-typescript-codegen` 从后端 Swagger 自动生成类型

### Day 19:状态管理

- [ ] Zustand `authStore`(currentUser / accessToken,只存内存)
- [ ] TanStack Query 配置:`QueryClient` + `QueryClientProvider`
- [ ] Query key 规范:`['habits']` / `['habit', id]` / `['checkins', habitId]`
- [ ] 全局错误处理:Toast 统一提示后端错误(读 `ApiErrorResponse.message`)
- [ ] Zustand `uiStore`(侧栏折叠、筛选面板开关等 UI 偏好)
  - *MVP 只做亮色主题*,Dark mode 明确不做(见 [coding-standards.md](../coding-standards.md) §Styling)。

### Day 20:路由 + 认证页面

- [ ] React Router v6 配置
- [ ] `ProtectedRoute`(未登录跳 `/login`,保留 `redirect` 参数)
- [ ] `PublicOnlyRoute`(已登录不能再访问登录注册)
- [ ] 登录页 + 注册页(React Hook Form + Zod 校验)
- [ ] 显示后端返回的字段级错误(`ApiErrorResponse.details`)
- [ ] **App 启动时自动尝试刷新 token**(无感恢复登录态)

### Day 21–22:业务页面

- [ ] `HabitListView`(卡片列表 + 新建按钮)
- [ ] `HabitDetailView`(习惯详情 + 打卡历史 + 快速打卡按钮)
- [ ] `HabitFormView`(新建 / 编辑,shared component)
- [ ] `CheckInFormDrawer`(抽屉式打卡表单,支持备注 + 附件)
- [ ] 三态处理齐全:Loading / Error / Empty
- [ ] 乐观更新(打卡后立刻 UI 反馈,失败回滚)

### Day 23:前端测试

- [ ] Vitest 配置
- [ ] 关键 store 的单测(`authStore` 的 login / logout)
- [ ] 关键组件的渲染 + 交互测试(`HabitCard`、`CheckInForm`)
- [ ] 目标:5–8 个测试文件

### ✅ 阶段 3 结束的自检

- ✅ 浏览器能完整走通:注册 → 登录 → 建习惯 → 打卡 → 查历史 → 登出 → 再登录
- ✅ Access Token 过期后自动续签,用户无感
- ✅ 页面三态处理齐全
- ✅ ESLint + Prettier 0 warning
- ✅ 至少 5 个前端测试

**预计 commit 数**:30–40 个

**面试故事储备**:
- "Axios 响应拦截器里的 refresh token 并发去重怎么做"(经典面试题)
- "为什么 Access Token 放内存而不是 localStorage"(安全)
- "TanStack Query 的乐观更新怎么实现"

---

## 阶段 4:打卡业务 + 差异化功能(1 周)

**目标**:让项目"有亮点",面试能讲 30 分钟不重样。

### Day 24:时区处理

- [ ] 前端安装 `date-fns` + `date-fns-tz`
- [ ] 前端读取浏览器 IANA 时区(`Intl.DateTimeFormat().resolvedOptions().timeZone`),用于注册默认值和旅行时提示用户更新资料
- [ ] 后端只使用 `users.timezone` 作为日期真相源,结合 `clientDate` 校验"用户当地今天/昨天"窗口
- [ ] 测试用例覆盖跨时区场景(奥克兰 UTC+12 vs 北京 UTC+8)
- [ ] 在 README 写清楚时区设计决策

### Day 25:Redis 热力图 + 排行榜

- [ ] **连续打卡天数(streak)**:
  - Redis key:`user:{userId}:habit:{habitId}:streak:current` / `user:{userId}:habit:{habitId}:streak:longest`
  - 每次打卡更新 + 数据库兜底
- [ ] **热力图数据**:
  - 后端按日期聚合 `SELECT check_in_date, count(*) FROM check_ins GROUP BY check_in_date`
  - 缓存到 Redis,TTL 5 分钟
- [ ] **全站排行榜**:
  - Redis ZSET:`leaderboard:total_checkins`
  - `ZINCRBY` 每次打卡 +1
  - `ZREVRANGE` 取 Top 10
- [ ] 对应 API:`GET /api/v1/stats/heatmap` / `GET /api/v1/stats/leaderboard`

### Day 26:前端数据可视化

- [ ] 安装 Recharts
- [ ] 实现 **GitHub 风格热力图**(365 格子,颜色深浅代表打卡数)
- [ ] 实现 **streak 统计卡片**(当前连续 / 历史最长)
- [ ] 实现 **月度完成率折线图**
- [ ] 实现 **排行榜页面**(Top 10 + 自己的排名)

### Day 27:附件上传(S3 预签名 URL)

- [ ] 后端:`POST /api/v1/attachments/presign`(返回预签名 PUT URL)
- [ ] 后端:`Attachment` entity(存 `userId` + S3 key + `PENDING/ATTACHED` 状态 + 关联的 checkin)
- [ ] 前端:打卡表单支持上传图片
  - 先调后端拿预签名 URL
  - 前端直接 PUT 到 S3
  - 成功后缓存 `attachmentId`,创建/编辑打卡时通过 `attachmentIds` 关联
- [ ] 展示时:后端按 `attachmentId` 返回预签名 GET URL 给前端
- [ ] 本地开发用 **LocalStack**(Docker 跑一个 S3 兼容服务),避免依赖真 AWS

### Day 28:每日提醒(ShedLock + Spring Mail)

- [ ] Spring Mail 配置(用 Gmail SMTP 或 Mailtrap 开发用)
- [ ] `ReminderService`:按用户时区,找出"今天还没打卡"的用户
- [ ] `@Scheduled(cron = "0 0 * * * *")` + `@SchedulerLock`(每小时检查一次,按用户时区判断是否到了其"早上 8 点")
- [ ] 邮件模板(Thymeleaf 渲染)
- [ ] 可选:加提醒偏好字段 `emailRemindersEnabled` / `reminderLocalTime` 让用户订阅 / 退订

### Day 29:LLM 集成(可选但强烈推荐,差异化亮点)

**按已定 API 契约实现 1 个即可**:

- 用户连续 3 天未打卡时,调用 Anthropic Claude API,生成鼓励性建议
- 端点:`POST /api/v1/ai/encouragement`
- 请求体:`{ "habitId": 17 }`
- 做缓存键:`(userId, habitId, streakBreakEpoch)` + 每用户每日限流

**实现要点**:
- 封装 `LlmService`,支持切换 provider
- 做 prompt 工程,**把 prompt 文件化放 `resources/prompts/`**(面试能讲 prompt 版本管理)
- 做 rate limit + 成本控制(缓存结果、限流)

### Day 30:阶段收尾

- [ ] 补集成测试:打卡 + 流 + 热力图 + 排行榜
- [ ] 前端截图:准备几张漂亮的截图放 README
- [ ] `/docs/journal/phase-4.md`
- [ ] 新增 ADR:"Why S3 presigned URL" + "Why ShedLock over Quartz" + "LLM integration strategy"

### ✅ 阶段 4 结束的自检

- ✅ 热力图 + 统计图表能看到自己的打卡数据
- ✅ 排行榜有数据(自己测试时多用几个账号造数据)
- ✅ 上传图片能正常展示
- ✅ 邮件提醒能收到(用 Mailtrap 测)
- ✅ 有一个 LLM 相关功能能 demo

**预计 commit 数**:25–35 个

**面试故事储备(爆炸多)**:
- 时区系统设计
- Redis ZSET 排行榜
- S3 预签名 URL 的安全 + 性能优势
- ShedLock 分布式锁
- Prompt 工程 + LLM 成本控制
- 热力图的数据聚合 + 缓存策略

---

## 阶段 5:工程化 + 部署(4 天)

**目标**:一个**公网能访问的 live demo** + 完整 CI/CD 流程。

### Day 31:Docker 化

- [ ] 后端 `Dockerfile`(多阶段构建,最终镜像 < 200MB)
- [ ] 前端 `Dockerfile`(多阶段,nginx alpine 服务静态文件)
- [ ] `docker-compose.yml`(MySQL + Redis + backend + LocalStack)
- [ ] `docker-compose up` 能一键拉起整个环境
- [ ] 在 README 写部署指南

### Day 32:CI/CD

- [ ] `.github/workflows/ci.yml`:
  - frontend job:lint + test + build
  - backend job:spotless check + test + build
  - 缓存 Maven / npm 依赖
- [ ] 加 PR 必须过 CI 才能 merge 的规则
- [ ] (可选)GitHub Actions 自动部署到 AWS

### Day 33:部署上线

- [ ] **前端 → Vercel**:
  - 连接 GitHub 仓库
  - 配置环境变量(`VITE_API_BASE_URL`)
  - 自动部署 main 分支
  - 免费自动 HTTPS + CDN
- [ ] **后端 → AWS EC2(t3.micro)**:
  - 装 Docker + Caddy
  - 拉 docker-compose,跑 backend + Redis
  - Caddy 自动 HTTPS(需要域名)
- [ ] **数据库 → AWS RDS MySQL(db.t3.micro,free tier)**
- [ ] **文件 → AWS S3(1 个 bucket)**
- [ ] **邮件 → AWS SES(production 用)或 Mailtrap(demo 用)**

### Day 34:域名 + 监控

- [ ] 买个便宜域名(Namecheap,`.me` 或 `.dev` 几十 NZD/年,**简历明显加分**)
- [ ] 配 DNS:
  - `app.yourname.dev` → Vercel
  - `api.yourname.dev` → AWS EC2
- [ ] 后端暴露 `/actuator/health` + `/actuator/metrics`
- [ ] 基础监控:UptimeRobot(免费)做 up/down 监控
- [ ] 造一些演示数据(至少 3 个账号,各自有 10+ 打卡记录)

### ✅ 阶段 5 结束的自检

- ✅ Live demo 链接能用浏览器打开、能注册登录、能打卡
- ✅ CI 每次 PR 自动跑,绿灯才能 merge
- ✅ 有自己的域名(加分项)
- ✅ 能演示跨时区:用两个不同时区账号,看时间戳处理是否正确

**预计 commit 数**:15–20 个

**面试故事储备**:
- "Docker 多阶段构建减少镜像体积"
- "Vercel + AWS 前后端分离部署的 CORS 配置"
- "AWS free tier 的成本控制"

---

## 阶段 6:文档 + 简历打磨(3 天)

**目标**:让 HR 在 30 秒内决定点进来看;让面试官 5 分钟爱上你的项目。

### Day 35:README 重写(英文)

**结构**:

1. **Hero section**:项目 logo / 名字 / slogan
2. **Badges**:build status、license、tech stack
3. **Live Demo**:**大大的链接 + 截图 / GIF**
4. **Features**:6–8 个 bullet,强调亮点
5. **Tech Stack**:分类列出(Backend / Frontend / DevOps)
6. **Architecture**:放 `/docs/architecture.md`
7. **Getting Started**:本地运行指南(5 分钟能跑起来)
8. **API Documentation**:链接到部署的 Swagger UI
9. **Challenges & Lessons Learned**:3–5 段深度内容
10. **License**

### Day 36:GIF + 截图制作

- [ ] 用 ScreenToGif(Windows)或 Kap(Mac)录 3–5 个 GIF:
  - 注册登录流程
  - 打卡 + 热力图变化
  - 排行榜
  - 邮件提醒(可用截图替代)
- [ ] 把 GIF 放 `/docs/demos/`,在 README 里嵌入

### Day 37:简历更新 + 准备投递

- [ ] 把项目加到简历,按 STAR 结构写 bullet:
  - **Situation**:"Built a personal habit-tracking web app to..."
  - **Task**:"Designed and implemented both frontend and backend..."
  - **Action**:"Used Spring Boot 3, JPA, Redis, AWS... Implemented JWT refresh token rotation..."
  - **Result**:"Live demo at xxx.dev, processed 1000+ test check-ins, 70%+ test coverage"
- [ ] 更新 LinkedIn projects 板块
- [ ] 预约 UoA CDES 做简历 review
- [ ] **投第一波简历**(10–15 家目标公司)

### ✅ 阶段 6 结束的自检

- ✅ README 英文、有截图 GIF、有 live demo 链接、有架构图
- ✅ HR 在 30 秒内能看懂这是什么、做了什么、怎么跑
- ✅ 简历 bullet 具体、有数字、技术关键词密集
- ✅ 第一波简历已投出

---

## 时间表总览

| 周次 | 日期 | 阶段 | 主任务 |
|---|---|---|---|
| W1 | 4/22 – 4/28 | 阶段 0 + 阶段 1 前半 | 需求 + 架构 + 后端骨架启动 |
| W2 | 4/29 – 5/5 | 阶段 1 后半 + 阶段 2 前半 | 后端 CRUD + Refresh Token |
| W3 | 5/6 – 5/12 | 阶段 2 后半 + 阶段 3 前半 | 后端深度 + 前端启动 |
| W4 | 5/13 – 5/19 | 阶段 3 后半 + 阶段 4 前半 | 前端 CRUD + 时区 + Redis |
| W5 | 5/20 – 5/26 | 阶段 4 后半 + 阶段 5 | 差异化功能 + 部署 |
| W6 | 5/27 – 6/2 | 阶段 6 | 文档 + 简历 + **第一波投递** |

**6/2 之前完成所有事情,赶上 5 月底第一波实习申请窗口。**

---

## 每周末的仪式感(必做)

**每周日花 1 小时做这件事,回报极高**:

1. **写 weekly journal**(`/docs/journal/week-N.md`,200–300 字)
   - 这周完成了什么
   - 最大的坑是什么,怎么解决的
   - 下周的重点
2. **整理 commit**:看看有没有垃圾 commit 需要 rebase(`git rebase -i`)
3. **更新 Claude Projects**:把 weekly journal 和新增 ADR 上传到知识库
4. **准备一个面试小故事**:从本周经历里提炼 1 个 15 分钟的故事

**6 周下来你会有 6 个 weekly journal + 10+ ADR,这就是你面试时的黄金素材库**。

---

## 风险预案

### 如果某个阶段超期了怎么办?

**优先级排序(保命顺序)**:

1. **阶段 0 不能省**(否则后面全乱)
2. **阶段 1 + 阶段 3 是基础**,必须完成
3. **阶段 2 的 Refresh Token + 权限必做**,其他可降级
4. **阶段 4 的差异化功能,至少完成 2 个**(时区 + 热力图最保底)
5. **阶段 5 必须部署上线**,没有 live demo 不算简历项目
6. **阶段 6 不能省**,不然前面白干

### 可以砍的东西(如果真的来不及)

- Day 29 的 LLM 集成(留到项目上线后慢慢加)
- Day 28 的邮件提醒(改成"应用内提醒"简化)
- 部分 Vitest 前端测试(后端测试不能砍)
- 域名(用 Vercel 默认域名也能跑)

### 绝对不能砍的

- Refresh Token 机制
- 全局异常处理
- 时区处理
- Docker 化
- 部署上线
- README + 简历

---

## 工具清单

### 开发环境
- **IDE**:IntelliJ IDEA Ultimate(学生免费)或 Community Edition + VS Code
- **数据库 GUI**:DBeaver / DataGrip
- **API 测试**:Postman / Insomnia / Bruno
- **终端**:Warp / iTerm2 / Windows Terminal

### AI 辅助
- **Claude Projects**:项目大脑(需求、架构、决策、面试素材)
- **Claude Code**:本地仓库的执行助手
- **关键:`CLAUDE.md` 放仓库根目录**,作为 Claude Code 的 system context

### 设计 / 文档工具
- **Excalidraw**:画架构图、流程图(free,手绘风格)
- **dbdiagram.io**:画 ER 图
- **Mermaid**:README 里嵌入图表(GitHub 原生支持)
- **Figma**:画线框图(free plan 够用)

### 部署 / 运维
- **Docker Desktop**:本地容器化
- **LocalStack**:本地模拟 AWS S3
- **Mailtrap**:开发邮件测试
- **UptimeRobot**:免费监控

### 录制演示
- **ScreenToGif**(Windows)
- **Kap**(Mac)
- **OBS**(录视频 demo)

---

## 最后的话

这 6 周如果按计划走,你 6/2 会有:

- 一个 **8/8 简历项目**,总分至少在奥克兰实习生里排前 20%
- 一个 **live demo** + 自己的域名
- **6 个 weekly journal + 10+ ADR** 作为面试素材库
- 深度理解的技术栈:JPA / Spring Security / Redis / AWS / React / TanStack Query
- **10+ 个可以讲 20 分钟的面试故事**

更重要的是:**这是一个完全属于你的项目**。每一行代码、每一个决策、每一个坑你都经历过。

面试官问你任何问题,你都能从实战经验里答上来。

这个阶段结束时,你不再是"一个刚入学没有项目的学生",而是"**一个有完整全栈项目、懂工程的实习生候选人**"。

---

**开工日期**:2026-04-22
**目标完成**:2026-06-02
**第一波投递**:2026-06-02 当天

现在,打开终端,`git init` 走起。
