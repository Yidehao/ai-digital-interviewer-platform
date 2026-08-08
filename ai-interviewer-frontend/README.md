### README – AI Interviewer Frontend (Admin Panel)

## 1. Overview

This project is a **frontend admin panel** for an “AI Interviewer” system.  
It is intentionally built as a **pure static site**:

- Entry: plain HTML (`pages/a/admin.html`)
- Framework: **Vue 2** + **vue-router**
- UI library: **Element UI 2.x**
- Networking: **Axios**, with a small API wrapper layer under `js/apis/admin/`
- Serving: any static HTTP server (e.g. **VS Code Live Server extension**)

There is **no Webpack/Vite build step**. Everything is loaded directly in the browser via `<script>` and `<link>` tags.

Students can:

- Open the HTML entry with Live Server
- Inspect how Vue + Element UI are wired up **without a build tool**
- Learn how routing, API calls, and UI components cooperate in a real admin dashboard

---

## 2. Tech Stack at a Glance

- **Vue 2 (script include)**
  - `libs/vue.min.js`
  - `libs/vue-router.js`
- **Element UI 2.15.x (script include)**
  - `libs/element-2.15.13/index.js`
  - `css/theme/style/theme/index.css` (Element UI theme)
- **Axios**
  - `libs/axios.min.js`
  - Central config: `js/request.js`
- **HTTP Server**
  - Any static server is fine (e.g. VS Code Live Server)
- **Backend API (expected)**
  - The frontend expects an API server at `http://localhost:8080` (configurable in the code)

---

## 3. Directory Overview (Frontend-Relevant Parts)

High-level structure (only main parts for this admin panel):

- `pages/a/`
  - `admin.html` – **Entry HTML** for the admin SPA
  - `adminrouter.js` – route & menu definitions
  - `dashboard.vue` – dashboard page
  - `candidate/` – candidate management pages (`list.vue`, `create.vue`, `interviewRecord.vue`)
  - `job/` – job management pages (`list.vue`, `create.vue`, `signMng.vue`)
  - `settings/` – AI settings & question library (`aiMng.vue`, `questionLib.vue`)
  - `adminInfo.vue` – admin account info page
- `js/`
  - `app.js` – global app utility object (`window.app`)
  - `request.js` – Axios instance & interceptors
  - `cookieUtils.js` – simple cookie & user info helpers
  - `apis/admin/` – **API wrappers** for each module:
    - `candidateApi.js`, `jobApi.js`, `questionLibApi.js`, `interviewerApi.js`, `interviewerRecordApi.js`
- `css/`
  - `admin/mng.css` – main layout & theme for the admin panel
  - `app.css` – global resets and typography
  - Other CSS for legacy/extra pages (not needed for just admin, but good for reference)

- `libs/`
  - `vue.min.js`, `vue-router.js`, `axios.min.js`
  - `element-2.15.13/` – Element UI JS/CSS
  - Other UI/editor libs (Cherry Markdown, Summernote, etc.)  
    These are used mainly in content-editing pages.

---

## 4. How the Admin App Boots (Step-by-Step)

### 4.1 Entry HTML: `pages/a/admin.html`

This file is the **front door** of the SPA:

1. Loads **CSS**:
   - Element UI theme: `css/theme/style/theme/index.css`
   - Global/app styles: `css/app.css`
   - Admin layout/styles: `css/admin/mng.css`

2. Loads **JS libraries** in `<script>` tags:
   - Vue: `libs/vue.min.js`
   - Axios: `libs/axios.min.js`
   - jQuery, cookies, etc. (for utilities)
   - Element UI: `libs/element-2.15.13/index.js`
   - Vue Router: `libs/vue-router.js`
   - `libs/httpVueLoader.js` – **loads `.vue` files in the browser** (no bundler)
   - Global app utilities: `js/app.js`, `js/cookieUtils.js`, `js/request.js`
   - API wrappers: `js/apis/admin/*.js`
   - Router config: `pages/a/adminrouter.js`

3. Creates a **Vue instance** mounted on `#dashboardPage`:
   - Uses `VueRouter` with routes defined in `adminrouter.js`
   - Uses `menuList` from `adminrouter.js` to render the left-hand navigation (Element `el-menu` / `el-submenu` / `el-menu-item`)

In code:

- `<router-view>` inside `admin.html` is where the current page (`*.vue` component) is rendered.
- `<el-aside>` + `<el-menu>` render the sidebar navigation using `myrouter.menuList`.

### 4.2 Routing: `pages/a/adminrouter.js`

`adminrouter.js` defines two things:

1. **Routes**:

   ```js
   window.myrouter = {
     routes: [
       { path: '/', component: httpVueLoader('candidate/list.vue') },
       { path: '/dashboard', component: httpVueLoader('dashboard.vue') },
       { path: '/candidateMng/candidateList', component: httpVueLoader('candidate/list.vue') },
       { path: '/candidateMng/candidateCreate', component: httpVueLoader('candidate/create.vue') },
       { path: '/candidateMng/interviewRecord', component: httpVueLoader('candidate/interviewRecord.vue') },
       { path: '/jobMng/jobList', component: httpVueLoader('job/list.vue') },
       { path: '/jobMng/jobCreate', component: httpVueLoader('job/create.vue') },
       { path: '/aiMng/aiSettings', component: httpVueLoader('settings/aiMng.vue') },
       { path: '/aiMng/questionLib', component: httpVueLoader('settings/questionLib.vue') },
     ],
   ```

   - **Notice** how `httpVueLoader('path/to/file.vue')` is used:  
     this loads `.vue` files at runtime, so there is no bundling step.

2. **Sidebar menu configuration** (`menuList`):
   ```js
   menuList: [
     {
       title: "Candidate Management",
       path: "/candidateMng",
       index: "candidateMng",
       icon: "el-icon-s-order",
       children: [
         {
           title: "Candidate List",
           path: "/candidateMng/candidateList",
           index: "candidateList",
           role: 1,
         },
         {
           title: "Create Candidate",
           path: "/candidateMng/candidateCreate",
           index: "candidateCreate",
           role: 1,
         },
         {
           title: "Interview Records",
           path: "/candidateMng/interviewRecord",
           index: "interviewRecord",
           role: 1,
         },
       ],
       role: 1,
     },
     // Job Management, AI Interviewer, etc.
   ];
   ```

`admin.html` reads `myrouter.routes` and `myrouter.menuList` to wire up Vue Router and the sidebar UI.

---

## 5. Vue Components & HTTPVueLoader

Each page (e.g. candidate list / job create / AI settings) is a `.vue` file under `pages/a/`:

- Typical structure:

  ```vue
  <template>...</template>
  <script>
  module.exports = { ... }
  </script>
  <style>
  ...
  </style>
  ```

- These components:
  - Are **not** compiled by a bundler.
  - Are loaded in the browser via **httpVueLoader**:
    - `httpVueLoader('candidate/list.vue')` fetches and parses the `.vue` file at runtime.
  - Use **Element UI components** extensively (e.g. `el-form`, `el-table`, `el-dialog`).

Examples:

- `pages/a/candidate/list.vue` – Candidate table, pagination, and filters.
- `pages/a/candidate/create.vue` – Form to create / edit candidates.
- `pages/a/job/list.vue` – Job list table, pagination, CRUD buttons.
- `pages/a/job/create.vue` – Create / edit job, select AI interviewer & prompt.
- `pages/a/settings/aiMng.vue` – Manage “digital human” interviewers and their avatars.
- `pages/a/settings/questionLib.vue` – Manage interview questions, answers, and AI video uploads.

---

## 6. API Layer & Request Flow

### 6.1 Axios instance: `js/request.js`

- Creates a single **Axios instance**:

  ```js
  const instance = axios.create({
    baseURL: "http://localhost:9090", // or your API base
    withCredentials: true,
    timeout: 5000,
  });
  ```

- **Request interceptor**:
  - Reads user info & token from `cookieUtils`
  - Adds headers: `headerUserId`, `headerUserToken` (if available)

- **Response interceptor**:
  - Checks `res.status` from the API response body
  - On error:
    - For `status == 599`, aggregates validation errors and shows them via `swal("Error", ...)`
    - Otherwise shows general error
  - On success (`status == 200`), returns `res.data` (simplifying API handling in components)

### 6.2 API wrappers: `js/apis/admin/*.js`

Each file exports a small `window.*Api` object, for example:

- `js/apis/admin/jobApi.js`:
  ```js
  window.jobApi = {
    createOrUpdate: function(bo) {
      return instance({
        url: '/job/createOrUpdate',
        method: 'post',
        data: bo
      })
    },
    delete: function(jobId) { ... },
    list: function(bo) { ... },
    nameList: function() { ... },
    detail: function(jobId) { ... },
  }
  ```

Within Vue components:

- The component calls `jobApi.list(...)`, `candidateApi.createOrUpdate(...)`, etc.
- These return Axios Promises, and the result drives the UI (tables, forms, dialogs, etc.).

---

## 7. Authentication & Global App Utilities

### 7.1 `js/app.js` – `window.app`

This file defines a global `window.app` object that encapsulates:

- **Frontend URLs**:
  - `portalIndexUrl`, `writerLoginUrl`, etc. (now pointing to `http://localhost:8080/...`).
- **Cookie domain config**:
  - `cookieDomain: ""` (empty for localhost; can be set for real domains).
- **User status helpers**:
  - `judgeUserLoginStatus(pageVue)`:
    - Reads cookies (`utoken`, `uid`)
    - Optionally fetches user info from backend
    - Handles banned user state, etc.
- **Utility functions**:
  - `saveUserInfo`, `fetchUserInfo`, `deleteUserInfo` – using `sessionStorage`
  - `getCookie`, `setCookie`, `deleteCookie`
  - `getUrlParam`, `getPageName`
  - `getDateBeforeNow(stringTime)` – converts a timestamp to “X days ago” style strings

Many of these are generic enough for students to reuse in other projects.

### 7.2 `js/cookieUtils.js`

- Provides a simpler abstraction over cookies + user info:
  - `getUserInfo()`, `saveUserInfo()`, `removeUserInfo()`
  - `getToken()`, `setToken()`, `removeToken()`

These are used by `js/request.js` to inject headers into outgoing requests.

---

## 8. Styling & Layout

### 8.1 Admin-specific CSS: `css/admin/mng.css`

Defines the **overall look and feel** of the admin:

- Layout:
  - `.dashboard-in-one` – full height container (`height: 100vh`)
  - `.menu-container` – left sidebar (Element `el-aside`)
  - `.header-container` – top header with glassmorphism
  - `.main-container` – main content area with light gray background

- Sidebar (Apple-style inspired):
  - Transparent/blurred background (`backdrop-filter`)
  - Slight border + shadow
  - Hover & active states for `el-menu-item`, `el-submenu__title`

- Cards, tables, inputs, buttons:
  - Customized `el-card`, `el-table`, `el-input`, `el-button` for a modern, clean style

### 8.2 Global CSS: `css/app.css`

- Global font & typography
- Utility classes for text decoration, etc.
- Browser scrollbar tweaks (in English now)

Students can learn:

- How to override Element UI’s default styles with custom CSS.
- How to build a modern dashboard layout without Tailwind/Bootstrap, just with raw CSS + Element UI.

---

## 9. Running the Project with Live Server

### 9.1 Requirements

- **VS Code**
- **Live Server extension** (Ritwick Dey)
- Optionally: a backend API server running on `http://localhost:8080` (or another URL that you configure in `js/request.js`)

### 9.2 Steps

1. **Open the project folder** in VS Code:
   - `/Users/.../ai-interviewer-frontend/`

2. **Locate the admin entry HTML**:
   - `pages/a/admin.html`

3. **Right-click `admin.html` → “Open with Live Server”**
   - Live Server will start a static HTTP server (e.g. `http://127.0.0.1:5500/...`).
   - Your browser will open the admin dashboard.

4. **Connect to a backend (optional but recommended)**:
   - Check `js/request.js`:
     - Set `baseURL` to your API server, e.g.:
       ```js
       const instance = axios.create({
         baseURL: "http://localhost:8080",
         ...
       });
       ```
   - Run your backend so that endpoints like `/job/list`, `/candidate/list`, `/questionLib/list`, etc., are available.

5. **Explore**:
   - Use the left sidebar to open **Candidate**, **Job**, **AI Interviewer**, **Question Library** pages.
   - Inspect network requests in browser dev tools to see how the frontend interacts with the backend.

---

## 10. How to Study / Extend the Project (for Students)

To understand the project deeply, students can follow this roadmap:

1. **Start from `admin.html`**
   - See how CSS and JS are linked.
   - Identify the main containers: `#dashboardPage`, `<router-view>`, `<el-menu>`.

2. **Inspect Routing (`adminrouter.js`)**
   - Understand how `routes` map to `.vue` files.
   - Understand how `menuList` maps to the sidebar UI.

3. **Open a simple page component**
   - E.g. `pages/a/dashboard.vue`:
     - Small component to see basic `template` / `script` / `style`.

4. **Open a fully-featured component**
   - E.g. `pages/a/candidate/list.vue`:
     - Study:
       - How `data()` returns state (search form, pagination info, candidate list).
       - How `mounted()` triggers `initCandidateList`.
       - How `candidateApi.list(...)` is called and how response data is used.
       - How Element UI’s `el-table`, `el-pagination`, and `el-dialog` work together.

5. **Dive into the API wrapper**
   - `js/apis/admin/candidateApi.js`:
     - See how endpoints and query parameters are structured.
     - Compare with backend API spec.

6. **Understand `js/request.js`**
   - Learn about Axios interceptors.
   - See how a single Axios instance can centralize:
     - `baseURL`
     - auth headers
     - error handling

7. **Authentication & Cookies**
   - Review `js/app.js` and `js/cookieUtils.js`
   - Learn how login state can be:
     - Persisted with cookies/sessionStorage
     - Injected into HTTP headers

8. **Styling & UX**
   - Explore `css/admin/mng.css`
   - See how simple CSS overrides can customize a third-party UI library.

9. **Extend**
   - Add a new menu item & route in `adminrouter.js`
   - Create a new `.vue` page under `pages/a/yourFeature/yourPage.vue`
   - Add a corresponding API in `js/apis/admin/yourFeatureApi.js`
   - Wire everything together and test via Live Server.
