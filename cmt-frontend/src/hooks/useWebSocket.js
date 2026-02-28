import { useEffect, useState, useRef } from 'react';

/**
 * Custom hook to manage WebSocket connection for order streaming
 * @param {string} url - WebSocket server URL
 * @returns {Object} - { messages, connectionStatus, error }
 */
const useWebSocket = (url) => {
  const [messages, setMessages] = useState([]);
  const [connectionStatus, setConnectionStatus] = useState('connecting');
  const [error, setError] = useState(null);
  const socketRef = useRef(null);

  useEffect(() => {
    // Create WebSocket connection
    const socket = new WebSocket(url);
    socketRef.current = socket;

    socket.onopen = () => {
      console.log('✅ Connected to Order Service');
      setConnectionStatus('connected');
      setError(null);
    };

    socket.onmessage = (event) => {
      try {
        console.log('📨 Raw data received:', event.data);
        const newOrder = JSON.parse(event.data);
        
        // Add new order at the beginning of the array
        setMessages((prevMessages) => [newOrder, ...prevMessages]);
      } catch (err) {
        console.error('Failed to parse message:', err);
        setError('Failed to parse incoming data');
      }
    };

    socket.onerror = (event) => {
      console.error('❌ WebSocket error:', event);
      setError('Connection error occurred');
      setConnectionStatus('error');
    };

    socket.onclose = (event) => {
      console.log('🔌 Disconnected from Order Service');
      setConnectionStatus('disconnected');
      
      if (!event.wasClean) {
        setError(`Connection closed unexpectedly (code: ${event.code})`);
      }
    };

    // Cleanup on unmount
    return () => {
      if (socket.readyState === WebSocket.OPEN) {
        socket.close();
      }
    };
  }, [url]);

  return { messages, connectionStatus, error };
};

export default useWebSocket;
