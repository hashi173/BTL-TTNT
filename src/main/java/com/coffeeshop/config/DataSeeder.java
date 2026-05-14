package com.coffeeshop.config;

import com.coffeeshop.entity.Role;
import com.coffeeshop.repository.CategoryRepository;
import com.coffeeshop.repository.OrderItemRepository;
import com.coffeeshop.repository.OrderRepository;
import com.coffeeshop.repository.ProductRepository;
import com.coffeeshop.repository.ProductSizeRepository;
import com.coffeeshop.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed-data", havingValue = "true")
public class DataSeeder implements CommandLineRunner {

    private static final String EXTERNAL_IMAGE_BASE_URL = "https://loremflickr.com/640/640/";

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductSizeRepository productSizeRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CacheManager cacheManager;

    private int categoryCounter = 0;
    private int productCounter = 0;
    private int orderCounter = 0;

    @Override
    public void run(String... args) {
        cleanupData();
        categoryCounter = 0;
        productCounter = 0;
        orderCounter = 0;
        seedProducts();
        seedUsers();
        seedHistory();
        seedActiveData();
        cacheManager.getCacheNames().forEach(name -> {
            var cache = cacheManager.getCache(name);
            if (cache != null) cache.clear();
        });
        log.info("All caches cleared after seeding.");
    }

    private void cleanupData() {
        try {
            orderItemRepository.deleteAll();
            orderRepository.deleteAll();
            userRepository.deleteAll();
            productSizeRepository.deleteAll();
            productRepository.deleteAll();
            categoryRepository.deleteAll();
        } catch (Exception e) {
            log.warn("Warning during cleanup: {}", e.getMessage());
        }
    }

    private void seedUsers() {
        // Admin user
        if (userRepository.findByUsername("admin").isEmpty()) {
            com.coffeeshop.entity.User admin = new com.coffeeshop.entity.User();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("123456"));
            admin.setFullName("System Administrator");
            admin.setRole(Role.ADMIN);
            admin.setUserCode("ADM01");
            admin.setActive(true);
            userRepository.save(admin);
        }

        // 30 regular users with diverse profiles for collaborative filtering
        String[][] userData = {
            // Coffee cluster (users 0-9)
            {"alice", "Alice Nguyen", "alice@example.com", "0901234501"},
            {"bob", "Bob Tran", "bob@example.com", "0901234502"},
            {"charlie", "Charlie Le", "charlie@example.com", "0901234503"},
            {"diana", "Diana Pham", "diana@example.com", "0901234504"},
            {"eric", "Eric Vo", "eric@example.com", "0901234505"},
            {"frank", "Frank Hoang", "frank@example.com", "0901234506"},
            {"grace", "Grace Do", "grace@example.com", "0901234507"},
            {"henry", "Henry Bui", "henry@example.com", "0901234508"},
            {"ivy", "Ivy Lam", "ivy@example.com", "0901234509"},
            {"jack", "Jack Mai", "jack@example.com", "0901234510"},
            // Tea cluster (users 10-19)
            {"kate", "Kate Duong", "kate@example.com", "0901234511"},
            {"leo", "Leo Ngo", "leo@example.com", "0901234512"},
            {"mia", "Mia Vu", "mia@example.com", "0901234513"},
            {"noah", "Noah Dinh", "noah@example.com", "0901234514"},
            {"olivia", "Olivia Ly", "olivia@example.com", "0901234515"},
            {"peter", "Peter Quach", "peter@example.com", "0901234516"},
            {"quinn", "Quinn Tang", "quinn@example.com", "0901234517"},
            {"rose", "Rose Ha", "rose@example.com", "0901234518"},
            {"sam", "Sam Trieu", "sam@example.com", "0901234519"},
            {"tina", "Tina Luu", "tina@example.com", "0901234520"},
            // Smoothie cluster (users 20-24)
            {"uma", "Uma Nguyen", "uma@example.com", "0901234521"},
            {"victor", "Victor Tran", "victor@example.com", "0901234522"},
            {"wendy", "Wendy Le", "wendy@example.com", "0901234523"},
            {"xavier", "Xavier Pham", "xavier@example.com", "0901234524"},
            {"yuki", "Yuki Vo", "yuki@example.com", "0901234525"},
            // Juice cluster (users 25-29)
            {"zoe", "Zoe Hoang", "zoe@example.com", "0901234526"},
            {"anh", "Anh Do", "anh@example.com", "0901234527"},
            {"binh", "Binh Bui", "binh@example.com", "0901234528"},
            {"cuong", "Cuong Lam", "cuong@example.com", "0901234529"},
            {"dung", "Dung Mai", "dung@example.com", "0901234530"},
        };

        for (int i = 0; i < userData.length; i++) {
            String username = userData[i][0];
            if (userRepository.findByUsername(username).isEmpty()) {
                com.coffeeshop.entity.User user = new com.coffeeshop.entity.User();
                user.setUsername(username);
                user.setPassword(passwordEncoder.encode("123456"));
                user.setFullName(userData[i][1]);
                user.setEmail(userData[i][2]);
                user.setPhone(userData[i][3]);
                user.setRole(Role.USER);
                user.setUserCode(String.format("USR-%03d", i + 1));
                user.setActive(true);
                userRepository.save(user);
            }
        }
        log.info("Users seeded: 1 admin + 30 regular users.");
    }

    private void seedHistory() {
        List<com.coffeeshop.entity.User> allUsers = userRepository.findAll().stream()
                .filter(u -> u.isActive() && u.getRole() == Role.USER).toList();
        List<com.coffeeshop.entity.Product> products = productRepository.findAll();
        if (allUsers.isEmpty() || products.isEmpty()) return;

        Random rand = new Random(20260501L);
        LocalDate today = LocalDate.now();
        int totalProducts = products.size();

        // Product index ranges per cluster (will be computed after products are seeded)
        // Coffee: 0-14, Tea: 15-29, Smoothie: 30-39, Juice: 40-49
        int coffeeEnd = Math.min(15, totalProducts);
        int teaEnd = Math.min(30, totalProducts);
        int smoothieEnd = Math.min(40, totalProducts);
        int juiceEnd = totalProducts;

        // Build preference arrays per cluster
        int[] coffeeProducts = range(0, coffeeEnd);
        int[] teaProducts = range(coffeeEnd, teaEnd);
        int[] smoothieProducts = range(teaEnd, smoothieEnd);
        int[] juiceProducts = range(smoothieEnd, juiceEnd);

        // Assign preference profiles per user index (30 users)
        // Users 0-9: coffee cluster, 10-19: tea, 20-24: smoothie, 25-29: juice
        Map<Integer, int[]> userPreferences = new HashMap<>();
        for (int i = 0; i < 10; i++) userPreferences.put(i, coffeeProducts);
        for (int i = 10; i < 20; i++) userPreferences.put(i, teaProducts);
        for (int i = 20; i < 25; i++) userPreferences.put(i, smoothieProducts);
        for (int i = 25; i < 30; i++) userPreferences.put(i, juiceProducts);

        for (int offset = 9; offset >= 0; offset--) {
            YearMonth period = YearMonth.from(today.minusMonths(offset));
            int month = period.getMonthValue();
            int year = period.getYear();
            int ordersCount = 150 + ((9 - offset) % 4) * 20 + (offset == 0 ? 15 : 0);

            for (int sequence = 1; sequence <= ordersCount; sequence++) {
                int safeDay = 1 + (((sequence - 1) * 2) % period.lengthOfMonth());
                LocalDate orderDate = period.atDay(safeDay);
                if (period.equals(YearMonth.from(today)) && orderDate.isAfter(today)) {
                    orderDate = today.minusDays((sequence - 1) % Math.max(today.getDayOfMonth(), 1));
                }

                int userIndex = (sequence - 1) % allUsers.size();
                com.coffeeshop.entity.User orderUser = allUsers.get(userIndex);
                int[] prefs = userPreferences.getOrDefault(userIndex, coffeeProducts);

                seedCompletedOrder(orderUser, products, rand,
                        orderDate.atTime(8 + ((sequence - 1) % 10), ((sequence - 1) * 7) % 60),
                        month, sequence, prefs);
            }

            log.info("Seeded {} history orders for {}/{}", ordersCount, month, year);
        }
        log.info("History data seeded for the last 10 months.");
    }

    private int[] range(int start, int end) {
        int[] arr = new int[end - start];
        for (int i = 0; i < arr.length; i++) arr[i] = start + i;
        return arr;
    }

    private void seedCompletedOrder(com.coffeeshop.entity.User user,
            List<com.coffeeshop.entity.Product> products,
            Random rand, java.time.LocalDateTime createdAt,
            int month, int sequence, int[] preferredIndices) {

        com.coffeeshop.entity.Order order = new com.coffeeshop.entity.Order();
        order.setUser(user);
        order.setCustomerName(user.getFullName());
        order.setOrderType(sequence % 2 == 0 ? "Delivery" : "Takeaway");
        order.setStatus(com.coffeeshop.entity.OrderStatus.COMPLETED);
        order.setOrderStatus(com.coffeeshop.entity.OrderStatus.COMPLETED.name());
        order.setCreatedAt(createdAt);

        double total = 0;
        int itemsCount = 1 + rand.nextInt(2);
        List<com.coffeeshop.entity.OrderItem> details = new java.util.ArrayList<>();

        for (int index = 0; index < itemsCount; index++) {
            // 70% chance pick from preferred products, 30% random
            int productIndex;
            if (rand.nextDouble() < 0.7) {
                productIndex = preferredIndices[rand.nextInt(preferredIndices.length)];
            } else {
                productIndex = rand.nextInt(products.size());
            }
            productIndex = productIndex % products.size();

            com.coffeeshop.entity.Product product = products.get(productIndex);
            double basePrice = product.getBasePrice() != null
                    ? product.getBasePrice().doubleValue() : 40_000.0;
            double price = basePrice + (rand.nextInt(3) * 5_000);
            int quantity = 1 + ((sequence + index) % 2);

            com.coffeeshop.entity.OrderItem item = new com.coffeeshop.entity.OrderItem();
            item.setOrder(order);
            item.setProduct(product);
            item.setSnapshotProductName(product.getName());
            item.setQuantity(quantity);
            item.setSnapshotUnitPrice(BigDecimal.valueOf(price));
            item.setSubTotal(BigDecimal.valueOf(price * quantity));
            total += price * quantity;
            details.add(item);
        }

        order.setTotalAmount(total);
        order.setGrandTotal(BigDecimal.valueOf(total));
        order.setTrackingCode(String.format("ORD-%06d", ++orderCounter));
        com.coffeeshop.entity.Order saved = orderRepository.save(order);

        for (com.coffeeshop.entity.OrderItem item : details) {
            item.setOrder(saved);
            orderItemRepository.save(item);
        }
    }

    private void seedActiveData() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        List<com.coffeeshop.entity.Product> products = productRepository.findAll();
        Random rand = new Random(20260501L + 1);
        com.coffeeshop.entity.User adminUser = userRepository.findByUsername("admin").orElse(null);

        for (int i = 0; i < 5; i++) {
            com.coffeeshop.entity.Order order = new com.coffeeshop.entity.Order();
            order.setUser(adminUser);
            order.setCustomerName("Active Customer " + (i + 1));
            order.setOrderType("Delivery");

            com.coffeeshop.entity.OrderStatus status = (i % 2 == 0)
                    ? com.coffeeshop.entity.OrderStatus.PENDING
                    : com.coffeeshop.entity.OrderStatus.CONFIRMED;
            order.setStatus(status);
            order.setOrderStatus(status.name());
            order.setCreatedAt(now.minusMinutes(10 + (i * 15)));

            double total = 0;
            int itemsCount = 1 + rand.nextInt(2);
            List<com.coffeeshop.entity.OrderItem> details = new java.util.ArrayList<>();
            for (int k = 0; k < itemsCount; k++) {
                com.coffeeshop.entity.Product product = products.get(rand.nextInt(products.size()));
                com.coffeeshop.entity.OrderItem item = new com.coffeeshop.entity.OrderItem();
                item.setOrder(order);
                item.setProduct(product);
                item.setSnapshotProductName(product.getName());
                item.setQuantity(1);
                item.setSnapshotUnitPrice(BigDecimal.valueOf(45_000.0));
                item.setSubTotal(BigDecimal.valueOf(45_000.0));
                total += 45_000.0;
                details.add(item);
            }

            order.setTotalAmount(total);
            order.setGrandTotal(BigDecimal.valueOf(total));
            order.setTrackingCode(String.format("ORD-%06d", ++orderCounter));
            com.coffeeshop.entity.Order saved = orderRepository.save(order);
            for (com.coffeeshop.entity.OrderItem item : details) {
                item.setOrder(saved);
                orderItemRepository.save(item);
            }
        }
        log.info("Active orders seeded.");
    }

    private void seedProducts() {
        com.coffeeshop.entity.Category coffee = createDetailsCategory("Coffee", "Premium beans from highlands");
        com.coffeeshop.entity.Category tea = createDetailsCategory("Tea", "Organic tea leaves");
        com.coffeeshop.entity.Category smoothie = createDetailsCategory("Smoothie", "Milk blended drinks");
        com.coffeeshop.entity.Category juice = createDetailsCategory("Juice", "Fresh pressed fruits");

        // COFFEE (15 products)
        createProduct("Cafe Latte", "Creamy espresso with steamed milk foam art.",
                "/images/products/CaffeLatte.png", coffee, 55000.0);
        createProduct("Espresso", "Intense double-shot espresso from Arabica beans.",
                "/images/products/Espresso.png", coffee, 45000.0);
        createProduct("Cappuccino", "Rich espresso topped with thick milk foam.",
                externalProductImage("cappuccino,coffee", 1003), coffee, 55000.0);
        createProduct("Americano", "Smooth espresso diluted with hot water.",
                externalProductImage("americano,coffee", 1004), coffee, 42000.0);
        createProduct("Mocha", "Espresso with chocolate syrup and steamed milk.",
                externalProductImage("mocha,coffee", 1005), coffee, 60000.0);
        createProduct("Caramel Macchiato", "Espresso with vanilla and caramel drizzle.",
                externalProductImage("caramel,macchiato,coffee", 1006), coffee, 62000.0);
        createProduct("Flat White", "Velvety microfoam over double espresso.",
                externalProductImage("flatwhite,coffee", 1007), coffee, 58000.0);
        createProduct("Cold Brew", "Slow-steeped cold coffee, smooth and bold.",
                externalProductImage("coldbrew,coffee", 1008), coffee, 50000.0);
        createProduct("Vietnamese Coffee", "Traditional drip coffee with condensed milk.",
                externalProductImage("vietnamese,coffee", 1009), coffee, 40000.0);
        createProduct("Affogato", "Vanilla ice cream drowned in hot espresso.",
                externalProductImage("affogato,dessert", 1010), coffee, 65000.0);
        createProduct("Hazelnut Latte", "Latte with roasted hazelnut flavor.",
                externalProductImage("hazelnut,latte,coffee", 1011), coffee, 60000.0);
        createProduct("Coconut Coffee", "Coffee blended with fresh coconut cream.",
                externalProductImage("coconut,coffee", 1012), coffee, 58000.0);
        createProduct("Matcha Espresso", "Fusion of matcha and espresso with milk.",
                externalProductImage("matcha,espresso", 1013), coffee, 62000.0);
        createProduct("Salted Caramel Coffee", "Coffee with sea salt caramel sauce.",
                externalProductImage("saltedcaramel,coffee", 1014), coffee, 63000.0);
        createProduct("Iced Coffee", "Chilled coffee served over ice.",
                externalProductImage("iced,coffee", 1015), coffee, 38000.0);

        // TEA (15 products)
        createProduct("Peach Tea", "Refreshing peach tea with fresh peach slices.",
                "/images/products/PeachTea.png", tea, 55000.0);
        createProduct("Sakura Blossom Tea", "Delicate cherry blossom infused green tea.",
                "/images/products/SakuraBlossomTea.png", tea, 58000.0);
        createProduct("Matcha Latte", "Premium Japanese matcha with steamed milk.",
                externalProductImage("matcha,latte", 2003), tea, 60000.0);
        createProduct("Oolong Tea", "Semi-oxidized Taiwan oolong, floral aroma.",
                externalProductImage("oolong,tea", 2004), tea, 52000.0);
        createProduct("Jasmine Tea", "Fragrant jasmine-scented green tea.",
                externalProductImage("jasmine,tea", 2005), tea, 48000.0);
        createProduct("Earl Grey", "Classic black tea with bergamot oil.",
                externalProductImage("earlgrey,tea", 2006), tea, 50000.0);
        createProduct("Chamomile Tea", "Calming chamomile flowers, caffeine-free.",
                externalProductImage("chamomile,tea", 2007), tea, 45000.0);
        createProduct("Thai Tea", "Sweet Thai-style tea with condensed milk.",
                externalProductImage("thaitea,tea", 2008), tea, 52000.0);
        createProduct("Passion Fruit Tea", "Tropical passion fruit green tea.",
                externalProductImage("passionfruit,tea", 2009), tea, 55000.0);
        createProduct("Lychee Tea", "Sweet lychee black tea with fruit bits.",
                externalProductImage("lychee,tea", 2010), tea, 55000.0);
        createProduct("Mango Tea", "Tropical mango infused oolong tea.",
                externalProductImage("mango,tea", 2011), tea, 56000.0);
        createProduct("Strawberry Tea", "Fresh strawberry green tea.",
                externalProductImage("strawberry,tea", 2012), tea, 54000.0);
        createProduct("Lemon Tea", "Classic lemon black tea, refreshing.",
                externalProductImage("lemon,tea", 2013), tea, 42000.0);
        createProduct("Honey Ginger Tea", "Warm ginger tea with natural honey.",
                externalProductImage("honey,ginger,tea", 2014), tea, 48000.0);
        createProduct("Taro Milk Tea", "Creamy taro flavored milk tea.",
                externalProductImage("taro,milktea", 2015), tea, 55000.0);

        // SMOOTHIE (10 products)
        createProduct("Strawberry Smoothie", "Thick strawberry smoothie blended with fresh milk.",
                "/images/products/StrawberrySmoothie.png", smoothie, 60000.0);
        createProduct("Mango Smoothie", "Creamy mango smoothie with yogurt.",
                externalProductImage("mango,smoothie", 3002), smoothie, 62000.0);
        createProduct("Banana Smoothie", "Banana blended with milk and honey.",
                externalProductImage("banana,smoothie", 3003), smoothie, 55000.0);
        createProduct("Blueberry Smoothie", "Antioxidant-rich blueberry smoothie.",
                externalProductImage("blueberry,smoothie", 3004), smoothie, 65000.0);
        createProduct("Avocado Smoothie", "Creamy avocado with condensed milk.",
                externalProductImage("avocado,smoothie", 3005), smoothie, 60000.0);
        createProduct("Dragon Fruit Smoothie", "Vibrant dragon fruit smoothie.",
                externalProductImage("dragonfruit,smoothie", 3006), smoothie, 62000.0);
        createProduct("Papaya Smoothie", "Sweet papaya smoothie with milk.",
                externalProductImage("papaya,smoothie", 3007), smoothie, 55000.0);
        createProduct("Peanut Smoothie", "Rich peanut butter smoothie.",
                externalProductImage("peanut,smoothie", 3008), smoothie, 58000.0);
        createProduct("Mixed Berry Smoothie", "Blend of strawberry, blueberry, raspberry.",
                externalProductImage("berry,smoothie", 3009), smoothie, 68000.0);
        createProduct("Green Detox Smoothie", "Spinach, apple, cucumber, ginger blend.",
                externalProductImage("green,smoothie", 3010), smoothie, 65000.0);

        // JUICE (10 products)
        createProduct("Coconut Juice", "Fresh young coconut water with coconut jelly.",
                "/images/products/CoconutJuice.png", juice, 50000.0);
        createProduct("Orange Juice", "Freshly squeezed orange juice.",
                externalProductImage("orange,juice", 4002), juice, 45000.0);
        createProduct("Watermelon Juice", "Chilled watermelon juice, no sugar added.",
                externalProductImage("watermelon,juice", 4003), juice, 42000.0);
        createProduct("Pineapple Juice", "Tropical pineapple juice with mint.",
                externalProductImage("pineapple,juice", 4004), juice, 45000.0);
        createProduct("Carrot Juice", "Fresh carrot juice with a hint of ginger.",
                externalProductImage("carrot,juice", 4005), juice, 48000.0);
        createProduct("Apple Juice", "Crisp apple juice, freshly pressed.",
                externalProductImage("apple,juice", 4006), juice, 45000.0);
        createProduct("Grape Juice", "Rich purple grape juice.",
                externalProductImage("grape,juice", 4007), juice, 50000.0);
        createProduct("Lime Soda", "Fresh lime juice with sparkling water.",
                externalProductImage("lime,soda", 4008), juice, 38000.0);
        createProduct("Passion Fruit Juice", "Tangy passion fruit juice with sugar.",
                externalProductImage("passionfruit,juice", 4009), juice, 48000.0);
        createProduct("Sugarcane Juice", "Fresh pressed sugarcane with calamansi.",
                externalProductImage("sugarcane,juice", 4010), juice, 35000.0);

        log.info("Products seeded: 50 products across 4 categories.");
    }

    private com.coffeeshop.entity.Category createDetailsCategory(String name, String description) {
        com.coffeeshop.entity.Category category = categoryRepository.findByName(name)
                .orElse(new com.coffeeshop.entity.Category());
        category.setName(name);
        category.setDescription(description);
        category.setCategoryCode(String.format("CAT-%05d", ++categoryCounter));
        return categoryRepository.save(category);
    }

    private String externalProductImage(String keywords, int lock) {
        return EXTERNAL_IMAGE_BASE_URL + keywords + "?lock=" + lock;
    }

    private void createProduct(String name, String description,
            String imageUrl, com.coffeeshop.entity.Category category, Double basePrice) {
        com.coffeeshop.entity.Product product = productRepository.findAll().stream()
                .filter(p -> p.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(new com.coffeeshop.entity.Product());
        product.setName(name);
        product.setDescription(description);
        product.setImage(imageUrl);
        product.setCategory(category);
        product.setProductCode(String.format("PRD-%05d", ++productCounter));
        product.setAvailable(true);
        product.setBasePrice(BigDecimal.valueOf(basePrice));
        com.coffeeshop.entity.Product savedProduct = productRepository.save(product);

        if (productSizeRepository.findByProductId(savedProduct.getId()).isEmpty()) {
            com.coffeeshop.entity.ProductSize size = new com.coffeeshop.entity.ProductSize(
                    "Standard", basePrice, savedProduct);
            productSizeRepository.save(size);
        }
    }
}
