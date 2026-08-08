# Issue tracking

---

## Not displaying the server banner
We don't show the server's banner on the homescreen.

---

## The theme setting lives under Client settings, not Display
jellyfin-web files the theme under Display, and `SettingsEntry.Display` is a placeholder for a
screen that does not exist yet. The Appearance section should move there when it lands.

---

## Light mode has only been checked in previews
`ColorSchemes.kt` has a full scheme each way and `tools/palette.py` proves the contrast, but only
Home, Login and Client settings have a light preview, and none of it has run on a device. The
player and the detail hero are deliberately dark in both (video and artwork).

---

## Cold start is dark even when the user picked Light
`Theme.JellyfinMobile` sets a fixed dark `windowBackground`, because the app's scheme lives in
DataStore and the window is themed long before that can be read. A user on Light sees a dark frame
between the launch icon and the first composed frame. Fixing it properly means mirroring the choice
somewhere readable synchronously at activity-create time.

---

## Badge text is low contrast
`BadgeContent` is white on `BadgeUnwatched` (`#00A4DC`), which is about 2.9:1 — under what small
text needs. Predates the theme work and was left alone rather than restyled in passing.
