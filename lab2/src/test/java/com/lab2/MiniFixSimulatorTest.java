package com.lab2;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import quickfix.*;
import quickfix.field.*;
import quickfix.fix44.NewOrderSingle;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MiniFixSimulatorTest {

    private static SocketAcceptor serverAcceptor;
    private static SocketInitiator clientInitiator;
    private static MockedStatic<DatabaseManager> mockedDatabase;

    // To capture execution reports from the server
    private static final List<Message> receivedExecutions = new CopyOnWriteArrayList<>();
    private static CountDownLatch messageLatch;
    
    private static SessionID clientSessionID = new SessionID(new BeginString("FIX.4.4"),
            new SenderCompID("MINIFIX_CLIENT"), new TargetCompID("EXEC_SERVER"));

    @BeforeAll
    public static void setupEnvironment() throws Exception {
        System.out.println("Starting MiniFix Integration Environment...");

        // 1. Mock the DatabaseManager so we don't need a real MySQL database
        mockedDatabase = Mockito.mockStatic(DatabaseManager.class);
        List<Security> mockSecurities = new ArrayList<>();
        mockSecurities.add(new Security("GOOG", "CS", "Google", "GOOG", 1));
        mockSecurities.add(new Security("MSFT", "CS", "Microsoft", "MSFT", 1));
        mockSecurities.add(new Security("IBM", "CS", "IBM", "IBM", 1));
        // Also a bad symbol check will naturally fail since it's not in the list
        mockedDatabase.when(DatabaseManager::loadAllSecurities).thenReturn(mockSecurities);
        // Mock unfilled orders recovery (empty list for tests)
        mockedDatabase.when(DatabaseManager::loadUnfilledOrders).thenReturn(new ArrayList<>());
        // Do nothing on inserts and updates
        mockedDatabase.when(() -> DatabaseManager.insertOrder(Mockito.any())).thenAnswer(inv -> null);
        mockedDatabase.when(() -> DatabaseManager.insertExecution(Mockito.any())).thenAnswer(inv -> null);
        mockedDatabase.when(() -> DatabaseManager.updateOrderStatus(Mockito.anyLong(), Mockito.anyString(), Mockito.anyDouble())).thenAnswer(inv -> null);

        // 2. Start the Server
        OrderBroadcaster mockBroadcaster = new OrderBroadcaster(8090) { // arbitrary unused port for test
            @Override
            public void start() {} // override to not bind actual websocket
            @Override
            public void broadcastOrder(com.lab2.Order order) {}
            @Override
            public void sendTradeUpdate(com.lab2.Execution exec) {}
            @Override
            public void sendOrderStatusUpdate(com.lab2.Order order) {}
        };
        BlockingQueue<Object> mockQueue = new LinkedBlockingQueue<>();
        
        OrderApplication serverApp = new OrderApplication(mockBroadcaster, mockQueue, null);
        
        // Use a programmatic server configuration matching order-service.cfg
        String serverCfg = "[DEFAULT]\n" +
                "ConnectionType=acceptor\n" +
                "StartTime=00:00:00\n" +
                "EndTime=00:00:00\n" +
                "HeartBtInt=30\n" +
                "SenderCompID=EXEC_SERVER\n" +
                "TargetCompID=MINIFIX_CLIENT\n" +
                "UseDataDictionary=Y\n" +
                "DataDictionary=FIX44.xml\n" +
                "[SESSION]\n" +
                "BeginString=FIX.4.4\n" +
                "SocketAcceptPort=9876\n";
        
        SessionSettings serverSettings = new SessionSettings(new ByteArrayInputStream(serverCfg.getBytes()));
        serverAcceptor = new SocketAcceptor(serverApp, new MemoryStoreFactory(), serverSettings,
                new ScreenLogFactory(serverSettings), new DefaultMessageFactory());
        serverAcceptor.start();

        // 3. Start the Client (MiniFix Simulator)
        Application clientApp = new Application() {
            public void onCreate(SessionID sessionId) {}
            public void onLogon(SessionID sessionId) {
                System.out.println("MiniFix Logged On");
            }
            public void onLogout(SessionID sessionId) {}
            public void toAdmin(Message message, SessionID sessionId) {}
            public void fromAdmin(Message message, SessionID sessionId) {}
            public void toApp(Message message, SessionID sessionId) {
                 System.out.println("MiniFix Sending: " + message);
            }
            public void fromApp(Message message, SessionID sessionId) {
                try {
                    String msgType = message.getHeader().getString(MsgType.FIELD);
                    if (msgType.equals(MsgType.EXECUTION_REPORT)) {
                        receivedExecutions.add(message);
                        if (messageLatch != null) {
                            messageLatch.countDown();
                        }
                    }
                } catch (Exception e) {}
            }
        };

        InputStream is = MiniFixSimulatorTest.class.getResourceAsStream("/minifix.cfg");
        SessionSettings clientSettings = new SessionSettings(is);
        clientInitiator = new SocketInitiator(clientApp, new MemoryStoreFactory(), clientSettings,
                new ScreenLogFactory(clientSettings), new DefaultMessageFactory());
        clientInitiator.start();

        // Wait for connection
        boolean loggedOn = false;
        for (int i = 0; i < 20; i++) {
            if (Session.lookupSession(clientSessionID) != null && Session.lookupSession(clientSessionID).isLoggedOn()) {
                loggedOn = true;
                break;
            }
            Thread.sleep(100);
        }
        assertTrue(loggedOn, "MiniFix Test Client failed to logon to the server");
    }

    @AfterAll
    public static void teardown() {
        if (clientInitiator != null) clientInitiator.stop();
        if (serverAcceptor != null) serverAcceptor.stop();
        if (mockedDatabase != null) mockedDatabase.close();
    }

    @Test
    @Order(1)
    public void test1_1_LogonHandshake() {
        // By the time we reach here, BeforeAll has already asserted the session is logged on.
        assertTrue(Session.lookupSession(clientSessionID).isLoggedOn());
    }

    @Test
    @Order(2)
    public void test2_1_ValidOrderAcceptance() throws Exception {
        receivedExecutions.clear();
        messageLatch = new CountDownLatch(1);

        // cl_ord_id is now a BIGINT
        NewOrderSingle order = new NewOrderSingle(
                new ClOrdID("1001"),
                new Side(Side.BUY),
                new TransactTime(),
                new OrdType(OrdType.LIMIT)
        );
        order.set(new Symbol("GOOG"));
        order.set(new OrderQty(100));
        order.set(new Price(150.50));

        Session.sendToTarget(order, clientSessionID);
        
        // Wait for ExecutionReport from server
        assertTrue(messageLatch.await(5, TimeUnit.SECONDS));

        Message execReport = receivedExecutions.get(0);
        assertEquals("1001", execReport.getString(ClOrdID.FIELD));
        assertEquals("0", execReport.getString(OrdStatus.FIELD), "Order Status must be 'New' (0)");
        assertEquals("0", execReport.getString(ExecType.FIELD), "Exec Type must be 'New' (0)");
        assertEquals("GOOG", execReport.getString(Symbol.FIELD));
        assertEquals(100.0, execReport.getDouble(LeavesQty.FIELD));
    }

    @Test
    @Order(3)
    public void test2_2_NegativeQuantityRejection() throws Exception {
        receivedExecutions.clear();
        messageLatch = new CountDownLatch(1);

        NewOrderSingle order = new NewOrderSingle(
                new ClOrdID("1002"),
                new Side(Side.BUY),
                new TransactTime(),
                new OrdType(OrdType.LIMIT)
        );
        order.set(new Symbol("GOOG"));
        order.set(new OrderQty(-100)); // Negative Qty
        order.set(new Price(150.50));

        Session.sendToTarget(order, clientSessionID);

        assertTrue(messageLatch.await(5, TimeUnit.SECONDS));
        Message execReport = receivedExecutions.get(0);

        assertEquals("1002", execReport.getString(ClOrdID.FIELD));
        assertEquals("8", execReport.getString(OrdStatus.FIELD), "Status must be 'Rejected' (8)");
        // Text field must mention our problem
        assertTrue(execReport.getString(Text.FIELD).contains("Invalid Price or Qty"));
    }

    @Test
    @Order(4)
    public void test3_2_AggressiveMatchPriceImprovement() throws Exception {
        receivedExecutions.clear();
        // We expect ONE Ack for Sell, ONE Ack for Buy, and ONE Fill for Sell, ONE Fill for Buy
        // In this implementation, the backend sends out fill reports immediately after Ack.
        // Let's just wait for 3 messages
        messageLatch = new CountDownLatch(3);

        // Step 1: Resting Sell Order at 100.00
        NewOrderSingle sellOrder = new NewOrderSingle(
                new ClOrdID("2100"),
                new Side(Side.SELL),
                new TransactTime(),
                new OrdType(OrdType.LIMIT)
        );
        sellOrder.set(new Symbol("MSFT"));
        sellOrder.set(new OrderQty(100));
        sellOrder.set(new Price(100.00));
        Session.sendToTarget(sellOrder, clientSessionID);

        // Step 2: Aggressive Buy Order at 101.00
        NewOrderSingle buyOrder = new NewOrderSingle(
                new ClOrdID("2101"),
                new Side(Side.BUY),
                new TransactTime(),
                new OrdType(OrdType.LIMIT)
        );
        buyOrder.set(new Symbol("MSFT"));
        buyOrder.set(new OrderQty(100));
        buyOrder.set(new Price(101.00));
        Session.sendToTarget(buyOrder, clientSessionID);

        assertTrue(messageLatch.await(5, TimeUnit.SECONDS));

        // Find the execution report for the BUY_101 trade
        Message tradeExec = receivedExecutions.stream()
                .filter(m -> {
                    try {
                        return m.getString(ExecType.FIELD).equals("F") && m.getString(ClOrdID.FIELD).startsWith("TRADE");
                    } catch (Exception e) { return false; }
                })
                .filter(m -> {
                    try {
                        return m.getChar(Side.FIELD) == Side.BUY;
                    } catch (Exception e) { return false; }
                })
                .findFirst()
                .orElseThrow(() -> new AssertionError("Did not find Trade Execution Report for BUY"));

        // Crucial Check: Tag 31 (LastPx) must be 100.00 (the resting order's better price!)
        assertEquals(100.00, tradeExec.getDouble(LastPx.FIELD));
        assertEquals("2", tradeExec.getString(OrdStatus.FIELD)); // Fully filled
    }

    @Test
    @Order(5)
    public void test3_3_PartialFill() throws Exception {
        receivedExecutions.clear();
        messageLatch = new CountDownLatch(3); // 2 Acks, 1 Fill

        // Step 1: Resting Sell for 50
        NewOrderSingle sellOrder = new NewOrderSingle(
                new ClOrdID("3050"),
                new Side(Side.SELL),
                new TransactTime(),
                new OrdType(OrdType.LIMIT)
        );
        sellOrder.set(new Symbol("IBM"));
        sellOrder.set(new OrderQty(50));
        sellOrder.set(new Price(102.00));
        Session.sendToTarget(sellOrder, clientSessionID);

        // Step 2: Aggressive Buy for 150
        NewOrderSingle buyOrder = new NewOrderSingle(
                new ClOrdID("3150"),
                new Side(Side.BUY),
                new TransactTime(),
                new OrdType(OrdType.LIMIT)
        );
        buyOrder.set(new Symbol("IBM"));
        buyOrder.set(new OrderQty(150));
        buyOrder.set(new Price(102.00));
        Session.sendToTarget(buyOrder, clientSessionID);

        assertTrue(messageLatch.await(5, TimeUnit.SECONDS));

        // Find the execution report for the BUY side
        Message tradeExec = receivedExecutions.stream()
                .filter(m -> {
                    try {
                        return m.getString(ExecType.FIELD).equals("F") && m.getChar(Side.FIELD) == Side.BUY;
                    } catch (Exception e) { return false; }
                })
                .findFirst()
                .orElseThrow(() -> new AssertionError("Did not find Trade Execution Report for BUY"));

        assertEquals(102.00, tradeExec.getDouble(LastPx.FIELD));
        assertEquals(50.0, tradeExec.getDouble(LastQty.FIELD));
        assertEquals(50.0, tradeExec.getDouble(CumQty.FIELD));
    }
}
