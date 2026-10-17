APRS Net Android - third-party attributions
==============================================

APRS symbol graphics
-----------------------
The 128x128 PNG sprite sheets in `app/src/main/res/drawable-nodpi/`:

  - aprs_symbols_128_0.png   (primary table)
  - aprs_symbols_128_1.png   (alternate table)
  - aprs_symbols_128_2.png   (overlay characters)

are taken from the aprs.fi high-resolution APRS symbol set by Heikki
Hannikainen, OH7LZB:

  https://github.com/hessu/aprs-symbols

The set is provided for free use by APRS applications; users are asked to
credit the source URL above. Copyright and licensing of individual symbols
differs and is documented upstream in COPYRIGHT.md. Some symbols are
vectorised vendor logos (Apple, Microsoft, Kenwood) whose marks belong to
their owners - review before commercial redistribution.

Symbol descriptions (`app/src/main/assets/aprs_symbols.json`) are derived from
the aprs.fi symbol index by the same author, licensed CC BY-SA 4.0:

  https://github.com/hessu/aprs-symbol-index

osmdroid + osmbonuspack
---------------------------
Map tiles are rendered by osmdroid (Apache 2.0). Marker clustering uses
osmbonuspack's RadiusMarkerClusterer.

  https://github.com/osmdroid/osmdroid
  https://github.com/MKergall/osmbonuspack