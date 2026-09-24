# List and table parity

The React screenshots in `docs/superking-sales-mobile-screenshots` and `docs/superking-sales-large-screenshots` define the item-level visual contract.

## Operational rows

- 64dp minimum interactive height.
- Pale brand icon tile at the leading edge.
- One-line primary identity and up to three metadata lines.
- Right-aligned amount or phone value.
- Worded semantic status badge with success, information, warning, error, or neutral tone.
- Inline print action and trailing chevron when the row opens a detail page.
- Full-row navigation remains available to accessibility services.

This pattern is shared by dashboard activity, trip activity, pending receiving, sales history, cash returns/ledger, customers, and sale/customer selectors.

## Data tables

- Tinted uppercase column header.
- Product identity in the first, wider column.
- Right-aligned numeric columns with supporting base-unit metadata.
- One-pixel row separators and a summary footer when totals are available.
- Product names wrap to two lines; identifiers remain below the name.

This pattern is shared by current stock, receiving shipment contents, and sale-detail line items. The same semantic row data reflows within the available Android width rather than maintaining a web-only fixed pixel width.

## State styling

Status mappings follow the React UI: green for completed/active, blue for in-transit/information, amber for draft/pending, red for failed/voided, and neutral gray for historical or unknown states. Every state remains labeled in text.
