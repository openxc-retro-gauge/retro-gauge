BOARD_TAG    = pro328
ARDUINO_LIBS = SwitecX25
USER_LIB_PATH = .

OSTYPE := $(shell uname)
ifndef MONITOR_PORT
	ifeq ($(OSTYPE),Darwin)
		MONITOR_PORT = /dev/tty.usbserial*
	else
		OSTYPE := $(shell uname -o)
		ifeq ($(OSTYPE),Cygwin)
			MONITOR_PORT = com3
		else
			MONITOR_PORT = /dev/ttyUSB*
		endif
	endif
endif

include Arduino-Makefile/arduino-mk/Arduino.mk
