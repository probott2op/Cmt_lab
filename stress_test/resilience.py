import socket
import time
import re
from datetime import datetime

# --- CONFIGURATION ---
HOST = '127.0.0.1'
PORT = 9876                 # <-- CHANGE THIS to your Java Server's Port
SENDER_COMP_ID = 'MINIFIX_CLIENT'  # Matches your setup
TARGET_COMP_ID = 'EXEC_SERVER' # Matches your setup
FIX_VERSION = 'FIX.4.4'
# ---------------------

def build_fix_message(msg_type, seq_num, extra_tags="", is_resend=False, orig_time=None):
    """Builds a strictly compliant FIX message."""
    sending_time = datetime.utcnow().strftime('%Y%m%d-%H:%M:%S')
    
    # Core Header
    header = f"35={msg_type}\x0134={seq_num}\x0149={SENDER_COMP_ID}\x0156={TARGET_COMP_ID}\x0152={sending_time}\x01"
    
    # If this is a resent message, FIX protocol requires Tag 43 and Tag 122
    if is_resend and orig_time:
        header += f"43=Y\x01122={orig_time}\x01"
        
    body = header + extra_tags
    body_len = len(body)
    msg = f"8={FIX_VERSION}\x019={body_len}\x01{body}"
    
    checksum = sum(msg.encode('ascii')) % 256
    return msg + f"10={checksum:03d}\x01"

def send_msg(sock, msg):
    print(f"-> Sending:  {msg.replace(chr(1), '|')} ")
    sock.sendall(msg.encode('ascii'))

def recv_msg(sock):
    try:
        data = sock.recv(4096)
        if data:
            print(f"<- Received: {data.decode('ascii').replace(chr(1), '|')} ")
        return data.decode('ascii')
    except socket.error:
        return ""

def extract_tag(msg, tag):
    """Helper to extract a specific FIX tag value using regex."""
    # ADDED THE BACKSLASH inside the brackets: [^\x01]
    match = re.search(rf'\x01{tag}=([^\x01]+)\x01', msg)
    return int(match.group(1)) if match else None

def run_resilience_test():
    print("\n=== STEP 1: BASELINE (Connecting & Sending 10 Orders) ===")
    s1 = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s1.connect((HOST, PORT))
    
    # 1. Logon (SeqNum 1)
    send_msg(s1, build_fix_message("A", 1, "98=0\x01108=30\x01"))
    time.sleep(1) 
    recv_msg(s1)
    
    # 2. Send 10 Orders (SeqNum 2 through 11)
    for i in range(2, 12):
        tags = f"11=ORD{i}\x0121=1\x0155=GOOG\x0154=1\x0160={datetime.utcnow().strftime('%Y%m%d-%H:%M:%S')}\x0140=2\x0144=150.00\x0138=100\x01"
        send_msg(s1, build_fix_message("D", i, tags))
        time.sleep(0.05)
        
    print("\n=== STEP 2: DISRUPTION (Hard Killing Connection) ===")
    s1.close() 
    print("Socket closed! Simulating offline state...")
    
    # 3. Simulate offline buffering (SeqNum 12 through 16)
    buffered_orders = {}
    print("Generating 5 offline orders locally (SeqNum 12-16)...")
    for i in range(12, 17):
        orig_time = datetime.utcnow().strftime('%Y%m%d-%H:%M:%S')
        tags = f"11=OFFLINE_ORD{i}\x0121=1\x0155=MSFT\x0154=1\x0160={orig_time}\x0140=2\x0144=300.00\x0138=50\x01"
        buffered_orders[i] = (tags, orig_time)
        time.sleep(0.1) # Simulate time passing
    
    print("\n=== STEP 3: RECOVERY (Reconnecting with Sequence Gap) ===")
    s2 = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s2.connect((HOST, PORT))
    
    # 4. Reconnect with SeqNum 17
    send_msg(s2, build_fix_message("A", 17, "98=0\x01108=30\x01"))
    
    print("\nListening for Server Resend Request (35=2)...")
    s2.settimeout(3.0) 
    
    try:
        while True:
            data = recv_msg(s2)
            if "35=2" in data:
                # 5. Server requested a resend. Parse what it wants.
                begin_seq = extract_tag(data, 7)
                end_seq = extract_tag(data, 16)
                
                # In FIX, EndSeqNo=0 means "send everything you have up to current"
                if end_seq == 0 or end_seq > 16:
                    end_seq = 16 
                    
                print(f"\n⚡ ACTION: Server requested sequence {begin_seq} to {end_seq}. Resending from buffer...")
                
                # 6. Resend the buffered orders with PossDupFlag (43=Y)
                for seq in range(begin_seq, end_seq + 1):
                    if seq in buffered_orders:
                        tags, orig_time = buffered_orders[seq]
                        resend_msg = build_fix_message("D", seq, tags, is_resend=True, orig_time=orig_time)
                        send_msg(s2, resend_msg)
                        time.sleep(0.05)
                
                print("\n✅ SUCCESS: Buffer flushed. Recovery complete!")
                break
            
    except socket.timeout:
        print("\n❌ TIMEOUT: Server did not respond or didn't send a Resend Request.")
        
    # Wait a moment to catch the server's acknowledgments (Execution Reports)
    time.sleep(1)
    recv_msg(s2)
    s2.close()

if __name__ == "__main__":
    run_resilience_test()