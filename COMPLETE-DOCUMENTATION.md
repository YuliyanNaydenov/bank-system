# Банкова система — пълна документация

Този документ обединява на едно място всичко за проекта: пълен преглед и архитектура, описание на всеки слой и клас, дълбочинно описание на функционалностите (журнал, история на транзакции, кредити, справки, сигурност и т.н.), сквозни сценарии и тестовия слой с обяснение как всеки тест може да бъде счупен.

[[TOC]]

## ЧАСТ I. ОБЩ ПРЕГЛЕД

### 1. Какво е приложението

Уеб приложение за банка, което управлява **клиенти** (физически и юридически лица), техните
**банкови сметки** (с движения по тях), **кредитни услуги** (видове кредит и отпуснати кредити
с анюитетен погасителен план), плюс администрация (потребители/служители, журнал на действията,
справки). Достъпът е по роли: `admin`, `employee`, `client`.

### 2. Архитектура и слоеве

Класическо слоесто Spring Boot приложение. Заявката минава отгоре надолу:

```
Браузър (Thymeleaf форми)  /  REST клиент (JSON)
        │
        ▼
[web.view]  @Controller            [web.api]  @RestController
   връща име на шаблон                връща DTO (JSON)
        │  подава/приема DTO или прости параметри
        ▼
[service]  Интерфейс → @Service Impl
   бизнес логика, валидации, @PreAuthorize (роли), @Transactional (атомарност)
        │  работи с entity-та; вика репозитории и други услуги
        ▼
[data.repo]  Spring Data JPA репозиторий (генерира SQL)
        │
        ▼
[data.entity]  JPA entity  ←→  MySQL
```

Хоризонтални (помощни) слоеве:
- **dto** — обекти за вход/изход; държат уеб слоя независим от entity-тата.
- **exception** — собствени изключения + два централизирани хендлъра (REST и view).
- **config** — сигурност, енкодер на пароли, слушател за вход.
- **util** — анюитетен калкулатор и ModelMapper.

**Защо точно така:** разделянето на слоеве дава едно място за всяка отговорност. Контролерите
не знаят SQL; услугите не знаят HTTP; entity-тата не знаят за уеб. Това прави кода тестваем
(услугите се тестват с mock репозитории) и устойчив на промени.

### 3. Технологии (и ролята им)

- **Java 21 / Spring Boot 3.3.4** — ядро и автоконфигурация.
- **Spring MVC + Thymeleaf** — сървърно рендиране на HTML (server-side); REST контролери за JSON.
- **Spring Data JPA (Hibernate)** — ORM; репозиториите се пишат като интерфейси, Spring
  генерира имплементацията и SQL-а от имената на методите.
- **MySQL** — релационна база.
- **Spring Security** — автентикация (form login, BCrypt) и авторизация (роли).
- **Bean Validation (Jakarta)** — декларативна валидация на вход (`@NotBlank`, `@Pattern`…).
- **Lombok** — генерира getter/setter/конструктори/builder, за да няма шаблонен код.
- **ModelMapper** — авто-мапинг между сходни обекти (ползва се избирателно).
- **Bootstrap 5 + Chart.js** — стил и графики на фронтенда.
- **JUnit 5 + Mockito + Spring MockMvc** — тестове.

### 4. Жизнен цикъл на една заявка (пример: отпускане на кредит)

1. Браузърът праща `POST /credits/new` с полетата на формата (+ CSRF токен).
2. Spring Security филтрите проверяват сесията и правото на достъп към `/credits/**`.
3. `CreditViewController.grant` приема `CreateCreditDTO` (Spring го попълва от формата) и го валидира.
4. Контролерът вика `creditService.grantCredit(dto)`.
5. Услугата изпълнява бизнес правилата, ползва `AnnuityCalculator`, създава entity-та и ги записва
   през репозиториите (Hibernate генерира INSERT-и в една транзакция).
6. Записва се ред в журнала.
7. Услугата връща `CreditDTO`; контролерът прави `redirect:/credits?granted`.
8. Браузърът зарежда списъка; toast показва „Кредитът е отпуснат".

### 5. Конфигурация (`application.properties`, `data.sql`)

- `server.port=8083`.
- MySQL връзка; `createDatabaseIfNotExist=true` — базата се създава сама.
- `spring.jpa.hibernate.ddl-auto=update` — Hibernate **създава/допълва** таблиците от entity-тата
  при стартиране (добавя нови таблици/колони, но **не свива** съществуващи — затова enum колоните
  имат изрично `length=20`).
- `spring.sql.init.mode=always` + `defer-datasource-initialization=true` — `data.sql` се изпълнява
  **след** като Hibernate създаде схемата (за да има в кои таблици да вмъкне seed данните: роли,
  3 потребителя, 2 кредитни вида, примерни клиенти/сметки/транзакция).
- `spring.jpa.open-in-view=true` (по подразбиране) — държи Hibernate сесията отворена по време на
  цялата заявка, затова lazy колекциите (`credit.installments`, `client.accounts`) се четат в
  мапинг методите без `LazyInitializationException`.

---

## ЧАСТ II. СЛОЙ ПО СЛОЙ, КЛАС ПО КЛАС

### A. Слой `config`

#### `BankSystemApplication`
Входната точка. `@SpringBootApplication` включва авто-конфигурацията и сканирането на компоненти;
`main()` стартира вградения Tomcat и контекста.

#### `PasswordEncoderConfig`
Дефинира бийн `PasswordEncoder` = `BCryptPasswordEncoder`. **Защо е отделен клас:** ако беше в
`SecurityConfig`, щеше да се получи циклична зависимост (`SecurityConfig` зависи от `UserService`,
който зависи от `PasswordEncoder`, който е дефиниран в `SecurityConfig`). Изваждайки го отделно,
веригата се разплита. BCrypt е еднопосочна хеш функция със сол — паролите никога не се пазят в
явен вид; при вход се сравнява хешът (`matches`).

#### `SecurityConfig`
Конфигурира цялата сигурност чрез `SecurityFilterChain`:
- `daoAuthenticationProvider()` — свързва `UserService` (зарежда потребителя по име) с
  `PasswordEncoder` (проверява паролата). Това е механизмът за form login.
- **Авторизация по URL** (`authorizeHttpRequests`) — правилата се четат отгоре надолу, първото
  съвпадение печели:
  - публични: `/login`, `/register`, статични ресурси;
  - `/api/**` по ресурс и HTTP метод (напр. `GET /api/clients` за всички логнати, `DELETE` само
    за admin, други методи за admin+employee);
  - view пътища: `/employees`, `/audit`, `/credit-types` → само `admin`; `/clients,/accounts,
    /credits,/statistics` → admin+employee; `/my/**` → само client; всичко друго → логнат.
- **CSRF** — включен за формите (Thymeleaf слага скрит токен във всяка POST форма), но
  `ignoringRequestMatchers("/api/**")` го изключва за REST API-то, за да е тестваемо програмно.
- **form login** (`/login`, при успех → `/`) и **logout** (`/logout`).
- **accessDeniedHandler** — при отказан достъп: за `/api/**` връща `403` JSON, иначе пренасочва
  към `/`.
- `@EnableMethodSecurity(securedEnabled = true)` — включва `@PreAuthorize` на ниво метод.

**Връзка:** правилата тук са „първата ограда". Втората е `@PreAuthorize` в услугите — дори някой
да заобиколи URL правило, услугата пак ще откаже. За клиентските операции има и трета проверка
(собственост) вътре в услугата.

#### `LoginAuditListener`
`@Component` със `@EventListener` за `AuthenticationSuccessEvent` (Spring Security го издава при
успешен вход). Взема името от събитието и вика `auditService.log(username, "Вход", …)`. Така
влизанията влизат в журнала, без да пипаме логин процеса. → продължава в `AuditService`.

### B. Слой `data.entity` (модели и връзки)

#### `BaseEntity`
`@MappedSuperclass` с `@Id @GeneratedValue(IDENTITY) long id`. Всички entity-та го наследяват, за
да имат единен авто-инкрементен първичен ключ. (Не е таблица сам по себе си — само „наследими"
полета.)

#### Наследяване на клиента: `Client`, `IndividualClient`, `CompanyClient`
- `Client` е **абстрактен** `@Entity` с `@Inheritance(SINGLE_TABLE)` и
  `@DiscriminatorColumn(client_type)`. **Какво значи това на ниво база:** има **една** таблица
  `client` с колона `client_type`, в която се пазят и физическите, и юридическите лица; типът се
  разпознава по дискриминатора. Общи полета: `username` (за връзка с вход), колекции `accounts` и
  `credits` (`@OneToMany`, `cascade=ALL, orphanRemoval=true` → триене на клиент трие сметките и
  кредитите му). Абстрактни методи `getDisplayName()`, `getIdentifier()`, `getTypeLabel()` —
  всеки подклас ги дефинира по своему (полиморфизъм).
- `IndividualClient` (`@DiscriminatorValue("INDIVIDUAL")`) — `firstName`, `lastName`, `egn`
  (`@Pattern \d{10}`, `unique`). `getDisplayName` = „име фамилия", `getIdentifier` = ЕГН.
- `CompanyClient` (`@DiscriminatorValue("COMPANY")`) — `companyName`, `eik`
  (`@Pattern \d{9}|\d{13}`, `unique`), `representativeName`. `getIdentifier` = ЕИК.

**Защо SINGLE_TABLE:** най-простата и бърза стратегия; една заявка чете всички клиенти, а
полиморфните методи (`getDisplayName`/`getIdentifier`) дават еднакъв интерфейс към горните слоеве
(`ClientServiceImpl.toDto` ги ползва, без да го интересува конкретният тип).

#### `Account` + `AccountStatus`
- `Account` — `iban` (`unique`), `balance` (`BigDecimal`, точни пари), `status`
  (`@Enumerated(STRING)`, `length=20`), `owner` (`@ManyToOne Client`), колекция `transactions`
  (`cascade=ALL, orphanRemoval=true`). `@ManyToOne(optional=false)` означава, че сметка винаги има
  собственик.
- `AccountStatus`: `ACTIVE`, `CLOSED`.

#### `Transaction` + `TransactionType`
- `Transaction` — `type` (enum, `length=20`), `amount`, `timestamp` (колона **`tx_timestamp`** —
  преименувана, защото „timestamp" е ключова дума в SQL), `balanceAfter` (остатъкът след
  операцията — пази се, за да се вижда в историята), `account` (`@ManyToOne`), `counterpartyIban`
  (за преводи), `description`.
- `TransactionType`: `DEPOSIT`, `WITHDRAWAL`, `TRANSFER_OUT`, `TRANSFER_IN`, `CREDIT_PAYMENT`.

#### `CreditType`
`name` (`unique`), `annualInterestRate`, `maxAmount`, `maxTermMonths` — конфигурируеми параметри
на вида кредит, с Bean Validation. → ползва се при отпускане за лимитите.

#### `Credit` + `CreditStatus`
- `Credit` — `client`, `creditType` (`@ManyToOne`), `amount`, `termMonths`, `startDate`, `status`
  (enum, `length=20`), `installments` (`@OneToMany cascade=ALL`, `@OrderBy("monthNumber ASC")` —
  планът винаги се чете подреден по месец).
- `CreditStatus`: `ACTIVE`, `PAID`, `CANCELLED`.

#### `Installment`
Една вноска: `credit` (`@ManyToOne`), `monthNumber`, `dueDate`, `paymentAmount`, `principalPart`,
`interestPart`, `remainingBalance`, `paid`. Генерира се от анюитетния калкулатор.

#### `User` + `Role`
- `User` реализира `UserDetails` (затова Spring Security може директно да го ползва): `username`,
  `password` (BCrypt хеш), `authorities` (`@ManyToMany Set<Role>`, EAGER — ролите се зареждат
  веднага, за да са налични при авторизация), флагове `enabled`, `accountNonLocked` и т.н.
- `Role` реализира `GrantedAuthority`: `authority` (`admin`/`employee`/`client`).

**Връзка:** `User`/`Role` са „идентичността"; `Client` е „бизнес профилът". Свързват се по
`username` (клиент-потребител има `Client.username == User.username`).

#### `AuditLog`
`timestamp`, `username`, `action` (≤100), `details` (≤1000) — един ред в журнала.

### C. Слой `data.repo` (репозитории)

Всеки разширява `JpaRepository<Entity, Long>` и така получава наготово `save`, `findById`,
`findAll`, `deleteById`, `existsById`, `count`. Допълнителните методи са **derived queries**
(Spring генерира SQL от името) или `@Query` (JPQL):

- **`ClientRepository`**: `findByUsername`; `existsByEgn`/`existsByEik` — `@Query` по подкласовете
  (`from IndividualClient c where c.egn = :egn`), защото ЕГН/ЕИК са в подкласовете.
- **`AccountRepository`**: `findByIban`, `existsByIban`, `findByOwner_Id`, `findByOwner_Username`,
  `countByStatus`, `sumAllBalances` (`@Query coalesce(sum,0)`).
- **`TransactionRepository`**: `findByAccount_IdOrderByTimestampDescIdDesc`,
  `findByAccount_Owner_UsernameOrderByTimestampDescIdDesc` — обърни внимание: имената обхождат
  връзките (`account.owner.username`).
- **`CreditTypeRepository`**: `findByName`, `existsByName`.
- **`CreditRepository`**: `findByClient_Id`, `findByClient_Username`, `countByStatus`,
  `sumAllAmounts`, `countGroupedByCreditType` (`@Query group by name → List<Object[]>`),
  `existsByCreditType_Id`, `existsByClient_IdAndStatus`.
- **`InstallmentRepository`**: `findByCredit_IdOrderByMonthNumberAsc`.
- **`UserRepository`**: `findByUsername`, `existsByUsername`, `findByAuthorities_Authority`.
- **`RoleRepository`**: `findByAuthority`.
- **`AuditLogRepository`**: `findTop200ByOrderByTimestampDescIdDesc` (последните 200, най-новото
  отгоре).

**Защо derived queries:** няма ръчно писане на SQL → по-малко грешки; името на метода е
спецификацията. Извикват се **само** от service слоя.

### D. Слой `dto`

DTO-тата са „договорът" между уеб и услугите. Командните (вход) носят Bean Validation; изходните
са „плоски" за лесно показване.

- Клиенти: `CreateIndividualClientDTO`, `CreateCompanyClientDTO` (+ `password` за вход), `ClientDTO`.
- Сметки: `CreateAccountDTO`, `AccountDTO`.
- Транзакции: `AmountDTO`, `TransferDTO`, `TransactionDTO`.
- Кредитни видове: `CreateCreditTypeDTO`, `CreditTypeDTO`.
- Кредити: `CreateCreditDTO`, `CreditDTO` (+ изчислени `monthlyPayment`, `totalToRepay`,
  `remainingBalance`, `paidInstallments`, `hasOverdue`, списък `installments`), `InstallmentDTO`.
- Потребители/регистрация: `RegisterDTO`, `CreateEmployeeDTO`, `ChangePasswordDTO`, `UserSummaryDTO`.
- Справки: `StatisticsDTO`, `ChartPointDTO`. Журнал: `AuditLogDTO`.

**Защо DTO, а не entity навън:** (1) сигурност — не излагаме вътрешни полета/пароли; (2)
стабилност — промяна в entity не чупи API-то; (3) избягване на безкрайна сериализация по
двупосочните връзки (затова колекциите в entity-тата имат `@JsonIgnore`).

### E. Слой `util`

#### `AnnuityCalculator` (статичен, без състояние)
Сърцето на математиката. Анюитет = равни месечни вноски; в началото повече лихва, към края повече
главница; лихвата се смята върху **оставащата** главница.

- `monthlyRate(annualPercent)` = `annual/100/12` (с висока точност).
- `monthlyPayment(P, annual, n)` = `P·r / (1 − (1+r)⁻ⁿ)`; при `r=0` → `P/n` (безлихвен случай).
- `generate(P, annual, n)` — за всеки месец: `лихва = остатък·r` (закръглена), `главница = вноска −
  лихва`, нов `остатък = остатък − главница`; **последната вноска** изравнява остатъка точно до 0
  (поема натрупаните закръгления). Връща `List<ScheduleRow>`.

**Пример (10000, 8.5%, 12):** `r ≈ 0.0070833`; вноска ≈ **872.20**. Месец 1: лихва ≈ 70.83,
главница ≈ 801.37; към края лихвата пада, главницата расте; сборът на главниците = 10000.00,
последен остатък = 0.00. Всичко в `BigDecimal`, HALF_UP, 2 знака — без грешки от плаваща запетая.

**Връзка:** ползва се само от `CreditServiceImpl.grantCredit`, който превръща всеки `ScheduleRow`
в `Installment`.

#### `MapperUtil`
`@Configuration` с бийн `ModelMapper` + помощен `mapList`. Ползва се в `CreditTypeServiceImpl`;
другите услуги мапват ръчно (повече контрол, особено при полиморфните клиенти).

### F. Слой `service` (бизнес логика)

Всяка услуга: интерфейс + `@Service` имплементация, с `@RequiredArgsConstructor` (Lombok
инжектира зависимостите през конструктор), `@PreAuthorize` на методите (роли) и `@Transactional`
там, където се пишат няколко неща атомарно.

#### `ClientService` / `ClientServiceImpl`
- `getAllClients`/`getClientById`/`getClientByUsername` — четат и мапват ръчно с `toDto`
  (полиморфно: типът се определя с `instanceof`, попълват се суровите полета за редакция).
- `createIndividual` — проверка за дублиран ЕГН (`existsByEgn`) → `DuplicateEntityException`;
  създава `IndividualClient`; журнал.
- `createCompany` — `@Transactional`; проверка за дублиран ЕИК; ако са подадени име **и** парола →
  `userService.createClientLogin(...)` (**препратка към `UserService`**), после създава
  `CompanyClient`. Двете записвания са атомарни.
- `updateIndividual`/`updateCompany` — проверка за тип (`instanceof`, иначе `IllegalArgument`);
  при смяна на ЕГН/ЕИК — проверка за дубликат.
- `deleteClient` — guard: ако клиентът има активни кредити (`existsByClient_IdAndStatus(id,
  ACTIVE)`) → `BusinessRuleException`; иначе изтрива (каскадно сметки/кредити/транзакции).
- Зависимости: `ClientRepository`, `CreditRepository`, `UserService`, `AuditService`.

#### `AccountService` / `AccountServiceImpl`
- `getAllAccounts`/`getAccountById`/`getAccountsByClient`/`getAccountsByUsername`.
- `openAccount` — намира клиента, проверява начална наличност, **генерира уникален IBAN**
  (`generateUniqueIban`: цикъл, който генерира псевдо-IBAN и проверява `existsByIban`, докато е
  уникален), запис; журнал.
- `closeAccount` — само ако наличността е **точно 0**; статус → `CLOSED`.
- `deleteAccount` — admin.

#### `TransactionService` / `TransactionServiceImpl`
Подробно описано в Част III (сквозни сценарии). Ключови методи: `deposit`/`withdraw`/`transfer`/
`getByAccount`/`getByUsername`; помощни `getActiveAccount`, `requirePositive`,
`requireSufficientFunds`, `ensureCanOperate` (собственост при клиент), `record` (запис на
движение с `balanceAfter`). `transfer` е `@Transactional` (две сметки).

#### `CreditTypeService` / `CreditTypeServiceImpl`
CRUD; `create`/`update` пазят уникалност на името (`DuplicateEntityException`); `delete` има
guard — ако има кредити от вида (`existsByCreditType_Id`) → `BusinessRuleException`. Мапва с
ModelMapper.

#### `CreditService` / `CreditServiceImpl` (най-голямата)
- `grantCredit` — зарежда клиент/вид; валидира сума/срок спрямо лимитите; създава `Credit`; през
  `AnnuityCalculator.generate` прави плана и задава `dueDate` за всяка вноска; записва; журнал.
- `payInstallment` (служител, касово) и `payInstallmentFromAccount` (клиент/служител) — споделят
  `getPayableInstallment` (неплатена + последователна) и `markPaidIfComplete` (всички платени →
  `PAID`). Вторият допълнително тегли от сметка и записва `Transaction(CREDIT_PAYMENT)` —
  атомарно (Част III).
- `earlyPayoff` — само активен → всички платени, статус `PAID`.
- `cancelCredit` — не може ако е PAID/CANCELLED или **има платена вноска** → `CANCELLED`.
- `deleteCredit` — admin.
- `toDto` — изчислява `monthlyPayment`, `totalToRepay`, `remainingBalance`, `paidInstallments`,
  `hasOverdue` (динамично, не се пази).
> Бележка: полето `installmentRepository` е инжектирано, но не се ползва — безвреден остатък.

#### `UserService` / `UserServiceImpl`
- `loadUserByUsername` (за Spring Security).
- `getEmployees`/`createEmployee`/`setEmployeeEnabled`/`deleteUser` — управление на служители
  (admin), със защита на admin акаунт.
- `changePassword` — проверява текущата (`matches`), записва новата (`encode`).
- `createClientLogin` — създава `User` с роля `client` (ползва се от `createCompany`).

#### `RegistrationService` / `RegistrationServiceImpl`
- `registerClient` — `@Transactional`: проверки (пароли, дубликат), създава `User`(роля client) +
  `IndividualClient`. Журнал.

#### `StatisticsService` / `StatisticsServiceImpl`
- `getOverview` (admin/employee) — събира агрегати (counts/sums/group by) в `StatisticsDTO`.

#### `AuditService` / `AuditServiceImpl`
- `log(action, details)` / `log(username, action, details)` — записва (никога не хвърля).
- `getRecent` (admin) — последните 200. `clearAll` (admin) — трие и оставя един запис.

### G. Слой `web.api` (REST)

`@RestController` под `/api/...`, връщат DTO/JSON; грешките се форматират от
`RestResponseEntityExceptionHandler`.
- `ClientApiController` (`/api/clients`), `AccountApiController` (`/api/accounts`),
  `TransactionApiController` (`/api/accounts/{id}/...`), `CreditTypeApiController`
  (`/api/credit-types`), `CreditApiController` (`/api/credits`). Всеки метод делегира към
  съответната услуга. Клиент вижда само своите данни (контролерът филтрира по име).

### H. Слой `web.view` (Thymeleaf)

`@Controller`, връщат имена на шаблони; подават DTO в модела; при успех правят `redirect:` с toast
параметър.
- `IndexController` (`/`), `AuthController` (`/login`, `/register`), `ClientViewController`,
  `AccountViewController`, `TransactionViewController`, `CreditTypeViewController`,
  `CreditViewController`, `EmployeeViewController`, `AuditViewController`,
  `StatisticsViewController`, `ProfileViewController`, `MyController` (клиентско самообслужване).
- Общи фрагменти в `fragments.html`: `head` (вкл. anti-flash скрипт за темата), `navbar` (с роля,
  тъмен режим), `toasts`, `confirmModal`, `scripts` (nav highlight, toasts auto-dismiss, theme
  toggle, confirm-modal логика). Стилът е в `app.css` (тема, тъмен режим, анимации).

### I. Слой `exception`

Собствени `RuntimeException`-и → HTTP статуси: not-found групата (404), `DuplicateEntityException`
(409), `BusinessRuleException` (422). `ErrorResponse` (record) е унифицираният JSON формат.
- `RestResponseEntityExceptionHandler` (`@RestControllerAdvice` за `web.api`) — мапва към статуси
  с `ErrorResponse`/полеви грешки (400/403/404/409/422/500).
- `ViewExceptionHandler` (`@ControllerAdvice` за `web.view`) — връща HTML страница `errors/errors`
  със статус/съобщение; има и обработка на невалидни параметри и DB грешки. Непознат URL → 
  `templates/error.html`.

**Връзка:** изключенията се хвърлят в услугите, „изплуват" нагоре и се хващат тук според това дали
заявката е към `web.api` или `web.view`.

---

## ЧАСТ III. СКВОЗНИ СЦЕНАРИИ (end-to-end)

#### Отпускане на кредит
`CreditViewController.grant` → `CreditService.grantCredit` (валидации + `AnnuityCalculator.
generate` → `Credit`+`Installment`) → `creditRepository.save` (един INSERT за кредита + по един за
вноските, в една транзакция) → `AuditService.log` → `CreditDTO` → шаблонът показва плана.

#### Плащане на вноска от сметка (атомарно)
`MyController.payInstallmentFromAccount` (проверка за собственост) → `CreditService.
payInstallmentFromAccount`: намира вноската (неплатена + по ред) → намира сметката →
`ensureClientAccess` → активна + достатъчна наличност → **дебит на сметката** → запис на
`Transaction(CREDIT_PAYMENT)` → маркиране на вноската → ако всички платени → `PAID` → запис на
кредита → журнал. Всичко в **една** `@Transactional` — при грешка нищо не се променя.

#### Превод
`TransactionViewController.transfer` / `MyController.transfer` → `TransactionService.transfer`
(`@Transactional`): активен подател + собственост → положителна сума → получателят съществува и е
активен → не е същата сметка → достатъчна наличност → дебит подател + кредит получател → два
записа в историята (OUT/IN) → журнал.

#### Влизане
Form login → `DaoAuthenticationProvider` (зарежда `User` през `UserService`, проверява BCrypt) →
`AuthenticationSuccessEvent` → `LoginAuditListener` → `AuditService.log("Вход")`.

---

## ЧАСТ IV. ТЕСТОВ СЛОЙ (тест по тест: какво тества + как да го счупим)

### Подход
- **Service тестове** — чист Mockito (`@ExtendWith(MockitoExtension.class)`), **без Spring/DB**:
  репозиториите са mock-нати, проверяваме само логиката. Бързи и изолирани.
- **Контролерни тестове** — `@WebMvcTest` slice (зарежда само уеб слоя + `SecurityConfig` чрез
  `@Import`), услугите са `@MockBean`. Проверяват маршрутизация, права по роли и формат на грешки
  чрез MockMvc.
- „Как да го счупим" = каква промяна в **продукционния** код би провалила теста — така се вижда
  какво точно пази тестът.

### `AnnuityCalculatorTest`
- `monthlyPayment_knownValue` — вноската за (10000, 8.5%, 12) е точно `872.20`.
  *Счупване:* промени формулата (напр. без `pow`) или закръглянето → числото се разминава.
- `generate_returnsRowForEveryMonth` — планът има точно `n` реда (1..n).
  *Счупване:* off-by-one в цикъла (`< months` вместо `<= months`).
- `generate_principalSumEqualsLoanAndEndsAtZero` — сборът на главниците = заема, последен остатък
  0. *Счупване:* премахни изравняването на последната вноска → остатъкът няма да е точно 0.
- `generate_paymentEqualsPrincipalPlusInterest` — за всеки ред вноска = главница + лихва.
  *Счупване:* запиши `payment` независимо от частите.
- `generate_interestDecreasesAndPrincipalIncreases` — монотонност.
  *Счупване:* смятай лихвата върху **първоначалната**, а не оставащата главница.
- `zeroInterest_splitsPrincipalEvenly` — при 0% вноската е `P/n`, лихва 0, остатък 0.
  *Счупване:* премахни клона `r==0` → деление на 0 / NaN.
- `invalidTerm_throws` — срок 0 хвърля `IllegalArgumentException`.
  *Счупване:* премахни проверката за срок.

### `ClientServiceImplTest` (mock: clientRepository, creditRepository, userService, auditService)
- `createIndividual_mapsAndSaves` — резултатът е тип `INDIVIDUAL` с правилни displayName/identifier.
  *Счупване:* в `toDto` сбъркай определянето на типа или мапинга.
- `updateIndividual_updatesFields` — след update displayName е новото име.
  *Счупване:* не копирай полетата от DTO в entity-то.
- `updateIndividual_onCompanyClient_throws` — update на физ. лице върху фирма → `IllegalArgument`.
  *Счупване:* премахни `instanceof` проверката.
- `updateCompany_updatesFields` — аналогично за фирма.
- `createCompany_mapsAndSaves` — тип `COMPANY`.
- `getClientById_notFound_throws` — липсващ → `ClientNotFoundException`.
  *Счупване:* връщай `null` вместо да хвърляш.
- `getAllClients_returnsMappedList` — мапва списъка.
- `deleteClient_notFound_throws` — `existsById=false` → `ClientNotFoundException`.
  *Счупване:* премахни проверката за съществуване.

### `AccountServiceImplTest` (mock: accountRepository, clientRepository, auditService)
- `openAccount_generatesIbanAndSaves` — IBAN започва с „BG", статус ACTIVE, наличност 100.
  *Счупване:* не задавай IBAN/статус или сбъркай началната наличност.
- `openAccount_clientNotFound_throws` — липсващ клиент → `ClientNotFoundException`.
- `deleteAccount_notFound_throws` — `existsById=false` → `AccountNotFoundException`.
- `deleteAccount_found_deletes` — вика `deleteById`.
- `closeAccount_withBalance_throws` — наличност 50 → `BusinessRuleException`.
  *Счупване:* премахни проверката за нулева наличност.
- `closeAccount_zeroBalance_setsClosed` — статус CLOSED.

### `TransactionServiceImplTest` (mock: accountRepository, transactionRepository, auditService; @BeforeEach логва „employee")
- `deposit_increasesBalance` — наличността расте, тип DEPOSIT, `balanceAfter` коректен.
  *Счупване:* забрави `account.balance += amount`.
- `withdraw_insufficientFunds_throws` — теглене над наличността → `BusinessRuleException`.
  *Счупване:* премахни `requireSufficientFunds`.
- `withdraw_onClosedAccount_throws` — закрита сметка → грешка.
  *Счупване:* премахни проверката в `getActiveAccount`.
- `transfer_movesFunds` — подателят намалява, получателят расте.
  *Счупване:* кредитирай грешната сметка / забрави едно от двете записвания.
- `transfer_toMissingIban_throws` — несъществуващ IBAN → `AccountNotFoundException`.
- `transfer_insufficientFunds_throws`.
- `clientCannotOperateOnForeignAccount` — клиент „intruder" върху чужда сметка → `AccessDenied`.
  *Счупване:* премахни `ensureCanOperate` от `withdraw`.
- `clientCanOperateOnOwnAccount` — собственикът може.

### `CreditServiceImplTest` (mock: всички repo + audit; @AfterEach чисти Security контекста; helper-и authenticateStaff/accountWith/buildCreditWithTwoInstallments)
- `grantCredit_generatesSchedule` — 12 вноски, статус ACTIVE, вноска 872.20, платени 0.
  *Счупване:* не генерирай вноски / сбъркай статуса.
- `grantCredit_amountOverMax_throws` — сума над лимита → `BusinessRuleException`.
  *Счупване:* премахни проверката за `maxAmount`.
- `grantCredit_termOverMax_throws` — срок над лимита.
- `payInstallment_outOfOrder_throws` — плащане на по-късна вноска преди по-ранна → грешка.
  *Счупване:* премахни проверката `hasUnpaidEarlier`.
- `payInstallment_lastOne_marksCreditPaid` — последната вноска → статус PAID.
  *Счупване:* премахни `markPaidIfComplete`.
- `grantCredit_setsDueDates` — вноските имат `dueDate`, кредитът не е просрочен веднага.
  *Счупване:* не задавай `dueDate` при генериране.
- `earlyPayoff_marksAllPaidAndStatusPaid`.
  *Счупване:* не маркирай всички вноски / не слагай PAID.
- `cancelCredit_withPaidInstallment_throws` — отказ при платена вноска → грешка.
  *Счупване:* премахни проверката за платени вноски.
- `cancelCredit_noPaidInstallments_setsCancelled` — без платени → CANCELLED.
- `payFromAccount_debitsAccountAndMarksPaid` — наличност 600→90, вноската платена, записва се
  транзакция. *Счупване:* не дебитирай сметката / не записвай `Transaction`.
- `payFromAccount_insufficientFunds_throws`.
  *Счупване:* премахни проверката за наличност.
- `payFromAccount_closedAccount_throws`.
  *Счупване:* премахни проверката за активна сметка.
- `payFromAccount_lastInstallment_marksCreditPaid`.

### `CreditTypeServiceImplTest` (mock: creditTypeRepository; конструиран с mock CreditRepository, реален MapperUtil, mock AuditService)
- `create_savesNewType` — записва нов вид.
- `create_duplicateName_throws` — дублирано име → `DuplicateEntityException`.
  *Счупване:* премахни `existsByName` проверката.
- `update_success_savesChanges` — промените се запазват.
- `update_duplicateName_throws` — преименуване към чуждо име → грешка.
- `delete_notFound_throws` — липсващ → `CreditTypeNotFoundException`.
- `getById_found_returnsDto` — мапва коректно.

### `UserServiceImplTest` (mock: userRepository, roleRepository, passwordEncoder, auditService)
- `loadUserByUsername_notFound_throws` — липсващ → `UsernameNotFoundException`.
  *Счупване:* връщай `null` вместо да хвърляш (Spring Security разчита на изключението).
- `createEmployee_duplicateUsername_throws`.
  *Счупване:* премахни `existsByUsername`.
- `createEmployee_passwordMismatch_throws` — несъвпадащи пароли → `BusinessRuleException`.
- `createEmployee_success_savesWithRole` — записва `User` (с роля employee).
  *Счупване:* не кодирай паролата / не добавяй ролята.
- `setEmployeeEnabled_onAdmin_throws` — деактивиране на admin → грешка.
  *Счупване:* премахни проверката за admin.
- `deleteUser_admin_throws` — триене на admin → грешка.
- `changePassword_wrongCurrent_throws` — грешна текуща → грешка.
  *Счупване:* премахни `passwordEncoder.matches` проверката.
- `changePassword_success_encodesAndSaves` — записва нов хеш.

### `RegistrationServiceImplTest` (mock: userRepository, roleRepository, clientRepository, passwordEncoder, auditService)
- `registerClient_passwordMismatch_throws`.
- `registerClient_duplicateUsername_throws`.
- `registerClient_success_savesUserAndClient` — записват се и `User`, и `IndividualClient`.
  *Счупване:* пропусни едно от двете записвания (тогава потребителят или профилът липсва).

### `StatisticsServiceImplTest`
- `getOverview_aggregatesCorrectly` — числата в `StatisticsDTO` отговарят на mock-натите агрегати;
  „кредити по вид" се мапва правилно от `List<Object[]>`.
  *Счупване:* сбъркай реда/типа на колоните при мапинга (`row[0]`/`row[1]`).

### `AuditServiceImplTest`
- `log_savesEntry` — `log` вика `save`.
- `log_neverThrows_evenIfRepoFails` — ако `save` хвърли, `log` **не** хвърля (try/catch).
  *Счупване:* премахни try/catch → журналът ще чупи основните действия.
- `getRecent_mapsToDto` — мапва записите.
- `clearAll_deletesThenLogsTheClear` — вика `deleteAll`, после `save` (записът за изчистването).
  *Счупване:* не записвай следата след изтриването.

### Контролерни тестове (`BaseControllerTest` mock-ва `UserService` + `PasswordEncoder` за `SecurityConfig`)
- `CreditApiControllerTest`: списък като employee (200); отпускане с валидно тяло (201); отпускане
  като client → **403**. *Счупване:* отслаби URL правилото за `POST /api/credits`.
- `TransactionApiControllerTest`: депозит като employee (201); като client → **403**.
- `ClientApiControllerTest`: списък като employee; създаване (201); триене като employee → **403**
  (само admin); `getById` за липсващ → **404 JSON** с `status/error/message`. *Счупване на 404
  теста:* промени мапинга в `RestResponseEntityExceptionHandler` (напр. върни 500).
- `AccountApiControllerTest`: списък; откриване (201); откриване като client → 403; бизнес правило
  → **422 JSON**. *Счупване:* мапни `BusinessRuleException` към друг статус.
- `CreditTypeApiControllerTest`: списък (логнат); създаване като admin (201); като employee → 403.

**Защо тези тестове са важни:** покриват трите вида риск — (1) грешна **сметка/логика**
(калкулатор, баланси, статуси), (2) нарушено **бизнес правило** (лимити, ред на вноските,
наличност), (3) грешен **достъп** (роли, собственост). „Счупванията" по-горе показват, че всеки
тест е „пазач" на конкретно поведение.

---

## ЧАСТ V. КАЧЕСТВО И БЕЛЕЖКИ

- **Без реални clash-ове.** Двойните контролери на еднакъв base-path (`/api/accounts`,
  `/accounts`, `/api/credits`) не се застъпват по конкретни пътища. Enum колоните са с
  `length=20`. Каскадните триения и уникалните ограничения са на място.
- **Сигурност на няколко слоя** — URL правила + `@PreAuthorize` + проверка за собственост.
- **Изчислимите неща не се дублират** в базата (просрочие, остатъци, статистики) — смятат се при
  четене, затова са винаги актуални.
- **Дребно (без ефект):** `installmentRepository` в `CreditServiceImpl` не се ползва; методът
  `getCreditsByClient` няма викащ в момента.
- **Зависимост от `open-in-view=true`** за lazy колекциите в списъчните мапинги — ако някога се
  изключи, тези места трябва да станат `@Transactional` или с fetch join.

## ЧАСТ VI. Дълбочинно описание на функционалностите

Тази част проследява end-to-end как работят ключовите функционалности: откъде идват данните, как се валидират, записват, четат и показват.

### 1. Журнал на действията (audit log)

#### Какво се записва и откъде идва
Журналът пази „кой, кога, какво" за всяко важно действие. Източникът на данните е **самият
service слой** — записът се прави от вътре в бизнес метода, в момента, в който действието
успее. Допълнително, успешните **влизания** се прихващат от слушател на събитие.

#### Модел и таблица
`data/entity/AuditLog` (таблица `audit_log`): `id`, `timestamp` (`LocalDateTime`), `username`
(до 100 знака), `action` (до 100), `details` (до 1000). Това е обикновено entity, наследяващо
`BaseEntity`.

#### Как се записва — `AuditServiceImpl`
Има две припокриващи се точки за запис:

- `log(String action, String details)` — взема текущия потребител от **Spring Security
  контекста**: `SecurityContextHolder.getContext().getAuthentication()`. Ако има логнат
  потребител → `auth.getName()`; ако няма (напр. системно действие) → низа `"система"`.
  После делегира на:
- `log(String username, String action, String details)` — изгражда `AuditLog` с текущия час
  (`LocalDateTime.now()`) и го записва чрез `auditLogRepository.save(...)`.

**Важна особеност:** цялото записване е обвито в `try/catch`, който **поглъща** евентуална
грешка. Така, ако записът в журнала се провали по някаква причина, основното действие
(напр. отпускане на кредит) **не се чупи**. Журналът е странична грижа, не критичен път.

**Транзакционно поведение:** `log(...)` се извиква от вътрешността на бизнес методи, които са
`@Transactional`. Затова записът в журнала **участва в същата транзакция** — ако основното
действие се върне назад (rollback), редът в журнала също не се записва. Това е желано: няма
смисъл да има запис „Отпуснат кредит", ако кредитът всъщност не е създаден. При влизането
(слушателят) няма активна транзакция, затова `save` отваря своя собствена.

#### Откъде се извикват записите
Записи се правят на всички ключови места в услугите, например:
- `ClientServiceImpl`: „Нов клиент", „Изтрит клиент";
- `AccountServiceImpl`: „Открита сметка", „Закрита сметка", „Изтрита сметка";
- `TransactionServiceImpl`: „Депозит", „Теглене", „Превод";
- `CreditServiceImpl`: „Отпуснат кредит", „Платена вноска", „Погасена вноска от сметка",
  „Предсрочно погасяване", „Отказан кредит", „Изтрит кредит";
- `CreditTypeServiceImpl`, `UserServiceImpl`, `RegistrationServiceImpl` — съответните действия.

#### Влизанията — `config/LoginAuditListener`
Това е `@Component` с метод, маркиран `@EventListener`, който слуша за
`AuthenticationSuccessEvent` (издава се от Spring Security при успешен вход). При събитие взема
името от събитието (`event.getAuthentication().getName()`) и извиква
`auditService.log(username, "Вход", "Успешно влизане в системата")`. Така журналът съдържа и
историята на влизанията, без да пипаме логин логиката.

#### Как се чете обратно
`AuditServiceImpl.getRecent()` (с `@PreAuthorize("hasAuthority('admin')")`) извиква
`auditLogRepository.findTop200ByOrderByTimestampDescIdDesc()`. Това е **derived query** — Spring
Data сам генерира SQL от името на метода: вземи първите 200 реда, подредени по `timestamp`
намаляващо, а при равенство — по `id` намаляващо (най-новото отгоре). Резултатът се мапва към
`AuditLogDTO` (за да не излагаме entity-то навън).

#### Как се показва
`web/view/AuditViewController` (`GET /audit`) подава списъка в модела под ключ `logs` и връща
шаблона `audit/audit.html`. Шаблонът обхожда `logs` и за всеки ред показва датата
(форматирана `dd.MM.yyyy HH:mm:ss`), потребителя, действието (като бадж) и детайлите. Достъпът
е защитен двойно: на URL ниво (`/audit/**` → само `admin` в `SecurityConfig`) и на ниво услуга
(`@PreAuthorize` върху `getRecent`).

#### Изчистване
Бутонът „Изчисти журнала" праща `POST /audit/clear` → `AuditServiceImpl.clearAll()`
(`@PreAuthorize admin`). Логиката умишлено е: първо запомня кой админ изчиства, после
`auditLogRepository.deleteAll()`, и **накрая записва един нов запис** „Изчистен журнал". Така
винаги остава следа кой и кога е изтрил историята (одит на самото изчистване). Контролерът
пренасочва към `/audit?cleared`, което показва toast.

---

### 2. Транзакции и история на движенията

#### Откъде тръгва
Движение по сметка се стартира от форма (служител: `/accounts/{id}/transactions`; клиент:
`/my/accounts/{id}/transactions`) или от REST (`/api/accounts/{id}/deposit|withdraw|transfer`).
Сумата идва като параметър (`@RequestParam BigDecimal amount`) при view формите или като DTO
(`AmountDTO`/`TransferDTO`) при REST.

#### Запис на едно движение — `TransactionServiceImpl.record(...)`
Това е централната точка. `record(account, type, amount, counterpartyIban, description)`
изгражда `Transaction` с:
- `type` (DEPOSIT / WITHDRAWAL / TRANSFER_OUT / TRANSFER_IN / CREDIT_PAYMENT),
- `amount`,
- `timestamp = LocalDateTime.now()`,
- `balanceAfter = account.getBalance()` — **важно: наличността вече е обновена** преди извикването
  на `record`, така че всеки ред в историята пази остатъка след операцията;
- `counterpartyIban` (само за преводи),
- `description`.
После го записва чрез `transactionRepository.save(tx)`.

#### Депозит — `deposit(accountId, amount)` (само служител)
1. `getActiveAccount(accountId)` — намира сметката и проверява, че е **ACTIVE** (иначе
   `BusinessRuleException`).
2. `requirePositive(amount)` — сумата > 0.
3. `account.balance += amount`; `accountRepository.save(account)`.
4. `record(... DEPOSIT ...)`; запис в журнала „Депозит".

#### Теглене — `withdraw(accountId, amount)` (служител или собственик-клиент)
Като депозита, но с две допълнителни проверки:
- `ensureCanOperate(account)` — ако извикващият е **клиент**, сметката трябва да е негова
  (сравнява `account.owner.username` с името от Security контекста); служител минава без
  ограничение. Иначе `AccessDeniedException`.
- `requireSufficientFunds(account, amount)` — наличността стига.
После `balance -= amount`, запис на `WITHDRAWAL`, журнал.

#### Превод — `transfer(fromId, toIban, amount)` (служител или собственик-клиент)
Методът е `@Transactional`, защото мести пари между **две** сметки и трябва да е атомарен:
1. `from = getActiveAccount(fromId)` + `ensureCanOperate(from)` (собственост при клиент).
2. `requirePositive(amount)`.
3. `to = accountRepository.findByIban(toIban.trim())` — получателят трябва да съществува
   (иначе `AccountNotFoundException`) и да е **ACTIVE** (иначе `BusinessRuleException`).
4. Не може към същата сметка (`from.id == to.id` → грешка).
5. `requireSufficientFunds(from, amount)`.
6. `from.balance -= amount`, `to.balance += amount`, записват се и двете сметки.
7. Записват се **две** движения: `TRANSFER_OUT` по подателя (с насрещен IBAN = получателя) и
   `TRANSFER_IN` по получателя (с насрещен IBAN = подателя). Журнал „Превод".

Ако някоя стъпка хвърли изключение, цялата транзакция се връща назад — парите не „изчезват".

#### Как се чете историята
`getByAccount(accountId)` намира сметката, прави `ensureCanOperate` (клиент вижда само своята)
и извиква `transactionRepository.findByAccount_IdOrderByTimestampDescIdDesc(accountId)` — отново
derived query: всички движения по сметката, най-новите отгоре. Мапва се към `TransactionDTO`,
който носи и IBAN-а на сметката (за показване). За клиентското табло има и
`findByAccount_Owner_UsernameOrderByTimestampDescIdDesc(username)` (всички движения по сметките
на даден потребител).

#### Как се показва
- Служител: `TransactionViewController` (`GET /accounts/{id}/transactions`) подава `account`,
  `transactions` и `allAccounts` (за падащото меню при превод) → `accounts/transactions.html`:
  обобщение на сметката, три форми (депозит/теглене/превод — при превода dropdown с **другите
  активни** сметки като „IBAN — притежател") и таблица с историята.
- Клиент: `MyController` (`GET /my/accounts/{id}/transactions`) — същата идея, но без депозит и
  с **ръчно въвеждане на IBAN** на получателя (за да не излагаме чужди сметки на клиента).
- В таблицата сумата е със знак: `+` и зелено за входящи (DEPOSIT / TRANSFER_IN), `-` и червено
  за изходящи (WITHDRAWAL / TRANSFER_OUT / CREDIT_PAYMENT). Показва се и „Наличност след".

#### Каскади
`Account` има `@OneToMany ... cascade=ALL, orphanRemoval=true` към транзакциите. Затова при
изтриване на сметка (или на клиент → неговите сметки) движенията също се трият автоматично —
няма „висящи" редове, нарушаващи външния ключ.

---

### 3. Клиентско банкиране и плащане на вноска от сметка

#### Идея
Клиентът оперира **само своите** сметки и кредити, през раздела `/my` (заключен за роля
`client` в `SecurityConfig`). Сигурността е на три нива: URL правило, `@PreAuthorize` на
услугата и **проверка за собственост** вътре в услугата.

#### Проверка за собственост
- В `TransactionServiceImpl.ensureCanOperate(account)` и в `CreditServiceImpl.ensureClientAccess(
  credit, account)` се чете текущият потребител от `SecurityContextHolder`. Ако е служител/админ →
  без ограничение. Ако е клиент → сравнява username-а със собственика на сметката (и на кредита).
- В `MyController` допълнително преди извикването се проверява, че сметката/кредитът е сред
  тези на текущия потребител (`accountService.getAccountsByUsername`, `creditService.
  getCreditsByUsername`). Това е „защита в дълбочина".

#### Плащане на вноска от сметка — `CreditServiceImpl.payInstallmentFromAccount(creditId, installmentId, accountId)`
Това е най-интересният нов метод, защото свързва **кредит + сметка + транзакция** в едно атомарно
действие (`@Transactional`). Стъпки:
1. `getEntity(creditId)` — зарежда кредита.
2. `getPayableInstallment(credit, installmentId)` — намира вноската и проверява, че е **неплатена**
   и че **няма предходна неплатена** (вноските се плащат последователно). Тази проверка е извлечена
   в общ помощен метод, който се ползва и от служителското „маркиране".
3. `accountRepository.findById(accountId)` — намира сметката.
4. `ensureClientAccess(credit, account)` — ако е клиент, и кредитът, и сметката трябва да са негови.
5. Сметката трябва да е **ACTIVE** и с **достатъчна наличност** за `installment.paymentAmount`.
6. **Дебит:** `account.balance -= вноска`; `accountRepository.save(account)`.
7. **Запис на движение:** `transactionRepository.save(Transaction ... type=CREDIT_PAYMENT ...)`
   с описание „Вноска #N по кредит #X" и `balanceAfter` = новата наличност.
8. **Маркиране:** `installment.setPaid(true)`; `markPaidIfComplete(credit)` — ако всички вноски
   са платени, статусът на кредита става `PAID`.
9. `creditRepository.save(credit)`; запис в журнала „Погасена вноска от сметка".

Понеже всичко е в една транзакция, при недостатъчна наличност (или друга грешка) нищо не се
променя — нито наличността, нито вноската.

#### Два начина за плащане (съзнателно запазени)
- **Служителско „маркиране"** (`payInstallment`) — отбелязва вноската платена **без движение по
  сметка** (касово плащане на гише).
- **Плащане от сметка** (`payInstallmentFromAccount`) — реално тегли парите.
В детайла на кредита служителят вижда бутон „Плати", а клиентът — падащо меню със своите активни
сметки + „Плати" (постващо към `/my/...`). Шаблонът ги разделя чрез `sec:authorize`.

---

### 4. Надграждания по кредитите

#### Жив калкулатор на формата (изцяло в браузъра)
В `credits/credit-form.html` всяка опция за вид кредит носи `data-rate`, `data-max`, `data-term`
(параметрите на вида). Малък JavaScript при промяна на сумата/срока/вида пресмята анюитетната
вноска по същата формула като сървъра (`A = P·r/(1−(1+r)⁻ⁿ)`), показва „Месечна вноска / Общо за
връщане / Обща лихва" и предупреждава, ако сумата/срокът надхвърлят лимита. Това е **само
ориентир** — истинската валидация и генериране се правят на сървъра при `grantCredit`. Тук няма
заявки към базата; данните за лихвите идват вградени в HTML-а.

#### Падежи на вноските и просрочие
- При отпускане (`grantCredit`) за всяка вноска се задава `dueDate = startDate.plusMonths(
  monthNumber)`. Това се пази в колоната `installment.due_date`.
- Просрочието **не се пази** като статус — изчислява се динамично при четене:
  - В `toInstallmentDto`: `overdue = !paid && dueDate != null && dueDate.isBefore(днес)`.
  - В `toDto` (на кредита): `hasOverdue = статус ACTIVE && има неплатена вноска с минал падеж`.
- Показване: в плана редът с просрочена вноска получава клас `table-danger` и бадж „Просрочена";
  в детайла има банер; в списъка статусът се показва като „Просрочен". Понеже се смята при всяко
  четене, веднага щом днешната дата „мине" падеж, кредитът става просрочен без нужда от фонов
  процес.

#### Предсрочно погасяване — `earlyPayoff(creditId)`
Само за **активен** кредит: маркира всички вноски като платени и слага статус `PAID`. Това е
опростено представяне на „изплати остатъка наведнъж".

#### Отказ — `cancelCredit(creditId)`
Бизнес правилата: не може да се откаже изплатен или вече отказан кредит, и **не може, ако има
поне една платена вноска** (тогава пътят е предсрочно погасяване). Ако всичко е наред → статус
`CANCELLED`. В UI бутонът „Откажи" се показва само когато няма платени вноски
(`paidInstallments == 0`), а сървърът пак проверява — двойна защита.

---

### 5. Справки и графика

#### Откъде идват числата — `StatisticsServiceImpl.getOverview()` (admin/employee)
Методът събира агрегати директно от базата чрез репозиторийни заявки (а не зарежда всички редове
в паметта):
- `clientRepository.count()` — общ брой клиенти (вграден JpaRepository метод).
- `accountRepository.count()`, `countByStatus(ACTIVE)` (derived), `sumAllBalances()` — JPQL
  `select coalesce(sum(a.balance),0) from Account a` (връща 0 вместо null при празна база).
- `creditRepository.countByStatus(ACTIVE/PAID)`, `sumAllAmounts()` — `coalesce(sum(c.amount),0)`.
- `creditRepository.countGroupedByCreditType()` — JPQL `select c.creditType.name, count(c) ...
  group by c.creditType.name`, връща `List<Object[]>`; всеки ред `{име, брой}` се превръща в
  `ChartPointDTO`. Всичко се пакетира в `StatisticsDTO`.

#### Как се показва
`StatisticsViewController` (`GET /statistics`) подава `stats` → `statistics/statistics.html`:
- Плочки (kv-tile) с числата.
- Карта с графика „Кредити по вид".

#### Как стигат данните до графиката (надеждният начин)
Графиката се рисува с **Chart.js** (по CDN). Понеже подаването на масив директно в inline
JavaScript през Thymeleaf проекция се оказа крехко (чупеше страницата при наличие на данни),
данните се предават през **скрити HTML елементи**: за всяка точка `th:each` извежда
`<span data-label=... data-value=...>` в скрит `#chartData` контейнер. После обикновен (не
inline) JavaScript обхожда тези span-ове, събира `labels` и `values` и създава стълбовидната
графика. Така няма зависимост от Thymeleaf JS сериализация и страницата е стабилна.

---

### 6. Търсене и филтриране

#### Подход (само във view слоя)
За да не пипаме service/repo слоя и да няма риск от регресия, търсенето и филтрирането се правят
**в самия view контролер**, върху списъка, който услугата вече връща. Параметрите идват като
незадължителни `@RequestParam` (`q`, `status`).

Примери:
- `ClientViewController.list(q)` — взема `clientService.getAllClients()` и ако има `q`, филтрира
  с stream по `displayName` / `identifier` (ЕГН/ЕИК) / `username`, **без значение от регистъра**
  (`toLowerCase().contains`).
- `AccountViewController.list(q, status)` — филтър по IBAN/притежател и по статус (ACTIVE/CLOSED).
- `CreditViewController.list(q, status)` — търсене по име на клиент; филтър по статус, като
  допълнително поддържа стойност `OVERDUE` (= статус ACTIVE и `hasOverdue`).

Шаблоните имат `GET` форма с поле за търсене и (където има) падащо меню за статус, които запазват
въведената стойност (`th:value` / `selected`) и бутон „Изчисти" за нулиране. При празен резултат
се показва празно състояние.

Това е просто и достатъчно за обема данни на проекта; ако таблиците станат много големи, същият
подход лесно се заменя с репозиторийни заявки + `Pageable`.

---

### 7. Профил и смяна на парола

#### Профил — `ProfileViewController` (`GET /profile`, всяка роля)
`populate(...)` слага в модела: `username` (от Security контекста), `roles` (списъкът от
authority-та, събран в низ), и ако потребителят е **клиент** — свързания клиентски профил
(`clientService.getClientByUsername`). Шаблонът показва тези данни и формата за смяна на парола.

#### Смяна на парола — `POST /profile/password` → `UserServiceImpl.changePassword(username, current, new)`
1. Контролерът проверява, че новата парола съвпада с потвърждението и че минава Bean Validation
   (`@Size`).
2. Услугата зарежда потребителя (`userRepository.findByUsername`), проверява старата парола с
   `passwordEncoder.matches(current, user.getPassword())` (понеже паролите се пазят само като
   **BCrypt хешове**, не като чист текст). Ако не съвпада → `BusinessRuleException`.
3. Записва новата криптирана: `user.setPassword(passwordEncoder.encode(new))`, `save`. Журнал
   „Смяна на парола".
4. Контролерът пренасочва към `/profile?pwchanged` (toast). Името в навигацията е линк към профила.

---

### 8. Тъмен режим

#### Без сървър — изцяло на клиента, с предпазване от „проблясък"
- В `fragments.html` (в `<head>`, **преди** рендирането на тялото) има малък скрипт, който чете
  `localStorage.getItem('theme')`; ако е `'dark'`, слага атрибути `data-theme="dark"` и
  `data-bs-theme="dark"` на `<html>` **веднага**. Това избягва кратко „мигване" в светла тема
  при зареждане.
- В навигацията има бутон ☾/☀. Скрипт превключва двата атрибута и записва избора в `localStorage`,
  за да се помни между страници и сесии.

#### Как се преоцветява
- `data-bs-theme="dark"` кара **самия Bootstrap** да пребоядиса базовите си компоненти.
- `app.css` дефинира `html[data-theme="dark"] { … }`, който подменя дизайн-токените
  (`--bg`, `--surface`, `--ink`, `--line` …) и прави конкретни корекции по места, които иначе
  биха останали светли: навигация, плочки, хедъри на карти, тъмни таблични хедъри, footer,
  светли алерти и outline бутони. Така целият интерфейс е четим и в двете теми. Има и блок за
  `prefers-reduced-motion`, който намалява анимациите за достъпност.

---

### 9. Управление на служители

#### Списък и създаване — `UserServiceImpl` (admin)
- `getEmployees()` → `userRepository.findByAuthorities_Authority("employee")` (derived query през
  връзката `User.authorities → Role.authority`). Мапва към `UserSummaryDTO` (id, username, роли,
  статус).
- `createEmployee(dto)` — проверки за съвпадение на паролите и за дублирано потребителско име;
  взема/създава роля `employee`; записва `User` с **криптирана** парола. Журнал „Нов служител".

#### Деактивиране / активиране — `setEmployeeEnabled(id, enabled)`
Слага `User.enabled = false/true`. Тук се крие важна връзка със Spring Security: `User` реализира
`UserDetails`, а `DaoAuthenticationProvider` **отказва вход на деактивиран акаунт** (`isEnabled()`
= false). Тоест деактивирането реално блокира влизането, без да трием записа (пазим историята).
Има защита да не се деактивира/трие администраторски акаунт.

#### Изтриване — `deleteUser(id)`
Трие потребителя (с guard срещу триене на admin). Достъпът до целия раздел `/employees` е само
за admin (URL правило + `@PreAuthorize`).

---

### 10. Вход за юридически лица

#### Проблемът
Юридическите лица се създават от служител/админ, но първоначално нямаха начин за вход (нямаше
свързан `User`).

#### Решението — `ClientServiceImpl.createCompany` + `UserServiceImpl.createClientLogin`
Формата за ново юридическо лице има по избор полета „Потребителско име" и „Парола". Логиката
(`@Transactional`, за да е атомарна):
1. Проверка за дублиран ЕИК.
2. Ако са подадени **и двете** (име и парола): валидира (и двете заедно, парола ≥ 4 символа) и
   извиква `userService.createClientLogin(username, password)`, който създава `User` с роля
   `client` и криптирана парола (с проверка за дублирано име). Журнал „Нов клиентски вход".
3. Създава `CompanyClient` и записва `username` върху клиента (връзката клиент ↔ вход е по
   потребителско име).

След това фирмата може да влезе и да ползва `/my` (вижда сметките/кредитите си, прави
теглене/превод и плаща вноски). Саморегистрацията (`/register`) умишлено остава **само** за
физически лица.

---

### 11. Валидации и предпазни проверки

Тези добавки правят системата по-устойчива и дават ясни съобщения вместо сурови грешки от базата.

- **Числов формат на ЕГН/ЕИК** — `@Pattern(\d{10})` за ЕГН и `@Pattern(\d{9}|\d{13})` за ЕИК, и
  в DTO-тата, и в entity-тата. Невалиден формат се хваща още при валидацията, преди базата.
- **Ясни дубликати** — преди запис `ClientService` проверява `clientRepository.existsByEgn/
  existsByEik` (JPQL по подкласовете) и хвърля `DuplicateEntityException` с разбираемо съобщение,
  вместо да оставя уникалното ограничение в базата да гръмне с техническа грешка.
- **Guard-ове при триене**:
  - Кредитен вид с отпуснати кредити не се трие (`creditRepository.existsByCreditType_Id`) →
    `BusinessRuleException`.
  - Клиент с **активни** кредити не се трие (`existsByClient_IdAndStatus(id, ACTIVE)`).
- **CSRF** — включен за уеб формите (Thymeleaf добавя токена автоматично), но **изключен само за
  `/api/**`**, за да може REST API-то да се тества с инструменти като Postman без CSRF токен.
- **Грешки от невалидни параметри** — ако към ендпойнт с `@RequestParam` се подаде липсваща или
  нечислова стойност, специален хендлър връща чист **400 „Невалидни данни"** вместо обща 500
  страница. Има и приятелска страница за непознат URL (`templates/error.html`) и приятелско
  съобщение при грешки от базата (`DataAccessException`).

---

### Обобщение на принципите зад новите функционалности

- **Данните за журнала и историята идват от service слоя в момента на действието**, записват се в
  собствени таблици (`audit_log`, `transaction`) и се четат обратно с подредени derived заявки.
- **Парите се местят само в `@Transactional` методи** — атомарно, с проверки за активна сметка,
  достатъчна наличност и собственост.
- **Изчислимите неща (просрочие, остатък, hasOverdue, статистики) не се дублират в базата**, а се
  смятат при четене — така винаги са актуални.
- **Сигурността е на няколко слоя** (URL + `@PreAuthorize` + проверка за собственост).
- **Презентационните екстри (калкулатор, графика, тъмен режим, търсене) не натоварват бизнес
  логиката** — правят се във view слоя или в браузъра.
