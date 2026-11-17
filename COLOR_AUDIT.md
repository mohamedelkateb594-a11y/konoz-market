# Color system audit (Developer B)

Scope: colors only. No layout, order of elements or function was changed.

## Where colors live
- `ui/theme/Color.kt` (`Brand`): the only place raw colors are defined.
- `ui/theme/Theme.kt`: Material 3 `LightScheme` / `DarkScheme` plus `KonozColors` (success, warning, info, sign-in accent), chosen by the app's dark flag.
- `ui/theme/ThemeMode.kt`: System / Light / Dark choice, kept in SharedPreferences, applied live (no activity recreation). Switch: More > Appearance tile.
- `MainActivity`: passes the choice to `KonozTheme` and sets system-bar icon colors from the app's own mode.
- `res/values-night/themes.xml`: dark window behind the first frame on a phone in dark mode.

## Findings
1. Sign-in screens had their own violet, header and border colors written as hex, plus `Color.White`, and read the phone's dark flag directly. They ignored the app's mode. Fixed: all from tokens.
2. No in-app appearance choice existed. Added (System / Light / Dark).
3. Window theme was always light: white flash when a phone in dark mode opened the app. Fixed with `values-night`.
4. Several pairs were under WCAG AA (measured, see table). Fixed by small value changes that keep the Modern Blue identity.
5. Control borders (`outline`) were 1.4:1 (light) and 1.7:1 (dark). Raised to 3:1 or more. `outlineVariant` stays subtle and is only for dividers.
6. No Info token. Added (`info` / `onInfo`).
7. Files read and found clean (tokens only, no raw colors): Dashboard, More tile grid, Cashier, Shop settings, Invoice renderer. The other screens (products, purchases, expenses, stock count, reports, shifts, users, backup, printer, alerts, returns) were NOT opened one by one: the repository text search is unavailable here, so a grep for `Color(0x`, `Color.White`, `Color.Black` over `ui/` is still to be run in an IDE.

## Changes (ratio = WCAG contrast; AA text needs 4.5, borders and graphics need 3)
| Token | Before | After | Pair | Before | After |
|---|---|---|---|---|---|
| TextSecondary (light) | 64748B | 56657B | on background / on accent | 4.4 / 4.2 | 5.5 / 5.2 |
| Secondary (light) | 4285F4 | 2D6AD6 | white on it | 3.6 | 5.1 |
| Success (light) | 0F8A5F | 0C7A54 | white on it | 4.4 | 5.4 |
| Outline (light) | D5DCE8 | 76859B | on surface | 1.4 | 3.8 |
| Outline (dark) | 33425E | 6B7C99 | on surface | 1.7 | 4.0 |
| Sign-in accent (light) | 6C63FF | 5850E6 | white on it | 4.3 | 5.7 |
| Sign-in accent (dark) | 8E88FF with white text | A39EFF with dark text | text on it | 2.9 | 7.8 |
| Info (new) | none | 0E7490 / 67D4E8 | | | 5.4 / 9.8 |

Everything else already passed and is unchanged. `ThemeContrastTest` fails the build if a token pair drops below AA.

## States
Disabled, pressed, focused and hover states are produced by Material 3 from the same tokens (alpha of onSurface / state layers), so they follow the new values.

## Open decisions for the owner
- The sign-in screen keeps the violet accent from the owner's reference. Switching it to the brand blue is a one-line change in `Brand.SignInAccentLight` / `SignInAccentDark`.
- Not tested on a device and not compiled yet: all numbers come from calculation, not from screenshots. Please check each screen in light and dark after installing.
