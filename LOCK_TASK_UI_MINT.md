# LOCK: UI-MINT (Developer A)

Owner decision (2026-11-18): Mint & Cream identity replaces Modern Blue; the product card opens a details screen; a product property opens its own screen.

Branch: `claude/developer-a-mint`, rebased on `main` @ c53edf3 (the earlier `claude/developer-a` was based on the old shared branch and conflicted with the new home screen, so it is abandoned).

## Files touched
- `ui/theme/Color.kt`, `ui/theme/Theme.kt`: Mint & Cream values. Kept from main: `OnBrand`, `Info*`, `SignIn*`, `Illustration*`, `KonozColors.isDark`, Cairo typography, theme preference. Added: cream, card gradient stops, success container.
- `ui/theme/ReportCardColors.kt`: `ReportCardTokens` has the same names, but the getters are `@Composable` and follow the theme (HomeScreen already calls them inside composables).
- new: `ui/theme/CardGradients.kt`, `ui/common/KonozComponents.kt` (`KonozIconBadge`)
- `ui/common/ProductImage.kt`: rounded frame
- `ui/home/HomeScreen.kt`: Rounded icons and rounded-square badge only; no behavior change
- `ui/products/ProductsRoute.kt`, `ProductsScreen.kt` (`onEdit` renamed `onOpen`), new `ProductDetailScreen.kt`, `ProductDetailViewModel.kt`
- new: `res/values{,-ar}/strings_product_details.xml`

## Not touched
`KonozRoot.kt`, `MoreRoute.kt`, `MoreTileGrid.kt` (no longer the main entry), `ReportCardsStrip.kt`, sign-in screen (its violet accent stays as on main).

## Known
- Not built here (no Android SDK). Run the build in Actions.
- Owner primary #4D9870 with white text is about 3.5:1: white on Primary is for bold button labels and icons only.
- Screens with hard-coded colors that were not opened are unaudited.

Delete this file on merge.
