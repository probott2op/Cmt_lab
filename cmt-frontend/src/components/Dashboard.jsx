import React, { useState, useMemo, useCallback, useRef, useEffect } from 'react';
import useWebSocket from '../hooks/useWebSocket';
import './Dashboard.css';

const ROW_HEIGHT = 44; // px — fixed row height for virtual scroll
const BUFFER_ROWS = 8; // Extra rows rendered above/below viewport

const Dashboard = () => {
  const {
    messages: orders, trades, auditTrails, notifications,
    connectionStatus, error, cancelOrder, requestAuditTrail, dismissNotification
  } = useWebSocket('ws://localhost:8081');

  const [activeTab, setActiveTab] = useState('orders');

  // ===== FILTERS =====
  const [filterSymbol, setFilterSymbol] = useState('ALL');
  const [filterSide, setFilterSide] = useState('ALL');
  const [filterStatus, setFilterStatus] = useState('ALL');
  const [searchId, setSearchId] = useState('');

  // ===== PAGINATION =====
  const [pageSize, setPageSize] = useState(25);
  const [currentPage, setCurrentPage] = useState(1);

  // ===== AUDIT TRAIL =====
  const [selectedOrderId, setSelectedOrderId] = useState(null);
  const [auditLoading, setAuditLoading] = useState(false);

  // ===== VIRTUAL SCROLL =====
  const scrollContainerRef = useRef(null);
  const [scrollTop, setScrollTop] = useState(0);

  // ===== HELPERS =====
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
      case 'CANCELLED': return 'status-badge--cancelled';
      case 'PARTIALLY_CANCELLED': return 'status-badge--cancelled';
      case 'CANCEL_PENDING': return 'status-badge--cancel-pending';
      case 'REJECTED': return 'status-badge--rejected';
      case 'NEW':
      default: return 'status-badge--new';
    }
  };

  const getStatusLabel = (status) => {
    switch (status) {
      case 'FILLED': return 'Filled';
      case 'PARTIALLY_FILLED': return 'Partial';
      case 'CANCELLED': return 'Cancelled';
      case 'PARTIALLY_CANCELLED': return 'Part. Cancelled';
      case 'CANCEL_PENDING': return 'Cancelling…';
      case 'REJECTED': return 'Rejected';
      case 'NEW':
      default: return 'New';
    }
  };

  const formatMicrosTimestamp = (micros) => {
    if (!micros) return '—';
    // Timestamps arrive as strings (Gson long→string serialization).
    // Values are within JS safe integer range (~1.77×10^15), so Number() is exact.
    const microsNum = Number(micros);
    const millis = Math.floor(microsNum / 1000);
    const microsPart = microsNum % 1000;
    const date = new Date(millis);
    return date.toLocaleTimeString() + '.' + String(microsPart).padStart(3, '0') + 'μs';
  };

  const isCancellable = (status) => status === 'NEW' || status === 'PARTIALLY_FILLED';

  // ===== UNIQUE SYMBOLS (for filter dropdown) =====
  const uniqueSymbols = useMemo(() => {
    const symbols = new Set(orders.map(o => o.symbol));
    return ['ALL', ...Array.from(symbols).sort()];
  }, [orders]);

  // ===== FILTERED ORDERS =====
  const filteredOrders = useMemo(() => {
    let result = orders;

    if (filterSymbol !== 'ALL') {
      result = result.filter(o => o.symbol === filterSymbol);
    }
    if (filterSide !== 'ALL') {
      result = result.filter(o => formatSide(o.side) === filterSide);
    }
    if (filterStatus !== 'ALL') {
      result = result.filter(o => o.status === filterStatus);
    }
    if (searchId.trim()) {
      const q = searchId.trim().toLowerCase();
      result = result.filter(o =>
        String(o.orderId).includes(q) || String(o.clOrdID).includes(q)
      );
    }

    return result;
  }, [orders, filterSymbol, filterSide, filterStatus, searchId]);

  // ===== PAGINATION =====
  const totalPages = Math.max(1, Math.ceil(filteredOrders.length / pageSize));
  const safePage = Math.min(currentPage, totalPages);

  const paginatedOrders = useMemo(() => {
    const start = (safePage - 1) * pageSize;
    return filteredOrders.slice(start, start + pageSize);
  }, [filteredOrders, safePage, pageSize]);

  // Reset to page 1 when filters change
  useEffect(() => {
    setCurrentPage(1);
  }, [filterSymbol, filterSide, filterStatus, searchId, pageSize]);

  // ===== VIRTUAL SCROLL =====
  const totalHeight = paginatedOrders.length * ROW_HEIGHT;

  const visibleRows = useMemo(() => {
    const container = scrollContainerRef.current;
    const viewportHeight = container ? container.clientHeight : 600;
    const startIdx = Math.max(0, Math.floor(scrollTop / ROW_HEIGHT) - BUFFER_ROWS);
    const endIdx = Math.min(
      paginatedOrders.length,
      Math.ceil((scrollTop + viewportHeight) / ROW_HEIGHT) + BUFFER_ROWS
    );
    return { startIdx, endIdx };
  }, [scrollTop, paginatedOrders.length]);

  const handleScroll = useCallback((e) => {
    setScrollTop(e.target.scrollTop);
  }, []);

  // ===== CANCEL HANDLER =====
  const handleCancel = useCallback((orderId) => {
    cancelOrder(orderId);
  }, [cancelOrder]);

  // ===== AUDIT TRAIL =====
  const handleOrderClick = useCallback((orderId) => {
    if (selectedOrderId === orderId) {
      setSelectedOrderId(null);
      return;
    }
    setSelectedOrderId(orderId);
    // Load audit trail on demand if not already loaded
    if (!auditTrails[orderId]) {
      setAuditLoading(true);
      requestAuditTrail(orderId);
      // Clear loading after a timeout (in case server doesn't respond)
      setTimeout(() => setAuditLoading(false), 5000);
    }
  }, [selectedOrderId, auditTrails, requestAuditTrail]);

  // Clear loading state when audit trail arrives
  useEffect(() => {
    if (selectedOrderId && auditTrails[selectedOrderId]) {
      setAuditLoading(false);
    }
  }, [selectedOrderId, auditTrails]);

  // ===== COMPUTED STATS =====
  const stats = useMemo(() => ({
    total: orders.length,
    unfilled: orders.filter(o => o.status === 'NEW' || o.status === 'PARTIALLY_FILLED').length,
    filled: orders.filter(o => o.status === 'FILLED').length,
    cancelled: orders.filter(o => o.status === 'CANCELLED' || o.status === 'PARTIALLY_CANCELLED').length,
    trades: trades.length,
  }), [orders, trades]);

  const status = getStatusConfig();

  // ===== AUDIT EVENT ICON =====
  const getAuditEventIcon = (eventType) => {
    switch (eventType) {
      case 'ORDER_NEW': return '📝';
      case 'ORDER_ACK': return '✅';
      case 'ORDER_REJECTED': return '❌';
      case 'ORDER_PARTIAL_FILL': return '🟡';
      case 'ORDER_FILLED': return '🟢';
      case 'CANCEL_REQUESTED': return '⏳';
      case 'CANCEL_ACCEPTED': return '🚫';
      case 'CANCEL_REJECTED': return '⚠️';
      default: return '📌';
    }
  };

  const getAuditEventLabel = (eventType) => {
    switch (eventType) {
      case 'ORDER_NEW': return 'Order Created';
      case 'ORDER_ACK': return 'Acknowledged';
      case 'ORDER_REJECTED': return 'Rejected';
      case 'ORDER_PARTIAL_FILL': return 'Partial Fill';
      case 'ORDER_FILLED': return 'Fully Filled';
      case 'CANCEL_REQUESTED': return 'Cancel Requested';
      case 'CANCEL_ACCEPTED': return 'Cancel Accepted';
      case 'CANCEL_REJECTED': return 'Cancel Rejected';
      default: return eventType;
    }
  };

  // Page numbers to show
  const pageNumbers = useMemo(() => {
    const pages = [];
    const maxShow = 5;
    let start = Math.max(1, safePage - Math.floor(maxShow / 2));
    let end = Math.min(totalPages, start + maxShow - 1);
    if (end - start < maxShow - 1) {
      start = Math.max(1, end - maxShow + 1);
    }
    for (let i = start; i <= end; i++) pages.push(i);
    return pages;
  }, [safePage, totalPages]);

  return (
    <div className="dashboard">
      {/* ===== NOTIFICATION TOASTS ===== */}
      <div className="toast-container" id="toast-container">
        {notifications.map(notif => (
          <div
            key={notif.id}
            className={`toast toast--${notif.type}`}
            id={`toast-${notif.id}`}
          >
            <div className="toast__content">
              <span className="toast__icon">
                {notif.type === 'error' ? '✕' : notif.type === 'success' ? '✓' : notif.type === 'warning' ? '⚡' : 'ℹ'}
              </span>
              <span className="toast__message">{notif.message}</span>
            </div>
            <button className="toast__close" onClick={() => dismissNotification(notif.id)}>×</button>
          </div>
        ))}
      </div>

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
            <span className="stat-card__value">{stats.total}</span>
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
            <span className="stat-card__value">{stats.trades}</span>
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
            <span className="stat-card__value">{stats.filled}</span>
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
            <span className="stat-card__value">{stats.unfilled}</span>
            <span className="stat-card__label">Unfilled</span>
          </div>
        </div>
        <div className="stat-card" id="stat-cancelled">
          <div className="stat-card__icon stat-card__icon--red">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="12" cy="12" r="10"/>
              <line x1="15" y1="9" x2="9" y2="15"/>
              <line x1="9" y1="9" x2="15" y2="15"/>
            </svg>
          </div>
          <div className="stat-card__content">
            <span className="stat-card__value">{stats.cancelled}</span>
            <span className="stat-card__label">Cancelled</span>
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

          {/* ===== ORDERS TAB ===== */}
          {activeTab === 'orders' && (
            <div className="table-panel" id="orders-panel">
              {/* Filter Bar */}
              <div className="filter-bar" id="filter-bar">
                <div className="filter-group">
                  <label className="filter-label">Symbol</label>
                  <select
                    className="filter-select"
                    id="filter-symbol"
                    value={filterSymbol}
                    onChange={e => setFilterSymbol(e.target.value)}
                  >
                    {uniqueSymbols.map(s => (
                      <option key={s} value={s}>{s}</option>
                    ))}
                  </select>
                </div>
                <div className="filter-group">
                  <label className="filter-label">Side</label>
                  <select
                    className="filter-select"
                    id="filter-side"
                    value={filterSide}
                    onChange={e => setFilterSide(e.target.value)}
                  >
                    <option value="ALL">All</option>
                    <option value="BUY">Buy</option>
                    <option value="SELL">Sell</option>
                  </select>
                </div>
                <div className="filter-group">
                  <label className="filter-label">Status</label>
                  <select
                    className="filter-select"
                    id="filter-status"
                    value={filterStatus}
                    onChange={e => setFilterStatus(e.target.value)}
                  >
                    <option value="ALL">All</option>
                    <option value="NEW">New</option>
                    <option value="PARTIALLY_FILLED">Partial</option>
                    <option value="FILLED">Filled</option>
                    <option value="CANCELLED">Cancelled</option>
                    <option value="PARTIALLY_CANCELLED">Part. Cancelled</option>
                  </select>
                </div>
                <div className="filter-group filter-group--search">
                  <label className="filter-label">Search ID</label>
                  <input
                    className="filter-input"
                    id="filter-search"
                    type="text"
                    placeholder="Order or Client ID…"
                    value={searchId}
                    onChange={e => setSearchId(e.target.value)}
                  />
                </div>
                <div className="filter-group filter-group--count">
                  <span className="filter-count">{filteredOrders.length} results</span>
                </div>
              </div>

              {filteredOrders.length === 0 ? (
                <div className="empty-state">
                  <div className="empty-state__icon">
                    <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" strokeLinejoin="round">
                      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                      <polyline points="14 2 14 8 20 8"/>
                      <line x1="16" y1="13" x2="8" y2="13"/>
                      <line x1="16" y1="17" x2="8" y2="17"/>
                    </svg>
                  </div>
                  <h3 className="empty-state__title">No Orders Found</h3>
                  <p className="empty-state__text">
                    {orders.length === 0
                      ? 'Waiting for incoming FIX orders...'
                      : 'No orders match the current filters.'}
                  </p>
                </div>
              ) : (
                <>
                  {/* Virtual Scroll Table */}
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
                          <th>Actions</th>
                        </tr>
                      </thead>
                    </table>
                    <div
                      className="virtual-scroll-container"
                      ref={scrollContainerRef}
                      onScroll={handleScroll}
                    >
                      <div className="virtual-scroll-spacer" style={{ height: totalHeight }}>
                        {paginatedOrders
                          .slice(visibleRows.startIdx, visibleRows.endIdx)
                          .map((order, idx) => {
                            const actualIdx = visibleRows.startIdx + idx;
                            const isCancelledRow = order.status === 'CANCELLED' || order.status === 'PARTIALLY_CANCELLED';
                            const isSelected = selectedOrderId === order.orderId;

                            return (
                              <React.Fragment key={order.orderId}>
                                <div
                                  className={`virtual-row ${isCancelledRow ? 'virtual-row--cancelled' : ''} ${isSelected ? 'virtual-row--selected' : ''}`}
                                  style={{
                                    position: 'absolute',
                                    top: actualIdx * ROW_HEIGHT,
                                    height: ROW_HEIGHT,
                                    width: '100%',
                                  }}
                                  onClick={() => handleOrderClick(order.orderId)}
                                  id={`order-row-${order.orderId}`}
                                >
                                  <span className="vcell vcell-mono" style={{ width: '12%' }}>{order.orderId}</span>
                                  <span className="vcell vcell-mono" style={{ width: '10%' }}>{order.clOrdID}</span>
                                  <span className="vcell vcell-symbol" style={{ width: '10%' }}>{order.symbol}</span>
                                  <span className="vcell" style={{ width: '8%' }}>
                                    <span className={`side-badge ${getSideClass(order.side)}`}>
                                      {formatSide(order.side)}
                                    </span>
                                  </span>
                                  <span className="vcell vcell-qty" style={{ width: '8%' }}>
                                    {formatQty(order.originalQuantity || order.quantity)}
                                  </span>
                                  <span className="vcell vcell-qty vcell-qty--remaining" style={{ width: '8%' }}>
                                    {formatQty(order.quantity)}
                                  </span>
                                  <span className="vcell vcell-price" style={{ width: '10%' }}>
                                    ${formatPrice(order.price)}
                                  </span>
                                  <span className="vcell" style={{ width: '12%' }}>
                                    <span className={`status-badge ${getStatusBadgeClass(order.status)}`}>
                                      {order.status === 'CANCEL_PENDING' && (
                                        <span className="cancel-spinner" />
                                      )}
                                      {getStatusLabel(order.status)}
                                    </span>
                                  </span>
                                  <span className="vcell vcell-time" style={{ width: '14%' }}>
                                    {formatMicrosTimestamp(order.timestampMicros)}
                                  </span>
                                  <span className="vcell vcell-actions" style={{ width: '8%' }}>
                                    {isCancellable(order.status) && (
                                      <button
                                        className="cancel-btn"
                                        id={`cancel-btn-${order.orderId}`}
                                        onClick={(e) => {
                                          e.stopPropagation();
                                          handleCancel(order.orderId);
                                        }}
                                        title="Cancel Order"
                                      >
                                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round">
                                          <line x1="18" y1="6" x2="6" y2="18"/>
                                          <line x1="6" y1="6" x2="18" y2="18"/>
                                        </svg>
                                        Cancel
                                      </button>
                                    )}
                                    {order.status === 'CANCEL_PENDING' && (
                                      <span className="cancel-pending-label">Pending…</span>
                                    )}
                                  </span>
                                </div>

                                {/* Inline Audit Trail Panel */}
                                {isSelected && (
                                  <div
                                    className="audit-panel"
                                    style={{
                                      position: 'absolute',
                                      top: actualIdx * ROW_HEIGHT + ROW_HEIGHT,
                                      width: '100%',
                                      zIndex: 10,
                                    }}
                                    onClick={(e) => e.stopPropagation()}
                                  >
                                    <div className="audit-panel__header">
                                      <h4 className="audit-panel__title">
                                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                                          <circle cx="12" cy="12" r="10"/>
                                          <polyline points="12 6 12 12 16 14"/>
                                        </svg>
                                        Audit Trail — Order {order.orderId}
                                      </h4>
                                      <button
                                        className="audit-panel__close"
                                        onClick={() => setSelectedOrderId(null)}
                                      >×</button>
                                    </div>
                                    <div className="audit-timeline">
                                      {auditLoading && !auditTrails[order.orderId] ? (
                                        <div className="audit-loading">
                                          <div className="audit-loading__spinner" />
                                          <span>Loading audit trail…</span>
                                        </div>
                                      ) : auditTrails[order.orderId]?.length > 0 ? (
                                        auditTrails[order.orderId].map((evt, i) => (
                                          <div key={evt.eventId || i} className="audit-event">
                                            <div className="audit-event__dot">
                                              <span className="audit-event__icon">{getAuditEventIcon(evt.eventType)}</span>
                                              {i < auditTrails[order.orderId].length - 1 && (
                                                <div className="audit-event__line" />
                                              )}
                                            </div>
                                            <div className="audit-event__content">
                                              <div className="audit-event__header">
                                                <span className="audit-event__type">{getAuditEventLabel(evt.eventType)}</span>
                                                <span className="audit-event__time">{formatMicrosTimestamp(evt.timestampMicros)}</span>
                                              </div>
                                              <p className="audit-event__detail">{evt.detail}</p>
                                              {evt.fromStatus && evt.toStatus && (
                                                <div className="audit-event__transition">
                                                  <span className={`status-badge status-badge--sm ${getStatusBadgeClass(evt.fromStatus)}`}>
                                                    {evt.fromStatus || '—'}
                                                  </span>
                                                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                                                    <line x1="5" y1="12" x2="19" y2="12"/>
                                                    <polyline points="12 5 19 12 12 19"/>
                                                  </svg>
                                                  <span className={`status-badge status-badge--sm ${getStatusBadgeClass(evt.toStatus)}`}>
                                                    {evt.toStatus}
                                                  </span>
                                                </div>
                                              )}
                                            </div>
                                          </div>
                                        ))
                                      ) : (
                                        <div className="audit-empty">No audit events found for this order.</div>
                                      )}
                                    </div>
                                  </div>
                                )}
                              </React.Fragment>
                            );
                          })}
                      </div>
                    </div>
                  </div>

                  {/* Pagination Controls */}
                  <div className="pagination" id="pagination">
                    <div className="pagination__info">
                      <span>
                        Showing {((safePage - 1) * pageSize) + 1}–{Math.min(safePage * pageSize, filteredOrders.length)} of {filteredOrders.length}
                      </span>
                    </div>
                    <div className="pagination__controls">
                      <button
                        className="pagination__btn"
                        disabled={safePage <= 1}
                        onClick={() => setCurrentPage(1)}
                        title="First page"
                      >«</button>
                      <button
                        className="pagination__btn"
                        disabled={safePage <= 1}
                        onClick={() => setCurrentPage(p => Math.max(1, p - 1))}
                        title="Previous page"
                      >‹</button>
                      {pageNumbers.map(p => (
                        <button
                          key={p}
                          className={`pagination__btn ${p === safePage ? 'pagination__btn--active' : ''}`}
                          onClick={() => setCurrentPage(p)}
                        >{p}</button>
                      ))}
                      <button
                        className="pagination__btn"
                        disabled={safePage >= totalPages}
                        onClick={() => setCurrentPage(p => Math.min(totalPages, p + 1))}
                        title="Next page"
                      >›</button>
                      <button
                        className="pagination__btn"
                        disabled={safePage >= totalPages}
                        onClick={() => setCurrentPage(totalPages)}
                        title="Last page"
                      >»</button>
                    </div>
                    <div className="pagination__size">
                      <label>Rows:</label>
                      <select
                        value={pageSize}
                        onChange={e => setPageSize(Number(e.target.value))}
                        className="pagination__select"
                        id="page-size-select"
                      >
                        <option value={25}>25</option>
                        <option value={50}>50</option>
                        <option value={100}>100</option>
                      </select>
                    </div>
                  </div>
                </>
              )}
            </div>
          )}

          {/* ===== TRADES TAB ===== */}
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
