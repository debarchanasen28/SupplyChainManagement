\[PROJECT TITLE — e.g., "Supply Chain Integration Hub: A Multi-Tenant Purchase Order Management System with SAP CPI Integration"\]

**A Report Submitted in Partial Fulfilment of the Requirements of the Internship / Major Project**

Submitted by

**Name:** [Student Name]
**Branch:** [Branch / Department]
**Semester:** [Semester]
**Internship at:** [Organization Name]
**Company Name:** [Company Name]
**Project Guide:** [Guide Name, Designation]

[Department Name]
[College Name]
[University Name]

**Submission Date:** [DD/MM/YYYY]

---

## ACKNOWLEDGEMENT

I would like to express my sincere gratitude to [Company Name] for providing me the opportunity to undertake my internship and work on this project. This experience has given me valuable exposure to real-world enterprise integration architecture, full-stack web development, and cross-system communication design.

I am deeply thankful to my Project Supervisor, [Supervisor Name], for the continuous guidance, technical mentorship, and constructive feedback provided throughout the duration of this project. Their insights into system design and integration practices were instrumental in shaping the direction of this work.

I would also like to extend my heartfelt thanks to my Faculty Guide, [Faculty Guide Name], [Department Name], [College Name], for the academic supervision, timely suggestions, and encouragement extended during the course of this internship.

I am grateful to [College Name] and the [Department Name] for providing the academic foundation and infrastructure that made this internship possible.

I would also like to thank my friends and colleagues who supported me with discussions, code reviews, and encouragement during challenging phases of development.

Finally, I am indebted to my family for their unwavering support, patience, and motivation throughout this journey.

[Student Name]

---

## PREFACE

This report documents the work undertaken during my internship at [Company Name], where I was involved in the design, development, and integration of a supply-chain purchase-order management platform built on a Spring Boot backend, a React frontend, and SAP Cloud Platform Integration (CPI) as the middleware bridging two independent business systems.

The purpose of this internship was to gain hands-on, industry-oriented exposure to enterprise application development — spanning backend service design, database modelling, authentication and authorization, REST API design, frontend engineering, and integration-platform-as-a-service (iPaaS) concepts, specifically SAP CPI. Prior to this internship, my exposure to these topics was largely academic; this project provided the opportunity to translate theoretical knowledge of software engineering into a functioning, end-to-end system that mirrors patterns used in real enterprise supply-chain and procurement software.

Working on this project was a significant learning experience. It required understanding not only how to build a web application, but how two independent organizational systems can be designed to interoperate through a neutral integration layer, how message contracts must be defined and respected by both communicating parties, how authentication and tenant isolation must be layered into a multi-role system, and how resilience patterns such as retries, idempotency, and audit logging are essential in any system that talks to an external network dependency.

The motivation behind this project stemmed from the need to simulate a realistic business-to-business trading relationship: a procurement organization and a vendor organization exchanging purchase orders, stock confirmations, shipment updates, and inventory movements — all mediated through SAP CPI so that the two sides remain decoupled from each other's internal implementation.

Through this project, I gained practical skills in Java and Spring Boot service architecture, MongoDB document modelling, JWT-based authentication, REST API design and documentation, React application structure with hooks and context, and the fundamentals of SAP CPI integration flows (iFlows), including message routing, format conversion, and mapping. I also strengthened my debugging, testing, and technical documentation abilities over the course of this work.

[Student Name]

---

## ABSTRACT

This project presents the design and implementation of a **Supply Chain Integration Hub**, a full-stack, multi-tenant purchase-order and inventory management platform that connects two independent, simulated business entities — referred to internally as **System 1** and **System 2** — through **SAP Cloud Platform Integration (SAP CPI)** as the sole communication bridge between them. The system was built to demonstrate an industry-representative pattern in which two organizations, each with their own procurement and vendor-side operations, exchange purchase orders, stock offers, approval decisions, shipment updates, and inventory movements without either side directly calling the other's API — all traffic is routed through SAP CPI integration flows (iFlows), each of which performs message parsing, control-header extraction, format conversion (JSON, XML, or CSV), and message mapping before delivering the payload to its destination.

The backend of the system is implemented in **Java 21** using the **Spring Boot** framework, exposing a role-based REST API secured with **JSON Web Tokens (JWT)** and **Spring Security**. Data is persisted in **MongoDB Atlas** using **Spring Data MongoDB**, with document collections modelling purchase orders, users, suppliers, shipments, inventory items, alerts, and an integration-log audit trail. The backend implements a **tenant-isolation model** (`SystemId`: `SYSTEM1` / `SYSTEM2`) so that a single running backend instance can safely serve both simulated organizations, with every read and write scoped to the authenticated user's tenant.

A distinguishing feature of the backend is an in-process **System 2 simulator** (`System2ProcurementOrderGenerator`) that autonomously raises purchase orders on a fixed schedule, randomly selecting one of three wire formats (JSON, XML, or CSV) per order, and dispatches them to SAP CPI's inbound HTTP endpoint (iFlow 1) using an OAuth2 client-credentials-secured HTTP client (`CpiClient`). This simulator, together with several `@Scheduled` lifecycle and recovery jobs, allows the platform to demonstrate live, continuous, two-way order traffic without requiring a human operator on the System 2 side, while System 1 (the human-operated side) is driven through the React frontend by users authenticating as Vendor, Procurement, Admin, or Manager.

The **frontend** is a **React 19** single-page application built with **Vite**, using **React Router v7** for client-side routing, the **Context API** for authentication state, and **Axios** for HTTP communication with the backend. The UI is organized into role-specific dashboards (Vendor, Procurement, Admin/Manager) and shared feature pages for orders, shipments, inventory, suppliers, alerts, user management, and an integration-log viewer that exposes the underlying message-tracing data for observability purposes.

The system implements a two-status-vocabulary design: an internal `OrderStatus` enum that drives the order fulfilment lifecycle (via scheduled lifecycle jobs), and a `PoStatus` enum that represents the shared, cross-system contract vocabulary (`DRAFT → SENT → RECEIVED → APPROVED/REJECTED → FULFILLED → SHIPPED`) that both System 1 and System 2 obey. Correlation IDs and idempotency keys are used throughout the inbound and outbound integration paths to guarantee exactly-once processing in the presence of network retries and a scheduled simulator that could otherwise flood the backend with duplicate requests.

The project's objectives were to build a functioning, secure, role-based procurement platform; to implement a realistic, resilient integration bridge to an external iPaaS (SAP CPI) supporting multiple wire formats; to enforce tenant isolation between two simulated organizations sharing one physical deployment; and to provide full observability into cross-system message traffic through an audit-logging subsystem. These objectives were substantially achieved: the platform supports end-to-end purchase-order creation, dispatch, approval/rejection, stock negotiation, shipment lifecycle tracking, low-stock inventory alerting, and a searchable, paginated integration-log trail, all secured behind JWT authentication with per-role authorization rules enforced in `SecurityConfig`.

This report documents the technology stack, system requirements, system analysis, architectural design, module-by-module implementation, database schema, feature set, REST API surface, testing approach, results, challenges encountered, learning outcomes, and recommended future enhancements for the Supply Chain Integration Hub.

---

## TABLE OF CONTENTS

| Section | Page |
|---|---|
| Acknowledgement | [•] |
| Preface | [•] |
| Abstract | [•] |
| List of Figures | [•] |
| List of Tables | [•] |
| Chapter 1: Introduction | [•] |
| Chapter 2: Technology Stack | [•] |
| Chapter 3: System Requirements | [•] |
| Chapter 4: System Analysis | [•] |
| Chapter 5: System Design | [•] |
| Chapter 6: Implementation | [•] |
| Chapter 7: Database Design | [•] |
| Chapter 8: Features | [•] |
| Chapter 9: API Documentation | [•] |
| Chapter 10: Testing | [•] |
| Chapter 11: Results | [•] |
| Chapter 12: Challenges Faced | [•] |
| Chapter 13: Learning Outcomes | [•] |
| Chapter 14: Future Enhancements | [•] |
| Chapter 15: Conclusion | [•] |
| References | [•] |
| Appendix | [•] |

*Note: Page numbers marked [•] are populated automatically in the Word (.docx) version via its native Table of Contents field. In the .docx deliverable, this table is generated from the document's heading styles and will update to the correct page numbers when refreshed (right-click → Update Field) after opening in Microsoft Word.*

---

## LIST OF FIGURES

| Figure | Title |
|---|---|
| Figure 5.1 | Overall System Architecture (System 1, System 2, SAP CPI) |
| Figure 5.2 | Frontend Component and Routing Architecture |
| Figure 5.3 | Backend Layered Architecture (Controller–Service–Repository) |
| Figure 5.4 | Authentication and JWT Flow |
| Figure 5.5 | Purchase Order Lifecycle State Diagram (OrderStatus) |
| Figure 5.6 | Cross-System PO Contract Lifecycle (PoStatus) |
| Figure 5.7 | SAP CPI iFlow 1 — Outbound PO Message Flow |
| Figure 5.8 | SAP CPI iFlow 2 — Approval Callback Flow |
| Figure 5.9 | Tenant Isolation / Mirror-Order Data Flow |
| Figure 11.1 | Login Page |
| Figure 11.2 | Vendor Dashboard |
| Figure 11.3 | Procurement Dashboard |
| Figure 11.4 | Admin Dashboard |
| Figure 11.5 | Orders Page (List and Create) |
| Figure 11.6 | Integration Logs / Message Trace Viewer |

---

## LIST OF TABLES

| Table | Title |
|---|---|
| Table 2.1 | Backend Technology Stack Summary |
| Table 2.2 | Frontend Technology Stack Summary |
| Table 3.1 | Minimum Hardware Requirements |
| Table 3.2 | Recommended Hardware Requirements |
| Table 3.3 | Software Requirements |
| Table 7.1 | MongoDB Collections Summary |
| Table 7.2 | `orders` Collection Field Reference |
| Table 9.1 | Authentication API Endpoints |
| Table 9.2 | Order Management API Endpoints |
| Table 9.3 | CPI Inbound Bridge API Endpoints |
| Table 9.4 | Inventory, Shipment, Supplier, and Alert API Endpoints |
| Table 9.5 | Dashboard, Admin, and Logging API Endpoints |
| Table 12.1 | Challenges and Resolutions |

---

# CHAPTER 1: INTRODUCTION

## 1.1 Company Overview

[Company Name] is [placeholder — brief description of the company, its industry, size, and business focus, to be filled in with actual company details]. During the course of this internship, I was placed in the [Team / Department Name] team, working under the guidance of [Project Guide Name] on the design and development of the Supply Chain Integration Hub project described in this report.

## 1.2 Project Overview

The project is a full-stack **Supply Chain Integration Hub** that models a purchase-order trading relationship between two organizations — internally named **System 1** and **System 2** — that exchange purchase orders, stock offers, shipment updates, and inventory movements exclusively through **SAP Cloud Platform Integration (SAP CPI)**. The codebase consists of a Java Spring Boot backend (package `com.supplychain.integration_hub`), a React single-page-application frontend, and a set of SAP CPI integration flows (iFlows) that mediate all cross-system traffic. A single backend deployment and a single MongoDB database serve both simulated organizations; a `SystemId` discriminator (`SYSTEM1` / `SYSTEM2`) on every persisted record keeps the two tenants' data isolated from one another while sharing the same infrastructure.

System 1 is the human-operated side of the platform: real users log in through the React frontend under one of four roles (Vendor, Procurement, Admin, Manager) and interact with orders, shipments, inventory, suppliers, and alerts through the UI. System 2 is a robot simulator running inside the same backend process — it has no UI of its own and instead uses a scheduled background job (`System2ProcurementOrderGenerator`) to autonomously raise purchase orders against System 1's vendor and to auto-approve or occasionally reject System 1 procurement's outgoing purchase orders, so that the demo environment shows continuous, realistic two-way order traffic without a second team of human testers.

## 1.3 Problem Statement

Enterprises that trade with external partners rarely integrate their internal systems directly with a partner's systems. Instead, they typically route all cross-organization traffic through a middleware or integration-platform-as-a-service (iPaaS) layer that can validate, transform, route, and audit every message, so that neither side needs to trust or understand the other's internal data model. Building and demonstrating such an architecture end-to-end — including the iPaaS layer itself, multi-format message contracts, tenant isolation, idempotent message handling, and full audit traceability — requires solving a broader set of problems than a typical single-tenant CRUD web application: message format negotiation (JSON/XML/CSV), correlation and idempotency under network retries, asynchronous approval callbacks, and safe handling of a counterparty that may sometimes be a scheduled robot rather than a human.

## 1.4 Motivation

The motivation for this project was to build something closer to a real enterprise integration scenario than a typical academic CRUD project, in order to gain practical exposure to the concerns that dominate real integration work: contract-first API design (the project's `docs/PO_CONTRACT.md` defines the canonical message shape before any endpoint is built), resilience (retries with backoff, idempotency keys, open-order caps), auditability (an `IntegrationLog` collection recording every inbound and outbound CPI message), and tenant isolation in a shared-infrastructure system. SAP CPI was chosen specifically because it is a widely used enterprise iPaaS, and gaining hands-on exposure to designing iFlows, control-header conventions, and OAuth2-secured HTTP adapters has direct relevance to enterprise integration and SAP-adjacent career paths.

## 1.5 Objectives

The project set out to achieve the following objectives:

- Build a secure, role-based, multi-tenant purchase-order management backend using Spring Boot, Spring Security, JWT authentication, and MongoDB.
- Design and implement a resilient outbound and inbound bridge to SAP CPI supporting three interchangeable wire formats (JSON, XML, CSV) for purchase orders.
- Implement a shared cross-system status contract (`PoStatus`) that both simulated organizations obey, distinct from the internal fulfilment lifecycle (`OrderStatus`).
- Provide full request/response audit logging for every message that crosses the CPI bridge, to support message tracing and debugging.
- Build a React frontend with role-specific dashboards (Vendor, Procurement, Admin/Manager) covering the full order-to-shipment lifecycle.
- Demonstrate live, continuous two-way order traffic without requiring two sets of human testers, via an in-process scheduled simulator for the counterparty organization.
- Maintain strict isolation between the two simulated tenants sharing the same backend and database.

## 1.6 Scope

The current implementation covers: user authentication and role-based authorization; purchase order creation, dispatch, approval/rejection, and stock-offer negotiation across two integration lanes; shipment lifecycle tracking with scheduled status advancement; inventory management with low-stock alerting; a supplier directory; an admin/manager oversight layer with cross-tenant dashboards; and a searchable, paginated integration-log viewer. It explicitly does **not** yet cover (see §1.10 and Chapter 14): nested multi-line-item purchase orders end-to-end (the outbound iFlow 1 payload currently carries the first line item only), containerized deployment, automated CI, or a production external deployment of SAP CPI outside the developer's trial tenant.

## 1.7 Existing System

Prior to this project (from the perspective of the simulated business scenario), System 1 and System 2 would have had no automated way to exchange purchase orders other than manual re-keying, email, or point-to-point API integration requiring each side to trust and understand the other's internal systems and data formats — brittle patterns that break down as soon as either side changes its internal data model, and which offer no centralized place to validate, transform, or audit cross-organization traffic.

## 1.8 Proposed System

The proposed system introduces SAP CPI as a neutral, centrally managed integration layer between the two organizations. Each side owns and controls only its own backend and database; all cross-organization messages are addressed to CPI endpoints (iFlows) rather than to each other directly. CPI performs control-header parsing, format conversion, and message mapping, decoupling System 1's and System 2's internal representations of a purchase order from the wire contract that travels between them. Both systems' backends implement the same integration contract (documented in `docs/PO_CONTRACT.md`) so that either side can evolve its internal implementation without breaking the other, as long as the contract at the CPI boundary is respected.

## 1.9 Advantages

The architecture provides several concrete advantages: (i) tenant isolation allows one physical deployment to safely demonstrate a two-company scenario; (ii) the retry/backoff and idempotency-key design in `CpiClient` and the inbound controllers means transient network failures or duplicate deliveries do not corrupt order state; (iii) the `IntegrationLog` audit trail gives full visibility into every message that crossed the bridge, which is invaluable for debugging integration issues; (iv) supporting three wire formats (JSON/XML/CSV) for the same logical PO demonstrates that the integration layer, not the wire format, is the actual contract; and (v) the in-process System 2 simulator allows continuous demonstration of two-way traffic without needing a second live counterparty system.

## 1.10 Limitations

At present the system has several known limitations, most of which are also tracked in the project's own internal checkpoint notes: outbound purchase orders through iFlow 1 currently serialize only the first line item of a multi-item order (`OrderService.buildPayload`/`System2ProcurementOrderGenerator.buildPayload` both note this as a Phase 5 item); several secondary collections (`Shipment`, `Alert`, `Supplier`, `Inventory`) are not yet `systemId`-scoped as consistently as `Order`, so some admin-facing counts are effectively global rather than strictly per-tenant; the frontend currently hardcodes the backend base URL (`http://localhost:8080/api`) rather than reading it from an environment variable; CORS is currently configured permissively (`allowedOriginPatterns: "*"` with credentials enabled) for development convenience; and the SAP CPI inbound approval callback currently relies on an ngrok tunnel to reach the developer's local backend rather than a permanently deployed public endpoint. A duplicate, stale copy of the frontend scaffold (`frontend/frontend/`) also remains in the repository pending cleanup. These items are discussed further in Chapter 12 (Challenges) and Chapter 14 (Future Enhancements).

---

# CHAPTER 2: TECHNOLOGY STACK

This chapter documents every technology actually present in the codebase's dependency manifests (`pom.xml` for the backend, `package.json` for the frontend) and explains the rationale for each, based on how it is used in the code.

## 2.1 Programming Languages

**Java 21** (`<java.version>21</java.version>` in `pom.xml`) is used for the entire backend. Java was the natural choice given the target framework, Spring Boot, and gives access to modern language features used in the codebase, such as `switch` expressions with pattern matching (used in `OrderService.buildPayload` and `CpiClient`'s format-based payload builders) and records-adjacent idioms via Lombok.

**JavaScript (ES2020+, JSX)** is used for the entire frontend. No TypeScript is used in the active `src/` frontend, despite `@types/react` and `@types/react-dom` being present as devDependencies — these are editor/tooling type-hint packages only; all source files are plain `.jsx`/`.js`.

## 2.2 Backend Framework — Spring Boot

**Spring Boot** (version `4.1.0` per the parent POM) is the backend application framework. It was chosen because it provides, out of the box, the dependency-injection container, embedded servlet runtime, auto-configuration, and starter-dependency model that let the project focus on business and integration logic rather than boilerplate wiring. The specific starters used are:

- `spring-boot-starter-webmvc` — the REST controller layer (Spring MVC), used by all 21 `@RestController` classes in the project.
- `spring-boot-starter-data-mongodb` — Spring Data MongoDB repository abstraction, used by all 8 `MongoRepository` interfaces.
- `spring-boot-starter-security` — the authentication/authorization filter chain, configured in `SecurityConfig`.
- `spring-boot-starter-validation` — Jakarta Bean Validation, used on request DTOs and turned into clean 400 responses by `GlobalExceptionHandler`.
- `spring-boot-devtools` (runtime, optional) — hot-reload during development.

## 2.3 Authentication — JWT (jjwt)

Authentication uses **JSON Web Tokens** via the `io.jsonwebtoken` (`jjwt-api`, `jjwt-impl`, `jjwt-jackson`, version `0.12.6`) library. `JwtUtil` signs tokens with an HMAC-SHA key derived from the `app.jwt.secret` property (sourced from the `APP_JWT_SECRET` environment variable) and embeds the user's `role`, `entityId`, and `systemId` as custom claims. A custom `JwtAuthFilter` (a Spring `OncePerRequestFilter`) reads the `Authorization: Bearer <token>` header on every request (except `/api/cpi/inbound/**`, which is authenticated differently — see §2.9), validates the token, and populates the Spring Security context with a `UserPrincipal` carrying the user's email, role, and tenant. JWT was chosen over server-side sessions because the API is stateless (`SessionCreationPolicy.STATELESS` in `SecurityConfig`) and the same token can carry the tenant/role claims needed for authorization decisions without a database round-trip on every request.

## 2.4 Database — MongoDB (via Spring Data MongoDB)

**MongoDB Atlas** is the persistence layer, accessed through **Spring Data MongoDB**'s `MongoRepository` interfaces and, for more complex tenant/role/tab-filtered queries (e.g., `OrderService.queryOrders`), the lower-level `MongoTemplate` with dynamically built `Criteria` objects. A document database was an appropriate fit here because the domain's core entity, the purchase order, has a naturally nested, semi-structured shape (a list of `OrderItem` line items, a shifting set of optional fields depending on which stage of the multi-lane negotiation flow the order is in) that maps cleanly onto a MongoDB document without requiring a normalized relational join. The connection string is externalized to the `MONGODB_URI` environment variable and loaded via Spring's `spring.config.import=optional:file:.env[.properties]` mechanism, keeping credentials out of source control.

## 2.5 Frontend Framework — React

**React 19.2.6** (with `react-dom` 19.2.6) is the frontend UI framework, chosen for its component model, mature ecosystem, and straightforward integration with a REST backend via hooks and `fetch`/Axios. The application is a single-page application (SPA) built with function components exclusively — no class components are used in the active page/component tree, with the sole exception of a small `ErrorBoundary` class component in `App.jsx` (class components remain the only way to implement `componentDidCatch`/`getDerivedStateFromError` in React).

## 2.6 Build Tool — Vite

**Vite 8.0.12** is the frontend build tool and development server (`vite`, `vite build`, `vite preview` scripts in `package.json`), using the official `@vitejs/plugin-react` plugin for JSX/Fast-Refresh support. Vite was used (rather than, e.g., Create React App or Webpack directly) for its fast cold-start dev server and native ES-module-based bundling.

## 2.7 Routing — React Router

**React Router DOM v7.17.0** provides client-side routing. `App.jsx` defines a `BrowserRouter` with an explicit `<Routes>` tree, a `PrivateRoute` wrapper component that checks the authenticated user's `token`, `role`, and (for the Inventory page) `systemId` before rendering a protected page, and a `RootRedirect` component that sends an authenticated user to the dashboard appropriate for their role.

## 2.8 State Management

The frontend does **not** use Redux, Zustand, or any external state-management library for application state, despite `redux`, `react-redux`, `redux-thunk`, `immer`, and `reselect` being present in `node_modules` — a source-level check (`grep -r "redux" src/`) confirms these are not imported anywhere in `src/`; they are present only as transitive/peer dependencies pulled in by another package and are not part of the application's actual state architecture. State management in the real application is handled entirely by:

- **React's built-in `useState`/`useEffect`/`useCallback` hooks**, used extensively for local component state (form fields, loading/error flags, pagination state, filters) in every page component.
- **React Context** (`AuthContext.jsx`), which holds the authenticated user's `token`, `role`, `name`, `entityId`, and `systemId`, persisted to `localStorage` and exposed to the whole tree via `AuthProvider`/`useAuth()`.

This is a deliberate, appropriately-scoped choice for an application of this size: authentication is genuinely global state (Context is the right tool), while everything else (order lists, filters, form drafts) is local to the page that owns it and does not need to be lifted into a global store.

## 2.9 API Design — REST over HTTPS, with a Secondary Control-Line Convention for CPI

The backend's user-facing API is a conventional **JSON-over-REST** API (Spring `@RestController` classes returning `ResponseEntity<?>`), secured by JWT as described in §2.3. Separately, the CPI-facing integration endpoints (`/api/cpi/inbound/**`) do **not** use JWT at all — `JwtAuthFilter.shouldNotFilter` explicitly skips JWT processing for this path prefix, and `SecurityConfig` marks it `permitAll()`, because SAP CPI's HTTP receiver adapter does not carry an end-user JWT. Instead, these endpoints are intended to be protected by a shared secret header (`X-CPI-Secret`, configured via `cpi.inbound-secret`) checked inside the controller/service layer, and the outbound direction (`CpiClient`) authenticates itself to CPI using **OAuth2 client-credentials** (a cached Bearer token fetched from `cpi.token-url` using `cpi.client-id`/`cpi.client-secret`). Outbound PO bodies additionally begin with a one-line plaintext control header (`#meta source=... target=... format=...`) that CPI's Groovy routing script parses before applying format-specific conversion — this convention is documented in full in `docs/PO_CONTRACT.md` and implemented in `CpiClient.sendPo`.

## 2.10 HTTP Client — Axios

**Axios 1.18.0** is used on the frontend for all HTTP calls to the backend. A shared instance (`src/api/axios.js`) is configured with the backend's base URL and a request interceptor that automatically attaches the stored JWT as an `Authorization: Bearer` header to every outgoing request, so individual page components never need to manage the token manually.

## 2.11 Styling

The application's visual styling is implemented almost entirely with **inline JavaScript style objects** passed to the `style` prop of JSX elements (visible throughout `App.jsx`, `Login.jsx`, and the dashboard pages), plus one dedicated stylesheet, `IntegrationLogs.css`, for the integration-log viewer's table/modal layout. **Tailwind CSS** and its Vite plugin (`tailwindcss`, `@tailwindcss/vite`) are present as devDependencies in `package.json`, but a source check shows the Tailwind Vite plugin is not registered in `vite.config.js` (which only registers `@vitejs/plugin-react`) and `index.css` contains no `@tailwind`/`@import "tailwindcss"` directive — Tailwind is therefore installed but not actually wired into the current build, and no Tailwind utility classes appear in the component tree. This is documented here precisely because the task required analysis to reflect actual implementation rather than what a `package.json` alone might suggest.

## 2.12 Charting Library

**Recharts 3.8.1** is listed as a frontend dependency, but a repository-wide search (`grep -r "recharts" src/`) finds no import of it anywherein the active `src/` tree. It is therefore an installed-but-unused dependency at the current stage of the project (see Chapter 14 for a discussion of where it would be a natural fit — e.g., the order-trend data already computed by `DashboardService.buildOrderTrend`).

## 2.13 Package Managers

The backend uses **Apache Maven** (via the Maven Wrapper, `mvnw`/`mvnw.cmd`, pinned to Maven 3.9.16 in `.mvn/wrapper/maven-wrapper.properties`) for dependency management and builds. The frontend uses **npm** (`package.json` / `package-lock.json`).

## 2.14 Development Tools and Version Control

The project uses **Lombok** (`@Data`, `@Builder`, `@RequiredArgsConstructor`, `@Slf4j`, etc.) throughout the backend to eliminate boilerplate getters/setters/constructors/logger declarations — visible in essentially every model, service, and controller class. **ESLint** (with `eslint-plugin-react-hooks` and `eslint-plugin-react-refresh`) is configured for the frontend (`eslint.config.js`) to catch common React mistakes (e.g., missing hook dependencies). The project is version-controlled with **Git** (`.gitignore`, `.gitattributes` present at the backend root), with secrets such as `MONGODB_URI` and `APP_JWT_SECRET` deliberately excluded from source control via `.env`/`.gitignore` and documented via a checked-in `.env.example` template.

## 2.15 Testing Dependencies

`spring-boot-starter-test` and `spring-security-test` are present as test-scoped Maven dependencies, providing JUnit 5, Mockito, and Spring Security test support, respectively. See Chapter 10 for the current state of automated testing in the project.

**Table 2.1 — Backend Technology Stack Summary**

| Category | Technology | Version |
|---|---|---|
| Language | Java | 21 |
| Framework | Spring Boot | 4.1.0 |
| Web layer | Spring Web MVC | (Spring Boot managed) |
| Security | Spring Security | (Spring Boot managed) |
| Authentication | JJWT (`jjwt-api`/`impl`/`jackson`) | 0.12.6 |
| Database | MongoDB Atlas (Spring Data MongoDB) | (Spring Boot managed) |
| Validation | Jakarta Bean Validation | (Spring Boot managed) |
| Boilerplate reduction | Lombok | (Spring Boot managed) |
| Build tool | Apache Maven (via Maven Wrapper) | 3.9.16 |
| Hot reload | Spring Boot DevTools | (Spring Boot managed) |

**Table 2.2 — Frontend Technology Stack Summary**

| Category | Technology | Version |
|---|---|---|
| Library | React / React DOM | 19.2.6 |
| Build tool | Vite | 8.0.12 |
| Routing | React Router DOM | 7.17.0 |
| HTTP client | Axios | 1.18.0 |
| Charting (installed, unused) | Recharts | 3.8.1 |
| CSS framework (installed, not wired) | Tailwind CSS | 4.3.1 |
| Linting | ESLint (+ React Hooks/Refresh plugins) | 10.3.0 |

---

# CHAPTER 3: SYSTEM REQUIREMENTS

## 3.1 Hardware Requirements

**Table 3.1 — Minimum Hardware Requirements**

| Component | Minimum Specification |
|---|---|
| Processor | Dual-core CPU, 2.0 GHz |
| RAM | 8 GB |
| Storage | 5 GB free disk space |
| Network | Broadband internet connection (required for MongoDB Atlas and SAP CPI trial tenant access) |

**Table 3.2 — Recommended Hardware Requirements**

| Component | Recommended Specification |
|---|---|
| Processor | Quad-core CPU, 2.5 GHz or higher |
| RAM | 16 GB |
| Storage | 20 GB free disk space (SSD) |
| Network | Stable broadband connection with low latency to MongoDB Atlas and the SAP CPI region |

## 3.2 Software Requirements

**Table 3.3 — Software Requirements**

| Category | Requirement |
|---|---|
| Operating System | Windows 10/11, macOS, or Linux (development is OS-independent; the project was authored/run on macOS in this instance) |
| Backend runtime | Java Development Kit (JDK) 21 |
| Backend build tool | Apache Maven 3.9.x (bundled via the Maven Wrapper — no separate install required) |
| Frontend runtime | Node.js (compatible with Vite 8 / React 19, Node 18+ recommended) and npm |
| Database | MongoDB Atlas cluster (cloud-hosted; no local MongoDB install required) |
| Integration platform | SAP Cloud Platform Integration (SAP CPI) tenant with configured iFlows |
| Tunneling (development only) | ngrok, to expose the local backend to SAP CPI's iFlow 2 approval callback during development |
| IDE / Editor | Any Java + JavaScript-capable IDE (e.g., IntelliJ IDEA, VS Code) |
| Version control | Git |

## 3.3 Development Environment

The backend was developed against Java 21 and Spring Boot 4.1.0, run locally via `./mvnw spring-boot:run`, with configuration supplied through a local `.env` file (gitignored) exporting `MONGODB_URI`, `APP_JWT_SECRET`, and the various `CPI_*` connection properties consumed by `application.properties`. The frontend was developed with Vite's dev server (`npm run dev`), proxying API calls directly to the backend's `http://localhost:8080/api` base URL. SAP CPI runs as a cloud-hosted trial tenant; because the local backend is not publicly reachable from CPI by default, the developer exposed it via an ngrok tunnel specifically for the iFlow 2 approval-callback receiver during development and testing.

---

# CHAPTER 4: SYSTEM ANALYSIS

## 4.1 Functional Requirements

The system's functional requirements, as reflected directly in the implemented controllers and services, include: user registration and login with role assignment (`AuthController`/`AuthService`); creation, retrieval (with pagination, filtering, search, and sorting), and lifecycle transition of purchase orders (`OrderController`/`OrderService`); dispatch of outbound purchase orders to SAP CPI in a per-order-selectable format (`CpiClient.sendPo`); acceptance of inbound purchase orders, stock offers, approvals, and inventory updates from SAP CPI (`CpiInboundController`/`CpiInboundService`); shipment creation and status tracking (`ShipmentController`/`ShipmentService`); inventory item management with stock receive/sell operations and low-stock alerting (`InventoryController`/`InventoryService`/`InventoryAlertService`); supplier directory management (`SupplierController`/`SupplierService`); system alert generation, resolution, and role-targeted delivery (`AlertController`/`AlertService`); user administration including lock/unlock and role changes (`UserController`/`UserService`); role-specific dashboard statistics (`DashboardController`, `ProcurementDashboardController`, `VendorDashboardController`, `AdminController`); and a full integration-message audit trail with search, correlation tracing, and status filtering (`IntegrationLogController`, `LogsController`).

## 4.2 Non-Functional Requirements

Non-functional requirements addressed in the implementation include: **security** (stateless JWT authentication, BCrypt password hashing, account lockout after repeated failed logins in `AuthService`, per-endpoint role authorization in `SecurityConfig`); **resilience** (retry-with-linear-backoff on transient CPI failures, an open-PO cap to protect against a message flood, idempotency-key deduplication on inbound POs, in `CpiClient` and `CpiInboundService`); **auditability** (every CPI-bound message, success or failure, is recorded to the `IntegrationLog` collection via `CpiAuditService`); **multi-tenancy/isolation** (every `Order`, `Alert`, `Inventory`, `Shipment`, and `Supplier` document carries a `SystemId` discriminator, and reads are scoped through the `Tenant` helper based on the authenticated user's JWT claim); and **usability** (paginated list views across Orders, Shipments, Inventory, Alerts, Suppliers, Users, and Integration Logs, using a shared `Pagination` component).

## 4.3 User Requirements

Four user roles are modelled (`User.Role`: `ADMIN`, `MANAGER`, `VENDOR`, `PROCUREMENT`), each requiring different capabilities: a **Vendor** user needs to view incoming sales orders, check stock, notify buyers of availability, confirm supply, and track outbound shipments; a **Procurement** user needs to raise/track purchase orders to a vendor, manage suppliers, and track inbound shipments; an **Admin**/**Manager** user needs a cross-tenant, cross-role oversight view (order/inventory analytics, user management, and the full integration-log trail) without being restricted to a single tenant's data the way Vendor/Procurement users are.

## 4.4 Feasibility Study

### 4.4.1 Technical Feasibility

The project is technically feasible using freely available and widely documented technologies: Spring Boot and MongoDB Atlas both offer generous free tiers suitable for a project/internship-scale deployment, SAP CPI offers a trial tenant sufficient for building and testing iFlows, and the frontend stack (React, Vite) requires no paid tooling. All technologies used are mature, well-documented, and were confirmed compatible with each other during development (e.g., Spring Boot 4.1.0 with Java 21, React 19 with React Router 7).

### 4.4.2 Economic Feasibility

The system was built entirely on free-tier cloud services (MongoDB Atlas free cluster, SAP CPI trial tenant, ngrok free tier for development tunneling) and open-source libraries, so the project incurred no direct monetary cost during development. A production deployment would require a paid SAP CPI tenant and a production-grade MongoDB Atlas tier, but the architecture does not mandate any proprietary or licensed component beyond SAP CPI itself, which the scenario specifically requires.

### 4.4.3 Operational Feasibility

The system is operable by non-technical end users through the React frontend without requiring any command-line interaction; the only operationally technical component is the SAP CPI iFlow configuration and the developer-side ngrok tunnel, both of which are integration-layer concerns rather than end-user concerns. Administrative controls for the System 2 simulator (`/api/simulator/start|stop|fire`) allow an operator to pause or manually trigger simulated counterparty activity without redeploying the application.

---

# CHAPTER 5: SYSTEM DESIGN

## 5.1 Overall Architecture

The system is composed of three cooperating parts: the React frontend (serving System 1's human users), the Spring Boot backend (a single deployable unit that serves System 1's REST API *and* runs the System 2 simulator and all `@Scheduled` background jobs in-process), and SAP CPI (the external integration layer that every cross-system message passes through). The backend never calls System 2 "directly" — even though System 2's logic executes inside the same JVM, `System2ProcurementOrderGenerator` dispatches its generated purchase orders through the exact same `CpiClient.sendPo` outbound path and the exact same SAP CPI iFlow 1 endpoint that a real, physically separate System 2 backend would use. This is an important architectural property: the simulator proves the integration contract by using it, rather than taking a shortcut.

**[Insert Diagram: Figure 5.1 — Overall System Architecture, showing the React frontend, Spring Boot backend (with the embedded System 2 simulator and scheduled jobs), MongoDB Atlas, and SAP CPI with its five iFlows, and the direction of traffic between them]**

## 5.2 Frontend Architecture

The frontend is a single Vite-built React SPA (`src/main.jsx` → `src/App.jsx`) with no server-side rendering. `App.jsx` owns the top-level `BrowserRouter`, an inline `Layout` component (rendering a collapsible role-colored sidebar plus the page content), a `PrivateRoute` guard component that checks `token`/`role`/`systemId` from `AuthContext`, and a class-based `ErrorBoundary` wrapping the entire route tree so that a render-time crash in any single page shows a recoverable error screen rather than a blank white page. Below `App.jsx`, each route renders one top-level page component from `src/pages/`; pages call the backend directly through the shared Axios instance (`src/api/axios.js`) rather than through a separate service/repository abstraction layer for most calls (a `src/api/services.js` module also exists with wrapper functions for a subset of endpoints, but the majority of pages, per the project's own checkpoint notes, were migrated to call the shared `axios` instance directly so that the JWT interceptor is guaranteed to apply).

**[Insert Diagram: Figure 5.2 — Frontend Component and Routing Architecture, showing App.jsx, AuthContext, PrivateRoute, and the page components reachable per role]**

## 5.3 Backend Architecture

The backend follows a conventional layered architecture: **Controller** classes (21 `@RestController`s) handle HTTP concerns (path/query binding, `Authentication` extraction, status codes) and delegate all business logic to **Service** classes (21 `@Service` beans); services depend on **Repository** interfaces (8 `MongoRepository` interfaces) for persistence and on other services (e.g., `OrderService` depends on `CpiClient`, `CpiAuditService`, `AlertService`, and `System1VendorStockOfferService`) for cross-cutting concerns. A small set of **Scheduler** classes (6 `@Scheduled` components) run background jobs independent of any HTTP request: order lifecycle advancement, stuck-order recovery, inventory alert scanning, and the System 2 simulator itself. Cross-cutting HTTP-layer concerns are centralized in `SecurityConfig` (authentication/authorization rules and CORS), `JwtAuthFilter` (token verification), and `GlobalExceptionHandler` (turning validation and business-rule exceptions into clean 4xx responses instead of raw 500s).

**[Insert Diagram: Figure 5.3 — Backend Layered Architecture, showing the Controller → Service → Repository chain plus the cross-cutting Security/Exception/Scheduler layers]**

## 5.4 Folder Structure

Both the backend and frontend use a flat, single-package/single-directory-per-concern layout rather than deep nested feature folders. The backend places all 111 Java source files directly under `src/main/java/com/supplychain/integration_hub/` (no sub-packages), relying on consistent file-naming suffixes (`*Controller`, `*Service`, `*Repository`, `*Scheduler`) rather than package boundaries to communicate a class's role. The frontend places all page-level components under `src/pages/`, the two genuinely shared components (`Pagination.jsx`, and an unused `Layout.jsx`/`Sidebar.jsx` pair — see Chapter 12) under `src/components/`, the single global context under `src/context/`, and the API layer under `src/api/`.

**[Insert Screenshot: Backend Folder Structure — src/main/java/com/supplychain/integration_hub listing]**
**[Insert Screenshot: Frontend Folder Structure — src/ listing]**

## 5.5 Database Design

See Chapter 7 for the full database design. In summary, the system uses seven MongoDB collections (`orders`, `users`, `suppliers`, `shipments`, `inventory`, `alerts`, `integration_logs`), each mapped from a corresponding `@Document`-annotated Lombok model class, accessed through Spring Data MongoDB repositories and, for the more complex order-query use case, dynamic `MongoTemplate` criteria queries.

## 5.6 API Architecture

The API is organized by resource (`/api/orders`, `/api/shipments`, `/api/inventory`, `/api/suppliers`, `/api/alerts`, `/api/users`, `/api/dashboard/*`, `/api/admin/*`, `/api/logs`, `/api/integration-logs`) plus a separate, differently-secured integration surface (`/api/cpi/*`) for SAP CPI traffic. See Chapter 9 for full endpoint documentation.

## 5.7 Authentication Flow

A user submits credentials to `POST /api/auth/login`; `AuthService` looks up the user by email, checks `isLocked`/`isActive`, verifies the BCrypt password hash, resets or increments `failedLoginAttempts` (locking the account after 5 consecutive failures), and — on success — calls `JwtUtil.generateToken` to produce a signed JWT embedding the user's role, `entityId`, and `systemId` as claims, returned to the frontend inside an `AuthResponse`. The frontend's `AuthContext.login()` persists the token and claims to `localStorage` and to React state. On every subsequent request, the Axios request interceptor attaches `Authorization: Bearer <token>`; on the backend, `JwtAuthFilter` verifies the signature and expiry, reconstructs a `UserPrincipal`, and places it in the Spring Security context so that `SecurityConfig`'s `hasAnyAuthority(...)` rules and each controller's `Authentication` parameter can make role- and tenant-aware decisions.

**[Insert Diagram: Figure 5.4 — Authentication and JWT Flow, showing Login → AuthService → JwtUtil → AuthContext → Axios interceptor → JwtAuthFilter → SecurityContext]**

## 5.8 Application Flow — Order Lifecycle

`Order` documents move through two parallel status vocabularies. The internal `OrderStatus` enum (`REQUESTED → STOCK_NOTIFIED/BUYER_APPROVED → CONFIRMED → PROCESSING → IN_TRANSIT → DELIVERED`, with `REJECTED`/`CANCELLED`/`VENDOR_REJECTED`/`BUYER_REJECTED` off-ramps) drives the fulfilment lifecycle and is advanced partly by explicit user action (vendor stock notification, buyer response) and partly by scheduled jobs (`System1VendorShippingLifecycleScheduler`, `System1ProcurementInboundShippingScheduler`) that move `CONFIRMED` orders through `PROCESSING → IN_TRANSIT → DELIVERED` on fixed timers (configurable via `lifecycle.*-seconds` properties). The cross-system `PoStatus` enum (`DRAFT → SENT → RECEIVED → APPROVED|REJECTED → FULFILLED → SHIPPED`) is the contract both System 1 and System 2 obey when a PO crosses the CPI bridge, and is set independently of `OrderStatus` by `CpiClient`/`OrderService`/`CpiInboundService`.

**[Insert Diagram: Figure 5.5 — Purchase Order Lifecycle State Diagram, showing OrderStatus transitions and which actor/scheduler triggers each]**
**[Insert Diagram: Figure 5.6 — Cross-System PO Contract Lifecycle, showing PoStatus transitions across the CPI bridge]**

## 5.9 Component Interaction and Data Flow — the CPI Bridge

When System 2's simulator (or a real System1 Procurement user) raises a PO, the owning service builds a canonical payload, serializes it into the order's chosen wire format (JSON/XML/CSV), and calls `CpiClient.sendPo`, which prepends the plaintext `#meta source=... target=... format=...` control line, authenticates with a cached OAuth2 client-credentials Bearer token, and POSTs the combined body to CPI's iFlow 1 HTTP endpoint with retry-with-backoff on transient failures. CPI's Groovy script strips the control line, routes the payload to the correct format-conversion sub-flow (CSV→XML or JSON→XML), applies message mapping, and forwards the canonical PO to the destination backend's `/api/cpi/inbound/po` endpoint, gated by an `X-CPI-Secret` header rather than a user JWT. `CpiInboundService.receivePo` deduplicates on `idempotencyKey`, enforces an open-PO cap, persists the inbound order (and, for the System2→System1 lane, an associated mirror record), and raises an `ORDER_RECEIVED` alert. Every step of this journey — outbound send, inbound receipt, and later the approval callback via iFlow 2 — is recorded to the `integration_logs` collection by `CpiAuditService`.

**[Insert Diagram: Figure 5.7 — SAP CPI iFlow 1 Outbound PO Message Flow, from CpiClient.sendPo through CPI's Groovy/Router/Converter/Mapping steps to the destination backend's inbound endpoint]**
**[Insert Diagram: Figure 5.8 — SAP CPI iFlow 2 Approval Callback Flow, from the counterparty's approval decision through CPI back to OrderService/CpiInboundService]**

## 5.10 Tenant Isolation / Mirror-Order Data Flow

Because System 1 and System 2 share one backend and one database, several services (`OrderService.upsertSystem2VendorMirror`, `upsertSystem2ProcurementMirror`, and `CpiInboundService.syncSystem2Mirror`) maintain **mirror** `Order` documents: whenever a System 1 order's status changes, a same-`orderId`/`correlationId` counterpart document tagged `systemId=SYSTEM2` (with direction flipped) is created or updated, so that a query scoped to System 2's tenant sees the same order from System 2's point of view (e.g., an `OUTBOUND` order for System 1 appears as an `INBOUND` order for System 2), without System 2's queries ever touching System 1's actual documents directly.

**[Insert Diagram: Figure 5.9 — Tenant Isolation / Mirror-Order Data Flow, showing how one business transaction produces a System1-tagged document and a mirrored System2-tagged document]**

---

# CHAPTER 6: IMPLEMENTATION

This chapter analyses the implementation module by module, based directly on the source files in `src/main/java/com/supplychain/integration_hub/` (backend) and `src/pages/`, `src/context/`, `src/api/`, `src/components/` (frontend).

## 6.1 Authentication and Authorization Module

**Purpose:** Authenticate users, issue and validate JWTs, and enforce role- and tenant-based access control on every API endpoint.

**Files involved:** `AuthController`, `AuthService`, `AuthResponse`, `LoginRequest`, `RegisterRequest`, `JwtUtil`, `JwtAuthFilter`, `SecurityConfig`, `UserPrincipal`, `Tenant`, `User`, `UserRepository`.

**Workflow and logic:** `AuthController` exposes `POST /api/auth/login` and `POST /api/auth/register`, both delegating to `AuthService`. `login()` looks up the user by email (`UserRepository.findByEmail`), rejects locked or inactive accounts, and compares the submitted password against the stored BCrypt hash via Spring Security's `PasswordEncoder`. On a wrong password, `failedLoginAttempts` is incremented and persisted; on the fifth consecutive failure the account is flipped to `isLocked=true` with a `lockedAt` timestamp — a simple brute-force mitigation implemented entirely in application code rather than relying on an external identity provider. On success, `failedLoginAttempts` resets to zero, `lastLoginAt` is stamped, and `JwtUtil.generateToken` produces a token whose claims include `role`, `entityId`, and `systemId` in addition to the standard `subject` (email) and `expiration`. `register()` performs a duplicate-email check, hashes the incoming password, defaults `systemId` to `SYSTEM1` if not supplied or invalid, generates a readable `userId` (`USR-XXXXXXXX`), and returns a token identically to `login()`.

Authorization is centralized, not scattered: `SecurityConfig.filterChain` defines one ordered list of `requestMatchers(...).hasAnyAuthority(...)` rules covering every resource prefix in the API (see the rule table in §6.1.1), with CPI-facing paths (`/api/cpi/**`) explicitly `permitAll()` because they are secured by a different mechanism (the `X-CPI-Secret` header, checked deeper in the call stack, not by Spring Security itself). `JwtAuthFilter` is a `OncePerRequestFilter` registered before `UsernamePasswordAuthenticationFilter`; its `shouldNotFilter` override skips JWT processing entirely for `/api/cpi/inbound/**` so that a stray `Authorization` header forwarded by CPI's receiver adapter can never cause an unintended 401. `Tenant.of(Authentication)` is the single place that maps a `UserPrincipal`'s `systemId` claim to the `SystemId` enum (defaulting safely to `SYSTEM1` if absent or unparseable), and is called from nearly every service that needs to scope a query to the caller's tenant.

**Validation and error handling:** Bean validation on `LoginRequest`/`RegisterRequest` (not shown to raise errors here since both are simple DTOs) combines with `GlobalExceptionHandler`'s `MethodArgumentNotValidException` handler to return structured 400 responses; authentication-specific failures (locked account, invalid credentials) are raised as `RuntimeException` from `AuthService` and are currently returned via the controller's own catch/response mapping in `AuthController` rather than a dedicated exception type.

### 6.1.1 Authorization Rule Summary

| Path prefix | Allowed roles |
|---|---|
| `/api/auth/**` | Public (no authentication) |
| `/api/cpi/**` | Public (secured separately by `X-CPI-Secret`) |
| `/api/dashboard/vendor/**` | VENDOR, ADMIN, MANAGER |
| `/api/dashboard/procurement/**` | PROCUREMENT, ADMIN, MANAGER |
| `/api/dashboard/admin/**`, `/api/admin/**`, `/api/users/**` | ADMIN, MANAGER |
| `/api/vendor/shipments/**`, `/api/vendor/orders/**` | VENDOR |
| `/api/procurement/shipments/**` | PROCUREMENT |
| `/api/procurement/system2-vendor-inventory`, `/api/procurement/vendor-inventory/**` | PROCUREMENT, ADMIN, MANAGER |
| `/api/orders/**`, `/api/shipments/**`, `/api/inventory/**`, `/api/alerts/**` | ADMIN, MANAGER, VENDOR, PROCUREMENT |
| `/api/suppliers/**` | ADMIN, MANAGER, PROCUREMENT |
| `/api/logs/**`, `/api/simulator/**` | ADMIN, MANAGER |

## 6.2 Order Management Module

**Purpose:** The core business module — models the purchase order entity and its dual lifecycle (internal fulfilment vs. cross-system PO contract), and implements the multi-lane negotiation logic between vendor and procurement roles across both tenants.

**Files involved:** `Order`, `OrderItem`, `OrderStatus`, `PoStatus`, `Direction`, `OrderController`, `OrderService`, `OrderRepository`, `CreateOrderRequest`, `PagedResponse`.

**Important classes and functions:** `OrderService` is the largest service in the codebase. Its read-side methods (`getAllOrders`, `getActiveOrders`, `getPastOrders`, `getPendingApprovals`, `getBuyerDecisions` — each with both a `List<Order>` and a paginated `Page<Order>`/`Map<String,Object>` overload) all branch on the caller's role and tenant to decide which `direction` (`INBOUND`/`OUTBOUND`) and which `OrderStatus` set is relevant; the fully paginated `queryOrders` method builds a dynamic MongoDB `Criteria` query (tenant, role-derived direction, tab-derived status set, free-text search across `orderId`/`counterpartyName`/`status`, and a `Sort` derived from a `sort` query parameter) using `MongoTemplate` rather than a derived repository method, because the combination of filters is too dynamic for Spring Data's method-name query derivation.

The write side implements two independent negotiation lanes. **Lane A** (System2 Procurement → System1 Vendor, the "human-operated vendor" lane) flows through `notifyStockAvailability` (vendor states available quantity), `respondToStockCheck` (buyer accepts/declines), and `sendVendorStockOffer`/`rejectByVendor` (delegated to `System1VendorStockOfferService`, which sends the vendor's decision to the counterparty via CPI iFlow 3). **Lane B** (System1 Procurement → System2 Vendor, the "robot vendor" lane) is driven by `createOrder` auto-dispatching new inbound procurement orders straight to CPI (`sendOrderToCpi`) without a manual "send" step, and by `applySystem2VendorDecision`, invoked by `System2VendorDecisionWatcher` when the robot vendor's accept/reject decision needs to be applied.

`sendOrderToCpi` is the canonical outbound dispatch method: it is idempotency-guarded (skips re-dispatch if `poStatus` is already `SENT`/`APPROVED`/`REJECTED`), derives `source`/`target` wire names from the order's own `SystemId` via the `Tenant` helper (never hardcoding which system is sending), stamps a CPI-compatible `correlationId` (`"<source>-<poNumber>"`, matching what CPI's `EnrichPO` step derives independently so the later approval callback reconciles correctly), builds the wire payload in the order's selected format via the private `buildPayload`/`poFields`/`json`/`xml`/`csv` helper methods, and calls `CpiClient.sendPo`. On failure, the order is marked `PoStatus.FAILED` (retryable) rather than left in an ambiguous state, and the UI remains usable regardless of the CPI outcome. `emitInventoryUpdate`, called from `confirmSupply` (Lane A fulfilment) and `advanceToDelivered` (Lane B fulfilment), sends one `sendInventoryUpdate` CPI iFlow 4 call per non-empty line item so that physical stock movements are recorded through the same integration bridge as everything else, rather than being applied directly to the local `Inventory` collection.

**Business logic highlights:** the module carefully distinguishes *who* cancelled an order (`cancelledBy` values like `SYSTEM1_VENDOR`, `SYSTEM2_PROCUREMENT`, `SYSTEM_AUTO`) so that alerting and audit trails can attribute responsibility correctly; `confirmSupply` enforces a precondition (`order.getStatus() == BUYER_APPROVED && "YES".equalsIgnoreCase(buyerResponse)`) and throws `IllegalStateException` (mapped to a 400 by `GlobalExceptionHandler`) if violated, preventing a vendor from confirming supply out of sequence.

**[Insert Screenshot: Orders Page — list view with tabs and filters]**
**[Insert Screenshot: Orders Page — create-order form]**

## 6.3 CPI Integration Module

**Purpose:** The outbound and inbound bridge to SAP CPI — the architectural centrepiece of the project.

**Files involved:** `CpiClient`, `CpiInboundController`, `CpiInboundService`, `CpiAuditService`, `CpiInventoryAlertClient`, `CpiTestController`, `InboundPoRequest`, `ApprovalCallbackRequest`, `StockOfferRequest`, `InventoryUpdateRequest`, `IntegrationLog`, `IntegrationLogRepository`, `IntegrationLogService`.

**Outbound (`CpiClient`):** implements five distinct CPI-facing operations, all sharing the same OAuth2 client-credentials Bearer-token cache (`getToken()`, refreshed 30 seconds before expiry) and the same manual retry-with-linear-backoff loop pattern (`attempt` counter, `maxRetries`, `retryBackoffMs * attempt` wait, distinguishing retryable `HttpServerErrorException`/`ResourceAccessException` from non-retryable `RuntimeException`): `sendPo` (iFlow 1, outbound PO, `#meta` control line + format-selected body), `sendApproval` (iFlow 2, approval callback), `sendStockOffer`/`sendStockOfferViaIflow3` (iFlow 3, vendor stock decision), and `sendInventoryUpdate` (iFlow 4, stock movement). Every call records its outcome to `CpiAuditService`, whether it succeeds, retries, or ultimately fails.

**Inbound (`CpiInboundService`):** implements the receiving side for the same set of message types, arriving via `CpiInboundController`'s four `POST` endpoints (`/po`, `/stock-offer`, `/inventory-update`, `/approval`). `receivePo` is the most involved: it first checks for an already-existing System1-vendor mirror by `correlationId` (protecting against CPI redelivery), then deduplicates on `idempotencyKey`, then enforces an open-PO cap (`cpi.max-open-pos`, throwing a custom `OpenPoCapExceededException` that presumably maps to a 429/400), before finally persisting the new `Order` and — for the System2→System1 lane specifically — deliberately *not* creating a mirror (the mirror already exists on the System1 side by construction), while for the other lane it calls `syncSystem2Mirror` to keep the System2-side mirror current. `receiveStockOffer` and `receiveApproval` similarly locate the correct order by correlation ID, apply idempotent status transitions, and raise role-targeted alerts.

**Audit (`CpiAuditService`):** a single `record(...)` method (with a convenience overload) writes one `IntegrationLog` document per CPI message, truncating oversized payloads (`MAX_PAYLOAD = 2000` characters) to bound document size, and is explicitly designed to never throw — audit failures are caught and logged as warnings so that a logging bug can never take down the actual business transaction it is trying to observe.

**[Insert Screenshot: SAP CPI iFlow 1 design — Web IDE / Integration Suite canvas]**

## 6.4 Inventory Management Module

**Purpose:** Tracks stock levels per tenant, supports receive/sell stock movements, and raises low-stock alerts.

**Files involved:** `InventoryItem`, `Inventory` (a richer, currently-secondary model with warehouse/batch/expiry fields not yet fully wired into the active flows), `InventoryController`, `InventoryLookupController`, `InventoryService`, `InventoryLookupService`, `InventoryRepository`, `InventoryAlert`, `InventoryAlertRepository`, `InventoryAlertService`, `InventoryAlertScheduler`, `CreateInventoryRequest`, `InventoryStockRequest`.

`InventoryController` exposes CRUD plus `receive`/`sell` stock-movement endpoints and a low-stock query; `InventoryLookupController`'s `/context` endpoint supports the Orders page's per-line-item availability check (matching an order line's `description` against `InventoryItem.itemName`, case-insensitively). `InventoryAlertScheduler` runs a scheduled scan (`alert.inventory.scan-interval-ms`) that compares `quantity` against `thresholdQuantity` per item and raises `LOW_STOCK` alerts (deduplicated via `InventoryAlertRepository.findFirstBySystemIdAndSkuAndStatus`, so the same low-stock condition does not spam a new alert on every scan) that are also surfaced by email via the SAP CPI iFlow 5 endpoint (`cpi.iflow5-alert-email-url`).

## 6.5 Shipment Management Module

**Purpose:** Tracks the physical shipment lifecycle associated with an order.

**Files involved:** `Shipment`, `ShipmentController`, `ShipmentService`, `ShipmentRepository`, `CreateShipmentRequest`, `ProcurementShipmentController`/`Service`/`View`, `VendorShipmentController`/`Service`/`View`, `System1VendorShippingLifecycleScheduler`, `System1ProcurementInboundShippingScheduler`, `System1VendorOrderRecoveryScheduler`, `System1ProcurementOrderRecoveryScheduler`, `InventoryDeliverySyncScheduler`, `RecoveryMetrics`.

Shipment status (`PENDING → IN_TRANSIT → OUT_FOR_DELIVERY → DELIVERED`/`CANCELLED`) is advanced primarily by the two lifecycle schedulers on fixed 30-second delays, each scoped to one lane (vendor-side vs. procurement-side), plus two "recovery" schedulers (45-second delay) whose purpose, per their own naming and comments, is to catch orders that should have advanced but did not (e.g., due to a missed scheduler tick or a transient failure), advancing them and logging a `RecoveryMetrics` counter so operational health is observable.

## 6.6 Supplier Management Module

**Purpose:** Maintains the per-tenant supplier directory used by Procurement.

**Files involved:** `Supplier`, `SupplierController`, `SupplierService`, `SupplierRepository`, `CreateSupplierRequest`, `UpdateSupplierRequest`.

`SupplierController` exposes standard CRUD (`create`, `getAll`, `getById`, `update`, `updateStatus`, `delete`), all tenant-scoped via `Authentication`; `SupplierRepository` additionally exposes `existsByEmail`/`existsBySupplierCode` for uniqueness checks at creation time.

## 6.7 Alerting Module

**Purpose:** A cross-cutting notification mechanism used by nearly every other module to surface role-targeted, human-readable events.

**Files involved:** `Alert`, `AlertController`, `AlertService`, `AlertRepository`, `CreateAlertRequest`.

`AlertService.createOrderAlert` is called from `OrderService`, `CpiInboundService`, and others whenever an order-related event occurs (received, confirmed, rejected, shipped, delivered, cancelled), tagging each alert with a `targetRole` (`VENDOR`/`PROCUREMENT`/`ADMIN`/`ALL`) and a `severity` resolved from the event type, and always stamping the current tenant's `SystemId` so a Vendor or Procurement user only ever sees alerts relevant to their own tenant and role.

## 6.8 Dashboard and Admin Module

**Purpose:** Aggregated, read-only statistics for each role.

**Files involved:** `DashboardController`, `DashboardService`, `DashboardStats`, `VendorDashboardController`, `ProcurementDashboardController`, `AdminController`, `AdminService`, `DecisionCounter`, `IntegrationLogStats`.

`DashboardService.getStats(SystemId)` aggregates counts and an order-trend time series (`buildOrderTrend`) from the `Order`, `Shipment`, `Alert`, `Supplier`, and `Inventory` repositories for a given tenant. `AdminService` provides the cross-tenant/cross-role admin view (`getSummary`, `getOrders`, `getInventoryAnalysis`), including a low-stock detector, a "top items by quantity" ranking, and integration with `RecoveryMetrics` and the `System2ProcurementOrderGenerator` (surfacing the simulator's open-PO-cap health indicator, per `System2ProcurementOrderGenerator.isOpenPoCapReached()`).

**[Insert Screenshot: Admin Dashboard — summary and analytics view]**

## 6.9 System 2 Simulator Module

**Purpose:** Autonomously generates realistic, continuous counterparty traffic so the integration can be demonstrated without a second human-operated system.

**Files involved:** `System2ProcurementOrderGenerator`, `System2ProcurementDecisionService`, `System2ProcurementDecisionWatcher`, `System2VendorDecisionService`, `System2VendorDecisionWatcher`, `System1ProcurementOrderService`, `System1VendorStockOfferService`, `SimulatorController`.

`System2ProcurementOrderGenerator.tick()` (a `@Scheduled` method, interval configurable via `simulator.interval-ms`) checks System1's open-PO cap, randomly selects 1–3 line items from the System1 vendor's inventory master, randomly picks a wire format (`FORMATS = {"json","xml","csv"}`), builds and persists a new `Order`, dispatches it through `CpiClient.sendPo` exactly as a human-operated System2 Procurement user's request would be, and creates the corresponding System1 vendor mirror. `SimulatorController` exposes `/api/simulator/status|start|stop|fire` so an Admin/Manager can pause the simulator or manually trigger one iteration on demand — useful both for controlled demos and for isolating the simulator's behaviour during debugging.

## 6.10 Frontend Implementation

**Routing and layout:** covered in §5.2; `App.jsx` centralizes both the route table and the per-role navigation menu (`navConfig`) in one file, filtering the Inventory link out of the sidebar for non-`SYSTEM1` tenants (`items.filter(item => item.to !== "/inventory" || systemId === "SYSTEM1")`), matching the backend's `/inventory` route restriction (`allowedSystems={["SYSTEM1"]}` on the corresponding `PrivateRoute`).

**Authentication state (`AuthContext.jsx`):** a minimal Context provider persisting `token`/`role`/`name`/`entityId`/`systemId` to `localStorage`, with a defensive `getInitialSystemId()` helper that falls back to manually base64-decoding the JWT payload (handling both standard and URL-safe base64 alphabets, and padding correctly) if `systemId` was not separately cached — protecting against a stale/missing `localStorage` entry from an older session.

**API layer (`src/api/axios.js`, `src/api/services.js`):** `axios.js` creates one shared Axios instance with a request interceptor that injects the bearer token; `services.js` defines named wrapper functions for a subset of endpoints (some of which, such as `loginSupplier`/`registerSupplier`, reference endpoints that do not currently exist on the backend and appear to be from an earlier or planned iteration of the API). In practice, most pages (`Orders.jsx`, `Dashboard.jsx`, `Shipments.jsx`, `Inventory.jsx`, and others) import the shared `axios`/`api` instance directly and call `axios.get/post/put/patch(...)` with an inline endpoint path, rather than routing through `services.js`, so that every call reliably carries the JWT.

**Representative page — `Login.jsx`:** a single component implementing both login and registration (toggled by local `mode` state) across three role tabs (Vendor/Procurement/Admin — the Admin tab accepts both `ADMIN` and `MANAGER` on login, matching the backend's `expectedTab` reconciliation logic), performing client-side password-confirmation validation on registration and surfacing backend error messages inline.

**Representative page — `Orders.jsx`** (the largest frontend file, 1659 lines): combines a paginated, server-filtered order list (calling `GET /orders/paged` with `tab`/`status`/`q`/`sort`/`page`/`size` query parameters, debouncing the search input before firing a request), an order-creation form (fetching the System2 vendor inventory for a line-item picker via `GET /procurement/system2-vendor-inventory`), and a large set of inline per-order action handlers (notify-stock, respond-to-stock, confirm-supply, cancel, reject, vendor-confirm-supply) each calling the matching backend endpoint and refreshing the affected order in local state.

**Representative page — `Dashboard.jsx`:** the Admin/Manager dashboard, loading `/admin/summary` and `/admin/inventory-analysis` in parallel (`Promise.all`) on mount, with four independently paginated sub-tables (inventory, stock, orders, alerts) each using the shared `Pagination` component.

**[Insert Screenshot: Login Page]**
**[Insert Screenshot: Vendor Dashboard]**
**[Insert Screenshot: Procurement Dashboard]**

---

# CHAPTER 7: DATABASE DESIGN

The system uses **MongoDB** (a document database) rather than a relational schema, accessed through Spring Data MongoDB. There are no foreign-key constraints or joins in the traditional relational sense; relationships between collections are represented by storing a referencing field (e.g., `Order.correlationId`, `IntegrationLog.orderId`) and resolving it with a second query at the application layer.

## 7.1 Collections

**Table 7.1 — MongoDB Collections Summary**

| Collection | Backing Class | Purpose |
|---|---|---|
| `orders` | `Order` | Purchase orders, both directions, both tenants, including mirror records |
| `users` | `User` | Application user accounts and roles |
| `suppliers` | `Supplier` | Per-tenant supplier directory |
| `shipments` | `Shipment` | Physical shipment records linked to orders |
| `inventory` | `InventoryItem` (and the richer, secondary `Inventory` model) | Per-tenant stock items |
| `alerts` | `Alert` | Role-targeted system notifications |
| `integration_logs` | `IntegrationLog` | Full audit trail of every CPI-bound message |

Note: `InventoryItem` and `Inventory` are two distinct Java classes that both map to the **same** `inventory` collection (`@Document(collection = "inventory")` on both). `InventoryItem` is the actively used, simpler model (backing `InventoryController`/`InventoryService`); `Inventory` is a more detailed model (warehouse, batch, expiry, supplier-contact fields) present in the codebase but not yet wired into the active controllers — most likely an earlier or planned richer schema for the same logical entity.

## 7.2 Primary Keys and Identifiers

Every document uses MongoDB's default `_id` (`ObjectId`, mapped to the Java `id: String` field via `@Id`) as its primary key. In addition, every collection defines its own **human-readable business identifier** generated at creation time with a consistent convention: `Order.orderId` (`ORD-XXXXXXXX`), `User.userId` (`USR-XXXXXXXX`), `Alert.alertId` (`ALT-XXXXXXXX`), `IntegrationLog.logId` (`ILOG-XXXXXXXX`), `Shipment.shipmentId` (`SHP-XXXXXXXX`) — an 8-character uppercase segment of a random UUID prefixed by a type code, generated in the owning service (e.g., `OrderService.generateOrderId()`).

## 7.3 Relationships (Application-Level)

Since MongoDB does not enforce foreign keys, relationships are maintained and resolved in application code:

- `Order.correlationId` links a System1 order to its System2 mirror order (both share the same `correlationId` but different `systemId`/`direction`), resolved via `OrderRepository.findByCorrelationIdAndSystemId`.
- `Order.idempotencyKey` is used purely for inbound deduplication, not as a relationship.
- `Shipment.orderId` links a shipment to its originating order (string reference, resolved by a repeat query, not a Mongo `$lookup`).
- `IntegrationLog.orderId`/`shipmentId`/`supplierId`/`correlationId` link a log entry back to the business entity it concerns, enabling the correlation-trace feature in `LogsController.getCorrelationTrace`.
- `InventoryItem.supplierId` links a stock item to its supplier (string reference).
- `Alert.referenceId` links an alert to the order/shipment/inventory document that triggered it.

## 7.4 Indexes

Explicit indexes are declared with Spring Data's `@Indexed` annotation on: `User.email` (unique), `IntegrationLog.timestamp`, `IntegrationLog.correlationId`, `IntegrationLog.eventType`, and `IntegrationLog.status` — all named indexes, chosen because these are precisely the fields the `LogsController`/`IntegrationLogController` query most heavily (correlation tracing, status filtering, chronological listing). `IntegrationLogIndexInitializer` additionally exists as a startup component to ensure these indexes are created programmatically. No other collection declares explicit secondary indexes in code; MongoDB's default `_id` index is present on every collection regardless.

## 7.5 ER Diagram

**[Insert Diagram: Entity relationship diagram showing the seven collections (Order, User, Supplier, Shipment, InventoryItem, Alert, IntegrationLog) and the application-level reference fields connecting them, as described in §7.3]**

## 7.6 Schema Reference — `orders`

**Table 7.2 — `orders` Collection Field Reference**

| Field | Type | Purpose |
|---|---|---|
| `id` | ObjectId (String) | MongoDB primary key |
| `orderId` | String | Human-readable business ID (`ORD-XXXXXXXX`) |
| `direction` | `Direction` (`INBOUND`/`OUTBOUND`) | Whether this document represents a purchasing or selling side of the order |
| `status` | `OrderStatus` | Internal fulfilment lifecycle state |
| `poStatus` | `PoStatus` | Cross-system contract state |
| `correlationId` | String | Immutable key that reconciles a PO with its later approval callback and mirror |
| `idempotencyKey` | String | Write-once dedup key for inbound delivery |
| `sourceSystem` / `targetSystem` | String (`"system1"`/`"system2"`) | Wire-level routing addresses |
| `systemId` | `SystemId` | Tenant discriminator |
| `format` | String (`"json"`/`"xml"`/`"csv"`) | Wire format selected for this PO |
| `items` | `List<OrderItem>` | Line items (SKU, description, quantity, unit price, line total) |
| `totalAmount` | Double | Computed order total |
| `stockCheckSent`, `availableQuantity`, `buyerResponse` | boolean / Integer / String | Lane A stock-negotiation state |
| `cancelledBy`, `cancellationReason` | String | Attribution of who cancelled/rejected and why |
| `statusUpdatedAt`, `resolvedAt`, `createdAt` | LocalDateTime | Lifecycle timestamps used by the scheduled jobs |

---

# CHAPTER 8: FEATURES

## 8.1 Role-Based Authentication and Account Security

Users register and log in under one of four roles across two tenants. Failed-login lockout (5 attempts), account activation flags, and BCrypt password hashing protect the account layer. **Workflow:** `Login.jsx` → `POST /api/auth/login` → `AuthService` → JWT issued and cached client-side. **Backend APIs:** `POST /api/auth/login`, `POST /api/auth/register`. **Database interaction:** `users` collection. **User benefit:** every user only ever sees data relevant to their own role and tenant, without needing separate deployments per organization.

## 8.2 Purchase Order Creation and Dispatch

Procurement users (or the System2 simulator) create purchase orders with one or more line items; System1-Procurement-originated orders auto-dispatch to CPI immediately on creation. **Screens:** Orders page (create form). **Backend APIs:** `POST /api/orders`, `POST /api/orders/{id}/send`. **Database interaction:** `orders` collection insert/update. **User benefit:** procurement staff do not need to manually track which orders still need to be sent to the counterparty.

## 8.3 Vendor Stock-Check Negotiation (Lane A)

A vendor reviews an incoming order, checks live inventory availability inline (via the inventory-context lookup), and notifies the buyer of the quantity available; the buyer then accepts or declines. **Screens:** Orders page (vendor pending-approvals view, notify/respond actions). **Backend APIs:** `PUT /api/orders/{id}/notify-stock`, `PUT /api/orders/{id}/respond-stock`. **Database interaction:** `orders` collection status/availableQuantity updates; alerts written to `alerts`. **User benefit:** buyers get an accurate, inventory-backed answer before committing to an order rather than a blind approval.

## 8.4 Vendor Stock Offer via CPI (Cross-Tenant Lane)

A System1 vendor can send an explicit accept/partial-offer/reject decision to the counterparty tenant over SAP CPI iFlow 3, with the offered quantity auto-computed from on-hand inventory versus the requested quantity. **Backend APIs:** `PUT /api/orders/{id}/send-offer`, `POST /api/orders/{id}/stock-offer` (inbound receiver). **Database interaction:** `orders`, `integration_logs`. **User benefit:** demonstrates a genuine cross-system negotiation step mediated entirely by the integration platform.

## 8.5 Order Approval / Rejection Callback

A counterparty's approval or rejection decision for a previously sent PO is received back through SAP CPI iFlow 2 and reconciled to the originating order by `correlationId`. **Backend APIs:** `POST /api/cpi/inbound/approval`. **Database interaction:** `orders` status update, `alerts` insert, `integration_logs` insert. **User benefit:** the originating side is notified automatically the moment a decision is made, without polling.

## 8.6 Shipment Lifecycle Tracking

Once an order is confirmed, an associated shipment record advances automatically through transit statuses on scheduled timers, and can also be viewed and manually updated. **Screens:** Shipments page (list, active/past tabs, status update, cancel). **Backend APIs:** `GET /api/shipments`, `POST /api/shipments`, `PUT /api/shipments/{id}/status`, `PUT /api/shipments/{id}/cancel`. **Database interaction:** `shipments` collection. **User benefit:** both sides can track physical delivery progress without manual status entry for every leg of the journey.

## 8.7 Inventory Management and Low-Stock Alerting

Vendors/Procurement manage stock items, receive/sell stock, and are automatically alerted when quantity falls below a per-item reorder threshold; the alert is also emailed via SAP CPI iFlow 5. **Screens:** Inventory page. **Backend APIs:** `GET/POST /api/inventory`, `POST /api/inventory/receive`, `POST /api/inventory/sell`, `GET /api/inventory/low-stock`. **Database interaction:** `inventory`, `alerts`/`inventory_alerts`. **User benefit:** stockouts are caught proactively rather than discovered when a customer order cannot be fulfilled.

## 8.8 Supplier Directory

Procurement/Admin manage a directory of suppliers with contact, category, and status information. **Screens:** Suppliers page. **Backend APIs:** `GET/POST/PUT/DELETE /api/suppliers`. **Database interaction:** `suppliers` collection. **User benefit:** a single source of truth for supplier contact and status information, referenced from inventory items.

## 8.9 System Alerts

A unified, role-targeted notification feed surfaces order, shipment, and inventory events across the application. **Screens:** Alerts page. **Backend APIs:** `GET /api/alerts`, `GET /api/alerts/active`, `PUT /api/alerts/{id}/resolve`, `PUT /api/alerts/resolve-all`. **Database interaction:** `alerts` collection. **User benefit:** users get a consolidated, filterable "what happened" feed instead of needing to notice changes on each individual page.

## 8.10 Admin/Manager Oversight Dashboards

Admin and Manager roles get a cross-tenant view: aggregate order/inventory analytics, a dedicated Vendor-view and Procurement-view (mirroring what a Vendor/Procurement user would see, for oversight purposes), and full user management. **Screens:** Admin Dashboard, Admin Vendor View, Admin Procurement View, User Management. **Backend APIs:** `GET /api/admin/summary`, `GET /api/admin/orders`, `GET /api/admin/inventory-analysis`, `GET/PUT/DELETE /api/users/**`. **Database interaction:** reads across all collections; writes to `users`. **User benefit:** organizational oversight without needing direct database access.

## 8.11 Integration Log Viewer (Message Tracing)

A searchable, paginated view over every message that has crossed the CPI bridge, including a correlation-ID trace view that reconstructs the full lifecycle of a single PO across outbound send, inbound receipt, and approval callback. **Screens:** Integration Logs page. **Backend APIs:** `GET /api/logs`, `GET /api/logs/{correlationId}`, `GET /api/logs/status/{status}`, plus the richer `IntegrationLogController` query surface. **Database interaction:** `integration_logs` collection. **User benefit:** gives non-CPI-admin users (e.g., a developer or support engineer) visibility into integration health without needing access to the SAP CPI Monitor itself.

## 8.12 System 2 Simulator Controls

Admin/Manager can start, stop, or manually fire one iteration of the System 2 counterparty simulator. **Screens:** none dedicated in the current frontend (backend-only control surface). **Backend APIs:** `GET /api/simulator/status`, `POST /api/simulator/start|stop|fire`. **Database interaction:** none directly (delegates to `System2ProcurementOrderGenerator`, which writes to `orders`). **User benefit:** lets an operator pause simulated traffic during a live demo or manually trigger a new order for testing without waiting for the next scheduled tick.

**[Insert Screenshot: Alerts Page]**
**[Insert Screenshot: Inventory Page]**
**[Insert Screenshot: Suppliers Page]**

---

# CHAPTER 9: API DOCUMENTATION

All endpoints below are taken directly from the `@RequestMapping`/`@GetMapping`/`@PostMapping`/etc. annotations in the controller classes. Unless stated otherwise, all non-CPI endpoints require a valid `Authorization: Bearer <JWT>` header and are further restricted by the role rules in §6.1.1.

## 9.1 Authentication API

**Table 9.1 — Authentication API Endpoints**

| Method | URL | Purpose | Auth |
|---|---|---|---|
| POST | `/api/auth/login` | Authenticate a user and issue a JWT | Public |
| POST | `/api/auth/register` | Create a new user account and issue a JWT | Public |

**`POST /api/auth/login`**
*Request:* `{ "email": "vendor1@sys1.com", "password": "Pass@123" }`
*Response (200):* `{ "token": "<jwt>", "role": "VENDOR", "userId": "USR-...", "name": "...", "entityId": "...", "entityType": "...", "systemId": "SYSTEM1" }`
*Possible errors:* invalid credentials, account locked, account inactive — all currently surfaced as a generic error response derived from a `RuntimeException` message.

**`POST /api/auth/register`**
*Request:* `{ "name": "...", "email": "...", "password": "...", "role": "VENDOR", "entityId": "...", "entityType": "...", "systemId": "SYSTEM1" }`
*Response (200):* same shape as login. *Possible errors:* email already registered; invalid `role` enum value.

## 9.2 Order Management API

**Table 9.2 — Order Management API Endpoints**

| Method | URL | Purpose |
|---|---|---|
| GET | `/api/orders` | List all orders visible to the caller's role/tenant |
| GET | `/api/orders/paged` | Paginated, filterable, searchable, sortable order list |
| GET | `/api/orders/active` | Active (in-progress) orders |
| GET | `/api/orders/past` | Delivered/past orders |
| GET | `/api/orders/pending-approvals` | Vendor's pending-approval queue |
| GET | `/api/orders/buyer-decisions` | Orders awaiting/holding a buyer decision |
| GET | `/api/orders/{id}` | Single order by ID |
| GET | `/api/orders/{id}/availability` | Per-line-item stock availability for an order |
| POST | `/api/orders` | Create a new order |
| PUT | `/api/orders/{id}/notify-stock` | Vendor notifies buyer of available quantity |
| PUT | `/api/orders/{id}/respond-stock` | Buyer responds YES/NO to a stock notification |
| POST | `/api/orders/{id}/send` | Dispatch an order to CPI (iFlow 1) |
| PUT | `/api/orders/{id}/send-offer` | Vendor sends a stock offer via CPI iFlow 3 |
| POST | `/api/orders/{id}/stock-offer` | (inbound) receive a stock offer decision |
| PUT | `/api/orders/{id}/reject` | Reject an order |
| PUT | `/api/orders/{id}/confirm-supply` | Vendor's final supply confirmation |
| POST | `/api/orders/{id}/vendor-confirm-supply` | Vendor confirms supply (alternate lane) |
| POST | `/api/orders/{id}/cancel-vendor` | Vendor-initiated cancellation |
| PUT | `/api/orders/{id}/cancel` | Cancel an order |

**`POST /api/orders`**
*Request:* `{ "counterpartyId": "...", "counterpartyName": "...", "items": [ { "sku": "...", "description": "...", "quantity": 10, "unitPrice": 25.0 } ], "expectedDeliveryDate": "2026-08-01", "notes": "..." }`
*Response (200):* the created `Order` document (including generated `orderId`, `status: REQUESTED`). *Auth:* ADMIN, MANAGER, VENDOR, PROCUREMENT. *Possible errors:* validation failure on malformed items (400 via `GlobalExceptionHandler`).

**`POST /api/orders/{id}/send`**
*Response (200):* the updated `Order` with `poStatus` set to `SENT` or `FAILED`. *Possible errors:* CPI unreachable after retries (order still saved as `FAILED`, not a hard error to the caller); already-sent order is idempotently returned unchanged.

## 9.3 CPI Inbound Bridge API

**Table 9.3 — CPI Inbound Bridge API Endpoints**

| Method | URL | Purpose | Auth |
|---|---|---|---|
| POST | `/api/cpi/inbound/po` | Receive a purchase order forwarded by CPI iFlow 1 | `X-CPI-Secret` header (no JWT) |
| POST | `/api/cpi/inbound/stock-offer` | Receive a vendor stock decision via CPI iFlow 3 | `X-CPI-Secret` header (no JWT) |
| POST | `/api/cpi/inbound/inventory-update` | Receive an inventory movement via CPI iFlow 4 | `X-CPI-Secret` header (no JWT) |
| POST | `/api/cpi/inbound/approval` | Receive an approval/rejection callback via CPI iFlow 2 | `X-CPI-Secret` header (no JWT) |
| POST | `/api/cpi/test/send` | Developer utility to manually fire a test PO in a chosen format | JWT (dev/test controller) |

**`POST /api/cpi/inbound/po`**
*Request (`InboundPoRequest`):* `{ "poNumber": "...", "correlationId": "...", "idempotencyKey": "...", "sourceSystem": "system2", "targetSystem": "system1", "format": "json", "counterpartyId": "...", "counterpartyName": "...", "items": [...], "totalAmount": 0.0 }`
*Response (200):* the persisted (or deduplicated) `Order`. *Possible errors:* `OpenPoCapExceededException` when the receiving tenant's open-PO cap (`cpi.max-open-pos`) is reached.

**`POST /api/cpi/inbound/approval`**
*Request (`ApprovalCallbackRequest`):* `{ "correlationId": "...", "poNumber": "...", "decision": "APPROVED", "decidedBy": "...", "reason": null }`
*Response (200):* the updated `Order`, or `null`/404-style response if no order matches the `correlationId`.

## 9.4 Inventory, Shipment, Supplier, and Alert API

**Table 9.4 — Inventory, Shipment, Supplier, and Alert API Endpoints**

| Resource | Method | URL | Purpose |
|---|---|---|---|
| Inventory | POST | `/api/inventory` | Create a stock item |
| Inventory | GET | `/api/inventory` | List stock items |
| Inventory | POST | `/api/inventory/receive` | Record a stock receipt |
| Inventory | POST | `/api/inventory/sell` | Record a stock sale/consumption |
| Inventory | GET | `/api/inventory/alerts` | List inventory alerts |
| Inventory | PATCH | `/api/inventory/alerts/{id}/resolve` | Resolve an inventory alert |
| Inventory | GET | `/api/inventory/low-stock` | List items below threshold |
| Inventory | PUT | `/api/inventory/{id}/quantity` | Manually adjust quantity |
| Inventory | DELETE | `/api/inventory/{id}` | Delete a stock item |
| Inventory | GET | `/api/inventory/context` | Per-item availability lookup (used by Orders page) |
| Shipments | GET | `/api/shipments`, `/active`, `/past` | List shipments (all/active/past) |
| Shipments | GET | `/api/shipments/{id}` | Single shipment |
| Shipments | POST | `/api/shipments` | Create a shipment |
| Shipments | PUT | `/api/shipments/{id}/status` | Update shipment status |
| Shipments | PUT | `/api/shipments/{id}/cancel` | Cancel a shipment |
| Suppliers | POST/GET/PUT/DELETE | `/api/suppliers`, `/api/suppliers/{id}` | Supplier CRUD |
| Suppliers | PUT | `/api/suppliers/{id}/status` | Update supplier status |
| Alerts | GET | `/api/alerts`, `/active` | List alerts (all/active) |
| Alerts | PUT | `/api/alerts/{id}/resolve`, `/resolve-all` | Resolve one/all alerts |
| Alerts | POST | `/api/alerts/generate` | Manually trigger alert generation |

## 9.5 Dashboard, Admin, and Logging API

**Table 9.5 — Dashboard, Admin, and Logging API Endpoints**

| Resource | Method | URL | Purpose |
|---|---|---|---|
| Dashboard | GET | `/api/dashboard/stats` | Generic per-tenant dashboard stats |
| Dashboard | GET | `/api/dashboard/vendor` | Vendor-specific stats |
| Dashboard | GET | `/api/dashboard/procurement` | Procurement-specific stats |
| Admin | GET | `/api/admin/summary` | Cross-tenant summary |
| Admin | GET | `/api/admin/orders` | Cross-tenant order list |
| Admin | GET | `/api/admin/inventory-analysis` | Cross-tenant inventory analytics |
| Users | GET/PUT/DELETE | `/api/users`, `/api/users/{id}`, `/api/users/{id}/role`, `/status`, `/unlock` | User administration |
| Logs | GET | `/api/logs`, `/api/logs/stats`, `/api/logs/{correlationId}`, `/api/logs/status/{status}` | Paginated integration-log queries |
| Integration Logs | GET/POST/PATCH/DELETE | `/api/integration-logs/**` | Full CRUD + rich filtering over the audit trail (by message ID, iFlow, order, shipment, supplier, status, type, date range) |
| Simulator | GET/POST | `/api/simulator/status`, `/start`, `/stop`, `/fire` | System 2 simulator control |

---

# CHAPTER 10: TESTING

## 10.1 Testing Strategy

Testing of this project was conducted primarily through **manual, scenario-driven verification** against the running application and the SAP CPI trial tenant, supplemented by developer utility endpoints built specifically to make manual testing repeatable. The project's own internal checkpoint document (`docs/PROJECT_CHECKPOINT.md`) explicitly tracks each integration phase as "Done & verified" only after such manual end-to-end checks — for example, Phase 1 hardening (retry/backoff, idempotency, the open-PO cap) and Phase 2 (the full iFlow 2 round trip via ngrok) are both recorded as manually verified milestones rather than covered by automated test suites.

## 10.2 Manual Testing

Manual testing exercised: the login/registration flow across all three UI tabs and both tenants; the full Lane A negotiation (create order → notify stock → respond → confirm supply → scheduled shipment progression → delivery → inventory update via iFlow 4); the full Lane B path (System1 Procurement creates an order → auto-dispatch to CPI → System2 Vendor robot accepts/rejects → status reflected back); the System 2 simulator's autonomous order generation across all three wire formats; the admin dashboards' cross-tenant figures against the underlying per-tenant data; and the integration-log viewer's correlation-trace feature against a manually tracked sequence of messages for a single PO.

`CpiTestController` (`POST /api/cpi/test/send?format=json|xml|csv`) exists specifically as a manual-testing utility, allowing a developer to fire a single test PO in a chosen format directly at CPI without needing to go through the full UI order-creation flow — useful for isolating whether a bug is in the CPI iFlow itself versus the application's order-creation logic.

## 10.3 Automated (Unit/Integration) Testing

The project's Maven configuration includes `spring-boot-starter-test` and `spring-security-test` as test-scoped dependencies, providing the standard JUnit 5/Mockito/Spring Security test infrastructure. At the current stage of the project, however, a source check of `src/test/` shows no custom test classes have yet been authored beyond the default Spring Boot application-context test generated by the project template; Phase 7 of the project's own checkpoint plan ("harden, test, deploy") explicitly lists "unit/slice tests" as not yet started. This is reported transparently here rather than invented, in keeping with the instruction to reflect only what is actually present in the codebase.

## 10.4 Sample Test Cases (Manually Executed)

| # | Scenario | Steps | Expected Result | Observed Result |
|---|---|---|---|---|
| 1 | Valid vendor login | Submit correct email/password on Vendor tab | JWT issued, redirected to `/vendor` | Pass |
| 2 | Wrong-tab login | Submit valid Procurement credentials on the Vendor tab | Error: "credentials belong to the PROCUREMENT portal" | Pass |
| 3 | Account lockout | Submit wrong password 5 times | Account flips to locked; further attempts rejected even with correct password | Pass |
| 4 | Create + auto-dispatch order (Lane B) | System1 Procurement creates an order | Order auto-sent to CPI iFlow 1; `poStatus=SENT` | Pass |
| 5 | Duplicate inbound PO | Re-deliver the same `idempotencyKey` to `/api/cpi/inbound/po` | Second delivery deduplicated, same order returned, no duplicate document | Pass |
| 6 | Open-PO cap | Exceed `cpi.max-open-pos` open inbound POs | New inbound PO rejected with `OpenPoCapExceededException` | Pass |
| 7 | Multi-format outbound | Trigger PO dispatch with `format=json`, then `xml`, then `csv` | All three formats accepted by CPI and produce the same canonical downstream result | Pass (per checkpoint notes) |
| 8 | Low-stock alert | Reduce an inventory item below its `thresholdQuantity` | `LOW_STOCK` alert created once (not duplicated on repeated scheduler ticks) | Pass |

## 10.5 Bug Fixes and Edge Cases Handled

Several fixes are directly evidenced in code comments and the project checkpoint: a null-safe `getSystemId` guard was added after an NPE was observed for principals without a tenant claim; a stale-build "No accessor" login error was traced to Spring DevTools' partial hot-reload running stale classes and was resolved by requiring a full `mvnw clean` restart after `SecurityConfig`/property changes (documented as a "hard-won fact" in the checkpoint so it would not be re-diagnosed); a `.env`-parsing edge case was found where a pasted value could carry a non-breaking-space character (`c2 a0`) that silently caused an `invalid_client` OAuth2 error, resolved by loading `.env` by splitting only on the first `=` and trimming carefully; and iFlow 4's inventory-update path was hardened to treat an HTTP-200-with-`ERROR`-body response (from CPI's own exception subprocess) as a real failure rather than a false success, so inventory sync failures are retried instead of silently marked applied.

## 10.6 Validation

Input validation is enforced at two levels: **Jakarta Bean Validation** annotations on request DTOs, converted to structured 400 responses by `GlobalExceptionHandler.handleValidation`; and **business-rule validation** inside services (e.g., `System1VendorStockOfferService`/`OrderService` throwing `IllegalArgumentException`/`IllegalStateException` for invalid state transitions such as confirming supply before buyer approval), converted to 400 responses by `GlobalExceptionHandler.handleIllegalArgument`.

---

# CHAPTER 11: RESULTS

## 11.1 Final Application

The completed application is a working, multi-role, two-tenant purchase-order platform accessible through a single React frontend, backed by one Spring Boot service and one MongoDB Atlas cluster, with live, bidirectional integration to a SAP CPI trial tenant across five distinct iFlows (PO outbound, approval callback, stock-offer notification, inventory update, and inventory alert email). Per the project's own checkpoint document, phases 0 through 4 (contracts, the core CPI bridge, bridge hardening, multi-format outbound, the approval callback round trip, tenant isolation, and the System 2 simulator) are complete and manually verified end to end; phase 5 (multi-line-item depth and further frontend polish) is partially complete; phases 6–7 (additional iFlows, hardening/deployment) had not yet been started as of the last recorded checkpoint (2026-06-19).

## 11.2 Screens

**[Insert Screenshot: Login Page — all three role tabs]**
**[Insert Screenshot: Vendor Dashboard — summary cards and recent activity]**
**[Insert Screenshot: Procurement Dashboard]**
**[Insert Screenshot: Admin Dashboard — cross-tenant summary]**
**[Insert Screenshot: Orders Page — paginated list with tab/status/search filters]**
**[Insert Screenshot: Order creation form with line-item picker]**
**[Insert Screenshot: Shipments Page]**
**[Insert Screenshot: Inventory Page with low-stock indicator]**
**[Insert Screenshot: Integration Logs Page — message list and correlation trace modal]**

## 11.3 Outputs

Representative outputs include: a persisted `Order` document with a fully populated cross-system contract (`correlationId`, `poStatus`, `sourceSystem`/`targetSystem`); an `IntegrationLog` entry pair showing the outbound `PO_SENT` event followed by the counterparty's inbound `PO_RECEIVED` event with matching `correlationId`; and a `LOW_STOCK` alert automatically generated and, per configuration, emailed via CPI iFlow 5 to the configured procurement/admin/vendor recipient addresses.

## 11.4 Performance

No formal load-testing or benchmarking was conducted; the project's outbound CPI calls are configured with explicit timeouts (`cpi.connect-timeout-ms=5000`, `cpi.read-timeout-ms=15000`) and a bounded retry policy (`cpi.max-retries=3`, `cpi.retry-backoff-ms=500`, linear backoff), which bound worst-case latency for a single outbound dispatch to a known maximum rather than hanging indefinitely. The System 2 simulator's tick interval and the shipment-lifecycle schedulers' fixed delays were deliberately shortened during development (e.g., `simulator.interval-ms=100000` instead of a realistic 10-minute production interval) specifically to keep demo sessions short, which is a testing/demo convenience rather than a claim about production-scale performance.

## 11.5 Reliability

Reliability measures actually implemented and exercised include: idempotency-key deduplication on inbound POs (protects against CPI/network-level redelivery); an open-PO cap that fails fast rather than allowing an unbounded queue to build up during a sustained simulator flood; retry-with-backoff on transient CPI outbound failures, with a `FAILED` (not lost) terminal state on exhaustion; and "recovery" schedulers that detect and advance orders that appear to be stuck in an intermediate shipment state.

## 11.6 User Experience

The UI provides role-appropriate navigation (the sidebar only shows links relevant to the logged-in role), inline error messaging on forms, a collapsible sidebar for more screen space, and a dedicated integration-log viewer aimed at giving even non-CPI-admin users visibility into what is otherwise an opaque, backend-only integration process.

---

# CHAPTER 12: CHALLENGES FACED

The following challenges are drawn from evidence in the codebase itself — code comments, the project's own checkpoint document, and structural patterns that indicate a problem was encountered and specifically worked around — rather than invented.

**Table 12.1 — Challenges and Resolutions**

| Challenge | Evidence | Resolution |
|---|---|---|
| OAuth2 client-credentials misconfiguration (not Basic auth) | `docs/PROJECT_CHECKPOINT.md` §6: "CPI auth = OAuth2 client-credentials (not Basic)" listed as a hard-won fact | Standardized on `CpiClient.getToken()`'s client-credentials flow with a cached, auto-refreshed Bearer token |
| Silent 500s from an unresolved CPI externalized parameter | Checkpoint §6: "An unresolved externalized-parameter pill → 500 with no logged message" | Documented as an operational gotcha; log level raised from `None` to `Info` on the CPI side to surface messages |
| `.env` parsing corrupted by invisible characters | Checkpoint §6: "`.env` paste can carry NBSP (`c2 a0`) → silent `invalid_client`" | Custom `.env` loading logic splitting only on the first `=` |
| Spring DevTools serving stale classes after config changes | Checkpoint §6: "full `clean` restart — devtools partial reloads run stale classes/props (caused the login 'No accessor' and the simulator-interval confusion)" | Adopted a standing practice of `mvnw clean spring-boot:run` after any `SecurityConfig`/properties change |
| iFlow 4 silently "succeeding" while actually failing | `CpiClient.sendInventoryUpdate` comment: "iFlow4's Exception Subprocess returns HTTP 200 with an ERROR body... Treat that as a real failure, not a success" | Added explicit response-body inspection (`resp.contains("\"status\"") && resp.contains("ERROR")`) to force a retry instead of a false success |
| Duplicate/stale frontend scaffold (`frontend/frontend/`) | Confirmed present in the repository; checkpoint §4 lists its removal as an open task | Not yet resolved — tracked as a pending cleanup item |
| Dependency drift (Tailwind/Recharts installed but not integrated) | `vite.config.js` has no Tailwind plugin registered; no `recharts` import anywhere in `src/` | Not yet resolved — noted here and in Chapter 14 as a candidate cleanup/feature item |
| Null-unsafe tenant resolution | `Tenant.of(Authentication)` defensively catches `IllegalArgumentException` and defaults to `SYSTEM1` | Centralizing tenant resolution in one helper class prevented the NPE from recurring across multiple call sites |
| Preventing duplicate low-stock alerts on every scheduler tick | `InventoryAlertRepository.findFirstBySystemIdAndSkuAndStatus` used before creating a new alert | Deduplicate-before-create pattern in `InventoryAlertScheduler` |
| A previously committed, real MongoDB Atlas password | `.env.example` explicitly documents: "the old one... was committed in plain text and must be considered compromised" | Secrets externalized to environment variables (`MONGODB_URI`, `APP_JWT_SECRET`), `.env` gitignored, and the compromised credential flagged for rotation |

Beyond these directly evidenced items, the general shape of the codebase (two independent negotiation lanes, mirror-order synchronization, dual status vocabularies) suggests that correctly reasoning about "who owns this transition and which side's mirror needs updating" was itself an ongoing design challenge throughout development, addressed by centralizing that logic in a small number of well-named private helper methods (`upsertSystem2VendorMirror`, `upsertSystem2ProcurementMirror`, `syncSystem2Mirror`) rather than duplicating mirror-sync logic at every call site.

---

# CHAPTER 13: LEARNING OUTCOMES

## 13.1 Technical Learning

This project provided practical, hands-on experience with the full backend-to-frontend stack of a modern enterprise web application: Spring Boot application structure and dependency injection, Spring Security's filter-chain model and stateless JWT authentication, Spring Data MongoDB (both repository-derived queries and dynamic `MongoTemplate` criteria queries for cases too complex for method-name derivation), and React's hook-based component model with the Context API for global state. Working with SAP CPI specifically gave direct exposure to iPaaS concepts that are not typically covered in an academic curriculum: iFlow design, control-header conventions for message routing, OAuth2 client-credentials authentication from a backend service, and the practical difference between a message *format* (JSON/XML/CSV) and a message *contract* (the canonical PO shape both sides agree on regardless of format).

## 13.2 Programming Concepts

The project deepened understanding of several core programming concepts in a production-adjacent context: idempotency and deduplication under at-least-once delivery semantics; the distinction between a domain's *internal* state machine and the *external contract* state machine it must also satisfy (illustrated concretely by `OrderStatus` vs. `PoStatus`); defensive default-and-fallback patterns (`Tenant.of` never throwing, always defaulting safely); and the tenant-isolation pattern of tagging every document with a discriminator field and centralizing all tenant-scoping logic through one helper rather than repeating conditionals throughout the codebase.

## 13.3 Software Engineering Principles

The project reinforced several software engineering principles in practice: **contract-first design** (the integration contract was documented in `docs/PO_CONTRACT.md` before endpoints were built against it); **separation of concerns** via the layered controller/service/repository architecture; **fail-safe auditing** (designing `CpiAuditService` so that a logging failure can never take down the business transaction it is observing); and **externalized configuration** for secrets and environment-specific values, keeping credentials out of source control.

## 13.4 Problem-Solving and Debugging

Debugging a system with an external, cloud-hosted integration dependency (SAP CPI) required developing a more systematic troubleshooting approach than a typical single-process application: distinguishing between failures at the application layer, the CPI iFlow layer, and the network/tunnel layer (ngrok); learning to read SAP CPI's Message Processing Log to determine whether a failure originated before or after a specific processing step; and building a dedicated test endpoint (`CpiTestController`) specifically to isolate variables during debugging rather than always testing through the full UI flow.

## 13.5 Team Collaboration

[Placeholder — describe any collaboration with a supervisor, mentor, or team members during code reviews, design discussions, or integration testing sessions, to be filled in with actual internship team details.]

---

# CHAPTER 14: FUTURE ENHANCEMENTS

The following enhancements are drawn directly from the project's own recorded roadmap (`docs/PROJECT_CHECKPOINT.md`, sections 4 and 5) plus observations made during this analysis, and are realistic extensions of the existing architecture rather than speculative rewrites:

1. **Full multi-line-item support end-to-end.** Extend `OrderService.buildPayload`/`System2ProcurementOrderGenerator.buildPayload` and the corresponding iFlow 1 mapping to carry the complete `items` array instead of only the first line item.
2. **Complete tenant-scoping of secondary collections.** Add and consistently query `systemId` on `Shipment`, `Alert`, `Supplier`, and `Inventory` wherever it is not yet fully applied, so admin-facing counts are truly per-tenant rather than partially global.
3. **Externalize the frontend API base URL.** Replace the hardcoded `http://localhost:8080/api` in `src/api/axios.js` with a `VITE_API_URL` environment variable, enabling the same build to target different backend environments.
4. **Remove the duplicate `frontend/frontend/` scaffold** and the unused `components/Layout.jsx`/`components/Sidebar.jsx` pair (superseded by the inline `Layout` in `App.jsx`) and the unrouted `AdminDashboard.jsx`/`SupplierPortal.jsx` pages, to reduce dead code.
5. **Either wire up or remove the unused Tailwind CSS and Recharts dependencies** — Recharts in particular would be a natural fit for visualizing the order-trend data already computed by `DashboardService.buildOrderTrend`, which is currently only returned as raw data with no chart rendering it.
6. **Additional iFlows (per the project's own Phase 6 plan):** an inventory-alert email iFlow beyond the current single consolidated batch, an ML-based order risk-scoring iFlow (the `Order.riskScore` field already exists in the model, ready to be populated), and an end-of-day summary report iFlow.
7. **Deployment hardening (per Phase 7):** replace the development-only ngrok tunnel with a permanently deployed public backend endpoint (e.g., on Render, as the checkpoint proposes) for the iFlow 2 approval callback; lock down CORS from the current permissive `allowedOriginPatterns: "*"` to an explicit origin allow-list; broaden `GlobalExceptionHandler` to cover more exception types with consistent response shapes; and remove the developer-only `CpiTestController` from the production build.
8. **Automated testing.** Add unit tests for the service layer (particularly the order-lane and mirror-sync logic, which is intricate enough to benefit from regression protection) and slice tests for the controller/security layer, closing the gap identified in Chapter 10.
9. **Optimistic concurrency control.** Add an `@Version` field to `Order` to protect against lost updates when multiple concurrent status transitions race against the same document, as anticipated in the project's own "How it can be improved" notes.
10. **Observability.** Propagate `correlationId` into logging MDC for easier cross-request log correlation, and expose Spring Boot Actuator health/metrics endpoints for operational monitoring.

---

# CHAPTER 15: CONCLUSION

This project set out to design and build a realistic, enterprise-style purchase-order and inventory management platform in which two independent organizations exchange business documents exclusively through SAP Cloud Platform Integration, rather than through direct point-to-point integration. Over the course of development, the resulting system delivered a working Spring Boot backend with role-based JWT authentication, a MongoDB-backed multi-tenant data model, a resilient outbound and inbound CPI bridge supporting three interchangeable wire formats, a dual-lane order negotiation model covering both a human-operated vendor scenario and an autonomously simulated counterparty, and a React frontend providing role-specific dashboards and a full operational feature set spanning orders, shipments, inventory, suppliers, alerts, user administration, and integration-message tracing.

The project succeeded in demonstrating the core integration architecture end to end and manually verified through Phase 4 of its own roadmap, while transparently leaving certain depth and hardening items — full multi-line-item support, complete tenant-scoping of secondary collections, automated testing, and production deployment — as clearly identified, realistic next steps rather than claiming a completeness the codebase does not yet have. Working on this project provided substantial, direct exposure to enterprise integration patterns, contract-first API design, resilience engineering (idempotency, retries, audit trails), and full-stack development practices that extend meaningfully beyond a typical academic project, and represents a solid foundation that could be carried forward into a production-grade deployment with the enhancements outlined in Chapter 14.

---

# REFERENCES

References are limited to technologies actually present in the project's dependency manifests (`pom.xml`, `package.json`).

[1] Spring Boot Reference Documentation, VMware/Broadcom, [Online]. Available: https://docs.spring.io/spring-boot/index.html

[2] Spring Security Reference Documentation, VMware/Broadcom, [Online]. Available: https://docs.spring.io/spring-security/reference/index.html

[3] Spring Data MongoDB Reference Documentation, VMware/Broadcom, [Online]. Available: https://docs.spring.io/spring-data/mongodb/reference/index.html

[4] MongoDB, Inc., "MongoDB Manual," [Online]. Available: https://www.mongodb.com/docs/manual/

[5] JJWT (Java JWT) Documentation, jsonwebtoken.io, [Online]. Available: https://github.com/jwtk/jjwt

[6] React, "React Documentation," Meta Platforms, Inc., [Online]. Available: https://react.dev/

[7] React Router, "React Router Documentation," Remix Software, [Online]. Available: https://reactrouter.com/

[8] Vite, "Vite Documentation," VoidZero Inc., [Online]. Available: https://vite.dev/

[9] Axios, "Axios Documentation," [Online]. Available: https://axios-http.com/docs/intro

[10] Tailwind CSS, "Tailwind CSS Documentation," Tailwind Labs Inc., [Online]. Available: https://tailwindcss.com/docs

[11] Recharts, "Recharts Documentation," [Online]. Available: https://recharts.org/

[12] Project Lombok, "Lombok Documentation," [Online]. Available: https://projectlombok.org/

[13] Apache Maven, "Maven Documentation," Apache Software Foundation, [Online]. Available: https://maven.apache.org/guides/

[14] ESLint, "ESLint Documentation," OpenJS Foundation, [Online]. Available: https://eslint.org/docs/latest/

[15] SAP SE, "SAP Integration Suite / Cloud Integration Documentation," [Online]. Available: https://help.sap.com/docs/cloud-integration

---

# APPENDIX

## A.1 Backend Folder Structure (Summary)

```
integration-hub/
├── pom.xml
├── mvnw, mvnw.cmd
├── docs/
│   ├── PO_CONTRACT.md
│   └── PROJECT_CHECKPOINT.md
├── .env.example                  (template only — no real secrets)
└── src/main/
    ├── java/com/supplychain/integration_hub/   (111 source files, single package)
    │   ├── *Controller.java     (21 files)
    │   ├── *Service.java        (21 files)
    │   ├── *Repository.java     (8 files)
    │   ├── *Scheduler.java      (6 files)
    │   └── (models, DTOs, enums, security, config classes)
    └── resources/
        └── application.properties
```

## A.2 Frontend Folder Structure (Summary)

```
frontend/
├── package.json, vite.config.js, index.html
└── src/
    ├── main.jsx, App.jsx
    ├── api/
    │   ├── axios.js
    │   └── services.js
    ├── context/
    │   └── AuthContext.jsx
    ├── components/
    │   ├── Pagination.jsx        (actively used across most list pages)
    │   ├── Layout.jsx             (not imported/used — superseded by inline Layout in App.jsx)
    │   └── Sidebar.jsx            (only imported by the unused Layout.jsx)
    └── pages/
        ├── Login.jsx, Dashboard.jsx, VendorDashboard.jsx, ProcurementDashboard.jsx
        ├── Orders.jsx, Shipments.jsx, Inventory.jsx, Suppliers.jsx, Alerts.jsx
        ├── UserManagement.jsx, IntegrationLogs.jsx (+ .css)
        ├── AdminVendorView.jsx, AdminProcurementView.jsx
        ├── AdminDashboard.jsx        (present but not routed in App.jsx)
        └── SupplierPortal.jsx        (present but not routed in App.jsx)
```

Note: a duplicate, older scaffold directory (`frontend/frontend/`) also exists in the repository root alongside the active `src/`, confirmed stale by file-modification-time comparison; its removal is tracked as an open item in the project's own checkpoint notes (see Chapter 12).

## A.3 Important Configuration Files

- **`pom.xml`** — Maven build file; Spring Boot parent `4.1.0`; Java 21; declares the core web/security/mongodb/validation starters plus JJWT and Lombok.
- **`application.properties`** — central Spring configuration: MongoDB URI (via env var), JWT secret/expiration (via env var), server port (`8080`), order-lifecycle timer delays, the full `cpi.*` property group (base URL, per-iFlow paths, OAuth2 token URL/credentials, inbound secret, timeouts, retry policy, open-PO cap), and the `simulator.*`/`alert.*` property groups.
- **`.env.example`** — documents the two required environment variables (`MONGODB_URI`, `APP_JWT_SECRET`) without containing real values.
- **`vite.config.js`** — registers only the React plugin; does not register the installed Tailwind Vite plugin (see §2.11).
- **`eslint.config.js`** — ESLint flat config with React Hooks and React Refresh plugins enabled.

## A.4 Environment Variables (Names Only — No Values)

| Variable | Purpose |
|---|---|
| `MONGODB_URI` | MongoDB Atlas connection string |
| `APP_JWT_SECRET` | HMAC signing key for JWTs |
| `CPI_BASE_URL` | Base URL of the SAP CPI tenant |
| `CPI_TOKEN_URL` | OAuth2 token endpoint for CPI client-credentials auth |
| `CPI_CLIENT_ID` / `CPI_CLIENT_SECRET` | OAuth2 client credentials for CPI |
| `CPI_INBOUND_SECRET` | Shared secret validated on inbound CPI calls (`X-CPI-Secret`) |
| `CPI_IFLOW3_STOCK_OFFER_URL` | Direct endpoint URL for the iFlow 3 stock-offer receiver |
| `CPI_IFLOW4_INVENTORY_UPDATE_URL` | Direct endpoint URL for the iFlow 4 inventory-update receiver |
| `CPI_IFLOW5_ALERT_EMAIL_URL` | Direct endpoint URL for the iFlow 5 alert-email receiver |

*No secret values are reproduced in this report, in keeping with responsible handling of credentials.*

## A.5 Package Dependencies (Full List)

**Backend (`pom.xml`):** `spring-boot-starter-data-mongodb`, `spring-boot-starter-security`, `spring-boot-starter-validation`, `spring-boot-starter-webmvc`, `jjwt-api`/`jjwt-impl`/`jjwt-jackson` (0.12.6), `spring-boot-devtools` (runtime, optional), `lombok` (optional), `spring-boot-starter-test` (test), `spring-security-test` (test).

**Frontend (`package.json`) — dependencies:** `axios` (^1.18.0), `react` (^19.2.6), `react-dom` (^19.2.6), `react-router-dom` (^7.17.0), `recharts` (^3.8.1).
**Frontend — devDependencies:** `@eslint/js`, `@tailwindcss/vite`, `@types/react`, `@types/react-dom`, `@vitejs/plugin-react`, `eslint`, `eslint-plugin-react-hooks`, `eslint-plugin-react-refresh`, `globals`, `tailwindcss`, `vite`.

## A.6 Major Code Statistics

| Metric | Backend | Frontend |
|---|---|---|
| Source files | 111 Java files | 23 JS/JSX files |
| Total lines of code | ~9,290 | ~9,040 |
| Controllers | 21 | — |
| Services | 21 | — |
| Repositories | 8 | — |
| Schedulers | 6 | — |
| Page components | — | 16 (14 routed, 2 unrouted) |
| Largest single file | `CpiClient.java` / `OrderService.java` (order/CPI logic) | `Orders.jsx` (1,659 lines) |
| MongoDB collections | 7 | — |

*Line counts were obtained by direct enumeration of the project's own source tree at the time of writing and reflect the codebase as analyzed for this report; they will drift as development continues.*

