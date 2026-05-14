# Hashiji Cafe - API Reference

> **Base URL:** `http://localhost:8080`
>
> **Auth methods:**
> - Session cookie from Spring Security form login.
> - `ROLE_ADMIN` is required for `/admin/**`.
> - Public storefront, cart, checkout, tracking, invoice, login, and register pages do not require login.

---

## Member Assignment

| Member | Modules |
|---|---|
| **Phan** | Products, Categories |
| **Ha** | Orders, Cart, Checkout, Tracking, Invoice, History |
| **Quynh** | Auth, Users, Profile, Toppings, Dashboard, AI Dashboard |

> Recruitment, Ingredients, and Work Shifts are not in the current application scope.

---

## Phan - Products, Categories

### Products (Public)

| # | Method | URL | Auth | Description |
|---|---|---|---|---|
| 1 | GET | `/` | Public | Home page with products, categories, search, and recommendations |
| 2 | GET | `/products/fragment?categoryId=&keyword=` | Public | HTMX fragment for menu filtering/search |
| 3 | GET | `/product/{id}` | Public | Product detail page with sizes and toppings |

### Products (Admin)

| # | Method | URL | Auth | Description |
|---|---|---|---|---|
| 4 | GET | `/admin/products` | ADMIN | Product list split by active/inactive, with search and pagination |
| 5 | GET | `/admin/products/new` | ADMIN | Product create form |
| 6 | POST | `/admin/products/save` | ADMIN | Create/update product, including upload file or image URL |
| 7 | GET | `/admin/products/edit/{id}` | ADMIN | Product edit form |
| 8 | GET | `/admin/products/activate/{id}` | ADMIN | Set product active |
| 9 | GET | `/admin/products/deactivate/{id}` | ADMIN | Set product inactive |
| 10 | GET | `/admin/products/delete/{id}` | ADMIN | Delete product if safe, otherwise deactivate through service fallback |

### Categories (Admin)

| # | Method | URL | Auth | Description |
|---|---|---|---|---|
| 11 | GET | `/admin/categories` | ADMIN | Category list with search and pagination |
| 12 | GET | `/admin/categories/new` | ADMIN | Category create form |
| 13 | POST | `/admin/categories/save` | ADMIN | Create/update category |
| 14 | GET | `/admin/categories/edit/{id}` | ADMIN | Category edit form |
| 15 | GET | `/admin/categories/delete/{id}` | ADMIN | Delete category; service prevents unsafe deletion through DB constraints |

---

## Ha - Orders, Cart, Checkout, Tracking, Invoice, History

### Cart (Public)

| # | Method | URL | Auth | Description |
|---|---|---|---|---|
| 16 | GET | `/cart` | Public | View session cart |
| 17 | POST | `/cart/add` | Public | Add product with `productId`, optional `sizeId`, `quantity`, toppings, sugar, ice, note |
| 18 | POST | `/cart/update` | Public | Update quantity by cart index |
| 19 | GET | `/cart/remove/{index}` | Public | Remove cart item by index |

### Checkout (Public)

| # | Method | URL | Auth | Description |
|---|---|---|---|---|
| 20 | GET | `/checkout` | Public | Checkout form; redirects to `/cart` if cart is empty |
| 21 | POST | `/checkout/place-order` | Public | Place order with `customerName`, `phone`, `address`, optional `note` |

### Tracking & Invoice (Public)

| # | Method | URL | Auth | Description |
|---|---|---|---|---|
| 22 | GET | `/tracking` | Public | Order tracking page |
| 23 | GET | `/tracking/search?code=` | Public | Lookup order by tracking code |
| 24 | POST | `/tracking/cancel` | Public | Cancel a `PENDING` order with `orderId` and `trackingCode` |
| 25 | GET | `/invoice/{orderId}` | Public | Download order invoice PDF |

### Orders (Admin)

| # | Method | URL | Auth | Description |
|---|---|---|---|---|
| 26 | GET | `/admin/orders` | ADMIN | Active/history order list with search, status filter, pagination |
| 27 | GET | `/admin/orders/{id}` | ADMIN | Order detail |
| 28 | POST | `/admin/orders/{id}/status` | ADMIN | Update order status |
| 29 | POST | `/admin/orders/{id}/cancel` | ADMIN | Cancel order |

### History (Admin)

| # | Method | URL | Auth | Description |
|---|---|---|---|---|
| 30 | GET | `/admin/history` | ADMIN | Monthly revenue overview with Chart.js |
| 31 | GET | `/admin/history/details?month=&year=` | ADMIN | Completed orders and revenue for one month |

---

## Quynh - Auth, Users, Profile, Toppings, Dashboard, AI

### Auth & Public Pages

| # | Method | URL | Auth | Description |
|---|---|---|---|---|
| 32 | GET | `/login` | Public | Login page |
| 33 | POST | `/do-login` | Public | Spring Security login processing |
| 34 | GET | `/logout` | Auth | Logout |
| 35 | GET | `/register` | Public | Registration form |
| 36 | POST | `/register` | Public | Create user account |
| 37 | GET | `/about` | Public | Redirect to home `#about` section |
| 38 | GET | `/info` | Public | Redirect to home `#info` section |

### User Pages

| # | Method | URL | Auth | Description |
|---|---|---|---|---|
| 39 | GET | `/profile` | Auth | View profile |
| 40 | POST | `/profile` | Auth | Update profile fields |
| 41 | GET | `/my-orders?page=` | Auth | Current user's order history |

### Admin Users

| # | Method | URL | Auth | Description |
|---|---|---|---|---|
| 42 | GET | `/admin/users` | ADMIN | User list with search and pagination |
| 43 | POST | `/admin/users/{id}/toggle` | ADMIN | Toggle user active status |
| 44 | POST | `/admin/users/{id}/reset-password` | ADMIN | Reset user password to `123456` |

### Toppings (Admin)

| # | Method | URL | Auth | Description |
|---|---|---|---|---|
| 45 | GET | `/admin/toppings` | ADMIN | Topping list |
| 46 | POST | `/admin/toppings/save` | ADMIN | Create/update topping |
| 47 | GET | `/admin/toppings/delete/{id}` | ADMIN | Delete topping |

### Dashboard & AI (Admin)

| # | Method | URL | Auth | Description |
|---|---|---|---|---|
| 48 | GET | `/admin/dashboard` | ADMIN | KPI dashboard and top products |
| 49 | GET | `/admin/ai/dashboard` | ADMIN | AI evaluation dashboard |
| 50 | GET | `/admin/ai/api/evaluate` | ADMIN | Recommendation evaluation metrics JSON |
| 51 | GET | `/admin/ai/api/baselines` | ADMIN | Baseline comparison JSON |
| 52 | GET | `/admin/ai/api/ablation-weights` | ADMIN | CF/RB weight sweep JSON |
| 53 | GET | `/admin/ai/api/ablation-k` | ADMIN | K sweep JSON |
| 54 | GET | `/admin/ai/api/confusion-matrix` | ADMIN | Naive Bayes confusion matrix JSON |
| 55 | GET | `/admin/ai/api/pr-curve` | ADMIN | Precision/Recall curve JSON |
| 56 | GET | `/admin/ai/api/f1-curve` | ADMIN | F1@K curve JSON |
| 57 | GET | `/admin/ai/api/keywords` | ADMIN | Naive Bayes category keywords JSON |

---

## Summary

| Member | Main routes |
|---|---|
| **Phan** | `/`, `/product`, `/products/fragment`, `/admin/products`, `/admin/categories` |
| **Ha** | `/cart`, `/checkout`, `/tracking`, `/invoice`, `/admin/orders`, `/admin/history` |
| **Quynh** | `/login`, `/register`, `/profile`, `/my-orders`, `/admin/users`, `/admin/toppings`, `/admin/dashboard`, `/admin/ai` |
