# Наръчник на проекта „Банкова система"

Подробно описание на всеки клас, неговите методи и логика, и как се свързват слоевете
помежду си. Документът е подреден по слоеве; където един метод „продължава" в друг слой,
има препратка (→).

---

## 0. Обща архитектура и поток на заявка

Приложението е класическо слоесто Spring Boot приложение:

```
Браузър / REST клиент
        │
        ▼
[web]  Controller (view: Thymeleaf  |  api: JSON)
        │  подава DTO / прости параметри
        ▼
[service]  Service (интерфейс) → ServiceImpl  — бизнес логика, валидации, @PreAuthorize, @Transactional
        │  работи с entity-та
        ▼
[data.repo]  Repository (Spring Data JPA)
        │
        ▼
[data.entity] Entity ←→ MySQL
```

Помощни слоеве:
- **dto** — обекти за вход/изход; изолират entity-тата от уеб слоя.
- **exception** — собствени изключения + два централизирани хендлъра (за REST и за view).
- **config** — сигурност, енкодер на пароли, слушател за вход.
- **util** — анюитетен калкулатор и ModelMapper.

Принципи, важащи навсякъде:
- **Сигурност на два слоя**: URL правила в `SecurityConfig` + `@PreAuthorize` на service методите.
- **DTO навътре/навън**: контролерите никога не връщат entity директно.
- **`open-in-view=true`** (по подразбиране) държи Hibernate сесията отворена по време на
  заявката, затова lazy колекциите (напр. `credit.installments`) се четат в мапинг методите
  без грешка.

---

## 1. Слой `config` — конфигурация

### `BankSystemApplication`
Входната точка. `@SpringBootApplication` + `main()` стартира контекста.

### `PasswordEncoderConfig`
Дефинира бийн `PasswordEncoder` = `BCryptPasswordEncoder`. Отделен е от `SecurityConfig`, за
да се избегне циклична зависимост (`SecurityConfig → UserService → PasswordEncoder`).
→ Ползва се в `UserServiceImpl`, `RegistrationServiceImpl`, `SecurityConfig`.

### `SecurityConfig`
Сърцето на сигурността.
- `daoAuthenticationProvider()` — свързва `UserService` (зарежда потребителя) + `PasswordEncoder`.
- `filterChain(HttpSecurity)` — дефинира:
  - **публични** пътища: `/login`, `/register`, статични ресурси;
  - **URL правила по роли** за `/api/**` и за view пътищата (напр. `/employees/**`,
    `/audit/**`, `/credit-types/**` → само `admin`; `/my/**` → само `client`;
    `/clients,/accounts,/credits,/statistics` → `admin`+`employee`);
  - **CSRF** — включен за формите, изключен само за `/api/**` (за да е тестваемо API-то);
  - **form login** (`/login`), **logout** (`/logout`);
  - **accessDeniedHandler** — за `/api/**` връща JSON 403, иначе пренасочва към `/`.
→ Зависи от `UserService` (за зареждане на потребител). Правилата тук работят паралелно с
`@PreAuthorize` в service слоя.

### `LoginAuditListener`
`@Component` с `@EventListener` за `AuthenticationSuccessEvent`. При успешен вход извиква
`AuditService.log(username, "Вход", …)`. → продължава в **service** (`AuditService`).

---

## 2. Слой `data.entity` — модели (JPA)

### `BaseEntity`
`@MappedSuperclass` с `@Id @GeneratedValue` `long id`. Всички entity-та го наследяват, за да
имат еднакъв първичен ключ.

### `Client` (абстрактен) + `IndividualClient` + `CompanyClient`
- `Client` — `@Entity` с **`SINGLE_TABLE` наследяване** и дискриминатор `client_type`.
  Общи полета: `username` (за връзка с вход), колекции `accounts` и `credits`
  (`@OneToMany`, cascade ALL + orphanRemoval → триене на клиент трие и сметките/кредитите му).
  Абстрактни методи `getDisplayName()`, `getIdentifier()`, `getTypeLabel()`.
- `IndividualClient` (`@DiscriminatorValue("INDIVIDUAL")`) — `firstName`, `lastName`, `egn`
  (`@Pattern \d{10}`, unique). `getDisplayName` = „име фамилия", `getIdentifier` = ЕГН.
- `CompanyClient` (`@DiscriminatorValue("COMPANY")`) — `companyName`, `eik`
  (`@Pattern \d{9}|\d{13}`, unique), `representativeName`. `getIdentifier` = ЕИК.
→ Управляват се от `ClientService`; мапват се към `ClientDTO`.

### `Account` + `AccountStatus`
- `Account` — `iban` (unique), `balance` (`BigDecimal`), `status` (enum, `length=20`),
  `owner` (`@ManyToOne Client`), колекция `transactions` (cascade ALL + orphanRemoval →
  триене на сметка трие движенията ѝ).
- `AccountStatus` — `ACTIVE`, `CLOSED`.
→ Управлява се от `AccountService`; движенията — от `TransactionService`.

### `Transaction` + `TransactionType`
- `Transaction` — `type` (enum, `length=20`), `amount`, `timestamp` (колона `tx_timestamp`,
  преименувана, за да не се бие с ключова дума), `balanceAfter`, `account` (`@ManyToOne`),
  `counterpartyIban` (за преводи), `description`.
- `TransactionType` — `DEPOSIT`, `WITHDRAWAL`, `TRANSFER_OUT`, `TRANSFER_IN`, `CREDIT_PAYMENT`.
→ Създават се от `TransactionServiceImpl.record(...)` и от `CreditServiceImpl` (при
плащане на вноска от сметка).

### `CreditType`
`name` (unique), `annualInterestRate`, `maxAmount`, `maxTermMonths` — конфигурируеми
параметри на вида кредит, с Bean Validation. → Управлява се от `CreditTypeService`,
ползва се при `CreditService.grantCredit` за валидиране на лимитите.

### `Credit` + `CreditStatus`
- `Credit` — `client`, `creditType` (`@ManyToOne`), `amount`, `termMonths`, `startDate`,
  `status` (enum, `length=20`), `installments` (`@OneToMany`, cascade ALL, `@OrderBy monthNumber`).
- `CreditStatus` — `ACTIVE`, `PAID`, `CANCELLED`.
→ Управлява се от `CreditService`.

### `Installment`
Една вноска: `credit` (`@ManyToOne`), `monthNumber`, `dueDate`, `paymentAmount`,
`principalPart`, `interestPart`, `remainingBalance`, `paid`. Генерира се от анюитетния
калкулатор в `CreditServiceImpl.grantCredit`.

### `User` + `Role`
- `User` — реализира `UserDetails`; `username`, `password` (BCrypt), `authorities`
  (`@ManyToMany Set<Role>`, EAGER), флагове `enabled` и т.н.
- `Role` — реализира `GrantedAuthority`; `authority` (`admin`/`employee`/`client`).
→ `User` се зарежда от `UserServiceImpl.loadUserByUsername` (за Spring Security).

### `AuditLog`
`timestamp`, `username`, `action`, `details` — един запис в журнала.
→ Създава се от `AuditServiceImpl`.

---

## 3. Слой `data.repo` — репозитории (Spring Data JPA)

Всеки разширява `JpaRepository<Entity, Long>` (готови CRUD методи: `findById`, `findAll`,
`save`, `deleteById`, `existsById`, `count`). Допълнителни (derived/JPQL) методи:

- **`ClientRepository`** — `findByUsername`; `existsByEgn` / `existsByEik` (JPQL по подкласовете).
- **`AccountRepository`** — `findByIban`, `existsByIban`, `findByOwner_Id`,
  `findByOwner_Username`, `countByStatus`, `sumAllBalances` (JPQL `coalesce(sum,0)`).
- **`TransactionRepository`** — `findByAccount_IdOrderByTimestampDescIdDesc`,
  `findByAccount_Owner_UsernameOrderByTimestampDescIdDesc`.
- **`CreditTypeRepository`** — `findByName`, `existsByName`.
- **`CreditRepository`** — `findByClient_Id`, `findByClient_Username`, `countByStatus`,
  `sumAllAmounts`, `countGroupedByCreditType` (за справките), `existsByCreditType_Id`,
  `existsByClient_IdAndStatus` (за guard-ове при триене).
- **`InstallmentRepository`** — `findByCredit_IdOrderByMonthNumberAsc`.
- **`UserRepository`** — `findByUsername`, `existsByUsername`, `findByAuthorities_Authority`.
- **`RoleRepository`** — `findByAuthority`.
- **`AuditLogRepository`** — `findTop200ByOrderByTimestampDescIdDesc`.

→ Извикват се само от **service** слоя.

---

## 4. Слой `dto` — обекти за пренос на данни

Командни (вход, с Bean Validation) и изходни (за показване):

- **Клиенти**: `CreateIndividualClientDTO`, `CreateCompanyClientDTO` (има и `password` за
  създаване на вход), `ClientDTO` (изход — тип, displayName, identifier, брой сметки/кредити + сурови полета за редакция).
- **Сметки**: `CreateAccountDTO` (ownerId, initialBalance), `AccountDTO`.
- **Транзакции**: `AmountDTO` (депозит/теглене), `TransferDTO` (toIban + amount), `TransactionDTO`.
- **Кредитни видове**: `CreateCreditTypeDTO`, `CreditTypeDTO`.
- **Кредити**: `CreateCreditDTO`, `CreditDTO` (вкл. изчислени `monthlyPayment`, `totalToRepay`,
  `remainingBalance`, `paidInstallments`, `hasOverdue`, списък вноски), `InstallmentDTO`.
- **Потребители/регистрация**: `RegisterDTO`, `CreateEmployeeDTO`, `ChangePasswordDTO`, `UserSummaryDTO`.
- **Справки**: `StatisticsDTO`, `ChartPointDTO`.
- **Журнал**: `AuditLogDTO`.

→ Създават се/попълват се в **service** (`toDto(...)`) и се приемат от **web** контролерите.

---

## 5. Слой `util` — помощни

### `AnnuityCalculator` (статичен)
Ядрото на изчисленията. Методи:
- `monthlyRate(annualPercent)` — месечен лихвен процент (`годишен/100/12`).
- `monthlyPayment(P, annual, n)` — анюитетна вноска `A = P·r / (1 − (1+r)⁻ⁿ)`; при `r=0` →
  `P/n` (безлихвен случай).
- `generate(P, annual, n)` — пълен план: за всеки месец лихва = остатък·r, главница = вноска −
  лихва, нов остатък; последната вноска изравнява остатъка до 0. Връща `List<ScheduleRow>`.
Всичко в `BigDecimal`, HALF_UP, 2 знака.
→ Ползва се само от `CreditServiceImpl.grantCredit`.

### `MapperUtil`
`@Configuration` с бийн `ModelMapper` + помощен `mapList(...)`. → Ползва се в
`CreditTypeServiceImpl` (другите услуги мапват ръчно).

---

## 6. Слой `service` — бизнес логика

Всяка услуга е интерфейс + `@Service` имплементация (`@RequiredArgsConstructor`,
`@PreAuthorize` на методите). Тук са валидациите, бизнес правилата и транзакциите.

### `ClientService` / `ClientServiceImpl`
- `getAllClients` / `getClientById` / `getClientByUsername` → `ClientRepository`, мапва ръчно с `toDto`.
- `createIndividual` — проверка за дублиран ЕГН (`existsByEgn`) → DuplicateEntity; създава
  `IndividualClient`; → **audit** „Нов клиент".
- `createCompany` — проверка за дублиран ЕИК; ако са подадени потребителско име **и** парола →
  `userService.createClientLogin(...)` (**препратка към `UserService`**) и след това създава
  `CompanyClient`. `@Transactional`, за да са атомарни двете записвания.
- `updateIndividual` / `updateCompany` — проверка за тип; ако се сменя ЕГН/ЕИК — проверка за дубликат.
- `deleteClient` — guard: ако има активни кредити (`creditRepository.existsByClient_IdAndStatus`) →
  BusinessRule; иначе изтрива (каскадно сметки/кредити).
→ Извиква се от `ClientApiController`, `ClientViewController`; ползва `CreditRepository`, `UserService`, `AuditService`.

### `AccountService` / `AccountServiceImpl`
- `getAllAccounts` / `getAccountById` / `getAccountsByClient` / `getAccountsByUsername`.
- `openAccount` — намира клиента, проверява начална наличност, **генерира уникален IBAN**
  (`generateUniqueIban` с `existsByIban`), запис; → audit „Открита сметка".
- `closeAccount` — само ако наличността е 0; статус → `CLOSED`.
- `deleteAccount` — admin.
→ Извиква се от `AccountApiController`, `AccountViewController`, `TransactionViewController`, `MyController`.

### `TransactionService` / `TransactionServiceImpl`
- `deposit` (staff) / `withdraw` / `transfer` / `getByAccount` / `getByUsername`.
- Общи проверки: `getActiveAccount` (активна), `requirePositive`, `requireSufficientFunds`,
  `record` (записва `Transaction` + наличност след).
- `ensureCanOperate(account)` — ако извикващият е **клиент**, сметката трябва да е негова
  (чете `SecurityContextHolder`), иначе `AccessDenied`. Прилага се на `withdraw/transfer/getByAccount`.
- `transfer` — атомарен (`@Transactional`): дебит на подателя + кредит на получателя + два
  записа в историята.
→ Извиква се от `TransactionApiController`, `TransactionViewController`, `MyController`, и
от `CreditServiceImpl` (косвено — плащане от сметка ползва същия модел на запис).

### `CreditTypeService` / `CreditTypeServiceImpl`
- CRUD; `create`/`update` пазят уникалност на името (DuplicateEntity); `delete` има guard —
  ако има кредити от вида (`existsByCreditType_Id`) → BusinessRule. Мапва с **ModelMapper**.
→ Извиква се от `CreditTypeApiController`, `CreditTypeViewController`, и от `CreditService` (за лимитите).

### `CreditService` / `CreditServiceImpl` (най-голямата)
- `grantCredit` — валидира клиент/вид/сума/срок спрямо лимитите, създава `Credit`, **генерира
  плана** чрез `AnnuityCalculator.generate` (**препратка към `util`**) и попълва падежите;
  → audit „Отпуснат кредит".
- `payInstallment` (staff, касово) и `payInstallmentFromAccount` (клиент/staff) — споделят
  `getPayableInstallment` (неплатена + последователна) и `markPaidIfComplete` (всички платени →
  `PAID`). Вторият допълнително: `ensureClientAccess` (собственост), активна сметка, достатъчна
  наличност → **дебит на сметката + запис на `Transaction` тип `CREDIT_PAYMENT`** (**препратка
  към `data` слоя през `accountRepository`/`transactionRepository`**), всичко в една `@Transactional`.
- `earlyPayoff` — маркира всички вноски платени, статус `PAID`.
- `cancelCredit` — само ако не е изплатен/отказан **и няма платени вноски** → `CANCELLED`.
- `deleteCredit` (admin).
- `toDto` — изчислява `monthlyPayment`, `totalToRepay`, `remainingBalance`, `paidInstallments`,
  `hasOverdue` (активен + минал падеж).
> Бележка: полето `installmentRepository` е инжектирано, но **не се ползва** — безвреден
> остатък, който може да се премахне.
→ Извиква се от `CreditApiController`, `CreditViewController`, `MyController`.

### `UserService` / `UserServiceImpl`
- `loadUserByUsername` (за Spring Security) → `UserRepository`.
- `getEmployees` / `createEmployee` / `setEmployeeEnabled` / `deleteUser` — управление на служители
  (admin); защита да не се пипа admin акаунт.
- `changePassword` — проверява текущата (`passwordEncoder.matches`), записва новата.
- `createClientLogin` — създава `User` с роля `client` (ползва се от `ClientServiceImpl.createCompany`).
→ Извиква се от `EmployeeViewController`, `ProfileViewController`, `ClientServiceImpl`, `SecurityConfig`.

### `RegistrationService` / `RegistrationServiceImpl`
- `registerClient` — саморегистрация на физическо лице: проверки (пароли, дубликат),
  създава `User`(роля client) + `IndividualClient`. `@Transactional`. → audit „Регистрация".
→ Извиква се от `AuthController`.

### `StatisticsService` / `StatisticsServiceImpl`
- `getOverview` — събира агрегати от трите репозитория (брой клиенти/сметки/кредити, суми,
  „кредити по вид") в `StatisticsDTO`. → Извиква се от `StatisticsViewController`.

### `AuditService` / `AuditServiceImpl`
- `log(action, details)` / `log(username, action, details)` — записва запис; **никога не хвърля**
  (try/catch), за да не чупи основното действие.
- `getRecent` (admin) — последните 200 записа.
- `clearAll` (admin) — трие всичко и оставя един запис за самото изчистване.
→ Извиква се от почти всички services + `LoginAuditListener`; преглежда се от `AuditViewController`.

---

## 7. Слой `web.api` — REST контролери (JSON)

Всеки е `@RestController` под `/api/...`, връща DTO. Делегират директно на service слоя;
грешките се форматират от `RestResponseEntityExceptionHandler`.

- **`ClientApiController`** (`/api/clients`) — списък (клиент вижда само себе си),
  по id, създаване индивидуален/фирма, update, delete.
- **`AccountApiController`** (`/api/accounts`) — списък, по id, по клиент, откриване,
  закриване, изтриване.
- **`TransactionApiController`** (`/api/accounts/{id}/...`) — история, депозит, теглене, превод.
- **`CreditTypeApiController`** (`/api/credit-types`) — CRUD.
- **`CreditApiController`** (`/api/credits`) — списък, по id, отпускане, плащане на вноска,
  плащане от сметка, предсрочно погасяване, отказ, изтриване.

→ Всеки метод продължава в съответната **service**.

---

## 8. Слой `web.view` — Thymeleaf контролери

`@Controller`, връщат имена на шаблони (в `resources/templates`). Подават DTO към модела;
формите постват обратно командни DTO; при успех — `redirect:` с toast параметър.

- **`IndexController`** (`/`) — начален екран (metric карти).
- **`AuthController`** (`/login`, `/register`) — вход и саморегистрация → `RegistrationService`.
- **`ClientViewController`** (`/clients`) — списък с **търсене**, форми за физ./юр. лице, редакция, триене.
- **`AccountViewController`** (`/accounts`) — списък с **търсене+филтър**, откриване, закриване, триене.
- **`TransactionViewController`** (`/accounts/{id}/transactions`) — история + депозит/теглене/превод (служител).
- **`CreditTypeViewController`** (`/credit-types`) — CRUD (admin).
- **`CreditViewController`** (`/credits`) — списък с **търсене+филтър**, отпускане (с жив
  калкулатор в шаблона), детайл с плана, плащане, предсрочно, отказ, триене.
- **`EmployeeViewController`** (`/employees`) — служители (admin).
- **`AuditViewController`** (`/audit`) — журнал + изчистване (admin).
- **`StatisticsViewController`** (`/statistics`) — справки + графика → `StatisticsService`.
- **`ProfileViewController`** (`/profile`) — профил + смяна на парола → `UserService`.
- **`MyController`** (`/my`) — клиентско самообслужване: табло, детайл на собствен кредит,
  **плащане на вноска от сметка**, теглене/превод по собствени сметки → `CreditService`,
  `AccountService`, `TransactionService` (с проверки за собственост).

Шаблоните използват общ фрагмент (`fragments.html`: head, navbar с роля и тъмен режим, toasts,
confirm modal, scripts) + `app.css` (тема, тъмен режим, анимации).

---

## 9. Слой `exception` — изключения и обработка

### Собствени изключения (всички `RuntimeException`)
- `ClientNotFoundException`, `AccountNotFoundException`, `CreditNotFoundException`,
  `CreditTypeNotFoundException`, `InstallmentNotFoundException` → **404**.
- `DuplicateEntityException` → **409** (дублиран запис).
- `BusinessRuleException` → **422** (нарушено бизнес правило).

### `ErrorResponse`
`record` с `timestamp, status, error, message, path` — унифициран JSON формат за грешки.

### `RestResponseEntityExceptionHandler` (`@RestControllerAdvice` за `web.api`)
Мапва изключенията към HTTP статуси с `ErrorResponse`/валидационен JSON:
404 (not-found групата), 403 (`AccessDenied`), 400 (`IllegalArgument`, `MethodArgumentNotValid`
с полеви грешки, `MethodArgumentTypeMismatch`), 409 (Duplicate), 422 (BusinessRule),
500 (`DataAccessException`, общ `Exception`).

### `ViewExceptionHandler` (`@ControllerAdvice` за `web.view`)
Същите изключения, но връща HTML страница `errors/errors` с подходящ статус/заглавие/съобщение
(вкл. невалидни параметри и DB грешки). За непознат URL Spring ползва `templates/error.html`.

→ Изключенията се хвърлят в **service** слоя и се „хващат" тук според това дали заявката е
към `web.api` или `web.view`.

---

## 10. Слой на тестовете (`src/test`)

Unit тестове (Mockito, без Spring/DB) и slice тестове (`@WebMvcTest` + MockMvc).

- **`AnnuityCalculatorTest`** — коректност на анюитета (вноска, сбор на главниците = заема,
  остатък 0, намаляваща лихва/растяща главница, безлихвен случай, невалиден срок).
- **`ClientServiceImplTest`** — създаване/update физ./юр. лице, грешен тип, ненамерен, триене.
- **`AccountServiceImplTest`** — откриване, ненамерен клиент, закриване (с/без наличност), триене.
- **`TransactionServiceImplTest`** — депозит/теглене/превод, недостатъчна наличност, закрита
  сметка, несъществуващ IBAN, и **собственост на клиента** (чужда/своя сметка).
- **`CreditServiceImplTest`** — отпускане + лимити, падежи, плащане (ред/последна),
  предсрочно, отказ (със/без платени вноски), **плащане от сметка** (дебит, недостатъчна
  наличност, закрита сметка, последна вноска → PAID).
- **`CreditTypeServiceImplTest`** — създаване, дубликат, update, триене.
- **`UserServiceImplTest`** — служители (дубликат/несъвпадащи пароли/успех), защита на admin,
  смяна на парола, ненамерен потребител.
- **`RegistrationServiceImplTest`** — несъвпадащи пароли, дубликат, успешна регистрация.
- **`StatisticsServiceImplTest`** — агрегиране на справките.
- **`AuditServiceImplTest`** — запис, „не чупи при DB грешка", мапване, изчистване.
- **REST slice** (`BaseControllerTest` + `*ApiControllerTest`) — за `Credit`, `Transaction`,
  `Client`, `Account`, `CreditType`: успешни операции, права по роли (403), формат на
  грешките (404/422 JSON).

→ Service тестовете подменят (mock) репозиториите; контролерните — service-ите и зареждат
`SecurityConfig`, за да проверят правата.

---

## 11. Сквозни сценарии (end-to-end през слоевете)

**Отпускане на кредит:**
`CreditViewController.grant` (web) → `CreditService.grantCredit` (service: валидации +
`AnnuityCalculator.generate` от util) → `Credit`+`Installment` (entity) → `creditRepository.save`
(repo) → `AuditService.log` → връща `CreditDTO` → шаблонът показва плана.

**Клиент плаща вноска от сметка:**
`MyController.payInstallmentFromAccount` (web, проверка за собственост) → `CreditService.
payInstallmentFromAccount` (service: `ensureClientAccess`, активна сметка, наличност →
дебит на `Account` + запис на `Transaction(CREDIT_PAYMENT)` + маркиране на вноската, всичко
`@Transactional`) → repo записи → audit → редирект с toast.

**Превод между сметки:**
`TransactionViewController.transfer` / `MyController.transfer` (web) → `TransactionService.
transfer` (service: `ensureCanOperate`, проверки, атомарен дебит+кредит + два записа в
историята) → repo → audit.

---

## 12. Констатации от прегледа

1. **Логиката е коректна и слоевете са консистентно свързани**; не са открити реални clash-ове.
   Двойните контролери на еднакъв base-path (`/api/accounts`, `/accounts`, `/api/credits`) не
   се застъпват по пътища. Enum колоните са с достатъчна дължина (`length=20`). Каскадните
   триения и уникалните ограничения са на място.
2. **Сигурност на два слоя** — URL правила + `@PreAuthorize`; собствеността при клиент се
   проверява допълнително в service (`ensureCanOperate` / `ensureClientAccess`).
3. **Дребно (без ефект):** `installmentRepository` в `CreditServiceImpl` е инжектиран, но не
   се ползва — може да се премахне. Методът `getCreditsByClient` няма викащ в момента (част от
   интерфейса; аналогът `getAccountsByClient` се ползва от REST).
4. **Зависимост от `open-in-view=true`** (по подразбиране) — позволява четене на lazy колекции
   (`credit.installments`, `client.accounts`) в мапинг методите по време на заявката. Ако някога
   се изключи, тези места ще трябва да станат `@Transactional` или с fetch join.
5. Препоръчителните валидации/guard-ове (числов ЕГН/ЕИК, дубликати, триене на използван вид/
   клиент с активни кредити, невалидни параметри) вече са добавени.
