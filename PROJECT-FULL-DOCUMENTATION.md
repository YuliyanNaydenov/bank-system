# Банкова система — пълна техническа документация (deep dive)

Изчерпателно описание на целия проект: какво представлява, как е устроен по слоеве, какво
съдържа и управлява всеки клас, как работи логиката и как класовете се свързват помежду си.
Накрая — отделна, подробна част за тестовия слой: всеки тест какво проверява и **как бихме го
счупили** (за да се разбере какво точно пази тестът).

---

# ЧАСТ I. ОБЩ ПРЕГЛЕД

## 1. Какво е приложението

Уеб приложение за банка, което управлява **клиенти** (физически и юридически лица), техните
**банкови сметки** (с движения по тях), **кредитни услуги** (видове кредит и отпуснати кредити
с анюитетен погасителен план), плюс администрация (потребители/служители, журнал на действията,
справки). Достъпът е по роли: `admin`, `employee`, `client`.

## 2. Архитектура и слоеве

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

## 3. Технологии (и ролята им)

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

## 4. Жизнен цикъл на една заявка (пример: отпускане на кредит)

1. Браузърът праща `POST /credits/new` с полетата на формата (+ CSRF токен).
2. Spring Security филтрите проверяват сесията и правото на достъп към `/credits/**`.
3. `CreditViewController.grant` приема `CreateCreditDTO` (Spring го попълва от формата) и го валидира.
4. Контролерът вика `creditService.grantCredit(dto)`.
5. Услугата изпълнява бизнес правилата, ползва `AnnuityCalculator`, създава entity-та и ги записва
   през репозиториите (Hibernate генерира INSERT-и в една транзакция).
6. Записва се ред в журнала.
7. Услугата връща `CreditDTO`; контролерът прави `redirect:/credits?granted`.
8. Браузърът зарежда списъка; toast показва „Кредитът е отпуснат".

## 5. Конфигурация (`application.properties`, `data.sql`)

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

# ЧАСТ II. СЛОЙ ПО СЛОЙ, КЛАС ПО КЛАС

## A. Слой `config`

### `BankSystemApplication`
Входната точка. `@SpringBootApplication` включва авто-конфигурацията и сканирането на компоненти;
`main()` стартира вградения Tomcat и контекста.

### `PasswordEncoderConfig`
Дефинира бийн `PasswordEncoder` = `BCryptPasswordEncoder`. **Защо е отделен клас:** ако беше в
`SecurityConfig`, щеше да се получи циклична зависимост (`SecurityConfig` зависи от `UserService`,
който зависи от `PasswordEncoder`, който е дефиниран в `SecurityConfig`). Изваждайки го отделно,
веригата се разплита. BCrypt е еднопосочна хеш функция със сол — паролите никога не се пазят в
явен вид; при вход се сравнява хешът (`matches`).

### `SecurityConfig`
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

### `LoginAuditListener`
`@Component` със `@EventListener` за `AuthenticationSuccessEvent` (Spring Security го издава при
успешен вход). Взема името от събитието и вика `auditService.log(username, "Вход", …)`. Така
влизанията влизат в журнала, без да пипаме логин процеса. → продължава в `AuditService`.

## B. Слой `data.entity` (модели и връзки)

### `BaseEntity`
`@MappedSuperclass` с `@Id @GeneratedValue(IDENTITY) long id`. Всички entity-та го наследяват, за
да имат единен авто-инкрементен първичен ключ. (Не е таблица сам по себе си — само „наследими"
полета.)

### Наследяване на клиента: `Client`, `IndividualClient`, `CompanyClient`
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

### `Account` + `AccountStatus`
- `Account` — `iban` (`unique`), `balance` (`BigDecimal`, точни пари), `status`
  (`@Enumerated(STRING)`, `length=20`), `owner` (`@ManyToOne Client`), колекция `transactions`
  (`cascade=ALL, orphanRemoval=true`). `@ManyToOne(optional=false)` означава, че сметка винаги има
  собственик.
- `AccountStatus`: `ACTIVE`, `CLOSED`.

### `Transaction` + `TransactionType`
- `Transaction` — `type` (enum, `length=20`), `amount`, `timestamp` (колона **`tx_timestamp`** —
  преименувана, защото „timestamp" е ключова дума в SQL), `balanceAfter` (остатъкът след
  операцията — пази се, за да се вижда в историята), `account` (`@ManyToOne`), `counterpartyIban`
  (за преводи), `description`.
- `TransactionType`: `DEPOSIT`, `WITHDRAWAL`, `TRANSFER_OUT`, `TRANSFER_IN`, `CREDIT_PAYMENT`.

### `CreditType`
`name` (`unique`), `annualInterestRate`, `maxAmount`, `maxTermMonths` — конфигурируеми параметри
на вида кредит, с Bean Validation. → ползва се при отпускане за лимитите.

### `Credit` + `CreditStatus`
- `Credit` — `client`, `creditType` (`@ManyToOne`), `amount`, `termMonths`, `startDate`, `status`
  (enum, `length=20`), `installments` (`@OneToMany cascade=ALL`, `@OrderBy("monthNumber ASC")` —
  планът винаги се чете подреден по месец).
- `CreditStatus`: `ACTIVE`, `PAID`, `CANCELLED`.

### `Installment`
Една вноска: `credit` (`@ManyToOne`), `monthNumber`, `dueDate`, `paymentAmount`, `principalPart`,
`interestPart`, `remainingBalance`, `paid`. Генерира се от анюитетния калкулатор.

### `User` + `Role`
- `User` реализира `UserDetails` (затова Spring Security може директно да го ползва): `username`,
  `password` (BCrypt хеш), `authorities` (`@ManyToMany Set<Role>`, EAGER — ролите се зареждат
  веднага, за да са налични при авторизация), флагове `enabled`, `accountNonLocked` и т.н.
- `Role` реализира `GrantedAuthority`: `authority` (`admin`/`employee`/`client`).

**Връзка:** `User`/`Role` са „идентичността"; `Client` е „бизнес профилът". Свързват се по
`username` (клиент-потребител има `Client.username == User.username`).

### `AuditLog`
`timestamp`, `username`, `action` (≤100), `details` (≤1000) — един ред в журнала.

## C. Слой `data.repo` (репозитории)

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

## D. Слой `dto`

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

## E. Слой `util`

### `AnnuityCalculator` (статичен, без състояние)
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

### `MapperUtil`
`@Configuration` с бийн `ModelMapper` + помощен `mapList`. Ползва се в `CreditTypeServiceImpl`;
другите услуги мапват ръчно (повече контрол, особено при полиморфните клиенти).

## F. Слой `service` (бизнес логика)

Всяка услуга: интерфейс + `@Service` имплементация, с `@RequiredArgsConstructor` (Lombok
инжектира зависимостите през конструктор), `@PreAuthorize` на методите (роли) и `@Transactional`
там, където се пишат няколко неща атомарно.

### `ClientService` / `ClientServiceImpl`
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

### `AccountService` / `AccountServiceImpl`
- `getAllAccounts`/`getAccountById`/`getAccountsByClient`/`getAccountsByUsername`.
- `openAccount` — намира клиента, проверява начална наличност, **генерира уникален IBAN**
  (`generateUniqueIban`: цикъл, който генерира псевдо-IBAN и проверява `existsByIban`, докато е
  уникален), запис; журнал.
- `closeAccount` — само ако наличността е **точно 0**; статус → `CLOSED`.
- `deleteAccount` — admin.

### `TransactionService` / `TransactionServiceImpl`
Подробно описано в Част III (сквозни сценарии). Ключови методи: `deposit`/`withdraw`/`transfer`/
`getByAccount`/`getByUsername`; помощни `getActiveAccount`, `requirePositive`,
`requireSufficientFunds`, `ensureCanOperate` (собственост при клиент), `record` (запис на
движение с `balanceAfter`). `transfer` е `@Transactional` (две сметки).

### `CreditTypeService` / `CreditTypeServiceImpl`
CRUD; `create`/`update` пазят уникалност на името (`DuplicateEntityException`); `delete` има
guard — ако има кредити от вида (`existsByCreditType_Id`) → `BusinessRuleException`. Мапва с
ModelMapper.

### `CreditService` / `CreditServiceImpl` (най-голямата)
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

### `UserService` / `UserServiceImpl`
- `loadUserByUsername` (за Spring Security).
- `getEmployees`/`createEmployee`/`setEmployeeEnabled`/`deleteUser` — управление на служители
  (admin), със защита на admin акаунт.
- `changePassword` — проверява текущата (`matches`), записва новата (`encode`).
- `createClientLogin` — създава `User` с роля `client` (ползва се от `createCompany`).

### `RegistrationService` / `RegistrationServiceImpl`
- `registerClient` — `@Transactional`: проверки (пароли, дубликат), създава `User`(роля client) +
  `IndividualClient`. Журнал.

### `StatisticsService` / `StatisticsServiceImpl`
- `getOverview` (admin/employee) — събира агрегати (counts/sums/group by) в `StatisticsDTO`.

### `AuditService` / `AuditServiceImpl`
- `log(action, details)` / `log(username, action, details)` — записва (никога не хвърля).
- `getRecent` (admin) — последните 200. `clearAll` (admin) — трие и оставя един запис.

## G. Слой `web.api` (REST)

`@RestController` под `/api/...`, връщат DTO/JSON; грешките се форматират от
`RestResponseEntityExceptionHandler`.
- `ClientApiController` (`/api/clients`), `AccountApiController` (`/api/accounts`),
  `TransactionApiController` (`/api/accounts/{id}/...`), `CreditTypeApiController`
  (`/api/credit-types`), `CreditApiController` (`/api/credits`). Всеки метод делегира към
  съответната услуга. Клиент вижда само своите данни (контролерът филтрира по име).

## H. Слой `web.view` (Thymeleaf)

`@Controller`, връщат имена на шаблони; подават DTO в модела; при успех правят `redirect:` с toast
параметър.
- `IndexController` (`/`), `AuthController` (`/login`, `/register`), `ClientViewController`,
  `AccountViewController`, `TransactionViewController`, `CreditTypeViewController`,
  `CreditViewController`, `EmployeeViewController`, `AuditViewController`,
  `StatisticsViewController`, `ProfileViewController`, `MyController` (клиентско самообслужване).
- Общи фрагменти в `fragments.html`: `head` (вкл. anti-flash скрипт за темата), `navbar` (с роля,
  тъмен режим), `toasts`, `confirmModal`, `scripts` (nav highlight, toasts auto-dismiss, theme
  toggle, confirm-modal логика). Стилът е в `app.css` (тема, тъмен режим, анимации).

## I. Слой `exception`

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

# ЧАСТ III. СКВОЗНИ СЦЕНАРИИ (end-to-end)

### Отпускане на кредит
`CreditViewController.grant` → `CreditService.grantCredit` (валидации + `AnnuityCalculator.
generate` → `Credit`+`Installment`) → `creditRepository.save` (един INSERT за кредита + по един за
вноските, в една транзакция) → `AuditService.log` → `CreditDTO` → шаблонът показва плана.

### Плащане на вноска от сметка (атомарно)
`MyController.payInstallmentFromAccount` (проверка за собственост) → `CreditService.
payInstallmentFromAccount`: намира вноската (неплатена + по ред) → намира сметката →
`ensureClientAccess` → активна + достатъчна наличност → **дебит на сметката** → запис на
`Transaction(CREDIT_PAYMENT)` → маркиране на вноската → ако всички платени → `PAID` → запис на
кредита → журнал. Всичко в **една** `@Transactional` — при грешка нищо не се променя.

### Превод
`TransactionViewController.transfer` / `MyController.transfer` → `TransactionService.transfer`
(`@Transactional`): активен подател + собственост → положителна сума → получателят съществува и е
активен → не е същата сметка → достатъчна наличност → дебит подател + кредит получател → два
записа в историята (OUT/IN) → журнал.

### Влизане
Form login → `DaoAuthenticationProvider` (зарежда `User` през `UserService`, проверява BCrypt) →
`AuthenticationSuccessEvent` → `LoginAuditListener` → `AuditService.log("Вход")`.

---

# ЧАСТ IV. ТЕСТОВ СЛОЙ (тест по тест: какво тества + как да го счупим)

## Подход
- **Service тестове** — чист Mockito (`@ExtendWith(MockitoExtension.class)`), **без Spring/DB**:
  репозиториите са mock-нати, проверяваме само логиката. Бързи и изолирани.
- **Контролерни тестове** — `@WebMvcTest` slice (зарежда само уеб слоя + `SecurityConfig` чрез
  `@Import`), услугите са `@MockBean`. Проверяват маршрутизация, права по роли и формат на грешки
  чрез MockMvc.
- „Как да го счупим" = каква промяна в **продукционния** код би провалила теста — така се вижда
  какво точно пази тестът.

## `AnnuityCalculatorTest`
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

## `ClientServiceImplTest` (mock: clientRepository, creditRepository, userService, auditService)
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

## `AccountServiceImplTest` (mock: accountRepository, clientRepository, auditService)
- `openAccount_generatesIbanAndSaves` — IBAN започва с „BG", статус ACTIVE, наличност 100.
  *Счупване:* не задавай IBAN/статус или сбъркай началната наличност.
- `openAccount_clientNotFound_throws` — липсващ клиент → `ClientNotFoundException`.
- `deleteAccount_notFound_throws` — `existsById=false` → `AccountNotFoundException`.
- `deleteAccount_found_deletes` — вика `deleteById`.
- `closeAccount_withBalance_throws` — наличност 50 → `BusinessRuleException`.
  *Счупване:* премахни проверката за нулева наличност.
- `closeAccount_zeroBalance_setsClosed` — статус CLOSED.

## `TransactionServiceImplTest` (mock: accountRepository, transactionRepository, auditService; @BeforeEach логва „employee")
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

## `CreditServiceImplTest` (mock: всички repo + audit; @AfterEach чисти Security контекста; helper-и authenticateStaff/accountWith/buildCreditWithTwoInstallments)
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

## `CreditTypeServiceImplTest` (mock: creditTypeRepository; конструиран с mock CreditRepository, реален MapperUtil, mock AuditService)
- `create_savesNewType` — записва нов вид.
- `create_duplicateName_throws` — дублирано име → `DuplicateEntityException`.
  *Счупване:* премахни `existsByName` проверката.
- `update_success_savesChanges` — промените се запазват.
- `update_duplicateName_throws` — преименуване към чуждо име → грешка.
- `delete_notFound_throws` — липсващ → `CreditTypeNotFoundException`.
- `getById_found_returnsDto` — мапва коректно.

## `UserServiceImplTest` (mock: userRepository, roleRepository, passwordEncoder, auditService)
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

## `RegistrationServiceImplTest` (mock: userRepository, roleRepository, clientRepository, passwordEncoder, auditService)
- `registerClient_passwordMismatch_throws`.
- `registerClient_duplicateUsername_throws`.
- `registerClient_success_savesUserAndClient` — записват се и `User`, и `IndividualClient`.
  *Счупване:* пропусни едно от двете записвания (тогава потребителят или профилът липсва).

## `StatisticsServiceImplTest`
- `getOverview_aggregatesCorrectly` — числата в `StatisticsDTO` отговарят на mock-натите агрегати;
  „кредити по вид" се мапва правилно от `List<Object[]>`.
  *Счупване:* сбъркай реда/типа на колоните при мапинга (`row[0]`/`row[1]`).

## `AuditServiceImplTest`
- `log_savesEntry` — `log` вика `save`.
- `log_neverThrows_evenIfRepoFails` — ако `save` хвърли, `log` **не** хвърля (try/catch).
  *Счупване:* премахни try/catch → журналът ще чупи основните действия.
- `getRecent_mapsToDto` — мапва записите.
- `clearAll_deletesThenLogsTheClear` — вика `deleteAll`, после `save` (записът за изчистването).
  *Счупване:* не записвай следата след изтриването.

## Контролерни тестове (`BaseControllerTest` mock-ва `UserService` + `PasswordEncoder` за `SecurityConfig`)
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

# ЧАСТ V. КАЧЕСТВО И БЕЛЕЖКИ

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
