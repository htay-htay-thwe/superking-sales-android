# Foundation layout contract

This phase covers screen geometry and visual tokens only. Screen content, list rows, reusable components,
detail-page actions, navigation, state, and API behavior remain unchanged.

- Mobile reference: 390 × 844 CSS px; compact layouts use one content column and a 64 dp header.
- Large reference: 1440 × 900 CSS px; windows at `w600dp` use a bounded 1120 dp canvas. `w840dp` adds 80 dp outer gutters.
- Content gutters: 16 dp compact, 24 dp wide, 80 dp extra-wide. System bars apply outside the content shell.
- Spacing scale: 4 / 8 / 12 / 16 / 24 / 32 dp. Panel radius: 6 dp.
- Typography: 22 sp page title, 18 sp section title, 14 sp body, 12 sp supporting, 11 sp eyebrow.
- Touch targets: 48 dp minimum for navigation, buttons, account, and back affordances.
- Light and dark themes retain identical geometry; dark mode swaps surfaces/ink only.
- Myanmar text uses existing locale resources and activity font scaling. Long labels wrap rather than changing business values.

The implementation is in `values/foundation.xml`, width-qualified values, theme resources, `WorkspaceColumn`,
and `ResponsiveGrid`. Resizing remeasures existing child views, preserving input state.
