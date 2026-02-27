package com.lab2;

import quickfix.Application;
import quickfix.Message;
import quickfix.SessionID;
import quickfix.DoNotSend;
import quickfix.FieldNotFound;
import quickfix.IncorrectDataFormat;
import quickfix.IncorrectTagValue;
import quickfix.RejectLogon;
import quickfix.UnsupportedMessageType;
public class OrderApplication implements Application {
    @Override
    public void onCreate(SessionID sessionId) {
    System.out.println("Session Created: " + sessionId);
    }
    @Override
    public void onLogon(SessionID sessionId) {
    System.out.println("LOGON Success: " + sessionId);
    }
    @Override
    public void onLogout(SessionID sessionId) {
    System.out.println("LOGOUT: " + sessionId);
    }
    @Override
    public void toAdmin(Message message, SessionID sessionId) {
    // Used for administrative messages (Heartbeats, Logons)
    }
    @Override
    public void fromAdmin(Message message, SessionID sessionId) throws FieldNotFound,
    IncorrectDataFormat, IncorrectTagValue, RejectLogon {
    // Received administrative messages
    }
    @Override
    public void toApp(Message message, SessionID sessionId) throws DoNotSend {
    // Outgoing business messages
    }
    @Override
    public void fromApp(Message message, SessionID sessionId) throws FieldNotFound,
    IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType {
    // Incoming business messages (New Orders will arrive here later)
    System.out.println("Message Received: " + message.toString());
    }
}
