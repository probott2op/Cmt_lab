import socket
import time
import simplefix
import random
import uuid
from datetime import datetime
import random

# --- CONFIGURATION ---
HOST = "127.0.0.1"
PORT = 9876
SENDER_COMP_ID = b"MINIFIX_CLIENT"
TARGET_COMP_ID = b"EXEC_SERVER"
TOTAL_ORDERS = 10000
ORDERS_PER_SEC = 1000

def get_timestamp():
    """Returns FIX formatted UTC timestamp: YYYYMMDD-HH:MM:SS.SSS"""
    return datetime.utcnow().strftime("%Y%m%d-%H:%M:%S.%f")[:-3].encode()

def create_base_message(msg_type, seq_num):
    """Constructs the standard FIX header"""
    msg = simplefix.FixMessage()
    msg.append_pair(8, b"FIX.4.4")
    msg.append_pair(35, msg_type)
    msg.append_pair(34, seq_num)
    msg.append_pair(49, SENDER_COMP_ID)
    msg.append_pair(56, TARGET_COMP_ID)
    msg.append_pair(52, get_timestamp())
    return msg

def main():
    # 1. Open a raw TCP Socket to the Spring Boot engine
    print(f"Connecting to {HOST}:{PORT}...")
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    try:
        sock.connect((HOST, PORT))
    except ConnectionRefusedError:
        print("ERROR: Connection refused. Is the Spring Boot engine running?")
        return

    seq_num = 1

    # 2. Send LOGON (MsgType=A)
    # We send Tag 141=Y to force the engine to reset its expected sequence to 1
    print("Sending Logon...")
    logon_msg = create_base_message(b"A", seq_num)
    logon_msg.append_pair(98, b"0")   # EncryptMethod = None
    logon_msg.append_pair(108, b"30") # HeartBtInt = 30 seconds
    logon_msg.append_pair(141, b"Y")  # ResetSeqNumFlag = Yes
    
    sock.sendall(logon_msg.encode())
    seq_num += 1
    
    # Wait a moment for the engine to process the Logon and reply
    time.sleep(1)

    # 3. Blast the Orders
    delay = 1.0 / ORDERS_PER_SEC
    symbols = [b"MSFT", b"IBM", b"GOOG"]
    sides = [b"1", b"2"] # 1=Buy, 2=Sell
    
    print(f"Injecting {TOTAL_ORDERS} orders at {ORDERS_PER_SEC} msgs/sec...")
    start_time = time.time()
    
    for i in range(TOTAL_ORDERS):
        order = create_base_message(b"D", seq_num)
        
        # --- ORDER DETAILS MATCHING YOUR IMAGE ---
        
        # Tag 11: Unique ID (Equivalent to your $UNIQUE)
        order.append_pair(11, random.randint(10000000, 99999999)) 
        
        # Tag 21: HandlInst = 1 (Automated execution)
        order.append_pair(21, b"1")                       
        
        # Tag 55: Symbol (e.g., GOOG)
        order.append_pair(55, random.choice(symbols))     
        
        # Tag 54: Side (1 = Buy, 2 = Sell)
        order.append_pair(54, random.choice(sides))       
        
        # Tag 60: Time (Equivalent to your $TIMESTAMP)
        order.append_pair(60, get_timestamp())            
        
        # Tag 40: OrdType = 2 (Limit Order)
        order.append_pair(40, b"2")                       
        
        # Tag 44: Price (Required for Limit Orders)
        # Generating a random price between 50 and 150 for variety
        random_price = str(random.randint(50, 150)).encode()
        order.append_pair(44, random_price)               
        
        # Tag 38: OrderQty
        order.append_pair(38, b"100")    
        
        # Send the order
        sock.sendall(order.encode())
        seq_num += 1
        
        # Throttle to maintain 100/sec
        time.sleep(delay)

    end_time = time.time()
    duration = end_time - start_time
    
    print("\n--- Load Test Complete ---")
    print(f"Orders Sent: {TOTAL_ORDERS}")
    print(f"Time Taken: {duration:.2f} seconds")
    print(f"Effective Rate: {TOTAL_ORDERS / duration:.2f} orders/sec")

    # 4. Send LOGOUT (MsgType=5)
    print("Logging out...")
    logout_msg = create_base_message(b"5", seq_num)
    sock.sendall(logout_msg.encode())
    
    time.sleep(0.5)
    sock.close()

if __name__ == "__main__":
    main()