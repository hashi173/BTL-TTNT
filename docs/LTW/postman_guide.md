# Hashiji Cafe – Postman Testing Guide

## Setup

### 1. Create a Collection

1. Open Postman → **New** → **Collection** → Name it `Hashiji Cafe`
2. Under **Variables** tab, add:
   | Variable | Initial Value |
   |---|---|
   | `base_url` | `http://localhost:8080` |
   | `admin_username` | `admin` |
   | `admin_password` | `123456` |

### 2. Configure Session Authentication

Since the app uses form-based login (Spring Security), all admin requests need a session cookie.

1. Add a **Pre-request Script** at the collection level, or create a dedicated **Login** request:
   - Method: `POST`
   - URL: `{{base_url}}/do-login`
   - Body: `x-www-form-urlencoded`
     - `username` = `admin`
     - `password` = `123456`
2. Postman will automatically store the `JSESSIONID` cookie.
3. Make sure **"Automatically follow redirects"** is **ON** in collection settings.
4. All subsequent requests will send the cookie automatically.

> **Tip:** Run the login request first every time you start a testing session.

---

## Test Cases by Module

---

### 🍵 Products & Categories (Phan)

#### TC-01: Get Home Page (public)
- **GET** `{{base_url}}/`
- Expected: `200 OK`, HTML page with product list

#### TC-02: Filter Products by Category (AJAX fragment)
- **GET** `{{base_url}}/products/fragment?categoryId=<UUID>`
- Expected: `200 OK`, HTML fragment with filtered products
- Get a valid categoryId from TC-01 page source or admin categories list

#### TC-03: Product Detail
- **GET** `{{base_url}}/product/<UUID>`
- Expected: `200 OK`, product page with size and topping selectors

#### TC-04: Admin - List Products
- **GET** `{{base_url}}/admin/products`
- Expected: `200 OK` (requires session cookie from login)

#### TC-05: Admin - Create Product
- **POST** `{{base_url}}/admin/products/save`
- Body: `form-data`
  - `name` = `Test Latte`
  - `description` = `A test product`
  - `category.id` = `<valid category UUID>`
  - `sizes[0].sizeName` = `M`
  - `sizes[0].price` = `45000`
  - `imageFile` = *(leave empty or attach an image file)*
  - `imageUrl` = `https://placehold.co/300`
- Expected: `302` redirect to `/admin/products` with flash "Product saved successfully!"

#### TC-06: Admin - Deactivate Product
- **GET** `{{base_url}}/admin/products/deactivate/<UUID>`
- Expected: `302` redirect

#### TC-07: Admin - Delete Product
- **GET** `{{base_url}}/admin/products/delete/<UUID>`
- Expected: `302` redirect

---

### 🛒 Cart, Checkout, Tracking, Invoice, Orders, History (Hà)

#### TC-08: View Cart (empty)
- **GET** `{{base_url}}/cart`
- Expected: `200 OK`, empty cart page

#### TC-09: Add Item to Cart
- **POST** `{{base_url}}/cart/add`
- Body: `x-www-form-urlencoded`
  - `productId` = `<valid product UUID>`
  - `sizeId` = `<valid size UUID>`
  - `quantity` = `2`
  - `sugar` = `100%`
  - `ice` = `100%`
  - `note` = `extra hot`
- Expected: `302` redirect to `/cart`

#### TC-10: Update Cart Item Quantity
- **POST** `{{base_url}}/cart/update`
- Body: `x-www-form-urlencoded`
  - `index` = `0`
  - `quantity` = `3`
- Expected: `302` redirect to `/cart`

#### TC-11: Remove Cart Item
- **GET** `{{base_url}}/cart/remove/0`
- Expected: `302` redirect to `/cart`

#### TC-12: Show Checkout
- **GET** `{{base_url}}/checkout`
- Prerequisite: Cart must be non-empty (run TC-09 first)
- Expected: `200 OK`, checkout form

#### TC-13: Place Order
- **POST** `{{base_url}}/checkout/place-order`
- Body: `x-www-form-urlencoded`
  - `customerName` = `Nguyen Van A`
  - `phone` = `0901234567`
  - `address` = `123 Le Loi, Hanoi`
  - `note` = `Ring doorbell`
- Expected: `200 OK`, success page showing tracking code (format: `ORD-XXXXXX`)

#### TC-14: Track Order
- **GET** `{{base_url}}/tracking/search?code=ORD-XXXXXX`
- Use tracking code from TC-13
- Expected: `200 OK`, order details shown

#### TC-15: Download Invoice (PDF)
- **GET** `{{base_url}}/invoice/<order-UUID>`
- Expected: `200 OK`, `Content-Type: application/pdf`
- Tip: In Postman click **"Send and Download"** to save the PDF

#### TC-16: Admin - List Orders
- **GET** `{{base_url}}/admin/orders`
- Expected: `200 OK`, order management table

#### TC-17: Admin - Update Order Status
- **POST** `{{base_url}}/admin/orders/<order-UUID>/status`
- Body: `x-www-form-urlencoded`
  - `status` = `CONFIRMED`
- Expected: `302` redirect to `/admin/orders/<UUID>`

#### TC-18: Admin - Cancel Order
- **POST** `{{base_url}}/admin/orders/<order-UUID>/cancel`
- Expected: `302` redirect

---

### 👤 Auth, Users, Toppings, Dashboard (Quỳnh)

#### TC-19: Public - Register User
- **POST** `{{base_url}}/register`
- Body: `x-www-form-urlencoded`
  - `username` = `testuser`
  - `password` = `123456`
  - `confirmPassword` = `123456`
  - `fullName` = `Test User`
  - `email` = `testuser@example.com`
  - `phone` = `0900000000`
- Expected: `302` redirect to `/login`

#### TC-20: Auth - View Profile
- **GET** `{{base_url}}/profile`
- Expected: `200 OK` after logging in as a user

#### TC-21: Auth - Update Profile
- **POST** `{{base_url}}/profile`
- Body: `x-www-form-urlencoded`
  - `fullName` = `Test User Updated`
  - `email` = `updated@example.com`
  - `phone` = `0900000001`
- Expected: `302` redirect to `/profile`

#### TC-22: Auth - My Orders
- **GET** `{{base_url}}/my-orders`
- Expected: `200 OK`, current user's order history

#### TC-23: Admin - List Users
- **GET** `{{base_url}}/admin/users`
- Expected: `200 OK`, user table

#### TC-24: Admin - Toggle User Status
- **POST** `{{base_url}}/admin/users/<user-UUID>/toggle`
- Expected: `302` redirect to `/admin/users`

#### TC-25: Admin - Reset User Password
- **POST** `{{base_url}}/admin/users/<user-UUID>/reset-password`
- Expected: `302` redirect to `/admin/users`

#### TC-26: Admin - Manage Toppings
- **GET** `{{base_url}}/admin/toppings`
- Expected: `200 OK`, topping table and modal form

#### TC-27: Admin - Save Topping
- **POST** `{{base_url}}/admin/toppings/save`
- Body: `x-www-form-urlencoded`
  - `name` = `Extra Shot`
  - `price` = `10000`
- Expected: `302` redirect to `/admin/toppings`

#### TC-28: Admin - Dashboard
- **GET** `{{base_url}}/admin/dashboard`
- Expected: `200 OK`, KPI cards and charts

#### TC-29: Admin - AI Evaluation Dashboard
- **GET** `{{base_url}}/admin/ai/dashboard`
- Expected: `200 OK`, AI metrics and charts

---

## Common Issues

| Problem | Fix |
|---|---|
| `403 Forbidden` on admin routes | Run the login request first to get a session cookie |
| `302` redirect loops | Ensure "Follow redirects" is ON in Postman settings |
| Empty cart redirect | Add items to cart before testing checkout |
| `UUID` format errors | UUIDs must be format `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx` |
| PDF not rendering | Use "Send and Download" in Postman for binary responses |
