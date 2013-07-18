Gauge Driver
============

The Gauge Driver is the Android side of the RetroGauge. It supplies the gauge
with a rounded two digit data value, percent (derived from previously set
bounds), and color data.

## Dependencies

**OpenXC Libraries**

The OpenXC library
[documentation](http://openxcplatform.com/android/api-guide.html) guide you with
installing this library. The source code also includes example code and
additional documentation in the README file.

### FT Driver

The Gauge Driver also requires [our fork of
FTDriver](https://github.com/openxc/FTDriver) on the `openxc` branch. Download
this library and import it into the same Eclipse workspace as the Gauge project
to fix compile errors.

If there are still errors, go to the `Android` properties for the
`GaugeDriverActivity` project and manually re-link the `openxc` and `FTDriver`
libraries at the bottom of the window.

## Connections

The Gauge Driver connects to the CAN Translator via USB. This connection is
controlled via the OpenXC Vehicle Interface. It connects to the Retro Gauge via
USB controlled by FTDriver, a serial connection emulator.

## Operation

After startup, the user has the following options:

* Start Updates/Stop Updates – This switch toggles the updates to the Retro
  Gauge. When off, no information is sent to the gauge.
* Exit - Exits the application. Note that if the user navigates away from this
  activity via the home or back buttons, updates will continue in the
  background. The Exit button ceases all USB communication with the gauge.
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
* Manual Data Entry - This is a debugging tool. Text entered in the text box is
  sent to the Retro Gauge when the Send button is tapped.

## Under the Hood

### FTDriver

In the Gauge Driver, this is set up in OnCreate only if the app is not returning
from running in the background. Vendor and product IDs do not need to be set,
they are hard coded in the FTDriver code to connect to the FTDI chip.

In preparation for a lack of permission, an Intent `mPermissionIntent` is
created to receive the intent `ACTION_USB_PERMISSION`. `mBroadcastReceiver` is
registered to handle this incoming intent. Once all this is in place, the
FTDriver object `mSerialPort` is created, its permission intent is set to
`mPermissionIntent`, and the app attempts to start the port. If the user has not
yet granted permission, the start fails.

Down in the definition of `mBroadcastReceiver`, we handle the user's granting or
denial of permission to use the port.

Once we've successfully started the port, we start updates with onTimerToggle().

### Timer

The duration of the timer is hard coded as `mTimerPeriod`.

The timer created is `mReceiveTimer`. It is created as `null` and when it is
stopped with onTimerToggle, it is set to null again. It can therefore be used to
determine whether or not it's currently running. If it's `null`, it's not
running.

The timer task, `ReceiveTimerMethod`, polls incoming data,
converts/scales/rounds the data as needed, calculates a percentage on hard coded
parameters, and sends the value and percentage to the virtual serial port.

If the user has selected that the color of the gauge should be controlled by the
data, the color is calculated and sent to the gauge in this method as well.

### Data Sources

The Gauge Driver is set up to use three data sources. The vehicle speed,
mileage, and the steering wheel angle. When the user selects a data source,
`mDataUsed` is set. `ReceiveTimerMethod` determines which data and parameters to
use via the `mUsedData` variable. `mGaugeMin` and `mGaugeRange` are also set.
These as used for the computation of a percentage. All this happens in the
button handler for each data set.
