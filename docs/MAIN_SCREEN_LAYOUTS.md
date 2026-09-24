# Main screen layout map

The main screens share the bounded workspace container, responsive grids, swipe refresh, and state regions. Their business actions and data sources are unchanged.

| Screen | Layout boundary |
| --- | --- |
| Login | Centered, single-column sign-in surface capped at 480dp |
| Dashboard | Route header, KPI grid, primary sale action, recent sales, stock/receiving columns |
| Trip | Trip identity, KPI grid, actions, sales/expense columns, settlement boundary |
| Stock | Header, balance metrics, view/search controls, receiving and inventory sections |
| Customers | Header action, search controls, operational customer rows |
| New customer | Header, cash-only metadata, responsive field grid, cancel/create action row |
| New sale | Workspace header, four-step indicator, step-specific scroll/list boundary, bottom action row |
| Sales history | Summary metrics, primary action, filter controls, transaction rows |
| Cash hold | Trip context, custody metrics, breakdown, history controls and ledger/return rows |
| Profile/settings | Identity metadata, responsive personal/security columns, display and device settings |
| Sale detail | Read-only header boundary, transaction metadata, products, totals, location and audit |
| Receiving | Transfer header and overview, shipment rows, audit, all-or-nothing receiving action |

Compact widths use one column. Responsive grids add columns only when their minimum cell width remains available after font scaling. All screens retain pull-to-refresh and shared loading/error/empty regions.
