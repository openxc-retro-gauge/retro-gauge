Gauge Driver
============

The Gauge Driver is the Android side of the RetroGauge. It supplies the gauge
with a rounded two digit data value, percent (derived from previously set
bounds), and color data.

Dependencies
------------

The Gauge Driver requires the OpenXC Android Libraries as well as the open
source FTDriver by ksksue, available here:  https://github.com/ksksue/FTDriver

Connections
-----------

The Gauge Driver connects to the CAN Translator via USB. This connection is
controlled via the OpenXC Vehicle Interface. It connects to the Retro Gauge via
USB controlled by FTDriver, a serial connection emulator.

Data Format
-----------

The Retro Gauge can receive two types of data packet.

* Color data is delivered with three digits within angle brackets. `<###>`
  Valid values are between 0 and 259.
* Value data is delivered with two digits each of value, and percentage. The
  numbers are separated by a comma, and enclosed in parentheses. `(vv,pp)`

Operation
==========

After startup, the user has the following options:

* Start Updates/Stop Updates – This switch toggles the updates to the Retro
  Gauge. When off, no information is sent to the gauge.
* Exit - Exits the application. Note that if the user navigates away from this
  activity via the home or back buttons, updates will continue in the
  background. The Exit button ceases all USB communcation with the gauge.
* Data Source Selection - The large buttons labeled "Vehicle Speed", "Miles per
  Gallon", and "Steering Wheel Angle" select those data sources for the
  application. When a data source is selected, a hard coded color pallet is
  set. Hard coded bounds are also set for computing a percentage. All this
  happens in the button handlers.
* Color control slider - The user can use this slider to manually change the
  color of the gauge.
* Map Color To Value - If this is checked, the app ignores the slider and
  instead sets the color by the percentage value. Each data source uses a
  different color scheme. Vehicle Speed data sets colors ranging from green at
  zero to red at 80mph. Miles Per Gallon data sweeps through red at zero to
  green at 50 miles per gallon and above. Steering Wheel data sweeps through
  the entire spectrum.
* Manual Data Entry - This is a debuging tool. Text entered in the text box is
  sent to the Retro Gauge when the Send button is tapped.
