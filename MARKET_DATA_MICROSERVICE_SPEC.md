# Market Data & Options Pricing Microservice - Complete Specification

## Table of Contents
1. [Database Schema & Connection](#database-schema--connection)
2. [Existing System Architecture](#existing-system-architecture)
3. [Data Models (POJOs)](#data-models-pojos)
4. [Microservice Requirements](#microservice-requirements)
5. [Team Member Responsibilities](#team-member-responsibilities)
6. [Frontend Requirements](#frontend-requirements)
7. [Technical Stack & Dependencies](#technical-stack--dependencies)

---

## Database Schema & Connection

### Database Connection Details
- **Database Name**: `trading_system`
- **URL**: `jdbc:mysql://localhost:3306/trading_system`
- **User**: `root`
- **Password**: `root@fintech`
- **Driver**: MySQL JDBC Driver

### Table Schemas

#### 1. `orders` Table
```sql
CREATE TABLE orders (
    order_id VARCHAR(50) PRIMARY KEY,        -- Server generated UUID
    cl_ord_id VARCHAR(50),                   -- Client's Order ID
    symbol VARCHAR(20),                       -- Security symbol
    side CHAR(1),                             -- '1' = BUY, '2' = SELL
    price DECIMAL(15, 2),                     -- Limit price
    quantity DECIMAL(15, 0),                  -- Order quantity
    status VARCHAR(20),                       -- Order status (NEW, FILLED, REJECTED, etc.)
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**Side Values:**
- `'1'` = BUY
- `'2'` = SELL

**Status Values:**
- `NEW` - Order accepted
- `FILLED` - Fully executed
- `PARTIALLY_FILLED` - Partially executed
- `REJECTED` - Order rejected

#### 2. `executions` Table
```sql
CREATE TABLE executions (
    exec_id VARCHAR(50) PRIMARY KEY,          -- Execution UUID
    order_id VARCHAR(50),                     -- Foreign key to orders.order_id
    symbol VARCHAR(20),                       -- Security symbol
    side CHAR(1),                             -- '1' = BUY, '2' = SELL
    exec_qty INT,                             -- Executed quantity
    exec_price DECIMAL(15, 2),                -- Execution price
    match_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_order FOREIGN KEY (order_id) REFERENCES orders(order_id)
);
```

**Purpose:** Stores trade executions (matches between buy and sell orders)

#### 3. `security_master` Table
```sql
CREATE TABLE security_master (
    symbol VARCHAR(20) PRIMARY KEY,           -- Security identifier (e.g., AAPL, MSFT)
    security_type VARCHAR(20),                -- Type: STOCK, OPTION, FUTURE, etc.
    description VARCHAR(255),                 -- Human-readable description
    underlying VARCHAR(20),                   -- For options: underlying stock symbol
    lot_size INT                              -- Standard trading lot size
);
```

**Security Types:**
- `STOCK` - Equity security
- `OPTION` - Options contract
- `FUTURE` - Futures contract

**Example Data:**
```sql
INSERT INTO security_master VALUES 
('AAPL', 'STOCK', 'Apple Inc.', NULL, 100),
('AAPL_CALL_150_2026', 'OPTION', 'AAPL Call Strike 150 Exp 2026', 'AAPL', 100);
```

#### 4. `customer_master` Table
```sql
CREATE TABLE customer_master (
    customer_code VARCHAR(50) PRIMARY KEY,    -- Unique customer identifier
    customer_name VARCHAR(255),               -- Customer name
    customer_type VARCHAR(50),                -- Type: RETAIL, INSTITUTIONAL, etc.
    credit_limit DECIMAL(15, 2)               -- Credit limit for trading
);
```

**Customer Types:**
- `RETAIL` - Individual retail trader
- `INSTITUTIONAL` - Institutional client
- `MARKET_MAKER` - Market maker

---

## Existing System Architecture

### System Overview
The existing trading system is a **FIX-based Order Management System** with the following components:

```
┌─────────────────┐
│  FIX Client     │ ──FIX 4.4──▶ ┌─────────────────────┐
│  (Trading App)  │              │  OrderApplication   │
└─────────────────┘              │  (QuickFIX/J)       │
                                 └──────────┬──────────┘
                                           │
                    ┌──────────────────────┼──────────────────────┐
                    ▼                      ▼                      ▼
           ┌─────────────────┐   ┌─────────────────┐   ┌─────────────────┐
           │  OrderBook      │   │  OrderPersister │   │ OrderBroadcaster│
           │  (Matching)     │   │  (DB Worker)    │   │  (WebSocket)    │
           └─────────────────┘   └────────┬────────┘   └────────┬────────┘
                    │                     ▼                      ▼
                    │            ┌─────────────────┐   ┌─────────────────┐
                    │            │   MySQL DB      │   │  React Frontend │
                    │            │ (trading_system)│   │  (Port 8080)    │
                    │            └─────────────────┘   └─────────────────┘
                    │
                    └──▶ Execution Records (trades)
```

### Component Responsibilities

1. **OrderApplication** (FIX Acceptor)
   - Receives FIX messages from clients
   - Validates orders against `security_master`
   - Sends acknowledgments via FIX
   - Routes orders to OrderBook for matching
   - Broadcasts updates via WebSocket

2. **OrderBook** (Matching Engine)
   - Maintains bid/ask order books per symbol
   - Matches incoming orders using price-time priority
   - Returns list of `Execution` objects for each match
   - Uses `ConcurrentSkipListMap` for thread-safe operations

3. **OrderPersister** (Async DB Worker)
   - Consumer thread reading from `BlockingQueue<Object>`
   - Persists both `Order` and `Execution` objects to database
   - Runs asynchronously to avoid blocking trading logic

4. **OrderBroadcaster** (WebSocket Server)
   - Broadcasts real-time updates to web clients
   - Port: `8080`
   - Message Types: `ORDER`, `TRADE`

5. **Frontend (React + Vite)**
   - Displays live order blotter
   - Shows trade executions
   - Connects via WebSocket

---

## Data Models (POJOs)

### Order.java
```java
public class Order {
    private String orderId;      // Server-generated UUID
    private String clOrdID;      // Client Order ID
    private String symbol;       // Security symbol
    private char side;           // '1' = BUY, '2' = SELL
    private double price;        // Limit price
    private double quantity;     // Order quantity
    
    // Constructor
    public Order(String orderId, String clOrdID, String symbol, 
                 char side, double price, double quantity) { ... }
    
    // Getters, Setters, reduceQty()
}
```

### Execution.java
```java
public class Execution {
    private String execId;       // Execution UUID
    private String orderId;      // Reference to order_id
    private String symbol;       // Security symbol
    private char side;           // '1' = BUY, '2' = SELL
    private int execQty;         // Executed quantity
    private double execPrice;    // Execution price
    private Timestamp matchTime; // Timestamp of match
    
    // Constructors, Getters, Setters
}
```

### Security.java
```java
public class Security {
    private String symbol;
    private String securityType;  // STOCK, OPTION, FUTURE
    private String description;
    private String underlying;    // For options
    private int lotSize;
    
    // Constructor, Getters, Setters
}
```

### Customer.java
```java
public class Customer {
    private String customerCode;
    private String customerName;
    private String customerType;  // RETAIL, INSTITUTIONAL
    private BigDecimal creditLimit;
    
    // Constructor, Getters, Setters
}
```

---

## Microservice Requirements

### Overview
Build a **Market Data & Options Pricing Microservice** that:
1. Polls market data from flat files
2. Maintains real-time price cache
3. Computes option prices using Black-Scholes
4. Consumes trade executions to update pricing
5. Provides WebSocket API for real-time updates

### Key Features
- **Decoupled Architecture**: Independent microservice querying shared database
- **Real-time Updates**: WebSocket streaming for stock prices and options
- **Delta Updates**: Incremental update protocol (not full refresh)
- **High Performance**: O(1) price lookups from in-memory cache
- **Observability**: Telemetry, logging, metrics

---

## Team Member Responsibilities

### Member G3-M1: Market Data File Poller
**Responsibility:** Implement file-based market data ingestion with incremental updates

**Requirements:**
1. **File Polling**
   - Scheduled polling interval (e.g., every 5 seconds)
   - Monitor flat file directory for new/updated quote files
   - Support CSV or fixed-width format

2. **Data Parsing**
   ```
   Example CSV format:
   symbol,bid,offer,last,close,volume,timestamp
   AAPL,150.25,150.30,150.27,149.50,1000000,2026-03-02T10:30:00
   MSFT,380.10,380.15,380.12,379.00,500000,2026-03-02T10:30:00
   ```

3. **Delta Processing**
   - Track last modification time or file sequence number
   - Process only changed records (not full refresh)
   - Detect new symbols vs updates to existing

4. **Validation**
   - Validate symbol against `security_master` table
   - Check bid <= offer price constraint
   - Verify timestamp is recent (not stale data)
   - Handle malformed rows gracefully

5. **Event Production**
   - Produce `MarketDataUpdated` events
   - Event payload: `{ symbol, bid, offer, last, close, volume, timestamp }`
   - Publish to internal event bus or queue

**Technical Notes:**
- Use `WatchService` API or scheduled executor for polling
- Consider file locking mechanisms
- Implement exponential backoff on file read errors

---

### Member G3-M2: Market Data Store + Subscription API
**Responsibility:** In-memory price store with WebSocket subscription service

**Requirements:**
1. **In-Memory Store**
   ```java
   // Fast O(1) lookup by symbol
   ConcurrentHashMap<String, MarketData> priceStore = new ConcurrentHashMap<>();
   
   class MarketData {
       String symbol;
       double bid;
       double offer;
       double last;
       double close;
       long volume;
       Timestamp lastUpdate;
   }
   ```

2. **WebSocket Server** (Port: 8081 recommended)
   - Subscribe: `{ "action": "subscribe", "symbols": ["AAPL", "MSFT"] }`
   - Unsubscribe: `{ "action": "unsubscribe", "symbols": ["AAPL"] }`
   - Subscribe All: `{ "action": "subscribe_all" }`

3. **Message Protocol**
   - **Snapshot**: Full market data on subscription
     ```json
     {
       "type": "SNAPSHOT",
       "data": {
         "symbol": "AAPL",
         "bid": 150.25,
         "offer": 150.30,
         "last": 150.27,
         "close": 149.50,
         "volume": 1000000,
         "timestamp": "2026-03-02T10:30:00Z"
       }
     }
     ```
   
   - **Incremental Update**: Only changed fields
     ```json
     {
       "type": "UPDATE",
       "data": {
         "symbol": "AAPL",
         "last": 150.35,
         "volume": 1000500,
         "timestamp": "2026-03-02T10:30:05Z"
       }
     }
     ```

4. **Subscription Management**
   - Track subscriptions per WebSocket connection
   - Send updates only to subscribed clients
   - Handle connection drops gracefully

5. **Thread Safety**
   - Use concurrent collections
   - Consider read locks for snapshot generation

**Technical Stack:**
- WebSocket: Java-WebSocket or Spring WebSocket
- JSON: Gson or Jackson

---

### Member G3-M3: Options Pricing Engine (Black-Scholes)
**Responsibility:** Implement Black-Scholes model with numerical stability

**Requirements:**
1. **Black-Scholes Formula**
   ```
   Call Option Price = S * N(d1) - K * e^(-r*T) * N(d2)
   Put Option Price  = K * e^(-r*T) * N(-d2) - S * N(-d1)
   
   Where:
   d1 = [ln(S/K) + (r + σ²/2)*T] / (σ * √T)
   d2 = d1 - σ * √T
   
   S = Current stock price (from market data or executions)
   K = Strike price
   T = Time to expiration (years)
   r = Risk-free rate
   σ = Volatility
   N(x) = Cumulative normal distribution
   ```

2. **Input Parameters**
   ```java
   class OptionPricingInput {
       String symbol;           // Option symbol
       String underlying;       // Underlying stock symbol
       double underlyingPrice;  // Current stock price (S)
       double strike;           // Strike price (K)
       double timeToExpiry;     // Years to expiration (T)
       double volatility;       // Implied volatility (σ)
       double riskFreeRate;     // Risk-free rate (r)
       char optionType;         // 'C' = Call, 'P' = Put
   }
   ```

3. **Parse Option Symbol**
   - Format: `{UNDERLYING}_{TYPE}_{STRIKE}_{EXPIRY}`
   - Example: `AAPL_CALL_150_20260331` = AAPL Call Strike $150, Expires 2026-03-31
   - Extract: underlying, strike, expiry date, call/put

4. **Static Parameters**
   - Store in database table `option_parameters`:
     ```sql
     CREATE TABLE option_parameters (
         symbol VARCHAR(50) PRIMARY KEY,
         underlying VARCHAR(20),
         strike DECIMAL(15, 2),
         expiry_date DATE,
         option_type CHAR(1),  -- 'C' or 'P'
         volatility DECIMAL(5, 4),      -- e.g., 0.25 = 25%
         risk_free_rate DECIMAL(5, 4)   -- e.g., 0.05 = 5%
     );
     ```

5. **Numerical Stability**
   - Handle edge cases:
     - Very deep ITM/OTM options
     - Near-zero time to expiry
     - Extreme volatility values
   - Use `Math.exp()` carefully to avoid overflow
   - Clamp extreme d1/d2 values

6. **Unit Tests**
   - Test against known reference values (Black-Scholes calculators)
   - Edge cases: T → 0, σ → 0, extreme S/K ratios
   - Validate Greeks if implemented (Delta, Gamma, Vega, etc.)

**References:**
- Cumulative Normal Distribution: Use Apache Commons Math `NormalDistribution`
- Numerical Methods: https://en.wikipedia.org/wiki/Black%E2%80%93Scholes_model

---

### Member G3-M4: Trade-Driven Pricing Updates
**Responsibility:** Consume trade executions and recompute option prices

**Requirements:**
1. **Trade Consumption**
   - Query `executions` table for latest trades
   - Poll every N milliseconds (e.g., 100ms)
   - Filter trades by underlying symbols of active options
   - Alternative: Listen to OrderBroadcaster WebSocket `TRADE` messages

2. **Price Update Logic**
   ```java
   // When trade execution received
   onTradeExecuted(Execution trade) {
       // 1. Get affected options (all options with this underlying)
       List<Option> affectedOptions = getOptionsByUnderlying(trade.symbol);
       
       // 2. Update underlying price in MarketData store
       marketDataStore.update(trade.symbol, trade.execPrice);
       
       // 3. Recompute option prices
       for (Option option : affectedOptions) {
           double newPrice = blackScholesPricer.compute(
               option.underlying, 
               trade.execPrice,  // Use latest execution price
               option.strike, 
               option.timeToExpiry,
               option.volatility,
               option.riskFreeRate
           );
           
           // 4. Update option in store
           optionPriceStore.update(option.symbol, newPrice);
           
           // 5. Publish update event
           publishOptionPriceUpdate(option.symbol, newPrice);
       }
   }
   ```

3. **Throttling/Aggregation**
   - Problem: High-frequency trading can cause 1000s of updates/second
   - Solution: Aggregate updates per symbol per time window (e.g., 100ms)
   - Implementation:
     ```java
     // Collect updates in a buffer
     Map<String, List<Double>> updateBuffer = new HashMap<>();
     
     // Flush buffer every 100ms
     scheduledExecutor.scheduleAtFixedRate(() -> {
         for (String symbol : updateBuffer.keySet()) {
             double avgPrice = calculateAverage(updateBuffer.get(symbol));
             publishUpdate(symbol, avgPrice);
         }
         updateBuffer.clear();
     }, 100, 100, TimeUnit.MILLISECONDS);
     ```

4. **Event Publishing**
   - Produce `OptionPriceUpdated` event
   - Payload: `{ symbol, fairPrice, underlyingPrice, timestamp }`
   - Send to WebSocket clients via G3-M2's subscription API

5. **Dynamic Pricing**
   - Option prices update in real-time as underlying stock trades
   - No manual refresh required
   - Historical price snapshots stored for analytics

**Performance Considerations:**
- Avoid blocking I/O in critical path
- Use async processing where possible
- Monitor CPU usage during high trade volume

---

### Member G3-M5: Reference Data + DB Init Service
**Responsibility:** Database initialization and cached reference data APIs

**Requirements:**
1. **Database Initialization**
   - Create tables if not exists (DDL scripts)
   - Load initial data from CSV files:
     - `security_master.csv`
     - `customer_master.csv`
     - `option_parameters.csv`

2. **Data Loader Implementation**
   ```java
   class ReferenceDataLoader {
       // Load securities from CSV
       public void loadSecurities(String csvPath) {
           // Parse CSV
           // INSERT INTO security_master ...
           // Bulk insert for performance
       }
       
       // Load option parameters
       public void loadOptionParameters(String csvPath) {
           // Parse CSV
           // INSERT INTO option_parameters ...
       }
   }
   ```

3. **Cached Lookup APIs**
   ```java
   class ReferenceDataService {
       // In-memory cache
       private Map<String, Security> securityCache = new ConcurrentHashMap<>();
       private Map<String, OptionParameter> optionCache = new ConcurrentHashMap<>();
       
       // Load on startup
       public void initialize() {
           loadSecuritiesIntoCache();
           loadOptionParametersIntoCache();
       }
       
       // O(1) lookups
       public Security getSecurity(String symbol) { ... }
       public OptionParameter getOptionParameter(String symbol) { ... }
       public List<String> getOptionsByUnderlying(String underlying) { ... }
   }
   ```

4. **Data Quality Checks**
   - Validate required fields are not null
   - Check referential integrity (option underlying exists in security_master)
   - Detect duplicate symbols
   - Log warnings for data anomalies

5. **Migration Scripts**
   - Versioned SQL migration scripts
   - Use Flyway or Liquibase for schema management
   - Example: `V1__create_tables.sql`, `V2__add_option_parameters.sql`

6. **Startup Sequence**
   ```
   1. Run database migrations
   2. Load reference data from files
   3. Validate data integrity
   4. Warm up in-memory caches
   5. Start market data services
   ```

**File Formats:**
```csv
# security_master.csv
symbol,security_type,description,underlying,lot_size
AAPL,STOCK,Apple Inc.,,100
MSFT,STOCK,Microsoft Corp.,,100
AAPL_CALL_150_20260331,OPTION,AAPL Call 150 Mar26,AAPL,100

# option_parameters.csv
symbol,underlying,strike,expiry_date,option_type,volatility,risk_free_rate
AAPL_CALL_150_20260331,AAPL,150.00,2026-03-31,C,0.25,0.05
AAPL_PUT_140_20260331,AAPL,140.00,2026-03-31,P,0.25,0.05
```

---

### Member G3-M6: Observability for Pricing + Market Data
**Responsibility:** Telemetry, monitoring, alerting, and performance benchmarks

**Requirements:**
1. **Telemetry Metrics**
   ```java
   // Metrics to track
   class MarketDataMetrics {
       AtomicLong messagesReceived;        // Total market data updates
       AtomicLong messagesProcessed;       // Successfully processed
       AtomicLong messagesFailed;          // Failed/malformed
       AtomicLong updateLatencyMs;         // Avg time from file read to store update
       AtomicLong priceComputations;       // Total option pricing calls
       AtomicLong priceComputeTimeMs;      // Avg Black-Scholes compute time
       
       ConcurrentHashMap<String, Long> symbolUpdateCounts;  // Per-symbol stats
   }
   ```

2. **Message Lag Monitoring**
   - Track timestamp in market data file vs processing time
   - Alert if lag > 5 seconds (configurable threshold)
   - Calculate: `processingLag = currentTime - dataTimestamp`

3. **Update Frequency**
   - Measure updates per second per symbol
   - Detect anomalies (sudden drop or spike)
   - Dashboard: Real-time update rate graph

4. **Dropped Events**
   - Count events that fail validation
   - Log reasons for drops (e.g., invalid symbol, stale data)
   - Dead-letter queue for manual inspection

5. **Compute Time**
   - Measure Black-Scholes computation time
   - Track percentiles: p50, p95, p99
   - Alert if p99 > 10ms (configurable)

6. **Error Logging**
   ```java
   // Structured logging with context
   logger.error("Failed to parse market data file", 
       Map.of(
           "file", fileName,
           "line", lineNumber,
           "error", exception.getMessage(),
           "timestamp", Instant.now()
       )
   );
   ```

7. **Dead-Letter Handling**
   - Save malformed records to `failed_market_data` table
   - Retry mechanism with exponential backoff
   - Admin UI to review and reprocess

8. **Performance Benchmarks**
   ```
   Benchmark Suite:
   - Price Updates: Measure throughput (updates/sec) vs CPU usage
   - Subscription Load: Test 1000 concurrent WebSocket clients
   - Database Query: Measure retrieval time for 10k executions
   - Black-Scholes: Time 1M option price calculations
   
   Target SLAs:
   - 10,000 price updates/sec with <50% CPU
   - WebSocket broadcast <10ms latency
   - Database queries <100ms p95
   - Option pricing <1ms per calculation
   ```

9. **Dashboards**
   - Use Grafana or similar for visualization
   - Metrics: Update rate, latency, error rate, CPU/memory
   - Alerts: Email/Slack notifications on threshold breaches

10. **Health Checks**
    ```java
    @GetMapping("/health")
    public HealthStatus health() {
        return new HealthStatus(
            marketDataPoller.isRunning(),
            websocketServer.isConnected(),
            databaseConnection.isAlive(),
            lastUpdateTime,
            activeSubscriptions
        );
    }
    ```

---

## Frontend Requirements

### Overview
Build a React frontend to display real-time market data and option prices

### Features
1. **Stock Price Dashboard**
   - Display live stock prices (symbol, bid, offer, last, volume)
   - Auto-refresh on WebSocket updates
   - Color coding: Green (up), Red (down) based on last price change

2. **Options Chain Viewer**
   - Display all options for a selected underlying
   - Columns: Symbol, Strike, Type, Fair Price, Underlying Price, Time to Expiry
   - Filter by call/put, strike range, expiry date

3. **Trade Blotter**
   - Real-time trade feed (from existing OrderBroadcaster)
   - Display: Exec ID, Symbol, Side, Quantity, Price, Time

4. **WebSocket Integration**
   ```javascript
   // Connect to Market Data WebSocket (Port 8081)
   const marketDataWs = new WebSocket('ws://localhost:8081');
   
   marketDataWs.onopen = () => {
       // Subscribe to symbols
       marketDataWs.send(JSON.stringify({
           action: 'subscribe',
           symbols: ['AAPL', 'MSFT', 'AAPL_CALL_150_20260331']
       }));
   };
   
   marketDataWs.onmessage = (event) => {
       const message = JSON.parse(event.data);
       
       if (message.type === 'SNAPSHOT') {
           // Initialize data
           setMarketData(prev => ({...prev, [message.data.symbol]: message.data}));
       } else if (message.type === 'UPDATE') {
           // Incremental update
           setMarketData(prev => ({
               ...prev,
               [message.data.symbol]: {...prev[message.data.symbol], ...message.data}
           }));
       }
   };
   ```

5. **UI Components**
   - `StockTicker.jsx` - Scrolling ticker with live prices
   - `OptionChain.jsx` - Table view of options
   - `TradeBlotter.jsx` - Real-time trade log
   - `PriceChart.jsx` - Historical price chart (optional)

6. **Styling**
   - Clean, professional trading UI
   - Monospace fonts for prices
   - Real-time sparklines for price trends

---

## Technical Stack & Dependencies

### Backend (Java Microservice)
- **Framework**: Spring Boot 3.x
- **Database**: MySQL 8.x with JDBC
- **WebSocket**: Java-WebSocket or Spring WebSocket
- **JSON**: Gson or Jackson
- **Math Library**: Apache Commons Math (for Black-Scholes)
- **Concurrency**: `java.util.concurrent` (ConcurrentHashMap, ScheduledExecutorService)
- **Logging**: SLF4J + Logback
- **Metrics**: Micrometer (for Prometheus/Grafana integration)

### Frontend (React)
- **Framework**: React 18 + Vite
- **WebSocket**: Native WebSocket API
- **UI Library**: Material-UI or Ant Design
- **State Management**: React Hooks (useState, useEffect, useContext)
- **Charts**: Recharts or Chart.js

### DevOps
- **Build**: Maven or Gradle
- **Docker**: containerize microservice
- **CI/CD**: GitHub Actions or Jenkins

---

## Database Queries Reference

### Query Latest Orders
```sql
-- Get all orders for a symbol
SELECT * FROM orders WHERE symbol = 'AAPL' ORDER BY timestamp DESC LIMIT 100;

-- Get latest trade executions
SELECT * FROM executions ORDER BY match_time DESC LIMIT 100;

-- Join orders with executions
SELECT o.order_id, o.cl_ord_id, o.symbol, o.side, o.price, o.quantity,
       e.exec_id, e.exec_price, e.exec_qty, e.match_time
FROM orders o
LEFT JOIN executions e ON o.order_id = e.order_id
WHERE o.symbol = 'AAPL'
ORDER BY o.timestamp DESC;
```

### Query Market Data (from executions)
```sql
-- Latest execution price per symbol (approximates market price)
SELECT symbol, 
       AVG(exec_price) as avg_price,
       MIN(exec_price) as low,
       MAX(exec_price) as high,
       SUM(exec_qty) as volume,
       MAX(match_time) as last_update
FROM executions
WHERE match_time >= NOW() - INTERVAL 1 HOUR
GROUP BY symbol;
```

### Query Reference Data
```sql
-- Get all options for an underlying
SELECT * FROM security_master 
WHERE security_type = 'OPTION' AND underlying = 'AAPL';

-- Get option parameters
SELECT * FROM option_parameters WHERE underlying = 'AAPL';

-- Validate symbol exists
SELECT COUNT(*) FROM security_master WHERE symbol = 'AAPL';
```

---

## Integration Points

### With Existing System
1. **Shared Database**: Both systems query/write to same MySQL instance
2. **Independent Deployment**: Market data microservice runs separately
3. **WebSocket Coexistence**: 
   - Port 8080: OrderBroadcaster (orders/trades)
   - Port 8081: MarketDataBroadcaster (prices/options)

### Data Flow
```
FIX Orders → OrderApplication → OrderBook → Executions
                                               ↓
                                         MySQL Database
                                               ↓
                            Market Data Microservice (Polling)
                                               ↓
                                    Compute Option Prices
                                               ↓
                                    WebSocket (Port 8081)
                                               ↓
                                         React Frontend
```

---

## Success Criteria

### Functional
- ✅ Market data polls from flat files every 5 seconds
- ✅ Delta updates (not full refresh) working correctly
- ✅ Black-Scholes pricing matches reference calculators (±0.01)
- ✅ WebSocket clients receive real-time updates <100ms latency
- ✅ Option prices update within 200ms of underlying trade

### Performance
- ✅ Handle 10,000 price updates/second
- ✅ Support 500 concurrent WebSocket connections
- ✅ Black-Scholes computation <1ms per option
- ✅ Database queries <100ms p95

### Observability
- ✅ Metrics dashboard showing update rate, latency, errors
- ✅ Dead-letter queue captures malformed data
- ✅ Health check endpoint responds <50ms
- ✅ Alerts configured for critical failures

---

## Appendix: Sample Data

### Sample Market Data File
```csv
# market_data_20260302_103000.csv
symbol,bid,offer,last,close,volume,timestamp
AAPL,150.25,150.30,150.27,149.50,1000000,2026-03-02T10:30:00Z
MSFT,380.10,380.15,380.12,379.00,500000,2026-03-02T10:30:00Z
GOOG,2800.50,2800.75,2800.60,2795.00,250000,2026-03-02T10:30:00Z
```

### Sample Option Parameters
```csv
symbol,underlying,strike,expiry_date,option_type,volatility,risk_free_rate
AAPL_CALL_150_20260331,AAPL,150.00,2026-03-31,C,0.25,0.05
AAPL_CALL_155_20260331,AAPL,155.00,2026-03-31,C,0.25,0.05
AAPL_PUT_145_20260331,AAPL,145.00,2026-03-31,P,0.25,0.05
MSFT_CALL_380_20260430,MSFT,380.00,2026-04-30,C,0.30,0.05
```

---

## Getting Started

### Step 1: Database Setup
```sql
-- Use existing trading_system database
USE trading_system;

-- Create option_parameters table
CREATE TABLE option_parameters (
    symbol VARCHAR(50) PRIMARY KEY,
    underlying VARCHAR(20),
    strike DECIMAL(15, 2),
    expiry_date DATE,
    option_type CHAR(1),
    volatility DECIMAL(5, 4),
    risk_free_rate DECIMAL(5, 4),
    FOREIGN KEY (underlying) REFERENCES security_master(symbol)
);
```

### Step 2: Project Setup
```bash
# Create Spring Boot project
spring init --dependencies=web,websocket,jdbc,mysql market-data-service

# Add dependencies
# - Apache Commons Math
# - Gson
# - Java-WebSocket
```

### Step 3: Implementation Order
1. G3-M5: Reference data loader (foundation)
2. G3-M1: File poller (data ingestion)
3. G3-M2: Market data store + WebSocket (API layer)
4. G3-M3: Black-Scholes engine (pricing logic)
5. G3-M4: Trade-driven updates (integration)
6. G3-M6: Observability (monitoring)
7. Frontend: React UI (visualization)

---

## Contact & Support

For questions about the existing trading system:
- Database: `jdbc:mysql://localhost:3306/trading_system`
- WebSocket (Orders): `ws://localhost:8080`
- FIX Acceptor: Port 9876

For this microservice:
- WebSocket (Market Data): `ws://localhost:8081`
- REST API: `http://localhost:8082` (if implemented)

---

**Last Updated**: March 2, 2026  
**Version**: 1.0  
**Status**: Ready for Implementation 🚀
