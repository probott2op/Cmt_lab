import React, { useState, useMemo } from 'react';
import useWebSocket from '../hooks/useWebSocket';
import './Dashboard.css';

const Dashboard = () => {
  const { messages: orders, trades, connectionStatus, error } = useWebSocket('ws://localhost:8081');
  const [activeTab, setActiveTab] = useState('orders');

  const getStatusConfig = () => {
    switch (connectionStatus) {
      case 'connected':
        return { color: '#10b981', label: 'Live', dotClass: 'status-dot--live' };
      case 'connecting':
      case 'reconnecting':
        return { color: '#f59e0b', label: 'Connecting', dotClass: 'status-dot--connecting' };
      case 'disconnected':
      case 'error':
        return { color: '#ef4444', label: 'Offline', dotClass: 'status-dot--offline' };
      default:
        return { color: '#64748b', label: 'Unknown', dotClass: '' };
    }
  };

  const formatSide = (side) => (side === '1' || side === 1 ? 'BUY' : 'SELL');
  const getSideClass = (side) => (side === '1' || side === 1 ? 'side--buy' : 'side--sell');
  const formatPrice = (p) => (typeof p === 'number' ? p.toFixed(2) : parseFloat(p).toFixed(2));
  const formatQty = (q) => (typeof q === 'number' ? q.toFixed(0) : parseFloat(q).toFixed(0));

  const getStatusBadgeClass = (status) => {
    switch (status) {
      case 'FILLED': return 'status-badge--filled';
      case 'PARTIALLY_FILLED': return 'status-badge--partial';
      case 'NEW':
      default: return 'status-badge--new';
    }
  };

  const getStatusLabel = (status) => {
    switch (status) {
      case 'FILLED': return 'Filled';
      case 'PARTIALLY_FILLED': return 'Partial';
      case 'NEW':
      default: return 'New';
    }
  };

  const formatMicrosTimestamp = (micros) => {
    if (!micros) return '—';
    const millis = Math.floor(micros / 1000);
    const microsPart = micros % 1000;
    const date = new Date(millis);
    return date.toLocaleTimeString() + '.' + String(microsPart).padStart(3, '0') + 'μs';
  };

  // Computed stats
  const unfilledCount = useMemo(() =>
    orders.filter(o => o.status === 'NEW' || o.status === 'PARTIALLY_FILLED').length,
    [orders]
  );

  const filledCount = useMemo(() =>
    orders.filter(o => o.status === 'FILLED').length,
    [orders]
  );

  const status = getStatusConfig();

  return (
    <div className="dashboard">
      {/* ===== TOP BAR ===== */}
      <header className="topbar">
        <div className="topbar__left">
          <div className="topbar__logo">
            <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="url(#logoGrad)" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
              <defs>
                <linearGradient id="logoGrad" x1="0%" y1="0%" x2="100%" y2="100%">
                  <stop offset="0%" stopColor="#6366f1"/>
                  <stop offset="100%" stopColor="#a855f7"/>
                </linearGradient>
              </defs>
              <polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/>
            </svg>
          </div>
          <div className="topbar__brand">
            <h1 className="topbar__title">CMT Trading Engine</h1>
            <span className="topbar__subtitle">FIX 4.4 · Real-Time Dashboard</span>
          </div>
        </div>
        <div className="topbar__right">
          {error && (
            <div className="topbar__alert">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/>
                <line x1="12" y1="9" x2="12" y2="13"/>
                <line x1="12" y1="17" x2="12.01" y2="17"/>
              </svg>
              <span>{error}</span>
            </div>
          )}
          <div className="topbar__status" id="connection-status">
            <div className={`status-dot ${status.dotClass}`} style={{ '--dot-color': status.color }}/>
            <span className="status-label">{status.label}</span>
          </div>
        </div>
      </header>

      {/* ===== STATS BAR ===== */}
      <section className="stats-bar">
        <div className="stat-card" id="stat-orders">
          <div className="stat-card__icon stat-card__icon--indigo">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
              <polyline points="14 2 14 8 20 8"/>
              <line x1="16" y1="13" x2="8" y2="13"/>
              <line x1="16" y1="17" x2="8" y2="17"/>
            </svg>
          </div>
          <div className="stat-card__content">
            <span className="stat-card__value">{orders.length}</span>
            <span className="stat-card__label">Orders</span>
          </div>
        </div>
        <div className="stat-card" id="stat-trades">
          <div className="stat-card__icon stat-card__icon--green">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <polyline points="23 6 13.5 15.5 8.5 10.5 1 18"/>
              <polyline points="17 6 23 6 23 12"/>
            </svg>
          </div>
          <div className="stat-card__content">
            <span className="stat-card__value">{trades.length}</span>
            <span className="stat-card__label">Trades</span>
          </div>
        </div>
        <div className="stat-card" id="stat-filled">
          <div className="stat-card__icon stat-card__icon--purple">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
              <polyline points="22 4 12 14.01 9 11.01"/>
            </svg>
          </div>
          <div className="stat-card__content">
            <span className="stat-card__value">{filledCount}</span>
            <span className="stat-card__label">Filled</span>
          </div>
        </div>
        <div className="stat-card" id="stat-unfilled">
          <div className="stat-card__icon stat-card__icon--amber">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="12" cy="12" r="10"/>
              <polyline points="12 6 12 12 16 14"/>
            </svg>
          </div>
          <div className="stat-card__content">
            <span className="stat-card__value">{unfilledCount}</span>
            <span className="stat-card__label">Unfilled</span>
          </div>
        </div>
      </section>

      {/* ===== MAIN CONTENT ===== */}
      <main className="dashboard__main dashboard__main--full">
        <div className="dashboard__tables">
          {/* Tab Navigation */}
          <div className="tab-nav">
            <button
              className={`tab-btn ${activeTab === 'orders' ? 'tab-btn--active' : ''}`}
              onClick={() => setActiveTab('orders')}
              id="tab-orders"
            >
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                <polyline points="14 2 14 8 20 8"/>
              </svg>
              Orders
              <span className="tab-btn__count">{orders.length}</span>
            </button>
            <button
              className={`tab-btn ${activeTab === 'trades' ? 'tab-btn--active' : ''}`}
              onClick={() => setActiveTab('trades')}
              id="tab-trades"
            >
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <polyline points="23 6 13.5 15.5 8.5 10.5 1 18"/>
                <polyline points="17 6 23 6 23 12"/>
              </svg>
              Trades
              <span className="tab-btn__count">{trades.length}</span>
            </button>
          </div>

          {/* Orders Table */}
          {activeTab === 'orders' && (
            <div className="table-panel" id="orders-panel">
              {orders.length === 0 ? (
                <div className="empty-state">
                  <div className="empty-state__icon">
                    <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" strokeLinejoin="round">
                      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                      <polyline points="14 2 14 8 20 8"/>
                      <line x1="16" y1="13" x2="8" y2="13"/>
                      <line x1="16" y1="17" x2="8" y2="17"/>
                    </svg>
                  </div>
                  <h3 className="empty-state__title">No Orders Yet</h3>
                  <p className="empty-state__text">Waiting for incoming FIX orders...</p>
                </div>
              ) : (
                <div className="table-scroll">
                  <table className="data-table" id="orders-table">
                    <thead>
                      <tr>
                        <th>Order ID</th>
                        <th>Client ID</th>
                        <th>Symbol</th>
                        <th>Side</th>
                        <th>Orig Qty</th>
                        <th>Remaining</th>
                        <th>Price</th>
                        <th>Status</th>
                        <th>Time</th>
                      </tr>
                    </thead>
                    <tbody>
                      {orders.map((order, i) => (
                        <tr key={`${order.orderId}-${i}`} className="data-row data-row--animate">
                          <td className="cell-mono">{order.orderId}</td>
                          <td className="cell-mono">{order.clOrdID}</td>
                          <td className="cell-symbol">{order.symbol}</td>
                          <td>
                            <span className={`side-badge ${getSideClass(order.side)}`}>
                              {formatSide(order.side)}
                            </span>
                          </td>
                          <td className="cell-qty">{formatQty(order.originalQuantity || order.quantity)}</td>
                          <td className="cell-qty cell-qty--remaining">
                            {formatQty(order.quantity)}
                          </td>
                          <td className="cell-price">${formatPrice(order.price)}</td>
                          <td>
                            <span className={`status-badge ${getStatusBadgeClass(order.status)}`}>
                              {getStatusLabel(order.status)}
                            </span>
                          </td>
                          <td className="cell-time">{formatMicrosTimestamp(order.timestampMicros)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          )}

          {/* Trades Table */}
          {activeTab === 'trades' && (
            <div className="table-panel" id="trades-panel">
              {trades.length === 0 ? (
                <div className="empty-state">
                  <div className="empty-state__icon">
                    <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" strokeLinejoin="round">
                      <polyline points="23 6 13.5 15.5 8.5 10.5 1 18"/>
                      <polyline points="17 6 23 6 23 12"/>
                    </svg>
                  </div>
                  <h3 className="empty-state__title">No Trades Yet</h3>
                  <p className="empty-state__text">Waiting for order matching...</p>
                </div>
              ) : (
                <div className="table-scroll">
                  <table className="data-table" id="trades-table">
                    <thead>
                      <tr>
                        <th>Exec ID</th>
                        <th>Buy Order</th>
                        <th>Sell Order</th>
                        <th>Symbol</th>
                        <th>Side</th>
                        <th>Qty</th>
                        <th>Price</th>
                        <th>Time</th>
                      </tr>
                    </thead>
                    <tbody>
                      {trades.map((trade, i) => (
                        <tr key={`${trade.execId}-${i}`} className="data-row data-row--animate">
                          <td className="cell-mono">{trade.execId}</td>
                          <td className="cell-mono cell-mono--buy">{trade.buyOrderId}</td>
                          <td className="cell-mono cell-mono--sell">{trade.sellOrderId}</td>
                          <td className="cell-symbol">{trade.symbol}</td>
                          <td>
                            <span className={`side-badge ${getSideClass(trade.side)}`}>
                              {formatSide(trade.side)}
                            </span>
                          </td>
                          <td className="cell-qty">{trade.execQty}</td>
                          <td className="cell-price">${formatPrice(trade.execPrice)}</td>
                          <td className="cell-time">{formatMicrosTimestamp(trade.matchTimeMicros)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          )}
        </div>
      </main>
    </div>
  );
};

export default Dashboard;
