import { useEffect, useMemo, useState } from "react";
import axios from "axios";
import {
  Activity,
  AlertCircle,
  ArrowUpRight,
  CheckCircle2,
  Clock3,
  CreditCard,
  RefreshCw,
  Search,
  ShieldAlert,
  FlaskConical,
  WalletCards,
  XCircle,
} from "lucide-react";
import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import "./App.css";

const API = import.meta.env.VITE_API_URL || "http://localhost:8080/api";

function App() {
  const [payments, setPayments] = useState([]);
  const [attempts, setAttempts] = useState([]);
  const [selectedPayment, setSelectedPayment] = useState(null);
  const [decision, setDecision] = useState(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState("");
  const [activePage, setActivePage] = useState("dashboard");
  const [paymentSearch, setPaymentSearch] = useState("");
  const [paymentStatus, setPaymentStatus] = useState("ALL");

  const loadDashboard = async () => {
    try {
      setError("");

      const [paymentsResponse, attemptsResponse] = await Promise.all([
        axios.get(`${API}/payments`),
        axios.get(`${API}/recovery-attempts`),
      ]);

      setPayments(paymentsResponse.data);
      setAttempts(attemptsResponse.data);
    } catch (err) {
      console.error(err);
      setError(
        "Unable to connect to RecoverAI backend. Make sure Spring Boot is running on port 8080."
      );
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  useEffect(() => {
    loadDashboard();
  }, []);

  const refresh = async () => {
    setRefreshing(true);
    await loadDashboard();
  };

  const executeAttempt = async (attempt) => {
    try {
      setError("");

      await axios.post(
        `${API}/recovery-attempts/${attempt.id}/execute`
      );

      await loadDashboard();
    } catch (err) {
      console.error("Recovery execution error:", err);

      setError(
        err?.response?.data?.error ||
        "Unable to execute recovery attempt."
      );
    }
  };

  const classifyPayment = async (payment) => {
    try {
      setSelectedPayment(payment);
      setDecision(null);
      setError("");

      const isFailed =
        payment.status?.toUpperCase() === "FAILED";

      let decisionResponse;

      if (isFailed) {
        // First try to load an already-generated decision.
        try {
          decisionResponse = await axios.get(
            `${API}/payments/${payment.id}/decision`
          );
        } catch (decisionError) {
          // If no decision exists yet, generate one.
          if (decisionError?.response?.status === 404) {
            decisionResponse = await axios.post(
              `${API}/payments/${payment.id}/classify`
            );
          } else {
            throw decisionError;
          }
        }
      } else {
        decisionResponse = await axios.get(
          `${API}/payments/${payment.id}/decision`
        );
      }

      setDecision(decisionResponse.data);
      await loadDashboard();
    } catch (err) {
      console.error("Recovery decision error:", err);

      const message =
        err?.response?.status === 404
          ? "No recovery decision has been recorded for this payment yet."
          : err?.response?.data?.error ||
            "Unable to load recovery decision.";

      setError(message);
    }
  };
  const handlePaymentAction = (payment) => {
    const status = payment.status?.toUpperCase();

    if (status === "FAILED") {
      classifyPayment(payment);
      return;
    }

    setSelectedPayment(payment);
    setError("");

    if (status === "SUCCESS") {
      setDecision({
        uiType: "SUCCESS",
        strategy: "ALREADY_RECOVERED",
        recommendedAction: "No recovery action is required.",
        reason: "This payment has already completed successfully.",
        riskLevel: "LOW",
        confidence: 1,
      });
      return;
    }

    if (status === "MANUAL_REVIEW") {
      setDecision({
        uiType: "MANUAL_REVIEW",
        strategy: "MANUAL_REVIEW",
        recommendedAction: "Review this payment manually.",
        reason:
          "This payment is already marked for manual review and should not be sent through automated recovery classification.",
        riskLevel: "HIGH",
        confidence: null,
      });
      return;
    }

    setDecision({
      uiType: "OTHER",
      strategy: "NO_RECOVERY_ACTION",
      recommendedAction: "No automated recovery action is available.",
      reason: "This payment is not in a failed state requiring recovery analysis.",
      riskLevel: "LOW",
      confidence: null,
    });
  };

  const getPaymentActionLabel = (payment) => {
    switch (payment.status?.toUpperCase()) {
      case "FAILED":
        return "Analyze";
      case "SUCCESS":
        return "Recovered";
      case "MANUAL_REVIEW":
        return "Review";
      default:
        return "View";
    }
  };

  const stats = useMemo(() => {
    const total = payments.length;

    const success = payments.filter(
      (p) => p.status?.toUpperCase() === "SUCCESS"
    ).length;

    const failed = payments.filter(
      (p) => p.status?.toUpperCase() === "FAILED"
    ).length;

    const manualReview = payments.filter(
      (p) => p.status?.toUpperCase() === "MANUAL_REVIEW"
    ).length;

    const scheduled = attempts.filter(
      (a) => a.status?.toUpperCase() === "SCHEDULED"
    ).length;

    const recoveryRate =
      total > 0 ? ((success / total) * 100).toFixed(1) : "0.0";

    return {
      total,
      success,
      failed,
      manualReview,
      scheduled,
      recoveryRate,
    };
  }, [payments, attempts]);

  const filteredPayments = useMemo(() => {
    const query = paymentSearch.trim().toLowerCase();

    return payments.filter((payment) => {
      const matchesSearch =
        !query ||
        String(payment.id ?? "").toLowerCase().includes(query) ||
        String(payment.customerId ?? "").toLowerCase().includes(query) ||
        String(payment.failureReason ?? "").toLowerCase().includes(query);

      const matchesStatus =
        paymentStatus === "ALL" ||
        payment.status?.toUpperCase() === paymentStatus;

      return matchesSearch && matchesStatus;
    });
  }, [payments, paymentSearch, paymentStatus]);

  const chartData = useMemo(() => {
    const buckets = {};

    attempts.forEach((attempt) => {
      const date = new Date(
        attempt.executedAt || attempt.createdAt
      );

      if (Number.isNaN(date.getTime())) return;

      const label = date.toLocaleDateString("en-IN", {
        day: "2-digit",
        month: "short",
      });

      if (!buckets[label]) {
        buckets[label] = {
          date: label,
          attempts: 0,
          successful: 0,
        };
      }

      buckets[label].attempts += 1;

      if (attempt.status?.toUpperCase() === "SUCCESS") {
        buckets[label].successful += 1;
      }
    });

    return Object.values(buckets).slice(-7);
  }, [attempts]);

  const recentAttempts = [...attempts]
    .sort(
      (a, b) =>
        new Date(b.createdAt) - new Date(a.createdAt)
    )
    .slice(0, 6);

  const formatDate = (value) => {
    if (!value) return "N/A";

    return new Date(value).toLocaleString("en-IN", {
      day: "2-digit",
      month: "short",
      hour: "2-digit",
      minute: "2-digit",
    });
  };

  const formatAmount = (amount, currency = "INR") => {
    return new Intl.NumberFormat("en-IN", {
      style: "currency",
      currency,
      maximumFractionDigits: 2,
    }).format(amount);
  };

  const statusClass = (status) => {
    switch (status?.toUpperCase()) {
      case "SUCCESS":
        return "success";

      case "FAILED":
        return "failed";

      case "MANUAL_REVIEW":
        return "review";

      case "SCHEDULED":
        return "scheduled";

      case "SKIPPED":
        return "skipped";

      default:
        return "neutral";
    }
  };

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <div className="brand-mark">R</div>
          <div>
            <div className="brand-name">RecoverAI</div>
            <div className="brand-subtitle">Payment Recovery</div>
          </div>
        </div>

        <nav className="navigation">
          <div className="nav-section">WORKSPACE</div>

          <button
            className={`nav-item ${activePage === "dashboard" ? "active" : ""}`}
            onClick={() => setActivePage("dashboard")}
          >
            <Activity size={18} />
            Dashboard
          </button>

          <button
            className={`nav-item ${activePage === "payments" ? "active" : ""}`}
            onClick={() => setActivePage("payments")}
          >
            <CreditCard size={18} />
            Payments
          </button>

          <button
            className={`nav-item ${activePage === "recovery-attempts" ? "active" : ""}`}
            type="button"
            onClick={() => setActivePage("recovery-attempts")}
          >
            <RefreshCw size={18} />
            Recovery Attempts
          </button>

          <button
            className={`nav-item ${activePage === "manual-review" ? "active" : ""}`}
            type="button"
            onClick={() => setActivePage("manual-review")}
          >
            <ShieldAlert size={18} />
            Manual Review
          </button>
          <button
            className={`nav-item ${activePage === "simulation" ? "active" : ""}`}
            type="button"
            onClick={() => setActivePage("simulation")}
          >
            <FlaskConical size={18} />
            Simulation
          </button>


          <div className="nav-section nav-section-bottom">
            SYSTEM
          </div>

          <button
            className={`nav-item ${activePage === "system-health" ? "active" : ""}`}
            type="button"
            onClick={() => setActivePage("system-health")}
          >
            <Activity size={18} />
            System Health
          </button>
        </nav>

        <div className="sidebar-footer">
          <div className="system-dot"></div>
          <div>
            <div className="system-title">System operational</div>
            <div className="system-subtitle">Backend connected</div>
          </div>
        </div>
      </aside>

      <main className="main-content">
        <header className="topbar">
          <div>
            <div className="breadcrumb">
              Workspace / {
                activePage === "dashboard"
                  ? "Dashboard"
                  : activePage === "payments"
                    ? "Payments"
                    : activePage === "recovery-attempts"
                      ? "Recovery Attempts"
                      : activePage === "manual-review"
                        ? "Manual Review"
                        : "Recovery Simulation"
              }
            </div>
            <h1>
  {
    activePage === "dashboard"
      ? "Recovery Overview"
      : activePage === "payments"
        ? "Payments"
        : activePage === "recovery-attempts"
          ? "Recovery Attempts"
          : activePage === "manual-review"
            ? "Manual Review"
            : activePage === "simulation"
              ? "Recovery Simulation"
              : activePage === "system-health"
                ? "System Health"
                : "Recovery Overview"
  }
</h1>
            <p>
              {activePage === "dashboard"
                ? "Monitor payment failures and automated recovery activity."
                : activePage === "simulation"
                  ? "Run controlled payment-failure scenarios and evaluate recovery outcomes."
                  : activePage === "system-health"
                    ? "Monitor RecoverAI service connectivity and operational status."
                    : "Review payment records and analyze recovery decisions."}
            </p>
          </div>

          <button
            className="refresh-button"
            onClick={refresh}
            disabled={refreshing}
          >
            <RefreshCw
              size={16}
              className={refreshing ? "spin" : ""}
            />
            Refresh
          </button>
        </header>

        {error && (
          <div className="error-banner">
            <AlertCircle size={18} />
            {error}
          </div>
        )}

        {loading ? (
          <div className="loading-state">
            <RefreshCw className="spin" size={26} />
            <span>Loading recovery data...</span>
          </div>
        ) : activePage === "payments" ? (
          <PaymentsPage
            payments={filteredPayments}
            totalPayments={payments.length}
            search={paymentSearch}
            status={paymentStatus}
            onSearch={setPaymentSearch}
            onStatus={setPaymentStatus}
            onAnalyze={handlePaymentAction}
            getPaymentActionLabel={getPaymentActionLabel}
            formatDate={formatDate}
            formatAmount={formatAmount}
          />
        ) : activePage === "recovery-attempts" ? (
          <RecoveryAttemptsPage
            attempts={attempts}
            formatDate={formatDate}
            onExecute={executeAttempt}
          />
        ) : activePage === "manual-review" ? (
          <ManualReviewPage
            payments={payments.filter(
              (payment) =>
                payment.status?.toUpperCase() === "MANUAL_REVIEW"
            )}
            onAnalyze={handlePaymentAction}
            getPaymentActionLabel={getPaymentActionLabel}
            formatDate={formatDate}
            formatAmount={formatAmount}
          />
        ) : activePage === "simulation" ? (
          <SimulationPage />
        ) : activePage === "system-health" ? (
          <SystemHealthPage
            payments={payments}
            attempts={attempts}
          />
        ) : (
          <>
            <section className="stats-grid">
              <StatCard
                title="Total Payments"
                value={stats.total}
                icon={<WalletCards size={20} />}
                description="All processed payments"
              />

              <StatCard
                title="Recovery Rate"
                value={`${stats.recoveryRate}%`}
                icon={<ArrowUpRight size={20} />}
                description={`${stats.success} successfully recovered`}
                positive
              />

              <StatCard
                title="Failed Payments"
                value={stats.failed}
                icon={<XCircle size={20} />}
                description="Currently awaiting recovery"
                danger
              />

              <StatCard
                title="Manual Review"
                value={stats.manualReview}
                icon={<ShieldAlert size={20} />}
                description={`${stats.scheduled} retries scheduled`}
                warning
              />
            </section>

            <section className="dashboard-grid">
              <div className="panel chart-panel">
                <div className="panel-header">
                  <div>
                    <h2>Recovery Activity</h2>
                    <p>Recovery attempts over time</p>
                  </div>

                  <div className="chart-legend">
                    <span>
                      <i className="legend-attempts"></i>
                      Attempts
                    </span>

                    <span>
                      <i className="legend-success"></i>
                      Successful
                    </span>
                  </div>
                </div>

                <div className="chart-container">
                  {chartData.length === 0 ? (
                    <div className="empty-state">
                      No recovery activity yet.
                    </div>
                  ) : (
                    <ResponsiveContainer width="100%" height="100%">
                      <AreaChart data={chartData}>
                        <defs>
                          <linearGradient
                            id="attemptGradient"
                            x1="0"
                            y1="0"
                            x2="0"
                            y2="1"
                          >
                            <stop
                              offset="0%"
                              stopOpacity={0.25}
                            />
                            <stop
                              offset="100%"
                              stopOpacity={0}
                            />
                          </linearGradient>
                        </defs>

                        <CartesianGrid
                          strokeDasharray="3 3"
                          vertical={false}
                        />

                        <XAxis
                          dataKey="date"
                          axisLine={false}
                          tickLine={false}
                        />

                        <YAxis
                          allowDecimals={false}
                          axisLine={false}
                          tickLine={false}
                        />

                        <Tooltip />

                        <Area
                          type="monotone"
                          dataKey="attempts"
                          strokeWidth={2}
                          fill="url(#attemptGradient)"
                        />

                        <Area
                          type="monotone"
                          dataKey="successful"
                          strokeWidth={2}
                          fill="none"
                        />
                      </AreaChart>
                    </ResponsiveContainer>
                  )}
                </div>
              </div>

              <div className="panel status-panel">
                <div className="panel-header">
                  <div>
                    <h2>Payment Health</h2>
                    <p>Current payment distribution</p>
                  </div>
                </div>

                <div className="health-list">
                  <HealthRow
                    label="Successful"
                    value={stats.success}
                    total={stats.total}
                    icon={<CheckCircle2 size={18} />}
                    type="success"
                  />

                  <HealthRow
                    label="Failed"
                    value={stats.failed}
                    total={stats.total}
                    icon={<XCircle size={18} />}
                    type="failed"
                  />

                  <HealthRow
                    label="Manual Review"
                    value={stats.manualReview}
                    total={stats.total}
                    icon={<ShieldAlert size={18} />}
                    type="review"
                  />
                </div>
              </div>
            </section>

            <section className="lower-grid">
              <div className="panel payments-panel">
                <div className="panel-header">
                  <div>
                    <h2>Recent Payments</h2>
                    <p>Latest payment recovery candidates</p>
                  </div>

                  <span className="record-count">
                    {payments.length} records
                  </span>
                </div>

                <div className="table-wrapper">
                  <table>
                    <thead>
                      <tr>
                        <th>Payment</th>
                        <th>Customer</th>
                        <th>Amount</th>
                        <th>Failure Reason</th>
                        <th>Status</th>
                        <th></th>
                      </tr>
                    </thead>

                    <tbody>
                      {payments.slice(0, 8).map((payment) => (
                        <tr key={payment.id}>
                          <td>
                            <div className="payment-id">
                              #{payment.id}
                            </div>
                            <div className="table-date">
                              {formatDate(payment.createdAt)}
                            </div>
                          </td>

                          <td>{payment.customerId}</td>

                          <td className="amount">
                            {formatAmount(
                              payment.amount,
                              payment.currency
                            )}
                          </td>

                          <td>
                            <span className="failure-reason">
                              {payment.failureReason === "HIGH_VALUE_FAILURE" ? "HIGH_VALUE_RISK" : (payment.failureReason || "N/A")}
                            </span>
                          </td>

                          <td>
                            <StatusBadge
                              status={payment.status}
                            />
                          </td>

                          <td>
                            <button
                              className="details-button"
                              onClick={() => handlePaymentAction(payment)}
                            >
                              {getPaymentActionLabel(payment)}
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>

                  {payments.length === 0 && (
                    <div className="empty-state">
                      No payments found.
                    </div>
                  )}
                </div>
              </div>

              <div className="panel attempts-panel">
                <div className="panel-header">
                  <div>
                    <h2>Recovery Timeline</h2>
                    <p>Latest automated attempts</p>
                  </div>
                </div>

                <div className="timeline">
                  {recentAttempts.map((attempt) => (
                    <div
                      className="timeline-item"
                      key={attempt.id}
                    >
                      <div
                        className={`timeline-icon ${statusClass(
                          attempt.status
                        )}`}
                      >
                        {attempt.status === "SUCCESS" ? (
                          <CheckCircle2 size={16} />
                        ) : attempt.status === "SCHEDULED" ? (
                          <Clock3 size={16} />
                        ) : (
                          <RefreshCw size={16} />
                        )}
                      </div>

                      <div className="timeline-content">
                        <div className="timeline-title">
                          Payment #{attempt.paymentId} - Attempt #{attempt.attemptNumber}
                          <StatusBadge
                            status={attempt.status}
                          />
                        </div>

                        <div className="timeline-description">
                          {attempt.result ||
                            "Recovery attempt scheduled"}
                        </div>

                        <div className="timeline-date">
                          {formatDate(
                            attempt.executedAt ||
                              attempt.scheduledAt ||
                              attempt.createdAt
                          )}
                        </div>
                      </div>
                    </div>
                  ))}

                  {recentAttempts.length === 0 && (
                    <div className="empty-state">
                      No recovery attempts found.
                    </div>
                  )}
                </div>
              </div>
            </section>
          </>
        )}
      </main>

      {selectedPayment && (
        <div
          className="modal-backdrop"
          onClick={() => {
            setSelectedPayment(null);
            setDecision(null);
          }}
        >
          <div
            className="decision-modal"
            onClick={(event) => event.stopPropagation()}
          >
            <div className="modal-header">
              <div>
                <div className="modal-label">
                  {selectedPayment.status?.toUpperCase() === "SUCCESS"
                    ? "PAYMENT RECOVERY STATUS"
                    : selectedPayment.status?.toUpperCase() === "MANUAL_REVIEW"
                      ? "MANUAL REVIEW STATUS"
                      : "AI RECOVERY ANALYSIS"}
                </div>

                <h2>
                  Payment #{selectedPayment.id}
                </h2>
              </div>

              <button
                className="close-button"
                onClick={() => {
                  setSelectedPayment(null);
                  setDecision(null);
                }}
              >
                
              </button>
            </div>

            <div className="decision-payment">
              <div>
                <span>Customer</span>
                <strong>{selectedPayment.customerId}</strong>
              </div>

              <div>
                <span>Amount</span>
                <strong>
                  {formatAmount(
                    selectedPayment.amount,
                    selectedPayment.currency
                  )}
                </strong>
              </div>

              <div>
                <span>
                  {selectedPayment.status?.toUpperCase() === "SUCCESS"
                    ? "Status"
                    : "Failure"}
                </span>
                <strong>
                  {selectedPayment.status?.toUpperCase() === "SUCCESS"
                    ? "Payment completed successfully"
                    : (selectedPayment.failureReason === "HIGH_VALUE_FAILURE" ? "HIGH_VALUE_RISK" : (selectedPayment.failureReason || "Unknown"))}
                </strong>
              </div>
            </div>

            {!decision ? (
              <div className="decision-loading">
                <RefreshCw className="spin" size={22} />
                <span>Generating recovery decision...</span>
              </div>
            ) : decision.uiType === "SUCCESS" ? (
              <div className="decision-result">
                <div className="decision-strategy">
                  <span>Recovery Status</span>
                  <strong>ALREADY RECOVERED</strong>
                </div>

                <div className="decision-reason">
                  <span>Outcome</span>
                  <p>Payment completed successfully. No recovery action is required.</p>
                </div>
              </div>
            ) : decision.uiType === "MANUAL_REVIEW" ? (
              <div className="decision-result">
                <div className="decision-strategy">
                  <span>Recovery Status</span>
                  <strong>MANUAL REVIEW</strong>
                </div>

                <div className="decision-reason">
                  <span>Action</span>
                  <p>{decision.reason || "This payment requires human intervention."}</p>
                </div>
              </div>
            ) : decision.uiType === "OTHER" ? (
              <div className="decision-result">
                <div className="decision-strategy">
                  <span>Recovery Status</span>
                  <strong>NO RECOVERY ACTION</strong>
                </div>

                <div className="decision-reason">
                  <span>Reason</span>
                  <p>{decision.reason || "No automated recovery action is required."}</p>
                </div>
              </div>
            ) : (
              <div className="decision-result">
                <div className="decision-strategy">
                  <span>Recommended Strategy</span>
                  <strong>
                    {decision.strategy || "N/A"}
                  </strong>
                </div>

                <div className="decision-context-grid">
                  <div>
                    <span>Diagnosis</span>
                    <p>{decision.diagnosis || "N/A"}</p>
                  </div>

                  <div>
                    <span>Recommended Action</span>
                    <p>{decision.recommendedAction || "N/A"}</p>
                  </div>

                  <div>
                    <span>Risk Level</span>
                    <strong>{decision.riskLevel || "N/A"}</strong>
                  </div>

                  <div>
                    <span>Policy Status</span>
                    <strong>{decision.policyStatus || "N/A"}</strong>
                  </div>

                  <div>
                    <span>Max Attempts</span>
                    <strong>{decision.maxAttempts ?? "N/A"}</strong>
                  </div>

                  <div>
                    <span>EXECUTION DELAY</span>
                    <strong>
                      {decision.retryDelaySeconds != null
                        ? `${decision.retryDelaySeconds}s`
                        : "N/A"}
                    </strong>
                  </div>
                </div>

                <div className="confidence">
                  <div className="confidence-header">
                    <span>Decision Confidence</span>
                    <strong>
                      {decision.confidence != null
                        ? `${(
                            decision.confidence * 100
                          ).toFixed(0)}%`
                        : "N/A"}
                    </strong>
                  </div>

                  <div className="confidence-bar">
                    <div
                      style={{
                        width: `${
                          decision.confidence != null
                            ? decision.confidence * 100
                            : 0
                        }%`,
                      }}
                    />
                  </div>
                </div>

                <div className="decision-reason">
                  <span>Why this decision?</span>
                  <p>{decision.reason || "N/A"}</p>
                </div>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

function RecoveryAttemptsPage({
  attempts,
  formatDate,
  onExecute,
}) {
  const successful = attempts.filter(
    (attempt) => attempt.status === "SUCCESS"
  ).length;

  const failed = attempts.filter(
    (attempt) => attempt.status === "FAILED"
  ).length;

  const scheduled = attempts.filter(
    (attempt) => attempt.status === "SCHEDULED"
  ).length;

  return (
    <>
      <section className="stats-grid">
        <StatCard
          title="Total Attempts"
          value={attempts.length}
          icon={<RefreshCw size={20} />}
          description="All recovery attempts"
        />

        <StatCard
          title="Successful"
          value={successful}
          icon={<CheckCircle2 size={20} />}
          description="Payments recovered"
          positive
        />

        <StatCard
          title="Failed Attempts"
          value={failed}
          icon={<XCircle size={20} />}
          description="Unsuccessful recovery attempts"
          danger
        />

        <StatCard
          title="Scheduled"
          value={scheduled}
          icon={<Clock3 size={20} />}
          description="Awaiting execution"
          warning
        />
      </section>

      <section className="panel full-payments-panel">
        <div className="panel-header">
          <div>
            <h2>Recovery Attempts</h2>
            <p>Complete recovery execution history</p>
          </div>

          <span className="record-count">
            {attempts.length} records
          </span>
        </div>

        <div className="table-wrapper payments-table-wrapper">
          {attempts.length === 0 ? (
            <div className="empty-state">
              No recovery attempts found.
            </div>
          ) : (
            <table>
              <thead>
                <tr>
                  <th>Attempt</th>
                  <th>Payment</th>
                  <th>Strategy</th>
                  <th>Status</th>
                  <th>Result</th>
                  <th>Executed</th>
                  <th></th>
                </tr>
              </thead>

              <tbody>
                {attempts
                  .slice()
                  .sort(
                    (a, b) =>
                      new Date(b.executedAt || b.createdAt) -
                      new Date(a.executedAt || a.createdAt)
                  )
                  .map((attempt) => (
                    <tr key={attempt.id}>
                      <td>#{attempt.attemptNumber}</td>

                      <td>
                        <strong>
                          Payment #{attempt.paymentId}
                        </strong>
                      </td>

                      <td>
                        {attempt.strategy || "N/A"}
                      </td>

                      <td>
                        <StatusBadge status={attempt.status} />
                      </td>

                      <td>
                        {attempt.result || "N/A"}
                      </td>

                      <td>
                        {formatDate(
                          attempt.executedAt ||
                            attempt.scheduledAt ||
                            attempt.createdAt
                        )}
                      </td>

                      <td>
                        {attempt.status === "SCHEDULED" && (
                          <button
                            className="details-button"
                            onClick={() => onExecute(attempt)}
                          >
                            Execute
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
              </tbody>
            </table>
          )}
        </div>
      </section>
    </>
  );
}

function ManualReviewPage({
  payments,
  onAnalyze,
  getPaymentActionLabel,
  formatDate,
  formatAmount,
}) {
  return (
    <>
      <section className="stats-grid">
        <StatCard
          title="Pending Review"
          value={payments.length}
          icon={<ShieldAlert size={20} />}
          description="Payments requiring human review"
          warning
        />

        <StatCard
          title="Review Value"
          value={formatAmount(
            payments.reduce(
              (total, payment) =>
                total + Number(payment.amount || 0),
              0
            ),
            payments[0]?.currency || "INR"
          )}
          icon={<WalletCards size={20} />}
          description="Total value awaiting review"
        />
      </section>

      <section className="panel full-payments-panel">
        <div className="panel-header">
          <div>
            <h2>Manual Review Queue</h2>
            <p>Payments requiring human intervention</p>
          </div>

          <span className="record-count">
            {payments.length} records
          </span>
        </div>

        <div className="table-wrapper payments-table-wrapper">
          {payments.length === 0 ? (
            <div className="empty-state">
              No payments are currently awaiting manual review.
            </div>
          ) : (
            <table>
              <thead>
                <tr>
                  <th>Payment</th>
                  <th>Customer</th>
                  <th>Amount</th>
                  <th>Reason</th>
                  <th>Created</th>
                  <th></th>
                </tr>
              </thead>

              <tbody>
                {payments.map((payment) => (
                  <tr key={payment.id}>
                    <td>
                      <strong>#{payment.id}</strong>
                    </td>

                    <td>{payment.customerId}</td>

                    <td>
                      <strong>
                        {formatAmount(
                          payment.amount,
                          payment.currency
                        )}
                      </strong>
                    </td>

                    <td>
                      {payment.failureReason ===
                      "HIGH_VALUE_FAILURE"
                        ? "HIGH_VALUE_RISK"
                        : payment.failureReason || "N/A"}
                    </td>

                    <td>{formatDate(payment.createdAt)}</td>

                    <td>
                      <button
                        className="details-button"
                        onClick={() => onAnalyze(payment)}
                      >
                        {getPaymentActionLabel(payment)}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </section>
    </>
  );
}

function SystemHealthPage({
  payments,
  attempts,
}) {
  return (
    <>
      <section className="stats-grid">
        <StatCard
          title="Backend API"
          value="Operational"
          icon={<Activity size={20} />}
          description="Frontend successfully connected"
          positive
        />

        <StatCard
          title="Payments API"
          value="Healthy"
          icon={<CreditCard size={20} />}
          description={`${payments.length} payment records loaded`}
          positive
        />

        <StatCard
          title="Recovery API"
          value="Healthy"
          icon={<RefreshCw size={20} />}
          description={`${attempts.length} attempts loaded`}
          positive
        />

        <StatCard
          title="System Status"
          value="Operational"
          icon={<CheckCircle2 size={20} />}
          description="Core recovery services available"
          positive
        />
      </section>

      <section className="dashboard-grid">
        <div className="panel">
          <div className="panel-header">
            <div>
              <h2>Service Health</h2>
              <p>Current RecoverAI service connectivity</p>
            </div>
          </div>

          <div className="timeline">
            <div className="timeline-item">
              <div className="timeline-icon success">
                <CheckCircle2 size={16} />
              </div>

              <div className="timeline-content">
                <div className="timeline-title">
                  Backend API
                  <StatusBadge status="SUCCESS" />
                </div>

                <div className="timeline-description">
                  API connection is operational and payment data is
                  available.
                </div>
              </div>
            </div>

            <div className="timeline-item">
              <div className="timeline-icon success">
                <CheckCircle2 size={16} />
              </div>

              <div className="timeline-content">
                <div className="timeline-title">
                  Payment Service
                  <StatusBadge status="SUCCESS" />
                </div>

                <div className="timeline-description">
                  Payment records are being retrieved successfully.
                </div>
              </div>
            </div>

            <div className="timeline-item">
              <div className="timeline-icon success">
                <CheckCircle2 size={16} />
              </div>

              <div className="timeline-content">
                <div className="timeline-title">
                  Recovery Service
                  <StatusBadge status="SUCCESS" />
                </div>

                <div className="timeline-description">
                  Recovery attempt records are available.
                </div>
              </div>
            </div>
          </div>
        </div>

        <div className="panel">
          <div className="panel-header">
            <div>
              <h2>System Summary</h2>
              <p>Current workload and recovery state</p>
            </div>
          </div>

          <div className="health-summary">
            <div className="summary-metric">
              <div className="summary-metric-icon neutral">
                <WalletCards size={17} />
              </div>
              <div className="summary-metric-content">
                <span>Payments</span>
                <strong>{payments.length}</strong>
                <small>Records loaded</small>
              </div>
            </div>

            <div className="summary-metric">
              <div className="summary-metric-icon purple">
                <RefreshCw size={17} />
              </div>
              <div className="summary-metric-content">
                <span>Recovery Attempts</span>
                <strong>{attempts.length}</strong>
                <small>Total executions</small>
              </div>
            </div>

            <div className="summary-metric">
              <div className="summary-metric-icon success">
                <CheckCircle2 size={17} />
              </div>
              <div className="summary-metric-content">
                <span>Successful</span>
                <strong>
                  {
                    attempts.filter(
                      (attempt) => attempt.status === "SUCCESS"
                    ).length
                  }
                </strong>
                <small>
                  {
                    attempts.length
                      ? (
                          attempts.filter(
                            (attempt) => attempt.status === "SUCCESS"
                          ).length / attempts.length * 100
                        ).toFixed(1)
                      : "0.0"
                  }% success rate
                </small>
              </div>
            </div>

            <div className="summary-metric">
              <div className="summary-metric-icon danger">
                <XCircle size={17} />
              </div>
              <div className="summary-metric-content">
                <span>Failed</span>
                <strong>
                  {
                    attempts.filter(
                      (attempt) => attempt.status === "FAILED"
                    ).length
                  }
                </strong>
                <small>
                  {
                    attempts.length
                      ? (
                          attempts.filter(
                            (attempt) => attempt.status === "FAILED"
                          ).length / attempts.length * 100
                        ).toFixed(1)
                      : "0.0"
                  }% failure rate
                </small>
              </div>
            </div>
          </div>
        </div>
      </section>
    </>
  );
}

function SimulationPage() {
  const [count, setCount] = useState(25);
  const [result, setResult] = useState(null);
  const [running, setRunning] = useState(false);
  const [simulationError, setSimulationError] = useState("");

  const [customerId, setCustomerId] = useState("recruiter_demo");
  const [amount, setAmount] = useState("12999");
  const [paymentOutcome, setPaymentOutcome] = useState("FAILED");
  const [failureReason, setFailureReason] = useState("INSUFFICIENT_FUNDS");
  const [creatingPayment, setCreatingPayment] = useState(false);
  const [createdPayment, setCreatedPayment] = useState(null);

  const runSimulation = async () => {
    try {
      setRunning(true);
      setSimulationError("");

      const response = await axios.post(
        `${API}/simulation/run`,
        { count }
      );

      setResult(response.data);
    } catch (err) {
      console.error("Simulation error:", err);

      setSimulationError(
        err?.response?.data?.error ||
        "Unable to run simulation. Make sure the backend is running."
      );
    } finally {
      setRunning(false);
    }
  };

  const createTestPayment = async () => {
    try {
      setCreatingPayment(true);
      setSimulationError("");
      setCreatedPayment(null);

      const paymentBody = {
        customerId,
        amount: Number(amount),
        currency: "INR",
        status: paymentOutcome,
        failureReason:
          paymentOutcome === "FAILED"
            ? failureReason
            : null,
        retryCount: 0,
      };

      const response = await axios.post(
        `${API}/payments`,
        paymentBody
      );

      setCreatedPayment(response.data);

      if (paymentOutcome === "FAILED") {
        await axios.post(
          `${API}/payments/${response.data.id}/classify`
        );
      }

      return response.data;
    } catch (err) {
      console.error("Test payment creation error:", err);

      setSimulationError(
        err?.response?.data?.error ||
        "Unable to create test payment."
      );
    } finally {
      setCreatingPayment(false);
    }
  };

  const formatCurrency = (value) =>
    new Intl.NumberFormat("en-IN", {
      style: "currency",
      currency: "INR",
      maximumFractionDigits: 0,
    }).format(value || 0);

  return (
    <section className="simulation-page">

      <div className="panel simulation-breakdown" style={{ marginBottom: "20px" }}>
        <div className="panel-header">
          <div>
            <div className="simulation-eyebrow">
              RECRUITER DEMO
            </div>

            <h2>Test a Payment Recovery</h2>

            <p>
              Create a synthetic payment and observe how RecoverAI
              handles successful and failed payment scenarios.
            </p>
          </div>
        </div>

        <div
          style={{
            display: "grid",
            gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))",
            gap: "16px",
            marginTop: "20px",
          }}
        >
          <label>
            <span style={{ display: "block", marginBottom: "6px" }}>
              Customer ID <span className="required">*</span>
            </span>

            <input
              value={customerId}
              required
              onChange={(event) =>
                setCustomerId(event.target.value)
              }
              disabled={creatingPayment}
              placeholder="recruiter_demo"
            />
          </label>

          <label>
            <span style={{ display: "block", marginBottom: "6px" }}>
              Amount (INR) <span className="required">*</span>
            </span>

            <input
              type="number"
              min="1"
              required
              value={amount}
              onChange={(event) =>
                setAmount(event.target.value)
              }
              disabled={creatingPayment}
            />
          </label>

          <label>
            <span style={{ display: "block", marginBottom: "6px" }}>
              Payment Outcome
            </span>

            <select className="simulation-select"
              value={paymentOutcome}
              onChange={(event) =>
                setPaymentOutcome(event.target.value)
              }
              disabled={creatingPayment}
            >
              <option value="FAILED">Failed</option>
              <option value="SUCCESS">Successful</option>
            </select>
          </label>

          {paymentOutcome === "FAILED" && (
            <label>
              <span style={{ display: "block", marginBottom: "6px" }}>
                Failure Reason
              </span>

              <select className="simulation-select"
                value={failureReason}
                onChange={(event) =>
                  setFailureReason(event.target.value)
                }
                disabled={creatingPayment}
              >
                <option value="INSUFFICIENT_FUNDS">
                  Insufficient Funds
                </option>
                <option value="CARD_DECLINED">
                  Card Declined
                </option>
                <option value="NETWORK_ERROR">
                  Network Error
                </option>
                <option value="EXPIRED_CARD">
                  Expired Card
                </option>
              </select>
            </label>
          )}
        </div>

        <div style={{ marginTop: "20px" }}>
          <button
            className="simulation-run-button"
            onClick={createTestPayment}
            disabled={
              creatingPayment ||
              !customerId.trim() ||
              !amount ||
              Number(amount) <= 0
            }
          >
            <FlaskConical
              size={17}
              className={creatingPayment ? "spin" : ""}
            />

            {creatingPayment
              ? "Creating..."
              : "Simulate Payment"}
          </button>
        </div>

        {createdPayment && (
          <div
            className="simulation-note"
            style={{ marginTop: "20px" }}
          >
            <CheckCircle2 size={16} />

            <span>
              Payment #{createdPayment.id} created as{" "}
              <strong>
                {createdPayment.status}
              </strong>
              .
              {createdPayment.status === "FAILED" &&
                " Recovery analysis has been triggered."}
            </span>
          </div>
        )}
      </div>

      <div className="simulation-hero panel">
        <div>
          <div className="simulation-eyebrow">
            RECOVERY SIMULATION
          </div>

          <h2>Test RecoverAI recovery performance</h2>

          <p>
            Run a controlled payment-failure simulation to estimate
            recovery outcomes, automated attempts, and revenue recovered.
          </p>
        </div>

        <div className="simulation-controls">
          <select className="simulation-select"
            value={count}
            onChange={(event) =>
              setCount(Number(event.target.value))
            }
            disabled={running}
          >
            <option value={25}>25 payments</option>
            <option value={50}>50 payments</option>
            <option value={100}>100 payments</option>
            <option value={250}>250 payments</option>
            <option value={500}>500 payments</option>
          </select>

          <button
            className="simulation-run-button"
            onClick={runSimulation}
            disabled={running}
          >
            <FlaskConical
              size={17}
              className={running ? "spin" : ""}
            />

            {running ? "Running..." : "Run Simulation"}
          </button>
        </div>
      </div>

      {simulationError && (
        <div className="error-banner">
          <AlertCircle size={18} />
          {simulationError}
        </div>
      )}

      {!result && !running && !simulationError && (
        <div className="simulation-empty panel">
          <div className="simulation-empty-icon">
            <FlaskConical size={24} />
          </div>

          <h3>No simulation results yet</h3>

          <p>
            Select a scenario size and run the simulation to see
            projected recovery performance.
          </p>
        </div>
      )}

      {running && (
        <div className="simulation-empty panel">
          <RefreshCw className="spin" size={24} />

          <h3>Running recovery simulation</h3>

          <p>
            Evaluating payment failures and bounded recovery attempts...
          </p>
        </div>
      )}

      {result && !running && (
        <>
          <div className="simulation-summary">
            <div className="simulation-metric panel">
              <span>Revenue at Risk</span>
              <strong>
                {formatCurrency(result.revenueAtRisk)}
              </strong>
              <small>
                {result.paymentsAnalyzed} payments analyzed
              </small>
            </div>

            <div className="simulation-metric panel positive">
              <span>Revenue Recovered</span>
              <strong>
                {formatCurrency(result.recoveredAmount)}
              </strong>
              <small>
                {result.recoveredPayments} payments recovered
              </small>
            </div>

            <div className="simulation-metric panel">
              <span>Recovery Rate</span>
              <strong>
                {result.recoveryRate.toFixed(2)}%
              </strong>
              <small>
                Recovered revenue / revenue at risk
              </small>
            </div>
          </div>

          <div className="simulation-results-grid">

            <div className="panel simulation-breakdown">
              <div className="panel-header">
                <div>
                  <h2>Recovery Outcome</h2>
                  <p>What happened to simulated payments</p>
                </div>
              </div>

              <div className="simulation-outcome-list">

                <div className="simulation-outcome success">
                  <CheckCircle2 size={20} />

                  <div>
                    <span>Recovered</span>
                    <strong>{result.recoveredPayments}</strong>
                  </div>
                </div>

                <div className="simulation-outcome review">
                  <ShieldAlert size={20} />

                  <div>
                    <span>Manual Review</span>
                    <strong>{result.manualReviewPayments}</strong>
                  </div>
                </div>

                <div className="simulation-outcome failed">
                  <XCircle size={20} />

                  <div>
                    <span>Retry Exhausted</span>
                    <strong>{result.unrecoveredPayments}</strong>
                  </div>
                </div>

              </div>
            </div>

            <div className="panel simulation-breakdown">
              <div className="panel-header">
                <div>
                  <h2>Recovery Attempts</h2>
                  <p>Execution activity across the simulation</p>
                </div>
              </div>

              <div className="simulation-attempt-stats">

                <div>
                  <span>Total Attempts</span>
                  <strong>{result.totalAttempts}</strong>
                </div>

                <div>
                  <span>Successful</span>
                  <strong>{result.successfulAttempts}</strong>
                </div>

                <div>
                  <span>Failed</span>
                  <strong>{result.failedAttempts}</strong>
                </div>

              </div>
            </div>

          </div>

          <div className="simulation-note">
            <Activity size={15} />

            <span>
              Simulation results are synthetic and do not modify
              production payment records.
            </span>
          </div>
        </>
      )}
    </section>
  );
}

function PaymentsPage({
  payments,
  totalPayments,
  search,
  status,
  onSearch,
  onStatus,
  onAnalyze,
  getPaymentActionLabel,
  formatDate,
  formatAmount,
}) {
  return (
    <section className="payments-page">
      <div className="payments-toolbar">
        <div className="payment-search">
          <Search size={16} />
          <input
            value={search}
            onChange={(event) => onSearch(event.target.value)}
            className="payment-search-input"
            placeholder="Search payments, customers, or failure reasons"
          />
        </div>

        <select
          className="simulation-select status-filter"
          value={status}
          onChange={(event) => onStatus(event.target.value)}
        >
          <option value="ALL">All statuses</option>
          <option value="SUCCESS">Success</option>
          <option value="FAILED">Failed</option>
          <option value="MANUAL_REVIEW">Manual review</option>
        </select>

        <span className="record-count">
          {payments.length} of {totalPayments} records
        </span>
      </div>

      <div className="panel full-payments-panel">
        <div className="panel-header">
          <div>
            <h2>Payment Records</h2>
            <p>All payments returned by the RecoverAI backend</p>
          </div>
        </div>

        <div className="table-wrapper payments-table-wrapper">
          {payments.length === 0 ? (
            <div className="empty-state payment-empty">
              No payments match the current filters.
            </div>
          ) : (
            <table>
              <thead>
                <tr>
                  <th>Payment</th>
                  <th>Customer</th>
                  <th>Amount</th>
                  <th>Failure Reason</th>
                  <th>Status</th>
                  <th>Created</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {payments.map((payment) => (
                  <tr key={payment.id}>
                    <td>
                      <div className="payment-id">#{payment.id}</div>
                    </td>
                    <td>{payment.customerId || "N/A"}</td>
                    <td className="amount">
                      {formatAmount(payment.amount, payment.currency)}
                    </td>
                    <td>
                      <span className="failure-reason">
                        {payment.failureReason === "HIGH_VALUE_FAILURE" ? "HIGH_VALUE_RISK" : (payment.failureReason || "N/A")}
                      </span>
                    </td>
                    <td>
                      <StatusBadge status={payment.status} />
                    </td>
                    <td>{formatDate(payment.createdAt)}</td>
                    <td>
                      <button
                        className="details-button"
                        onClick={() => onAnalyze(payment)}
                      >
                        {getPaymentActionLabel(payment)}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </section>
  );
}

function StatCard({
  title,
  value,
  icon,
  description,
  positive,
  danger,
  warning,
}) {
  let type = "";

  if (positive) type = "positive";
  if (danger) type = "danger";
  if (warning) type = "warning";

  return (
    <div className="stat-card">
      <div className={`stat-icon ${type}`}>
        {icon}
      </div>

      <div className="stat-content">
        <span>{title}</span>
        <strong>{value}</strong>
        <small>{description}</small>
      </div>
    </div>
  );
}

function HealthRow({
  label,
  value,
  total,
  icon,
  type,
}) {
  const percentage =
    total > 0 ? (value / total) * 100 : 0;

  return (
    <div className="health-row">
      <div className={`health-icon ${type}`}>
        {icon}
      </div>

      <div className="health-info">
        <div className="health-label">
          <span>{label}</span>
          <strong>{value}</strong>
        </div>

        <div className="progress-track">
          <div
            className={`progress-fill ${type}`}
            style={{ width: `${percentage}%` }}
          />
        </div>
      </div>
    </div>
  );
}

function StatusBadge({ status }) {
  const type = status?.toLowerCase() || "neutral";

  return (
    <span className={`status-badge ${type}`}>
      {status?.replace("_", " ") || "UNKNOWN"}
    </span>
  );
}

export default App;







































