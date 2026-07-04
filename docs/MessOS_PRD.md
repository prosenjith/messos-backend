# MessOS — Product Requirements Document

**Version 1.0 · Draft PRD**
The operating system for shared mess housing
Prepared for internal development — Android (Kotlin/Compose) + Backend (Kotlin/Ktor)
Dhaka, Bangladesh

---

## 1. Problem Statement & Goals

Shared "mess" housing is a near-universal living arrangement for students and young professionals across Dhaka, evidenced by existing apps in this space already crossing 100K+ installs — yet the leading solution is let down by cluttered navigation, slow performance, and confusing dues calculations, forcing managers back to manual notebooks or spreadsheets even after adopting the app.

The people most affected are mess managers, who juggle daily meal counts, grocery expenses, and deposits for 5–15 people and need the math to be instantly trustworthy, and members, who just want a fast, zero-friction way to log a meal and see exactly what they owe without digging through menus.

MessOS succeeds if a manager can close a month's accounts in under a minute of manual work, a member can log a meal in two taps, and every person in the mess can see live, accurate, dispute-free numbers at any moment — replacing both the notebook and the frustrating app with something people actually enjoy using daily.

### Success Metrics (v1)
- Meal logging takes ≤ 2 taps from app open
- Dues are visible on the home screen with 0 additional taps
- Month-end close requires ≤ 1 minute of manager action
- Live sync latency under 2 seconds across mess members

---

## 2. Product Overview

MessOS is a multi-user, real-time Android application that digitizes the day-to-day operations of a shared mess household: meal logging, grocery expense tracking, deposit tracking, and automatic dues calculation — replacing both the physical khata (notebook) and existing but poorly-executed mess management apps.

**Positioning:** Not just a meal counter — the full operating system for running a mess, with live multi-user sync and trustworthy, always-visible accounting.

---

## 3. Core Features (v1)

| # | Feature | Description |
|---|---|---|
| 1 | Auth & Mess Setup | Signup/login (JWT). Create a mess (generates join code) or join via code. Manager & Member roles. |
| 2 | Meal Management | Members log their own daily meals; manager can edit any member's entries. Calendar view for past days. |
| 3 | Bazaar (Grocery) Log | Manager logs each grocery trip with amount, date, note, optional receipt photo. Full running list. |
| 4 | Deposit Tracking | Manager logs member deposits into the mess fund; members see their own deposit history. |
| 5 | Automatic Hisab (Dues Engine) | Live meal rate = total bazaar ÷ total meals. Each member's due = (meals × rate) − deposits. Always visible on home screen. |
| 6 | Live Sync | WebSocket-based real-time updates — any change reflects instantly for all members, no manual refresh. |
| 7 | Monthly Cycle & Summary | Manager closes a month to lock numbers and generate a shareable per-member summary. History retained. |
| 8 | Notices/Announcements | Manager posts short pinned notices (e.g. deadlines, meal-off days) visible to all members. |

### Out of Scope for v1
- Push notifications
- In-app chat
- PDF export, multi-language support, dark mode
- Manager handover flow, multiple messes per user, mess archiving
- Guest meal requests, advance meal-off scheduling

---

## 4. User Roles & Permissions

| Capability | Manager | Member |
|---|---|---|
| Create / join a mess | Yes | Join only |
| Log own meals | Yes | Yes |
| Edit any member's meals | Yes | No |
| Log grocery (bazaar) expenses | Yes | No |
| Log member deposits | Yes | No |
| View live dues for self | Yes | Yes |
| View dues for all members | Yes | No |
| Post notices | Yes | No |
| Close a monthly cycle | Yes | No |

---

## 5. Key User Flows

### 5.1 Onboarding
- User signs up with phone/email + password
- Chooses: Create a new mess (becomes Manager, gets join code) OR Join a mess (enters code, becomes Member)
- Lands on Home screen showing current dues and today's meal status

### 5.2 Logging a Meal (Member) — target: 2 taps
- Open app → Home screen shows today's date with meal toggles
- Tap breakfast/lunch/dinner icons to mark eaten — saves instantly, syncs live

### 5.3 Logging a Bazaar Expense (Manager)
- Manager taps "Add Expense" → enters amount, date (defaults today), optional note/photo
- Saves → total bazaar cost and meal rate recalculate instantly for all members

### 5.4 Checking Dues (Any Member)
- Home screen always shows: "You owe ৳X" or "You're owed ৳X" — no navigation required
- Tap for breakdown: meals eaten × rate, minus deposits, with full transaction history

### 5.5 Month-End Close (Manager)
- Manager taps "Close Month" → reviews final numbers per member
- Confirms → numbers lock, shareable summary generated, new cycle begins automatically

---

## 6. Data Model

Core entities and relationships for the PostgreSQL schema (via Exposed ORM). One user can belong to one active mess at a time in v1.

### 6.1 User
| Field | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| name | String | |
| phone_or_email | String | Unique, used for login |
| password_hash | String | Bcrypt hashed |
| created_at | Timestamp | |

### 6.2 Mess
| Field | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| name | String | e.g. "Green Road Mess" |
| join_code | String | Unique, shown to manager for inviting members |
| manager_id | UUID | FK → User |
| created_at | Timestamp | |

### 6.3 MessMember
| Field | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| mess_id | UUID | FK → Mess |
| user_id | UUID | FK → User |
| role | Enum | MANAGER \| MEMBER |
| joined_at | Timestamp | |

### 6.4 Meal
| Field | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| mess_id | UUID | FK → Mess |
| member_id | UUID | FK → MessMember |
| date | Date | |
| breakfast_count | Decimal | Supports 0 / 0.5 / 1+ |
| lunch_count | Decimal | |
| dinner_count | Decimal | |
| updated_by | UUID | FK → User (self or manager) |

### 6.5 Expense (Bazaar)
| Field | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| mess_id | UUID | FK → Mess |
| amount | Decimal | BDT |
| date | Date | |
| note | String | Optional |
| receipt_photo_url | String | Optional |
| logged_by | UUID | FK → User (manager) |

### 6.6 Deposit
| Field | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| mess_id | UUID | FK → Mess |
| member_id | UUID | FK → MessMember |
| amount | Decimal | BDT |
| date | Date | |
| logged_by | UUID | FK → User (manager) |

### 6.7 MonthlyCycle
| Field | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| mess_id | UUID | FK → Mess |
| start_date / end_date | Date | |
| status | Enum | OPEN \| CLOSED |
| meal_rate_snapshot | Decimal | Locked at close |
| closed_at | Timestamp | Nullable |

### 6.8 Notice
| Field | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| mess_id | UUID | FK → Mess |
| message | String | |
| posted_by | UUID | FK → User (manager) |
| created_at | Timestamp | |

---

## 7. API Contracts (REST + WebSocket)

All REST endpoints are prefixed with `/api/v1` and require a JWT bearer token except auth endpoints. WebSocket channel is scoped per mess.

### 7.1 Auth
| Method | Endpoint | Description |
|---|---|---|
| POST | `/auth/signup` | Create account |
| POST | `/auth/login` | Returns JWT |

### 7.2 Mess
| Method | Endpoint | Description |
|---|---|---|
| POST | `/mess` | Create mess (caller becomes Manager) |
| POST | `/mess/join` | Join mess via join code |
| GET | `/mess/{id}` | Mess details, members, current cycle |

### 7.3 Meals
| Method | Endpoint | Description |
|---|---|---|
| POST | `/mess/{id}/meals` | Log/update a meal entry for a date |
| GET | `/mess/{id}/meals?month=` | Get meal entries for calendar view |

### 7.4 Expenses & Deposits
| Method | Endpoint | Description |
|---|---|---|
| POST | `/mess/{id}/expenses` | Log a bazaar expense |
| GET | `/mess/{id}/expenses` | List expenses for current cycle |
| POST | `/mess/{id}/deposits` | Log a member deposit |
| GET | `/mess/{id}/deposits` | List deposits for current cycle |

### 7.5 Dues & Cycle
| Method | Endpoint | Description |
|---|---|---|
| GET | `/mess/{id}/dues` | Live computed dues for all members |
| POST | `/mess/{id}/cycle/close` | Manager closes current month |
| GET | `/mess/{id}/cycle/history` | Past closed cycles & summaries |

### 7.6 Notices
| Method | Endpoint | Description |
|---|---|---|
| POST | `/mess/{id}/notices` | Post a notice (manager only) |
| GET | `/mess/{id}/notices` | List active notices |

### 7.7 WebSocket
- `ws/mess/{id}` — authenticated channel; server pushes events: `MEAL_UPDATED`, `EXPENSE_ADDED`, `DEPOSIT_ADDED`, `DUES_RECALCULATED`, `NOTICE_POSTED`, `CYCLE_CLOSED`

---

## 8. Non-Functional Requirements

| Category | Requirement |
|---|---|
| Performance | Live sync latency under 2 seconds; meal log action completes in under 500ms |
| Offline Support | Room-based local cache; meal logging queues and syncs when back online |
| Security | JWT auth, bcrypt password hashing, role-based authorization enforced server-side |
| Data Integrity | Dues calculations must be server-authoritative; client never computes final dues |
| Scalability | Single mess = 5–15 users; architecture should support 1,000+ concurrent messes |
| Availability | Deployed on Railway.app with automatic restarts; target 99% uptime for MVP |

---

## 9. Technical Stack Summary

| Layer | Technology |
|---|---|
| Backend Language | Kotlin |
| Backend Framework | Ktor |
| ORM | Exposed |
| Database | PostgreSQL |
| Auth | JWT (Ktor plugin) |
| Real-time | Ktor WebSockets |
| Hosting | Railway.app |
| Android UI | Kotlin + Jetpack Compose |
| Android Networking | Retrofit + OkHttp (WebSocket client) |
| Local Storage | Room |
| DI | Hilt |
| Architecture | MVVM + Clean Architecture, multi-module |

---

## 10. Post-v1 Roadmap
- Push notifications for notices and dues reminders
- PDF export of monthly summaries
- Multi-language support (Bangla/English toggle)
- Manager handover flow and mess archiving
- Multiple messes per user
- Guest meals and advance meal-off scheduling
- In-app chat
